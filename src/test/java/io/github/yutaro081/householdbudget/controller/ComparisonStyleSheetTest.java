package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 予実比較の表と要約カードの CSS（style.css）のテスト（設計書 2.3「予実比較」、issue 020、issue 024）。
 * 見た目そのもの（間延びしないか、にじみ）は、ここでは確かめられない。ブラウザで確かめる。
 * ここでは、決めた数字（列の幅、合計行の形、チップ、赤い数字、白いカード、4 段の文字の大きさ）を確かめる。
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

	/** 過去・未来の季節（5 列）の幅：科目 22%・状況 16%・予算 20%・実績 20%・差額 22%（issue 024） */
	@Test
	void theFiveColumnsOfThePastSeasonAreSizedByTheirContent() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table th:first-child")).as("科目").contains("width: 22%");
		assertThat(rule(css, ".comparison-table th.col-status")).as("状況").contains("width: 16%");
		assertThat(rule(css, ".comparison-table th.col-budget")).as("予算").contains("width: 20%");
		assertThat(rule(css, ".comparison-table th.col-actual")).as("実績").contains("width: 20%");
		assertThat(rule(css, ".comparison-table th.col-diff")).as("差額").contains("width: 22%");
		assertThat(rule(css, ".comparison-table th.num")).as("数字の列の共通の規則には、幅を書かない").doesNotContain("width").contains("text-align: right");
		assertThat(rule(css, ".comparison-table td.num")).doesNotContain("width").contains("text-align: right");
	}

	/**
	 * 今の季節（7 列。表に has-forecast）の幅：科目 13%・今の状況 11%・予算 13%・実績 13%・残り 13%・見込み 17%・見込みとの差 20%。
	 * 数字の 3 列（予算・実績・残り）は同じ幅。見込みと見込みとの差は、中身が長いので広くする（5 列を等分すると、見込みとの差が横 1366px に収まらない）
	 */
	@Test
	void theSevenColumnsOfTheCurrentSeasonAreSizedByTheirContent() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table.has-forecast th:first-child")).as("科目").contains("width: 13%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-status")).as("今の状況").contains("width: 11%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-budget")).as("予算").contains("width: 13%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-actual")).as("実績").contains("width: 13%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-remain")).as("残り").contains("width: 13%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-forecast")).as("見込み").contains("width: 17%");
		assertThat(rule(css, ".comparison-table.has-forecast th.col-gap")).as("見込みとの差").contains("width: 20%");
		assertThat(rule(css, ".comparison-table.has-forecast")).as("7 列は、せまい幅で横にスクロールする（見込みとの差が約 214px 必要な幅から）").contains("min-width: 68rem");
	}

	/** 残りと見込みとの差を、同じくらい目立たせる：数字は太字、大きさは変数 1 か所（今は 1rem。20px に戻すときは、この変数と列の割合を変える）。見出しは濃いターコイズ（#0B6E6B）。マイナスは赤 */
	@Test
	void theRemainingAndTheGapAreEquallyProminentWithOneSizeVariable() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table td.gap-cell")).contains("font-weight: 700", "font-size: var(--hb-key-number-size)");
		assertThat(rule(css, ".comparison-table td.remain-cell")).contains("font-weight: 700", "font-size: var(--hb-key-number-size)");
		assertThat(rule(css, ".comparison-table td.diff-cell")).contains("font-weight: 700", "font-size: var(--hb-key-number-size)");
		assertThat(rule(css, ":root")).contains("--hb-key-number-size: 1rem");
		assertThat(rule(css, ".comparison-table th.col-gap")).contains("color: var(--hb-teal-text)");
		assertThat(rule(css, ".comparison-table th.col-remain")).contains("color: var(--hb-teal-text)");
		assertThat(rule(css, ".comparison-table th.col-diff")).contains("color: var(--hb-teal-text)");
		assertThat(rule(css, ".comparison-table td.gap-over")).contains("color: var(--hb-over)");
		assertThat(rule(css, ".comparison-table td.diff-over")).contains("color: var(--hb-over)");
	}

	/** 「残り」と「見込み」の間の縦の区切り線（見込みの列の左。見出しもセルも）：1px、#C9D2D1（変数 --hb-divider） */
	@Test
	void aThinVerticalLineSeparatesTheTwoGroupsOfColumns() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table th.col-forecast")).contains("border-left: 1px solid var(--hb-divider)");
		assertThat(rule(css, ".comparison-table td.forecast-cell")).contains("border-left: 1px solid var(--hb-divider)");
	}

	@Test
	void everyRowHasTheSameMinimumHeightSoTheChipsDoNotMakeSomeRowsTaller() throws Exception {
		assertThat(rule(css(), ".comparison-table tbody td")).contains("height: 2.75rem");
	}

	/** 文字の大きさは 4 段（28・20・16・13px）だけ：表のセルは本文（1rem）、見出しは補足（13px）の太字で、色は補足の文字（#56625F）。17px（1.0625rem）などは使わない */
	@Test
	void theCellsAreTheBodySizeAndTheHeadingsAreTheSmallSizeBoldInTheSubColor() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-table tbody td")).contains("font-size: var(--hb-font-size-body)");
		assertThat(rule(css, ".comparison-table thead th")).contains("font-size: var(--hb-font-size-small)", "font-weight: 700", "color: var(--hb-text-sub)");
		String all = rule(css, ".comparison-table tbody td") + rule(css, ".comparison-table thead th") + rule(css, ".comparison-table td.remain-cell")
				+ rule(css, ".comparison-table td.gap-cell") + rule(css, ".comparison-table td.diff-cell");
		assertThat(all).as("4 段の外の大きさは使わない").doesNotContain("1.0625rem", "1.125rem", "1.5em", "0.625rem");
		assertThat(css).as("💮 の大きな絵文字の規則は、なくなった").doesNotContain(".comparison-table .within-mark");
	}

	/** 数字の列の全体は、太字にしない。太字は、残り・見込みとの差・差額のセルだけ（クラスで指定する。「最後の列」では指定しない。列を足しても崩れない） */
	@Test
	void onlyTheKeyNumberCellsAreBoldByTheirOwnClassNotByTheLastColumn() throws Exception {
		String css = css();

		assertThat(css).as("最後の列で指定しない（列を足すと、そちらが太字になってしまう）").doesNotContain(".comparison-table tbody td:last-child").doesNotContain("td:last-child {\n    font-weight: 700");
		assertThat(rule(css, ".comparison-table td.num")).as("数字の列の全体は、太字にしない").doesNotContain("font-weight");
	}

	/** 見込みのセルの「/ その科目の予算」は、小さめの灰色（400） */
	@Test
	void theBudgetInTheForecastCellIsSmallAndGray() throws Exception {
		assertThat(rule(css(), ".comparison-table .cell-sub")).contains("color: var(--hb-text-sub)", "font-size: var(--hb-font-size-small)", "font-weight: 400");
	}

	/** 合計行（予算設定の合計の行と同じ形）：1.25rem（20px。4 段の中の大きさ）の太字、上の線を太く（3px）。issue 024 で決めた：小さくしない */
	@Test
	void theTotalRowIsLargeWithAThickLineAboveLikeTheBudgetScreen() throws Exception {
		String total = rule(css(), ".comparison-table tr.total-row td");

		assertThat(total).contains("font-size: 1.25rem", "font-weight: 700", "border-top: 3px solid var(--hb-text)");
	}

	/** 色は、チップと、残り・見込みとの差の赤い数字だけ。青い数字（diff-low）と、行の背景（row-within）、行の文字全体の色の規則は、なくなった */
	@Test
	void colorIsOnTheChipsAndTheRedNumbersOnly() throws Exception {
		String css = css();

		assertThat(css).doesNotContain(".comparison-table td.diff-low", ".comparison-table tr.row-within", "tr.row-over td", "tr.row-low td", ".comparison-table .cell-mark");
	}

	/** チップ：13px、400、枠 1px、角を丸く、折り返さない。意味ごとに、色の変数（赤・青・予算内）を使う */
	@Test
	void theChipsAreSmallRoundedLabelsWithTheMeaningColors() throws Exception {
		String css = css();
		String chip = rule(css, ".chip");

		assertThat(chip).contains("white-space: nowrap", "border: 1px solid", "border-radius: 999px", "font-size: var(--hb-font-size-small)", "font-weight: 400");
		assertThat(chip).as("数字の下の 2 行目に置かない（その行だけ高くなる）").doesNotContain("display: block");
		assertThat(rule(css, ".chip-over")).contains("color: var(--hb-over)", "border-color: var(--hb-over)");
		assertThat(rule(css, ".chip-low")).contains("color: var(--hb-low)", "border-color: var(--hb-low)");
		assertThat(rule(css, ".chip-within")).contains("background-color: var(--hb-apricot)", "border-color: var(--hb-within-border)", "color: var(--hb-within-text)");
		assertThat(rule(css, ".chip-within .chip-icon")).as("花のアイコン（塗りの花。色は SVG の中に固定で書く）").contains("display: inline-flex").doesNotContain("color:");
	}

	/** 表は白いカード：背景 白、枠 1px #E2E8E8（今ある --hb-line。新しい色は増やさない）、角丸 16px、影なし */
	@Test
	void theTableCardIsAWhiteCardWithTheExistingLineColor() throws Exception {
		String card = rule(css(), ".table-card");

		assertThat(card).contains("background-color: var(--hb-input-bg)", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(card).doesNotContain("box-shadow");
	}

	@Test
	void theCardPutsThreeBigNumbersInARowAndTheMeterUsesTheFullWidth() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card-figures")).as("上の段は、カードの幅に合わせて列に分ける。せまいときは、縦に並ぶ（1 列）。下の線をそろえる")
				.contains("display: grid", "grid-template-columns: 1fr", "align-items: start").doesNotContain("last baseline", "flex-wrap", "justify-content");
		assertThat(css).as("メーターは、カードの幅いっぱい（幅の上限をなくした）").doesNotContain("max-width: 32rem");
	}

	/** 上の段は、1：1：2 の 3 列（1 列目 予算、2 列目 実績、3 列目 見込みと差の文）。列の中は左寄せ。幅 768px 以上で 3 列、それより狭いときは縦に並ぶ（1 列）。3 列目に入らない差の文は、見込みの下に回り込む（.comparison-card-forecast-row の flex-wrap） */
	@Test
	void theTopRowOfTheCardIsThreeColumnsInTheRatioOneOneTwoOnWideScreensAndOneColumnOnNarrowOnes() throws Exception {
		String css = css();

		assertThat(css).containsPattern("(?s)@media \\(min-width: 768px\\)\\s*\\{\\s*\\.comparison-card-figures\\s*\\{[^}]*grid-template-columns: 1fr 1fr 2fr");
		assertThat(rule(css, ".comparison-card-figures")).as("せまいときは 1 列").contains("grid-template-columns: 1fr;");
		assertThat(rule(css, ".comparison-card-item")).as("列の中は、左寄せ").doesNotContain("text-align: right", "text-align: center", "align-items: center", "align-items: flex-end");
		assertThat(rule(css, ".comparison-card-forecast-row")).as("3 列目に入らない差の文は、下に回り込む").contains("flex-wrap: wrap");
	}

	@Test
	void theSummaryCardIsAWhiteCardWithABigNumber() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card")).contains("background-color: var(--hb-input-bg)", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(rule(css, ".comparison-card-forecast")).as("見込みは、いちばん大きな字（28px）").contains("font-size: var(--hb-font-size-h1)", "font-weight: 700");
	}

	/** 表は白いカード：表のセルの背景（Bootstrap の --bs-table-bg は、ふだんページの背景色）を透明にして、カードの白を見せる（issue 024 の見直し） */
	@Test
	void theCellsOfTheTableInsideTheCardAreTransparentSoTheCardIsWhite() throws Exception {
		assertThat(rule(css(), ".table-card table")).contains("--bs-table-bg: transparent");
	}

	/**
	 * 縦の線は、「残り」と「見込み」の間（見込みの列の左）だけ（issue 024 の見直し）。ほかの列（科目の左を含む）に、縦の線（border-left・border-right・border-inline）を引く規則は、ない。
	 * 表の外側の枠は、白いカード（.table-card）の枠だけ
	 */
	@Test
	void onlyTheForecastColumnHasAVerticalLine() throws Exception {
		String css = css();
		Matcher rules = Pattern.compile("(?m)^([^{}\\n]*\\.comparison-table[^{}\\n]*)\\{([^}]*)\\}").matcher(css);
		int found = 0;
		while (rules.find()) {
			String selector = rules.group(1).strip();
			String body = rules.group(2);
			boolean vertical = body.contains("border-left") || body.contains("border-right") || body.contains("border-inline")
					|| Pattern.compile("(?m)^\\s*border\\s*:").matcher(body).find();
			if (vertical) {
				found++;
				assertThat(selector).as("縦の線を引く規則は、見込みの列だけ").isIn(".comparison-table th.col-forecast", ".comparison-table td.forecast-cell");
			}
		}
		assertThat(found).as("見込みの列の見出しとセルの 2 つだけ").isEqualTo(2);
		assertThat(rule(css, ".comparison-table th:first-child")).as("科目の列は、幅だけ").doesNotContain("border");
		assertThat(rule(css, ".comparison-table.has-forecast th:first-child")).doesNotContain("border");
		assertThat(css).doesNotContain(".comparison-table td:first-child", ".comparison-table tr > :first-child");
	}

	/**
	 * 要約カードの文字の大きさは 4 段：ラベルは 13px の 400（色は補足の文字 #56625F）、予算・実績の数字は 20px の太字、見込みの数字は 28px の太字、差の文は 16px の 400（多いときだけ赤）。
	 * 3 つの数字の間は 48px（var(--hb-space-7)）。差の文は、見込みの数字のすぐ右で、下の線（ベースライン）を見込みの数字にそろえる。メーター上のラベルと、「上回ります」の行の規則は、なくなった
	 */
	@Test
	void theCardFiguresLabelsAndTheVerdictUseTheFourSizes() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card-label")).contains("font-size: var(--hb-font-size-small)", "font-weight: 400", "color: var(--hb-text-sub)");
		assertThat(rule(css, ".comparison-card-figure")).as("予算・実績の数字は、見込みと同じ 28px の太字（3 列に広げて、20px だと小さく見えるため）").contains("font-size: var(--hb-font-size-h1)", "font-weight: 700");
		assertThat(rule(css, ".comparison-card-forecast")).contains("font-size: var(--hb-font-size-h1)", "font-weight: 700");
		assertThat(css).as("カード上の見出し（h2）の規則は、なくした").doesNotContain(".comparison-card h2");
		assertThat(rule(css, ".comparison-card-verdict")).contains("font-size: var(--hb-font-size-body)", "font-weight: 400");
		assertThat(rule(css, ".comparison-card-verdict-over")).contains("color: var(--hb-over)");
		assertThat(rule(css, ".comparison-card-figures")).contains("var(--hb-space-7)");
		assertThat(rule(css, ".comparison-card-forecast-row")).contains("display: flex", "align-items: baseline", "flex-wrap: wrap");
		assertThat(rule(css, ":root")).as("48px").contains("--hb-space-7: 3rem");
		assertThat(css).doesNotContain(".comparison-card-labels", ".comparison-card-over", ".comparison-card-amount");
	}

	/**
	 * 3 つの数字の下の線（ベースライン）は、いつも見込みの数字にそろう。3 つの数字は同じ大きさ（28px）で、列は上そろえ（align-items: start）。
	 * 差の文が見込みの下に回り込んでも、予算・実績の数字は、差の文の行にそろわない（last baseline は使わない）。見込みの行の中では、差の文を、見込みの数字の下の線にそろえる
	 */
	@Test
	void theThreeNumbersShareTheSameSizeAndTopAlignmentSoTheirBaselinesMatchEvenWhenTheVerdictWraps() throws Exception {
		String css = css();

		assertThat(rule(css, ".comparison-card-figure")).contains("font-size: var(--hb-font-size-h1)");
		assertThat(rule(css, ".comparison-card-forecast")).contains("font-size: var(--hb-font-size-h1)");
		assertThat(rule(css, ".comparison-card-figures")).contains("align-items: start").doesNotContain("last baseline");
		assertThat(rule(css, ".comparison-card-forecast-row")).as("差の文は、見込みの数字の下の線にそろえる").contains("align-items: baseline");
	}
}
