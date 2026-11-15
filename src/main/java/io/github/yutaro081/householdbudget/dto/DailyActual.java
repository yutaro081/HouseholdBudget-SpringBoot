package io.github.yutaro081.householdbudget.dto;

import java.time.LocalDate;

/**
 * 科目・日付ごとの実績と件数（期間内の取引の合計）。{@code TransactionRepository.sumByCategoryAndDate} の結果を受け取る入れ物
 * （設計書 4.6、4.7、issue 028）。取引のない科目・日は、結果に出ない。
 *
 * @param categoryId 科目のID
 * @param date       取引の日付
 * @param total      その科目・日の取引金額の合計（円）。INT を超えうるので Long（設計書 3.1）
 * @param count      その科目・日の取引の件数
 */
public record DailyActual(Long categoryId, LocalDate date, Long total, Long count) {
}
