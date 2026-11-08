package io.github.yutaro081.householdbudget.domain;

/**
 * 週の予算と、今週の実績の切り上げ（設計書 5.11、要件定義書 4.5）。
 * 画面にもデータベースにも依存しない計算。すべて整数（long）で行い、小数の誤差を作らない。
 * <p>
 * 週の予算 ＝ 季節の予算 ÷ 13 を、100円単位で切り上げた額。
 * 「÷ 13 をして100円単位で切り上げ」は、「予算 ÷ 1,300 を切り上げて × 100」と同じ結果になる（テストで0〜300万円を確かめている）。
 * 今週の実績 ＝ 科目ごとの合計を、100円単位で切り上げた額。
 * 色の判定は、ここでは行わない。{@link BudgetColorJudge} に、この2つの額を渡す。
 */
public final class WeeklyAmount {

	/** 切り上げの単位（円） */
	public static final long UNIT = 100;

	/** 季節を13週と数える */
	public static final long WEEKS_PER_SEASON = 13;

	private WeeklyAmount() {
	}

	/**
	 * 週の予算。
	 *
	 * @param seasonBudget 今日が入っている季節の、その科目の予算。未設定なら null
	 * @return 週の予算。季節の予算が未設定（null）なら null（未設定のまま）。0円は0円
	 */
	public static Long weeklyBudget(Long seasonBudget) {
		if (seasonBudget == null) {
			return null;
		}
		long step = UNIT * WEEKS_PER_SEASON;   // 1,300
		return (seasonBudget + step - 1) / step * UNIT;
	}

	/** 今週の実績を、100円単位で切り上げる（0円は0円）。 */
	public static long roundUpActual(long actual) {
		return (actual + UNIT - 1) / UNIT * UNIT;
	}

}
