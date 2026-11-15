package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * XSS 対策（設計書 7.5）：画面のテンプレートで、無害化しない出力（th:utext、[( … )]）を使っていないことの確認。
 * Phase 8 の 4.4 のコードの検索（th:utext が 0 件）を、自動テストにしたもの。Spring もデータベースも使わない。
 * コメント（「th:utext は使わない」と書いた説明）は、「=」が続かないので、引っかからない。
 * <p>
 * あわせて、HTML に直接書く JavaScript（onsubmit・onclick などのイベントの属性、javascript: の URL）を使っていないことも確かめる
 * （設計書 2.2、7.5、issue 026）。JavaScript は、static/js の固定のファイルだけに書く。
 */
class TemplateSafetyTest {

	private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");

	/** 属性としての th:utext（th:utext="…"、data-th-utext="…"）と、文章の中の無害化しない出力 [( … )] */
	private static final Pattern UNESCAPED = Pattern.compile("(th:utext|data-th-utext)\\s*=|\\[\\(");

	@Test
	void noTemplateUsesUnescapedOutput() throws IOException {
		List<Path> templates;
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			templates = files.filter(path -> path.toString().endsWith(".html")).toList();
		}
		assertThat(templates).as("テンプレートが見つかる（検索の場所が合っている）").hasSizeGreaterThan(15);

		List<String> found = new ArrayList<>();
		for (Path template : templates) {
			if (UNESCAPED.matcher(Files.readString(template)).find()) {
				found.add(template.toString());
			}
		}
		assertThat(found).as("th:utext または [( … )] を使っているテンプレート").isEmpty();
	}

	/**
	 * イベントの属性（on で始まる属性。th:onclick、data-th-onclick、th:attr="onclick=…" の形もふくむ）と、javascript: の URL。
	 * 属性の前は、空白か引用符（th:attr の中）。「=」が続くものだけを見るので、ふつうの言葉（only など）は引っかからない。
	 */
	private static final Pattern INLINE_SCRIPT = Pattern.compile("(?i)[\\s\"'](?:th:|data-th-)?on[a-z]+\\s*=|javascript\\s*:");

	/** コメント（Thymeleaf の解析時のコメントと、HTML のコメント）。説明の文に「onsubmit」と書いてあっても、引っかからないように除く */
	private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");

	@Test
	void noTemplateHasInlineEventHandlersOrJavascriptUrls() throws IOException {
		List<Path> templates;
		try (Stream<Path> files = Files.walk(TEMPLATES)) {
			templates = files.filter(path -> path.toString().endsWith(".html")).toList();
		}
		assertThat(templates).as("テンプレートが見つかる（検索の場所が合っている）").hasSizeGreaterThan(15);

		List<String> found = new ArrayList<>();
		for (Path template : templates) {
			String html = COMMENT.matcher(Files.readString(template)).replaceAll("");
			if (INLINE_SCRIPT.matcher(html).find()) {
				found.add(template.toString());
			}
		}
		assertThat(found).as("イベントの属性（onsubmit など）または javascript: の URL を使っているテンプレート").isEmpty();
	}

}
