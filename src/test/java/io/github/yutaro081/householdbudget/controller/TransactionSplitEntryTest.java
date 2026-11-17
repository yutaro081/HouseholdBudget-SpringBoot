package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * 1 回の支払いを、明細の行（5 行まで）で複数の科目に分けて登録する画面と処理のテスト（issue 032 の段階 2。設計書 2.2、2.3、6.2、7.8）。
 * 段階 2 では、保存は「印のない、ふつうの取引を N 件」。編集は 1 つの取引ずつ（明細は 1 行）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストの準備（ApplicationContext）を増やさないため、自分の @TestConfiguration は作らない（段階 0。手順書 2.5）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionSplitEntryTest {

	private static final int LINES = 5;

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long userId, String username, Category food, Category daily, Vendor vendor, PaymentMethod payment) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@BeforeEach
	void createUsers() {
		a = createOwner("splitx1");
		b = createOwner("splitx2");
		asA = user(new LoginUser(a.userId(), a.username(), "x"));
		asB = user(new LoginUser(b.userId(), b.username(), "x"));
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, username,
				categoryRepository.save(new Category(id, "食費")), categoryRepository.save(new Category(id, "日用品")),
				vendorRepository.save(new Vendor(id, "スーパー")), paymentMethodRepository.save(new PaymentMethod(id, "現金")));
	}

	private Transaction saveFor(Owner o, int amount, String description) {
		Transaction t = transactionRepository.save(new Transaction(o.userId(), LocalDate.of(2026, 11, 9), amount,
				o.food(), description, o.vendor(), o.payment()));
		entityManager.flush();
		entityManager.clear();
		return t;
	}

	/** 登録の古い順（id の小さい順） */
	private List<Transaction> transactionsOf(Owner o) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(o.userId()).stream()
				.sorted(Comparator.comparing(Transaction::getId)).toList();
	}

	private static String line(int index, String field) {
		return "lines[" + index + "]." + field;
	}

	/**
	 * 共通の項目（日付・取引先・支払方法）を正しく入れた POST。values に「名前, 値, 名前, 値, ...」を渡すと、その項目を足す・差し替える
	 * （MockMvc の param は同じ名前を置き換えず追加するため、差し替えはここで行う）。
	 */
	private MockHttpServletRequestBuilder common(MockHttpServletRequestBuilder builder, Owner o, String... values) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("transactionDate", "2026-11-09");
		params.put("vendorId", String.valueOf(o.vendor().getId()));
		params.put("paymentMethodId", String.valueOf(o.payment().getId()));
		for (int i = 0; i < values.length; i += 2) {
			params.put(values[i], values[i + 1]);
		}
		builder.with(csrf());
		params.forEach(builder::param);
		return builder;
	}

	/** 例：スーパー 3,000 円 → 食費 2,200 円（1 行目）＋ 日用品 800 円（2 行目） */
	private String[] receipt(Owner o) {
		return new String[] {
				line(0, "categoryId"), String.valueOf(o.food().getId()), line(0, "amount"), "2200", line(0, "description"), "お弁当",
				line(1, "categoryId"), String.valueOf(o.daily().getId()), line(1, "amount"), "800", line(1, "description"), "" };
	}

	private static String[] join(String[] first, String... more) {
		String[] all = new String[first.length + more.length];
		System.arraycopy(first, 0, all, 0, first.length);
		System.arraycopy(more, 0, all, first.length, more.length);
		return all;
	}

	private String page(MockHttpServletRequestBuilder request, RequestPostProcessor as) throws Exception {
		return mockMvc.perform(request.with(as)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/** index 番目の明細の行（data-split-line の入れ物）の HTML。次の行、または「キャンセル」「保存」の行の手前まで */
	private static String lineBlock(String body, int index) {
		int start = -1;
		for (int i = 0; i <= index; i++) {
			start = body.indexOf("data-split-line", start + 1);
			assertThat(start).as((index + 1) + " 行目の入れ物がある").isPositive();
		}
		int next = body.indexOf("data-split-line", start + 1);
		int end = next >= 0 ? next : body.indexOf("form-actions", start);
		return body.substring(start, end);
	}

	/** name の select の中身 */
	private static String selectNamed(String body, String name) {
		Matcher m = Pattern.compile("(?s)<select[^>]*name=\"" + Pattern.quote(name) + "\"[^>]*>(.*?)</select>").matcher(body);
		assertThat(m.find()).as("select[name=" + name + "] がある").isTrue();
		return m.group(1);
	}

	// ---- 登録の画面 ----

	/** 共通の項目は 1 つずつ。明細の行は 5 行（科目・金額・摘要）。6 行目はない */
	@Test
	void theNewFormHasTheCommonFieldsOnceAndFiveLines() throws Exception {
		String body = page(get("/transactions/new"), asA);

		for (String name : List.of("transactionDate", "vendorId", "paymentMethodId")) {
			assertThat(count(body, "name=\"" + name + "\"")).as(name).isEqualTo(1);
		}
		for (int i = 0; i < LINES; i++) {
			for (String field : List.of("categoryId", "amount", "description")) {
				assertThat(count(body, "name=\"" + line(i, field) + "\"")).as(line(i, field)).isEqualTo(1);
			}
		}
		assertThat(body).doesNotContain("lines[5]");
		assertThat(count(body, "data-split-line")).isEqualTo(LINES);
		assertThat(body).as("金額・科目・摘要の古い名前は、もうない").doesNotContain("name=\"amount\"", "name=\"categoryId\"", "name=\"description\"");
	}

	/** どの行の科目も、空の選択肢（「選択してください」）で始まる。最初の科目が選ばれた状態だと、空の行が「一部だけ入った行」になるため */
	@Test
	void everyLineOfTheNewFormStartsWithTheEmptyCategoryChoice() throws Exception {
		String body = page(get("/transactions/new"), asA);

		for (int i = 0; i < LINES; i++) {
			String select = selectNamed(body, line(i, "categoryId"));
			assertThat(select.strip()).as((i + 1) + " 行目：最初の選択肢は空").startsWith("<option value=\"\">選択してください</option>");
			assertThat(select).as((i + 1) + " 行目：選ばれた科目はない").doesNotContain("selected");
		}
	}

	/** 複写：写す元は 1 行目に入る。2〜5 行目は空（科目は空の選択肢）。日付は今日（ここでは日付の値は確かめない） */
	@Test
	void theCopyFormPutsTheSourceInTheFirstLineAndLeavesTheOtherLinesEmpty() throws Exception {
		Transaction src = saveFor(a, 3456, "週末の買い物");

		String body = page(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())), asA);

		assertThat(selectNamed(body, line(0, "categoryId"))).contains("<option value=\"" + a.food().getId() + "\" selected=\"selected\">食費</option>");
		assertThat(body).contains("name=\"" + line(0, "amount") + "\" value=\"3456\"", "name=\"" + line(0, "description") + "\" value=\"週末の買い物\"");
		for (int i = 1; i < LINES; i++) {
			String select = selectNamed(body, line(i, "categoryId"));
			assertThat(select.strip()).as((i + 1) + " 行目").startsWith("<option value=\"\">選択してください</option>");
			assertThat(select).as((i + 1) + " 行目：選ばれた科目はない").doesNotContain("selected");
			assertThat(body).as((i + 1) + " 行目の金額・摘要は空")
					.contains("name=\"" + line(i, "amount") + "\" value=\"\"", "name=\"" + line(i, "description") + "\" value=\"\"");
		}
		assertThat(count(body, "data-split-line")).isEqualTo(LINES);
	}

	/** 「行を足す」と、明細の合計の入れ物は、JavaScript が動いたときだけ見せる（hidden）。この画面だけで split-lines.js を読む */
	@Test
	void theAddButtonAndTheTotalAreHiddenUntilJavaScriptRunsAndOnlyThisScreenLoadsTheScript() throws Exception {
		String body = page(get("/transactions/new"), asA);

		assertThat(body).containsPattern("<button type=\"button\"[^>]*data-split-add[^>]*hidden[^>]*>[^<]*行を足す</button>");
		assertThat(body).containsPattern("<[a-z]+[^>]*data-split-total-box[^>]*hidden");
		assertThat(body).contains("data-split-total");
		assertThat(body).contains("<script defer src=\"/js/split-lines.js\"></script>");
		for (String path : List.of("/transactions", "/budgets", "/categories", "/comparison", "/")) {
			assertThat(page(get(path), asA)).as(path).doesNotContain("split-lines.js", "data-split-");
		}
	}

	/** 「行を消す」の開始タグ（type="button" と hidden がある。属性の順は問わない） */
	private static final Pattern REMOVE_BUTTON = Pattern.compile("<button[^>]*data-split-remove[^>]*>");

	/** 登録の画面（新規・複写・入力エラーの出し直し）：明細 2〜5 にだけ「行を消す」がある。type="button"、hidden で始まる。明細 1 にはない */
	@Test
	void linesTwoToFiveHaveAHiddenRemoveButtonAndLineOneDoesNot() throws Exception {
		Transaction src = saveFor(a, 3456, "週末の買い物");
		String[] bodies = {
				page(get("/transactions/new"), asA),
				page(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())), asA),
				page(common(post("/transactions/new"), a, line(0, "categoryId"), String.valueOf(a.food().getId()), line(0, "amount"), "abc"), asA) };
		for (String body : bodies) {
			assertThat(REMOVE_BUTTON.matcher(lineBlock(body, 0)).find()).as("明細 1 には付けない").isFalse();
			for (int i = 1; i < LINES; i++) {
				Matcher tag = REMOVE_BUTTON.matcher(lineBlock(body, i));
				assertThat(tag.find()).as("明細 " + (i + 1) + " にある").isTrue();
				assertThat(tag.group()).contains("type=\"button\"").containsPattern("\\shidden[\\s>]");
				assertThat(lineBlock(body, i)).contains(">行を消す</button>");
				assertThat(tag.find()).as("1 行に 1 つ").isFalse();
			}
			assertThat(count(body, "data-split-remove")).isEqualTo(LINES - 1);
		}
	}

	/** 編集の画面（1 行）には「行を消す」を出さない */
	@Test
	void theEditFormHasNoRemoveButton() throws Exception {
		Transaction t = saveFor(a, 1200, "昼食");

		assertThat(page(get("/transactions/" + t.getId() + "/edit"), asA)).doesNotContain("data-split-remove", "行を消す");
	}

	// ---- 登録 ----

	/** 2 行を入れて保存すると、取引が 2 件できる。日付・取引先・支払方法は同じ。行の順に登録される */
	@Test
	void twoLinesBecomeTwoTransactionsWithTheSameDateVendorAndPayment() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a, receipt(a)).with(asA))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/transactions"))
				.andExpect(flash().attribute("successMessageKey", "success.transactionRegistered"));

		List<Transaction> saved = transactionsOf(a);
		assertThat(saved).hasSize(2);
		assertThat(saved).extracting(Transaction::getAmount).containsExactly(2200, 800);
		assertThat(saved).extracting(t -> t.getCategory().getId()).containsExactly(a.food().getId(), a.daily().getId());
		assertThat(saved).extracting(Transaction::getDescription).containsExactly("お弁当", null);
		assertThat(saved).allSatisfy(t -> {
			assertThat(t.getUserId()).isEqualTo(a.userId());
			assertThat(t.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 9));
			assertThat(t.getVendor().getId()).isEqualTo(a.vendor().getId());
			assertThat(t.getPaymentMethod().getId()).isEqualTo(a.payment().getId());
		});
	}

	/** 全部が空の行は無視する（1 行目と 3 行目だけ → 2 件）。摘要が空白だけの行も、空の行 */
	@Test
	void blankLinesAreIgnored() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a,
				line(0, "categoryId"), String.valueOf(a.food().getId()), line(0, "amount"), "100", line(0, "description"), "",
				line(1, "categoryId"), "", line(1, "amount"), "", line(1, "description"), "　 ",
				line(2, "categoryId"), String.valueOf(a.daily().getId()), line(2, "amount"), "300", line(2, "description"), "",
				line(3, "categoryId"), "", line(3, "amount"), " ", line(3, "description"), "",
				line(4, "categoryId"), "", line(4, "amount"), "", line(4, "description"), "").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).extracting(Transaction::getAmount).containsExactly(100, 300);
	}

	/** 1 行だけの登録は、今までの登録と同じ（1 件） */
	@Test
	void oneLineIsOneTransactionAsBefore() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a,
				line(0, "categoryId"), String.valueOf(a.food().getId()), line(0, "amount"), "1200").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).extracting(Transaction::getAmount).containsExactly(1200);
	}

	/** 1 行も入っていなければ、入力エラー。何も保存しない。5 行が出し直される */
	@ParameterizedTest(name = "明細が空：{0}")
	@ValueSource(strings = { "行を 1 つも送らない", "5 行とも空で送る" })
	void noLinesIsAnErrorAndSavesNothing(String how) throws Exception {
		String[] blanks = new String[0];
		if (how.startsWith("5")) {
			blanks = new String[LINES * 6];
			for (int i = 0; i < LINES; i++) {
				blanks[i * 6] = line(i, "categoryId");
				blanks[i * 6 + 1] = "";
				blanks[i * 6 + 2] = line(i, "amount");
				blanks[i * 6 + 3] = "";
				blanks[i * 6 + 4] = line(i, "description");
				blanks[i * 6 + 5] = "";
			}
		}

		String body = page(common(post("/transactions/new"), a, blanks), asA);

		assertThat(body).contains("明細を 1 行以上入力してください");
		assertThat(count(body, "class=\"field-error\"")).isEqualTo(1);
		assertThat(count(body, "data-split-line")).isEqualTo(LINES);
		assertThat(transactionsOf(a)).isEmpty();
	}

	/**
	 * 一部だけ入った行は、足りない項目のエラーを、その行に出す。何も保存しない（1 行目が正しくても）。入れた値は残る。
	 * 金額に型変換のエラーがある行は、空の行として扱わない（科目のエラーも出る）
	 */
	@ParameterizedTest(name = "2 行目に {0} だけ → {1}")
	@CsvSource(delimiter = '|', value = {
			"amount|500|科目を選択してください|1",
			"categoryId|DAILY|金額を入力してください|1",
			"description|メモ|科目を選択してください,金額を入力してください|2",
			"amount|abc|金額は整数で入力してください,科目を選択してください|2",
			"categoryId|abc|科目を選択してください,金額を入力してください|2"
	})
	void aPartlyFilledLineIsAnErrorOnThatLine(String field, String value, String messages, int errors) throws Exception {
		String sent = "DAILY".equals(value) ? String.valueOf(a.daily().getId()) : value;

		String body = page(common(post("/transactions/new"), a,
				line(0, "categoryId"), String.valueOf(a.food().getId()), line(0, "amount"), "2200",
				line(1, field), sent), asA);

		String second = lineBlock(body, 1);
		for (String message : messages.split(",")) {
			assertThat(second).as("2 行目にエラー").contains(message);
		}
		assertThat(count(body, "class=\"field-error\"")).as("エラーは 2 行目の分だけ").isEqualTo(errors);
		assertThat(count(lineBlock(body, 0), "class=\"field-error\"")).as("1 行目にはエラーがない").isZero();
		if (!"categoryId".equals(field)) {
			assertThat(second).contains("name=\"" + line(1, field) + "\" value=\"" + value + "\"");
		}
		assertThat(body).doesNotContain("明細を 1 行以上入力してください");
		assertThat(transactionsOf(a)).isEmpty();
	}

	/**
	 * 今までの入力チェックは、どの行でも、今までどおり効く（3 行目で確かめる）：金額の範囲、型変換のエラー、必須、全角の数字、摘要の文字数。
	 * エラーはその行に 1 つ。入力した値は残る。何も保存しない（1 行目が正しくても）
	 */
	@ParameterizedTest(name = "3 行目：{0} = [{1}] → {2}")
	@CsvSource(delimiter = '|', value = {
			"amount|0|金額は1円以上1,000,000円以下で入力してください",
			"amount|1000001|金額は1円以上1,000,000円以下で入力してください",
			"amount|１０００００１|金額は1円以上1,000,000円以下で入力してください",
			"amount|abc|金額は整数で入力してください",
			"amount|1,000|金額は整数で入力してください",
			"amount|１２円|金額は整数で入力してください",
			"amount|' '|金額を入力してください",
			"description|あああああああああああああああああああああああああああああああ|摘要は30文字以内で入力してください"
	})
	void theExistingChecksWorkOnEveryLine(String field, String value, String message) throws Exception {
		String[] third = { line(2, "categoryId"), String.valueOf(a.daily().getId()), line(2, "amount"), "300" };
		third = join(third, line(2, field), value);

		String body = page(common(post("/transactions/new"), a,
				join(new String[] { line(0, "categoryId"), String.valueOf(a.food().getId()), line(0, "amount"), "2200" }, third)), asA);

		String block = lineBlock(body, 2);
		assertThat(block).contains(message);
		assertThat(count(body, "class=\"field-error\"")).as("エラーは 1 つ").isEqualTo(1);
		assertThat(count(block, "class=\"field-error\"")).as("3 行目に").isEqualTo(1);
		if (!value.isBlank() && !value.startsWith("１０")) {
			assertThat(block).contains("name=\"" + line(2, field) + "\" value=\"" + value + "\"");
		}
		assertThat(transactionsOf(a)).isEmpty();
	}

	/** 全角の数字は、どの行でも半角に直して受け付ける（R13） */
	@Test
	void fullWidthAmountsAreAcceptedOnEveryLine() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a,
				line(0, "categoryId"), String.valueOf(a.food().getId()), line(0, "amount"), "１２００",
				line(4, "categoryId"), String.valueOf(a.daily().getId()), line(4, "amount"), "　３4５　").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).extracting(Transaction::getAmount).containsExactly(1200, 345);
	}

	/** 入力エラーで出し直すときも、5 行が出る（送った行が 1 行だけでも）。選んでいた科目・取引先・支払方法は選ばれたまま */
	@Test
	void theRedisplayedNewFormStillHasFiveLinesAndKeepsTheChoices() throws Exception {
		String body = page(common(post("/transactions/new"), a,
				line(0, "categoryId"), String.valueOf(a.daily().getId()), line(0, "amount"), "abc"), asA);

		assertThat(count(body, "data-split-line")).isEqualTo(LINES);
		assertThat(selectNamed(body, line(0, "categoryId"))).contains("<option value=\"" + a.daily().getId() + "\" selected=\"selected\">日用品</option>");
		assertThat(selectNamed(body, "vendorId")).contains("selected=\"selected\">スーパー</option>");
		for (int i = 1; i < LINES; i++) {
			assertThat(selectNamed(body, line(i, "categoryId"))).as((i + 1) + " 行目").doesNotContain("selected");
		}
	}

	// ---- 守り（他人のデータ、送信の形、CSRF） ----

	/** 他人の科目が 1 行でも入っていたら 404。ほかの行も含めて、何も保存しない（行ごとに保存しない） */
	@Test
	void anotherUsersCategoryOnOneLineSavesNoLineAtAll() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a, join(receipt(a), line(1, "categoryId"), String.valueOf(b.daily().getId())))
				.with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(transactionsOf(a)).isEmpty();
		assertThat(transactionsOf(b)).isEmpty();
	}

	/** 存在しない科目が 2 行目にあっても、同じ 404。1 行目も保存しない */
	@Test
	void aMissingCategoryOnTheSecondLineSavesNoLineAtAll() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a, join(receipt(a), line(1, "categoryId"), "999999999")).with(asA))
				.andExpect(status().isNotFound());

		assertThat(transactionsOf(a)).isEmpty();
	}

	/** 他人の取引先・支払方法（共通の項目）なら 404。どの行も保存しない */
	@ParameterizedTest(name = "{0} が他人のもの → 404、0 件")
	@ValueSource(strings = { "vendorId", "paymentMethodId" })
	void anotherUsersVendorOrPaymentMethodSavesNoLineAtAll(String field) throws Exception {
		String other = "vendorId".equals(field) ? String.valueOf(b.vendor().getId()) : String.valueOf(b.payment().getId());

		mockMvc.perform(common(post("/transactions/new"), a, join(receipt(a), field, other)).with(asA))
				.andExpect(status().isNotFound());

		assertThat(transactionsOf(a)).isEmpty();
		assertThat(transactionsOf(b)).isEmpty();
	}

	/** 6 行目（lines[5]）や、番号を書き換えた行を送ると 400。何も保存しない */
	@ParameterizedTest(name = "{0} を送る → 400")
	@ValueSource(strings = { "lines[5].amount", "lines[5].categoryId", "lines[-1].amount", "lines[abc].amount", "lines[99].description" })
	void tooManyOrMalformedLinesAreABadRequest(String name) throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a, join(receipt(a), name, "100")).with(asA))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("error/4xx"));

		assertThat(transactionsOf(a)).isEmpty();
	}

	/** CSRF のトークンがない POST は 403。何も保存しない */
	@Test
	void postWithoutCsrfTokenSavesNoLine() throws Exception {
		MockHttpServletRequestBuilder request = post("/transactions/new").with(asA)
				.param("transactionDate", "2026-11-09")
				.param("vendorId", String.valueOf(a.vendor().getId()))
				.param("paymentMethodId", String.valueOf(a.payment().getId()));
		String[] lines = receipt(a);
		for (int i = 0; i < lines.length; i += 2) {
			request.param(lines[i], lines[i + 1]);
		}

		mockMvc.perform(request).andExpect(status().isForbidden());

		assertThat(transactionsOf(a)).isEmpty();
	}

	/** 画面から送られた userId は使わない（持ち主はログイン中のユーザー） */
	@Test
	void aUserIdSentFromTheScreenIsIgnored() throws Exception {
		mockMvc.perform(common(post("/transactions/new"), a, join(receipt(a), "userId", String.valueOf(b.userId()))).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).hasSize(2);
		assertThat(transactionsOf(b)).isEmpty();
	}

	// ---- 編集（段階 2 では 1 つの取引ずつ） ----

	/** 編集の画面は、明細が 1 行だけ。「行を足す」は出さない */
	@Test
	void theEditFormHasOnlyOneLineAndNoAddButton() throws Exception {
		Transaction t = saveFor(a, 1200, "昼食");

		String body = page(get("/transactions/" + t.getId() + "/edit"), asA);

		assertThat(count(body, "data-split-line")).isEqualTo(1);
		assertThat(body).contains("name=\"" + line(0, "amount") + "\" value=\"1200\"").doesNotContain("lines[1]", "data-split-add");
	}

	/** 編集で 2 行以上を送ると 400。取引は変わらない */
	@Test
	void editingWithTwoLinesIsABadRequestAndChangesNothing() throws Exception {
		Transaction t = saveFor(a, 1200, "昼食");

		mockMvc.perform(common(post("/transactions/" + t.getId() + "/edit"), a, receipt(a)).with(asA))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("error/4xx"));

		List<Transaction> after = transactionsOf(a);
		assertThat(after).hasSize(1);
		assertThat(after.get(0).getAmount()).isEqualTo(1200);
		assertThat(after.get(0).getDescription()).isEqualTo("昼食");
	}

	/** 他人の取引の ID なら、2 行を送っても、先に 404（ID と user_id で先に探す。設計書 7.8） */
	@Test
	void editingAnotherUsersTransactionWithTwoLinesIsStillNotFound() throws Exception {
		Transaction mine = saveFor(a, 1200, "昼食");

		mockMvc.perform(common(post("/transactions/" + mine.getId() + "/edit"), b, receipt(b)).with(asB))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(transactionsOf(a).get(0).getAmount()).isEqualTo(1200);
	}

	/** 編集で明細を空にすると、入力エラー（1 行以上）。変わらない。出し直した画面も 1 行 */
	@Test
	void editingWithAnEmptyLineIsAnErrorAndShowsOneLine() throws Exception {
		Transaction t = saveFor(a, 1200, "昼食");

		String body = page(common(post("/transactions/" + t.getId() + "/edit"), a,
				line(0, "categoryId"), "", line(0, "amount"), "", line(0, "description"), ""), asA);

		assertThat(body).contains("明細を 1 行以上入力してください");
		assertThat(count(body, "data-split-line")).isEqualTo(1);
		assertThat(transactionsOf(a).get(0).getAmount()).isEqualTo(1200);
	}

}
