package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
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
 * 予算設定画面の「自動予算」（GET /budgets の auto。要件定義書 4.3、設計書 2.2・2.3・5.14・7.8、issue 019）の
 * 画面のテスト。<b>文そのもの</b>（最初に出す 3 行、案の根拠、ボタンの横の一言）は、ここで確かめる。
 * <p>
 * 「今日」は、日本時間の 2026-11-11 に固定した Clock（設計書 5.14 の「計算の合った例」と同じ日）。
 * 画面は 2026 年 冬（/budgets?year=2026&season=winter）。直前の季節は 2026 年 秋（今日を含む。D＝91、n＝72）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストのパスワードのハッシュは、ダミーの値。
 * <p>
 * 合計の JavaScript（budget-total.js）は、まだ作らない。ここでは確かめない（あとの手順）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BudgetAutoControllerTest {

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2026, 11, 11).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

	private static final String WINTER_URL = "/budgets?year=2026&amp;season=winter";
	private static final String BUTTON_HREF = WINTER_URL + "&amp;auto=1";

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

	private Long userId;
	private Vendor vendor;
	private PaymentMethod payment;
	private RequestPostProcessor login;

	@BeforeEach
	void createUser() {
		userId = userRepository.save(new User("autoctl1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "autoctl1", "x"));
	}

	// ================================================================
	// データを作る道具
	// ================================================================

	private static SeasonPeriod p(int year, Season season) {
		return new SeasonPeriod(year, season);
	}

	private Category category(String name) {
		return categoryRepository.save(new Category(userId, name));
	}

	private void budget(Category category, SeasonPeriod period, int amount) {
		budgetRepository.save(new Budget(userId, category, period.year(), period.season(), amount));
	}

	/** その季節の真ん中あたり（開始日の 30 日あと）に、取引を 1 件入れる */
	private void spend(Category category, SeasonPeriod season, int amount) {
		transactionRepository.save(new Transaction(userId, season.startDate().plusDays(30), amount, category, null,
				vendor, payment));
	}

	/** 傾向の 12 季節：2023 秋〜2026 夏（古い順。冬は 1・5・9 番目） */
	private static List<SeasonPeriod> twelveSeasons() {
		List<SeasonPeriod> list = new ArrayList<>();
		SeasonPeriod season = p(2023, Season.AUTUMN);
		for (int i = 0; i < 12; i++) {
			list.add(season);
			season = season.next().orElseThrow();
		}
		return list;
	}

	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * 設計書 5.14「計算の合った例」の 6 科目。傾向 110%、案は OK 186,000／節約 170,000／上乗せ 205,000。
	 * 食費・交通費・雑費＝ルール①、医療費＝②（ペース）、日用品＝③、娯楽＝④（冬 2026 に 5,000 円が保存済み）。
	 * 食費の「冬以外」の取引は、傾向の T1・T2・T3 を 570,000・600,000・630,000 にそろえるための調整。
	 */
	private void designExample() {
		Category food = category("食費");
		Category fare = category("交通費");
		Category misc = category("雑費");
		Category medical = category("医療費");
		Category supplies = category("日用品");
		Category fun = category("娯楽");

		spend(food, p(2023, Season.WINTER), 84_000);
		spend(food, p(2024, Season.WINTER), 96_000);
		spend(food, p(2025, Season.WINTER), 90_000);
		spend(fare, p(2023, Season.WINTER), 41_000);
		spend(fare, p(2024, Season.WINTER), 38_000);
		spend(fare, p(2025, Season.WINTER), 33_000);
		spend(misc, p(2023, Season.WINTER), 30_000);
		spend(misc, p(2024, Season.WINTER), 24_000);
		spend(misc, p(2025, Season.WINTER), 18_000);

		List<SeasonPeriod> seasons = twelveSeasons();
		int[] filler = { 138_000, 0, 138_000, 139_000, 147_000, 0, 147_000, 148_000, 163_000, 0, 163_000, 163_000 };
		for (int i = 0; i < 12; i++) {
			if (filler[i] > 0) {
				spend(food, seasons.get(i), filler[i]);
			}
		}

		// 直前の季節（2026 年 秋）の実績と予算
		spend(food, p(2026, Season.AUTUMN), 49_200);
		spend(fare, p(2026, Season.AUTUMN), 5_600);
		spend(misc, p(2026, Season.AUTUMN), 22_000);
		spend(medical, p(2026, Season.AUTUMN), 3_600);
		budget(food, p(2026, Season.AUTUMN), 95_000);
		budget(fare, p(2026, Season.AUTUMN), 40_000);
		budget(misc, p(2026, Season.AUTUMN), 30_000);
		budget(supplies, p(2026, Season.AUTUMN), 8_000);

		budget(fun, p(2026, Season.WINTER), 5_000);   // 画面の季節にすでに保存した額
		flush();
	}

	/** 食費 1 科目。12 季節のすべてに取引がある（T1・T2・T3 の 4 季節は、それぞれ a1・a2・a3 の 1 件ずつ） */
	private void trendOnly(int a1, int a2, int a3) {
		Category food = category("食費");
		List<SeasonPeriod> seasons = twelveSeasons();
		for (int i = 0; i < 12; i++) {
			spend(food, seasons.get(i), i < 4 ? a1 : (i < 8 ? a2 : a3));
		}
		flush();
	}

	// ================================================================
	// 画面を取る・読む道具
	// ================================================================

	private MockHttpServletRequestBuilder request(String year, String season, String auto) {
		MockHttpServletRequestBuilder builder = get("/budgets").with(login);
		if (year != null) {
			builder.param("year", year);
		}
		if (season != null) {
			builder.param("season", season);
		}
		if (auto != null) {
			builder.param("auto", auto);
		}
		return builder;
	}

	private String page(String year, String season, String auto) throws Exception {
		return mockMvc.perform(request(year, season, auto)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private String winter(String auto) throws Exception {
		return page("2026", "winter", auto);
	}

	/** タグを取り、空白の連なりを 1 つにした文字列（文を探すため） */
	private static String plain(String html) {
		return html.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").trim();
	}

	private static String budgetTable(String html) {
		Matcher m = Pattern.compile("(?s)<table[^>]*budget-table[^>]*>(.*?)</table>").matcher(html);
		assertThat(m.find()).as("予算の表がある").isTrue();
		return m.group(1);
	}

	/** 見出し行のセルの文字 */
	private static List<String> headerCells(String html) {
		Matcher m = Pattern.compile("(?s)<thead>(.*?)</thead>").matcher(budgetTable(html));
		assertThat(m.find()).as("表の見出し").isTrue();
		return cellsOf(m.group(1));
	}

	/** 見出し以外の行（科目の行と、最後の合計の行）。1 行は セルの文字の並び */
	private static List<List<String>> bodyRows(String html) {
		String table = budgetTable(html).replaceAll("(?s)<thead>.*?</thead>", "");
		List<List<String>> rows = new ArrayList<>();
		Matcher m = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(table);
		while (m.find()) {
			rows.add(cellsOf(m.group(1)));
		}
		return rows;
	}

	private static List<String> cellsOf(String trHtml) {
		List<String> cells = new ArrayList<>();
		Matcher m = Pattern.compile("(?s)<t[dh][^>]*>(.*?)</t[dh]>").matcher(trHtml);
		while (m.find()) {
			cells.add(plain(m.group(1)));
		}
		return cells;
	}

	/** 科目名（または「合計」）で始まる行 */
	private static List<String> rowOf(String html, String label) {
		return bodyRows(html).stream().filter(cells -> !cells.isEmpty() && cells.get(0).startsWith(label)).findFirst()
				.orElseThrow(() -> new AssertionError("行がない：" + label));
	}

	/** 科目の行の、予算額の入力欄の値（value）。行ごとの HTML から取る */
	private static String amountInputOf(String html, String label) {
		Matcher rowMatcher = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(budgetTable(html));
		while (rowMatcher.find()) {
			String row = rowMatcher.group(1);
			if (!plain(row).startsWith(label)) {
				continue;
			}
			Matcher input = Pattern.compile("<input[^>]*name=\"rows\\[\\d+\\]\\.amount\"[^>]*>").matcher(row);
			assertThat(input.find()).as("入力欄がある：" + label).isTrue();
			Matcher value = Pattern.compile("value=\"([^\"]*)\"").matcher(input.group());
			assertThat(value.find()).as("入力欄に value がある：" + label).isTrue();
			return value.group(1);
		}
		throw new AssertionError("行がない：" + label);
	}

	/** 「自動予算」ボタン（auto=1 への <a>）の開始タグ。なければ null */
	private static String autoButtonTag(String html) {
		Matcher m = Pattern.compile("(?s)(<a[^>]*href=\"" + Pattern.quote(BUTTON_HREF) + "\"[^>]*>)\\s*自動予算\\s*</a>")
				.matcher(html);
		return m.find() ? m.group(1) : null;
	}

	private static String choiceTag(String html, int auto) {
		Matcher m = Pattern.compile("(?s)(<a[^>]*href=\"" + Pattern.quote(WINTER_URL + "&amp;auto=" + auto) + "\"[^>]*>)"
				+ "(?!\\s*自動予算\\s*</a>)").matcher(html);
		return m.find() ? m.group(1) : null;
	}

	private int budgetRowsOfUser() {
		flush();
		return (int) budgetRepository.findAll().stream().filter(b -> b.getUserId().equals(userId)).count();
	}

	// ================================================================
	// auto の受け取り（守りに関わる所。設計書 5.14、7.8）
	// ================================================================

	@ParameterizedTest(name = "auto={0} は受け付ける")
	@ValueSource(strings = { "1", "2", "3" })
	void autoOneToThreeIsAccepted(String auto) throws Exception {
		designExample();
		mockMvc.perform(request("2026", "winter", auto)).andExpect(status().isOk());
	}

	/** 1〜3 と完全に同じ文字だけ。ほかは 404（空・0・4・01・全角・前後の空白・小数・文字） */
	@ParameterizedTest(name = "auto=「{0}」は 404")
	@ValueSource(strings = { "0", "4", "abc", "", "01", "-1", "１", " 1", "1 ", "1.0", "2,3", "9999999999" })
	void autoOtherThanOneToThreeIs404(String auto) throws Exception {
		mockMvc.perform(request("2026", "winter", auto)).andExpect(status().isNotFound());
	}

	/** auto が 2 つ付いている（auto=1&auto=2）も 404 */
	@Test
	void autoGivenTwiceIs404() throws Exception {
		mockMvc.perform(request("2026", "winter", "1").param("auto", "2")).andExpect(status().isNotFound());
	}

	/** 年と季節の検査が先。auto が正しくても、年・季節がだめなら 404 */
	@ParameterizedTest(name = "year={0} season={1} auto=1 は 404")
	@CsvSource(value = { "1999|winter", "2101|winter", "2026|monsoon", "abc|winter", "2026|", "|winter" }, delimiter = '|')
	void badSeasonWithValidAutoIs404(String year, String season) throws Exception {
		mockMvc.perform(request(year, season, "1")).andExpect(status().isNotFound());
	}

	/** auto だけで、年・季節がなければ、今の季節（2026 年 秋）。200 で案が入る */
	@Test
	void autoWithoutSeasonUsesTheCurrentSeason() throws Exception {
		Category food = category("食費");
		spend(food, p(2026, Season.SUMMER), 15_200);
		flush();

		String text = plain(page(null, null, "1"));

		assertThat(text).contains("2026年 秋");
		assertThat(text).contains("過去の秋の実績をもとにした自動予算は 16,000 円です。");
	}

	/** 案を入れても、保存はしない（予算の行は増えない） */
	@Test
	void autoDoesNotSaveAnything() throws Exception {
		designExample();
		int before = budgetRowsOfUser();

		winter("1");
		winter("2");
		winter("3");

		assertThat(budgetRowsOfUser()).isEqualTo(before);
	}

	/** 案を入れた画面でも、保存のフォームは今までどおり（POST、CSRF のトークンつき、年と季節を含む URL） */
	@Test
	void saveFormKeepsPostAndCsrfToken() throws Exception {
		designExample();

		String body = winter("1");

		assertThat(body).contains("action=\"" + WINTER_URL + "\"");
		assertThat(body).contains("method=\"post\"");
		assertThat(body).contains("name=\"_csrf\"");
	}

	// ================================================================
	// ふだんの画面（auto なし）
	// ================================================================

	/** ふだんの画面：表は 2 列のまま。案の文・選ぶリンク・直前の季節の列は出ない。入力欄は保存済みの額か 0 円 */
	@Test
	void plainScreenHasTwoColumnsAndNoProposal() throws Exception {
		designExample();

		String body = winter(null);
		String text = plain(body);

		assertThat(headerCells(body)).hasSize(2);
		assertThat(headerCells(body).get(0)).isEqualTo("科目");
		assertThat(headerCells(body).get(1)).isEqualTo("予算額（円）");
		assertThat(text).doesNotContain("自動予算は");
		assertThat(text).doesNotContain("直前の季節（");
		assertThat(text).doesNotContain("1. これで OK");
		assertThat(text).doesNotContain("案の根拠");
		assertThat(amountInputOf(body, "食費")).isEqualTo("0");
		assertThat(amountInputOf(body, "娯楽")).isEqualTo("5000");
		assertThat(rowOf(body, "食費").get(0)).contains("未保存");
		assertThat(rowOf(body, "娯楽").get(0)).doesNotContain("未保存");
	}

	/** ふだんの画面にも、合計の行（保存済み 5,000 円＋0 円×5）と、ふだんは隠した 2 つの固定の文 */
	@Test
	void plainScreenHasTotalRowWithHiddenWarnings() throws Exception {
		designExample();

		String body = winter(null);
		List<String> total = rowOf(body, "合計");

		assertThat(total).hasSize(2);
		assertThat(total.get(1)).contains("5,000");
		assertThat(body).containsPattern("(?s)<[^>]*\\bhidden\\b[^>]*>\\s*数字でない欄があります\\s*</");
		assertThat(body).containsPattern("(?s)<[^>]*\\bhidden\\b[^>]*>\\s*空の欄があります\\s*</");
	}

	// ================================================================
	// 「自動予算」ボタンと、ボタンの横の一言
	// ================================================================

	/** ボタンは GET のリンク（auto=1）で、ボタンの見た目（btn）。横に一言。押す前の画面にも出る */
	@Test
	void buttonIsAGetLinkWithTheNoteBesideIt() throws Exception {
		designExample();

		String body = winter(null);
		String text = plain(body);
		String note = "手で直した額は、ボタンを押すと消えます。保存した額は、保存するまで変わりません。";

		String tag = autoButtonTag(body);
		assertThat(tag).as("自動予算ボタン").isNotNull();
		assertThat(tag).contains("btn");
		assertThat(text).contains(note);
		assertThat(text.indexOf("自動予算")).isLessThan(text.indexOf(note));
		assertThat(body.indexOf(note)).isLessThan(body.indexOf("budget-table"));
	}

	/** 案を入れたあとも、ボタンと一言は出る（もう一度押すと、1 で、もとから計算し直す） */
	@Test
	void buttonStaysAfterTheProposalIsIn() throws Exception {
		designExample();

		String body = winter("2");

		assertThat(autoButtonTag(body)).isNotNull();
		assertThat(plain(body)).contains("手で直した額は、ボタンを押すと消えます。");
	}

	/** 過去の同じ季節が、範囲（2000 年 春）より前しかないとき：ボタンも一言も出さない（2000 年はどの季節も） */
	@ParameterizedTest(name = "2000 年 {0} は、ボタンを出さない")
	@ValueSource(strings = { "spring", "summer", "autumn", "winter" })
	void noButtonWhenAllPastSeasonsAreBeforeTheRange(String season) throws Exception {
		category("食費");
		flush();

		String body = page("2000", season, null);

		assertThat(body).doesNotContain("auto=1");
		assertThat(plain(body)).doesNotContain("自動予算");
		assertThat(plain(body)).doesNotContain("手で直した額は");
	}

	/** 過去の同じ季節が 1 つでも範囲の中にあれば、ボタンを出す（2001 年 春 → 2000 年 春） */
	@Test
	void buttonIsShownWhenOnePastSeasonIsInRange() throws Exception {
		category("食費");
		flush();

		String body = page("2001", "spring", null);

		assertThat(body).contains("href=\"/budgets?year=2001&amp;season=spring&amp;auto=1\"");
		assertThat(plain(body)).contains("手で直した額は、ボタンを押すと消えます。");
	}

	/** 範囲の外しかない画面に auto で直接来ても、エラーにしない（材料がなければ「案を作れる材料がありません」。Q019-3） */
	@Test
	void autoDirectlyOnTheFirstSeasonShowsNoMaterial() throws Exception {
		Category food = category("食費");
		budget(food, p(2000, Season.SPRING), 7_000);
		flush();

		String body = page("2000", "spring", "1");

		assertThat(plain(body)).contains("案を作れる材料がありません");
		assertThat(amountInputOf(body, "食費")).isEqualTo("7000");
		assertThat(headerCells(body).get(1)).as("直前の季節がないときの見出し").isEqualTo("直前の季節の予算");
		assertThat(headerCells(body).get(2)).isEqualTo("直前の季節の実績");
	}

	/** 科目が 0 件のときは、ボタンも一言も出さない（表も保存ボタンもない画面なので） */
	@Test
	void noButtonWithoutCategories() throws Exception {
		String body = winter(null);

		assertThat(plain(body)).contains("先に科目を追加してください");
		assertThat(plain(body)).doesNotContain("自動予算");
		assertThat(body).doesNotContain("budget-table");
	}

	// ================================================================
	// 最初に出す 3 行の文（設計書 5.14。文そのもの）
	// ================================================================

	/** 設計書 5.14 の例：1 行目・2 行目・注記・3 行目〔一部の科目は直前の季節〕。この順で、表より上 */
	@Test
	void threeLinesOfTheDesignExample() throws Exception {
		designExample();

		String body = winter("1");
		String text = plain(body);

		String line1 = "直前の季節（2026 年 秋）は予算 173,000 円・実績 80,400 円（11/11 まで）でした。";
		String line2 = "ここ 3 年、出費は増える傾向です。そのため、案は過去の冬の額より 10% 多くしています。";
		String note = "※ 過去の冬の額は、だいたい 2 年前の水準です。ここ 3 年の増え方を 2 年分延ばして、今年の冬の水準に合わせています。";
		String line3 = "過去の冬の実績をもとにした自動予算は 186,000 円です。（一部の科目は直前の季節をもとにしています）";
		assertThat(text).contains(line1).contains(line2).contains(note).contains(line3);
		assertThat(text.indexOf(line1)).isLessThan(text.indexOf(line2));
		assertThat(text.indexOf(line2)).isLessThan(text.indexOf(note));
		assertThat(text.indexOf(note)).isLessThan(text.indexOf(line3));
		assertThat(body.indexOf("自動予算は")).isLessThan(body.indexOf("budget-table"));
		assertThat(body.indexOf("直前の季節（")).isLessThan(body.indexOf("自動予算は"));
		assertThat(text).doesNotContain("案を作れる材料がありません");
		assertThat(text).doesNotContain("実績がないので");
	}

	/** 注記は、傾向の文の下に「小さく」出す（small） */
	@Test
	void theNoteIsSmall() throws Exception {
		designExample();

		String body = winter("1");

		assertThat(body).containsPattern("(?s)<small[^>]*>\\s*※ 過去の冬の額は、");
	}

	/** 傾向の文の 4 通り（増える・減る・上限 2 通り。「ほぼ同じ」は下の別のテスト）。全科目がルール①なので、終わりに何も付けない */
	@ParameterizedTest(name = "T1={0} T2={1} T3={2} → {4}")
	@CsvSource(value = {
			"142500|150000|157500|ここ 3 年、出費は増える傾向です。そのため、案は過去の冬の額より 10% 多くしています。|165,000",
			"157500|150000|142500|ここ 3 年、出費は減る傾向です。そのため、案は過去の冬の額より 10% 少なくしています。|135,000",
			"75000|150000|225000|ここ 3 年、出費は増える傾向です。そのため、案は過去の冬の額より 30% 多く（上限）しています。|195,000",
			"225000|150000|75000|ここ 3 年、出費は減る傾向です。そのため、案は過去の冬の額より 30% 少なく（上限）しています。|105,000"
	}, delimiter = '|')
	void trendSentence(int a1, int a2, int a3, String sentence, String total) throws Exception {
		trendOnly(a1, a2, a3);

		String text = plain(winter("1"));

		assertThat(text).contains(sentence);
		assertThat(text).contains("※ 過去の冬の額は、だいたい 2 年前の水準です。");
		assertThat(text).contains("過去の冬の実績をもとにした自動予算は " + total + " 円です。");
		assertThat(text).doesNotContain("直前の季節をもとにしています");   // 全科目がルール①のときは何も付けない
		assertThat(text).contains("直前の季節（2026 年 秋）は予算 未設定・実績 0 円（11/11 まで）でした。");
	}

	/** 傾向が「ほぼ同じ」（100% ちょうど）のときは、小さな注記を出さない（増え方を延ばす、が意味をなさないため） */
	@Test
	void noNoteWhenTheTrendIsAboutTheSame() throws Exception {
		trendOnly(150_000, 150_000, 150_000);

		String body = winter("1");
		String text = plain(body);

		assertThat(text).contains("ここ 3 年、出費はほぼ同じです。");
		assertThat(text).doesNotContain("※ 過去の冬の額は");
		assertThat(body).doesNotContain("<small class=\"auto-budget-note\">※");
		assertThat(text).contains("過去の冬の実績をもとにした自動予算は 150,000 円です。");
	}

	/** 12 季節がそろわないとき：傾向は入れていない、の文。注記は出さない。全科目が②〜④（記録はある）→「すべての科目が…」 */
	@Test
	void trendNotCalculatedAndEveryCategoryFromPrevious() throws Exception {
		Category food = category("食費");
		Category fare = category("交通費");
		Category misc = category("雑費");
		spend(food, p(2023, Season.WINTER), 8_000);    // 3 年のうち 1 年だけ → 中央値 0 円
		spend(fare, p(2024, Season.WINTER), 5_000);
		spend(misc, p(2025, Season.WINTER), 6_000);
		spend(food, p(2026, Season.AUTUMN), 3_600);    // 直前の季節の実績 → ②（ペース 4,550）
		flush();

		String text = plain(winter("1"));

		assertThat(text).contains("記録が 3 年分そろっていないので、増える・減る傾向は入れていません。");
		assertThat(text).doesNotContain("※ 過去の冬の額は");
		assertThat(text).doesNotContain("傾向です");
		assertThat(text).contains("過去の冬の実績をもとにした自動予算は 5,000 円です。（すべての科目が直前の季節をもとにしています）");
	}

	/** 記録のある年が 1 つもないとき：2 行目と注記の代わりに「過去の○の実績がないので…」。3 行目には何も付けない */
	@Test
	void noRecordYearsSentence() throws Exception {
		Category food = category("食費");
		spend(food, p(2026, Season.SUMMER), 15_200);   // 直前の季節（2026 夏。終わっている）だけ
		flush();

		String body = page("2026", "autumn", "1");
		String text = plain(body);

		assertThat(text).contains("直前の季節（2026 年 夏）は予算 未設定・実績 15,200 円でした。");   // 終わった季節に「まで」は付けない
		assertThat(text).contains("過去の秋の実績がないので、直前の季節の実績や予算をもとにしました。");
		assertThat(text).doesNotContain("ここ 3 年");
		assertThat(text).doesNotContain("※ 過去の秋の額は");
		assertThat(text).contains("過去の秋の実績をもとにした自動予算は 16,000 円です。");
		assertThat(text).doesNotContain("をもとにしています");
		assertThat(text.indexOf("実績がないので")).isLessThan(text.indexOf("自動予算は"));
	}

	/** 全科目がルール④：1 行目と「案を作れる材料がありません」だけ。入力欄は何も変えない。選ぶリンクは出さない */
	@Test
	void noMaterialShowsOnlyLineOneAndTheMessage() throws Exception {
		Category food = category("食費");
		category("交通費");
		budget(food, p(2026, Season.WINTER), 7_000);
		flush();

		String body = winter("1");
		String text = plain(body);

		assertThat(text).contains("直前の季節（2026 年 秋）は予算 未設定・実績 0 円（11/11 まで）でした。");
		assertThat(text).contains("案を作れる材料がありません");
		assertThat(text).doesNotContain("自動予算は");
		assertThat(text).doesNotContain("実績がないので");
		assertThat(text).doesNotContain("ここ 3 年");
		assertThat(amountInputOf(body, "食費")).isEqualTo("7000");
		assertThat(amountInputOf(body, "交通費")).isEqualTo("0");
		assertThat(choiceTag(body, 2)).isNull();
		assertThat(rowOf(body, "食費").get(3)).isEqualTo("材料なし（今の値のまま）");
	}

	// ================================================================
	// 5 列の表、案が入った入力欄、案の根拠のセル、合計の行（設計書 2.3、5.14）
	// ================================================================

	/** 案を入れたあと：見出しは 5 列 */
	@Test
	void proposalTableHasFiveColumns() throws Exception {
		designExample();

		List<String> header = headerCells(winter("1"));

		assertThat(header).hasSize(5);
		assertThat(header.get(0)).isEqualTo("科目");
		assertThat(header.get(1)).isEqualTo("2026 年 秋の予算");
		assertThat(header.get(2)).isEqualTo("2026 年 秋の実績（11/11 まで）");   // 今日を含む季節だけ、日付を見出しに 1 回
		assertThat(header.get(3)).isEqualTo("案の根拠（過去 3 年の冬）");
		assertThat(header.get(4)).isEqualTo("予算額（円）");
	}

	/** 科目の行は、すべて 5 つのセル（合計の行も）。科目の登録順 */
	@Test
	void everyRowHasFiveCellsInRegistrationOrder() throws Exception {
		designExample();

		List<List<String>> rows = bodyRows(winter("1"));

		assertThat(rows).hasSize(7);
		assertThat(rows).allSatisfy(cells -> assertThat(cells).hasSize(5));
		assertThat(rows.stream().map(cells -> cells.get(0).replace("未保存", "").trim()).toList())
				.containsExactly("食費", "交通費", "雑費", "医療費", "日用品", "娯楽", "合計");
	}

	/** 案の入力欄・直前の季節の予算と実績・根拠のセル（設計書 5.14 の例。割合 100%） */
	@Test
	void rowsOfTheDesignExampleAtOneHundredPercent() throws Exception {
		designExample();

		String body = winter("1");

		assertRow(body, "食費", "95,000", "49,200", "ペース 約 62,200", "中央値 90,000 円（3 年）", "99000");
		assertRow(body, "交通費", "40,000", "5,600", "ペース 約 7,100", "中央値 38,000 円（3 年）", "42000");
		assertRow(body, "雑費", "30,000", "22,000", "ペース 約 27,900", "中央値 24,000 円（3 年）", "27000");
		assertRow(body, "医療費", "未設定", "3,600", "ペース 約 4,600", "秋のペース 4,600 円", "5000");
		assertRow(body, "日用品", "8,000", "0", null, "秋の予算 8,000 円", "8000");
		assertRow(body, "娯楽", "未設定", "0", null, "材料なし（今の値のまま）", "5000");
	}

	private void assertRow(String body, String name, String previousBudget, String actual, String pace, String basis,
			String input) {
		List<String> cells = rowOf(body, name);
		assertThat(cells.get(1)).as(name + " 直前の予算").isEqualTo(previousBudget);
		if (pace == null) {
			assertThat(cells.get(2)).as(name + " 直前の実績").isEqualTo(actual);
		} else {
			assertThat(cells.get(2)).as(name + " 直前の実績").startsWith(actual).endsWith(pace);
		}
		assertThat(cells.get(2)).as(name + " 日付は各行に出さない（見出しに 1 回だけ）").doesNotContain("11/11").doesNotContain("まで");
		assertThat(cells.get(3)).as(name + " 根拠").isEqualTo(basis);
		assertThat(amountInputOf(body, name)).as(name + " 入力欄").isEqualTo(input);
	}

	/** 「このペースなら」は、実績の下に小さく出す（small） */
	@Test
	void paceIsSmall() throws Exception {
		designExample();

		String body = winter("1");

		assertThat(body).containsPattern("(?s)<small[^>]*>\\s*ペース 約 62,200\\s*</small>");
	}

	/** 合計の行：直前の予算の合計（設定済みの科目だけ）、実績の合計とそのペース（合計から出す。科目の足し算とは 100 円ちがう）、案の合計 */
	@Test
	void totalRowOfTheDesignExample() throws Exception {
		designExample();

		List<String> total = rowOf(winter("1"), "合計");

		assertThat(total.get(1)).isEqualTo("173,000");
		assertThat(total.get(2)).startsWith("80,400").endsWith("ペース 約 101,700").doesNotContain("11/11");
		assertThat(total.get(4)).contains("186,000");
	}

	/** 直前の季節が終わっているとき：「まで」も「このペースなら」も付けない。根拠は「実績」。予算が 1 つもなければ合計は「未設定」 */
	@Test
	void finishedPreviousSeasonShowsOnlyTheActual() throws Exception {
		Category food = category("食費");
		spend(food, p(2026, Season.SUMMER), 15_200);
		flush();

		String body = page("2026", "autumn", "1");

		List<String> row = rowOf(body, "食費");
		assertThat(row.get(1)).isEqualTo("未設定");
		assertThat(row.get(2)).isEqualTo("15,200");
		assertThat(row.get(3)).isEqualTo("夏の実績 15,200 円");
		List<String> total = rowOf(body, "合計");
		assertThat(total.get(1)).isEqualTo("未設定");
		assertThat(total.get(2)).isEqualTo("15,200");
		assertThat(total.get(4)).contains("16,000");
	}

	/** 記録のある年が 2 つなら「平均」 */
	@Test
	void basisWordingForTwoConsecutiveYears() throws Exception {
		Category food = category("食費");
		spend(food, p(2024, Season.WINTER), 20_000);
		spend(food, p(2025, Season.WINTER), 23_000);
		flush();

		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("平均 21,500 円（2 年）");
	}

	/** 1 つなら「去年の冬」〔その年が 1 年前〕。年が飛ぶときは「2023・2025 年の冬の平均」 */
	@Test
	void basisWordingForOneYearAndForAGap() throws Exception {
		Category food = category("食費");
		spend(food, p(2025, Season.WINTER), 22_050);
		flush();
		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("去年 22,050 円");

		spend(food, p(2023, Season.WINTER), 20_000);
		flush();
		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("平均 21,025 円（2023・2025 年）");
	}

	// ================================================================
	// 選ぶリンク 3 つと、今の選択（設計書 2.3、Q019-4）
	// ================================================================

	/** 案を入れたあと：リンク 3 つ（auto=1・2・3）。いまの選択だけ太字。割合ごとの案と合計 */
	@ParameterizedTest(name = "auto={0} → 合計 {7}")
	@CsvSource(value = {
			"1|99000|42000|27000|5000|8000|5000|186,000",
			"2|90000|38000|24000|5000|8000|5000|170,000",
			"3|109000|46000|30000|6000|9000|5000|205,000"
	}, delimiter = '|')
	void choiceLinksAndAmounts(int auto, String food, String fare, String misc, String medical, String supplies,
			String fun, String total) throws Exception {
		designExample();

		String body = winter(String.valueOf(auto));

		assertThat(amountInputOf(body, "食費")).isEqualTo(food);
		assertThat(amountInputOf(body, "交通費")).isEqualTo(fare);
		assertThat(amountInputOf(body, "雑費")).isEqualTo(misc);
		assertThat(amountInputOf(body, "医療費")).isEqualTo(medical);
		assertThat(amountInputOf(body, "日用品")).isEqualTo(supplies);
		assertThat(amountInputOf(body, "娯楽")).isEqualTo(fun);
		assertThat(plain(body)).contains("自動予算は " + total + " 円です。");
		assertThat(rowOf(body, "合計").get(4)).contains(total);

		for (int n = 1; n <= 3; n++) {
			String tag = choiceTag(body, n);
			assertThat(tag).as("auto=" + n + " のリンク").isNotNull();
			if (n == auto) {
				assertThat(tag).as("いまの選択は太字").contains("fw-bold");
			} else {
				assertThat(tag).as("ほかは太字にしない").doesNotContain("fw-bold");
			}
		}
	}

	/** 3 つのリンクの文字 */
	@Test
	void choiceLinkLabels() throws Exception {
		designExample();

		String body = winter("1");

		assertThat(body).containsPattern("(?s)auto=1\"[^>]*>\\s*1\\. これで OK\\s*</a>");
		assertThat(body).containsPattern("(?s)auto=2\"[^>]*>\\s*2\\. 節約したい（さらに 10% 減らす）\\s*</a>");
		assertThat(body).containsPattern("(?s)auto=3\"[^>]*>\\s*3\\. 上乗せしたい（さらに 10% 増やす）\\s*</a>");
	}

	// ================================================================
	// ブラウザで見つかった直し（hidden、チップ、並び、表の下の小さな一言）
	// ================================================================

	/** hidden の文は、display を指定するクラス（d-block など）を持たない。持つと hidden が打ち消され、ふだんから赤字で出る */
	@ParameterizedTest(name = "auto={0} でも、hidden の文に display のクラスを付けない")
	@ValueSource(strings = { "", "1" })
	void hiddenWarningsHaveNoDisplayClass(String auto) throws Exception {
		designExample();

		String body = winter(auto.isEmpty() ? null : auto);

		Matcher tags = Pattern.compile("<[a-z]+(?:\\s[^>]*)?\\shidden(?=[\\s>/=])[^>]*>").matcher(body);
		int n = 0;
		while (tags.find()) {
			n++;
			assertThat(tags.group()).as("hidden の要素").doesNotContainPattern("\\bd-(block|flex|inline|inline-block|inline-flex|grid|table|table-cell|table-row)\\b");
		}
		assertThat(n).as("hidden の要素は 2 つ").isEqualTo(2);
	}

	/** 自作の CSS に、[hidden] を display: none !important にする規則がある（クラスで打ち消されないように） */
	@Test
	void styleSheetKeepsHiddenHidden() throws Exception {
		String css = new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);

		assertThat(css).containsPattern("(?s)\\[hidden\\][^{]*\\{[^}]*display:\\s*none\\s*!important");
	}

	/** 「未保存」は小さなチップ（ふだんの画面にも出す）。保存済みの行には出さない。古い言い方は残さない */
	@Test
	void unsavedChip() throws Exception {
		designExample();

		String body = winter(null);

		assertThat(count(body, "未保存")).isEqualTo(5);   // 保存済みの娯楽以外の 5 科目
		assertThat(body).containsPattern("<span class=\"[^\"]*unset-mark[^\"]*\">\\s*未保存\\s*</span>");
		assertThat(body).doesNotContain("この季節は未設定");
		assertThat(rowOf(body, "娯楽").get(0)).doesNotContain("未保存");
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/** 「0〜3,000,000 の整数」は、見出しではなく、表の下に小さく（ふだんの画面にも、案を入れたあとにも） */
	@ParameterizedTest(name = "auto={0}：入力の範囲は表の下に小さく")
	@ValueSource(strings = { "", "1" })
	void amountRangeHintIsBelowTheTable(String auto) throws Exception {
		designExample();

		String body = winter(auto.isEmpty() ? null : auto);

		assertThat(headerCells(body).get(headerCells(body).size() - 1)).doesNotContain("3000000").doesNotContain("0〜");
		int table = body.indexOf("</table>");
		assertThat(table).isPositive();
		assertThat(body.indexOf("0〜3,000,000 の整数")).isGreaterThan(table);
		assertThat(body).containsPattern("(?s)<small[^>]*>[^<]*0〜3,000,000 の整数");
		assertThat(body.indexOf("0〜3,000,000 の整数")).as("表の下（保存は上にある）").isLessThan(body.indexOf("</form>", table));
	}

	/** 並びは、ボタン（と一言）→ 上の 3 行の文 → 選ぶリンク 3 つ → 表 */
	@Test
	void orderIsButtonThenThreeLinesThenChoicesThenTable() throws Exception {
		designExample();

		String body = winter("1");

		int button = body.indexOf("手で直した額は");
		int line1 = body.indexOf("直前の季節（2026 年 秋）は");
		int line3 = body.indexOf("自動予算は");
		int choices = body.indexOf("1. これで OK");
		int table = body.indexOf("budget-table");
		assertThat(button).isPositive();
		assertThat(button).isLessThan(line1);
		assertThat(line1).isLessThan(line3);
		assertThat(line3).isLessThan(choices);
		assertThat(choices).isLessThan(table);
	}

	/** 「ペース」は実績の下に 1 行で（折り返さない目印の nowrap。改行 <br> を入れない） */
	@Test
	void paceIsOneLine() throws Exception {
		designExample();

		String body = winter("1");

		assertThat(body).containsPattern("(?s)<small[^>]*nowrap[^>]*>\\s*ペース 約 62,200\\s*</small>");
	}

	// ================================================================
	// 「保存」ボタンは上だけ（設計書 2.3）。表を囲むフォームの外から、form 属性で同じフォームを送る
	// ================================================================

	private static final Pattern SAVE_BUTTON = Pattern.compile("(?s)<button[^>]*>\\s*保存\\s*</button>");

	private static String saveButtonTag(String body) {
		Matcher m = SAVE_BUTTON.matcher(body);
		assertThat(m.find()).as("保存ボタンがある").isTrue();
		String tag = m.group();
		assertThat(m.find()).as("保存ボタンは1つだけ（表の下にはない）").isFalse();
		return tag;
	}

	/** 保存ボタンは 1 つで、表より前（上）。表を囲むフォームの外にあり、form 属性でそのフォームを指す。塗りのボタン */
	@ParameterizedTest(name = "auto={0}：保存ボタンは上に 1 つ、同じフォームを送る")
	@ValueSource(strings = { "", "1", "2", "3" })
	void saveButtonIsOnlyAtTheTopAndSubmitsTheSameForm(String auto) throws Exception {
		designExample();

		String body = winter(auto.isEmpty() ? null : auto);
		String tag = saveButtonTag(body);

		Matcher formTag = Pattern.compile("<form[^>]*\\bid=\"([^\"]+)\"[^>]*method=\"post\"[^>]*>|<form[^>]*method=\"post\"[^>]*\\bid=\"([^\"]+)\"[^>]*>").matcher(body);
		assertThat(formTag.find()).as("保存のフォーム（POST）に id がある").isTrue();
		String formId = formTag.group(1) != null ? formTag.group(1) : formTag.group(2);
		assertThat(tag).contains("type=\"submit\"").contains("form=\"" + formId + "\"").contains("btn-primary");
		assertThat(body.indexOf(tag)).as("表より前").isLessThan(body.indexOf("budget-table"));
		assertThat(body.indexOf(tag)).as("フォームの外（フォームより前）").isLessThan(body.indexOf(formTag.group()));
		assertThat(body.substring(body.indexOf("</table>"))).as("表の下に保存ボタンはない").doesNotContain(">保存</button>");
		// CSRF のトークンは、保存のフォームの中にあり、上のボタンからも付く（同じフォームを送るため）
		String formBody = body.substring(body.indexOf(formTag.group()), body.indexOf("</form>", body.indexOf(formTag.group())));
		assertThat(formBody).contains("name=\"_csrf\"");
		assertThat(formBody).as("入力欄はそのフォームの中").contains("rows[0].amount");
	}

	/** ふだんの画面：保存ボタンは「自動予算」ボタンと同じ行（間に行の終わりがない）の、そのあと（右端）。リンクの行はない */
	@Test
	void saveButtonIsOnTheSameRowAsTheAutoButtonOnTheRight() throws Exception {
		designExample();

		String body = winter(null);
		String save = saveButtonTag(body);
		String button = autoButtonTag(body);

		int from = body.indexOf(button);
		int to = body.indexOf(save);
		assertThat(to).isGreaterThan(from);
		assertThat(body.substring(from, to)).as("同じ行（同じ入れ物の中）").doesNotContain("</div>");
		assertThat(save).as("右端").contains("budget-save");
	}

	/** 案を入れたあと：保存ボタンは、1〜3 のリンクと同じ行の右端。リンクのあと。塗りのボタン。リンクとは間をあける（CSS） */
	@Test
	void saveButtonIsOnTheSameRowAsTheChoiceLinksOnTheRight() throws Exception {
		designExample();

		String body = winter("2");
		String save = saveButtonTag(body);

		int lastLink = body.indexOf("3. 上乗せしたい");
		int to = body.indexOf(save);
		assertThat(to).as("3 つめのリンクのあと").isGreaterThan(lastLink);
		assertThat(body.substring(lastLink, to)).as("同じ行（同じ入れ物の中）").doesNotContain("</div>").doesNotContain("</p>");
		assertThat(save).contains("btn-primary").contains("budget-save");
		assertThat(autoButtonTag(body)).isNotNull();
		assertThat(body.indexOf(autoButtonTag(body))).as("「自動予算」ボタンの行とは別の行").isLessThan(body.indexOf("直前の季節（"));
	}

	/** 自動予算のボタンを出さない画面（2000 年）や、材料がなくて選ぶリンクがない画面でも、保存ボタンは上に 1 つある */
	@Test
	void saveButtonStillExistsWithoutTheAutoButtonOrTheChoiceLinks() throws Exception {
		Category food = category("食費");
		budget(food, p(2000, Season.SPRING), 7_000);
		flush();

		String noButton = page("2000", "spring", null);
		saveButtonTag(noButton);
		assertThat(autoButtonTag(noButton)).isNull();
		assertThat(noButton.indexOf(">保存</button>")).isLessThan(noButton.indexOf("budget-table"));

		String noMaterial = page("2000", "spring", "1");
		saveButtonTag(noMaterial);
		assertThat(noMaterial.indexOf(">保存</button>")).isLessThan(noMaterial.indexOf("budget-table"));
	}

	/** 科目が 0 件のときは、保存ボタンもフォームも出さない（今までどおり） */
	@Test
	void noSaveButtonWithoutCategories() throws Exception {
		String body = winter(null);

		assertThat(body).doesNotContain(">保存</button>").doesNotContain("action=\"/budgets?");   // ログアウトも POST なので、保存の URL で見分ける
	}

	/** 保存ボタンを右端に寄せ、リンクと間をあける規則が、自作の CSS にある */
	@Test
	void styleSheetPutsTheSaveButtonOnTheRight() throws Exception {
		String css = new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);

		assertThat(css).containsPattern("(?s)\\.budget-save\\s*\\{[^}]*margin-left:\\s*auto");
		assertThat(css).containsPattern("(?s)\\.auto-budget-choices\\s*\\{[^}]*gap:");
	}

	// ================================================================
	// XSS（設計書 7.5）。科目名は th:text で出す
	// ================================================================

	@Test
	void categoryNameIsEscapedOnTheProposalScreen() throws Exception {
		Category tag = category("<b>太字</b>");
		spend(tag, p(2025, Season.WINTER), 10_000);
		flush();

		String body = winter("1");

		assertThat(body).contains("&lt;b&gt;太字&lt;/b&gt;");
		assertThat(body).doesNotContain("<b>太字</b>");
	}

}
