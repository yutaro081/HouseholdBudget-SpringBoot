package io.github.yutaro081.householdbudget.dto;

import java.util.List;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;

/**
 * 予実比較の画面全体の表示データ（設計書 4.7、Q7-4 の A）。
 * <p>
 * 画面が読む値は、すべてこの中に入れて渡す（open-in-view=false。実行計画書 ルール8）。
 * 期間の開始日・終了日の翌日は、period から求められる（SeasonPeriod.startDate など）。
 *
 * @param period   表示する季節（年と季節）
 * @param previous 前の季節。範囲の端（2000年春）では null（リンクを出さない、設計書 2.4、B15）
 * @param next     次の季節。範囲の端（2100年冬）では null
 * @param rows     科目ごとの行（科目の登録順）。科目が0件なら空で、そのとき画面は表と合計行を出さず、案内を出す（Q7-7 の A）
 * @param total    合計行。科目が0件のときも値は作る（予算 null・実績0・差額 null・色なし）が、画面は出さない
 */
public record ComparisonResult(SeasonPeriod period, SeasonPeriod previous, SeasonPeriod next,
		List<ComparisonRow> rows, ComparisonRow total) {
}
