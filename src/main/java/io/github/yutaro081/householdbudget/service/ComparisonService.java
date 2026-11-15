package io.github.yutaro081.householdbudget.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.BudgetColor;
import io.github.yutaro081.householdbudget.domain.BudgetColorJudge;
import io.github.yutaro081.householdbudget.domain.CountingStart;
import io.github.yutaro081.householdbudget.domain.LandingForecast;
import io.github.yutaro081.householdbudget.domain.LandingVerdict;
import io.github.yutaro081.householdbudget.domain.MeterScale;
import io.github.yutaro081.householdbudget.domain.PeriodProgress;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.CategoryActual;
import io.github.yutaro081.householdbudget.dto.ComparisonResult;
import io.github.yutaro081.householdbudget.dto.ComparisonRow;
import io.github.yutaro081.householdbudget.dto.ComparisonView;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 予実比較の表の組み立て（設計書 4.5、5.9）。データベースには何も書かない（readOnly）。
 * <p>
 * 【決まり】メソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * 科目・予算・取引は、すべて自分の分だけを取り出す（設計書 3.9）。
 * 範囲（2000年春〜2100年冬）の外の季節は、呼ぶ側（Controller）が 404 にするが、ここでも NotFoundException にする
 * （守りを二重にする。BudgetService と同じ）。
 * <p>
 * 予算は、その季節の行だけを見る。行がなければ「未設定」。予算設定画面の「前の季節の額を最初の値にする」は、
 * 入力欄の初期値だけの決まりで、ここでは使わない（設計書 2.4、R11）。
 */
@Service
public class ComparisonService {

	private final CategoryService categoryService;
	private final BudgetRepository budgetRepository;
	private final TransactionRepository transactionRepository;

	public ComparisonService(CategoryService categoryService, BudgetRepository budgetRepository,
			TransactionRepository transactionRepository) {
		this.categoryService = categoryService;
		this.budgetRepository = budgetRepository;
		this.transactionRepository = transactionRepository;
	}

	/**
	 * 着地見込み・自動予算のペースの、日数を数え始める日（設計書 5.13、issue 020）。
	 * 自分のいちばん最初の取引の日が、その季節の中にあり、今日より後でないときだけ、その日。それ以外は季節の初日。
	 */
	@Transactional(readOnly = true)
	public LocalDate countingStart(Long userId, SeasonPeriod season, LocalDate today) {
		return CountingStart.of(season, transactionRepository.findFirstTransactionDate(userId).orElse(null), today);
	}

	/**
	 * 指定した季節の、予実比較の表を作る（設計書 5.9）。
	 * <ol>
	 * <li>自分の科目を、登録順に取り出す</li>
	 * <li>その季節の自分の予算を、科目ごとに探す（行がなければ未設定）</li>
	 * <li>期間（開始日以上、終了日の翌日未満）の自分の取引を、科目ごとに合計する（取引がなければ0円）</li>
	 * <li>科目ごとに、差額（予算 − 実績。未設定なら空）と色（BudgetColorJudge）を決めて、1行を作る</li>
	 * <li>合計行：予算は設定済みの科目の予算の合計、実績は全科目の実績の合計。設定済みの科目が1つもなければ予算は未設定。色は同じ判定</li>
	 * </ol>
	 *
	 * @throws NotFoundException 範囲外の季節（null を含む）
	 */
	@Transactional(readOnly = true)
	public ComparisonResult compare(Long userId, SeasonPeriod period) {
		if (period == null || !period.isInRange()) {
			throw new NotFoundException();
		}

		List<Category> categories = categoryService.list(userId);

		Map<Long, Integer> budgetOf = new HashMap<>();
		for (Budget budget : budgetRepository.findByUserIdAndSeasonYearAndSeason(userId, period.year(), period.season())) {
			budgetOf.put(budget.getCategory().getId(), budget.getAmount());
		}

		Map<Long, Long> actualOf = new HashMap<>();
		for (CategoryActual actual : transactionRepository.sumByCategory(userId,
				period.startDate(), period.endDateExclusive())) {
			actualOf.put(actual.categoryId(), actual.total());
		}

		List<ComparisonRow> rows = new ArrayList<>();
		long totalActual = 0;
		long totalBudget = 0;
		boolean anyBudget = false;
		for (Category category : categories) {
			Long budget = budgetOf.containsKey(category.getId()) ? Long.valueOf(budgetOf.get(category.getId())) : null;
			long actual = actualOf.getOrDefault(category.getId(), 0L);
			rows.add(row(category.getId(), category.getName(), budget, actual));

			totalActual += actual;
			if (budget != null) {
				totalBudget += budget;
				anyBudget = true;
			}
		}
		ComparisonRow total = row(null, null, anyBudget ? Long.valueOf(totalBudget) : null, totalActual);

		return new ComparisonResult(period, period.previous().orElse(null), period.next().orElse(null), rows, total);
	}

	/**
	 * 予実比較の画面用のまとまり（設計書 4.5・4.7、5.13。issue 027。それまでは Controller が計算していた）。
	 * {@link #compare} の結果に、今の季節のときだけ、科目ごと・合計の見込みと、見込みとの差、要約カードの値を足す。
	 * 「今日」は、呼ぶ側が 1 回だけ取って渡す（季節を決めた日と、同じ日を使う。設計書 B6）。
	 * 範囲の外の季節は NotFoundException（compare と同じ）。
	 *
	 * @param today 今日（日本時間）
	 */
	@Transactional(readOnly = true)
	public ComparisonView view(Long userId, SeasonPeriod period, LocalDate today) {
		ComparisonResult comparison = compare(userId, period);
		boolean showForecast = period.equals(SeasonPeriod.from(today));
		// 数え始めの日は、最初の取引の日が季節の途中にあるときだけ、その日（設計書 5.13）
		LocalDate countFrom = countingStart(userId, period, today);

		List<ComparisonView.Line> lines = new ArrayList<>();
		for (ComparisonRow row : comparison.rows()) {
			lines.add(line(row, period, today, countFrom));
		}
		ComparisonView.Line total = line(comparison.total(), period, today, countFrom);
		return new ComparisonView(comparison, showForecast, List.copyOf(lines), total, card(total, period, today, countFrom));
	}

	/**
	 * 表の 1 行に、見込みと、見込みとの差（予算 − 表示する見込みの額）を添える。
	 * 見込みは、今の季節で、予算が 0 円より大きいときだけ（LandingForecast が決める）。出さないときは、見込みも差も null（画面は「—」）。
	 */
	private static ComparisonView.Line line(ComparisonRow row, SeasonPeriod period, LocalDate today, LocalDate countFrom) {
		Optional<LandingForecast.Result> forecast = LandingForecast.of(row.budget(), row.actual(), period, today, countFrom);
		Long gap = forecast.isPresent() ? Long.valueOf(row.budget() - forecast.get().amount()) : null;
		return new ComparisonView.Line(row, forecast.orElse(null), gap);
	}

	/**
	 * 要約カード。合計の見込みがあるときだけ（ないときは null）。
	 * 差の文は、合計の行の「見込みとの差」と同じ値から決める。棒は、トップの季節のメーターと同じ求め方（PeriodProgress、MeterScale）。
	 */
	private static ComparisonView.Card card(ComparisonView.Line total, SeasonPeriod period, LocalDate today, LocalDate countFrom) {
		if (total.forecast() == null) {
			return null;
		}
		Long budget = total.row().budget();
		long actual = total.row().actual();
		return new ComparisonView.Card(total.forecast(), LandingVerdict.of(total.gap()),
				PeriodProgress.usedPercent(budget, actual), PeriodProgress.seasonElapsedPercent(period, today),
				MeterScale.of(budget, actual, PeriodProgress.seasonElapsedPermille(period, today)),
				// 最初の取引の日から数えたとき（その日が季節の初日より後のとき）だけ、カードに知らせる
				countFrom.isAfter(period.startDate()) ? countFrom : null);
	}

	/** 予算（未設定なら null）と実績から、1行（科目の行も合計行も同じ）を作る。合計行は、科目の ID も名前も null。 */
	private static ComparisonRow row(Long categoryId, String name, Long budget, long actual) {
		Long difference = budget == null ? null : budget - actual;
		BudgetColor color = BudgetColorJudge.judge(budget, actual);
		boolean withinBudget = BudgetColorJudge.isWithinBudget(budget, actual);
		return new ComparisonRow(categoryId, name, budget, actual, difference, color, withinBudget);
	}

}
