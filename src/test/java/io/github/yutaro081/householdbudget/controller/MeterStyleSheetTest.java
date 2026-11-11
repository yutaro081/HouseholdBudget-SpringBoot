package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * メーターの CSS（style.css）のテスト（設計書 2.3「メーターの見た目」、issue 020）。
 * 棒の右端（100％ ＝ 予算）の短い灰色の縦線、「予算」の小さな文字、予算を超えたときの赤い棒。
 * 見た目そのもの（線の見分けやすさ）は、ここでは確かめられない。ブラウザで確かめる。
 */
class MeterStyleSheetTest {

	private static String css() throws Exception {
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 棒の右端に、濃い灰色の短い縦線（幅 2px。「今日」の線より短い） */
	@Test
	void theBudgetLineIsAShortDarkGrayLineAtTheRightEndOfTheMeter() throws Exception {
		String line = rule(css(), ".top-meter::after");

		assertThat(line).contains("content: \"\"", "position: absolute", "right: 0", "width: 2px", "background: var(--hb-text-sub)");
		assertThat(line).as("予算の線は、上下に 0.125rem ずつはみ出す").contains("top: -0.125rem", "bottom: -0.125rem");
	}

	@Test
	void theTodayLineIsLongerAndInTheTextColorSoTheTwoLinesCanBeToldApart() throws Exception {
		String css = css();
		String today = rule(css, ".top-meter-today");

		assertThat(today).contains("top: -0.25rem", "bottom: -0.25rem", "background: var(--hb-text)");
		assertThat(rule(css, ".top-meter::after")).as("予算の線は「今日」の線と同じ色にしない").doesNotContain("background: var(--hb-text);");
	}

	@Test
	void theBudgetLabelIsSmallGrayAndRightAligned() throws Exception {
		String label = rule(css(), ".top-meter-budget-label");

		assertThat(label).contains("text-align: right", "font-size: var(--hb-font-size-small)", "color: var(--hb-text-sub)");
	}

	/** 予算を超えたときの棒は、予算超過の色（赤 #c62828） */
	@Test
	void theBarIsRedWhenTheBudgetIsExceeded() throws Exception {
		assertThat(rule(css(), ".top-meter-bar.top-meter-over")).contains("background: #c62828");
	}

	@Test
	void theBarKeepsItsNormalColorWhenTheBudgetIsNotExceeded() throws Exception {
		assertThat(rule(css(), ".top-meter-bar")).contains("background: var(--hb-chart-2)");
	}
}
