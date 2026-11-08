package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;

/**
 * ヘッダー（ロゴ・メニューのアイコン・「〇〇 さん」・ログアウト）の HTML のテスト（設計書 2.3、7.5、issue 017 の 1 回目）。
 * 見た目（色、角、高さ、折り返し）は MockMvc では見えないので、ブラウザで確かめる。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeaderMarkupTest {

	/** ヘッダーのある画面（どれも、ログイン後の全画面で同じヘッダーが出る） */
	private static final List<String> PAGES = List.of("/", "/transactions", "/categories", "/vendors",
			"/payment-methods", "/budgets", "/comparison");

	private static final Pattern HEADER = Pattern.compile("<header.*?</header>", Pattern.DOTALL);
	private static final Pattern SVG = Pattern.compile("<svg.*?</svg>", Pattern.DOTALL);

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;

	private Long userId;

	@BeforeEach
	void createUser() {
		userId = userRepository.save(new User("hdrmark1", "dummy-hash-for-test")).getId();
	}

	private RequestPostProcessor login(String username) {
		return user(new LoginUser(userId, username, "x"));
	}

	private String body(String path, String username) throws Exception {
		return mockMvc.perform(get(path).with(login(username))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private static String header(String body) {
		Matcher m = HEADER.matcher(body);
		assertThat(m.find()).as("ヘッダー（header）がある").isTrue();
		return m.group();
	}

	private static List<String> svgs(String html) {
		Matcher m = SVG.matcher(html);
		List<String> list = new java.util.ArrayList<>();
		while (m.find()) {
			list.add(m.group());
		}
		return list;
	}

	// ---- ロゴ ----

	@Test
	void theLogoLinksToTheTopAndHasTheNameAndAnIcon() throws Exception {
		String header = header(body("/transactions", "hdrmark1"));

		assertThat(header).containsPattern(Pattern.compile(
				"<a class=\"brand\" href=\"/\"><span class=\"brand-logo\"><svg[^>]*>.*?</svg></span><span class=\"brand-name\">家計・予実管理</span></a>",
				Pattern.DOTALL));
	}

	// ---- メニュー ----

	@Test
	void everyMenuItemHasOneIconAndTheLabel() throws Exception {
		String body = body("/transactions", "hdrmark1");
		Matcher nav = Pattern.compile("<nav class=\"menu[^>]*>(.*?)</nav>", Pattern.DOTALL).matcher(body);
		assertThat(nav.find()).isTrue();
		String items = nav.group(1);

		for (String label : List.of("トップ", "取引一覧", "科目", "取引先", "支払方法", "予算", "予実比較")) {
			assertThat(items).as(label).containsPattern(Pattern.compile("<a [^>]*><svg[^>]*>.*?</svg>" + label + "</a>", Pattern.DOTALL));
		}
		assertThat(svgs(items)).as("メニューのアイコンは 7 つ").hasSize(7);
	}

	@Test
	void theSevenMenuIconsAreAllDifferent() throws Exception {
		String body = body("/transactions", "hdrmark1");
		Matcher nav = Pattern.compile("<nav class=\"menu[^>]*>(.*?)</nav>", Pattern.DOTALL).matcher(body);
		assertThat(nav.find()).isTrue();

		assertThat(svgs(nav.group(1)).stream().distinct().count()).isEqualTo(7);
	}

	// ---- アイコンの安全 ----

	@Test
	void theHeaderIconsAreDecorativeAndSafe() throws Exception {
		String header = header(body("/", "hdrmark1"));

		List<String> icons = svgs(header);
		assertThat(icons).as("ロゴ 1 + メニュー 7").hasSize(8);
		for (String svg : icons) {
			assertThat(svg).contains("aria-hidden=\"true\"", "viewBox=\"0 0 24 24\"", "stroke=\"currentColor\"", "fill=\"none\"");
			assertThat(svg).doesNotContain("<script", "<use", "<image", "xmlns", "style=", "http", "onclick", "href");
		}
	}

	// ---- ユーザー名・ログアウト ----

	@Test
	void showsTheUserNameWithSanAndEscapesIt() throws Exception {
		String header = header(body("/transactions", "<b>x</b>"));

		assertThat(header).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
		assertThat(header).containsPattern("&lt;b&gt;x&lt;/b&gt;</span><span class=\"user-honorific\">さん</span>");
	}

	@Test
	void logoutIsAPostFormWithTheCsrfToken() throws Exception {
		String header = header(body("/transactions", "hdrmark1"));

		assertThat(header).containsPattern("<form action=\"/logout\" method=\"post\"");
		assertThat(header).contains("name=\"_csrf\"");
		assertThat(header).containsPattern(Pattern.compile("<button type=\"submit\" class=\"btn btn-outline-secondary btn-sm\">ログアウト</button>"));
	}

	// ---- 全画面で同じ ----

	@Test
	void theSameHeaderAppearsOnEveryScreen() throws Exception {
		for (String path : PAGES) {
			String header = header(body(path, "hdrmark1"));
			assertThat(header).as(path).contains("class=\"brand\"", "家計・予実管理", "class=\"app-header-inner\"", "さん", "ログアウト");
			assertThat(svgs(header)).as(path).hasSize(8);
		}
	}

	/**
	 * issue 017 の 2 回目：ログイン前の画面（ログイン、登録）のヘッダーは、ロゴと「家計・予実管理」だけ。
	 * ロゴは、ログイン後のヘッダーと同じ HTML（共有の部品）。メニュー・「さん」・ログアウトは出ない。
	 */
	@Test
	void screensBeforeLoginHaveTheHeaderWithOnlyTheLogo() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String page = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			String header = header(page);

			assertThat(header).as(path).contains("class=\"app-header-inner\"", "家計・予実管理");
			assertThat(header).as(path).containsPattern(Pattern.compile(
					"<a class=\"brand\" href=\"/\"><span class=\"brand-logo\"><svg[^>]*>.*?</svg></span><span class=\"brand-name\">家計・予実管理</span></a>",
					Pattern.DOTALL));
			assertThat(svgs(header)).as(path + "：アイコンはロゴの 1 つだけ").hasSize(1);
			assertThat(header).as(path).doesNotContain("class=\"menu", "aria-current", "さん", "ログアウト", "/logout", "<nav", "<form", "<button");
			assertThat(Pattern.compile("<a class=\"brand\".*?</a>", Pattern.DOTALL).matcher(header).results().findFirst().orElseThrow().group())
					.as(path + "：ログイン後のロゴと同じ HTML")
					.isEqualTo(Pattern.compile("<a class=\"brand\".*?</a>", Pattern.DOTALL).matcher(header(body("/transactions", "hdrmark1"))).results().findFirst().orElseThrow().group());
		}
	}

}
