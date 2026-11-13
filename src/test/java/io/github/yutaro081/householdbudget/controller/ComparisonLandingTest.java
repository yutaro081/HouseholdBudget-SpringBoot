package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
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
 * 予実比較の着地見込みの 1 行（要件定義書 4.4、設計書 2.3・5.13、issue 018。Q018-1〜Q018-3、Q018-9）。
 * 「今日」は、日本時間の 2026-11-08（秋の 69 日目。D ＝ 91、L ＝ 22）に固定した Clock。
 * 見込み ＝（実績 × 91 × 113 ＋ 予算 × 22²）÷ 91²、100 円単位で切り上げ。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComparisonLandingTest {

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2026, 11, 8).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

	/** 要約カードの大きな数字と予算（issue 020。issue 018 の 1 行の文を置きかえた）。{0}＝見込みの額、{1}＝合計の予算 */
	private static String[] sentence(String amount, String budget) {
		return new String[] { "<strong class=\"comparison-card-forecast\">約 " + amount + "円</strong>",
				"<span class=\"comparison-card-label\">予算</span><strong class=\"comparison-card-figure\">" + budget + "円</strong>" };
	}

	/** 要約カード（&lt;section class="comparison-card"&gt; から &lt;/section&gt; まで） */
	private static String cardOf(String body) {
		int start = body.indexOf("<section class=\"comparison-card\"");
		assertThat(start).as("要約カードがある").isGreaterThanOrEqualTo(0);
		return body.substring(start, body.indexOf("</section>", start));
	}

	/** 見込みが予算を上回るときの文（issue 024：「見込みは、予算を上回ります。」を、「予算より 約 ○円 多い見込みです。」に置きかえた）。この部分が、含まれるかどうかで確かめる */
	private static final String OVER = "多い見込みです。";
	private static final String NOTE = "月ごとにまとめて払うものは、払う日で見込みが上下します。";

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
		userId = userRepository.save(new User("cmplnd1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "cmplnd1", "x"));
	}

	private Category category(String name, Integer budget, Season season, int year) {
		Category c = categoryRepository.save(new Category(userId, name));
		if (budget != null) {
			budgetRepository.save(new Budget(userId, c, year, season, budget));
		}
		return c;
	}

	/**
	 * 「記録は、ずっと昔に始めた」状態にする（issue 020）。どの計算の範囲（過去 3 年、12 季節、直前の季節）にも入らない昔の日付（2001 年 春。DB の下限は 2000-03-01）に、1 円の取引を足す。
	 * 最初の取引の日が季節の途中だと、着地見込みの数え始めの日がその日になる。このテストは、季節の初日から数える前提で書いてあるので、
	 * 最初の取引の日を、季節より前にそろえる。ほかの数字（季節の実績など）は変わらない。
	 */
	private void recordingStartedLongAgo(Category c) {
		transactionRepository.save(new Transaction(userId, LocalDate.of(2001, 3, 1), 1, c, null, vendor, payment));
	}

	private void spend(Category c, int amount) {
		transactionRepository.save(new Transaction(userId, TODAY, amount, c, null, vendor, payment));
	}

	private String page(String path) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get(path).with(login)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	// ---- 出る：今の季節 ----

	@Test
	void theSummaryCardIsShownAboveTheTableForTheCurrentSeason() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 60_000);                // 見込み：80,350.20… → 80,400。予算以下

		String body = page("/comparison");

		assertThat(cardOf(body)).contains(sentence("80,400", "100,000")).contains(NOTE);
		assertThat(body).as("予算以下なので、上回ると出ない").doesNotContain(OVER);
		assertThat(body.indexOf("comparison-card")).as("表の上").isLessThan(body.indexOf("<table"));
		assertThat(body.indexOf("comparison-card")).as("年と季節を選ぶ欄の下").isGreaterThan(body.indexOf("season-select"));
		assertThat(body.indexOf(NOTE)).as("補足は、見込みの数字の下").isGreaterThan(body.indexOf("約 80,400円"));
	}

	@Test
	void theSameCardIsShownWhenTheCurrentSeasonIsGivenInTheUrl() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 60_000);

		assertThat(cardOf(page("/comparison?year=2026&season=autumn"))).contains(sentence("80,400", "100,000"));
	}

	@Test
	void theTotalUsesAllCategoriesForTheActualAndOnlyBudgetedCategoriesForTheBudget() throws Exception {
		Category food = category("食費", 40_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		Category transport = category("交通費", 60_000, Season.AUTUMN, 2026);
		Category unset = category("予算なし科目", null, Season.AUTUMN, 2026);
		spend(food, 30_000);
		spend(transport, 46_000);
		spend(unset, 5_000);                // 合計の実績 81,000、合計の予算 100,000 → 106,427.12… → 106,500

		assertThat(cardOf(page("/comparison"))).contains(sentence("106,500", "100,000")).contains(OVER);
	}

	// ---- 「上回ります」：出る場合と出ない場合（金額で決まる） ----

	@Test
	void theOverLineIsShownOnlyWhenTheRoundedForecastIsOverTheBudget() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 76_000);                // 100,218.33… → 100,300：予算を上回る

		String body = page("/comparison");

		assertThat(cardOf(body)).contains(sentence("100,300", "100,000")).contains(OVER);
		assertThat(body.indexOf(OVER)).as("上回る文は、見込みの数字の下").isGreaterThan(body.indexOf("約 100,300円"));
		assertThat(body.indexOf(OVER)).as("表の上").isLessThan(body.indexOf("<table"));
	}

	/** 予算 91,000 円・69 日目：実績 69,000 円が予定どおり（見込み ＝ 予算）。1 円増えると 91,100 円（上回る）、1 円減っても 91,000 円（上回らない） */
	@Test
	void exactlyOnTheBudgetOrOneYenBelowDoesNotShowTheOverLine() throws Exception {
		Category food = category("食費", 91_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 69_000);

		assertThat(cardOf(page("/comparison"))).contains(sentence("91,000", "91,000")).doesNotContain(OVER);
	}

	@Test
	void oneYenOverTheExpectedPaceShowsTheOverLine() throws Exception {
		Category food = category("食費", 91_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 69_001);                // 91,001.24… → 91,100

		assertThat(cardOf(page("/comparison"))).contains(sentence("91,100", "91,000")).contains(OVER);
	}

	/**
	 * Q018-9 の A：「予算の ○%」は四捨五入。見込みが予算をわずかに上回るとき、トップは「予算の 100%」と出て、予実比較は「上回ります」と出る。
	 * 食い違って見えるが、決まったとおりの動き（今の画面にも、実績が予算を 0.2% 超えるとトップは 100%、予実比較は赤、という同じ仕組みがある）。
	 */
	@Test
	void thePercentOnTheTopCanBe100WhileTheComparisonSaysOver() throws Exception {
		Category food = category("食費", 91_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 69_001);

		String comparison = page("/comparison");
		String top = page("/");

		assertThat(comparison).contains(OVER);
		assertThat(top.replaceAll("<[^>]+>", "")).contains("このペースだと、季節の終わりには 約 91,100円（予算の 100%）の見込みです。");
		assertThat(top).as("トップには「上回ります」を出さない（Q018-2 の b）").doesNotContain(OVER);
	}

	// ---- 出ない ----

	@Test
	void notShownForAPastOrAFutureSeason() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		spend(food, 60_000);
		budgetRepository.save(new Budget(userId, food, 2026, Season.SUMMER, 100_000));
		budgetRepository.save(new Budget(userId, food, 2026, Season.WINTER, 100_000));

		for (String path : new String[] { "/comparison?year=2026&season=summer", "/comparison?year=2026&season=winter",
				"/comparison?year=2025&season=autumn", "/comparison?year=2027&season=autumn" }) {
			String body = page(path);

			assertThat(body).as(path).contains("<table").doesNotContain("comparison-card", OVER, NOTE);
		}
	}

	@Test
	void notShownWhenTheBudgetIsUnsetOrTheBudgetTotalIsZeroOrThereIsNoCategory() throws Exception {
		// 科目が 0 件
		assertThat(page("/comparison")).doesNotContain("comparison-card", NOTE);

		// 予算が未設定（科目はあるが、予算の行がない）
		Category food = category("食費", null, Season.AUTUMN, 2026);
		spend(food, 5_000);
		assertThat(page("/comparison")).contains("<table").doesNotContain("comparison-card", OVER, NOTE);

		// 予算の合計が 0 円
		budgetRepository.save(new Budget(userId, food, 2026, Season.AUTUMN, 0));
		assertThat(page("/comparison")).contains("<table").doesNotContain("comparison-card", OVER, NOTE);
	}

	// ---- 見た目：色も印もない。警告の言葉もない ----

	@Test
	void theCardHasNoColorClassNoMarkAndNoAlertWords() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 90_000);                // 117,602.95… → 117,700（上回る）

		String body = page("/comparison");
		String block = cardOf(body);

		assertThat(block).contains(sentence("117,700", "100,000")).contains(OVER, NOTE);
		assertThat(block).doesNotContain("diff-over", "diff-low", "row-within", "week-over", "week-low", "💮", "text-danger", "alert", "color:");
		assertThat(body).doesNotContain("このままだと", "超えそう", "超えます", "超えてしまう", "予算を超えています", "着地", "予測");
	}

	// ---- 他の人のデータ ----

	@Test
	void otherUsersDataIsNeverMixedIn() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 60_000);
		Long otherId = userRepository.save(new User("cmplnd2", "dummy-hash-for-test")).getId();
		Category otherFood = categoryRepository.save(new Category(otherId, "食費"));
		budgetRepository.save(new Budget(otherId, otherFood, 2026, Season.AUTUMN, 10_000));
		transactionRepository.save(new Transaction(otherId, TODAY, 900_000, otherFood, null,
				vendorRepository.save(new Vendor(otherId, "取引先")), paymentMethodRepository.save(new PaymentMethod(otherId, "支払方法"))));

		String body = page("/comparison");
		assertThat(cardOf(body)).contains(sentence("80,400", "100,000"));
		assertThat(body).doesNotContain(OVER).doesNotContain("900,000");
	}


	// ---- 差の文（予算より 約 ○円 少ない・多い見込みです。同じなら「予算どおりの見込みです。」。多いときだけ赤。issue 024） ----

	@Test
	void theVerdictSentenceTellsHowMuchMoreOrLessTheForecastIsThanTheBudget() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 76_000);                // 見込み 100,300、予算 100,000 → 300 円 多い

		String more = cardOf(page("/comparison"));
		assertThat(more).contains("予算より 約 300円 多い見込みです。").doesNotContain("少ない見込みです。", "予算どおりの見込みです。");
		assertThat(more).as("多いときだけ赤").contains("comparison-card-verdict comparison-card-verdict-over");
	}

	@Test
	void aLessVerdictAndASameVerdictAreNotRed() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 60_000);                // 見込み 80,400 → 19,600 円 少ない（実績 60,000、季節の 72 日目）

		String less = cardOf(page("/comparison"));
		assertThat(less).contains("予算より 約 19,600円 少ない見込みです。").doesNotContain(OVER, "予算どおりの見込みです。", "comparison-card-verdict-over");
		assertThat(less).contains("class=\"comparison-card-verdict\"");
	}

	@Test
	void whenTheForecastIsExactlyTheBudgetTheSentenceSaysAsPlannedAndIsNotRed() throws Exception {
		Category food = category("食費", 91_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 69_000);                // 見込み ＝ 予算 ＝ 91,000

		String card = cardOf(page("/comparison"));

		assertThat(card).contains("予算どおりの見込みです。", "class=\"comparison-card-verdict\"").doesNotContain("少ない見込みです。", OVER, "comparison-card-verdict-over");
	}

	/** 差は、表の見込みとの差の列（合計行）と同じ値。表示する見込みの額（100 円単位に切り上げ）と予算の差 */
	@Test
	void theVerdictAmountIsTheSameAsTheTotalGapInTheTable() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 90_000);                // 117,700 → 17,700 円 多い

		String body = page("/comparison");

		assertThat(cardOf(body)).contains("予算より 約 17,700円 多い見込みです。");
		assertThat(body.substring(body.indexOf("class=\"total-row\""))).contains("-17,700");
	}

	/** 差の文の金額は、百万円を超える額でも、3 桁ごとにカンマが付く（メッセージの引数 {0} が、MessageFormat で書式付きになるため。2 本目の確かめ：「約 1234000円」にならない） */
	@Test
	void theVerdictAmountOfMillionsHasCommas() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, 1_000_000);             // 見込みは 100 万円台 → 予算との差も 100 万円台の「多い」

		String card = cardOf(page("/comparison"));

		assertThat(card).containsPattern("予算より 約 [0-9]{1,3}(,[0-9]{3}){2}円 多い見込みです。");
		assertThat(card).doesNotContainPattern("約 [0-9]{4,}円");
	}
}
