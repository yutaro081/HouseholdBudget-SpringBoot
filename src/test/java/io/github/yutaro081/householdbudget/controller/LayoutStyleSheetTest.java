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
 * ヘッダーとトップの並びの CSS（style.css）のテスト（設計書 2.3、4.3、issue 017 の 1 回目）。
 * 見た目そのもの（折り返し、色の見え方、にじみ）は、ここでは確かめられない。ブラウザで確かめる。
 * ここでは、決めた数字（1280px、992px、440px、44px、34px、16px・10px・14px の棒）と、色の決まりを確かめる。
 */
class LayoutStyleSheetTest {

	private static String css() throws Exception {
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 「@media (min-width: 992px) { … }」の中身（最上位の閉じ括弧まで）を全部集める */
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

	// ---- 幅と 2 列 ----

	@Test
	void theTopMainIsAtMost1280pxAndOtherScreensStayAt960px() throws Exception {
		String css = css();

		assertThat(rule(css, "main.top-wide")).contains("max-width: 1280px");
		assertThat(rule(css, "main.container")).contains("max-width: 960px");
	}

	@Test
	void twoColumnsStartAt992pxWithAFixed470pxLeftColumn() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-columns")).as("せまいときは 1 列").contains("grid-template-columns: 1fr");
		List<String> blocks = mediaBlocks(css, "(min-width: 992px)");
		String twoColumns = blocks.stream().filter(b -> b.contains(".top-columns")).findFirst().orElse("");
		assertThat(twoColumns).as("992px 以上で 2 列").contains("grid-template-columns: 470px minmax(0, 1fr)");
	}

	/**
	 * 季節のメーターのカード：左（合計）は中身の幅だけ、右（棒）が残りの幅を全部使う。間は 40px くらい。
	 * 左右に並べるのは 768px 以上。せまいときは上下。
	 */
	@Test
	void theSeasonMeterGivesTheLeftItsOwnWidthAndTheBarTheRestWithA40pxGap() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-season-grid")).contains("display: flex", "flex-direction: column");
		assertThat(rule(css, ".top-season-main")).as("左は中身の幅だけ").contains("flex: 0 0 auto");
		assertThat(rule(css, ".top-season-bar")).as("右の棒が残りの幅を全部使う").contains("flex: 1 1 auto", "min-width: 0");
		String wide = mediaBlocks(css, "(min-width: 768px)").stream().filter(b -> b.contains(".top-season-grid")).findFirst().orElse("");
		assertThat(wide).as("768px 以上で左右に並べる。上をそろえる（左のまとまりと、メーターの上の位置）").contains("flex-direction: row", "gap: 40px", "align-items: flex-start");
		assertThat(wide).doesNotContain("align-items: center");
	}

	/** 見込みの一文（issue 018。issue 017 のペースの一言を置きかえた）：濃いターコイズ（#0B6E6B、リンクと同じ色）の太字。赤・青の意味の色は使わない */
	@Test
	void theLandingSentenceIsDarkTealBoldWithNoMeaningColor() throws Exception {
		String css = css();
		String pace = rule(css, ".top-season-pace");

		assertThat(pace).contains("color: var(--hb-teal-text)", "font-weight: 700");
		assertThat(pace).doesNotContain("#c62828").doesNotContain("#1565c0").doesNotContain("--hb-apricot").doesNotContain("--hb-danger");
		assertThat(css).as("見込みの大きさ（予算を上回るかどうか）で色を変える規則を作らない。issue 017 の判定ごとのクラスも残さない").doesNotContain("top-season-pace.").doesNotContain("pace-slow")
				.doesNotContain("pace-fast").doesNotContain("pace-same");
		assertThat(css).as("リンクの文字と同じ色（--hb-teal-text は #0B6E6B）").contains("--hb-teal-text: #0B6E6B");
	}

	/** 「残り ○円」：「残り」は補足の灰色のまま、金額だけを本文の色の太字にして、少し目立たせる */
	@Test
	void theRemainingAmountIsBodyColorBoldAndTheWordRemainingStaysGray() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-season-remaining")).contains("color: var(--hb-text-sub)");
		assertThat(rule(css, ".top-season-remaining strong")).contains("color: var(--hb-text)", "font-weight: 700");
	}

	// ---- ヘッダー ----

	@Test
	void theHeaderContentIsAtMost1280pxAndCentered() throws Exception {
		String inner = rule(css(), ".app-header-inner");

		assertThat(inner).contains("max-width: 1280px", "margin: 0 auto", "flex-wrap: wrap");
	}

	/** issue 020：ヘッダーの中身に左右 12px の余白（本文の .container の余白と端を合わせる）。帯そのものの左右の余白は 0 */
	@Test
	void theHeaderContentHas12pxOnTheSidesToLineUpWithTheMainAndTheBandHasNone() throws Exception {
		assertThat(rule(css(), ".app-header-inner")).contains("padding: 0 12px");
		assertThat(rule(css(), ".app-header")).contains("padding: 8px 0");
	}

	/** issue 020：予算設定・予実比較の本体の幅は 1280px。ほかの画面は 960px のまま */
	@Test
	void theTableWideMainIs1280pxAndTheDefaultStaysAt960px() throws Exception {
		assertThat(rule(css(), "main.table-wide")).contains("max-width: 1280px");
		assertThat(rule(css(), "main.container")).contains("max-width: 960px");
	}

	@Test
	void theLogoSquareIs34pxWithTheButtonGroundNotTheBaseTeal() throws Exception {
		String logo = rule(css(), ".brand-logo");

		assertThat(logo).contains("width: 34px", "height: 34px", "border-radius: 10px");
		assertThat(logo).contains("background: var(--hb-teal-button)", "color: #fff");
		assertThat(logo).doesNotContain("var(--hb-teal)");
		assertThat(rule(css(), ".brand-name")).contains("font-weight: 700");
	}

	@Test
	void menuItemsAre44pxHighWithRoundedCornersAndTheOtherItemsUseTheSubText() throws Exception {
		String item = rule(css(), ".menu a");

		assertThat(item).contains("min-height: 44px", "border-radius: 10px", "color: var(--hb-text-sub)");
		assertThat(rule(css(), ".menu a svg")).contains("flex: none");
	}

	/** 設計書 2.3「文字の決まり」の例外：今開いている項目は 700。にじむようなら 400 に戻す（ブラウザで確かめる） */
	@Test
	void theCurrentMenuItemIsPaleTealDarkTealAndBold() throws Exception {
		String current = rule(css(), ".menu a[aria-current=\"page\"]");

		assertThat(current).contains("background-color: var(--hb-teal-pale)", "color: var(--hb-teal-text)", "font-weight: 700");
	}

	@Test
	void theUserNameAreaStaysOnTheRightAndTheButtonsKeepTheirWeight() throws Exception {
		assertThat(rule(css(), ".user-area")).contains("margin-left: auto");
		// ボタンの文字は 400 のまま（小さいボタンの 700 は線がつぶれる。issue 015）
		assertThat(css()).containsPattern("\\.btn\\s*\\{[^}]*--bs-btn-font-weight:\\s*400");
	}

	// ---- 棒の太さ ----

	@Test
	void barThicknessIs16pxForTheSeason14pxForTheTotalRowAnd10pxForTheCategories() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-season .top-meter")).contains("height: 16px");
		assertThat(rule(css, ".top-weeks .top-week-total .top-meter")).contains("height: 14px");
		assertThat(rule(css, ".top-weeks .top-meter")).contains("height: 10px");
	}

	/** 今週の進み具合の行は、背景なし（issue 024：薄いアプリコットの行の背景は、なくした。状態は、チップ） */
	@Test
	void progressRowsHaveNoBackgroundOrMeaningColorOfTheirOwn() throws Exception {
		String css = css();

		assertThat(css).doesNotContain(".top-weeks li.week-within", ".top-weeks li.week-over", ".top-weeks li.week-low");
	}

	// ---- 色・文字の決まりを破っていない ----

	@Test
	void newRulesDoNotUseTheBaseTealOutsideBordersNorPixelFontSizes() throws Exception {
		String css = css();
		Matcher m = Pattern.compile("([a-z-]+)\\s*:[^;{}]*var\\(--hb-teal\\)").matcher(css.replaceAll("--hb-teal:\\s*#0E9594;", ""));
		while (m.find()) {
			assertThat(m.group(1)).startsWith("border");
		}
		assertThat(css).doesNotContainPattern("font-size:\\s*[0-9.]+px");
		assertThat(css).doesNotContain("url(\"data:").doesNotContain("mask");
	}


	/** 季節のカードの左のまとまり（issue 024）：使った額は 34px の太字（ログイン画面の大きな見出しと同じ例外 --hb-font-size-intro-title）、ラベルは 16px、「/ 予算 ○円」と「残り ○円」は 20px */
	@Test
	void theLeftGroupOfTheSeasonCardIsBiggerWithTheAmountAs34pxBold() throws Exception {
		String css = css();

		assertThat(rule(css, ".top-numbers .top-season-amount")).contains("font-size: var(--hb-font-size-intro-title)", "font-weight: 700");
		assertThat(rule(css, ":root")).contains("--hb-font-size-intro-title: 2.125rem");
		assertThat(rule(css, ".top-season h2")).as("ラベル「この季節に使った合計」").contains("font-size: var(--hb-font-size-body)");
		assertThat(rule(css, ".top-season-budget")).as("/ 予算 ○円").contains("font-size: var(--hb-font-size-h2)");
		assertThat(rule(css, ".top-season-remaining")).as("残り ○円").contains("font-size: var(--hb-font-size-h2)");
	}
}
