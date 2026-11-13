package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * トップの CSS（style.css）のテスト（設計書 2.3「色の決まり」、4.3、issue 010 の Q010-9）。
 * 見た目そのもの（円の角度、棒の位置、色の見え方）は、ここでは確かめられない。ブラウザで確かめる。
 * ここでは、色の変数がそろっていること、赤・青・💮 の色が予実比較と同じ値であること、
 * 棒・円の色に基本のターコイズ（--hb-teal）を使っていないこと（ColorSchemeStyleSheetTest の「枠だけ」の決まりを守る）を確かめる。
 */
class TopStyleSheetTest {

	/** 設計書 2.3 の値（2026年11月8日に測り直して決めた） */
	private static final Map<String, String> CHART_COLORS = Map.of(
			"--hb-chart-1", "#0B6E6B",
			"--hb-chart-2", "#0F8F8E",
			"--hb-chart-3", "#3FAAA9",
			"--hb-chart-4", "#78C2C1",
			"--hb-chart-5", "#A5D8D7",
			"--hb-chart-6", "#8A9694");

	private static String css() throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
	}

	private static String withoutComments(String css) {
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	/** 選ぶ部分が selector の規則の中身を返す（なければ失敗） */
	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(withoutComments(css));
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	@Test
	void chartColorVariablesAreDefinedWithTheDesignValues() throws Exception {
		String root = rule(css(), ":root");
		for (Map.Entry<String, String> e : CHART_COLORS.entrySet()) {
			assertThat(root).as(e.getKey()).containsIgnoringCase(e.getKey() + ": " + e.getValue());
		}
	}

	@Test
	void chartColorsAreNotTheBaseTealNorTheMeaningColors() throws Exception {
		String css = withoutComments(css());
		for (String value : CHART_COLORS.values()) {
			assertThat(value).isNotEqualToIgnoringCase("#0E9594");     // 基本のターコイズは枠だけ
			assertThat(value).isNotEqualToIgnoringCase("#c62828");     // 赤
			assertThat(value).isNotEqualToIgnoringCase("#1565c0");     // 青
			assertThat(value).isNotEqualToIgnoringCase("#FFF2E5");     // 💮 の背景
		}
		Matcher m = Pattern.compile("(?s)\\.(top-[a-z-]+|week-[a-z-]+)[^{]*\\{([^}]*)\\}").matcher(css);
		while (m.find()) {
			assertThat(m.group(2)).as("トップの規則 ." + m.group(1) + " は --hb-teal を使わない").doesNotContain("var(--hb-teal)");
		}
	}

	/** 今週 何に使ったか：期間は見出しと同じ行（せまいときだけ折り返す）。一覧は、行の上下の余白が半分、行の間の線なし。赤・青は金額の色 */
	@Test
	void pieHeadingRowAndCompactListAndNoJudgementColors() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-pie-head")).contains("display: flex", "flex-wrap: wrap", "align-items: baseline");
		assertThat(rule(css, ".top-pie-list td")).contains("padding-top: 0.25rem", "padding-bottom: 0.25rem", "border-bottom-width: 0");
		assertThat(withoutComments(css)).as("円グラフの一覧に、判定の色の規則はない（issue 024）").doesNotContain("pie-over", "pie-low");
		assertThat(withoutComments(css)).as("行の背景（薄いアプリコット）は付けない").doesNotContainPattern("\\.top-pie[^{]*\\{[^}]*apricot");
	}

	/** 今週の進み具合の行に、色・背景の規則はない（状態は、チップで示す。issue 024） */
	@Test
	void weekRowsHaveNoRowColorOrBackgroundRules() throws Exception {
		String css = withoutComments(css());

		assertThat(css).doesNotContain(".top-weeks li.week-over", ".top-weeks li.week-low", ".top-weeks li.week-within");
	}

	/** トップの文字の大きさは 4 段だけ：4 段の外（17px・18px・10px・文字の 1.5 倍）は、トップの規則に使わない。今日の矢印は 13px、今週のひとことは 16px */
	@Test
	void theTopRulesUseOnlyTheFourFontSizes() throws Exception {
		String css = withoutComments(css());

		Matcher m = Pattern.compile("(?m)^(\\.top-[^{\\n]*|\\.hitokoto[^{\\n]*)\\{([^}]*)\\}").matcher(css);
		int checked = 0;
		while (m.find()) {
			checked++;
			assertThat(m.group(2)).as(m.group(1).strip()).doesNotContain("font-size: 1.0625rem", "font-size: 1.125rem", "font-size: 0.625rem", "font-size: 1.5em");
		}
		assertThat(checked).isGreaterThan(20);
		assertThat(rule(css, ".top-meter-today-arrow")).contains("font-size: var(--hb-font-size-small)");
		assertThat(rule(css, ".top-week-pick")).contains("font-size: var(--hb-font-size-body)");
	}

	/**
	 * 円グラフの一覧は、円の右に並べる（issue 024）：円は 180px。左の列（470px）の中で、円と一覧が横に並ぶ。幅が足りないときは、一覧が円の下に回り込む（flex-wrap）。
	 * 一覧は、残りの幅を使う（flex: 1 1 13rem）。一覧の中身（金額と割合だけ）は変えない
	 */
	@Test
	void thePieAndItsListAreSideBySideAndTheListWrapsBelowWhenThereIsNoRoom() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-pie-box")).contains("display: flex", "flex-direction: row", "flex-wrap: wrap").doesNotContain("flex-direction: column");
		assertThat(rule(css, ".top-pie")).as("円は 180px").contains("width: 180px", "height: 180px", "border-radius: 50%");
		assertThat(rule(css, ".top-pie-list")).as("一覧は、残りの幅を使う。入りきらないときは、下に回り込む").contains("flex: 1 1 13rem", "min-width: 0");
		assertThat(rule(css, ".top-pie-list td.num")).as("狭くなった一覧でも、金額と割合は折り返さない（「4,200 / 円」にならない）").contains("white-space: nowrap");
		assertThat(rule(css, ".top-pie-list td")).as("横の余白を詰める").contains("padding-left: 0.25rem", "padding-right: 0.25rem");
		// 円（180px）＋ すきま（16px）＋ 一覧の最小の幅（13rem ＝ 208px）が、左の列の中の幅（470px − 左右の余白 48px − 枠 2px ＝ 420px）に収まる
		assertThat(180 + 16 + 13 * 16).isLessThanOrEqualTo(470 - 2 * 24 - 2);
	}

	@Test
	void pieAndMeterRulesExist() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-pie")).contains("border-radius: 50%");
		assertThat(rule(css, ".top-meter-bar")).contains("var(--hb-chart-2)");
		assertThat(rule(css, ".top-meter-today")).contains("position: absolute");
	}

	@Test
	void everyChartColorNumberUsedByThePieHasAVariable() throws Exception {
		// 円の色は var(--hb-chart-1)〜var(--hb-chart-6) だけを使う（PieSlices.cssStop）
		assertThat(CHART_COLORS).hasSize(6);
		assertThat(css()).contains("--hb-chart-1:", "--hb-chart-6:");
	}

}
