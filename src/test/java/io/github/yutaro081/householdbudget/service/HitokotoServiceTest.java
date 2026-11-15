package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yutaro081.householdbudget.domain.GraphComment;
import io.github.yutaro081.householdbudget.domain.GraphComment.Row;
import io.github.yutaro081.householdbudget.dto.CalendarItem;
import io.github.yutaro081.householdbudget.dto.CalendarItem.Kind;
import io.github.yutaro081.householdbudget.dto.HitokotoView;

/**
 * HitokotoService（きょうのひとこと）の単体テスト（設計書 5.12、要件定義書 4.5）。Spring もデータベースも使わない。
 * 暦のひとこと：ふだんは七十二候、節気の日は節気（七十二候の代わり）、行事の日は行事（七十二候の代わり）、
 * 行事と節気が同じ日は行事を先にその下に節気。次の節気の補足は、節気の当日と、代わりの計算を使った日は出さない。
 */
class HitokotoServiceTest {

	private final HitokotoService service = new HitokotoService(new HitokotoTexts());

	private HitokotoView calendarOnly(LocalDate date) {
		return service.hitokoto(date, List.of(), null, 0);
	}

	private static List<String> names(HitokotoView view) {
		return view.items().stream().map(CalendarItem::name).toList();
	}

	private static List<Kind> kinds(HitokotoView view) {
		return view.items().stream().map(CalendarItem::kind).toList();
	}

	// ---- 暦のひとこと ----

	@Test
	void anOrdinaryDayShowsTheSeventyTwoKoAndTheNextSekki() {
		HitokotoView view = calendarOnly(LocalDate.of(2026, 11, 8));

		assertThat(kinds(view)).containsExactly(Kind.KO);
		assertThat(names(view)).containsExactly("山茶始開");
		assertThat(view.items().get(0).reading()).isEqualTo("つばきはじめてひらく");
		assertThat(view.items().get(0).text()).contains("サザンカの花が咲きはじめます");
		assertThat(view.nextSekki().name()).isEqualTo("小雪");
		assertThat(view.nextSekki().days()).isEqualTo(14);
	}

	@Test
	void aSekkiDayShowsTheSekkiInsteadOfTheKoAndNoNextSekki() {
		HitokotoView view = calendarOnly(LocalDate.of(2026, 11, 7));

		assertThat(kinds(view)).containsExactly(Kind.SEKKI);
		assertThat(names(view)).containsExactly("立冬");
		assertThat(view.items().get(0).text()).startsWith("今日は立冬。");
		assertThat(view.nextSekki()).isNull();
	}

	@Test
	void aSekkiDayIsFollowedByTheKoFromTheNextDay() {
		assertThat(names(calendarOnly(LocalDate.of(2026, 11, 8)))).containsExactly("山茶始開");
	}

	@Test
	void aFixedEventDayShowsOnlyTheEventAndNoKo() {
		HitokotoView view = calendarOnly(LocalDate.of(2026, 1, 1));

		assertThat(kinds(view)).containsExactly(Kind.EVENT);
		assertThat(names(view)).containsExactly("元日");
		assertThat(view.nextSekki().name()).isEqualTo("小寒");     // 行事の日は、次の節気の補足を出す
		assertThat(view.nextSekki().days()).isEqualTo(4);
	}

	@Test
	void newYearsEveShowsTheEventAndTheFirstOfJanuaryOfTheNextYearToo() {
		HitokotoView eve = calendarOnly(LocalDate.of(2026, 12, 31));
		HitokotoView newYear = calendarOnly(LocalDate.of(2027, 1, 1));

		assertThat(kinds(eve)).containsExactly(Kind.EVENT);
		assertThat(names(eve)).containsExactly("大晦日");
		assertThat(eve.nextSekki().name()).isEqualTo("小寒");      // 年をまたぐ：次の年の小寒
		assertThat(eve.nextSekki().days()).isEqualTo(5);
		assertThat(names(newYear)).containsExactly("元日");
	}

	@Test
	void theNextDayOfTheNewYearIsBackToTheKo() {
		HitokotoView view = calendarOnly(LocalDate.of(2026, 1, 2));

		assertThat(names(view)).containsExactly("雪下出麦");
		assertThat(view.nextSekki().days()).isEqualTo(3);
	}

	@Test
	void anEventComputedFromSekkiShowsOnlyTheEvent() {
		HitokotoView view = calendarOnly(LocalDate.of(2026, 3, 17));      // 春分 3/20 の3日前

		assertThat(kinds(view)).containsExactly(Kind.EVENT);
		assertThat(names(view)).containsExactly("春のお彼岸入り");
		assertThat(view.nextSekki().name()).isEqualTo("春分");
		assertThat(view.nextSekki().days()).isEqualTo(3);
	}

	@Test
	void setsubunIsTheDayBeforeRisshun() {
		assertThat(names(calendarOnly(LocalDate.of(2026, 2, 3)))).containsExactly("節分");
		assertThat(names(calendarOnly(LocalDate.of(2026, 2, 4)))).containsExactly("立春");
	}

	@Test
	void whenAnEventAndASekkiFallOnTheSameDayTheEventComesFirst() {
		HitokotoView tango = calendarOnly(LocalDate.of(2026, 5, 5));       // 端午の節句 + 立夏
		HitokotoView tanabata = calendarOnly(LocalDate.of(2026, 7, 7));    // 七夕 + 小暑

		assertThat(kinds(tango)).containsExactly(Kind.EVENT, Kind.SEKKI);
		assertThat(names(tango)).containsExactly("端午の節句", "立夏");
		assertThat(kinds(tanabata)).containsExactly(Kind.EVENT, Kind.SEKKI);
		assertThat(names(tanabata)).containsExactly("七夕", "小暑");
		assertThat(tanabata.items().get(0).text()).startsWith("今日は七夕。");
		assertThat(tanabata.items().get(1).text()).startsWith("今日は小暑。");
		assertThat(tanabata.nextSekki()).isNull();
	}

	@Test
	void eventsHaveNoReading() {
		assertThat(calendarOnly(LocalDate.of(2026, 7, 7)).items().get(0).reading()).isNull();
	}

	@Test
	void aDateOutsideTheTableStillShowsTheKoButNoNextSekki() {
		HitokotoView view = calendarOnly(LocalDate.of(2028, 3, 1));       // 表の外：「ごろ」の日付で動く

		assertThat(names(view)).containsExactly("草木萌動");
		assertThat(view.nextSekki()).isNull();
	}

	@Test
	void everyDayOf2026AndTheYearsAroundItShowsOneOrTwoItemsInTheRightOrder() {
		for (LocalDate date = LocalDate.of(2025, 1, 1); date.isBefore(LocalDate.of(2031, 1, 1)); date = date.plusDays(1)) {
			HitokotoView view = calendarOnly(date);
			assertThat(view.items()).as(date.toString()).isNotEmpty().hasSizeLessThanOrEqualTo(2);
			if (view.items().size() == 2) {
				assertThat(kinds(view)).as(date.toString()).containsExactly(Kind.EVENT, Kind.SEKKI);
			}
			if (kinds(view).contains(Kind.EVENT)) {
				assertThat(kinds(view)).as(date.toString()).doesNotContain(Kind.KO);
			}
			String day = date.toString();
			view.items().forEach(item -> assertThat(item.text()).as(day).isNotBlank());
		}
	}

	// ---- グラフへのひとこと ----

	@Test
	void noCategoriesGivesNoGraphCommentButStillTheCalendar() {
		HitokotoView view = service.hitokoto(LocalDate.of(2026, 11, 8), List.of(), null, 0);

		assertThat(view.graph()).isNull();
		assertThat(view.items()).isNotEmpty();
	}

	@Test
	void theGraphCommentIsMadeFromTheWeeklyNumbers() {
		// 予算なし・今週 3 件・先週 0 円 → 出せる型は「記録の件数」だけ
		List<Row> rows = List.of(new Row("食費", null, 4_500, 3, 0), new Row("交通費", null, 0, 0, 0));

		HitokotoView view = service.hitokoto(LocalDate.of(2026, 11, 8), rows, null, 4_500);

		assertThat(view.graph()).isNotNull();
		assertThat(view.graph().key()).isEqualTo("hitokoto.graph.count");
		assertThat(view.graph().arg0()).isEqualTo(3L);
		assertThat(view.graph().arg1()).isNull();
	}

	@Test
	void noTypeAvailableGivesNoGraphComment() {
		List<Row> rows = List.of(new Row("食費", null, 0, 0, 0));

		assertThat(service.hitokoto(LocalDate.of(2026, 11, 8), rows, null, 0).graph()).isNull();
	}

	@Test
	void graphLineKeysAndArguments() {
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.PER_DAY, null, 1_300, 4)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.perday", 1_300L, 4));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.NOT_OVER, null, 8, 5)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.notover", 8L, 5));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.ROOM, "交通費", 0, 7)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.room", "交通費", 7));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.LAST_WEEK, null, -1_200, 0)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.lastweek.less", 1_200L, null));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.LAST_WEEK, null, 1_200, 0)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.lastweek.more", 1_200L, null));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.LAST_WEEK, null, 0, 0)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.lastweek.same", null, null));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.COUNT, null, 12, 0)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.count", 12L, null));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.UNUSED, "交際費", 0, 0)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.unused", "交際費", null));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.WEEK_START, null, 13_800, 0)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.weekstart", 13_800L, null));
	}

	/** 週の予算以下の数：全部の科目が週の予算以下のときだけ、「すべて」の文に分ける（6 のうち 6、1 のうち 1）。ひとつ足りなければ、今の文 */
	@Test
	void notOverUsesTheAllSentenceOnlyWhenEveryCategoryIsWithinItsBudget() {
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.NOT_OVER, null, 6, 6)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.notover.all", 6L, null));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.NOT_OVER, null, 6, 5)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.notover", 6L, 5));
		assertThat(HitokotoService.graphLine(new GraphComment.Result(GraphComment.Type.NOT_OVER, null, 1, 1)))
				.isEqualTo(new HitokotoView.GraphLine("hitokoto.graph.notover.all", 1L, null));
	}

	// ---- issue 017：題の補足（「七十二候（立冬の初候）」の括弧の中） ----

	@Test
	void detailTellsWhichSekkiAndWhichKo() {
		assertThat(calendarOnly(LocalDate.of(2026, 11, 8)).items().get(0).detail()).isEqualTo("立冬の初候");
		assertThat(calendarOnly(LocalDate.of(2026, 11, 12)).items().get(0).detail()).isEqualTo("立冬の次候");
		assertThat(calendarOnly(LocalDate.of(2026, 11, 17)).items().get(0).detail()).isEqualTo("立冬の末候");
		assertThat(calendarOnly(LocalDate.of(2026, 2, 2)).items().get(0).detail()).isEqualTo("大寒の末候");     // 年の最後の候（2/3 は節分）
		assertThat(calendarOnly(LocalDate.of(2026, 2, 5)).items().get(0).detail()).isEqualTo("立春の初候");     // 年の最初の候
	}

	@Test
	void detailOfASekkiIsItsOwnNameAndAnEventHasNone() {
		HitokotoView sekki = calendarOnly(LocalDate.of(2026, 11, 7));
		HitokotoView event = calendarOnly(LocalDate.of(2026, 1, 1));
		HitokotoView both = calendarOnly(LocalDate.of(2026, 7, 7));

		assertThat(sekki.items().get(0).detail()).isEqualTo("立冬");
		assertThat(event.items().get(0).detail()).isNull();
		assertThat(both.items().get(0).detail()).isNull();       // 七夕
		assertThat(both.items().get(1).detail()).isEqualTo("小暑");
	}

}
