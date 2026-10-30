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
