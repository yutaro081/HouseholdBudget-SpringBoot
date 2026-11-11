package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 「今週の科目のひとこと」の見せ方の CSS（設計書 5.15、issue 020）のテスト。
 * 文全体は、上の季節のカードの文（.top-season-pace）と同じ：濃いターコイズ（#0B6E6B）の太字。文字は 1.0625rem。
 * 「予算の ○%」の部分だけ、予算を超えるときに赤（予算超過の色 #c62828。予実比較の「見込みとの差」の赤と同じ）。
 */
class TopWeekPickStyleSheetTest {

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
	void theSentenceIsDarkTealBoldAndSlightlyLargerLikeTheSeasonSentence() throws Exception {
		String css = css();
		String pick = rule(css, ".top-week-pick");
		String season = rule(css, ".top-season-pace");

		assertThat(pick).contains("color: var(--hb-teal-text)", "font-weight: 700", "font-size: 1.0625rem");
		assertThat(season).as("上の季節のカードの文と、同じ色・太さ").contains("color: var(--hb-teal-text)", "font-weight: 700");
		assertThat(css).contains("--hb-teal-text: #0B6E6B");
	}

	@Test
	void onlyThePercentPartTurnsRed() throws Exception {
		assertThat(rule(css(), ".top-week-pick-over")).contains("color: #c62828");
	}
}
