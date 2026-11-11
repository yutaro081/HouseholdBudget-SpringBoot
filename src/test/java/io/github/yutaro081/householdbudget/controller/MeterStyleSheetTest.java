package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * メーターの CSS（style.css）のテスト（設計書 2.3「メーターの見た目」、issue 020）。
 * 予算（100％）の位置の短い灰色の縦線、「予算」の小さな文字、予算を超えた部分の赤、「+○%」の文字。
 * 縮尺（70％）は MeterScale だけで決める：CSS に 70％ を書かない。
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

	/** 予算の線：濃い灰色の短い縦線（幅 2px。「今日」の線より短い）。位置は style 属性で渡される（CSS に 70％ を書かない） */
	@Test
	void theBudgetLineIsAShortDarkGrayLine() throws Exception {
		String css = css();
		String line = rule(css, ".top-meter-budget");

		assertThat(line).contains("position: absolute", "width: 2px", "background: var(--hb-text-sub)");
		assertThat(line).as("予算の線は、上下に 0.125rem ずつはみ出す").contains("top: -0.125rem", "bottom: -0.125rem");
		assertThat(css).as("右端の線（::after）は、なくした").doesNotContain(".top-meter::after");
	}

	@Test
	void theTodayLineIsLongerAndInTheTextColorSoTheTwoLinesCanBeToldApart() throws Exception {
		String css = css();
		String today = rule(css, ".top-meter-today");

		assertThat(today).contains("top: -0.25rem", "bottom: -0.25rem", "background: var(--hb-text)");
		assertThat(rule(css, ".top-meter-budget")).as("予算の線は「今日」の線と同じ色にしない").doesNotContain("background: var(--hb-text);");
	}

	/** 「予算」の文字は、線の位置を中心にそろえる（位置は style 属性の margin-left で渡される） */
	@Test
	void theBudgetLabelIsSmallGrayAndCenteredOnTheLine() throws Exception {
		String label = rule(css(), ".top-meter-budget-label");

		assertThat(label).contains("width: max-content", "transform: translateX(-50%)", "font-size: var(--hb-font-size-small)", "color: var(--hb-text-sub)");
	}

	/** 予算を超えた部分だけが、予算超過の色（赤 #c62828） */
	@Test
	void theExceededPartIsRed() throws Exception {
		assertThat(rule(css(), ".top-meter-bar.top-meter-over")).contains("background: #c62828");
	}

	@Test
	void theBarKeepsItsNormalColorUpToTheBudget() throws Exception {
		assertThat(rule(css(), ".top-meter-bar")).contains("background: var(--hb-chart-2)");
	}

	/** 143％ を超えたら、棒の右に小さく「+○%」（赤い小さな文字） */
	@Test
	void theOverflowPercentIsASmallRedTextToTheRightOfTheBar() throws Exception {
		String overflow = rule(css(), ".top-meter-excess");

		assertThat(overflow).contains("color: #c62828", "font-size: var(--hb-font-size-small)", "white-space: nowrap");
	}

	/** 棒の右に「+○%」の幅（3.25rem）をいつもあける（縮尺がメーターによってずれないため）。グリッドで、棒・「+○%」・「予算」を並べる */
	@Test
	void everyMeterReservesTheRoomForTheOverflowPercent() throws Exception {
		assertThat(rule(css(), ".top-meter-box")).contains("display: grid", "grid-template-columns: minmax(0, 1fr) 3.25rem");
	}

	/** 縮尺（70％）は MeterScale の 1 か所だけ。CSS には書かない */
	@Test
	void theScaleIsNotWrittenInTheStyleSheet() throws Exception {
		assertThat(css()).doesNotContainPattern("(?<![0-9.])70(\\.0)?%");
	}
}
