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
			Map.entry("--hb-row-hover", "#EEF3F3"),
			Map.entry("--hb-over", "#C62828"),
			Map.entry("--hb-low", "#1565C0"),
			Map.entry("--hb-within-border", "#E8C9A8"),
			Map.entry("--hb-within-text", "#7A3A10"),
			Map.entry("--hb-within-icon", "#B4571A"),
			Map.entry("--hb-divider", "#C9D2D1"),
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

	/** 取引一覧の行にマウスを載せたときの色（issue 024）：薄い灰緑の変数。薄いターコイズは使わない。この表だけで上書きする */
	@Test
	void theTransactionListRowHoverUsesThePaleGrayGreenNotThePaleTeal() throws Exception {
		String css = css("/css/style.css");
		String rule = rule(css, ".list-card table.list");

		assertThat(rule).contains("--bs-table-hover-bg: var(--hb-row-hover)");
		assertThat(rule).doesNotContain("--hb-teal-pale");
		assertThat(css.toLowerCase()).as("行の色に薄いターコイズの値を直接書かない").doesNotContain("--bs-table-hover-bg: #e1f3f2");
	}

	/** 行にマウスを載せたときの色（#EEF3F3）の上で、文字が読めること：本文・リンク・補足の文字との明るさの比が 4.5 以上（issue 024。設計書 2.3 の色の表の値） */
	@Test
	void theRowHoverColorKeepsTheTextReadable() throws Exception {
		String css = css("/css/style.css");
		String hover = variable(css, "hb-row-hover");

		assertThat(ratio(variable(css, "hb-text"), hover)).as("本文の色").isGreaterThanOrEqualTo(4.5).isBetween(12.1, 12.3);
		assertThat(ratio(variable(css, "hb-teal-text"), hover)).as("リンクの文字").isGreaterThanOrEqualTo(4.5).isBetween(5.4, 5.5);
		assertThat(ratio(variable(css, "hb-text-sub"), hover)).as("補足の文字（日付・支払方法・摘要）").isGreaterThanOrEqualTo(4.5).isBetween(5.6, 5.7);
	}

	/** 花のアイコンの中心の縁の色（#B4571A）は、変数 --hb-within-icon と、アイコンの部品（icons.html）の中の値が、同じ。花びら #F0A060 と中心 #FFF8EE は、飾りの色（意味は持たせない。比べない） */
	@Test
	void theFlowerRingColorInTheIconIsTheSameAsTheVariable() throws Exception {
		String icons = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "resources", "templates", "fragments", "icons.html"));
		String ring = variable(css("/css/style.css"), "hb-within-icon");

		assertThat(icons).contains("stroke=\"" + ring + "\"", "fill=\"#F0A060\"", "fill=\"#FFF8EE\"");
	}

	private static String variable(String css, String name) {
		Matcher m = Pattern.compile("--" + Pattern.quote(name) + ":\s*(#[0-9A-Fa-f]{6})").matcher(css);
		assertThat(m.find()).as("--" + name).isTrue();
		return m.group(1);
	}

	private static double luminance(String hex) {
		double[] c = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
			c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	private static double ratio(String a, String b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** 予算内は、チップの背景の薄いアプリコット（#FFF2E5）。行の背景の規則（row-within）と、前の薄い緑（#e3f8e9）は、どこにも残っていない（issue 024：💮 と行の背景を、チップに置きかえた） */
	@Test
	void withinBudgetIsAnApricotChipAndTheOldRowBackgroundAndGreenAreGone() throws Exception {
		String css = css("/css/style.css");
		assertThat(rule(css, ".chip-within")).as("予算内のチップの背景は、薄いアプリコットの変数")
				.contains("background-color: var(--hb-apricot)");
		assertThat(css).as("行の背景の規則は、なくなった").doesNotContain("tr.row-within");
		assertThat(css.toLowerCase()).as("前の薄い緑（#e3f8e9）が、どこにも残っていない").doesNotContain("e3f8e9");
		// 赤・青は意味の色で、色の値は今のまま。issue 024 で、青い数字はなくなり、青はチップだけ
		assertThat(rule(css, ".comparison-table td.diff-over")).contains("color: var(--hb-over)");
		assertThat(rule(css, ".chip-over")).contains("var(--hb-over)");
		assertThat(rule(css, ".chip-low")).contains("var(--hb-low)");
	}

	/** チップの文字と背景の明るさの比（設計書 2.3、手順書 2.2）：予算超え 5.62、使い残し 5.75、予算内 7.82（いずれも 4.5 以上）。花のアイコンは飾りの図なので 3 以上（4.42）。表の見出し 6.35、「残り」「見込みとの差」の見出し 6.07 */
	@Test
	void theChipsAndTheTableHeadingsKeepTheTextReadable() throws Exception {
		String css = css("/css/style.css");
		String white = variable(css, "hb-input-bg");
		String apricot = variable(css, "hb-apricot");

		assertThat(ratio(variable(css, "hb-over"), white)).as("予算超え（赤の文字と白）").isGreaterThanOrEqualTo(4.5).isBetween(5.6, 5.7);
		assertThat(ratio(variable(css, "hb-low"), white)).as("使い残し（青の文字と白）").isGreaterThanOrEqualTo(4.5).isBetween(5.7, 5.8);
		assertThat(ratio(variable(css, "hb-within-text"), apricot)).as("予算内（文字とあんず色の背景）").isGreaterThanOrEqualTo(4.5).isBetween(7.8, 7.9);
		assertThat(ratio(variable(css, "hb-within-icon"), apricot)).as("花のアイコン（飾りの図。3 対 1 が目安）").isGreaterThanOrEqualTo(3.0).isBetween(4.4, 4.5);
		assertThat(ratio(variable(css, "hb-text-sub"), white)).as("表の見出し（#56625F と白）").isGreaterThanOrEqualTo(4.5).isBetween(6.3, 6.4);
		assertThat(ratio(variable(css, "hb-teal-text"), white)).as("「残り」「見込みとの差」の見出し（#0B6E6B と白）").isGreaterThanOrEqualTo(4.5).isBetween(6.0, 6.1);
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
	void baseColorTealIsUsedOnlyForBordersNeverForTextOrBackgrounds() throws Exception {
		String css = css("/css/style.css");
		// コメント（説明に色の値を書いている）と、変数の定義そのものを除いて調べる
		String withoutDefinition = css.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("--hb-teal:\\s*#0E9594;", "");
		assertThat(withoutDefinition).as("#0E9594 の値そのものは、変数の定義のほかに書かない")
				.doesNotContainIgnoringCase("#0E9594");
		// 文字を乗せない所だけ。今は、取引の登録・編集画面の金額の欄の枠（issue 015 の 3 回目）。使い道は border だけ
		Matcher m = Pattern.compile("([a-z-]+)\\s*:[^;{}]*var\\(--hb-teal\\)").matcher(withoutDefinition);
		int uses = 0;
		while (m.find()) {
			uses++;
			assertThat(m.group(1)).as("var(--hb-teal) を使う宣言（文字・背景には使わない）").startsWith("border");
		}
		assertThat(uses).as("金額の欄の枠で使っている").isGreaterThanOrEqualTo(1);
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

	/**
	 * 複写のお知らせの 1 行（alert-info。issue 019 のあとのブラウザの確認）：Bootstrap の標準の水色をやめて、白い地・左に 4px のターコイズの線・本文の色。
	 * 薄いターコイズは「メニューの今の項目と小さな印だけ」の決まりのまま使わない。基本のターコイズは border にだけ使う。
	 */
	@Test
	void copyNoticeIsWhiteWithATealLeftLineAndBodyTextColor() throws Exception {
		String css = css("/css/style.css");
		String rule = rule(css, ".alert-info");

		assertThat(rule).contains("--bs-alert-bg: var(--hb-input-bg)", "--bs-alert-color: var(--hb-text)", "border-left: 4px solid var(--hb-teal)");
		assertThat(rule).as("薄いターコイズは使わない").doesNotContain("--hb-teal-pale").doesNotContain("--hb-teal-text").doesNotContain("--hb-teal-button");
		assertThat(rule).as("Bootstrap の標準の水色（#cff4fc など）を書かない").doesNotContainIgnoringCase("#cff4fc").doesNotContainIgnoringCase("#9eeaf9").doesNotContainIgnoringCase("#055160");
		assertThat(css("/css/style.css")).as("白は入力欄の地と同じ変数（--hb-input-bg）").containsPattern("--hb-input-bg:\s*#FFFFFF");
	}

}
