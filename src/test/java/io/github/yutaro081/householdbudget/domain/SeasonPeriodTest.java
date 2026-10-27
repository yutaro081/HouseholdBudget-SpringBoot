package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 今日の日付から、季節と年を決める計算の単体テスト（設計書 5.8）。
 * Spring もデータベースも使わない。
 */
class SeasonPeriodTest {

	/** 設計書 5.8 の表の5つの日付 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2027-02-28, 2026, WINTER",
			"2027-01-15, 2026, WINTER",
			"2026-12-01, 2026, WINTER",
			"2026-11-30, 2026, AUTUMN",
			"2027-03-01, 2027, SPRING"
	})
	void designTableDates(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** うるう年の2月29日は、前の年の冬。翌日の3月1日は春 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2028-02-28, 2027, WINTER",
			"2028-02-29, 2027, WINTER",
			"2028-03-01, 2028, SPRING",
			"2024-02-29, 2023, WINTER",
			"2024-03-01, 2024, SPRING",
			// うるう年ではない年の2月末日と、3月1日
			"2027-02-28, 2026, WINTER",
			"2027-03-01, 2027, SPRING"
	})
	void leapYear(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** 季節の境目（前日と当日）。冬は12月がある年 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2026-02-28, 2025, WINTER",
			"2026-03-01, 2026, SPRING",
			"2026-05-31, 2026, SPRING",
			"2026-06-01, 2026, SUMMER",
			"2026-08-31, 2026, SUMMER",
			"2026-09-01, 2026, AUTUMN",
			"2026-11-30, 2026, AUTUMN",
			"2026-12-01, 2026, WINTER",
			"2026-12-31, 2026, WINTER",
			"2027-01-01, 2026, WINTER"
	})
	void seasonBoundaries(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** 範囲の端の年（取引の日付は2000年1月1日〜2100年12月31日、R12） */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2000-01-01, 1999, WINTER",
			"2000-03-01, 2000, SPRING",
			"2100-12-31, 2100, WINTER"
	})
	void rangeEdges(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** 12か月すべてが、設計書 5.8 の月と季節の対応どおり */
	@Test
	void everyMonth() {
		assertThat(Season.fromMonth(Month.MARCH)).isEqualTo(Season.SPRING);
		assertThat(Season.fromMonth(Month.APRIL)).isEqualTo(Season.SPRING);
		assertThat(Season.fromMonth(Month.MAY)).isEqualTo(Season.SPRING);
		assertThat(Season.fromMonth(Month.JUNE)).isEqualTo(Season.SUMMER);
		assertThat(Season.fromMonth(Month.JULY)).isEqualTo(Season.SUMMER);
		assertThat(Season.fromMonth(Month.AUGUST)).isEqualTo(Season.SUMMER);
		assertThat(Season.fromMonth(Month.SEPTEMBER)).isEqualTo(Season.AUTUMN);
		assertThat(Season.fromMonth(Month.OCTOBER)).isEqualTo(Season.AUTUMN);
		assertThat(Season.fromMonth(Month.NOVEMBER)).isEqualTo(Season.AUTUMN);
		assertThat(Season.fromMonth(Month.DECEMBER)).isEqualTo(Season.WINTER);
		assertThat(Season.fromMonth(Month.JANUARY)).isEqualTo(Season.WINTER);
		assertThat(Season.fromMonth(Month.FEBRUARY)).isEqualTo(Season.WINTER);
	}

	/** URL で使う名前 */
	@Test
	void urlNames() {
		assertThat(Season.SPRING.getUrlName()).isEqualTo("spring");
		assertThat(Season.SUMMER.getUrlName()).isEqualTo("summer");
		assertThat(Season.AUTUMN.getUrlName()).isEqualTo("autumn");
		assertThat(Season.WINTER.getUrlName()).isEqualTo("winter");
	}

}
