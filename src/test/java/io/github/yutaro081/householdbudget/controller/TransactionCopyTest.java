package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
 * 取引の複写（issue 012。設計書 2.2、2.3、2.6、3.9、7.8、U8）のテスト。
 * 詳細画面の「この取引を写して登録」から {@code GET /transactions/new?copyFrom={id}} で、値の入った登録画面を開く。
 * 開いただけでは保存しない。他人の取引・存在しない ID・数字でない ID は 404。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は、日本時間の 2026-10-27 に固定した Clock で確かめる。テストのパスワードのハッシュは、ダミーの値。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionCopyTest {

	/** 日本時間の 2026-10-27 に固定した Clock（複写した画面の日付は今日） */
	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2026, 10, 27).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

	/** 写す元の取引の日付（今日の 2026-10-27 とは違う日にしておく） */
	private static final LocalDate SOURCE_DATE = LocalDate.of(2026, 9, 5);

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

	private record Owner(Long userId, String username, Category category1, Category category2,
			Vendor vendor1, Vendor vendor2, PaymentMethod payment1, PaymentMethod payment2) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@BeforeEach
	void createUsers() {
		a = createOwner("copytx1");
		b = createOwner("copytx2");
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

	/** 写す元：2 番目の科目・取引先・支払方法を使う（初期値の「1 番目」と区別するため） */
	private Transaction saveSource(Owner o, String description) {
		Transaction t = transactionRepository.save(new Transaction(o.userId(), SOURCE_DATE, 3456,
				o.category2(), description, o.vendor2(), o.payment2()));
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

	// ---- データ分離（先に書いたテスト） ----

	/** 他人の取引・存在しない ID・数字でない ID・範囲外の数字は、すべて同じ 404（画面も状態コードも、設計書 7.8） */
	@Test
	void copyOfOtherUsersMissingAndInvalidIdsIsTheSame404() throws Exception {
		Transaction mine = saveSource(a, "昼食");

		for (String id : List.of(String.valueOf(mine.getId()), "999999999", "abc", "1.5", "-", "99999999999999999999")) {
			mockMvc.perform(get("/transactions/new").param("copyFrom", id).with(asB))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error/404"));
		}
	}

	@Test
	void copyOfOwnTransactionIsOk() throws Exception {
		Transaction mine = saveSource(a, "昼食");

		mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(mine.getId())).with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"));
	}

	@Test
	void copyWithoutLoginGoesToLoginPage() throws Exception {
		Transaction mine = saveSource(a, "昼食");

		mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(mine.getId())))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	// ---- 写される値 ----

	@Test
	void copyFormHasTheSourceValuesAndTodayAsDate() throws Exception {
		Transaction src = saveSource(a, "週末の買い物");

		String body = body(mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())).with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("value=\"2026-10-27\"");                          // 日付は今日
		assertThat(body).doesNotContain("2026-09-05");                              // 元の日付は写さない
		assertThat(body).contains("value=\"3456\"", "value=\"週末の買い物\"");
		assertThat(body).contains("<option value=\"" + a.category2().getId() + "\" selected=\"selected\">交通費</option>");
		assertThat(body).contains("<option value=\"" + a.vendor2().getId() + "\" selected=\"selected\">Amazon</option>");
		assertThat(body).contains("<option value=\"" + a.payment2().getId() + "\" selected=\"selected\">カード</option>");
		assertThat(body).contains("action=\"/transactions/new\"");                  // 送信先は登録（U1、U8）
		assertThat(body).contains("取引の登録");
		assertThat(body).contains("name=\"_csrf\"");
	}

	@Test
	void copyOfTransactionWithoutDescriptionShowsBlankNotNull() throws Exception {
		Transaction src = saveSource(a, null);

		String body = body(mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())).with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).doesNotContain("value=\"null\"");
		assertThat(body).contains("value=\"3456\"");
	}

	// ---- 保存しないこと ----

	@Test
	void openingTheCopyFormDoesNotCreateOrChangeAnything() throws Exception {
		Transaction src = saveSource(a, "昼食");
		assertThat(transactionsOf(a)).hasSize(1);

		mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())).with(asA))
				.andExpect(status().isOk());
		mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())).with(asA))
				.andExpect(status().isOk());

		List<Transaction> after = transactionsOf(a);
		assertThat(after).hasSize(1);
		Transaction t = after.get(0);
		assertThat(t.getTransactionDate()).isEqualTo(SOURCE_DATE);
		assertThat(t.getAmount()).isEqualTo(3456);
		assertThat(t.getDescription()).isEqualTo("昼食");
		assertThat(transactionsOf(b)).isEmpty();
	}

	/** 複写の画面から「登録」を押すと、ふつうの登録として 1 件増える。写す元は変わらない */
	@Test
	void registeringFromTheCopyFormAddsOneAndKeepsTheSource() throws Exception {
		Transaction src = saveSource(a, "昼食");

		mockMvc.perform(post("/transactions/new").with(asA).with(csrf())
				.param("transactionDate", "2026-10-27")
				.param("amount", "3456")
				.param("categoryId", String.valueOf(a.category2().getId()))
				.param("description", "昼食")
				.param("vendorId", String.valueOf(a.vendor2().getId()))
				.param("paymentMethodId", String.valueOf(a.payment2().getId())))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/transactions"));

		List<Transaction> after = transactionsOf(a);
		assertThat(after).hasSize(2);
		assertThat(after).extracting(Transaction::getTransactionDate)
				.containsExactlyInAnyOrder(LocalDate.of(2026, 10, 27), SOURCE_DATE);
		assertThat(after).filteredOn(t -> t.getId().equals(src.getId())).hasSize(1);
	}

	/** 複写の画面でも、登録の入力チェックは同じ（金額の範囲）。エラーでは、入力値が残り、複写の 1 行は出ない */
	@Test
	void invalidAmountFromTheCopyFormIsRejectedLikeAnyRegistration() throws Exception {
		saveSource(a, "昼食");

		String body = body(mockMvc.perform(post("/transactions/new").with(asA).with(csrf())
				.param("transactionDate", "2026-10-27")
				.param("amount", "1000001")
				.param("categoryId", String.valueOf(a.category2().getId()))
				.param("description", "昼食")
				.param("vendorId", String.valueOf(a.vendor2().getId()))
				.param("paymentMethodId", String.valueOf(a.payment2().getId())))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"))
				.andReturn());

		assertThat(body).contains("value=\"1000001\"");
		assertThat(body).doesNotContain(COPY_NOTICE);
		assertThat(transactionsOf(a)).hasSize(1);
	}

	// ---- 固定の 1 行 ----

	private static final String COPY_NOTICE = "この取引を写しています";

	@Test
	void copyFormShowsTheFixedNoticeOnlyForCopy() throws Exception {
		Transaction src = saveSource(a, "昼食");

		String copied = body(mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())).with(asA))
				.andReturn());
		String plain = body(mockMvc.perform(get("/transactions/new").with(asA)).andReturn());

		assertThat(copied).contains(COPY_NOTICE, "日付は今日にしてあります", "保存されません");
		assertThat(copied).as("ボタンの文字（保存）と合わせる（issue 015 の 3 回目。issue 024 で「登録する」から変えた）").contains("「保存」を押すまで")
				.doesNotContain("「登録する」を押すまで", "「登録」を押すまで");
		assertThat(plain).doesNotContain(COPY_NOTICE);
	}

	@Test
	void copyNoticeDoesNotEmbedUserInput() throws Exception {
		Transaction src = saveSource(a, "<script>alert(1)</script>");

		String body = body(mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(src.getId())).with(asA))
				.andReturn());

		assertThat(body).doesNotContain("<script>alert(1)</script>");               // th:field が文字をエスケープする
		assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
	}

	// ---- copyFrom が空 ----

	@ParameterizedTest(name = "copyFrom=[{0}]（空）はふつうの登録画面")
	@ValueSource(strings = { "" })
	void emptyCopyFromShowsThePlainNewForm(String value) throws Exception {
		saveSource(a, "昼食");

		String body = body(mockMvc.perform(get("/transactions/new").param("copyFrom", value).with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("transactions/form"))
				.andReturn());

		assertThat(body).contains("value=\"2026-10-27\"");
		assertThat(body).doesNotContain(COPY_NOTICE, "value=\"3456\"");
	}

	// ---- 詳細画面のボタン ----

	@Test
	void detailPageHasTheCopyLinkNextToEdit() throws Exception {
		Transaction src = saveSource(a, "昼食");

		String body = body(mockMvc.perform(get("/transactions/" + src.getId()).with(asA))
				.andExpect(status().isOk()).andReturn());

		String link = "href=\"/transactions/new?copyFrom=" + src.getId() + "\"";
		assertThat(body).contains(link, "この取引を写して登録");
		assertThat(body.indexOf("/edit\"")).isLessThan(body.indexOf(link));        // 「編集」の隣（あと）
		assertThat(body.indexOf(link)).isLessThan(body.indexOf("/delete\""));       // 「削除」の前
	}
}
