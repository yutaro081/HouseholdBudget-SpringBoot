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

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
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
 * TransactionController（取引の画面）のテスト（設計書 2.2、2.3、2.6、3.9、6.5〜6.7、7.8）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は、日本時間の 2026-10-27 に固定した Clock で確かめる。テストのパスワードのハッシュは、ダミーの値。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerTest {

	/** 日本時間の 2026-10-27 に固定した Clock（登録画面の日付の初期値、U7） */
	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2026, 10, 27).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

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

	/** 1人分のユーザーと、科目・取引先・支払方法を、それぞれ2つずつ（登録順） */
	private record Owner(Long userId, String username, Category category1, Category category2,
			Vendor vendor1, Vendor vendor2, PaymentMethod payment1, PaymentMethod payment2) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@BeforeEach
	void createUsers() {
		a = createOwner("ctrltx1");
		b = createOwner("ctrltx2");
		asA = user(new LoginUser(a.userId(), a.username(), "x"));
		asB = user(new LoginUser(b.userId(), b.username(), "x"));
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, username,
				categoryRepository.save(new Category(id, "食費")), categoryRepository.save(new Category(id, "交通費")),
				vendorRepository.save(new Vendor(id, "コンビニ")), vendorRepository.save(new Vendor(id, "Amazon")),
				paymentMethodRepository.save(new PaymentMethod(id, "現金")),
				paymentMethodRepository.save(new PaymentMethod(id, "カード")));
	}

	private Transaction saveFor(Owner o, LocalDate date, int amount, String description) {
		Transaction t = transactionRepository.save(new Transaction(o.userId(), date, amount,
				o.category1(), description, o.vendor1(), o.payment1()));
		entityManager.flush();
		entityManager.clear();
		return t;
	}

	private String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	private List<Transaction> transactionsOf(Owner o) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(o.userId());
	}

	/**
	 * 正しい入力の POST（登録・更新）。overrides に「項目名, 値, 項目名, 値, ...」を渡すと、その項目だけ差し替える。
	 * （MockMvc の param は同じ名前を置き換えず追加するため、差し替えはここで行う）
	 */
	private MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder builder, Owner o, String... overrides) {
		java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
		values.put("transactionDate", "2026-10-20");
		values.put("amount", "1200");
		values.put("categoryId", String.valueOf(o.category1().getId()));
		values.put("description", "昼食");
		values.put("vendorId", String.valueOf(o.vendor1().getId()));
		values.put("paymentMethodId", String.valueOf(o.payment1().getId()));
		for (int i = 0; i < overrides.length; i += 2) {
			values.put(overrides[i], overrides[i + 1]);
		}
		builder.with(csrf());
		values.forEach(builder::param);
		return builder;
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	// ---- ログイン必須・CSRF ----

	@ParameterizedTest(name = "未ログインで GET {0} は、ログイン画面へ移動する")
	@ValueSource(strings = { "/transactions", "/transactions/new", "/transactions/1", "/transactions/1/edit" })
	void notLoggedInIsRedirected(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "未ログインで POST {0} は、ログイン画面へ移動する（トークンがあっても）")
	@ValueSource(strings = { "/transactions/new", "/transactions/1/edit", "/transactions/1/delete" })
	void notLoggedInPostIsRedirected(String path) throws Exception {
		mockMvc.perform(post(path).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "トークンなしの POST {0} は 403")
	@ValueSource(strings = { "/transactions/new", "/transactions/1/edit", "/transactions/1/delete" })
	void postWithoutCsrfTokenIsForbidden(String path) throws Exception {
		mockMvc.perform(post(path).with(asA))
				.andExpect(status().isForbidden());
	}

	@Test
	void postWithoutCsrfTokenSavesNothing() throws Exception {
		mockMvc.perform(post("/transactions/new").with(asA)
				.param("transactionDate", "2026-10-20").param("amount", "1200")
				.param("categoryId", String.valueOf(a.category1().getId()))
				.param("vendorId", String.valueOf(a.vendor1().getId()))
				.param("paymentMethodId", String.valueOf(a.payment1().getId())))
				.andExpect(status().isForbidden());

		assertThat(transactionsOf(a)).isEmpty();
	}

	// ---- 一覧 ----

	@Test
	void listShowsOwnTransactionsNewestDateFirstAndNotOthers() throws Exception {
		saveFor(a, LocalDate.of(2026, 1, 5), 100, "一番古い");
		saveFor(a, LocalDate.of(2026, 12, 1), 300, "一番新しい");
		saveFor(a, LocalDate.of(2026, 10, 27), 200, "真ん中");
		saveFor(b, LocalDate.of(2026, 11, 1), 999, "他人の摘要");

		MvcResult result = mockMvc.perform(get("/transactions").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("一番古い", "真ん中", "一番新しい");
		assertThat(body).doesNotContain("他人の摘要");
		assertThat(body.indexOf("一番新しい")).isLessThan(body.indexOf("真ん中"));
		assertThat(body.indexOf("真ん中")).isLessThan(body.indexOf("一番古い"));
	}

	@Test
	void listShowsSameDayNewestRegisteredFirst() throws Exception {
		saveFor(a, LocalDate.of(2026, 10, 27), 100, "先に登録");
		saveFor(a, LocalDate.of(2026, 10, 27), 200, "あとに登録");

		String body = body(mockMvc.perform(get("/transactions").with(asA)).andReturn());

		assertThat(body.indexOf("あとに登録")).isLessThan(body.indexOf("先に登録"));
	}

	@Test
	void listShowsMessageWhenThereAreNone() throws Exception {
		String body = body(mockMvc.perform(get("/transactions").with(asA)).andReturn());

		assertThat(body).contains("取引はまだありません");
		assertThat(body).contains("href=\"/transactions/new\"");   // 「取引を登録」は0件のときも出る
	}

	@Test
	void listShowsFormattedDateAndAmountAndLinksButNoEditOrDelete() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 30000, null);

		String body = body(mockMvc.perform(get("/transactions").with(asA)).andReturn());

		assertThat(body).contains("2026/10/19", "30,000円", "食費", "コンビニ", "現金");
		assertThat(body).contains("href=\"/transactions/" + t.getId() + "\"");                    // 詳細リンク
		assertThat(body).contains("href=\"/transactions/new\"");                                  // 取引を登録
		assertThat(body).contains(a.username());                                                  // ヘッダーのユーザー名
		assertThat(body).doesNotContain("/delete").doesNotContain("/edit");                       // 一覧には置かない（U3）
		assertThat(body).doesNotContain("【仮】");                                                 // 仮の画面ではない
		assertThat(body).doesNotContain(">null<");                                                // 摘要がなくても null と出ない
	}

	@Test
	void listShowsMinusFreeAmountsWithCommas() throws Exception {
		saveFor(a, LocalDate.of(2026, 10, 27), 1_000_000, null);
		saveFor(a, LocalDate.of(2026, 10, 26), 1, null);

		String body = body(mockMvc.perform(get("/transactions").with(asA)).andReturn());

		assertThat(body).contains("1,000,000円", ">1円<");
	}

	@Test
	void listShowsSuccessMessage() throws Exception {
		for (String[] pair : new String[][] { { "success.registered", "登録しました" },
				{ "success.transactionUpdated", "更新しました" }, { "success.deleted", "削除しました" } }) {
			String body = body(mockMvc.perform(get("/transactions").with(asA).flashAttr("successMessageKey", pair[0])).andReturn());
			assertThat(body).contains(pair[1]);
		}
	}

	/** 利用者が入力した値は、無害化されて表示される（th:text、設計書 7.5） */
	@Test
	void valuesAreEscapedOnListDetailAndEdit() throws Exception {
		String script = "<script>alert(1)</script>";
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 27), 100, script);

		for (String path : List.of("/transactions", "/transactions/" + t.getId(), "/transactions/" + t.getId() + "/edit")) {
			String body = body(mockMvc.perform(get(path).with(asA)).andReturn());
			assertThat(body).as(path).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain(script);
		}
	}

	// ---- 登録の画面 ----

	@Test
	void newFormShowsTodayAsInitialDateAndOwnOptionsInOrder() throws Exception {
		MvcResult result = mockMvc.perform(get("/transactions/new").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("value=\"2026-10-27\"");                                         // 日付の初期値は今日（U7）
		assertThat(body).contains("action=\"/transactions/new\"");
		assertThat(body).contains("name=\"_csrf\"");
		assertThat(body).contains("選択してください");
		assertThat(body.indexOf("食費")).isLessThan(body.indexOf("交通費"));                          // 登録順
		assertThat(body.indexOf("コンビニ")).isLessThan(body.indexOf("Amazon"));
		assertThat(body.indexOf("現金")).isLessThan(body.indexOf("カード"));
		assertThat(body).contains("href=\"/transactions\"");                                      // 戻るリンク
		assertThat(body).doesNotContain("先に科目を追加してください");
	}

	@Test
	void newFormDoesNotShowOtherUsersOptions() throws Exception {
		categoryRepository.save(new Category(b.userId(), "他人の科目"));
		vendorRepository.save(new Vendor(b.userId(), "他人の取引先"));
		paymentMethodRepository.save(new PaymentMethod(b.userId(), "他人の支払"));

		String body = body(mockMvc.perform(get("/transactions/new").with(asA)).andReturn());

		assertThat(body).doesNotContain("他人の科目", "他人の取引先", "他人の支払");
	}

	/** /transactions/new が、ID「new」の詳細として読まれない */
	@Test
	void newIsNotTreatedAsAnId() throws Exception {
		mockMvc.perform(get("/transactions/new").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"));
	}

	/** B2：プルダウンが空のときは、足りない種類の説明とリンクを出し、フォームは出さない（Q5-7） */
	@Test
	void newFormWithNoMastersShowsGuidanceAndNoForm() throws Exception {
		Long id = userRepository.save(new User("ctrltx3", "dummy-hash-for-test")).getId();
		RequestPostProcessor asEmpty = user(new LoginUser(id, "ctrltx3", "x"));

		String body = body(mockMvc.perform(get("/transactions/new").with(asEmpty))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("先に科目を追加してください", "先に取引先を追加してください", "先に支払方法を追加してください");
		assertThat(body).contains("href=\"/categories\"", "href=\"/vendors\"", "href=\"/payment-methods\"");
		assertThat(body).doesNotContain("action=\"/transactions/new\"");
		assertThat(body).doesNotContain("name=\"amount\"");
	}

	@Test
	void newFormWithOnlySomeMastersMissingNamesOnlyTheMissingOnes() throws Exception {
		Long id = userRepository.save(new User("ctrltx4", "dummy-hash-for-test")).getId();
		categoryRepository.save(new Category(id, "食費"));
		RequestPostProcessor asPartial = user(new LoginUser(id, "ctrltx4", "x"));

		String body = body(mockMvc.perform(get("/transactions/new").with(asPartial)).andReturn());

		assertThat(body).doesNotContain("先に科目を追加してください");
		assertThat(body).contains("先に取引先を追加してください", "先に支払方法を追加してください");
		assertThat(body).doesNotContain("action=\"/transactions/new\"");
	}

	// ---- 登録 ----

	@Test
	void registerSucceedsAndRedirectsToList() throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a).with(asA))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/transactions"))
				.andExpect(flash().attribute("successMessageKey", "success.registered"));

		List<Transaction> list = transactionsOf(a);
		assertThat(list).hasSize(1);
		Transaction t = list.get(0);
		assertThat(t.getUserId()).isEqualTo(a.userId());
		assertThat(t.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(t.getAmount()).isEqualTo(1200);
		assertThat(t.getDescription()).isEqualTo("昼食");
		assertThat(t.getCategory().getId()).isEqualTo(a.category1().getId());
		assertThat(t.getVendor().getId()).isEqualTo(a.vendor1().getId());
		assertThat(t.getPaymentMethod().getId()).isEqualTo(a.payment1().getId());
	}

	/** 持ち主は、ログイン中のユーザー。画面から送られた userId は使わない（設計書 3.9、6.1） */
	@Test
	void registerIgnoresAUserIdSentFromTheScreen() throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a, "userId", String.valueOf(b.userId())).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).hasSize(1);
		assertThat(transactionsOf(b)).isEmpty();
	}

	@Test
	void registerStripsAmountAndDescriptionSpacesAndStoresNullForBlankDescription() throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a, "amount", " 800 ", "description", "　").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		Transaction t = transactionsOf(a).get(0);
		assertThat(t.getAmount()).isEqualTo(800);
		assertThat(t.getDescription()).isNull();
	}

	@Test
	void registerStripsSpacesAroundDescription() throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a, "description", "　昼食 ").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a).get(0).getDescription()).isEqualTo("昼食");
	}

	@ParameterizedTest(name = "登録できる：金額 {0}、日付 {1}、摘要の長さ {2}")
	@CsvSource(delimiter = '|', value = {
			"1|2026-10-20|0",
			"1000000|2026-10-20|0",
			// 全角の数字は、半角に直して受け付ける（R13）
			"１|2026-10-20|0",
			"１００００００|2026-10-20|0",
			"1200|2000-03-01|0",
			"1200|2100-12-31|0",
			"1200|2026-10-20|30"
	})
	void registerAcceptsBoundaryValues(String amount, String date, int descriptionLength) throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a, "amount", amount, "transactionDate", date,
				"description", "あ".repeat(descriptionLength)).with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).hasSize(1);
	}

	@ParameterizedTest(name = "登録の入力エラー：{0} = [{1}] → {2}")
	@CsvSource(delimiter = '|', value = {
			// 金額（型変換のエラー、範囲のエラー、必須）
			"amount|0|金額は1円以上1,000,000円以下で入力してください",
			"amount|-1|金額は1円以上1,000,000円以下で入力してください",
			"amount|1000001|金額は1円以上1,000,000円以下で入力してください",
			"amount|abc|金額は整数で入力してください",
			"amount|1,000|金額は整数で入力してください",
			// 全角の数字は半角に直す（R13）ので、範囲のチェックは直した値で行われる
			"amount|０|金額は1円以上1,000,000円以下で入力してください",
			"amount|１０００００１|金額は1円以上1,000,000円以下で入力してください",
			// 全角でも、数字以外はエラーのまま
			"amount|－５|金額は整数で入力してください",
			"amount|１　２|金額は整数で入力してください",
			"amount|１．５|金額は整数で入力してください",
			"amount|１２円|金額は整数で入力してください",
			"amount|1 000|金額は整数で入力してください",
			"amount|0x10|金額は整数で入力してください",
			"amount|#10|金額は整数で入力してください",
			"amount|+5|金額は整数で入力してください",
			"amount|1.5|金額は整数で入力してください",
			"amount|10円|金額は整数で入力してください",
			"amount|99999999999|金額は整数で入力してください",
			"amount|''|金額を入力してください",
			"amount|' '|金額を入力してください",
			// 日付（範囲のエラー、型変換のエラー、必須）
			"transactionDate|1999-12-31|日付は2000年3月1日から2100年12月31日までで入力してください",
			"transactionDate|2000-02-29|日付は2000年3月1日から2100年12月31日までで入力してください",
			"transactionDate|2101-01-01|日付は2000年3月1日から2100年12月31日までで入力してください",
			"transactionDate|0226-05-01|日付は2000年3月1日から2100年12月31日までで入力してください",
			"transactionDate|2026-02-30|日付を正しく入力してください",
			"transactionDate|20026-05-01|日付を正しく入力してください",
			"transactionDate|abc|日付を正しく入力してください",
			"transactionDate|''|日付を入力してください",
			// 選択（未選択、数字でない値は未選択と同じ文言、G5）
			"categoryId|''|科目を選択してください",
			"categoryId|abc|科目を選択してください",
			"vendorId|''|取引先を選択してください",
			"vendorId|abc|取引先を選択してください",
			"paymentMethodId|''|支払方法を選択してください",
			"paymentMethodId|abc|支払方法を選択してください"
	})
	void registerWithInvalidInputRedisplaysTheFormAndSavesNothing(String field, String value, String message) throws Exception {
		MvcResult result = mockMvc.perform(valid(post("/transactions/new"), a, field, value).with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains(message);
		assertThat(count(body, "class=\"field-error\"")).as("エラーは1項目に1つだけ（G3、G7）").isEqualTo(1);
		assertThat(transactionsOf(a)).isEmpty();
	}

	@ParameterizedTest(name = "登録の入力エラーでも入力値が残る：{0} = [{1}]")
	@CsvSource(delimiter = '|', value = {
			"amount|abc", "amount|１２円", "amount|0", "amount|1000001",
			"transactionDate|2026-02-30", "transactionDate|1999-12-31", "transactionDate|2000-02-29"
	})
	void registerWithInvalidInputKeepsTheEnteredValue(String field, String value) throws Exception {
		String body = body(mockMvc.perform(valid(post("/transactions/new"), a, field, value).with(asA)).andReturn());

		assertThat(body).contains("name=\"" + field + "\" value=\"" + value + "\"");
	}

	/** 全角の数字で打った金額は、半角に直して登録される（R13） */
	@Test
	void registerAcceptsAFullWidthAmount() throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a, "amount", "１２００").with(asA))
				.andExpect(redirectedUrl("/transactions"));
		mockMvc.perform(valid(post("/transactions/new"), a, "amount", "　３4５　").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a)).extracting(Transaction::getAmount).containsExactlyInAnyOrder(1200, 345);
	}

	/** 全角で打って範囲のエラーになったときは、入力欄に半角に直した数字が出る（型変換のエラーのときだけ、入力した文字がそのまま残る） */
	@Test
	void registerWithAFullWidthOutOfRangeAmountShowsHalfWidthValueInTheField() throws Exception {
		String body = body(mockMvc.perform(valid(post("/transactions/new"), a, "amount", "１０００００１").with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("金額は1円以上1,000,000円以下で入力してください");
		assertThat(body).contains("name=\"amount\" value=\"1000001\"");
		assertThat(transactionsOf(a)).isEmpty();
	}

	/** 全角の数字の更新も、半角に直して保存される */
	@Test
	void updateAcceptsAFullWidthAmount() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		mockMvc.perform(valid(post("/transactions/" + t.getId() + "/edit"), a, "amount", "１５００").with(asA))
				.andExpect(redirectedUrl("/transactions"));

		assertThat(transactionsOf(a).get(0).getAmount()).isEqualTo(1500);
	}

	@Test
	void registerWithErrorKeepsOtherValuesAndSelectedOptions() throws Exception {
		String body = body(mockMvc.perform(post("/transactions/new").with(asA).with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "abc")
				.param("categoryId", String.valueOf(a.category2().getId())).param("description", "入力した摘要")
				.param("vendorId", String.valueOf(a.vendor2().getId()))
				.param("paymentMethodId", String.valueOf(a.payment2().getId())))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("value=\"2026-10-20\"", "value=\"入力した摘要\"");
		assertThat(body).contains("<option value=\"" + a.category2().getId() + "\" selected=\"selected\">交通費</option>");
		assertThat(body).contains("<option value=\"" + a.vendor2().getId() + "\" selected=\"selected\">Amazon</option>");
		assertThat(body).contains("<option value=\"" + a.payment2().getId() + "\" selected=\"selected\">カード</option>");
		// 選択肢は取り直されている（自分のものが全部ある）
		assertThat(body).contains("食費", "コンビニ", "現金");
	}

	@Test
	void registerWithTooLongDescriptionShowsErrorAndKeepsIt() throws Exception {
		String thirtyOne = "あ".repeat(31);

		String body = body(mockMvc.perform(valid(post("/transactions/new"), a, "description", thirtyOne).with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("摘要は30文字以内で入力してください");
		assertThat(body).contains("value=\"" + thirtyOne + "\"");
		assertThat(transactionsOf(a)).isEmpty();
	}

	@Test
	void registerWithSeveralErrorsShowsEachOnItsField() throws Exception {
		String body = body(mockMvc.perform(post("/transactions/new").with(asA).with(csrf())
				.param("transactionDate", "").param("amount", "0").param("categoryId", "")
				.param("vendorId", "").param("paymentMethodId", ""))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("日付を入力してください", "金額は1円以上1,000,000円以下で入力してください",
				"科目を選択してください", "取引先を選択してください", "支払方法を選択してください");
		assertThat(transactionsOf(a)).isEmpty();
	}

	/** 他人の科目／取引先／支払方法のID、存在しないIDは、404。取引は増えない（6.7） */
	@ParameterizedTest(name = "登録で {0} に他人のIDを送ると 404")
	@ValueSource(strings = { "categoryId", "vendorId", "paymentMethodId" })
	void registerWithOtherUsersItemIsNotFound(String field) throws Exception {
		Long otherId = switch (field) {
			case "categoryId" -> b.category1().getId();
			case "vendorId" -> b.vendor1().getId();
			default -> b.payment1().getId();
		};

		mockMvc.perform(valid(post("/transactions/new"), a, field, String.valueOf(otherId)).with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(transactionsOf(a)).isEmpty();
		assertThat(transactionsOf(b)).isEmpty();
	}

	@ParameterizedTest(name = "登録で {0} に存在しないIDを送ると 404")
	@ValueSource(strings = { "categoryId", "vendorId", "paymentMethodId" })
	void registerWithNonexistentItemIsNotFound(String field) throws Exception {
		mockMvc.perform(valid(post("/transactions/new"), a, field, "999999999").with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(transactionsOf(a)).isEmpty();
	}

	// ---- 詳細 ----

	@Test
	void detailShowsTheTransactionWithEditAndDeleteButtons() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		MvcResult result = mockMvc.perform(get("/transactions/" + t.getId()).with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/detail"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("2026/10/19", "1,200円", "食費", "コンビニ", "現金", "昼食");
		assertThat(body).contains("href=\"/transactions/" + t.getId() + "/edit\"");                       // 編集
		assertThat(body).contains("action=\"/transactions/" + t.getId() + "/delete\"");                   // 削除
		assertThat(body).contains("onsubmit=\"return confirm('削除しますか？')\"");                        // 確認ダイアログ（固定の文言）
		assertThat(body).contains("name=\"_csrf\"");
		assertThat(body).contains("href=\"/transactions\"");                                              // 一覧に戻る
	}

	@Test
	void detailWithoutDescriptionShowsBlankNotNull() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, null);

		String body = body(mockMvc.perform(get("/transactions/" + t.getId()).with(asA)).andReturn());

		assertThat(body).doesNotContain(">null<");
		assertThat(body).contains("<dd></dd>");
	}

	/** 他人のID・存在しないID・数字でないIDは、すべて同じ 404（画面も状態コードも、設計書 7.8） */
	@Test
	void detailOfOtherUsersMissingAndNonNumericIdsAreTheSame404() throws Exception {
		Transaction mine = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		for (String id : List.of(String.valueOf(mine.getId()), "999999999", "abc", "99999999999999999999")) {
			mockMvc.perform(get("/transactions/" + id).with(asB))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error/404"));
		}
	}

	// ---- 編集の画面 ----

	@Test
	void editFormShowsTheRegisteredValues() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		MvcResult result = mockMvc.perform(get("/transactions/" + t.getId() + "/edit").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("action=\"/transactions/" + t.getId() + "/edit\"");
		assertThat(body).contains("value=\"2026-10-19\"", "value=\"1200\"", "value=\"昼食\"");
		assertThat(body).contains("<option value=\"" + a.category1().getId() + "\" selected=\"selected\">食費</option>");
		assertThat(body).contains("<option value=\"" + a.vendor1().getId() + "\" selected=\"selected\">コンビニ</option>");
		assertThat(body).contains("<option value=\"" + a.payment1().getId() + "\" selected=\"selected\">現金</option>");
		assertThat(body).contains("取引の編集");
		assertThat(body).contains("name=\"_csrf\"");
		assertThat(body).contains("href=\"/transactions\"");                                              // 戻る
	}

	@Test
	void editFormOfOtherUsersMissingAndNonNumericIdsAreNotFound() throws Exception {
		Transaction mine = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		for (String id : List.of(String.valueOf(mine.getId()), "999999999", "abc")) {
			mockMvc.perform(get("/transactions/" + id + "/edit").with(asB))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error/404"));
		}
	}

	// ---- 更新 ----

	@Test
	void updateSucceedsAndRedirectsToList() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		mockMvc.perform(post("/transactions/" + t.getId() + "/edit").with(asA).with(csrf())
				.param("transactionDate", "2026-11-03").param("amount", "1500")
				.param("categoryId", String.valueOf(a.category2().getId())).param("description", "")
				.param("vendorId", String.valueOf(a.vendor2().getId()))
				.param("paymentMethodId", String.valueOf(a.payment2().getId())))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/transactions"))
				.andExpect(flash().attribute("successMessageKey", "success.transactionUpdated"));

		List<Transaction> list = transactionsOf(a);
		assertThat(list).hasSize(1);
		Transaction changed = list.get(0);
		assertThat(changed.getUserId()).isEqualTo(a.userId());
		assertThat(changed.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 3));
		assertThat(changed.getAmount()).isEqualTo(1500);
		assertThat(changed.getDescription()).isNull();
		assertThat(changed.getCategory().getId()).isEqualTo(a.category2().getId());
		assertThat(changed.getVendor().getId()).isEqualTo(a.vendor2().getId());
		assertThat(changed.getPaymentMethod().getId()).isEqualTo(a.payment2().getId());
	}

	@Test
	void updateWithInvalidInputRedisplaysTheEditFormKeepsValuesAndChangesNothing() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		MvcResult result = mockMvc.perform(post("/transactions/" + t.getId() + "/edit").with(asA).with(csrf())
				.param("transactionDate", "2026-11-03").param("amount", "１２円")
				.param("categoryId", String.valueOf(a.category2().getId())).param("description", "入力した摘要")
				.param("vendorId", String.valueOf(a.vendor2().getId()))
				.param("paymentMethodId", String.valueOf(a.payment2().getId())))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("金額は整数で入力してください");
		assertThat(body).contains("action=\"/transactions/" + t.getId() + "/edit\"");
		assertThat(body).contains("value=\"１２円\"", "value=\"2026-11-03\"", "value=\"入力した摘要\"");
		assertThat(body).contains("<option value=\"" + a.category2().getId() + "\" selected=\"selected\">交通費</option>");

		Transaction unchanged = transactionsOf(a).get(0);
		assertThat(unchanged.getAmount()).isEqualTo(1200);
		assertThat(unchanged.getDescription()).isEqualTo("昼食");
		assertThat(unchanged.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 19));
	}

	/** 他人の取引・存在しない取引への更新は、入力が正しくても間違っていても、同じ 404（設計書 7.8）。他人の取引は変わらない */
	@ParameterizedTest(name = "他人のIDへの更新：金額 [{0}] → 404")
	@ValueSource(strings = { "9999", "abc", "0", "" })
	void updateOfOtherUsersTransactionIsNotFoundWhetherOrNotTheInputIsValid(String amount) throws Exception {
		Transaction mine = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		mockMvc.perform(valid(post("/transactions/" + mine.getId() + "/edit"), b, "amount", amount, "description", "書き換え")
				.with(asB))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		Transaction unchanged = transactionsOf(a).get(0);
		assertThat(unchanged.getAmount()).isEqualTo(1200);
		assertThat(unchanged.getDescription()).isEqualTo("昼食");
		assertThat(unchanged.getUserId()).isEqualTo(a.userId());
		assertThat(transactionsOf(b)).isEmpty();
	}

	@ParameterizedTest(name = "存在しない・数字でないIDへの更新 [{0}] → 404")
	@ValueSource(strings = { "999999999", "abc" })
	void updateOfMissingOrNonNumericIdIsNotFound(String id) throws Exception {
		mockMvc.perform(valid(post("/transactions/" + id + "/edit"), a).with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));
		mockMvc.perform(valid(post("/transactions/" + id + "/edit"), a, "amount", "abc").with(asA))
				.andExpect(status().isNotFound());
	}

	/** 更新で他人の科目などを送ると 404。元の取引は変わらない */
	@ParameterizedTest(name = "更新で {0} に他人のIDを送ると 404")
	@ValueSource(strings = { "categoryId", "vendorId", "paymentMethodId" })
	void updateWithOtherUsersItemIsNotFoundAndChangesNothing(String field) throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");
		Long otherId = switch (field) {
			case "categoryId" -> b.category1().getId();
			case "vendorId" -> b.vendor1().getId();
			default -> b.payment1().getId();
		};

		mockMvc.perform(valid(post("/transactions/" + t.getId() + "/edit"), a, "amount", "7777", field, String.valueOf(otherId))
				.with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		Transaction unchanged = transactionsOf(a).get(0);
		assertThat(unchanged.getAmount()).isEqualTo(1200);
		assertThat(unchanged.getCategory().getId()).isEqualTo(a.category1().getId());
		assertThat(unchanged.getVendor().getId()).isEqualTo(a.vendor1().getId());
		assertThat(unchanged.getPaymentMethod().getId()).isEqualTo(a.payment1().getId());
	}

	// ---- 削除 ----

	@Test
	void deleteSucceedsAndRedirectsToList() throws Exception {
		Transaction t = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");
		Transaction kept = saveFor(a, LocalDate.of(2026, 10, 20), 500, "残す");

		mockMvc.perform(post("/transactions/" + t.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/transactions"))
				.andExpect(flash().attribute("successMessageKey", "success.deleted"));

		assertThat(transactionsOf(a)).extracting(Transaction::getId).containsExactly(kept.getId());
	}

	@Test
	void deleteOfOtherUsersTransactionIsNotFoundAndKeepsIt() throws Exception {
		Transaction mine = saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		mockMvc.perform(post("/transactions/" + mine.getId() + "/delete").with(asB).with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(transactionsOf(a)).hasSize(1);
	}

	@ParameterizedTest(name = "存在しない・数字でないIDの削除 [{0}] → 404")
	@ValueSource(strings = { "999999999", "abc" })
	void deleteOfMissingOrNonNumericIdIsNotFound(String id) throws Exception {
		saveFor(a, LocalDate.of(2026, 10, 19), 1200, "昼食");

		mockMvc.perform(post("/transactions/" + id + "/delete").with(asA).with(csrf()))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));

		assertThat(transactionsOf(a)).hasSize(1);
	}

}
