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

	/** 科目の登録順に並び、金額が 6,000円 の形、差額のマイナスが -1,000円、予算が未設定の行は「未設定」で差額が空欄。最下行が合計行 */
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

		assertThat(rowOf(body, "交通費")).contains("<td class=\"num\">5,000円</td>", "<td class=\"num\">6,000円</td>", "<td class=\"num\">-1,000円</td>");
		assertThat(rowOf(body, "食費")).contains(">20,000円<", ">1,500円<", ">18,500円<");
		assertThat(rowOf(body, "雑費")).contains(">10,000円<", ">5,000円<", ">5,000円<");

		// 予算が未設定の行：予算「未設定」、実績は表示、差額は空欄
		String unset = rowOf(body, "予算なし");
		assertThat(unset).contains("<td class=\"num\">未設定</td>", "<td class=\"num\">700円</td>", "<td class=\"num\"></td>");

		// 合計行：予算は設定済みの科目の合計、実績は全科目の合計、差額は予算 − 実績
		assertThat(rowOf(body, "合計")).contains("total-row", ">35,000円<", ">13,200円<", ">21,800円<");
		assertThat(body).contains("赤：予算超過／青：予算の1/3以下");
	}

	/** 前の季節の予算は引き継がない：予算がない季節は、全科目が「未設定」 */
	@Test
	void seasonWithoutBudgetsShowsUnsetForEveryCategory() throws Exception {
		Owner a = createOwner("ctrlcmp8");
		autumn(a, category(a, "食費"), 20_000, 1_500);
		autumn(a, category(a, "交通費"), 5_000, 0);

		String body = open(a, "2026", "winter");

		assertThat(rowOf(body, "食費")).contains(">未設定<", ">0円<").doesNotContain("row-");
		assertThat(rowOf(body, "交通費")).contains(">未設定<").doesNotContain("row-");
		assertThat(rowOf(body, "合計")).contains(">未設定<", ">0円<").doesNotContain("row-over", "row-low");
		assertThat(body).doesNotContain("20,000円", "5,000円");
	}

	// ================================================================
	// 色：行の文字全体に付ける（F4）。合計行も同じ
	// ================================================================

	/** 赤・青・色なし・予算が未設定の行（色なし）。クラスは <tr> に付く（行の文字全体に効く） */
	@Test
	void colorClassIsOnTheWholeRow() throws Exception {
		Owner a = createOwner("ctrlcmp9");
		autumn(a, category(a, "超過"), 5_000, 6_000);       // 赤
		autumn(a, category(a, "余裕"), 20_000, 1_500);      // 青
		autumn(a, category(a, "ふつう"), 10_000, 5_000);    // 色なし（5,000×3 ＞ 10,000）
		autumn(a, category(a, "予算なし"), null, 700);       // 予算が未設定：色なし

		String body = open(a, "2026", "autumn");

		assertThat(rowOf(body, "超過")).startsWith("<tr class=\"row-over\">");
		assertThat(rowOf(body, "余裕")).startsWith("<tr class=\"row-low\">");
		assertThat(rowOf(body, "ふつう")).startsWith("<tr>");
		assertThat(rowOf(body, "予算なし")).startsWith("<tr>");
		// 合計行：予算 35,000、実績 13,200 → 色なし（13,200×3 ＞ 35,000）
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row\">");
	}

	@Test
	void totalRowIsBlueWhenTheTotalActualIsAThirdOrLess() throws Exception {
		Owner a = createOwner("ctrlcmp10");
		autumn(a, category(a, "食費"), 30_000, 0);

		String body = open(a, "2026", "autumn");

		assertThat(rowOf(body, "食費")).startsWith("<tr class=\"row-low\">");
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row row-low\">");
	}

	@Test
	void totalRowIsRedWhenTheTotalActualExceedsTheTotalBudget() throws Exception {
		Owner a = createOwner("ctrlcmp11");
		autumn(a, category(a, "食費"), 1_000, 2_000);

		String body = open(a, "2026", "autumn");

		assertThat(rowOf(body, "食費")).startsWith("<tr class=\"row-over\">");
		assertThat(rowOf(body, "合計")).startsWith("<tr class=\"total-row row-over\">");
		assertThat(rowOf(body, "合計")).contains(">-1,000円<");
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
		assertThat(body).contains("<a href=\"/comparison\">予実比較</a>");
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
		assertThat(rowOf(body, "食費")).contains(">30,000円<", ">1,500円<", ">28,500円<");
		assertThat(rowOf(body, "合計")).contains(">30,000円<", ">1,500円<");
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
