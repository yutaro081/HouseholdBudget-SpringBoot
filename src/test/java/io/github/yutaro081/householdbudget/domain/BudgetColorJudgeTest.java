package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 色の判定の単体テスト（設計書 5.9、要件定義書 4.4）。Spring もデータベースも使わない。
 * 予算が未設定なら色なし。設定されていれば、①実績 ＞ 予算 → 赤、②予算 ＝ 0円 → 色なし、
 * ③実績 × 3 ≦ 予算 → 青、④それ以外 → 色なし、の順に判定する。
 */
class BudgetColorJudgeTest {

	/** 設計書 5.9 の表の9行（予算が未設定の2行は、下の unsetBudget でも確かめる） */
	@ParameterizedTest(name = "予算 {0} ・実績 {1} → {2}")
	@CsvSource({
			"30000, 30001, RED",
			"30000, 30000, NONE",
			"30000, 10000, BLUE",
			"30000, 10001, NONE",
			"30000, 0,     BLUE",
			"0,     1,     RED",
			"0,     0,     NONE"
	})
	void designTable(long budget, long actual, BudgetColor expected) {
		assertThat(BudgetColorJudge.judge(budget, actual)).isEqualTo(expected);
	}

	/** 設計書 5.9 の表の残り2行：予算が未設定 → 実績が5,000円でも0円でも色なし */
	@ParameterizedTest(name = "未設定・実績 {0} → 色なし")
	@ValueSource(longs = { 5000, 0 })
	void designTableUnsetBudget(long actual) {
		assertThat(BudgetColorJudge.judge(null, actual)).isEqualTo(BudgetColor.NONE);
	}

	/** 超過の境界：予算30,000円で、29,999 は超過ではない・30,000 は超過ではない・30,001 から赤 */
	@ParameterizedTest(name = "予算 30000 ・実績 {0} → {1}")
	@CsvSource({
			"29999, NONE",
			"30000, NONE",
			"30001, RED"
	})
	void overBoundary(long actual, BudgetColor expected) {
		assertThat(BudgetColorJudge.judge(30_000L, actual)).isEqualTo(expected);
	}

	/** ちょうど1/3の前後：9,999 と 10,000 は青、10,001 は色なし */
	@ParameterizedTest(name = "予算 30000 ・実績 {0} → {1}")
	@CsvSource({
			"9999,  BLUE",
			"10000, BLUE",
			"10001, NONE"
	})
	void oneThirdBoundary(long actual, BudgetColor expected) {
		assertThat(BudgetColorJudge.judge(30_000L, actual)).isEqualTo(expected);
	}

	/** 予算0円：実績0円は色なし（青にならない）、1円は赤 */
	@Test
	void zeroBudget() {
		assertThat(BudgetColorJudge.judge(0L, 0)).isEqualTo(BudgetColor.NONE);
		assertThat(BudgetColorJudge.judge(0L, 1)).isEqualTo(BudgetColor.RED);
	}

	/** 予算1円：実績0円は青（0 ≦ 1）、1円は色なし（超過ではなく、3 ＞ 1）、2円は赤 */
	@ParameterizedTest(name = "予算 1 ・実績 {0} → {1}")
	@CsvSource({
			"0, BLUE",
			"1, NONE",
			"2, RED"
	})
	void budgetOfOneYen(long actual, BudgetColor expected) {
		assertThat(BudgetColorJudge.judge(1L, actual)).isEqualTo(expected);
	}

	/** 予算の上限3,000,000円：実績1,000,000円はちょうど1/3で青、1,000,001円は色なし、3,000,001円は赤 */
	@ParameterizedTest(name = "予算 3000000 ・実績 {0} → {1}")
	@CsvSource({
			"0,       BLUE",
			"1000000, BLUE",
			"1000001, NONE",
			"3000000, NONE",
			"3000001, RED"
	})
	void maximumBudget(long actual, BudgetColor expected) {
		assertThat(BudgetColorJudge.judge(3_000_000L, actual)).isEqualTo(expected);
	}

	/** 予算が未設定なら、実績が0円でも、大きくても、色なし */
	@ParameterizedTest(name = "未設定・実績 {0} → 色なし")
	@ValueSource(longs = { 0, 1, 5000, 3_000_000, 3_000_000_000L, Long.MAX_VALUE / 4 })
	void unsetBudget(long actual) {
		assertThat(BudgetColorJudge.judge(null, actual)).isEqualTo(BudgetColor.NONE);
	}

	/**
	 * INT（約21億）を超える合計でも、あふれない（合計行。設計書 3.1、5.9）。
	 * 「実績 × 3」は、実績が1,000,000,001 のとき 3,000,000,003 で、INT で計算すると負の数にあふれて、青と誤る。
	 */
	@ParameterizedTest(name = "予算 {0} ・実績 {1} → {2}")
	@CsvSource({
			"3000000000, 1000000000, BLUE",
			"3000000000, 1000000001, NONE",
			"3000000000, 3000000000, NONE",
			"3000000000, 3000000001, RED",
			"2000000000, 3000000000, RED",
			"9000000000, 3000000000, BLUE",
			"9000000000, 3000000001, NONE"
	})
	void totalsBeyondIntRange(long budget, long actual, BudgetColor expected) {
		assertThat(BudgetColorJudge.judge(budget, actual)).isEqualTo(expected);
	}

	/** 実行計画書の完了条件：合計の予算30,000円・合計の実績30,001円 → 赤（合計行も同じ判定） */
	@Test
	void totalRowOver() {
		assertThat(BudgetColorJudge.judge(30_000L, 30_001L)).isEqualTo(BudgetColor.RED);
	}

	/** Phase 7 の手順書 4.9 の画面の確認：食費 予算20,000円・実績1,500円、合計 予算75,000円・実績1,500円 → どちらも青 */
	@Test
	void browserCheckValues() {
		assertThat(BudgetColorJudge.judge(20_000L, 1_500L)).isEqualTo(BudgetColor.BLUE);
		assertThat(BudgetColorJudge.judge(75_000L, 1_500L)).isEqualTo(BudgetColor.BLUE);
	}

	// ================================================================
	// 予算内の印（💮。Phase 8 の issue 005、設計書 5.9、Q8-27〜Q8-33）
	// 色の判定で④（それ以外は色なし）に当たり、かつ予算が設定されていて、予算 ＞ 0、実績 ＞ 0 の行に付ける。
	// 式にすると：予算が設定されていて、実績 ≦ 予算 かつ 実績 × 3 ＞ 予算。
	// ================================================================

	/** 設計書 5.9 の表の、💮 の列（予算が未設定の行は、下の unsetBudgetIsNeverWithin で確かめる） */
	@ParameterizedTest(name = "予算 {0} ・実績 {1} → 💮 {2}")
	@CsvSource({
			"30000, 20000, true",
			"30000, 30000, true",
			"30000, 10001, true",
			"30000, 10000, false",
			"30000, 0,     false",
			"30000, 30001, false",
			"0,     1,     false",
			"0,     0,     false"
	})
	void withinBudgetDesignTable(long budget, long actual, boolean expected) {
		assertThat(BudgetColorJudge.isWithinBudget(budget, actual)).isEqualTo(expected);
	}

	/** 予算が未設定（null）なら、実績がいくつでも付けない */
	@ParameterizedTest(name = "未設定・実績 {0} → 💮 なし")
	@ValueSource(longs = { 0, 1, 5000, 3_000_000, 3_000_000_000L, Long.MAX_VALUE / 4 })
	void unsetBudgetIsNeverWithin(long actual) {
		assertThat(BudgetColorJudge.isWithinBudget(null, actual)).isFalse();
	}

	/** 予算ちょうど（実績 ＝ 予算）は付ける（Q8-29）。1円足りない・1円多い も確かめる */
	@ParameterizedTest(name = "予算 30000 ・実績 {0} → 💮 {1}")
	@CsvSource({
			"29999, true",
			"30000, true",
			"30001, false"
	})
	void upperBoundaryIsTheBudgetItself(long actual, boolean expected) {
		assertThat(BudgetColorJudge.isWithinBudget(30_000L, actual)).isEqualTo(expected);
	}

	/** 下の境界は、青との境目（ちょうど1/3は青で付けない。1円多いと付ける） */
	@ParameterizedTest(name = "予算 30000 ・実績 {0} → 💮 {1}")
	@CsvSource({
			"9999,  false",
			"10000, false",
			"10001, true"
	})
	void lowerBoundaryIsTheOneThirdOfTheBudget(long actual, boolean expected) {
		assertThat(BudgetColorJudge.isWithinBudget(30_000L, actual)).isEqualTo(expected);
	}

	/** 予算1円：実績0円は青、1円は付ける（1 × 3 ＞ 1 かつ 1 ≦ 1）、2円は赤 */
	@ParameterizedTest(name = "予算 1 ・実績 {0} → 💮 {1}")
	@CsvSource({
			"0, false",
			"1, true",
			"2, false"
	})
	void budgetOfOneYen(long actual, boolean expected) {
		assertThat(BudgetColorJudge.isWithinBudget(1L, actual)).isEqualTo(expected);
	}

	/** 予算が0円なら、実績が0円でも1円でも付けない（実績0円は色なし②、実績があれば赤） */
	@ParameterizedTest(name = "予算 0 ・実績 {0} → 💮 なし")
	@ValueSource(longs = { 0, 1, 1000 })
	void zeroBudgetIsNeverWithin(long actual) {
		assertThat(BudgetColorJudge.isWithinBudget(0L, actual)).isFalse();
	}

	/** 予算の上限3,000,000円 */
	@ParameterizedTest(name = "予算 3000000 ・実績 {0} → 💮 {1}")
	@CsvSource({
			"0,       false",
			"1000000, false",
			"1000001, true",
			"3000000, true",
			"3000001, false"
	})
	void maximumBudgetForWithin(long actual, boolean expected) {
		assertThat(BudgetColorJudge.isWithinBudget(3_000_000L, actual)).isEqualTo(expected);
	}

	/**
	 * INT（約21億）を超える合計でも、あふれない（合計行。設計書 3.1）。
	 * 「実績 × 3」を INT で計算すると、実績が1,000,000,001 のとき負の数にあふれて、付けるべき行を落とす。
	 */
	@ParameterizedTest(name = "予算 {0} ・実績 {1} → 💮 {2}")
	@CsvSource({
			"3000000000, 1000000000, false",
			"3000000000, 1000000001, true",
			"3000000000, 3000000000, true",
			"3000000000, 3000000001, false",
			"9000000000, 3000000000, false",
			"9000000000, 3000000001, true"
	})
	void totalsBeyondIntRangeForWithin(long budget, long actual, boolean expected) {
		assertThat(BudgetColorJudge.isWithinBudget(budget, actual)).isEqualTo(expected);
	}

	/**
	 * 色の判定との整合：💮 が付くのは、色が「色なし」で、予算が設定されていて予算 ＞ 0、実績 ＞ 0 の行だけ（赤・青には付かない）。
	 * 予算と実績の組み合わせを細かく並べて、すべてで確かめる（片方だけ直したときに食い違いに気づくため）。
	 */
	@Test
	void withinBudgetIsExactlyTheNoColorRowsWithABudgetAndAnActual() {
		long[] values = { 0, 1, 2, 3, 4, 5, 9, 10, 11, 29, 30, 31, 99, 100, 101, 299, 300, 301, 333, 334, 1000 };
		for (long budget : values) {
			for (long actual : values) {
				boolean expected = BudgetColorJudge.judge(budget, actual) == BudgetColor.NONE && budget > 0 && actual > 0;
				assertThat(BudgetColorJudge.isWithinBudget(budget, actual))
						.as("予算 %d ・実績 %d（色は %s）", budget, actual, BudgetColorJudge.judge(budget, actual))
						.isEqualTo(expected);
			}
		}
		for (long actual : values) {
			assertThat(BudgetColorJudge.isWithinBudget(null, actual)).as("未設定・実績 %d", actual).isFalse();
		}
	}

	/** Phase 7 の手順書 4.9 の画面の確認の値：食費 予算20,000円・実績1,500円は青なので付けない。予算30,000円・実績20,000円は付ける */
	@Test
	void browserCheckValuesForWithin() {
		assertThat(BudgetColorJudge.isWithinBudget(20_000L, 1_500L)).isFalse();
		assertThat(BudgetColorJudge.isWithinBudget(30_000L, 20_000L)).isTrue();
	}

}
