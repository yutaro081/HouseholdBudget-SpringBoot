package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.BudgetColor;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ComparisonResult;
import io.github.yutaro081.householdbudget.dto.ComparisonRow;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * ComparisonService のテスト（設計書 5.9、2.4、5.8、3.9、要件定義書 4.4）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストのパスワードのハッシュは、ダミーの値（実在するアカウントのものではない）。
 */
@SpringBootTest
@Transactional
class ComparisonServiceTest {

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
	private ComparisonService service;
	@Autowired
	private EntityManager entityManager;

	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final SeasonPeriod WINTER_2026 = new SeasonPeriod(2026, Season.WINTER);

	/** 1人分のユーザーと、取引に使う取引先・支払方法 */
	private record Owner(Long id, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id,
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category category(Owner owner, String name) {
		return categoryRepository.save(new Category(owner.id(), name));
	}

	private void budget(Owner owner, Category category, SeasonPeriod period, int amount) {
		budgetRepository.save(new Budget(owner.id(), category, period.year(), period.season(), amount));
	}

	private void spend(Owner owner, Category category, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(owner.id(), date, amount, category, null,
				owner.vendor(), owner.paymentMethod()));
	}

	private ComparisonResult compare(Owner owner, SeasonPeriod period) {
		entityManager.flush();
		entityManager.clear();
		return service.compare(owner.id(), period);
	}

	private static void assertRow(ComparisonRow row, String name, Long budget, long actual, Long difference,
			BudgetColor color) {
		assertThat(row.categoryName()).isEqualTo(name);
		assertThat(row.budget()).isEqualTo(budget);
		assertThat(row.actual()).isEqualTo(actual);
		assertThat(row.difference()).isEqualTo(difference);
		assertThat(row.color()).isEqualTo(color);
	}

	// ---- 行：予算・実績・差額・色（5.9） ----

	@Test
	void rowsHaveBudgetActualDifferenceAndColor() {
		Owner a = createOwner("cmptest1");
		Category transport = category(a, "交通費");
		Category food = category(a, "食費");
		Category misc = category(a, "雑費");
		budget(a, transport, AUTUMN_2026, 25_000);
		budget(a, food, AUTUMN_2026, 20_000);
		budget(a, misc, AUTUMN_2026, 30_000);
		spend(a, food, LocalDate.of(2026, 10, 29), 1_500);

		ComparisonResult result = compare(a, AUTUMN_2026);

		// 手順書 4.9 の画面の確認と同じ値
		assertThat(result.rows()).hasSize(3);
		assertRow(result.rows().get(0), "交通費", 25_000L, 0, 25_000L, BudgetColor.BLUE);
		assertRow(result.rows().get(1), "食費", 20_000L, 1_500, 18_500L, BudgetColor.BLUE);
		assertRow(result.rows().get(2), "雑費", 30_000L, 0, 30_000L, BudgetColor.BLUE);
		assertRow(result.total(), null, 75_000L, 1_500, 73_500L, BudgetColor.BLUE);
	}

	@Test
	void colorsRedBlueAndNone() {
		Owner a = createOwner("cmptest2");
		Category over = category(a, "超過");
		Category exact = category(a, "ちょうど");
		Category third = category(a, "三分の一");
		Category justOver = category(a, "三分の一超");
		Category zeroZero = category(a, "ゼロ");
		Category zeroSpent = category(a, "ゼロ超過");
		budget(a, over, AUTUMN_2026, 30_000);
		budget(a, exact, AUTUMN_2026, 30_000);
		budget(a, third, AUTUMN_2026, 30_000);
		budget(a, justOver, AUTUMN_2026, 30_000);
		budget(a, zeroZero, AUTUMN_2026, 0);
		budget(a, zeroSpent, AUTUMN_2026, 0);
		spend(a, over, LocalDate.of(2026, 10, 1), 30_001);
		spend(a, exact, LocalDate.of(2026, 10, 1), 30_000);
		spend(a, third, LocalDate.of(2026, 10, 1), 10_000);
		spend(a, justOver, LocalDate.of(2026, 10, 1), 10_001);
		spend(a, zeroSpent, LocalDate.of(2026, 10, 1), 1);

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.rows().get(0), "超過", 30_000L, 30_001, -1L, BudgetColor.RED);
		assertRow(result.rows().get(1), "ちょうど", 30_000L, 30_000, 0L, BudgetColor.NONE);
		assertRow(result.rows().get(2), "三分の一", 30_000L, 10_000, 20_000L, BudgetColor.BLUE);
		assertRow(result.rows().get(3), "三分の一超", 30_000L, 10_001, 19_999L, BudgetColor.NONE);
		assertRow(result.rows().get(4), "ゼロ", 0L, 0, 0L, BudgetColor.NONE);
		assertRow(result.rows().get(5), "ゼロ超過", 0L, 1, -1L, BudgetColor.RED);
	}

	@Test
	void differenceIsNegativeWhenOverTheBudget() {
		Owner a = createOwner("cmptest3");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);
		spend(a, food, LocalDate.of(2026, 10, 1), 35_000);

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.rows().get(0), "食費", 30_000L, 35_000, -5_000L, BudgetColor.RED);
	}

	// ---- 予算が未設定 ----

	@Test
	void categoryWithoutBudgetRowIsUnsetWithNoDifferenceAndNoColorButShowsTheActual() {
		Owner a = createOwner("cmptest4");
		Category food = category(a, "食費");
		spend(a, food, LocalDate.of(2026, 10, 1), 5_000);

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.rows().get(0), "食費", null, 5_000, null, BudgetColor.NONE);
	}

	@Test
	void unsetWithNoSpendingIsAlsoUnsetAndNoColor() {
		Owner a = createOwner("cmptest5");
		category(a, "食費");

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.rows().get(0), "食費", null, 0, null, BudgetColor.NONE);
	}

	@Test
	void theAmountOfAnEarlierSeasonIsNotCarriedOver() {
		Owner a = createOwner("cmptest6");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);

		// 予算設定画面なら、冬の初期値は秋の額になる。予実比較では、冬の行がないので「未設定」
		ComparisonResult winter = compare(a, WINTER_2026);
		ComparisonResult autumn = compare(a, AUTUMN_2026);

		assertRow(winter.rows().get(0), "食費", null, 0, null, BudgetColor.NONE);
		assertRow(autumn.rows().get(0), "食費", 30_000L, 0, 30_000L, BudgetColor.BLUE);
	}

	@Test
	void budgetOfAnotherSeasonOrYearIsNotUsed() {
		Owner a = createOwner("cmptest7");
		Category food = category(a, "食費");
		budget(a, food, new SeasonPeriod(2025, Season.AUTUMN), 11_111);
		budget(a, food, new SeasonPeriod(2026, Season.SUMMER), 22_222);
		budget(a, food, AUTUMN_2026, 33_333);

		assertRow(compare(a, AUTUMN_2026).rows().get(0), "食費", 33_333L, 0, 33_333L, BudgetColor.BLUE);
	}

	// ---- 取引がない科目は0円・並び ----

	@Test
	void categoryWithoutTransactionsHasZeroActualAndRowsAreInRegistrationOrder() {
		Owner a = createOwner("cmptest8");
		Category third = category(a, "さん");
		Category first = category(a, "いち");
		Category second = category(a, "に");
		budget(a, first, AUTUMN_2026, 100);
		spend(a, second, LocalDate.of(2026, 10, 1), 50);

		ComparisonResult result = compare(a, AUTUMN_2026);

		// 登録順（id の順）。名前の順ではない
		assertThat(result.rows()).extracting(ComparisonRow::categoryName).containsExactly("さん", "いち", "に");
		assertThat(result.rows()).extracting(ComparisonRow::actual).containsExactly(0L, 0L, 50L);
		assertThat(third.getId()).isLessThan(first.getId());
	}

	// ---- 合計行（Q22 の C、5.9） ----

	@Test
	void totalBudgetIsOfSetCategoriesOnlyAndTotalActualIsOfAllCategories() {
		Owner a = createOwner("cmptest9");
		Category transport = category(a, "交通費");
		Category food = category(a, "食費");
		budget(a, transport, AUTUMN_2026, 30_000);
		spend(a, transport, LocalDate.of(2026, 10, 1), 10_000);
		spend(a, food, LocalDate.of(2026, 10, 1), 5_000);

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.rows().get(0), "交通費", 30_000L, 10_000, 20_000L, BudgetColor.BLUE);
		assertRow(result.rows().get(1), "食費", null, 5_000, null, BudgetColor.NONE);
		// 合計の予算30,000円、合計の実績15,000円、差額15,000円。15,000×3 ＞ 30,000 なので色なし
		assertRow(result.total(), null, 30_000L, 15_000, 15_000L, BudgetColor.NONE);
	}

	@Test
	void totalIsUnsetWhenAllCategoriesAreUnsetButTheActualIsShown() {
		Owner a = createOwner("cmptest10");
		Category transport = category(a, "交通費");
		Category food = category(a, "食費");
		spend(a, transport, LocalDate.of(2026, 10, 1), 1_000);
		spend(a, food, LocalDate.of(2026, 10, 1), 5_000);

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.total(), null, null, 6_000, null, BudgetColor.NONE);
	}

	@Test
	void totalIsRedWhenTheTotalActualExceedsTheTotalBudget() {
		Owner a = createOwner("cmptest11");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);
		spend(a, food, LocalDate.of(2026, 10, 1), 30_001);

		assertRow(compare(a, AUTUMN_2026).total(), null, 30_000L, 30_001, -1L, BudgetColor.RED);
	}

	@Test
	void totalWithOnlyZeroBudgetsIsSetToZeroAndRedWhenThereIsSpending() {
		Owner a = createOwner("cmptest12");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 0);
		spend(a, food, LocalDate.of(2026, 10, 1), 1);

		// 予算0円は設定済み。合計の予算は0円で、実績があれば赤
		assertRow(compare(a, AUTUMN_2026).total(), null, 0L, 1, -1L, BudgetColor.RED);
	}

	@Test
	void totalWithOnlyZeroBudgetsAndNoSpendingHasNoColor() {
		Owner a = createOwner("cmptest13");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 0);

		assertRow(compare(a, AUTUMN_2026).total(), null, 0L, 0, 0L, BudgetColor.NONE);
	}

	@Test
	void totalBeyondIntRangeIsCalculatedWithLong() {
		Owner a = createOwner("cmptest14");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 3_000_000);
		// 1,000,000円を2,148件で 2,148,000,000円（INT の最大を超える）。予算3,000,000円を大きく超える
		for (int i = 0; i < 2148; i++) {
			spend(a, food, LocalDate.of(2026, 10, 1 + (i % 28)), 1_000_000);
		}

		ComparisonResult result = compare(a, AUTUMN_2026);

		assertRow(result.rows().get(0), "食費", 3_000_000L, 2_148_000_000L, 3_000_000L - 2_148_000_000L, BudgetColor.RED);
		assertRow(result.total(), null, 3_000_000L, 2_148_000_000L, 3_000_000L - 2_148_000_000L, BudgetColor.RED);
	}

	// ---- 科目が0件（Q7-7 の A） ----

	@Test
	void noCategoriesGivesNoRows() {
		Owner a = createOwner("cmptest15");

		ComparisonResult result = compare(a, AUTUMN_2026);

		// 画面は、行が0件のとき、表と合計行を出さずに案内を出す
		assertThat(result.rows()).isEmpty();
		assertRow(result.total(), null, null, 0, null, BudgetColor.NONE);
		assertThat(result.period()).isEqualTo(AUTUMN_2026);
	}

	// ---- 季節の境目（5.8）：取引が正しい季節に入る ----

	@ParameterizedTest(name = "{0} の取引は {1}年 {2} に入る")
	@CsvSource({
			"2027-02-28, 2026, WINTER",
			"2027-01-15, 2026, WINTER",
			"2026-12-01, 2026, WINTER",
			"2026-11-30, 2026, AUTUMN",
			"2027-03-01, 2027, SPRING",
			"2028-02-29, 2027, WINTER",
			"2028-03-01, 2028, SPRING"
	})
	void aTransactionIsCountedInTheRightSeasonOnly(LocalDate date, int year, Season season) {
		Owner a = createOwner("cmptest16");
		Category food = category(a, "食費");
		spend(a, food, date, 1_234);
		SeasonPeriod expected = new SeasonPeriod(year, season);

		assertThat(compare(a, expected).rows().get(0).actual()).isEqualTo(1_234L);
		// 同じ日の取引は、前後の季節には入らない
		expected.previous().ifPresent(p -> assertThat(compare(a, p).rows().get(0).actual()).isZero());
		expected.next().ifPresent(n -> assertThat(compare(a, n).rows().get(0).actual()).isZero());
	}

	@Test
	void transactionsOfJanuaryAndFebruary2000AreInNoDisplayedSeason() {
		Owner a = createOwner("cmptest17");
		Category food = category(a, "食費");
		spend(a, food, LocalDate.of(2000, 1, 1), 10);
		spend(a, food, LocalDate.of(2000, 2, 29), 100);

		assertThat(compare(a, new SeasonPeriod(2000, Season.SPRING)).rows().get(0).actual()).isZero();
		assertThat(compare(a, new SeasonPeriod(2000, Season.WINTER)).rows().get(0).actual()).isZero();
	}

	// ---- 自分の分だけ（3.9） ----

	@Test
	void otherUsersCategoriesBudgetsAndTransactionsAreNotIncluded() {
		Owner a = createOwner("cmptest18");
		Owner b = createOwner("cmptest19");
		Category aFood = category(a, "食費");
		Category bFood = category(b, "食費");
		Category bOnly = category(b, "Bだけ");
		budget(a, aFood, AUTUMN_2026, 20_000);
		budget(b, bFood, AUTUMN_2026, 99_000);
		budget(b, bOnly, AUTUMN_2026, 1_000);
		spend(a, aFood, LocalDate.of(2026, 10, 29), 1_500);
		spend(b, bFood, LocalDate.of(2026, 10, 29), 70_000);

		ComparisonResult forA = compare(a, AUTUMN_2026);
		ComparisonResult forB = compare(b, AUTUMN_2026);

		assertThat(forA.rows()).hasSize(1);
		assertRow(forA.rows().get(0), "食費", 20_000L, 1_500, 18_500L, BudgetColor.BLUE);
		assertRow(forA.total(), null, 20_000L, 1_500, 18_500L, BudgetColor.BLUE);
		assertThat(forB.rows()).hasSize(2);
		assertRow(forB.rows().get(0), "食費", 99_000L, 70_000, 29_000L, BudgetColor.NONE);
		assertRow(forB.total(), null, 100_000L, 70_000, 30_000L, BudgetColor.NONE);
	}

	// ---- 前・次の季節（2.4） ----

	@Test
	void previousAndNextSeasons() {
		Owner a = createOwner("cmptest20");

		ComparisonResult spring = compare(a, new SeasonPeriod(2026, Season.SPRING));
		assertThat(spring.previous()).isEqualTo(new SeasonPeriod(2025, Season.WINTER));
		assertThat(spring.next()).isEqualTo(new SeasonPeriod(2026, Season.SUMMER));

		ComparisonResult winter = compare(a, WINTER_2026);
		assertThat(winter.previous()).isEqualTo(AUTUMN_2026);
		assertThat(winter.next()).isEqualTo(new SeasonPeriod(2027, Season.SPRING));
	}

	@Test
	void edgesOfTheRangeHaveNoPreviousOrNext() {
		Owner a = createOwner("cmptest21");

		ComparisonResult first = compare(a, new SeasonPeriod(2000, Season.SPRING));
		assertThat(first.previous()).isNull();
		assertThat(first.next()).isEqualTo(new SeasonPeriod(2000, Season.SUMMER));

		ComparisonResult last = compare(a, new SeasonPeriod(2100, Season.WINTER));
		assertThat(last.previous()).isEqualTo(new SeasonPeriod(2100, Season.AUTUMN));
		assertThat(last.next()).isNull();

		assertThat(compare(a, new SeasonPeriod(2000, Season.WINTER)).previous()).isNotNull();
		assertThat(compare(a, new SeasonPeriod(2100, Season.SPRING)).next()).isNotNull();
	}

	@Test
	void aFutureSeasonCanBeShownWithZeroActual() {
		Owner a = createOwner("cmptest22");
		Category food = category(a, "食費");
		budget(a, food, new SeasonPeriod(2100, Season.SUMMER), 5_000);

		ComparisonResult result = compare(a, new SeasonPeriod(2100, Season.SUMMER));

		assertRow(result.rows().get(0), "食費", 5_000L, 0, 5_000L, BudgetColor.BLUE);
	}

	// ---- 範囲外の季節 ----

	@ParameterizedTest(name = "{0}年 {1} は範囲外で NotFoundException")
	@CsvSource({
			"1999, WINTER",
			"2101, SPRING",
			"1999, SPRING",
			"2101, WINTER",
			"0, SPRING"
	})
	void outOfRangeSeasonIsNotFound(int year, Season season) {
		Owner a = createOwner("cmptest23");

		assertThatThrownBy(() -> service.compare(a.id(), new SeasonPeriod(year, season)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void nullPeriodIsNotFound() {
		Owner a = createOwner("cmptest24");

		assertThatThrownBy(() -> service.compare(a.id(), null)).isInstanceOf(NotFoundException.class);
	}

	// ---- 何も書かない ----

	@Test
	void comparingDoesNotChangeTheNumberOfBudgetsOrTransactions() {
		Owner a = createOwner("cmptest25");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 20_000);
		spend(a, food, LocalDate.of(2026, 10, 29), 1_500);
		entityManager.flush();
		entityManager.clear();
		long budgets = budgetRepository.count();
		long transactions = transactionRepository.count();

		compare(a, AUTUMN_2026);
		compare(a, WINTER_2026);
		compare(a, new SeasonPeriod(2027, Season.SPRING));

		assertThat(budgetRepository.count()).isEqualTo(budgets);
		assertThat(transactionRepository.count()).isEqualTo(transactions);
	}

}
