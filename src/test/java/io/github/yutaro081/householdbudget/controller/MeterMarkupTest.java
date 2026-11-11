package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
import jakarta.persistence.EntityManager;

/**
 * メーターの HTML のテスト（設計書 2.3「メーターの見た目」、issue 020）。
 * ・「予算」の小さな文字は、トップの季節のメーターと予実比較のカードだけ。今週の科目ごとのメーターには付けない。
 * ・予算を超えたとき（実績 ＞ 予算）だけ、棒に top-meter-over（赤）が付く。トップの季節、今週（科目と合計）、予実比較のカード。
 * 「今日」は 2026-11-11（水曜。秋の 72 日目。今週は 11/8〜11/14）に固定する（FixedClock20261111Config。ほかのテストと共有する）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock20261111Config.class)
@Transactional
class MeterMarkupTest {

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
	private static final String LABEL = "<p class=\"top-meter-budget-label\">予算</p>";
	private static final String OVER_BAR = "class=\"top-meter-bar top-meter-over\"";

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

	private Long userId;
	private Vendor vendor;
	private PaymentMethod payment;
	private RequestPostProcessor login;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("meter1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "meter1", "x"));
	}

	private Category category(String name, int budget) {
		Category c = categoryRepository.save(new Category(userId, name));
		budgetRepository.save(new Budget(userId, c, 2026, Season.AUTUMN, budget));
		return c;
	}

	private void spend(Category c, int amount) {
		transactionRepository.save(new Transaction(userId, SUNDAY, amount, c, null, vendor, payment));
	}

	private String page(String path) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get(path).with(login)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/** 今週の科目ごとのメーターの部分（&lt;section class="top-weeks …"&gt; の中） */
	private static String weeks(String body) {
		int start = body.indexOf("top-weeks");
		assertThat(start).as("今週の科目ごとのメーターがある").isGreaterThanOrEqualTo(0);
		return body.substring(start);
	}

	/** 季節のメーター（&lt;section class="top-section top-season"&gt; の中） */
	private static String seasonCard(String body) {
		int start = body.indexOf("top-season-grid");
		assertThat(start).as("季節のメーターがある").isGreaterThanOrEqualTo(0);
		return body.substring(start, body.indexOf("</section>", start));
	}

	/** 科目名を含む今週の行（&lt;li&gt; から &lt;/li&gt; まで） */
	private static String weekRow(String body, String name) {
		String w = weeks(body);
		int at = w.indexOf(">" + name + "<");
		assertThat(at).as("「%s」の行がある", name).isGreaterThanOrEqualTo(0);
		return w.substring(w.lastIndexOf("<li", at), w.indexOf("</li>", at));
	}

	// ---- 「予算」の小さな文字：季節のメーターとカードだけ ----

	@Test
	void theSeasonMeterOnTheTopHasTheBudgetLabelAndTheWeeklyMetersDoNot() throws Exception {
		Category food = category("食費", 130_000);
		spend(food, 3_000);

		String body = page("/");

		assertThat(seasonCard(body)).contains(LABEL);
		assertThat(count(body, "top-meter-budget-label")).as("文字は季節のメーターに 1 つだけ").isEqualTo(1);
		assertThat(weeks(body)).as("今週の科目ごとのメーターは、縦線だけ（文字は書かない）").doesNotContain("top-meter-budget-label");
		assertThat(seasonCard(body).indexOf(LABEL)).as("メーターの下").isGreaterThan(seasonCard(body).indexOf("class=\"top-meter\""));
	}

	@Test
	void theComparisonCardHasTheBudgetLabelUnderTheMeter() throws Exception {
		Category food = category("食費", 130_000);
		spend(food, 3_000);

		String body = page("/comparison");
		String card = body.substring(body.indexOf("class=\"comparison-card\""), body.indexOf("<table"));

		assertThat(card).contains(LABEL);
		assertThat(card.indexOf(LABEL)).isGreaterThan(card.indexOf("class=\"top-meter\""));
		assertThat(count(body, "top-meter-budget-label")).isEqualTo(1);
	}

	// ---- 予算を超えたときだけ、棒が赤 ----

	@Test
	void noBarIsRedWhenNothingIsOverTheBudget() throws Exception {
		Category food = category("食費", 130_000);
		spend(food, 3_000);                       // 週の予算 10,000 円以下、季節の予算 130,000 円以下

		assertThat(page("/")).doesNotContain("top-meter-over");
		assertThat(page("/comparison")).doesNotContain("top-meter-over");
	}

	@Test
	void theSeasonBarOnTheTopAndTheCardBarAreRedWhenTheSeasonBudgetIsExceeded() throws Exception {
		Category food = category("食費", 10_000);
		spend(food, 12_000);                      // 季節の予算 10,000 円を超える

		String top = page("/");
		String comparison = page("/comparison");

		assertThat(seasonCard(top)).contains(OVER_BAR);
		assertThat(comparison.substring(comparison.indexOf("class=\"comparison-card\""), comparison.indexOf("<table"))).contains(OVER_BAR);
	}

	@Test
	void theWeeklyBarsAreRedOnlyForTheRowsAndTheTotalOverTheWeeklyBudget() throws Exception {
		Category food = category("食費", 130_000);
		Category transport = category("交通費", 130_000);
		spend(food, 20_000);                      // 週の予算 10,000 円を超える（季節の予算 130,000 円は超えない）
		spend(transport, 1_000);                  // 超えない

		String body = page("/");

		assertThat(weekRow(body, "食費")).contains(OVER_BAR);
		assertThat(weekRow(body, "交通費")).doesNotContain("top-meter-over");
		assertThat(weekRow(body, "合計")).as("合計の週の予算 20,000 円に対して、実績 21,000 円").contains(OVER_BAR);
		assertThat(seasonCard(body)).as("季節の予算は超えていない").doesNotContain("top-meter-over");
	}
}
