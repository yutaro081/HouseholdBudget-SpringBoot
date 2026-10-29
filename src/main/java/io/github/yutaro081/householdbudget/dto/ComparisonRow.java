package io.github.yutaro081.householdbudget.dto;

import io.github.yutaro081.householdbudget.domain.BudgetColor;

/**
 * 予実比較の1行分（設計書 4.7、Q7-4 の A）。科目の行にも、合計行にも使う。
 * <p>
 * 画面が読む値は、すべてこの中に入れて渡す（open-in-view=false なので、遅延読み込みに頼らない。実行計画書 ルール8）。
 *
 * @param categoryName 科目名。合計行では null（画面で「合計」を出す）
 * @param budget       予算（円）。その季節の予算が未設定なら null（0円とは別。0円は設定済み）。合計行では、設定済みの科目の予算の合計で、全科目が未設定なら null
 * @param actual       実績（円）。期間内の取引の合計。取引がなければ0。合計行では全科目の合計。INT を超えうるので long
 * @param difference   差額（円）＝ 予算 − 実績（超過するとマイナス）。予算が未設定（null）なら null（画面では空欄）
 * @param color        色。予算が未設定なら色なし（BudgetColorJudge）
 */
public record ComparisonRow(String categoryName, Long budget, long actual, Long difference, BudgetColor color) {
}
