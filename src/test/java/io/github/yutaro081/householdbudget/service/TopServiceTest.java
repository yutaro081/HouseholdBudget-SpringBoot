package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.BudgetColor;
import io.github.yutaro081.householdbudget.domain.PieSlices;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ComparisonResult;
import io.github.yutaro081.householdbudget.dto.TopView;
import io.github.yutaro081.householdbudget.dto.WeekRow;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * TopService のテスト（設計書 5.11、3.9、要件定義書 4.5）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は引数で渡す。基本は 2026-11-08（日曜。秋。今週は 11/8〜11/14）。
 * 計算そのもの（切り上げ・円の並びなど）は domain のテストで確かめてあるので、ここでは、
 * 取り出す範囲（週の境目、季節をまたぐ週、他の人のデータ）と、組み立てを確かめる。
 * テストのパスワードのハッシュは、ダミーの値。
 */
@SpringBootTest
@Transactional
class TopServiceTest {

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final SeasonPeriod WINTER_2026 = new SeasonPeriod(2026, Season.WINTER);

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private TopService service;
	@Autowired
	private ComparisonService comparisonService;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long id, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id,
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category category(Owner o, String name) {
		return categoryRepository.save(new Category(o.id(), name));
	}

	private void budget(Owner o, Category c, SeasonPeriod period, int amount) {
		budgetRepository.save(new Budget(o.id(), c, period.year(), period.season(), amount));
	}

	private void spend(Owner o, Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(o.id(), date, amount, c, null, o.vendor(), o.paymentMethod()));
	}

	private TopView top(Owner o, LocalDate today) {
		entityManager.flush();
		entityManager.clear();
		return service.top(o.id(), today);
	}

	/** 食費・交通費・雑費を、秋の予算 30,000 円ずつで作る */
	private Category[] threeCategories(Owner o) {
		Category[] cs = { category(o, "食費"), category(o, "交通費"), category(o, "雑費") };
		for (Category c : cs) {
			budget(o, c, AUTUMN_2026, 30_000);
		}
		return cs;
	}

	// ---- 週の予算・切り上げ・合計 ----

	@Test
	void weeklyBudgetsAreBudgetDividedBy13RoundedUpTo100AndTotalIsTheSum() {
		Owner a = createOwner("toptest01");
		threeCategories(a);

		TopView view = top(a, SUNDAY);

		assertThat(view.rows()).extracting(WeekRow::categoryName).containsExactly("食費", "交通費", "雑費");
		assertThat(view.rows()).extracting(WeekRow::budget).containsExactly(2400L, 2400L, 2400L);
		assertThat(view.total().categoryName()).isNull();
		assertThat(view.total().budget()).isEqualTo(7200L);   // 90,000 ÷ 13 を切り上げた 7,000 ではなく、科目ごとの合計
		assertThat(view.total().actual()).isZero();
		assertThat(view.hasCategories()).isTrue();
	}

	@Test
	void actualIsRoundedUpPerCategoryAfterAddingUpTheWeek() {
		Owner a = createOwner("toptest02");
		Category[] cs = threeCategories(a);
		spend(a, cs[0], LocalDate.of(2026, 11, 8), 50);
		spend(a, cs[0], LocalDate.of(2026, 11, 10), 50);   // 食費は 100 円（50 円ずつ切り上げた 200 円ではない）
		spend(a, cs[1], LocalDate.of(2026, 11, 9), 1);     // 交通費は 100 円

		TopView view = top(a, SUNDAY);

		assertThat(view.rows()).extracting(WeekRow::actual).containsExactly(100L, 100L, 0L);
		assertThat(view.total().actual()).isEqualTo(200L);   // 切り上げた額の和
		assertThat(view.slices()).extracting(s -> s.amount()).containsExactly(100L, 100L);
		assertThat(view.pieTotal()).isEqualTo(200L);
	}

	// ---- 週の境目 ----

	@Test
	void onlyTransactionsFromSundayToSaturdayAreCounted() {
		Owner a = createOwner("toptest03");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);
		spend(a, food, LocalDate.of(2026, 11, 7), 1_000);    // 前の週の土曜：入らない
		spend(a, food, LocalDate.of(2026, 11, 8), 100);      // 日曜：入る
		spend(a, food, LocalDate.of(2026, 11, 14), 200);     // 土曜：入る
		spend(a, food, LocalDate.of(2026, 11, 15), 10_000);  // 次の週の日曜：入らない

        TopView view = top(a, LocalDate.of(2026, 11, 11));   // 水曜

		assertThat(view.weekStart()).isEqualTo(LocalDate.of(2026, 11, 8));
		assertThat(view.weekEnd()).isEqualTo(LocalDate.of(2026, 11, 14));
		assertThat(view.rows().get(0).actual()).isEqualTo(300L);
	}

	@Test
	void aWeekAcrossSeasonsCountsBothSeasonsButUsesTheBudgetOfTodaysSeason() {
		Owner a = createOwner("toptest04");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);   // 週の予算 2,400
		budget(a, food, WINTER_2026, 13_000);   // 週の予算 1,000
		spend(a, food, LocalDate.of(2026, 11, 30), 1_000);   // 秋
		spend(a, food, LocalDate.of(2026, 12, 1), 500);      // 冬

		TopView onWednesday = top(a, LocalDate.of(2026, 12, 2));   // 週は 11/29〜12/5。今日は冬

		assertThat(onWednesday.season()).isEqualTo(WINTER_2026);
		WeekRow row = onWednesday.rows().get(0);
		assertThat(row.budget()).isEqualTo(1_000L);                // 冬の予算
		assertThat(row.actual()).isEqualTo(1_500L);                // 秋の取引も入る
		assertThat(row.color()).isEqualTo(BudgetColor.RED);
		// 季節のメーターは今の季節（冬）だけ
		assertThat(onWednesday.seasonActual()).isEqualTo(500L);
		assertThat(onWednesday.seasonBudget()).isEqualTo(13_000L);

		TopView onMonday = top(a, LocalDate.of(2026, 11, 30));     // 同じ週の月曜。今日は秋
		assertThat(onMonday.rows().get(0).budget()).isEqualTo(2_400L);   // 日付が変わると、週の予算も変わる
		assertThat(onMonday.rows().get(0).actual()).isEqualTo(1_500L);
	}

	// ---- 色 ----

	@ParameterizedTest(name = "実績 {0} 円 → 切り上げ {1} 円・{2}・💮 {3}")
	@CsvSource({
			"0,    0,    BLUE, false",
			"800,  800,  BLUE, false",
			"801,  900,  NONE, true",
			"2400, 2400, NONE, true",
			"2401, 2500, RED,  false"
	})
	void colorIsJudgedWithTheRoundedUpAmounts(int spent, long rounded, BudgetColor color, boolean within) {
		Owner a = createOwner("toptest05");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);   // 週の予算 2,400
		if (spent > 0) {
			spend(a, food, SUNDAY, spent);
		}

		TopView view = top(a, SUNDAY);

		WeekRow row = view.rows().get(0);
		assertThat(row.actual()).isEqualTo(rounded);
		assertThat(row.color()).isEqualTo(color);
		assertThat(row.withinBudget()).isEqualTo(within);
		assertThat(view.total().color()).isEqualTo(color);     // 科目が1つなので、合計行も同じ
		assertThat(view.total().withinBudget()).isEqualTo(within);
	}

	@Test
	void budgetZeroMakesNoColorWithoutSpendingAndRedWithSpending() {
		Owner a = createOwner("toptest06");
		Category zero = category(a, "ゼロ");
		budget(a, zero, AUTUMN_2026, 0);

		assertThat(top(a, SUNDAY).rows().get(0).color()).isEqualTo(BudgetColor.NONE);

		spend(a, zero, SUNDAY, 1);
		TopView view = top(a, SUNDAY);
		assertThat(view.rows().get(0).budget()).isZero();
		assertThat(view.rows().get(0).color()).isEqualTo(BudgetColor.RED);
	}

	// ---- 予算が未設定 ----

	@Test
	void unsetBudgetShowsUnsetAndIsLeftOutOfTheBudgetTotal() {
		Owner a = createOwner("toptest07");
		Category food = category(a, "食費");
		Category unset = category(a, "未設定の科目");
		budget(a, food, AUTUMN_2026, 30_000);
		spend(a, unset, SUNDAY, 150);

		TopView view = top(a, SUNDAY);

		WeekRow unsetRow = view.rows().get(1);
		assertThat(unsetRow.budget()).isNull();
		assertThat(unsetRow.difference()).isNull();
		assertThat(unsetRow.color()).isEqualTo(BudgetColor.NONE);
		assertThat(unsetRow.barPermille()).isZero();
		assertThat(view.total().budget()).isEqualTo(2_400L);   // 設定済みの科目だけ
		assertThat(view.total().actual()).isEqualTo(200L);     // 実績は全科目
	}

	@Test
	void whenEveryBudgetIsUnsetTheTotalBudgetIsUnsetToo() {
		Owner a = createOwner("toptest08");
		Category food = category(a, "食費");
		spend(a, food, SUNDAY, 150);

		TopView view = top(a, SUNDAY);

		assertThat(view.total().budget()).isNull();
		assertThat(view.total().color()).isEqualTo(BudgetColor.NONE);
		assertThat(view.seasonBudget()).isNull();
		assertThat(view.seasonRemaining()).isNull();
		assertThat(view.seasonActual()).isEqualTo(150L);   // 秋の取引（切り上げない）
	}

	// ---- 季節のメーター ----

	@Test
	void seasonMeterMatchesTheTotalRowOfTheComparison() {
		Owner a = createOwner("toptest09");
		Category[] cs = threeCategories(a);
		spend(a, cs[0], LocalDate.of(2026, 9, 3), 12_345);
		spend(a, cs[1], LocalDate.of(2026, 11, 8), 6_789);

		TopView view = top(a, SUNDAY);
		ComparisonResult comparison = comparisonService.compare(a.id(), AUTUMN_2026);

		assertThat(view.season()).isEqualTo(AUTUMN_2026);
		assertThat(view.seasonBudget()).isEqualTo(comparison.total().budget()).isEqualTo(90_000L);
		assertThat(view.seasonActual()).isEqualTo(comparison.total().actual()).isEqualTo(19_134L);   // 切り上げない
		assertThat(view.seasonRemaining()).isEqualTo(comparison.total().difference()).isEqualTo(70_866L);
		assertThat(view.seasonBarPermille()).isEqualTo(212);   // 19,134 ÷ 90,000
		assertThat(view.seasonTodayPermille()).isEqualTo(758); // 91 日のうち 69 日目
		assertThat(view.weekTodayPermille()).isEqualTo(142);   // 日曜は 1/7
	}

	@Test
	void overBudgetSeasonKeepsTheBarAtFullAndTheRemainingNegative() {
		Owner a = createOwner("toptest10");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 1_000);
		spend(a, food, LocalDate.of(2026, 10, 1), 1_500);

		TopView view = top(a, SUNDAY);

		assertThat(view.seasonBarPermille()).isEqualTo(1000);
		assertThat(view.seasonRemaining()).isEqualTo(-500L);
	}

	@Test
	void todayLineOfTheWeekMovesFromSundayToSaturday() {
		Owner a = createOwner("toptest11");
		category(a, "食費");

		assertThat(top(a, LocalDate.of(2026, 11, 8)).weekTodayPermille()).isEqualTo(142);
		assertThat(top(a, LocalDate.of(2026, 11, 14)).weekTodayPermille()).isEqualTo(1000);
	}

	// ---- 0 件 ----

	@Test
	void noCategoriesGivesAnEmptyView() {
		Owner a = createOwner("toptest12");

		TopView view = top(a, SUNDAY);

		assertThat(view.hasCategories()).isFalse();
		assertThat(view.rows()).isEmpty();
		assertThat(view.slices()).isEmpty();
	}

	@Test
	void noTransactionsThisWeekGivesNoPieButAllRows() {
		Owner a = createOwner("toptest13");
		Category[] cs = threeCategories(a);
		spend(a, cs[0], LocalDate.of(2026, 11, 1), 5_000);   // 先週

		TopView view = top(a, SUNDAY);

		assertThat(view.slices()).isEmpty();
		assertThat(view.pieTotal()).isZero();
		assertThat(view.rows()).hasSize(3);                   // 使っていない科目も、メーターには出す
		assertThat(view.rows()).extracting(WeekRow::actual).containsOnly(0L);
	}

	// ---- 円グラフ ----

	@Test
	void pieShowsOnlyCategoriesUsedThisWeekWithOtherForSevenOrMore() {
		Owner a = createOwner("toptest14");
		for (int i = 1; i <= 8; i++) {
			Category c = category(a, "科目" + i);
			budget(a, c, AUTUMN_2026, 30_000);
			if (i <= 7) {
				spend(a, c, SUNDAY, i * 100);
			}
		}

		TopView view = top(a, SUNDAY);

		assertThat(view.slices()).hasSize(6);
		assertThat(view.slices()).extracting(s -> s.name())
				.containsExactly("科目7", "科目6", "科目5", "科目4", "科目3", PieSlices.OTHER_NAME);
		assertThat(view.slices().get(5).mergedCount()).isEqualTo(2);   // 科目2 と 科目1
		assertThat(view.pieTotal()).isEqualTo(2_800L);
		assertThat(view.pieStops()).startsWith("var(--hb-chart-1) 0.0%").endsWith("100.0%");
	}

	// ---- 他の人のデータ ----

	@Test
	void otherUsersDataIsNeverMixedIn() {
		Owner a = createOwner("toptest15");
		Owner b = createOwner("toptest16");
		Category aFood = category(a, "食費");
		Category bFood = category(b, "Bの食費");
		budget(a, aFood, AUTUMN_2026, 30_000);
		budget(b, bFood, AUTUMN_2026, 99_000);
		spend(a, aFood, SUNDAY, 100);
		spend(b, bFood, SUNDAY, 50_000);

		TopView view = top(a, SUNDAY);

		assertThat(view.rows()).extracting(WeekRow::categoryName).containsExactly("食費");
		assertThat(view.total().actual()).isEqualTo(100L);
		assertThat(view.total().budget()).isEqualTo(2_400L);
		assertThat(view.seasonBudget()).isEqualTo(30_000L);
		assertThat(view.seasonActual()).isEqualTo(100L);
		assertThat(view.slices()).extracting(s -> s.name()).containsExactly("食費");
	}

}
