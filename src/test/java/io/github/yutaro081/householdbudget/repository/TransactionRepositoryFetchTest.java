package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.SessionFactory;
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
 * 一覧が、取引の件数に関係なく、同じ数の SQL で済むこと（取引1件ごとに別々に読み込まない、設計書 4.6）の確認。
 * Hibernate の統計を使うため、このテストだけ設定を足す（ほかのテストの設定は変えない）。
 * 開発用の DB につなぐが、トランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class TransactionRepositoryFetchTest {

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

	/** 取引を count 件作り、一覧を読んで、科目などの名前まで読んだときの SQL の数を返す。 */
	private long statementsToReadList(String username, int count) {
		User user = userRepository.save(new User(username, "dummy-hash-for-test"));
		Long userId = user.getId();
		for (int i = 0; i < count; i++) {
			// 取引ごとに別の科目・取引先・支払方法にする（読み込み方が悪いと、件数分の SQL が増える）
			Category category = categoryRepository.save(new Category(userId, "科目" + i));
			Vendor vendor = vendorRepository.save(new Vendor(userId, "取引先" + i));
			PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払" + i));
			transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 1 + i), 100 + i,
					category, null, vendor, payment));
		}
		entityManager.flush();
		entityManager.clear();

		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		List<Transaction> list = transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId);
		for (Transaction t : list) {
			// 画面が名前を読むのと同じ
			t.getCategory().getName();
			t.getVendor().getName();
			t.getPaymentMethod().getName();
		}
		assertThat(list).hasSize(count);
		return statistics.getPrepareStatementCount();
	}

	@Test
	void numberOfStatementsDoesNotGrowWithNumberOfTransactions() {
		long withOne = statementsToReadList("txfetch1", 1);
		long withFive = statementsToReadList("txfetch2", 5);

		assertThat(withOne).isEqualTo(1);
		assertThat(withFive).isEqualTo(withOne);
	}

}
