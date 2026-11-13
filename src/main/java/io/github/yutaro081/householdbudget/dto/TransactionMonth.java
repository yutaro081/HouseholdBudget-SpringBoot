package io.github.yutaro081.householdbudget.dto;

import java.time.YearMonth;
import java.util.List;

import io.github.yutaro081.householdbudget.entity.Transaction;

/**
 * 取引一覧の「月ごとのまとまり」（設計書 2.3、issue 025 の段階 2）。
 * {@code TransactionService.listByMonth} が、ログイン中の人の取引だけから作る。画面（テンプレート）では足し算しない。
 *
 * @param month        取引の日付の西暦の年月（冬の 1 月・2 月も、その日付の年。季節の年ではない）
 * @param count        その月の取引の件数
 * @param total        その月の取引金額の合計（円）。件数が多いと INT を超えるので long
 * @param transactions その月の取引（日付の新しい順、同じ日は登録の新しい順）
 */
public record TransactionMonth(YearMonth month, int count, long total, List<Transaction> transactions) {
}
