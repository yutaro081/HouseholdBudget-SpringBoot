package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 予実比較の「見込みとの差」の列（要件定義書 4.4、設計書 2.3「予実比較」、issue 020）の確かめ。
 * 今の季節は「科目／今の状況／予算／実績／残り／見込み／見込みとの差」の 7 列（issue 024 で、「今の状況」を足した）。残りは過去・未来の季節の差額と同じ値（名前だけ変える）。
 * 見込みとの差 ＝ 予算 − 見込み（表示する額）。マイナスのときだけ赤字と「超える見込み」のチップ。見込みが「—」の科目は「—」。
 * 「今日」は 2026-11-11（秋の 72 日目。D ＝ 91、L ＝ 19）に固定する（FixedClock20261111Config）。
 * 見込み ＝ 切り上げ 100（（実績 × 91 × 110 ＋ 予算 × 361）÷ 8,281）。
 * 食費（予算 40,000、実績 30,000）→ 38,100、差 1,900。交通費（予算 10,000、実績 9,900）→ 12,500、差 −2,500。雑費（予算なし）、娯楽（予算 0 円）は「—」。
 * 合計（予算 50,000、実績 39,900）→ 50,500、差 −500。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock20261111Config.class)
@Transactional
class ComparisonForecastGapTest {

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
		userId = userRepository.save(new User("cmpgap1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "cmpgap1", "x"));
	}

	private Category category(String name, Integer budget) {
		Category c = categoryRepository.save(new Category(userId, name));
		if (budget != null) {
			budgetRepository.save(new Budget(userId, c, 2026, Season.AUTUMN, budget));
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

	private void fourCategories() {
		Category food = category("食費", 40_000);
		Category transport = category("交通費", 10_000);
		category("雑費", null);
		category("娯楽", 0);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 30_000);
		spend(transport, LocalDate.of(2026, 11, 3), 9_900);
	}

	private String page(String path) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get(path).with(login)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String rowOf(String body, String marker) {
		int at = body.indexOf(marker);
		assertThat(at).as("「%s」が画面にある", marker).isGreaterThanOrEqualTo(0);
		int start = body.lastIndexOf("<tr", at);
		return body.substring(start, body.indexOf("</tr>", at));
	}

	/** 行の各セル（&lt;td&gt;）の、タグを除いた文字。空白は詰める */
	private static List<String> cellsOf(String row) {
		List<String> cells = new ArrayList<>();
		Matcher m = Pattern.compile("(?s)<td[^>]*>(.*?)</td>").matcher(row);
		while (m.find()) {
			cells.add(m.group(1).replaceAll("(?s)<[^>]+>", "").replaceAll("\\s+", ""));
		}
		return cells;
	}

	/** 行の n 番目（0 始まり）のセルの HTML（&lt;td ...&gt; から &lt;/td&gt; まで） */
	private static String cellHtml(String row, int n) {
		Matcher m = Pattern.compile("(?s)<td[^>]*>.*?</td>").matcher(row);
		for (int i = 0; m.find(); i++) {
			if (i == n) {
				return m.group();
			}
		}
		throw new AssertionError("セルがない：" + n);
	}

	private static String thead(String body) {
		return body.substring(body.indexOf("<thead"), body.indexOf("</thead>"));
	}

	private static int columns(String thead) {
		Matcher m = Pattern.compile("<th[ >]").matcher(thead);
		int n = 0;
		while (m.find()) {
			n++;
		}
		return n;
	}

	// ---- 列 ----

	@Test
	void theCurrentSeasonHasSevenColumnsInTheDecidedOrder() throws Exception {
		fourCategories();

		String head = thead(page("/comparison"));

		assertThat(columns(head)).isEqualTo(7);
		assertThat(head).containsSubsequence("<th>科目</th>", "<th class=\"col-status\">今の状況</th>", "<th class=\"num col-budget\">予算（円）</th>", "<th class=\"num col-actual\">実績（円）</th>",
				"<th class=\"num col-remain\">残り（円）</th>", "<th class=\"num col-forecast\">見込み（円）</th>", "<th class=\"num col-gap\">見込みとの差（円）</th>");
		assertThat(head).as("今の季節では「差額」の名前は使わない").doesNotContain("差額");
	}

	@Test
	void thePastSeasonKeepsTheDifferenceColumnWithTheStatusColumn() throws Exception {
		fourCategories();

		String head = thead(page("/comparison?year=2026&season=summer"));

		assertThat(columns(head)).isEqualTo(5);
		assertThat(head).containsSubsequence("<th>科目</th>", "<th class=\"col-status\">状況</th>", "<th class=\"num col-budget\">予算（円）</th>", "<th class=\"num col-actual\">実績（円）</th>", "<th class=\"num col-diff\">差額（円）</th>");
		assertThat(head).doesNotContain("残り", "見込み", "col-gap");
	}

	@Test
	void theTableOfTheCurrentSeasonHasTheForecastClassAndThePastOneDoesNot() throws Exception {
		fourCategories();

		assertThat(page("/comparison")).contains("class=\"list comparison-table table table-hover align-middle has-forecast\"");
		assertThat(page("/comparison?year=2026&season=summer")).doesNotContain("has-forecast");
	}

	/** 表は白いカード（.table-card）の中に入る（今の季節も、過去の季節も） */
	@Test
	void theTableIsInsideTheWhiteTableCard() throws Exception {
		fourCategories();

		for (String path : new String[] { "/comparison", "/comparison?year=2026&season=summer" }) {
			String body = page(path);
			assertThat(body.indexOf("class=\"table-card")).as(path).isGreaterThanOrEqualTo(0).isLessThan(body.indexOf("<table"));
		}
	}

	// ---- 値 ----

	@Test
	void everyRowHasTheValuesInTheDecidedOrder() throws Exception {
		fourCategories();

		String body = page("/comparison");
		List<String> food = cellsOf(rowOf(body, "食費"));
		List<String> transport = cellsOf(rowOf(body, "交通費"));

		assertThat(food).hasSize(7);
		assertThat(food.get(0)).startsWith("食費");
		assertThat(food.get(1)).as("今の状況：予算内のチップ").isEqualTo("予算内");
		assertThat(food.subList(2, 7)).as("予算・実績・残り・見込み（/ その科目の予算）・見込みとの差").containsExactly("40,000", "30,000", "10,000", "38,100/40,000", "1,900");
		assertThat(transport.get(1)).isEqualTo("予算内");
		assertThat(transport.subList(2, 7)).containsExactly("10,000", "9,900", "100", "12,500/10,000", "超える見込み-2,500");
	}

	@Test
	void theForecastColumnHasOnlyNumbersNoChip() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(body).doesNotContain("予算超えの見込み");
		assertThat(cellHtml(rowOf(body, "交通費"), 5)).doesNotContain("chip").contains("12,500");
	}

	/** 見込みのセルは「7,900 / 25,000」の形。「/ その科目の予算」は小さめの灰色（cell-sub）。見込みが「—」の科目は「—」だけ。合計の行も同じ形 */
	@Test
	void theForecastCellShowsTheForecastAndTheBudgetInSmallGray() throws Exception {
		fourCategories();

		String body = page("/comparison");

		assertThat(cellHtml(rowOf(body, "食費"), 5)).contains("<span>38,100</span><span class=\"cell-sub\"> / 40,000</span>");
		assertThat(cellHtml(rowOf(body, "合計"), 5)).contains("<span>50,500</span><span class=\"cell-sub\"> / 50,000</span>");
		for (String name : new String[] { "雑費", "娯楽" }) {
			String cell = cellHtml(rowOf(body, name), 5);
			assertThat(cell).as(name + " は「—」だけ").doesNotContain("cell-sub", " / ");
			assertThat(cellsOf(rowOf(body, name)).get(5)).isEqualTo("—");
		}
	}

	@Test
	void theTotalForecastIsADashAloneWhenThereIsNoTotalForecast() throws Exception {
		Category food = category("食費", null);
		spend(food, LocalDate.of(2026, 11, 3), 5_000);

		String body = page("/comparison");

		assertThat(cellHtml(rowOf(body, "合計"), 5)).doesNotContain("cell-sub");
		assertThat(cellsOf(rowOf(body, "合計")).get(5)).isEqualTo("—");
	}

	@Test
	void aNegativeGapIsRedWithTheChipToTheLeftOfTheNumber() throws Exception {
		fourCategories();

		String row = rowOf(page("/comparison"), "交通費");
		String gap = cellHtml(row, 6);

		assertThat(gap).contains("class=\"num gap-cell gap-over\"", "<span class=\"chip chip-over\">超える見込み</span><span>-2,500</span>");
	}

	@Test
	void aPositiveGapHasNeitherColorNorChip() throws Exception {
		fourCategories();

		String gap = cellHtml(rowOf(page("/comparison"), "食費"), 6);

		assertThat(gap).contains("class=\"num gap-cell\"", "<span>1,900</span>").doesNotContain("gap-over", "超える見込み", "chip");
	}

	/** 見込みが予算ちょうど（91,000 円）のとき、差は 0 円。0 円以上は色もチップもない */
	@Test
	void aZeroGapHasNeitherColorNorChip() throws Exception {
		Category food = category("食費", 91_000);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 72_000);          // 見込み ＝ 91,000（割り切れる）

		String row = rowOf(page("/comparison"), "食費");

		assertThat(cellsOf(row).get(5)).isEqualTo("91,000/91,000");
		assertThat(cellHtml(row, 6)).contains("<span>0</span>").doesNotContain("gap-over", "超える見込み");
	}

	@Test
	void theGapIsADashWhereTheForecastIsADash() throws Exception {
		fourCategories();

		String body = page("/comparison");

		for (String name : new String[] { "雑費", "娯楽" }) {
			List<String> cells = cellsOf(rowOf(body, name));
			assertThat(cells.get(5)).as(name + " の見込み").isEqualTo("—");
			assertThat(cells.get(6)).as(name + " の見込みとの差").isEqualTo("—");
		}
		assertThat(cellsOf(rowOf(body, "雑費")).get(4)).as("予算が未設定の科目の残りは空欄").isEmpty();
		assertThat(rowOf(body, "食費")).doesNotContain("—");
	}

	@Test
	void theTotalRowHasTheSameSevenColumnsAndTheTotalGap() throws Exception {
		fourCategories();

		String row = rowOf(page("/comparison"), "合計");
		List<String> cells = cellsOf(row);

		assertThat(cells).hasSize(7);
		assertThat(cells.get(1)).as("合計の状況：予算内のチップ").isEqualTo("予算内");
		assertThat(cells.subList(2, 7)).containsExactly("50,000", "39,900", "10,100", "50,500/50,000", "超える見込み-500");
		assertThat(cellHtml(row, 6)).contains("gap-cell gap-over");
	}

	@Test
	void theTotalGapIsADashWhenThereIsNoTotalForecast() throws Exception {
		Category food = category("食費", null);
		spend(food, LocalDate.of(2026, 11, 3), 5_000);

		List<String> cells = cellsOf(rowOf(page("/comparison"), "合計"));

		assertThat(cells.get(5)).isEqualTo("—");
		assertThat(cells.get(6)).isEqualTo("—");
	}

	/** 状況・残り・見込み・見込みとの差のセルに、それぞれのクラス（太字・大きさ、区切り線）。今の季節だけ */
	@Test
	void theCellsOfTheCurrentSeasonHaveTheirOwnClasses() throws Exception {
		fourCategories();

		String body = page("/comparison");

		for (String name : new String[] { "食費", "交通費", "雑費", "娯楽", "合計" }) {
			String row = rowOf(body, name);
			assertThat(cellHtml(row, 1)).as(name + " の今の状況").contains("status-cell");
			assertThat(cellHtml(row, 4)).as(name + " の残り").contains("remain-cell");
			assertThat(cellHtml(row, 5)).as(name + " の見込み").contains("forecast-cell");
			assertThat(cellHtml(row, 6)).as(name + " の見込みとの差").contains("gap-cell");
			assertThat(cellHtml(row, 2) + cellHtml(row, 3)).as(name + " の予算・実績には付かない").doesNotContain("remain-cell", "forecast-cell", "gap-cell", "status-cell");
		}
		String past = page("/comparison?year=2026&season=summer");
		assertThat(past).doesNotContain("remain-cell", "forecast-cell", "gap-cell");
	}

	// ---- 残り：今までの差額 ----

	/** 残りは、赤い数字（マイナス）のまま。「予算超え」の札は、状況のセルのチップに移った */
	@Test
	void theRemainingKeepsTheRedNumberAndTheOverChipMovedToTheStatusCell() throws Exception {
		fourCategories();
		Category extra = category("超過の科目", 1_000);
		spend(extra, LocalDate.of(2026, 11, 3), 2_000);

		String row = rowOf(page("/comparison"), "超過の科目");

		assertThat(cellHtml(row, 1)).contains("<span class=\"chip chip-over\">予算超え</span>");
		assertThat(cellHtml(row, 4)).contains("diff-over", "<span>-1,000</span>").doesNotContain("chip", "cell-mark");
		assertThat(cellHtml(row, 6)).as("見込みとの差も赤（見込みが予算を超える）").contains("gap-over", "超える見込み");
		assertThat(row).as("今の季節では、差額のクラス（diff-cell）は使わない").doesNotContain("diff-cell");
	}

	@Test
	void theBlueRowHasTheLeftoverChipAndNoBlueNumberAndTheGapHasNone() throws Exception {
		Category food = category("食費", 90_000);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 3), 10_000);          // 実績 × 3 ≦ 予算 → 青

		String row = rowOf(page("/comparison"), "食費");

		assertThat(cellHtml(row, 1)).contains("<span class=\"chip chip-low\">使い残し</span>");
		assertThat(cellHtml(row, 4)).as("青い数字はない（青はチップだけ）").doesNotContain("diff-low", "diff-over", "chip");
		assertThat(cellHtml(row, 6)).doesNotContain("gap-over", "超える見込み");
	}

	// ---- 注記 ----

	@Test
	void theLegendMatchesTheColumnsOfTheSeason() throws Exception {
		fourCategories();

		String current = page("/comparison");
		String past = page("/comparison?year=2026&season=summer");

		assertThat(current).contains("チップ：予算超え（赤）／使い残し（青。予算の1/3以下）／予算内（予算の1/3を超え、予算以下）／見込みとの差の赤：超える見込み").doesNotContain("💮");
		assertThat(past).contains("チップ：予算超え（赤）／使い残し（青。予算の1/3以下）／予算内（予算の1/3を超え、予算以下）").doesNotContain("見込みとの差の赤", "💮");
	}

	/** 縦の線は、見込みの列（col-forecast・forecast-cell）だけ。ほかのセルには、縦の線のクラス（Bootstrap の border-start・border-end・table-bordered など）を付けない。表は、今の季節も過去の季節も */
	@Test
	void noCellExceptTheForecastColumnCarriesAVerticalLineClass() throws Exception {
		fourCategories();

		for (String path : new String[] { "/comparison", "/comparison?year=2026&season=summer" }) {
			String body = page(path);
			String table = body.substring(body.indexOf("<table"), body.indexOf("</table>"));
			assertThat(table).as(path).doesNotContain("table-bordered", "border-start", "border-end", "border-left", "border-right", "border-");
			Matcher m = Pattern.compile("<t[hd][^>]*class=\"([^\"]*)\"").matcher(table);
			while (m.find()) {
				if (m.group(1).contains("forecast")) {
					assertThat(path).as("見込みの列があるのは、今の季節だけ").doesNotContain("summer");
				}
			}
		}
	}
}
