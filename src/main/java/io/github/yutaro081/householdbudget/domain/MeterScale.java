package io.github.yutaro081.householdbudget.domain;

/**
 * メーターの縮尺（設計書 2.3「メーターの見た目」、5.11、issue 020）。
 * <b>予算（100％）を、棒全体の 70％（700）の位置に置く</b>。棒の右端は約 143％（100 ÷ 0.7）。
 * トップの季節のメーター、今週の合計・科目ごとのメーター、予実比較のカードの、すべてのメーターが、この 1 か所の数字を使う（CSS・テンプレートに 70 を書かない）。
 * 整数（long）だけで計算する。長さ・位置は 1000分率（0〜1000。切り捨て）。画面は「整数部.小数1桁%」にして style 属性に入れる。
 * <ul>
 * <li>棒：予算までは通常色（{@code normalPermille}）、予算を超えた部分だけ赤（{@code overPermille}。700 から始まる）。</li>
 * <li>実績 × 700 が 予算 × 1000 を超えたら、棒は右端（1000）で止まり、棒の右に「+○%」（予算を超えた割合）を出す。</li>
 * <li>「今日」の線も同じ縮尺（日が進んだ割合 × 0.7）。</li>
 * </ul>
 */
public final class MeterScale {

	/** 予算（100％）の位置（棒全体の 1000分率）。この数字は、ここだけで決める */
	public static final int BUDGET_PERMILLE = 700;

	private static final int FULL = 1000;

	private MeterScale() {
	}

	/**
	 * 1 つのメーターの、画面に渡す値。
	 *
	 * @param budgetPermille  予算の線（と「予算」の文字）の位置 ＝ {@link #BUDGET_PERMILLE}
	 * @param normalPermille  通常色の部分の長さ（0 から、予算の位置まで）
	 * @param overPermille    予算を超えた部分（赤）の長さ。予算の位置から始まり、右端（1000）を超えない。超えていなければ 0
	 * @param over            実績が予算を超えているか（1 円でも超えたら true。赤い部分の長さが 0 に近くても出す）
	 * @param todayPermille   「今日」の線の位置
	 * @param overflowPercent 棒が右端で止まったとき（約 143％ を超えたとき）の「+○%」（（実績 − 予算）÷ 予算の四捨五入）。止まっていない、または予算が 0 円のときは null
	 */
	public record Meter(int budgetPermille, int normalPermille, int overPermille, boolean over, int todayPermille,
			Integer overflowPercent) {
	}

	/**
	 * @param budget          予算（円）。未設定なら null（空のメーター。画面はメーターを出さない）
	 * @param actual          実績（円）
	 * @param elapsedPermille 日が進んだ割合（1000分率。0〜1000）
	 * @throws ArithmeticException あふれたとき
	 */
	public static Meter of(Long budget, long actual, int elapsedPermille) {
		int today = (int) ((long) elapsedPermille * BUDGET_PERMILLE / FULL);
		if (budget == null) {
			return new Meter(BUDGET_PERMILLE, 0, 0, false, today, null);
		}
		if (budget == 0) {
			// 予算 0 円：実績がなければ空。あれば、予算の位置までは通常色、残りを赤（割合が決められないので「+○%」は出さない）
			return actual > 0
					? new Meter(BUDGET_PERMILLE, BUDGET_PERMILLE, FULL - BUDGET_PERMILLE, true, today, null)
					: new Meter(BUDGET_PERMILLE, 0, 0, false, today, null);
		}
		long scaled = Math.multiplyExact(actual, (long) BUDGET_PERMILLE) / budget;     // 実績 × 700 ÷ 予算
		boolean over = actual > budget;
		int normal = (int) Math.min(scaled, BUDGET_PERMILLE);
		int overPart = over ? (int) (Math.min(scaled, FULL) - BUDGET_PERMILLE) : 0;
		boolean capped = Math.multiplyExact(actual, (long) BUDGET_PERMILLE) > Math.multiplyExact(budget, (long) FULL);
		Integer overflow = capped ? PeriodProgress.roundedPercent(actual - budget, budget) : null;
		return new Meter(BUDGET_PERMILLE, normal, overPart, over, today, overflow);
	}
}
