package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;

/**
 * 今週の期間（設計書 5.11、要件定義書 4.5）。日曜〜土曜の7日間。
 * 画面にもデータベースにも依存しない計算だけを持つ（P1）。
 * <p>
 * 取引は「日付 ≧ 開始日 かつ 日付 ＜ 終了日の翌日（次の日曜）」で選ぶ（季節の期間と同じ形、設計書 5.8）。
 * 季節をまたぐ週も、前の季節の取引をそのまま入れる（季節は見ない）。
 *
 * @param start 週の開始日（日曜）
 */
public record WeekPeriod(LocalDate start) {

	/** 1週間の日数 */
	public static final int DAYS = 7;

	/** 日付を含む週。日曜が1日目。 */
	public static WeekPeriod containing(LocalDate date) {
		// DayOfWeek は 月曜＝1 … 日曜＝7。日曜は 7 % 7 ＝ 0 日戻る（その日が開始日）
		int daysSinceSunday = date.getDayOfWeek().getValue() % DAYS;
		return new WeekPeriod(date.minusDays(daysSinceSunday));
	}

	/** 週の最後の日（土曜）。 */
	public LocalDate endDate() {
		return start.plusDays(DAYS - 1);
	}

	/** 終了日の翌日（次の日曜。含まない）。 */
	public LocalDate endDateExclusive() {
		return start.plusDays(DAYS);
	}

	/** その日が週の何日目か（日曜＝1、土曜＝7）。週の外の日付を渡さない（呼ぶ側が今日を渡す）。 */
	public int dayNumber(LocalDate date) {
		return (int) (date.toEpochDay() - start.toEpochDay()) + 1;
	}

}
