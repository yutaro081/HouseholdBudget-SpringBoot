package io.github.yutaro081.householdbudget.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * Entity が、schema.sql で作ったテーブル（列名・型・制約）と合っているかの確認（Phase 3 の 4.3）。
 * ddl-auto=none のため、食い違いは実際に読み書きするまで分からない。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@Transactional
class EntityMappingTest {

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
	private EntityManager entityManager;

	private User saveUser(String username) {
		User user = userRepository.save(new User(username, "dummy-hash-for-test"));
		entityManager.flush();
		return user;
	}

	@Test
	void user() {
		User saved = saveUser("maptest1");
		entityManager.clear();

		User found = userRepository.findByUsername("maptest1").orElseThrow();

		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getUsername()).isEqualTo("maptest1");
		assertThat(found.getPasswordHash()).isEqualTo("dummy-hash-for-test");
	}

	/** ユーザー名の大文字・小文字は、DB の照合順序（utf8mb4_0900_as_ci）で同じとみなされる（Q10、3.12） */
	@Test
	void usernameIgnoresCase() {
		saveUser("MapTest2");
		entityManager.clear();

		assertThat(userRepository.existsByUsername("maptest2")).isTrue();
		assertThat(userRepository.findByUsername("MAPTEST2")).isPresent();
		assertThat(userRepository.existsByUsername("maptest3")).isFalse();
	}

	@Test
	void categoryVendorPaymentMethod() {
		User user = saveUser("maptest4");
		Long userId = user.getId();

		Category category = categoryRepository.save(new Category(userId, "交通費"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "ガソリンスタンド"));
		PaymentMethod paymentMethod = paymentMethodRepository.save(new PaymentMethod(userId, "クレジットカード"));
		entityManager.flush();
		entityManager.clear();

		Category c = categoryRepository.findById(category.getId()).orElseThrow();
		assertThat(c.getUserId()).isEqualTo(userId);
		assertThat(c.getName()).isEqualTo("交通費");

		Vendor v = vendorRepository.findById(vendor.getId()).orElseThrow();
		assertThat(v.getUserId()).isEqualTo(userId);
		assertThat(v.getName()).isEqualTo("ガソリンスタンド");

		PaymentMethod p = paymentMethodRepository.findById(paymentMethod.getId()).orElseThrow();
		assertThat(p.getUserId()).isEqualTo(userId);
		assertThat(p.getName()).isEqualTo("クレジットカード");
	}

	/** 取引の印（receipt_group。issue 032）：最初は NULL。値を入れて読み直せる。NULL に戻せる */
	@Test
	void transactionReceiptGroup() {
		User user = saveUser("maptest6");
		Long userId = user.getId();
		Category category = categoryRepository.save(new Category(userId, "食費"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "スーパー"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		Transaction saved = transactionRepository.save(new Transaction(userId, java.time.LocalDate.of(2026, 11, 9), 100, category, null, vendor, payment));
		entityManager.flush();
		entityManager.clear();

		Transaction found = transactionRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow();
		assertThat(found.getReceiptGroup()).isNull();

		found.changeReceiptGroup(saved.getId());
		entityManager.flush();
		entityManager.clear();
		assertThat(transactionRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow().getReceiptGroup()).isEqualTo(saved.getId());
		Object stored = entityManager.createNativeQuery("SELECT receipt_group FROM transactions WHERE id = ?1")
				.setParameter(1, saved.getId()).getSingleResult();
		assertThat(((Number) stored).longValue()).isEqualTo(saved.getId());

		transactionRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow().changeReceiptGroup(null);
		entityManager.flush();
		entityManager.clear();
		assertThat(transactionRepository.findByIdAndUserId(saved.getId(), userId).orElseThrow().getReceiptGroup()).isNull();
	}

	/** 予算：season は SPRING などの文字で保存される。冬の年は12月がある年 */
	@Test
	void budget() {
		User user = saveUser("maptest5");
		Category category = categoryRepository.save(new Category(user.getId(), "食費"));
		Budget saved = budgetRepository.save(new Budget(user.getId(), category, 2026, Season.WINTER, 30000));
		entityManager.flush();
		entityManager.clear();

		Budget found = budgetRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getUserId()).isEqualTo(user.getId());
		assertThat(found.getCategory().getId()).isEqualTo(category.getId());
		assertThat(found.getSeasonYear()).isEqualTo(2026);
		assertThat(found.getSeason()).isEqualTo(Season.WINTER);
		assertThat(found.getAmount()).isEqualTo(30000);

		// DB に保存された season の値そのものを確かめる（Season の名前の文字）
		Object stored = entityManager
				.createNativeQuery("SELECT season FROM budgets WHERE id = ?1")
				.setParameter(1, saved.getId())
				.getSingleResult();
		assertThat(stored).isEqualTo("WINTER");
	}

}
