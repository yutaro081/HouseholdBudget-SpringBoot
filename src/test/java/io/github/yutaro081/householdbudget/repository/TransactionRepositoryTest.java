package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

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
 * Transaction の保存と、TransactionRepository の「使用中」の確認（設計書 5.5）のテスト。
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

}
