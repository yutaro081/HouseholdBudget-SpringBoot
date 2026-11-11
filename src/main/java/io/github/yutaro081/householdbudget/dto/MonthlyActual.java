package io.github.yutaro081.householdbudget.dto;

/**
 * 科目・月ごとの実績（期間内の取引の合計）。{@code TransactionRepository.sumByCategoryAndMonth} の結果を受け取る入れ物
 * （設計書 4.6、4.7、issue 019）。取引のない科目・月は、結果に出ない。
 *
 * @param categoryId 科目のID
 * @param year       取引の日付の西暦の年（季節の年ではない。冬の 1 月・2 月も、その日付の年）
 * @param month      取引の日付の月（1〜12）
 * @param total      その科目・月の取引金額の合計（円）。件数が多いと INT を超えるので Long（設計書 3.1）
 */
public record MonthlyActual(Long categoryId, int year, int month, Long total) {
}
