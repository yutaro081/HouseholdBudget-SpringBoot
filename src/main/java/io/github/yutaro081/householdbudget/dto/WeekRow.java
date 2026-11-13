package io.github.yutaro081.householdbudget.dto;

import io.github.yutaro081.householdbudget.domain.BudgetColor;
import io.github.yutaro081.householdbudget.domain.MeterScale;

/**
 * トップの「今週の科目ごとのメーター」の1行分（設計書 4.7、5.11）。科目の行にも、合計の行にも使う。
 * 画面が読む値は、すべてこの中に入れて渡す（open-in-view=false なので、遅延読み込みに頼らない）。
 *
 * @param categoryName 科目名。合計の行では null（画面で「合計」を出す）
 * @param budget       週の予算（円。100円単位）。今の季節の予算が未設定なら null。合計の行では、設定済みの科目の週の予算の合計で、全部未設定なら null
 * @param actual       今週の実績（円。科目ごとに100円単位で切り上げた額。合計の行は、その和）
 * @param difference   差額（円）＝ 週の予算 − 今週の実績。週の予算が未設定なら null
 * @param color        色。予実比較と同じ判定（BudgetColorJudge）を、週の予算と切り上げた実績に当てはめたもの
 * @param withinBudget 予算内か（BudgetColorJudge.isWithinBudget）。画面は、色と合わせて、状態のチップ（予算超え・使い残し・予算内）を出す（issue 024。以前の 💮 を置きかえた）
 * @param meter        メーターの値（棒、予算の線、「今日」の線、「+○%」。縮尺は MeterScale。issue 020）。週の予算が未設定なら、画面は棒を出さない
 * @param overAmount   予算超えの金額（円）＝ 実績 − 週の予算。「予算超え ＋○円」のチップに出す。予算超え（赤）のときだけ。ほかは null
 */
public record WeekRow(String categoryName, Long budget, long actual, Long difference, BudgetColor color,
		boolean withinBudget, MeterScale.Meter meter, Long overAmount) {
}
