package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.yutaro081.householdbudget.domain.PieSlices.Entry;
import io.github.yutaro081.householdbudget.domain.PieSlices.Slice;

/**
 * 円グラフの並び・まとめ方・割合・区切りの単体テスト（設計書 5.11、要件定義書 4.5）。Spring もデータベースも使わない。
 * 入力は、科目の登録順に並べた「科目名と、切り上げた額」。0円の科目は出さない。金額の多い順（同額は登録順）。
 * 6科目以下は全部、7科目以上は上位5科目＋「その他」。割合は整数％に四捨五入。区切りは1000分率で、最後はちょうど1000。
 */
class PieSlicesTest {

	private static List<Slice> of(Entry... entries) {
		return PieSlices.of(List.of(entries));
	}

	@Test
	void noEntriesOrOnlyZeroGivesNoSlices() {
		assertThat(PieSlices.of(List.of())).isEmpty();
		assertThat(of(new Entry("食費", 0), new Entry("交通費", 0))).isEmpty();
	}

	@Test
	void oneEntryIsTheWholeCircle() {
		List<Slice> slices = of(new Entry("食費", 1000));

		assertThat(slices).hasSize(1);
		Slice s = slices.get(0);
		assertThat(s.name()).isEqualTo("食費");
		assertThat(s.amount()).isEqualTo(1000);
		assertThat(s.percent()).isEqualTo(100);
		assertThat(s.startPermille()).isZero();
		assertThat(s.endPermille()).isEqualTo(1000);
		assertThat(s.colorNumber()).isEqualTo(1);
		assertThat(s.mergedCount()).isEqualTo(1);
	}

	@Test
	void sortedByAmountDescendingWithPercentAndBoundaries() {
		List<Slice> slices = of(new Entry("B", 1200), new Entry("A", 3000), new Entry("C", 800));

		assertThat(slices).extracting(Slice::name).containsExactly("A", "B", "C");
		assertThat(slices).extracting(Slice::percent).containsExactly(60, 24, 16);
		assertThat(slices).extracting(Slice::startPermille).containsExactly(0, 600, 840);
		assertThat(slices).extracting(Slice::endPermille).containsExactly(600, 840, 1000);
		assertThat(slices).extracting(Slice::colorNumber).containsExactly(1, 2, 3);
	}

	@Test
	void zeroAmountCategoriesAreLeftOut() {
		List<Slice> slices = of(new Entry("A", 0), new Entry("B", 500));

		assertThat(slices).extracting(Slice::name).containsExactly("B");
	}

	@Test
	void sameAmountKeepsRegistrationOrder() {
		List<Slice> slices = of(new Entry("X", 100), new Entry("Y", 100), new Entry("Z", 200));

		assertThat(slices).extracting(Slice::name).containsExactly("Z", "X", "Y");
	}

	@Test
	void sixCategoriesAreAllShownAndTheSixthIsGray() {
		List<Slice> slices = of(new Entry("a", 600), new Entry("b", 500), new Entry("c", 400),
				new Entry("d", 300), new Entry("e", 200), new Entry("f", 100));

		assertThat(slices).hasSize(6);
		assertThat(slices).extracting(Slice::name).containsExactly("a", "b", "c", "d", "e", "f");
		assertThat(slices).extracting(Slice::colorNumber).containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(slices).extracting(Slice::mergedCount).containsOnly(1);
		assertThat(slices.get(5).endPermille()).isEqualTo(1000);
	}

	@Test
	void sevenOrMoreCategoriesBecomeTopFivePlusOther() {
		List<Slice> slices = of(new Entry("a", 700), new Entry("b", 600), new Entry("c", 500), new Entry("d", 400),
				new Entry("e", 300), new Entry("f", 200), new Entry("g", 100));

		assertThat(slices).hasSize(6);
		assertThat(slices).extracting(Slice::name).containsExactly("a", "b", "c", "d", "e", PieSlices.OTHER_NAME);
		Slice other = slices.get(5);
		assertThat(other.amount()).isEqualTo(300);            // f + g
		assertThat(other.mergedCount()).isEqualTo(2);
		assertThat(other.colorNumber()).isEqualTo(6);
		assertThat(other.percent()).isEqualTo(11);            // 300 / 2800 = 10.7 → 11
		assertThat(other.endPermille()).isEqualTo(1000);
		assertThat(slices.stream().mapToLong(Slice::amount).sum()).isEqualTo(2800);
	}

	@Test
	void manyCategoriesStillGiveSixSlices() {
		List<Entry> entries = new ArrayList<>();
		for (int i = 1; i <= 256; i++) {
			entries.add(new Entry("科目" + i, i * 100L));
		}
		List<Slice> slices = PieSlices.of(entries);

		assertThat(slices).hasSize(6);
		assertThat(slices.get(5).mergedCount()).isEqualTo(251);
		assertThat(slices.get(5).endPermille()).isEqualTo(1000);
	}

	@Test
	void percentIsRoundedHalfUp() {
		List<Slice> slices = of(new Entry("A", 700), new Entry("B", 100));   // 87.5 と 12.5

		assertThat(slices).extracting(Slice::percent).containsExactly(88, 13);   // 足して101になってもよい
	}

	@Test
	void equalThirdsHaveBoundariesThatEndAtExactly1000() {
		List<Slice> slices = of(new Entry("A", 100), new Entry("B", 100), new Entry("C", 100));

		assertThat(slices).extracting(Slice::percent).containsExactly(33, 33, 33);
		assertThat(slices).extracting(Slice::startPermille).containsExactly(0, 333, 666);
		assertThat(slices).extracting(Slice::endPermille).containsExactly(333, 666, 1000);
	}

	@Test
	void cssStopIsBuiltOnlyFromNumbersAndFixedText() {
		List<Slice> slices = of(new Entry("<script>\"x\";}</script>", 3000), new Entry("B", 2000));

		assertThat(slices.get(0).cssStop()).isEqualTo("var(--hb-chart-1) 0.0% 60.0%");
		assertThat(slices.get(1).cssStop()).isEqualTo("var(--hb-chart-2) 60.0% 100.0%");
		assertThat(PieSlices.cssStops(slices)).isEqualTo("var(--hb-chart-1) 0.0% 60.0%, var(--hb-chart-2) 60.0% 100.0%");
	}

	// ---- 判定は、円グラフの一覧に出さない（issue 024）。判定は、隣の「今週の進み具合」のチップだけで見せる ----

	/** 入力（Entry）も出力（Slice）も、色・予算内の印の項目を持たない。金額と割合（と円の色の番号）だけ */
	@Test
	void entryAndSliceCarryNoJudgement() {
		assertThat(java.util.Arrays.stream(Entry.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)).containsExactly("name", "amount");
		assertThat(java.util.Arrays.stream(Slice.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
				.containsExactly("name", "amount", "percent", "startPermille", "endPermille", "colorNumber", "mergedCount");
	}

	/** 8 科目のとき、上位 5 科目と「その他」にまとまる（判定がなくなっても、まとめ方は同じ） */
	@Test
	void theOtherSliceStillMergesTheRestWithoutAnyJudgement() {
		List<Entry> entries = new ArrayList<>();
		for (int i = 1; i <= 8; i++) {
			entries.add(new Entry("科目" + i, i * 100));
		}

		List<Slice> slices = PieSlices.of(entries);

		assertThat(slices).hasSize(6);
		assertThat(slices.get(5).name()).isEqualTo(PieSlices.OTHER_NAME);
		assertThat(slices.get(5).mergedCount()).isEqualTo(3);
		assertThat(slices).extracting(Slice::name).containsExactly("科目8", "科目7", "科目6", "科目5", "科目4", PieSlices.OTHER_NAME);
	}

}
