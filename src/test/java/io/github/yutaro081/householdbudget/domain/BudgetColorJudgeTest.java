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

	/** 手順書 4.9 の画面の確認：食費 予算20,000円・実績1,500円、合計 予算75,000円・実績1,500円 → どちらも青 */
	@Test
	void browserCheckValues() {
		assertThat(BudgetColorJudge.judge(20_000L, 1_500L)).isEqualTo(BudgetColor.BLUE);
		assertThat(BudgetColorJudge.judge(75_000L, 1_500L)).isEqualTo(BudgetColor.BLUE);
	}

}
