package io.github.yutaro081.householdbudget.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.BudgetColor;
import io.github.yutaro081.householdbudget.domain.BudgetColorJudge;
import io.github.yutaro081.householdbudget.domain.PeriodProgress;
import io.github.yutaro081.householdbudget.domain.PieSlices;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.domain.WeekPeriod;
import io.github.yutaro081.householdbudget.domain.WeeklyAmount;
import io.github.yutaro081.householdbudget.dto.CategoryActual;
import io.github.yutaro081.householdbudget.dto.ComparisonRow;
import io.github.yutaro081.householdbudget.dto.TopView;
import io.github.yutaro081.householdbudget.dto.WeekRow;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * トップの画面用のデータの組み立て（設計書 5.11、要件定義書 4.5）。データベースには何も書かない（readOnly）。
 * <p>
 * 【決まり】メソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * 科目・予算・取引は、すべて自分の分だけを取り出す（設計書 3.9）。
 * 「今日」は呼ぶ側（Controller）が日本時間の Clock から取って渡す。ここで LocalDate.now() を呼ばない（設計書 B6）。
 * <p>
 * 計算は domain に置いてある（WeekPeriod、WeeklyAmount、PeriodProgress、PieSlices、BudgetColorJudge）。ここは、
 * DB から取り出した値を、それらに渡して、画面用のデータ（TopView）にまとめるだけ。
 * 季節のメーターは、予実比較の合計行（ComparisonService.compare）をそのまま使う（予実比較と同じ数字になる）。
 */
@Service
public class TopService {

	private final CategoryService categoryService;
	private final BudgetRepository budgetRepository;
	private final TransactionRepository transactionRepository;
	private final ComparisonService comparisonService;

	public TopService(CategoryService categoryService, BudgetRepository budgetRepository,
			TransactionRepository transactionRepository, ComparisonService comparisonService) {
		this.categoryService = categoryService;
		this.budgetRepository = budgetRepository;
		this.transactionRepository = transactionRepository;
		this.comparisonService = comparisonService;
	}

	@Transactional(readOnly = true)
	public TopView top(Long userId, LocalDate today) {
		SeasonPeriod season = SeasonPeriod.from(today);
		WeekPeriod week = WeekPeriod.containing(today);

		// 季節のメーター：予実比較の合計行（予算は設定済みの科目の合計、実績は全科目の合計）
		ComparisonRow seasonTotal = comparisonService.compare(userId, season).total();

		List<Category> categories = categoryService.list(userId);

		// 週の予算の元：今日が入っている季節の予算（行がなければ未設定）
		Map<Long, Integer> seasonBudgetOf = new HashMap<>();
		for (Budget budget : budgetRepository.findByUserIdAndSeasonYearAndSeason(userId, season.year(), season.season())) {
			seasonBudgetOf.put(budget.getCategory().getId(), budget.getAmount());
		}

		// 今週の実績：日曜から次の日曜の前日まで。季節をまたいでも、そのまま入れる
		Map<Long, Long> weekActualOf = new HashMap<>();
		for (CategoryActual actual : transactionRepository.sumByCategory(userId,
				week.start(), week.endDateExclusive())) {
			weekActualOf.put(actual.categoryId(), actual.total());
		}

		List<WeekRow> rows = new ArrayList<>();
		List<PieSlices.Entry> pieEntries = new ArrayList<>();
		long totalActual = 0;
		long totalBudget = 0;
		boolean anyBudget = false;
		for (Category category : categories) {
			Long seasonBudget = seasonBudgetOf.containsKey(category.getId())
					? Long.valueOf(seasonBudgetOf.get(category.getId())) : null;
			Long weeklyBudget = WeeklyAmount.weeklyBudget(seasonBudget);
			long actual = WeeklyAmount.roundUpActual(weekActualOf.getOrDefault(category.getId(), 0L));
			rows.add(row(category.getName(), weeklyBudget, actual));
			pieEntries.add(new PieSlices.Entry(category.getName(), actual));

			totalActual += actual;
			if (weeklyBudget != null) {
				totalBudget += weeklyBudget;
				anyBudget = true;
			}
		}
		WeekRow total = row(null, anyBudget ? Long.valueOf(totalBudget) : null, totalActual);

		List<PieSlices.Slice> slices = PieSlices.of(pieEntries);
		long pieTotal = slices.stream().mapToLong(PieSlices.Slice::amount).sum();

		return new TopView(season, seasonTotal.budget(), seasonTotal.actual(), seasonTotal.difference(),
				PeriodProgress.barPermille(seasonTotal.budget(), seasonTotal.actual()),
				PeriodProgress.seasonElapsedPermille(season, today),
				week.start(), week.endDate(), PeriodProgress.weekElapsedPermille(week.dayNumber(today)),
				!categories.isEmpty(), slices, pieTotal, PieSlices.cssStops(slices), rows, total);
	}

	/** 週の予算（未設定なら null）と、切り上げた実績から、1行（科目の行も合計の行も同じ）を作る。 */
	private static WeekRow row(String name, Long weeklyBudget, long actual) {
		Long difference = weeklyBudget == null ? null : weeklyBudget - actual;
		BudgetColor color = BudgetColorJudge.judge(weeklyBudget, actual);
		boolean within = BudgetColorJudge.isWithinBudget(weeklyBudget, actual);
		return new WeekRow(name, weeklyBudget, actual, difference, color, within,
				PeriodProgress.barPermille(weeklyBudget, actual));
	}

}
