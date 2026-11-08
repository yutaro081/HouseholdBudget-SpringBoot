package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * style.css の色の決まり（設計書 2.3「色の決まり」、issue 015 の 1 回目）の確認。
 * 色の変数（--hb-…）が、表のとおりの値であること。主ボタン・入力欄・リンク・今のメニュー・💮 の行が、その変数を使うこと。
 * 💮 の行の背景は薄いアプリコット #FFF2E5 で、前の薄い緑が残っていないこと。Bootstrap の CSS ファイルを書き換えていないこと。
 * 見た目そのもの（実際にその色で見えること）は、MockMvc では確かめられないので、ブラウザで確かめる（手順書 4.7）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ColorSchemeStyleSheetTest {

	/** 設計書 2.3「色の決まり」の表の値（変数名 → 色） */
	private static final Map<String, String> COLORS = Map.ofEntries(
			Map.entry("--hb-teal", "#0E9594"),
			Map.entry("--hb-teal-pale", "#E1F3F2"),
			Map.entry("--hb-teal-text", "#0B6E6B"),
			Map.entry("--hb-teal-button", "#0B7F7E"),
			Map.entry("--hb-text", "#24302F"),
			Map.entry("--hb-text-sub", "#56625F"),
			Map.entry("--hb-bg", "#F6F8F8"),
			Map.entry("--hb-line", "#E2E8E8"),
			Map.entry("--hb-input-border", "#849290"),
			Map.entry("--hb-input-bg", "#FFFFFF"),
			Map.entry("--hb-apricot", "#FFF2E5"),
			Map.entry("--hb-danger", "#B02A37"));

	@Autowired
	private MockMvc mockMvc;

	private String css(String path) throws Exception {
		String body = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		return body.replace("\r\n", "\n");
	}

	/** セレクタの規則の中身（{ と } の間）を返す */
	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則「" + selector + "」がある").isTrue();
		return m.group(1);
	}

	@Test
	void colorVariablesHaveTheValuesOfTheDesign() throws Exception {
		String root = rule(css("/css/style.css"), ":root");
		for (Map.Entry<String, String> e : COLORS.entrySet()) {
			assertThat(root).as(e.getKey())
					.containsPattern("(?i)" + Pattern.quote(e.getKey()) + ":\\s*" + Pattern.quote(e.getValue()) + "\\s*;");
		}
	}

	@Test
	void withinBudgetRowBackgroundIsApricotAndTheOldGreenIsGone() throws Exception {
		String css = css("/css/style.css");
		assertThat(rule(css, ".comparison-table tr.row-within")).as("💮 の行の背景は、薄いアプリコットの変数")
				.contains("--bs-table-bg: var(--hb-apricot)");
		assertThat(css.toLowerCase()).as("前の薄い緑（#e3f8e9）が、どこにも残っていない").doesNotContain("e3f8e9");
		// 赤・青は意味の色で、今のまま
		assertThat(rule(css, ".comparison-table tr.row-over td")).containsIgnoringCase("#c62828");
		assertThat(rule(css, ".comparison-table tr.row-low td")).containsIgnoringCase("#1565c0");
	}

	@Test
	void primaryButtonLinksInputsAndCurrentMenuUseTheColorVariables() throws Exception {
		String css = css("/css/style.css");
		assertThat(rule(css, ".btn-primary")).contains("--bs-btn-bg: var(--hb-teal-button)",
				"--bs-btn-border-color: var(--hb-teal-button)");
		assertThat(rule(css, ":root")).contains("--bs-link-color: var(--hb-teal-text)", "--bs-body-color: var(--hb-text)",
				"--bs-body-bg: var(--hb-bg)", "--bs-border-color: var(--hb-line)");
		assertThat(rule(css, ".form-control,\n.form-select")).as("入力欄：地は白、枠は #849290 の変数")
				.contains("background-color: var(--hb-input-bg)", "border-color: var(--hb-input-border)");
		assertThat(rule(css, ".menu a[aria-current=\"page\"]")).contains("background-color: var(--hb-teal-pale)",
				"color: var(--hb-teal-text)");
	}

	@Test
	void errorRedDeleteButtonAndSecondaryButtonAreDarkerThanBootstrapDefaults() throws Exception {
		String css = css("/css/style.css");
		assertThat(rule(css, ":root")).as("エラーの文字（.field-error）は --bs-danger を使うので、これを濃い赤にする")
				.contains("--bs-danger: var(--hb-danger)", "--bs-danger-rgb: 176, 42, 55");
		assertThat(rule(css, ".field-error")).contains("var(--bs-danger");
		assertThat(rule(css, ".btn-outline-danger")).as("削除ボタン（赤い枠）は、濃い赤の変数")
				.contains("--bs-btn-color: var(--hb-danger)", "--bs-btn-border-color: var(--hb-danger)", "--bs-btn-hover-bg: var(--hb-danger)");
		assertThat(rule(css, ".btn-outline-secondary")).as("副ボタンの文字・枠は、補足の文字と同じ色の変数")
				.contains("--bs-btn-color: var(--hb-text-sub)", "--bs-btn-border-color: var(--hb-text-sub)", "--bs-btn-hover-bg: var(--hb-text-sub)");
		String withoutComments = css.replaceAll("(?s)/\\*.*?\\*/", "").toLowerCase();
		assertThat(withoutComments).as("Bootstrap の標準の色（#dc3545、#6c757d）を、規則の中に書いていない（コメントの説明は除く）")
				.doesNotContain("#dc3545", "#6c757d");
	}

	@Test
	void baseColorTealIsNotUsedYetBecauseNothingPutsTextOnIt() throws Exception {
		String css = css("/css/style.css");
		// コメント（説明に色の値を書いている）と、変数の定義そのものを除いて調べる
		String withoutDefinition = css.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--hb-teal:\\s*#0E9594;", "");
		assertThat(withoutDefinition).as("#0E9594（文字を乗せない所だけに使う色）を、今は使っていない")
				.doesNotContainIgnoringCase("#0E9594").doesNotContain("var(--hb-teal)");
	}

	@Test
	void styleSheetLoadsNothingFromOutside() throws Exception {
		// 例外は、アプリの中のフォントのファイルを指す url("../fonts/…woff2") だけ（issue 015 の 2 回目）。それ以外の url(...) は使わない
		String withoutOwnFonts = css("/css/style.css").replaceAll("url\\(\"\\.\\./fonts/ZenKakuGothicNew-(Regular|Bold)\\.woff2\"\\)", "");
		assertThat(withoutOwnFonts).doesNotContain("http://", "https://", "@import", "url(");
	}

	@Test
	void bootstrapCssFileIsNotRewritten() throws Exception {
		String bootstrap = css("/css/bootstrap.min.css");
		assertThat(bootstrap).as("Bootstrap の CSS は配布物のまま（青い主ボタンの値が残っている）")
				.contains(".btn-primary{--bs-btn-color:#fff;--bs-btn-bg:#0d6efd;").doesNotContain("--hb-");
	}

}
