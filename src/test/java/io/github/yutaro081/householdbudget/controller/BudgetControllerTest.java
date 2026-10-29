package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * BudgetController（予算設定の画面）のテスト（設計書 2.2〜2.4、2.6、3.9、5.7、6.5、7.8、Q6-1〜Q6-3）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は、日本時間の 2027-02-15（2026年の冬）に固定した Clock で確かめる（冬の2月は前の年の冬、設計書 5.8）。
 * テストのパスワードのハッシュは、ダミーの値。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetControllerTest {

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
	private BudgetRepository budgetRepository;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long userId, String username, Category food, Category fare) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@BeforeEach
	void createUsers() {
		a = createOwner("ctrlbud1");
		b = createOwner("ctrlbud2");
		asA = user(new LoginUser(a.userId(), a.username(), "x"));
		asB = user(new LoginUser(b.userId(), b.username(), "x"));
		// 初期ユーザーと同じ：今の季節ではなく、2026年の秋に、食費 30,000円・交通費 0円（保存済み）
		saved(a, a.food(), 2026, Season.AUTUMN, 30000);
		saved(a, a.fare(), 2026, Season.AUTUMN, 0);
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, username,
				categoryRepository.save(new Category(id, "食費")), categoryRepository.save(new Category(id, "交通費")));
	}

	private void saved(Owner o, Category category, int year, Season season, int amount) {
		budgetRepository.save(new Budget(o.userId(), category, year, season, amount));
		entityManager.flush();
		entityManager.clear();
	}

	private String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	/** そのユーザーの予算の行を、全部の季節ぶん取り出す（保存前の状態を見ないよう、読み直す） */
	private List<Budget> allOf(Owner o) {
		entityManager.flush();
		entityManager.clear();
		return budgetRepository.findByUserIdAndSeasonYearLessThan(o.userId(), 100000);
	}

	private List<Budget> seasonOf(Owner o, int year, Season season) {
		entityManager.flush();
		entityManager.clear();
		return budgetRepository.findByUserIdAndSeasonYearAndSeason(o.userId(), year, season);
	}

	private static int amountOf(List<Budget> rows, Category category) {
		return rows.stream().filter(r -> r.getCategory().getId().equals(category.getId())).findFirst().orElseThrow().getAmount();
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/**
	 * 保存の POST を作る。年・季節が null のときは、そのパラメータを付けない。
	 * idAndAmount は「科目ID, 金額, 科目ID, 金額, …」。金額が null のときは、その行の金額のパラメータを付けない。
	 * （MockMvc の param は同じ名前を置き換えず追加するので、最初から組み立てる）
	 */
	private MockHttpServletRequestBuilder save(String year, String season, Object... idAndAmount) {
		MockHttpServletRequestBuilder builder = post("/budgets").with(csrf());
		if (year != null) {
			builder.param("year", year);
		}
		if (season != null) {
			builder.param("season", season);
		}
		for (int i = 0; i < idAndAmount.length; i += 2) {
			int row = i / 2;
			if (idAndAmount[i] != null) {
				builder.param("rows[" + row + "].categoryId", String.valueOf(idAndAmount[i]));
			}
			if (idAndAmount[i + 1] != null) {
				builder.param("rows[" + row + "].amount", String.valueOf(idAndAmount[i + 1]));
			}
		}
		return builder;
	}

	// ================================================================
	// ログイン必須・CSRF
	// ================================================================

	@ParameterizedTest(name = "未ログインで GET {0} は、ログイン画面へ移動する")
	@ValueSource(strings = { "/budgets", "/budgets?year=2026&season=spring" })
	void notLoggedInIsRedirected(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@Test
	void notLoggedInPostIsRedirected() throws Exception {
		mockMvc.perform(save("2026", "spring", a.food().getId(), 100))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
		assertThat(seasonOf(a, 2026, Season.SPRING)).isEmpty();
	}

	@Test
	void postWithoutCsrfTokenIsForbiddenAndSavesNothing() throws Exception {
		mockMvc.perform(post("/budgets").with(asA).param("year", "2026").param("season", "winter")
				.param("rows[0].categoryId", String.valueOf(a.food().getId())).param("rows[0].amount", "100"))
				.andExpect(status().isForbidden());

		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	// ================================================================
	// 季節の指定と見出し・前後のリンク
	// ================================================================

	/** 季節を指定しないときは、今の季節（固定した Clock：2027年2月15日 ＝ 2026年の冬） */
	@Test
	void withoutSeasonShowsTheCurrentSeasonWhichIsTheWinterOfThePreviousYearInFebruary() throws Exception {
		String body = body(mockMvc.perform(get("/budgets").with(asA))
				.andExpect(status().isOk()).andExpect(view().name("budgets/form")).andReturn());

		assertThat(body).contains("2026年 冬", "（2026年12月〜2027年2月）");
		assertThat(body).contains("action=\"/budgets?year=2026&amp;season=winter\"");
		assertThat(body).contains("href=\"/budgets?year=2026&amp;season=autumn\">前の季節");
		assertThat(body).contains("href=\"/budgets?year=2027&amp;season=spring\">次の季節");
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
		String body = body(mockMvc.perform(get("/budgets").param("year", year).param("season", season).with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains(title, period);
		assertThat(body).contains("href=\"/budgets?" + prev + "\">前の季節");
		assertThat(body).contains("href=\"/budgets?" + next + "\">次の季節");
		assertThat(body).contains("action=\"/budgets?year=" + year + "&amp;season=" + season + "\"");
	}

	/** 範囲の端：2000年春は「前の季節」を、2100年冬は「次の季節」を出さない。端のすぐ内側は両方出る */
	@Test
	void edgesOfTheRangeHaveNoLinkOutOfTheRange() throws Exception {
		String first = body(mockMvc.perform(get("/budgets").param("year", "2000").param("season", "spring").with(asA))
				.andExpect(status().isOk()).andReturn());
		assertThat(first).doesNotContain("前の季節");
		assertThat(first).contains("href=\"/budgets?year=2000&amp;season=summer\">次の季節");

		String last = body(mockMvc.perform(get("/budgets").param("year", "2100").param("season", "winter").with(asA))
				.andExpect(status().isOk()).andReturn());
		assertThat(last).doesNotContain("次の季節");
		assertThat(last).contains("href=\"/budgets?year=2100&amp;season=autumn\">前の季節");
		assertThat(last).contains("（2100年12月〜2101年2月）");

		String beforeLast = body(mockMvc.perform(get("/budgets").param("year", "2100").param("season", "autumn").with(asA)).andReturn());
		assertThat(beforeLast).contains("次の季節", "前の季節");
		String afterFirst = body(mockMvc.perform(get("/budgets").param("year", "2000").param("season", "winter").with(asA)).andReturn());
		assertThat(afterFirst).contains("href=\"/budgets?year=2001&amp;season=spring\">次の季節",
				"href=\"/budgets?year=2000&amp;season=autumn\">前の季節");
	}

	// ================================================================
	// 404：範囲外・不正・片方だけ・空文字（GET も POST も、何も保存しない）
	// ================================================================

	private static final String[][] BAD_SEASON_PARAMS = {
			{ "1999", "winter" }, { "1999", "spring" }, { "2101", "spring" }, { "2101", "winter" },
			{ "abc", "spring" }, { "2026", "xyz" }, { "2026", "Spring" }, { "2026", "SPRING" },
			{ "", "" }, { "2026", "" }, { "", "spring" },
			{ "02026", "spring" }, { "+2026", "spring" }, { "2026.5", "spring" }, { "２０２６", "spring" }, { " 2026", "spring" },
			{ "2026", " spring" }, { "20260", "spring" }, { "0", "spring" }, { "-2026", "spring" }
	};

	@Test
	void badYearOrSeasonIsNotFoundOnGet() throws Exception {
		for (String[] p : BAD_SEASON_PARAMS) {
			mockMvc.perform(get("/budgets").param("year", p[0]).param("season", p[1]).with(asA))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error/404"));
		}
	}

	@Test
	void onlyOneOfYearAndSeasonIsNotFound() throws Exception {
		mockMvc.perform(get("/budgets").param("year", "2026").with(asA)).andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(get("/budgets").param("season", "spring").with(asA)).andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(save("2026", null, a.food().getId(), 100).with(asA)).andExpect(status().isNotFound());
		mockMvc.perform(save(null, "spring", a.food().getId(), 100).with(asA)).andExpect(status().isNotFound());
		assertThat(allOf(a)).hasSize(2);
	}

	@Test
	void sameParameterTwiceIsNotFound() throws Exception {
		mockMvc.perform(get("/budgets").param("year", "2026", "2027").param("season", "spring").with(asA))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/budgets").param("year", "2026").param("season", "spring", "summer").with(asA))
				.andExpect(status().isNotFound());
	}

	@Test
	void badYearOrSeasonIsNotFoundOnPostAndSavesNothing() throws Exception {
		for (String[] p : BAD_SEASON_PARAMS) {
			mockMvc.perform(save(p[0], p[1], a.food().getId(), 100, a.fare().getId(), 200).with(asA))
					.andExpect(status().isNotFound())
					.andExpect(view().name("error/404"));
		}
		assertThat(allOf(a)).hasSize(2);       // 最初の2行（秋）だけ。増えていない
	}

	/** 404 の画面は、範囲外のとき、状態コードも 404（画面だけでなく） */
	@Test
	void notFoundHasStatus404() throws Exception {
		mockMvc.perform(get("/budgets").param("year", "1999").param("season", "winter").with(asA))
				.andExpect(status().is(404));
	}

	// ================================================================
	// 表示
	// ================================================================

	@Test
	void savedSeasonShowsSavedAmountsWithoutMark() throws Exception {
		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "autumn").with(asA))
				.andExpect(status().isOk()).andExpect(view().name("budgets/form")).andReturn());

		assertThat(body).contains("name=\"rows[0].amount\" value=\"30000\"", "name=\"rows[1].amount\" value=\"0\"");
		assertThat(body).doesNotContain("（この季節は未設定）");
		assertThat(body).contains("name=\"rows[0].categoryId\" value=\"" + a.food().getId() + "\"",
				"name=\"rows[1].categoryId\" value=\"" + a.fare().getId() + "\"");
	}

	/** 次の季節（冬）：行がないので、前の季節（秋）の額が初期値で、「未設定」の印が出る */
	@Test
	void nextSeasonShowsPreviousAmountsWithMark() throws Exception {
		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "winter").with(asA)).andReturn());

		assertThat(body).contains("name=\"rows[0].amount\" value=\"30000\"", "name=\"rows[1].amount\" value=\"0\"");
		assertThat(count(body, "（この季節は未設定）")).isEqualTo(2);
	}

	@Test
	void seasonBeforeAllSavedRowsShowsZeroWithMark() throws Exception {
		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "summer").with(asA)).andReturn());

		assertThat(body).contains("name=\"rows[0].amount\" value=\"0\"", "name=\"rows[1].amount\" value=\"0\"");
		assertThat(count(body, "（この季節は未設定）")).isEqualTo(2);
	}

	@Test
	void rowsAreInRegistrationOrderAndOnlyOwn() throws Exception {
		categoryRepository.save(new Category(b.userId(), "他人の科目"));

		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "autumn").with(asA)).andReturn());

		assertThat(body).doesNotContain("他人の科目");
		assertThat(body.indexOf("食費")).isLessThan(body.indexOf("交通費"));
		assertThat(body).contains(a.username());       // ヘッダーのユーザー名
	}

	@Test
	void pageHasMenuTitleFormAndNoCommasOrYenInInputs() throws Exception {
		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "autumn").with(asA)).andReturn());

		assertThat(body).contains("href=\"/budgets\">予算");             // メニューの「予算」
		assertThat(body).contains("予算設定", "保存");
		assertThat(body).contains("name=\"_csrf\"");
		assertThat(body).containsPattern("name=\"rows\\[0\\]\\.amount\" value=\"[0-9]+\"");   // 入力欄は数字だけ（カンマ・円なし）
		assertThat(body).doesNotContain("value=\"30,000").doesNotContain("円\"");
	}

	@Test
	void categoryNamesAreEscaped() throws Exception {
		Category tricky = categoryRepository.save(new Category(a.userId(), "<i>x</i>"));
		entityManager.flush();
		entityManager.clear();

		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "autumn").with(asA)).andReturn());

		assertThat(body).contains("&lt;i&gt;x&lt;/i&gt;").doesNotContain("<i>x</i>");
		assertThat(tricky.getId()).isNotNull();
	}

	@Test
	void noCategoriesShowsGuidanceAndNoSaveButton() throws Exception {
		Long id = userRepository.save(new User("ctrlbud3", "dummy-hash-for-test")).getId();
		RequestPostProcessor asEmpty = user(new LoginUser(id, "ctrlbud3", "x"));

		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "autumn").with(asEmpty))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("先に科目を追加してください", "href=\"/categories\"");
		assertThat(body).doesNotContain("<button type=\"submit\">保存").doesNotContain("action=\"/budgets?");
		assertThat(body).contains("2026年 秋");      // 見出しは出る
	}

	@Test
	void successMessageIsShown() throws Exception {
		String body = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "autumn").with(asA)
				.flashAttr("successMessageKey", "success.saved")).andReturn());

		assertThat(body).contains("保存しました");
	}

	/** 開いただけでは、行が増えない（設計書 5.7、Q25） */
	@Test
	void showingDoesNotCreateRows() throws Exception {
		for (String[] p : new String[][] { { "2026", "autumn" }, { "2026", "winter" }, { "2027", "spring" }, { "2000", "spring" }, { "2100", "winter" } }) {
			mockMvc.perform(get("/budgets").param("year", p[0]).param("season", p[1]).with(asA)).andExpect(status().isOk());
		}
		mockMvc.perform(get("/budgets").with(asA)).andExpect(status().isOk());

		assertThat(allOf(a)).hasSize(2);
	}

	// ================================================================
	// 保存
	// ================================================================

	@Test
	void saveSucceedsRedirectsToTheSameSeasonAndCreatesAllRows() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 50000, a.fare().getId(), 30000).with(asA))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/budgets?year=2026&season=winter"))
				.andExpect(flash().attribute("successMessageKey", "success.saved"));

		List<Budget> winter = seasonOf(a, 2026, Season.WINTER);
		assertThat(winter).hasSize(2);
		assertThat(amountOf(winter, a.food())).isEqualTo(50000);
		assertThat(amountOf(winter, a.fare())).isEqualTo(30000);
		assertThat(winter).allMatch(r -> r.getUserId().equals(a.userId()));
		assertThat(allOf(a)).hasSize(4);            // 秋の2行は変わらず残る
	}

	@Test
	void redirectGoesToTheSavedSeasonForEverySeason() throws Exception {
		mockMvc.perform(save("2027", "spring", a.food().getId(), 1).with(asA)).andExpect(redirectedUrl("/budgets?year=2027&season=spring"));
		mockMvc.perform(save("2026", "summer", a.food().getId(), 1).with(asA)).andExpect(redirectedUrl("/budgets?year=2026&season=summer"));
		mockMvc.perform(save("2000", "spring", a.food().getId(), 1).with(asA)).andExpect(redirectedUrl("/budgets?year=2000&season=spring"));
		mockMvc.perform(save("2100", "winter", a.food().getId(), 1).with(asA)).andExpect(redirectedUrl("/budgets?year=2100&season=winter"));
	}

	/** 保存すると、その季節の「未設定」の印が消える。もう一度保存しても、行は増えない */
	@Test
	void marksDisappearAfterSavingAndSavingAgainDoesNotAddRows() throws Exception {
		String before = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "winter").with(asA)).andReturn());
		assertThat(before).contains("（この季節は未設定）");

		mockMvc.perform(save("2026", "winter", a.food().getId(), 30000, a.fare().getId(), 0).with(asA)).andExpect(status().is3xxRedirection());
		String after = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "winter").with(asA)).andReturn());
		assertThat(after).doesNotContain("（この季節は未設定）");
		assertThat(after).contains("name=\"rows[0].amount\" value=\"30000\"");

		mockMvc.perform(save("2026", "winter", a.food().getId(), 31000, a.fare().getId(), 10).with(asA)).andExpect(status().is3xxRedirection());
		List<Budget> winter = seasonOf(a, 2026, Season.WINTER);
		assertThat(winter).hasSize(2);
		assertThat(amountOf(winter, a.food())).isEqualTo(31000);
		assertThat(amountOf(winter, a.fare())).isEqualTo(10);
	}

	@Test
	void saveBoundaryAmountsAndSpaces() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 0, a.fare().getId(), 3000000).with(asA))
				.andExpect(redirectedUrl("/budgets?year=2026&season=winter"));
		List<Budget> winter = seasonOf(a, 2026, Season.WINTER);
		assertThat(amountOf(winter, a.food())).isZero();
		assertThat(amountOf(winter, a.fare())).isEqualTo(3_000_000);

		mockMvc.perform(save("2026", "summer", a.food().getId(), " 100 ", a.fare().getId(), "　200　").with(asA))
				.andExpect(redirectedUrl("/budgets?year=2026&season=summer"));
		List<Budget> summer = seasonOf(a, 2026, Season.SUMMER);
		assertThat(amountOf(summer, a.food())).isEqualTo(100);
		assertThat(amountOf(summer, a.fare())).isEqualTo(200);
	}

	/** R13：全角の数字で打った予算額は、半角に直して保存される（ブラウザの確認：食費に２５０００ → 25000） */
	@Test
	void fullWidthAmountsAreSavedAsHalfWidth() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), "２５０００", a.fare().getId(), "３０００００").with(asA))
				.andExpect(redirectedUrl("/budgets?year=2026&season=winter"));

		List<Budget> winter = seasonOf(a, 2026, Season.WINTER);
		assertThat(amountOf(winter, a.food())).isEqualTo(25000);
		assertThat(amountOf(winter, a.fare())).isEqualTo(300000);
	}

	/** 全角で打って範囲のエラーになったときは、入力欄に半角に直した数字が出る */
	@Test
	void fullWidthOutOfRangeShowsHalfWidthValueInTheField() throws Exception {
		String body = body(mockMvc.perform(save("2026", "winter", a.food().getId(), "３０００００１", a.fare().getId(), "５").with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("予算額は0円以上3,000,000円以下で入力してください");
		assertThat(body).contains("name=\"rows[0].amount\" value=\"3000001\"", "name=\"rows[1].amount\" value=\"5\"");
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	/** 持ち主は、ログイン中のユーザー。画面から送られた userId は使わない */
	@Test
	void saveIgnoresAUserIdSentFromTheScreen() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 100).with(asA).param("userId", String.valueOf(b.userId())))
				.andExpect(redirectedUrl("/budgets?year=2026&season=winter"));

		assertThat(seasonOf(a, 2026, Season.WINTER)).hasSize(1);
		assertThat(allOf(b)).isEmpty();
	}

	/** 送られなかった科目の予算は変わらない（F2）。ほかの季節・ほかのユーザーも変わらない */
	@Test
	void categoriesNotSentAndOtherSeasonsAndUsersAreUntouched() throws Exception {
		saved(a, a.fare(), 2026, Season.WINTER, 4000);
		saved(b, b.food(), 2026, Season.WINTER, 12345);

		mockMvc.perform(save("2026", "winter", a.food().getId(), 777).with(asA)).andExpect(status().is3xxRedirection());

		List<Budget> winter = seasonOf(a, 2026, Season.WINTER);
		assertThat(winter).hasSize(2);
		assertThat(amountOf(winter, a.food())).isEqualTo(777);
		assertThat(amountOf(winter, a.fare())).isEqualTo(4000);
		assertThat(amountOf(seasonOf(a, 2026, Season.AUTUMN), a.food())).isEqualTo(30000);
		assertThat(amountOf(seasonOf(b, 2026, Season.WINTER), b.food())).isEqualTo(12345);
	}

	/** 2人が同じ季節で別々の額を保存しても、互いに見えない・変わらない */
	@Test
	void twoUsersSaveTheSameSeasonSeparately() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 1111, a.fare().getId(), 2222).with(asA)).andExpect(status().is3xxRedirection());
		mockMvc.perform(save("2026", "winter", b.food().getId(), 3333, b.fare().getId(), 4444).with(asB)).andExpect(status().is3xxRedirection());

		assertThat(amountOf(seasonOf(a, 2026, Season.WINTER), a.food())).isEqualTo(1111);
		assertThat(amountOf(seasonOf(b, 2026, Season.WINTER), b.food())).isEqualTo(3333);
		String bBody = body(mockMvc.perform(get("/budgets").param("year", "2026").param("season", "winter").with(asB)).andReturn());
		assertThat(bBody).contains("value=\"3333\"", "value=\"4444\"").doesNotContain("value=\"1111\"").doesNotContain("value=\"2222\"");
	}

	// ================================================================
	// 保存の入力エラー：何も保存せず、入力値を残して表示し直す
	// ================================================================

	@ParameterizedTest(name = "予算額 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"-1|予算額は0円以上3,000,000円以下で入力してください",
			"3000001|予算額は0円以上3,000,000円以下で入力してください",
			"''|予算額を入力してください",
			"' '|予算額を入力してください",
			"abc|予算額は整数で入力してください",
			"'1,000'|予算額は整数で入力してください",
			"1 000|予算額は整数で入力してください",
			"0x10|予算額は整数で入力してください",
			"#10|予算額は整数で入力してください",
			"+5|予算額は整数で入力してください",
			"1.5|予算額は整数で入力してください",
			"10円|予算額は整数で入力してください",
			"99999999999|予算額は整数で入力してください",
			"－５|予算額は整数で入力してください",
			"１，０００|予算額は整数で入力してください",
			"３０００００１|予算額は0円以上3,000,000円以下で入力してください"
	})
	void invalidAmountRedisplaysKeepsAllInputsAndSavesNothing(String amount, String message) throws Exception {
		MvcResult result = mockMvc.perform(save("2026", "winter", a.food().getId(), amount, a.fare().getId(), 777).with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("budgets/form"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains(message);
		assertThat(count(body, "class=\"field-error\"")).as("エラーは1行に1つだけ（G3、G7）").isEqualTo(1);
		assertThat(body).contains("name=\"rows[1].amount\" value=\"777\"");          // エラーのない行の入力値も残る
		assertThat(body).contains("2026年 冬", "action=\"/budgets?year=2026&amp;season=winter\"");   // 見出し・送信先も同じ
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();                       // エラーのない行も保存されない
		assertThat(allOf(a)).hasSize(2);
	}

	@ParameterizedTest(name = "入力した文字がそのまま残る：[{0}]")
	@CsvSource(delimiter = '|', value = { "abc", "1 000", "0x10", "+5", "1.5", "10円", "－５", "１，０００" })
	void typeMismatchKeepsTheTypedText(String amount) throws Exception {
		String body = body(mockMvc.perform(save("2026", "winter", a.food().getId(), amount, a.fare().getId(), 777).with(asA)).andReturn());

		assertThat(body).contains("name=\"rows[0].amount\" value=\"" + amount + "\"");
	}

	/** 複数の行にエラーがあるとき、それぞれの行にメッセージが出る。送られた順のまま、科目名と印が出る */
	@Test
	void eachBadRowGetsItsOwnMessageInThePostedOrderWithNamesAndMarks() throws Exception {
		saved(a, a.fare(), 2026, Season.WINTER, 4000);      // 冬は交通費だけ保存済み（食費は未設定）

		// 送られた順は、登録順と逆（交通費、食費）
		String body = body(mockMvc.perform(save("2026", "winter", a.fare().getId(), "abc", a.food().getId(), "-1").with(asA))
				.andExpect(status().isOk()).andReturn());

		assertThat(body).contains("予算額は整数で入力してください", "予算額は0円以上3,000,000円以下で入力してください");
		assertThat(count(body, "class=\"field-error\"")).isEqualTo(2);
		assertThat(body.indexOf("交通費")).isLessThan(body.indexOf("食費"));
		assertThat(count(body, "（この季節は未設定）")).isEqualTo(1);             // 未設定なのは食費だけ
		assertThat(body.indexOf("食費")).isLessThan(body.indexOf("（この季節は未設定）"));
		assertThat(body).contains("name=\"rows[0].categoryId\" value=\"" + a.fare().getId() + "\"",
				"name=\"rows[1].categoryId\" value=\"" + a.food().getId() + "\"");
		assertThat(seasonOf(a, 2026, Season.WINTER)).hasSize(1);                  // 保存されていない（交通費の4000だけ）
		assertThat(amountOf(seasonOf(a, 2026, Season.WINTER), a.fare())).isEqualTo(4000);
	}

	@Test
	void redisplayEscapesCategoryNames() throws Exception {
		Category tricky = categoryRepository.save(new Category(a.userId(), "<b>太</b>"));
		entityManager.flush();
		entityManager.clear();

		String body = body(mockMvc.perform(save("2026", "winter", tricky.getId(), "abc").with(asA)).andExpect(status().isOk()).andReturn());

		assertThat(body).contains("&lt;b&gt;太&lt;/b&gt;").doesNotContain("<b>太</b>");
	}

	// ================================================================
	// 科目ID：他人・存在しない・重複・数字でない・空は 404（入力が間違っていても、先に確かめる）
	// ================================================================

	@Test
	void otherUsersCategoryIdIsNotFoundAndSavesNothingEvenIfOtherRowsAreValid() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 111, b.food().getId(), 222).with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));
		mockMvc.perform(save("2026", "winter", b.food().getId(), 222, a.food().getId(), 111).with(asA))
				.andExpect(status().isNotFound());

		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
		assertThat(allOf(b)).isEmpty();
	}

	/** 入力が間違っていても、他人・存在しない科目IDなら 404（入力エラーの画面にならない。設計書 7.8、Q6-3） */
	@Test
	void foreignCategoryIdIsNotFoundEvenWhenTheAmountIsInvalid() throws Exception {
		mockMvc.perform(save("2026", "winter", b.food().getId(), "abc").with(asA)).andExpect(status().isNotFound());
		mockMvc.perform(save("2026", "winter", a.food().getId(), "abc", b.fare().getId(), "-1").with(asA)).andExpect(status().isNotFound());
		mockMvc.perform(save("2026", "winter", 999999999L, "").with(asA)).andExpect(status().isNotFound());
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	@Test
	void nonexistentCategoryIdIsNotFound() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 100, 999999999L, 200).with(asA)).andExpect(status().isNotFound());
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	@Test
	void duplicatedCategoryIdIsNotFoundWhetherOrNotTheAmountsAreValid() throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 100, a.fare().getId(), 200, a.food().getId(), 300).with(asA))
				.andExpect(status().isNotFound());
		mockMvc.perform(save("2026", "winter", a.food().getId(), "abc", a.food().getId(), "-1").with(asA))
				.andExpect(status().isNotFound());
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	@ParameterizedTest(name = "科目ID [{0}] → 404")
	@ValueSource(strings = { "abc", "", " ", "1.5", "99999999999999999999", "-", "０" })
	void nonNumericOrEmptyCategoryIdIsNotFound(String categoryId) throws Exception {
		mockMvc.perform(save("2026", "winter", a.food().getId(), 100, categoryId, 200).with(asA))
				.andExpect(status().isNotFound())
				.andExpect(view().name("error/404"));
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	@Test
	void missingCategoryIdParameterIsNotFound() throws Exception {
		mockMvc.perform(save("2026", "winter", null, 100).with(asA)).andExpect(status().isNotFound());
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	/** 送られた行がないとき（科目が0件の画面など）：何も保存せず、成功として戻る */
	@Test
	void postWithNoRowsSavesNothingAndRedirects() throws Exception {
		mockMvc.perform(save("2026", "winter").with(asA))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/budgets?year=2026&season=winter"));
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

	/**
	 * 行の数の上限（Q6-9 の A）：Spring は、行の並びを 256 行までしか組み立てない。
	 * 255 番目までは使えるが、256 番目（257 行目）を送ると、束縛そのものが失敗する（InvalidPropertyException）。
	 * 実際の画面（本番）では、500 番台のエラー画面になる。何も保存されない。科目が 257 以上あると保存できない。
	 * （実際に調べて、400 番台ではなく、この動きだと分かった。Phase 8 で確認する）
	 */
	@Test
	void moreThan256RowsFailsBindingAndSavesNothing() throws Exception {
		// 255 番目：組み立てられるので、科目IDの確認まで進み、他人の科目IDで 404
		mockMvc.perform(save("2026", "winter", a.food().getId(), 1).with(asA)
				.param("rows[255].categoryId", String.valueOf(b.food().getId())).param("rows[255].amount", "1"))
				.andExpect(status().isNotFound());

		// 256 番目：束縛が失敗する
		assertThatThrownBy(() -> mockMvc.perform(save("2026", "winter", a.food().getId(), 1).with(asA)
				.param("rows[256].categoryId", String.valueOf(a.fare().getId())).param("rows[256].amount", "1")))
				.hasStackTraceContaining("InvalidPropertyException").hasStackTraceContaining("rows[256]");
		assertThat(seasonOf(a, 2026, Season.WINTER)).isEmpty();
	}

}
