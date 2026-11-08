package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * フォント（Zen Kaku Gothic New。設計書 1.2、2.3「文字の決まり」、4.3、7.3、7.10、issue 015 の 2 回目）の確認。
 * ①フォントのファイルが、ログインなしで開ける。②フォントだけ、ブラウザに覚えさせる（max-age 30 日）。画面・CSS・JavaScript は no-store のまま。
 * ③style.css の @font-face・フォントの指定・太さ・文字の大きさ（rem）。④外のサーバーを読む URL がない。
 * ⑤ライセンス（OFL.txt と THIRD-PARTY-NOTICES.md）。
 * 見た目そのもの（実際にそのフォントで見えること）は、MockMvc では確かめられないので、ブラウザで確かめる（手順書 5.6）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FontAssetsTest {

	private static final String REGULAR = "/fonts/ZenKakuGothicNew-Regular.woff2";
	private static final String BOLD = "/fonts/ZenKakuGothicNew-Bold.woff2";
	private static final long THIRTY_DAYS = 30L * 24 * 60 * 60;

	@Autowired
	private MockMvc mockMvc;

	private byte[] bytes(String path) throws Exception {
		return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
	}

	private String text(String path) throws Exception {
		return new String(bytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	// ---- ファイルが開ける（ログインなし）----

	@ParameterizedTest(name = "未ログインでも {0} は woff2 のファイルとして開ける")
	@ValueSource(strings = { REGULAR, BOLD })
	void fontFilesAreOpenWithoutLoginAndAreWoff2(String path) throws Exception {
		byte[] body = bytes(path);

		assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).as("woff2 の印").isEqualTo("wOF2");
		assertThat(body.length).as("大きさ（全部の文字を入れても 1.5 MB 以下）").isBetween(500_000, 1_500_000);
	}

	@Test
	void licenseFileIsServedNextToTheFonts() throws Exception {
		String ofl = text("/fonts/OFL.txt");

		assertThat(ofl).contains("Copyright 2022 The Zen Kaku Gothic Project Authors");
		assertThat(ofl).contains("SIL OPEN FONT LICENSE Version 1.1");
		assertThat(ofl).contains("PERMISSION & CONDITIONS");
	}

	// ---- ファイルの中身（SHA-256）----

	/**
	 * 配っているフォントと OFL.txt が、THIRD-PARTY-NOTICES.md に記録した SHA-256 と大きさのとおりであること。
	 * 先頭の4バイトと大きさだけでは、壊れた woff2 や、別のフォントへの差し替えに気づけないため（Codex の指摘）。
	 * 期待する値は、テストの中に書き写さず、THIRD-PARTY-NOTICES.md の表から読む（記録と実物を照らし合わせる）。
	 */
	@ParameterizedTest(name = "{0} の SHA-256 と大きさが、THIRD-PARTY-NOTICES.md の記録と同じ")
	@ValueSource(strings = { "ZenKakuGothicNew-Regular.woff2", "ZenKakuGothicNew-Bold.woff2", "OFL.txt" })
	void servedFileMatchesTheShaRecordedInThirdPartyNotices(String name) throws Exception {
		String notices = Files.readString(Path.of("THIRD-PARTY-NOTICES.md"), StandardCharsets.UTF_8);
		// 表の行：| `ファイル名`（説明） | 1,052,404 | `64文字の16進数` |
		Matcher row = Pattern.compile("\\|\\s*`" + Pattern.quote(name) + "`[^|]*\\|\\s*([0-9,]+)\\s*\\|\\s*`([0-9a-f]{64})`")
				.matcher(notices);
		assertThat(row.find()).as("THIRD-PARTY-NOTICES.md の表に %s の行がある", name).isTrue();
		long recordedSize = Long.parseLong(row.group(1).replace(",", ""));
		String recordedSha = row.group(2);

		byte[] served = bytes("/fonts/" + name);

		assertThat(served.length).as("%s の大きさ", name).isEqualTo((int) recordedSize);
		assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(served)))
				.as("%s の SHA-256", name).isEqualTo(recordedSha);
	}

	@Test
	void missingFontIsNotFoundNotRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/fonts/nothing.woff2")).andExpect(status().isNotFound());
	}

	// ---- キャッシュ（設計書 7.10）----

	@ParameterizedTest(name = "{0} は 30 日覚えさせる（no-store ではない）")
	@ValueSource(strings = { REGULAR, BOLD, "/fonts/OFL.txt" })
	void fontsAreCachedForThirtyDays(String path) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get(path)).andReturn().getResponse();

		String cacheControl = response.getHeader("Cache-Control");
		assertThat(cacheControl).isNotNull().contains("max-age=" + THIRTY_DAYS).doesNotContain("no-store");
	}

	@ParameterizedTest(name = "{0} は今までどおり no-store（フォントだけが例外）")
	@ValueSource(strings = { "/login", "/register", "/css/style.css", "/css/bootstrap.min.css", "/js/back-reload.js" })
	void everythingElseStaysNoStore(String path) throws Exception {
		String cacheControl = mockMvc.perform(get(path)).andReturn().getResponse().getHeader("Cache-Control");

		assertThat(cacheControl).isNotNull().contains("no-store");
	}

	// ---- style.css ----

	@Test
	void styleSheetDeclaresTheTwoFontFacesFromOwnFiles() throws Exception {
		String css = text("/css/style.css");

		Matcher faces = Pattern.compile("@font-face\\s*\\{([^}]*)\\}").matcher(css);
		List<String> blocks = new ArrayList<>();
		while (faces.find()) {
			blocks.add(faces.group(1));
		}
		assertThat(blocks).as("@font-face は 400 と 700 の 2 つ").hasSize(2);
		for (String block : blocks) {
			assertThat(block).contains("font-family: \"Zen Kaku Gothic New\"");
			assertThat(block).contains("font-display: swap");
			assertThat(block).contains("format(\"woff2\")");
			assertThat(block).doesNotContain("http");
		}
		assertThat(blocks.get(0)).contains("url(\"../fonts/ZenKakuGothicNew-Regular.woff2\")").contains("font-weight: 400");
		assertThat(blocks.get(1)).contains("url(\"../fonts/ZenKakuGothicNew-Bold.woff2\")").contains("font-weight: 700");
	}

	@Test
	void bodyFontIsZenKakuGothicNewWithTheFallbackFonts() throws Exception {
		String css = text("/css/style.css");

		assertThat(css).contains("--hb-font-family: \"Zen Kaku Gothic New\", \"Hiragino Sans\", \"Yu Gothic\", Meiryo, sans-serif;");
		assertThat(css).contains("--bs-body-font-family: var(--hb-font-family);");
	}

	@Test
	void fontSizesAreWrittenInRemInFourSteps() throws Exception {
		String css = text("/css/style.css");

		assertThat(css).contains("--hb-font-size-h1: 1.75rem;");
		assertThat(css).contains("--hb-font-size-h2: 1.25rem;");
		assertThat(css).contains("--hb-font-size-body: 1rem;");
		assertThat(css).contains("--hb-font-size-small: 0.8125rem;");
		assertThat(css).as("文字の大きさに px を使わない（ブラウザの設定に合わせるため）")
				.doesNotContainPattern("font-size:\\s*[0-9.]+px");
	}

	@Test
	void headingsAndTableHeadingsAreBoldButButtonsAreRegular() throws Exception {
		String css = text("/css/style.css");

		assertThat(css).containsPattern("h1,[^{]*\\{[^}]*font-weight:\\s*700");
		// ボタンの文字は 400。700 だと、小さい日本語の線がつぶれて読みにくい（Q15-7 の見直し）
		assertThat(css).containsPattern("\\.btn\\s*\\{[^}]*--bs-btn-font-weight:\\s*400");
		assertThat(css).doesNotContainPattern("\\.btn\\s*\\{[^}]*--bs-btn-font-weight:\\s*700");
		assertThat(css).containsPattern("th\\s*\\{[^}]*font-weight:\\s*700");
	}

	// ---- 外のサーバーを読まない（CDN なし）----

	@Test
	void noExternalFontUrlsInStylesOrTemplates() throws IOException {
		List<Path> targets;
		try (Stream<Path> s = Files.walk(Path.of("src/main/resources"))) {
			targets = s.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".html") || p.getFileName().toString().equals("style.css"))
					.toList();
		}
		assertThat(targets).isNotEmpty();
		for (Path p : targets) {
			String text = Files.readString(p, StandardCharsets.UTF_8);
			assertThat(text).as(p.toString()).doesNotContain("fonts.googleapis.com").doesNotContain("fonts.gstatic.com")
					.doesNotContain("@import");
		}
		String css = Files.readString(Path.of("src/main/resources/static/css/style.css"), StandardCharsets.UTF_8);
		assertThat(css).as("style.css の url(...) に外の URL がない")
				.doesNotContainPattern("url\\(\\s*[\"']?(https?:)?//");
	}

	// ---- ライセンスの表示 ----

	@Test
	void thirdPartyNoticesDescribeTheFontAndItsLicense() throws IOException {
		String notices = Files.readString(Path.of("THIRD-PARTY-NOTICES.md"), StandardCharsets.UTF_8);

		assertThat(notices).contains("Zen Kaku Gothic New");
		assertThat(notices).contains("SIL Open Font License");
		assertThat(notices).contains("Copyright 2022 The Zen Kaku Gothic Project Authors");
		assertThat(notices).contains("2705757e17e42954f3acbdf921ac0ae24d1270cd");
		assertThat(notices).contains("static/fonts/OFL.txt");
		assertThat(notices).as("OFL の全文").contains("SIL OPEN FONT LICENSE Version 1.1");
	}

	@Test
	void licenseTextInNoticesIsTheSameAsTheShippedOfl() throws IOException {
		String notices = Files.readString(Path.of("THIRD-PARTY-NOTICES.md"), StandardCharsets.UTF_8).replace("\r\n", "\n");
		String ofl = Files.readString(Path.of("src/main/resources/static/fonts/OFL.txt"), StandardCharsets.UTF_8).replace("\r\n", "\n");
		int start = ofl.indexOf("SIL OPEN FONT LICENSE Version 1.1");

		assertThat(start).isPositive();
		assertThat(notices).as("OFL.txt のライセンス本文が、1 字も変わらず入っている").contains(ofl.substring(start).strip());
	}
}
