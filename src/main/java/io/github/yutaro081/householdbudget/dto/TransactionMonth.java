package io.github.yutaro081.householdbudget.dto;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import io.github.yutaro081.householdbudget.entity.Transaction;

/**
 * 取引一覧の「月ごとのまとまり」（設計書 2.3、issue 025 の段階 2）。
 * {@code TransactionService.listByMonth} が、ログイン中の人の取引だけから作る。画面（テンプレート）では足し算しない。
 *
 * @param month        取引の日付の西暦の年月（冬の 1 月・2 月も、その日付の年。季節の年ではない）
 * @param count        その月の取引の件数（行の数。分けて登録したレシートは、行の数だけ数える。issue 032）
 * @param total        その月の取引金額の合計（円）。件数が多いと INT を超えるので long
 * @param transactions その月の取引（一覧の順：日付の新しい順 → まとまりの新しい順 → まとまりの中は id の順）
 * @param receipts     その月の、同じレシートの取引の合計と行の数（印 → 合計）。削除の確認の文に使う（issue 032）。
 *                     同じレシートの取引は日付が同じなので、必ず同じ月に入る
 */
public record TransactionMonth(YearMonth month, int count, long total, List<Transaction> transactions,
		Map<Long, Receipt> receipts) {

	/**
	 * 同じレシートの取引の、金額の合計と行の数（issue 032）。
	 *
	 * @param total 行の金額の合計（円）
	 * @param lines 行の数
	 */
	public record Receipt(long total, int lines) {
	}

}
