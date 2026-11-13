package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 予実比較の要約カードの文「予算より 約 ○円 少ない（多い）見込みです。」の向き・額・赤かどうか（要件定義書 4.4、設計書 2.3、issue 024）。
 * 差 ＝ 予算 − 見込みの額（表示する額。100 円単位に切り上げたもの）。プラスなら少ない、マイナスなら多い、0 なら予算どおり。
 * 赤にするのは「多い」ときだけ（見込みが予算を超えるとき。見込みとの差・トップの文・今週のひとことと同じ決まり）。判定は、テンプレートでなく、ここで行う。
 * 多いか少ないか・赤にするかは「決まり」なので、色の判定（BudgetColorJudge）と同じく domain に置く（issue 024）。
 */
class LandingVerdictTest {

	@Test
	void aPositiveGapMeansTheForecastIsLessThanTheBudgetAndIsNotRed() {
		LandingVerdict verdict = LandingVerdict.of(37_000);

		assertThat(verdict.direction()).isEqualTo(LandingVerdict.Direction.LESS);
		assertThat(verdict.amount()).isEqualTo(37_000);
		assertThat(verdict.key()).isEqualTo("less");
		assertThat(verdict.over()).isFalse();
	}

	@Test
	void aNegativeGapMeansTheForecastIsMoreThanTheBudgetAndOnlyThisIsRed() {
		LandingVerdict verdict = LandingVerdict.of(-500);

		assertThat(verdict.direction()).isEqualTo(LandingVerdict.Direction.MORE);
		assertThat(verdict.amount()).as("額は、絶対値（「約 500円 多い」）").isEqualTo(500);
		assertThat(verdict.key()).isEqualTo("more");
		assertThat(verdict.over()).isTrue();
	}

	@Test
	void aZeroGapMeansExactlyOnTheBudgetAndIsNotRed() {
		LandingVerdict verdict = LandingVerdict.of(0);

		assertThat(verdict.direction()).isEqualTo(LandingVerdict.Direction.SAME);
		assertThat(verdict.amount()).isZero();
		assertThat(verdict.key()).isEqualTo("same");
		assertThat(verdict.over()).isFalse();
	}

	/** 1 円の差でも向きが決まる（境目）。大きな額でもあふれない */
	@Test
	void theBoundariesAndLargeAmounts() {
		assertThat(LandingVerdict.of(1).key()).isEqualTo("less");
		assertThat(LandingVerdict.of(-1).key()).isEqualTo("more");
		assertThat(LandingVerdict.of(-1).over()).isTrue();
		assertThat(LandingVerdict.of(-3_000_000_000L).amount()).isEqualTo(3_000_000_000L);
		assertThat(LandingVerdict.of(3_000_000_000L).amount()).isEqualTo(3_000_000_000L);
	}
}
