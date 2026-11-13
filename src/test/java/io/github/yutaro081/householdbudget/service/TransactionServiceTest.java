package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.dto.TransactionMonth;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.TransactionForm;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * TransactionService のテスト（設計書 1.4 の例1、3.9、5.5、6.7）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストに出てくるパスワードのハッシュは、ダミーの値（実在するアカウントのものではない）。
 */
@SpringBootTest
@Transactional
class TransactionServiceTest {

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
	private TransactionService service;
	@Autowired
	private CategoryService categoryService;
	@Autowired
	private VendorService vendorService;
	@Autowired
	private PaymentMethodService paymentMethodService;
	@Autowired
	private EntityManager entityManager;

	/** 1人分のユーザーと、科目・取引先・支払方法を1つずつ */
	private record Owner(Long userId, Category category, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id,
				categoryRepository.save(new Category(id, "科目")),
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private static TransactionForm form(Owner owner, LocalDate date, int amount, String description) {
		TransactionForm form = new TransactionForm();
		form.setTransactionDate(date);
		form.setAmount(amount);
		form.setCategoryId(owner.category().getId());
		form.setVendorId(owner.vendor().getId());
		form.setPaymentMethodId(owner.paymentMethod().getId());
		form.setDescription(description);
		return form;
	}

	private static TransactionForm form(Owner owner) {
		return form(owner, LocalDate.of(2026, 10, 27), 1000, "昼食");
	}

	private long countOf(Long userId) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId).size();
	}

	/** DB から読み直した取引（保存前の状態をキャッシュから見ないため） */
	private Transaction reread(Long userId, Long id) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.findByIdAndUserId(id, userId).orElseThrow();
	}

	// ---- 一覧 ----

	@Test
	void listReturnsOwnTransactionsNewestDateFirstThenNewestRegistered() {
		Owner a = createOwner("svctx1");
		Owner b = createOwner("svctx2");
		Transaction oldest = service.register(a.userId(), form(a, LocalDate.of(2026, 1, 5), 100, null));
		Transaction sameDayFirst = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 200, null));
		Transaction sameDaySecond = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 300, null));
		Transaction newest = service.register(a.userId(), form(a, LocalDate.of(2026, 12, 1), 400, null));
		service.register(b.userId(), form(b, LocalDate.of(2026, 11, 1), 999, null));
		entityManager.flush();
		entityManager.clear();

		List<Transaction> list = service.list(a.userId());

		assertThat(list).extracting(Transaction::getId)
				.containsExactly(newest.getId(), sameDaySecond.getId(), sameDayFirst.getId(), oldest.getId());
		assertThat(list).allMatch(t -> t.getUserId().equals(a.userId()));
	}

	@Test
	void listIsEmptyWhenNoTransactions() {
		Owner a = createOwner("svctx3");
		assertThat(service.list(a.userId())).isEmpty();
	}

	// ---- 登録 ----

	@Test
	void registerSavesValuesAndOwner() {
		Owner a = createOwner("svctx4");

		Transaction saved = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 1234, "昼食"));

		Transaction found = reread(a.userId(), saved.getId());
		assertThat(found.getUserId()).isEqualTo(a.userId());
		assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 27));
		assertThat(found.getAmount()).isEqualTo(1234);
		assertThat(found.getDescription()).isEqualTo("昼食");
		assertThat(found.getCategory().getId()).isEqualTo(a.category().getId());
		assertThat(found.getVendor().getId()).isEqualTo(a.vendor().getId());
		assertThat(found.getPaymentMethod().getId()).isEqualTo(a.paymentMethod().getId());
		assertThat(countOf(a.userId())).isEqualTo(1);
	}

	@Test
	void registerWithNullDescriptionStoresNull() {
		Owner a = createOwner("svctx5");

		Transaction saved = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 500, null));

		assertThat(reread(a.userId(), saved.getId()).getDescription()).isNull();
	}

	@Test
	void registerAcceptsBoundaryAmountsAndDates() {
		Owner a = createOwner("svctx6");

		assertThatCode(() -> {
			service.register(a.userId(), form(a, LocalDate.of(2000, 3, 1), 1, null));
			service.register(a.userId(), form(a, LocalDate.of(2100, 12, 31), 1_000_000, null));
			entityManager.flush();
		}).doesNotThrowAnyException();
		assertThat(countOf(a.userId())).isEqualTo(2);
	}

	/** 他人の科目／取引先／支払方法のID、存在しないIDは、NotFoundException。取引は増えない（3つとも） */
	@Test
	void registerWithOtherUsersItemsIsNotFoundAndSavesNothing() {
		Owner a = createOwner("svctx7");
		Owner b = createOwner("svctx8");

		TransactionForm otherCategory = form(a);
		otherCategory.setCategoryId(b.category().getId());
		TransactionForm otherVendor = form(a);
		otherVendor.setVendorId(b.vendor().getId());
		TransactionForm otherPayment = form(a);
		otherPayment.setPaymentMethodId(b.paymentMethod().getId());

		for (TransactionForm bad : List.of(otherCategory, otherVendor, otherPayment)) {
			assertThatThrownBy(() -> service.register(a.userId(), bad)).isInstanceOf(NotFoundException.class);
		}
		assertThat(countOf(a.userId())).isZero();
		assertThat(countOf(b.userId())).isZero();
	}

	@Test
	void registerWithNonexistentItemsIsNotFoundAndSavesNothing() {
		Owner a = createOwner("svctx9");
		long missing = a.category().getId() + a.vendor().getId() + a.paymentMethod().getId() + 1_000_000;

		TransactionForm noCategory = form(a);
		noCategory.setCategoryId(missing);
		TransactionForm noVendor = form(a);
		noVendor.setVendorId(missing);
		TransactionForm noPayment = form(a);
		noPayment.setPaymentMethodId(missing);

		for (TransactionForm bad : List.of(noCategory, noVendor, noPayment)) {
			assertThatThrownBy(() -> service.register(a.userId(), bad)).isInstanceOf(NotFoundException.class);
		}
		assertThat(countOf(a.userId())).isZero();
	}

	// ---- 詳細（1件取得） ----

	@Test
	void getReturnsOwnTransactionWithAssociations() {
		Owner a = createOwner("svctx10");
		Transaction saved = service.register(a.userId(), form(a));
		entityManager.flush();
		entityManager.clear();

		Transaction found = service.get(a.userId(), saved.getId());

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getCategory().getName()).isEqualTo("科目");
		assertThat(found.getVendor().getName()).isEqualTo("取引先");
		assertThat(found.getPaymentMethod().getName()).isEqualTo("支払方法");
	}

	@Test
	void getOtherUsersOrMissingTransactionIsNotFound() {
		Owner a = createOwner("svctx11");
		Owner b = createOwner("svctx12");
		Transaction mine = service.register(a.userId(), form(a));

		assertThatThrownBy(() -> service.get(b.userId(), mine.getId())).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.get(a.userId(), mine.getId() + 1_000_000)).isInstanceOf(NotFoundException.class);
	}

	// ---- 更新 ----

	@Test
	void updateChangesValuesButNotOwner() {
		Owner a = createOwner("svctx13");
		Category otherCategory = categoryRepository.save(new Category(a.userId(), "別の科目"));
		Transaction saved = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 1000, "昼食"));

		TransactionForm changed = form(a, LocalDate.of(2026, 11, 3), 2500, null);
		changed.setCategoryId(otherCategory.getId());
		service.update(a.userId(), saved.getId(), changed);

		Transaction found = reread(a.userId(), saved.getId());
		assertThat(found.getUserId()).isEqualTo(a.userId());
		assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 3));
		assertThat(found.getAmount()).isEqualTo(2500);
		assertThat(found.getDescription()).isNull();
		assertThat(found.getCategory().getId()).isEqualTo(otherCategory.getId());
		assertThat(countOf(a.userId())).isEqualTo(1);
	}

	/** 他人の取引・存在しない取引は NotFoundException。他人の取引は変わっていない */
	@Test
	void updateOtherUsersTransactionIsNotFoundAndLeavesItUnchanged() {
		Owner a = createOwner("svctx14");
		Owner b = createOwner("svctx15");
		Transaction mine = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 1000, "昼食"));

		// B が、自分の科目などを使って、A の取引の ID を更新しようとする
		TransactionForm attempt = form(b, LocalDate.of(2026, 12, 25), 9999, "書き換え");
		assertThatThrownBy(() -> service.update(b.userId(), mine.getId(), attempt)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(a.userId(), mine.getId() + 1_000_000, form(a)))
				.isInstanceOf(NotFoundException.class);

		Transaction found = reread(a.userId(), mine.getId());
		assertThat(found.getUserId()).isEqualTo(a.userId());
		assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 27));
		assertThat(found.getAmount()).isEqualTo(1000);
		assertThat(found.getDescription()).isEqualTo("昼食");
		assertThat(found.getCategory().getId()).isEqualTo(a.category().getId());
	}

	/** 更新で他人の科目などを渡すと NotFoundException。元の取引は変わっていない */
	@Test
	void updateWithOtherUsersItemsIsNotFoundAndLeavesTransactionUnchanged() {
		Owner a = createOwner("svctx16");
		Owner b = createOwner("svctx17");
		Transaction mine = service.register(a.userId(), form(a, LocalDate.of(2026, 10, 27), 1000, "昼食"));

		TransactionForm otherCategory = form(a, LocalDate.of(2026, 12, 25), 9999, "書き換え");
		otherCategory.setCategoryId(b.category().getId());
		TransactionForm otherVendor = form(a, LocalDate.of(2026, 12, 25), 9999, "書き換え");
		otherVendor.setVendorId(b.vendor().getId());
		TransactionForm otherPayment = form(a, LocalDate.of(2026, 12, 25), 9999, "書き換え");
		otherPayment.setPaymentMethodId(b.paymentMethod().getId());

		for (TransactionForm bad : List.of(otherCategory, otherVendor, otherPayment)) {
			assertThatThrownBy(() -> service.update(a.userId(), mine.getId(), bad)).isInstanceOf(NotFoundException.class);
		}

		Transaction found = reread(a.userId(), mine.getId());
		assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 27));
		assertThat(found.getAmount()).isEqualTo(1000);
		assertThat(found.getDescription()).isEqualTo("昼食");
		assertThat(found.getCategory().getId()).isEqualTo(a.category().getId());
		assertThat(found.getVendor().getId()).isEqualTo(a.vendor().getId());
		assertThat(found.getPaymentMethod().getId()).isEqualTo(a.paymentMethod().getId());
	}

	// ---- 削除（物理削除） ----

	@Test
	void deleteRemovesTheRow() {
		Owner a = createOwner("svctx18");
		Transaction saved = service.register(a.userId(), form(a));
		Transaction kept = service.register(a.userId(), form(a));
		entityManager.flush();
		entityManager.clear();

		service.delete(a.userId(), saved.getId());

		entityManager.flush();
		entityManager.clear();
		assertThat(transactionRepository.findByIdAndUserId(saved.getId(), a.userId())).isEmpty();
		assertThat(transactionRepository.findByIdAndUserId(kept.getId(), a.userId())).isPresent();
		assertThat(countOf(a.userId())).isEqualTo(1);
	}

	/** 他人の取引・存在しない取引は NotFoundException。他人の取引は残っている */
	@Test
	void deleteOtherUsersOrMissingTransactionIsNotFoundAndKeepsIt() {
		Owner a = createOwner("svctx19");
		Owner b = createOwner("svctx20");
		Transaction mine = service.register(a.userId(), form(a));

		assertThatThrownBy(() -> service.delete(b.userId(), mine.getId())).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(a.userId(), mine.getId() + 1_000_000)).isInstanceOf(NotFoundException.class);

		assertThat(reread(a.userId(), mine.getId())).isNotNull();
		assertThat(countOf(a.userId())).isEqualTo(1);
	}

	/** 取引を削除すると、その取引で使っていた科目・取引先・支払方法が削除できるようになる（Q4-2 の C の自動版） */
	@Test
	void afterDeletingTheTransactionItsItemsCanBeDeleted() {
		Owner a = createOwner("svctx21");
		Transaction saved = service.register(a.userId(), form(a));
		entityManager.flush();
		entityManager.clear();

		// 使用中の間は削除できない
		assertThatThrownBy(() -> categoryService.delete(a.userId(), a.category().getId()))
				.hasMessageContaining("error.delete.inUse");
		assertThatThrownBy(() -> vendorService.delete(a.userId(), a.vendor().getId()))
				.hasMessageContaining("error.delete.inUse");
		assertThatThrownBy(() -> paymentMethodService.delete(a.userId(), a.paymentMethod().getId()))
				.hasMessageContaining("error.delete.inUse");

		service.delete(a.userId(), saved.getId());
		entityManager.flush();
		entityManager.clear();

		assertThatCode(() -> {
			categoryService.delete(a.userId(), a.category().getId());
			vendorService.delete(a.userId(), a.vendor().getId());
			paymentMethodService.delete(a.userId(), a.paymentMethod().getId());
		}).doesNotThrowAnyException();
	}

	// ---- 月ごとのまとまり（issue 025 の段階 2。取引一覧の月の見出し）----

	private void saveOn(Owner owner, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(owner.userId(), date, amount, owner.category(), null, owner.vendor(), owner.paymentMethod()));
	}

	private List<TransactionMonth> months(Owner owner) {
		entityManager.flush();
		entityManager.clear();
		return service.listByMonth(owner.userId());
	}

	/** 月ごとに分かれ、新しい月が先。件数と合計が合う。月の中は日付の新しい順 */
	@Test
	void listByMonthGroupsByMonthNewestFirstWithCountAndTotal() {
		Owner a = createOwner("bymonth1");
		saveOn(a, LocalDate.of(2026, 10, 15), 400);
		saveOn(a, LocalDate.of(2026, 11, 1), 200);
		saveOn(a, LocalDate.of(2026, 10, 31), 300);
		saveOn(a, LocalDate.of(2026, 11, 30), 100);

		List<TransactionMonth> months = months(a);

		assertThat(months).extracting(TransactionMonth::month).containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 10));
		assertThat(months).extracting(TransactionMonth::count).containsExactly(2, 2);
		assertThat(months).extracting(TransactionMonth::total).containsExactly(300L, 700L);
		assertThat(months.get(0).transactions()).extracting(Transaction::getTransactionDate)
				.containsExactly(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1));
	}

	/** 月の境目：月末（31 日）と翌月の初め（1 日）は、別の月。うるう年の 2 月 29 日と 3 月 1 日も */
	@Test
	void theLastDayAndTheFirstDayOfTheNextMonthAreInDifferentMonths() {
		Owner a = createOwner("bymonth2");
		saveOn(a, LocalDate.of(2024, 2, 29), 10);
		saveOn(a, LocalDate.of(2024, 3, 1), 20);
		saveOn(a, LocalDate.of(2024, 1, 31), 30);
		saveOn(a, LocalDate.of(2024, 2, 1), 40);

		List<TransactionMonth> months = months(a);

		assertThat(months).extracting(TransactionMonth::month)
				.containsExactly(YearMonth.of(2024, 3), YearMonth.of(2024, 2), YearMonth.of(2024, 1));
		assertThat(months).extracting(TransactionMonth::total).containsExactly(20L, 50L, 30L);
		assertThat(months).extracting(TransactionMonth::count).containsExactly(1, 2, 1);
	}

	/** 年またぎ：12 月 31 日と 1 月 1 日は、年の違う別の月。1 月が上（新しい） */
	@Test
	void decemberAndJanuaryAcrossTheNewYearAreSeparateMonths() {
		Owner a = createOwner("bymonth3");
		saveOn(a, LocalDate.of(2025, 12, 31), 600);
		saveOn(a, LocalDate.of(2026, 1, 1), 500);
		saveOn(a, LocalDate.of(2025, 12, 1), 7);

		List<TransactionMonth> months = months(a);

		assertThat(months).extracting(TransactionMonth::month).containsExactly(YearMonth.of(2026, 1), YearMonth.of(2025, 12));
		assertThat(months).extracting(TransactionMonth::total).containsExactly(500L, 607L);
		assertThat(months).extracting(TransactionMonth::count).containsExactly(1, 2);
	}

	/** 同じ月でも、年が違えば別（2025 年 11 月と 2026 年 11 月） */
	@Test
	void theSameMonthOfDifferentYearsIsNotMerged() {
		Owner a = createOwner("bymonth4");
		saveOn(a, LocalDate.of(2025, 11, 5), 1);
		saveOn(a, LocalDate.of(2026, 11, 5), 2);

		assertThat(months(a)).extracting(TransactionMonth::month).containsExactly(YearMonth.of(2026, 11), YearMonth.of(2025, 11));
	}

	/** 守り：件数にも合計にも、他人の取引が混ざらない（同じ月に他人の取引があっても） */
	@Test
	void otherUsersTransactionsAreNeverCountedOrSummed() {
		Owner a = createOwner("bymonth5");
		Owner b = createOwner("bymonth6");
		saveOn(a, LocalDate.of(2026, 11, 9), 1_500);
		saveOn(b, LocalDate.of(2026, 11, 9), 70_000);
		saveOn(b, LocalDate.of(2026, 11, 10), 80_000);
		saveOn(b, LocalDate.of(2026, 9, 1), 90_000);

		List<TransactionMonth> months = months(a);

		assertThat(months).hasSize(1);
		assertThat(months.get(0).count()).isEqualTo(1);
		assertThat(months.get(0).total()).isEqualTo(1_500L);
		assertThat(months.get(0).transactions()).extracting(Transaction::getUserId).containsOnly(a.userId());
		assertThat(months(b)).extracting(TransactionMonth::total).containsExactly(150_000L, 90_000L);
	}

	/** 取引が 1 件もないとき、空 */
	@Test
	void listByMonthIsEmptyWithoutTransactions() {
		assertThat(months(createOwner("bymonth7"))).isEmpty();
	}

	/** 月ごとの件数の合計は、全件（list）の件数と同じ。並びも、全部つなぐと list と同じ */
	@Test
	void theMonthsTogetherAreExactlyTheFullList() {
		Owner a = createOwner("bymonth8");
		saveOn(a, LocalDate.of(2026, 11, 9), 1);
		saveOn(a, LocalDate.of(2026, 11, 9), 2);
		saveOn(a, LocalDate.of(2026, 10, 9), 3);
		saveOn(a, LocalDate.of(2025, 12, 9), 4);

		List<TransactionMonth> months = months(a);
		List<Long> fromMonths = months.stream().flatMap(m -> m.transactions().stream()).map(Transaction::getId).toList();
		List<Long> fromList = service.list(a.userId()).stream().map(Transaction::getId).toList();

		assertThat(fromMonths).isEqualTo(fromList);
		assertThat(months.stream().mapToInt(TransactionMonth::count).sum()).isEqualTo(4);
		assertThat(months.stream().mapToLong(TransactionMonth::total).sum()).isEqualTo(10L);
	}
}
