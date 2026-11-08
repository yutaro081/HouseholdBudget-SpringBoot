package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 年中行事の日の判定の単体テスト（設計書 5.12、要件定義書 4.5）。
 * 月日が決まっているもの（8個）と、節気から引くもの（節分・春と秋のお彼岸入り）。日付の型で引く（月日で引かない）。
 */
class EventDaysTest {

	private final SolarTermCalendar calendar = SolarTermTestData.calendar();

	private static EventRule setsubun() {
		return EventRule.beforeSekki("節分", SolarTermCalendar.NAMES.indexOf("立春"), 1);
	}

	private static EventRule springHigan() {
		return EventRule.beforeSekki("春のお彼岸入り", SolarTermCalendar.NAMES.indexOf("春分"), 3);
	}

	private static EventRule autumnHigan() {
		return EventRule.beforeSekki("秋のお彼岸入り", SolarTermCalendar.NAMES.indexOf("秋分"), 3);
	}

	@ParameterizedTest(name = "{0} は {1}/{2}（{3}）")
	@CsvSource({
			"元日, 1, 1, 2026-01-01",
			"七草, 1, 7, 2026-01-07",
			"ひな祭り, 3, 3, 2026-03-03",
			"端午の節句, 5, 5, 2026-05-05",
			"七夕, 7, 7, 2026-07-07",
			"お盆, 8, 13, 2026-08-13",
			"七五三, 11, 15, 2026-11-15",
			"大晦日, 12, 31, 2026-12-31"
	})
	void fixedEventsAreOnlyOnTheirOwnDay(String name, int month, int day, LocalDate date) {
		EventRule rule = EventRule.fixed(name, month, day);

		assertThat(EventDays.isEventDay(rule, date, calendar)).isTrue();
		assertThat(EventDays.isEventDay(rule, date.minusDays(1), calendar)).isFalse();
		assertThat(EventDays.isEventDay(rule, date.plusDays(1), calendar)).isFalse();
		assertThat(EventDays.isEventDay(rule, date.plusYears(1), calendar)).isTrue();     // 毎年同じ
	}

	@ParameterizedTest(name = "節分は {0}")
	@CsvSource({"2026-02-03", "2027-02-03", "2025-02-02"})     // 立春 2/4・2/4・2/3 の前の日
	void setsubunIsTheDayBeforeRisshun(LocalDate date) {
		assertThat(EventDays.isEventDay(setsubun(), date, calendar)).isTrue();
		assertThat(EventDays.isEventDay(setsubun(), date.plusDays(1), calendar)).isFalse();
		assertThat(EventDays.isEventDay(setsubun(), date.minusDays(1), calendar)).isFalse();
	}

	@ParameterizedTest(name = "春のお彼岸入りは {0}")
	@CsvSource({"2026-03-17", "2027-03-18", "2025-03-17"})     // 春分 3/20・3/21・3/20 の3日前
	void springHiganStartsThreeDaysBeforeShunbun(LocalDate date) {
		assertThat(EventDays.isEventDay(springHigan(), date, calendar)).isTrue();
		assertThat(EventDays.isEventDay(springHigan(), date.plusDays(1), calendar)).isFalse();
		assertThat(EventDays.isEventDay(springHigan(), date.minusDays(1), calendar)).isFalse();
	}

	@ParameterizedTest(name = "秋のお彼岸入りは {0}")
	@CsvSource({"2026-09-20", "2027-09-20", "2025-09-20"})     // 秋分 9/23 の3日前
	void autumnHiganStartsThreeDaysBeforeShubun(LocalDate date) {
		assertThat(EventDays.isEventDay(autumnHigan(), date, calendar)).isTrue();
		assertThat(EventDays.isEventDay(autumnHigan(), date.plusDays(1), calendar)).isFalse();
	}

	@Test
	void rulesComputedFromSekkiWorkInYearsOutsideTheTable() {
		// 2030年は表にない：春分は「ごろ」の 3/20 → お彼岸入りは 3/17、立春は 2/4 → 節分は 2/3
		assertThat(EventDays.isEventDay(springHigan(), LocalDate.of(2030, 3, 17), calendar)).isTrue();
		assertThat(EventDays.isEventDay(setsubun(), LocalDate.of(2030, 2, 3), calendar)).isTrue();
	}

	@Test
	void ordinaryDaysAreNotEventDays() {
		for (EventRule rule : new EventRule[] { setsubun(), springHigan(), autumnHigan(),
				EventRule.fixed("元日", 1, 1) }) {
			assertThat(EventDays.isEventDay(rule, LocalDate.of(2026, 11, 8), calendar)).isFalse();
		}
	}

}
