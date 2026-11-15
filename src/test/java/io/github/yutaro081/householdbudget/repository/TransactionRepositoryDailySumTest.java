package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.dto.DailyActual;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import jakarta.persistence.EntityManager;

/**
 * TransactionRepository.sumByCategoryAndDate（期間内の、科目・日付ごとの合計と件数。設計書 4.6、5.11、5.12、issue 028）のテスト。
 * ・科目と日付ごとに 1 行。同じ科目・同じ日の取引は、合計と件数にまとまる
 * ・期間は「開始日以上、終了日の翌日未満」（sumByCategory と同じ。5.8）
 * ・取引のない科目・日は結果に出ない
 * ・自分の取引だけ（他人の取引は入らない。3.9）
 * ・SQL は 1 本
 * 期間は、トップが使う「先週の日曜（2026-11-01）から、今週の次の日曜（2026-11-15）の前日まで」。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TransactionRepositoryDailySumTest {

	private static final LocalDate LAST_SUNDAY = LocalDate.of(2026, 11, 1);
	private static final LocalDate NEXT_SUNDAY = LocalDate.of(2026, 11, 15);

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

	/** 1人分のユーザーと、取引に使う取引先・支払方法 */
	private record Owner(Long id, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id,
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category createCategory(Owner owner, String name) {
		return categoryRepository.save(new Category(owner.id(), name));
	}

	private void save(Owner owner, Category category, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(owner.id(), date, amount, category, null,
				owner.vendor(), owner.paymentMethod()));
	}

	/** 保存して読み直し、先週の日曜〜今週の土曜の結果を返す */
	private List<DailyActual> twoWeeks(Owner owner) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.sumByCategoryAndDate(owner.id(), LAST_SUNDAY, NEXT_SUNDAY);
	}

	@Test
	void oneRowPerCategoryAndDateWithTheTotalAndTheCount() {
		Owner a = createOwner("dailysum1");
		Category food = createCategory(a, "食費");
		Category transport = createCategory(a, "交通費");
		save(a, food, LocalDate.of(2026, 11, 3), 1_000);
		save(a, food, LocalDate.of(2026, 11, 3), 250);       // 同じ科目・同じ日：1 行にまとまる
		save(a, food, LocalDate.of(2026, 11, 10), 400);      // 同じ科目・別の日：別の行
		save(a, transport, LocalDate.of(2026, 11, 10), 700); // 別の科目・同じ日：別の行

		assertThat(twoWeeks(a))
				.extracting(DailyActual::categoryId, DailyActual::date, DailyActual::total, DailyActual::count)
				.containsExactlyInAnyOrder(
						tuple(food.getId(), LocalDate.of(2026, 11, 3), 1_250L, 2L),
						tuple(food.getId(), LocalDate.of(2026, 11, 10), 400L, 1L),
						tuple(transport.getId(), LocalDate.of(2026, 11, 10), 700L, 1L));
	}

	@Test
	void theStartDateIsIncludedAndTheEndDateIsNot() {
		Owner a = createOwner("dailysum2");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 10, 31), 1);        // 開始日の前の日：入らない
		save(a, food, LocalDate.of(2026, 11, 1), 10);        // 開始日（先週の日曜）：入る
		save(a, food, LocalDate.of(2026, 11, 14), 100);      // 終了日の前の日（今週の土曜）：入る
		save(a, food, LocalDate.of(2026, 11, 15), 1_000);    // 終了日（次の日曜）：入らない

		assertThat(twoWeeks(a)).extracting(DailyActual::date, DailyActual::total)
				.containsExactlyInAnyOrder(
						tuple(LocalDate.of(2026, 11, 1), 10L),
						tuple(LocalDate.of(2026, 11, 14), 100L));
	}

	@Test
	void categoriesAndDaysWithoutTransactionsAreNotInTheResult() {
		Owner a = createOwner("dailysum3");
		Category food = createCategory(a, "食費");
		createCategory(a, "雑費");
		save(a, food, LocalDate.of(2026, 11, 9), 500);

		assertThat(twoWeeks(a)).extracting(DailyActual::categoryId, DailyActual::date)
				.containsExactly(tuple(food.getId(), LocalDate.of(2026, 11, 9)));
	}

	@Test
	void noTransactionsGivesAnEmptyResult() {
		Owner a = createOwner("dailysum4");
		createCategory(a, "食費");

		assertThat(twoWeeks(a)).isEmpty();
	}

	/** 他人の取引は、同じ日でも入らない（3.9） */
	@Test
	void otherUsersTransactionsAreNotCounted() {
		Owner a = createOwner("dailysum5");
		Owner b = createOwner("dailysum6");
		Category aFood = createCategory(a, "食費");
		Category bFood = createCategory(b, "食費");
		save(a, aFood, LocalDate.of(2026, 11, 10), 1_500);
		save(b, bFood, LocalDate.of(2026, 11, 10), 70_000);
		save(b, bFood, LocalDate.of(2026, 11, 2), 30_000);

		assertThat(twoWeeks(a)).extracting(DailyActual::categoryId, DailyActual::total, DailyActual::count)
				.containsExactly(tuple(aFood.getId(), 1_500L, 1L));
		assertThat(twoWeeks(b)).extracting(DailyActual::categoryId).containsOnly(bFood.getId());
	}

	@Test
	void numberOfStatementsIsOneRegardlessOfTheNumberOfCategoriesAndDays() {
		Owner a = createOwner("dailysum7");
		for (int i = 0; i < 5; i++) {
			Category category = createCategory(a, "科目" + i);
			save(a, category, LocalDate.of(2026, 11, 2 + i), 100 + i);
			save(a, category, LocalDate.of(2026, 11, 9 + i), 200 + i);
		}
		entityManager.flush();
		entityManager.clear();

		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		List<DailyActual> list = transactionRepository.sumByCategoryAndDate(a.id(), LAST_SUNDAY, NEXT_SUNDAY);

		assertThat(list).hasSize(10);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

}
