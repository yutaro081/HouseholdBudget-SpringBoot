package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 着地見込みを、最初の取引の日から数えるときの単体テスト（設計書 5.13、issue 020）。
 * D′ ＝ その日から季節の終わりまでの日数、L ＝ 残りの日数。予算は D′ ÷ D に日割り：
 * 見込み ＝（実績 × D ×（D′ ＋ L）＋ 予算 × L²）÷（D × D′）、100 円単位で切り上げ。D′ ＝ D なら、今までの式と同じ。
 * 2026年秋は 91 日。9/1 が 1 日目、10/31 が 61 日目、11/9 が 70 日目。
 */
class LandingForecastCountingFromTest {

	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final LocalDate SEASON_START = LocalDate.of(2026, 9, 1);

	@Test
	void theDesignExampleIsAbout61000AndNotAbout113800() {
		// D ＝ 91、F ＝ 10/31（D′ ＝ 31）、11/9（L ＝ 21）、実績 20,000、予算 175,000
		// （20,000 × 91 × 52 ＋ 175,000 × 441）÷（91 × 31）＝ 171,815,000 ÷ 2,821 ＝ 60,903.5… → 61,000
		LandingForecast.Result result = LandingForecast.of(175_000L, 20_000, AUTUMN_2026, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 31)).orElseThrow();

		assertThat(result.amount()).isEqualTo(61_000L);
		assertThat(result.percentOfBudget()).isEqualTo(35);    // 61,000 ÷ 175,000 ＝ 34.9％ → 35（季節全体の予算で割る）
		assertThat(result.overBudget()).isFalse();
	}

	@Test
	void theAmountFunctionTakesTheCountedDays() {
		assertThat(LandingForecast.amount(175_000, 20_000, 91, 21, 31)).isEqualTo(61_000L);
	}

	@Test
	void startingOnTheFirstDayOfTheSeasonGivesTheSameAsTheOldForm() {
		var old = LandingForecast.of(175_000L, 107_800, AUTUMN_2026, LocalDate.of(2026, 11, 8)).orElseThrow();
		var same = LandingForecast.of(175_000L, 107_800, AUTUMN_2026, LocalDate.of(2026, 11, 8), SEASON_START).orElseThrow();

		assertThat(same).isEqualTo(old);
	}

	@ParameterizedTest(name = "D＝{0}、L＝{1}、D′＝D のとき、amount は今までの式と同じ")
	@CsvSource({ "90, 0", "90, 45", "91, 21", "92, 91", "91, 0" })
	void whenTheCountedDaysEqualTheSeasonDaysTheOldAmountIsReturned(int days, int remaining) {
		assertThat(LandingForecast.amount(120_000, 33_333, days, remaining, days))
				.isEqualTo(LandingForecast.amount(120_000, 33_333, days, remaining));
	}

	@Test
	void startingToday_theBudgetIsProratedToTheRestOfTheSeason() {
		// F ＝ 今日 ＝ 11/9（70 日目）：D′ ＝ 22、L ＝ 21。実績 0 → 175,000 × 441 ÷（91 × 22）＝ 38,548.9… → 38,600
		var result = LandingForecast.of(175_000L, 0, AUTUMN_2026, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9)).orElseThrow();

		assertThat(result.amount()).isEqualTo(38_600L);
	}

	@Test
	void onTheLastDayTheForecastEqualsTheActualWhateverTheStart() {
		// 11/30（91 日目）：L ＝ 0。見込み ＝ 実績 × D × D′ ÷（D × D′）＝ 実績
		var result = LandingForecast.of(175_000L, 12_300, AUTUMN_2026, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 10, 1)).orElseThrow();

		assertThat(result.amount()).isEqualTo(12_300L);
	}

	@Test
	void theForecastIsNeverBelowTheActual() {
		var result = LandingForecast.of(1L, 50_000, AUTUMN_2026, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 31)).orElseThrow();

		assertThat(result.amount()).isGreaterThanOrEqualTo(50_000L);
	}

	@Test
	void overBudgetIsDecidedWithTheWholeSeasonBudget() {
		// 実績 200,000 円（予算 175,000 円を超えている）。見込みは実績より小さくならないので、上回る
		var result = LandingForecast.of(175_000L, 200_000, AUTUMN_2026, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 10, 31)).orElseThrow();

		assertThat(result.overBudget()).isTrue();
	}

	@Test
	void stillEmptyWhenTheBudgetIsUnsetOrZeroOrTheSeasonIsNotCurrent() {
		LocalDate today = LocalDate.of(2026, 11, 9);
		LocalDate from = LocalDate.of(2026, 10, 31);

		assertThat(LandingForecast.of(null, 20_000, AUTUMN_2026, today, from)).isEmpty();
		assertThat(LandingForecast.of(0L, 20_000, AUTUMN_2026, today, from)).isEmpty();
		assertThat(LandingForecast.of(175_000L, 20_000, new SeasonPeriod(2026, Season.SUMMER), today, from)).isEmpty();
	}
}
