package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 年と季節の組み合わせ（設計書 4.5、5.8）。
 * <p>
 * 年は「季節の年」で、冬は12月がある年にする
 * （12月はその年、1月・2月は前の年。例：2027年2月は「2026年の冬」）。
 * 画面にもデータベースにも依存しない計算だけを持つ（P1）。
 * <p>
 * Phase 3 で「今日の日付から、季節と年を決める」部分を作り、
 * Phase 6 で、期間（開始日と終了日の翌日）、前・次の季節、2000年春〜2100年冬の範囲の判定、
 * 期間の順の比べ方、URL の値からの変換を足した（設計書 5.8、2.4。予算設定と予実比較の両方で使う）。
 */
public record SeasonPeriod(int year, Season season) {

	/** 表示できる範囲の、最初の季節の年（2000年春から、設計書 2.4）。 */
	public static final int FIRST_YEAR = 2000;

	/** 表示できる範囲の、最後の季節の年（2100年冬まで、設計書 2.4）。 */
	public static final int LAST_YEAR = 2100;

	/** URL の年：半角の数字4桁だけ（Q6-2 の A）。02026・+2026・全角・前後の空白は受け付けない。 */
	private static final Pattern URL_YEAR = Pattern.compile("[0-9]{4}");

	/** 今日の日付から、季節と年を決める。「今日」は日本時間（B6）で、呼ぶ側が渡す。 */
	public static SeasonPeriod from(LocalDate date) {
		Season season = Season.fromMonth(date.getMonth());
		// 冬の1月・2月は、前の年の冬に入る（12月がある年を、冬の年とする）
		int year = (season == Season.WINTER && date.getMonthValue() <= 2)
				? date.getYear() - 1
				: date.getYear();
		return new SeasonPeriod(year, season);
	}

	/**
	 * 期間の開始日（含む）。春は3月1日、夏は6月1日、秋は9月1日、冬は12月1日（設計書 5.8）。
	 * 冬の年は12月がある年なので、冬は year 年の12月1日から始まる。
	 */
	public LocalDate startDate() {
		return switch (season) {
			case SPRING -> LocalDate.of(year, 3, 1);
			case SUMMER -> LocalDate.of(year, 6, 1);
			case AUTUMN -> LocalDate.of(year, 9, 1);
			case WINTER -> LocalDate.of(year, 12, 1);
		};
	}

	/**
	 * 期間の終了日の翌日（含まない）。取引は「日付 ≧ 開始日 かつ 日付 ＜ 終了日の翌日」で選ぶ（設計書 5.8）。
	 * 冬は翌年の3月1日（2月の最終日が28日か29日かを数えなくてよい）。
	 */
	public LocalDate endDateExclusive() {
		return switch (season) {
			case SPRING -> LocalDate.of(year, 6, 1);
			case SUMMER -> LocalDate.of(year, 9, 1);
			case AUTUMN -> LocalDate.of(year, 12, 1);
			case WINTER -> LocalDate.of(year + 1, 3, 1);
		};
	}

	/** 範囲（2000年春〜2100年冬）の中か。年が2000〜2100なら、4つの季節すべてが範囲の中。 */
	public boolean isInRange() {
		return year >= FIRST_YEAR && year <= LAST_YEAR;
	}

	/**
	 * 前の季節。春の前は前の年の冬。範囲の外（2000年春の前）は「なし」（設計書 2.4、B15）。
	 * この季節自身が範囲の外のときも、返すのは範囲の中の季節だけ。
	 */
	public Optional<SeasonPeriod> previous() {
		SeasonPeriod candidate = (season == Season.SPRING)
				? new SeasonPeriod(year - 1, Season.WINTER)
				: new SeasonPeriod(year, Season.values()[season.ordinal() - 1]);
		return candidate.isInRange() ? Optional.of(candidate) : Optional.empty();
	}

	/**
	 * 次の季節。冬の次は翌年の春。範囲の外（2100年冬の次）は「なし」（設計書 2.4、B15）。
	 * この季節自身が範囲の外のときも、返すのは範囲の中の季節だけ。
	 */
	public Optional<SeasonPeriod> next() {
		SeasonPeriod candidate = (season == Season.WINTER)
				? new SeasonPeriod(year + 1, Season.SPRING)
				: new SeasonPeriod(year, Season.values()[season.ordinal() + 1]);
		return candidate.isInRange() ? Optional.of(candidate) : Optional.empty();
	}

	/**
	 * 期間の順で、other より前か（年、次に 春→夏→秋→冬）。同じ季節は「前」ではない。
	 * DB に保存する季節の文字の順（AUTUMN < SPRING < SUMMER < WINTER）とは違う。
	 * 冬の年は12月がある年なので、2025年の冬は2026年の春より前（設計書 5.8）。
	 */
	public boolean isBefore(SeasonPeriod other) {
		return year < other.year || (year == other.year && season.ordinal() < other.season.ordinal());
	}

	/**
	 * URL の年と季節の文字列から、季節を求める（設計書 2.4、Q6-2 の A）。
	 * 予算設定と予実比較が同じ決まりで使う。「不正」のときは空を返し、呼ぶ側が 404 にする。
	 * <ul>
	 * <li>年：半角の数字4桁だけ（02026、+2026、全角、前後の空白は不正）で、2000〜2100</li>
	 * <li>季節：小文字の spring・summer・autumn・winter だけ</li>
	 * <li>どちらかが null（URL にない）なら不正</li>
	 * </ul>
	 * 年も季節も指定されていないときに、今の季節を表示するのは、呼ぶ側の決まり（ここでは「不正」を返す）。
	 */
	public static Optional<SeasonPeriod> parse(String year, String season) {
		if (year == null || season == null || !URL_YEAR.matcher(year).matches()) {
			return Optional.empty();
		}
		return Season.fromUrlName(season)
				.map(s -> new SeasonPeriod(Integer.parseInt(year), s))
				.filter(SeasonPeriod::isInRange);
	}

	/**
	 * URL の年と季節の文字列から、表示する季節を決める。予算設定と予実比較が同じ決まりで使う（設計書 2.4、Q6-2、Q7-1）。
	 * <ul>
	 * <li>年も季節も指定されていない（両方とも null）→ 今日の季節（{@link #from(LocalDate)}。冬は12月がある年）</li>
	 * <li>それ以外は {@link #parse(String, String)} の結果（片方だけ・空文字・範囲外・不正な値は空）</li>
	 * </ul>
	 * 空のときに 404（NotFoundException）にするのは、呼ぶ側（Controller）の役目。domain は例外の仕組みに頼らない。
	 *
	 * @param year   URL の year（なければ null）
	 * @param season URL の season（なければ null）
	 * @param today  今日（日本時間。Controller が Clock から求めて渡す）
	 */
	public static Optional<SeasonPeriod> resolve(String year, String season, LocalDate today) {
		if (year == null && season == null) {
			return Optional.of(from(today));
		}
		return parse(year, season);
	}

}
