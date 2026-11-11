package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.yutaro081.householdbudget.domain.MeterScale.Meter;

/**
 * メーターの縮尺の単体テスト（設計書 2.3「メーターの見た目」、5.11、issue 020）。
 * 予算（100％）を、棒全体の 70％（700）の位置に置く。棒の右端は約 143％。この 70％ は、MeterScale だけで決める。
 * すべて 1000分率の整数（切り捨て）。Spring もデータベースも使わない。
 */
class MeterScaleTest {

	@Test
	void theBudgetIsAt70PercentOfTheBar() {
		assertThat(MeterScale.BUDGET_PERMILLE).isEqualTo(700);
		assertThat(MeterScale.of(2_400L, 0, 0).budgetPermille()).isEqualTo(700);
	}

	/** 予算 2,400 円：実績 1,200（50％）→ 棒の 35％。超えていない */
	@ParameterizedTest(name = "予算 {0}・実績 {1} → 通常色 {2}、赤 {3}")
	@CsvSource({
			"2400,    0,     0,   0",
			"2400,    1200,  350, 0",
			"2400,    2399,  699, 0",
			"2400,    2400,  700, 0",       // ちょうど予算：超えていない
			"2400,    3000,  700, 175",     // 125％ → 875。赤は 700 から 875 まで（175）
			"2400,    3600,  700, 300",     // 150％ → 1050 → 1000 で止まる。赤は 300
			"2400,    24000, 700, 300"       // 10 倍でも、右端で止まる
	})
	void barSplitsIntoTheNormalPartAndTheRedPart(long budget, long actual, int normal, int over) {
		Meter m = MeterScale.of(budget, actual, 0);

		assertThat(m.normalPermille()).isEqualTo(normal);
		assertThat(m.overPermille()).isEqualTo(over);
	}

	/** 予算があるときの赤は、予算の位置（70％）から始まる */
	@Test
	void theRedPartStartsAtTheBudgetPositionWhenThereIsABudget() {
		assertThat(MeterScale.of(2_400L, 3_000, 0).overStartPermille()).isEqualTo(700);
	}

	@Test
	void overIsTrueOnlyWhenTheActualExceedsTheBudget() {
		assertThat(MeterScale.of(2_400L, 2_400, 0).over()).isFalse();
		assertThat(MeterScale.of(2_400L, 2_401, 0).over()).as("1 円超えただけでも、超えている（赤い部分の長さは 0 に近くても出す）").isTrue();
		assertThat(MeterScale.of(2_400L, 100, 0).over()).isFalse();
	}

	/** 143％ を超えたら（実績 × 700 ＞ 予算 × 1000）、「+○%」。○ ＝ （実績 − 予算）÷ 予算の四捨五入 */
	@ParameterizedTest(name = "予算 {0}・実績 {1} → +{2}%")
	@CsvSource({ "7000, 10001, 43", "2400, 3600, 50", "2400, 4800, 100", "10000, 25000, 150" })
	void overflowPercentIsShownOnlyOnceTheBarIsCapped(long budget, long actual, int percent) {
		assertThat(MeterScale.of(budget, actual, 0).overflowPercent()).isEqualTo(percent);
	}

	@ParameterizedTest(name = "予算 {0}・実績 {1} → 「+○%」なし")
	@CsvSource({ "10000, 0", "10000, 5000", "10000, 10000", "10000, 10000", "7000, 10000", "2400, 3000" })
	void noOverflowPercentWhileTheBarFits(long budget, long actual) {
		assertThat(MeterScale.of(budget, actual, 0).overflowPercent()).isNull();
	}

	/** 「今日」の線も同じ縮尺：日が進んだ割合（1000分率）× 0.7。季節の 79％ が過ぎた日（791）は、約 55％ */
	@ParameterizedTest(name = "日の進み {0} → 今日の線 {1}")
	@CsvSource({ "0, 0", "142, 99", "758, 530", "791, 553", "1000, 700" })
	void theTodayLineUsesTheSameScale(int elapsed, int expected) {
		assertThat(MeterScale.of(2_400L, 0, elapsed).todayPermille()).isEqualTo(expected);
	}

	@Test
	void anUnsetBudgetGivesAnEmptyMeter() {
		Meter m = MeterScale.of(null, 5_000, 500);

		assertThat(m.normalPermille()).isZero();
		assertThat(m.overPermille()).isZero();
		assertThat(m.over()).isFalse();
		assertThat(m.overflowPercent()).isNull();
	}

	/** 予算 0 円：実績がなければ空。あれば、使った分は全部が予算超えなので、棒の全体（左端から右端まで）を赤にする（割合が決められないので「+○%」は出さない） */
	@Test
	void aZeroBudgetMakesTheWholeBarRedWhenThereIsSpending() {
		Meter none = MeterScale.of(0L, 0, 0);
		Meter some = MeterScale.of(0L, 500, 0);

		assertThat(none.normalPermille()).isZero();
		assertThat(none.overPermille()).isZero();
		assertThat(none.over()).isFalse();
		assertThat(some.normalPermille()).as("通常色の部分はない").isZero();
		assertThat(some.overStartPermille()).as("赤は左端から").isZero();
		assertThat(some.overPermille()).as("右端まで").isEqualTo(1000);
		assertThat(some.over()).isTrue();
		assertThat(some.overflowPercent()).isNull();
	}

	@Test
	void hugeAmountsDoNotOverflow() {
		Meter m = MeterScale.of(3_000_000L, 700_000_000_000L, 1000);

		assertThat(m.normalPermille()).isEqualTo(700);
		assertThat(m.overPermille()).isEqualTo(300);
		assertThat(m.overflowPercent()).isEqualTo(23_333_233);
	}
}
