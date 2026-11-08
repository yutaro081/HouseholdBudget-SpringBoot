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

	/**
	 * 棒の長さ（1000分率）＝ 実績 × 1000 ÷ 予算（切り捨て）。100％（1000）で止める。
	 * 予算が未設定（null）なら 0（棒は出さない）。予算が0円なら、実績があれば 1000、なければ 0。
	 */
	public static int barPermille(Long budget, long actual) {
		if (budget == null) {
			return 0;
		}
		if (budget == 0) {
			return actual > 0 ? FULL : 0;
		}
		return (int) Math.min(FULL, actual * FULL / budget);
	}

}
