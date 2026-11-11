package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 直前の季節の「ペース」（ルール②の元。設計書 4.2、5.14、issue 019）。
 * 整数（long）だけで計算し、あふれたら ArithmeticException。
 */
public final class SpendingPace {

	/** ペースを使う、過ぎた日数（今日を含む）の下限。これ未満は、季節の初めで大きくぶれるので使わない。 */
	private static final int MIN_ELAPSED_DAYS = 14;

	private SpendingPace() {
	}

	/**
	 * ペース。割り算の前の分数のまま持つ（案の計算で、途中で丸めないため）。
	 * 終わった季節は 実績 / 1、今日を含む季節は（実績 × 季節の日数）/ 過ぎた日数。
	 */
	public record Pace(long numerator, long denominator) {

		/** 表に出す額（円。100 円単位に切り上げ）。 */
		public long displayAmount() {
			return Math.multiplyExact(Math.ceilDiv(numerator, Math.multiplyExact(denominator, 100L)), 100L);
		}
	}

	/**
	 * ペース。使えないときは空：今日を含む季節で、過ぎた日数（今日を含む）が 14 日未満のとき、または季節がまだ始まっていないとき。
	 *
	 * @param actual   直前の季節の実績
	 * @param previous 直前の季節
	 * @param today    今日
	 * @throws ArithmeticException あふれたとき
	 */
	public static Optional<Pace> of(long actual, SeasonPeriod previous, LocalDate today) {
		return of(actual, previous, today, previous.startDate());
	}

	/**
	 * 数え始めの日を指定するペース（issue 020。設計書 5.14）。今日を含む季節の過ぎた日数 n を、countFrom から今日まで（今日を含む）で数える。
	 * D（季節の日数）は季節全体のまま。14 日の下限も、この n で見る。終わった季節は、実績そのまま（countFrom は関係しない）。
	 *
	 * @param countFrom 数え始めの日（{@link CountingStart#of} で決めた日）。季節の中にない日、今日より後の日は、季節の初日として扱う
	 */
	public static Optional<Pace> of(long actual, SeasonPeriod previous, LocalDate today, LocalDate countFrom) {
		LocalDate start = previous.startDate();
		if (today.isBefore(start)) {
			return Optional.empty();   // まだ始まっていない
		}
		if (!today.isBefore(previous.endDateExclusive())) {
			return Optional.of(new Pace(actual, 1));   // 終わった季節は、実績そのまま
		}
		LocalDate from = CountingStart.of(previous, countFrom, today);
		int elapsedDays = (int) ChronoUnit.DAYS.between(from, today) + 1;   // 数え始めの日から、今日を含む
		if (elapsedDays < MIN_ELAPSED_DAYS) {
			return Optional.empty();
		}
		long numerator = Math.multiplyExact(actual, (long) PeriodProgress.seasonDays(previous));
		return Optional.of(new Pace(numerator, elapsedDays));
	}

}
