package io.github.yutaro081.householdbudget.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.repository.UserRepository;

/**
 * 画面の応答に Cache-Control: no-store が付くことの確認（設計書 7.10：Spring Security が標準で付けるヘッダーを、そのまま使い、外さない）。
 * ログアウトのあとに「戻る」で前の画面が見えないようにする対策（Phase 8 の F2、Q8-25）は、JavaScript が主役だが、
 * サーバーが no-store を返し続けていることが前提になる（設定を外したときに気づくためのテスト。4.13 の案 (b)）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CacheControlHeaderTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private RequestPostProcessor loggedIn() {
		Long id = userRepository.save(new User("cachehdr1", "dummy-hash-for-test")).getId();
		return user(new LoginUser(id, "cachehdr1", "x"));
	}

	private static void assertNoStore(MockHttpServletResponse response, String what) {
		assertThat(response.getHeader("Cache-Control")).as("%s の Cache-Control", what).contains("no-store");
	}

	/** ログイン中の画面（一覧、入力、設定、比較）と、404 の画面 */
	@ParameterizedTest(name = "ログイン中の {0} に no-store が付く")
	@ValueSource(strings = { "/", "/transactions", "/transactions/new", "/categories", "/vendors", "/payment-methods", "/budgets",
			"/budgets?year=2026&season=winter", "/comparison", "/comparison?year=2026&season=autumn", "/transactions/999999999" })
	void loggedInScreensAreNotStored(String path) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get(path).with(loggedIn())).andReturn().getResponse();

		assertNoStore(response, path);
	}

	/** ログインなしで開く画面 */
	@ParameterizedTest(name = "ログイン前の {0} に no-store が付く")
	@ValueSource(strings = { "/login", "/register", "/login?error", "/login?logout" })
	void publicScreensAreNotStored(String path) throws Exception {
		assertNoStore(mockMvc.perform(get(path)).andReturn().getResponse(), path);
	}

	/** 未ログインでログイン画面へ移動する応答、ログアウトの応答にも付く */
	@Test
	void redirectsAreNotStoredEither() throws Exception {
		assertNoStore(mockMvc.perform(get("/transactions")).andReturn().getResponse(), "未ログインの移動");
		// トップ（GET /）。ログイン後の最初の画面なので、未ログインで開いたときの移動の応答にも付くことを直接確かめる（issue 010 の Codex のレビュー）
		assertNoStore(mockMvc.perform(get("/")).andReturn().getResponse(), "未ログインでトップ（/）を開いたときの移動");
		assertNoStore(mockMvc.perform(post("/logout").with(csrf()).with(loggedIn())).andReturn().getResponse(), "ログアウト");
	}

}
