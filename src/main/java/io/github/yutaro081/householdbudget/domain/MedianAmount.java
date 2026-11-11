package io.github.yutaro081.householdbudget.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * 記録のある年の実績から、もとの額（中央値）を求める（設計書 4.2、5.14、issue 019）。
 * 半円を出さないため、中央値の 2 倍のまま持つ。整数（long）だけで計算し、あふれたら ArithmeticException。
 */
public final class MedianAmount {

	private MedianAmount() {
	}

	/**
	 * 中央値の 2 倍。3 個＝真ん中の 2 倍、2 個＝合計、1 個＝その額の 2 倍、0 個＝空。
	 *
	 * @param amounts 記録のある年の実績（0〜3 個。順番は問わない。渡した List は書き換えない）
	 * @throws ArithmeticException      あふれたとき
	 * @throws IllegalArgumentException 4 個以上のとき
	 */
	public static OptionalLong twice(List<Long> amounts) {
		List<Long> sorted = new ArrayList<>(amounts);
		sorted.sort(null);
		return switch (sorted.size()) {
			case 0 -> OptionalLong.empty();
			case 1 -> OptionalLong.of(Math.multiplyExact(sorted.get(0), 2L));
			case 2 -> OptionalLong.of(Math.addExact(sorted.get(0), sorted.get(1)));
			case 3 -> OptionalLong.of(Math.multiplyExact(sorted.get(1), 2L));
			default -> throw new IllegalArgumentException("記録のある年は 3 つまで: " + sorted.size());
		};
	}

	/** 表に出す額 ＝ 中央値の 2 倍を 2 で割って、切り上げ。 */
	public static long display(long twice) {
		return Math.ceilDiv(twice, 2L);
	}

}
