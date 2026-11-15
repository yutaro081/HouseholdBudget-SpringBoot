package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * 取引の登録・編集画面の、取引先・支払方法の選択肢の並び（要件定義書 4.2、設計書 2.2・6.7、issue 029 の段階 2）。
 * ・全期間の取引の件数が多い順。件数が同じなら id の順（登録順）。取引が 0 件のものは最後（id の順）
 * ・件数は、ログイン中の人の取引だけで数える（ほかの人の取引は数えない。3.9）
 * ・新規・編集・複写・入力エラーで出し直す画面で同じ並び。選んでいたものは、並びが変わっても選ばれたまま
 * ・科目の選択肢と、取引先・支払方法の一覧の画面は、登録順のまま
 * <p>
 * データ（登録順）：取引先 コンビニ（0 件）・スーパー（1 件）・ドラッグストア（3 件）・本屋（1 件）、
 * 支払方法 現金（0 件）・カード（3 件）・電子マネー（2 件）、科目 食費（1 件）・日用品（4 件）。
 * 期待する並び：取引先 ドラッグストア → スーパー → 本屋 → コンビニ、支払方法 カード → 電子マネー → 現金、科目 食費 → 日用品（登録順）。
 * <p>
 * ほかの人の取引：DB の外部キーは vendor_id → vendors.id だけなので、ほかの人（user_id が違う）の取引が、
 * 自分の取引先・支払方法を指す行を、テストでは作れる（ふつうの画面の操作では作れない）。その行を 5 件作り、数えないことを確かめる。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionFormChoiceOrderTest {

	private static final List<String> VENDORS_BY_USE = List.of("ドラッグストア", "スーパー", "本屋", "コンビニ");
	private static final List<String> PAYMENTS_BY_USE = List.of("カード", "電子マネー", "現金");
	private static final List<String> CATEGORIES_IN_ORDER = List.of("食費", "日用品");

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

	private RequestPostProcessor asUser;
	private Category food;
	private Vendor convenience;
	private Vendor supermarket;
	private PaymentMethod cash;
	private PaymentMethod emoney;
	/** スーパー・電子マネーの取引（並びの先頭ではない取引先・支払方法を使う） */
	private Transaction supermarketTransaction;

	@BeforeEach
	void setUp() {
		Long userId = userRepository.save(new User("choiceord1", "dummy-hash-for-test")).getId();
		asUser = user(new LoginUser(userId, "choiceord1", "x"));

		food = categoryRepository.save(new Category(userId, "食費"));
		Category daily = categoryRepository.save(new Category(userId, "日用品"));
		convenience = vendorRepository.save(new Vendor(userId, "コンビニ"));
		supermarket = vendorRepository.save(new Vendor(userId, "スーパー"));
		Vendor drugstore = vendorRepository.save(new Vendor(userId, "ドラッグストア"));
		Vendor bookstore = vendorRepository.save(new Vendor(userId, "本屋"));
		cash = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		PaymentMethod card = paymentMethodRepository.save(new PaymentMethod(userId, "カード"));
		emoney = paymentMethodRepository.save(new PaymentMethod(userId, "電子マネー"));

		save(userId, daily, drugstore, card);
		save(userId, daily, drugstore, card);
		save(userId, daily, drugstore, card);
		supermarketTransaction = save(userId, food, supermarket, emoney);
		save(userId, daily, bookstore, emoney);
		entityManager.flush();
		entityManager.clear();
	}

	private Transaction save(Long userId, Category category, Vendor vendor, PaymentMethod payment) {
		return transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 20), 1000, category, null, vendor, payment));
	}

	/** ほかの人の取引 5 件が、自分の「コンビニ」「現金」を指す（数えると、コンビニと現金が先頭になる） */
	private void otherUsersTransactionsPointToMyConvenienceAndCash() {
		Long otherId = userRepository.save(new User("choiceord2", "dummy-hash-for-test")).getId();
		Category otherCategory = categoryRepository.save(new Category(otherId, "食費"));
		for (int i = 0; i < 5; i++) {
			save(otherId, otherCategory, convenience, cash);
		}
		entityManager.flush();
		entityManager.clear();
	}

	private String body(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** id の select の、「選択してください」を除いた選択肢の文字（並び順のまま） */
	private static List<String> labels(String body, String id) {
		List<String> labels = new ArrayList<>();
		Matcher option = Pattern.compile("<option value=\"(\\d+)\"[^>]*>([^<]*)</option>").matcher(select(body, id));
		while (option.find()) {
			labels.add(option.group(2));
		}
		return labels;
	}

	/** id の select の中で、選ばれている選択肢の文字（1 つだけのはず） */
	private static List<String> selected(String body, String id) {
		List<String> labels = new ArrayList<>();
		Matcher option = Pattern.compile("<option value=\"\\d+\"[^>]*selected=\"selected\"[^>]*>([^<]*)</option>").matcher(select(body, id));
		while (option.find()) {
			labels.add(option.group(1));
		}
		return labels;
	}

	private static String select(String body, String id) {
		Matcher m = Pattern.compile("(?s)<select[^>]*id=\"" + id + "\"[^>]*>(.*?)</select>").matcher(body);
		assertThat(m.find()).as("select#" + id + " がある").isTrue();
		return m.group(1);
	}

	private static void assertChoiceOrder(String body, String screen) {
		assertThat(labels(body, "vendorId")).as(screen + "：取引先は件数の多い順。同じ件数は登録順。0 件は最後").containsExactlyElementsOf(VENDORS_BY_USE);
		assertThat(labels(body, "paymentMethodId")).as(screen + "：支払方法は件数の多い順。0 件は最後").containsExactlyElementsOf(PAYMENTS_BY_USE);
		assertThat(labels(body, "categoryId")).as(screen + "：科目は登録順のまま（件数は日用品のほうが多い）").containsExactlyElementsOf(CATEGORIES_IN_ORDER);
	}

	// ---- 並び ----

	@Test
	void theNewFormListsVendorsAndPaymentMethodsByUseAndCategoriesInOrder() throws Exception {
		assertChoiceOrder(body(get("/transactions/new")), "新規");
	}

	@Test
	void otherUsersTransactionsAreNotCounted() throws Exception {
		otherUsersTransactionsPointToMyConvenienceAndCash();

		assertChoiceOrder(body(get("/transactions/new")), "ほかの人の取引があるとき");
	}

	// ---- 選んでいたものが、選ばれたまま ----

	@Test
	void theEditFormKeepsTheTransactionsVendorAndPaymentMethodSelected() throws Exception {
		String body = body(get("/transactions/" + supermarketTransaction.getId() + "/edit"));

		assertChoiceOrder(body, "編集");
		assertThat(selected(body, "vendorId")).as("2 番目に並ぶスーパーが選ばれたまま").containsExactly("スーパー");
		assertThat(selected(body, "paymentMethodId")).as("2 番目に並ぶ電子マネーが選ばれたまま").containsExactly("電子マネー");
		assertThat(selected(body, "categoryId")).containsExactly("食費");
	}

	@Test
	void theCopyFormKeepsTheCopiedVendorAndPaymentMethodSelected() throws Exception {
		String body = body(get("/transactions/new").param("copyFrom", String.valueOf(supermarketTransaction.getId())));

		assertChoiceOrder(body, "複写");
		assertThat(selected(body, "vendorId")).containsExactly("スーパー");
		assertThat(selected(body, "paymentMethodId")).containsExactly("電子マネー");
	}

	@Test
	void theNewFormRedisplayedAfterAnInputErrorKeepsTheChosenVendorAndPaymentMethod() throws Exception {
		String body = body(post("/transactions/new").with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "1200").param("categoryId", "")
				.param("vendorId", String.valueOf(convenience.getId()))
				.param("paymentMethodId", String.valueOf(cash.getId())));

		assertThat(body).as("入力エラーで出し直した画面").contains("field-error");
		assertChoiceOrder(body, "新規の入力エラー");
		assertThat(selected(body, "vendorId")).as("最後に並ぶコンビニが選ばれたまま").containsExactly("コンビニ");
		assertThat(selected(body, "paymentMethodId")).as("最後に並ぶ現金が選ばれたまま").containsExactly("現金");
	}

	@Test
	void theEditFormRedisplayedAfterAnInputErrorKeepsTheChosenVendorAndPaymentMethod() throws Exception {
		String body = body(post("/transactions/" + supermarketTransaction.getId() + "/edit").with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "abc")
				.param("categoryId", String.valueOf(food.getId()))
				.param("vendorId", String.valueOf(supermarket.getId()))
				.param("paymentMethodId", String.valueOf(emoney.getId())));

		assertThat(body).as("入力エラーで出し直した画面").contains("field-error");
		assertChoiceOrder(body, "編集の入力エラー");
		assertThat(selected(body, "vendorId")).containsExactly("スーパー");
		assertThat(selected(body, "paymentMethodId")).containsExactly("電子マネー");
	}

	// ---- 一覧の画面は登録順のまま ----

	@Test
	void theVendorAndPaymentMethodListScreensStayInRegistrationOrder() throws Exception {
		String vendors = body(get("/vendors"));
		assertThat(vendors.indexOf(">コンビニ<")).as("取引先の一覧は登録順").isPositive()
				.isLessThan(vendors.indexOf(">スーパー<"));
		assertThat(vendors.indexOf(">スーパー<")).isLessThan(vendors.indexOf(">ドラッグストア<"));
		assertThat(vendors.indexOf(">ドラッグストア<")).isLessThan(vendors.indexOf(">本屋<"));

		String payments = body(get("/payment-methods"));
		assertThat(payments.indexOf(">現金<")).as("支払方法の一覧は登録順").isPositive()
				.isLessThan(payments.indexOf(">カード<"));
		assertThat(payments.indexOf(">カード<")).isLessThan(payments.indexOf(">電子マネー<"));
	}
}
