package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * テスト用の二十四節気の日付（国立天文台 暦要項。2025〜2027年）。アプリの表（JSON）とは別に、テストの中に持つ。
 * 2026・2027年は、本人が国立天文台のページと目で見比べて確かめた日付（2026年11月8日）。
 * 「ごろ」の月日は、文面（docs/hitokoto-source.md）の二十四節気の表の日付。
 */
public final class SolarTermTestData {

	private SolarTermTestData() {
	}

	public static final String Y2025 = "小寒1/5 大寒1/20 立春2/3 雨水2/18 啓蟄3/5 春分3/20 清明4/4 穀雨4/20 立夏5/5 小満5/21 芒種6/5 夏至6/21 "
			+ "小暑7/7 大暑7/22 立秋8/7 処暑8/23 白露9/7 秋分9/23 寒露10/8 霜降10/23 立冬11/7 小雪11/22 大雪12/7 冬至12/22";
	public static final String Y2026 = "小寒1/5 大寒1/20 立春2/4 雨水2/19 啓蟄3/5 春分3/20 清明4/5 穀雨4/20 立夏5/5 小満5/21 芒種6/6 夏至6/21 "
			+ "小暑7/7 大暑7/23 立秋8/7 処暑8/23 白露9/7 秋分9/23 寒露10/8 霜降10/23 立冬11/7 小雪11/22 大雪12/7 冬至12/22";
	public static final String Y2027 = "小寒1/5 大寒1/20 立春2/4 雨水2/19 啓蟄3/6 春分3/21 清明4/5 穀雨4/20 立夏5/6 小満5/21 芒種6/6 夏至6/21 "
			+ "小暑7/7 大暑7/23 立秋8/8 処暑8/23 白露9/8 秋分9/23 寒露10/8 霜降10/24 立冬11/8 小雪11/22 大雪12/7 冬至12/22";

	/** 文面の「ごろ」の月日（二十四節気の表の日付） */
	public static final String APPROX = "立春2/4 雨水2/19 啓蟄3/5 春分3/20 清明4/5 穀雨4/20 立夏5/5 小満5/21 芒種6/5 夏至6/21 小暑7/7 大暑7/23 "
			+ "立秋8/7 処暑8/23 白露9/7 秋分9/23 寒露10/8 霜降10/23 立冬11/7 小雪11/22 大雪12/7 冬至12/22 小寒1/5 大寒1/20";

	/** 「名前月/日」を空白で区切った文を、名前 → 月日 に直す */
	private static Map<String, int[]> parse(String text) {
		Map<String, int[]> map = new LinkedHashMap<>();
		for (String item : text.split(" ")) {
			int split = 0;
			while (!Character.isDigit(item.charAt(split))) {
				split++;
			}
			int slash = item.indexOf('/');
			map.put(item.substring(0, split),
					new int[] { Integer.parseInt(item.substring(split, slash)), Integer.parseInt(item.substring(slash + 1)) });
		}
		return map;
	}

	public static Map<String, LocalDate> year(int year, String text) {
		Map<String, LocalDate> map = new LinkedHashMap<>();
		parse(text).forEach((name, md) -> map.put(name, LocalDate.of(year, md[0], md[1])));
		return map;
	}

	public static Map<String, MonthDay> approx() {
		Map<String, MonthDay> map = new LinkedHashMap<>();
		parse(APPROX).forEach((name, md) -> map.put(name, MonthDay.of(md[0], md[1])));
		return map;
	}

	/** 2025〜2027年の表を持つ暦（アプリの本番と同じ範囲） */
	public static SolarTermCalendar calendar() {
		return new SolarTermCalendar(Map.of(2025, year(2025, Y2025), 2026, year(2026, Y2026), 2027, year(2027, Y2027)),
				approx());
	}

}
