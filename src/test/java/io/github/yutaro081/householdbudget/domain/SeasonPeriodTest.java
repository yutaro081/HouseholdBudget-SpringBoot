package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 季節と年の計算の単体テスト（設計書 5.8、2.4）。Spring もデータベースも使わない。
 * ・今日の日付から、季節と年を決める（Phase 3）
 * ・期間、前・次の季節、範囲（2000年春〜2100年冬）、期間の順の比べ方、URL の値からの変換（Phase 6）
 */
class SeasonPeriodTest {

	/** 設計書 5.8 の表の5つの日付 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2027-02-28, 2026, WINTER",
			"2027-01-15, 2026, WINTER",
			"2026-12-01, 2026, WINTER",
			"2026-11-30, 2026, AUTUMN",
			"2027-03-01, 2027, SPRING"
	})
	void designTableDates(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** うるう年の2月29日は、前の年の冬。翌日の3月1日は春 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2028-02-28, 2027, WINTER",
			"2028-02-29, 2027, WINTER",
			"2028-03-01, 2028, SPRING",
			"2024-02-29, 2023, WINTER",
			"2024-03-01, 2024, SPRING",
			// うるう年ではない年の2月末日と、3月1日
			"2027-02-28, 2026, WINTER",
			"2027-03-01, 2027, SPRING"
	})
	void leapYear(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** 季節の境目（前日と当日）。冬は12月がある年 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2026-02-28, 2025, WINTER",
			"2026-03-01, 2026, SPRING",
			"2026-05-31, 2026, SPRING",
			"2026-06-01, 2026, SUMMER",
			"2026-08-31, 2026, SUMMER",
			"2026-09-01, 2026, AUTUMN",
			"2026-11-30, 2026, AUTUMN",
			"2026-12-01, 2026, WINTER",
			"2026-12-31, 2026, WINTER",
			"2027-01-01, 2026, WINTER"
	})
	void seasonBoundaries(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/**
	 * 範囲の端の年。取引の日付は2000年3月1日〜2100年12月31日（R12、R15）だが、from は日付だけの計算で範囲を知らないので、
	 * 範囲の外の 2000-01-01（1999年の冬）も確かめる
	 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2000-01-01, 1999, WINTER",
			"2000-03-01, 2000, SPRING",
			"2100-12-31, 2100, WINTER"
	})
	void rangeEdges(LocalDate date, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.from(date)).isEqualTo(new SeasonPeriod(expectedYear, expectedSeason));
	}

	/** 12か月すべてが、設計書 5.8 の月と季節の対応どおり */
	@Test
	void everyMonth() {
		assertThat(Season.fromMonth(Month.MARCH)).isEqualTo(Season.SPRING);
		assertThat(Season.fromMonth(Month.APRIL)).isEqualTo(Season.SPRING);
		assertThat(Season.fromMonth(Month.MAY)).isEqualTo(Season.SPRING);
		assertThat(Season.fromMonth(Month.JUNE)).isEqualTo(Season.SUMMER);
		assertThat(Season.fromMonth(Month.JULY)).isEqualTo(Season.SUMMER);
		assertThat(Season.fromMonth(Month.AUGUST)).isEqualTo(Season.SUMMER);
		assertThat(Season.fromMonth(Month.SEPTEMBER)).isEqualTo(Season.AUTUMN);
		assertThat(Season.fromMonth(Month.OCTOBER)).isEqualTo(Season.AUTUMN);
		assertThat(Season.fromMonth(Month.NOVEMBER)).isEqualTo(Season.AUTUMN);
		assertThat(Season.fromMonth(Month.DECEMBER)).isEqualTo(Season.WINTER);
		assertThat(Season.fromMonth(Month.JANUARY)).isEqualTo(Season.WINTER);
		assertThat(Season.fromMonth(Month.FEBRUARY)).isEqualTo(Season.WINTER);
	}

	/** URL で使う名前 */
	@Test
	void urlNames() {
		assertThat(Season.SPRING.getUrlName()).isEqualTo("spring");
		assertThat(Season.SUMMER.getUrlName()).isEqualTo("summer");
		assertThat(Season.AUTUMN.getUrlName()).isEqualTo("autumn");
		assertThat(Season.WINTER.getUrlName()).isEqualTo("winter");
	}

	// ================================================================
	// Phase 6：期間、前・次の季節、範囲、期間の順の比べ方、URL の値からの変換
	// （設計書 5.8、2.4。本体を足す前に、境界値のテストを先に書いた）
	// ================================================================

	private static SeasonPeriod p(int year, Season season) {
		return new SeasonPeriod(year, season);
	}

	/** 範囲の中のすべての季節（2000年春〜2100年冬の 101年×4季節 = 404個）を、期間の順で */
	private static List<SeasonPeriod> allInRange() {
		List<SeasonPeriod> all = new ArrayList<>();
		for (int year = 2000; year <= 2100; year++) {
			for (Season season : Season.values()) {
				all.add(p(year, season));
			}
		}
		return all;
	}

	// ---- 期間（開始日以上、終了日の翌日未満） ----

	@ParameterizedTest(name = "{0}年 {1} は {2} 以上 {3} 未満")
	@CsvSource({
			"2026, SPRING, 2026-03-01, 2026-06-01",
			"2026, SUMMER, 2026-06-01, 2026-09-01",
			"2026, AUTUMN, 2026-09-01, 2026-12-01",
			// 冬は年をまたぐ（12月がある年。終了日の翌日は翌年の3月1日）
			"2026, WINTER, 2026-12-01, 2027-03-01",
			// うるう年の2月29日を含む冬（2027年冬は2028年2月29日まで）
			"2027, WINTER, 2027-12-01, 2028-03-01",
			"2023, WINTER, 2023-12-01, 2024-03-01",
			// 範囲の端
			"2000, SPRING, 2000-03-01, 2000-06-01",
			"2100, WINTER, 2100-12-01, 2101-03-01"
	})
	void period(int year, Season season, LocalDate start, LocalDate endExclusive) {
		SeasonPeriod period = p(year, season);

		assertThat(period.startDate()).isEqualTo(start);
		assertThat(period.endDateExclusive()).isEqualTo(endExclusive);
	}

	/** 冬の期間は、翌年の3月1日の前日まで（2月の最終日。うるう年は29日） */
	@ParameterizedTest(name = "{0}年の冬の最後の日は {1}")
	@CsvSource({
			"2026, 2027-02-28",
			"2027, 2028-02-29",   // うるう年
			"2023, 2024-02-29",   // うるう年
			"2000, 2001-02-28",
			"1999, 2000-02-29",   // 2000年はうるう年（400で割り切れる）
			"2099, 2100-02-28"    // 2100年はうるう年ではない（100で割り切れ、400で割り切れない）
	})
	void winterEndsOnTheLastDayOfFebruary(int year, LocalDate lastDay) {
		SeasonPeriod winter = p(year, Season.WINTER);

		assertThat(winter.endDateExclusive().minusDays(1)).isEqualTo(lastDay);
		assertThat(winter.endDateExclusive()).isEqualTo(LocalDate.of(year + 1, 3, 1));   // 終了日の翌日は、いつも翌年の3月1日
	}

	/** うるう年の2月29日は、前の年の冬の最後の日。翌日の3月1日は、その年の春の最初の日 */
	@Test
	void leapDayBelongsToTheWinterOfThePreviousYear() {
		SeasonPeriod winter2027 = p(2027, Season.WINTER);
		SeasonPeriod spring2028 = p(2028, Season.SPRING);
		LocalDate leapDay = LocalDate.of(2028, 2, 29);

		assertThat(SeasonPeriod.from(leapDay)).isEqualTo(winter2027);
		assertThat(!leapDay.isBefore(winter2027.startDate()) && leapDay.isBefore(winter2027.endDateExclusive())).isTrue();
		assertThat(leapDay.plusDays(1)).isEqualTo(spring2028.startDate());          // 3月1日
		assertThat(SeasonPeriod.from(leapDay.plusDays(1))).isEqualTo(spring2028);
		assertThat(winter2027.next()).contains(spring2028);
		assertThat(winter2027.endDateExclusive()).isEqualTo(spring2028.startDate());
	}

	/** 期間は隣と隙間なく続く（各季節の終了日の翌日＝次の季節の開始日）。範囲の中の404季節すべてで確かめる */
	@Test
	void periodsAreContiguous() {
		List<SeasonPeriod> all = allInRange();
		assertThat(all).hasSize(404);

		for (int i = 0; i < all.size() - 1; i++) {
			SeasonPeriod current = all.get(i);
			assertThat(current.endDateExclusive()).as("%s の終了日の翌日", current).isEqualTo(all.get(i + 1).startDate());
			assertThat(current.startDate()).isBefore(current.endDateExclusive());
		}
	}

	/** 開始日・最後の日・終了日の翌日の3つの日付が、それぞれ正しい季節になる（今日の日付から季節を決める計算と、期間が合っている） */
	@Test
	void periodAndDateToSeasonAgree() {
		List<SeasonPeriod> all = allInRange();

		for (int i = 0; i < all.size(); i++) {
			SeasonPeriod current = all.get(i);
			assertThat(SeasonPeriod.from(current.startDate())).as("%s の開始日", current).isEqualTo(current);
			assertThat(SeasonPeriod.from(current.endDateExclusive().minusDays(1))).as("%s の最後の日", current).isEqualTo(current);
			if (i + 1 < all.size()) {
				assertThat(SeasonPeriod.from(current.endDateExclusive())).as("%s の終了日の翌日", current).isEqualTo(all.get(i + 1));
			}
		}
	}

	// ---- 前・次の季節（設計書 2.4 の表） ----

	@ParameterizedTest(name = "{0}年 {1}：前は {2}年 {3}、次は {4}年 {5}")
	@CsvSource({
			"2026, SPRING, 2025, WINTER, 2026, SUMMER",
			"2026, SUMMER, 2026, SPRING, 2026, AUTUMN",
			"2026, AUTUMN, 2026, SUMMER, 2026, WINTER",
			"2026, WINTER, 2026, AUTUMN, 2027, SPRING",
			// 範囲の端のすぐ内側
			"2000, SUMMER, 2000, SPRING, 2000, AUTUMN",
			"2000, WINTER, 2000, AUTUMN, 2001, SPRING",
			"2100, SPRING, 2099, WINTER, 2100, SUMMER",
			"2100, AUTUMN, 2100, SUMMER, 2100, WINTER"
	})
	void previousAndNext(int year, Season season, int prevYear, Season prevSeason, int nextYear, Season nextSeason) {
		SeasonPeriod period = p(year, season);

		assertThat(period.previous()).contains(p(prevYear, prevSeason));
		assertThat(period.next()).contains(p(nextYear, nextSeason));
	}

	/** 範囲の端：2000年春の前の季節はない。2100年冬の次の季節はない */
	@Test
	void noPreviousBeforeSpring2000AndNoNextAfterWinter2100() {
		SeasonPeriod first = p(2000, Season.SPRING);
		SeasonPeriod last = p(2100, Season.WINTER);

		assertThat(first.previous()).isEmpty();
		assertThat(last.next()).isEmpty();
		// 反対側はある
		assertThat(first.next()).contains(p(2000, Season.SUMMER));
		assertThat(last.previous()).contains(p(2100, Season.AUTUMN));
	}

	/** 前 → 次、次 → 前 で、元に戻る。春2000から次へたどると、404個で2100年冬に着く */
	@Test
	void previousAndNextAreInversesAndWalkCoversTheWholeRange() {
		for (SeasonPeriod period : allInRange()) {
			period.next().ifPresent(next -> assertThat(next.previous()).as("%s の次の前", period).contains(period));
			period.previous().ifPresent(prev -> assertThat(prev.next()).as("%s の前の次", period).contains(period));
		}

		int count = 1;
		SeasonPeriod walker = p(2000, Season.SPRING);
		for (Optional<SeasonPeriod> next = walker.next(); next.isPresent(); next = walker.next()) {
			walker = next.get();
			count++;
		}
		assertThat(count).isEqualTo(404);
		assertThat(walker).isEqualTo(p(2100, Season.WINTER));
	}

	// ---- 範囲（2000年春〜2100年冬） ----

	@ParameterizedTest(name = "{0}年 {1} は範囲内：{2}")
	@CsvSource({
			"2000, SPRING, true",
			"2000, SUMMER, true",
			"2000, AUTUMN, true",
			"2000, WINTER, true",
			"2100, SPRING, true",
			"2100, SUMMER, true",
			"2100, AUTUMN, true",
			"2100, WINTER, true",
			"2026, AUTUMN, true",
			"1999, WINTER, false",
			"1999, SPRING, false",
			"2101, SPRING, false",
			"2101, WINTER, false",
			"0, SPRING, false",
			"9999, WINTER, false"
	})
	void inRange(int year, Season season, boolean expected) {
		assertThat(p(year, season).isInRange()).isEqualTo(expected);
	}

	/** 範囲外の季節の前後（今の季節の計算が範囲の外を作ることがあっても、前後は範囲の中だけを返す） */
	@Test
	void previousAndNextOfOutOfRangeNeverLeaveTheRange() {
		assertThat(p(1999, Season.WINTER).previous()).isEmpty();
		assertThat(p(1999, Season.WINTER).next()).contains(p(2000, Season.SPRING));
		assertThat(p(2101, Season.SPRING).next()).isEmpty();
		assertThat(p(2101, Season.SPRING).previous()).contains(p(2100, Season.WINTER));
		assertThat(p(2101, Season.SUMMER).previous()).isEmpty();
	}

	// ---- 期間の順の比べ方（DB の文字の順 AUTUMN < SPRING < SUMMER < WINTER とは違う） ----

	@ParameterizedTest(name = "{0}年 {1} は {2}年 {3} より前：{4}")
	@CsvSource({
			// 同じ年の中：春 → 夏 → 秋 → 冬
			"2026, SPRING, 2026, SUMMER, true",
			"2026, SUMMER, 2026, AUTUMN, true",
			"2026, AUTUMN, 2026, WINTER, true",
			"2026, SPRING, 2026, WINTER, true",
			"2026, WINTER, 2026, SPRING, false",
			"2026, AUTUMN, 2026, SPRING, false",
			"2026, SUMMER, 2026, SPRING, false",
			// 文字の順なら AUTUMN が先だが、期間の順では春のほうが先
			"2026, SPRING, 2026, AUTUMN, true",
			"2026, AUTUMN, 2026, SUMMER, false",
			// 年をまたぐ：去年の冬は、今年の春より前（12月がある年が冬の年）
			"2025, WINTER, 2026, SPRING, true",
			"2026, SPRING, 2025, WINTER, false",
			"2025, WINTER, 2026, WINTER, true",
			"2026, WINTER, 2027, SPRING, true",
			// 同じ季節は、前ではない
			"2026, SPRING, 2026, SPRING, false",
			"2026, WINTER, 2026, WINTER, false"
	})
	void isBefore(int year, Season season, int otherYear, Season otherSeason, boolean expected) {
		assertThat(p(year, season).isBefore(p(otherYear, otherSeason))).isEqualTo(expected);
	}

	/** 期間の順で並べた全季節は、次々に「より前」になる */
	@Test
	void isBeforeFollowsThePeriodOrder() {
		List<SeasonPeriod> all = allInRange();
		for (int i = 0; i < all.size() - 1; i++) {
			assertThat(all.get(i).isBefore(all.get(i + 1))).as("%s は %s より前", all.get(i), all.get(i + 1)).isTrue();
			assertThat(all.get(i + 1).isBefore(all.get(i))).isFalse();
		}
	}

	/** 同じ年の中で、ある季節より前の季節（DB の検索に使う）。冬の年は12月がある年なので、前の年の冬は含まない */
	@Test
	void earlierSeasonsInTheSameYear() {
		assertThat(Season.SPRING.earlierInYear()).isEmpty();
		assertThat(Season.SUMMER.earlierInYear()).containsExactly(Season.SPRING);
		assertThat(Season.AUTUMN.earlierInYear()).containsExactly(Season.SPRING, Season.SUMMER);
		assertThat(Season.WINTER.earlierInYear()).containsExactly(Season.SPRING, Season.SUMMER, Season.AUTUMN);
	}

	// ---- URL の値からの変換（Q6-2 の A：年は半角の数字4桁だけ、季節は小文字の4つだけ） ----

	@ParameterizedTest(name = "year={0} season={1} → {2}年 {3}")
	@CsvSource({
			"2026, spring, 2026, SPRING",
			"2026, summer, 2026, SUMMER",
			"2026, autumn, 2026, AUTUMN",
			"2026, winter, 2026, WINTER",
			// 範囲の端
			"2000, spring, 2000, SPRING",
			"2000, winter, 2000, WINTER",
			"2100, spring, 2100, SPRING",
			"2100, winter, 2100, WINTER"
	})
	void parseAccepts(String year, String season, int expectedYear, Season expectedSeason) {
		assertThat(SeasonPeriod.parse(year, season)).contains(p(expectedYear, expectedSeason));
	}

	@ParameterizedTest(name = "year=[{0}] season=[{1}] は不正")
	@CsvSource(delimiter = '|', value = {
			// 範囲外の年
			"1999|winter", "1999|spring", "2101|spring", "2101|winter", "0000|spring", "9999|spring",
			// 数字でない・形が違う年
			"abc|spring", "''|spring", "' '|spring", "' 2026'|spring", "'2026 '|spring", "02026|spring", "+2026|spring",
			"-2026|spring", "2026.0|spring", "2026.5|spring", "20260|spring", "202|spring", "２０２６|spring",
			"2,026|spring", "0x7EA|spring",
			// 4つ以外の季節・形が違う季節
			"2026|xyz", "2026|Spring", "2026|SPRING", "2026|' spring'", "2026|'spring '", "2026|''", "2026|' '",
			"2026|spring,summer", "2026|春", "2026|fall", "2026|1"
	})
	void parseRejects(String year, String season) {
		assertThat(SeasonPeriod.parse(year, season)).isEmpty();
	}

	/** 改行・タブなどの空白も不正（CSV には書けないので、文字列で直接書く） */
	@Test
	void parseRejectsWhitespaceCharacters() {
		assertThat(SeasonPeriod.parse("2026\n", "spring")).isEmpty();
		assertThat(SeasonPeriod.parse("\t2026", "spring")).isEmpty();
		assertThat(SeasonPeriod.parse("2026", "spring\n")).isEmpty();
		assertThat(SeasonPeriod.parse("2026", "\tspring")).isEmpty();
		assertThat(SeasonPeriod.parse("2026　", "spring")).isEmpty();    // 全角の空白
		assertThat(SeasonPeriod.parse("2026", "spring　")).isEmpty();
	}

	/** 値がない（URL に年か季節がない）ときは「不正」。両方ないときに今の季節を表示するのは、呼ぶ側の決まり */
	@Test
	void parseRejectsMissingValues() {
		assertThat(SeasonPeriod.parse(null, "spring")).isEmpty();
		assertThat(SeasonPeriod.parse("2026", null)).isEmpty();
		assertThat(SeasonPeriod.parse(null, null)).isEmpty();
	}

	@ParameterizedTest(name = "季節の名前 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"spring|SPRING", "summer|SUMMER", "autumn|AUTUMN", "winter|WINTER"
	})
	void seasonFromUrlName(String urlName, Season expected) {
		assertThat(Season.fromUrlName(urlName)).contains(expected);
	}

	@ParameterizedTest(name = "季節の名前 [{0}] は不正")
	@CsvSource(delimiter = '|', value = {
			"Spring", "SPRING", "' spring'", "'spring '", "''", "' '", "xyz", "春", "fall", "springs"
	})
	void seasonFromUrlNameRejects(String urlName) {
		assertThat(Season.fromUrlName(urlName)).isEmpty();
		assertThat(Season.fromUrlName(null)).isEmpty();
	}

	/** URL の名前の往復：すべての季節で、名前から元の季節に戻る */
	@Test
	void urlNameRoundTrip() {
		for (Season season : Season.values()) {
			assertThat(Season.fromUrlName(season.getUrlName())).contains(season);
		}
	}


	// ---- resolve：URL の年と季節から表示する季節を決める（予算設定と予実比較に共通。設計書 2.4、Q7-1） ----

	/** 年も季節も指定がない（両方 null）→ 今日の季節。冬の1月・2月は前の年 */
	@ParameterizedTest(name = "指定なし・今日 {0} → {1}年 {2}")
	@CsvSource({
			"2026-10-29, 2026, AUTUMN",
			"2027-02-28, 2026, WINTER",
			"2027-01-15, 2026, WINTER",
			"2026-12-01, 2026, WINTER",
			"2026-11-30, 2026, AUTUMN",
			"2027-03-01, 2027, SPRING",
			"2028-02-29, 2027, WINTER"
	})
	void resolveWithoutParametersIsTodaysSeason(LocalDate today, int year, Season season) {
		assertThat(SeasonPeriod.resolve(null, null, today)).contains(new SeasonPeriod(year, season));
	}

	/** 年と季節の両方がある → 今日に関係なく、その季節 */
	@ParameterizedTest(name = "year={0}, season={1} → {0}年 {2}")
	@CsvSource({
			"2026, spring, SPRING",
			"2026, summer, SUMMER",
			"2026, autumn, AUTUMN",
			"2026, winter, WINTER",
			"2000, spring, SPRING",
			"2100, winter, WINTER"
	})
	void resolveWithBothParametersIgnoresToday(String year, String season, Season expected) {
		assertThat(SeasonPeriod.resolve(year, season, LocalDate.of(2026, 10, 29)))
				.contains(new SeasonPeriod(Integer.parseInt(year), expected));
		assertThat(SeasonPeriod.resolve(year, season, LocalDate.of(2000, 1, 1)))
				.contains(new SeasonPeriod(Integer.parseInt(year), expected));
	}

	/** 片方だけ → 不正（空）。今の季節にはならない */
	@Test
	void resolveWithOnlyOneParameterIsEmpty() {
		LocalDate today = LocalDate.of(2026, 10, 29);
		assertThat(SeasonPeriod.resolve("2026", null, today)).isEmpty();
		assertThat(SeasonPeriod.resolve(null, "spring", today)).isEmpty();
	}

	/** 範囲外・不正な値 → 空（呼ぶ側が 404 にする）。空文字は「指定された」ので今の季節にはならない */
	@ParameterizedTest(name = "year=[{0}], season=[{1}] → 不正")
	@CsvSource(value = {
			"1999, winter",
			"2101, spring",
			"abc, spring",
			"02026, spring",
			"+2026, spring",
			"2026.5, spring",
			"'', spring",
			"2026, ''",
			"'', ''",
			"2026, Spring",
			"2026, SPRING",
			"2026, xyz",
			"' 2026', spring",
			"2026, ' spring'"
	})
	void resolveRejectsInvalidValues(String year, String season) {
		assertThat(SeasonPeriod.resolve(year, season, LocalDate.of(2026, 10, 29))).isEmpty();
	}

	/** resolve は、年と季節が両方ある・両方ないとき以外は parse と同じ結果になる */
	@Test
	void resolveGivesTheSameAnswerAsParseWhenSomethingIsSpecified() {
		LocalDate today = LocalDate.of(2026, 10, 29);
		String[][] cases = { { "2026", "spring" }, { "1999", "winter" }, { "2026", null }, { null, "winter" },
				{ "", "" }, { "abc", "xyz" } };
		for (String[] c : cases) {
			assertThat(SeasonPeriod.resolve(c[0], c[1], today)).isEqualTo(SeasonPeriod.parse(c[0], c[1]));
		}
	}

}
