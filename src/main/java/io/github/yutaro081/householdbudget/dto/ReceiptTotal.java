package io.github.yutaro081.householdbudget.dto;

/**
 * 同じレシートの取引の、金額の合計と行の数（issue 023。設計書 4.6、4.7、5.17）。
 * TransactionRepository.sumByReceiptGroups の結果。「印と user_id」で読んだ、レシート全体の値（絞り込む前の値）。
 *
 * @param receiptGroup 同じレシートの印（まとまりの最初の行の id）
 * @param total        行の金額の合計（円）
 * @param lines        行の数
 */
public record ReceiptTotal(Long receiptGroup, Long total, Long lines) {
}
