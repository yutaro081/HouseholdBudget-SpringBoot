package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;

/**
 * 日数を数え始める日（要件定義書 4.4、設計書 5.13、5.14、issue 020）。
 * 利用者のいちばん最初の取引の日 F が、季節の中（初日以上、終わりの翌日未満）にあり、今日より後でないときだけ F。
 * それ以外（取引がない、F が季節より前・後、F が今日より後）は、季節の初日。
 * 画面にもデータベースにも依存しない。
 */
public final class CountingStart {

	private CountingStart() {
	}

	/**
	 * @param season               数える季節（着地見込みでは表示している季節、自動予算のペースでは直前の季節）
	 * @param firstTransactionDate 利用者のいちばん最初の取引の日（取引がなければ null）
	 * @param today                今日
	 */
	public static LocalDate of(SeasonPeriod season, LocalDate firstTransactionDate, LocalDate today) {
		if (firstTransactionDate != null
				&& !firstTransactionDate.isBefore(season.startDate())
				&& firstTransactionDate.isBefore(season.endDateExclusive())
				&& !firstTransactionDate.isAfter(today)) {
			return firstTransactionDate;
		}
		return season.startDate();
	}
}
