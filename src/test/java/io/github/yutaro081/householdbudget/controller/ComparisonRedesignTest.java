package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import jakarta.persistence.EntityManager;

/**
 * 予実比較の画面の作り直し（要件定義書 4.4、設計書 2.3「予実比較」、5.13、issue 020）の確かめ。
 * 「今日」は 2026-11-11（秋の 72 日目。D ＝ 91、L ＝ 19）に固定する。
 * 見込み ＝ 切り上げ 100（（実績 × 91 × 110 ＋ 予算 × 361）÷ 8,281）。
 * 食費（予算 40,000、実績 30,000）→ 38,100（予算以下）。交通費（予算 10,000、実績 9,900）→ 12,500（予算を上回る）。
 * 雑費（予算なし）と娯楽（予算 0 円）は「—」。合計（予算 50,000、実績 39,900）→ 50,500（上回る）。予算のうち 80%、今日は季節の 79%。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock20261111Config.class)
@Transactional
class ComparisonRedesignTest {

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
		userId = userRepository.save(new User("cmpredes1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "cmpredes1", "x"));
	}

	private Category category(String name, Integer budget, Season season, int year) {
		Category c = categoryRepository.save(new Category(userId, name));
		if (budget != null) {
			budgetRepository.save(new Budget(userId, c, year, season, budget));
		}
		return c;
	}

	private void spend(Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(userId, date, amount, c, null, vendor, payment));
	}

	/** 「記録は、ずっと昔に始めた」状態にする：昔の日付に 1 円の取引（最初の取引の日が季節の途中だと、数え始めの日が変わるため） */
	private void recordingStartedLongAgo(Category c) {
		spend(c, LocalDate.of(2001, 3, 1), 1);
	}

	/** 食費・交通費・雑費・娯楽を、上の説明の数字で作る */
	private void fourCategories() {
		Category food = category("食費", 40_000, Season.AUTUMN, 2026);
		Category transport = category("交通費", 10_000, Season.AUTUMN, 2026);
		category("雑費", null, Season.AUTUMN, 2026);
		category("娯楽", 0, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 30_000);
		spend(transport, LocalDate.of(2026, 11, 3), 9_900);
	}

	private String page(String path) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get(path).with(login)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 科目名を含む行（&lt;tr ...&gt; から &lt;/tr&gt; まで）を取り出す */
	private static String rowOf(String body, String marker) {
		int at = body.indexOf(marker);
		assertThat(at).as("「%s」が画面にある", marker).isGreaterThanOrEqualTo(0);
		int start = body.lastIndexOf("<tr", at);
		return body.substring(start, body.indexOf("</tr>", at));
	}

	private static String thead(String body) {
		return body.substring(body.indexOf("<thead"), body.indexOf("</thead>"));
	}

	/** 見出しの行の列の数（<th> または <th class=…>。<thead> は数えない） */
	private static int columns(String thead) {
		Matcher m = Pattern.compile("<th[ >]").matcher(thead);
		int n = 0;
		while (m.find()) {
			n++;
		}
		return n;
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	// ---- 科目ごとの見込みの列 ----

	@Test
	void theForecastColumnShowsTheForecastOfEachCategoryBetweenActualAndDifference() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(thead(body)).containsSubsequence("科目", "予算（円）", "実績（円）", "見込み（円）", "差額（円）");
		assertThat(rowOf(body, "食費")).contains("38,100");
		assertThat(rowOf(body, "交通費")).contains("12,500");
	}

	@Test
	void categoriesWithoutAnyForecastShowADash() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(rowOf(body, "雑費")).as("予算が未設定").contains("—");
		assertThat(rowOf(body, "娯楽")).as("予算が 0 円").contains("—");
		assertThat(rowOf(body, "食費")).as("見込みのある科目には出ない").doesNotContain("—");
	}

	@Test
	void onlyTheCategoryWhoseForecastIsOverTheBudgetGetsTheMark() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(rowOf(body, "交通費")).contains("予算超えの見込み");
		assertThat(rowOf(body, "食費")).doesNotContain("予算超えの見込み");
		assertThat(rowOf(body, "雑費")).doesNotContain("予算超えの見込み");
		assertThat(rowOf(body, "合計")).as("合計 50,500 は予算 50,000 を上回る").contains("50,500", "予算超えの見込み");
	}

	@Test
	void noSentenceIsShownBelowTheTable() throws Exception {
		fourCategories();

		String body = page("/comparison");
		String afterTable = body.substring(body.indexOf("</table>"));

		assertThat(afterTable).doesNotContain("予算超えの見込み", "このペースだと", "見込みは、予算を上回ります");
	}

	@Test
	void thePastSeasonHasNeitherTheForecastColumnNorTheCard() throws Exception {
		fourCategories();
		category("夏の科目", 1_000, Season.SUMMER, 2026);

		String body = page("/comparison?year=2026&season=summer");

		assertThat(thead(body)).doesNotContain("見込み");
		assertThat(body).doesNotContain("comparison-card");
		assertThat(columns(thead(body))).as("科目・予算・実績・差額の 4 列（💮 の列もない）").isEqualTo(4);
	}

	@Test
	void theCurrentSeasonHasFiveColumnsAndNoColumnForTheMark() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(columns(thead(body))).isEqualTo(5);
	}

	// ---- 要約カード ----

	@Test
	void theSummaryCardShowsTheTotalForecastTheBudgetAndTheMeterAboveTheTable() throws Exception {
		fourCategories();

		String body = page("/comparison");
		int card = body.indexOf("class=\"comparison-card\"");

		assertThat(card).isGreaterThanOrEqualTo(0);
		assertThat(card).as("表の上").isLessThan(body.indexOf("<table"));
		assertThat(card).as("年と季節を選ぶ欄の下").isGreaterThan(body.indexOf("season-select"));
		String cardText = body.substring(card, body.indexOf("<table"));
		assertThat(cardText).contains("約 50,500円", "予算 50,000円", "実績 39,900円（80%）", "class=\"top-meter\"", "予算のうち 80% を使った", "縦の線は『今日』（季節の 79%）");
		assertThat(cardText).contains("見込みは、予算を上回ります。", "月ごとにまとめて払うものは、払う日で見込みが上下します。");
	}

	/** 上の段に大きな数字が横に 3 つ（見込み・予算・実績の順）。その下にメーター。説明のラベルはメーターのすぐ上。補足は一番下 */
	@Test
	void theCardHasThreeFiguresThenTheMeterWithItsLabelsAboveAndTheNotesAtTheBottom() throws Exception {
		fourCategories();

		String body = page("/comparison");
		String card = body.substring(body.indexOf("class=\"comparison-card\""), body.indexOf("<table"));

		assertThat(count(card, "class=\"comparison-card-amount\"")).as("大きな数字は 3 つ").isEqualTo(3);
		int forecast = card.indexOf("約 50,500円");
		int budget = card.indexOf("予算 50,000円");
		int actual = card.indexOf("実績 39,900円（80%）");
		int labels = card.indexOf("予算のうち 80% を使った");
		int today = card.indexOf("縦の線は『今日』");
		int meter = card.indexOf("class=\"top-meter\"");
		assertThat(forecast).isGreaterThanOrEqualTo(0).isLessThan(budget);
		assertThat(budget).isLessThan(actual);
		assertThat(actual).as("数字の下にメーター").isLessThan(labels);
		assertThat(labels).as("ラベルは、メーターのすぐ上（左：使った割合、右：今日）").isLessThan(today);
		assertThat(today).isLessThan(meter);
		assertThat(card.indexOf("見込みは、予算を上回ります。")).as("上回る文は、メーターの下").isGreaterThan(meter);
		assertThat(card.indexOf("月ごとにまとめて払うもの")).as("補足は、一番下").isGreaterThan(card.indexOf("見込みは、予算を上回ります。"));
	}

	/** 印は、数字の左に、同じ行の小さなチップ（数字の下の 2 行目ではない） */
	@Test
	void theMarksAreChipsToTheLeftOfTheNumbers() throws Exception {
		fourCategories();
		Category extra = category("超過の科目", 1_000, Season.AUTUMN, 2026);
		spend(extra, LocalDate.of(2026, 11, 3), 2_000);
		Category low = category("余裕の科目", 90_000, Season.AUTUMN, 2026);
		spend(low, LocalDate.of(2026, 11, 3), 10_000);

		String body = page("/comparison");

		assertThat(rowOf(body, "交通費")).contains("<small class=\"cell-mark\">予算超えの見込み</small><span>12,500</span>");
		assertThat(rowOf(body, "超過の科目")).contains("<small class=\"cell-mark\">予算超え</small><span>-1,000</span>");
		assertThat(rowOf(body, "余裕の科目")).contains("<small class=\"cell-mark\">使い残し</small><span>80,000</span>");
	}

	/** 差額のセルにだけ diff-cell が付く（科目の行も合計行も）。予算・実績・見込みのセルには付かない。色のクラス（diff-over・diff-low）とは別 */
	@Test
	void onlyTheDifferenceCellsHaveTheDiffCellClass() throws Exception {
		fourCategories();

		String body = page("/comparison");
		String tbody = body.substring(body.indexOf("<tbody>"), body.indexOf("</tbody>"));

		assertThat(count(tbody, "diff-cell")).as("科目 4 行と合計行の、5 つの差額のセル").isEqualTo(5);
		for (String name : new String[] { "食費", "交通費", "雑費", "娯楽", "合計" }) {
			String row = rowOf(body, name);
			assertThat(count(row, "diff-cell")).as(name).isEqualTo(1);
			assertThat(row.substring(row.lastIndexOf("<td"))).as(name + " の最後のセルが差額").contains("diff-cell");
			assertThat(row.substring(0, row.lastIndexOf("<td"))).as(name + " の、ほかのセルには付かない").doesNotContain("diff-cell");
		}
	}

	@Test
	void thePastSeasonDifferenceCellsHaveTheDiffCellClassToo() throws Exception {
		fourCategories();
		category("夏の科目", 1_000, Season.SUMMER, 2026);

		String body = page("/comparison?year=2026&season=summer");

		assertThat(count(body.substring(body.indexOf("<tbody>"), body.indexOf("</tbody>")), "diff-cell")).as("科目 5 行（科目は季節によらず全部出る）と合計行").isEqualTo(6);
	}

	@Test
	void theTotalRowChipIsAlsoToTheLeftOfTheForecast() throws Exception {
		fourCategories();

		assertThat(rowOf(page("/comparison"), "合計")).contains("<small class=\"cell-mark\">予算超えの見込み</small><span>50,500</span>");
	}

	@Test
	void theOldOneLineSentenceIsGone() throws Exception {
		fourCategories();

		assertThat(page("/comparison")).doesNotContain("このペースだと、季節の終わりには、合計で");
	}

	@Test
	void theOverLineIsNotShownWhenTheTotalForecastIsWithinTheBudget() throws Exception {
		Category food = category("食費", 100_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 10_000);        // 見込みは予算より小さい

		String body = page("/comparison");

		assertThat(body).contains("class=\"comparison-card\"").doesNotContain("見込みは、予算を上回ります。");
	}

	@Test
	void noCardWhenTheBudgetIsUnsetOrZero() throws Exception {
		Category food = category("食費", null, Season.AUTUMN, 2026);
		spend(food, LocalDate.of(2026, 11, 3), 10_000);
		assertThat(page("/comparison")).doesNotContain("comparison-card");

		category("日用品", 0, Season.AUTUMN, 2026);
		assertThat(page("/comparison")).as("予算の合計が 0 円").doesNotContain("comparison-card");
	}

	@Test
	void theCardTellsWhenTheCountStartsFromTheFirstTransactionDay() throws Exception {
		Category food = category("食費", 175_000, Season.AUTUMN, 2026);
		spend(food, LocalDate.of(2026, 10, 31), 20_000);

		String body = page("/comparison");

		assertThat(body).contains("10/31 に記録を始めたので、その日から数えています。");
	}

	@Test
	void theCardDoesNotTellWhenTheFirstTransactionIsBeforeTheSeason() throws Exception {
		Category food = category("食費", 175_000, Season.AUTUMN, 2026);
		spend(food, LocalDate.of(2026, 8, 20), 1_000);
		spend(food, LocalDate.of(2026, 10, 31), 20_000);

		assertThat(page("/comparison")).doesNotContain("に記録を始めたので");
	}

	// ---- 色と印：差額のセルだけ ----

	@Test
	void theRedDifferenceCellHasTheOverMarkAndOnlyThatCellIsColored() throws Exception {
		fourCategories();
		Category extra = category("超過の科目", 1_000, Season.AUTUMN, 2026);
		spend(extra, LocalDate.of(2026, 11, 3), 2_000);

		String row = rowOf(page("/comparison"), "超過の科目");

		assertThat(row).startsWith("<tr>");
		assertThat(count(row, "diff-over")).as("色のクラスは差額のセルだけ").isEqualTo(1);
		String diff = row.substring(row.indexOf("diff-over") - 20);
		assertThat(diff).contains("予算超え", "-1,000");
	}

	@Test
	void theBlueDifferenceCellHasTheLeftoverMark() throws Exception {
		Category food = category("食費", 90_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 10_000);          // 実績 × 3 ≦ 予算 → 青

		String row = rowOf(page("/comparison"), "食費");

		assertThat(row).startsWith("<tr>").contains("diff-low", "使い残し");
		assertThat(row).doesNotContain("予算超え");
	}

	@Test
	void aRowWithoutColorHasNoMarkInTheDifferenceCell() throws Exception {
		Category food = category("食費", 40_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 30_000);          // 予算内（💮）

		String row = rowOf(page("/comparison"), "食費");

		assertThat(row).doesNotContain("diff-over", "diff-low", "予算超え", "使い残し");
	}

	// ---- 円は見出しに、💮 は科目名の横に、合計の行 ----

	@Test
	void theCellsHaveNoYenSignBecauseTheHeadingsSayIt() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(rowOf(body, "食費")).doesNotContain("円");
		assertThat(rowOf(body, "合計")).doesNotContain("円");
		assertThat(rowOf(body, "食費")).contains("40,000", "30,000", "10,000");
	}

	@Test
	void theWithinMarkIsNextToTheCategoryNameInTheFirstCell() throws Exception {
		Category food = category("食費", 40_000, Season.AUTUMN, 2026);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 30_000);          // 予算内（💮）

		String row = rowOf(page("/comparison"), "食費");
		String firstCell = row.substring(row.indexOf("<td"), row.indexOf("</td>"));

		assertThat(row).contains("<tr class=\"row-within\"");
		assertThat(firstCell).contains("食費", "💮", "予算内");
		assertThat(count(row, "💮")).isEqualTo(1);
	}

	@Test
	void theTotalRowKeepsItsClassAndTheLegendNamesTheMarks() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(rowOf(body, "合計")).contains("total-row");
		assertThat(body).contains("class=\"legend\"");
		String legend = body.substring(body.indexOf("class=\"legend\""));
		assertThat(legend).contains("予算超え", "使い残し", "💮");
	}
}
