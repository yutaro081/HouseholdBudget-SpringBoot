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
		css = css.replace("\r\n", "\n");
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

	/** 表のセルの文字は、本文と同じ濃さ（#24302F）。灰色にしない。大きさは、予実比較の表と同じ（4 段の本文 1rem。issue 024 の段階 11） */
	@Test
	void theCellsHaveTheBodyColorAndTheSameSizeAsTheComparisonTable() throws Exception {
		String css = css();
		String cells = rule(css, ".budget-table tbody td");

		assertThat(css).contains("--hb-text: #24302F");
		assertThat(cells).contains("color: var(--hb-text)", "font-size: var(--hb-font-size-body)");
		assertThat(cells).as("1.0625rem は 4 段の外").doesNotContain("1.0625rem");
		assertThat(rule(css, ".comparison-table tbody td")).as("予実比較の表と同じ大きさ").contains("font-size: var(--hb-font-size-body)");
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

	/** 案のもとにした数字の目印（issue 024 の段階 11）：下線をやめ、薄いターコイズの背景（#E1F3F2）と太字。文字は本文の色のまま */
	@Test
	void theSourceOfTheProposalHasAPaleTealBackgroundAndIsBold() throws Exception {
		String css = css();

		for (String selector : new String[] { ".budget-table .auto-source", ".budget-table td.auto-source-cell > span" }) {
			String rule = rule(css, selector);
			assertThat(rule).as(selector).contains("background-color: var(--hb-teal-pale)", "font-weight: 700");
			assertThat(rule).as(selector + "：下線をやめた").doesNotContain("border-bottom", "underline");
			assertThat(rule).as(selector + "：文字の色は変えない（本文の色）").doesNotContainPattern("(?<![-a-z])color:");
		}
		assertThat(css).contains("--hb-teal-pale: #E1F3F2");
	}

	/** 自動予算のカード：白い地、左に 4px のターコイズの線（複写のお知らせ alert-info と同じ形）、角丸 16px、影なし。基本のターコイズは線にだけ使う */
	@Test
	void theAutoBudgetCardIsWhiteWithA4pxTealLineOnTheLeft() throws Exception {
		String card = rule(css(), ".auto-budget-card");

		assertThat(card).contains("background-color: var(--hb-input-bg)", "border-left: 4px solid var(--hb-teal)",
				"border-radius: var(--hb-radius-card)");
		assertThat(card).contains("border: 1px solid var(--hb-line)");
		assertThat(card).doesNotContain("box-shadow");
		assertThat(card.replace("border-left: 4px solid var(--hb-teal);", "")).as("基本のターコイズは線だけ").doesNotContain("var(--hb-teal)");
	}

	/** 一の位をそろえる：入力欄の数字の右端と、合計の数字の右端は、同じ余白の変数（1 つ）で決める */
	@Test
	void theLastDigitOfTheInputsAndTheTotalAreAlignedByOneVariable() throws Exception {
		String css = css();
		Matcher definition = Pattern.compile("--hb-budget-digit-inset:\s*([^;]+);").matcher(css);

		assertThat(definition.find()).as("余白の変数がある").isTrue();
		assertThat(definition.find()).as("変数は 1 つだけ決める").isFalse();
		assertThat(rule(css, ".budget-table input[type=\"text\"]")).contains("padding-right: var(--hb-budget-digit-inset)");
		String total = rule(css, ".budget-table tfoot td.budget-total-cell");
		assertThat(total).as("合計のセルの右の余白 ＝ セルの余白 ＋ 入力欄の枠 2px ＋ 入力欄の内側の余白")
				.contains("padding-right: calc(0.5rem + 2px + var(--hb-budget-digit-inset))");
	}

	/** 文字の大きさは 4 段（入力欄・セル・「自動予算」ボタンは 1rem）。合計の行は、20px の太字のまま（上の太い線 3px も） */
	@Test
	void sizesAreOnTheFourStepsAndTheTotalRowStaysAt20px() throws Exception {
		String css = css();

		assertThat(rule(css, ".budget-table input[type=\"text\"]")).contains("font-size: var(--hb-font-size-body)").doesNotContain("1.125rem");
		assertThat(rule(css, ".btn.auto-budget-button")).contains("--bs-btn-font-size: var(--hb-font-size-body)").doesNotContain("1.0625rem");
		assertThat(rule(css, ".budget-table .budget-sum")).contains("font-size: 1.25rem", "font-weight: 700");
		assertThat(rule(css, ".budget-table tfoot th,\n.budget-table tfoot td")).contains("font-size: 1.25rem", "border-top: 3px solid var(--hb-text)");
	}

	/** 右の 2 列（前の季節の実績・前の季節の予算）は、同じ幅（18%）。「5,600 / ペース 約 7,100」が 1 行に収まるよう、折り返さない */
	@Test
	void theTwoRightColumnsHaveTheSameWidthAndDoNotWrap() throws Exception {
		String css = css();

		assertThat(rule(css, ".budget-table th.budget-prev")).contains("width: 18%");
		assertThat(rule(css, ".budget-table td.budget-prev")).contains("width: 18%", "white-space: nowrap");
		assertThat(css).as("「ペース」を 2 行目に置いていた時の規則（行いっぱいの block の下線の幅）は、なくした").doesNotContain("small.auto-source");
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

	/** 予算額の入力欄の文字は、表のセルと同じ 1rem（4 段の本文。issue 024 の段階 11） */
	@Test
	void theAmountInputsHaveTheBodySize() throws Exception {
		assertThat(rule(css(), ".budget-table input[type=\"text\"]")).contains("font-size: var(--hb-font-size-body)");
	}

	/** 予算額の入力欄は、取引の登録画面の金額の欄（.amount-input）と同じ枠：2px のターコイズ（#0E9594）の枠、右寄せ */
	@Test
	void theAmountInputsLookLikeTheAmountFieldOfTheTransactionForm() throws Exception {
		String css = css();
		String input = rule(css, ".budget-table input[type=\"text\"]");

		assertThat(css).contains("--hb-teal: #0E9594");
		assertThat(input).contains("border: 2px solid var(--hb-teal)", "text-align: right", "font-size: var(--hb-font-size-body)");
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
