package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * 科目・取引先・支払方法の追加の画面で、1人あたり256個の上限（R14）のメッセージが出るかのテスト（設計書 5.4、6.2、6.9）。
 * 255個あるときは追加でき（256個目）、256個あるときは追加できず、名前の項目の横に「科目は256個までです」などが出て、入力値が残る。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasterLimitControllerTest {

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
	private EntityManager entityManager;

	private static final int LIMIT = 256;

	private record Login(Long userId, RequestPostProcessor principal) {
	}

	private Login login(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Login(id, user(new LoginUser(id, username, "x")));
	}

	private long categoriesOf(Long userId) {
		entityManager.flush();
		return categoryRepository.countByUserId(userId);
	}

	private long vendorsOf(Long userId) {
		entityManager.flush();
		return vendorRepository.countByUserId(userId);
	}

	private long paymentMethodsOf(Long userId) {
		entityManager.flush();
		return paymentMethodRepository.countByUserId(userId);
	}

	private void fillCategories(Long userId, int count) {
		List<Category> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new Category(userId, "c" + i));
		}
		categoryRepository.saveAll(list);
	}

	private void fillVendors(Long userId, int count) {
		List<Vendor> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new Vendor(userId, "v" + i));
		}
		vendorRepository.saveAll(list);
	}

	private void fillPaymentMethods(Long userId, int count) {
		List<PaymentMethod> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new PaymentMethod(userId, "p" + i));
		}
		paymentMethodRepository.saveAll(list);
	}

	// ---- 科目 ----

	@Test
	void the256thCategoryCanBeAddedAndThe257thShowsTheMessageAndKeepsTheInput() throws Exception {
		Login u = login("limc1");
		fillCategories(u.userId(), LIMIT - 1);

		mockMvc.perform(post("/categories").with(u.principal()).with(csrf()).param("name", "256個目"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("successMessageKey", "success.added"));
		assertThat(categoriesOf(u.userId())).isEqualTo(LIMIT);

		String body = mockMvc.perform(post("/categories").with(u.principal()).with(csrf()).param("name", "257個目"))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/list"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("科目は256個までです");
		assertThat(body).contains("value=\"257個目\"");                       // 入力値が残る
		assertThat(categoriesOf(u.userId())).isEqualTo(LIMIT);                // 追加されていない
	}

	/** 上限に達していても、変更・削除はできる。削除すると、また追加できる */
	@Test
	void categoryCanBeDeletedAtTheLimitAndThenAddedAgain() throws Exception {
		Login u = login("limc2");
		fillCategories(u.userId(), LIMIT);
		entityManager.flush();
		Long first = categoryRepository.findByUserIdOrderByIdAsc(u.userId()).get(0).getId();

		mockMvc.perform(post("/categories/" + first + "/delete").with(u.principal()).with(csrf()))
				.andExpect(redirectedUrl("/categories"));
		mockMvc.perform(post("/categories").with(u.principal()).with(csrf()).param("name", "また追加"))
				.andExpect(redirectedUrl("/categories"));

		assertThat(categoriesOf(u.userId())).isEqualTo(LIMIT);
	}

	// ---- 取引先 ----

	@Test
	void the256thVendorCanBeAddedAndThe257thShowsTheMessageAndKeepsTheInput() throws Exception {
		Login u = login("limc3");
		fillVendors(u.userId(), LIMIT - 1);

		mockMvc.perform(post("/vendors").with(u.principal()).with(csrf()).param("name", "256個目"))
				.andExpect(redirectedUrl("/vendors"));
		assertThat(vendorsOf(u.userId())).isEqualTo(LIMIT);

		String body = mockMvc.perform(post("/vendors").with(u.principal()).with(csrf()).param("name", "257個目"))
				.andExpect(status().isOk())
				.andExpect(view().name("vendors/list"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("取引先は256個までです");
		assertThat(body).contains("value=\"257個目\"");
		assertThat(vendorsOf(u.userId())).isEqualTo(LIMIT);
	}

	// ---- 支払方法 ----

	@Test
	void the256thPaymentMethodCanBeAddedAndThe257thShowsTheMessageAndKeepsTheInput() throws Exception {
		Login u = login("limc4");
		fillPaymentMethods(u.userId(), LIMIT - 1);

		mockMvc.perform(post("/payment-methods").with(u.principal()).with(csrf()).param("name", "256個目"))
				.andExpect(redirectedUrl("/payment-methods"));
		assertThat(paymentMethodsOf(u.userId())).isEqualTo(LIMIT);

		String body = mockMvc.perform(post("/payment-methods").with(u.principal()).with(csrf()).param("name", "257個目"))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/list"))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).contains("支払方法は256個までです");
		assertThat(body).contains("value=\"257個目\"");
		assertThat(paymentMethodsOf(u.userId())).isEqualTo(LIMIT);
	}

	/** 上限のメッセージは、自分の分が256個のときだけ。ほかのユーザーが256個でも、自分は追加できる */
	@Test
	void anotherUsersFullListDoesNotBlockMe() throws Exception {
		Login full = login("limc5");
		Login me = login("limc6");
		fillCategories(full.userId(), LIMIT);
		fillVendors(full.userId(), LIMIT);
		fillPaymentMethods(full.userId(), LIMIT);

		mockMvc.perform(post("/categories").with(me.principal()).with(csrf()).param("name", "自分の科目")).andExpect(redirectedUrl("/categories"));
		mockMvc.perform(post("/vendors").with(me.principal()).with(csrf()).param("name", "自分の取引先")).andExpect(redirectedUrl("/vendors"));
		mockMvc.perform(post("/payment-methods").with(me.principal()).with(csrf()).param("name", "自分の支払")).andExpect(redirectedUrl("/payment-methods"));
	}

}
