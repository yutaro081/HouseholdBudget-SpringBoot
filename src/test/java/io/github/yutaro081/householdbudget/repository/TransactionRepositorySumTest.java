package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.CategoryActual;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import jakarta.persistence.EntityManager;

/**
 * TransactionRepository.sumByCategory（期間内の科目ごとの合計。設計書 5.8、5.9、Q7-3）のテスト。
 * ・自分の取引だけを数える（他人の取引は数えない、3.9）
 * ・期間は「開始日以上、終了日の翌日未満」。冬は年をまたぐ。うるう年の2月29日は前の年の冬
 * ・取引がない科目は結果に出ない（0円にするのは Service）
 * ・合計が INT を超えても Long で受けられる
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 開発用の DB に残してある取引（test1）は、テスト用に作ったユーザーの user_id で絞るので、数えない。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TransactionRepositorySumTest {

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

	/** 保存して読み直し、期間 period の科目ごとの合計を、科目のID → 合計 の形で返す */
	private Map<Long, Long> sums(Owner owner, SeasonPeriod period) {
		entityManager.flush();
		entityManager.clear();
		List<CategoryActual> list = transactionRepository.sumByCategory(owner.id(),
				period.startDate(), period.endDateExclusive());
		Map<Long, Long> map = list.stream().collect(Collectors.toMap(CategoryActual::categoryId, CategoryActual::total));
		assertThat(map).as("同じ科目が2行返らない").hasSize(list.size());
		return map;
	}

	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final SeasonPeriod WINTER_2026 = new SeasonPeriod(2026, Season.WINTER);

	// ---- 科目ごとの合計 ----

	@Test
	void sumsPerCategory() {
		Owner a = createOwner("sumtest1");
		Category food = createCategory(a, "食費");
		Category transport = createCategory(a, "交通費");
		save(a, food, LocalDate.of(2026, 10, 1), 1500);
		save(a, food, LocalDate.of(2026, 10, 29), 2500);
		save(a, food, LocalDate.of(2026, 11, 30), 100);
		save(a, transport, LocalDate.of(2026, 9, 15), 400);

		Map<Long, Long> sums = sums(a, AUTUMN_2026);

		assertThat(sums).containsOnly(
				Map.entry(food.getId(), 4100L),
				Map.entry(transport.getId(), 400L));
	}

	@Test
	void categoryWithoutTransactionsIsNotInTheResult() {
		Owner a = createOwner("sumtest2");
		Category food = createCategory(a, "食費");
		Category unused = createCategory(a, "雑費");
		save(a, food, LocalDate.of(2026, 10, 1), 1500);

		Map<Long, Long> sums = sums(a, AUTUMN_2026);

		assertThat(sums).containsOnlyKeys(food.getId());
		assertThat(sums).doesNotContainKey(unused.getId());
	}

	@Test
	void noTransactionsAtAllGivesAnEmptyResult() {
		Owner a = createOwner("sumtest3");
		createCategory(a, "食費");

		assertThat(sums(a, AUTUMN_2026)).isEmpty();
	}

	@Test
	void transactionsOnlyOutsideThePeriodGiveAnEmptyResult() {
		Owner a = createOwner("sumtest4");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 8, 31), 100);
		save(a, food, LocalDate.of(2026, 12, 1), 100);

		assertThat(sums(a, AUTUMN_2026)).isEmpty();
	}

	// ---- 他人の取引を数えない（3.9） ----

	@Test
	void otherUsersTransactionsAreNotCounted() {
		Owner a = createOwner("sumtest5");
		Owner b = createOwner("sumtest6");
		Category aFood = createCategory(a, "食費");
		Category bFood = createCategory(b, "食費");
		save(a, aFood, LocalDate.of(2026, 10, 29), 1500);
		save(b, bFood, LocalDate.of(2026, 10, 29), 70000);

		Map<Long, Long> forA = sums(a, AUTUMN_2026);
		Map<Long, Long> forB = sums(b, AUTUMN_2026);

		assertThat(forA).containsOnly(Map.entry(aFood.getId(), 1500L));
		assertThat(forB).containsOnly(Map.entry(bFood.getId(), 70000L));
	}

	@Test
	void aUserWithoutTransactionsGetsNothingEvenIfOthersHaveSome() {
		Owner a = createOwner("sumtest7");
		Owner b = createOwner("sumtest8");
		Category aFood = createCategory(a, "食費");
		save(a, aFood, LocalDate.of(2026, 10, 29), 1500);
		entityManager.flush();
		entityManager.clear();

		List<CategoryActual> list = transactionRepository.sumByCategory(b.id(),
				AUTUMN_2026.startDate(), AUTUMN_2026.endDateExclusive());

		assertThat(list).isEmpty();
	}

	// ---- 期間の境目（開始日以上、終了日の翌日未満。5.8） ----

	@Test
	void startDateIsIncludedAndTheDayBeforeIsNot() {
		Owner a = createOwner("sumtest9");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 8, 31), 1);
		save(a, food, LocalDate.of(2026, 9, 1), 10);

		assertThat(sums(a, AUTUMN_2026)).containsOnly(Map.entry(food.getId(), 10L));
	}

	@Test
	void lastDayIsIncludedAndTheDayAfterTheEndIsNot() {
		Owner a = createOwner("sumtest10");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 11, 30), 10);
		save(a, food, LocalDate.of(2026, 12, 1), 1000);

		assertThat(sums(a, AUTUMN_2026)).containsOnly(Map.entry(food.getId(), 10L));
	}

	// ---- 冬は年をまたぐ（5.8） ----

	@Test
	void winterCrossesTheYearBoundary() {
		Owner a = createOwner("sumtest11");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2026, 11, 30), 1);        // 秋：冬に入らない
		save(a, food, LocalDate.of(2026, 12, 1), 10);        // 冬の初日
		save(a, food, LocalDate.of(2026, 12, 31), 100);      // 12月（年内）
		save(a, food, LocalDate.of(2027, 1, 1), 1000);       // 1月は前の年の冬
		save(a, food, LocalDate.of(2027, 2, 28), 10000);     // 冬の最後の日（2027年はうるう年ではない）
		save(a, food, LocalDate.of(2027, 3, 1), 100000);     // 春：冬に入らない

		assertThat(sums(a, WINTER_2026)).containsOnly(Map.entry(food.getId(), 11110L));
	}

	@Test
	void springOfTheNextYearStartsOnMarchFirst() {
		Owner a = createOwner("sumtest12");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2027, 2, 28), 1);
		save(a, food, LocalDate.of(2027, 3, 1), 10);

		assertThat(sums(a, new SeasonPeriod(2027, Season.SPRING))).containsOnly(Map.entry(food.getId(), 10L));
	}

	// ---- うるう年の2月29日は、前の年の冬 ----

	@Test
	void february29BelongsToTheWinterOfThePreviousYear() {
		Owner a = createOwner("sumtest13");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2028, 2, 28), 10);
		save(a, food, LocalDate.of(2028, 2, 29), 100);       // うるう年の2月29日
		save(a, food, LocalDate.of(2028, 3, 1), 1000);       // 春

		assertThat(sums(a, new SeasonPeriod(2027, Season.WINTER))).containsOnly(Map.entry(food.getId(), 110L));
		assertThat(sums(a, new SeasonPeriod(2028, Season.SPRING))).containsOnly(Map.entry(food.getId(), 1000L));
		assertThat(sums(a, new SeasonPeriod(2028, Season.WINTER))).isEmpty();
	}

	// ---- 取引の日付の範囲の端（下限は 2000-03-01 ＝ 2000年春の初日。R15、issue 003） ----

	/** 入力できる最初の日（2000-03-01）の取引は、表示できる最初の季節（2000年春）に入る */
	@Test
	void theFirstAllowedDateIsInTheFirstDisplayedSeason() {
		Owner a = createOwner("sumtest14");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2000, 3, 1), 10);

		assertThat(sums(a, new SeasonPeriod(2000, Season.SPRING))).containsOnly(Map.entry(food.getId(), 10L));
		assertThat(sums(a, new SeasonPeriod(2000, Season.SUMMER))).isEmpty();
		assertThat(sums(a, new SeasonPeriod(2000, Season.WINTER))).isEmpty();
	}

	/** 入力できる最後の日（2100-12-31）の取引は、表示できる最後の季節（2100年冬）に入る */
	@Test
	void theLastAllowedDateIsInTheLastDisplayedSeason() {
		Owner a = createOwner("sumtest17");
		Category food = createCategory(a, "食費");
		save(a, food, LocalDate.of(2100, 12, 31), 10);

		assertThat(sums(a, new SeasonPeriod(2100, Season.WINTER))).containsOnly(Map.entry(food.getId(), 10L));
		assertThat(sums(a, new SeasonPeriod(2100, Season.AUTUMN))).isEmpty();
	}

	// ---- 合計が INT を超える（3.1） ----

	@Test
	void totalBeyondIntRangeIsSummedAsLong() {
		Owner a = createOwner("sumtest15");
		Category food = createCategory(a, "食費");
		// 1,000,000円を2,148件で 2,148,000,000円（INT の最大 2,147,483,647 を超える）
		for (int i = 0; i < 2148; i++) {
			save(a, food, LocalDate.of(2026, 10, 1 + (i % 28)), 1_000_000);
		}

		Map<Long, Long> sums = sums(a, AUTUMN_2026);

		assertThat(sums.get(food.getId())).isEqualTo(2_148_000_000L);
		assertThat(sums.get(food.getId())).isGreaterThan((long) Integer.MAX_VALUE);
	}

	// ---- SQL の数 ----

	@Test
	void numberOfStatementsIsOneRegardlessOfTheNumberOfCategories() {
		Owner a = createOwner("sumtest16");
		for (int i = 0; i < 5; i++) {
			save(a, createCategory(a, "科目" + i), LocalDate.of(2026, 10, 1 + i), 100 + i);
		}
		entityManager.flush();
		entityManager.clear();

		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		List<CategoryActual> list = transactionRepository.sumByCategory(a.id(),
				AUTUMN_2026.startDate(), AUTUMN_2026.endDateExclusive());

		assertThat(list).hasSize(5);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

}
