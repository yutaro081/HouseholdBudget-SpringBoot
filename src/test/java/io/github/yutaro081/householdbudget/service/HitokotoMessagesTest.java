package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * グラフへのひとことの文（messages.properties の hitokoto.graph.…）のテスト（設計書 5.12、手順書 Q016-4）。
 * 事実だけを述べ、評価・命令・感嘆符を使わない。禁止の言葉がないことを確かめる。
 * Spring は使わず、ファイルを直接読む（文字化けやキーの綴り違いにも気づける）。
 */
class HitokotoMessagesTest {

	private static final List<String> GRAPH_KEYS = List.of("hitokoto.graph.top", "hitokoto.graph.remaining",
			"hitokoto.graph.remaining.saturday", "hitokoto.graph.over", "hitokoto.graph.over.saturday",
			"hitokoto.graph.unused");

	/** 手順書 Q016-4 で決めた、使わない言葉 */
	private static final List<String> BANNED = List.of("使いすぎ", "使い過ぎ", "無駄", "ムダ", "節約", "控え", "気をつけ", "気を付け", "がんばって",
			"頑張って", "！", "!", "ダメ", "だめ", "しましょう", "してください");

	private static Properties messages() throws Exception {
		Properties properties = new Properties();
		try (InputStream in = HitokotoMessagesTest.class.getResourceAsStream("/messages.properties");
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static String format(String key, Object... args) throws Exception {
		return new MessageFormat(messages().getProperty(key), Locale.JAPAN).format(args);
	}

	@Test
	void allKeysExist() throws Exception {
		for (String key : GRAPH_KEYS) {
			assertThat(messages().getProperty(key)).as(key).isNotBlank();
		}
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "hitokoto.graph.top", "hitokoto.graph.remaining", "hitokoto.graph.remaining.saturday",
			"hitokoto.graph.over", "hitokoto.graph.over.saturday", "hitokoto.graph.unused" })
	void noBannedWords(String key) throws Exception {
		String text = messages().getProperty(key);

		for (String word : BANNED) {
			assertThat(text).as(key + " に「" + word + "」がない").doesNotContain(word);
		}
		assertThat(text).as("です・ます、または事実を言い切る形").matches(".*(です。|ます。|せん。)$");
	}

	@Test
	void sentencesFormatWithThousandsSeparatorsAndPlainNumbers() throws Exception {
		assertThat(format("hitokoto.graph.top", "食費", 4_500L)).isEqualTo("今週いちばん多いのは食費（4,500円）です。");
		assertThat(format("hitokoto.graph.remaining", 2_300L, 2)).isEqualTo("週の予算の残りは2,300円、土曜まであと2日です。");
		assertThat(format("hitokoto.graph.remaining.saturday", 2_300L, 0)).isEqualTo("週の予算の残りは2,300円、今日は土曜です。");
		assertThat(format("hitokoto.graph.over", 800L, 2)).isEqualTo("週の予算を800円こえています。土曜まであと2日です。");
		assertThat(format("hitokoto.graph.over.saturday", 800L, 0)).isEqualTo("週の予算を800円こえています。今日は土曜です。");
		assertThat(format("hitokoto.graph.unused", "交際費", 0L)).isEqualTo("今週はまだ交際費を使っていません。");
	}

	@Test
	void kindLabelsExist() throws Exception {
		assertThat(messages().getProperty("hitokoto.kind.KO")).isEqualTo("七十二候");
		assertThat(messages().getProperty("hitokoto.kind.SEKKI")).isEqualTo("二十四節気");
		assertThat(messages().getProperty("hitokoto.kind.EVENT")).isEqualTo("年中行事");
	}

}
