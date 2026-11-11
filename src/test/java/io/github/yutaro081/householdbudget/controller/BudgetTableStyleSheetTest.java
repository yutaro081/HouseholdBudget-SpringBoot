package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 予算設定の表の CSS（style.css）のテスト（設計書 2.3「予算設定」、issue 020 の見直し）。
 * 根拠の列を狭くして実績の列のすぐ右に寄せる。予算額の列を目立たせる（入力欄の文字を大きく、見出しを濃いターコイズに）。
 * 見た目そのもの（間が空いて見えないか）は、ここでは確かめられない。ブラウザで確かめる。
 */
class BudgetTableStyleSheetTest {

	private static String css() throws Exception {
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 根拠の列は中身に合わせて狭くする（幅 1%。セルは折り返さない）。ほかの列は、残りを分ける */
	@Test
	void theBasisColumnShrinksToItsContentSoItStaysRightNextToTheActualColumn() throws Exception {
		String css = css();

		assertThat(rule(css, ".budget-table th.budget-basis")).as("見出しも折り返さない（途中で折れて読みにくいため）").contains("width: 1%", "white-space: nowrap");
		assertThat(rule(css, ".budget-table td.budget-basis")).contains("width: 1%", "white-space: nowrap");
	}

	/** 実績の列との間は、1rem の左の余白だけ（それまでの 2.5rem から狭めた） */
	@Test
	void theGapBeforeTheBasisColumnIsOnly1rem() throws Exception {
		String basis = rule(css(), ".budget-table .budget-basis");

		assertThat(basis).contains("padding-left: 1rem");
		assertThat(basis).doesNotContain("2.5rem");
	}

	/** 見出し「予算額（円）」は、濃いターコイズ（#0B6E6B。リンクと同じ色）。大きさ・太さは変えない */
	@Test
	void theAmountHeadingIsDarkTealAndKeepsTheSizeOfTheOtherHeadings() throws Exception {
		String css = css();

		assertThat(css).contains("--hb-teal-text: #0B6E6B");
		String heading = rule(css, ".budget-table th.budget-amount");
		assertThat(heading).contains("color: var(--hb-teal-text)");
		assertThat(heading).as("見出しの大きさは、ほかの列と同じ（規則で変えない）").doesNotContain("font-size");
	}

	/** 予算額の入力欄の文字を大きく（1.125rem） */
	@Test
	void theAmountInputsHaveBiggerText() throws Exception {
		assertThat(rule(css(), ".budget-table input[type=\"text\"]")).contains("font-size: 1.125rem");
	}
}
