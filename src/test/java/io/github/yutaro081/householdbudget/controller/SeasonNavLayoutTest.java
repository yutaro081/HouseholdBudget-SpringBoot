package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * 予算設定・予実比較の「前の季節」「次の季節」（設計書 2.3、2.4、issue 025 の「季節の移動」）。
 * 画面の両端にあって押しにくかったので、季節の名前のすぐ左右に寄せて、名前と一緒に中央にまとめる。
 * ボタンの言葉は「‹ 前の季節」「次の季節 ›」。ボタンと名前の間は 16px。リンク先（URL）と、年と季節を選ぶ欄の行は、今のまま。
 * 狭い幅で折り返しても、前・名前・次の順が崩れない（HTML の順のまま、CSS で順を入れかえない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeasonNavLayoutTest {

	private static final List<String> PATHS = List.of("/budgets", "/comparison");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;

	private RequestPostProcessor asUser;

	@BeforeEach
	void setUp() {
		Long id = userRepository.save(new User("seasonnav1", "dummy-hash-for-test")).getId();
		asUser = user(new LoginUser(id, "seasonnav1", "x"));
	}

	private String page(String path, String query) throws Exception {
		return mockMvc.perform(get(path + query).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 季節の行（season-nav）の中身。次の「年と季節を選ぶ欄」（form）の前まで */
	private static String nav(String body) {
		int start = body.indexOf("<div class=\"season-nav\">");
		assertThat(start).as("季節の行がある").isPositive();
		int end = body.indexOf("<form", start);
		return body.substring(start, end);
	}

	/** 中央にまとまる並び：「‹ 前の季節」→ 季節の名前（見出し）→「次の季節 ›」。両端に寄せるための、空の入れ物（span）は使わない */
	@Test
	void thePreviousLinkTheNameAndTheNextLinkAreInThatOrder() throws Exception {
		for (String path : PATHS) {
			String nav = nav(page(path, "?year=2026&season=autumn"));

			int previous = nav.indexOf("‹ 前の季節</a>");
			int title = nav.indexOf("<h2 class=\"season-title\">");
			int next = nav.indexOf("次の季節 ›</a>");
			assertThat(previous).as(path).isPositive().isLessThan(title);
			assertThat(title).as(path).isLessThan(next);
			assertThat(nav).as(path + "：両端に寄せるための入れ物は使わない").doesNotContain("season-link");
			assertThat(nav.replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ")).as(path).contains("2026年 秋 （9月〜11月）");
		}
	}

	/** リンク先（URL）は変えない：前の季節・次の季節の、年と季節を含む URL。ボタンの見た目（btn）も今のまま */
	@Test
	void theLinkTargetsAreUnchanged() throws Exception {
		for (String path : PATHS) {
			String nav = nav(page(path, "?year=2026&season=autumn"));

			assertThat(nav).as(path).containsPattern("<a class=\"btn btn-outline-secondary btn-sm\" href=\"" + path + "\\?year=2026&amp;season=summer\">‹ 前の季節</a>");
			assertThat(nav).as(path).containsPattern("<a class=\"btn btn-outline-secondary btn-sm\" href=\"" + path + "\\?year=2026&amp;season=winter\">次の季節 ›</a>");
		}
	}

	/** 範囲の端：2000 年春は「前の季節」を、2100 年冬は「次の季節」を出さない。出さない側に、空の場所を残さない */
	@Test
	void theEndsOfTheRangeShowOnlyOneLinkWithoutEmptyPlaceholders() throws Exception {
		for (String path : PATHS) {
			String first = nav(page(path, "?year=2000&season=spring"));
			String last = nav(page(path, "?year=2100&season=winter"));

			assertThat(first).as(path).doesNotContain("前の季節").contains("次の季節 ›</a>").doesNotContain("season-link");
			assertThat(last).as(path).doesNotContain("次の季節").contains("‹ 前の季節</a>").doesNotContain("season-link");
		}
	}

	/** 年と季節を選ぶ欄の行は、今のまま（年・季節・表示・今の季節） */
	@Test
	void theSelectRowIsUnchanged() throws Exception {
		for (String path : PATHS) {
			String body = page(path, "?year=2026&season=autumn");
			int select = body.indexOf("<form method=\"get\"");

			assertThat(select).as(path).isGreaterThan(body.indexOf("<div class=\"season-nav\">"));
			String form = body.substring(select, body.indexOf("</form>", select));
			assertThat(form).as(path).contains("class=\"season-select\"", "id=\"season-year\"", "id=\"season-name\"", ">表示</button>", ">今の季節</a>");
		}
	}

	private static String css() throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css")).replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 中央にまとめる：左右の端に離す（space-between）をやめ、中央（center）に寄せる。ボタンと名前の間は 16px。幅がせまいときは折り返す */
	@Test
	void theRowIsCenteredWithA16pxGapAndWrapsWhenNarrow() throws Exception {
		String nav = rule(css(), ".season-nav");

		assertThat(nav).contains("display: flex", "justify-content: center", "flex-wrap: wrap", "gap: var(--hb-space-4)", "align-items: center");
		assertThat(nav).doesNotContain("space-between");
		assertThat(css()).as("両端に寄せるための最小の幅の規則は、なくした").doesNotContain(".season-nav .season-link");
	}

	/** 折り返しても、前・名前・次の順が崩れない：順を入れかえる指定（order、row-reverse）を使わない。HTML の順がそのまま並ぶ */
	@Test
	void noRuleReordersThePreviousTheNameAndTheNext() throws Exception {
		String css = css();
		String block = css.substring(css.indexOf(".season-nav {"), css.indexOf(".season-select {"));

		assertThat(block).doesNotContain("order:", "reverse", "float");
	}
}
