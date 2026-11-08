package io.github.yutaro081.householdbudget.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.yutaro081.householdbudget.domain.EventDays;
import io.github.yutaro081.householdbudget.domain.GraphComment;
import io.github.yutaro081.householdbudget.domain.SolarTermCalendar;
import io.github.yutaro081.householdbudget.dto.CalendarItem;
import io.github.yutaro081.householdbudget.dto.CalendarItem.Kind;
import io.github.yutaro081.householdbudget.dto.HitokotoView;
import io.github.yutaro081.householdbudget.dto.HitokotoView.GraphLine;
import io.github.yutaro081.householdbudget.dto.HitokotoView.NextSekki;

/**
 * トップの「きょうのひとこと」の組み立て（設計書 5.12、要件定義書 4.5）。データベースは使わない。
 * <p>
 * 暦のひとこと：ふだんは七十二候。二十四節気が始まる日は、七十二候の代わりに節気。年中行事の日は、七十二候の代わりに行事。
 * 行事と節気が同じ日は、行事を先に、その下に節気。
 * グラフへのひとこと：今週の数字（TopService が集めたもの）から、domain の GraphComment が型と部品を決める。
 * 「今日」は呼ぶ側が日本時間の Clock から取って渡す（設計書 B6）。ここで LocalDate.now() を呼ばない。
 */
@Service
public class HitokotoService {

	private static final Logger log = LoggerFactory.getLogger(HitokotoService.class);

	private final HitokotoTexts texts;
	/** 代わりの計算を使った年のうち、ログに警告を出した年（1年に1度だけ出す） */
	private final Set<Integer> warnedYears = ConcurrentHashMap.newKeySet();

	public HitokotoService(HitokotoTexts texts) {
		this.texts = texts;
	}

	/**
	 * @param today       今日（日本時間）
	 * @param rows        科目ごとの今週の数字（科目の登録順）。科目が0件なら空
	 * @param totalBudget 週の予算の合計（設定済みの科目だけ）。全部未設定なら null
	 * @param totalActual 今週の実績の合計（100円単位に切り上げた額の和）
	 */
	public HitokotoView hitokoto(LocalDate today, List<GraphComment.Row> rows, Long totalBudget, long totalActual) {
		SolarTermCalendar calendar = texts.calendar();
		SolarTermCalendar.Position position = calendar.positionOn(today);
		if (position.approximate() && warnedYears.add(today.getYear())) {
			log.warn("二十四節気の日付の表に {} 年の前後がないため、おおよその日付（1日ほどずれることがある）で動かしています", today.getYear());
		}

		List<CalendarItem> items = new ArrayList<>();
		for (HitokotoTexts.EventText event : texts.events()) {
			if (EventDays.isEventDay(event.rule(), today, calendar)) {
				items.add(new CalendarItem(Kind.EVENT, event.rule().name(), null, event.text()));
			}
		}
		if (position.isSekkiDay()) {
			HitokotoTexts.SekkiText sekki = texts.sekki(position.sekkiIndex());
			items.add(new CalendarItem(Kind.SEKKI, sekki.name(), sekki.reading(), sekki.text()));
		} else if (items.isEmpty()) {
			HitokotoTexts.KoText ko = texts.ko(position.koNumber());
			items.add(new CalendarItem(Kind.KO, ko.name(), ko.reading(), ko.text()));
		}

		NextSekki nextSekki = null;
		if (!position.isSekkiDay()) {
			nextSekki = calendar.nextAfter(today)
					.map(next -> new NextSekki(SolarTermCalendar.NAMES.get(next.sekkiIndex()), next.daysUntil()))
					.orElse(null);
		}

		GraphLine graph = GraphComment.choose(rows, totalBudget, totalActual, today)
				.map(HitokotoService::graphLine).orElse(null);
		return new HitokotoView(List.copyOf(items), nextSekki, graph);
	}

	/** 型と部品から、messages.properties のキーと引数にする（文そのものは作らない）。 */
	static GraphLine graphLine(GraphComment.Result result) {
		return switch (result.type()) {
			case TOP -> new GraphLine("hitokoto.graph.top", result.categoryName(), result.amount());
			case UNUSED -> new GraphLine("hitokoto.graph.unused", result.categoryName(), null);
			case REMAINING -> {
				String base = result.over() ? "hitokoto.graph.over" : "hitokoto.graph.remaining";
				yield new GraphLine(result.daysLeft() == 0 ? base + ".saturday" : base, result.amount(),
						result.daysLeft());
			}
		};
	}

}
