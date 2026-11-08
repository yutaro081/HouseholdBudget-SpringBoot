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

	@Test
	void weekRowColorsAreTheSameAsTheComparisonRules() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-weeks tr.week-over td")).containsIgnoringCase("#c62828");
		assertThat(rule(css, ".top-weeks tr.week-low td")).containsIgnoringCase("#1565c0");
		assertThat(rule(css, ".top-weeks tr.week-within")).contains("var(--hb-apricot)");
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
