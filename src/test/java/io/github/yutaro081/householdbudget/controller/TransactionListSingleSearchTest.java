package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * 取引一覧を開いたとき、取引の検索（全件）は 1 回だけ走る（issue 025 の見直し。issue 020 の countingStart と同じ形の無駄をなくす）。
 * 件数の文（「3 件を表示しています」）と「取引がまだない」の判定は、月ごとのまとまりから出す。
 * リポジトリの呼ばれた回数で確かめる（<code>MockitoSpyBean</code>。本物のデータベースにつなぐ）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionListSingleSearchTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoSpyBean
	private TransactionRepository transactionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private EntityManager entityManager;

	private Long userId;
	private RequestPostProcessor asUser;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("onesearch1", "dummy-hash-for-test")).getId();
		asUser = user(new LoginUser(userId, "onesearch1", "x"));
	}

	private void saveThree() {
		Category category = categoryRepository.save(new Category(userId, "食費"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 11, 9), 100, category, null, vendor, payment));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 11, 8), 200, category, null, vendor, payment));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 31), 300, category, null, vendor, payment));
		entityManager.flush();
		entityManager.clear();
	}

	private String list() throws Exception {
		return mockMvc.perform(get("/transactions").with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 取引がある画面：全件の検索は 1 回。件数の文と月の見出しは、前と同じ */
	@Test
	void theListSearchesTheTransactionsOnlyOnce() throws Exception {
		saveThree();

		String body = list();

		verify(transactionRepository, times(1)).findByUserIdOrderByTransactionDateDescIdDesc(userId);
		assertThat(body).contains("日付の新しい順に、3 件を表示しています。");
		assertThat(body).contains("2 件・合計 <span class=\"month-amount\">300円</span>", "1 件・合計 <span class=\"month-amount\">300円</span>");
	}

	/** 取引がない画面：検索は 1 回。「取引はまだありません」で、件数の文も表もない */
	@Test
	void theEmptyListAlsoSearchesOnlyOnceAndShowsTheEmptyMessage() throws Exception {
		String body = list();

		verify(transactionRepository, times(1)).findByUserIdOrderByTransactionDateDescIdDesc(userId);
		assertThat(body).contains("取引はまだありません").doesNotContain("件を表示しています", "<table", "month-row");
	}

	/** 件数は、1,000 件でも、カンマ付き（月のまとまりの件数を足した数）。検索は 1 回のまま */
	@Test
	void theCountIsTheSumOfTheMonthsAndHasCommas() throws Exception {
		Category category = categoryRepository.save(new Category(userId, "食費"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		for (int i = 0; i < 1000; i++) {
			transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 11, 1).plusDays(i % 2 == 0 ? 0 : 40), 1, category, null, vendor, payment));
		}
		entityManager.flush();
		entityManager.clear();
		org.mockito.Mockito.clearInvocations(transactionRepository);

		String body = list();

		verify(transactionRepository, times(1)).findByUserIdOrderByTransactionDateDescIdDesc(userId);
		assertThat(body).contains("日付の新しい順に、1,000 件を表示しています。");
	}
}
