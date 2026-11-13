package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
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
 * ヘッダーの数のテスト（issue 017 の 2 回目の見直し）。
 * ログイン後の画面には、メニュー付きのヘッダーが 1 つだけ（ロゴも 1 つだけ）。
 * ログイン前の画面（ログイン、登録）には、ロゴだけのヘッダーが 1 つだけ。
 * 「最初の header だけ」を調べるテストでは、2 つ目が出ても気づけなかったので、ページ全体で数える。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeaderCountTest {

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
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;

	private Long userId;
	private Long transactionId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("hdrcount1", "dummy-hash-for-test")).getId();
		Category c = categoryRepository.save(new Category(userId, "食費"));
		Vendor v = vendorRepository.save(new Vendor(userId, "コンビニ"));
		PaymentMethod p = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transactionId = transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 19), 100, c, null, v, p)).getId();
		entityManager.flush();
		entityManager.clear();
	}

	private static int count(String text, String regex) {
		Matcher m = Pattern.compile(regex).matcher(text);
		int n = 0;
		while (m.find()) {
			n++;
		}
		return n;
	}

	private String loggedIn(String path) throws Exception {
		return mockMvc.perform(get(path).with(user(new LoginUser(userId, "hdrcount1", "x")))).andReturn().getResponse().getContentAsString();
	}

	@Test
	void screensAfterLoginHaveExactlyOneHeaderWithTheMenuAndOneLogo() throws Exception {
		List<String> paths = List.of("/", "/transactions", "/transactions/new", "/transactions/" + transactionId + "/edit", "/categories", "/vendors",
				"/payment-methods", "/budgets", "/comparison");
		for (String path : paths) {
			String body = loggedIn(path);

			assertThat(count(body, "<header[ >]")).as(path + "：header は 1 つ").isEqualTo(1);
			assertThat(count(body, "class=\"brand\"")).as(path + "：ロゴは 1 つ").isEqualTo(1);
			assertThat(count(body, "<nav class=\"menu")).as(path + "：メニューは 1 つ").isEqualTo(1);
			assertThat(count(body, "action=\"/logout\"")).as(path + "：ログアウトは 1 つ").isEqualTo(1);
			assertThat(count(body, "class=\"app-header-inner\"")).as(path).isEqualTo(1);
		}
	}

	/** 404 の画面は、ログイン前でも出せる作り（設計書 2.5、7.7）なので、ヘッダーを出さない。2 つ出ないことだけ確かめる */
	@Test
	void theNotFoundScreenHasNoMoreThanOneHeader() throws Exception {
		String body = loggedIn("/transactions/999999999/edit");   // 存在しない取引の編集（詳細の画面は、issue 025 でなくした）

		assertThat(body).contains("ページが見つかりません");
		assertThat(count(body, "<header[ >]")).isLessThanOrEqualTo(1);
		assertThat(count(body, "class=\"brand\"")).isLessThanOrEqualTo(1);
		assertThat(count(body, "<nav class=\"menu")).isLessThanOrEqualTo(1);
	}

	@Test
	void screensBeforeLoginHaveExactlyOneHeaderWithOnlyTheLogo() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String body = mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();

			assertThat(count(body, "<header[ >]")).as(path + "：header は 1 つ").isEqualTo(1);
			assertThat(count(body, "class=\"brand\"")).as(path + "：ロゴは 1 つ").isEqualTo(1);
			assertThat(count(body, "class=\"app-header-inner\"")).as(path).isEqualTo(1);
			assertThat(body).as(path).doesNotContain("<nav", "class=\"menu", "action=\"/logout\"");
		}
	}

}
