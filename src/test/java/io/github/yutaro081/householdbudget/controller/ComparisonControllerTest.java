package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * ComparisonController（予実比較の画面）のテスト（設計書 2.2〜2.4、3.9、5.9、F4、Q7-5〜Q7-8）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は、日本時間の 2027-02-15（2026年の冬）に固定した Clock で確かめる（冬の2月は前の年の冬、設計書 5.8）。
 * 計算（色の判定・集計）は BudgetColorJudgeTest・ComparisonServiceTest で確かめてあるので、ここでは画面に出る形を確かめる。
 * テストのパスワードのハッシュは、ダミーの値。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComparisonControllerTest {

	/** 日本時間の 2027-02-15（2026年の冬）に固定した Clock（季節を指定しないときの「今の季節」） */
	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2027, 2, 15).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long id, RequestPostProcessor login, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, user(new LoginUser(id, username, "x")),
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category category(Owner o, String name) {
		return categoryRepository.save(new Category(o.id(), name));
	}

	private void budget(Owner o, Category c, int year, Season season, int amount) {
		budgetRepository.save(new Budget(o.id(), c, year, season, amount));
	}

	private void spend(Owner o, Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(o.id(), date, amount, c, null, o.vendor(), o.paymentMethod()));
	}

	/** 2026年の秋に、予算と取引を入れる */
	private void autumn(Owner o, Category c, Integer budgetAmount, int spent) {
		if (budgetAmount != null) {
			budget(o, c, 2026, Season.AUTUMN, budgetAmount);
		}
		if (spent > 0) {
			spend(o, c, LocalDate.of(2026, 10, 29), spent);
		}
	}

	private String open(Owner o, String year, String season) throws Exception {
		entityManager.flush();
		entityManager.clear();
		var request = get("/comparison").with(o.login());
		if (year != null) {
			request.param("year", year);
		}
		if (season != null) {
			request.param("season", season);
		}
		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andExpect(view().name("comparison/index")).andReturn();
		return result.getResponse().getContentAsString();
	}

	/** 科目名を含む行（&lt;tr ...&gt; から &lt;/tr&gt; まで）を取り出す */
	private static String rowOf(String body, String marker) {
		int at = body.indexOf(marker);
		assertThat(at).as("「%s」が画面にある", marker).isGreaterThanOrEqualTo(0);
		int start = body.lastIndexOf("<tr", at);
		return body.substring(start, body.indexOf("</tr>", at));
	}

	// ================================================================
	// ログイン必須
	// ================================================================

	@ParameterizedTest(name = "未ログインで GET {0} は、ログイン画面へ移動する")
	@ValueSource(strings = { "/comparison", "/comparison?year=2026&season=spring" })
	void notLoggedInIsRedirected(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	// ================================================================
	// 季節の指定と見出し・前後のリンク
	// ================================================================

	/** 季節を指定しないときは、今の季節（固定した Clock：2027年2月15日 ＝ 2026年の冬） */
	@Test
	void withoutSeasonShowsTheCurrentSeasonWhichIsTheWinterOfThePreviousYearInFebruary() throws Exception {
		Owner a = createOwner("ctrlcmp1");
		category(a, "食費");

		String body = open(a, null, null);

		assertThat(body).contains("2026年 冬", "（2026年12月〜2027年2月）");
		assertThat(body).contains("href=\"/comparison?year=2026&amp;season=autumn\">前の季節");
		assertThat(body).contains("href=\"/comparison?year=2027&amp;season=spring\">次の季節");
	}

	/** issue 020：本体の幅は 1280px（table-wide） */
	@Test
	void mainIsTableWideSo1280px() throws Exception {
		Owner a = createOwner("ctrlcmp6");

		String body = open(a, null, null);

		assertThat(body).contains("<main class=\"container table-wide\"");
	}

	/** 「戻る」で覚えていた画面を開き直す JavaScript（別ファイル。設計書 7.10）を、ログイン中の画面も読む */
	@Test
	void loadsTheBackReloadScriptAsAnExternalFile() throws Exception {
		Owner a = createOwner("ctrlcmp4");

		String body = open(a, null, null);

		assertThat(body).contains("src=\"/js/back-reload.js\"");
		assertThat(body).doesNotContainPattern("<script(?![^>]*\ssrc=)");
	}

	/** エラー画面（404）も同じ枠なので、JavaScript を読む */
	@Test
	void errorScreenLoadsTheBackReloadScriptToo() throws Exception {
		Owner a = createOwner("ctrlcmp5");

		String body = mockMvc.perform(get("/comparison").param("year", "1999").param("season", "winter").with(a.login()))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

		assertThat(body).contains("ページが見つかりません", "src=\"/js/back-reload.js\"");
	}

	@ParameterizedTest(name = "year={0} season={1} → 「{2}」「{3}」、前は {4}、次は {5}")
	@CsvSource(delimiter = '|', value = {
			"2026|spring|2026年 春|（3月〜5月）|year=2025&amp;season=winter|year=2026&amp;season=summer",
			"2026|summer|2026年 夏|（6月〜8月）|year=2026&amp;season=spring|year=2026&amp;season=autumn",
			"2026|autumn|2026年 秋|（9月〜11月）|year=2026&amp;season=summer|year=2026&amp;season=winter",
			"2026|winter|2026年 冬|（2026年12月〜2027年2月）|year=2026&amp;season=autumn|year=2027&amp;season=spring",
			"2027|winter|2027年 冬|（2027年12月〜2028年2月）|year=2027&amp;season=autumn|year=2028&amp;season=spring"
	})
	void headingAndLinks(String year, String season, String title, String period, String prev, String next) throws Exception {
		Owner a = createOwner("ctrlcmp2");

		String body = open(a, year, season);

		assertThat(body).contains(title, period);
		assertThat(body).contains("href=\"/comparison?" + prev + "\">前の季節");
		assertThat(body).contains("href=\"/comparison?" + next + "\">次の季節");
	}

	/** 範囲の端：2000年春は「前の季節」を、2100年冬は「次の季節」を出さない。端のすぐ内側は両方出る */
	@Test
	void edgesOfTheRangeHaveNoLinkOutOfTheRange() throws Exception {
		Owner a = createOwner("ctrlcmp3");

		String first = open(a, "2000", "spring");
		assertThat(first).doesNotContain("前の季節");
		assertThat(first).contains("href=\"/comparison?year=2000&amp;season=summer\">次の季節");

		String last = open(a, "2100", "winter");
		assertThat(last).doesNotContain("次の季節");
		assertThat(last).contains("href=\"/comparison?year=2100&amp;season=autumn\">前の季節");
		assertThat(last).contains("（2100年12月〜2101年2月）");

		assertThat(open(a, "2100", "autumn")).contains("次の季節", "前の季節");
		String afterFirst = open(a, "2000", "winter");
		assertThat(afterFirst).contains("href=\"/comparison?year=2001&amp;season=spring\">次の季節",
				"href=\"/comparison?year=2000&amp;season=autumn\">前の季節");
	}

	// ================================================================
	// 年と季節を選ぶ欄（GET のフォーム。設計書 2.4、Phase 8 の G1）
	// ================================================================

	/** 季節を指定しないときは、今の季節（固定した Clock：2026年の冬）が選ばれている */
	@Test
	void seasonSelectShowsTheCurrentSeasonWhenNotSpecified() throws Exception {
		Owner a = createOwner("ctrlcmp4");
		category(a, "食費");

		SeasonSelectAssertions.assertSeasonSelect(open(a, null, null), "/comparison", 2026, "winter");
	}

	@ParameterizedTest(name = "year={0} season={1} のとき、選ばれているのは同じ年と季節")
	@CsvSource({ "2026,spring", "2026,summer", "2026,autumn", "2026,winter", "2000,spring", "2100,winter", "2027,winter" })
	void seasonSelectShowsTheDisplayedSeason(int year, String season) throws Exception {
		Owner a = createOwner("ctrlcmp5");

		SeasonSelectAssertions.assertSeasonSelect(open(a, String.valueOf(year), season), "/comparison", year, season);
	}

	/** 範囲の端でも選ぶ欄は出る（リンクだけが、範囲外を指さないように出ない）。前・次のリンクも残っている */
	@Test
	void seasonSelectIsShownAtTheEdgesAndTheLinksRemain() throws Exception {
		Owner a = createOwner("ctrlcmp6");

		String first = open(a, "2000", "spring");
		assertThat(first).doesNotContain("前の季節").contains("次の季節");
		SeasonSelectAssertions.assertSeasonSelect(first, "/comparison", 2000, "spring");

		String last = open(a, "2100", "winter");
		assertThat(last).doesNotContain("次の季節").contains("前の季節");
		SeasonSelectAssertions.assertSeasonSelect(last, "/comparison", 2100, "winter");
	}

	/** 科目が0件でも、選ぶ欄は出る（案内と一緒に） */
	@Test
	void seasonSelectIsShownEvenWithoutCategories() throws Exception {
		Owner a = createOwner("ctrlcmp7");

		String body = open(a, null, null);

		assertThat(body).contains("先に科目を追加してください");
		SeasonSelectAssertions.assertSeasonSelect(body, "/comparison", 2026, "winter");
	}

	// ================================================================
	// 「今の季節」ボタン（Phase 8 の issue 007、設計書 2.4）
	// ================================================================

	/** 季節を指定しないとき（今の季節を見ているとき）も、ほかの季節を見ているときも、出る */
	@ParameterizedTest(name = "「今の季節」が出る：year={0} season={1}")
	@CsvSource(nullValues = "none", value = { "none,none", "2026,winter", "2030,summer", "2027,winter" })
	void currentSeasonLinkIsShownWhetherOrNotTheCurrentSeasonIsDisplayed(String year, String season) throws Exception {
		Owner a = createOwner("ctrlcmp8");
		category(a, "食費");

		SeasonSelectAssertions.assertCurrentSeasonLink(open(a, year, season), "/comparison");
	}

	/** 範囲の端（2000年春、2100年冬）でも出る */
	@ParameterizedTest(name = "範囲の端でも「今の季節」が出る：{0}年 {1}")
	@CsvSource({ "2000,spring", "2100,winter" })
	void currentSeasonLinkIsShownAtTheEdges(String year, String season) throws Exception {
		Owner a = createOwner("ctrlcmp9");

		SeasonSelectAssertions.assertCurrentSeasonLink(open(a, year, season), "/comparison");
	}

	/** 科目が0件でも、見出しの行は出るので、ボタンも出る（案内と一緒に） */
	@Test
	void currentSeasonLinkIsShownEvenWithoutCategories() throws Exception {
		Owner a = createOwner("ctrlcmp10");

		String body = open(a, null, null);

		assertThat(body).contains("先に科目を追加してください");
		SeasonSelectAssertions.assertCurrentSeasonLink(body, "/comparison");
	}

	/** 選ぶ欄の送り先は、リンクと同じ形の URL。年と季節の組み合わせ（端を含む）を送ると、その季節が開く */
	@ParameterizedTest(name = "選ぶ欄から year={0} season={1} を送ると、その季節が開く")
	@CsvSource({ "2000,spring", "2000,winter", "2100,spring", "2100,winter", "2026,summer" })
	void submittingTheSeasonSelectOpensThatSeason(String year, String season) throws Exception {
		Owner a = createOwner("ctrlcmp8");

		assertThat(open(a, year, season)).contains(year + "年 ");
	}

	// ================================================================
	// 404：範囲外・不正・片方だけ・空文字（画面も状態コードも 404）
	// ================================================================

	private static final String[][] BAD_SEASON_PARAMS = {
			{ "1999", "winter" }, { "1999", "spring" }, { "2101", "spring" }, { "2101", "winter" },
			{ "abc", "spring" }, { "2026", "xyz" }, { "2026", "Spring" }, { "2026", "SPRING" },
			{ "", "" }, { "2026", "" }, { "", "spring" },
			{ "02026", "spring" }, { "+2026", "spring" }, { "2026.5", "spring" }, { "２０２６", "spring" }, { " 2026", "spring" },
			{ "2026", " spring" }, { "20260", "spring" }, { "0", "spring" }, { "-2026", "spring" }
	};

	@Test
	void badYearOrSeasonIsNotFound() throws Exception {
		Owner a = createOwner("ctrlcmp4");
		for (String[] p : BAD_SEASON_PARAMS) {
			mockMvc.perform(get("/comparison").param("year", p[0]).param("season", p[1]).with(a.login()))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error/404"));
		}
	}

	@Test
	void onlyOneOfYearAndSeasonIsNotFound() throws Exception {
		Owner a = createOwner("ctrlcmp5");
		mockMvc.perform(get("/comparison").param("year", "2026").with(a.login()))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(get("/comparison").param("season", "spring").with(a.login()))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
	}

	@Test
	void sameParameterTwiceIsNotFound() throws Exception {
		Owner a = createOwner("ctrlcmp6");
		mockMvc.perform(get("/comparison").param("year", "2026", "2027").param("season", "spring").with(a.login()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/comparison").param("year", "2026").param("season", "spring", "summer").with(a.login()))
				.andExpect(status().isNotFound());
	}

	// ================================================================
	// 表の中身：金額の形・未設定・合計行
	// ================================================================

	/** 科目の登録順に並び、金額が 6,000 の形（「円」は見出しにまとめた。issue 020）、差額のマイナスが -1,000、予算が未設定の行は「未設定」で差額が空欄。最下行が合計行 */
	@Test
	void rowsAreInRegistrationOrderWithFormattedAmountsAndTotalRowLast() throws Exception {
		Owner a = createOwner("ctrlcmp7");
		autumn(a, category(a, "交通費"), 5_000, 6_000);   // 超過：差額 -1,000円
		autumn(a, category(a, "食費"), 20_000, 1_500);    // 差額 18,500円
		autumn(a, category(a, "雑費"), 10_000, 5_000);    // 差額 5,000円
		autumn(a, category(a, "予算なし"), null, 700);    // 予算が未設定

		String body = open(a, "2026", "autumn");

		assertThat(body.indexOf("交通費")).isLessThan(body.indexOf("食費"));
		assertThat(body.indexOf("食費")).isLessThan(body.indexOf("雑費"));
		assertThat(body.indexOf("雑費")).isLessThan(body.indexOf("予算なし"));
		assertThat(body.indexOf("予算なし")).isLessThan(body.indexOf("合計"));

		assertThat(rowOf(body, "交通費")).contains("<td class=\"num\">5,000</td>", "<td class=\"num\">6,000</td>", ">-1,000<", "予算超え");
		assertThat(rowOf(body, "食費")).contains(">20,000<", ">1,500<", ">18,500<");
		assertThat(rowOf(body, "雑費")).contains(">10,000<", ">5,000<", ">5,000<");

		// 予算が未設定の行：予算「未設定」、実績は表示、差額は空欄
		String unset = rowOf(body, "予算なし");
		assertThat(unset).contains("<td class=\"num\">未設定</td>", "<td class=\"num\">700</td>").containsPattern("<td class=\"num diff-cell\">\\s*</td>");

		// 合計行：予算は設定済みの科目の合計、実績は全科目の合計、差額は予算 − 実績
		assertThat(rowOf(body, "合計")).contains("total-row", ">35,000<", ">13,200<", ">21,800<");
		assertThat(body).contains("チップ：予算超え（赤）／使い残し（青。予算の1/3以下）／予算内（予算の1/3を超え、予算以下）");
	}

	/** 前の季節の予算は引き継がない：予算がない季節は、全科目が「未設定」 */
	@Test
	void seasonWithoutBudgetsShowsUnsetForEveryCategory() throws Exception {
		Owner a = createOwner("ctrlcmp8");
		autumn(a, category(a, "食費"), 20_000, 1_500);
		autumn(a, category(a, "交通費"), 5_000, 0);

		String body = open(a, "2026", "winter");

		assertThat(rowOf(body, "食費")).contains(">未設定<", ">0<").doesNotContain("row-", "diff-over", "chip");
		assertThat(rowOf(body, "交通費")).contains(">未設定<").doesNotContain("row-", "diff-over", "chip");
		assertThat(rowOf(body, "合計")).contains(">未設定<", ">0<").doesNotContain("row-within", "diff-over", "chip");
		assertThat(body).doesNotContain("20,000", "5,000");
	}

	// ================================================================
	// 予算内のチップ（Phase 8 の issue 005 の 💮 を、issue 024 で置きかえた。設計書 2.3、5.9、Q8-27〜Q8-34）：今の状況（過去・未来の季節では「状況」）の列
	// ================================================================

	/** 予算内のチップ：あんず色の背景の札。花のアイコン（飾り）と、画面に見える文字「予算内」 */
	private static final String CHIP_WITHIN = "<span class=\"chip chip-within\"><span class=\"chip-icon\"><svg";

	/** 表の本体（&lt;tbody&gt; の中）。注記を含めずに、行だけを調べるため */
	private static String tbodyOf(String body) {
		int start = body.indexOf("<tbody>");
		assertThat(start).as("表の本体がある").isGreaterThanOrEqualTo(0);
		return body.substring(start, body.indexOf("</tbody>", start));
	}

	/** 行の右端のセル（最後の &lt;td&gt;）。右端は差額のセル */
	private static String lastCellOf(String row) {
		return row.substring(row.lastIndexOf("<td"));
	}

	/** 行の左端のセル（科目名のセル） */
	private static String firstCellOf(String row) {
		return row.substring(row.indexOf("<td"), row.indexOf("</td>") + "</td>".length());
	}

	/** 行の 2 番目のセル（今の状況・状況。チップが入る） */
	private static String statusCellOf(String row) {
		int first = row.indexOf("</td>") + "</td>".length();
		int start = row.indexOf("<td", first);
		return row.substring(start, row.indexOf("</td>", start) + "</td>".length());
	}

	private static int count(String text, String part) {
		return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
	}

	/** 予算内の行（色なしで実績がある。ちょうども）と合計行の、状況のセルに「予算内」のチップが出る。赤・青・予算が未設定・実績0円の行には出ない */
	@Test
	void withinBudgetRowsGetTheWithinChipInTheStatusColumn() throws Exception {
		Owner a = createOwner("ctrlcmp11");
		autumn(a, category(a, "超過"), 5_000, 6_000);        // 赤
		autumn(a, category(a, "余裕"), 20_000, 1_500);       // 青
		autumn(a, category(a, "ふつう"), 10_000, 5_000);     // 予算内（5,000×3 ＞ 10,000）
		autumn(a, category(a, "ちょうど"), 10_000, 10_000);  // 予算ちょうども予算内（Q8-29）
		autumn(a, category(a, "予算なし"), null, 700);        // 予算が未設定
		autumn(a, category(a, "実績なし"), 10_000, 0);        // 実績が0円（青）

		String body = open(a, "2026", "autumn");

		assertThat(statusCellOf(rowOf(body, "ふつう"))).contains(CHIP_WITHIN, "予算内</span>");
		assertThat(statusCellOf(rowOf(body, "ちょうど"))).contains(CHIP_WITHIN, "予算内</span>");
		for (String name : new String[] { "超過", "余裕", "予算なし", "実績なし" }) {
			assertThat(rowOf(body, name)).as(name).doesNotContain("chip-within", "予算内");
		}
		// 合計：予算 5,000＋20,000＋10,000＋10,000＋10,000 ＝ 55,000円、実績 6,000＋1,500＋5,000＋10,000＋700 ＝ 23,200円 → 色なしで予算内
		assertThat(statusCellOf(rowOf(body, "合計"))).contains(CHIP_WITHIN);
		assertThat(count(tbodyOf(body), "chip-within\"")).as("ふつう・ちょうど・合計の3か所").isEqualTo(3);
		assertThat(firstCellOf(rowOf(body, "ふつう"))).as("科目名のセルには、もう付かない（💮 は、なくなった）").doesNotContain("chip", "💮");
	}

	/** 過去の季節の行は 5 つのセル（科目・状況・予算・実績・差額）。見込みの列は今の季節だけ */
	@Test
	void everyRowHasFiveCellsWithTheStatusColumn() throws Exception {
		Owner a = createOwner("ctrlcmp12");
		autumn(a, category(a, "ふつう"), 10_000, 5_000);
		autumn(a, category(a, "余裕"), 20_000, 1_500);

		String body = open(a, "2026", "autumn");

		for (String name : new String[] { "ふつう", "余裕", "合計" }) {
			assertThat(count(rowOf(body, name), "<td")).as(name).isEqualTo(5);
		}
		assertThat(rowOf(body, "ふつう")).contains("<td class=\"num\">10,000</td>", "<td class=\"num\">5,000</td>");
		assertThat(lastCellOf(rowOf(body, "余裕"))).doesNotContain("💮", "visually-hidden", "chip");
	}

	/** 見出しの行は、過去の季節で 5 列。単位「円」は見出しにまとめる。「状況」の列を足した */
	@Test
	void theHeaderHasFiveColumnsWithTheYenInTheHeadings() throws Exception {
		Owner a = createOwner("ctrlcmp13");
		autumn(a, category(a, "食費"), 10_000, 5_000);

		String body = open(a, "2026", "autumn");

		String head = body.substring(body.indexOf("<thead>") + "<thead>".length(), body.indexOf("</thead>"));
		assertThat(count(head, "<th")).isEqualTo(5);
		assertThat(head).containsSubsequence("<th>科目</th>", "<th class=\"col-status\">状況</th>", "<th class=\"num col-budget\">予算（円）</th>",
				"<th class=\"num col-actual\">実績（円）</th>", "<th class=\"num col-diff\">差額（円）</th>");
		assertThat(head).doesNotContain("評価", "見込み");
	}

	/** 花のアイコンは飾りとして読ませず（aria-hidden）、「予算内」は画面に見える文字で出す（読み上げ用の隠し文字は使わない） */
	@Test
	void theWithinChipShowsTheWordAndHidesTheFlowerFromScreenReaders() throws Exception {
		Owner a = createOwner("ctrlcmp14");
		autumn(a, category(a, "食費"), 10_000, 5_000);

		String body = open(a, "2026", "autumn");

		String cell = statusCellOf(rowOf(body, "食費"));
		assertThat(cell).contains("aria-hidden=\"true\"", "</svg></span>予算内</span>");
		assertThat(rowOf(body, "食費")).doesNotContain("visually-hidden", "💮");
	}

	/** 注記に、チップの説明がある（💮 は、なくなった） */
	@Test
	void theLegendExplainsTheChips() throws Exception {
		Owner a = createOwner("ctrlcmp15");
		autumn(a, category(a, "食費"), 10_000, 5_000);

		String body = open(a, "2026", "autumn");

		assertThat(body).contains("チップ：予算超え（赤）／使い残し（青。予算の1/3以下）／予算内（予算の1/3を超え、予算以下）").doesNotContain("💮");
	}

	/** 予算が設定されていない季節（全科目が未設定）は、科目の行にも合計行にも出ない。科目が0件なら表ごと出ない */
	@Test
	void noChipWhenNothingIsWithinBudget() throws Exception {
		Owner a = createOwner("ctrlcmp16");
		autumn(a, category(a, "食費"), 20_000, 5_000);

		String unsetSeason = open(a, "2026", "winter");
		assertThat(tbodyOf(unsetSeason)).doesNotContain("chip", "予算内");

		Owner empty = createOwner("ctrlcmp17");
		String noCategories = open(empty, "2026", "autumn");
		assertThat(noCategories).doesNotContain("<table", "chip-within");
	}

	/** 行に、予算内の背景のクラス（row-within）は付かない。合計行だけ total-row。チップは、予算内の行（合計行も）に 3 つ（Q8-39 を置きかえた） */
	@Test
	void onlyTheTotalRowHasARowClassAndTheWithinChipsAreOnTheWithinRows() throws Exception {
		Owner a = createOwner("ctrlcmp18");
		autumn(a, category(a, "超過"), 5_000, 6_000);          // 赤
		autumn(a, category(a, "余裕"), 20_000, 1_500);         // 青
		autumn(a, category(a, "ふつう"), 10_000, 5_000);       // 予算内
		autumn(a, category(a, "ちょうど"), 10_000, 10_000);    // 予算ちょうど：予算内（Q8-29）
		autumn(a, category(a, "予算なし"), null, 700);          // 未設定
		autumn(a, category(a, "実績なし"), 10_000, 0);          // 実績0円（青）
		autumn(a, category(a, "ゼロ"), 0, 0);                  // 予算0円・実績0円（色なし）

		String body = open(a, "2026", "autumn");

		assertThat(tbodyOf(body)).doesNotContain("row-within");
		for (String name : new String[] { "超過", "余裕", "ふつう", "ちょうど", "予算なし", "実績なし", "ゼロ" }) {
			assertThat(rowOf(body, name)).as(name).startsWith("<tr>");
		}
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row\">");
		assertThat(rowOf(body, "ゼロ")).as("予算0円・実績0円は、チップなし").doesNotContain("chip");
		assertThat(count(tbodyOf(body), "chip-within\"")).as("ふつう・ちょうど・合計の3か所").isEqualTo(3);
	}

	/** 合計行が赤・青のときは、予算内のチップは付かず、合計の状況のチップ（予算超え・使い残し）が付く。赤い数字は、合計の差額のセルだけ */
	@Test
	void totalRowHasItsOwnChipAndOnlyTheDifferenceCellIsRedWhenItIsNotWithinBudget() throws Exception {
		Owner blue = createOwner("ctrlcmp19");
		autumn(blue, category(blue, "食費"), 30_000, 1_000);
		String blueTotal = rowOf(open(blue, "2026", "autumn"), "合計");
		assertThat(blueTotal).startsWith("<tr class=\"total-row\">").contains("chip-low", "使い残し").doesNotContain("chip-within", "diff-over");

		Owner red = createOwner("ctrlcmp20");
		autumn(red, category(red, "食費"), 30_000, 30_001);
		String redTotal = rowOf(open(red, "2026", "autumn"), "合計");
		assertThat(redTotal).startsWith("<tr class=\"total-row\">").contains("chip-over", "予算超え", "diff-over").doesNotContain("chip-within", "chip-low");
	}

	/** style.css に、チップの規則がある。行の背景と 💮 の規則は、なくなった（クラス名の食い違いに気づくため。色の値は ColorSchemeStyleSheetTest で確かめる） */
	@Test
	void styleSheetHasTheChipRulesAndNoLongerTheWithinRowRules() throws Exception {
		String css = mockMvc.perform(get("/css/style.css")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

		assertThat(css).contains(".chip-over", ".chip-low", ".chip-within", ".chip-icon");
		assertThat(css).doesNotContain(".comparison-table tr.row-within", ".comparison-table .within-mark");
	}

	/** XSS 対策（設計書 7.5）：科目名の < > は文字として出る。タグとしては出ない（科目名が表の行に出る画面。Phase 8 の 4.14） */
	@Test
	void categoryNamesAreEscaped() throws Exception {
		Owner a = createOwner("ctrlcmp22");
		autumn(a, category(a, "<b>x</b>"), 10_000, 5_000);
		autumn(a, category(a, "<script>"), 10_000, 5_000);   // 科目名は10文字まで

		String body = open(a, "2026", "autumn");

		assertThat(body).contains("&lt;b&gt;x&lt;/b&gt;", "&lt;script&gt;");
		assertThat(body).doesNotContain("<b>x</b>");
		assertThat(body.replace("<script defer", "")).as("科目名の <script> がタグとして出ていない（画面の枠の script は src 付き）").doesNotContain("<script>");
	}

	// ================================================================
	// チップと赤い数字：状況のチップ（予算超え・使い残し・予算内）と、差額のセルの赤（issue 024 で、文字の印を置きかえた）。合計行も同じ
	// ================================================================

	/** 赤・青・予算内は、状況のセルのチップ（chip-over・chip-low・chip-within）。差額のセルの赤（diff-over）は、赤のときだけ。青の数字はない。<tr> にはクラスがない。予算が未設定の行はチップも色もない */
	@Test
	void theChipIsInTheStatusCellAndOnlyRedHasARedNumber() throws Exception {
		Owner a = createOwner("ctrlcmp9");
		autumn(a, category(a, "超過"), 5_000, 6_000);       // 赤
		autumn(a, category(a, "余裕"), 20_000, 1_500);      // 青
		autumn(a, category(a, "ふつう"), 10_000, 5_000);    // 色なしで予算内（5,000×3 ＞ 10,000）
		autumn(a, category(a, "予算なし"), null, 700);       // 予算が未設定：色なし、チップなし

		String body = open(a, "2026", "autumn");

		String over = rowOf(body, "超過");
		assertThat(over).startsWith("<tr>").contains("chip-over", "予算超え", "diff-over").doesNotContain("chip-low", "使い残し", "chip-within");
		assertThat(count(over, "diff-over")).as("赤い数字のクラスは差額のセルだけ").isEqualTo(1);
		assertThat(statusCellOf(over)).contains("<span class=\"chip chip-over\">予算超え</span>");
		String low = rowOf(body, "余裕");
		assertThat(low).startsWith("<tr>").contains("chip-low", "使い残し").doesNotContain("diff-over", "diff-low", "chip-over", "予算超え");
		assertThat(statusCellOf(low)).contains("<span class=\"chip chip-low\">使い残し</span>");
		String normal = rowOf(body, "ふつう");
		assertThat(normal).startsWith("<tr>").contains("chip-within").doesNotContain("diff-over", "diff-low", "chip-over", "chip-low", "予算超え", "使い残し");
		assertThat(rowOf(body, "予算なし")).startsWith("<tr>").doesNotContain("diff-over", "diff-low", "chip");
		// 合計行：予算 35,000、実績 13,200 → 色なしで予算内（13,200×3 ＞ 35,000）
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row\">").contains("chip-within");
	}

	@Test
	void totalRowIsBlueWhenTheTotalActualIsAThirdOrLess() throws Exception {
		Owner a = createOwner("ctrlcmp10");
		autumn(a, category(a, "食費"), 30_000, 0);

		String body = open(a, "2026", "autumn");

		assertThat(rowOf(body, "食費")).startsWith("<tr>").contains("chip-low", "使い残し");
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row\">").contains("chip-low", "使い残し");
	}

	@Test
	void totalRowIsRedWhenTheTotalActualExceedsTheTotalBudget() throws Exception {
		Owner a = createOwner("ctrlcmp11");
		autumn(a, category(a, "食費"), 1_000, 2_000);

		String body = open(a, "2026", "autumn");

		assertThat(rowOf(body, "食費")).startsWith("<tr>").contains("chip-over", "予算超え", "diff-over");
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row\">").contains("chip-over", "予算超え", "diff-over");
		assertThat(rowOf(body, "合計")).contains(">-1,000<");
	}

	// ================================================================
	// 無害化・メニュー・他人のデータ・読むだけ・科目0件
	// ================================================================

	@Test
	void categoryNameIsEscapedAndMenuHasTheComparisonLink() throws Exception {
		Owner a = createOwner("ctrlcmp12");
		category(a, "<b>x</b>");

		String body = open(a, "2026", "autumn");

		assertThat(body).doesNotContain("<b>x</b>");
		assertThat(body).contains("&lt;b&gt;x&lt;/b&gt;");
		assertThat(body).as("予実比較の画面では、メニューの「予実比較」が、今開いている項目として示される（issue 015）").containsPattern(java.util.regex.Pattern.compile("<a href=\"/comparison\" aria-current=\"page\"><svg[^>]*>.*?</svg>予実比較</a>", java.util.regex.Pattern.DOTALL));
	}

	/** 他のユーザーの科目・予算・取引は、出ない（持ち主は、ログイン中のユーザー） */
	@Test
	void otherUsersDataIsNotShown() throws Exception {
		Owner a = createOwner("ctrlcmp13");
		Owner b = createOwner("ctrlcmp14");
		autumn(a, category(a, "食費"), 30_000, 1_500);
		autumn(b, category(b, "他人の科目"), 99_999, 88_888);
		autumn(b, category(b, "食費"), 77_777, 66_666);

		String body = open(a, "2026", "autumn");

		assertThat(body).doesNotContain("他人の科目", "99,999", "88,888", "77,777", "66,666");
		assertThat(rowOf(body, "食費")).contains(">30,000<", ">1,500<", ">28,500<");
		assertThat(rowOf(body, "合計")).contains(">30,000<", ">1,500<");
	}

	@Test
	void openingTheScreenDoesNotChangeBudgetsOrTransactions() throws Exception {
		Owner a = createOwner("ctrlcmp15");
		autumn(a, category(a, "食費"), 30_000, 1_500);
		entityManager.flush();
		long budgets = budgetRepository.count();
		long transactions = transactionRepository.count();

		open(a, "2026", "autumn");
		open(a, "2026", "winter");
		open(a, null, null);

		assertThat(budgetRepository.count()).isEqualTo(budgets);
		assertThat(transactionRepository.count()).isEqualTo(transactions);
	}

	/** 科目が0件：案内と科目の画面へのリンクを出し、表と合計行は出さない。見出しと前後のリンクは出す（Q7-7 の A） */
	@Test
	void withoutCategoriesShowsGuidanceInsteadOfTheTable() throws Exception {
		Owner a = createOwner("ctrlcmp16");

		String body = open(a, "2026", "autumn");

		assertThat(body).contains("先に科目を追加してください", "href=\"/categories\">科目の画面へ");
		assertThat(body).doesNotContain("<table", "合計", "赤：予算超過");
		assertThat(body).contains("2026年 秋", "前の季節", "次の季節");
	}

}
