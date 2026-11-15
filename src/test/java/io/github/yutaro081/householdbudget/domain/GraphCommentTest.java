package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.yutaro081.householdbudget.domain.GraphComment.Row;
import io.github.yutaro081.householdbudget.domain.GraphComment.Type;

/**
 * グラフへのひとことの単体テスト（設計書 5.12、要件定義書 4.5、issue 028）。Spring もデータベースも使わない。
 * 材料は、科目ごとの今週の数字（週の予算・100円単位に切り上げた実績・取引の件数）と、先週の同じ曜日までの実績（切り上げた額）。
 * 文そのものは作らず、型と部品を返す。型は「1日あたり」「週の予算以下の数」「残りの割合」「先週と比べる」「記録の件数」「未使用」「週の始め」。
 * 週の始めが出せる日は、それだけ。ほかの日は、出せる型の中から、日付で1つ選ぶ（同じ日は、いつも同じ）。
 */
class GraphCommentTest {

	/** 2026年11月8日（日曜）。今週は 11/8〜11/14 */
	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
	/** 2026年11月11日（水曜）。今日を入れて あと 4 日 */
	private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);

	/** 今週使っていない・先週もない科目 */
	private static Row row(String name, Long budget) {
		return new Row(name, budget, 0, 0, 0);
	}

	/** 今週 1 件、actual 円（切り上げた額）使った科目。先週の分はない */
	private static Row used(String name, Long budget, long actual) {
		return new Row(name, budget, actual, 1, 0);
	}

	private static long sum(List<Row> rows) {
		return rows.stream().mapToLong(Row::actual).sum();
	}

	private static Long budgetSum(List<Row> rows) {
		long sum = 0;
		boolean any = false;
		for (Row r : rows) {
			if (r.budget() != null) {
				sum += r.budget();
				any = true;
			}
		}
		return any ? sum : null;
	}

	private static List<Type> available(List<Row> rows, LocalDate today) {
		return GraphComment.availableTypes(rows, budgetSum(rows), sum(rows), today);
	}

	private static GraphComment.Result result(Type type, List<Row> rows, LocalDate today) {
		return GraphComment.result(type, rows, budgetSum(rows), sum(rows), today);
	}

	// ---- 出せる型がない ----

	@Test
	void noCategoriesGivesNoType() {
		assertThat(GraphComment.availableTypes(List.of(), null, 0, WEDNESDAY)).isEmpty();
		assertThat(GraphComment.choose(List.of(), null, 0, WEDNESDAY)).isEmpty();
	}

	@Test
	void noTransactionsNoBudgetAndNoLastWeekGivesNothing() {
		List<Row> rows = List.of(row("食費", null), row("交通費", null));

		assertThat(available(rows, WEDNESDAY)).isEmpty();
		assertThat(GraphComment.choose(rows, budgetSum(rows), sum(rows), WEDNESDAY)).isEmpty();
	}

	// ---- 1 日あたり ----

	@ParameterizedTest(name = "{0}：今日を入れて あと {1} 日")
	@CsvSource({
			"2026-11-08, 7",    // 日曜
			"2026-11-09, 6",
			"2026-11-10, 5",
			"2026-11-11, 4",
			"2026-11-12, 3",
			"2026-11-13, 2",
			"2026-11-14, 1"     // 土曜
	})
	void daysLeftIncludeToday(LocalDate date, int days) {
		List<Row> rows = List.of(used("食費", 70_000L, 700));   // 残り 69,300 円

		var result = result(Type.PER_DAY, rows, date);

		assertThat(available(rows, date)).contains(Type.PER_DAY);
		assertThat(result.number()).isEqualTo(days);
		assertThat(result.amount()).isEqualTo(69_300 / days / 100 * 100);
	}

	@Test
	void perDayIsRoundedDownTo100Yen() {
		List<Row> rows = List.of(used("食費", 2_400L, 1_300));   // 残り 1,100 円 ÷ 4 日 = 275 円 → 200 円

		assertThat(result(Type.PER_DAY, rows, WEDNESDAY).amount()).isEqualTo(200);
	}

	@Test
	void perDayUsesTheBudgetTotalOfBudgetedCategoriesAndTheActualOfEveryCategory() {
		// 週の予算の合計 2,400（設定済みの科目だけ）、実績の合計 1,300 + 500 → 残り 600 円 ÷ 4 日 = 150 → 100 円
		List<Row> rows = List.of(used("食費", 2_400L, 1_300), used("雑費", null, 500));

		assertThat(result(Type.PER_DAY, rows, WEDNESDAY).amount()).isEqualTo(100);
	}

	@ParameterizedTest(name = "実績 {0} 円（週の予算 2,400 円、あと 4 日）：1 日あたりは出ない")
	@ValueSource(longs = { 2_100, 2_400, 3_000 })     // 残り 300（÷4 = 75 → 0 円）、使い切った、超えた
	void noPerDayWhenItRoundsDownToZeroOrBelow(long actual) {
		List<Row> rows = List.of(used("食費", 2_400L, actual));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.PER_DAY);
	}

	@Test
	void noPerDayWithoutAnyBudget() {
		List<Row> rows = List.of(used("食費", null, 1_000));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.PER_DAY);
	}

	// ---- 週の予算以下の数 ----

	@Test
	void notOverCountsBudgetedCategoriesWithinTheirBudgetIncludingUnusedOnes() {
		List<Row> rows = List.of(used("食費", 2_400L, 3_000),      // 超えた：数えない
				used("交通費", 1_000L, 1_000),                      // ちょうど：数える
				row("交際費", 1_000L),                              // 使っていない：数える
				used("雑費", null, 500),                            // 予算なし：分母にも入れない
				row("趣味", 0L));                                   // 予算 0 円：分母にも入れない

		var result = result(Type.NOT_OVER, rows, WEDNESDAY);

		assertThat(available(rows, WEDNESDAY)).contains(Type.NOT_OVER);
		assertThat(result.amount()).as("週の予算が 0 円より大きい科目の数").isEqualTo(3);
		assertThat(result.number()).as("そのうち週の予算以下の科目の数").isEqualTo(2);
	}

	@Test
	void noNotOverWithoutAnyTransactionThisWeek() {
		List<Row> rows = List.of(row("食費", 2_400L), row("交通費", 1_000L));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.NOT_OVER);
	}

	@Test
	void noNotOverWhenEveryBudgetedCategoryIsOver() {
		List<Row> rows = List.of(used("食費", 2_400L, 3_000), used("交通費", 1_000L, 1_100));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.NOT_OVER);
	}

	// ---- 残りの割合 ----

	@Test
	void roomIsTheUsedCategoryWithTheLargestShareLeftInTenths() {
		List<Row> rows = List.of(used("食費", 2_400L, 1_300),     // 残り 1,100 / 2,400 → 4 割
				used("交通費", 1_000L, 200),                      // 残り 800 / 1,000 → 8 割
				row("交際費", 1_000L));                           // 使っていない：対象外（10 割でも選ばない）

		var result = result(Type.ROOM, rows, WEDNESDAY);

		assertThat(available(rows, WEDNESDAY)).contains(Type.ROOM);
		assertThat(result.categoryName()).isEqualTo("交通費");
		assertThat(result.number()).isEqualTo(8);
	}

	@Test
	void roomIsRoundedDownToTenths() {
		List<Row> rows = List.of(used("食費", 2_400L, 300));      // 残り 2,100 / 2,400 = 8.75 割 → 8 割

		assertThat(result(Type.ROOM, rows, WEDNESDAY).number()).isEqualTo(8);
	}

	@Test
	void roomTieGoesToTheEarlierCategory() {
		List<Row> rows = List.of(used("食費", 2_000L, 1_000), used("日用品", 1_000L, 500));   // どちらも 5 割

		assertThat(result(Type.ROOM, rows, WEDNESDAY).categoryName()).isEqualTo("食費");
	}

	@ParameterizedTest(name = "実績 {0} 円（週の予算 2,400 円）：残りの割合は出ない")
	@ValueSource(longs = { 2_200, 2_400, 3_000 })     // 残り 200（0.8 割 → 0 割）、ちょうど、超えた
	void noRoomWhenTheShareLeftIsZeroTenthsOrBelow(long actual) {
		List<Row> rows = List.of(used("食費", 2_400L, actual));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.ROOM);
	}

	@Test
	void noRoomWhenTheOnlyCategoryWithRoomIsUnused() {
		List<Row> rows = List.of(used("食費", 2_400L, 2_400), row("交際費", 1_000L), used("雑費", null, 100));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.ROOM);
	}

	// ---- 先週と比べる ----

	@Test
	void lastWeekGivesTheDifferenceOfTheTotals() {
		List<Row> rows = List.of(new Row("食費", null, 1_300, 1, 2_000), new Row("日用品", null, 500, 1, 1_000));

		var result = result(Type.LAST_WEEK, rows, WEDNESDAY);

		assertThat(available(rows, WEDNESDAY)).contains(Type.LAST_WEEK);
		assertThat(result.amount()).as("今週 1,800 − 先週 3,000").isEqualTo(-1_200);
	}

	@Test
	void lastWeekCanBeMoreOrTheSame() {
		List<Row> more = List.of(new Row("食費", null, 3_000, 2, 1_000));
		List<Row> same = List.of(new Row("食費", null, 1_000, 1, 1_000));

		assertThat(result(Type.LAST_WEEK, more, WEDNESDAY).amount()).isEqualTo(2_000);
		assertThat(available(same, WEDNESDAY)).contains(Type.LAST_WEEK);
		assertThat(result(Type.LAST_WEEK, same, WEDNESDAY).amount()).isZero();
	}

	@Test
	void lastWeekIsShownEvenWithoutTransactionsThisWeek() {
		List<Row> rows = List.of(new Row("食費", null, 0, 0, 1_000));

		assertThat(available(rows, WEDNESDAY)).containsExactly(Type.LAST_WEEK);
		assertThat(result(Type.LAST_WEEK, rows, WEDNESDAY).amount()).isEqualTo(-1_000);
	}

	@Test
	void noLastWeekWhenLastWeekWasZero() {
		List<Row> rows = List.of(used("食費", null, 1_000));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.LAST_WEEK);
	}

	// ---- 記録の件数 ----

	@Test
	void countIsTheSumOfTheTransactionsOfEveryCategory() {
		List<Row> rows = List.of(new Row("食費", null, 1_300, 3, 0), new Row("日用品", null, 500, 2, 0), row("雑費", null));

		assertThat(available(rows, WEDNESDAY)).containsExactly(Type.COUNT);
		assertThat(result(Type.COUNT, rows, WEDNESDAY).amount()).isEqualTo(5);
	}

	@Test
	void noCountWithoutTransactions() {
		List<Row> rows = List.of(row("食費", null), new Row("日用品", null, 0, 0, 500));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.COUNT);
	}

	// ---- 未使用（issue 016 のまま） ----

	@Test
	void unusedIsTheUnusedCategoryWithTheLargestWeeklyBudget() {
		List<Row> rows = List.of(used("食費", 2_400L, 1_300), row("交際費", 1_000L), row("趣味", 3_000L), row("衣類", 3_000L));

		assertThat(available(rows, WEDNESDAY)).contains(Type.UNUSED);
		assertThat(result(Type.UNUSED, rows, WEDNESDAY).categoryName()).isEqualTo("趣味");     // 同額は登録順
	}

	@Test
	void unusedNeedsABudgetGreaterThanZero() {
		// 予算 0 円の科目、予算が未設定の科目は「未使用」の対象にしない
		List<Row> rows = List.of(used("食費", 2_400L, 1_300), row("交際費", 0L), row("雑費", null));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.UNUSED);
	}

	@Test
	void noUnusedWithoutAnyTransactionThisWeek() {
		List<Row> rows = List.of(row("食費", 2_400L), row("交際費", 1_000L));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.UNUSED);
	}

	@Test
	void everyCategoryUsedMeansNoUnusedType() {
		List<Row> rows = List.of(used("食費", 2_400L, 1_300), used("交通費", 1_000L, 100));

		assertThat(available(rows, WEDNESDAY)).doesNotContain(Type.UNUSED);
	}

	// ---- 週の始め ----

	@Test
	void onASundayWithNoTransactionAndABudgetOnlyTheWeekStartIsShown() {
		List<Row> rows = List.of(row("食費", 2_400L), row("交通費", 1_000L), new Row("日用品", null, 0, 0, 500));

		assertThat(available(rows, SUNDAY)).as("1 日あたり・先週と比べるも出せるが、出さない").containsExactly(Type.WEEK_START);
		var result = GraphComment.choose(rows, budgetSum(rows), sum(rows), SUNDAY).orElseThrow();
		assertThat(result.type()).isEqualTo(Type.WEEK_START);
		assertThat(result.amount()).as("週の予算の合計").isEqualTo(3_400);
	}

	@Test
	void theSameNumbersOnAMondayGiveTheOtherTypesInsteadOfTheWeekStart() {
		List<Row> rows = List.of(row("食費", 2_400L), row("交通費", 1_000L), new Row("日用品", null, 0, 0, 500));

		assertThat(available(rows, SUNDAY.plusDays(1))).containsExactly(Type.PER_DAY, Type.LAST_WEEK);
	}

	@Test
	void noWeekStartOnASundayWithATransaction() {
		List<Row> rows = List.of(used("食費", 2_400L, 100));

		assertThat(available(rows, SUNDAY)).doesNotContain(Type.WEEK_START).contains(Type.PER_DAY, Type.COUNT);
	}

	@Test
	void noWeekStartWhenTheBudgetTotalIsZeroOrUnset() {
		assertThat(available(List.of(row("食費", 0L)), SUNDAY)).isEmpty();
		assertThat(available(List.of(row("食費", null)), SUNDAY)).isEmpty();
	}

	// ---- 並べる順と、日付で選ぶこと ----

	@Test
	void everyTypeExceptTheWeekStartIsListedInTheFixedOrder() {
		List<Row> rows = List.of(new Row("食費", 2_400L, 1_300, 2, 1_000), row("交際費", 1_000L));

		assertThat(available(rows, WEDNESDAY)).containsExactly(Type.PER_DAY, Type.NOT_OVER, Type.ROOM, Type.LAST_WEEK,
				Type.COUNT, Type.UNUSED);
	}

	@Test
	void theTypeIsChosenFromTheDateAndStaysTheSameOnTheSameDay() {
		List<Row> rows = List.of(new Row("食費", 2_400L, 1_300, 2, 1_000), row("交際費", 1_000L));

		for (int i = 1; i < 7; i++) {      // 月曜〜土曜（どの日も、出せる型は同じ 6 つ）
			LocalDate date = SUNDAY.plusDays(i);
			List<Type> types = available(rows, date);
			Type expected = types.get((int) Math.floorMod(date.toEpochDay(), (long) types.size()));
			var first = GraphComment.choose(rows, budgetSum(rows), sum(rows), date).orElseThrow();
			var second = GraphComment.choose(rows, budgetSum(rows), sum(rows), date).orElseThrow();
			assertThat(first.type()).as(date.toString()).isEqualTo(expected);
			assertThat(second).isEqualTo(first);
		}
	}

	@Test
	void consecutiveDaysCycleThroughEveryAvailableType() {
		List<Row> rows = List.of(used("食費", 2_400L, 1_300), row("交際費", 1_000L));   // 1日あたり・以下の数・残りの割合・件数・未使用
		Set<Type> seen = EnumSet.noneOf(Type.class);

		for (int i = 0; i < 5; i++) {
			seen.add(GraphComment.choose(rows, budgetSum(rows), sum(rows), WEDNESDAY.plusDays(i)).orElseThrow().type());
		}

		assertThat(seen).containsExactlyInAnyOrder(Type.PER_DAY, Type.NOT_OVER, Type.ROOM, Type.COUNT, Type.UNUSED);
	}

	@Test
	void aSingleAvailableTypeIsAlwaysChosen() {
		List<Row> rows = List.of(used("食費", null, 4_500));

		for (int i = 0; i < 7; i++) {
			assertThat(GraphComment.choose(rows, null, 4_500, SUNDAY.plusDays(i)).orElseThrow().type()).isEqualTo(Type.COUNT);
		}
	}

}
