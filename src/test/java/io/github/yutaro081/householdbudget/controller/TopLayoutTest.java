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

	/**
	 * 「記録は、ずっと昔に始めた」状態にする（issue 020）。どの計算の範囲（過去 3 年、12 季節、直前の季節）にも入らない昔の日付（2001 年 春。DB の下限は 2000-03-01）に、1 円の取引を足す。
	 * 最初の取引の日が季節の途中だと、数え始めの日がその日になる。このテストは、季節の初日から数える前提で書いてあるので、
	 * 最初の取引の日を、季節より前にそろえる。ほかの数字（季節の実績、記録のある年など）は変わらない。
	 */
	private void recordingStartedLongAgo(Owner o, Category c) {
		spend(o, c, LocalDate.of(2001, 3, 1), 1);
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
	void whenOverTheBudgetThePercentageGoesPastOneHundredAndTheBarShowsTheRedPartPastTheBudget() throws Exception {
		Owner a = createOwner("toplay06");
		Category food = category(a, "食費", 30_000);
		spend(a, food, LocalDate.of(2026, 9, 3), 36_000);

		String body = topBody(a);

		assertThat(body).contains("予算のうち 120% を使った");
		// issue 020：予算（100％）は棒の 70％ の位置。120％ → 通常色は 70.0％ まで、赤は 70.0％ から 14.0％（棒は 100％ で止まらず、右端の約 143％ まで伸びる）
		assertThat(body).contains("style=\"width: 70.0%\"", "style=\"left: 70.0%; width: 14.0%\"");
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
	void theLandingSentenceIsShownBelowTheMeterInsideTheSeasonCard() throws Exception {
		Owner a = createOwner("toplay20");
		Category food = category(a, "食費", 130_000);
		recordingStartedLongAgo(a, food);
		spend(a, food, SUNDAY, 84_326);       // 65％ ／ 日の進み方 76％。見込み：112,310.62… → 112,400（予算の 86％）

		String body = topBody(a);

		assertThat(seasonCard(body)).contains("季節は 76% 過ぎて、予算は 65% 使いました。このペースだと、季節の終わりには約 112,400 円（予算の 86%）の見込みです。");
		assertThat(body.indexOf("このペースだと")).isGreaterThan(body.indexOf("縦の線は『今日』"));
		assertThat(body.indexOf("このペースだと")).isLessThan(body.indexOf("top-columns"));
	}

	// ---- 今週 何に使ったか（issue 017 の見直し：期間の位置、頭の行なし、今週の判定） ----

	/** 円グラフの一覧の表（頭の行なし）を取り出す */
	private static String pieTable(String body) {
		String left = leftColumn(body);
		int start = left.indexOf("<table");
		assertThat(start).as("一覧の表がある").isGreaterThanOrEqualTo(0);
		return left.substring(start, left.indexOf("</table>", start));
	}

	/** 一覧の 1 行（科目名を含む <tr> から </tr> まで） */
	private static String pieRow(String body, String name) {
		String table = pieTable(body);
		int at = table.indexOf(">" + name + "<");
		assertThat(at).as(name + " の行がある").isGreaterThanOrEqualTo(0);
		int start = table.lastIndexOf("<tr", at);
		return table.substring(start, table.indexOf("</tr>", at));
	}

	@Test
	void thePeriodIsOnTheSameRowAsTheHeadingAndTheListHasNoHeaderRow() throws Exception {
		Owner a = createOwner("toplay30");
		spend(a, category(a, "食費", 30_000), SUNDAY, 1_234);

		String body = topBody(a);
		String left = leftColumn(body);

		assertThat(left).containsPattern("<div class=\"top-pie-head\">\\s*<h2>今週 何に使ったか</h2>\\s*<p class=\"top-note\">11月8日（日）〜 11月14日（土）</p>\\s*</div>");
		String table = pieTable(body);
		assertThat(table).doesNotContain("<thead", "<th", "visually-hidden\">色");
		assertThat(table).as("頭の行の代わりに、読み上げ用の名前").contains("aria-label=\"今週の科目ごとの金額と割合\"");
	}

	@Test
	void theHeadingRowIsAlsoShownWhenNothingWasSpentThisWeek() throws Exception {
		Owner a = createOwner("toplay31");
		category(a, "食費", 30_000);

		String left = leftColumn(topBody(a));

		assertThat(left).containsPattern("<div class=\"top-pie-head\">\\s*<h2>今週 何に使ったか</h2>");
		assertThat(left).contains("今週はまだ使っていません").doesNotContain("<table");
	}

	/** 判定は「今週の進み具合」と同じ。赤・青は金額の文字の色（クラスは行に付ける）、💮 は科目名の横 */
	@Test
	void eachListRowShowsTheJudgementOfTheWeek() throws Exception {
		Owner a = createOwner("toplay32");
		spend(a, category(a, "食費", 40_000), SUNDAY, 4_200);        // 週の予算 3,100 → 赤
		spend(a, category(a, "雑費", 30_000), SUNDAY, 2_000);        // 2,400 → 💮
		spend(a, category(a, "交通費", 25_000), SUNDAY, 600);        // 2,000 → 青
		spend(a, category(a, "予算なし科目", null), SUNDAY, 500);     // 未設定 → 色なし

		String body = topBody(a);
		String mark = "<span aria-hidden=\"true\">💮</span><span class=\"visually-hidden\">予算内</span>";

		String food = pieRow(body, "食費");
		assertThat(food).contains("class=\"pie-over\"", "<td class=\"num\">4,200円</td>").doesNotContain("💮", "pie-low");
		String misc = pieRow(body, "雑費");
		assertThat(misc).contains(mark).doesNotContain("pie-over", "pie-low");
		assertThat(misc.indexOf(">雑費<")).as("💮 は科目名の横").isLessThan(misc.indexOf("💮"));
		assertThat(misc.indexOf("💮")).as("金額より前").isLessThan(misc.indexOf("2,000円"));
		String transport = pieRow(body, "交通費");
		assertThat(transport).contains("class=\"pie-low\"").doesNotContain("💮", "pie-over");
		String unset = pieRow(body, "予算なし科目");
		assertThat(unset).doesNotContain("💮", "pie-over", "pie-low");
	}

	@Test
	void theOtherRowHasNoJudgementAndNoRowHasABackgroundClass() throws Exception {
		Owner a = createOwner("toplay33");
		for (int i = 1; i <= 8; i++) {
			spend(a, category(a, "科目" + i, 0), SUNDAY, i * 100);   // 予算 0 円で実績あり → どれも赤
		}

		String body = topBody(a);
		String other = pieRow(body, "その他");

		assertThat(other).doesNotContain("pie-over", "pie-low", "💮");
		assertThat(pieRow(body, "科目8")).contains("class=\"pie-over\"");
		assertThat(pieTable(body)).as("行の背景（薄いアプリコット）の目印は付けない").doesNotContain("week-within", "pie-within", "row-within");
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

	/** 予算 100,000 円。2026-11-08（69 日目、D ＝ 91、L ＝ 22）。76,000 円で見込みは 100,218.33… → 100,300（予算を少し上回るが、％は 100）。90,000 円で 117,602.95… → 117,700（118％）。120,000 円で 154,855.69… → 154,900（155％） */
	@Test
	void theLandingSentenceShowsTheAmountAndThePercentOfTheBudgetEvenAbove100Percent() throws Exception {
		Owner a = createOwner("toplay21");
		Category food = category(a, "食費", 100_000);
		recordingStartedLongAgo(a, food);

		spend(a, food, SUNDAY, 76_000);
		assertThat(seasonCard(topBody(a))).contains("季節は 76% 過ぎて、予算は 76% 使いました。このペースだと、季節の終わりには約 100,300 円（予算の 100%）の見込みです。");

		spend(a, food, SUNDAY, 14_000);       // 合計 90,000
		assertThat(seasonCard(topBody(a))).contains("季節は 76% 過ぎて、予算は 90% 使いました。このペースだと、季節の終わりには約 117,700 円（予算の 118%）の見込みです。");

		spend(a, food, SUNDAY, 30_000);       // 合計 120,000
		assertThat(seasonCard(topBody(a))).contains("季節は 76% 過ぎて、予算は 120% 使いました。このペースだと、季節の終わりには約 154,900 円（予算の 155%）の見込みです。");
	}

	/** 5 ポイントの判定（ゆっくりめ・ほぼ予定どおり・速め）は、なくした（issue 018）。戻っていないこと */
	@Test
	void theFivePointJudgementOfIssue017IsGone() throws Exception {
		Owner a = createOwner("toplay22");
		Category food = category(a, "食費", 100_000);
		spend(a, food, SUNDAY, 71_000);       // 差 −5 だった例

		String card = seasonCard(topBody(a));

		assertThat(card).contains("このペースだと").doesNotContain("使うペースは", "ゆっくりめ", "ほぼ予定どおり", "速めです");
	}

	@Test
	void noLandingSentenceWithoutABudgetOrWithAZeroBudget() throws Exception {
		Owner a = createOwner("toplay23");
		Category food = category(a, "食費", null);
		spend(a, food, SUNDAY, 500);
		assertThat(seasonCard(topBody(a))).doesNotContain("このペースだと", "過ぎて、予算は", "見込みです");

		Owner b = createOwner("toplay24");
		Category zero = category(b, "食費", 0);
		spend(b, zero, SUNDAY, 500);
		assertThat(seasonCard(topBody(b))).doesNotContain("このペースだと", "過ぎて、予算は", "見込みです");
	}

	/** 最初の 2 つの％は、メーターに出している「季節の ○%」「予算のうち ○%」と同じ値。見込みの額と％は、予算から計算した値と合う */
	@Test
	void thePercentagesInTheSentenceAreTheSameAsTheOnesShownOnTheMeter() throws Exception {
		Owner a = createOwner("toplay26");
		Category food = category(a, "食費", 130_000);
		spend(a, food, SUNDAY, 84_326);

		String card = seasonCard(topBody(a));
		java.util.regex.Matcher used = java.util.regex.Pattern.compile("予算のうち ([0-9]+)% を使った").matcher(card);
		java.util.regex.Matcher elapsed = java.util.regex.Pattern.compile("季節の ([0-9]+)%").matcher(card);
		java.util.regex.Matcher sentence = java.util.regex.Pattern
				.compile("季節は ([0-9]+)% 過ぎて、予算は ([0-9]+)% 使いました。このペースだと、季節の終わりには約 ([0-9,]+) 円（予算の ([0-9]+)%）の見込みです。")
				.matcher(card);

		assertThat(used.find()).isTrue();
		assertThat(elapsed.find()).isTrue();
		assertThat(sentence.find()).isTrue();
		assertThat(sentence.group(1)).as("季節の％").isEqualTo(elapsed.group(1));
		assertThat(sentence.group(2)).as("予算の％（使った割合）").isEqualTo(used.group(1));
		long amount = Long.parseLong(sentence.group(3).replace(",", ""));
		assertThat(amount % 100).as("見込みは 100 円単位").isZero();
		assertThat(Integer.parseInt(sentence.group(4))).as("予算に対する％は、表示する額 ÷ 予算の四捨五入")
				.isEqualTo((int) ((200 * amount + 130_000) / (2 * 130_000)));
	}

	@Test
	void theSentenceHasNoColorClassAndNoAlertWords() throws Exception {
		Owner a = createOwner("toplay25");
		Category food = category(a, "食費", 100_000);
		recordingStartedLongAgo(a, food);
		spend(a, food, SUNDAY, 90_000);

		String body = topBody(a);

		assertThat(body).containsPattern("<p class=\"top-season-pace\">季節は 76% 過ぎて、予算は 90% 使いました。このペースだと、季節の終わりには約 117,700 円（予算の 118%）の見込みです。</p>");
		assertThat(body).doesNotContain("このままだと", "超えそう", "着地", "上回ります");   // 上回ります〔予実比較の 1 行〕は、トップには出さない（Q018-2 の b）
	}

	@Test
	void theSentenceIsNotShownWhenThereIsNoCategory() throws Exception {
		Owner a = createOwner("toplay27");

		assertThat(topBody(a)).doesNotContain("このペースだと", "過ぎて、予算は");
	}

}
