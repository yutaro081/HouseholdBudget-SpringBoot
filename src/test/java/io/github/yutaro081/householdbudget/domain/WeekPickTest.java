package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yutaro081.householdbudget.domain.WeekPick.Candidate;

/**
 * 今週の科目のひとこと：科目の選び方の単体テスト（設計書 5.15、要件定義書 4.5、issue 020）。Spring もデータベースも使わない。
 * 週の予算が未設定・0 円の科目は除く。週の予算に対する今週の実績の割合（どちらも 100 円単位に切り上げた額）がいちばん高い科目。
 * 同じなら科目の並び順で先。週の予算を超えていなくても選ぶ。今週の実績が 0 円の科目は選ばない（取引が 1 件もなければ、誰も選ばれない）。
 */
class WeekPickTest {

	private static Candidate c(String name, Long budget, long actual) {
		return new Candidate(name, budget, actual);
	}

	@Test
	void picksTheHighestRatioOfTheActualToTheWeeklyBudget() {
		List<Candidate> list = List.of(c("食費", 10_000L, 3_000), c("交通費", 5_000L, 2_500), c("雑費", 4_000L, 1_000));

		assertThat(WeekPick.choose(list)).hasValue(1);        // 30％、50％、25％
	}

	@Test
	void picksEvenWhenNothingIsOverTheBudget() {
		List<Candidate> list = List.of(c("食費", 10_000L, 100), c("交通費", 5_000L, 200));

		assertThat(WeekPick.choose(list)).as("1％ と 4％。どちらも予算内でも、高いほうを選ぶ").hasValue(1);
	}

	@Test
	void picksOneOverTheBudgetToo() {
		List<Candidate> list = List.of(c("食費", 10_000L, 9_000), c("交通費", 5_000L, 7_500));

		assertThat(WeekPick.choose(list)).as("90％ と 150％").hasValue(1);
	}

	/** 割合が同じなら、科目の並び順で先のもの（割合は、かけ算で比べる） */
	@Test
	void whenTheRatiosAreEqualTheEarlierCategoryWins() {
		List<Candidate> list = List.of(c("食費", 10_000L, 5_000), c("交通費", 5_000L, 2_500), c("雑費", 2_000L, 1_000));

		assertThat(WeekPick.choose(list)).as("3 つとも 50％").hasValue(0);
		assertThat(WeekPick.choose(List.of(c("食費", 10_000L, 4_000), c("交通費", 5_000L, 2_500), c("雑費", 2_000L, 1_000)))).as("40％、50％、50％").hasValue(1);
	}

	@Test
	void ratiosAreComparedExactlyWithoutRounding() {
		// 3,333 ÷ 10,000 ＝ 33.33％、1,667 ÷ 5,000 ＝ 33.34％：わずかに交通費のほうが高い
		List<Candidate> list = List.of(c("食費", 10_000L, 3_333), c("交通費", 5_000L, 1_667));

		assertThat(WeekPick.choose(list)).hasValue(1);
	}

	@Test
	void categoriesWithoutAWeeklyBudgetOrWithAZeroBudgetAreLeftOut() {
		List<Candidate> list = List.of(c("未設定", null, 9_000), c("ゼロ", 0L, 9_000), c("食費", 10_000L, 100));

		assertThat(WeekPick.choose(list)).as("予算がない科目がたくさん使っていても、除く").hasValue(2);
	}

	@Test
	void nothingIsPickedWhenNoCategoryHasAWeeklyBudget() {
		assertThat(WeekPick.choose(List.of(c("未設定", null, 9_000), c("ゼロ", 0L, 9_000)))).isEmpty();
	}

	/** 今週の取引が 1 件もない（どの科目の実績も 0 円）ときは、選ばない */
	@Test
	void nothingIsPickedWhenThereIsNoSpendingThisWeek() {
		assertThat(WeekPick.choose(List.of(c("食費", 10_000L, 0), c("交通費", 5_000L, 0)))).isEmpty();
	}

	/** 週の予算のある科目が、どれも今週使っていないときは、選ばない（予算のない科目だけが使っていても） */
	@Test
	void nothingIsPickedWhenOnlyCategoriesWithoutABudgetHaveSpending() {
		assertThat(WeekPick.choose(List.of(c("食費", 10_000L, 0), c("未設定", null, 5_000)))).isEmpty();
	}

	@Test
	void aCategoryWithNoSpendingIsNotPickedEvenIfItIsTheOnlyOneWithABudget() {
		assertThat(WeekPick.choose(List.of(c("食費", 10_000L, 0)))).isEmpty();
	}

	@Test
	void anEmptyListPicksNothing() {
		assertThat(WeekPick.choose(List.of())).isEmpty();
	}

	@Test
	void hugeAmountsAreComparedWithoutOverflow() {
		List<Candidate> list = List.of(c("食費", 700_000_000L, 700_000_000L), c("交通費", 1_000_000L, 2_000_000L));

		assertThat(WeekPick.choose(list)).as("100％ と 200％").hasValue(1);
	}
}
