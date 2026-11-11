package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 自動予算で使う季節を決める（設計書 4.2、5.14、issue 019）。画面にもデータベースにも依存しない。
 * <ul>
 * <li>同じ季節の過去 3 年：画面の季節の 1・2・3 年前の同じ季節。2000 年 春より前は入れない</li>
 * <li>傾向の 12 季節：画面の季節より前で、もう終わっている（終了日の翌日 ≦ 今日の）いちばん新しい季節を末尾にして、古い順に 12 個。
 * 2000 年 春より前にかかるときは「なし」</li>
 * <li>直前の季節：画面の季節の 1 つ前</li>
 * <li>集計する期間：いちばん古い季節の開始日 〜 画面の季節の開始日の前</li>
 * </ul>
 */
public final class AutoBudgetSeasons {

	private static final int YEARS_BACK = 3;
	private static final int TREND_SEASONS = 12;

	private final SeasonPeriod screen;
	private final List<SeasonPeriod> sameSeasonYears;
	private final Optional<List<SeasonPeriod>> trendSeasons;
	private final Optional<SeasonPeriod> previousSeason;
	private final LocalDate aggregateFrom;

	private AutoBudgetSeasons(SeasonPeriod screen, LocalDate today) {
		this.screen = screen;

		List<SeasonPeriod> years = new ArrayList<>();
		for (int back = 1; back <= YEARS_BACK; back++) {
			SeasonPeriod candidate = new SeasonPeriod(screen.year() - back, screen.season());
			if (candidate.isInRange()) {
				years.add(candidate);
			}
		}
		this.sameSeasonYears = List.copyOf(years);
		this.previousSeason = screen.previous();
		this.trendSeasons = findTrendSeasons(screen, today);

		LocalDate from = screen.startDate();
		for (SeasonPeriod season : sameSeasonYears) {
			from = earlier(from, season.startDate());
		}
		if (previousSeason.isPresent()) {
			from = earlier(from, previousSeason.get().startDate());
		}
		if (trendSeasons.isPresent()) {
			from = earlier(from, trendSeasons.get().get(0).startDate());
		}
		this.aggregateFrom = from;
	}

	/**
	 * @param screen 画面の季節
	 * @param today  今日（日本時間。呼ぶ側が渡す）
	 */
	public static AutoBudgetSeasons of(SeasonPeriod screen, LocalDate today) {
		return new AutoBudgetSeasons(screen, today);
	}

	/** 傾向の 12 季節。画面の季節より前で、もう終わっているいちばん新しい季節から、古いほうへ 12 個たどる。 */
	private static Optional<List<SeasonPeriod>> findTrendSeasons(SeasonPeriod screen, LocalDate today) {
		Optional<SeasonPeriod> latest = screen.previous();
		while (latest.isPresent() && latest.get().endDateExclusive().isAfter(today)) {
			latest = latest.get().previous();   // まだ終わっていない季節は飛ばす
		}
		List<SeasonPeriod> seasons = new ArrayList<>();
		Optional<SeasonPeriod> current = latest;
		for (int i = 0; i < TREND_SEASONS; i++) {
			if (current.isEmpty()) {
				return Optional.empty();   // 2000 年 春より前にかかる
			}
			seasons.add(current.get());
			current = current.get().previous();
		}
		Collections.reverse(seasons);   // 古い順
		return Optional.of(List.copyOf(seasons));
	}

	private static LocalDate earlier(LocalDate a, LocalDate b) {
		return b.isBefore(a) ? b : a;
	}

	/** 画面の季節と同じ季節の、1・2・3 年前（この順。範囲の外は入れない）。 */
	public List<SeasonPeriod> sameSeasonYears() {
		return sameSeasonYears;
	}

	/** 傾向の 12 季節（古い順。先頭の 4 つが T1、次の 4 つが T2、最後の 4 つが T3）。2000 年 春より前にかかるときは空。 */
	public Optional<List<SeasonPeriod>> trendSeasons() {
		return trendSeasons;
	}

	/** 直前の季節（画面の季節の 1 つ前）。範囲の外は空。 */
	public Optional<SeasonPeriod> previousSeason() {
		return previousSeason;
	}

	/** 一度に集計する期間の開始日（含む）。使う季節がひとつもないときは、画面の季節の開始日（空の期間）。 */
	public LocalDate aggregateFrom() {
		return aggregateFrom;
	}

	/** 一度に集計する期間の終わり（含まない）＝ 画面の季節の開始日。 */
	public LocalDate aggregateToExclusive() {
		return screen.startDate();
	}

}
