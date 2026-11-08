package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 今週の期間の単体テスト（設計書 5.11、要件定義書 4.5）。Spring もデータベースも使わない。
 * 今週は日曜〜土曜。範囲は「開始日以上、次の日曜（終了日の翌日）未満」。日曜が1日目、土曜が7日目。
 */
class WeekPeriodTest {

	/** 2026年11月8日は日曜。その週の7日間は、どの日でも同じ週になる */
	@ParameterizedTest(name = "{0} は {1} 日目。週は 2026-11-08 から")
	@CsvSource({
			"2026-11-08, 1",
			"2026-11-09, 2",
			"2026-11-10, 3",
			"2026-11-11, 4",
			"2026-11-12, 5",
			"2026-11-13, 6",
			"2026-11-14, 7"
	})
	void sevenDaysOfOneWeek(LocalDate date, int dayNumber) {
		WeekPeriod week = WeekPeriod.containing(date);

		assertThat(week.start()).isEqualTo(LocalDate.of(2026, 11, 8));
		assertThat(week.endDateExclusive()).isEqualTo(LocalDate.of(2026, 11, 15));
		assertThat(week.endDate()).isEqualTo(LocalDate.of(2026, 11, 14));
		assertThat(week.dayNumber(date)).isEqualTo(dayNumber);
	}

	@Test
	void theDayAfterTheLastSaturdayStartsTheNextWeek() {
		assertThat(WeekPeriod.containing(LocalDate.of(2026, 11, 15)).start()).isEqualTo(LocalDate.of(2026, 11, 15));
		assertThat(WeekPeriod.containing(LocalDate.of(2026, 11, 7)).start()).isEqualTo(LocalDate.of(2026, 11, 1));
	}

	/** 月・年・季節・うるう日の変わり目をまたぐ週 */
	@ParameterizedTest(name = "{0} の週は {1} から {2} の前日まで")
	@CsvSource({
			"2026-08-31, 2026-08-30, 2026-09-06",   // 夏から秋へ（月曜）
			"2026-12-02, 2026-11-29, 2026-12-06",   // 秋から冬へ（水曜）
			"2026-12-31, 2026-12-27, 2027-01-03",   // 年をまたぐ（木曜）
			"2027-02-28, 2027-02-28, 2027-03-07",   // 冬から春へ（日曜）
			"2028-02-29, 2028-02-27, 2028-03-05"    // うるう日（火曜）
	})
	void weeksAcrossMonthSeasonYearAndLeapDay(LocalDate date, LocalDate start, LocalDate endExclusive) {
		WeekPeriod week = WeekPeriod.containing(date);

		assertThat(week.start()).isEqualTo(start);
		assertThat(week.endDateExclusive()).isEqualTo(endExclusive);
		assertThat(week.start().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
	}

	/** どの日でも、開始日は日曜で、その日は開始日から6日以内（2年分を総当たりで確かめる） */
	@Test
	void everyDayOfTwoYearsBelongsToOneSundayStartedWeek() {
		for (LocalDate d = LocalDate.of(2027, 1, 1); d.isBefore(LocalDate.of(2029, 1, 1)); d = d.plusDays(1)) {
			WeekPeriod week = WeekPeriod.containing(d);
			assertThat(week.start().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
			assertThat(week.dayNumber(d)).isBetween(1, 7);
			assertThat(week.start().plusDays(week.dayNumber(d) - 1)).isEqualTo(d);
		}
	}

}
