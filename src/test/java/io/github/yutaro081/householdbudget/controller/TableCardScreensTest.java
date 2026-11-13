package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 表のある画面の表が、白いカード（.table-card。設計書 2.3、issue 024 の段階 12）に入っていることのテスト。
 * 取引一覧、科目・取引先・支払方法の一覧が対象（予実比較と予算設定は、段階 8・11 で済んだ）。
 * テンプレートと CSS のファイルを読んで確かめる（見た目そのものは、ブラウザで確かめる）。
 * あわせて、トップの「きょうのひとこと」の見出しが、ほかのカードの見出しと同じ規則であること。
 */
class TableCardScreensTest {

	private static String template(String name) throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "templates", name));
	}

	private static String css() throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css")).replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 4 つの一覧の表は、table-card の中（表を囲む入れ物が table-card）。横に長いときの囲み（table-responsive）は残す */
	@ParameterizedTest(name = "{0}：表は白いカードの中")
	@ValueSource(strings = { "transactions/list.html", "categories/list.html", "vendors/list.html", "payment-methods/list.html" })
	void theTableIsInsideATableCard(String name) throws Exception {
		String html = template(name);

		assertThat(html).containsPattern("(?s)<div class=\"table-card[^\"]*table-responsive[^\"]*\"[^>]*>\\s*<table");
	}

	/** 表のカードの見た目は 1 つの部品（.table-card）が決める。取引一覧の .list-card は、中のセルの規則だけ（カードの見た目を二重に持たない） */
	@Test
	void theCardLookIsOnlyInTheTableCardPart() throws Exception {
		String css = css();
		String card = rule(css, ".table-card");

		assertThat(card).contains("background-color: var(--hb-input-bg)", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)");
		assertThat(card).doesNotContain("box-shadow");
		assertThat(css).as("取引一覧の目印 .list-card は、カードの見た目の規則を持たない").doesNotContainPattern("(?m)^\\.list-card\\s*\\{");
	}

	/** 文字の大きさなど、ほかの見た目は変えない：取引一覧のセルの規則は、今のまま */
	@Test
	void theTransactionListCellRulesAreUnchanged() throws Exception {
		String css = css();

		assertThat(rule(css, ".list-card th")).contains("font-size: var(--hb-font-size-small)", "padding-left: 1.25rem");
		assertThat(rule(css, ".list-card td")).contains("padding-left: 1.25rem", "padding-right: 1.25rem");
		assertThat(rule(css, ".list-card td.num")).contains("font-weight: 700");
		assertThat(rule(css, ".list-card table.list")).contains("--bs-table-hover-bg: var(--hb-row-hover)");
	}

	/** 「きょうのひとこと」の見出しは、ほかのカードの見出し（.top-section h2）と同じ 1 つの規則（大きさ・字間を同じに）。字間は標準のまま */
	@Test
	void theHitokotoHeadingSharesTheRuleOfTheOtherCardHeadings() throws Exception {
		String css = css();
		Matcher m = Pattern.compile("(?m)^([^{}\\n]*\\.top-section h2,\\s*\\.hitokoto-frame h2)\\s*\\{([^}]*)\\}").matcher(css);

		assertThat(m.find()).isTrue();
		assertThat(m.group(1)).as("同じ規則の中に入っている").contains(".hitokoto-frame h2");
		assertThat(m.group(2)).contains("font-size: var(--hb-font-size-h2)", "letter-spacing: normal");
	}
}
