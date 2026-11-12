package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * 着地見込みの文（messages.properties の top.landing、comparison.landing…。要件定義書 4.4・4.5、設計書 5.11・5.13、issue 018）のテスト。
 * 文は決まったとおりであること。issue 017 のペースの一言（top.pace.…）がなくなっていること。
 * 禁止の言葉は、「評価・命令」「警告・断定」「専門用語（予測・着地）」。見込みの文に必要な言葉（見込み・約・このペースだと・上回ります）は、元から禁止の一覧にない。
 * Spring は使わず、ファイルを直接読む。
 */
class LandingMessagesTest {

	private static final List<String> KEYS = List.of("top.landing.line1", "top.landing.line2.before", "top.landing.big.before", "top.landing.percent", "top.landing.big.after", "top.landing.line2.after", "comparison.verdict.less", "comparison.verdict.more", "comparison.verdict.same", "comparison.landing.note", "comparison.card.amount", "comparison.card.budget", "comparison.card.actual", "comparison.card.budget.label", "comparison.card.actual.label", "comparison.card.amount.label", "comparison.card.counting");

	private static Properties messages() throws Exception {
		Properties properties = new Properties();
		try (InputStream in = LandingMessagesTest.class.getResourceAsStream("/messages.properties");
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	@Test
	void sentencesAreExactlyTheDecidedOnes() throws Exception {
		Properties p = messages();

		// issue 020 の見直し：文は 2 行に分け、2 行目は部分に分けた（「約 ○円（予算の ○%）」だけ大きく、「予算の ○%」は超えるときだけ赤）。
		// {0}＝季節の％、{1}＝予算の％（どちらもメーターと同じ値）。見込みの額は 100 円単位。「予算の ○%」は、見込みの、予算に対する％
		assertThat(p.getProperty("top.landing")).as("1 つの文は、なくした").isNull();
		assertThat(p.getProperty("top.landing.line1")).isEqualTo("季節は {0}% 過ぎて、予算は {1}% 使いました。");
		assertThat(p.getProperty("top.landing.line2.before")).isEqualTo("このペースだと、季節の終わりには");
		assertThat(p.getProperty("top.landing.big.before")).isEqualTo("約 {0}円（");
		assertThat(p.getProperty("top.landing.percent")).isEqualTo("予算の {0}%");
		assertThat(p.getProperty("top.landing.big.after")).isEqualTo("）");
		assertThat(p.getProperty("top.landing.line2.after")).isEqualTo("の見込みです。");
		// issue 020：予実比較の 1 行の文（comparison.landing）は、要約カードに置きかえて、なくした。{0}＝見込みの額、{1}＝合計の予算
		assertThat(p.getProperty("comparison.landing")).as("1 行の文は、なくした").isNull();
		assertThat(p.getProperty("comparison.card.amount")).isEqualTo("約 {0}円");
		assertThat(p.getProperty("comparison.card.budget")).as("数字（ラベルは別の行。issue 024）").isEqualTo("{0}円");
		assertThat(p.getProperty("comparison.card.actual")).isEqualTo("{0}円");
		// issue 024：3 つの数字は、ラベルを上、数字を下の 2 段。ラベル：「予算」「実績（予算の ○%）」「季節の終わりの見込み」。{0}＝実績が予算の何％か
		assertThat(p.getProperty("comparison.card.budget.label")).isEqualTo("予算");
		assertThat(p.getProperty("comparison.card.actual.label")).isEqualTo("実績（予算の {0}%）");
		assertThat(p.getProperty("comparison.card.amount.label")).isEqualTo("季節の終わりの見込み");
		assertThat(p.getProperty("comparison.card.counting")).isEqualTo("{0}/{1} に記録を始めたので、その日から数えています。");
		// issue 024：「見込みは、予算を上回ります。」の 1 行は、見込みと予算の差の文に置きかえた。{0}＝差の額（100 円単位）。「多い」のときだけ赤（画面の CSS）
		assertThat(p.getProperty("comparison.landing.over")).as("「見込みは、予算を上回ります。」は、なくした").isNull();
		assertThat(p.getProperty("comparison.verdict.less")).isEqualTo("予算より 約 {0}円 少ない見込みです。");
		assertThat(p.getProperty("comparison.verdict.more")).isEqualTo("予算より 約 {0}円 多い見込みです。");
		assertThat(p.getProperty("comparison.verdict.same")).isEqualTo("予算どおりの見込みです。");
		assertThat(p.getProperty("comparison.card.used")).as("メーター上の「予算のうち ○% を使った」は、なくした（実績のラベルと同じ数字のため）").isNull();
		assertThat(p.getProperty("comparison.landing.note")).isEqualTo("月ごとにまとめて払うものは、払う日で見込みが上下します。");
	}

	@Test
	void theFivePointPaceSentencesOfIssue017AreGone() throws Exception {
		Properties p = messages();

		assertThat(p.stringPropertyNames()).noneMatch(key -> key.startsWith("top.pace"));
	}

	@Test
	void theLandingSentencesNeedTheWordsForAForecastAndNoWordsForAnAlert() throws Exception {
		Properties p = messages();
		assertThat(p.getProperty("top.landing.line2.before")).contains("このペースだと");
		assertThat(p.getProperty("top.landing.line2.after")).contains("見込み");
		assertThat(p.getProperty("top.landing.big.before")).contains("約");
		assertThat(p.getProperty("comparison.card.title")).as("カード上の見出し「この季節の見込み」は、なくした（見込みのラベルと同じことのため。issue 024）").isNull();
		assertThat(p.getProperty("comparison.card.amount.label")).contains("見込み");
		assertThat(p.getProperty("comparison.landing.note")).contains("見込み");

		List<String> evaluation = List.of("使いすぎ", "使い過ぎ", "無駄", "節約", "控え", "気をつけ", "がんばって", "！", "!", "しましょう", "してください");
		List<String> alert = List.of("このままだと", "超えそう", "超えます", "超えてしまう", "予算を超えています", "早めです");
		List<String> jargon = List.of("予測", "着地");
		for (String key : KEYS) {
			String text = p.getProperty(key);
			assertThat(text).as(key + " が、messages.properties にある").isNotNull();
			for (List<String> banned : List.of(evaluation, alert, jargon)) {
				for (String word : banned) {
					assertThat(text).as(key + " に「" + word + "」がない").doesNotContain(word);
				}
			}
		}
	}

}
