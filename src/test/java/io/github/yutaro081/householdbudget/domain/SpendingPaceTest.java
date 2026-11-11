package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yutaro081.householdbudget.domain.SpendingPace.Pace;

/**
 * 直前の季節の「ペース」の単体テスト（設計書 5.14、issue 019）。Spring もデータベースも使わない。
 * 終わった季節 ＝ 実績そのまま（実績 / 1）。今日を含む季節 ＝ 実績 × 季節の日数 D ÷ 過ぎた日数 n（今日を含む）。
 * n が 14 日未満、またはまだ始まっていない季節は、使えない（空）。予算は関係ない。
 * 案には割り算の前の分数を使うので、Pace は（分子, 分母）のまま持つ。表に出す額は 100 円単位に切り上げ。
 */
class SpendingPaceTest {

	/** 2026年秋（9〜11月。91 日。9/1 が 1 日目） */
	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);

	private static Optional<Pace> autumnOn(long actual, int month, int day) {
		return SpendingPace.of(actual, AUTUMN_2026, LocalDate.of(2026, month, day));
	}

	// ---- 例 ----

	@Test
	void theIssueExampleIs45500OnTheFourteenthDay() {
		// 季節 91 日のうち 14 日が過ぎ（9/14）、実績 7,000 円 → 7,000 × 91 ÷ 14 ＝ 45,500
		Pace pace = autumnOn(7_000, 9, 14).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(7_000L * 91);
		assertThat(pace.denominator()).isEqualTo(14L);
		assertThat(pace.displayAmount()).isEqualTo(45_500L);
	}

	@Test
	void theDesignExampleForMedicalCostsIs4550AndShowsAbout4600() {
		// 設計書 5.14：2026/11/11（n＝72）、医療費 3,600 × 91 ÷ 72 ＝ 4,550。表は 100 円切り上げで 4,600
		Pace pace = autumnOn(3_600, 11, 11).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(3_600L * 91);
		assertThat(pace.denominator()).isEqualTo(72L);
		assertThat(pace.displayAmount()).isEqualTo(4_600L);
	}

	@ParameterizedTest
	@CsvSource({
			"49200, 62200",    // 62,183.33…
			"5600, 7100",      // 7,077.77…
			"22000, 27900",    // 27,805.55…
			"3600, 4600",      // 4,550
			"80400, 101700"    // 合計の行：101,616.66… 。科目ごとの足し算（101,800）とは 100 円ちがう
	})
	void displayRoundsUpToHundredYenOnNovember11(long actual, long expectedDisplay) {
		assertThat(autumnOn(actual, 11, 11).orElseThrow().displayAmount()).isEqualTo(expectedDisplay);
	}

	// ---- 14 日の境目 ----

	@Test
	void the13thDayIsNotUsable() {
		assertThat(autumnOn(7_000, 9, 13)).isEmpty();
	}

	@Test
	void the14thDayIsUsable() {
		Pace pace = autumnOn(7_000, 9, 14).orElseThrow();

		assertThat(pace.denominator()).isEqualTo(14L);
	}

	@Test
	void the15thDayIsUsable() {
		Pace pace = autumnOn(7_000, 9, 15).orElseThrow();

		assertThat(pace.denominator()).isEqualTo(15L);
	}

	@Test
	void theFirstDayIsNotUsable() {
		assertThat(autumnOn(7_000, 9, 1)).isEmpty();
	}

	@Test
	void theLastDayOfTheSeasonIsUsableAndEqualsTheActual() {
		// 11/30 は 91 日目（まだ今日を含む）。実績 × 91 ÷ 91 ＝ 実績
		Pace pace = autumnOn(12_345, 11, 30).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(12_345L * 91);
		assertThat(pace.denominator()).isEqualTo(91L);
		assertThat(pace.displayAmount()).isEqualTo(12_400L);
	}

	// ---- 終わった季節 ----

	@Test
	void aFinishedSeasonUsesTheActualAsIs() {
		// 12/1 は、秋が終わった次の日
		Pace pace = SpendingPace.of(15_200, AUTUMN_2026, LocalDate.of(2026, 12, 1)).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(15_200L);
		assertThat(pace.denominator()).isEqualTo(1L);
		assertThat(pace.displayAmount()).isEqualTo(15_200L);
	}

	@Test
	void aFinishedSeasonIsUsableEvenYearsLaterAndEvenWithZeroActual() {
		Pace pace = SpendingPace.of(0, AUTUMN_2026, LocalDate.of(2030, 5, 20)).orElseThrow();

		assertThat(pace.numerator()).isZero();
		assertThat(pace.denominator()).isEqualTo(1L);
		assertThat(pace.displayAmount()).isZero();
	}

	@Test
	void aFinishedSeasonDisplayRoundsUpToHundredYen() {
		assertThat(SpendingPace.of(15_200, AUTUMN_2026, LocalDate.of(2026, 12, 1)).orElseThrow().displayAmount())
				.isEqualTo(15_200L);
		assertThat(SpendingPace.of(15_201, AUTUMN_2026, LocalDate.of(2026, 12, 1)).orElseThrow().displayAmount())
				.isEqualTo(15_300L);
	}

	// ---- まだ始まっていない季節 ----

	@Test
	void aSeasonThatHasNotStartedIsNotUsable() {
		// 秋の前の日（8/31）
		assertThat(autumnOn(7_000, 8, 31)).isEmpty();
		// 来年の秋を、今日（2026/11/11）に見る
		assertThat(SpendingPace.of(7_000, new SeasonPeriod(2027, Season.AUTUMN), LocalDate.of(2026, 11, 11))).isEmpty();
	}

	// ---- 実績 0 円 ----

	@Test
	void zeroActualInTheMiddleOfTheSeasonIsPresentAndZero() {
		Pace pace = autumnOn(0, 11, 11).orElseThrow();

		assertThat(pace.displayAmount()).isZero();
	}

	// ---- 季節の日数（90・91・92）----

	@Test
	void winterOf90DaysUsesNinety() {
		// 2025 冬：2025-12-01 〜 2026-02-28 ＝ 90 日。12/14 が 14 日目
		SeasonPeriod winter = new SeasonPeriod(2025, Season.WINTER);

		Pace pace = SpendingPace.of(9_000, winter, LocalDate.of(2025, 12, 14)).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(9_000L * 90);
		assertThat(pace.denominator()).isEqualTo(14L);
		assertThat(SpendingPace.of(9_000, winter, LocalDate.of(2025, 12, 13))).isEmpty();
	}

	@Test
	void winterOf91DaysInALeapYearUsesNinetyOne() {
		// 2023 冬：2023-12-01 〜 2024-02-29 ＝ 91 日。翌年 2 月の中で見る（1/20 は 51 日目）
		SeasonPeriod winter = new SeasonPeriod(2023, Season.WINTER);

		Pace pace = SpendingPace.of(10_000, winter, LocalDate.of(2024, 1, 20)).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(10_000L * 91);
		assertThat(pace.denominator()).isEqualTo(51L);
	}

	@Test
	void springOf92DaysUsesNinetyTwo() {
		SeasonPeriod spring = new SeasonPeriod(2026, Season.SPRING);

		Pace pace = SpendingPace.of(9_200, spring, LocalDate.of(2026, 3, 14)).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(9_200L * 92);
		assertThat(pace.denominator()).isEqualTo(14L);
		assertThat(pace.displayAmount()).isEqualTo(60_500L);   // 9,200 × 92 ÷ 14 ＝ 60,457.14…
	}

	@Test
	void theLastDayOfWinterInFebruaryIsStillInsideTheSeason() {
		// 2026-02-28 は 2025 冬の 90 日目。翌日 3/1 は終わった季節の扱い
		SeasonPeriod winter = new SeasonPeriod(2025, Season.WINTER);

		assertThat(SpendingPace.of(9_000, winter, LocalDate.of(2026, 2, 28)).orElseThrow().denominator()).isEqualTo(90L);
		assertThat(SpendingPace.of(9_000, winter, LocalDate.of(2026, 3, 1)).orElseThrow().denominator()).isEqualTo(1L);
	}

	// ---- 整数で、途中で丸めない ----

	@Test
	void nothingIsRoundedBeforeTheFinalDisplay() {
		// 1 円 × 91 ÷ 72 ＝ 1.26… 。分子と分母のまま持ち、表示のときだけ 100 円に切り上げる
		Pace pace = autumnOn(1, 11, 11).orElseThrow();

		assertThat(pace.numerator()).isEqualTo(91L);
		assertThat(pace.denominator()).isEqualTo(72L);
		assertThat(pace.displayAmount()).isEqualTo(100L);
	}

	// ---- 桁あふれ ----

	@Test
	void overflowWhileMultiplyingByTheSeasonDaysThrows() {
		assertThatThrownBy(() -> autumnOn(Long.MAX_VALUE / 2, 11, 11)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void roundingTheLargestAmountUpToHundredYenOverflowsAndThrows() {
		Pace pace = SpendingPace.of(Long.MAX_VALUE, AUTUMN_2026, LocalDate.of(2026, 12, 1)).orElseThrow();

		assertThatThrownBy(pace::displayAmount).isInstanceOf(ArithmeticException.class);
	}

}
