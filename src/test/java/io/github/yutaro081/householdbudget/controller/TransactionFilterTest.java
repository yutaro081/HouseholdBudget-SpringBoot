package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ComparisonResult;
import io.github.yutaro081.householdbudget.dto.ComparisonRow;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.ComparisonService;
import jakarta.persistence.EntityManager;

/**
 * 取引一覧の絞り込み（issue 023 の段階 1。要件定義書 4.2、設計書 2.2〜2.5、3.9、5.17、7.8）。
 * <p>
 * 画面（HTTP）から確かめる：守り（404・他人のデータ）、見出しの文、予実比較の実績との一致（季節の境目）、
 * 同じレシートの取引を科目で絞ったときの削除の確認の文、並び。
 * 印（receipt_group）は SQL で付ける（TransactionReceiptTest と同じ。登録の作りに頼らない）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストの準備（ApplicationContext）を増やさないため、自分の @TestConfiguration は作らない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionFilterTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
	private static final String NONE_MESSAGE = "この条件に当てはまる取引はありません。";
	private static final String EMPTY_MESSAGE = "取引はまだありません";
	private static final Pattern CLEAR_LINK = Pattern.compile("<a[^>]*href=\"/transactions\"[^>]*>絞り込みを外す</a>");
	private static final Pattern HEADING_NUMBERS = Pattern.compile(">[^<>]*の取引（(?:全期間・)?([0-9,]+) 件・合計 ([0-9,]+)円）<");

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
	private TransactionRepository transactionRepository;
	@Autowired
	private ComparisonService comparisonService;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long userId, String username, Category food, Category daily, Vendor vendor, PaymentMethod payment) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;

	@BeforeEach
	void createUsers() {
		a = createOwner("fltrxa");
		b = createOwner("fltrxb");
		asA = user(new LoginUser(a.userId(), a.username(), "x"));
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, username,
				categoryRepository.save(new Category(id, "食費")), categoryRepository.save(new Category(id, "日用品")),
				vendorRepository.save(new Vendor(id, "スーパー")), paymentMethodRepository.save(new PaymentMethod(id, "現金")));
	}

	// ---------------------------------------------------------------
	// 準備と読み出し
	// ---------------------------------------------------------------

	private Long saveRow(Owner o, LocalDate date, Category category, int amount, String description) {
		Long id = transactionRepository.save(new Transaction(o.userId(), date, amount, category, description, o.vendor(), o.payment())).getId();
		entityManager.flush();
		entityManager.clear();
		return id;
	}

	/** 同じレシートの行を保存し、SQL で印（最初の行の id）を付ける。科目は、上から順に categories のとおり。摘要は prefix-1, prefix-2… */
	private List<Long> saveReceipt(Owner o, LocalDate date, String prefix, Category[] categories, int... amounts) {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < amounts.length; i++) {
			ids.add(saveRow(o, date, categories[i], amounts[i], prefix + "-" + (i + 1)));
		}
		setGroup(ids.get(0), ids);
		return ids;
	}

	private void setGroup(Long group, List<Long> ids) {
		entityManager.flush();
		for (Long id : ids) {
			entityManager.createNativeQuery("UPDATE transactions SET receipt_group = ?1 WHERE id = ?2")
					.setParameter(1, group).setParameter(2, id).executeUpdate();
		}
		entityManager.clear();
	}

	/** 行の科目を、SQL で直接書きかえる（アプリでは作れない「他人の行に、自分の科目の ID」の状態を作る） */
	private void setCategory(Long transactionId, Long categoryId) {
		entityManager.flush();
		entityManager.createNativeQuery("UPDATE transactions SET category_id = ?1 WHERE id = ?2")
				.setParameter(1, categoryId).setParameter(2, transactionId).executeUpdate();
		entityManager.clear();
	}

	/** その人の取引の { 件数, 金額の合計 }（SQL で数える） */
	private long[] countAndSum(Owner o) {
		entityManager.flush();
		entityManager.clear();
		Object[] r = (Object[]) entityManager.createNativeQuery("SELECT COUNT(*), COALESCE(SUM(amount), 0) FROM transactions WHERE user_id = ?1")
				.setParameter(1, o.userId()).getSingleResult();
		return new long[] { ((Number) r[0]).longValue(), ((Number) r[1]).longValue() };
	}

	private MockHttpServletRequestBuilder list(String... params) {
		MockHttpServletRequestBuilder builder = get("/transactions");
		for (int i = 0; i < params.length; i += 2) {
			builder.param(params[i], params[i + 1]);
		}
		return builder;
	}

	private String page(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.with(asA)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private void notFound(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(request.with(asA)).andExpect(status().isNotFound()).andExpect(view().name("error/404"));
	}

	private static String id(Category category) {
		return String.valueOf(category.getId());
	}

	/** 見出しの { 件数, 合計 } */
	private static long[] headingNumbers(String body) {
		Matcher m = HEADING_NUMBERS.matcher(body);
		assertThat(m.find()).as("絞り込みの見出しがある").isTrue();
		long[] numbers = { Long.parseLong(m.group(1).replace(",", "")), Long.parseLong(m.group(2).replace(",", "")) };
		assertThat(m.find()).as("絞り込みの見出しは 1 つだけ").isFalse();
		return numbers;
	}

	/** 一覧の行（摘要の文字で探す）の、上からの順 */
	private static List<String> orderOf(String body, String... descriptions) {
		List<int[]> found = new ArrayList<>();
		for (int i = 0; i < descriptions.length; i++) {
			int at = body.indexOf(">" + descriptions[i] + "<");
			assertThat(at).as(descriptions[i] + " が一覧にある").isPositive();
			found.add(new int[] { at, i });
		}
		found.sort((x, y) -> Integer.compare(x[0], y[0]));
		return found.stream().map(f -> descriptions[f[1]]).toList();
	}

	/** その行（摘要の文字で探す）の HTML */
	private static String rowOf(String body, String description) {
		Matcher m = Pattern.compile("(?s)<tr[^>]*>(?:(?!</tr>).)*?>" + Pattern.quote(description) + "<(?:(?!</tr>).)*</tr>").matcher(body);
		assertThat(m.find()).as(description + " の行").isTrue();
		return m.group();
	}

	/** その行の削除のフォームの、確認の文 */
	private static String confirmOf(String row) {
		Matcher m = Pattern.compile("<form[^>]*\\bdata-confirm=\"([^\"]*)\"[^>]*>").matcher(row);
		assertThat(m.find()).as("data-confirm がある").isTrue();
		return m.group(1).replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
	}

	// ---------------------------------------------------------------
	// 守り：ログイン・404・他人のデータ（設計書 2.4、3.9、7.8）
	// ---------------------------------------------------------------

	@ParameterizedTest(name = "未ログインで GET {0} は、ログイン画面へ移動する")
	@ValueSource(strings = { "/transactions?category=1", "/transactions?year=2026&season=autumn", "/transactions?category=1&year=2026&season=autumn" })
	void notLoggedInIsRedirected(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	/** 他人の科目・存在しない科目・数字でない値・範囲外の値は、科目だけでも、季節と一緒でも、同じ 404 */
	@Test
	void otherUsersMissingAndInvalidCategoriesAreTheSame404() throws Exception {
		saveRow(b, DAY, b.food(), 999, "他人の行");
		for (String category : List.of(id(b.food()), "999999999", "abc", "1.5", "-", "99999999999999999999", "0", "-1")) {
			notFound(list("category", category));
			notFound(list("category", category, "year", "2026", "season", "autumn"));
		}
	}

	/** 年・季節が不正・範囲外・空のときは、予実比較と同じく 404（科目があってもなくても） */
	@ParameterizedTest(name = "year=[{0}] season=[{1}] は 404")
	@CsvSource(delimiter = '|', value = {
			"1999|winter", "2101|spring", "abc|spring", "02026|spring", "+2026|spring", "20260|spring", "0|spring", "-2026|spring",
			"2026|Spring", "2026|SPRING", "2026|haru", "2026|' spring'", "2026|'spring '", "' 2026'|spring", "''|''", "2026|''", "''|spring" })
	void badYearOrSeasonIsNotFound(String year, String season) throws Exception {
		notFound(list("year", year, "season", season));
		notFound(list("category", id(a.food()), "year", year, "season", season));
	}

	/** 年と季節の片方だけは 404（科目があってもなくても） */
	@Test
	void onlyOneOfYearAndSeasonIsNotFound() throws Exception {
		notFound(list("year", "2026"));
		notFound(list("season", "autumn"));
		notFound(list("category", id(a.food()), "year", "2026"));
		notFound(list("category", id(a.food()), "season", "autumn"));
	}

	/** 年・季節が 2 回来たら 404（文字でつながり、4 桁の数字・季節の名前にならない。予実比較と同じ） */
	@Test
	void sameYearOrSeasonTwiceIsNotFound() throws Exception {
		notFound(get("/transactions").param("year", "2026", "2027").param("season", "autumn"));
		notFound(get("/transactions").param("year", "2026").param("season", "autumn", "winter"));
		notFound(get("/transactions").param("year", "2026", "2026").param("season", "autumn", "autumn"));
		notFound(get("/transactions").param("category", id(a.food())).param("year", "2026", "2027").param("season", "autumn"));
	}

	/**
	 * category が 2 回来たとき（設計書 7.8：どちらの値でも、自分の科目でなければ 404）。
	 * Long で受けるので、Spring は最初の値を使う（このテストで確かめた動き）。他人のデータは、どの順でも見えない。
	 */
	@Test
	void categoryTwiceUsesTheFirstValueAndNeverShowsOthersData() throws Exception {
		saveRow(a, DAY, a.food(), 100, "自分の食費");
		saveRow(a, DAY, a.daily(), 200, "自分の日用品");
		saveRow(b, DAY, b.food(), 999, "他人の行");

		String body = page(get("/transactions").param("category", id(a.food()), id(a.daily())));
		assertThat(body).contains(">自分の食費<").doesNotContain(">自分の日用品<", ">他人の行<");
		assertThat(body).contains(">食費の取引（全期間・1 件・合計 100円）<");

		body = page(get("/transactions").param("category", id(a.food()), id(b.food())));
		assertThat(body).contains(">自分の食費<").doesNotContain(">他人の行<", ">自分の日用品<");

		notFound(get("/transactions").param("category", id(b.food()), id(a.food())));
		notFound(get("/transactions").param("category", "abc", id(a.food())));
	}

	/** 空の category は「指定なし」（copyFrom と同じ）。季節と一緒なら、季節だけの絞り込み */
	@Test
	void emptyCategoryIsUnspecified() throws Exception {
		saveRow(a, DAY, a.food(), 100, "秋の食費");
		saveRow(a, LocalDate.of(2026, 12, 1), a.daily(), 200, "冬の日用品");

		String all = page(list("category", ""));
		assertThat(all).contains(">秋の食費<", ">冬の日用品<", "日付の新しい順に、2 件を表示しています。");
		assertThat(CLEAR_LINK.matcher(all).find()).isFalse();

		String autumn = page(list("category", "", "year", "2026", "season", "autumn"));
		assertThat(autumn).contains(">秋の食費<", ">2026年 秋の取引（1 件・合計 100円）<").doesNotContain(">冬の日用品<");
	}

	/**
	 * 他人の行に、自分の科目の ID や、同じ期間の日付が入っていても（アプリでは作れない。SQL で作る）、絞り込んだ一覧に出ない。
	 * 科目だけ・季節だけ・両方のどれでも。見出しの件数と合計にも入らない
	 */
	@Test
	void otherUsersRowsAreNeverShownEvenWithMyCategoryId() throws Exception {
		saveRow(a, DAY, a.food(), 100, "自分の食費");
		Long theirs = saveRow(b, DAY, b.food(), 999, "他人の行");
		setCategory(theirs, a.food().getId());

		for (String[] params : List.of(new String[] { "category", id(a.food()) },
				new String[] { "year", "2026", "season", "autumn" },
				new String[] { "category", id(a.food()), "year", "2026", "season", "autumn" })) {
			String body = page(list(params));
			assertThat(body).contains(">自分の食費<").doesNotContain("他人の行");
			assertThat(headingNumbers(body)).containsExactly(1, 100);
		}
	}

	/** 科目名は無害化して出す（th:text。th:utext は使わない） */
	@Test
	void categoryNameInTheHeadingIsEscaped() throws Exception {
		Category odd = categoryRepository.save(new Category(a.userId(), "<b>x</b>"));
		saveRow(a, DAY, odd, 100, "記号の科目");

		String body = page(list("category", id(odd), "year", "2026", "season", "autumn"));

		assertThat(body).contains(">2026年 秋・&lt;b&gt;x&lt;/b&gt;の取引（1 件・合計 100円）<").doesNotContain("<b>x</b>");
	}

	// ---------------------------------------------------------------
	// 見出しの文・絞り込みを外すリンク・0 件（設計書 2.3、Q023-1〜3）
	// ---------------------------------------------------------------

	/** 3 つとも：「2026年 秋・食費の取引（2 件・合計 1,500円）」と、絞り込みを外すリンク。件数の文は出さない */
	@Test
	void headingForCategoryAndSeason() throws Exception {
		saveRow(a, DAY, a.food(), 500, "昼食");
		saveRow(a, DAY, a.food(), 1000, "夕食");
		saveRow(a, DAY, a.daily(), 300, "洗剤");

		String body = page(list("category", id(a.food()), "year", "2026", "season", "autumn"));

		assertThat(body).contains(">2026年 秋・食費の取引（2 件・合計 1,500円）<", ">昼食<", ">夕食<").doesNotContain(">洗剤<");
		assertThat(CLEAR_LINK.matcher(body).find()).isTrue();
		assertThat(body).doesNotContain("を表示しています。", NONE_MESSAGE, EMPTY_MESSAGE);
	}

	/** 科目だけ：「食費の取引（全期間・○ 件・合計 ○円）」。季節をまたいで全部 */
	@Test
	void headingForCategoryOnly() throws Exception {
		saveRow(a, LocalDate.of(2025, 5, 1), a.food(), 12000, "去年の食費");
		saveRow(a, DAY, a.food(), 345, "今年の食費");
		saveRow(a, DAY, a.daily(), 300, "洗剤");

		String body = page(list("category", id(a.food())));

		assertThat(body).contains(">食費の取引（全期間・2 件・合計 12,345円）<", ">去年の食費<", ">今年の食費<").doesNotContain(">洗剤<");
		assertThat(CLEAR_LINK.matcher(body).find()).isTrue();
	}

	/** 季節だけ：「2026年 秋の取引（○ 件・合計 ○円）」。全科目 */
	@Test
	void headingForSeasonOnly() throws Exception {
		saveRow(a, DAY, a.food(), 500, "昼食");
		saveRow(a, DAY, a.daily(), 300, "洗剤");
		saveRow(a, LocalDate.of(2026, 12, 1), a.food(), 9999, "冬の食費");

		String body = page(list("year", "2026", "season", "autumn"));

		assertThat(body).contains(">2026年 秋の取引（2 件・合計 800円）<", ">昼食<", ">洗剤<").doesNotContain(">冬の食費<");
		assertThat(CLEAR_LINK.matcher(body).find()).isTrue();
	}

	/** 冬は、期間を添える（年をまたぐ。Q023-2） */
	@Test
	void winterHeadingShowsThePeriod() throws Exception {
		saveRow(a, LocalDate.of(2026, 12, 1), a.food(), 100, "12月の食費");
		saveRow(a, LocalDate.of(2027, 2, 28), a.food(), 200, "2月の食費");

		assertThat(page(list("category", id(a.food()), "year", "2026", "season", "winter")))
				.contains(">2026年 冬（2026年12月〜2027年2月）・食費の取引（2 件・合計 300円）<", ">12月の食費<", ">2月の食費<");
		assertThat(page(list("year", "2026", "season", "winter")))
				.contains(">2026年 冬（2026年12月〜2027年2月）の取引（2 件・合計 300円）<");
	}

	/** 絞り込んで 0 件：見出し（0 件）とリンクを出し、その下に別の文。「取引はまだありません」と表は出さない（Q023-3） */
	@Test
	void noMatchShowsTheHeadingTheLinkAndItsOwnMessage() throws Exception {
		saveRow(a, DAY, a.food(), 500, "昼食");

		String body = page(list("category", id(a.daily()), "year", "2026", "season", "autumn"));

		assertThat(body).contains(">2026年 秋・日用品の取引（0 件・合計 0円）<", NONE_MESSAGE).doesNotContain(EMPTY_MESSAGE, "list-card", ">昼食<");
		assertThat(CLEAR_LINK.matcher(body).find()).isTrue();
		assertThat(body.indexOf("（0 件・合計 0円）")).isLessThan(body.indexOf(NONE_MESSAGE));
	}

	/** 絞らない一覧は今のまま：件数の文があり、見出しとリンクはない */
	@Test
	void theUnfilteredListIsUnchanged() throws Exception {
		saveRow(a, DAY, a.food(), 500, "昼食");

		String body = page(list());

		assertThat(body).contains("日付の新しい順に、1 件を表示しています。").doesNotContain("の取引（", NONE_MESSAGE);
		assertThat(CLEAR_LINK.matcher(body).find()).isFalse();
	}

	// ---------------------------------------------------------------
	// 予実比較の実績との一致（季節の境目。設計書 5.17、決まったこと 7）
	// ---------------------------------------------------------------

	/** 境目の前後に取引を置く（11/30・12/1・2/28・2/29・3/1。冬は年をまたぐ。2028 年はうるう年） */
	private void saveBoundaryRows() {
		saveRow(a, LocalDate.of(2026, 8, 31), a.food(), 1, "夏の最終日");
		saveRow(a, LocalDate.of(2026, 9, 1), a.food(), 10, "秋の初日");
		saveRow(a, LocalDate.of(2026, 11, 30), a.food(), 100, "秋の最終日");
		saveRow(a, LocalDate.of(2026, 11, 30), a.daily(), 1000, "秋の日用品");
		saveRow(a, LocalDate.of(2026, 12, 1), a.food(), 10000, "冬の初日");
		saveRow(a, LocalDate.of(2027, 2, 28), a.food(), 20000, "2月28日");
		saveRow(a, LocalDate.of(2027, 3, 1), a.food(), 40000, "春の初日");
		saveRow(a, LocalDate.of(2027, 12, 1), a.daily(), 3, "2027年冬の初日");
		saveRow(a, LocalDate.of(2028, 2, 29), a.daily(), 30, "うるう日");
		saveRow(a, LocalDate.of(2028, 3, 1), a.daily(), 300, "2028年春の初日");
		// 他人の取引（同じ期間）。どちらの合計にも入らない
		saveRow(b, LocalDate.of(2026, 11, 30), b.food(), 7777, "他人の行");
	}

	private long actualOf(SeasonPeriod period, Category category) {
		ComparisonResult result = comparisonService.compare(a.userId(), period);
		return result.rows().stream().filter(row -> category.getId().equals(row.categoryId())).mapToLong(ComparisonRow::actual).sum();
	}

	/**
	 * 季節と科目を両方指定したとき、見出しの合計は、予実比較の同じ季節・同じ科目の実績と同じ。件数は、その期間・その科目の行の数。
	 * 季節だけのときの合計は、予実比較の合計の行の実績と同じ
	 */
	@ParameterizedTest(name = "{0}年 {1}：食費 {2} 件・{3}円、日用品 {4} 件・{5}円")
	@CsvSource({
			"2026, SUMMER, 1, 1,     0, 0",
			"2026, AUTUMN, 2, 110,   1, 1000",
			"2026, WINTER, 2, 30000, 0, 0",
			"2027, SPRING, 1, 40000, 0, 0",
			"2027, WINTER, 0, 0,     2, 33",
			"2028, SPRING, 0, 0,     1, 300" })
	void theHeadingTotalMatchesTheComparisonActualAtTheSeasonBoundaries(int year, Season season, long foodCount, long foodTotal,
			long dailyCount, long dailyTotal) throws Exception {
		saveBoundaryRows();
		SeasonPeriod period = new SeasonPeriod(year, season);
		String y = String.valueOf(year);
		String s = season.getUrlName();

		long[] food = headingNumbers(page(list("category", id(a.food()), "year", y, "season", s)));
		long[] daily = headingNumbers(page(list("category", id(a.daily()), "year", y, "season", s)));
		long[] all = headingNumbers(page(list("year", y, "season", s)));

		assertThat(food).containsExactly(foodCount, foodTotal);
		assertThat(daily).containsExactly(dailyCount, dailyTotal);
		assertThat(food[1]).isEqualTo(actualOf(period, a.food()));
		assertThat(daily[1]).isEqualTo(actualOf(period, a.daily()));
		assertThat(all).containsExactly(foodCount + dailyCount, foodTotal + dailyTotal);
		assertThat(all[1]).isEqualTo(comparisonService.compare(a.userId(), period).total().actual());
	}

	/** 科目だけ（全期間）のときは、その科目の全部の行。他人の行は入らない */
	@Test
	void theCategoryOnlyHeadingCountsEveryPeriod() throws Exception {
		saveBoundaryRows();

		assertThat(headingNumbers(page(list("category", id(a.food()))))).containsExactly(6, 70111);
		assertThat(headingNumbers(page(list("category", id(a.daily()))))).containsExactly(4, 1333);
	}

	// ---------------------------------------------------------------
	// 同じレシートの取引（決まったこと 4、Q023-4）
	// ---------------------------------------------------------------

	/**
	 * 科目で絞ると、レシートの一部の行だけが出る。確認の文の合計と行の数は、レシート全体（実際に消える行）の値で、
	 * 出ていない行の数を「よろしいですか？」の前に足す。削除すると、確認の文のとおりの行と金額が消える
	 */
	@Test
	void theDeleteConfirmationOfAPartlyShownReceiptCountsTheWholeReceipt() throws Exception {
		List<Long> receipt = saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily() }, 2200, 800);
		saveRow(a, DAY, a.food(), 50, "単独");

		String body = page(list("category", id(a.food()), "year", "2026", "season", "autumn"));

		assertThat(body).contains(">R-1<", ">単独<").doesNotContain(">R-2<");
		assertThat(rowOf(body, "R-1")).contains(">分割<");
		assertThat(confirmOf(rowOf(body, "R-1")))
				.isEqualTo("2026/11/09 スーパー 3,000円（2 行）を削除します。この一覧に出ていない 1 行も消えます。よろしいですか？");
		assertThat(confirmOf(rowOf(body, "単独"))).isEqualTo("2026/11/09 食費 50円 を削除します。よろしいですか？");
		assertThat(headingNumbers(body)).containsExactly(2, 2250);

		long[] before = countAndSum(a);
		mockMvc.perform(post("/transactions/" + receipt.get(0) + "/delete").with(csrf()).with(asA)).andExpect(redirectedUrl("/transactions"));
		long[] after = countAndSum(a);
		assertThat(before[0] - after[0]).as("消えた行の数 ＝ 確認の文の行の数").isEqualTo(2);
		assertThat(before[1] - after[1]).as("消えた金額 ＝ 確認の文の合計").isEqualTo(3000);
	}

	/** 同じ科目の行が 2 つ出るレシート：見出しは 2 件と数え、どちらの行も、全体の行の数と、出ていない 1 行 */
	@Test
	void aReceiptWithTwoShownLinesCountsBothAndOneHiddenLine() throws Exception {
		saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily(), a.food() }, 1000, 200, 30);

		String body = page(list("category", id(a.food())));

		assertThat(headingNumbers(body)).containsExactly(2, 1030);
		for (String description : List.of("R-1", "R-3")) {
			assertThat(confirmOf(rowOf(body, description)))
					.isEqualTo("2026/11/09 スーパー 1,230円（3 行）を削除します。この一覧に出ていない 1 行も消えます。よろしいですか？");
		}
	}

	/** 全部の行が出ているとき（季節だけで絞る。レシートは分かれない）は、今の文のまま */
	@Test
	void aWhollyShownReceiptKeepsTheUsualMessage() throws Exception {
		saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily() }, 2200, 800);

		String body = page(list("year", "2026", "season", "autumn"));

		for (String description : List.of("R-1", "R-2")) {
			assertThat(confirmOf(rowOf(body, description))).isEqualTo("2026/11/09 スーパー 3,000円（2 行）を削除します。よろしいですか？");
		}
	}

	/** 他人の行に、自分のレシートと同じ印の値が入っていても（SQL で作る）、レシートの合計と行の数に入らない（「印と user_id」で読む） */
	@Test
	void anotherUsersRowWithTheSameMarkIsNotCounted() throws Exception {
		List<Long> mine = saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily() }, 2200, 800);
		Long theirs = saveRow(b, DAY, b.food(), 999, "他人の行");
		setGroup(mine.get(0), List.of(theirs));

		String body = page(list("category", id(a.food())));

		assertThat(confirmOf(rowOf(body, "R-1")))
				.isEqualTo("2026/11/09 スーパー 3,000円（2 行）を削除します。この一覧に出ていない 1 行も消えます。よろしいですか？");
		assertThat(body).doesNotContain("他人の行");
	}

	// ---------------------------------------------------------------
	// 並び（決まったこと 6：並びの指定が 2 か所なので、同じことを守る）
	// ---------------------------------------------------------------

	/** 絞った一覧の並びは、絞らない一覧から、同じ行だけを取り出した並びと同じ */
	@Test
	void theFilteredOrderIsTheUnfilteredOrderOfTheSameRows() throws Exception {
		saveRow(a, DAY.minusDays(1), a.food(), 10, "前日");
		saveRow(a, DAY, a.food(), 20, "単独1");
		saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily(), a.food() }, 30, 40, 50);
		saveRow(a, DAY, a.daily(), 60, "単独2");
		saveRow(a, DAY, a.food(), 70, "単独3");
		saveRow(a, DAY.plusDays(1), a.food(), 80, "翌日");
		String[] food = { "前日", "単独1", "R-1", "R-3", "単独3", "翌日" };

		List<String> unfiltered = orderOf(page(list()), food);
		List<String> filtered = orderOf(page(list("category", id(a.food()), "year", "2026", "season", "autumn")), food);
		List<String> categoryOnly = orderOf(page(list("category", id(a.food()))), food);

		assertThat(unfiltered).containsExactly("翌日", "単独3", "R-1", "R-3", "単独1", "前日");
		assertThat(filtered).isEqualTo(unfiltered);
		assertThat(categoryOnly).isEqualTo(unfiltered);
	}
}
