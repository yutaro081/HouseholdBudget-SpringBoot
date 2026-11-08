package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 週の予算と、今週の実績の切り上げの単体テスト（設計書 5.11、要件定義書 4.5）。Spring もデータベースも使わない。
 * 週の予算 ＝ 季節の予算 ÷ 13 を100円単位で切り上げ（式：予算 ÷ 1,300 を切り上げて × 100）。
 */
class WeeklyAmountTest {

	@ParameterizedTest(name = "季節の予算 {0} → 週の予算 {1}")
	@CsvSource({
			"0,        0",
			"1,        100",
			"13,       100",
			"1299,     100",
			"1300,     100",
			"1301,     200",
			"2600,     200",
			"2601,     300",
			"30000,    2400",
			"3000000,  230800"
	})
	void weeklyBudgetBoundaries(long seasonBudget, long expected) {
		assertThat(WeeklyAmount.weeklyBudget(seasonBudget)).isEqualTo(expected);
	}

	@Test
	void unsetBudgetStaysUnset() {
		assertThat(WeeklyAmount.weeklyBudget(null)).isNull();
	}

	/** 「÷ 13 をして切り上げ、さらに100円単位で切り上げ」と同じ結果になること（0〜300万円を1円ずつ） */
	@Test
	void sameAsDividingBy13ThenRoundingUpTo100() {
		for (long budget = 0; budget <= 3_000_000; budget++) {
			long viaDivision = ((budget + 12) / 13 + 99) / 100 * 100;
			if (WeeklyAmount.weeklyBudget(budget) != viaDivision) {
				throw new AssertionError("予算 " + budget + " で食い違い：" + WeeklyAmount.weeklyBudget(budget) + " と " + viaDivision);
			}
		}
	}

	@ParameterizedTest(name = "実績 {0} → {1}")
	@CsvSource({
			"0,            0",
			"1,            100",
			"99,           100",
			"100,          100",
			"101,          200",
			"1000000,      1000000",
			"9999999999,   10000000000"
	})
	void actualIsRoundedUpTo100(long actual, long expected) {
		assertThat(WeeklyAmount.roundUpActual(actual)).isEqualTo(expected);
	}

}
