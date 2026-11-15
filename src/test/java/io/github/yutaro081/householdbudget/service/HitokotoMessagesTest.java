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

	private static final List<String> GRAPH_KEYS = List.of("hitokoto.graph.perday", "hitokoto.graph.notover",
			"hitokoto.graph.notover.all", "hitokoto.graph.room", "hitokoto.graph.lastweek.less", "hitokoto.graph.lastweek.more",
			"hitokoto.graph.lastweek.same", "hitokoto.graph.count", "hitokoto.graph.unused", "hitokoto.graph.weekstart");

	/** issue 028 でなくした文のキー（最多、残り、予算超え、土曜の文） */
	private static final List<String> REMOVED_KEYS = List.of("hitokoto.graph.top", "hitokoto.graph.remaining",
			"hitokoto.graph.remaining.saturday", "hitokoto.graph.over", "hitokoto.graph.over.saturday");

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
	@ValueSource(strings = { "hitokoto.graph.perday", "hitokoto.graph.notover", "hitokoto.graph.notover.all",
			"hitokoto.graph.room",
			"hitokoto.graph.lastweek.less", "hitokoto.graph.lastweek.more", "hitokoto.graph.lastweek.same",
			"hitokoto.graph.count", "hitokoto.graph.unused", "hitokoto.graph.weekstart" })
	void noBannedWords(String key) throws Exception {
		String text = messages().getProperty(key);

		for (String word : BANNED) {
			assertThat(text).as(key + " に「" + word + "」がない").doesNotContain(word);
		}
		assertThat(text).as("です・ます、または事実を言い切る形").matches(".*(です。|ます。|せん。)$");
	}

	@Test
	void sentencesFormatWithThousandsSeparatorsAndPlainNumbers() throws Exception {
		assertThat(format("hitokoto.graph.perday", 1_300L, 4))
				.isEqualTo("今日を入れて あと 4 日。残りを日数で割ると、1 日あたり 約 1,300円 です。");
		assertThat(format("hitokoto.graph.notover", 8L, 5)).isEqualTo("今週は、週の予算がある 8 科目のうち 5 科目が、週の予算以下です。");
		assertThat(format("hitokoto.graph.notover.all", 6L)).isEqualTo("今週は、週の予算がある 6 科目が、すべて週の予算以下です。");
		assertThat(format("hitokoto.graph.room", "交通費", 7)).isEqualTo("交通費は、週の予算の 7 割がまだ残っています。");
		assertThat(format("hitokoto.graph.lastweek.less", 1_200L)).isEqualTo("今週の合計は、先週の同じ曜日までより 約 1,200円 少ないです。");
		assertThat(format("hitokoto.graph.lastweek.more", 12_300L)).isEqualTo("今週の合計は、先週の同じ曜日までより 約 12,300円 多いです。");
		assertThat(format("hitokoto.graph.lastweek.same")).isEqualTo("今週の合計は、先週の同じ曜日までと同じ額です。");
		assertThat(format("hitokoto.graph.count", 12L)).isEqualTo("今週の記録は 12 件です。");
		assertThat(format("hitokoto.graph.unused", "交際費", 0L)).isEqualTo("今週はまだ交際費を使っていません。");
		assertThat(format("hitokoto.graph.weekstart", 13_800L)).isEqualTo("新しい週です。週の予算の合計は 13,800円 です。");
	}

	/** なくした文のキーは、ファイルにない。組にして、残す文のキーはあることも見る（新しい文のキーは allKeysExist） */
	@Test
	void removedKeysAreGone() throws Exception {
		Properties messages = messages();

		assertThat(messages.getProperty("hitokoto.graph.unused")).as("残す文はある").isNotBlank();
		for (String key : REMOVED_KEYS) {
			assertThat(messages.getProperty(key)).as(key + " はない").isNull();
		}
	}

	@Test
	void kindLabelsExist() throws Exception {
		assertThat(messages().getProperty("hitokoto.kind.KO")).isEqualTo("七十二候");
		assertThat(messages().getProperty("hitokoto.kind.SEKKI")).isEqualTo("二十四節気");
		assertThat(messages().getProperty("hitokoto.kind.EVENT")).isEqualTo("年中行事");
	}

}
