package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
 * 科目・取引先・支払方法の一覧の、上の追加フォームの形（設計書 2.3「科目一覧」、issue 024 の段階 6）。
 * 名前の入力欄と「追加」ボタンを、同じ行に並べる。3 つの画面で同じ形。入力エラーの文は欄の下に出て、ボタンは動かない。
 * 追加・重複・他人のデータの動きは、CategoryControllerTest などが確かめている。見た目の並びそのものは、ブラウザで確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasterAddFormLayoutTest {

	private static final List<String> PATHS = List.of("/categories", "/vendors", "/payment-methods");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;

	private RequestPostProcessor asUser;

	@BeforeEach
	void setUp() {
		Long id = userRepository.save(new User("addform1", "dummy-hash-for-test")).getId();
		asUser = user(new LoginUser(id, "addform1", "x"));
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 追加フォーム（本体の最初の form。ヘッダーのログアウトの form は除く）の中身 */
	private static String addForm(String body) {
		int start = body.indexOf("<form", body.indexOf("<main"));
		return body.substring(start, body.indexOf("</form>", start));
	}

	private String css() throws Exception {
		return mockMvc.perform(get("/css/style.css")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則「" + selector + "」がある").isTrue();
		return m.group(1);
	}

	@Test
	void theInputAndTheAddButtonAreInTheSameRowInAllThreeScreens() throws Exception {
		for (String path : PATHS) {
			String form = addForm(page(path));

			assertThat(form).as(path).contains("class=\"add-form\"");
			assertThat(form).as(path + "：横に並べる行がある").contains("<div class=\"add-row\">");
			String row = form.substring(form.indexOf("<div class=\"add-row\">"));
			assertThat(row.indexOf("<input")).as(path + "：欄が左").isPositive().isLessThan(row.indexOf("type=\"submit\""));
			assertThat(row.substring(0, row.indexOf("</div>"))).as(path + "：欄とボタンが同じ行の中").contains("<input", "type=\"submit\"", ">追加</button>");
			assertThat(form.indexOf("<label")).as(path + "：ラベルは行の上").isLessThan(form.indexOf("<div class=\"add-row\">"));
		}
	}

	@Test
	void theThreeScreensShareTheSameShapeOfTheForm() throws Exception {
		String shape = null;
		for (String path : PATHS) {
			String s = addForm(page(path)).replaceAll("(?s)<label.*?</label>", "<label/>").replaceAll("action=\"[^\"]*\"", "")
					.replaceAll("<input[^>]*>", "<input/>").replaceAll("\s+", " ");
			if (shape == null) {
				shape = s;
			}
			assertThat(s).as(path).isEqualTo(shape);
		}
	}

	/** 入力エラーの文は欄の下（行の外）に出る。ボタンは欄と同じ行のまま */
	@Test
	void theErrorMessageIsBelowTheRowAndTheTokenAndFormStayInPlace() throws Exception {
		for (String path : PATHS) {
			String body = mockMvc.perform(post(path).with(asUser).with(csrf()).param("name", "")).andExpect(status().isOk()).andReturn()
					.getResponse().getContentAsString();
			String form = addForm(body);

			assertThat(form).as(path).contains("class=\"field-error\"", "name=\"_csrf\"", "class=\"add-form\"");
			assertThat(form.indexOf("</button>")).as(path + "：エラーの文はボタンの後（行の下）")
					.isLessThan(form.indexOf("class=\"field-error\""));
			assertThat(form.indexOf("class=\"add-row\"")).isLessThan(form.indexOf("class=\"field-error\""));
		}
	}

	@Test
	void theRowStyleKeepsTheButtonAtTheTopAndNeverWrapsIt() throws Exception {
		String css = css();
		String row = rule(css, ".add-row");
		assertThat(row).contains("display: flex", "align-items: flex-start");
		assertThat(rule(css, ".add-row .form-control")).as("欄は広がり、縮められる。広げすぎない").contains("flex: 1 1 auto", "min-width: 0", "max-width: 28rem");
		assertThat(rule(css, ".add-row .btn")).as("ボタンは折り返さない").contains("flex: none", "white-space: nowrap");
		assertThat(rule(css, ".add-form")).as("追加フォームは行いっぱいのブロック（inline-block の inline-form は使わない）").contains("margin: 0 0 1rem");
	}
}
