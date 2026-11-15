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
	 * @param rows        科目ごとの今週の数字と、先週の同じ曜日までの実績（科目の登録順。issue 028）。科目が0件なら空
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
				items.add(new CalendarItem(Kind.EVENT, event.rule().name(), null, event.text(), null));
			}
		}
		if (position.isSekkiDay()) {
			HitokotoTexts.SekkiText sekki = texts.sekki(position.sekkiIndex());
			items.add(new CalendarItem(Kind.SEKKI, sekki.name(), sekki.reading(), sekki.text(), sekki.name()));
		} else if (items.isEmpty()) {
			HitokotoTexts.KoText ko = texts.ko(position.koNumber());
			items.add(new CalendarItem(Kind.KO, ko.name(), ko.reading(), ko.text(),
					ko.sekki() + "の" + positionName(ko.position())));
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

	/** 初候・次候・末候（ファイルの first・second・third から） */
	private static String positionName(String position) {
		return switch (position) {
			case "first" -> "初候";
			case "second" -> "次候";
			case "third" -> "末候";
			default -> throw new IllegalStateException("七十二候の位置が正しくない：" + position);
		};
	}

	/** 型と部品から、messages.properties のキーと引数にする（文そのものは作らない）。 */
	static GraphLine graphLine(GraphComment.Result result) {
		return switch (result.type()) {
			case PER_DAY -> new GraphLine("hitokoto.graph.perday", result.amount(), result.number());
			// 全部の科目が週の予算以下のとき（分子 ＝ 分母）だけ、「すべて」の文にする（「6 科目のうち 6 科目」と言わない）
			case NOT_OVER -> result.number() == result.amount()
					? new GraphLine("hitokoto.graph.notover.all", result.amount(), null)
					: new GraphLine("hitokoto.graph.notover", result.amount(), result.number());
			case ROOM -> new GraphLine("hitokoto.graph.room", result.categoryName(), result.number());
			case LAST_WEEK -> {
				long difference = result.amount();
				if (difference == 0) {
					yield new GraphLine("hitokoto.graph.lastweek.same", null, null);
				}
				yield new GraphLine(difference < 0 ? "hitokoto.graph.lastweek.less" : "hitokoto.graph.lastweek.more",
						Math.abs(difference), null);
			}
			case COUNT -> new GraphLine("hitokoto.graph.count", result.amount(), null);
			case UNUSED -> new GraphLine("hitokoto.graph.unused", result.categoryName(), null);
			case WEEK_START -> new GraphLine("hitokoto.graph.weekstart", result.amount(), null);
		};
	}

}
