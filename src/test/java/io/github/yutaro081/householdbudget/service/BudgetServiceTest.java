package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.BudgetEditView;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.BudgetForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * BudgetService のテスト（設計書 5.7、5.8、3.9、Q23〜Q25、Q6-3）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストのパスワードのハッシュは、ダミーの値（実在するアカウントのものではない）。
 */
@SpringBootTest
@Transactional
class BudgetServiceTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private BudgetService service;
	@Autowired
	private CategoryService categoryService;
	@Autowired
	private EntityManager entityManager;

	private static SeasonPeriod p(int year, Season season) {
		return new SeasonPeriod(year, season);
	}

	private Long createUser(String username) {
		return userRepository.save(new User(username, "dummy-hash-for-test")).getId();
	}

	private Category category(Long userId, String name) {
		return categoryRepository.save(new Category(userId, name));
	}

	private void saved(Long userId, Category category, int year, Season season, int amount) {
		budgetRepository.save(new Budget(userId, category, year, season, amount));
		entityManager.flush();
		entityManager.clear();
	}

	/** 科目IDと金額の行の並びを、Form にする */
	private static BudgetForm form(Object... categoryIdAndAmount) {
		List<BudgetForm.Row> rows = new ArrayList<>();
		for (int i = 0; i < categoryIdAndAmount.length; i += 2) {
			rows.add(new BudgetForm.Row((Long) categoryIdAndAmount[i], (Integer) categoryIdAndAmount[i + 1]));
		}
		return new BudgetForm(rows);
	}

	/** そのユーザーの予算の行を、全部の季節ぶん取り出す */
	private List<Budget> allBudgetsOf(Long userId) {
		entityManager.flush();
		entityManager.clear();
		return budgetRepository.findByUserIdAndSeasonYearLessThan(userId, 100000);
	}

	private static BudgetEditView.Row rowOf(BudgetEditView view, Long categoryId) {
		return view.rows().stream().filter(r -> r.categoryId().equals(categoryId)).findFirst().orElseThrow();
	}

	// ================================================================
	// 表示：最初の値と「未設定」の印（設計書 5.7、手順書 3.3 の具体例）
	// ================================================================

	/** 保存済みの季節：その額、印なし（初期ユーザーの今の季節） */
	@Test
	void savedSeasonShowsTheSavedAmountWithoutMark() {
		Long u = createUser("bsvc1");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.AUTUMN, 30000);

		BudgetEditView.Row row = rowOf(service.edit(u, p(2026, Season.AUTUMN)), food.getId());

		assertThat(row.amount()).isEqualTo(30000);
		assertThat(row.unset()).isFalse();
		assertThat(row.categoryName()).isEqualTo("食費");
	}

	/** 次の季節（冬）：行がないので、前の季節（秋）の額が初期値。印あり */
	@Test
	void nextSeasonShowsThePreviousSeasonAmountWithMark() {
		Long u = createUser("bsvc2");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.AUTUMN, 30000);

		BudgetEditView.Row row = rowOf(service.edit(u, p(2026, Season.WINTER)), food.getId());

		assertThat(row.amount()).isEqualTo(30000);
		assertThat(row.unset()).isTrue();
	}

	/** 夏：夏より後の行（秋）は見ない。0円、印あり */
	@Test
	void seasonBeforeTheOnlySavedRowShowsZeroWithMark() {
		Long u = createUser("bsvc3");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.AUTUMN, 30000);

		BudgetEditView.Row row = rowOf(service.edit(u, p(2026, Season.SUMMER)), food.getId());

		assertThat(row.amount()).isZero();
		assertThat(row.unset()).isTrue();
	}

	/** 2027年春：秋と冬が保存済みなら、直前＝冬の額 */
	@Test
	void nearestEarlierSeasonIsUsed() {
		Long u = createUser("bsvc4");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.AUTUMN, 30000);
		saved(u, food, 2026, Season.WINTER, 50000);

		BudgetEditView.Row row = rowOf(service.edit(u, p(2027, Season.SPRING)), food.getId());

		assertThat(row.amount()).isEqualTo(50000);
		assertThat(row.unset()).isTrue();
	}

	/** 今日追加した科目：今の季節に0円の行がある（Phase 4）ので、0円・印なし */
	@Test
	void categoryAddedTodayShowsZeroWithoutMark() {
		Long u = createUser("bsvc5");
		Category added = category(u, "間食");
		saved(u, added, 2026, Season.AUTUMN, 0);

		BudgetEditView.Row row = rowOf(service.edit(u, p(2026, Season.AUTUMN)), added.getId());

		assertThat(row.amount()).isZero();
		assertThat(row.unset()).isFalse();
		// ほかの季節では、0円・印あり
		assertThat(rowOf(service.edit(u, p(2026, Season.WINTER)), added.getId()).unset()).isTrue();
		assertThat(rowOf(service.edit(u, p(2026, Season.WINTER)), added.getId()).amount()).isZero();
	}

	/** 直前は、最後に保存した季節ではなく、期間の順でいちばん近い季節（Q23）。後の季節の額は使わない */
	@Test
	void nearestEarlierIsByPeriodOrderNotByLastSaved() {
		Long u = createUser("bsvc6");
		Category food = category(u, "食費");
		saved(u, food, 2025, Season.SPRING, 111);
		saved(u, food, 2026, Season.AUTUMN, 222);      // 最後に保存した季節（夏より後）
		saved(u, food, 2026, Season.WINTER, 333);

		BudgetEditView.Row summer = rowOf(service.edit(u, p(2026, Season.SUMMER)), food.getId());

		assertThat(summer.amount()).isEqualTo(111);     // 夏より前でいちばん近いのは、2025年春
		assertThat(summer.unset()).isTrue();
	}

	/** 年をまたぐ：2026年春の直前は、2025年の冬（12月がある年が冬の年）。2025年の秋・2024年の冬より近い */
	@Test
	void nearestEarlierAcrossTheYearBoundary() {
		Long u = createUser("bsvc7");
		Category food = category(u, "食費");
		saved(u, food, 2024, Season.WINTER, 1);
		saved(u, food, 2025, Season.AUTUMN, 2);
		saved(u, food, 2025, Season.WINTER, 3);
		saved(u, food, 2026, Season.SPRING, 9);        // 同じ季節は「前」ではない（保存済みなので、その額が出る）

		assertThat(rowOf(service.edit(u, p(2026, Season.SPRING)), food.getId()).amount()).isEqualTo(9);
		assertThat(rowOf(service.edit(u, p(2026, Season.SPRING)), food.getId()).unset()).isFalse();
		// 2026年春の行がない別の科目を見るために、夏で確かめる：直前は2026年春
		assertThat(rowOf(service.edit(u, p(2026, Season.SUMMER)), food.getId()).amount()).isEqualTo(9);
	}

	@Test
	void nearestEarlierWithinTheSameYear() {
		Long u = createUser("bsvc8");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.SPRING, 10);
		saved(u, food, 2026, Season.SUMMER, 20);

		assertThat(rowOf(service.edit(u, p(2026, Season.AUTUMN)), food.getId()).amount()).isEqualTo(20);
		assertThat(rowOf(service.edit(u, p(2026, Season.WINTER)), food.getId()).amount()).isEqualTo(20);
	}

	/** 科目ごとに独立：ある科目は前の季節の額、ある科目は保存済み、ある科目は0円 */
	@Test
	void eachCategoryIsDecidedIndependently() {
		Long u = createUser("bsvc9");
		Category food = category(u, "食費");
		Category fare = category(u, "交通費");
		Category misc = category(u, "雑費");
		saved(u, food, 2026, Season.AUTUMN, 30000);                   // 冬：前の季節から
		saved(u, fare, 2026, Season.WINTER, 5000);                    // 冬：保存済み
		// 雑費：どの季節も行なし

		BudgetEditView view = service.edit(u, p(2026, Season.WINTER));

		assertThat(rowOf(view, food.getId()).amount()).isEqualTo(30000);
		assertThat(rowOf(view, food.getId()).unset()).isTrue();
		assertThat(rowOf(view, fare.getId()).amount()).isEqualTo(5000);
		assertThat(rowOf(view, fare.getId()).unset()).isFalse();
		assertThat(rowOf(view, misc.getId()).amount()).isZero();
		assertThat(rowOf(view, misc.getId()).unset()).isTrue();
	}

	@Test
	void rowsAreInRegistrationOrderAndOnlyOwn() {
		Long a = createUser("bsvc10");
		Long b = createUser("bsvc11");
		Category first = category(a, "交通費");
		category(b, "他人の科目");
		Category second = category(a, "食費");
		Category third = category(a, "雑費");
		saved(b, categoryRepository.findAll().stream().filter(c -> c.getUserId().equals(b)).findFirst().orElseThrow(),
				2026, Season.AUTUMN, 999);

		BudgetEditView view = service.edit(a, p(2026, Season.AUTUMN));

		assertThat(view.rows()).extracting(BudgetEditView.Row::categoryId)
				.containsExactly(first.getId(), second.getId(), third.getId());
		assertThat(view.rows()).extracting(BudgetEditView.Row::categoryName).doesNotContain("他人の科目");
	}

	/** 他のユーザーの予算は、初期値に使われない */
	@Test
	void otherUsersBudgetsAreNotUsedForInitialValues() {
		Long a = createUser("bsvc12");
		Long b = createUser("bsvc13");
		Category aFood = category(a, "食費");
		Category bFood = category(b, "食費");
		saved(b, bFood, 2026, Season.AUTUMN, 99999);

		BudgetEditView.Row row = rowOf(service.edit(a, p(2026, Season.WINTER)), aFood.getId());

		assertThat(row.amount()).isZero();
		assertThat(row.unset()).isTrue();
	}

	@Test
	void noCategoriesMeansNoRows() {
		Long u = createUser("bsvc14");

		BudgetEditView view = service.edit(u, p(2026, Season.AUTUMN));

		assertThat(view.rows()).isEmpty();
	}

	/** 見出し・前後の季節（範囲の端では null） */
	@Test
	void viewHasThePeriodAndPreviousAndNext() {
		Long u = createUser("bsvc15");

		BudgetEditView winter = service.edit(u, p(2026, Season.WINTER));
		assertThat(winter.period()).isEqualTo(p(2026, Season.WINTER));
		assertThat(winter.previous()).isEqualTo(p(2026, Season.AUTUMN));
		assertThat(winter.next()).isEqualTo(p(2027, Season.SPRING));

		BudgetEditView first = service.edit(u, p(2000, Season.SPRING));
		assertThat(first.previous()).isNull();
		assertThat(first.next()).isEqualTo(p(2000, Season.SUMMER));

		BudgetEditView last = service.edit(u, p(2100, Season.WINTER));
		assertThat(last.previous()).isEqualTo(p(2100, Season.AUTUMN));
		assertThat(last.next()).isNull();
	}

	/** 表示しただけでは、行が増えない（設計書 5.7、Q25） */
	@Test
	void editDoesNotCreateAnyRows() {
		Long u = createUser("bsvc16");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.AUTUMN, 30000);
		int before = allBudgetsOf(u).size();

		service.edit(u, p(2026, Season.WINTER));
		service.edit(u, p(2027, Season.SPRING));
		service.edit(u, p(2026, Season.AUTUMN));
		service.rowsOf(u, p(2026, Season.WINTER), form(food.getId(), 1));

		assertThat(allBudgetsOf(u)).hasSize(before).hasSize(1);
	}

	// ================================================================
	// 範囲外の季節
	// ================================================================

	@ParameterizedTest(name = "{0}年 {1} は範囲外 → NotFoundException")
	@CsvSource({ "1999, WINTER", "1999, SPRING", "2101, SPRING", "2101, WINTER", "0, SPRING", "9999, SUMMER" })
	void outOfRangeSeasonIsNotFound(int year, Season season) {
		Long u = createUser("bsvc17");
		Category food = category(u, "食費");
		BudgetForm form = form(food.getId(), 100);
		SeasonPeriod period = p(year, season);

		assertThatThrownBy(() -> service.edit(u, period)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.rowsOf(u, period, form)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.save(u, period, form)).isInstanceOf(NotFoundException.class);
		assertThat(allBudgetsOf(u)).isEmpty();
	}

	@Test
	void nullPeriodIsNotFound() {
		Long u = createUser("bsvc18");
		assertThatThrownBy(() -> service.edit(u, null)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.save(u, null, new BudgetForm())).isInstanceOf(NotFoundException.class);
	}

	@Test
	void edgesOfTheRangeCanBeShownAndSaved() {
		Long u = createUser("bsvc19");
		Category food = category(u, "食費");

		service.save(u, p(2000, Season.SPRING), form(food.getId(), 100));
		service.save(u, p(2100, Season.WINTER), form(food.getId(), 200));

		assertThat(allBudgetsOf(u)).extracting(Budget::getSeasonYear).containsExactlyInAnyOrder(2000, 2100);
	}

	// ================================================================
	// 保存
	// ================================================================

	/** 全科目の行が作られる。初期値のまま変えなかった行も、その額で作られ、印が消える */
	@Test
	void saveCreatesRowsForAllPostedCategoriesAndMarksDisappear() {
		Long u = createUser("bsvc20");
		Category food = category(u, "食費");
		Category fare = category(u, "交通費");
		saved(u, food, 2026, Season.AUTUMN, 30000);
		saved(u, fare, 2026, Season.AUTUMN, 30000);
		SeasonPeriod winter = p(2026, Season.WINTER);

		BudgetEditView before = service.edit(u, winter);
		assertThat(before.rows()).allMatch(BudgetEditView.Row::unset);

		// 画面に表示された額（食費30000、交通費30000）のまま、食費だけ変えて保存する
		service.save(u, winter, form(food.getId(), 50000, fare.getId(), 30000));

		List<Budget> winterRows = budgetRepository.findByUserIdAndSeasonYearAndSeason(u, 2026, Season.WINTER);
		assertThat(winterRows).hasSize(2);
		assertThat(winterRows).extracting(b -> b.getCategory().getId()).containsExactlyInAnyOrder(food.getId(), fare.getId());
		assertThat(winterRows).extracting(Budget::getAmount).containsExactlyInAnyOrder(50000, 30000);
		assertThat(winterRows).allMatch(b -> b.getUserId().equals(u) && b.getSeasonYear() == 2026 && b.getSeason() == Season.WINTER);

		BudgetEditView after = service.edit(u, winter);
		assertThat(after.rows()).noneMatch(BudgetEditView.Row::unset);
		assertThat(rowOf(after, food.getId()).amount()).isEqualTo(50000);
	}

	/** すでにある行は更新され、行は増えない。もう一度保存しても増えない */
	@Test
	void saveUpdatesExistingRowsWithoutAddingAndIsRepeatable() {
		Long u = createUser("bsvc21");
		Category food = category(u, "食費");
		saved(u, food, 2026, Season.AUTUMN, 30000);
		SeasonPeriod autumn = p(2026, Season.AUTUMN);

		service.save(u, autumn, form(food.getId(), 45000));
		entityManager.flush();
		entityManager.clear();
		service.save(u, autumn, form(food.getId(), 45000));

		List<Budget> rows = allBudgetsOf(u);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getAmount()).isEqualTo(45000);
	}

	@Test
	void saveBoundaryAmounts() {
		Long u = createUser("bsvc22");
		Category food = category(u, "食費");
		Category fare = category(u, "交通費");

		service.save(u, p(2026, Season.AUTUMN), form(food.getId(), 0, fare.getId(), 3_000_000));

		List<Budget> rows = allBudgetsOf(u);
		assertThat(rows).extracting(Budget::getAmount).containsExactlyInAnyOrder(0, 3_000_000);
	}

	/** ほかの季節・ほかのユーザーの予算は変わらない */
	@Test
	void saveDoesNotTouchOtherSeasonsOrOtherUsers() {
		Long a = createUser("bsvc23");
		Long b = createUser("bsvc24");
		Category aFood = category(a, "食費");
		Category bFood = category(b, "食費");
		saved(a, aFood, 2026, Season.AUTUMN, 30000);
		saved(a, aFood, 2027, Season.WINTER, 77);
		saved(b, bFood, 2026, Season.WINTER, 12345);

		service.save(a, p(2026, Season.WINTER), form(aFood.getId(), 50000));

		List<Budget> aRows = allBudgetsOf(a);
		assertThat(aRows).hasSize(3);
		assertThat(aRows.stream().filter(r -> r.getSeason() == Season.AUTUMN).findFirst().orElseThrow().getAmount()).isEqualTo(30000);
		assertThat(aRows.stream().filter(r -> r.getSeasonYear() == 2027).findFirst().orElseThrow().getAmount()).isEqualTo(77);
		List<Budget> bRows = allBudgetsOf(b);
		assertThat(bRows).hasSize(1);
		assertThat(bRows.get(0).getAmount()).isEqualTo(12345);
	}

	/** 送られなかった科目の、その季節の予算は変わらない（F2）。行も作られない */
	@Test
	void categoriesNotSentAreUntouched() {
		Long u = createUser("bsvc25");
		Category food = category(u, "食費");
		Category fare = category(u, "交通費");
		saved(u, fare, 2026, Season.WINTER, 4000);          // 送らない科目の、その季節の行

		service.save(u, p(2026, Season.WINTER), form(food.getId(), 1000));

		List<Budget> rows = allBudgetsOf(u);
		assertThat(rows).hasSize(2);
		assertThat(rows.stream().filter(r -> r.getCategory().getId().equals(fare.getId())).findFirst().orElseThrow().getAmount())
				.isEqualTo(4000);
	}

	@Test
	void saveWithNoRowsSavesNothing() {
		Long u = createUser("bsvc26");
		category(u, "食費");

		service.save(u, p(2026, Season.AUTUMN), new BudgetForm());

		assertThat(allBudgetsOf(u)).isEmpty();
	}

	// ---- 科目IDの確認：だめなら NotFoundException で、何も保存しない ----

	private void assertNothingSavedAndNotFound(Long userId, SeasonPeriod period, BudgetForm bad, Runnable beforeAssertions) {
		int before = allBudgetsOf(userId).size();
		assertThatThrownBy(() -> service.save(userId, period, bad)).isInstanceOf(NotFoundException.class);
		beforeAssertions.run();
		assertThat(allBudgetsOf(userId)).hasSize(before);
	}

	/** 他人の科目IDが1つでも混ざると NotFoundException。ほかの正しい行も保存されない */
	@Test
	void otherUsersCategoryIdMakesTheWholeSaveNotFound() {
		Long a = createUser("bsvc27");
		Long b = createUser("bsvc28");
		Category mine = category(a, "食費");
		Category others = category(b, "食費");
		saved(a, mine, 2026, Season.AUTUMN, 30000);

		assertNothingSavedAndNotFound(a, p(2026, Season.WINTER), form(mine.getId(), 111, others.getId(), 222), () -> { });

		assertThat(budgetRepository.findByUserIdAndSeasonYearAndSeason(a, 2026, Season.WINTER)).isEmpty();
		assertThat(allBudgetsOf(b)).isEmpty();
	}

	/** 先頭が他人でも、後ろが他人でも同じ。すでにある行の金額も変わらない */
	@Test
	void notFoundDoesNotChangeExistingRows() {
		Long a = createUser("bsvc29");
		Long b = createUser("bsvc30");
		Category mine = category(a, "食費");
		Category others = category(b, "食費");
		saved(a, mine, 2026, Season.WINTER, 30000);

		assertNothingSavedAndNotFound(a, p(2026, Season.WINTER), form(others.getId(), 1, mine.getId(), 999), () -> { });
		assertNothingSavedAndNotFound(a, p(2026, Season.WINTER), form(mine.getId(), 999, others.getId(), 1), () -> { });

		assertThat(budgetRepository.findByUserIdAndSeasonYearAndSeason(a, 2026, Season.WINTER))
				.extracting(Budget::getAmount).containsExactly(30000);
	}

	@Test
	void nonexistentCategoryIdIsNotFound() {
		Long a = createUser("bsvc31");
		Category mine = category(a, "食費");

		assertNothingSavedAndNotFound(a, p(2026, Season.AUTUMN), form(mine.getId(), 100, mine.getId() + 1_000_000, 200), () -> { });
	}

	/** 同じ科目IDが2回送られたときも NotFoundException（F3） */
	@Test
	void duplicatedCategoryIdIsNotFound() {
		Long a = createUser("bsvc32");
		Category mine = category(a, "食費");
		Category other = category(a, "交通費");

		assertNothingSavedAndNotFound(a, p(2026, Season.AUTUMN), form(mine.getId(), 100, other.getId(), 200, mine.getId(), 300), () -> { });
	}

	@Test
	void nullCategoryIdIsNotFound() {
		Long a = createUser("bsvc33");
		Category mine = category(a, "食費");
		BudgetForm bad = new BudgetForm(List.of(new BudgetForm.Row(mine.getId(), 100), new BudgetForm.Row(null, 200)));

		assertNothingSavedAndNotFound(a, p(2026, Season.AUTUMN), bad, () -> { });
	}

	// ---- 入力エラーで表示し直すための行（rowsOf）：科目IDの確認を先にする ----

	@Test
	void rowsOfKeepsThePostedOrderAndLooksUpNamesAndMarks() {
		Long u = createUser("bsvc34");
		Category food = category(u, "食費");
		Category fare = category(u, "交通費");
		Category misc = category(u, "雑費");
		saved(u, fare, 2026, Season.WINTER, 5000);                        // 冬に保存済みなのは交通費だけ

		// 送られた順は、登録順と違う（雑費、食費、交通費）。送られなかった科目はない
		BudgetEditView view = service.rowsOf(u, p(2026, Season.WINTER),
				form(misc.getId(), null, food.getId(), null, fare.getId(), null));

		assertThat(view.rows()).extracting(BudgetEditView.Row::categoryName).containsExactly("雑費", "食費", "交通費");
		assertThat(view.rows()).extracting(BudgetEditView.Row::unset).containsExactly(true, true, false);
		assertThat(view.rows()).extracting(BudgetEditView.Row::amount).containsOnlyNulls();
		assertThat(view.period()).isEqualTo(p(2026, Season.WINTER));
	}

	@Test
	void rowsOfWithBadCategoryIdsIsNotFound() {
		Long a = createUser("bsvc35");
		Long b = createUser("bsvc36");
		Category mine = category(a, "食費");
		Category others = category(b, "食費");
		SeasonPeriod period = p(2026, Season.AUTUMN);

		assertThatThrownBy(() -> service.rowsOf(a, period, form(others.getId(), null))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.rowsOf(a, period, form(mine.getId(), null, others.getId(), null))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.rowsOf(a, period, form(mine.getId(), null, mine.getId(), null))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.rowsOf(a, period, form(mine.getId() + 1_000_000, null))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.rowsOf(a, period,
				new BudgetForm(List.of(new BudgetForm.Row(null, null))))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void rowsOfWithNoRowsIsEmpty() {
		Long u = createUser("bsvc37");
		category(u, "食費");

		assertThat(service.rowsOf(u, p(2026, Season.AUTUMN), new BudgetForm()).rows()).isEmpty();
	}

	// ================================================================
	// 科目の削除との関係（要件4.3：いずれかの季節に0円でない予算がある科目は削除できない）
	// ================================================================

	@Test
	void categoryWithNonZeroSavedBudgetCannotBeDeletedButZeroCan() {
		Long u = createUser("bsvc38");
		Category nonZero = category(u, "食費");
		Category zero = category(u, "間食");

		service.save(u, p(2026, Season.WINTER), form(nonZero.getId(), 1, zero.getId(), 0));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> categoryService.delete(u, nonZero.getId()))
				.isInstanceOf(DeletionNotAllowedException.class).hasMessageContaining("error.delete.budgetNotZero");
		categoryService.delete(u, zero.getId());
		entityManager.flush();
		entityManager.clear();

		// 削除した科目の予算の行も消えている
		assertThat(allBudgetsOf(u)).extracting(b -> b.getCategory().getId()).containsOnly(nonZero.getId());
	}

}
