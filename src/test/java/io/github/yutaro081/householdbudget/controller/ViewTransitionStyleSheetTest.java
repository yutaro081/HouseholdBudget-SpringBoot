package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 画面の切りかえ（View Transitions）のテスト（設計書 2.3「画面の切りかえ」、issue 031、手順書 5.2・2.6）。
 * 読む先は 2 つ。①{@code fragments/common.html} の head の {@code <style>}：{@code @view-transition} の指定だけを置く
 * （インライン CSS の 1 つだけの例外）。②{@code style.css}：ヘッダーの名前と、切りかえの時間などの見た目の規則。
 * 見え方そのもの（なめらかさ、ヘッダーの落ち着き）は、ここでは確かめられない。実機で確かめる（手順書 4 章）。
 * 「あるべきもの」（T1〜T3）と「あってはいけないもの」（T2 の条件の外、T4〜T9）を対にして確かめる。
 */
class ViewTransitionStyleSheetTest {

	private static final String NO_PREFERENCE = "(prefers-reduced-motion: no-preference)";

	private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");

	private static final Path COMMON = TEMPLATES.resolve(Path.of("fragments", "common.html"));

	private static final Pattern STYLE_TAG = Pattern.compile("(?s)<style\\b[^>]*>(.*?)</style>");

	private static String css() throws Exception {
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		return css.replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", "");
	}

	/** common.html の head の {@code <style>} の中身（コメントを取ったもの）。1 つあることも確かめる */
	private static String headStyle() throws Exception {
		Matcher m = STYLE_TAG.matcher(Files.readString(COMMON, StandardCharsets.UTF_8).replace("\r\n", "\n"));
		assertThat(m.find()).as("common.html に <style> がある").isTrue();
		return m.group(1).replaceAll("(?s)/\\*.*?\\*/", "");
	}

	/** 「@media (…) { … }」の中身（最上位の閉じ括弧まで）を全部集める。LayoutStyleSheetTest の mediaBlocks と同じ方法（行の頭の空白は許す） */
	private static List<String> mediaBlocks(String css, String condition) {
		List<String> blocks = new ArrayList<>();
		Matcher m = Pattern.compile("(?m)^\\s*@media " + Pattern.quote(condition) + "\\s*\\{").matcher(css);
		while (m.find()) {
			int depth = 1;
			int i = m.end();
			while (depth > 0 && i < css.length()) {
				char c = css.charAt(i++);
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
				}
			}
			blocks.add(css.substring(m.end(), i - 1));
		}
		return blocks;
	}

	private static int count(String text, Pattern pattern) {
		Matcher m = pattern.matcher(text);
		int n = 0;
		while (m.find()) {
			n++;
		}
		return n;
	}

	private static final Pattern VIEW_TRANSITION_RULE = Pattern.compile("@view-transition\\b");

	/** 指定したフォルダの下の、すべてのファイル（文字）を、「パス → 中身」の形で集める */
	private static List<String[]> filesUnder(Path dir) throws IOException {
		List<String[]> files = new ArrayList<>();
		try (Stream<Path> s = Files.walk(dir)) {
			for (Path p : s.filter(Files::isRegularFile).toList()) {
				files.add(new String[] { p.toString(), Files.readString(p, StandardCharsets.UTF_8) });
			}
		}
		assertThat(files).as(dir + " にファイルがある").isNotEmpty();
		return files;
	}

	// ---- あるべきもの ----

	/** T1：common.html の head の <style> に @view-transition があり、中に navigation: auto がある */
	@Test
	void headStyleHasViewTransitionWithNavigationAuto() throws Exception {
		assertThat(headStyle()).containsPattern("@view-transition\\s*\\{[^}]*navigation:\\s*auto");
	}

	/**
	 * T2：<style> の中の @view-transition の数 ＝ 「動きを減らす」でない設定の条件の中の数、かつ 1 つ以上。
	 * あるべきもの（条件の中にある）と、あってはいけないもの（条件の外に 1 つもない）の対。
	 */
	@Test
	void everyViewTransitionRuleIsInsideTheNoPreferenceCondition() throws Exception {
		String style = headStyle();

		int all = count(style, VIEW_TRANSITION_RULE);
		int inside = 0;
		for (String block : mediaBlocks(style, NO_PREFERENCE)) {
			inside += count(block, VIEW_TRANSITION_RULE);
		}

		assertThat(all).as("@view-transition が 1 つ以上ある").isGreaterThanOrEqualTo(1);
		assertThat(inside).as("条件の外に @view-transition がない（全体の数 ＝ 条件の中の数）").isEqualTo(all);
	}

	/** T3：style.css の .app-header の規則に view-transition-name: app-header がある */
	@Test
	void appHeaderHasTheViewTransitionName() throws Exception {
		Matcher m = Pattern.compile("(?m)^\\.app-header\\s*\\{([^}]*)\\}").matcher(css());
		boolean found = false;
		while (m.find()) {
			if (m.group(1).matches("(?s).*view-transition-name:\\s*app-header\\s*;.*")) {
				found = true;
			}
		}
		assertThat(found).as(".app-header の規則に view-transition-name: app-header がある").isTrue();
	}

	// ---- あってはいけないもの ----

	/** T4：view-transition-name は app-header のほかにない（同じ名前が 2 つあると、切りかえが取りやめになる） */
	@Test
	void noViewTransitionNameOtherThanAppHeader() throws Exception {
		Matcher m = Pattern.compile("view-transition-name:\\s*([^;}]*)").matcher(css());
		List<String> values = new ArrayList<>();
		while (m.find()) {
			values.add(m.group(1).trim());
		}

		assertThat(values).as("view-transition-name の値は app-header だけ").allMatch(v -> v.equals("app-header"));
		assertThat(values).as("view-transition-name は 1 つまで").hasSizeLessThanOrEqualTo(1);
	}

	/** T5：テンプレートに <meta name="view-transition" がない。切りかえの指定を 1 か所にそろえ、2 つ目の指定を作らないため */
	@Test
	void noViewTransitionMetaTagInTemplates() throws Exception {
		for (String[] file : filesUnder(TEMPLATES)) {
			assertThat(file[1]).as(file[0]).doesNotContainPattern("<meta\\s+name\\s*=\\s*[\"']view-transition[\"']");
		}
	}

	/** T6：JavaScript とテンプレートに、startViewTransition・pagereveal・pageswap がない（JavaScript は使わない） */
	@Test
	void noViewTransitionScriptInJsAndTemplates() throws Exception {
		List<String[]> files = new ArrayList<>(filesUnder(Path.of("src", "main", "resources", "static", "js")));
		files.addAll(filesUnder(TEMPLATES));
		for (String[] file : files) {
			assertThat(file[1]).as(file[0]).doesNotContain("startViewTransition").doesNotContain("pagereveal")
					.doesNotContain("pageswap");
		}
	}

	/** T7：style.css に @view-transition がない（同じ指定を 2 か所に置かない。指定は common.html の <style> だけ） */
	@Test
	void styleSheetHasNoViewTransitionRule() throws Exception {
		assertThat(count(css(), VIEW_TRANSITION_RULE)).as("style.css の @view-transition の数").isZero();
	}

	/** T8：テンプレートの中で、<style> は common.html の 1 つだけ（インライン CSS の 1 つだけの例外） */
	@Test
	void theOnlyStyleTagIsTheOneInCommonHtml() throws Exception {
		Pattern open = Pattern.compile("<style\\b");
		int total = 0;
		for (String[] file : filesUnder(TEMPLATES)) {
			int n = count(file[1], open);
			if (!Path.of(file[0]).equals(COMMON)) {
				assertThat(n).as(file[0] + " に <style> がない").isZero();
			}
			total += n;
		}
		assertThat(total).as("テンプレート全体の <style> の数").isEqualTo(1);
	}

	/** T9：その <style> の中身は、@view-transition の指定だけ（ほかの見た目の規則を、ここに増やさない） */
	@Test
	void headStyleHoldsOnlyTheViewTransitionOptIn() throws Exception {
		assertThat(headStyle().strip()).matches("@media " + Pattern.quote(NO_PREFERENCE)
				+ "\\s*\\{\\s*@view-transition\\s*\\{\\s*navigation:\\s*auto;?\\s*\\}\\s*\\}");
	}
}
