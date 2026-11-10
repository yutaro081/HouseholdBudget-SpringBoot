package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 着地見込みの計算（要件定義書 4.4、設計書 5.13、issue 018）。「今のペースが続いたら、季節の終わりに、合計でいくらになるか」。
 * 画面にもデータベースにも依存しない計算。整数（long）だけで行い、小数を使わない。
 * <p>
 * 式：見込み ＝ 実績 ×（1 ＋ r）＋ 予算 × r²。r ＝ 残りの割合 ＝ L ÷ D。
 * 季節の初めは予算を信じ、日がたつほど実際のペースを信じる。予定どおりのペース（実績 ＝ 予算 × 経過の割合）なら、どの日でも見込み ＝ 予算。
 * <ul>
 * <li>D ＝ 季節の日数（{@link PeriodProgress#seasonDays}）</li>
 * <li>L ＝ 残りの日数 ＝ D − 今日が季節の何日目か（今日は経過に含める。最後の日は 0）。
 * <b>{@link PeriodProgress#seasonDaysLeft}（今日を含む残り日数。最後の日は 1）より 1 小さい</b></li>
 * </ul>
 * 整数で計算する形（分母を D² にそろえた）：見込み ＝（実績 × D ×（D ＋ L）＋ 予算 × L²）÷ D²。
 * 100 円単位の切り上げは、小数を使わず、整数の割り算：切り上げ（分子 ÷（100 × D²））× 100。
 * <p>
 * <b>桁あふれ</b>：すべて long で計算し、Math.multiplyExact・addExact を使う。あふれたら ArithmeticException にして、
 * 黙って違う数字を出さない（季節が 92 日のとき、実績が約 5.5 × 10^14 円までは収まる）。
 * <p>
 * 「予算の ○%」は、{@link PeriodProgress#roundedPercent} と同じ決まり（四捨五入。半分は切り上げ）。分子は、表示する見込みの額（100 円切り上げ後）。
 * 「上回る」は金額（切り上げ後の見込み ＞ 予算）で決め、％では決めない。そのため、予算の 0.5% 未満だけ上回るとき、％は 100 のまま「上回る」になる（Q018-9 の A）。
 */
public final class LandingForecast {

	/** 見込みの額の単位（円） */
	private static final long UNIT = 100;

	private LandingForecast() {
	}

	/**
	 * 結果。
	 *
	 * @param amount          見込みの額（円。100 円単位に切り上げ）
	 * @param percentOfBudget 予算に対する％（表示する額 ÷ 予算の四捨五入。100％をこえてもそのまま）
	 * @param overBudget      表示する見込みの額が、予算を超えるか（金額で決める。％では決めない）
	 */
	public record Result(long amount, int percentOfBudget, boolean overBudget) {
	}

	/**
	 * 見込み。次のときは空（画面は出さない）。
	 * <ul>
	 * <li>予算が未設定（null）、または 0 円〔設定済みの合計が 0〕</li>
	 * <li>表示する季節が、今日が属する季節でない（過去・未来の季節）</li>
	 * </ul>
	 *
	 * @param budget 合計の予算（設定済みの科目の合計。予実比較の合計行と同じ値）。未設定なら null
	 * @param actual 合計の実績（全科目の合計。予実比較の合計行と同じ値）
	 * @throws ArithmeticException あふれたとき
	 */
	public static Optional<Result> of(Long budget, long actual, SeasonPeriod season, LocalDate today) {
		if (budget == null || budget <= 0) {
			return Optional.empty();
		}
		if (!season.equals(SeasonPeriod.from(today))) {
			return Optional.empty();
		}
		int days = PeriodProgress.seasonDays(season);
		int remainingDays = days - PeriodProgress.seasonDayNumber(season, today);   // 今日は経過に含める。最後の日は 0
		long amount = amount(budget, actual, days, remainingDays);
		return Optional.of(new Result(amount, percent(amount, budget), amount > budget));
	}

	/**
	 * 見込みの額（円。100 円単位に切り上げ）＝ 切り上げ（（実績 × D ×（D ＋ L）＋ 予算 × L²）÷（100 × D²））× 100。
	 *
	 * @param days          季節の日数 D
	 * @param remainingDays 残りの日数 L（今日を経過に含める。最後の日は 0）
	 * @throws ArithmeticException あふれたとき
	 */
	public static long amount(long budget, long actual, int days, int remainingDays) {
		long d = days;
		long l = remainingDays;
		long fromActual = Math.multiplyExact(Math.multiplyExact(actual, d), Math.addExact(d, l));   // 実績 × D × (D + L)
		long fromBudget = Math.multiplyExact(budget, Math.multiplyExact(l, l));                     // 予算 × L²
		long numerator = Math.addExact(fromActual, fromBudget);
		long denominator = Math.multiplyExact(UNIT, Math.multiplyExact(d, d));                     // 100 × D²
		return Math.multiplyExact(Math.ceilDiv(numerator, denominator), UNIT);
	}

	/** 四捨五入した整数％（半分は切り上げ）＝ (2 × 額 × 100 ＋ 予算) ÷ (2 × 予算)。PeriodProgress.roundedPercent と同じ決まり。あふれたら例外 */
	private static int percent(long amount, long budget) {
		long numerator = Math.addExact(Math.multiplyExact(200L, amount), budget);
		long denominator = Math.multiplyExact(2L, budget);
		return Math.toIntExact(numerator / denominator);
	}

}
