package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.yutaro081.householdbudget.domain.EventDays;
import io.github.yutaro081.householdbudget.domain.SolarTermCalendar;
import io.github.yutaro081.householdbudget.domain.SolarTermTestData;

/**
 * 文面と日付の表のファイル（src/main/resources/hitokoto/）のテスト（設計書 5.12、手順書 issue-016-hitokoto.md 5.4）。
 * Spring もデータベースも使わない。ファイルが欠けたまま動かさないための確認（件数・番号・空の文）と、
 * 二十四節気の日付の表を、暦要項から取得した日付の写し（SolarTermTestData）と照らし合わせる。
 */
class HitokotoTextsTest {

	private static final HitokotoTexts TEXTS = new HitokotoTexts();

	// ---- 件数・番号・形 ----

	@Test
	void hasSeventyTwoKoTwentyFourSekkiAndElevenEvents() {
		for (int n = 1; n <= 72; n++) {
			assertThat(TEXTS.ko(n).number()).isEqualTo(n);
		}
		for (int i = 0; i < 24; i++) {
			assertThat(TEXTS.sekki(i).number()).isEqualTo(i + 1);
		}
		assertThat(TEXTS.events()).hasSize(11);
	}

	@Test
	void koBelongToTheRightSekkiAndPosition() {
		String[] positions = { "first", "second", "third" };
		for (int n = 1; n <= 72; n++) {
			var ko = TEXTS.ko(n);
			assertThat(ko.sekki()).as("七十二候 " + n).isEqualTo(SolarTermCalendar.NAMES.get((n - 1) / 3));
			assertThat(ko.position()).as("七十二候 " + n).isEqualTo(positions[(n - 1) % 3]);
		}
	}

	@Test
	void sekkiAreInOrderAndTheirTextsStartWithToday() {
		for (int i = 0; i < 24; i++) {
			var sekki = TEXTS.sekki(i);
			assertThat(sekki.name()).isEqualTo(SolarTermCalendar.NAMES.get(i));
			assertThat(sekki.text()).startsWith("今日は" + sekki.name() + "。");
		}
	}

	@Test
	void eventsAreTheElevenInTheDecidedOrder() {
		assertThat(TEXTS.events().stream().map(e -> e.rule().name()).toList()).containsExactly("元日", "七草", "節分",
				"ひな祭り", "春のお彼岸入り", "端午の節句", "七夕", "お盆", "秋のお彼岸入り", "七五三", "大晦日");
	}

	@Test
	void everyTextIsNotBlankAndHasNoMarkupOrUrl() {
		List<String> all = new java.util.ArrayList<>();
		for (int n = 1; n <= 72; n++) {
			all.add(TEXTS.ko(n).text());
			all.add(TEXTS.ko(n).name());
			all.add(TEXTS.ko(n).reading());
		}
		for (int i = 0; i < 24; i++) {
			all.add(TEXTS.sekki(i).text());
			all.add(TEXTS.sekki(i).reading());
		}
		TEXTS.events().forEach(e -> all.add(e.text()));

		for (String text : all) {
			assertThat(text).isNotBlank();
			assertThat(text).isEqualTo(text.strip());
			assertThat(text).doesNotContain("<", ">", "&", "http", "style=");
		}
	}

	@Test
	void textsAreAllDifferent() {
		Set<String> texts = new HashSet<>();
		for (int n = 1; n <= 72; n++) {
			assertThat(texts.add(TEXTS.ko(n).text())).as("七十二候 " + n).isTrue();
		}
		for (int i = 0; i < 24; i++) {
			assertThat(texts.add(TEXTS.sekki(i).text())).isTrue();
		}
		TEXTS.events().forEach(e -> assertThat(texts.add(e.text())).isTrue());
		assertThat(texts).hasSize(107);
	}

	/** 手順書 Q016-7：本人が直した 2 か所。節気の当日の翌日から出る文なので、「今日から」ではない */
	@Test
	void theTwoSentencesFixedByTheOwnerAreInPlace() {
		assertThat(TEXTS.ko(19).name()).isEqualTo("蛙始鳴");
		assertThat(TEXTS.ko(19).text()).contains("暦の上では、もう夏です。").doesNotContain("今日から夏");
		assertThat(TEXTS.ko(37).name()).isEqualTo("涼風至");
		assertThat(TEXTS.ko(37).text()).contains("暦の上では、もう秋です。").doesNotContain("今日から秋");
	}

	@Test
	void firstAndLastKoAreWhereTheyShouldBe() {
		assertThat(TEXTS.ko(1).name()).isEqualTo("東風解凍");
		assertThat(TEXTS.ko(55).name()).isEqualTo("山茶始開");
		assertThat(TEXTS.ko(72).name()).isEqualTo("鶏始乳");
	}

	// ---- 二十四節気の日付の表 ----

	@Test
	void theTableHas2025To2027OnlyAndNothingFrom2028() {
		SolarTermCalendar calendar = TEXTS.calendar();

		assertThat(calendar.hasYear(2025)).isTrue();
		assertThat(calendar.hasYear(2026)).isTrue();
		assertThat(calendar.hasYear(2027)).isTrue();
		assertThat(calendar.hasYear(2024)).isFalse();
		assertThat(calendar.hasYear(2028)).isFalse();
	}

	/** 暦要項から取得した日付の写し（2025〜2027年の72件）と一致する（写し間違いを見つける） */
	@Test
	void theTableMatchesTheDatesCheckedAgainstTheObservatoryPage() {
		Map<Integer, String> expected = Map.of(2025, SolarTermTestData.Y2025, 2026, SolarTermTestData.Y2026, 2027,
				SolarTermTestData.Y2027);
		for (var year : expected.entrySet()) {
			Map<String, LocalDate> dates = SolarTermTestData.year(year.getKey(), year.getValue());
			for (var item : dates.entrySet()) {
				int index = SolarTermCalendar.NAMES.indexOf(item.getKey());
				assertThat(TEXTS.calendar().dateOf(year.getKey(), index)).as(year.getKey() + "年 " + item.getKey())
						.isEqualTo(item.getValue());
			}
		}
	}

	// ---- 機械で見る 4 項目（手順書 5.4②。表に年を足すときも、同じ確認をする） ----

	/** ① 各年 24 個、日付が増える順 */
	@Test
	void everyYearHasTwentyFourDatesInIncreasingOrder() {
		for (int year : new int[] { 2025, 2026, 2027 }) {
			// 暦の年の中では 小寒・大寒（1月）→ 立春（2月）→ … → 冬至（12月）の順
			String[] chronological = { "小寒", "大寒", "立春", "雨水", "啓蟄", "春分", "清明", "穀雨", "立夏", "小満", "芒種", "夏至", "小暑", "大暑",
					"立秋", "処暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至" };
			LocalDate previous = null;
			for (String name : chronological) {
				LocalDate date = TEXTS.calendar().dateOf(year, SolarTermCalendar.NAMES.indexOf(name));
				assertThat(date.getYear()).isEqualTo(year);
				if (previous != null) {
					assertThat(date).as(year + "年 " + name).isAfter(previous);
					// ② 隣り合う節気の間が 14〜16 日
					assertThat(ChronoUnit.DAYS.between(previous, date)).as(year + "年 " + name).isBetween(14L, 16L);
				}
				previous = date;
			}
		}
	}

	/** ③ 各日付が、文面の「ごろ」の日付から 2 日以内 */
	@Test
	void everyDateIsWithinTwoDaysOfTheApproximateDateInTheText() {
		for (int year : new int[] { 2025, 2026, 2027 }) {
			for (int i = 0; i < 24; i++) {
				LocalDate date = TEXTS.calendar().dateOf(year, i);
				LocalDate approx = TEXTS.sekki(i).approx().atYear(year);
				assertThat(Math.abs(ChronoUnit.DAYS.between(approx, date))).as(year + "年 " + SolarTermCalendar.NAMES.get(i))
						.isLessThanOrEqualTo(2L);
			}
		}
	}

	/** ④ 春分・秋分が、国が発表した春分の日・秋分の日（内閣府「国民の祝日について」。2026年11月8日に確認）と同じ */
	@Test
	void shunbunAndShubunMatchTheNationalHolidays() {
		int shunbun = SolarTermCalendar.NAMES.indexOf("春分");
		int shubun = SolarTermCalendar.NAMES.indexOf("秋分");

		assertThat(TEXTS.calendar().dateOf(2025, shunbun)).isEqualTo(LocalDate.of(2025, 3, 20));
		assertThat(TEXTS.calendar().dateOf(2025, shubun)).isEqualTo(LocalDate.of(2025, 9, 23));
		assertThat(TEXTS.calendar().dateOf(2026, shunbun)).isEqualTo(LocalDate.of(2026, 3, 20));
		assertThat(TEXTS.calendar().dateOf(2026, shubun)).isEqualTo(LocalDate.of(2026, 9, 23));
		assertThat(TEXTS.calendar().dateOf(2027, shunbun)).isEqualTo(LocalDate.of(2027, 3, 21));
		assertThat(TEXTS.calendar().dateOf(2027, shubun)).isEqualTo(LocalDate.of(2027, 9, 23));
	}

	// ---- 年中行事の決まり方 ----

	@Test
	void eventRulesFromTheFileFindTheDecidedDays() {
		var calendar = TEXTS.calendar();
		Map<String, String> days = Map.of("節分", "2026-02-03", "春のお彼岸入り", "2026-03-17", "秋のお彼岸入り", "2026-09-20",
				"元日", "2026-01-01", "七草", "2026-01-07", "ひな祭り", "2026-03-03", "端午の節句", "2026-05-05", "七夕", "2026-07-07",
				"お盆", "2026-08-13", "七五三", "2026-11-15");
		for (var event : TEXTS.events()) {
			String name = event.rule().name();
			if (days.containsKey(name)) {
				assertThat(EventDays.isEventDay(event.rule(), LocalDate.parse(days.get(name)), calendar)).as(name).isTrue();
			}
		}
		var newYearsEve = TEXTS.events().stream().filter(e -> e.rule().name().equals("大晦日")).findFirst().orElseThrow();
		assertThat(EventDays.isEventDay(newYearsEve.rule(), LocalDate.of(2026, 12, 31), calendar)).isTrue();
	}

	/** 年中行事が同じ日に2個以上ない（2025〜2031年の全日。行事 + 節気の2個までにおさまる） */
	@Test
	void noDayHasMoreThanOneEvent() {
		var calendar = TEXTS.calendar();
		for (int year = 2025; year <= 2031; year++) {
			for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1)) {
				LocalDate day = date;
				long events = TEXTS.events().stream().filter(e -> EventDays.isEventDay(e.rule(), day, calendar)).count();
				assertThat(events).as(day.toString()).isLessThanOrEqualTo(1L);
			}
		}
	}

}
