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

	/** 表のセルの文字は、本文と同じ濃さ（#24302F）で、予実比較の表と同じ大きさ（1.0625rem）。灰色にしない */
	@Test
	void theCellsHaveTheBodyColorAndTheSameSizeAsTheComparisonTable() throws Exception {
		String css = css();
		String cells = rule(css, ".budget-table tbody td");

		assertThat(css).contains("--hb-text: #24302F");
		assertThat(cells).contains("color: var(--hb-text)", "font-size: 1.0625rem");
		assertThat(rule(css, ".comparison-table tbody td")).as("予実比較の表と同じ大きさ").contains("font-size: 1.0625rem");
	}

	/** 参考の列を灰色にする規則（.budget-ref）は、なくした */
	@Test
	void noRuleMakesTheReferenceColumnsGray() throws Exception {
		assertThat(css()).doesNotContain(".budget-ref");
	}

	/** 「ペース 約 …」の補足だけは、小さな灰色のまま */
	@Test
	void onlyThePaceNoteStaysSmallAndGray() throws Exception {
		String note = rule(css(), ".auto-budget-note");

		assertThat(note).contains("color: var(--hb-text-sub)", "font-size: var(--hb-font-size-small)");
	}

	/** 案のもとにした数字：予算額の欄の枠と同じターコイズ（#0E9594）の 2px の下線と、太字。基本のターコイズは枠（線）にだけ使う決まりなので、下線は border-bottom */
	@Test
	void theSourceOfTheProposalHasATealUnderlineAndIsBold() throws Exception {
		String css = css();

		for (String selector : new String[] { ".budget-table .auto-source", ".budget-table td.auto-source-cell > span" }) {
			String rule = rule(css, selector);
			assertThat(rule).as(selector).contains("border-bottom: 2px solid var(--hb-teal)", "font-weight: 700");
			assertThat(rule).as(selector + "：基本のターコイズは、線だけ").doesNotContain("color: var(--hb-teal)");
		}
		assertThat(css).contains("--hb-teal: #0E9594");
	}

	/** 「ペース 約 …」は行いっぱいの block なので、下線が文字の幅だけになるよう、中身の幅にして右に寄せる */
	@Test
	void theUnderlineOfThePaceNoteIsOnlyAsWideAsItsText() throws Exception {
		assertThat(rule(css(), ".budget-table small.auto-source")).contains("width: fit-content", "margin-left: auto");
	}

	/** 予算額の列は、今の 2px の枠と太字のまま */
	@Test
	void theAmountColumnKeepsItsBorderAndTheBoldText() throws Exception {
		String css = css();

		assertThat(rule(css, ".budget-table td.budget-amount")).contains("font-weight: 700");
		assertThat(rule(css, ".budget-table input[type=\"text\"]")).contains("border: 2px solid var(--hb-teal)");
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

	/** 予算額の入力欄は、取引の登録画面の金額の欄（.amount-input）と同じ形：2px のターコイズ（#0E9594）の枠、大きめの文字、右寄せ */
	@Test
	void theAmountInputsLookLikeTheAmountFieldOfTheTransactionForm() throws Exception {
		String css = css();
		String input = rule(css, ".budget-table input[type=\"text\"]");

		assertThat(css).contains("--hb-teal: #0E9594");
		assertThat(input).contains("border: 2px solid var(--hb-teal)", "text-align: right", "font-size: 1.125rem");
		assertThat(rule(css, ".amount-input")).as("取引の登録画面の金額の欄と、枠が同じ").contains("border: 2px solid var(--hb-teal)");
	}

	/** 基本のターコイズは枠にだけ使う決まり：入力欄の規則の中で、--hb-teal は border だけ。選んだとき（フォーカス）の枠は、フォームの入力欄と同じ */
	@Test
	void theBaseTealIsOnlyUsedForTheBorderAndTheFocusBorderIsLikeTheOtherInputs() throws Exception {
		String css = css();
		String input = rule(css, ".budget-table input[type=\"text\"]");

		assertThat(input.replace("border: 2px solid var(--hb-teal);", "")).as("枠のほかに、基本のターコイズを使わない").doesNotContain("var(--hb-teal)");
		assertThat(rule(css, ".budget-table input[type=\"text\"]:focus")).contains("border-color: var(--hb-teal-button)");
	}
}
