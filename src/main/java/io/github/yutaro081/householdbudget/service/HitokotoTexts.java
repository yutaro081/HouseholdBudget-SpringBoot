package io.github.yutaro081.householdbudget.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.github.yutaro081.householdbudget.domain.EventRule;
import io.github.yutaro081.householdbudget.domain.SolarTermCalendar;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * きょうのひとことの文面と、二十四節気の日付の表を、ファイル（src/main/resources/hitokoto/）から読んで持つ（設計書 4.3、5.12）。
 * 起動のとき1回だけ読む。件数（七十二候72・二十四節気24・年中行事11）、番号、空の文を確かめ、
 * 合わなければ起動を失敗させる（欠けたまま動かさない）。
 * <p>
 * 文面の原本は、このファイル（JSON）。文面の言い回しは、コードの側で変えない。
 * 文は、アプリの中のファイルの文で、利用者の入力ではない。
 */
@Component
public class HitokotoTexts {

	/** 七十二候。position は first（初候）・second（次候）・third（末候） */
	public record KoText(int number, String sekki, String position, String name, String reading, String text) {
	}

	/** 二十四節気（その節気が始まる日に出す文）。approx は「ごろ」の月日（表にない年の代わり） */
	public record SekkiText(int number, String name, String reading, String text, MonthDay approx) {
	}

	/** 年中行事。日の決まり方と、文 */
	public record EventText(EventRule rule, String text) {
	}

	/** ファイルの形（読み込み用） */
	public record RawSekki(int number, String name, String reading, String text, RawMonthDay approx) {
	}

	public record RawMonthDay(int month, int day) {
	}

	public record RawEvent(String name, String rule, Integer month, Integer day, String sekki, Integer days,
			String text) {
	}

	private static final String DIRECTORY = "/hitokoto/";

	private final List<KoText> ko;
	private final List<SekkiText> sekki;
	private final List<EventText> events;
	private final SolarTermCalendar calendar;

	public HitokotoTexts() {
		JsonMapper mapper = JsonMapper.builder().build();
		List<KoText> kos = read(mapper, "shichijuni-ko.json", new TypeReference<List<KoText>>() {
		});
		List<RawSekki> rawSekki = read(mapper, "nijushi-sekki.json", new TypeReference<List<RawSekki>>() {
		});
		List<RawEvent> rawEvents = read(mapper, "nenchu-gyoji.json", new TypeReference<List<RawEvent>>() {
		});
		Map<String, Map<String, String>> rawDates = read(mapper, "nijushi-sekki-dates.json",
				new TypeReference<Map<String, Map<String, String>>>() {
				});

		this.ko = checkKo(kos);
		this.sekki = checkSekki(rawSekki);
		this.events = checkEvents(rawEvents);

		Map<Integer, Map<String, LocalDate>> table = new LinkedHashMap<>();
		rawDates.forEach((year, dates) -> {
			Map<String, LocalDate> parsed = new LinkedHashMap<>();
			dates.forEach((name, date) -> parsed.put(name, LocalDate.parse(date)));
			table.put(Integer.parseInt(year), parsed);
		});
		table.forEach((year, dates) -> dates.forEach((name, date) -> {
			if (date.getYear() != year) {
				throw new IllegalStateException("二十四節気の日付の表：" + year + "年の " + name + " の年が違う");
			}
		}));
		Map<String, MonthDay> approx = new LinkedHashMap<>();
		this.sekki.forEach(s -> approx.put(s.name(), s.approx()));
		try {
			this.calendar = new SolarTermCalendar(table, approx);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("二十四節気の日付の表が正しくない：" + e.getMessage(), e);
		}
	}

	private static <T> T read(JsonMapper mapper, String file, TypeReference<T> type) {
		try (InputStream in = HitokotoTexts.class.getResourceAsStream(DIRECTORY + file)) {
			if (in == null) {
				throw new IllegalStateException("文面のファイルがない：" + DIRECTORY + file);
			}
			return mapper.readValue(in, type);
		} catch (IOException e) {
			throw new IllegalStateException("文面のファイルを読めない：" + DIRECTORY + file, e);
		}
	}

	private static List<KoText> checkKo(List<KoText> list) {
		if (list.size() != 72) {
			throw new IllegalStateException("七十二候が72個ない（" + list.size() + "個）");
		}
		Set<Integer> numbers = new HashSet<>();
		for (KoText k : list) {
			requireText(k.text(), "七十二候 " + k.number() + " の文");
			requireText(k.name(), "七十二候 " + k.number() + " の名前");
			requireText(k.reading(), "七十二候 " + k.number() + " の読み");
			if (k.number() < 1 || k.number() > 72 || !numbers.add(k.number())) {
				throw new IllegalStateException("七十二候の番号が正しくない：" + k.number());
			}
		}
		List<KoText> sorted = new ArrayList<>(list);
		sorted.sort((a, b) -> Integer.compare(a.number(), b.number()));
		return List.copyOf(sorted);
	}

	private static List<SekkiText> checkSekki(List<RawSekki> list) {
		if (list.size() != 24) {
			throw new IllegalStateException("二十四節気が24個ない（" + list.size() + "個）");
		}
		List<SekkiText> result = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			RawSekki s = list.get(i);
			if (s.number() != i + 1 || !SolarTermCalendar.NAMES.get(i).equals(s.name())) {
				throw new IllegalStateException("二十四節気の並びが正しくない：" + (i + 1) + " 番目 " + s.name());
			}
			requireText(s.text(), "二十四節気 " + s.name() + " の文");
			requireText(s.reading(), "二十四節気 " + s.name() + " の読み");
			result.add(new SekkiText(s.number(), s.name(), s.reading(), s.text(),
					MonthDay.of(s.approx().month(), s.approx().day())));
		}
		return List.copyOf(result);
	}

	private static List<EventText> checkEvents(List<RawEvent> list) {
		if (list.size() != 11) {
			throw new IllegalStateException("年中行事が11個ない（" + list.size() + "個）");
		}
		List<EventText> result = new ArrayList<>();
		for (RawEvent e : list) {
			requireText(e.text(), "年中行事 " + e.name() + " の文");
			EventRule rule;
			if ("FIXED".equals(e.rule()) && e.month() != null && e.day() != null) {
				rule = EventRule.fixed(e.name(), e.month(), e.day());
			} else if ("BEFORE_SEKKI".equals(e.rule()) && e.sekki() != null && e.days() != null
					&& SolarTermCalendar.NAMES.contains(e.sekki())) {
				rule = EventRule.beforeSekki(e.name(), SolarTermCalendar.NAMES.indexOf(e.sekki()), e.days());
			} else {
				throw new IllegalStateException("年中行事の決まり方が正しくない：" + e.name());
			}
			result.add(new EventText(rule, e.text()));
		}
		return List.copyOf(result);
	}

	private static void requireText(String value, String what) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(what + "が空");
		}
	}

	/** 七十二候（通し番号 1〜72。立春の初候が1） */
	public KoText ko(int number) {
		return ko.get(number - 1);
	}

	/** 二十四節気の文（位置 0〜23。立春が0） */
	public SekkiText sekki(int index) {
		return sekki.get(index);
	}

	/** 年中行事（ファイルの並び） */
	public List<EventText> events() {
		return events;
	}

	/** 二十四節気の日付の表を持つ暦 */
	public SolarTermCalendar calendar() {
		return calendar;
	}

}
