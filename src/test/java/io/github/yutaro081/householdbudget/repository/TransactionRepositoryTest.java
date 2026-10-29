package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import jakarta.persistence.EntityManager;

/**
 * Transaction の保存と、TransactionRepository の「使用中」の確認（設計書 5.5）、一覧・1件取得（3.9）、
 * DB の CHECK 制約（3.6）のテスト。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@Transactional
class TransactionRepositoryTest {

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

	/** 1人分のユーザーと、科目・取引先・支払方法を1つずつ */
	private record Owner(User user, Category category, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		User user = userRepository.save(new User(username, "dummy-hash-for-test"));
		Long id = user.getId();
		return new Owner(user,
				categoryRepository.save(new Category(id, "科目")),
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Transaction saveTransaction(Owner owner, String description) {
		Transaction saved = transactionRepository.save(new Transaction(owner.user().getId(),
				LocalDate.of(2026, 10, 27), 1000, owner.category(), description, owner.vendor(), owner.paymentMethod()));
		entityManager.flush();
		entityManager.clear();
		return saved;
	}

	// ---- Entity と表の対応（3.6） ----

	@Test
	void savesAndReadsBack() {
		Owner owner = createOwner("txtest1");
		Transaction saved = saveTransaction(owner, "昼食");

		Transaction found = transactionRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getUserId()).isEqualTo(owner.user().getId());
		assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 27));
		assertThat(found.getAmount()).isEqualTo(1000);
		assertThat(found.getCategory().getId()).isEqualTo(owner.category().getId());
		assertThat(found.getDescription()).isEqualTo("昼食");
		assertThat(found.getVendor().getId()).isEqualTo(owner.vendor().getId());
		assertThat(found.getPaymentMethod().getId()).isEqualTo(owner.paymentMethod().getId());
	}

	/** 摘要だけ NULL を許す（要件4.2、3.6） */
	@Test
	void descriptionCanBeNull() {
		Owner owner = createOwner("txtest2");
		Transaction saved = saveTransaction(owner, null);

		Transaction found = transactionRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getDescription()).isNull();
	}

	@Test
	void descriptionOf30CharactersIsSaved() {
		Owner owner = createOwner("txtest3");
		String thirty = "あ".repeat(30);
		Transaction saved = saveTransaction(owner, thirty);

		assertThat(transactionRepository.findById(saved.getId()).orElseThrow().getDescription()).isEqualTo(thirty);
	}

	// ---- 「使用中」の確認（5.5）：user_id でも絞っている ----

	@Test
	void usedByOwnTransaction() {
		Owner owner = createOwner("txtest4");
		saveTransaction(owner, null);
		Long userId = owner.user().getId();

		assertThat(transactionRepository.existsByUserIdAndCategoryId(userId, owner.category().getId())).isTrue();
		assertThat(transactionRepository.existsByUserIdAndVendorId(userId, owner.vendor().getId())).isTrue();
		assertThat(transactionRepository.existsByUserIdAndPaymentMethodId(userId, owner.paymentMethod().getId())).isTrue();
	}

	@Test
	void notUsedWhenNoTransaction() {
		Owner owner = createOwner("txtest5");
		Long userId = owner.user().getId();

		assertThat(transactionRepository.existsByUserIdAndCategoryId(userId, owner.category().getId())).isFalse();
		assertThat(transactionRepository.existsByUserIdAndVendorId(userId, owner.vendor().getId())).isFalse();
		assertThat(transactionRepository.existsByUserIdAndPaymentMethodId(userId, owner.paymentMethod().getId())).isFalse();
	}

	/** 同じユーザーの、別の科目・取引先・支払方法は、使われていない */
	@Test
	void otherItemsOfTheSameUserAreNotUsed() {
		Owner owner = createOwner("txtest6");
		Long userId = owner.user().getId();
		Category otherCategory = categoryRepository.save(new Category(userId, "別の科目"));
		Vendor otherVendor = vendorRepository.save(new Vendor(userId, "別の取引先"));
		PaymentMethod otherPayment = paymentMethodRepository.save(new PaymentMethod(userId, "別の支払方法"));
		saveTransaction(owner, null);

		assertThat(transactionRepository.existsByUserIdAndCategoryId(userId, otherCategory.getId())).isFalse();
		assertThat(transactionRepository.existsByUserIdAndVendorId(userId, otherVendor.getId())).isFalse();
		assertThat(transactionRepository.existsByUserIdAndPaymentMethodId(userId, otherPayment.getId())).isFalse();
	}

	/** 他のユーザーの取引は、数えない（user_id で絞っている）。他人が自分の ID の科目を使っていても、自分のものとして答えない */
	@Test
	void otherUsersTransactionsAreNotCounted() {
		Owner a = createOwner("txtest7");
		Owner b = createOwner("txtest8");
		saveTransaction(a, null);

		// B から見ると、A の科目・取引先・支払方法は「使われている」ことにならない
		Long bId = b.user().getId();
		assertThat(transactionRepository.existsByUserIdAndCategoryId(bId, a.category().getId())).isFalse();
		assertThat(transactionRepository.existsByUserIdAndVendorId(bId, a.vendor().getId())).isFalse();
		assertThat(transactionRepository.existsByUserIdAndPaymentMethodId(bId, a.paymentMethod().getId())).isFalse();
		// B の科目は、A の取引では使われていない
		Long aId = a.user().getId();
		assertThat(transactionRepository.existsByUserIdAndCategoryId(aId, b.category().getId())).isFalse();
	}

	// ---- 一覧・1件取得（Phase 5。user_id で絞る、3.9） ----

	private Transaction saveOn(Owner owner, LocalDate date, int amount) {
		Transaction saved = transactionRepository.save(new Transaction(owner.user().getId(), date, amount,
				owner.category(), null, owner.vendor(), owner.paymentMethod()));
		entityManager.flush();
		entityManager.clear();
		return saved;
	}

	/** 日付の新しい順。同じ日は id の新しい順（Q4・Q5）。他のユーザーの取引は返らない。 */
	@Test
	void listIsNewestDateFirstThenNewestIdAndOnlyOwn() {
		Owner a = createOwner("txtest10");
		Owner b = createOwner("txtest11");
		Transaction oldest = saveOn(a, LocalDate.of(2026, 1, 5), 100);
		Transaction sameDayFirst = saveOn(a, LocalDate.of(2026, 10, 27), 200);
		Transaction sameDaySecond = saveOn(a, LocalDate.of(2026, 10, 27), 300);
		Transaction newest = saveOn(a, LocalDate.of(2026, 12, 1), 400);
		saveOn(b, LocalDate.of(2026, 11, 1), 999);

		List<Transaction> list = transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(a.user().getId());

		assertThat(list).extracting(Transaction::getId)
				.containsExactly(newest.getId(), sameDaySecond.getId(), sameDayFirst.getId(), oldest.getId());
	}

	@Test
	void listIsEmptyWhenNoTransactions() {
		Owner a = createOwner("txtest12");

		assertThat(transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(a.user().getId())).isEmpty();
	}

	/** ID と user_id の両方で探す。自分のものは見つかり、他人のID・存在しないIDは見つからない。 */
	@Test
	void findByIdAndUserIdFindsOnlyOwn() {
		Owner a = createOwner("txtest13");
		Owner b = createOwner("txtest14");
		Transaction mine = saveOn(a, LocalDate.of(2026, 10, 27), 100);

		assertThat(transactionRepository.findByIdAndUserId(mine.getId(), a.user().getId())).isPresent();
		assertThat(transactionRepository.findByIdAndUserId(mine.getId(), b.user().getId())).isEmpty();
		assertThat(transactionRepository.findByIdAndUserId(mine.getId() + 100000, a.user().getId())).isEmpty();
	}

	/** 一覧・1件取得では、科目・取引先・支払方法も読み込み済み（設計書 4.6）。 */
	@Test
	void associationsAreLoadedTogether() {
		Owner a = createOwner("txtest15");
		Transaction saved = saveOn(a, LocalDate.of(2026, 10, 27), 100);
		var util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

		Transaction one = transactionRepository.findByIdAndUserId(saved.getId(), a.user().getId()).orElseThrow();
		assertThat(util.isLoaded(one, "category")).isTrue();
		assertThat(util.isLoaded(one, "vendor")).isTrue();
		assertThat(util.isLoaded(one, "paymentMethod")).isTrue();

		entityManager.clear();
		Transaction fromList = transactionRepository
				.findByUserIdOrderByTransactionDateDescIdDesc(a.user().getId()).get(0);
		assertThat(util.isLoaded(fromList, "category")).isTrue();
		assertThat(util.isLoaded(fromList, "vendor")).isTrue();
		assertThat(util.isLoaded(fromList, "paymentMethod")).isTrue();
	}

	// ---- 更新用のメソッド：持ち主は変わらない ----

	@Test
	void updateChangesValuesButNotOwner() {
		Owner a = createOwner("txtest16");
		Long userId = a.user().getId();
		Category other = categoryRepository.save(new Category(userId, "別の科目"));
		Transaction saved = saveOn(a, LocalDate.of(2026, 10, 27), 100);

		Transaction found = transactionRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow();
		found.update(LocalDate.of(2026, 11, 1), 250, other, "変更後", a.vendor(), a.paymentMethod());
		entityManager.flush();
		entityManager.clear();

		Transaction reread = transactionRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow();
		assertThat(reread.getUserId()).isEqualTo(userId);
		assertThat(reread.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(reread.getAmount()).isEqualTo(250);
		assertThat(reread.getCategory().getId()).isEqualTo(other.getId());
		assertThat(reread.getDescription()).isEqualTo("変更後");
	}

	// ---- DB の CHECK 制約（3.6、R12）：最後の守り。失敗するテストは1つずつ別にする ----

	private void saveWith(Owner owner, LocalDate date, int amount) {
		transactionRepository.saveAndFlush(new Transaction(owner.user().getId(), date, amount,
				owner.category(), null, owner.vendor(), owner.paymentMethod()));
	}

	@Test
	void checkAllowsAmountBoundaries() {
		Owner a = createOwner("txtest20");
		saveWith(a, LocalDate.of(2026, 10, 27), 1);
		saveWith(a, LocalDate.of(2026, 10, 27), 1_000_000);
	}

	@Test
	void checkAllowsDateBoundaries() {
		Owner a = createOwner("txtest21");
		saveWith(a, LocalDate.of(2000, 1, 1), 100);
		saveWith(a, LocalDate.of(2100, 12, 31), 100);
	}

	@Test
	void checkRejectsAmountZero() {
		Owner a = createOwner("txtest22");
		assertThatThrownBy(() -> saveWith(a, LocalDate.of(2026, 10, 27), 0))
				.rootCause().hasMessageContaining("ck_transactions_amount");
	}

	@Test
	void checkRejectsAmountOver1000000() {
		Owner a = createOwner("txtest23");
		assertThatThrownBy(() -> saveWith(a, LocalDate.of(2026, 10, 27), 1_000_001))
				.rootCause().hasMessageContaining("ck_transactions_amount");
	}

	@Test
	void checkRejectsNegativeAmount() {
		Owner a = createOwner("txtest24");
		assertThatThrownBy(() -> saveWith(a, LocalDate.of(2026, 10, 27), -1))
				.rootCause().hasMessageContaining("ck_transactions_amount");
	}

	@Test
	void checkRejectsDateBefore2000() {
		Owner a = createOwner("txtest25");
		assertThatThrownBy(() -> saveWith(a, LocalDate.of(1999, 12, 31), 100))
				.rootCause().hasMessageContaining("ck_transactions_date");
	}

	@Test
	void checkRejectsDateAfter2100() {
		Owner a = createOwner("txtest26");
		assertThatThrownBy(() -> saveWith(a, LocalDate.of(2101, 1, 1), 100))
				.rootCause().hasMessageContaining("ck_transactions_date");
	}

}
