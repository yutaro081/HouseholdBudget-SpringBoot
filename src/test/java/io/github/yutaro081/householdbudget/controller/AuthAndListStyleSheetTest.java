package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * ログイン・登録の 2 列とカード、取引一覧の見出しの行とカードの CSS（style.css）のテスト
 * （設計書 2.3、4.3、issue 017 の 2 回目。Q017-10、Q017-15）。
 * 見た目そのもの（折り返し、色の見え方）は、ここでは確かめられない。ブラウザで確かめる。
 */
class AuthAndListStyleSheetTest {

	private static String css() throws Exception {
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 「@media (min-width: …) { … }」の中身を全部集める（最上位の閉じ括弧まで） */
	private static List<String> mediaBlocks(String css, String condition) {
		List<String> blocks = new ArrayList<>();
		Matcher m = Pattern.compile("(?m)^@media " + Pattern.quote(condition) + "\\s*\\{").matcher(css);
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

	// ---- ログイン・登録の 2 列（Q017-10：768px 以上で 2 列。せまいときは縦でカードが上） ----

	@Test
	void narrowWidthStacksTheCardOnTopAndFrom768pxTheColumnsAreSideBySideInTheHtmlOrder() throws Exception {
		String css = css();

		assertThat(rule(css, ".auth-layout")).contains("display: flex", "flex-direction: column");
		assertThat(rule(css, ".auth-card-col")).as("せまいとき、カードを上へ").contains("order: -1");
		String wide = mediaBlocks(css, "(min-width: 768px)").stream().filter(b -> b.contains(".auth-layout")).findFirst().orElse("");
		assertThat(wide).as("768px 以上で 2 列").contains(".auth-layout", "display: grid", "grid-template-columns");
		assertThat(wide).as("2 列のときは HTML の順（紹介が左）").contains(".auth-card-col", "order: 0");
	}

	/**
	 * 768px 以上（2 列）：ヘッダーの下の残りの高さの縦の真ん中に、紹介とカードを置く（設計書 2.3）。
	 * body を画面の高さいっぱいの縦の flex にし、main.auth-main を残りの高さに伸ばして、中身を縦の真ん中にそろえる。
	 */
	@Test
	void from768pxTheAuthMainIsCenteredVerticallyInTheHeightBelowTheHeader() throws Exception {
		String css = css();

		String block = mediaBlocks(css, "(min-width: 768px)").stream().filter(b -> b.contains("body:has(> main.auth-main)")).findFirst().orElse("");
		assertThat(block).as("768px 以上の規則がある").isNotEmpty();
		block = block.replaceAll("(?m)^\\s+", ""); // rule() は行頭の選択子を探すので、字下げを外す
		String body = rule(block, "body:has(> main.auth-main)");
		assertThat(body).contains("min-height: 100vh", "display: flex", "flex-direction: column");
		String main = rule(block, "body:has(> main.auth-main) main.auth-main");
		assertThat(main).contains("flex: 1 1 auto", "display: flex", "flex-direction: column", "justify-content: center");
	}

	/** せまいとき（768px 未満。カードが上に来る）は今のまま上から並べる：縦の中央にする規則を、768px 以上の外には書かない */
	@Test
	void belowTheTwoColumnWidthTheAuthMainStaysAtTheTop() throws Exception {
		String css = css();

		assertThat(rule(css, "main.auth-main")).doesNotContain("justify-content", "min-height", "flex");
		String outside = css;
		for (String b : mediaBlocks(css, "(min-width: 768px)")) {
			outside = outside.replace(b, "");
		}
		assertThat(outside).as("768px 以上の外に、縦の中央の規則を置かない").doesNotContain("body:has(> main.auth-main)");
	}

	@Test
	void theMainKeepsTheStandardWidth() throws Exception {
		String css = css();

		assertThat(rule(css, "main.container")).contains("max-width: 960px");
		assertThat(rule(css, "main.narrow")).as("詳細・編集・エラー画面が使うので残す").contains("max-width: 420px");
		assertThat(css).as("ログインの本体の幅の規則は新しく作らない").doesNotContainPattern("main\\.auth-main\\s*\\{[^}]*max-width");
	}

	// ---- ログイン・登録のカード（Q017-15） ----

	@Test
	void theCardIsWhiteWithALineAnd16pxCornersAndNoShadow() throws Exception {
		String card = rule(css(), ".auth-card");

		assertThat(card).contains("background: #fff", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(card).doesNotContain("box-shadow", "18px", "var(--hb-teal");
	}

	@Test
	void theFieldsInTheCardAre46pxHighAndOnlyThere() throws Exception {
		String css = css();

		assertThat(rule(css, ".auth-card .form-control")).contains("min-height: 46px");
		assertThat(rule(css, ".auth-card .btn")).as("ボタンの文字は 400 のまま").doesNotContain("font-weight");
	}

	@Test
	void theHeadingsUseTheFourStepsExceptTheIntroTitleAndTheButtonStays400() throws Exception {
		String css = css();

		assertThat(rule(css, ".auth-title")).as("紹介の大きな見出しだけ、4 段の外（34px ＝ 2.125rem。設計書 2.3 の例外）")
				.contains("font-size: var(--hb-font-size-intro-title)", "font-weight: 700");
		assertThat(rule(css, ":root")).contains("--hb-font-size-intro-title: 2.125rem;");
		assertThat(rule(css, ".auth-card h2")).contains("font-size: var(--hb-font-size-h2)", "font-weight: 700");
		assertThat(rule(css, ".auth-chip")).contains("font-size: var(--hb-font-size-small)");
	}

	/** 薄いターコイズは小さな印（チップ、チェックの四角）だけ。紹介の背景やカードには使わない */
	@Test
	void thePaleTealIsUsedOnlyBySmallMarks() throws Exception {
		String css = css();
		Matcher m = Pattern.compile("(?m)^(\\.auth-[^{}]*|main\\.auth-main[^{}]*)\\{([^}]*)\\}").matcher(css);
		boolean chip = false;
		boolean check = false;
		int rules = 0;
		while (m.find()) {
			rules++;
			String selector = m.group(1).trim();
			String body = m.group(2);
			if (selector.equals(".auth-chip")) {
				chip = true;
				assertThat(body).contains("background: var(--hb-teal-pale)", "color: var(--hb-teal-text)");
			} else if (selector.equals(".auth-check")) {
				check = true;
				assertThat(body).contains("background: var(--hb-teal-pale)", "color: var(--hb-teal-text)");
			} else {
				assertThat(body).as(selector + " は薄いターコイズを使わない").doesNotContain("--hb-teal-pale", "#E1F3F2");
			}
			assertThat(body).as(selector).doesNotContain("#0E9594", "#c62828", "#1565c0", "--hb-apricot", "url(");
			assertThat(body).as(selector + "：--hb-teal は枠の線だけ").doesNotContainPattern("(?<![a-z-])(background|color)[^;]*var\\(--hb-teal\\)");
		}
		assertThat(rules).isGreaterThan(5);
		assertThat(chip).isTrue();
		assertThat(check).isTrue();
	}

	// ---- 取引一覧（見出しの行とカード） ----

	@Test
	void theHeadingRowWrapsAndTheCardHasTheSameLookAsTheOtherCards() throws Exception {
		String css = css();

		assertThat(rule(css, ".page-head")).contains("display: flex", "flex-wrap: wrap", "justify-content: space-between");
		assertThat(rule(css, ".page-sub")).contains("color: var(--hb-text-sub)", "font-size: var(--hb-font-size-small)");
		String card = rule(css, ".list-card");
		assertThat(card).contains("background: #fff", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(card).doesNotContain("box-shadow");
	}

	@Test
	void theListCardRulesDoNotTouchTheOtherTables() throws Exception {
		String css = css();

		assertThat(rule(css, ".list-card th")).contains("font-size: var(--hb-font-size-small)", "color: var(--hb-text-sub)");
		assertThat(rule(css, ".list-card td.num")).as("金額は太字・桁をそろえる").contains("font-weight: 700", "font-variant-numeric: tabular-nums");
		assertThat(rule(css, ".list-card td.list-muted")).contains("color: var(--hb-text-sub)");
		// 取引一覧のための規則は、すべて .list-card の下。table.list を直接変える規則を足していない
		Matcher m = Pattern.compile("(?m)^(table\\.list[^{}]*)\\{").matcher(css);
		List<String> selectors = new ArrayList<>();
		while (m.find()) {
			selectors.add(m.group(1).trim().replaceAll("\\s+", " "));
		}
		assertThat(selectors).as("今までの table.list の規則だけ").containsExactlyInAnyOrder("table.list td.num, table.list th.num");
	}

	/** 案に合わせた紹介のゆとり（issue 017 の 2 回目の見直し）。px ではなく rem で書く（34px＝2.125rem、14px＝0.875rem、20px＝1.25rem、72px＝4.5rem） */
	@Test
	void theIntroductionHasTheSpacingOfTheDesign() throws Exception {
		String css = css();

		assertThat(rule(css, ".auth-title")).contains("line-height: 1.45", "margin-bottom: 1.25rem");
		assertThat(rule(css, ".auth-lead")).as("説明：行の間 1.85。下の余白は、チェックの上の 24px の側に持たせる").contains("line-height: 1.85", "margin: 0");
		assertThat(rule(css, ".auth-points")).as("チェックの上 24px、2 行の間 12px").contains("margin: 1.5rem 0 0", "gap: var(--hb-space-3)");
		assertThat(rule(css, ".auth-chip")).as("チップの下 14px").contains("margin-bottom: 0.875rem");
		String wide = mediaBlocks(css, "(min-width: 768px)").stream().filter(b -> b.contains(".auth-layout")).findFirst().orElse("");
		assertThat(wide).as("2 列のとき、紹介とカードの間は 72px").contains("gap: 4.5rem");
	}

	/** 取引一覧：本体の幅をトップと同じ 1120px、表のマスの左右の余白を 20px（見出しの行のマスも同じ） */
	@Test
	void theListMainIsAsWideAsTheTopAndTheCellsHave20pxOnTheSides() throws Exception {
		String css = css();

		assertThat(rule(css, "main.list-wide")).contains("max-width: 1120px");
		assertThat(rule(css, "main.top-wide")).contains("max-width: 1120px");
		assertThat(rule(css, "main.container")).as("ほかの画面は 960px のまま").contains("max-width: 960px");
		assertThat(rule(css, ".list-card th")).contains("padding-left: 1.25rem", "padding-right: 1.25rem");
		assertThat(rule(css, ".list-card td")).contains("padding-left: 1.25rem", "padding-right: 1.25rem");
	}

}
