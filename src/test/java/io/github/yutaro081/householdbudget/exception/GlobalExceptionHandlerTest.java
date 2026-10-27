package io.github.yutaro081.householdbudget.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.testsupport.TestErrorEndpoints;

/**
 * GlobalExceptionHandler のテスト（設計書 7.7、7.8、S4）。
 * まだ NotFoundException を投げる本物の画面がないので、テスト用の URL（/test-handler/…）を用意して確かめる。
 * このテスト用の URL（testsupport.TestErrorEndpoints）は、このテストが読み込んだときだけ有効（他のテストや、本物のアプリには入らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestErrorEndpoints.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	private static org.springframework.test.web.servlet.request.RequestPostProcessor loggedIn() {
		return user(new LoginUser(1L, "handlertest", "x"));
	}

	@Test
	void notFoundExceptionBecomes404Screen() throws Exception {
		MvcResult result = mockMvc.perform(get("/test-handler/not-found").with(loggedIn()))
				.andExpect(status().isNotFound())          // 状態コードも 404
				.andExpect(view().name("error/404"))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("ページが見つかりません");
		// 詳しい中身を出さない
		assertThat(body.toLowerCase()).doesNotContain("exception").doesNotContain("trace").doesNotContain("whitelabel");
	}

	@ParameterizedTest(name = "ID が数字でない URL [{0}] は 404")
	@ValueSource(strings = { "abc", "1a", "-", "1.5", "99999999999999999999" })
	void nonNumericIdBecomes404(String id) throws Exception {
		MvcResult result = mockMvc.perform(get("/test-handler/ids/" + id).with(loggedIn()))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"))
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("ページが見つかりません");
	}

	@Test
	void numericIdIsNotAffected() throws Exception {
		mockMvc.perform(get("/test-handler/ids/5").with(loggedIn()))
				.andExpect(status().isOk());
	}

	/** 存在しない（NotFoundException）と、数字でない ID は、画面も状態コードも同じ（7.8） */
	@Test
	void notFoundAndNonNumericLookTheSame() throws Exception {
		MvcResult notFound = mockMvc.perform(get("/test-handler/not-found").with(loggedIn())).andReturn();
		MvcResult nonNumeric = mockMvc.perform(get("/test-handler/ids/abc").with(loggedIn())).andReturn();

		assertThat(nonNumeric.getResponse().getStatus()).isEqualTo(notFound.getResponse().getStatus());
		assertThat(nonNumeric.getResponse().getContentAsString()).isEqualTo(notFound.getResponse().getContentAsString());
	}

}
