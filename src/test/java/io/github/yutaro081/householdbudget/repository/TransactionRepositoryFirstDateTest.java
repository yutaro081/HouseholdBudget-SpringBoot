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
 * TransactionRepository.findFirstTransactionDate（自分の取引の日付の最小値。設計書 5.13、3.9、issue 020）のテスト。
 * 他人の取引は入らない。取引がなければ空。各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@Transactional
class TransactionRepositoryFirstDateTest {

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

	private record Owner(Long id, Category category, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, categoryRepository.save(new Category(id, "食費")),
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private void save(Owner o, LocalDate date) {
		transactionRepository.save(new Transaction(o.id(), date, 100, o.category(), null, o.vendor(), o.paymentMethod()));
	}

	@Test
	void returnsTheOldestDateOfTheOwnTransactionsWhateverTheInsertOrder() {
		Owner a = createOwner("firstdate1");
		save(a, LocalDate.of(2026, 11, 1));
		save(a, LocalDate.of(2026, 9, 20));
		save(a, LocalDate.of(2026, 10, 5));
		entityManager.flush();
		entityManager.clear();

		assertThat(transactionRepository.findFirstTransactionDate(a.id())).contains(LocalDate.of(2026, 9, 20));
	}

	@Test
	void isEmptyWhenThereAreNoTransactions() {
		Owner a = createOwner("firstdate2");

		assertThat(transactionRepository.findFirstTransactionDate(a.id())).isEmpty();
	}

	@Test
	void doesNotSeeOtherUsersTransactions() {
		Owner a = createOwner("firstdate3");
		Owner b = createOwner("firstdate4");
		save(a, LocalDate.of(2026, 11, 1));
		save(b, LocalDate.of(2020, 1, 1));       // 他人の、もっと古い取引
		entityManager.flush();
		entityManager.clear();

		assertThat(transactionRepository.findFirstTransactionDate(a.id())).contains(LocalDate.of(2026, 11, 1));
		assertThat(transactionRepository.findFirstTransactionDate(b.id())).contains(LocalDate.of(2020, 1, 1));
	}
}
