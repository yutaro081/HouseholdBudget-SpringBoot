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

	/**
	 * 「記録は、ずっと昔に始めた」状態にする（issue 020）。どの計算の範囲（過去 3 年、12 季節、直前の季節）にも入らない昔の日付（2001 年 春。DB の下限は 2000-03-01）に、1 円の取引を足す。
	 * 最初の取引の日が季節の途中だと、数え始めの日がその日になる。このテストは、季節の初日から数える前提で書いてあるので、
	 * 最初の取引の日を、季節より前にそろえる。ほかの数字（季節の実績、記録のある年など）は変わらない。
	 */
	private void recordingStartedLongAgo(Owner o, Category c) {
		spend(o, c, LocalDate.of(2001, 3, 1), 1);
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
		assertThat(unsetRow.meter().normalPermille()).isZero();
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
		// issue 020：予算（100％）は棒の 70％（700）の位置。縮尺は MeterScale
		assertThat(view.seasonMeter().normalPermille()).isEqualTo(148);   // 19,134 ÷ 90,000 × 0.7（212 × 0.7）
		assertThat(view.seasonMeter().todayPermille()).isEqualTo(530);    // 91 日のうち 69 日目（758）× 0.7
		assertThat(view.total().meter().todayPermille()).isEqualTo(99);   // 日曜は 1/7（142）× 0.7
	}

	@Test
	void overBudgetSeasonFillsTheBarWithTheRedPartAndTheRemainingIsNegative() {
		Owner a = createOwner("toptest10");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 1_000);
		spend(a, food, LocalDate.of(2026, 10, 1), 1_500);

		TopView view = top(a, SUNDAY);

		assertThat(view.seasonMeter().normalPermille()).as("予算（100％）までは通常色").isEqualTo(700);
		assertThat(view.seasonMeter().overPermille()).as("150％ → 1050 → 右端で止まる。赤は 300").isEqualTo(300);
		assertThat(view.seasonMeter().overflowPercent()).isEqualTo(50);
		assertThat(view.seasonRemaining()).isEqualTo(-500L);
	}

	@Test
	void todayLineOfTheWeekMovesFromSundayToSaturday() {
		Owner a = createOwner("toptest11");
		category(a, "食費");

		assertThat(top(a, LocalDate.of(2026, 11, 8)).total().meter().todayPermille()).isEqualTo(99);     // 1/7（142）× 0.7
		assertThat(top(a, LocalDate.of(2026, 11, 14)).total().meter().todayPermille()).isEqualTo(700);  // 7/7（1000）× 0.7
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

	/** 円グラフの一覧の判定は、「今週の進み具合」の行と同じ（週の予算 ＝ 予算 ÷ 13 を 100 円単位で切り上げ）。issue 017 の見直し */
	@Test
	void eachPieSliceHasTheSameJudgementAsTheWeekRowOfTheSameCategory() {
		Owner a = createOwner("toptest30");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 40_000);       // 週の予算 3,100
		spend(a, food, SUNDAY, 4_200);              // 超過 → 赤
		Category misc = category(a, "雑費");
		budget(a, misc, AUTUMN_2026, 30_000);       // 週の予算 2,400
		spend(a, misc, SUNDAY, 2_000);              // 1/3 を超えて予算以下 → 💮
		Category transport = category(a, "交通費");
		budget(a, transport, AUTUMN_2026, 25_000);  // 週の予算 2,000
		spend(a, transport, SUNDAY, 600);           // 1/3 以下 → 青
		Category unset = category(a, "予算なし科目");
		spend(a, unset, SUNDAY, 500);               // 予算が未設定 → 色なし

		TopView view = top(a, SUNDAY);

		assertThat(view.slices()).extracting(s -> s.name()).containsExactly("食費", "雑費", "交通費", "予算なし科目");
		assertThat(view.slices().get(0).color()).isEqualTo(BudgetColor.RED);
		assertThat(view.slices().get(1).color()).isEqualTo(BudgetColor.NONE);
		assertThat(view.slices().get(1).withinBudget()).isTrue();
		assertThat(view.slices().get(2).color()).isEqualTo(BudgetColor.BLUE);
		assertThat(view.slices().get(3).color()).isEqualTo(BudgetColor.NONE);
		assertThat(view.slices().get(3).withinBudget()).isFalse();
		for (var slice : view.slices()) {
			var row = view.rows().stream().filter(r -> slice.name().equals(r.categoryName())).findFirst().orElseThrow();
			assertThat(slice.color()).as(slice.name() + " の色").isEqualTo(row.color());
			assertThat(slice.withinBudget()).as(slice.name() + " の 💮").isEqualTo(row.withinBudget());
		}
	}

	@Test
	void theOtherSliceHasNoJudgement() {
		Owner a = createOwner("toptest31");
		for (int i = 1; i <= 8; i++) {
			Category c = category(a, "科目" + i);
			budget(a, c, AUTUMN_2026, 0);            // 予算 0 円で実績あり → 赤
			spend(a, c, SUNDAY, i * 100);
		}

		TopView view = top(a, SUNDAY);

		assertThat(view.slices()).hasSize(6);
		assertThat(view.slices().subList(0, 5)).extracting(s -> s.color()).containsOnly(BudgetColor.RED);
		assertThat(view.slices().get(5).name()).isEqualTo(PieSlices.OTHER_NAME);
		assertThat(view.slices().get(5).color()).isEqualTo(BudgetColor.NONE);
		assertThat(view.slices().get(5).withinBudget()).isFalse();
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

	// ---- issue 017：見出しの行、季節のメーターの %、状態の短い文 ----

	@Test
	void headingRowValuesForAutumn() {
		Owner a = createOwner("toptest90");
		category(a, "食費");

		TopView view = top(a, SUNDAY);

		assertThat(view.today()).isEqualTo(SUNDAY);
		assertThat(view.seasonFirstMonth()).isEqualTo(9);
		assertThat(view.seasonLastMonth()).isEqualTo(11);
		assertThat(view.seasonDaysLeft()).isEqualTo(23);
		assertThat(view.seasonElapsedPercent()).isEqualTo(76);
	}

	@Test
	void headingRowValuesForWinterCrossTheNewYear() {
		Owner a = createOwner("toptest91");
		category(a, "食費");

		TopView december = top(a, LocalDate.of(2026, 12, 15));
		TopView february = top(a, LocalDate.of(2027, 2, 28));

		assertThat(december.seasonFirstMonth()).isEqualTo(12);
		assertThat(december.seasonLastMonth()).isEqualTo(2);
		assertThat(february.season()).isEqualTo(WINTER_2026);
		assertThat(february.seasonDaysLeft()).isEqualTo(1);
		assertThat(february.seasonElapsedPercent()).isEqualTo(100);
	}

	@Test
	void seasonUsedPercentIsRoundedAndKeepsGoingPastOneHundred() {
		Owner a = createOwner("toptest92");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);

		spend(a, food, LocalDate.of(2026, 9, 3), 10_000);
		assertThat(top(a, SUNDAY).seasonUsedPercent()).isEqualTo(33);         // 33.3%

		spend(a, food, LocalDate.of(2026, 9, 4), 26_000);
		assertThat(top(a, SUNDAY).seasonUsedPercent()).isEqualTo(120);        // 36,000 / 30,000
		assertThat(top(a, SUNDAY).seasonMeter().normalPermille()).isEqualTo(700);    // 120％：通常色は予算（70％）まで
		assertThat(top(a, SUNDAY).seasonMeter().overPermille()).isEqualTo(140);      // 赤は 840 − 700
	}

	@Test
	void seasonUsedPercentIsNullWithoutABudgetOrWithAZeroBudget() {
		Owner a = createOwner("toptest93");
		Category food = category(a, "食費");
		spend(a, food, SUNDAY, 500);
		assertThat(top(a, SUNDAY).seasonUsedPercent()).isNull();               // 予算が未設定

		budget(a, food, AUTUMN_2026, 0);
		assertThat(top(a, SUNDAY).seasonUsedPercent()).isNull();               // 予算が 0 円
	}

	@Test
	void statusOfEachRow() {
		Owner a = createOwner("toptest94");
		Category over = category(a, "超過");
		Category low = category(a, "少なめ");
		Category within = category(a, "予算内");
		category(a, "未設定");               // 予算を設定しない科目（4 番目の行。下で「予算なし」を確かめる）
		Category zero = category(a, "ゼロ");
		budget(a, over, AUTUMN_2026, 30_000);
		budget(a, low, AUTUMN_2026, 30_000);
		budget(a, within, AUTUMN_2026, 30_000);
		budget(a, zero, AUTUMN_2026, 0);
		spend(a, over, SUNDAY, 2_401);       // 2,500 > 2,400 → 100 円オーバー
		spend(a, within, SUNDAY, 2_000);

		TopView view = top(a, SUNDAY);

		assertThat(view.rows().get(0).statusKey()).isEqualTo("top.status.over");
		assertThat(view.rows().get(0).statusAmount()).isEqualTo(100L);
		assertThat(view.rows().get(1).statusKey()).isEqualTo("top.status.low");
		assertThat(view.rows().get(2).statusKey()).isEqualTo("top.status.within");
		assertThat(view.rows().get(3).statusKey()).isEqualTo("top.status.none");    // 予算なし
		assertThat(view.rows().get(4).statusKey()).isNull();                         // 予算 0 円で実績 0 円（色なし）
	}

	@Test
	void statusOfTheTotalRow() {
		Owner a = createOwner("toptest95");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);
		spend(a, food, SUNDAY, 3_000);        // 3,000 > 2,400 → 600 円オーバー

		WeekRow total = top(a, SUNDAY).total();

		assertThat(total.statusKey()).isEqualTo("top.status.over");
		assertThat(total.statusAmount()).isEqualTo(600L);
	}

	// ---- issue 018：着地見込み（トップの一文の数字） ----

	/**
	 * 2026-11-08 は秋の 69 日目（D ＝ 91、L ＝ 22。69/91 ＝ 75.8％ → 76％）。予算 100,000 円で、使った額を動かす。
	 * 期待値は、手と分数で計算した値（手順書 2.2）：見込み ＝（実績 × 91 × 113 ＋ 予算 × 22²）÷ 91²、100 円単位で切り上げ。
	 */
	@ParameterizedTest(name = "使った額 {0} 円 → 見込み {1} 円（予算の {2}％）")
	@CsvSource({
			"0,      5900,   6",
			"71000,  94100,  94",
			"72000,  95300,  95",
			"76000,  100300, 100",      // 100,218.33… → 100,300。予算を少し上回るが、％は 100
			"80000,  105200, 105",
			"120000, 154900, 155"       // 予算を超えている
	})
	void landingFollowsTheSeasonTotal(int spent, long amount, int percent) {
		Owner a = createOwner("toptest" + (300 + spent / 1000));
		Category food = category(a, "食費");
		recordingStartedLongAgo(a, food);
		budget(a, food, AUTUMN_2026, 100_000);
		if (spent > 0) {
			spend(a, food, LocalDate.of(2026, 9, 3), spent);
		}

		TopView view = top(a, SUNDAY);

		assertThat(view.seasonUsedPercent()).isEqualTo(spent / 1000);
		assertThat(view.landingAmount()).isEqualTo(amount);
		assertThat(view.landingPercent()).isEqualTo(percent);
	}

	/** 合計の予算は設定済みの科目の合計、合計の実績は全科目の合計（予実比較の合計行と同じ） */
	@Test
	void landingUsesTheSameTotalsAsTheComparisonTotalRow() {
		Owner a = createOwner("toptest40");
		Category food = category(a, "食費");
		recordingStartedLongAgo(a, food);
		Category transport = category(a, "交通費");
		Category unset = category(a, "予算なし科目");
		budget(a, food, AUTUMN_2026, 40_000);
		budget(a, transport, AUTUMN_2026, 60_000);
		spend(a, food, SUNDAY, 30_000);
		spend(a, transport, SUNDAY, 46_000);
		spend(a, unset, SUNDAY, 5_000);                      // 予算がない科目の実績も、合計の実績に入る

		TopView view = top(a, SUNDAY);

		// 予算 100,000、実績 81,000：（81,000 × 91 × 113 ＋ 100,000 × 484）÷ 8,281 ＝ 106,427.12… → 106,500
		assertThat(view.seasonBudget()).isEqualTo(100_000L);
		assertThat(view.seasonActual()).isEqualTo(81_000L);
		assertThat(view.landingAmount()).isEqualTo(106_500L);
		assertThat(view.landingPercent()).isEqualTo(107);
	}

	@Test
	void noLandingWhenTheBudgetIsUnsetOrZero() {
		Owner a = createOwner("toptest96");
		Category food = category(a, "食費");
		spend(a, food, SUNDAY, 500);
		assertThat(top(a, SUNDAY).landingAmount()).isNull();             // 予算が未設定
		assertThat(top(a, SUNDAY).landingPercent()).isNull();

		budget(a, food, AUTUMN_2026, 0);
		assertThat(top(a, SUNDAY).landingAmount()).isNull();             // 予算の合計が 0 円（Q018-1 の A）
		assertThat(top(a, SUNDAY).landingPercent()).isNull();
	}

	@Test
	void landingUsesTheSeasonTheDateBelongsTo() {
		Owner a = createOwner("toptest97");
		Category food = category(a, "食費");
		budget(a, food, WINTER_2026, 90_000);
		spend(a, food, LocalDate.of(2026, 12, 2), 1_000);

		// 冬（90 日）の最後の日（2027-02-28）：残りの日数は 0。見込み ＝ 実績 ＝ 1,000 円。1,000 ÷ 90,000 ＝ 1.1％ → 1
		TopView view = top(a, LocalDate.of(2027, 2, 28));

		assertThat(view.landingAmount()).isEqualTo(1_000L);
		assertThat(view.landingPercent()).isEqualTo(1);
	}

	@Test
	void otherUsersDataIsNeverMixedIntoTheLanding() {
		Owner a = createOwner("toptest41");
		Owner b = createOwner("toptest42");
		Category foodA = category(a, "食費");
		recordingStartedLongAgo(a, foodA);
		budget(a, foodA, AUTUMN_2026, 100_000);
		spend(a, foodA, SUNDAY, 76_000);
		Category foodB = category(b, "食費");
		budget(b, foodB, AUTUMN_2026, 50_000);
		spend(b, foodB, SUNDAY, 900_000);

		TopView view = top(a, SUNDAY);

		assertThat(view.landingAmount()).isEqualTo(100_300L);
		assertThat(view.landingPercent()).isEqualTo(100);
	}

	/** 取引の上限 1,000,000 円の取引が、季節に数百件ある場合でも、集計から見込みまで、そのまま正しく計算される */
	@Test
	void manyLargeTransactionsAreCalculatedExactly() {
		Owner a = createOwner("toptest43");
		Category food = category(a, "食費");
		recordingStartedLongAgo(a, food);
		budget(a, food, AUTUMN_2026, 3_000_000);
		for (int i = 0; i < 500; i++) {
			spend(a, food, LocalDate.of(2026, 10, 1).plusDays(i % 30), 1_000_000);
		}

		TopView view = top(a, SUNDAY);

		// 実績 500,000,000、予算 3,000,000、D ＝ 91、L ＝ 22：（5 億 × 91 × 113 ＋ 300 万 × 484）÷ 8,281 ＝ 621,054,462.02… → 切り上げて 621,054,500 円
		assertThat(view.seasonActual()).isEqualTo(500_000_000L);
		assertThat(view.landingAmount()).isEqualTo(621_054_500L);
		assertThat(view.landingPercent()).isEqualTo(20_702);           // 予算の約 207 倍（％は 100% をこえてもそのまま）
	}

}
