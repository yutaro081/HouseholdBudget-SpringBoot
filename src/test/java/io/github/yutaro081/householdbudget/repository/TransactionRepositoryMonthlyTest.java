package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.dto.MonthlyActual;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import jakarta.persistence.EntityManager;

/**
 * TransactionRepository.sumByCategoryAndMonth（期間内の科目・月ごとの合計。自動予算の過去の実績。
 * 設計書 4.6、5.14、7.4、issue 019）のテスト。
 * ・自分の取引だけを数える（他人の取引は数えない、3.9。守りに関わる所）
 * ・期間は「開始日以上、終了日の翌日未満」
 * ・科目・年・月ごとに 1 行（月は取引の日付の月。冬の 1 月・2 月も、その日付の年と月）
 * ・取引のない科目・月は結果に出ない（季節にまとめるのは Service）
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TransactionRepositoryMonthlyTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;

	private record Owner(User user, Vendor vendor, PaymentMethod paymentMethod) {
		Long id() {
			return user.getId();
		}
	}

	private Owner createOwner(String username) {
		User user = userRepository.save(new User(username, "dummy-hash-for-test"));
		return new Owner(user,
				vendorRepository.save(new Vendor(user.getId(), "取引先")),
				paymentMethodRepository.save(new PaymentMethod(user.getId(), "支払方法")));
	}

	private Category createCategory(Owner owner, String name) {
		return categoryRepository.save(new Category(owner.id(), name));
	}

	private void save(Owner owner, Category category, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(owner.id(), date, amount, category, null,
				owner.vendor(), owner.paymentMethod()));
	}

	/** 保存して読み直し、期間 [start, endExclusive) の科目・月ごとの合計を返す */
	private List<MonthlyActual> sums(Owner owner, LocalDate start, LocalDate endExclusive) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.sumByCategoryAndMonth(owner.id(), start, endExclusive);
	}

	private static final LocalDate AUTUMN_START = LocalDate.of(2026, 9, 1);
	private static final LocalDate AUTUMN_END_EXCLUSIVE = LocalDate.of(2026, 12, 1);

	// ---- 科目 × 月ごとの合計 ----

	@Test
	void sumsPerCategoryAndMonth() {
		Owner a = createOwner("mact1");
		Category food = createCategory(a, "食費");
		Category transport = createCategory(a, "交通費");
		save(a, food, LocalDate.of(2026, 9, 3), 1000);
		save(a, food, LocalDate.of(2026, 9, 28), 500);        // 同じ月・同じ科目は 1 行にまとまる
		save(a, food, LocalDate.of(2026, 10, 1), 2500);
		save(a, transport, LocalDate.of(2026, 10, 15), 400);
		save(a, transport, LocalDate.of(2026, 11, 30), 90);

		List<MonthlyActual> result = sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(result).containsExactlyInAnyOrder(
				new MonthlyActual(food.getId(), 2026, 9, 1500L),
				new MonthlyActual(food.getId(), 2026, 10, 2500L),
				new MonthlyActual(transport.getId(), 2026, 10, 400L),
				new MonthlyActual(transport.getId(), 2026, 11, 90L));
	}

	@Test
	void sameMonthOfDifferentYearsStaysSeparate() {
		Owner a = createOwner("mact2");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2024, 10, 10), 100);
		save(a, food, LocalDate.of(2025, 10, 10), 1000);
		save(a, food, LocalDate.of(2026, 10, 10), 10000);

		List<MonthlyActual> result = sums(a, LocalDate.of(2023, 9, 1), LocalDate.of(2026, 12, 1));

		assertThat(result).containsExactlyInAnyOrder(
				new MonthlyActual(food.getId(), 2024, 10, 100L),
				new MonthlyActual(food.getId(), 2025, 10, 1000L),
				new MonthlyActual(food.getId(), 2026, 10, 10000L));
	}

	@Test
	void decemberAndJanuaryOfTheSameWinterAreSeparateRowsWithTheirOwnYears() {
		Owner a = createOwner("mact3");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2025, 12, 31), 10);
		save(a, food, LocalDate.of(2026, 1, 1), 100);
		save(a, food, LocalDate.of(2026, 2, 28), 1000);

		List<MonthlyActual> result = sums(a, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 3, 1));

		assertThat(result).containsExactlyInAnyOrder(
				new MonthlyActual(food.getId(), 2025, 12, 10L),
				new MonthlyActual(food.getId(), 2026, 1, 100L),
				new MonthlyActual(food.getId(), 2026, 2, 1000L));
	}

	@Test
	void categoriesAndMonthsWithoutTransactionsAreNotInTheResult() {
		Owner a = createOwner("mact4");
		Category food = createCategory(a, "食費");
		Category unused = createCategory(a, "雑費");
		save(a, food, LocalDate.of(2026, 10, 1), 1500);

		List<MonthlyActual> result = sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(result).containsExactly(new MonthlyActual(food.getId(), 2026, 10, 1500L));
		assertThat(result).extracting(MonthlyActual::categoryId).doesNotContain(unused.getId());
	}

	@Test
	void noTransactionsAtAllGivesAnEmptyResult() {
		Owner a = createOwner("mact5");
		createCategory(a, "食費");

		assertThat(sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE)).isEmpty();
	}

	// ---- 他人の取引を数えない（3.9） ----

	@Test
	void otherUsersTransactionsAreNotCounted() {
		Owner a = createOwner("mact6");
		Owner b = createOwner("mact7");
		Category aFood = createCategory(a, "食費");
		Category bFood = createCategory(b, "食費");
		save(a, aFood, LocalDate.of(2026, 10, 29), 1500);
		save(b, bFood, LocalDate.of(2026, 10, 29), 70000);
		save(b, bFood, LocalDate.of(2026, 9, 1), 5);

		List<MonthlyActual> forA = sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE);
		List<MonthlyActual> forB = sums(b, AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(forA).containsExactly(new MonthlyActual(aFood.getId(), 2026, 10, 1500L));
		assertThat(forB).containsExactlyInAnyOrder(
				new MonthlyActual(bFood.getId(), 2026, 10, 70000L),
				new MonthlyActual(bFood.getId(), 2026, 9, 5L));
	}

	@Test
	void aUserWithoutTransactionsGetsNothingEvenIfOthersHaveSome() {
		Owner a = createOwner("mact8");
		Owner b = createOwner("mact9");
		Category aFood = createCategory(a, "食費");
		save(a, aFood, LocalDate.of(2026, 10, 29), 1500);

		assertThat(sums(b, AUTUMN_START, AUTUMN_END_EXCLUSIVE)).isEmpty();
	}

	// ---- 期間の端（開始日を含み、終了日の翌日を含まない） ----

	@Test
	void startDateIsIncludedAndTheDayBeforeIsNot() {
		Owner a = createOwner("mact10");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 8, 31), 1);
		save(a, food, LocalDate.of(2026, 9, 1), 10);

		List<MonthlyActual> result = sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(result).containsExactly(new MonthlyActual(food.getId(), 2026, 9, 10L));
	}

	@Test
	void theDayBeforeTheEndDateIsIncludedAndTheEndDateItselfIsNot() {
		Owner a = createOwner("mact11");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 11, 30), 10);
		save(a, food, LocalDate.of(2026, 12, 1), 1000);

		List<MonthlyActual> result = sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(result).containsExactly(new MonthlyActual(food.getId(), 2026, 11, 10L));
	}

	@Test
	void transactionsOnlyOutsideThePeriodGiveAnEmptyResult() {
		Owner a = createOwner("mact12");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 8, 31), 100);
		save(a, food, LocalDate.of(2026, 12, 1), 100);

		assertThat(sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE)).isEmpty();
	}

	@Test
	void anEmptyPeriodGivesAnEmptyResult() {
		Owner a = createOwner("mact13");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 9, 1), 100);

		// 開始日 ＝ 終了日の翌日（使う季節がないときの、空の期間）
		assertThat(sums(a, AUTUMN_START, AUTUMN_START)).isEmpty();
	}

	@Test
	void theFirstAllowedDateIsFoundWhenThePeriodStartsAtSpring2000() {
		Owner a = createOwner("mact14");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2000, 3, 1), 10);

		List<MonthlyActual> result = sums(a, LocalDate.of(2000, 3, 1), LocalDate.of(2003, 3, 1));

		assertThat(result).containsExactly(new MonthlyActual(food.getId(), 2000, 3, 10L));
	}

	// ---- 合計が INT を超える（3.1） ----

	@Test
	void totalBeyondIntRangeIsSummedAsLong() {
		Owner a = createOwner("mact15");
		Category food = createCategory(a, "食費");
		// 1,000,000円を2,148件で 2,148,000,000円（INT の最大 2,147,483,647 を超える）
		for (int i = 0; i < 2148; i++) {
			save(a, food, LocalDate.of(2026, 10, 1 + (i % 28)), 1_000_000);
		}

		List<MonthlyActual> result = sums(a, AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(result).containsExactly(new MonthlyActual(food.getId(), 2026, 10, 2_148_000_000L));
	}

	// ---- SQL の数 ----

	@Test
	void numberOfStatementsIsOneRegardlessOfTheNumberOfCategoriesAndMonths() {
		Owner a = createOwner("mact16");
		for (int i = 0; i < 5; i++) {
			Category category = createCategory(a, "科目" + i);
			save(a, category, LocalDate.of(2026, 9, 1 + i), 100 + i);
			save(a, category, LocalDate.of(2026, 10, 1 + i), 100 + i);
		}
		entityManager.flush();
		entityManager.clear();

		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		List<MonthlyActual> result = transactionRepository.sumByCategoryAndMonth(a.id(), AUTUMN_START, AUTUMN_END_EXCLUSIVE);

		assertThat(result).hasSize(10);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

}
