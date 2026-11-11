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
 * @param autoAvailable 「自動予算」ボタンと一言を出すか（issue 019）。過去の同じ季節が範囲（2000年春）より前しかないとき、
 *                      科目が 0 件のときは false
 * @param total    開いたときの予算額の合計（欄の最初の値の足し算。JavaScript が無効のときの表示、設計書 2.3）。
 *                 入力エラーで表示し直すときは null（画面は「—」）
 * @param auto     自動予算の中身。「自動予算」を押していないときは null
 */
public record BudgetEditView(SeasonPeriod period, SeasonPeriod previous, SeasonPeriod next, List<Row> rows,
		boolean autoAvailable, Long total, AutoBudgetView auto) {

	/** 自動予算の項目がない形（BudgetService が作る。ボタンの有無などは Controller が足す）。 */
	public BudgetEditView(SeasonPeriod period, SeasonPeriod previous, SeasonPeriod next, List<Row> rows) {
		this(period, previous, next, rows, false, null, null);
	}

	/** 自動予算の項目だけを足した新しい表示データ。 */
	public BudgetEditView withAuto(boolean autoAvailable, Long total, AutoBudgetView auto) {
		return new BudgetEditView(period, previous, next, rows, autoAvailable, total, auto);
	}

	/**
	 * 1科目分の行。
	 *
	 * @param categoryId   科目のID（画面の隠し項目に入れる）
	 * @param categoryName 科目名
	 * @param amount       入力欄の最初の値（その季節の予算 → 0円、設計書 5.7。issue 019 で、直前の季節の額をやめた）。
	 *                     入力エラーで表示し直すための行（BudgetService.rowsOf）では、画面は送られた値を使うので null
	 * @param unset        「（この季節は未設定）」の印を出すか。その季節の予算が保存されていなければ true（Q26）
	 */
	public record Row(Long categoryId, String categoryName, Integer amount, boolean unset) {
	}

}
