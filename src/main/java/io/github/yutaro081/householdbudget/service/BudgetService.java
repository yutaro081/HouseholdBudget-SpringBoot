package io.github.yutaro081.householdbudget.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.BudgetEditView;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.BudgetForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;

/**
 * 予算設定の表示用の行の組み立てと、まとめての保存（設計書 4.5、5.7、Q23〜Q25）。
 * <p>
 * 【決まり】すべてのメソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * 季節は SeasonPeriod で受け取る。範囲（2000年春〜2100年冬）の外の季節は、呼ぶ側（Controller）が 404 にするが、
 * ここでも NotFoundException にする（守りを二重にする、設計書 5.7）。
 * 科目は、CategoryService.list(userId)（自分の科目だけ）で取り出して照らす。自分で Repository の findById は呼ばない。
 * 他人の科目ID・存在しない科目ID・同じ科目IDが2回・科目IDなし（null）は、NotFoundException（F3、設計書 7.8）。
 * <p>
 * 画面を開いただけでは、データベースに予算の行を作らない。行を作る・更新するのは、save だけ（設計書 5.7、Q25）。
 */
@Service
public class BudgetService {

	private final BudgetRepository budgetRepository;
	private final CategoryService categoryService;

	public BudgetService(BudgetRepository budgetRepository, CategoryService categoryService) {
		this.budgetRepository = budgetRepository;
		this.categoryService = categoryService;
	}

	/**
	 * 予算設定画面の表示データを作る。データベースには何も書かない。
	 * 行は自分の科目の登録順。各行の最初の値は、次の順で決める（設計書 5.7、Q23・Q24）。
	 * <ol>
	 * <li>その季節の予算が保存されていれば、その額（印なし）</li>
	 * <li>なければ、表示季節より前の季節のうち、その科目で予算が設定されている、期間の順でいちばん近い季節の額（「未設定」の印）。
	 * 最後に保存した季節ではない</li>
	 * <li>それもなければ、0円（「未設定」の印）</li>
	 * </ol>
	 *
	 * @throws NotFoundException 範囲外の季節
	 */
	@Transactional(readOnly = true)
	public BudgetEditView edit(Long userId, SeasonPeriod period) {
		requireInRange(period);
		List<Category> categories = categoryService.list(userId);
		Map<Long, Budget> saved = savedOf(userId, period);

		// 保存されていない科目があるときだけ、前の季節の額を探す
		Map<Long, Budget> nearestEarlier = categories.stream().allMatch(c -> saved.containsKey(c.getId()))
				? Map.of()
				: nearestEarlierOf(userId, period);

		List<BudgetEditView.Row> rows = new ArrayList<>();
		for (Category category : categories) {
			Budget own = saved.get(category.getId());
			if (own != null) {
				rows.add(new BudgetEditView.Row(category.getId(), category.getName(), own.getAmount(), false));
			} else {
				Budget earlier = nearestEarlier.get(category.getId());
				rows.add(new BudgetEditView.Row(category.getId(), category.getName(),
						earlier != null ? earlier.getAmount() : 0, true));
			}
		}
		return view(period, rows);
	}

	/**
	 * 入力エラーで表示し直すための行を作る（設計書 5.7）。送られた順のまま、科目IDから科目名と「未設定」の印を引く。
	 * 入力欄の値は、画面が送られた値（Form）を使うので、行の amount は null。
	 * 科目IDの確認（自分の科目か、同じIDが2回ないか、IDがあるか）を、入力チェックの結果を見る前に行う
	 * （入力が間違っていても、他人・存在しないIDなら 404。設計書 7.8、Q6-3）。
	 *
	 * @throws NotFoundException 範囲外の季節。他人・存在しない・重複・なしの科目ID
	 */
	@Transactional(readOnly = true)
	public BudgetEditView rowsOf(Long userId, SeasonPeriod period, BudgetForm form) {
		requireInRange(period);
		Map<Long, Category> ownCategories = ownCategoriesById(userId);
		verifyCategoryIds(ownCategories, form);
		Map<Long, Budget> saved = savedOf(userId, period);

		List<BudgetEditView.Row> rows = new ArrayList<>();
		for (BudgetForm.Row row : form.getRows()) {
			Category category = ownCategories.get(row.getCategoryId());
			rows.add(new BudgetEditView.Row(category.getId(), category.getName(), null, !saved.containsKey(category.getId())));
		}
		return view(period, rows);
	}

	/**
	 * その季節の予算を、まとめて保存する（設計書 5.7、Q25）。全体で1つのトランザクション。
	 * <ol>
	 * <li>送られた科目IDが、すべて自分の科目か、同じIDが2回ないかを確かめる。ちがえば NotFoundException（何も保存しない）</li>
	 * <li>行ごとに、その季節の予算が、なければ作り、あれば金額を変える</li>
	 * </ol>
	 * 送られなかった科目の、その季節の予算は変えない（F2）。ほかの季節・ほかのユーザーの予算は変えない。
	 * 金額の範囲（0〜3,000,000）は、呼ぶ側が Form で確かめた値を渡す前提（最後の守りは DB の CHECK 制約）。
	 * 一意制約（科目・年・季節）に拒否された場合（同時保存）は、特別な扱いをしない（Q6-6 の A）。
	 *
	 * @throws NotFoundException 範囲外の季節。他人・存在しない・重複・なしの科目ID
	 */
	@Transactional
	public void save(Long userId, SeasonPeriod period, BudgetForm form) {
		requireInRange(period);
		Map<Long, Category> ownCategories = ownCategoriesById(userId);
		verifyCategoryIds(ownCategories, form);
		Map<Long, Budget> saved = savedOf(userId, period);

		for (BudgetForm.Row row : form.getRows()) {
			Budget existing = saved.get(row.getCategoryId());
			if (existing != null) {
				existing.changeAmount(row.getAmount());
			} else {
				budgetRepository.save(new Budget(userId, ownCategories.get(row.getCategoryId()),
						period.year(), period.season(), row.getAmount()));
			}
		}
		budgetRepository.flush();
	}

	// ------------------------------------------------------------------

	/** 範囲（2000年春〜2100年冬）の外の季節は、NotFoundException（設計書 2.4、B15）。 */
	private void requireInRange(SeasonPeriod period) {
		if (period == null || !period.isInRange()) {
			throw new NotFoundException();
		}
	}

	/** 自分の科目を、ID をキーにして、登録順のまま返す。 */
	private Map<Long, Category> ownCategoriesById(Long userId) {
		Map<Long, Category> map = new LinkedHashMap<>();
		for (Category category : categoryService.list(userId)) {
			map.put(category.getId(), category);
		}
		return map;
	}

	/** 送られた科目IDが、すべて自分の科目で、同じIDが2回なく、IDがあることを確かめる。ちがえば NotFoundException（F3）。 */
	private void verifyCategoryIds(Map<Long, Category> ownCategories, BudgetForm form) {
		Set<Long> seen = new HashSet<>();
		for (BudgetForm.Row row : form.getRows()) {
			Long categoryId = row.getCategoryId();
			if (categoryId == null || !ownCategories.containsKey(categoryId) || !seen.add(categoryId)) {
				throw new NotFoundException();
			}
		}
	}

	/** その季節の、自分の保存済みの予算を、科目IDをキーにして返す。 */
	private Map<Long, Budget> savedOf(Long userId, SeasonPeriod period) {
		Map<Long, Budget> map = new LinkedHashMap<>();
		for (Budget budget : budgetRepository.findByUserIdAndSeasonYearAndSeason(userId, period.year(), period.season())) {
			map.put(budget.getCategory().getId(), budget);
		}
		return map;
	}

	/**
	 * 表示季節より前の、自分の予算から、科目ごとに、期間の順でいちばん近い季節の予算を選ぶ（設計書 5.7、Q23）。
	 * 「前」は、年が前のもの＋同じ年で季節が前のもの。DB の季節の文字の順ではなく、期間の順で比べる（Q6-4 の A）。
	 */
	private Map<Long, Budget> nearestEarlierOf(Long userId, SeasonPeriod period) {
		List<Budget> earlier = new ArrayList<>(budgetRepository.findByUserIdAndSeasonYearLessThan(userId, period.year()));
		var seasonsBeforeInYear = period.season().earlierInYear();
		if (!seasonsBeforeInYear.isEmpty()) {
			earlier.addAll(budgetRepository.findByUserIdAndSeasonYearAndSeasonIn(userId, period.year(), seasonsBeforeInYear));
		}

		Comparator<Budget> byPeriod = Comparator.comparingInt(Budget::getSeasonYear)
				.thenComparingInt(budget -> budget.getSeason().ordinal());
		Map<Long, Budget> nearest = new LinkedHashMap<>();
		for (Budget budget : earlier) {
			nearest.merge(budget.getCategory().getId(), budget, (a, b) -> byPeriod.compare(a, b) >= 0 ? a : b);
		}
		return nearest;
	}

	private BudgetEditView view(SeasonPeriod period, List<BudgetEditView.Row> rows) {
		return new BudgetEditView(period, period.previous().orElse(null), period.next().orElse(null), rows);
	}

}
