package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.dto.CategoryActual;
import io.github.yutaro081.householdbudget.dto.DailyActual;
import io.github.yutaro081.householdbudget.dto.MonthlyActual;
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
 * 同じレシートの取引（issue 032 の段階 3。設計書 2.2、2.3、3.6、3.9、5.16、7.8）。手順書 3.3 の T3-1〜T3-13 の画面・処理の確かめ。
 * <p>
 * 印（receipt_group）は、このクラスでは SQL で直接読み書きする（Entity の形に頼らない）。準備で印を付けるときも SQL で付けるので、
 * 編集・削除・複写・一覧のテストは、登録の作りに頼らない。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストの準備（ApplicationContext）を増やさないため、自分の @TestConfiguration は作らない（段階 0）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionReceiptTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

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

	private record Owner(Long userId, String username, Category food, Category daily, Vendor vendor, Vendor vendor2,
			PaymentMethod payment, PaymentMethod payment2) {
	}

	/** DB の 1 行（SQL で読んだもの） */
	private record Row(Long id, LocalDate date, int amount, Long categoryId, String description, Long vendorId,
			Long paymentMethodId, Long group) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@BeforeEach
	void createUsers() {
		a = createOwner("rcptx1");
		b = createOwner("rcptx2");
		asA = user(new LoginUser(a.userId(), a.username(), "x"));
		asB = user(new LoginUser(b.userId(), b.username(), "x"));
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, username,
				categoryRepository.save(new Category(id, "食費")), categoryRepository.save(new Category(id, "日用品")),
				vendorRepository.save(new Vendor(id, "スーパー")), vendorRepository.save(new Vendor(id, "薬局")),
				paymentMethodRepository.save(new PaymentMethod(id, "現金")),
				paymentMethodRepository.save(new PaymentMethod(id, "カード")));
	}

	// ---------------------------------------------------------------
	// 準備と読み出し（SQL）
	// ---------------------------------------------------------------

	/** 印のない 1 行を保存する（段階 2 で保存した行と同じ形） */
	private Long saveRow(Owner o, LocalDate date, Category category, int amount, String description, Vendor vendor) {
		Long id = transactionRepository.save(new Transaction(o.userId(), date, amount, category, description, vendor, o.payment())).getId();
		entityManager.flush();
		entityManager.clear();
		return id;
	}

	/** 同じレシートの行を保存し、SQL で印（最初の行の id）を付ける。行の科目は、食費・日用品・食費…の順。摘要は prefix-1, prefix-2… */
	private List<Long> saveReceipt(Owner o, LocalDate date, Vendor vendor, String prefix, int... amounts) {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < amounts.length; i++) {
			ids.add(saveRow(o, date, i % 2 == 0 ? o.food() : o.daily(), amounts[i], prefix + "-" + (i + 1), vendor));
		}
		setGroup(ids.get(0), ids);
		return ids;
	}

	private void setGroup(Long group, List<Long> ids) {
		entityManager.flush();
		for (Long id : ids) {
			entityManager.createNativeQuery("UPDATE transactions SET receipt_group = ?1 WHERE id = ?2")
					.setParameter(1, group).setParameter(2, id).executeUpdate();
		}
		entityManager.clear();
	}

	/** その人の行を、id の順に（SQL で読む） */
	private List<Row> rowsOf(Owner o) {
		entityManager.flush();
		entityManager.clear();
		@SuppressWarnings("unchecked")
		List<Object[]> result = entityManager.createNativeQuery("""
				SELECT id, transaction_date, amount, category_id, description, vendor_id, payment_method_id, receipt_group
				FROM transactions WHERE user_id = ?1 ORDER BY id""").setParameter(1, o.userId()).getResultList();
		List<Row> rows = new ArrayList<>();
		for (Object[] r : result) {
			rows.add(new Row(number(r[0]), date(r[1]), number(r[2]).intValue(), number(r[3]), (String) r[4], number(r[5]),
					number(r[6]), number(r[7])));
		}
		return rows;
	}

	private static Long number(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}

	private static LocalDate date(Object value) {
		return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
	}

	private static List<Long> ids(List<Row> rows) {
		return rows.stream().map(Row::id).toList();
	}

	private static String line(int index, String field) {
		return "lines[" + index + "]." + field;
	}

	/** 共通の項目（日付・取引先・支払方法）と、明細の行の POST。values で足す・差し替える */
	private MockHttpServletRequestBuilder form(MockHttpServletRequestBuilder builder, Owner o, String... values) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("transactionDate", DAY.toString());
		params.put("vendorId", String.valueOf(o.vendor().getId()));
		params.put("paymentMethodId", String.valueOf(o.payment().getId()));
		for (int i = 0; i < values.length; i += 2) {
			params.put(values[i], values[i + 1]);
		}
		builder.with(csrf());
		params.forEach(builder::param);
		return builder;
	}

	/** 明細の行（科目, 金額, 摘要 の組を、上から） */
	private static String[] lines(Object... triples) {
		List<String> values = new ArrayList<>();
		for (int i = 0; i < triples.length; i += 3) {
			int index = i / 3;
			values.addAll(List.of(line(index, "categoryId"), String.valueOf(((Category) triples[i]).getId()),
					line(index, "amount"), String.valueOf(triples[i + 1]), line(index, "description"), String.valueOf(triples[i + 2])));
		}
		return values.toArray(String[]::new);
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

	// ---------------------------------------------------------------
	// T3-1 登録の印
	// ---------------------------------------------------------------

	/** 2 行以上で登録すると、全部の行に同じ印。印は最初の行の id */
	@Test
	void twoOrMoreLinesShareTheFirstLinesIdAsTheMark() throws Exception {
		mockMvc.perform(form(post("/transactions/new"), a, lines(a.food(), 2200, "お弁当", a.daily(), 800, "洗剤", a.food(), 300, "パン"))
				.with(asA)).andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).hasSize(3);
		assertThat(rows).extracting(Row::group).containsOnly(rows.get(0).id());
	}

	/** 1 行で登録した取引には、印を付けない */
	@Test
	void oneLineHasNoMark() throws Exception {
		mockMvc.perform(form(post("/transactions/new"), a, lines(a.food(), 1200, "昼食")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(rowsOf(a)).singleElement().extracting(Row::group).isNull();
	}

	/** レシートごとに、別の印になる（自分のほかのレシート、他人のレシートと、同じ値にならない） */
	@Test
	void eachReceiptGetsItsOwnMark() throws Exception {
		mockMvc.perform(form(post("/transactions/new"), a, lines(a.food(), 100, "", a.daily(), 200, "")).with(asA));
		mockMvc.perform(form(post("/transactions/new"), b, lines(b.food(), 300, "", b.daily(), 400, "")).with(asB));
		mockMvc.perform(form(post("/transactions/new"), a, lines(a.food(), 500, "", a.daily(), 600, "")).with(asA));

		List<Row> mine = rowsOf(a);
		List<Row> others = rowsOf(b);
		assertThat(mine).hasSize(4);
		assertThat(mine.get(0).group()).isEqualTo(mine.get(0).id()).isEqualTo(mine.get(1).group());
		assertThat(mine.get(2).group()).isEqualTo(mine.get(2).id()).isEqualTo(mine.get(3).group());
		assertThat(others.get(0).group()).isEqualTo(others.get(0).id());
		assertThat(List.of(mine.get(0).group(), mine.get(2).group(), others.get(0).group())).doesNotHaveDuplicates();
	}

	// ---------------------------------------------------------------
	// T3-2 印は画面から受け取らない
	// ---------------------------------------------------------------

	/** 登録で印の名前の値（他人のレシートの印）を送っても、使わない。他人のレシートは変わらない */
	@ParameterizedTest(name = "登録で {0} を送っても無視される")
	@ValueSource(strings = { "receiptGroup", "receipt_group", "lines[0].receiptGroup", "lines[1].receiptGroup" })
	void aMarkSentOnRegistrationIsIgnored(String name) throws Exception {
		List<Long> others = saveReceipt(b, DAY, b.vendor(), "他人", 300, 400);

		mockMvc.perform(form(post("/transactions/new"), a, join(lines(a.food(), 100, "", a.daily(), 200, ""), name,
				String.valueOf(others.get(0)))).with(asA)).andExpect(redirectedUrl("/transactions"));

		List<Row> mine = rowsOf(a);
		assertThat(mine).extracting(Row::group).containsOnly(mine.get(0).id());
		assertThat(rowsOf(b)).extracting(Row::group).containsOnly(others.get(0));
		assertThat(ids(rowsOf(b))).isEqualTo(others);
	}

	/** 1 行の登録で印を送っても、印は付かない */
	@ParameterizedTest(name = "1 行の登録で {0} を送っても印なし")
	@ValueSource(strings = { "receiptGroup", "receipt_group", "lines[0].receiptGroup" })
	void aMarkSentWithOneLineIsIgnored(String name) throws Exception {
		List<Long> others = saveReceipt(b, DAY, b.vendor(), "他人", 300, 400);

		mockMvc.perform(form(post("/transactions/new"), a, join(lines(a.food(), 100, ""), name, String.valueOf(others.get(0))))
				.with(asA)).andExpect(redirectedUrl("/transactions"));

		assertThat(rowsOf(a)).singleElement().extracting(Row::group).isNull();
	}

	/** 編集で印を送っても、使わない（自分のレシートの印のまま。他人のレシートに入らない） */
	@ParameterizedTest(name = "編集で {0} を送っても無視される")
	@ValueSource(strings = { "receiptGroup", "receipt_group", "lines[0].receiptGroup" })
	void aMarkSentOnEditIsIgnored(String name) throws Exception {
		List<Long> mine = saveReceipt(a, DAY, a.vendor(), "自分", 100, 200);
		List<Long> others = saveReceipt(b, DAY, b.vendor(), "他人", 300, 400);

		mockMvc.perform(form(post("/transactions/" + mine.get(1) + "/edit"), a,
				join(lines(a.food(), 110, "", a.daily(), 220, ""), name, String.valueOf(others.get(0)))).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(rowsOf(a)).extracting(Row::group).containsOnly(mine.get(0));
		assertThat(rowsOf(b)).extracting(Row::group).containsOnly(others.get(0));
	}

	// ---------------------------------------------------------------
	// T3-3 入口は、レシートのどの行でも
	// ---------------------------------------------------------------

	/** 編集の画面は、どの行から開いても、レシートの全部の行が登録の順に入る。残りは空の行（全部で 5 行） */
	@ParameterizedTest(name = "{0} 行目から編集の画面を開く")
	@ValueSource(ints = { 0, 1, 2 })
	void theEditScreenFromAnyRowShowsTheWholeReceiptInOrder(int entry) throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200, 300);

		String body = page(get("/transactions/" + ids.get(entry) + "/edit"), asA);

		assertThat(body).contains("action=\"/transactions/" + ids.get(entry) + "/edit\"");
		for (int i = 0; i < 3; i++) {
			assertThat(body).contains("name=\"" + line(i, "amount") + "\" value=\"" + (100 * (i + 1)) + "\"")
					.contains("name=\"" + line(i, "description") + "\" value=\"R-" + (i + 1) + "\"");
		}
		assertThat(body).contains("name=\"" + line(3, "amount") + "\" value=\"\"", "name=\"" + line(4, "amount") + "\" value=\"\"");
		assertThat(count(body, "data-split-line")).isEqualTo(5);
	}

	/** 削除は、どの行から押しても、レシートの全部が消える。自分のほかの取引・他人の取引は残る */
	@ParameterizedTest(name = "{0} 行目から削除する")
	@ValueSource(ints = { 0, 1, 2 })
	void deletingFromAnyRowDeletesTheWholeReceiptOnly(int entry) throws Exception {
		List<Long> receipt = saveReceipt(a, DAY, a.vendor(), "R", 100, 200, 300);
		List<Long> another = saveReceipt(a, DAY, a.vendor(), "S", 400, 500);
		Long single = saveRow(a, DAY, a.food(), 600, "単独", a.vendor());
		List<Long> others = saveReceipt(b, DAY, b.vendor(), "他人", 700, 800);

		mockMvc.perform(post("/transactions/" + receipt.get(entry) + "/delete").with(csrf()).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Long> left = new ArrayList<>(another);
		left.add(single);
		assertThat(ids(rowsOf(a))).isEqualTo(left);
		assertThat(ids(rowsOf(b))).isEqualTo(others);
	}

	/** 複写は、どの行から押しても、レシートの全部の行が入る。日付は今日（元の日付ではない）。開いただけでは、何も増えない */
	@ParameterizedTest(name = "{0} 行目から複写する")
	@ValueSource(ints = { 0, 1, 2 })
	void copyingFromAnyRowPutsTheWholeReceiptAndSavesNothing(int entry) throws Exception {
		LocalDate old = LocalDate.of(2025, 1, 15);
		List<Long> ids = saveReceipt(a, old, a.vendor(), "R", 100, 200, 300);

		String body = page(get("/transactions/new").param("copyFrom", String.valueOf(ids.get(entry))), asA);

		for (int i = 0; i < 3; i++) {
			assertThat(body).contains("name=\"" + line(i, "amount") + "\" value=\"" + (100 * (i + 1)) + "\"");
		}
		assertThat(body).contains("name=\"" + line(3, "amount") + "\" value=\"\"").doesNotContain("value=\"" + old + "\"");
		assertThat(body).contains("action=\"/transactions/new\"");
		assertThat(rowsOf(a)).hasSize(3);
	}

	/** 他人のレシートの行の ID への更新は、送った明細の行の数（1・2・5 行）にかかわらず 404。他人のレシートは変わらない */
	@ParameterizedTest(name = "他人のレシートの行への更新（明細 {0} 行）→ 404")
	@ValueSource(ints = { 1, 2, 5 })
	void updatingAnotherUsersReceiptRowIsNotFoundWhateverTheLineCount(int count) throws Exception {
		List<Long> others = saveReceipt(b, DAY, b.vendor(), "他人", 300, 400);
		List<Row> before = rowsOf(b);
		Object[] triples = new Object[count * 3];
		for (int i = 0; i < count; i++) {
			triples[i * 3] = a.food();
			triples[i * 3 + 1] = 9999;
			triples[i * 3 + 2] = "書き換え";
		}

		mockMvc.perform(form(post("/transactions/" + others.get(1) + "/edit"), a, lines(triples)).with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(rowsOf(b)).isEqualTo(before);
		assertThat(rowsOf(a)).isEmpty();
	}

	/** 他人のレシートの行は、編集の画面・削除・複写も 404。他人のレシートは変わらない */
	@Test
	void openingDeletingOrCopyingAnotherUsersReceiptRowIsNotFound() throws Exception {
		List<Long> others = saveReceipt(b, DAY, b.vendor(), "他人", 300, 400);
		List<Row> before = rowsOf(b);

		for (Long id : others) {
			mockMvc.perform(get("/transactions/" + id + "/edit").with(asA)).andExpect(status().isNotFound());
			mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(id)).with(asA)).andExpect(status().isNotFound());
			mockMvc.perform(post("/transactions/" + id + "/delete").with(csrf()).with(asA)).andExpect(status().isNotFound());
		}
		assertThat(rowsOf(b)).isEqualTo(before);
	}

	// ---------------------------------------------------------------
	// T3-4 編集で行を足す・消す・直す
	// ---------------------------------------------------------------

	/** 2 行 → 3 行（2 行目から編集）：残る 2 行の id は変わらず、値が変わる。足した行にも同じ印 */
	@Test
	void growingKeepsTheIdsAndMarksTheNewLine() throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200);

		mockMvc.perform(form(post("/transactions/" + ids.get(1) + "/edit"), a,
				lines(a.food(), 110, "一", a.daily(), 220, "二", a.food(), 330, "三")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).hasSize(3);
		assertThat(rows.subList(0, 2)).extracting(Row::id).isEqualTo(ids);
		assertThat(rows).extracting(Row::amount).containsExactly(110, 220, 330);
		assertThat(rows).extracting(Row::description).containsExactly("一", "二", "三");
		assertThat(rows).extracting(Row::group).containsOnly(ids.get(0));
	}

	/** 3 行 → 1 行：いちばん小さい id の行だけが残り、印を外す */
	@Test
	void shrinkingToOneLineKeepsTheSmallestIdAndRemovesTheMark() throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200, 300);

		mockMvc.perform(form(post("/transactions/" + ids.get(2) + "/edit"), a, lines(a.daily(), 999, "ひとつ")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).singleElement().satisfies(row -> {
			assertThat(row.id()).isEqualTo(ids.get(0));
			assertThat(row.amount()).isEqualTo(999);
			assertThat(row.categoryId()).isEqualTo(a.daily().getId());
			assertThat(row.group()).isNull();
		});
	}

	/** 印のない 1 行 → 2 行：残る行の id が印になり、足した行にも同じ印 */
	@Test
	void growingFromOneLineMarksWithTheRemainingId() throws Exception {
		Long single = saveRow(a, DAY, a.food(), 100, "一", a.vendor());

		mockMvc.perform(form(post("/transactions/" + single + "/edit"), a, lines(a.food(), 100, "一", a.daily(), 200, "二")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).id()).isEqualTo(single);
		assertThat(rows).extracting(Row::group).containsOnly(single);
	}

	/** 3 行 → 2 行：id がいちばん大きい行が消える。残る 2 行は直した値。印は最初の行の id のまま */
	@Test
	void threeToTwoDeletesTheLargestIdAndFixesTheOthers() throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200, 300);

		mockMvc.perform(form(post("/transactions/" + ids.get(0) + "/edit"), a, lines(a.daily(), 150, "直す1", a.daily(), 250, "直す2")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(ids(rows)).isEqualTo(ids.subList(0, 2));
		assertThat(rows).extracting(Row::amount).containsExactly(150, 250);
		assertThat(rows).extracting(Row::categoryId).containsOnly(a.daily().getId());
		assertThat(rows).extracting(Row::group).containsOnly(ids.get(0));
	}

	/** 明細 1 を空にすると、空の行は無視され、明細 2 の内容が、いちばん小さい id の行に入る（明細 1 には「行を消す」がないため） */
	@Test
	void clearingTheFirstLineMovesTheSecondLineIntoTheSmallestId() throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200);

		mockMvc.perform(form(post("/transactions/" + ids.get(0) + "/edit"), a,
				line(0, "categoryId"), "", line(0, "amount"), "", line(0, "description"), "",
				line(1, "categoryId"), String.valueOf(a.daily().getId()), line(1, "amount"), "800", line(1, "description"), "残す").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(rowsOf(a)).singleElement().satisfies(row -> {
			assertThat(row.id()).isEqualTo(ids.get(0));
			assertThat(row.amount()).isEqualTo(800);
			assertThat(row.description()).isEqualTo("残す");
			assertThat(row.group()).isNull();
		});
	}

	// ---------------------------------------------------------------
	// T3-5 共通の項目
	// ---------------------------------------------------------------

	/** 編集で日付・取引先・支払方法を変えると、レシートの全部の行が変わる（どの行から編集しても） */
	@ParameterizedTest(name = "{0} 行目から、日付・取引先・支払方法を変える")
	@ValueSource(ints = { 0, 2 })
	void changingDateVendorAndPaymentChangesEveryRowOfTheReceipt(int entry) throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200, 300);
		LocalDate newDay = LocalDate.of(2026, 11, 12);

		mockMvc.perform(form(post("/transactions/" + ids.get(entry) + "/edit"), a,
				join(lines(a.food(), 100, "R-1", a.daily(), 200, "R-2", a.food(), 300, "R-3"),
						"transactionDate", newDay.toString(), "vendorId", String.valueOf(a.vendor2().getId()),
						"paymentMethodId", String.valueOf(a.payment2().getId()))).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).hasSize(3);
		assertThat(rows).extracting(Row::date).containsOnly(newDay);
		assertThat(rows).extracting(Row::vendorId).containsOnly(a.vendor2().getId());
		assertThat(rows).extracting(Row::paymentMethodId).containsOnly(a.payment2().getId());
	}

	// ---------------------------------------------------------------
	// T3-6 他人の科目・取引先が 1 つでもあれば、何も変わらない
	// ---------------------------------------------------------------

	/** 編集で、2 行目の科目が他人のもの → 404。レシートは、行の数・値・印のどれも変わらない */
	@Test
	void anotherUsersCategoryOnTheSecondLineOfAnEditChangesNothing() throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200);
		List<Row> before = rowsOf(a);

		mockMvc.perform(form(post("/transactions/" + ids.get(0) + "/edit"), a,
				lines(a.food(), 111, "書き換え", b.daily(), 222, "他人の科目", a.food(), 333, "足す")).with(asA))
				.andExpect(status().isNotFound());

		assertThat(rowsOf(a)).isEqualTo(before);
	}

	/** 編集で、取引先・支払方法が他人のもの → 404。何も変わらない */
	@ParameterizedTest(name = "編集で {0} が他人のもの → 404")
	@ValueSource(strings = { "vendorId", "paymentMethodId" })
	void anotherUsersVendorOrPaymentOnAnEditChangesNothing(String field) throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200);
		List<Row> before = rowsOf(a);
		String other = "vendorId".equals(field) ? String.valueOf(b.vendor().getId()) : String.valueOf(b.payment().getId());

		mockMvc.perform(form(post("/transactions/" + ids.get(1) + "/edit"), a,
				join(lines(a.food(), 111, "", a.daily(), 222, ""), field, other)).with(asA))
				.andExpect(status().isNotFound());

		assertThat(rowsOf(a)).isEqualTo(before);
	}

	/** 登録で、3 行目の科目が他人のもの → 404。1 件も保存しない（印も付かない） */
	@Test
	void anotherUsersCategoryOnTheThirdLineOfARegistrationSavesNothing() throws Exception {
		mockMvc.perform(form(post("/transactions/new"), a, lines(a.food(), 100, "", a.daily(), 200, "", b.food(), 300, "")).with(asA))
				.andExpect(status().isNotFound());

		assertThat(rowsOf(a)).isEmpty();
	}

	// ---------------------------------------------------------------
	// T3-7 一覧の並びと印
	// ---------------------------------------------------------------

	/** 一覧の行（摘要の文字で探す）の、上からの順 */
	private static List<String> orderOf(String body, String... descriptions) {
		List<int[]> found = new ArrayList<>();
		for (int i = 0; i < descriptions.length; i++) {
			int at = body.indexOf(">" + descriptions[i] + "<");
			assertThat(at).as(descriptions[i] + " が一覧にある").isPositive();
			found.add(new int[] { at, i });
		}
		found.sort((x, y) -> Integer.compare(x[0], y[0]));
		return found.stream().map(f -> descriptions[f[1]]).toList();
	}

	/**
	 * 同じ日の中で、レシートの行は続けて並び、その中は id の順（明細の行の順）。レシートの順は、印（なければ id）の新しい順。
	 * あとから足した行（id がいちばん大きい）も、レシートの隣に並ぶ。ほかの取引が、レシートの行の間にはさまらない
	 */
	@Test
	void theListPutsReceiptRowsTogetherInLineOrder() throws Exception {
		Long s1 = saveRow(a, DAY, a.food(), 100, "単独1", a.vendor());
		Long r1 = saveRow(a, DAY, a.food(), 200, "レシート1", a.vendor());
		Long r2 = saveRow(a, DAY, a.daily(), 300, "レシート2", a.vendor());
		Long s2 = saveRow(a, DAY, a.food(), 400, "単独2", a.vendor());
		Long r3 = saveRow(a, DAY, a.daily(), 500, "レシート3", a.vendor());
		setGroup(r1, List.of(r1, r2, r3));
		assertThat(List.of(s1, r1, r2, s2, r3)).isSorted();

		String body = page(get("/transactions"), asA);

		assertThat(orderOf(body, "単独1", "レシート1", "レシート2", "単独2", "レシート3"))
				.containsExactly("単独2", "レシート1", "レシート2", "レシート3", "単独1");
	}

	/** 日付が違えば、日付の新しい順が先（レシートでも） */
	@Test
	void theDateStillComesFirst() throws Exception {
		saveReceipt(a, LocalDate.of(2026, 11, 1), a.vendor(), "古い", 100, 200);
		saveRow(a, LocalDate.of(2026, 11, 5), a.food(), 300, "新しい", a.vendor());

		String body = page(get("/transactions"), asA);

		assertThat(orderOf(body, "古い-1", "古い-2", "新しい")).containsExactly("新しい", "古い-1", "古い-2");
	}

	/** その行（摘要の文字で探す）の HTML */
	private static String rowOf(String body, String description) {
		Matcher m = Pattern.compile("(?s)<tr[^>]*>(?:(?!</tr>).)*?>" + Pattern.quote(description) + "<(?:(?!</tr>).)*</tr>").matcher(body);
		assertThat(m.find()).as(description + " の行").isTrue();
		return m.group();
	}

	/** レシートの行には印「分割」があり、印のない行にはない */
	@Test
	void onlyReceiptRowsHaveTheMark() throws Exception {
		saveReceipt(a, DAY, a.vendor(), "R", 100, 200);
		saveRow(a, DAY, a.food(), 300, "単独", a.vendor());

		String body = page(get("/transactions"), asA);

		assertThat(rowOf(body, "R-1")).contains("split-mark").contains(">分割<");
		assertThat(rowOf(body, "R-2")).contains("split-mark").contains(">分割<");
		assertThat(rowOf(body, "単独")).doesNotContain("split-mark").doesNotContain("分割");
		assertThat(count(body, "split-mark")).isEqualTo(2);
	}

	// ---------------------------------------------------------------
	// T3-8 削除の確認の文
	// ---------------------------------------------------------------

	private static String unescape(String attribute) {
		return attribute.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
	}

	/** その行の削除のフォームの、確認の文 */
	private static String confirmOf(String row) {
		Matcher m = Pattern.compile("<form[^>]*\\bdata-confirm=\"([^\"]*)\"[^>]*>").matcher(row);
		assertThat(m.find()).as("data-confirm がある").isTrue();
		return unescape(m.group(1));
	}

	/** レシートの行は、どの行も「日付 取引先名 合計（行の数）」の文 */
	@Test
	void receiptRowsAskToDeleteTheWholeReceipt() throws Exception {
		saveReceipt(a, DAY, a.vendor(), "R", 2200, 800);

		String body = page(get("/transactions"), asA);

		for (String description : List.of("R-1", "R-2")) {
			assertThat(confirmOf(rowOf(body, description))).isEqualTo("2026/11/09 スーパー 3,000円（2 行）を削除します。よろしいですか？");
		}
	}

	/** 印のない行は、今の文のまま（日付 科目名 金額） */
	@Test
	void singleRowsKeepTheOldMessage() throws Exception {
		saveRow(a, DAY, a.daily(), 1500, "単独", a.vendor());

		String body = page(get("/transactions"), asA);

		assertThat(confirmOf(rowOf(body, "単独"))).isEqualTo("2026/11/09 日用品 1,500円 を削除します。よろしいですか？");
	}

	/** 取引先名の記号で、属性や文が壊れない */
	@Test
	void symbolsInTheVendorNameDoNotBreakTheMessage() throws Exception {
		Vendor odd = vendorRepository.save(new Vendor(a.userId(), "a'b\"c<d>&"));
		saveReceipt(a, DAY, odd, "R", 1000, 234);

		String body = page(get("/transactions"), asA);

		String row = rowOf(body, "R-1");
		assertThat(confirmOf(row)).isEqualTo("2026/11/09 a'b\"c<d>& 1,234円（2 行）を削除します。よろしいですか？");
		assertThat(row).containsPattern("data-confirm=\"[^\"<>]*\"");
	}

	// ---------------------------------------------------------------
	// T3-9 印のない行（段階 2 で保存した行）
	// ---------------------------------------------------------------

	/** 同じ日・同じ取引先・同じ支払方法でも、印のない行は 1 件ずつ：編集の画面は 1 行、削除は 1 件、複写は 1 行 */
	@Test
	void unmarkedRowsAreStillHandledOneByOne() throws Exception {
		Long first = saveRow(a, DAY, a.food(), 2200, "印なし1", a.vendor());
		Long second = saveRow(a, DAY, a.daily(), 800, "印なし2", a.vendor());

		String edit = page(get("/transactions/" + first + "/edit"), asA);
		assertThat(edit).contains("value=\"印なし1\"").doesNotContain("印なし2");
		assertThat(edit).contains("name=\"" + line(1, "amount") + "\" value=\"\"");

		String copy = page(get("/transactions/new").param("copyFrom", String.valueOf(first)), asA);
		assertThat(copy).contains("value=\"印なし1\"").doesNotContain("印なし2");

		mockMvc.perform(post("/transactions/" + first + "/delete").with(csrf()).with(asA)).andExpect(redirectedUrl("/transactions"));
		assertThat(ids(rowsOf(a))).containsExactly(second);
	}

	/** 印のない行の編集で行を足すと、その行と足した行にだけ印が付く。同じ日のほかの印のない行は変わらない */
	@Test
	void addingALineToAnUnmarkedRowMarksOnlyThoseRows() throws Exception {
		Long first = saveRow(a, DAY, a.food(), 2200, "印なし1", a.vendor());
		Long second = saveRow(a, DAY, a.daily(), 800, "印なし2", a.vendor());

		mockMvc.perform(form(post("/transactions/" + first + "/edit"), a, lines(a.food(), 2200, "印なし1", a.food(), 100, "足した")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).hasSize(3);
		assertThat(rows).filteredOn(row -> row.id().equals(second)).singleElement().extracting(Row::group).isNull();
		assertThat(rows).filteredOn(row -> !row.id().equals(second)).extracting(Row::group).containsOnly(first);
	}

	// ---------------------------------------------------------------
	// T3-11（画面から）まとまりは「印 と user_id」で読む
	// ---------------------------------------------------------------

	/**
	 * 他人の行に、自分のレシートと同じ印の値が入っていても（不具合や、直接の書き換えを想定。準備で SQL で入れる）、
	 * 編集の画面・複写・更新・削除で、他人の行を読まない・変えない・消さない
	 */
	@Test
	void anotherUsersRowWithTheSameMarkValueIsNeverRead() throws Exception {
		List<Long> mine = saveReceipt(a, DAY, a.vendor(), "自分", 100, 200);
		Long theirs = saveRow(b, DAY, b.food(), 999, "他人の行", b.vendor());
		setGroup(mine.get(0), List.of(theirs));
		List<Row> before = rowsOf(b);

		assertThat(page(get("/transactions/" + mine.get(1) + "/edit"), asA)).doesNotContain("他人の行", "value=\"999\"");
		assertThat(page(get("/transactions/new").param("copyFrom", String.valueOf(mine.get(0))), asA)).doesNotContain("他人の行");

		mockMvc.perform(form(post("/transactions/" + mine.get(0) + "/edit"), a, lines(a.food(), 110, "直す", a.daily(), 220, "直す")).with(asA))
				.andExpect(redirectedUrl("/transactions"));
		assertThat(rowsOf(b)).isEqualTo(before);
		assertThat(ids(rowsOf(a))).isEqualTo(mine);

		mockMvc.perform(post("/transactions/" + mine.get(0) + "/delete").with(csrf()).with(asA)).andExpect(redirectedUrl("/transactions"));
		assertThat(rowsOf(a)).isEmpty();
		assertThat(rowsOf(b)).isEqualTo(before);
	}

	// ---------------------------------------------------------------
	// T3-12 集計（印のために、同じ行を 2 回数えない）
	// ---------------------------------------------------------------

	/** 分けて登録した取引は、予実比較・自動予算・トップの集計で、行ごとに科目へ 1 回ずつ入る */
	@Test
	void aSplitReceiptIsSummedOncePerLineByCategory() throws Exception {
		mockMvc.perform(form(post("/transactions/new"), a, lines(a.food(), 2200, "", a.daily(), 800, "")).with(asA))
				.andExpect(redirectedUrl("/transactions"));
		saveRow(a, DAY, a.food(), 1000, "単独", a.vendor());
		LocalDate start = LocalDate.of(2026, 9, 1);
		LocalDate end = LocalDate.of(2026, 12, 1);

		assertThat(transactionRepository.sumByCategory(a.userId(), start, end))
				.extracting(CategoryActual::categoryId, CategoryActual::total)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(a.food().getId(), 3200L),
						org.assertj.core.groups.Tuple.tuple(a.daily().getId(), 800L));
		assertThat(transactionRepository.sumByCategoryAndMonth(a.userId(), start, end))
				.extracting(MonthlyActual::categoryId, MonthlyActual::total)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(a.food().getId(), 3200L),
						org.assertj.core.groups.Tuple.tuple(a.daily().getId(), 800L));
		assertThat(transactionRepository.sumByCategoryAndDate(a.userId(), start, end))
				.extracting(DailyActual::categoryId, DailyActual::total, DailyActual::count)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(a.food().getId(), 3200L, 2L),
						org.assertj.core.groups.Tuple.tuple(a.daily().getId(), 800L, 1L));
	}

	// ---------------------------------------------------------------
	// T3-13 行の数の上限は、編集でも 5 行
	// ---------------------------------------------------------------

	/** 編集でも 5 行まで受け付ける（5 行 → 取引 5 件、全部に同じ印） */
	@Test
	void anEditAcceptsFiveLines() throws Exception {
		Long single = saveRow(a, DAY, a.food(), 100, "一", a.vendor());

		mockMvc.perform(form(post("/transactions/" + single + "/edit"), a,
				lines(a.food(), 1, "", a.daily(), 2, "", a.food(), 3, "", a.daily(), 4, "", a.food(), 5, "")).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		List<Row> rows = rowsOf(a);
		assertThat(rows).extracting(Row::amount).containsExactly(1, 2, 3, 4, 5);
		assertThat(rows).extracting(Row::group).containsOnly(single);
	}

	/** 編集で 6 行目（lines[5]）を送ると 400。何も変わらない */
	@Test
	void anEditWithASixthLineIsABadRequest() throws Exception {
		List<Long> ids = saveReceipt(a, DAY, a.vendor(), "R", 100, 200);
		List<Row> before = rowsOf(a);

		mockMvc.perform(form(post("/transactions/" + ids.get(0) + "/edit"), a,
				join(lines(a.food(), 1, "", a.daily(), 2, ""), line(5, "amount"), "6")).with(asA))
				.andExpect(status().isBadRequest())
				.andExpect(view().name("error/4xx"));

		assertThat(rowsOf(a)).isEqualTo(before);
	}

}
