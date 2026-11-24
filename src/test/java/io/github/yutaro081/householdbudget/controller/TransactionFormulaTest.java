package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
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
 * 取引の登録・編集画面の、金額の欄の式（issue 035 の段階 2。設計書 2.3、要件定義書 4.2）の、HTML とサーバーの確認。
 * <ul>
 * <li>画面：calculator.js を読むこと。計算できないときの固定の文が、行ごとに hidden で、その行の金額の欄を指すこと。説明の段落の 1 文</li>
 * <li>サーバー（見張り役。サーバーは変えていない）：式の文字列がそのまま送られても（JavaScript が無効のときなど）、保存されないこと</li>
 * </ul>
 * 式を計算する動きは、MockMvc では確かめられないので、本人がブラウザ（Console の一覧を含む）で確かめる（手順書 5 章・6 章）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionFormulaTest {

	private static final String ERROR_TEXT = "計算できない式です。使えるのは、数字と ＋ − だけです（カンマ・小数点・×・÷ は使えません）。";

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

	private Long userId;
	private Category category;
	private Vendor vendor;
	private PaymentMethod payment;
	private Transaction transaction;
	private RequestPostProcessor asUser;

	@BeforeEach
	void createUserWithATransaction() {
		userId = userRepository.save(new User("calcfml1", "dummy-hash-for-test")).getId();
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transaction = transactionRepository.save(
				new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, "昼食", vendor, payment));
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(userId, "calcfml1", "x"));
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 金額に、送られた文字のまま amount を入れて登録する（ブラウザで計算されなかったとき） */
	private String postAmount(String amount) throws Exception {
		return mockMvc.perform(post("/transactions/new").with(asUser).with(csrf())
				.param("transactionDate", "2026-10-20").param("lines[0].amount", amount)
				.param("lines[0].categoryId", String.valueOf(category.getId())).param("lines[0].description", "式のまま")
				.param("vendorId", String.valueOf(vendor.getId()))
				.param("paymentMethodId", String.valueOf(payment.getId())))
				.andExpect(status().isOk()).andExpect(view().name("transactions/form"))
				.andReturn().getResponse().getContentAsString();
	}

	private long countOfMyTransactions() {
		entityManager.flush();
		return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM transactions WHERE user_id = ?1")
				.setParameter(1, userId).getSingleResult()).longValue();
	}

	private static int count(String body, String word) {
		return body.split(Pattern.quote(word), -1).length - 1;
	}

	// ---- 画面 ----

	@Test
	void theFormLoadsTheCalculatorScript() throws Exception {
		for (String path : new String[] { "/transactions/new", "/transactions/" + transaction.getId() + "/edit" }) {
			String body = page(path);
			assertThat(body).as(path).contains("<script defer src=\"/js/calculator.js\"></script>");
			assertThat(body).as(path + "：今の金額ボタンは残す（段階 2）").contains("src=\"/js/amount-buttons.js\"", "data-amount-add=\"1000\"");
			assertThat(body).as(path + "：HTML の中にスクリプトを書かない").doesNotContain("<script>", "onkeydown", "onblur", "onsubmit");
		}
	}

	@Test
	void otherScreensDoNotLoadTheCalculatorScript() throws Exception {
		for (String path : new String[] { "/budgets", "/transactions", "/categories", "/comparison", "/" }) {
			assertThat(page(path)).as(path).doesNotContain("calculator.js", "data-calc-error");
		}
	}

	/** 計算できないときの文は、行ごとに 1 つ（登録・編集とも 5 行）。hidden で、その行の金額の欄を指す。文は HTML に固定で書く */
	@Test
	void eachLineHasAHiddenFixedErrorTextPointingToItsAmountField() throws Exception {
		for (String path : new String[] { "/transactions/new", "/transactions/" + transaction.getId() + "/edit" }) {
			String body = page(path);
			assertThat(count(body, "data-calc-error")).as(path).isEqualTo(5);
			assertThat(count(body, ERROR_TEXT)).as(path).isEqualTo(5);
			for (int i = 0; i < 5; i++) {
				Matcher tag = Pattern.compile("<p [^>]*data-calc-target=\"lines" + i + "\\.amount\"[^>]*>").matcher(body);
				assertThat(tag.find()).as(path + "：" + (i + 1) + " 行目").isTrue();
				assertThat(tag.group()).contains("data-calc-error", " hidden", "class=\"calc-error\"");
				assertThat(body.indexOf(tag.group())).as("金額の欄のあと、摘要の前")
						.isGreaterThan(body.indexOf("id=\"lines" + i + ".amount\"")).isLessThan(body.indexOf("id=\"lines" + i + ".description\""));
			}
		}
	}

	/** エラーと同じ赤だが、field-error のクラスは使わない（field-error はテストが数えている。Q035-1） */
	@Test
	void theErrorTextIsRedButIsNotAFieldError() throws Exception {
		String body = page("/transactions/new");
		assertThat(count(body, "class=\"field-error\"")).as("エラーがない画面に field-error はない").isZero();
		String css = mockMvc.perform(get("/css/style.css")).andReturn().getResponse().getContentAsString();
		Matcher rule = Pattern.compile("(?m)^\\.calc-error\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(rule.find()).as("規則「.calc-error」がある").isTrue();
		assertThat(rule.group(1)).contains("color: var(--bs-danger, #c00)", "font-size: var(--hb-font-size-small)");
	}

	/** 説明の段落（JavaScript が動くときだけ見せる、明細の上の 1 回だけの段落）に、式が打てることの 1 文 */
	@Test
	void theExplanationTellsThatFormulasCanBeTyped() throws Exception {
		String body = page("/transactions/new");
		Matcher explain = Pattern.compile("(?s)<p [^>]*data-amount-explain[^>]*>(.*?)</p>").matcher(body);
		assertThat(explain.find()).isTrue();
		assertThat(explain.group(1)).contains("金額の欄には、1280+398-300 のような式も打てます（Enter で計算）。");
	}

	// ---- サーバー（見張り役。式のまま送られても保存しない） ----

	@ParameterizedTest(name = "式のまま [{0}] は保存しない")
	@ValueSource(strings = { "1280+398", "１２８０＋３９８", "1280ー300", "1,280", "=1280", "1280+" })
	void aFormulaSentAsItIsIsNotSaved(String amount) throws Exception {
		long before = countOfMyTransactions();

		String body = postAmount(amount);

		assertThat(body).contains("金額は整数で入力してください");
		assertThat(countOfMyTransactions()).isEqualTo(before);
	}

	/** 計算の結果がマイナスのときは、そのまま欄に入る。サーバーは整数として読み、範囲のエラーにする */
	@Test
	void aNegativeResultIsRejectedByTheRangeCheck() throws Exception {
		long before = countOfMyTransactions();

		String body = postAmount("-200");

		assertThat(body).contains("金額は1円以上1,000,000円以下で入力してください");
		assertThat(countOfMyTransactions()).isEqualTo(before);
	}

}
