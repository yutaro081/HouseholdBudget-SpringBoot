package io.github.yutaro081.householdbudget.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;

/**
 * ログインが切れたあとの動き（issue 030。設計書 7.10、S10）。
 * <p>
 * ログインが切れる（30 分操作がない、アプリを再起動した）と、サーバーのセッションが消える。ブラウザは、もうない
 * セッションID（古い JSESSIONID）を送ってくる。MockMvc では、本物の Cookie とセッションは再現できないので、
 * 「古いセッションID で来た」形（requestedSessionId があり、それが有効でない）を作って確かめる。
 * <ul>
 * <li>古いセッションID の GET・POST は、/login?expired へ移る。POST は保存されない</li>
 * <li>セッションID を持たない、または有効なセッションID のトークンなし・違う POST は、今までどおり 403</li>
 * <li>/login?expired には、案内の形（alert-info と info のアイコン）で「ログインが切れました。もう一度ログインしてください。」</li>
 * </ul>
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionExpiredTest {

	private static final String EXPIRED_MESSAGE = "ログインが切れました。もう一度ログインしてください。";

	/** info のアイコンの形の目印（IconsFragmentTest・NoticeMarkupTest と同じ） */
	private static final String INFO = "M12 7.5h.01";

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

	/** 古いセッションID で来た形（サーバーにもうないセッションID。再起動や 30 分の放置のあと） */
	private static RequestPostProcessor oldSession() {
		return request -> {
			request.setRequestedSessionId("old-session-id");
			request.setRequestedSessionIdValid(false);
			return request;
		};
	}

	/** 有効なセッションID で来た形（別のタブでログインし直したあとの、古いタブなど。設計書 7.10 の限界 (2)） */
	private static RequestPostProcessor currentSession() {
		return request -> {
			request.setRequestedSessionId("current-session-id");
			request.setRequestedSessionIdValid(true);
			return request;
		};
	}

	private static String redirectedUrl(MvcResult result) {
		return result.getResponse().getRedirectedUrl();
	}

	// ---- 古いセッションID：ログイン画面へ（/login?expired） ----

	@ParameterizedTest(name = "古いセッションID で GET {0} を開くと、/login?expired へ移る")
	@ValueSource(strings = { "/", "/transactions", "/transactions/new", "/budgets", "/comparison", "/categories" })
	void getWithOldSessionGoesToLoginWithExpired(String path) throws Exception {
		MvcResult result = mockMvc.perform(get(path).with(oldSession()))
				.andExpect(status().is3xxRedirection()).andReturn();

		// 移動先は /login?expired（テストでは相対、本物のサーバーでは http://…/login?expired の形で返ることがある）
		assertThat(redirectedUrl(result)).endsWith("/login?expired");
	}

	@ParameterizedTest(name = "古いセッションID で POST {0} を送ると、403 ではなく /login?expired へ移る")
	@ValueSource(strings = { "/transactions/new", "/transactions/1/edit", "/transactions/1/delete", "/categories",
			"/vendors", "/payment-methods", "/budgets", "/login", "/logout", "/register" })
	void postWithOldSessionGoesToLoginWithExpired(String path) throws Exception {
		// 前に開いていた画面のフォームには、古いトークンが入っている（サーバーには、もう残っていない）
		MvcResult result = mockMvc.perform(post(path).with(oldSession()).param("_csrf", "old-token")
				.param("username", "x").param("password", "y"))
				.andExpect(status().is3xxRedirection()).andReturn();

		assertThat(redirectedUrl(result)).endsWith("/login?expired");
	}

	/** 取引の登録：古いセッションID の POST は保存されない（ログイン中の形にしても、CSRF の確認で止まる） */
	@Test
	void transactionPostWithOldSessionSavesNothing() throws Exception {
		Long userId = userRepository.save(new User("expired1", "dummy-hash-for-test")).getId();
		Category category = categoryRepository.save(new Category(userId, "食費"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));

		MvcResult result = mockMvc.perform(post("/transactions/new").with(oldSession())
				.with(user(new LoginUser(userId, "expired1", "x"))).param("_csrf", "old-token")
				.param("transactionDate", "2026-10-20").param("amount", "1200")
				.param("categoryId", String.valueOf(category.getId()))
				.param("vendorId", String.valueOf(vendor.getId()))
				.param("paymentMethodId", String.valueOf(payment.getId())))
				.andExpect(status().is3xxRedirection()).andReturn();

		assertThat(redirectedUrl(result)).endsWith("/login?expired");
		assertThat(transactionRepository.findAll()).noneMatch(t -> t.getUserId().equals(userId));
	}

	/** 科目の追加：古いセッションID の POST は保存されない */
	@Test
	void categoryPostWithOldSessionSavesNothing() throws Exception {
		Long userId = userRepository.save(new User("expired2", "dummy-hash-for-test")).getId();

		MvcResult result = mockMvc.perform(post("/categories").with(oldSession())
				.with(user(new LoginUser(userId, "expired2", "x"))).param("_csrf", "old-token").param("name", "食費"))
				.andExpect(status().is3xxRedirection()).andReturn();

		assertThat(redirectedUrl(result)).endsWith("/login?expired");
		assertThat(categoryRepository.findAll()).noneMatch(c -> c.getUserId().equals(userId));
	}

	// ---- 古いセッションID でないとき：今までどおり ----

	@Test
	void getWithoutSessionStillGoesToPlainLogin() throws Exception {
		MvcResult result = mockMvc.perform(get("/transactions"))
				.andExpect(status().is3xxRedirection()).andReturn();

		assertThat(redirectedUrl(result)).endsWith("/login").doesNotContain("expired");
	}

	@ParameterizedTest(name = "セッションID を持たないトークンなしの POST {0} は、今までどおり 403")
	@ValueSource(strings = { "/transactions/new", "/categories", "/login", "/logout", "/register" })
	void postWithoutSessionAndTokenIsStillForbidden(String path) throws Exception {
		mockMvc.perform(post(path).param("username", "x").param("password", "y"))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest(name = "有効なセッションID で、トークンが違う POST {0} は、今までどおり 403")
	@ValueSource(strings = { "/transactions/new", "/categories", "/logout" })
	void postWithCurrentSessionAndWrongTokenIsStillForbidden(String path) throws Exception {
		mockMvc.perform(post(path).with(currentSession()).with(user(new LoginUser(1L, "someone", "x")))
				.param("_csrf", "wrong-token"))
				.andExpect(status().isForbidden());
	}

	// ---- ログイン画面の文（案内の形） ----

	@Test
	void loginPageWithExpiredShowsTheMessageAsAnInfoNotice() throws Exception {
		String body = mockMvc.perform(get("/login?expired"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		int start = body.indexOf("class=\"message info alert alert-info\"");
		assertThat(start).as("案内の形（alert-info）のお知らせがある").isGreaterThanOrEqualTo(0);
		String notice = body.substring(start, body.indexOf("</div>", start));
		assertThat(notice).contains("class=\"alert-icon\"", INFO, "aria-hidden=\"true\"", EXPIRED_MESSAGE);
		assertThat(notice.indexOf("alert-icon")).as("アイコンは文の左").isLessThan(notice.indexOf(EXPIRED_MESSAGE));
		assertThat(body).as("成功・失敗の形では出さない").doesNotContain("alert-success", "alert-danger");
	}

	@ParameterizedTest(name = "{0} には「ログインが切れました」を出さない")
	@ValueSource(strings = { "/login", "/login?error", "/login?logout" })
	void otherLoginPagesDoNotShowTheExpiredMessage(String path) throws Exception {
		String body = mockMvc.perform(get(path))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("ログインが切れました", "alert-info");
	}

}
