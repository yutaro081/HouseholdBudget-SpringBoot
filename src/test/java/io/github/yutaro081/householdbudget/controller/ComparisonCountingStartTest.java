package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
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
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * 予実比較の着地見込みが、自分の最初の取引の日から数えること（要件定義書 4.4、設計書 5.13、issue 020）。
 * 「今日」は 2026-11-11（秋の 72 日目）に固定する。10/31 に記録を始め、実績 20,000 円、予算 175,000 円 → 約 54,700 円。
 * （季節の初日から数えると約 31,900 円。）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComparisonCountingStartTest {

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2026, 11, 11).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

	@Autowired
	private MockMvc mockMvc;
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

	@Test
	void theLandingLineCountsFromTheFirstTransactionDay() throws Exception {
		Long id = userRepository.save(new User("cmpstart1", "dummy-hash-for-test")).getId();
		Vendor vendor = vendorRepository.save(new Vendor(id, "取引先"));
		PaymentMethod method = paymentMethodRepository.save(new PaymentMethod(id, "支払方法"));
		Category food = categoryRepository.save(new Category(id, "食費"));
		budgetRepository.save(new Budget(id, food, 2026, Season.AUTUMN, 175_000));
		transactionRepository.save(new Transaction(id, LocalDate.of(2026, 10, 31), 20_000, food, null, vendor, method));
		entityManager.flush();
		entityManager.clear();

		String body = mockMvc.perform(get("/comparison").with(user(new LoginUser(id, "cmpstart1", "x"))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(body).contains("約 54,700円", "予算 175,000円", "10/31 に記録を始めたので、その日から数えています。");
	}
}
