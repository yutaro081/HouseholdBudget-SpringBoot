package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * もとの額（中央値）の単体テスト（設計書 5.14、issue 019）。Spring もデータベースも使わない。
 * 入力は「記録のある年」の実績だけ（0〜3 個）。記録のある年の実績 0 円は 0 円として数える（空とは別）。
 * 半円を出さないので、中央値の 2 倍のまま持つ（3 個＝真ん中の 2 倍、2 個＝合計、1 個＝その額の 2 倍、0 個＝空）。
 * 入力の List.of(...) は変更できないので、順番を変えて数える実装が元の List を書き換えないことも確かめている。
 */
class MedianAmountTest {

	// ---- 3 年分 ----

	@Test
	void threeYearsUseTheMiddleOne() {
		// 設計書 5.14 の食費：2023 冬 84,000、2024 冬 96,000、2025 冬 90,000 → 90,000
		assertThat(MedianAmount.twice(List.of(90_000L, 96_000L, 84_000L))).hasValue(180_000L);
		assertThat(MedianAmount.display(180_000L)).isEqualTo(90_000L);
	}

	@Test
	void theOrderOfTheInputDoesNotMatter() {
		assertThat(MedianAmount.twice(List.of(1L, 2L, 3L))).hasValue(4L);
		assertThat(MedianAmount.twice(List.of(3L, 2L, 1L))).hasValue(4L);
		assertThat(MedianAmount.twice(List.of(2L, 3L, 1L))).hasValue(4L);
		assertThat(MedianAmount.twice(List.of(2L, 1L, 3L))).hasValue(4L);
	}

	@Test
	void anOutlierIsDroppedByTheMedian() {
		// demo の雑費：2023 冬 12,000、2024 冬 118,000（家電）、2025 冬 12,700 → 12,700
		assertThat(MedianAmount.twice(List.of(12_000L, 118_000L, 12_700L))).hasValue(25_400L);
		assertThat(MedianAmount.display(25_400L)).isEqualTo(12_700L);
	}

	@Test
	void threeEqualAmountsGiveThatAmount() {
		assertThat(MedianAmount.twice(List.of(5L, 5L, 5L))).hasValue(10L);
	}

	@Test
	void twoEqualAmountsAndOneDifferent() {
		assertThat(MedianAmount.twice(List.of(7L, 7L, 100L))).hasValue(14L);
		assertThat(MedianAmount.twice(List.of(0L, 100L, 100L))).hasValue(200L);
	}

	// ---- 2 年分（平均）----

	@Test
	void twoYearsUseTheirAverage() {
		// 設計書 5.14：2024〜2025 年の冬の平均 21,500 円（2 年）
		assertThat(MedianAmount.twice(List.of(21_000L, 22_000L))).hasValue(43_000L);
		assertThat(MedianAmount.display(43_000L)).isEqualTo(21_500L);
	}

	@Test
	void twoYearsKeepTheHalfYenAsTheDoubledValue() {
		// 100 と 201 の平均は 150.5。半円は出さず、2 倍の 301 のまま持つ
		assertThat(MedianAmount.twice(List.of(201L, 100L))).hasValue(301L);
	}

	@Test
	void twoYearsHaveNoOutlierProtection() {
		// 2 年分では、飛び抜けた年を外せない（平均になる）
		assertThat(MedianAmount.twice(List.of(10_000L, 118_000L))).hasValue(128_000L);
	}

	// ---- 1 年分 ----

	@Test
	void oneYearUsesThatAmount() {
		// 設計書 5.14：去年の冬 22,050 円
		assertThat(MedianAmount.twice(List.of(22_050L))).hasValue(44_100L);
		assertThat(MedianAmount.display(44_100L)).isEqualTo(22_050L);
	}

	// ---- 0 年分 ----

	@Test
	void noYearsGiveNothing() {
		assertThat(MedianAmount.twice(List.of())).isEqualTo(OptionalLong.empty());
	}

	// ---- 実績 0 円（記録のある年の 0 円は、0 円として数える）----

	@Test
	void aYearWithRecordsButZeroSpendingCountsAsZeroNotAsMissing() {
		// 3 年のうち 1 年だけ使った科目：0、0、50,000 → 中央値 0（空ではない）
		assertThat(MedianAmount.twice(List.of(0L, 0L, 50_000L))).hasValue(0L);
		// 0、30,000、50,000 → 中央値 30,000（0 の年が下に引っぱる）
		assertThat(MedianAmount.twice(List.of(0L, 30_000L, 50_000L))).hasValue(60_000L);
	}

	@Test
	void zeroAloneIsZeroNotEmpty() {
		// 記録のある年が 1 つで、その科目の実績が 0 円
		assertThat(MedianAmount.twice(List.of(0L))).hasValue(0L);
	}

	@Test
	void allZeroYearsGiveZero() {
		assertThat(MedianAmount.twice(List.of(0L, 0L, 0L))).hasValue(0L);
		assertThat(MedianAmount.twice(List.of(0L, 0L))).hasValue(0L);
	}

	// ---- 表に出す額（2 倍を 2 で割って切り上げ）----

	@ParameterizedTest
	@CsvSource({
			"0, 0",
			"1, 1",       // 0.5 円 → 1 円
			"2, 1",
			"3, 2",       // 1.5 円 → 2 円
			"301, 151",   // 150.5 円 → 151 円
			"300, 150",
			"999999999999, 500000000000"
	})
	void displayRoundsHalfAYenUp(long twice, long expected) {
		assertThat(MedianAmount.display(twice)).isEqualTo(expected);
	}

	// ---- 4 個以上（記録のある年は 3 つまで）----

	@Test
	void fourAmountsAreRejected() {
		assertThatThrownBy(() -> MedianAmount.twice(List.of(1L, 2L, 3L, 4L)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ---- 桁あふれ ----

	@Test
	void doublingTheLargestAmountOverflowsAndThrows() {
		assertThatThrownBy(() -> MedianAmount.twice(List.of(Long.MAX_VALUE)))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void addingTwoLargeAmountsOverflowsAndThrows() {
		assertThatThrownBy(() -> MedianAmount.twice(List.of(Long.MAX_VALUE, Long.MAX_VALUE)))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void theLargestSafeAmountsStillWork() {
		// 2 倍して Long.MAX_VALUE を超えない最大
		long half = Long.MAX_VALUE / 2;
		assertThat(MedianAmount.twice(List.of(half))).hasValue(half * 2);
	}

}
