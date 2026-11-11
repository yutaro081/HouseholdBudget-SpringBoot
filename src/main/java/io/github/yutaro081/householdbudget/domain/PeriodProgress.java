package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 「今日」の線の位置と、棒の長さ（設計書 5.11、要件定義書 4.5）。
 * 画面にもデータベースにも依存しない計算。値はすべて <b>1000分率の整数</b>（0〜1000。500 なら 50.0％）。
 * 小数を使わないので、誤差が出ない。画面に出すときは「整数部.小数1桁%」にする（TopService、テンプレート）。
 * <p>
 * 日が進んだ割合 ＝ 今日を含めた日数 × 1000 ÷ 全部の日数（切り捨て）。最後の日は 1000（100％）。
 */
public final class PeriodProgress {

	/** 100％ ＝ 1000 */
	public static final int FULL = 1000;

	private PeriodProgress() {
	}

	/** 季節の全部の日数（春92、夏92、秋91、冬90または91）。 */
	public static int seasonDays(SeasonPeriod period) {
		return (int) ChronoUnit.DAYS.between(period.startDate(), period.endDateExclusive());
	}

	/** 今日が季節の何日目か（季節の最初の日＝1）。今日を含める。 */
	public static int seasonDayNumber(SeasonPeriod period, LocalDate today) {
		return (int) ChronoUnit.DAYS.between(period.startDate(), today) + 1;
	}

	/** 季節の終わりまであと何日（今日を含める。季節の最後の日は 1）。見出しの行「季節の終わりまで、あと ○ 日」に使う（issue 017）。 */
	public static int seasonDaysLeft(SeasonPeriod period, LocalDate today) {
		return seasonDays(period) - seasonDayNumber(period, today) + 1;
	}

	/**
	 * 四捨五入した整数の％（半分は切り上げ）＝ 分子 × 100 ÷ 分母。整数だけで計算する（issue 017）。
	 * 式は (2 × 分子 × 100 + 分母) ÷ (2 × 分母) の商。例：1/8 ＝ 12.5％ → 13、1/3 ＝ 33.3％ → 33。
	 * 100％をこえてもそのまま（6/5 → 120）。
	 */
	public static int roundedPercent(long numerator, long denominator) {
		return (int) ((200 * numerator + denominator) / (2 * denominator));
	}

	/**
	 * 「予算のうち ○% を使った」の％（四捨五入した整数。100％をこえてもそのまま）。
	 * 予算が未設定（null）または 0円のときは、割合を決められないので null（画面は文を出さない）。
	 */
	public static Integer usedPercent(Long budget, long actual) {
		if (budget == null || budget <= 0) {
			return null;
		}
		return roundedPercent(actual, budget);
	}

	/** 季節の何％まで日が進んだか（四捨五入した整数）。「縦の線は『今日』（季節の ○%）」に使う。 */
	public static int seasonElapsedPercent(SeasonPeriod period, LocalDate today) {
		return roundedPercent(seasonDayNumber(period, today), seasonDays(period));
	}

	/** 季節の何割まで日が進んだか（1000分率）。 */
	public static int seasonElapsedPermille(SeasonPeriod period, LocalDate today) {
		return elapsedPermille(seasonDayNumber(period, today), seasonDays(period));
	}

	/** 週の何割まで日が進んだか（1000分率）。dayNumber は日曜＝1、土曜＝7。 */
	public static int weekElapsedPermille(int dayNumber) {
		return elapsedPermille(dayNumber, WeekPeriod.DAYS);
	}

	private static int elapsedPermille(int dayNumber, int totalDays) {
		return dayNumber * FULL / totalDays;
	}

}
