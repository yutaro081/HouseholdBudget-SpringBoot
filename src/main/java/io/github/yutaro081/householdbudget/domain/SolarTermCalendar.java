package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 二十四節気の日付から、今日が「節気の日」か、七十二候のどの候か、次の節気はいつか、を求める（設計書 5.12、要件定義書 4.5）。
 * 画面にもデータベースにも依存しない（P1）。日付の表は、呼ぶ側（HitokotoTexts）がファイルから読んで渡す。
 * <p>
 * 【決まり】
 * <ul>
 * <li>節気の日 +0日＝節気の日、+1〜4日＝初候、+5〜9日＝次候、+10日〜次の節気の前の日＝末候（七十二候は、節気の日から5日ずつ）。</li>
 * <li>七十二候の通し番号は、立春の初候を1、大寒の末候を72とする。</li>
 * <li>1月は前の年の冬至から、年末の「次の節気」は次の年の立春なので、前の年と次の年の日付も使う。</li>
 * <li>今日の前の節気と次の節気が、どちらも表の日付のときだけ、表を使う。どちらかが表にない年なら、
 * その日は、文面の「ごろ」の月日で代わりに求める（最大で1日ずれることがある）。このとき {@code approximate} が true になる。</li>
 * </ul>
 */
public final class SolarTermCalendar {

	/** 二十四節気の名前。立春から大寒の順（文面の番号 1〜24 と同じ並び） */
	public static final List<String> NAMES = List.of("立春", "雨水", "啓蟄", "春分", "清明", "穀雨", "立夏", "小満", "芒種", "夏至", "小暑", "大暑",
			"立秋", "処暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至", "小寒", "大寒");

	/**
	 * 今日の位置。
	 *
	 * @param sekkiIndex  今日以前でいちばん近い節気（NAMES の位置。0〜23）
	 * @param daysSince   その節気の日から何日たったか（節気の日は0）
	 * @param approximate 代わりの計算（「ごろ」の月日）を使ったか
	 */
	public record Position(int sekkiIndex, int daysSince, boolean approximate) {

		public boolean isSekkiDay() {
			return daysSince == 0;
		}

		/** 七十二候の通し番号（1〜72）。節気の日には候がないので、呼べない。 */
		public int koNumber() {
			if (isSekkiDay()) {
				throw new IllegalStateException("節気の日には七十二候がない");
			}
			int step = daysSince < 5 ? 1 : daysSince < 10 ? 2 : 3;
			return sekkiIndex * 3 + step;
		}
	}

	/**
	 * 次の節気。
	 *
	 * @param sekkiIndex 次の節気（NAMES の位置）
	 * @param daysUntil  あと何日（明日なら1）
	 */
	public record Next(int sekkiIndex, int daysUntil) {
	}

	private record Node(LocalDate date, int index, boolean exact) {
	}

	private final Map<Integer, Map<String, LocalDate>> table;
	private final Map<String, MonthDay> approx;

	/**
	 * @param table  年 → （節気の名前 → 日付）。年ごとに24個そろっていること
	 * @param approx 節気の名前 → 「ごろ」の月日（24個。表にない年の代わり）
	 */
	public SolarTermCalendar(Map<Integer, Map<String, LocalDate>> table, Map<String, MonthDay> approx) {
		for (var year : table.entrySet()) {
			for (String name : NAMES) {
				if (!year.getValue().containsKey(name)) {
					throw new IllegalArgumentException(year.getKey() + "年の表に " + name + " がない");
				}
			}
		}
		for (String name : NAMES) {
			if (!approx.containsKey(name)) {
				throw new IllegalArgumentException("「ごろ」の月日に " + name + " がない");
			}
		}
		this.table = Map.copyOf(table);
		this.approx = Map.copyOf(approx);
	}

	public boolean hasYear(int year) {
		return table.containsKey(year);
	}

	/** その年の節気の日。表にない年は「ごろ」の月日。 */
	public LocalDate dateOf(int year, int sekkiIndex) {
		Map<String, LocalDate> dates = table.get(year);
		String name = NAMES.get(sekkiIndex);
		return dates != null ? dates.get(name) : approx.get(name).atYear(year);
	}

	public Position positionOn(LocalDate today) {
		Neighbors n = neighbors(today);
		return new Position(n.previous.index, (int) ChronoUnit.DAYS.between(n.previous.date, today), !n.exact);
	}

	/** 次の節気。代わりの計算を使った日は、日付がずれるかもしれないので、返さない。 */
	public Optional<Next> nextAfter(LocalDate today) {
		Neighbors n = neighbors(today);
		if (!n.exact) {
			return Optional.empty();
		}
		return Optional.of(new Next(n.next.index, (int) ChronoUnit.DAYS.between(today, n.next.date)));
	}

	private record Neighbors(Node previous, Node next, boolean exact) {
	}

	private Neighbors neighbors(LocalDate today) {
		Neighbors mixed = find(today, false);
		if (mixed.previous.exact && mixed.next.exact) {
			return new Neighbors(mixed.previous, mixed.next, true);
		}
		Neighbors allApprox = find(today, true);
		return new Neighbors(allApprox.previous, allApprox.next, false);
	}

	/** 前の年・今年・次の年の節気を日付順に並べて、今日以前でいちばん近いものと、今日より後でいちばん近いものを探す。 */
	private Neighbors find(LocalDate today, boolean forceApprox) {
		List<Node> nodes = new ArrayList<>();
		for (int year = today.getYear() - 1; year <= today.getYear() + 1; year++) {
			boolean exact = !forceApprox && table.containsKey(year);
			for (int i = 0; i < NAMES.size(); i++) {
				LocalDate date = exact ? table.get(year).get(NAMES.get(i)) : approx.get(NAMES.get(i)).atYear(year);
				nodes.add(new Node(date, i, exact));
			}
		}
		nodes.sort(Comparator.comparing(Node::date));
		Node previous = null;
		Node next = null;
		for (Node node : nodes) {
			if (!node.date.isAfter(today)) {
				previous = node;
			} else if (next == null) {
				next = node;
			}
		}
		return new Neighbors(previous, next, true);
	}

}
