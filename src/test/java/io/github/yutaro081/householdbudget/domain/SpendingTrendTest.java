package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.yutaro081.householdbudget.domain.SpendingTrend.Result;

/**
 * 出費の傾向（%）の単体テスト（設計書 5.14、issue 019）。Spring もデータベースも使わない。
 * 傾向 ＝ 四捨五入（100 ×（S ＋ 3 ×（T3 − T1））÷ S）%。S ＝ T1＋T2＋T3。半分は切り上げ。上限は 70〜130%。
 * 12 季節に取引のない季節があるときは、計算せず 100%。
 * 「上限で止めたか」は、四捨五入したあとの % が 130 を超えた（70 を下回った）ときだけ true。ちょうど 130（70）は止めていない。
 */
class SpendingTrendTest {

	private static Result trend(long t1, long t2, long t3) {
		return SpendingTrend.of(true, t1, t2, t3);
	}

	// ---- 例 ----

	@Test
	void theDesignExampleIs110Percent() {
		// 設計書 5.14：T1＝570,000、T2＝600,000、T3＝630,000 → 110%
		Result r = trend(570_000, 600_000, 630_000);

		assertThat(r.percent()).isEqualTo(110);
		assertThat(r.capped()).isFalse();
		assertThat(r.calculated()).isTrue();
	}

	@Test
	void theDemoDataIs106PercentRoundedFrom105Point52() {
		// 手順書 4.2：T1＝449,000、T2＝568,300、T3＝476,500。1.0552… → 106%
		Result r = trend(449_000, 568_300, 476_500);

		assertThat(r.percent()).isEqualTo(106);
		assertThat(r.capped()).isFalse();
	}

	@Test
	void aFlatTrendIsExactly100Percent() {
		Result r = trend(600_000, 600_000, 600_000);

		assertThat(r.percent()).isEqualTo(100);
		assertThat(r.capped()).isFalse();
		assertThat(r.calculated()).isTrue();
	}

	@Test
	void aSymmetricUpAndDownCancelsOutTo100Percent() {
		// T3 と T1 が同じなら、T2 がどうでも 100%
		assertThat(trend(500_000, 900_000, 500_000).percent()).isEqualTo(100);
		assertThat(trend(500_000, 100_000, 500_000).percent()).isEqualTo(100);
	}

	@Test
	void aFallingTrendIsBelow100Percent() {
		// T1＝630,000、T2＝600,000、T3＝570,000 → 90%
		Result r = trend(630_000, 600_000, 570_000);

		assertThat(r.percent()).isEqualTo(90);
		assertThat(r.capped()).isFalse();
	}

	// ---- 四捨五入（半分は切り上げ）----

	@Test
	void roundsDownBelowAHalf() {
		// S＝400、T3−T1＝1 → 100.75 → 101
		assertThat(trend(100, 199, 101).percent()).isEqualTo(101);
		// S＝400、T3−T1＝−1 → 99.25 → 99
		assertThat(trend(101, 199, 100).percent()).isEqualTo(99);
	}

	@Test
	void roundsHalfUpWhenExactlyAHalf() {
		// S＝200、T3−T1＝1 → 101.5 → 102
		assertThat(trend(50, 99, 51).percent()).isEqualTo(102);
		// S＝200、T3−T1＝−1 → 98.5 → 99（0 に近づける丸めではなく、切り上げ）
		assertThat(trend(51, 99, 50).percent()).isEqualTo(99);
	}

	@Test
	void roundsUpAboveAHalf() {
		// S＝1000、T3−T1＝2 → 100.6 → 101
		assertThat(trend(299, 400, 301).percent()).isEqualTo(101);
	}

	// ---- 上限 130% / 下限 70%（境目）----

	@Test
	void exactly130PercentIsNotCapped() {
		// S＝1000、T3−T1＝100 → 130.0
		Result r = trend(300, 300, 400);

		assertThat(r.percent()).isEqualTo(130);
		assertThat(r.capped()).isFalse();
	}

	@Test
	void roundedTo131PercentIsCappedTo130() {
		// S＝1000、T3−T1＝102 → 130.6 → 131 → 130 で止める
		Result r = trend(298, 302, 400);

		assertThat(r.percent()).isEqualTo(130);
		assertThat(r.capped()).isTrue();
	}

	@Test
	void aHugeGrowthIsCappedTo130() {
		Result r = trend(100_000, 100_000, 5_000_000);

		assertThat(r.percent()).isEqualTo(130);
		assertThat(r.capped()).isTrue();
		assertThat(r.calculated()).isTrue();
	}

	@Test
	void exactly70PercentIsNotCapped() {
		// S＝1000、T3−T1＝−100 → 70.0
		Result r = trend(400, 300, 300);

		assertThat(r.percent()).isEqualTo(70);
		assertThat(r.capped()).isFalse();
	}

	@Test
	void roundedTo69PercentIsCappedTo70() {
		// S＝1000、T3−T1＝−102 → 69.4 → 69 → 70 で止める
		Result r = trend(400, 302, 298);

		assertThat(r.percent()).isEqualTo(70);
		assertThat(r.capped()).isTrue();
	}

	@Test
	void aHugeDropIsCappedTo70EvenWhenTheFormulaGoesNegative() {
		// 4×T3 ＋ T2 − 2×T1 が負になる：式の値は負でも、70 で止める
		Result r = trend(5_000_000, 100_000, 100_000);

		assertThat(r.percent()).isEqualTo(70);
		assertThat(r.capped()).isTrue();
	}

	// ---- 12 季節がそろわないとき ----

	@Test
	void whenSomeSeasonHasNoRecordsItIsNotCalculatedAndIs100Percent() {
		// 数字が大きく伸びていても、計算しない
		Result r = SpendingTrend.of(false, 570_000, 600_000, 630_000);

		assertThat(r.percent()).isEqualTo(100);
		assertThat(r.capped()).isFalse();
		assertThat(r.calculated()).isFalse();
	}

	@Test
	void notCalculatedDoesNotApplyTheCapEither() {
		Result r = SpendingTrend.of(false, 100_000, 100_000, 5_000_000);

		assertThat(r.percent()).isEqualTo(100);
		assertThat(r.capped()).isFalse();
		assertThat(r.calculated()).isFalse();
	}

	@Test
	void whenAllTotalsAreZeroItIsNotCalculatedAndIs100Percent() {
		// 平均 0 のとき（0 で割れない）。12 季節に取引があれば合計は 0 にならないが、割り算の前に守る
		Result r = SpendingTrend.of(true, 0, 0, 0);

		assertThat(r.percent()).isEqualTo(100);
		assertThat(r.capped()).isFalse();
		assertThat(r.calculated()).isFalse();
	}

	// ---- 桁あふれ ----

	@Test
	void overflowThrowsInsteadOfGivingAWrongNumber() {
		long big = Long.MAX_VALUE / 2;

		assertThatThrownBy(() -> trend(big, big, big)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void largeButSafeTotalsStillWork() {
		// 1 兆円の合計でも、整数のまま計算できる
		Result r = trend(1_000_000_000_000L, 1_000_000_000_000L, 1_000_000_000_000L);

		assertThat(r.percent()).isEqualTo(100);
	}

}
