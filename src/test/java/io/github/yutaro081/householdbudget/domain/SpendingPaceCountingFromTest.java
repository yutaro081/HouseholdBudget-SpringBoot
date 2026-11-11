package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 直前の季節のペースを、最初の取引の日から数えるときの単体テスト（設計書 5.14、issue 020）。
 * 過ぎた日数 n を、その日から今日まで（今日を含む）で数える。D は季節全体のまま。ペース ＝ 実績 × D ÷ n。n が 14 未満は使えない。
 * 2026年秋は 91 日。9/1 が 1 日目、今日は 11/11（72 日目）。
 */
class SpendingPaceCountingFromTest {

	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

	@Test
	void nIsCountedFromTheFirstDayAndDStaysTheWholeSeason() {
		// 10/15 から 11/11 まで、今日を含めて 28 日。9,000 × 91 ÷ 28 ＝ 29,250
		var pace = SpendingPace.of(9_000, AUTUMN_2026, TODAY, LocalDate.of(2026, 10, 15)).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(9_000L * 91);
		assertThat(pace.denominator()).isEqualTo(28L);
		assertThat(pace.displayAmount()).isEqualTo(29_300L);   // 29,250 → 100 円切り上げで 29,300
	}

	@Test
	void the14DayLimitIsCountedFromTheFirstDay() {
		assertThat(SpendingPace.of(9_000, AUTUMN_2026, TODAY, LocalDate.of(2026, 10, 30))).as("13 日").isEmpty();
		assertThat(SpendingPace.of(9_000, AUTUMN_2026, TODAY, LocalDate.of(2026, 10, 29))).as("14 日").isPresent();
	}

	@Test
	void startingOnTheSeasonStartGivesTheSameAsTheOldForm() {
		var old = SpendingPace.of(80_400, AUTUMN_2026, TODAY).orElseThrow();
		var same = SpendingPace.of(80_400, AUTUMN_2026, TODAY, LocalDate.of(2026, 9, 1)).orElseThrow();

		assertThat(same).isEqualTo(old);
	}

	@Test
	void aFinishedSeasonStaysTheActualWhateverTheStart() {
		var pace = SpendingPace.of(9_000, AUTUMN_2026, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 10, 15)).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(9_000L);
		assertThat(pace.denominator()).isEqualTo(1L);
	}

	@Test
	void aSeasonThatHasNotStartedIsEmpty() {
		assertThat(SpendingPace.of(9_000, AUTUMN_2026, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1))).isEmpty();
	}
}
