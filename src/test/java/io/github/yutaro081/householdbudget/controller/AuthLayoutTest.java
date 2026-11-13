package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ログイン画面とアカウント登録画面の並び（設計書 2.3、issue 017 の 2 回目。Q017-9〜Q017-12、Q017-15）。
 * 見た目（2 列、色、角、高さ）は MockMvc では見えないので、ブラウザで確かめる。CSS の数字は AuthAndListStyleSheetTest。
 * 動き（ログイン、登録、メッセージ、ユーザー名が残る・パスワードが残らない）は SecurityBehaviorTest が確かめている。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthLayoutTest {

	private static final Pattern SVG = Pattern.compile("<svg.*?</svg>", Pattern.DOTALL);

	@Autowired
	private MockMvc mockMvc;

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String main(String body) {
		return body.substring(body.indexOf("<main"), body.indexOf("</main>"));
	}

	private static String intro(String body) {
		String main = main(body);
		return main.substring(main.indexOf("<section class=\"auth-intro\">"), main.indexOf("<div class=\"auth-card-col\">"));
	}

	// ---- 紹介（左） ----

	@Test
	void bothScreensShowTheSameIntroduction() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String main = main(page(path));

			assertThat(main).as(path).contains("季節ごとの家計簿",
					"季節の終わりの見込みが分かります",
					"過去の実績から、予算の案を自動で作れます",
					"自分のデータは、自分だけが見られます");
			assertThat(main).as(path + "：差しかえた古いチェックは出さない").doesNotContain("科目・取引先・支払方法は、自由に追加できます");
			assertThat(main).as(path + "：画面にない言葉「着地見込み」は使わない").doesNotContain("着地見込み");
			assertThat(main).as(path + "：チップ").contains("<span class=\"auth-chip\">季節ごとの家計簿</span>");
			assertThat(main).as(path + "：大きな見出しは h1。改行で 2 行")
					.contains("<h1 class=\"auth-title\">予算と実績を、<br>3 か月ごとにふり返る。</h1>");
			assertThat(main.split("<h1", -1).length - 1).as(path + "：h1 は 1 つ").isEqualTo(1);
		}
	}

	@Test
	void theIntroductionHasThreeCheckIconsAndTheOwnDataLineComesLast() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String whole = intro(page(path));
			String intro = whole.substring(whole.indexOf("<ul class=\"auth-points\">"));   // チェックの 3 行（説明の文の「予算内」のチップの花は、別）
			Matcher m = SVG.matcher(intro);
			int n = 0;
			while (m.find()) {
				n++;
				assertThat(m.group()).as(path).contains("M5 12l5 5 9-10", "stroke-width=\"2.6\"", "aria-hidden=\"true\"");
			}
			assertThat(n).as(path + "：チェックの印は 3 つ").isEqualTo(3);
			for (String line : List.of("季節の終わりの見込みが分かります", "過去の実績から、予算の案を自動で作れます",
					"自分のデータは、自分だけが見られます")) {
				assertThat(intro).as(path + "：" + line).containsPattern(
						Pattern.compile("<span class=\"auth-check\"><svg[^>]*>.*?</svg></span>" + line, Pattern.DOTALL));
			}
			assertThat(intro.indexOf("見込みが分かります")).as(path + "：1 つ目").isLessThan(intro.indexOf("予算の案を自動で作れます"));
			assertThat(intro.indexOf("予算の案を自動で作れます")).as(path + "：2 つ目").isLessThan(intro.indexOf("自分のデータは、自分だけが見られます"));
		}
	}

	/** 説明の文（<p class="auth-lead">）の、画面に見える文字（アイコン・タグを除く） */
	private static String leadText(String main) {
		Matcher m = Pattern.compile("(?s)<p class=\"auth-lead\">(.*?)</p>").matcher(main);
		assertThat(m.find()).as("説明の文がある").isTrue();
		return m.group(1).replaceAll("(?s)<svg.*?</svg>", "").replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").strip();
	}

	/**
	 * issue 025：説明の文は、予算内だけの言い方（1/3 以下の「使い残し」が抜けて不正確だった）をやめて、3 つの状況のチップで示す。
	 * 文は「…科目ごとの状況は、［予算超え］［使い残し］［予算内］の印で分かります。」。［ ］の 3 つは、fragments/chip のチップ。
	 */
	@Test
	void theLeadNamesTheThreeStatusMarksWithTheChips() throws Exception {
		String requirements = Files.readString(Path.of("docs", "reviews", "requirements.md"));
		assertThat(requirements).as("要件定義書：決まり（予算内のチップ）と、紹介の文").contains("実績があって予算以下なら、「予算内」のチップ", "科目ごとの状況が「予算超え」「使い残し」「予算内」の印で分かること");

		for (String path : List.of("/login", "/register")) {
			String main = main(page(path));

			assertThat(leadText(main)).as(path).isEqualTo("取引を記録すると、季節（春・夏・秋・冬）ごとに、科目別の予算と実績が並びます。科目ごとの状況は、予算超え 使い残し 予算内の印で分かります。");
			Matcher lead = Pattern.compile("(?s)<p class=\"auth-lead\">(.*?)</p>").matcher(main);
			assertThat(lead.find()).isTrue();
			String html = lead.group(1);
			assertThat(html).as(path + "：チップは fragments/chip のもの。順番は 予算超え → 使い残し → 予算内").containsPattern(
					"(?s)<span class=\"chip chip-over\">予算超え</span>\\s*<span class=\"chip chip-low\">使い残し</span>\\s*<span class=\"chip chip-within\"><span class=\"chip-icon\"><svg.*?</svg></span>予算内</span>");
			assertThat(html).as(path + "：予算超えのチップに金額は付けない").doesNotContain("＋", "円");
			assertThat(count(html, "class=\"chip ")).as(path).isEqualTo(3);
			assertThat(html).as(path + "：予算内のチップは花のアイコンつき（aria-hidden）").contains("aria-hidden=\"true\"");
			assertThat(main).as(path + "：前の言い方（予算内だけ）は使わない").doesNotContain("予算の 1/3 を超えて", "予算以内に収まった科目には", "💮");
		}
	}

	/** チェックの 3 行が、それぞれ 1 行に収まる（紹介の列は約 470px。16px の文字で 1 行 24 文字までを目安にする）。1 つ目は短くした */
	@Test
	void everyCheckLineIsShortEnoughForOneLine() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String intro = intro(page(path));
			Matcher items = Pattern.compile("(?s)<li><span class=\"auth-check\">.*?</svg></span>(.*?)</li>").matcher(intro);
			int n = 0;
			while (items.find()) {
				n++;
				assertThat(items.group(1).strip().length()).as(path + "：" + items.group(1)).isLessThanOrEqualTo(24);
			}
			assertThat(n).as(path).isEqualTo(3);
			assertThat(intro).as(path + "：前の 1 つ目（長くて折り返した）は使わない").doesNotContain("このペースだと");
		}
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/** 登録エラーのテストが「パスワードが残っていない」を英字で調べるので、紹介は日本語だけ（3.2 の #6） */
	@Test
	void theIntroductionHasNoInputValueOrAsciiWords() throws Exception {
		String intro = intro(page("/login"));
		String withoutTags = intro.replaceAll("(?s)<svg.*?</svg>", "").replaceAll("<[^>]*>", "");

		assertThat(withoutTags).doesNotContainPattern("[A-Za-z]{2,}");
		assertThat(intro).doesNotContain("value=\"", "<input", "<form");
	}

	// ---- 並び ----

	@Test
	void theIntroductionComesBeforeTheCardInTheHtmlAndTheLayoutIsNotNarrow() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String body = page(path);

			assertThat(body.indexOf("class=\"auth-intro")).as(path).isPositive().isLessThan(body.indexOf("class=\"auth-card\""));
			assertThat(body).as(path).contains("<main class=\"container auth-main\">", "class=\"auth-layout\"", "class=\"auth-card-col\"");
			assertThat(body).as(path + "：420px の narrow をやめる").doesNotContain("container narrow");
		}
	}

	// ---- ログインのカード（右） ----

	@Test
	void theLoginCardHasTheHeadingTheFieldsTheWideButtonAndTheRegisterLink() throws Exception {
		String body = page("/login");
		String card = body.substring(body.indexOf("class=\"auth-card\""), body.indexOf("</main>"));

		assertThat(card).contains("<h2>ログイン</h2>", "action=\"/login\"", "name=\"username\"", "name=\"password\"", "name=\"_csrf\"");
		assertThat(card).contains("<button type=\"submit\" class=\"btn btn-primary w-100\">ログイン</button>");
		assertThat(card).contains("class=\"auth-switch\">はじめての方は <a href=\"/register\">アカウント登録</a>");
		assertThat(card.indexOf("<button")).isLessThan(card.indexOf("auth-switch"));
		assertThat(card).as("パスワードの欄に値を入れない").doesNotContainPattern("name=\"password\"[^>]*value=");
		assertThat(card).as("ラベルは入力欄の上").containsPattern(Pattern.compile("<label[^>]*for=\"username\">ユーザー名</label>\\s*<input"));
	}

	@Test
	void theMessagesAreInsideTheCard() throws Exception {
		String body = page("/login?logout");

		assertThat(body.indexOf("class=\"auth-card\"")).isPositive();
		assertThat(body.indexOf("ログアウトしました")).isGreaterThan(body.indexOf("class=\"auth-card\""));
	}

	// ---- 登録のカード（右） ----

	@Test
	void theRegisterCardHasTheSameShapeAndKeepsTheLabelsAndErrors() throws Exception {
		String body = page("/register");
		String card = body.substring(body.indexOf("class=\"auth-card\""), body.indexOf("</main>"));

		assertThat(card).contains("<h2>アカウント登録</h2>", "action=\"/register\"", "name=\"_csrf\"",
				"ユーザー名（半角英数字、10文字以内）", "パスワード（半角の英数字と記号、8〜30文字）");
		assertThat(card).contains("<button type=\"submit\" class=\"btn btn-primary w-100\">登録</button>");
		assertThat(card).contains("class=\"auth-switch\">アカウントをお持ちの方は <a href=\"/login\">ログイン</a>");
	}

	@Test
	void theIntroductionIsAlsoShownWhenTheRegisterFormIsShownAgainWithErrors() throws Exception {
		String body = mockMvc.perform(post("/register").with(csrf()).param("username", "あいう").param("password", "short"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(body).contains("予算と実績を、", "科目ごとの状況は、", "class=\"field-error\"", "value=\"あいう\"", "ユーザー名は半角英数字で入力してください");
		assertThat(body).doesNotContain("value=\"short\"");
	}

	// ---- autocomplete（Codex のレビューの指摘。issue 017 の 2 回目） ----

	/** name が name の &lt;input&gt; の開きタグを取り出す（属性の順番に依存しない） */
	private static String input(String body, String name) {
		Matcher m = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*>").matcher(body);
		assertThat(m.find()).as(name + " の入力欄がある").isTrue();
		return m.group();
	}

	@Test
	void theLoginFieldsTellTheBrowserWhichIsTheUsernameAndTheCurrentPassword() throws Exception {
		String body = page("/login");

		assertThat(input(body, "username")).contains("autocomplete=\"username\"");
		assertThat(input(body, "password")).contains("autocomplete=\"current-password\"");
	}

	@Test
	void theRegisterFieldsTellTheBrowserWhichIsTheUsernameAndANewPassword() throws Exception {
		String body = page("/register");

		assertThat(input(body, "username")).contains("autocomplete=\"username\"");
		assertThat(input(body, "password")).contains("autocomplete=\"new-password\"");
		assertThat(body.split("type=\"password\"", -1).length - 1).as("パスワードの欄は 1 つ（確認用の欄はない）").isEqualTo(1);
	}

	/** 入力エラーで表示し直したときも、付いたまま */
	@Test
	void theRegisterFieldsKeepAutocompleteWhenShownAgainWithErrors() throws Exception {
		String body = mockMvc.perform(post("/register").with(csrf()).param("username", "あいう").param("password", "short"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(input(body, "username")).contains("autocomplete=\"username\"");
		assertThat(input(body, "password")).contains("autocomplete=\"new-password\"");
	}

}
