package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.yutaro081.householdbudget.security.LoginUser;

/**
 * エラーの画面 4 つ（404・403・その他の 400 番台・500 番台）の形と文（issue 030 の段階 2。設計書 2.5、7.7）。
 * 4 つとも同じ形：ロゴだけのヘッダー、1 行の見出し（h1）、失敗のお知らせ（alert-danger と alert のアイコン）、主ボタン「トップへ」（/ へ）。
 * 「取引一覧へ戻る」のリンクはない。ユーザー名・メニュー・ログアウトも出さない（ログイン前でも出せる画面なので）。
 * MockMvc は /error への転送をしないので、Spring Boot が使う /error に、エラーの状態コードを付けて開く
 * （ブラウザと同じ Accept: text/html。付けないと JSON が返る）。画面のテンプレートは、状態コードで決まる。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorScreensTest {

	/** alert のアイコンの形の目印（NoticeMarkupTest と同じ） */
	private static final String ALERT = "M12 16.5h.01";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MessageSource messageSource;

	private String screen(int statusCode) throws Exception {
		return mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
				.requestAttr("jakarta.servlet.error.status_code", statusCode))
				.andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString();
	}

	private static final String CASES = "404|ページが見つかりません|開こうとしたページはありません。トップから開き直してください。\n"
			+ "403|操作を完了できませんでした|別の画面でログインし直したか、画面が古くなったため、受け付けられませんでした。内容は保存されていません。画面を開き直して、もう一度操作してください。\n"
			+ "405|操作を完了できませんでした|受け付けられない操作でした。内容は保存されていません。画面を開き直して、もう一度操作してください。\n"
			+ "400|操作を完了できませんでした|受け付けられない操作でした。内容は保存されていません。画面を開き直して、もう一度操作してください。\n"
			+ "500|エラーが発生しました|しばらくしてから、もう一度お試しください。\n"
			+ "503|エラーが発生しました|しばらくしてから、もう一度お試しください。";

	// ---- 見出し・お知らせの文（設計書 7.7 の表のとおり） ----

	@ParameterizedTest(name = "状態コード {0}：見出し「{1}」と、お知らせの文")
	@CsvSource(delimiter = '|', textBlock = CASES)
	void headingAndNoticeTextAreAsInTheDesign(int statusCode, String heading, String notice) throws Exception {
		String body = screen(statusCode);

		assertThat(body).containsPattern("<h1[^>]*>" + heading + "</h1>");
		assertThat(body.split("<h1", -1).length - 1).as("見出しは 1 つ").isEqualTo(1);
		assertThat(body).contains(notice);
		assertThat(body).as("詳しい中身を出さない").doesNotContain("Whitelabel", "Exception", "trace");
	}

	// ---- 失敗のお知らせ（alert-danger と alert のアイコン） ----

	@ParameterizedTest(name = "状態コード {0}：失敗の形のお知らせ（alert-danger と alert のアイコン）")
	@ValueSource(ints = { 404, 403, 405, 500 })
	void noticeIsTheFailureForm(int statusCode) throws Exception {
		String body = screen(statusCode);

		int start = body.indexOf("alert alert-danger");
		assertThat(start).as("alert-danger のお知らせがある").isGreaterThanOrEqualTo(0);
		String notice = body.substring(start, body.indexOf("</div>", start));
		assertThat(notice).contains("class=\"alert-icon\"", ALERT, "aria-hidden=\"true\"");
		assertThat(notice.indexOf("alert-icon")).as("アイコンは文の左").isLessThan(notice.indexOf("</svg>"));
		assertThat(body).as("成功・案内の形ではない").doesNotContain("alert-success", "alert-info", "alert-warning");
	}

	// ---- 主ボタン「トップへ」（ある）と、「取引一覧へ戻る」（ない）は、組 ----

	@ParameterizedTest(name = "状態コード {0}：主ボタン「トップへ」が / へ向かう")
	@ValueSource(ints = { 404, 403, 405, 500 })
	void hasThePrimaryButtonToTop(int statusCode) throws Exception {
		assertThat(screen(statusCode)).containsPattern("<a [^>]*class=\"btn btn-primary\"[^>]*href=\"/\"[^>]*>トップへ</a>");
	}

	@ParameterizedTest(name = "状態コード {0}：「取引一覧へ戻る」のリンクがない")
	@ValueSource(ints = { 404, 403, 405, 500 })
	void hasNoLinkBackToTransactions(int statusCode) throws Exception {
		assertThat(screen(statusCode)).doesNotContain("取引一覧へ戻る").doesNotContain("href=\"/transactions\"");
	}

	// ---- ロゴだけのヘッダー（ある）と、ユーザー名・メニュー・ログアウト（ない）は、組 ----

	@ParameterizedTest(name = "状態コード {0}：ロゴだけのヘッダーが 1 つある")
	@ValueSource(ints = { 404, 403, 405, 500 })
	void hasTheLogoOnlyHeader(int statusCode) throws Exception {
		String body = screen(statusCode);

		assertThat(body.split("<header[ >]", -1).length - 1).as("ヘッダーは 1 つ").isEqualTo(1);
		assertThat(body).contains("class=\"brand\"", "家計・予実管理");
	}

	@ParameterizedTest(name = "状態コード {0}：ログイン中でも、ユーザー名・メニュー・ログアウトを出さない")
	@ValueSource(ints = { 404, 403, 405, 500 })
	void hasNoUserNameMenuOrLogout(int statusCode) throws Exception {
		String body = mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
				.requestAttr("jakarta.servlet.error.status_code", statusCode)
				.with(user(new LoginUser(1L, "shownameX", "x"))))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("class=\"brand\"");
		assertThat(body).doesNotContain("shownameX", "<nav class=\"menu", "action=\"/logout\"", "class=\"user-name\"");
	}

	// ---- messages.properties：文ごとに「。」で終える ----

	@ParameterizedTest(name = "{0} のお知らせの文は「。」で終わる")
	@ValueSource(strings = { "error.page.notFound.message", "error.page.forbidden.message", "error.page.clientError.message",
			"error.page.serverError.message" })
	void noticeMessagesEndEachSentenceWithAPeriod(String key) {
		String text = messageSource.getMessage(key, null, Locale.JAPAN);

		assertThat(text).endsWith("。");
		for (String sentence : text.split("(?<=。)")) {
			assertThat(sentence).as("1 文が「。」で終わる").endsWith("。");
		}
	}

	@ParameterizedTest(name = "{0} の見出しは 1 行の短い文で、「。」を付けない")
	@ValueSource(strings = { "error.page.notFound", "error.page.forbidden", "error.page.clientError", "error.page.serverError" })
	void headingsAreShortAndHaveNoPeriod(String key) {
		String text = messageSource.getMessage(key, null, Locale.JAPAN);

		assertThat(text).doesNotContain("。").hasSizeLessThanOrEqualTo(15);
	}

	@ParameterizedTest(name = "成功の文「{0}」は「。」で終わる")
	@ValueSource(strings = { "success.accountRegistered" })
	void accountRegisteredMessageEndsWithAPeriod(String key) {
		assertThat(messageSource.getMessage(key, null, Locale.JAPAN)).isEqualTo("登録しました。ログインしてください。");
	}

}
