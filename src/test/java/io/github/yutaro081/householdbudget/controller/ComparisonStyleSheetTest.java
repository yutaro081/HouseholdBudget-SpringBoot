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
	void theTableFitsItsContentAndTheCategoryColumnIsNotStretched() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table")).as("表は広げない（1120px にしても、科目と予算の間が空きすぎない）").contains("width: auto");
		assertThat(rule(css, ".comparison-table th:first-child")).as("科目の列は、名前に合う幅（最小 8rem）").contains("min-width: 8rem");
	}

	@Test
	void theFourNumberColumnsHaveTheSameWidthAndAreRightAligned() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table th.num")).contains("width: 9.5rem", "text-align: right");
		assertThat(rule(css, ".comparison-table td.num")).contains("width: 9.5rem", "text-align: right");
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
	void theMarksUnderTheNumbersAreSmallAndHaveNoMeaningColor() throws Exception {
		String mark = rule(css(), ".comparison-table .cell-mark");

		assertThat(mark).contains("display: block", "font-size: var(--hb-font-size-small)", "font-weight: 400");
		assertThat(mark).as("印そのものに色は付けない（色は差額のセルの文字の色を引き継ぐ）").doesNotContain("color: #c62828", "color: #1565c0");
	}

	@Test
	void theSummaryCardIsAWhiteCardWithABigNumber() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card")).contains("background-color: var(--hb-input-bg)", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(rule(css, ".comparison-card-amount")).contains("font-size: var(--hb-font-size-h1)", "font-weight: 700");
	}
}
