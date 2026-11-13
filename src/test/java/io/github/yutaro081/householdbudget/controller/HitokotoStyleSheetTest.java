package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 「きょうのひとこと」の枠の CSS（style.css）のテスト（設計書 2.3「色の決まり」、手順書 issue-016-hitokoto.md）。
 * 決まり：白地に、左だけターコイズの線。薄いターコイズの大きな面は使わない。基本のターコイズ（--hb-teal）は枠の線だけ。
 * 見た目そのもの（線の見え方、余白）は、ここでは確かめられない。ブラウザで確かめる。
 */
class HitokotoStyleSheetTest {

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

	@Test
	void theFrameIsAPlainWhiteCardLikeTheOtherCardsWithoutTheLeftTealLine() throws Exception {
		String css = css();
		String frame = rule(css, ".hitokoto-frame");

		assertThat(frame).contains("background: #fff", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(frame).as("左の 4px のターコイズの線は、なくした（issue 024。ほかのカードと同じ形）").doesNotContain("border-left", "var(--hb-teal)");
		assertThat(rule(css, ".top-section")).as("ほかのカードと同じ余白").contains("padding: var(--hb-space-4) var(--hb-space-5)");
		assertThat(frame).contains("padding: var(--hb-space-4) var(--hb-space-5)");
	}

	/** 見出し「きょうのひとこと」は、ほかのカードの見出し（.top-section h2）と同じ：20px の太字、本文の色（ターコイズにしない） */
	@Test
	void theHeadingIsTheSameAsTheOtherCardHeadingsTwentyPxBoldInTheBodyColor() throws Exception {
		String css = css();
		String h2 = rule(css, ".top-section h2,\n.hitokoto-frame h2");   // 段階 12：ほかのカードの見出しと同じ規則

		assertThat(h2).contains("font-size: var(--hb-font-size-h2)");
		assertThat(h2).doesNotContain("color:", "font-size: var(--hb-font-size-body)");
	}

	/**
	 * 決まり：枠そのものと、ほかの規則は、薄いターコイズを使わない（大きな面にしない）。
	 * 例外（issue 017）：アイコンの小さな四角（.hitokoto-icon）だけは、薄いターコイズの地にしてよい（小さな印。issue 015）。
	 */
	@Test
	void thePaleTealIsUsedOnlyByTheSmallIconSquare() throws Exception {
		Matcher m = Pattern.compile("(?m)^(\\.[^{}]*hitokoto[^{}]*)\\{([^}]*)\\}").matcher(css());
		int rules = 0;
		boolean iconRule = false;
		while (m.find()) {
			rules++;
			String selector = m.group(1).trim();
			String body = m.group(2);
			if (selector.equals(".hitokoto-icon")) {
				iconRule = true;
				continue;
			}
			assertThat(body).as(selector + " は薄いターコイズを使わない").doesNotContain("--hb-teal-pale").doesNotContain("#E1F3F2");
			assertThat(body).as(selector).doesNotContain("#c62828").doesNotContain("#1565c0").doesNotContain("--hb-apricot");
		}
		assertThat(rules).isGreaterThan(0);
		assertThat(iconRule).as(".hitokoto-icon の規則がある").isTrue();
	}

	@Test
	void theIconSquareIsSmallWithPaleTealGroundAndDarkTealLine() throws Exception {
		String icon = rule(css(), ".hitokoto-icon");

		assertThat(icon).contains("width: 36px", "height: 36px", "border-radius: 10px");
		assertThat(icon).contains("background: var(--hb-teal-pale)", "color: var(--hb-teal-text)");
	}

	@Test
	void fontSizesInTheFrameUseOnlyTheFourStepsAndNoPx() throws Exception {
		Matcher m = Pattern.compile("(?m)^\\.[^{}]*hitokoto[^{}]*\\{([^}]*)\\}").matcher(css());
		while (m.find()) {
			Matcher size = Pattern.compile("font-size:\\s*([^;]+);").matcher(m.group(1));
			while (size.find()) {
				assertThat(size.group(1).trim()).startsWith("var(--hb-font-size-");
			}
		}
	}

}
