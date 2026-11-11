package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 予実比較の表と要約カードの CSS（style.css）のテスト（設計書 2.3「予実比較」、issue 020）。
 * 見た目そのもの（間延びしないか、にじみ）は、ここでは確かめられない。ブラウザで確かめる。
 * ここでは、決めた数字（列の幅、合計行の形、色は差額のセルだけ）を確かめる。
 */
class ComparisonStyleSheetTest {

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
	void theTableIsFullWidthSoItsEdgesLineUpWithTheSummaryCard() throws Exception {
		String table = rule(css(), ".comparison-table");

		assertThat(table).contains("width: 100%", "table-layout: fixed");
		assertThat(table).as("せまい幅では、表の最小の幅で横にスクロールする").contains("min-width: 44rem");
	}

	/** 過去・未来の季節（4 列）の幅：科目 16%・予算 16%・実績 16%・差額 28%（チップの付く差額を広く） */
	@Test
	void theColumnsAreSizedByTheirContent() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table th:first-child")).as("科目").contains("width: 16%");
		assertThat(rule(css, ".comparison-table th.col-budget")).as("予算").contains("width: 16%");
		assertThat(rule(css, ".comparison-table th.col-actual")).as("実績").contains("width: 16%");
		assertThat(rule(css, ".comparison-table th.col-diff")).as("差額（チップが付く）").contains("width: 28%");
		assertThat(rule(css, ".comparison-table th.num")).as("数字の列の共通の規則には、幅を書かない").doesNotContain("width").contains("text-align: right");
		assertThat(rule(css, ".comparison-table td.num")).doesNotContain("width").contains("text-align: right");
	}

	/** 今の季節（6 列。表に has-forecast）の幅：科目 16%、数字の 5 列は残りを等分（16.8% ずつ） */
	@Test
	void theSixColumnsOfTheCurrentSeasonAreSizedByTheirContent() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table.has-forecast th.col-budget")).as("予算").contains("width: 16.8%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-actual")).as("実績").contains("width: 16.8%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-remain")).as("残り（チップが付く。見込みとの差と同じくらい）").contains("width: 16.8%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-forecast")).as("見込み").contains("width: 16.8%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-gap")).as("見込みとの差（チップが付く）").contains("width: 16.8%");
		assertThat(rule(css, ".comparison-table.has-forecast")).as("6 列は、せまい幅で横にスクロールする").contains("min-width: 72rem");
	}

	/** 残りと見込みとの差を、同じくらい目立たせる：1.25rem、太字、見出しは濃いターコイズ（#0B6E6B）。見込みとの差のマイナスは赤 */
	@Test
	void theRemainingAndTheGapAreEquallyProminent() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table td.gap-cell")).contains("font-weight: 700", "font-size: 1.25rem");
		assertThat(rule(css, ".comparison-table td.remain-cell")).contains("font-weight: 700", "font-size: 1.25rem");
		assertThat(rule(css, ".comparison-table th.col-gap")).contains("color: var(--hb-teal-text)");
		assertThat(rule(css, ".comparison-table th.col-remain")).contains("color: var(--hb-teal-text)");
		assertThat(rule(css, ".comparison-table td.gap-over")).contains("color: #c62828");
	}

	/** 実績・残りの組と、見込み・見込みとの差の組の間に、薄い縦の区切り線（見込みの列の左。見出しもセルも） */
	@Test
	void aThinVerticalLineSeparatesTheTwoGroupsOfColumns() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table th.col-forecast")).contains("border-left: 1px solid var(--hb-line)");
		assertThat(rule(css, ".comparison-table td.forecast-cell")).contains("border-left: 1px solid var(--hb-line)");
	}

	/** 過去・未来の季節の差額は、今までどおり目立たせる（差額の数字は大きく 1.25rem、見出し「差額（円）」は濃いターコイズ #0B6E6B） */
	@Test
	void theDifferenceIsBigAndItsHeadingIsDarkTeal() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table td.diff-cell")).contains("font-weight: 700", "font-size: 1.25rem");
		assertThat(rule(css, ".comparison-table th.col-diff")).contains("color: var(--hb-teal-text)");
		assertThat(css).contains("--hb-teal-text: #0B6E6B");
	}

	@Test
	void everyRowHasTheSameMinimumHeightSoTheChipsDoNotMakeSomeRowsTaller() throws Exception {
		assertThat(rule(css(), ".comparison-table tbody td")).contains("height: 2.75rem");
	}

	/** 表のセルの文字は、本文（1rem）より少し大きく（1.0625rem）。見出しは本文と同じ大きさのまま */
	@Test
	void theCellsAreASlightlyLargerThanTheBodyText() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table tbody td")).contains("font-size: 1.0625rem");
		assertThat(rule(css, ".comparison-table thead th")).contains("font-size: var(--hb-font-size-body)");
	}

	/** 差額のセルだけ、数字を太字にして目立たせる（差額のセルにだけ付くクラス diff-cell。「最後の列」では指定しない。列を足しても崩れない）。チップの文字は 400 のまま */
	@Test
	void onlyTheDifferenceCellsAreBoldByTheirOwnClassNotByTheLastColumn() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table td.diff-cell")).contains("font-weight: 700");
		assertThat(css).as("最後の列で指定しない（列を足すと、そちらが太字になってしまう）").doesNotContain(".comparison-table tbody td:last-child").doesNotContain("td:last-child {\n    font-weight: 700");
		assertThat(rule(css, ".comparison-table td.num")).as("数字の列の全体は、太字にしない").doesNotContain("font-weight");
		assertThat(rule(css, ".comparison-table .cell-mark")).contains("font-weight: 400");
	}

	/** 見込みのセルの「/ その科目の予算」は、小さめの灰色（400） */
	@Test
	void theBudgetInTheForecastCellIsSmallAndGray() throws Exception {
		assertThat(rule(css(), ".comparison-table .cell-sub")).contains("color: var(--hb-text-sub)", "font-size: var(--hb-font-size-small)", "font-weight: 400");
	}

	@Test
	void theHeadingsHaveTheBodySizeAndBold() throws Exception {
		assertThat(rule(css(), ".comparison-table thead th")).contains("font-size: var(--hb-font-size-body)", "font-weight: 700");
	}

	@Test
	void theTotalRowIsLargeWithAThickLineAboveLikeTheBudgetScreen() throws Exception {
		String total = rule(css(), ".comparison-table tr.total-row td");

		assertThat(total).contains("font-size: 1.25rem", "border-top: 3px solid var(--hb-text)");
	}

	@Test
	void colorIsOnTheDifferenceCellOnly() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table td.diff-over")).contains("color: #c62828");
		assertThat(rule(css, ".comparison-table td.diff-low")).contains("color: #1565c0");
		assertThat(css).as("行の文字全体に色を付ける規則は、なくした").doesNotContain("tr.row-over td").doesNotContain("tr.row-low td");
	}

	@Test
	void theWithinRowKeepsTheApricotBackground() throws Exception {
		assertThat(rule(css(), ".comparison-table tr.row-within")).contains("--bs-table-bg: var(--hb-apricot)");
	}

	@Test
	void theMarksAreSmallChipsOnTheSameLineAsTheNumberAndHaveNoMeaningColor() throws Exception {
		String mark = rule(css(), ".comparison-table .cell-mark");

		assertThat(mark).contains("display: inline-block", "white-space: nowrap", "border: 1px solid currentColor", "border-radius", "font-size: var(--hb-font-size-small)", "font-weight: 400");
		assertThat(mark).as("数字の下の 2 行目に置かない（その行だけ高くなる）").doesNotContain("display: block");
		assertThat(mark).as("印そのものに色は付けない（色は差額のセルの文字の色を引き継ぐ）").doesNotContain("color: #c62828", "color: #1565c0");
	}

	@Test
	void theCardPutsThreeBigNumbersInARowAndTheMeterUsesTheFullWidth() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card-figures")).contains("display: grid", "grid-template-columns");
		assertThat(css).as("メーターは、カードの幅いっぱい（幅の上限をなくした）").doesNotContain("max-width: 32rem");
	}

	@Test
	void theSummaryCardIsAWhiteCardWithABigNumber() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card")).contains("background-color: var(--hb-input-bg)", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(rule(css, ".comparison-card-amount")).contains("font-size: var(--hb-font-size-h1)", "font-weight: 700");
	}
}
