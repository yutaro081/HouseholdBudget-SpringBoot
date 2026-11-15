package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * XSS 対策（設計書 7.5、issue 026）：自作の JavaScript（static/js の全ファイル）で、文字列を HTML として画面に入れる書き方と、
 * 文字列をプログラムとして実行する書き方を使っていないことの確認。Spring もデータベースも使わない。
 * <p>
 * ファイルごとのテスト（AmountButtonsScriptTest など）と違い、フォルダの中を全部回るので、JavaScript のファイルを足したときも漏れない。
 * コメントもふくめて見る（説明の文にも、この言葉は書かない）。
 */
class StaticScriptSafetyTest {

	private static final Path SCRIPTS = Path.of("src", "main", "resources", "static", "js");

	/** 使わない書き方。「eval(」は、evaluate のようなふつうの言葉に当たらないよう、かっこまで見る */
	private static final List<String> FORBIDDEN = List.of("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(",
			"new Function");

	@Test
	void noScriptBuildsHtmlFromStringsOrRunsStrings() throws IOException {
		List<Path> scripts;
		try (Stream<Path> files = Files.walk(SCRIPTS)) {
			scripts = files.filter(path -> path.toString().endsWith(".js")).toList();
		}
		assertThat(scripts).as("JavaScript のファイルが見つかる（検索の場所が合っている）").hasSizeGreaterThanOrEqualTo(4);

		List<String> found = new ArrayList<>();
		for (Path script : scripts) {
			String code = Files.readString(script);
			for (String word : FORBIDDEN) {
				if (code.contains(word)) {
					found.add(script.getFileName() + "：" + word);
				}
			}
		}
		assertThat(found).as("使わない書き方（innerHTML、eval など）がある JavaScript").isEmpty();
	}

}
