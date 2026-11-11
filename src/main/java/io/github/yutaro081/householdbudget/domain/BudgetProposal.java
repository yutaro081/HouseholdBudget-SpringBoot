package io.github.yutaro081.householdbudget.domain;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 科目ごとの予算の案（ルール①〜④を上から順に当てはめる。設計書 4.2、5.14、issue 019）。
 * <ul>
 * <li>①中央値 ＞ 0 円 → 中央値 × 傾向 × 選んだ割合</li>
 * <li>②直前の実績 ＞ 0 円、かつペースが使える → ペース × 選んだ割合（傾向はかけない）</li>
 * <li>③直前の予算 ＞ 0 円 → 予算 × 選んだ割合（傾向はかけない）</li>
 * <li>④どれもない → 今の値のまま（割合もかけない）</li>
 * </ul>
 * ①〜③は、傾向の % のほかは途中で丸めず、最後に 1 回だけ 1,000 円単位に切り上げ、3,000,000 円を上限とする。
 * 整数（long）だけで計算し、あふれたら ArithmeticException。
 */
public final class BudgetProposal {

	/** 1 科目の上限（円）。 */
	public static final long LIMIT = 3_000_000;

	/** 切り上げの単位（円） */
	private static final long UNIT = 1_000;

	private BudgetProposal() {
	}

	/**
	 * 結果。
	 *
	 * @param rule   当てはまったルールの番号（1〜4）
	 * @param amount 案の額（円）
	 */
	public record Result(int rule, long amount) {
	}

	/**
	 * @param twiceMedian    もとの額（中央値）の 2 倍。記録のある年がないときは空
	 * @param trendPercent   傾向の %（ルール①だけにかける）
	 * @param choice         選んだ割合
	 * @param previousActual 直前の季節の実績
	 * @param pace           直前の季節のペース（使えないときは空）
	 * @param previousBudget 直前の季節の予算（未設定は null）
	 * @param currentValue   今の値（保存済みならその額、なければ 0 円）
	 * @throws ArithmeticException あふれたとき
	 */
	public static Result of(OptionalLong twiceMedian, int trendPercent, ProposalChoice choice, long previousActual,
			Optional<SpendingPace.Pace> pace, Long previousBudget, long currentValue) {
		long c = choice.percent();
		if (twiceMedian.isPresent() && twiceMedian.getAsLong() > 0) {
			// 切り上げ（M2 × t × c ÷（2 × 100 × 100 × 1000））× 1000
			long numerator = Math.multiplyExact(Math.multiplyExact(twiceMedian.getAsLong(), (long) trendPercent), c);
			return new Result(1, roundedUp(numerator, 2L * 100 * 100 * UNIT));
		}
		if (previousActual > 0 && pace.isPresent()) {
			// 切り上げ（分子 × c ÷（分母 × 100 × 1000））× 1000
			long numerator = Math.multiplyExact(pace.get().numerator(), c);
			long denominator = Math.multiplyExact(pace.get().denominator(), 100L * UNIT);
			return new Result(2, roundedUp(numerator, denominator));
		}
		if (previousBudget != null && previousBudget > 0) {
			// 切り上げ（予算 × c ÷（100 × 1000））× 1000
			return new Result(3, roundedUp(Math.multiplyExact(previousBudget, c), 100L * UNIT));
		}
		return new Result(4, currentValue);
	}

	/** 切り上げ（分子 ÷ 分母）× 1000 を、上限 3,000,000 円で止める。 */
	private static long roundedUp(long numerator, long denominator) {
		long rounded = Math.multiplyExact(Math.ceilDiv(numerator, denominator), UNIT);
		return Math.min(rounded, LIMIT);
	}

}
