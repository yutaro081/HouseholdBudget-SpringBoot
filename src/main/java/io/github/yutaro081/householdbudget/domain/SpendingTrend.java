package io.github.yutaro081.householdbudget.domain;

/**
 * 出費の傾向（全科目で同じ。%）を求める（設計書 4.2、5.14、issue 019）。
 * 傾向 ＝ 四捨五入（100 ×（S ＋ 3 ×（T3 − T1））÷ S）%。S ＝ T1＋T2＋T3。半分は切り上げ。上限は 70〜130%。
 * 整数（long）だけで計算し、あふれたら ArithmeticException。
 */
public final class SpendingTrend {

	private static final int MAX_PERCENT = 130;
	private static final int MIN_PERCENT = 70;

	private SpendingTrend() {
	}

	/**
	 * 結果。
	 *
	 * @param percent    案にかける傾向の %（整数。70〜130）。計算しなかったときは 100
	 * @param capped     上限（130）か下限（70）で止めたか。ちょうど 130・70 は止めたことにしない
	 * @param calculated 計算したか（12 季節がそろわないとき、S が 0 のときは false）
	 */
	public record Result(int percent, boolean capped, boolean calculated) {
	}

	/**
	 * @param allSeasonsHaveRecords 12 季節すべてに自分の取引が 1 件以上あるか
	 * @param t1                    いちばん古い 4 季節の、全科目の実績の合計
	 * @param t2                    次の 4 季節の合計
	 * @param t3                    いちばん新しい 4 季節の合計
	 * @throws ArithmeticException あふれたとき
	 */
	public static Result of(boolean allSeasonsHaveRecords, long t1, long t2, long t3) {
		if (!allSeasonsHaveRecords) {
			return notCalculated();
		}
		long sum = Math.addExact(Math.addExact(t1, t2), t3);
		if (sum == 0) {
			return notCalculated();
		}
		long numerator = Math.addExact(sum, Math.multiplyExact(3L, Math.subtractExact(t3, t1)));   // S ＋ 3 ×（T3 − T1）
		// 四捨五入（半分は切り上げ）＝ floor((2 × 100 × 分子 ＋ S) ÷ (2 × S))
		long rounded = Math.floorDiv(
				Math.addExact(Math.multiplyExact(200L, numerator), sum),
				Math.multiplyExact(2L, sum));
		if (rounded > MAX_PERCENT) {
			return new Result(MAX_PERCENT, true, true);
		}
		if (rounded < MIN_PERCENT) {
			return new Result(MIN_PERCENT, true, true);
		}
		return new Result((int) rounded, false, true);
	}

	private static Result notCalculated() {
		return new Result(100, false, false);
	}

}
