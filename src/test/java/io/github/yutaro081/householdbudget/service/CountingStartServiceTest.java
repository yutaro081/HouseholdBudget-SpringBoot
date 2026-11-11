package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.ProposalChoice;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView;
import io.github.yutaro081.householdbudget.dto.TopView;
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
 * 最初の取引の日から数える（要件定義書 4.4・4.5・4.3、設計書 5.13・5.14、issue 020）の、サービスの確かめ。
 * 計算そのものは domain のテストで確かめてあるので、ここでは、自分の最初の取引の日が、
 * トップの着地見込み、予実比較の着地見込み（countingStart）、自動予算のペースに届くことを確かめる。
 * 「今日」は 2026-11-11（秋の 72 日目。D ＝ 91、L ＝ 19）。
 */
@SpringBootTest
@Transactional
class CountingStartServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
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
	private TopService topService;
	@Autowired
	private ComparisonService comparisonService;
	@Autowired
	private AutoBudgetService autoBudgetService;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long id, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private void spend(Owner o, Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(o.id(), date, amount, c, null, o.vendor(), o.paymentMethod()));
	}

	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}

	/** トップ：10/31 に記録を始め、実績 20,000、予算 175,000 → 11/11（L ＝ 19、D′ ＝ 31）で、54,700（31％）。季節の初日から数えると 31,900 */
	@Test
	void theTopLandingCountsFromTheFirstTransaction() {
		Owner a = createOwner("cstart1");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		budgetRepository.save(new Budget(a.id(), food, 2026, Season.AUTUMN, 175_000));
		spend(a, food, LocalDate.of(2026, 10, 31), 20_000);
		flush();

		TopView view = topService.top(a.id(), TODAY);

		// （20,000 × 91 × 50 ＋ 175,000 × 361）÷（91 × 31）＝ 154,175,000 ÷ 2,821 ＝ 54,652.6… → 54,700
		assertThat(view.landingAmount()).isEqualTo(54_700L);
		assertThat(view.landingPercent()).isEqualTo(31);
	}

	/** 最初の取引が季節の前なら、季節の初日から数える（今までの値）。（20,000 × 91 × 110 ＋ 175,000 × 361）÷ 8,281 → 31,900 */
	@Test
	void theTopLandingCountsFromTheSeasonStartWhenTheFirstTransactionIsBeforeTheSeason() {
		Owner a = createOwner("cstart2");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		budgetRepository.save(new Budget(a.id(), food, 2026, Season.AUTUMN, 175_000));
		spend(a, food, LocalDate.of(2026, 8, 20), 1_000);        // 夏。秋の実績には入らない
		spend(a, food, LocalDate.of(2026, 10, 31), 20_000);
		flush();

		TopView view = topService.top(a.id(), TODAY);

		assertThat(view.landingAmount()).isEqualTo(31_900L);
	}

	/** 他人の古い取引は、自分の数え始めの日に影響しない */
	@Test
	void anotherUsersEarlierTransactionDoesNotChangeTheStart() {
		Owner a = createOwner("cstart3");
		Owner b = createOwner("cstart4");
		Category foodA = categoryRepository.save(new Category(a.id(), "食費"));
		Category foodB = categoryRepository.save(new Category(b.id(), "食費"));
		budgetRepository.save(new Budget(a.id(), foodA, 2026, Season.AUTUMN, 175_000));
		spend(a, foodA, LocalDate.of(2026, 10, 31), 20_000);
		spend(b, foodB, LocalDate.of(2026, 9, 1), 500);
		flush();

		assertThat(topService.top(a.id(), TODAY).landingAmount()).isEqualTo(54_700L);
	}

	/** 予実比較（ComparisonService.countingStart）：自分の最初の取引の日と、季節・今日から、数え始めの日を返す */
	@Test
	void theComparisonCountingStartIsTheFirstTransactionDayOrTheSeasonStart() {
		Owner a = createOwner("cstart5");
		Owner none = createOwner("cstart6");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		spend(a, food, LocalDate.of(2026, 10, 31), 20_000);
		flush();

		assertThat(comparisonService.countingStart(a.id(), AUTUMN_2026, TODAY)).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(comparisonService.countingStart(a.id(), WINTER_2026, TODAY)).as("季節の外").isEqualTo(WINTER_2026.startDate());
		assertThat(comparisonService.countingStart(none.id(), AUTUMN_2026, TODAY)).as("取引なし").isEqualTo(AUTUMN_2026.startDate());
	}

	/** 自動予算のペース：10/15 に記録を始め、秋の実績 9,000 円 → 11/11 に、9,000 × 91 ÷ 28 ＝ 29,250（表は 29,300） */
	@Test
	void theAutoBudgetPaceCountsFromTheFirstTransaction() {
		Owner a = createOwner("cstart7");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		budgetRepository.save(new Budget(a.id(), food, 2026, Season.AUTUMN, 30_000));
		spend(a, food, LocalDate.of(2026, 10, 15), 9_000);
		flush();

		AutoBudgetView view = autoBudgetService.propose(a.id(), WINTER_2026, ProposalChoice.OK, TODAY);

		assertThat(view.previousPaceTotal()).isEqualTo(29_300L);
	}
}
