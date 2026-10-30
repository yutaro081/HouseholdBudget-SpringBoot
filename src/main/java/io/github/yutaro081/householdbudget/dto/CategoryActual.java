package io.github.yutaro081.householdbudget.dto;

/**
 * 科目ごとの実績（期間内の取引の合計）。Repository の集計結果を受け取る入れ物（設計書 4.6、4.7、P4）。
 * <p>
 * 取引がない科目の分は、集計の結果に出ない。0円にするのは Service の役目（設計書 5.9）。
 *
 * @param categoryId 科目のID
 * @param total      その科目の期間内の取引金額の合計（円）。件数が多いと INT を超えるので Long（設計書 3.1）
 */
public record CategoryActual(Long categoryId, Long total) {
}
