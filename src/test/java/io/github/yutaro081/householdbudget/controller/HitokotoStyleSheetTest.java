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
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	@Test
	void theFrameIsWhiteWithOnlyTheLeftLineInTeal() throws Exception {
		String frame = rule(css(), ".hitokoto-frame");

		assertThat(frame).contains("background: #fff");
		assertThat(frame).contains("border-left: 4px solid var(--hb-teal)");
		// 左以外の線は、ふつうの区切り線の色（ターコイズではない）
		assertThat(frame).contains("border: 1px solid var(--hb-line)");
	}

	@Test
	void theFrameDoesNotUseAPaleTealBackground() throws Exception {
		String css = css();
		Matcher m = Pattern.compile("(?m)^\\.[^{}]*hitokoto[^{}]*\\{([^}]*)\\}").matcher(css);
		int rules = 0;
		while (m.find()) {
			rules++;
			assertThat(m.group(1)).doesNotContain("--hb-teal-pale").doesNotContain("#E1F3F2");
			assertThat(m.group(1)).doesNotContain("#c62828").doesNotContain("#1565c0").doesNotContain("--hb-apricot");
		}
		assertThat(rules).isGreaterThan(0);
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
