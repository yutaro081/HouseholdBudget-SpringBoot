package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * ペースの一言の文（messages.properties の top.pace.…）のテスト（要件定義書 4.5、設計書 5.11、issue 017 での見直し）。
 * 文は決まったとおりであること。評価・命令・感嘆符・予測の言い方を使わないこと。Spring は使わず、ファイルを直接読む。
 */
class PaceMessagesTest {

	private static Properties messages() throws Exception {
		Properties properties = new Properties();
		try (InputStream in = PaceMessagesTest.class.getResourceAsStream("/messages.properties");
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	@Test
	void sentencesAreExactlyTheDecidedOnes() throws Exception {
		Properties p = messages();

		assertThat(p.getProperty("top.pace.slow")).isEqualTo("日の進み方より、使い方がゆっくりです。");
		assertThat(p.getProperty("top.pace.same")).isEqualTo("日の進み方と、使い方はほぼ同じです。");
		assertThat(p.getProperty("top.pace.fast")).isEqualTo("日の進み方より、使い方が速めです。");
	}

	@Test
	void sentencesJustDescribeThePaceWithoutJudgingOrPredicting() throws Exception {
		List<String> banned = List.of("使いすぎ", "使い過ぎ", "無駄", "節約", "控え", "気をつけ", "がんばって", "！", "!", "このままだと", "超えそう",
				"超えます", "超えてしまう", "着地", "予測", "しましょう", "してください");
		for (String key : List.of("top.pace.slow", "top.pace.same", "top.pace.fast")) {
			String text = messages().getProperty(key);
			for (String word : banned) {
				assertThat(text).as(key + " に「" + word + "」がない").doesNotContain(word);
			}
		}
	}

}
