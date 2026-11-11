package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 自動予算で使う季節の単体テスト（設計書 5.14、issue 019）。Spring もデータベースも使わない。
 * <ul>
 * <li>同じ季節の過去 3 年：画面の季節の 1・2・3 年前の同じ季節（新しい順）。2000 年 春より前は入れない</li>
 * <li>傾向の 12 季節：画面の季節より前で、<b>もう終わっている</b>（終了日の翌日 ≦ 今日の）いちばん新しい季節を末尾にして、
 * 古い順に 12 個。先頭の 4 つが T1、次が T2、最後の 4 つが T3。2000 年 春より前にかかるときは「なし」</li>
 * <li>直前の季節：画面の季節の 1 つ前</li>
 * <li>集計する期間：いちばん古い季節の開始日 〜 画面の季節の開始日の前（終わりは含まない）</li>
 * </ul>
 */
class AutoBudgetSeasonsTest {

	private static final LocalDate NOV_11_2026 = LocalDate.of(2026, 11, 11);

	private static SeasonPeriod p(int year, Season season) {
		return new SeasonPeriod(year, season);
	}

	/** first から数えて count 個の季節（古い順） */
	private static List<SeasonPeriod> run(SeasonPeriod first, int count) {
		List<SeasonPeriod> list = new ArrayList<>();
		SeasonPeriod current = first;
		for (int i = 0; i < count; i++) {
			list.add(current);
			current = current.next().orElseThrow();
		}
		return list;
	}

	private static AutoBudgetSeasons of(SeasonPeriod screen, LocalDate today) {
		return AutoBudgetSeasons.of(screen, today);
	}

	// ---- 設計書の例：2026/11/11 に 2026 年 冬を開く ----

	@Test
	void theDesignExampleForWinter2026OnNovember11() {
		AutoBudgetSeasons seasons = of(p(2026, Season.WINTER), NOV_11_2026);

		assertThat(seasons.sameSeasonYears()).containsExactly(
				p(2025, Season.WINTER), p(2024, Season.WINTER), p(2023, Season.WINTER));
		assertThat(seasons.previousSeason()).contains(p(2026, Season.AUTUMN));
		// T3＝2025 秋〜2026 夏、T2＝2024 秋〜2025 夏、T1＝2023 秋〜2024 夏（冬もこの中）
		assertThat(seasons.trendSeasons()).contains(run(p(2023, Season.AUTUMN), 12));
		assertThat(seasons.trendSeasons().orElseThrow().get(0)).isEqualTo(p(2023, Season.AUTUMN));
		assertThat(seasons.trendSeasons().orElseThrow().get(4)).isEqualTo(p(2024, Season.AUTUMN));
		assertThat(seasons.trendSeasons().orElseThrow().get(8)).isEqualTo(p(2025, Season.AUTUMN));
		assertThat(seasons.trendSeasons().orElseThrow().get(11)).isEqualTo(p(2026, Season.SUMMER));
		// 一度に集計する期間：2023-09-01 〜 2026-12-01 の前
		assertThat(seasons.aggregateFrom()).isEqualTo(LocalDate.of(2023, 9, 1));
		assertThat(seasons.aggregateToExclusive()).isEqualTo(LocalDate.of(2026, 12, 1));
	}

	// ---- 11/11 に、画面の季節が冬・春・夏・秋のとき ----

	@Test
	void onNovember11ScreenSpringEndsTheTrendAtWinter2025() {
		AutoBudgetSeasons seasons = of(p(2026, Season.SPRING), NOV_11_2026);

		assertThat(seasons.trendSeasons()).contains(run(p(2023, Season.SPRING), 12));
		assertThat(seasons.sameSeasonYears()).containsExactly(
				p(2025, Season.SPRING), p(2024, Season.SPRING), p(2023, Season.SPRING));
		assertThat(seasons.previousSeason()).contains(p(2025, Season.WINTER));
	}

	@Test
	void onNovember11ScreenSummerEndsTheTrendAtSpring2026() {
		AutoBudgetSeasons seasons = of(p(2026, Season.SUMMER), NOV_11_2026);

		assertThat(seasons.trendSeasons()).contains(run(p(2023, Season.SUMMER), 12));
		assertThat(seasons.previousSeason()).contains(p(2026, Season.SPRING));
	}

	@Test
	void onNovember11ScreenAutumnEndsTheTrendAtSummer2026() {
		AutoBudgetSeasons seasons = of(p(2026, Season.AUTUMN), NOV_11_2026);

		assertThat(seasons.trendSeasons()).contains(run(p(2023, Season.AUTUMN), 12));
		assertThat(seasons.previousSeason()).contains(p(2026, Season.SUMMER));
	}

	@Test
	void aFutureScreenSeasonStillEndsTheTrendAtTheLatestFinishedSeason() {
		// 2027 年 春を 2026/11/11 に開く。秋は今日を含み、冬はまだ始まっていない。終わっているのは 2026 夏まで
		AutoBudgetSeasons seasons = of(p(2027, Season.SPRING), NOV_11_2026);

		assertThat(seasons.trendSeasons()).contains(run(p(2023, Season.AUTUMN), 12));
		assertThat(seasons.previousSeason()).contains(p(2026, Season.WINTER));
	}

	// ---- 「もう終わっている」の境目（季節の最終日と初日）----

	@Test
	void aSeasonIsNotFinishedOnItsLastDay() {
		// 秋 2026 の最終日 11/30。画面は冬 2026。秋はまだ終わっていないので、傾向は夏 2026 まで
		AutoBudgetSeasons seasons = of(p(2026, Season.WINTER), LocalDate.of(2026, 11, 30));

		assertThat(seasons.trendSeasons().orElseThrow().get(11)).isEqualTo(p(2026, Season.SUMMER));
	}

	@Test
	void aSeasonIsFinishedOnTheDayAfterItsLastDay() {
		// 12/1 は冬の初日。秋 2026 は終わっているので、傾向は秋 2026 まで（2023 冬〜）
		AutoBudgetSeasons seasons = of(p(2026, Season.WINTER), LocalDate.of(2026, 12, 1));

		assertThat(seasons.trendSeasons()).contains(run(p(2023, Season.WINTER), 12));
		assertThat(seasons.trendSeasons().orElseThrow().get(11)).isEqualTo(p(2026, Season.AUTUMN));
	}

	@Test
	void winterEndsOnTheLastDayOfFebruaryNotOnMarch1() {
		// 冬 2026 は 2027-03-01 の前（2027-02-28）が最終日。画面は春 2027
		assertThat(of(p(2027, Season.SPRING), LocalDate.of(2027, 2, 28)).trendSeasons().orElseThrow().get(11))
				.isEqualTo(p(2026, Season.AUTUMN));
		assertThat(of(p(2027, Season.SPRING), LocalDate.of(2027, 3, 1)).trendSeasons().orElseThrow().get(11))
				.isEqualTo(p(2026, Season.WINTER));
	}

	@Test
	void whenTodayIsInWinterTheWinterIsNotYetFinishedForTheNextScreen() {
		// 2027-01-15（冬 2026 の中）。画面は春 2027
		AutoBudgetSeasons seasons = of(p(2027, Season.SPRING), LocalDate.of(2027, 1, 15));

		assertThat(seasons.trendSeasons().orElseThrow().get(11)).isEqualTo(p(2026, Season.AUTUMN));
	}

	@Test
	void aPastScreenSeasonUsesOnlySeasonsBeforeItEvenIfLaterOnesAreFinished() {
		// 2024 年 冬を 2026/11/11 に開く。傾向は、画面より前の 2024 秋まで
		AutoBudgetSeasons seasons = of(p(2024, Season.WINTER), NOV_11_2026);

		assertThat(seasons.trendSeasons().orElseThrow().get(11)).isEqualTo(p(2024, Season.AUTUMN));
		assertThat(seasons.trendSeasons().orElseThrow().get(0)).isEqualTo(p(2021, Season.WINTER));
		assertThat(seasons.aggregateToExclusive()).isEqualTo(LocalDate.of(2024, 12, 1));
	}

	// ---- 2000 年 春の範囲の境目：同じ季節の過去 3 年 ----

	@Test
	void sameSeasonYearsStopAtTheFirstSpringOf2000() {
		// 2003 春：2002・2001・2000 の春（3 つ）
		assertThat(of(p(2003, Season.SPRING), NOV_11_2026).sameSeasonYears()).containsExactly(
				p(2002, Season.SPRING), p(2001, Season.SPRING), p(2000, Season.SPRING));
		// 2002 春：2001・2000 の春（2 つ。1999 は範囲の外）
		assertThat(of(p(2002, Season.SPRING), NOV_11_2026).sameSeasonYears()).containsExactly(
				p(2001, Season.SPRING), p(2000, Season.SPRING));
		// 2001 春：2000 の春（1 つ）
		assertThat(of(p(2001, Season.SPRING), NOV_11_2026).sameSeasonYears()).containsExactly(p(2000, Season.SPRING));
		// 2000 春：なし（ボタンを出さない）
		assertThat(of(p(2000, Season.SPRING), NOV_11_2026).sameSeasonYears()).isEmpty();
	}

	@Test
	void sameSeasonYearsForWinterAndSummerNearTheStartOfTheRange() {
		// 2000 年の冬は範囲の中（2000 年の 12 月）。2001 冬の 1 年前は 2000 冬
		assertThat(of(p(2001, Season.WINTER), NOV_11_2026).sameSeasonYears()).containsExactly(p(2000, Season.WINTER));
		assertThat(of(p(2000, Season.WINTER), NOV_11_2026).sameSeasonYears()).isEmpty();
		assertThat(of(p(2000, Season.SUMMER), NOV_11_2026).sameSeasonYears()).isEmpty();
		assertThat(of(p(2001, Season.SUMMER), NOV_11_2026).sameSeasonYears()).containsExactly(p(2000, Season.SUMMER));
	}

	// ---- 2000 年 春の範囲の境目：傾向の 12 季節 ----

	@Test
	void trendSeasonsAreAvailableWhenTheyStartExactlyAtSpring2000() {
		// 2003 春（今日は十分あと）：直前に終わった季節は冬 2002。12 季節は 2000 春〜2002 冬
		AutoBudgetSeasons seasons = of(p(2003, Season.SPRING), NOV_11_2026);

		assertThat(seasons.trendSeasons()).contains(run(p(2000, Season.SPRING), 12));
	}

	@Test
	void trendSeasonsAreNotAvailableWhenTheyWouldStartBeforeSpring2000() {
		// 2002 冬：12 季節は 1999 冬〜2002 秋になり、2000 春より前にかかる
		assertThat(of(p(2002, Season.WINTER), NOV_11_2026).trendSeasons()).isEmpty();
		assertThat(of(p(2002, Season.AUTUMN), NOV_11_2026).trendSeasons()).isEmpty();
		assertThat(of(p(2000, Season.SPRING), NOV_11_2026).trendSeasons()).isEmpty();
	}

	// ---- 直前の季節 ----

	@Test
	void previousSeasonOfSpringIsTheWinterOfTheYearBefore() {
		assertThat(of(p(2026, Season.SPRING), NOV_11_2026).previousSeason()).contains(p(2025, Season.WINTER));
	}

	@Test
	void previousSeasonIsEmptyBeforeSpring2000() {
		assertThat(of(p(2000, Season.SPRING), NOV_11_2026).previousSeason()).isEmpty();
	}

	@Test
	void previousSeasonOfSummer2000IsSpring2000() {
		assertThat(of(p(2000, Season.SUMMER), NOV_11_2026).previousSeason()).contains(p(2000, Season.SPRING));
	}

	// ---- 一度に集計する期間 ----

	@Test
	void theAggregateRangeStartsAtTheOldestSeasonAndEndsBeforeTheScreenSeason() {
		// 2002 春：同じ季節 2001・2000 の春、直前は 2001 冬、傾向なし。いちばん古いのは 2000 春（2000-03-01）
		AutoBudgetSeasons seasons = of(p(2002, Season.SPRING), NOV_11_2026);

		assertThat(seasons.aggregateFrom()).isEqualTo(LocalDate.of(2000, 3, 1));
		assertThat(seasons.aggregateToExclusive()).isEqualTo(LocalDate.of(2002, 3, 1));
	}

	@Test
	void theAggregateRangeForSummer2001StartsAtSummer2000() {
		// 同じ季節 2000 夏、直前 2001 春、傾向なし。いちばん古いのは 2000 夏
		AutoBudgetSeasons seasons = of(p(2001, Season.SUMMER), NOV_11_2026);

		assertThat(seasons.aggregateFrom()).isEqualTo(LocalDate.of(2000, 6, 1));
		assertThat(seasons.aggregateToExclusive()).isEqualTo(LocalDate.of(2001, 6, 1));
	}

	@Test
	void theAggregateRangeEndsExactlyAtTheScreenSeasonStartSoItNeverIncludesTheScreenSeason() {
		for (Season season : Season.values()) {
			SeasonPeriod screen = p(2026, season);

			assertThat(of(screen, NOV_11_2026).aggregateToExclusive()).isEqualTo(screen.startDate());
		}
	}

	@Test
	void whenNothingCanBeUsedTheAggregateRangeIsEmptyAtTheScreenSeasonStart() {
		AutoBudgetSeasons seasons = of(p(2000, Season.SPRING), NOV_11_2026);

		assertThat(seasons.aggregateFrom()).isEqualTo(LocalDate.of(2000, 3, 1));
		assertThat(seasons.aggregateToExclusive()).isEqualTo(LocalDate.of(2000, 3, 1));
	}

}
