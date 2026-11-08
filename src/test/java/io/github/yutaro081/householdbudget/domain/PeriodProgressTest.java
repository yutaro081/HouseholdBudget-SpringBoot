package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 「今日」の線の位置と、棒の長さの単体テスト（設計書 5.11、要件定義書 4.5）。Spring もデータベースも使わない。
 * 値はすべて1000分率の整数（0〜1000）。今日を含めた日数 × 1000 ÷ 全部の日数（切り捨て）。
 */
class PeriodProgressTest {

	private static final SeasonPeriod SPRING_2026 = new SeasonPeriod(2026, Season.SPRING);
	private static final SeasonPeriod SUMMER_2026 = new SeasonPeriod(2026, Season.SUMMER);
	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final SeasonPeriod WINTER_2026 = new SeasonPeriod(2026, Season.WINTER);
	private static final SeasonPeriod WINTER_2027 = new SeasonPeriod(2027, Season.WINTER);

	@Test
	void seasonLengths() {
		assertThat(PeriodProgress.seasonDays(SPRING_2026)).isEqualTo(92);
		assertThat(PeriodProgress.seasonDays(SUMMER_2026)).isEqualTo(92);
		assertThat(PeriodProgress.seasonDays(AUTUMN_2026)).isEqualTo(91);
		assertThat(PeriodProgress.seasonDays(WINTER_2026)).isEqualTo(90);   // 2027年2月は28日まで
		assertThat(PeriodProgress.seasonDays(WINTER_2027)).isEqualTo(91);   // 2028年2月は29日まで（うるう年）
	}

	@Test
	void seasonDayNumberCountsTodayAsIncluded() {
		assertThat(PeriodProgress.seasonDayNumber(AUTUMN_2026, LocalDate.of(2026, 9, 1))).isEqualTo(1);
		assertThat(PeriodProgress.seasonDayNumber(AUTUMN_2026, LocalDate.of(2026, 11, 8))).isEqualTo(69);
		assertThat(PeriodProgress.seasonDayNumber(AUTUMN_2026, LocalDate.of(2026, 11, 30))).isEqualTo(91);
		assertThat(PeriodProgress.seasonDayNumber(WINTER_2026, LocalDate.of(2027, 2, 28))).isEqualTo(90);
		assertThat(PeriodProgress.seasonDayNumber(WINTER_2027, LocalDate.of(2028, 2, 29))).isEqualTo(91);
	}

	@Test
	void seasonElapsedPermille() {
		assertThat(PeriodProgress.seasonElapsedPermille(AUTUMN_2026, LocalDate.of(2026, 9, 1))).isEqualTo(10);    // 1/91
		assertThat(PeriodProgress.seasonElapsedPermille(AUTUMN_2026, LocalDate.of(2026, 11, 8))).isEqualTo(758);  // 69/91
		assertThat(PeriodProgress.seasonElapsedPermille(AUTUMN_2026, LocalDate.of(2026, 11, 30))).isEqualTo(1000);
		assertThat(PeriodProgress.seasonElapsedPermille(WINTER_2027, LocalDate.of(2028, 2, 29))).isEqualTo(1000);
	}

	@ParameterizedTest(name = "週の {0} 日目 → {1}")
	@CsvSource({ "1, 142", "2, 285", "3, 428", "4, 571", "5, 714", "6, 857", "7, 1000" })
	void weekElapsedPermille(int dayNumber, int expected) {
		assertThat(PeriodProgress.weekElapsedPermille(dayNumber)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "予算 {0}・実績 {1} → 棒 {2}")
	@CsvSource({
			"2400,     0,       0",
			"2400,     800,     333",
			"2400,     2399,    999",
			"2400,     2400,    1000",
			"2400,     2500,    1000",
			"1000,     1,       1",
			"30000,    1,       0",
			"3000000,  1000000, 333",
			"0,        0,       0",
			"0,        100,     1000"
	})
	void barPermille(long budget, long actual, int expected) {
		assertThat(PeriodProgress.barPermille(budget, actual)).isEqualTo(expected);
	}

	@Test
	void unsetBudgetHasNoBar() {
		assertThat(PeriodProgress.barPermille(null, 5000)).isZero();
	}

	// ---- issue 017：見出しの行と、季節のメーターの % ----

	/** 季節の終わりまであと何日（今日を含める）。季節の最後の日は 1 */
	@Test
	void seasonDaysLeftCountsTodayAsIncluded() {
		assertThat(PeriodProgress.seasonDaysLeft(AUTUMN_2026, LocalDate.of(2026, 9, 1))).isEqualTo(91);
		assertThat(PeriodProgress.seasonDaysLeft(AUTUMN_2026, LocalDate.of(2026, 11, 8))).isEqualTo(23);   // 8日〜30日
		assertThat(PeriodProgress.seasonDaysLeft(AUTUMN_2026, LocalDate.of(2026, 11, 30))).isEqualTo(1);
		assertThat(PeriodProgress.seasonDaysLeft(WINTER_2026, LocalDate.of(2026, 12, 1))).isEqualTo(90);
		assertThat(PeriodProgress.seasonDaysLeft(WINTER_2026, LocalDate.of(2027, 2, 28))).isEqualTo(1);
		assertThat(PeriodProgress.seasonDaysLeft(WINTER_2027, LocalDate.of(2028, 2, 29))).isEqualTo(1);    // うるう日
	}

	/** 四捨五入した整数の％（半分は切り上げ）。整数だけで計算する */
	@ParameterizedTest(name = "{0} / {1} → {2}%")
	@CsvSource({
			"0, 5, 0",
			"1, 3, 33",
			"2, 3, 67",
			"1, 8, 13",       // 12.5 → 13（半分は切り上げ）
			"1, 200, 1",      // 0.5 → 1
			"1, 201, 0",      // 0.497… → 0
			"5, 5, 100",
			"6, 5, 120"
	})
	void roundedPercent(long numerator, long denominator, int expected) {
		assertThat(PeriodProgress.roundedPercent(numerator, denominator)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "予算 {0}・使った額 {1} → {2}%")
	@CsvSource({
			"130000, 84326, 65",     // 64.866 → 65
			"90000, 19134, 21",
			"30000, 0, 0",
			"30000, 30000, 100",
			"30000, 36000, 120",     // 予算を超えたら 100% をこえた値のまま
			"3000, 1, 0"
	})
	void usedPercent(long budget, long actual, int expected) {
		assertThat(PeriodProgress.usedPercent(budget, actual)).isEqualTo(expected);
	}

	@Test
	void usedPercentIsNullWhenTheBudgetIsUnsetOrZero() {
		assertThat(PeriodProgress.usedPercent(null, 500)).isNull();
		assertThat(PeriodProgress.usedPercent(0L, 500)).isNull();
		assertThat(PeriodProgress.usedPercent(0L, 0)).isNull();
	}

	@Test
	void seasonElapsedPercent() {
		assertThat(PeriodProgress.seasonElapsedPercent(AUTUMN_2026, LocalDate.of(2026, 9, 1))).isEqualTo(1);      // 1/91 = 1.1%
		assertThat(PeriodProgress.seasonElapsedPercent(AUTUMN_2026, LocalDate.of(2026, 11, 8))).isEqualTo(76);    // 69/91 = 75.8%
		assertThat(PeriodProgress.seasonElapsedPercent(AUTUMN_2026, LocalDate.of(2026, 11, 30))).isEqualTo(100);
		assertThat(PeriodProgress.seasonElapsedPercent(WINTER_2027, LocalDate.of(2028, 2, 29))).isEqualTo(100);
	}

}
