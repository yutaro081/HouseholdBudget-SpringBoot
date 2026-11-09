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

		// {0}＝季節の％（日の進み方）、{1}＝予算の％（使った割合）。メーターの数字と同じ値を、画面で渡す
		assertThat(p.getProperty("top.pace.slow")).isEqualTo("季節は {0}% 過ぎて、予算は {1}% 使いました。使うペースは、ゆっくりめです。");
		assertThat(p.getProperty("top.pace.same")).isEqualTo("季節は {0}% 過ぎて、予算は {1}% 使いました。使うペースは、ほぼ予定どおりです。");
		assertThat(p.getProperty("top.pace.fast")).isEqualTo("季節は {0}% 過ぎて、予算は {1}% 使いました。使うペースは、速めです。");
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
