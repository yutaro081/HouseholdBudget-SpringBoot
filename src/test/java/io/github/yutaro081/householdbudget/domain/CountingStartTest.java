package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * 日数を数え始める日の単体テスト（設計書 5.13、issue 020）。
 * 利用者のいちばん最初の取引の日 F が、季節の中にあり、今日より後でないときだけ F。それ以外は季節の初日。
 */
class CountingStartTest {

	/** 2026年秋（9/1〜11/30。91 日） */
	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final LocalDate SEASON_START = LocalDate.of(2026, 9, 1);
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

	private static LocalDate startFor(LocalDate firstDay) {
		return CountingStart.of(AUTUMN_2026, firstDay, TODAY);
	}

	@Test
	void noTransactionsMeansTheSeasonStart() {
		assertThat(startFor(null)).isEqualTo(SEASON_START);
	}

	@Test
	void aFirstDayBeforeTheSeasonMeansTheSeasonStart() {
		assertThat(startFor(LocalDate.of(2026, 8, 31))).isEqualTo(SEASON_START);
		assertThat(startFor(LocalDate.of(2020, 1, 1))).isEqualTo(SEASON_START);
	}

	@Test
	void aFirstDayOnTheSeasonStartIsTheSeasonStart() {
		assertThat(startFor(SEASON_START)).isEqualTo(SEASON_START);
	}

	@Test
	void aFirstDayInsideTheSeasonIsUsed() {
		assertThat(startFor(LocalDate.of(2026, 9, 2))).isEqualTo(LocalDate.of(2026, 9, 2));
		assertThat(startFor(LocalDate.of(2026, 10, 31))).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	@Test
	void aFirstDayOnTodayIsUsed() {
		assertThat(startFor(TODAY)).isEqualTo(TODAY);
	}

	@Test
	void aFirstDayAfterTodayIsNotUsed() {
		assertThat(startFor(LocalDate.of(2026, 11, 12))).isEqualTo(SEASON_START);
	}

	@Test
	void aFirstDayAfterTheSeasonEndIsNotUsed() {
		assertThat(startFor(LocalDate.of(2026, 12, 1))).isEqualTo(SEASON_START);
		assertThat(CountingStart.of(AUTUMN_2026, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5))).isEqualTo(SEASON_START);
	}

	@Test
	void theLastDayOfTheSeasonIsUsedWhenTodayIsThatDay() {
		assertThat(CountingStart.of(AUTUMN_2026, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 30))).isEqualTo(LocalDate.of(2026, 11, 30));
	}
}
