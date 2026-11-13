package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;

/**
 * 年と季節を選ぶ欄（fragments/season.html の GET のフォーム。設計書 2.4、Phase 8 の G1、Q8-15）の確認用の部品。
 * 予算設定（BudgetControllerTest）と予実比較（ComparisonControllerTest）が共有する。画面の HTML の文字列を読むだけで、DB は使わない。
 */
final class SeasonSelectAssertions {

	private static final Pattern OPTION = Pattern.compile("<option[^>]*>[^<]*</option>");
	private static final Pattern VALUE = Pattern.compile("value=\"([^\"]*)\"");
	private static final Pattern LABEL = Pattern.compile(">([^<]*)</option>");

	private SeasonSelectAssertions() {
	}

	/** 「年と季節を選ぶ」フォーム（class="season-select"）の、&lt;form&gt; から &lt;/form&gt; まで */
	static String form(String body) {
		int at = body.indexOf("class=\"season-select\"");
		assertThat(at).as("年と季節を選ぶフォームが画面にある").isGreaterThanOrEqualTo(0);
		int start = body.lastIndexOf("<form", at);
		return body.substring(start, body.indexOf("</form>", at) + "</form>".length());
	}

	/** select（id で探す）の option の value を、並んでいる順に返す */
	static List<String> values(String form, String selectId) {
		return options(form, selectId, VALUE);
	}

	/** select（id で探す）の option の表示名を、並んでいる順に返す */
	static List<String> labels(String form, String selectId) {
		return options(form, selectId, LABEL);
	}

	private static List<String> options(String form, String selectId, Pattern what) {
		List<String> result = new ArrayList<>();
		Matcher option = OPTION.matcher(select(form, selectId));
		while (option.find()) {
			Matcher m = what.matcher(option.group());
			assertThat(m.find()).isTrue();
			result.add(m.group(1));
		}
		return result;
	}

	/** select（id で探す）で、選ばれている option の value。選ばれているのは1つだけ */
	static String selected(String form, String selectId) {
		List<String> selected = new ArrayList<>();
		Matcher option = OPTION.matcher(select(form, selectId));
		while (option.find()) {
			if (option.group().contains("selected")) {
				Matcher m = VALUE.matcher(option.group());
				assertThat(m.find()).isTrue();
				selected.add(m.group(1));
			}
		}
		assertThat(selected).as("%s で選ばれているのは1つだけ", selectId).hasSize(1);
		return selected.get(0);
	}

	private static String select(String form, String selectId) {
		int start = form.indexOf("id=\"" + selectId + "\"");
		assertThat(start).as("select（%s）がある", selectId).isGreaterThanOrEqualTo(0);
		return form.substring(start, form.indexOf("</select>", start));
	}

	/**
	 * 「今の季節」のボタン（Phase 8 の issue 007、設計書 2.4。issue 019 で位置を変えた）の確認：リンクが1つだけあり、行き先は季節を指定しない URL
	 * （path のまま。? を含まない）。前後のリンクと同じボタンの見た目。<b>見出しのすぐ下の1行</b>、つまり年と季節を選ぶフォームの中で、
	 * 「表示」ボタンのあとに並ぶ。「前の季節」「次の季節」は見出しの両脇（見出しより前）にある（範囲の端で、片方が出ないときはある方だけ）。
	 * 「冬は、12月がある年を選びます」の一文は出さない。
	 */
	static void assertCurrentSeasonLink(String body, String path) {
		String text = ">今の季節</a>";
		int end = body.indexOf(text);
		assertThat(end).as("「今の季節」のリンクが画面にある").isGreaterThanOrEqualTo(0);
		assertThat(body.indexOf(text, end + 1)).as("「今の季節」のリンクは1つだけ").isEqualTo(-1);

		String tag = body.substring(body.lastIndexOf("<a", end), end + 1);
		assertThat(tag).contains("href=\"" + path + "\"").doesNotContain("?");
		assertThat(tag).as("前後のリンクと同じボタンの見た目").contains("class=\"btn btn-outline-secondary btn-sm\"");

		int heading = body.indexOf("</h2>");
		assertThat(heading).isGreaterThanOrEqualTo(0);
		assertThat(end).as("見出しより後（すぐ下の行）").isGreaterThan(heading);
		String form = form(body);
		int formStart = body.indexOf(form);
		assertThat(formStart).as("選ぶ欄の行は、見出しのすぐ下").isGreaterThan(heading);
		assertThat(form).as("年と季節を選ぶ欄・「表示」・「今の季節」が同じ1行（同じフォームの中）").contains(">今の季節</a>");
		assertThat(form.indexOf(">表示</button>")).as("「表示」のあとに「今の季節」").isLessThan(form.indexOf(">今の季節</a>"));
		assertThat(body.substring(heading, formStart)).as("見出しと選ぶ欄の間に、ほかの行を挟まない").doesNotContain("<table").doesNotContain("<h1");

		int previous = body.indexOf("‹ 前の季節</a>");
		if (previous >= 0) {
			assertThat(previous).as("「前の季節」は見出しより前").isLessThan(heading);
		}
		int next = body.indexOf("次の季節 ›</a>");
		if (next >= 0) {
			assertThat(next).as("「次の季節」は見出しより後ろではなく、同じ行（選ぶ欄より前）").isLessThan(formStart);
		}
		assertThat(body).as("一文はなくした").doesNotContain("冬は、12月がある年を選びます");
	}

	/** 年と季節を選ぶ欄の、まとめての確認：GET のフォームで、送り先が path。年は 2000〜2100、季節は 4 つ。最初の値は year と season */
	static void assertSeasonSelect(String body, String path, int year, String season) {
		String form = form(body);
		assertThat(form).as("GET のフォーム").contains("method=\"get\"");
		assertThat(form).as("送り先は、リンクと同じ画面の URL（問い合わせの文字列なし）").contains("action=\"" + path + "\"");
		assertThat(form).as("何も変えないので CSRF のトークンは入らない").doesNotContain("_csrf");
		assertThat(form).contains("name=\"year\"", "name=\"season\"", ">表示</button>");

		List<String> years = values(form, "season-year");
		assertThat(years).as("年の選択肢は 2000〜2100 の 101 個").hasSize(101);
		assertThat(years.get(0)).isEqualTo(String.valueOf(SeasonPeriod.FIRST_YEAR));
		assertThat(years.get(years.size() - 1)).isEqualTo(String.valueOf(SeasonPeriod.LAST_YEAR));
		assertThat(labels(form, "season-year")).startsWith("2000年", "2001年").endsWith("2099年", "2100年");

		assertThat(values(form, "season-name")).containsExactly("spring", "summer", "autumn", "winter");
		assertThat(labels(form, "season-name")).containsExactly("春", "夏", "秋", "冬");

		assertThat(selected(form, "season-year")).isEqualTo(String.valueOf(year));
		assertThat(selected(form, "season-name")).isEqualTo(season);
	}
}
