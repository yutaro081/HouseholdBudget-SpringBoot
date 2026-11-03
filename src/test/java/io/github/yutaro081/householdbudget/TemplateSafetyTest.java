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

}
