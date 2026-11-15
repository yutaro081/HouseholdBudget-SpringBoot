package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.ProposalChoice;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
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
 * 画面を作るときの SQL の本数が、科目の数で増えないこと（N+1 がないこと。設計書 4.6、issue 027）。
 * 予実比較・トップ・予算設定（「自動予算」を押したとき）が呼ぶ Service を、科目 2 個のときと 8 個のときで呼び、SQL の本数が同じことを確かめる。
 * 取引の登録・編集画面（issue 029）は、科目・取引先・支払方法 2 個ずつと 8 個ずつ（取引の件数も変える）で、選択肢を取る本数が同じことを確かめる。
 * どの科目にも、予算と取引がある（行ごとに検索する作りなら、科目の数だけ本数が増える）。
 * <p>
 * 本数は、Hibernate の統計（発行した SQL の数）で数える。取引一覧は、TransactionRepositoryFetchTest と TransactionListSingleSearchTest が確かめている。
 * 設定（統計を有効にする 1 行だけ）は、TransactionRepositoryFetchTest などと同じにして、Spring の準備（ApplicationContext）を使い回す
 * （準備を増やすと、データベースの接続の数が足りなくなる）。
 * 「今日」は 2026-11-11（秋）。開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ScreenStatementCountTest {

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
	private ComparisonService comparisonService;
	@Autowired
	private TopService topService;
	@Autowired
	private BudgetService budgetService;
	@Autowired
	private AutoBudgetService autoBudgetService;
	@Autowired
	private CategoryService categoryService;
	@Autowired
	private VendorService vendorService;
	@Autowired
	private PaymentMethodService paymentMethodService;
	@Autowired
	private TransactionService transactionService;
	@Autowired
	private EntityManager entityManager;

	/** 画面が Service を呼ぶ所（ユーザーの ID を受け取って、画面のデータを作る） */
	private interface Screen {
		void open(Long userId);
	}

	/**
	 * 科目を count 個持つユーザーを作る。どの科目にも、秋の予算、過去 2 年の冬の取引（自動予算の材料）、秋の取引、今週の取引がある。
	 * そのあと、画面のデータを作って、その間に発行した SQL の本数を返す。
	 */
	private long statementsToOpen(String username, int count, Screen screen) {
		Long userId = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		Vendor vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		for (int i = 0; i < count; i++) {
			Category category = categoryRepository.save(new Category(userId, "科目" + i));
			budgetRepository.save(new Budget(userId, category, 2026, Season.AUTUMN, 30_000));
			transactionRepository.save(new Transaction(userId, LocalDate.of(2024, 12, 10), 5_000 + i, category, null, vendor, payment));
			transactionRepository.save(new Transaction(userId, LocalDate.of(2025, 12, 10), 6_000 + i, category, null, vendor, payment));
			transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 9, 10), 3_000 + i, category, null, vendor, payment));
			transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 11, 9), 1_000 + i, category, null, vendor, payment));
		}
		entityManager.flush();
		entityManager.clear();

		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		screen.open(userId);
		return statistics.getPrepareStatementCount();
	}

	/** 予実比較：科目、その季節の予算、科目ごとの実績の合計、最初の取引の日の 4 本。科目が増えても同じ */
	@Test
	void theComparisonScreenDoesNotIssueMoreStatementsForMoreCategories() {
		Screen screen = userId -> assertThat(comparisonService.view(userId, AUTUMN_2026, TODAY).lines()).isNotEmpty();

		long withTwo = statementsToOpen("stmtcmp1", 2, screen);
		long withEight = statementsToOpen("stmtcmp2", 8, screen);

		assertThat(withEight).as("科目 8 個でも、2 個のときと同じ本数").isEqualTo(withTwo);
		assertThat(withTwo).isEqualTo(4);
	}

	/** トップ：予実比較の 3 本と、科目、季節の予算、今週の実績の合計、最初の取引の日で 7 本。科目が増えても同じ */
	@Test
	void theTopScreenDoesNotIssueMoreStatementsForMoreCategories() {
		Screen screen = userId -> assertThat(topService.top(userId, TODAY).rows()).isNotEmpty();

		long withTwo = statementsToOpen("stmttop1", 2, screen);
		long withEight = statementsToOpen("stmttop2", 8, screen);

		assertThat(withEight).as("科目 8 個でも、2 個のときと同じ本数").isEqualTo(withTwo);
		assertThat(withTwo).isEqualTo(7);
	}

	/**
	 * 予算設定で「自動予算」を押したとき（Controller と同じ順に、今の値の表と、案を作る）：
	 * 科目と予算（2 本）が 2 回、直前の季節の予算、過去の実績の月ごとの合計、最初の取引の日で 7 本。科目が増えても同じ
	 */
	@Test
	void theBudgetScreenWithTheAutoBudgetDoesNotIssueMoreStatementsForMoreCategories() {
		Screen screen = userId -> {
			assertThat(budgetService.edit(userId, WINTER_2026).rows()).isNotEmpty();
			assertThat(autoBudgetService.propose(userId, WINTER_2026, ProposalChoice.OK, TODAY).rows()).isNotEmpty();
		};

		long withTwo = statementsToOpen("stmtbud1", 2, screen);
		long withEight = statementsToOpen("stmtbud2", 8, screen);

		assertThat(withEight).as("科目 8 個でも、2 個のときと同じ本数").isEqualTo(withTwo);
		assertThat(withTwo).isEqualTo(7);
	}

	// ---- 取引の登録・編集画面（issue 029 の段階 2） ----

	/**
	 * 科目・取引先・支払方法を count 個ずつ持ち、取引先 i に i + 1 件の取引があるユーザーを作る（取引の件数で並ぶので、件数を変えておく）。
	 * そのあと、TransactionController.addFormModel と同じ順に、選択肢を取る（編集なら、先に取引を 1 件取る）。その間の SQL の本数を返す。
	 */
	private long statementsToOpenTheForm(String username, int count, boolean edit) {
		Long userId = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		Long transactionId = null;
		for (int i = 0; i < count; i++) {
			Category category = categoryRepository.save(new Category(userId, "科目" + i));
			Vendor vendor = vendorRepository.save(new Vendor(userId, "取引先" + i));
			PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法" + i));
			for (int n = 0; n <= i; n++) {
				transactionId = transactionRepository.save(
						new Transaction(userId, LocalDate.of(2026, 11, 9), 1_000 + n, category, null, vendor, payment)).getId();
			}
		}
		entityManager.flush();
		entityManager.clear();

		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		if (edit) {
			assertThat(transactionService.get(userId, transactionId).getVendor().getId()).isNotNull();
		}
		assertThat(categoryService.list(userId)).hasSize(count);
		assertThat(vendorService.listForChoices(userId)).hasSize(count);
		assertThat(paymentMethodService.listForChoices(userId)).hasSize(count);
		return statistics.getPrepareStatementCount();
	}

	/** 取引の登録画面：科目、取引先（件数の順）、支払方法（件数の順）の 3 本。科目・取引先・支払方法・取引が増えても同じ */
	@Test
	void theNewTransactionFormDoesNotIssueMoreStatementsForMoreChoicesOrTransactions() {
		long withTwo = statementsToOpenTheForm("stmtform1", 2, false);
		long withEight = statementsToOpenTheForm("stmtform2", 8, false);

		assertThat(withEight).as("8 個ずつ（取引 36 件）でも、2 個ずつ（取引 3 件）のときと同じ本数").isEqualTo(withTwo);
		assertThat(withTwo).isEqualTo(3);
	}

	/** 取引の編集画面：取引 1 件と、登録画面と同じ 3 本で 4 本。増えても同じ */
	@Test
	void theEditTransactionFormDoesNotIssueMoreStatementsForMoreChoicesOrTransactions() {
		long withTwo = statementsToOpenTheForm("stmtform3", 2, true);
		long withEight = statementsToOpenTheForm("stmtform4", 8, true);

		assertThat(withEight).as("8 個ずつでも、2 個ずつのときと同じ本数").isEqualTo(withTwo);
		assertThat(withTwo).isEqualTo(4);
	}

}
