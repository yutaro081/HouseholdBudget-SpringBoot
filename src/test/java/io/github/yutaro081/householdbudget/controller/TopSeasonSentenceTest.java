package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.TopService;
import jakarta.persistence.EntityManager;

/**
 * トップの季節のカードの文（要件定義書 4.5、設計書 2.3、issue 020 の見直し）の確かめ。
 * 文を 2 行に分け、2 行目の「約 ○円（予算の ○%）」だけを大きく（1.25rem）する。見込みが予算を超えるときは「予算の ○%」だけを赤にする（Service が決める）。
 * 「今日」は 2026-11-11（秋の 72 日目。79％）に固定する（FixedClock20261111Config）。
 * 食費（予算 130,000、使った 84,326）→ 65％ 使った。季節の見込み（84,326 × 10,010 ＋ 130,000 × 361）÷ 8,281 ＝ 107,599.5… → 107,600（予算の 83％）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock20261111Config.class)
@Transactional
class TopSeasonSentenceTest {

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

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
	private BudgetRepository budgetRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private TopService topService;

	private Long userId;
	private Vendor vendor;
	private PaymentMethod payment;
	private RequestPostProcessor login;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("seasonsen1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "seasonsen1", "x"));
	}

	/** 科目を作り、今日（11/11）の季節に予算と取引を入れる。最初の取引の日は昔（見込みの数え始めが季節の初日になるように） */
	private void food(int budget, int spent) {
		Category food = categoryRepository.save(new Category(userId, "食費"));
		budgetRepository.save(new Budget(userId, food, 2026, Season.AUTUMN, budget));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2001, 3, 1), 1, food, null, vendor, payment));
		transactionRepository.save(new Transaction(userId, SUNDAY, spent, food, null, vendor, payment));
	}

	private String page() throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get("/").with(login)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String sentence(String body) {
		int start = body.indexOf("class=\"top-season-pace\"");
		assertThat(start).as("季節の文がある").isGreaterThanOrEqualTo(0);
		return body.substring(body.indexOf('>', start) + 1, body.indexOf("</p>", start));
	}

	private static String text(String html) {
		return html.replaceAll("<[^>]+>", "");
	}

	// ---- 2 行に分ける ----

	@Test
	void theSentenceIsSplitIntoTwoLines() throws Exception {
		food(130_000, 84_326);

		String p = sentence(page());

		assertThat(p.split("class=\"top-season-pace-line\"", -1).length - 1).as("2 行").isEqualTo(2);
		assertThat(text(p.substring(p.indexOf("class=\"top-season-pace-line\""), p.lastIndexOf("class=\"top-season-pace-line\""))))
				.as("1 行目").contains("季節は 79% 過ぎて、予算は 65% 使いました。").doesNotContain("このペースだと");
		assertThat(text(p.substring(p.lastIndexOf("class=\"top-season-pace-line\""))))
				.as("2 行目").contains("このペースだと、季節の終わりには 約 107,600円（予算の 83%）の見込みです。");
	}

	/** 2 行目の「約 ○円（予算の ○%）」の部分だけが、大きい（top-season-pace-big）。ほかの部分には付かない */
	@Test
	void onlyTheForecastPartOfTheSecondLineIsBig() throws Exception {
		food(130_000, 84_326);

		String p = sentence(page());

		int big = p.indexOf("<span class=\"top-season-pace-big\">");
		assertThat(big).isGreaterThanOrEqualTo(0);
		assertThat(text(p.substring(big, p.indexOf("</span></span>", big)))).as("大きい部分の文字").isEqualTo("約 107,600円（予算の 83%）");
		assertThat(p).contains("<span class=\"top-season-pace-percent\">予算の 83%</span>");
		assertThat(p.split("top-season-pace-big", -1).length - 1).as("大きいのは 1 か所").isEqualTo(1);
		assertThat(text(p.substring(0, p.indexOf("top-season-pace-big")))).as("big の前の文字").doesNotContain("107,600");
	}

	// ---- 見込みが予算を超えるとき、「予算の ○%」だけを赤にする（Service が決める） ----

	@Test
	void onlyThePercentIsRedWhenTheForecastExceedsTheBudget() throws Exception {
		food(10_000, 12_000);                       // 見込み 15,000（予算の 150％）

		String p = sentence(page());

		assertThat(p).contains("<span class=\"top-season-pace-percent top-season-pace-over\">予算の 150%</span>");
		assertThat(p.split("top-season-pace-over", -1).length - 1).as("赤は、その部分だけ").isEqualTo(1);
	}

	@Test
	void nothingIsRedWhenTheForecastIsWithinTheBudget() throws Exception {
		food(130_000, 84_326);

		assertThat(sentence(page())).doesNotContain("top-season-pace-over");
	}

	/** 見込みが予算ちょうど（100％）：超えていないので、赤にしない */
	@Test
	void exactlyOnTheBudgetIsNotRed() throws Exception {
		food(91_000, 72_000);                       // 見込み ＝ 91,000（割り切れる）

		String p = sentence(page());

		assertThat(text(p)).contains("約 91,000円（予算の 100%）");
		assertThat(p).doesNotContain("top-season-pace-over");
	}

	@Test
	void theServiceDecidesWhetherThePercentIsRed() {
		food(10_000, 12_000);
		entityManager.flush();
		entityManager.clear();
		assertThat(topService.top(userId, LocalDate.of(2026, 11, 11)).landingOverBudget()).isTrue();
	}

	@Test
	void theServiceSaysNotOverWhenWithinTheBudgetOrThereIsNoForecast() {
		food(130_000, 84_326);
		entityManager.flush();
		entityManager.clear();
		assertThat(topService.top(userId, LocalDate.of(2026, 11, 11)).landingOverBudget()).isFalse();
	}

	// ---- CSS：2 行目の見込みの部分は 1.25rem（今週のひとこと 1.0625rem より大きい）。赤は予算超過の色 ----

	private static String css() throws Exception {
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	@Test
	void theForecastPartIs125remAndLargerThanTheWeeklySentence() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-season-pace-big")).contains("font-size: 1.25rem");
		assertThat(rule(css, ".top-week-pick")).contains("font-size: 1.0625rem");
		assertThat(rule(css, ".top-season-pace")).as("ほかは今のまま（濃いターコイズの太字）").contains("color: var(--hb-teal-text)", "font-weight: 700");
	}

	@Test
	void eachLineIsABlockAndTheOverPartIsRed() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-season-pace-line")).contains("display: block");
		assertThat(rule(css, ".top-season-pace-over")).contains("color: #c62828");
	}
}
