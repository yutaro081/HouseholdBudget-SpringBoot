package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;

/**
 * 年と季節の組み合わせ（設計書 4.5、5.8）。
 * <p>
 * 年は「季節の年」で、冬は12月がある年にする
 * （12月はその年、1月・2月は前の年。例：2027年2月は「2026年の冬」）。
 * 画面にもデータベースにも依存しない計算だけを持つ（P1）。
 * <p>
 * Phase 3 では「今日の日付から、季節と年を決める」部分だけを作った。
 * 期間（開始日と終了日の翌日）、前・次の季節、2000年春〜2100年冬の範囲の判定は、
 * 使う Phase（予算設定は Phase 6、予実比較は Phase 7）で足す。
 */
public record SeasonPeriod(int year, Season season) {

	/** 今日の日付から、季節と年を決める。「今日」は日本時間（B6）で、呼ぶ側が渡す。 */
	public static SeasonPeriod from(LocalDate date) {
		Season season = Season.fromMonth(date.getMonth());
		// 冬の1月・2月は、前の年の冬に入る（12月がある年を、冬の年とする）
		int year = (season == Season.WINTER && date.getMonthValue() <= 2)
				? date.getYear() - 1
				: date.getYear();
		return new SeasonPeriod(year, season);
	}

}
