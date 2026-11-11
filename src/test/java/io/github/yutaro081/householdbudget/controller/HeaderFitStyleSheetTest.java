package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * ヘッダーを、1100px 以上の幅で 1 段に収めるための余白（設計書 2.3「ヘッダー」、issue 020）のテスト。
 * 収まるかどうか（折り返し）は、ここでは確かめられない。ブラウザで確かめる（ヘッドレスの Chrome で、1070px 前後から 1 段になることを確かめた）。
 * ここでは、詰めた数字（項目の左右 8px、項目の間 2px、ロゴ・メニュー・ユーザー名の間 12px）と、変えないもの（高さ 44px、角 10px）を確かめる。
 */
class HeaderFitStyleSheetTest {

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
	void theMenuItemsHave8pxOnTheSidesAndTheyAre2pxApart() throws Exception {
		String css = css();

		assertThat(rule(css, ".menu a")).contains("padding: 0 8px", "min-height: 44px", "border-radius: 10px");
		assertThat(rule(css, ".menu")).contains("gap: 2px");
	}

	@Test
	void theLogoTheMenuAndTheUserAreaAre12pxApart() throws Exception {
		assertThat(rule(css(), ".app-header-inner")).contains("gap: 8px 12px", "max-width: 1280px", "padding: 0 12px");
	}
}
