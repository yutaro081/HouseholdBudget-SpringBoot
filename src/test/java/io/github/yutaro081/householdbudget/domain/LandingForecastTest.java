package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yutaro081.householdbudget.domain.LandingForecast.Result;

/**
 * 着地見込みの計算の単体テスト（要件定義書 4.4、設計書 5.13、issue 018）。Spring もデータベースも使わない。
 * 見込み ＝ 実績 ×（1 ＋ 残りの割合）＋ 予算 ×（残りの割合）²。整数の形は（実績 × D ×（D ＋ L）＋ 予算 × L²）÷ D²。
 * D ＝ 季節の日数、L ＝ 残りの日数 ＝ D − 今日が季節の何日目か（今日は経過に含める。最後の日は 0）。100 円単位で切り上げ。
 * 期待値は、手と分数で計算した値（手順書 2.2）。
 */
class LandingForecastTest {

	/** 2026年秋（9〜11月。91 日）の、ある日 */
	private static SeasonPeriod autumn() {
		return SeasonPeriod.from(LocalDate.of(2026, 9, 1));
	}

	private static Optional<Result> autumnOn(Long budget, long actual, LocalDate today) {
		return LandingForecast.of(budget, actual, SeasonPeriod.from(today), today);
	}

	// ---- 例（手順書 2.2） ----

	@Test
	void theDemoExampleIsAbout142000AndEightyOnePercentOfTheBudget() {
		// 実績 107,800、予算 175,000、91 日中 70 日目（2026-11-09）。141,996.45… → 142,000
		Result r = autumnOn(175_000L, 107_800, LocalDate.of(2026, 11, 9)).orElseThrow();

		assertThat(r.amount()).isEqualTo(142_000L);
		assertThat(r.percentOfBudget()).isEqualTo(81);
		assertThat(r.overBudget()).isFalse();
	}

	@Test
	void theThirdDayExampleTrustsTheBudgetAtTheStartOfTheSeason() {
		// 季節の 3 日目（2026-09-03）に食費 15,000 円、予算 40,000 円。66,911.60… → 67,000。日割りなら 455,000 円になる所
		Result r = autumnOn(40_000L, 15_000, LocalDate.of(2026, 9, 3)).orElseThrow();

		assertThat(r.amount()).isEqualTo(67_000L);
		assertThat(r.percentOfBudget()).isEqualTo(168);          // 67,000 ÷ 40,000 ＝ 167.5% → 四捨五入で 168
		assertThat(r.overBudget()).isTrue();
	}

	@Test
	void onTheFirstDayWithNothingSpentTheForecastIsCloseToTheBudget() {
		// 2026-09-01（D ＝ 91、L ＝ 90）。175,000 × 90² ÷ 91² ＝ 171,174.98… → 171,200
		Result r = autumnOn(175_000L, 0, LocalDate.of(2026, 9, 1)).orElseThrow();

		assertThat(r.amount()).isEqualTo(171_200L);
		assertThat(r.percentOfBudget()).isEqualTo(98);
		assertThat(r.overBudget()).isFalse();
	}

	@Test
	void whenTheActualIsAlreadyOverTheBudgetTheForecastIsOverToo() {
		Result r = autumnOn(175_000L, 200_000, LocalDate.of(2026, 11, 9)).orElseThrow();

		assertThat(r.amount()).isEqualTo(255_500L);
		assertThat(r.percentOfBudget()).isEqualTo(146);
		assertThat(r.overBudget()).isTrue();
	}

	// ---- 性質 ----

	/** 予定どおりのペース（実績 ＝ 予算 × 経過の割合）なら、どの日でも、見込み ＝ 予算。D ＝ 91、予算 91,000 円なら、1 日目から 91 日目まで、1 日 1,000 円 */
	@Test
	void onTheExpectedPaceTheForecastIsExactlyTheBudgetOnEveryDayOfTheSeason() {
		for (int day = 1; day <= 91; day++) {
			LocalDate today = LocalDate.of(2026, 9, 1).plusDays(day - 1);
			Result r = autumnOn(91_000L, 1_000L * day, today).orElseThrow();

			assertThat(r.amount()).as(day + " 日目").isEqualTo(91_000L);
			assertThat(r.percentOfBudget()).as(day + " 日目の％").isEqualTo(100);
			assertThat(r.overBudget()).as(day + " 日目に、上回ると出ない").isFalse();
		}
	}

	/** 季節の日数：春 92、夏 92、秋 91、冬 90、冬 91（うるう年）。どの季節でも、予定どおりなら見込み ＝ 予算 */
	@ParameterizedTest(name = "{0} の最初・まん中・最後の日")
	@CsvSource({
			"2026-03-01, 92",   // 春
			"2026-06-01, 92",   // 夏
			"2026-09-01, 91",   // 秋
			"2026-12-01, 90",   // 冬（2027 年 2 月は 28 日まで）
			"2027-12-01, 91"    // 冬（2028 年はうるう年。2 月は 29 日まで）
	})
	void theSeasonLengthsAreHandled(LocalDate start, int days) {
		assertThat(PeriodProgress.seasonDays(SeasonPeriod.from(start))).isEqualTo(days);
		long budget = 1_000L * days;
		for (int day : new int[] { 1, days / 2, days }) {
			LocalDate today = start.plusDays(day - 1);
			Result r = autumnOn(budget, 1_000L * day, today).orElseThrow();

			assertThat(r.amount()).as(start + " の " + day + " 日目").isEqualTo(budget);
		}
	}

	/** 最後の日は L ＝ 0。見込み ＝ 実績（100 円に切り上げ）。残りの日数に seasonDaysLeft〔今日を含む。最後の日は 1〕を使うと、ここで食い違う */
	@ParameterizedTest(name = "最後の日に実績 {0} 円 → 見込み {1} 円")
	@CsvSource({
			"107800, 107800",    // 100 円の倍数：そのまま
			"107801, 107900",    // 1 円超える：次の 100 円へ
			"107799, 107800",    // 1 円足りない：その 100 円へ
			"0, 0"
	})
	void onTheLastDayTheForecastIsTheActualRoundedUpTo100(long actual, long expected) {
		Result r = autumnOn(175_000L, actual, LocalDate.of(2026, 11, 30)).orElseThrow();

		assertThat(r.amount()).isEqualTo(expected);
	}

	@Test
	void theForecastNeverFallsBelowTheActual() {
		Random random = new Random(20261110);
		for (int i = 0; i < 20_000; i++) {
			long budget = 1 + random.nextInt(3_000_000);
			long actual = random.nextInt(3_000_000);
			LocalDate today = LocalDate.of(2026, 9, 1).plusDays(random.nextInt(91));

			assertThat(autumnOn(budget, actual, today).orElseThrow().amount())
					.as("予算 " + budget + "、実績 " + actual + "、" + today).isGreaterThanOrEqualTo(actual);
		}
	}

	// ---- 出さない ----

	@Test
	void noForecastWithoutABudgetOrWithAZeroBudget() {
		LocalDate today = LocalDate.of(2026, 11, 9);

		assertThat(autumnOn(null, 107_800, today)).isEmpty();      // 予算が未設定
		assertThat(autumnOn(0L, 107_800, today)).isEmpty();        // 予算の合計が 0 円（Q018-1 の A）
	}

	@Test
	void noForecastForAPastOrFutureSeason() {
		LocalDate today = LocalDate.of(2026, 11, 9);                       // 今の季節は 2026 年秋

		assertThat(LandingForecast.of(175_000L, 107_800, SeasonPeriod.from(LocalDate.of(2026, 6, 1)), today)).as("過去（夏）").isEmpty();
		assertThat(LandingForecast.of(175_000L, 107_800, SeasonPeriod.from(LocalDate.of(2026, 12, 1)), today)).as("未来（冬）").isEmpty();
		assertThat(LandingForecast.of(175_000L, 107_800, SeasonPeriod.from(LocalDate.of(2025, 9, 1)), today)).as("去年の秋").isEmpty();
		assertThat(LandingForecast.of(175_000L, 107_800, autumn(), today)).as("今の季節").isPresent();
	}

	// ---- 「予算の ○%」と「上回ります」の境目（Q018-9 の A：四捨五入のまま。食い違う場合を決まりとして確かめる） ----

	/** 予算 91,000 円・70 日目（2026-11-09）。予定どおり（実績 70,000）で、ちょうど予算 */
	@ParameterizedTest(name = "実績 {0} 円 → 見込み {1} 円、予算の {2}％、上回る＝{3}")
	@CsvSource({
			"69999, 91000, 100, false",     // 90,998.77… → 切り上げて 91,000：予算と同額なので、上回らない
			"70000, 91000, 100, false",     // ちょうど予算
			"70001, 91100, 100, true"       // 91,001.23… → 91,100：予算より 100 円多い。上回る。％は 100 のまま
	})
	void aroundTheBudgetTheOverFlagFollowsTheAmountAndThePercentIsRounded(long actual, long amount, int percent, boolean over) {
		Result r = autumnOn(91_000L, actual, LocalDate.of(2026, 11, 9)).orElseThrow();

		assertThat(r.amount()).isEqualTo(amount);
		assertThat(r.percentOfBudget()).isEqualTo(percent);
		assertThat(r.overBudget()).isEqualTo(over);
	}

	/** 「上回る」は金額で決まり、％では決まらない：予算 175,000 円で、見込みが 175,100〜175,800 円（予算の 0.5% 未満の超過）なら、％は 100 のまま、上回る。175,900 円で 101% */
	@ParameterizedTest(name = "最後の日に実績 {0} 円（予算 {1} 円）→ 予算の {2}％、上回る＝{3}")
	@CsvSource({
			"174900, 175000, 100, false",
			"175000, 175000, 100, false",
			"175100, 175000, 100, true",      // 100.06%：四捨五入で 100。でも、上回る
			"175800, 175000, 100, true",      // 100.46%
			"175900, 175000, 101, true",      // 100.51%
			"20000,  20000,  100, false",     // 予算 20,000 円以下は、100 円多いだけで 101% になる
			"20050,  20000,  101, true"       // 見込みは 20,100 円（切り上げ）→ 100.5%
	})
	void theOverFlagIsDecidedByTheAmountNotByThePercent(long actual, long budget, int percent, boolean over) {
		Result r = autumnOn(budget, actual, LocalDate.of(2026, 11, 30)).orElseThrow();

		assertThat(r.percentOfBudget()).isEqualTo(percent);
		assertThat(r.overBudget()).isEqualTo(over);
	}

	/** 「予算の ○%」は、「予算は ○% 使いました」と同じ決まり（PeriodProgress.roundedPercent。四捨五入、半分は切り上げ） */
	@Test
	void thePercentUsesTheSameRoundingAsTheUsedPercent() {
		Random random = new Random(7);
		for (int i = 0; i < 5_000; i++) {
			long budget = 1 + random.nextInt(3_000_000);
			long actual = random.nextInt(3_000_000);
			LocalDate today = LocalDate.of(2026, 9, 1).plusDays(random.nextInt(91));
			Result r = autumnOn(budget, actual, today).orElseThrow();

			assertThat(r.percentOfBudget()).isEqualTo(PeriodProgress.roundedPercent(r.amount(), budget));
		}
	}

	// ---- 整理前の式との一致（分数で確かめる） ----

	/** 厳密な分数（BigInteger）。整理前の式：見込み ＝ 実績 ＋ 残りの予算 × 補正率 を、そのまま書く（整数の形を使わない） */
	private record Fraction(BigInteger n, BigInteger d) {
		static Fraction of(long n, long d) {
			return new Fraction(BigInteger.valueOf(n), BigInteger.valueOf(d));
		}

		Fraction plus(Fraction o) {
			return new Fraction(n.multiply(o.d).add(o.n.multiply(d)), d.multiply(o.d));
		}

		Fraction times(Fraction o) {
			return new Fraction(n.multiply(o.n), d.multiply(o.d));
		}

		/** 100 円単位の切り上げ */
		long ceilTo100() {
			BigInteger unit = d.multiply(BigInteger.valueOf(100));
			BigInteger[] qr = n.divideAndRemainder(unit);
			BigInteger q = qr[1].signum() > 0 ? qr[0].add(BigInteger.ONE) : qr[0];
			return q.multiply(BigInteger.valueOf(100)).longValueExact();
		}
	}

	private static long beforeTidyingUp(long budget, long actual, int days, int dayNumber) {
		Fraction elapsed = Fraction.of(dayNumber, days);
		Fraction remaining = Fraction.of(days - dayNumber, days);
		Fraction remainingBudget = Fraction.of(budget, 1).times(remaining);                                   // 残りの予算 ＝ 予算 × 残りの割合
		Fraction pace = Fraction.of(actual, 1).times(new Fraction(BigInteger.valueOf(days), BigInteger.valueOf(budget).multiply(BigInteger.valueOf(dayNumber))));
		// ペース ＝ 実績 ÷（予算 × 経過の割合）
		Fraction correction = elapsed.times(pace).plus(remaining);                                             // 補正率 ＝ 経過の割合 × ペース ＋ 残りの割合 × 1
		return Fraction.of(actual, 1).plus(remainingBudget.times(correction)).ceilTo100();
	}

	@Test
	void theIntegerFormGivesTheSameAnswerAsTheFormBeforeTidyingUp() {
		Random random = new Random(20261110);
		int[] lengths = { 90, 91, 92 };
		for (int i = 0; i < 20_000; i++) {
			int days = lengths[random.nextInt(3)];
			int dayNumber = 1 + random.nextInt(days);
			long budget = 1 + random.nextInt(3_000_000);
			long actual = random.nextInt(3_000_000);

			assertThat(LandingForecast.amount(budget, actual, days, days - dayNumber))
					.as("D=" + days + " " + dayNumber + " 日目 予算 " + budget + " 実績 " + actual)
					.isEqualTo(beforeTidyingUp(budget, actual, days, dayNumber));
		}
	}

	// ---- 大きな金額と桁あふれ ----

	/** 取引の上限 1,000,000 円が季節に 500 件（実績 5 億円）、予算は 1 科目 3,000,000 円 × 256 科目 ＝ 7.68 億円。D ＝ 92、52 日目（L ＝ 40） */
	@Test
	void aLargeAmountIsCalculatedExactly() {
		long actual = 500L * 1_000_000;
		long budget = 256L * 3_000_000;

		assertThat(LandingForecast.amount(budget, actual, 92, 40)).isEqualTo(862_570_900L);          // 862,570,888.47… → 切り上げ
		assertThat(LandingForecast.amount(budget, actual, 92, 40)).isEqualTo(beforeTidyingUp(budget, actual, 92, 52));
		Result r = LandingForecast.of(budget, actual, SeasonPeriod.from(LocalDate.of(2026, 3, 1)), LocalDate.of(2026, 3, 1).plusDays(51)).orElseThrow();
		assertThat(r.amount()).isEqualTo(862_570_900L);
		assertThat(r.percentOfBudget()).isEqualTo(112);
		assertThat(r.overBudget()).isTrue();
	}

	@Test
	void overflowIsAnExceptionAndNeverASilentlyWrongNumber() {
		// 実績が long の上限の半分：実績 × D ×（D ＋ L）が long に収まらない
		assertThatThrownBy(() -> LandingForecast.amount(1_000L, Long.MAX_VALUE / 2, 91, 21)).isInstanceOf(ArithmeticException.class);
		// 予算が大きすぎる：予算 × L² が収まらない
		assertThatThrownBy(() -> LandingForecast.amount(Long.MAX_VALUE / 2, 0, 91, 90)).isInstanceOf(ArithmeticException.class);
		// 画面に近い入り口でも、同じ
		LocalDate today = LocalDate.of(2026, 11, 9);
		assertThatThrownBy(() -> autumnOn(1_000L, Long.MAX_VALUE / 2, today)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void theLargestAmountThatStillFitsIsCalculatedNotRejected() {
		// 実績が約 5 × 10^14 円（長さ 92 日の季節で、long に収まる上限 約 5.5 × 10^14 円の内側）
		long actual = 500_000_000_000_000L;

		assertThat(LandingForecast.amount(1_000L, actual, 92, 0)).isEqualTo(actual);               // 最後の日は実績と同じ
	}

}
