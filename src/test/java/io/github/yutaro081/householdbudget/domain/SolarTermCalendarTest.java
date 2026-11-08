package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 二十四節気・七十二候の計算の単体テスト（設計書 5.12、要件定義書 4.5）。Spring もデータベースも使わない。
 * 節気の日 +0日＝節気の日、+1〜4日＝初候、+5〜9日＝次候、+10日〜次の節気の前の日＝末候。
 * 七十二候の通し番号は、立春の初候を1、大寒の末候を72とする。
 */
class SolarTermCalendarTest {

	private final SolarTermCalendar calendar = SolarTermTestData.calendar();

	private static int index(String name) {
		return SolarTermCalendar.NAMES.indexOf(name);
	}

	@Test
	void namesAreInOrderFromRisshunToDaikan() {
		assertThat(SolarTermCalendar.NAMES).hasSize(24);
		assertThat(SolarTermCalendar.NAMES.get(0)).isEqualTo("立春");
		assertThat(SolarTermCalendar.NAMES.get(3)).isEqualTo("春分");
		assertThat(SolarTermCalendar.NAMES.get(15)).isEqualTo("秋分");
		assertThat(SolarTermCalendar.NAMES.get(23)).isEqualTo("大寒");
	}

	/** 節気の当日（2026年の24個すべて）は、その節気の日になる（候は出ない） */
	@Test
	void everySekkiDayOf2026IsASekkiDay() {
		var table = SolarTermTestData.year(2026, SolarTermTestData.Y2026);
		for (var entry : table.entrySet()) {
			var position = calendar.positionOn(entry.getValue());
			assertThat(position.isSekkiDay()).as(entry.getKey()).isTrue();
			assertThat(position.sekkiIndex()).as(entry.getKey()).isEqualTo(index(entry.getKey()));
			assertThat(position.daysSince()).isZero();
			assertThat(position.approximate()).isFalse();
		}
	}

	/** 立冬（2026年11月7日）のあと：候の境目 */
	@ParameterizedTest(name = "{0} は 立冬から {1} 日後、七十二候 {2} 番")
	@CsvSource({
			"2026-11-08, 1, 55",    // 初候（山茶始開）。当日の翌日から
			"2026-11-11, 4, 55",    // 初候の最後
			"2026-11-12, 5, 56",    // 次候（地始凍）
			"2026-11-16, 9, 56",    // 次候の最後
			"2026-11-17, 10, 57",   // 末候（金盞香）
			"2026-11-21, 14, 57"    // 末候の最後（小雪の前の日）
	})
	void koBoundariesAfterRitto(LocalDate date, int daysSince, int koNumber) {
		var position = calendar.positionOn(date);

		assertThat(position.isSekkiDay()).isFalse();
		assertThat(position.sekkiIndex()).isEqualTo(index("立冬"));
		assertThat(position.daysSince()).isEqualTo(daysSince);
		assertThat(position.koNumber()).isEqualTo(koNumber);
		assertThat(position.approximate()).isFalse();
	}

	@Test
	void theNextSekkiDayIsASekkiDayAgain() {
		var position = calendar.positionOn(LocalDate.of(2026, 11, 22));

		assertThat(position.isSekkiDay()).isTrue();
		assertThat(position.sekkiIndex()).isEqualTo(index("小雪"));
	}

	/** 年をまたぐ：1月の初めは、前の年の冬至から数える */
	@ParameterizedTest(name = "{0} は 冬至から {1} 日後、七十二候 {2} 番")
	@CsvSource({
			"2025-12-27, 5, 65",    // 次候（麋角解）
			"2025-12-31, 9, 65",
			"2026-01-01, 10, 66",   // 末候（雪下出麦）
			"2026-01-02, 11, 66",
			"2026-01-04, 13, 66"    // 小寒の前の日
	})
	void koAcrossTheNewYear(LocalDate date, int daysSince, int koNumber) {
		var position = calendar.positionOn(date);

		assertThat(position.sekkiIndex()).isEqualTo(index("冬至"));
		assertThat(position.daysSince()).isEqualTo(daysSince);
		assertThat(position.koNumber()).isEqualTo(koNumber);
		assertThat(position.approximate()).isFalse();
	}

	/** 大寒の末候（72番）は、立春の前の日まで */
	@Test
	void theLastKoEndsTheDayBeforeRisshun() {
		var position = calendar.positionOn(LocalDate.of(2026, 2, 3));

		assertThat(position.sekkiIndex()).isEqualTo(index("大寒"));
		assertThat(position.koNumber()).isEqualTo(72);
		assertThat(calendar.positionOn(LocalDate.of(2026, 2, 4)).isSekkiDay()).isTrue();
		assertThat(calendar.positionOn(LocalDate.of(2026, 2, 5)).koNumber()).isEqualTo(1);
	}

	@Test
	void koNumberOfASekkiDayIsNotAvailable() {
		var position = calendar.positionOn(LocalDate.of(2026, 11, 7));

		assertThatThrownBy(position::koNumber).isInstanceOf(IllegalStateException.class);
	}

	// ---- 次の節気 ----

	@ParameterizedTest(name = "{0} の次の節気は {1}、あと {2} 日")
	@CsvSource({
			"2026-11-08, 小雪, 14",
			"2026-11-07, 小雪, 15",     // 節気の当日も、次は「その次の節気」
			"2026-11-21, 小雪, 1",
			"2026-02-03, 立春, 1",
			"2026-12-31, 小寒, 5",      // 年をまたぐ（次の年の小寒 2027-01-05）
			"2026-01-01, 小寒, 4"
	})
	void nextSekki(LocalDate date, String name, int daysUntil) {
		var next = calendar.nextAfter(date);

		assertThat(next).isPresent();
		assertThat(next.get().sekkiIndex()).isEqualTo(index(name));
		assertThat(next.get().daysUntil()).isEqualTo(daysUntil);
	}

	// ---- 表の外の年（代わりの計算：文面の「ごろ」の月日） ----

	@Test
	void yearsOutsideTheTableUseTheApproximateDates() {
		// 2028年は表にない。雨水 2/19 のあと 11日後 ＝ 3/1 → 雨水の末候（草木萌動 6番）
		var position = calendar.positionOn(LocalDate.of(2028, 3, 1));

		assertThat(position.approximate()).isTrue();
		assertThat(position.sekkiIndex()).isEqualTo(index("雨水"));
		assertThat(position.koNumber()).isEqualTo(6);
		assertThat(calendar.nextAfter(LocalDate.of(2028, 3, 1))).isEmpty();     // 補足は出さない
	}

	@Test
	void sekkiDayOfAnApproximateYearIsStillASekkiDay() {
		var position = calendar.positionOn(LocalDate.of(2030, 11, 7));

		assertThat(position.isSekkiDay()).isTrue();
		assertThat(position.sekkiIndex()).isEqualTo(index("立冬"));
		assertThat(position.approximate()).isTrue();
	}

	/** 前の節気と次の節気が、どちらも表で求まるときだけ表を使う */
	@ParameterizedTest(name = "{0}：表を使う＝{1}")
	@CsvSource({
			"2025-01-02, false",    // 前の節気（2024年の冬至）が表にない
			"2025-06-15, true",
			"2026-12-31, true",     // 次の節気（2027年の小寒）は表にある
			"2027-06-01, true",
			"2027-12-30, false",    // 次の節気（2028年の小寒）が表にない
			"2027-12-31, false",
			"2028-01-01, false"
	})
	void tableIsUsedOnlyWhenBothNeighborsAreInTheTable(LocalDate date, boolean exact) {
		assertThat(calendar.positionOn(date).approximate()).isEqualTo(!exact);
		assertThat(calendar.nextAfter(date).isPresent()).isEqualTo(exact);
	}

	// ---- 全日 ----

	@Test
	void everyDayOf2026IsEitherASekkiDayOrInsideOneOfTheSeventyTwoKo() {
		Set<Integer> koNumbers = new HashSet<>();
		int sekkiDays = 0;
		for (LocalDate date = LocalDate.of(2026, 1, 1); date.getYear() == 2026; date = date.plusDays(1)) {
			var position = calendar.positionOn(date);
			assertThat(position.approximate()).as(date.toString()).isFalse();
			if (position.isSekkiDay()) {
				sekkiDays++;
			} else {
				assertThat(position.koNumber()).as(date.toString()).isBetween(1, 72);
				koNumbers.add(position.koNumber());
			}
			assertThat(calendar.nextAfter(date)).as(date.toString()).isPresent();
		}
		assertThat(sekkiDays).isEqualTo(24);
		assertThat(koNumbers).hasSize(72);
	}

	@Test
	void dateOfReturnsTheTableDateOrTheApproximateDate() {
		assertThat(calendar.dateOf(2026, index("立春"))).isEqualTo(LocalDate.of(2026, 2, 4));
		assertThat(calendar.dateOf(2027, index("春分"))).isEqualTo(LocalDate.of(2027, 3, 21));
		assertThat(calendar.dateOf(2030, index("春分"))).isEqualTo(LocalDate.of(2030, 3, 20));     // 表の外：ごろ
		assertThat(calendar.hasYear(2027)).isTrue();
		assertThat(calendar.hasYear(2028)).isFalse();
	}

}
