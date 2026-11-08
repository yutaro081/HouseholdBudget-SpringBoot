package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yutaro081.householdbudget.domain.PaceComment.Pace;

/**
 * ペースの一言の判定の単体テスト（設計書 5.11、要件定義書 4.5、issue 017 での見直し〔Q010-3〕）。Spring もデータベースも使わない。
 * 差 ＝ 使った割合（％）− 日の進み方（％）。どちらも四捨五入した整数。
 * 差が −5 以下＝ゆっくり、+5 以上＝速め、−4〜+4＝ほぼ同じ。<b>ちょうど 5 ポイント差は、ゆっくり／速めのほうに入る</b>。
 */
class PaceCommentTest {

	/** 日の進み方を 50％ に固定して、使った割合を動かす（境目の前後を 1 つずつ） */
	@ParameterizedTest(name = "使った割合 {0}%・日の進み方 50% → 差 {1} → {2}")
	@CsvSource({
			"0,  -50, SLOW",
			"44, -6,  SLOW",
			"45, -5,  SLOW",     // ちょうど 5 ポイント少ない → ゆっくり
			"46, -4,  SAME",     // 境目の内側
			"49, -1,  SAME",
			"50, 0,   SAME",
			"51, 1,   SAME",
			"54, 4,   SAME",     // 境目の内側
			"55, 5,   FAST",     // ちょうど 5 ポイント多い → 速め
			"56, 6,   FAST",
			"100, 50, FAST"
	})
	void judgesByThePointsOfDifference(int used, int difference, Pace expected) {
		assertThat(used - 50).isEqualTo(difference);
		assertThat(PaceComment.judge(used, 50)).contains(expected);
	}

	@ParameterizedTest(name = "使った割合 {0}%・日の進み方 {1}% → {2}")
	@CsvSource({
			"65, 76, SLOW",      // 案の例に近い：差 −11
			"71, 76, SLOW",      // 差 −5（ちょうど）
			"72, 76, SAME",      // 差 −4
			"76, 76, SAME",
			"80, 76, SAME",      // 差 +4
			"81, 76, FAST",      // 差 +5（ちょうど）
			"120, 76, FAST",     // 予算を超えている（100％をこえている）
			"0, 1, SAME",        // 季節の最初の日：差 −1
			"0, 5, SLOW",        // 差 −5（ちょうど）
			"5, 0, FAST",        // 日の進み方 0％（四捨五入）で、使った割合 5％ → 差 +5
			"100, 100, SAME",    // 季節の最後の日で、予算ちょうど
			"0, 100, SLOW"       // 季節の最後の日まで、何も使っていない
	})
	void judgesRealisticValues(int used, int elapsed, Pace expected) {
		assertThat(PaceComment.judge(used, elapsed)).contains(expected);
	}

	@Test
	void thereIsNoCommentWithoutAUsedPercent() {
		// 予算が未設定、または 0円のとき、使った割合はない（PeriodProgress.usedPercent が null）
		assertThat(PaceComment.judge(null, 76)).isEmpty();
		assertThat(PaceComment.judge(null, 0)).isEmpty();
	}

	@Test
	void theThresholdIsFivePoints() {
		assertThat(PaceComment.POINTS).isEqualTo(5);
	}

}
