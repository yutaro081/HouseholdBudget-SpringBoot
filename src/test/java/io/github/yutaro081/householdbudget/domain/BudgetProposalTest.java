package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yutaro081.householdbudget.domain.BudgetProposal.Result;
import io.github.yutaro081.householdbudget.domain.SpendingPace.Pace;

/**
 * 科目ごとの予算の案の単体テスト（設計書 5.14、issue 019）。Spring もデータベースも使わない。
 * ルール①〜④を上から順に当てはめる。
 * <ul>
 * <li>①中央値 ＞ 0 円 → 中央値 × 傾向 × 選んだ割合</li>
 * <li>②直前の実績 ＞ 0 円、かつペースが使える → ペース × 選んだ割合（傾向はかけない）</li>
 * <li>③直前の予算 ＞ 0 円 → 予算 × 選んだ割合（傾向はかけない）</li>
 * <li>④どれもない → 今の値のまま（割合もかけない）</li>
 * </ul>
 * どれも最後に 1 回だけ 1,000 円単位で切り上げ（途中で丸めない）、3,000,000 円を上限とする（④は今の値のまま）。
 * 期待値は、手と分数で計算した値。
 */
class BudgetProposalTest {

	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final LocalDate NOV_11_2026 = LocalDate.of(2026, 11, 11);

	/** 2026/11/11 の、秋 2026 のペース（D＝91、n＝72） */
	private static Optional<Pace> autumnPace(long actual) {
		return SpendingPace.of(actual, AUTUMN_2026, NOV_11_2026);
	}

	/** 2 倍の中央値（あり）、傾向、割合、直前の実績、ペース、直前の予算、今の値 */
	private static Result propose(OptionalLong twiceMedian, int trend, ProposalChoice choice, long previousActual,
			Optional<Pace> pace, Long previousBudget, long current) {
		return BudgetProposal.of(twiceMedian, trend, choice, previousActual, pace, previousBudget, current);
	}

	/** ルール①だけ使う形（中央値の 2 倍と、傾向、割合） */
	private static Result rule1(long twiceMedian, int trend, ProposalChoice choice) {
		return propose(OptionalLong.of(twiceMedian), trend, choice, 0, Optional.empty(), null, 0);
	}

	/** ルール③だけ使う形（直前の予算と、割合） */
	private static Result rule3(long budget, int trend, ProposalChoice choice) {
		return propose(OptionalLong.empty(), trend, choice, 0, Optional.empty(), budget, 0);
	}

	/** ルール②だけ使う形（ペースと、割合） */
	private static Result rule2(Pace pace, int trend, ProposalChoice choice) {
		return propose(OptionalLong.empty(), trend, choice, 1, Optional.of(pace), null, 0);
	}

	// ==== ルール①：中央値 × 傾向 × 割合 ====

	@ParameterizedTest
	@CsvSource({
			// 設計書 5.14 の例（傾向 110%）：食費 90,000、交通費 38,000、雑費 24,000
			"180000, 110, OK, 99000",
			"180000, 110, SAVE, 90000",    // 89,100
			"180000, 110, EXTRA, 109000",  // 108,900
			"76000, 110, OK, 42000",       // 41,800
			"76000, 110, SAVE, 38000",     // 37,620
			"76000, 110, EXTRA, 46000",    // 45,980
			"48000, 110, OK, 27000",       // 26,400
			"48000, 110, SAVE, 24000",     // 23,760
			"48000, 110, EXTRA, 30000",    // 29,040
			// 手順書 4.3 の demo（傾向 106%）：食費 43,300、交通費 6,700、雑費 12,700（家電が中央値で外れる）
			"86600, 106, OK, 46000",       // 45,898
			"86600, 106, SAVE, 42000",     // 41,308.2
			"86600, 106, EXTRA, 51000",    // 50,488.3
			"13400, 106, OK, 8000",        // 7,102
			"13400, 106, SAVE, 7000",      // 6,391.8
			"13400, 106, EXTRA, 8000",     // 7,812.2
			"25400, 106, OK, 14000",       // 13,462
			"25400, 106, SAVE, 13000",     // 12,115.8
			"25400, 106, EXTRA, 15000"     // 14,808.2
	})
	void rule1IsMedianTimesTrendTimesChoiceRoundedUpToThousand(long twiceMedian, int trend, ProposalChoice choice,
			long expected) {
		Result r = rule1(twiceMedian, trend, choice);

		assertThat(r.rule()).isEqualTo(1);
		assertThat(r.amount()).isEqualTo(expected);
	}

	@Test
	void rule1UsesTheDoubledMedianSoAHalfYenMedianStillWorks() {
		// 2 年分の平均 21,500.5 円（2 倍で 43,001）× 100% × 100% → 21,500.5 → 22,000
		assertThat(rule1(43_001, 100, ProposalChoice.OK).amount()).isEqualTo(22_000L);
		// ちょうど 21,500 円（43,000）→ 22,000（1,000 円単位で切り上げ）
		assertThat(rule1(43_000, 100, ProposalChoice.OK).amount()).isEqualTo(22_000L);
	}

	@Test
	void rule1IsUsedEvenWhenTheMedianIsOnlyHalfAYen() {
		// 中央値 0.5 円（2 倍で 1）は「0 円より大きい」ので①。切り上げて 1,000 円
		Result r = rule1(1, 100, ProposalChoice.OK);

		assertThat(r.rule()).isEqualTo(1);
		assertThat(r.amount()).isEqualTo(1_000L);
	}

	@Test
	void rule1AppliesTheTrendPercentAsGiven() {
		assertThat(rule1(200_000, 70, ProposalChoice.OK).amount()).isEqualTo(70_000L);
		assertThat(rule1(200_000, 100, ProposalChoice.OK).amount()).isEqualTo(100_000L);
		assertThat(rule1(200_000, 130, ProposalChoice.OK).amount()).isEqualTo(130_000L);
	}

	@Test
	void rule1TrendIsAppliedBeforeRoundingNotAfter() {
		// 中央値 1,000 円 × 101% ＝ 1,010 円 → 2,000 円（傾向を先に丸めて 1,000 円にすると 1,000 円になってしまう）
		assertThat(rule1(2_000, 101, ProposalChoice.OK).amount()).isEqualTo(2_000L);
	}

	// ---- 途中で丸めない（最後に 1 回だけ切り上げ）----

	@Test
	void nothingIsRoundedUpBeforeTheChoiceIsApplied() {
		// 中央値 10,500 円 × 110% ＝ 11,550 → 12,000。先に 11,000 に切り上げてから 110% をかけると 12,100 → 13,000 になる
		assertThat(rule1(21_000, 100, ProposalChoice.EXTRA).amount()).isEqualTo(12_000L);
		assertThat(rule3(10_500, 100, ProposalChoice.EXTRA).amount()).isEqualTo(12_000L);
	}

	// ---- 1,000 円の切り上げの境目 ----

	@ParameterizedTest
	@CsvSource({
			"1, 1000",
			"999, 1000",
			"1000, 1000",      // ちょうど：切り上げない
			"1001, 2000",      // 1 円超えたら次の 1,000 円
			"1999, 2000",
			"2000, 2000",
			"2001, 3000",
			"29999, 30000",
			"30000, 30000",
			"30001, 31000"
	})
	void roundingUpToThousandAtTheBoundaryWith100Percent(long budget, long expected) {
		assertThat(rule3(budget, 100, ProposalChoice.OK).amount()).isEqualTo(expected);
	}

	@Test
	void roundingUpToThousandAtTheBoundaryWith110Percent() {
		// 909 × 1.1 ＝ 999.9 → 1,000。910 × 1.1 ＝ 1,001 → 2,000
		assertThat(rule3(909, 100, ProposalChoice.EXTRA).amount()).isEqualTo(1_000L);
		assertThat(rule3(910, 100, ProposalChoice.EXTRA).amount()).isEqualTo(2_000L);
		// 1,000 × 1.1 ＝ 1,100 → 2,000。1,000 の 100% は 1,000 のまま
		assertThat(rule3(1_000, 100, ProposalChoice.EXTRA).amount()).isEqualTo(2_000L);
	}

	@Test
	void aSmallCategoryDoesNotGetSmallerWhenSaving() {
		// 1,000 円の科目の 90% ＝ 900 円 → 切り上げで 1,000 円（節約が効かない）
		assertThat(rule3(1_000, 100, ProposalChoice.SAVE).amount()).isEqualTo(1_000L);
		assertThat(rule1(2_000, 100, ProposalChoice.SAVE).amount()).isEqualTo(1_000L);
		assertThat(rule3(1, 100, ProposalChoice.SAVE).amount()).isEqualTo(1_000L);
	}

	// ---- 上限 3,000,000 円 ----

	@Test
	void theLimitIs3Million() {
		assertThat(BudgetProposal.LIMIT).isEqualTo(3_000_000L);
	}

	@Test
	void exactly3MillionIsKept() {
		assertThat(rule3(3_000_000, 100, ProposalChoice.OK).amount()).isEqualTo(3_000_000L);
		// 中央値 3,000,000 円（2 倍で 6,000,000）
		assertThat(rule1(6_000_000, 100, ProposalChoice.OK).amount()).isEqualTo(3_000_000L);
	}

	@Test
	void justBelowTheLimitIsKept() {
		assertThat(rule3(2_999_000, 100, ProposalChoice.OK).amount()).isEqualTo(2_999_000L);
		// 中央値 2,000,000 × 130% × 110% ＝ 2,860,000
		assertThat(rule1(4_000_000, 130, ProposalChoice.EXTRA).amount()).isEqualTo(2_860_000L);
	}

	@Test
	void justOverTheLimitIsCappedTo3Million() {
		// 3,000,001 円 → 切り上げで 3,001,000 → 3,000,000 に止める
		assertThat(rule3(3_000_001, 100, ProposalChoice.OK).amount()).isEqualTo(3_000_000L);
		// 中央値 3,000,001 円（2 倍で 6,000,002）
		assertThat(rule1(6_000_002, 100, ProposalChoice.OK).amount()).isEqualTo(3_000_000L);
		// 中央値 2,100,000 × 130% × 110% ＝ 3,003,000
		assertThat(rule1(4_200_000, 130, ProposalChoice.EXTRA).amount()).isEqualTo(3_000_000L);
	}

	@Test
	void theLimitAppliesAfterTheChoice() {
		// 3,000,000 円の 110% ＝ 3,300,000 → 3,000,000。90% ＝ 2,700,000 はそのまま
		assertThat(rule3(3_000_000, 100, ProposalChoice.EXTRA).amount()).isEqualTo(3_000_000L);
		assertThat(rule3(3_000_000, 100, ProposalChoice.SAVE).amount()).isEqualTo(2_700_000L);
	}

	@Test
	void theLimitAppliesJustAroundTheRoundingStep() {
		// 2,727,272 × 1.1 ＝ 2,999,999.2 → 切り上げ 3,000,000（上限ちょうど）
		assertThat(rule3(2_727_272, 100, ProposalChoice.EXTRA).amount()).isEqualTo(3_000_000L);
		// 2,727,273 × 1.1 ＝ 3,000,000.3 → 3,001,000 → 3,000,000 に止める
		assertThat(rule3(2_727_273, 100, ProposalChoice.EXTRA).amount()).isEqualTo(3_000_000L);
	}

	@Test
	void theLimitAppliesToRule2AndRule3Too() {
		assertThat(rule2(new Pace(10_000_000, 1), 100, ProposalChoice.OK).amount()).isEqualTo(3_000_000L);
		assertThat(rule3(1_000_000_000_000L, 100, ProposalChoice.SAVE).amount()).isEqualTo(3_000_000L);
	}

	// ==== ルール②：ペース × 割合（傾向はかけない）====

	@Test
	void rule2DesignExampleForMedicalCosts() {
		// 医療費：3,600 × 91 ÷ 72 ＝ 4,550。100% → 5,000、90% → 4,095 → 5,000、110% → 5,005 → 6,000
		Optional<Pace> pace = autumnPace(3_600);

		Result ok = propose(OptionalLong.of(0), 110, ProposalChoice.OK, 3_600, pace, null, 0);
		Result save = propose(OptionalLong.of(0), 110, ProposalChoice.SAVE, 3_600, pace, null, 0);
		Result extra = propose(OptionalLong.of(0), 110, ProposalChoice.EXTRA, 3_600, pace, null, 0);

		assertThat(ok.rule()).isEqualTo(2);
		assertThat(ok.amount()).isEqualTo(5_000L);
		assertThat(save.rule()).isEqualTo(2);
		assertThat(save.amount()).isEqualTo(5_000L);
		assertThat(extra.rule()).isEqualTo(2);
		assertThat(extra.amount()).isEqualTo(6_000L);
	}

	@Test
	void rule2DemoExampleAbout400Becomes1000() {
		// 手順書 4.3 の医療費：実績 300 → ペース 379.17 → 切り上げで 1,000（3 つとも）
		Optional<Pace> pace = autumnPace(300);

		for (ProposalChoice choice : ProposalChoice.values()) {
			Result r = propose(OptionalLong.empty(), 106, choice, 300, pace, null, 0);

			assertThat(r.rule()).isEqualTo(2);
			assertThat(r.amount()).isEqualTo(1_000L);
		}
	}

	@Test
	void rule2OfAFinishedSeasonUsesTheActual() {
		// 終わった季節：15,200 円 → 100% は 16,000、90% は 13,680 → 14,000、110% は 16,720 → 17,000
		Pace finished = new Pace(15_200, 1);

		assertThat(rule2(finished, 100, ProposalChoice.OK).amount()).isEqualTo(16_000L);
		assertThat(rule2(finished, 100, ProposalChoice.SAVE).amount()).isEqualTo(14_000L);
		assertThat(rule2(finished, 100, ProposalChoice.EXTRA).amount()).isEqualTo(17_000L);
	}

	@Test
	void rule2IsCalculatedFromTheExactPaceNotFromTheRoundedDisplayAmount() {
		// ペース 9,090.1 円（表は 9,100 円）。110% を実績から直接 → 9,999.11 → 10,000。
		// 表の 9,100 円から計算すると 10,010 → 11,000 になってしまう（Q019-2）
		Pace pace = new Pace(90_901, 10);

		assertThat(pace.displayAmount()).isEqualTo(9_100L);
		assertThat(rule2(pace, 100, ProposalChoice.EXTRA).amount()).isEqualTo(10_000L);
	}

	@Test
	void rule2DoesNotApplyTheTrend() {
		Optional<Pace> pace = autumnPace(3_600);

		Result trend70 = propose(OptionalLong.empty(), 70, ProposalChoice.EXTRA, 3_600, pace, null, 0);
		Result trend130 = propose(OptionalLong.empty(), 130, ProposalChoice.EXTRA, 3_600, pace, null, 0);

		assertThat(trend70.amount()).isEqualTo(6_000L);
		assertThat(trend130.amount()).isEqualTo(6_000L);
	}

	@Test
	void rule2IgnoresTheBudget() {
		// 直前の予算が未設定・0 円・大きい額でも、②が先
		Optional<Pace> pace = autumnPace(3_600);

		assertThat(propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, pace, null, 0).rule()).isEqualTo(2);
		assertThat(propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, pace, 0L, 0).rule()).isEqualTo(2);
		assertThat(propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, pace, 90_000L, 0).rule()).isEqualTo(2);
	}

	// ==== ルール③：直前の予算 × 割合（傾向はかけない）====

	@Test
	void rule3DesignExampleForDailyGoods() {
		// 日用品：直前の予算 8,000 円。100% → 8,000、90% は 7,200 → 8,000、110% は 8,800 → 9,000
		Result ok = propose(OptionalLong.of(0), 110, ProposalChoice.OK, 0, autumnPace(0), 8_000L, 0);
		Result save = propose(OptionalLong.of(0), 110, ProposalChoice.SAVE, 0, autumnPace(0), 8_000L, 0);
		Result extra = propose(OptionalLong.of(0), 110, ProposalChoice.EXTRA, 0, autumnPace(0), 8_000L, 0);

		assertThat(ok.rule()).isEqualTo(3);
		assertThat(ok.amount()).isEqualTo(8_000L);
		assertThat(save.rule()).isEqualTo(3);
		assertThat(save.amount()).isEqualTo(8_000L);
		assertThat(extra.rule()).isEqualTo(3);
		assertThat(extra.amount()).isEqualTo(9_000L);
	}

	@Test
	void rule3DoesNotApplyTheTrend() {
		assertThat(rule3(8_000, 70, ProposalChoice.OK).amount()).isEqualTo(8_000L);
		assertThat(rule3(8_000, 130, ProposalChoice.OK).amount()).isEqualTo(8_000L);
		assertThat(rule3(8_000, 130, ProposalChoice.EXTRA).amount()).isEqualTo(9_000L);
	}

	@Test
	void rule3IsUsedWhenTheActualIsZeroEvenIfThePaceIsAvailable() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 0, autumnPace(0), 30_000L, 0);

		assertThat(r.rule()).isEqualTo(3);
		assertThat(r.amount()).isEqualTo(30_000L);
	}

	@Test
	void rule3IsUsedWhenThePaceCannotBeUsedYet() {
		// 実績はあるが、季節が始まって 14 日たっていない（ペースが空）
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 5_000, Optional.empty(), 30_000L, 0);

		assertThat(r.rule()).isEqualTo(3);
		assertThat(r.amount()).isEqualTo(30_000L);
	}

	// ==== ルール④：今の値のまま ====

	@Test
	void rule4KeepsTheCurrentValueForEveryChoice() {
		// 娯楽：直前の予算が未設定で、実績 0 円。冬 2026 に保存済みの 5,000 円のまま
		for (ProposalChoice choice : ProposalChoice.values()) {
			Result r = propose(OptionalLong.of(0), 110, choice, 0, autumnPace(0), null, 5_000);

			assertThat(r.rule()).isEqualTo(4);
			assertThat(r.amount()).isEqualTo(5_000L);
		}
	}

	@Test
	void rule4KeepsAnUnsavedValueOfZero() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.EXTRA, 0, Optional.empty(), null, 0);

		assertThat(r.rule()).isEqualTo(4);
		assertThat(r.amount()).isZero();
	}

	@Test
	void rule4DoesNotApplyTheChoiceOrTheTrendOrRounding() {
		// 今の値 1,234 円は、切り上げも割合もかけない
		Result r = propose(OptionalLong.empty(), 130, ProposalChoice.EXTRA, 0, Optional.empty(), null, 1_234);

		assertThat(r.amount()).isEqualTo(1_234L);
	}

	@Test
	void aBudgetOfZeroYenIsNotAMaterialSoItDoesNotOverwriteASavedValueWithZero() {
		// Q019-1：直前の予算が 0 円（設定済み）。③にすると、画面の季節に保存済みの 5,000 円を 0 円で上書きしてしまう
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 0, autumnPace(0), 0L, 5_000);

		assertThat(r.rule()).isEqualTo(4);
		assertThat(r.amount()).isEqualTo(5_000L);
	}

	@Test
	void aBudgetOfZeroYenWithNothingSavedStaysZero() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 0, Optional.empty(), 0L, 0);

		assertThat(r.rule()).isEqualTo(4);
		assertThat(r.amount()).isZero();
	}

	// ==== 上から順に（①→②→③→④）====

	@Test
	void rule1BeatsEverythingElse() {
		// 中央値 90,000、実績 3,600（ペースあり）、予算 8,000、今の値 5,000 がすべてあっても①
		Result r = propose(OptionalLong.of(180_000), 110, ProposalChoice.OK, 3_600, autumnPace(3_600), 8_000L, 5_000);

		assertThat(r.rule()).isEqualTo(1);
		assertThat(r.amount()).isEqualTo(99_000L);
	}

	@Test
	void aMedianOfZeroFallsThroughToRule2() {
		// 記録のある年の実績がすべて 0 円（中央値 0 円）。直前の実績があれば②
		Result r = propose(OptionalLong.of(0), 110, ProposalChoice.OK, 3_600, autumnPace(3_600), 8_000L, 5_000);

		assertThat(r.rule()).isEqualTo(2);
	}

	@Test
	void noMedianAtAllFallsThroughToRule2() {
		// 記録のある年が 1 つもない
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, autumnPace(3_600), 8_000L, 5_000);

		assertThat(r.rule()).isEqualTo(2);
	}

	@Test
	void rule2BeatsRule3() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, autumnPace(3_600), 8_000L, 5_000);

		assertThat(r.rule()).isEqualTo(2);
		assertThat(r.amount()).isEqualTo(5_000L);   // ペース 4,550 → 5,000。予算 8,000 ではない
	}

	@Test
	void anActualWithoutAUsablePaceFallsThroughToRule3() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, Optional.empty(), 8_000L, 5_000);

		assertThat(r.rule()).isEqualTo(3);
		assertThat(r.amount()).isEqualTo(8_000L);
	}

	@Test
	void anActualWithoutAUsablePaceAndWithoutABudgetFallsThroughToRule4() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 3_600, Optional.empty(), null, 5_000);

		assertThat(r.rule()).isEqualTo(4);
		assertThat(r.amount()).isEqualTo(5_000L);
	}

	@Test
	void aZeroActualFallsThroughRule2EvenWithAPace() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 0, autumnPace(0), null, 5_000);

		assertThat(r.rule()).isEqualTo(4);
	}

	@Test
	void anActualOfOneYenIsEnoughForRule2() {
		Result r = propose(OptionalLong.empty(), 100, ProposalChoice.OK, 1, autumnPace(1), null, 0);

		assertThat(r.rule()).isEqualTo(2);
		assertThat(r.amount()).isEqualTo(1_000L);
	}

	// ==== 選び直すたびに、もとから計算する ====

	@Test
	void eachChoiceIsCalculatedFromTheOriginalNotFromThePreviousProposal() {
		// 節約を 2 回押しても、90% × 90% ＝ 81% にはならない（同じ入力なら、何回呼んでも同じ）
		Result first = rule1(200_000, 100, ProposalChoice.SAVE);
		Result second = rule1(200_000, 100, ProposalChoice.SAVE);

		assertThat(first.amount()).isEqualTo(90_000L);
		assertThat(second.amount()).isEqualTo(90_000L);
	}

	// ==== 桁あふれ ====

	@Test
	void overflowInRule1Throws() {
		assertThatThrownBy(() -> rule1(Long.MAX_VALUE, 130, ProposalChoice.EXTRA)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void overflowInRule2Throws() {
		assertThatThrownBy(() -> rule2(new Pace(Long.MAX_VALUE, 1), 100, ProposalChoice.EXTRA))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void overflowInRule3Throws() {
		assertThatThrownBy(() -> rule3(Long.MAX_VALUE / 2, 100, ProposalChoice.EXTRA))
				.isInstanceOf(ArithmeticException.class);
	}

	// ==== 設計書 5.14 の「計算の合った例」（2026/11/11 に 2026 年 冬を開く）====

	/** 科目 1 つ分の材料（過去の冬は、2023・2024・2025 年） */
	private record Row(String name, List<Long> pastWinters, long previousActual, Long previousBudget, long current) {
	}

	private static final List<Row> DESIGN_ROWS = List.of(
			new Row("食費", List.of(84_000L, 96_000L, 90_000L), 49_200, 95_000L, 0),
			new Row("交通費", List.of(41_000L, 38_000L, 33_000L), 5_600, 40_000L, 0),
			new Row("雑費", List.of(30_000L, 24_000L, 18_000L), 22_000, 30_000L, 0),
			new Row("医療費", List.of(0L, 0L, 0L), 3_600, null, 0),
			new Row("日用品", List.of(0L, 0L, 0L), 0, 8_000L, 0),
			new Row("娯楽", List.of(0L, 0L, 0L), 0, null, 5_000));

	private static Result designProposal(Row row, SpendingTrend.Result trend, SeasonPeriod previous, ProposalChoice choice) {
		return BudgetProposal.of(
				MedianAmount.twice(row.pastWinters()),
				trend.percent(),
				choice,
				row.previousActual(),
				SpendingPace.of(row.previousActual(), previous, NOV_11_2026),
				row.previousBudget(),
				row.current());
	}

	private static long designTotal(ProposalChoice choice) {
		AutoBudgetSeasons seasons = AutoBudgetSeasons.of(new SeasonPeriod(2026, Season.WINTER), NOV_11_2026);
		SpendingTrend.Result trend = SpendingTrend.of(true, 570_000, 600_000, 630_000);
		SeasonPeriod previous = seasons.previousSeason().orElseThrow();
		long total = 0;
		for (Row row : DESIGN_ROWS) {
			total += designProposal(row, trend, previous, choice).amount();
		}
		return total;
	}

	@Test
	void designExampleTrendIs110Percent() {
		SpendingTrend.Result trend = SpendingTrend.of(true, 570_000, 600_000, 630_000);

		assertThat(trend.percent()).isEqualTo(110);
	}

	@Test
	void designExampleRulesAndAmountsForOk() {
		AutoBudgetSeasons seasons = AutoBudgetSeasons.of(new SeasonPeriod(2026, Season.WINTER), NOV_11_2026);
		SpendingTrend.Result trend = SpendingTrend.of(true, 570_000, 600_000, 630_000);
		SeasonPeriod previous = seasons.previousSeason().orElseThrow();

		List<Result> results = DESIGN_ROWS.stream()
				.map(row -> designProposal(row, trend, previous, ProposalChoice.OK))
				.toList();

		assertThat(previous).isEqualTo(AUTUMN_2026);
		assertThat(results).extracting(Result::rule).containsExactly(1, 1, 1, 2, 3, 4);
		assertThat(results).extracting(Result::amount)
				.containsExactly(99_000L, 42_000L, 27_000L, 5_000L, 8_000L, 5_000L);
	}

	@Test
	void designExampleTotalsAre186000And170000And205000() {
		assertThat(designTotal(ProposalChoice.OK)).isEqualTo(186_000L);
		assertThat(designTotal(ProposalChoice.SAVE)).isEqualTo(170_000L);
		assertThat(designTotal(ProposalChoice.EXTRA)).isEqualTo(205_000L);
	}

	@Test
	void designExamplePaceTableAndItsTotalRowDifferBy100Yen() {
		// 表の「このペースなら」：食費 62,200、交通費 7,100、雑費 27,900、医療費 4,600。合計の行は 101,700（科目の足し算は 101,800）
		long food = autumnPace(49_200).orElseThrow().displayAmount();
		long transport = autumnPace(5_600).orElseThrow().displayAmount();
		long sundries = autumnPace(22_000).orElseThrow().displayAmount();
		long medical = autumnPace(3_600).orElseThrow().displayAmount();
		long totalRow = autumnPace(49_200 + 5_600 + 22_000 + 3_600).orElseThrow().displayAmount();

		assertThat(food).isEqualTo(62_200L);
		assertThat(transport).isEqualTo(7_100L);
		assertThat(sundries).isEqualTo(27_900L);
		assertThat(medical).isEqualTo(4_600L);
		assertThat(food + transport + sundries + medical).isEqualTo(101_800L);
		assertThat(totalRow).isEqualTo(101_700L);
	}

}
