package io.github.yutaro081.householdbudget.dto;

import java.util.List;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;

/**
 * 予算設定画面の表示データ（設計書 4.7、Q26）。
 * <p>
 * 画面が読む値は、すべてこの中に入れて渡す（open-in-view=false なので、画面の中で遅延読み込みに頼らない。実行計画書 ルール8）。
 * 期間の開始日・終了日の翌日は、period から求められる（SeasonPeriod.startDate など）。
 *
 * @param period   表示する季節（年と季節）
 * @param previous 前の季節。範囲の端（2000年春）では null（リンクを出さない、設計書 2.4、B15）
 * @param next     次の季節。範囲の端（2100年冬）では null
 * @param rows     科目ごとの行
 */
public record BudgetEditView(SeasonPeriod period, SeasonPeriod previous, SeasonPeriod next, List<Row> rows) {

	/**
	 * 1科目分の行。
	 *
	 * @param categoryId   科目のID（画面の隠し項目に入れる）
	 * @param categoryName 科目名
	 * @param amount       入力欄の最初の値（その季節の予算 → 直前の季節の額 → 0円、設計書 5.7）。
	 *                     入力エラーで表示し直すための行（BudgetService.rowsOf）では、画面は送られた値を使うので null
	 * @param unset        「（この季節は未設定）」の印を出すか。その季節の予算が保存されていなければ true（Q26）
	 */
	public record Row(Long categoryId, String categoryName, Integer amount, boolean unset) {
	}

}
