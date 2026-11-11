package io.github.yutaro081.householdbudget.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.AutoBudgetSeasons;
import io.github.yutaro081.householdbudget.domain.BudgetProposal;
import io.github.yutaro081.householdbudget.domain.CountingStart;
import io.github.yutaro081.householdbudget.domain.MedianAmount;
import io.github.yutaro081.householdbudget.domain.ProposalChoice;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.domain.SpendingPace;
import io.github.yutaro081.householdbudget.domain.SpendingTrend;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView.BasisKind;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView.TrendKind;
import io.github.yutaro081.householdbudget.dto.BudgetEditView;
import io.github.yutaro081.householdbudget.dto.MonthlyActual;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 自動予算の案と、表の元の数字を作る（設計書 4.4、5.14、issue 019）。<b>読むだけ</b>（データベースに書かない）。
 * <p>
 * 【決まり】最初の引数は userId。科目・予算・取引は、すべて自分の分だけ（user_id で絞る。設計書 3.9）。
 * 範囲（2000年春〜2100年冬）の外の季節（null を含む）は NotFoundException。
 * 計算は domain に渡す。ここでは、DB から集めた数字を季節ごと・科目ごとにまとめ、domain の結果を AutoBudgetView に詰める。
 * 過去の実績は、1 回の検索（sumByCategoryAndMonth）で集め、月を季節にまとめる。
 */
@Service
public class AutoBudgetService {

	private final BudgetService budgetService;
	private final BudgetRepository budgetRepository;
	private final TransactionRepository transactionRepository;

	public AutoBudgetService(BudgetService budgetService, BudgetRepository budgetRepository,
			TransactionRepository transactionRepository) {
		this.budgetService = budgetService;
		this.budgetRepository = budgetRepository;
		this.transactionRepository = transactionRepository;
	}

	/**
	 * @param userId ログイン中のユーザーのID
	 * @param period 画面の季節
	 * @param choice 選んだ割合
	 * @param today  今日（日本時間。呼ぶ側が Clock から取って渡す）
	 * @throws NotFoundException   範囲外の季節（null を含む）
	 * @throws ArithmeticException あふれたとき
	 */
	@Transactional(readOnly = true)
	public AutoBudgetView propose(Long userId, SeasonPeriod period, ProposalChoice choice, LocalDate today) {
		if (period == null || !period.isInRange()) {
			throw new NotFoundException();
		}
		AutoBudgetSeasons seasons = AutoBudgetSeasons.of(period, today);
		BudgetEditView current = budgetService.edit(userId, period);   // 自分の科目（登録順）と、今の値（保存済み、なければ 0 円）
		Optional<SeasonPeriod> previous = seasons.previousSeason();

		Map<Long, Budget> previousBudgets = previousBudgetsOf(userId, previous);
		Map<SeasonPeriod, Map<Long, Long>> actuals = actualsBySeason(userId, seasons);

		// 記録のある年（同じ季節で、自分の取引が 1 件以上ある年）。古い順
		List<SeasonPeriod> recordSeasons = new ArrayList<>();
		for (SeasonPeriod season : seasons.sameSeasonYears()) {
			if (actuals.containsKey(season)) {
				recordSeasons.add(0, season);
			}
		}
		List<Integer> years = recordSeasons.stream().map(SeasonPeriod::year).toList();
		boolean yearsConsecutive = isConsecutive(years);

		SpendingTrend.Result trend = trendOf(seasons, actuals);

		// 直前の季節
		boolean previousContainsToday = previous.isPresent()
				&& !today.isBefore(previous.get().startDate()) && today.isBefore(previous.get().endDateExclusive());
		Map<Long, Long> previousActuals = previous.map(s -> actuals.getOrDefault(s, Map.<Long, Long>of())).orElse(Map.of());

		// 直前の季節のペースの数え始め（issue 020）：自分のいちばん最初の取引の日（なければ null）
		LocalDate firstTransactionDate = transactionRepository.findFirstTransactionDate(userId).orElse(null);

		List<AutoBudgetView.Row> rows = new ArrayList<>();
		long previousActualTotal = 0;
		for (BudgetEditView.Row currentRow : current.rows()) {
			Long categoryId = currentRow.categoryId();
			long previousActual = previousActuals.getOrDefault(categoryId, 0L);
			previousActualTotal = Math.addExact(previousActualTotal, previousActual);
			Budget previousBudget = previousBudgets.get(categoryId);
			Long previousBudgetAmount = previousBudget == null ? null : (long) previousBudget.getAmount();
			Optional<SpendingPace.Pace> pace = previous.flatMap(s -> SpendingPace.of(previousActual, s, today, CountingStart.of(s, firstTransactionDate, today)));

			List<Long> amounts = new ArrayList<>();
			for (SeasonPeriod season : recordSeasons) {
				amounts.add(actuals.get(season).getOrDefault(categoryId, 0L));   // 記録のある年の実績 0 円は 0 円として数える
			}
			OptionalLong twiceMedian = MedianAmount.twice(amounts);

			BudgetProposal.Result result = BudgetProposal.of(twiceMedian, trend.percent(), choice, previousActual, pace,
					previousBudgetAmount, currentRow.amount());

			Long paceAmount = (previousContainsToday && previousActual > 0 && pace.isPresent())
					? pace.get().displayAmount()
					: null;
			rows.add(row(currentRow, previousBudgetAmount, previousActual, paceAmount, result, twiceMedian, years,
					yearsConsecutive, previousContainsToday, pace));
		}

		Long previousBudgetTotal = previousBudgets.isEmpty() ? null
				: previousBudgets.values().stream().mapToLong(Budget::getAmount).sum();
		Long previousPaceTotal = null;
		if (previousContainsToday && previousActualTotal > 0) {
			previousPaceTotal = SpendingPace.of(previousActualTotal, previous.get(), today, CountingStart.of(previous.get(), firstTransactionDate, today))
					.map(SpendingPace.Pace::displayAmount).orElse(null);
		}

		boolean hasRecordYears = !recordSeasons.isEmpty();
		boolean noMaterial = rows.stream().allMatch(r -> r.rule() == 4);

		return new AutoBudgetView(choice, previous.orElse(null), previousBudgetTotal, previousActualTotal,
				previousContainsToday, today, previousPaceTotal, period.season(), hasRecordYears,
				trend.percent(), trendKindOf(trend), trend.capped(), noMaterial, rows);
	}

	private AutoBudgetView.Row row(BudgetEditView.Row currentRow, Long previousBudget, long previousActual,
			Long paceAmount, BudgetProposal.Result result, OptionalLong twiceMedian, List<Integer> years,
			boolean yearsConsecutive, boolean previousContainsToday, Optional<SpendingPace.Pace> pace) {
		BasisKind kind;
		long base;
		List<Integer> usedYears = List.of();
		switch (result.rule()) {
		case 1 -> {
			kind = years.size() >= 3 ? BasisKind.MEDIAN : (years.size() == 2 ? BasisKind.AVERAGE : BasisKind.SINGLE_YEAR);
			base = MedianAmount.display(twiceMedian.getAsLong());
			usedYears = years;
		}
		case 2 -> {
			kind = previousContainsToday ? BasisKind.PACE : BasisKind.ACTUAL;
			base = previousContainsToday ? pace.get().displayAmount() : previousActual;
		}
		case 3 -> {
			kind = BasisKind.BUDGET;
			base = previousBudget;
		}
		default -> {
			kind = BasisKind.NONE;
			base = currentRow.amount();
		}
		}
		return new AutoBudgetView.Row(currentRow.categoryId(), currentRow.categoryName(), previousBudget,
				previousActual, paceAmount, result.rule(), kind, usedYears, result.rule() != 1 || yearsConsecutive, base,
				result.amount());
	}

	/** 直前の季節の、自分の予算を、科目IDをキーにして返す（保存されている行だけ。0 円も入る）。 */
	private Map<Long, Budget> previousBudgetsOf(Long userId, Optional<SeasonPeriod> previous) {
		Map<Long, Budget> map = new HashMap<>();
		if (previous.isPresent()) {
			for (Budget budget : budgetRepository.findByUserIdAndSeasonYearAndSeason(userId, previous.get().year(),
					previous.get().season())) {
				map.put(budget.getCategory().getId(), budget);
			}
		}
		return map;
	}

	/** 1 回の検索で集めた 科目×月 の実績を、季節ごと・科目ごとにまとめる。キーにある季節が「自分の取引が 1 件以上ある季節」。 */
	private Map<SeasonPeriod, Map<Long, Long>> actualsBySeason(Long userId, AutoBudgetSeasons seasons) {
		Map<SeasonPeriod, Map<Long, Long>> map = new HashMap<>();
		if (!seasons.aggregateFrom().isBefore(seasons.aggregateToExclusive())) {
			return map;   // 使う季節がひとつもない（空の期間）
		}
		for (MonthlyActual monthly : transactionRepository.sumByCategoryAndMonth(userId, seasons.aggregateFrom(),
				seasons.aggregateToExclusive())) {
			SeasonPeriod season = SeasonPeriod.from(LocalDate.of(monthly.year(), monthly.month(), 1));
			map.computeIfAbsent(season, k -> new HashMap<>()).merge(monthly.categoryId(), monthly.total(), Math::addExact);
		}
		return map;
	}

	private SpendingTrend.Result trendOf(AutoBudgetSeasons seasons, Map<SeasonPeriod, Map<Long, Long>> actuals) {
		Optional<List<SeasonPeriod>> trendSeasons = seasons.trendSeasons();
		if (trendSeasons.isEmpty()) {
			return SpendingTrend.of(false, 0, 0, 0);
		}
		List<SeasonPeriod> twelve = trendSeasons.get();
		boolean allRecorded = twelve.stream().allMatch(actuals::containsKey);
		return SpendingTrend.of(allRecorded, totalOf(twelve.subList(0, 4), actuals),
				totalOf(twelve.subList(4, 8), actuals), totalOf(twelve.subList(8, 12), actuals));
	}

	private static long totalOf(List<SeasonPeriod> seasons, Map<SeasonPeriod, Map<Long, Long>> actuals) {
		long total = 0;
		for (SeasonPeriod season : seasons) {
			for (long amount : actuals.getOrDefault(season, Map.of()).values()) {
				total = Math.addExact(total, amount);
			}
		}
		return total;
	}

	private static TrendKind trendKindOf(SpendingTrend.Result trend) {
		if (!trend.calculated()) {
			return TrendKind.NOT_CALCULATED;
		}
		return trend.percent() > 100 ? TrendKind.UP : (trend.percent() < 100 ? TrendKind.DOWN : TrendKind.SAME);
	}

	private static boolean isConsecutive(List<Integer> years) {
		for (int i = 1; i < years.size(); i++) {
			if (years.get(i) != years.get(i - 1) + 1) {
				return false;
			}
		}
		return true;
	}

}
