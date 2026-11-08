package io.github.yutaro081.householdbudget.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.form.RegisterForm;
import io.github.yutaro081.householdbudget.service.RegistrationService;

/**
 * ログイン必須・ログイン・ログアウト・CSRF の動きのテスト（設計書 7.3、7.6、7.10、2.6）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない、Q3-8）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityBehaviorTest {

	/** テスト用のダミーの値（実在するアカウントのパスワードではない） */
	private static final String DUMMY_PASSWORD = "dummy-Pass-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RegistrationService registrationService;

	private void registerUser(String username) {
		RegisterForm form = new RegisterForm();
		form.setUsername(username);
		form.setPassword(DUMMY_PASSWORD);
		registrationService.register(form);
	}

	// ---- ログイン必須（7.3） ----

	@ParameterizedTest(name = "未ログインで {0} を開くと、ログイン画面へ移動する")
	@ValueSource(strings = { "/", "/transactions", "/transactions/1", "/categories", "/vendors",
			"/payment-methods", "/budgets", "/comparison", "/nothing" })
	void notLoggedInIsRedirectedToLogin(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				// 移動先は /login（テストでは相対の /login、本物のサーバーでは http://…/login の形で返る）
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "未ログインでも {0} は開ける")
	@ValueSource(strings = { "/login", "/register", "/css/style.css", "/css/bootstrap.min.css",
			"/js/back-reload.js", "/fonts/ZenKakuGothicNew-Regular.woff2", "/fonts/ZenKakuGothicNew-Bold.woff2",
			"/fonts/OFL.txt" })
	void publicUrlsAreOpen(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isOk());
	}

	// ---- 「戻る」で覚えていた画面を開き直す JavaScript（7.10、Q8-25・Q8-26） ----

	private static final String BACK_RELOAD_TAG = "src=\"/js/back-reload.js\"";

	@Test
	void backReloadScriptReopensTheSameUrlWithGetWhenThePageComesFromTheBackForwardCache() throws Exception {
		String js = mockMvc.perform(get("/js/back-reload.js")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		assertThat(js).contains("pageshow", "event.persisted", "window.location.replace(window.location.href)");
		assertThat(js).as("再送信の確認が出るので reload() は使わない").doesNotContain("window.location.reload");
	}

	@ParameterizedTest(name = "未ログインの {0} の画面が script タグで JavaScript を読む")
	@ValueSource(strings = { "/login", "/register" })
	void screensWithoutLoginLoadTheBackReloadScript(String path) throws Exception {
		assertThat(mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
				.contains(BACK_RELOAD_TAG);
	}

	// ---- CSRF（7.6）：トークンなしの POST は 403 ----

	@ParameterizedTest(name = "トークンなしの POST {0} は 403")
	@ValueSource(strings = { "/register", "/login", "/logout" })
	void postWithoutCsrfTokenIsForbidden(String path) throws Exception {
		mockMvc.perform(post(path).param("username", "x").param("password", "y"))
				.andExpect(status().isForbidden());
	}

	@Test
	void postWithoutCsrfTokenIsForbiddenEvenWhenLoggedIn() throws Exception {
		mockMvc.perform(post("/logout").with(user(new LoginUser(1L, "someone", "x"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void loginAndRegisterFormsHaveCsrfToken() throws Exception {
		mockMvc.perform(get("/login")).andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
		mockMvc.perform(get("/register")).andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
	}

	// ---- アカウント登録（2.6） ----

	@Test
	void registerSuccessRedirectsToLoginWithMessage() throws Exception {
		mockMvc.perform(post("/register").with(csrf()).param("username", "sectest1").param("password", DUMMY_PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"))
				.andExpect(flash().attribute("successMessageKey", "success.accountRegistered"));
	}

	@Test
	void registerErrorRedisplaysWithUsernameAndWithoutPassword() throws Exception {
		MvcResult result = mockMvc.perform(post("/register").with(csrf())
				.param("username", "あいう").param("password", "short"))
				.andExpect(status().isOk())
				.andExpect(view().name("register"))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("value=\"あいう\"");                       // ユーザー名は残る
		assertThat(body).doesNotContain("short");                           // パスワードは残らない
		assertThat(body).contains("ユーザー名は半角英数字で入力してください");
		assertThat(body).contains("パスワードは8文字以上30文字以内で入力してください");
	}

	@Test
	void registerDuplicateShowsMessageNextToUsername() throws Exception {
		registerUser("sectest2");

		MvcResult result = mockMvc.perform(post("/register").with(csrf())
				.param("username", "SECTEST2").param("password", DUMMY_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(view().name("register"))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("このユーザー名はすでに使われています");
		assertThat(body).contains("value=\"SECTEST2\"");
		assertThat(body).doesNotContain(DUMMY_PASSWORD);
	}

	/** 登録の入力エラー：ユーザー名・パスワードの各エラーが、画面に文として出る（Phase 8 の 4.14。4.7 の表）。一部の項目だけがエラーのこともある */
	static Stream<Arguments> registerErrors() {
		return Stream.of(
				Arguments.of("", "abcd1234", "ユーザー名を入力してください"),
				Arguments.of("   ", "abcd1234", "ユーザー名を入力してください"),
				Arguments.of("abc-1", "abcd1234", "ユーザー名は半角英数字で入力してください"),
				Arguments.of("abcdefghijk", "abcd1234", "ユーザー名は10文字以内で入力してください"),
				Arguments.of("abcdefghij", "", "パスワードを入力してください"),
				Arguments.of("abcdefghij", "abc12", "パスワードは8文字以上30文字以内で入力してください"),
				Arguments.of("abcdefghij", "a".repeat(31), "パスワードは8文字以上30文字以内で入力してください"),
				Arguments.of("abcdefghij", "abc 1234", "パスワードは半角の英数字と記号で入力してください"),
				Arguments.of("abcdefghij", "あいうえおかきく", "パスワードは半角の英数字と記号で入力してください"));
	}

	@ParameterizedTest(name = "登録の入力エラー：ユーザー名 [{0}] → 「{2}」")
	@MethodSource("registerErrors")
	void registerErrorShowsTheMessageOnTheScreenAndKeepsOnlyTheUsername(String username, String password, String message)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/register").with(csrf())
				.param("username", username).param("password", password))
				.andExpect(status().isOk())
				.andExpect(view().name("register"))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(message);
		assertThat(body).contains("class=\"field-error\"");
		if (!username.isBlank()) {
			assertThat(body).contains("value=\"" + username + "\"");        // ユーザー名は残る
		}
		if (!password.isEmpty()) {
			assertThat(body).doesNotContain(password);                         // パスワードは残らない
		}
	}

	// ---- エラー画面の文言（設計書 7.7。Phase 8 の 4.14）----

	private static final String CLIENT_ERROR_MESSAGE = "操作を完了できませんでした。もう一度やり直してください";

	/**
	 * 403（CSRF のトークンなしなど）と 405（許されない方法）の画面。MockMvc は /error への転送をしないので、
	 * Spring Boot が使う /error に、エラーの状態コードを付けて直接開き（ブラウザと同じ Accept: text/html。付けないと JSON が返る）、画面の名前と文言を確かめる（状態コードは応答に付く）。画面の名前は MockMvc からは分からないので、文言で確かめる（403.html と 4xx.html は同じ文言）。
	 */
	@ParameterizedTest(name = "状態コード {0} の画面（{1}）")
	@CsvSource({ "403, error/403", "405, error/4xx" })
	void errorScreenHasTheMessageAndTheStatus(int statusCode, String templateName) throws Exception {
		MvcResult result = mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML).requestAttr("jakarta.servlet.error.status_code", statusCode))
				.andExpect(status().is(statusCode))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(CLIENT_ERROR_MESSAGE);
		assertThat(body).as("詳しい中身を出さない").doesNotContain("Whitelabel", "Exception", "trace");
	}

	/** 実際の流れ：削除の URL を GET で開くと 405（削除は POST だけ） */
	@ParameterizedTest(name = "GET {0} は 405")
	@ValueSource(strings = { "/categories/1/delete", "/vendors/1/delete", "/payment-methods/1/delete", "/transactions/1/delete" })
	void deleteByGetIsMethodNotAllowed(String path) throws Exception {
		mockMvc.perform(get(path).with(user(new LoginUser(1L, "x", "x"))))
				.andExpect(status().isMethodNotAllowed());
	}

	// ---- ログイン・ログアウト（2.6、U5、B8、7.10） ----

	/**
	 * ログイン後にセッションIDが新しくなること（C1、7.10）は、このテスト（MockMvc）では確かめられない
	 * （MockMvc は本物のサーバーと違い、セッションIDが変わらなかった）。ブラウザの開発者ツールで確かめた
	 * （Phase 3 の手順書 4.13 の(7)）。ここでは、ログイン成功後の移動先だけを確かめる。
	 */
	@Test
	void loginSuccessGoesToTop() throws Exception {
		registerUser("sectest3");

		mockMvc.perform(post("/login").with(csrf())
				.param("username", "sectest3").param("password", DUMMY_PASSWORD))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void loginSuccessIgnoresTheUrlThatWasRequestedBefore() throws Exception {
		registerUser("sectest4");

		// 先に保護された URL を開こうとして、ログイン画面へ移動した
		MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/categories"))
				.andExpect(status().is3xxRedirection())
				.andReturn().getRequest().getSession();

		mockMvc.perform(post("/login").with(csrf()).session(session)
				.param("username", "sectest4").param("password", DUMMY_PASSWORD))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void loginFailureIsTheSameForWrongPasswordAndUnknownUser() throws Exception {
		registerUser("sectest5");

		// パスワードが違う
		MvcResult wrongPassword = mockMvc.perform(post("/login").with(csrf())
				.param("username", "sectest5").param("password", "wrong-Pass-9"))
				.andExpect(redirectedUrl("/login?error"))
				.andReturn();
		// ユーザー名がない
		MvcResult unknownUser = mockMvc.perform(post("/login").with(csrf())
				.param("username", "nobody999").param("password", DUMMY_PASSWORD))
				.andExpect(redirectedUrl("/login?error"))
				.andReturn();

		// 移動先の画面も同じメッセージ。入力したユーザー名は残り、パスワードは出ない
		for (MvcResult failed : new MvcResult[] { wrongPassword, unknownUser }) {
			MockHttpSession session = (MockHttpSession) failed.getRequest().getSession();
			String body = mockMvc.perform(get("/login?error").session(session))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			assertThat(body).contains("ユーザー名またはパスワードが違います");
			assertThat(body).doesNotContain("wrong-Pass-9").doesNotContain(DUMMY_PASSWORD);
		}
		String body = mockMvc.perform(get("/login?error").session((MockHttpSession) unknownUser.getRequest().getSession()))
				.andReturn().getResponse().getContentAsString();
		// 1回見たら、ユーザー名は残らない（次の1回の表示に限る）
		assertThat(body).doesNotContain("value=\"nobody999\"");
	}

	@Test
	void loginFailureKeepsUsernameOnTheNextPage() throws Exception {
		MvcResult failed = mockMvc.perform(post("/login").with(csrf())
				.param("username", "nobody999").param("password", DUMMY_PASSWORD))
				.andReturn();

		String body = mockMvc.perform(get("/login?error").session((MockHttpSession) failed.getRequest().getSession()))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("value=\"nobody999\"");
	}

	@Test
	void usernameIsNotCaseSensitiveAtLogin() throws Exception {
		registerUser("sectest6");

		mockMvc.perform(post("/login").with(csrf())
				.param("username", "SECTEST6").param("password", DUMMY_PASSWORD))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void loggedInUserSeesTheFrameWithUsernameAndLogoutForm() throws Exception {
		String body = mockMvc.perform(get("/transactions").with(user(new LoginUser(1L, "frametest", "x"))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("frametest");
		assertThat(body).contains("action=\"/logout\"");
		assertThat(body).contains("name=\"_csrf\"");
	}

	@Test
	void logoutGoesToLoginWithMessage() throws Exception {
		mockMvc.perform(post("/logout").with(csrf()).with(user(new LoginUser(1L, "someone", "x"))))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?logout"));

		mockMvc.perform(get("/login?logout"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("ログアウトしました")));
	}

}
