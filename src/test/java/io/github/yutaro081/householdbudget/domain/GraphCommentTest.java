package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yutaro081.householdbudget.domain.GraphComment.Row;
import io.github.yutaro081.householdbudget.domain.GraphComment.Type;

/**
 * グラフへのひとことの単体テスト（設計書 5.12、要件定義書 4.5）。Spring もデータベースも使わない。
 * 材料は、画面に出ている今週の数字（週の予算・100円単位に切り上げた実績）。文そのものは作らず、型と部品を返す。
 * 型は「最多」「残り」「未使用」。出せる型の中から、日付で1つ選ぶ（同じ日は、いつも同じ）。
 */
class GraphCommentTest {

	/** 2026年11月8日（日曜）。今週の残り日数は 6（土曜まで） */
	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

	private static Row row(String name, Long budget, long actual) {
		return new Row(name, budget, actual);
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

	private static List<Type> available(List<Row> rows) {
		return GraphComment.availableTypes(rows, budgetSum(rows), sum(rows));
	}

	// ---- 出せる型 ----

	@Test
	void noCategoriesGivesNoType() {
		assertThat(GraphComment.availableTypes(List.of(), null, 0)).isEmpty();
		assertThat(GraphComment.choose(List.of(), null, 0, SUNDAY)).isEmpty();
	}

	@Test
	void noTransactionsAndNoBudgetGivesNothing() {
		List<Row> rows = List.of(row("食費", null, 0), row("交通費", null, 0));

		assertThat(available(rows)).isEmpty();
		assertThat(GraphComment.choose(rows, budgetSum(rows), sum(rows), SUNDAY)).isEmpty();
	}

	@Test
	void noTransactionsButBudgetGivesOnlyTheRemainingType() {
		List<Row> rows = List.of(row("食費", 2_400L, 0), row("交通費", 1_000L, 0));

		assertThat(available(rows)).containsExactly(Type.REMAINING);
		var result = GraphComment.choose(rows, budgetSum(rows), sum(rows), SUNDAY).orElseThrow();
		assertThat(result.type()).isEqualTo(Type.REMAINING);
		assertThat(result.amount()).isEqualTo(3_400);
		assertThat(result.over()).isFalse();
		assertThat(result.daysLeft()).isEqualTo(6);
	}

	@Test
	void transactionsAndNoBudgetAtAllGiveOnlyTheTopType() {
		List<Row> rows = List.of(row("食費", null, 4_500), row("交通費", null, 0));

		assertThat(available(rows)).containsExactly(Type.TOP);
		var result = GraphComment.choose(rows, budgetSum(rows), sum(rows), SUNDAY).orElseThrow();
		assertThat(result.type()).isEqualTo(Type.TOP);
		assertThat(result.categoryName()).isEqualTo("食費");
		assertThat(result.amount()).isEqualTo(4_500);
	}

	@Test
	void transactionsAndBudgetsAndAnUnusedCategoryGiveAllThreeTypesInOrder() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("交際費", 1_000L, 0));

		assertThat(available(rows)).containsExactly(Type.TOP, Type.REMAINING, Type.UNUSED);
	}

	@Test
	void unusedNeedsABudgetGreaterThanZeroAndSomeSpendingThisWeek() {
		// 予算 0 円の科目、予算が未設定の科目は「未使用」の対象にしない
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("交際費", 0L, 0), row("雑費", null, 0));

		assertThat(available(rows)).containsExactly(Type.TOP, Type.REMAINING);
	}

	@Test
	void everyCategoryUsedMeansNoUnusedType() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("交通費", 1_000L, 100));

		assertThat(available(rows)).containsExactly(Type.TOP, Type.REMAINING);
	}

	// ---- 最多 ----

	@Test
	void topIsTheCategoryWithTheLargestRoundedActual() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("日用品", 2_400L, 4_500), row("交通費", 1_000L, 100));

		var result = GraphComment.choose(rows, budgetSum(rows), sum(rows), SUNDAY.plusDays(0));
		var top = GraphComment.result(Type.TOP, rows, budgetSum(rows), sum(rows), SUNDAY);

		assertThat(result).isPresent();
		assertThat(top.categoryName()).isEqualTo("日用品");
		assertThat(top.amount()).isEqualTo(4_500);
	}

	@Test
	void topTieGoesToTheEarlierCategory() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("日用品", 2_400L, 1_300));

		assertThat(GraphComment.result(Type.TOP, rows, budgetSum(rows), sum(rows), SUNDAY).categoryName()).isEqualTo("食費");
	}

	// ---- 残り ----

	@ParameterizedTest(name = "{0}：土曜まであと {1} 日")
	@CsvSource({
			"2026-11-08, 6",    // 日曜
			"2026-11-09, 5",
			"2026-11-10, 4",
			"2026-11-11, 3",
			"2026-11-12, 2",
			"2026-11-13, 1",
			"2026-11-14, 0"     // 土曜
	})
	void daysLeftUntilSaturday(LocalDate date, int daysLeft) {
		List<Row> rows = List.of(row("食費", 4_700L, 2_400));

		var result = GraphComment.result(Type.REMAINING, rows, budgetSum(rows), sum(rows), date);

		assertThat(result.daysLeft()).isEqualTo(daysLeft);
		assertThat(result.amount()).isEqualTo(2_300);
		assertThat(result.over()).isFalse();
	}

	@Test
	void remainingIsZeroWhenTheBudgetIsUsedUpExactly() {
		List<Row> rows = List.of(row("食費", 2_400L, 2_400));

		var result = GraphComment.result(Type.REMAINING, rows, budgetSum(rows), sum(rows), SUNDAY);

		assertThat(result.amount()).isZero();
		assertThat(result.over()).isFalse();
	}

	@Test
	void remainingBecomesTheExcessWhenOverBudget() {
		List<Row> rows = List.of(row("食費", 2_400L, 3_200));

		var result = GraphComment.result(Type.REMAINING, rows, budgetSum(rows), sum(rows), SUNDAY);

		assertThat(result.amount()).isEqualTo(800);
		assertThat(result.over()).isTrue();
	}

	@Test
	void remainingUsesTheSameTotalsAsTheScreen() {
		// 予算が設定済みの科目だけの週の予算の合計（2,400）と、全科目の実績の合計（1,300 + 500）
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("雑費", null, 500));

		var result = GraphComment.result(Type.REMAINING, rows, budgetSum(rows), sum(rows), SUNDAY);

		assertThat(result.amount()).isEqualTo(600);
	}

	// ---- 未使用 ----

	@Test
	void unusedIsTheUnusedCategoryWithTheLargestWeeklyBudget() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("交際費", 1_000L, 0), row("趣味", 3_000L, 0),
				row("衣類", 3_000L, 0));

		var result = GraphComment.result(Type.UNUSED, rows, budgetSum(rows), sum(rows), SUNDAY);

		assertThat(result.categoryName()).isEqualTo("趣味");     // 同額は登録順
	}

	// ---- 選び方：日付で順番に回す ----

	@Test
	void theTypeIsChosenFromTheDateAndStaysTheSameOnTheSameDay() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("交際費", 1_000L, 0));
		List<Type> available = available(rows);

		for (int i = 0; i < 10; i++) {
			LocalDate date = SUNDAY.plusDays(i);
			Type expected = available.get((int) Math.floorMod(date.toEpochDay(), (long) available.size()));
			var first = GraphComment.choose(rows, budgetSum(rows), sum(rows), date).orElseThrow();
			var second = GraphComment.choose(rows, budgetSum(rows), sum(rows), date).orElseThrow();
			assertThat(first.type()).as(date.toString()).isEqualTo(expected);
			assertThat(second).isEqualTo(first);
		}
	}

	@Test
	void consecutiveDaysCycleThroughEveryAvailableType() {
		List<Row> rows = List.of(row("食費", 2_400L, 1_300), row("交際費", 1_000L, 0));
		Set<Type> seen = EnumSet.noneOf(Type.class);

		for (int i = 0; i < 3; i++) {
			seen.add(GraphComment.choose(rows, budgetSum(rows), sum(rows), SUNDAY.plusDays(i)).orElseThrow().type());
		}

		assertThat(seen).containsExactlyInAnyOrder(Type.TOP, Type.REMAINING, Type.UNUSED);
	}

	@Test
	void aSingleAvailableTypeIsAlwaysChosen() {
		List<Row> rows = List.of(row("食費", null, 4_500));

		for (int i = 0; i < 7; i++) {
			assertThat(GraphComment.choose(rows, null, 4_500, SUNDAY.plusDays(i)).orElseThrow().type()).isEqualTo(Type.TOP);
		}
	}

}
