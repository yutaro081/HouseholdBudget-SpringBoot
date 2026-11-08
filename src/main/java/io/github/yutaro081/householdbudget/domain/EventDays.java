package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;

/**
 * 年中行事の日かどうかの判定（設計書 5.12、要件定義書 4.5）。画面にもデータベースにも依存しない。
 * 節気から引くものは、日付の型で引く（月日で引かない）。表にない年は、暦（SolarTermCalendar）が「ごろ」の月日で代わりに求める。
 */
public final class EventDays {

	private EventDays() {
	}

	public static boolean isEventDay(EventRule rule, LocalDate date, SolarTermCalendar calendar) {
		if (rule.isFixed()) {
			return date.getMonthValue() == rule.month() && date.getDayOfMonth() == rule.day();
		}
		return calendar.dateOf(date.getYear(), rule.sekkiIndex()).minusDays(rule.daysBefore()).equals(date);
	}

}
