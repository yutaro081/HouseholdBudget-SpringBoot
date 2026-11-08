package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;

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
 * トップの並び・見出し・季節のメーターの文・今週の進み具合の HTML のテスト（設計書 2.3、5.11、issue 017 の 1 回目）。
 * 2 列の見え方（幅 992px の切り替え）や色は MockMvc では見えないので、ブラウザで確かめる。ここでは HTML の順と中身を確かめる。
 * 「今日」は、日本時間の 2026-11-08（日曜。秋。今週は 11/8〜11/14）を基本に、テストごとに変えられる Clock で固定する。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TopLayoutTest {

	static class SettableClock extends Clock {
		private volatile LocalDate date = LocalDate.of(2026, 11, 8);

		void set(LocalDate date) {
			this.date = date;
		}

		@Override
		public ZoneId getZone() {
			return AppConfig.JAPAN;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return date.atStartOfDay(AppConfig.JAPAN).toInstant();
		}
	}

	@TestConfiguration
	static class SettableClockConfig {
		@Bean
		@Primary
		SettableClock settableClock() {
			return new SettableClock();
		}
	}

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

	@Autowired
	private SettableClock clock;
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

	@BeforeEach
	void resetDate() {
		clock.set(SUNDAY);
	}

	private record Owner(Long id, RequestPostProcessor login, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, user(new LoginUser(id, username, "x")),
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category category(Owner o, String name, Integer autumnBudget) {
		Category c = categoryRepository.save(new Category(o.id(), name));
		if (autumnBudget != null) {
			budgetRepository.save(new Budget(o.id(), c, 2026, Season.AUTUMN, autumnBudget));
		}
		return c;
	}

	private void spend(Owner o, Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(o.id(), date, amount, c, null, o.vendor(), o.paymentMethod()));
	}

	private String topBody(Owner o) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get("/").with(o.login()))
				.andExpect(status().isOk())
				.andExpect(view().name("top/index"))
				.andReturn().getResponse().getContentAsString();
	}

	/** 左右の列の中身を取り出す */
	private static String leftColumn(String body) {
		int left = body.indexOf("top-col-left");
		int right = body.indexOf("top-col-right");
		assertThat(left).as("左の列がある").isGreaterThanOrEqualTo(0);
		assertThat(right).as("右の列が左の列のあとにある").isGreaterThan(left);
		return body.substring(left, right);
	}

	private static String rightColumn(String body) {
		int right = body.indexOf("top-col-right");
		assertThat(right).as("右の列がある").isGreaterThanOrEqualTo(0);
		return body.substring(right);
	}

	// ---- 本体の幅・見出しの行 ----

	@Test
	void theMainIsTheWideTopLayoutAndTheTabNameStaysTop() throws Exception {
		Owner a = createOwner("toplay01");
		category(a, "食費", 30_000);

		String body = topBody(a);

		assertThat(body).contains("<main class=\"container top-wide").doesNotContain("container narrow");
		assertThat(body).contains("<title>トップ</title>");
	}

	@Test
	void headingRowShowsTheSeasonTodayAndTheDaysLeft() throws Exception {
		Owner a = createOwner("toplay02");
		category(a, "食費", 30_000);

		String body = topBody(a);

		assertThat(body).contains("<h1>2026年 秋（9月〜11月）</h1>");
		assertThat(body).contains("今日は 11月8日（季節の終わりまで、あと 23 日）");
	}

	@Test
	void headingRowForWinterAndTheLastDayOfTheSeason() throws Exception {
		clock.set(LocalDate.of(2027, 2, 28));
		Owner a = createOwner("toplay03");
		category(a, "食費", null);

		String body = topBody(a);

		assertThat(body).contains("<h1>2026年 冬（12月〜2月）</h1>");
		assertThat(body).contains("今日は 2月28日（季節の終わりまで、あと 1 日）");
	}

	@Test
	void theRegisterButtonHasAPlusIconAndTheComparisonButtonDoesNot() throws Exception {
		Owner a = createOwner("toplay04");
		category(a, "食費", 30_000);

		String body = topBody(a);

		assertThat(body).containsPattern(Pattern.compile(
				"<a class=\"btn btn-primary\" href=\"/transactions/new\"><svg[^>]*>.*?</svg>取引を登録</a>", Pattern.DOTALL));
		assertThat(body).containsPattern("<a class=\"btn btn-outline-secondary\" href=\"/comparison\">予実比較を見る</a>");
	}

	// ---- 季節のメーター ----

	@Test
	void seasonMeterShowsTheTotalRemainingAndThePercentages() throws Exception {
		Owner a = createOwner("toplay05");
		Category food = category(a, "食費", 130_000);
		spend(a, food, SUNDAY, 84_326);

		String body = topBody(a);

		assertThat(body).contains("この季節に使った合計", "84,326円", "/ 予算 130,000円");
		// 「残り」は補足の灰色のまま、金額だけを太字（強調）にする（issue 017 の見直し）
		assertThat(body).contains("<p class=\"top-season-remaining\">残り <strong>45,674円</strong></p>");
		assertThat(body).contains("予算のうち 65% を使った");
		assertThat(body).contains("縦の線は『今日』（季節の 76%）");
	}

	@Test
	void whenOverTheBudgetThePercentageGoesPastOneHundredAndTheBarStopsAtOneHundred() throws Exception {
		Owner a = createOwner("toplay06");
		Category food = category(a, "食費", 30_000);
		spend(a, food, LocalDate.of(2026, 9, 3), 36_000);

		String body = topBody(a);

		assertThat(body).contains("予算のうち 120% を使った");
		assertThat(body).contains("width: 100.0%");
	}

	@Test
	void withoutABudgetThereIsNoPercentageAndNoBarButTheNotice() throws Exception {
		Owner a = createOwner("toplay07");
		Category food = category(a, "食費", null);
		spend(a, food, SUNDAY, 500);

		String body = topBody(a);

		assertThat(body).contains("今の季節の予算がまだありません");
		assertThat(body).doesNotContain("予算のうち").doesNotContain("縦の線は『今日』（季節の");
		assertThat(body).contains("500円");
	}

	@Test
	void withAZeroBudgetThePercentageIsNotShown() throws Exception {
		Owner a = createOwner("toplay08");
		Category food = category(a, "食費", 0);
		spend(a, food, SUNDAY, 500);

		String body = topBody(a);

		assertThat(body).doesNotContain("予算のうち");
		assertThat(body).contains("500円");
	}

	// ---- 2 列 ----

	@Test
	void theTwoColumnsKeepTheOrderPieThenHitokotoThenProgress() throws Exception {
		Owner a = createOwner("toplay09");
		Category food = category(a, "食費", 30_000);
		spend(a, food, SUNDAY, 1_234);

		String body = topBody(a);

		assertThat(body).contains("top-columns");
		String left = leftColumn(body);
		String right = rightColumn(body);
		assertThat(left).contains("今週 何に使ったか", "きょうのひとこと").doesNotContain("今週の進み具合");
		assertThat(left.indexOf("今週 何に使ったか")).isLessThan(left.indexOf("きょうのひとこと"));
		assertThat(right).contains("今週の進み具合").doesNotContain("きょうのひとこと").doesNotContain("今週 何に使ったか");
		assertThat(body.indexOf("この季節に使った合計")).isLessThan(body.indexOf("top-columns"));
	}

	@Test
	void pieCardShowsThePeriodWithWeekdaysAndTheNote() throws Exception {
		Owner a = createOwner("toplay10");
		Category food = category(a, "食費", 30_000);
		spend(a, food, SUNDAY, 1_234);

		String left = leftColumn(topBody(a));

		assertThat(left).contains("11月8日（日）〜 11月14日（土）");
		assertThat(left).contains("今週の合計", "1,300円", "食費");
		assertThat(left).contains("金額は 100円単位で切り上げ。今週まだ使っていない科目は出ません。");
		assertThat(left).contains("conic-gradient(");
	}

	@Test
	void theCardWithoutTransactionsThisWeekStillShowsTheMessage() throws Exception {
		Owner a = createOwner("toplay11");
		category(a, "食費", 30_000);

		String left = leftColumn(topBody(a));

		assertThat(left).contains("今週はまだ使っていません").doesNotContain("conic-gradient(");
	}

	// ---- 今週の進み具合 ----

	@Test
	void progressCardHasTheTotalRowFirstThenTheCategoriesAsTwoLineItems() throws Exception {
		Owner a = createOwner("toplay12");
		Category food = category(a, "食費", 30_000);
		category(a, "交通費", 30_000);
		spend(a, food, SUNDAY, 1_234);

		String right = rightColumn(topBody(a));

		assertThat(right).contains("<ul class=\"top-weeks");
		assertThat(right).doesNotContain("<table");
		assertThat(right).contains("縦の線は『今日』（週の 1 日目）");
		assertThat(right).contains("週の予算＝今日が入っている季節の予算 ÷ 13（100円単位で切り上げ）");
		int total = right.indexOf("top-week-total");
		assertThat(total).isGreaterThanOrEqualTo(0);
		assertThat(total).isLessThan(right.indexOf("食費"));
		assertThat(right.indexOf("食費")).isLessThan(right.indexOf("交通費"));
		assertThat(right).contains("1,300円", "2,400円", "合計");
	}

	@Test
	void eachRowShowsAShortStatusSentenceNextToItsColor() throws Exception {
		Owner a = createOwner("toplay13");
		Category over = category(a, "超過", 30_000);
		category(a, "少なめ", 30_000);
		Category within = category(a, "予算内", 30_000);
		category(a, "未設定", null);
		spend(a, over, SUNDAY, 2_401);       // 2,500 > 2,400 → 100 円オーバー
		spend(a, within, SUNDAY, 2_000);

		String right = rightColumn(topBody(a));

		assertThat(right).contains("赤：100円オーバー", "青：1/3以下", "予算内", "予算なし");
		assertThat(right).contains("week-over", "week-low", "week-within");
		assertThat(right).contains("<span aria-hidden=\"true\">💮</span>");
		assertThat(right).doesNotContain("予算を超えています");
	}

	// ---- 科目が 0 件 ----

	@Test
	void withNoCategoryThereAreNoColumnsButTheNoticeAndTheHitokoto() throws Exception {
		Owner a = createOwner("toplay14");

		String body = topBody(a);

		assertThat(body).contains("<h1>2026年 秋（9月〜11月）</h1>");
		assertThat(body).contains("先に科目を追加してください", "きょうのひとこと");
		assertThat(body).doesNotContain("top-columns").doesNotContain("今週の進み具合").doesNotContain("この季節に使った合計");
	}

	// ---- 安全 ----

	@Test
	void categoryNamesAreEscapedInTheProgressCard() throws Exception {
		Owner a = createOwner("toplay15");
		category(a, "<i>x</i>", 30_000);

		String body = topBody(a);

		assertThat(body).doesNotContain("<i>x</i>").contains("&lt;i&gt;x&lt;/i&gt;");
	}

	// ---- issue 017（見直し）：季節のメーターの下のペースの一言 ----

	/** 季節のメーターのカード（<section class="… top-season">）の中身だけを取り出す */
	private static String seasonCard(String body) {
		int start = body.indexOf("top-season");
		assertThat(start).isGreaterThanOrEqualTo(0);
		start = body.lastIndexOf("<section", start);
		return body.substring(start, body.indexOf("</section>", start));
	}

	@Test
	void slowPaceIsShownBelowTheMeterInsideTheSeasonCard() throws Exception {
		Owner a = createOwner("toplay20");
		Category food = category(a, "食費", 130_000);
		spend(a, food, SUNDAY, 84_326);       // 65％ ／ 日の進み方 76％ → 差 −11

		String body = topBody(a);

		assertThat(seasonCard(body)).contains("日の進み方より、使い方がゆっくりです。");
		assertThat(body.indexOf("日の進み方より、使い方がゆっくりです。")).isGreaterThan(body.indexOf("縦の線は『今日』"));
		assertThat(body.indexOf("日の進み方より、使い方がゆっくりです。")).isLessThan(body.indexOf("top-columns"));
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/** 位置：カードのいちばん下ではなく、右側の棒（top-season-bar）の中の、棒のすぐ下。左側（合計）の中には入らない */
	@Test
	void thePaceSentenceIsInTheRightColumnRightBelowTheBar() throws Exception {
		Owner a = createOwner("toplay27");
		Category food = category(a, "食費", 130_000);
		spend(a, food, SUNDAY, 84_326);

		String card = seasonCard(topBody(a));

		int left = card.indexOf("top-season-main");
		int right = card.indexOf("top-season-bar");
		int meterEnd = card.indexOf("top-meter-today", right);
		int pace = card.indexOf("top-season-pace");
		assertThat(left).isGreaterThanOrEqualTo(0);
		assertThat(right).as("右側（棒）が左側のあとにある").isGreaterThan(left);
		assertThat(card.substring(left, right)).as("左側（合計）の中には入らない").doesNotContain("top-season-pace");
		assertThat(pace).as("棒のすぐ下").isGreaterThan(meterEnd);
		// 右側（top-season-bar）の始まりから一言までの間に、棒と「今日」の線がある（= 棒のすぐ下）
		assertThat(card.substring(right, pace)).contains("top-meter-bar", "top-meter-today");
		// 一言が右側の <div> の中にある：右側の <div> を開いてから一言までの間で、開いた <div> が閉じた数より 1 つ多い（右側がまだ閉じていない）
		String fromRightDiv = card.substring(card.lastIndexOf("<div", right), pace);
		assertThat(count(fromRightDiv, "<div") - count(fromRightDiv, "</div>")).as("右側の <div> の中にある").isEqualTo(1);
	}

	@Test
	void sameAndFastPaceSentences() throws Exception {
		Owner a = createOwner("toplay21");
		Category food = category(a, "食費", 100_000);

		spend(a, food, SUNDAY, 76_000);       // 76％ ／ 76％ → ほぼ同じ
		assertThat(seasonCard(topBody(a))).contains("日の進み方と、使い方はほぼ同じです。");

		spend(a, food, SUNDAY, 14_000);       // 合計 90,000（90％）→ 差 +14 → 速め
		assertThat(seasonCard(topBody(a))).contains("日の進み方より、使い方が速めです。");
	}

	@Test
	void exactlyFivePointsOfDifferenceIsSlowOrFast() throws Exception {
		Owner a = createOwner("toplay22");
		Category food = category(a, "食費", 100_000);

		spend(a, food, SUNDAY, 71_000);       // 71％ ／ 76％ → 差 −5
		assertThat(seasonCard(topBody(a))).contains("日の進み方より、使い方がゆっくりです。");

		spend(a, food, SUNDAY, 10_000);       // 81％ → 差 +5
		assertThat(seasonCard(topBody(a))).contains("日の進み方より、使い方が速めです。");
	}

	@Test
	void noPaceSentenceWithoutABudgetOrWithAZeroBudget() throws Exception {
		Owner a = createOwner("toplay23");
		Category food = category(a, "食費", null);
		spend(a, food, SUNDAY, 500);
		assertThat(seasonCard(topBody(a))).doesNotContain("日の進み方より", "日の進み方と、使い方");

		Owner b = createOwner("toplay24");
		Category zero = category(b, "食費", 0);
		spend(b, zero, SUNDAY, 500);
		assertThat(seasonCard(topBody(b))).doesNotContain("日の進み方より", "日の進み方と、使い方");
	}

	@Test
	void theSentenceHasNoColorClassAndNoPrediction() throws Exception {
		Owner a = createOwner("toplay25");
		Category food = category(a, "食費", 100_000);
		spend(a, food, SUNDAY, 90_000);

		String body = topBody(a);

		assertThat(body).containsPattern("<p class=\"top-season-pace\">日の進み方より、使い方が速めです。</p>");
		assertThat(body).doesNotContain("このままだと", "超えそう", "着地");
	}

	@Test
	void theSentenceIsNotShownWhenThereIsNoCategory() throws Exception {
		Owner a = createOwner("toplay26");

		assertThat(topBody(a)).doesNotContain("日の進み方より", "日の進み方と、使い方");
	}

}
