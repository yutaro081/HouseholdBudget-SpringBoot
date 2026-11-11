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
 * 合計の JavaScript（budget-total.js）の動きはここでは確かめない（Console の表。手順書 8 章）。ここでは、HTML の印と読み込みだけ確かめる。
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
		Matcher m = Pattern.compile("(?s)(<a[^>]*href=\"" + Pattern.quote(BUTTON_HREF) + "\"[^>]*>)(?:(?!</a>).)*自動予算(?:(?!</a>).)*</a>")
				.matcher(html);
		return m.find() ? m.group(1) : null;
	}

	private static String choiceTag(String html, int auto) {
		Matcher m = Pattern.compile("(?s)(<a[^>]*href=\"" + Pattern.quote(WINTER_URL + "&amp;auto=" + auto) + "\"[^>]*>)"
				+ "(?!(?:(?!</a>).)*自動予算)").matcher(html);
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

	/** issue 020：本体の幅は 1280px（table-wide） */
	@Test
	void mainIsTableWideSo1280px() throws Exception {
		category("食費");
		flush();

		assertThat(page("2026", "winter", null)).contains("<main class=\"container table-wide\"");
	}

	/** auto だけで、年・季節がなければ、今の季節（2026 年 秋）。200 で案が入る */
	@Test
	void autoWithoutSeasonUsesTheCurrentSeason() throws Exception {
		Category food = category("食費");
		spend(food, p(2026, Season.SUMMER), 15_200);
		flush();

		String text = plain(page(null, null, "1"));

		assertThat(text).contains("2026年 秋");
		assertThat(text).contains("過去の秋の実績がないので、直前の季節の実績や予算をもとにしました。");
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

	/** 設計書 5.14 の例：1 行目・2 行目・注記。この順で、表より上（3 行目はなくした） */
	@Test
	void twoLinesOfTheDesignExample() throws Exception {
		designExample();

		String body = winter("1");
		String text = plain(body);

		String line1 = "直前の季節（2026 年 秋）は予算 173,000 円・実績 80,400 円（11/11 まで）でした。";
		String line2 = "ここ 3 年、出費は増える傾向です。そのため、案は過去の冬の額より 10% 多くしています。";
		String note = "※ 過去の冬の額は、だいたい 2 年前の水準です。ここ 3 年の増え方を 2 年分延ばして、今年の冬の水準に合わせています。";
		assertThat(text).contains(line1).contains(line2).contains(note);
		assertThat(text.indexOf(line1)).isLessThan(text.indexOf(line2));
		assertThat(text.indexOf(line2)).isLessThan(text.indexOf(note));
		assertThat(body.indexOf(note.substring(0, 6))).isLessThan(body.indexOf("budget-table"));
		assertThat(text).doesNotContain("案を作れる材料がありません");
		assertThat(text).doesNotContain("実績がないので");
	}

	/** 3 行目（案の合計の文）と、その付記は、なくした。合計は表の一番下の合計の行で分かる */
	@ParameterizedTest(name = "auto={0}：3 行目と付記は出ない")
	@ValueSource(strings = { "1", "2", "3" })
	void theTotalSentenceAndItsSuffixAreGone(String auto) throws Exception {
		designExample();   // ①と②〜④が混ざる（以前は「一部の科目は…」が付いた）

		String body = winter(auto);
		String text = plain(body);

		assertThat(text).doesNotContain("をもとにした自動予算は").doesNotContain("自動予算は");
		assertThat(text).doesNotContain("一部の科目は").doesNotContain("すべての科目が").doesNotContain("直前の季節をもとにしています");
		assertThat(rowOf(body, "合計").get(4)).as("合計は合計の行で分かる").contains(auto.equals("1") ? "186,000" : (auto.equals("2") ? "170,000" : "205,000"));
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
		assertThat(text).doesNotContain("自動予算は");
		assertThat(rowOf(winter("1"), "合計").get(4)).contains(total);
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
		assertThat(text).doesNotContain("自動予算は");
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
		assertThat(text).doesNotContain("自動予算は").doesNotContain("すべての科目が");
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
		assertThat(text).doesNotContain("自動予算は").doesNotContain("をもとにしています");
		assertThat(text).as("ここ 3 年の文は出ず、この文だけが 2 行目").contains("過去の秋の実績がないので、直前の季節の実績や予算をもとにしました。");
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
		assertThat(rowOf(body, "食費").get(3)).isEqualTo("材料なし");
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
		assertThat(header.get(3)).isEqualTo("根拠（過去 3 年の冬の中央値）");
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

		assertRow(body, "食費", "95,000", "49,200", "ペース 約 62,200", "90,000", "99000");
		assertRow(body, "交通費", "40,000", "5,600", "ペース 約 7,100", "38,000", "42000");
		assertRow(body, "雑費", "30,000", "22,000", "ペース 約 27,900", "24,000", "27000");
		assertRow(body, "医療費", "—", "3,600", "ペース 約 4,600", "秋のペース 4,600", "5000");
		assertRow(body, "日用品", "8,000", "0", null, "秋の予算 8,000", "8000");
		assertRow(body, "娯楽", "—", "0", null, "材料なし", "5000");
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
		assertThat(row.get(1)).isEqualTo("—");
		assertThat(row.get(2)).isEqualTo("15,200");
		assertThat(row.get(3)).isEqualTo("夏の実績 15,200");
		List<String> total = rowOf(body, "合計");
		assertThat(total.get(1)).isEqualTo("—");
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

		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("平均 21,500（2 年）");
	}

	/** 1 つなら「去年の冬」〔その年が 1 年前〕。年が飛ぶときは「2023・2025 年の冬の平均」 */
	@Test
	void basisWordingForOneYearAndForAGap() throws Exception {
		Category food = category("食費");
		spend(food, p(2025, Season.WINTER), 22_050);
		flush();
		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("去年 22,050");

		spend(food, p(2023, Season.WINTER), 20_000);
		flush();
		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("平均 21,025（2023・2025 年）");
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
		assertThat(rowOf(body, "合計").get(4)).contains(total);

		for (int n = 1; n <= 3; n++) {
			String tag = choiceTag(body, n);
			assertThat(tag).as("auto=" + n + " のリンク").isNotNull();
			if (n == auto) {
				assertThat(tag).as("いまの選択は、選んでいると分かる印").contains("aria-current=\"true\"");
			} else {
				assertThat(tag).as("ほかには印を付けない").doesNotContain("aria-current");
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
		int line3 = body.indexOf("ここ 3 年");   // 最初の文の最後（2 行目）
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
		assertThat(count(body.substring(lastLink, to), "</div>")).as("同じ行：間にあるのは切りかえボタンの閉じだけ").isEqualTo(1);
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
	// 切りかえボタン（btn-group）、表の見た目（設計書 2.3。ブラウザで見て決めた）
	// ================================================================

	/** 1〜3 は、3 つをくっつけた切りかえボタン（btn-group）。中身は GET のリンク。保存は塗り、切りかえは枠線で、見た目が違う */
	@ParameterizedTest(name = "auto={0}：切りかえボタン")
	@ValueSource(ints = { 1, 2, 3 })
	void choicesAreAButtonGroup(int auto) throws Exception {
		designExample();

		String body = winter(String.valueOf(auto));

		Matcher group = Pattern.compile("(?s)<div[^>]*btn-group[^>]*>(.*?)</div>").matcher(body);
		assertThat(group.find()).as("btn-group がある").isTrue();
		assertThat(group.group()).contains("role=\"group\"");
		List<String> links = new ArrayList<>();
		Matcher a = Pattern.compile("<a [^>]*>").matcher(group.group(1));
		while (a.find()) {
			links.add(a.group());
		}
		assertThat(links).as("3 つがくっついている").hasSize(3);
		for (int n = 1; n <= 3; n++) {
			String tag = links.get(n - 1);
			assertThat(tag).contains("href=\"" + WINTER_URL + "&amp;auto=" + n + "\"").contains("btn").doesNotContain("btn-primary");
			if (n == auto) {
				assertThat(tag).as("選んでいるもの").contains("aria-current=\"true\"");
			} else {
				assertThat(tag).doesNotContain("aria-current");
			}
		}
		assertThat(plain(group.group(1))).contains("1. これで OK").contains("2. 節約したい（さらに 10% 減らす）").contains("3. 上乗せしたい（さらに 10% 増やす）");
		assertThat(saveButtonTag(body)).as("保存は塗りのボタン").contains("btn-primary").doesNotContain("outline");
		assertThat(body.indexOf("</div>", body.indexOf("btn-group"))).as("保存は切りかえボタンの外（右端）").isLessThan(body.indexOf(saveButtonTag(body)));
	}

	/** 選んでいる切りかえボタンは、メニューの「今の画面」と同じ薄いターコイズの地に濃い文字 */
	@Test
	void styleSheetMarksTheSelectedChoice() throws Exception {
		String css = new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);

		assertThat(css).containsPattern("(?s)\\[aria-current=\"true\"\\][^{]*\\{[^}]*background(-color)?:\\s*var\\(--hb-teal-pale\\)[^}]*color:\\s*var\\(--hb-teal-text\\)");
	}

	private static List<String> thTags(String body) {
		Matcher m = Pattern.compile("(?s)<thead>(.*?)</thead>").matcher(budgetTable(body));
		assertThat(m.find()).isTrue();
		List<String> tags = new ArrayList<>();
		Matcher th = Pattern.compile("<th[^>]*>").matcher(m.group(1));
		while (th.find()) {
			tags.add(th.group());
		}
		return tags;
	}

	/** 科目の行（最初の行）の、セルの開きタグ */
	private static List<String> tdTags(String body, String label) {
		Matcher row = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(budgetTable(body));
		while (row.find()) {
			if (plain(row.group(1)).startsWith(label)) {
				List<String> tags = new ArrayList<>();
				Matcher td = Pattern.compile("<t[dh][^>]*>").matcher(row.group(1));
				while (td.find()) {
					tags.add(td.group());
				}
				return tags;
			}
		}
		throw new AssertionError("行がない：" + label);
	}

	/** 数字の列（直前の季節の予算・実績、予算額）は、見出しも右寄せ（num）。科目と根拠の列は右寄せにしない */
	@Test
	void numberColumnHeadersAreRightAligned() throws Exception {
		designExample();

		List<String> auto = thTags(winter("1"));
		assertThat(auto).hasSize(5);
		assertThat(auto.get(0)).doesNotContain("num");
		assertThat(auto.get(1)).contains("num");
		assertThat(auto.get(2)).contains("num");
		assertThat(auto.get(3)).as("根拠の列も、見出しを右寄せ").contains("num");
		assertThat(auto.get(4)).contains("num");

		List<String> plain = thTags(winter(null));
		assertThat(plain).hasSize(2);
		assertThat(plain.get(0)).doesNotContain("num");
		assertThat(plain.get(1)).as("ふだんの画面の予算額も").contains("num");
	}

	/** 根拠の列：見出しもセルも右寄せ（num）で、実績の列との間をあける印（budget-basis）。他の列には付けない */
	@Test
	void basisColumnIsRightAlignedWithRoomOnTheLeft() throws Exception {
		designExample();

		String body = winter("1");
		List<String> th = thTags(body);
		List<String> td = tdTags(body, "食費");
		assertThat(th.get(3)).contains("num").contains("budget-basis");
		assertThat(td.get(3)).contains("num").contains("budget-basis");
		for (int i : new int[] { 0, 1, 2, 4 }) {
			assertThat(th.get(i)).as("見出し " + i).doesNotContain("budget-basis");
			assertThat(td.get(i)).as("セル " + i).doesNotContain("budget-basis");
		}
	}

	/** 自作の CSS：根拠のセルの左の余白を広げる（実績の列との間をあける） */
	@Test
	void styleSheetWidensTheGapBeforeTheBasisColumn() throws Exception {
		String css = new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);

		assertThat(css).containsPattern("(?s)\\.budget-basis[^{]*\\{[^}]*padding-left:\\s*[0-9.]+(rem|px)");
	}

	/** 3 年分そろった中央値は数字だけ（「（3 年）」を付けない）。2 年・1 年・飛ぶ年・ペース・予算・材料なしは今までの言葉 */
	@Test
	void medianOfThreeYearsIsJustTheNumber() throws Exception {
		designExample();

		String text = plain(winter("1"));

		assertThat(text).doesNotContain("（3 年）");
		assertThat(rowOf(winter("1"), "食費").get(3)).isEqualTo("90,000");
		assertThat(rowOf(winter("1"), "医療費").get(3)).isEqualTo("秋のペース 4,600");
		assertThat(rowOf(winter("1"), "日用品").get(3)).isEqualTo("秋の予算 8,000");
		assertThat(rowOf(winter("1"), "娯楽").get(3)).isEqualTo("材料なし");
	}

	/** 参考の 3 列（直前の季節の予算・実績・根拠）は budget-ref（灰色で小さい）。予算額の列は budget-amount（目立たせる） */
	@Test
	void referenceColumnsAreMutedAndTheAmountColumnStandsOut() throws Exception {
		designExample();

		String body = winter("1");
		List<String> th = thTags(body);
		List<String> td = tdTags(body, "食費");
		for (int i : new int[] { 1, 2, 3 }) {
			assertThat(th.get(i)).as("見出し " + i).contains("budget-ref");
			assertThat(td.get(i)).as("セル " + i).contains("budget-ref");
		}
		assertThat(th.get(0)).doesNotContain("budget-ref");
		assertThat(td.get(0)).doesNotContain("budget-ref");
		assertThat(th.get(4)).contains("budget-amount");
		assertThat(td.get(4)).contains("budget-amount");
		assertThat(th.get(4)).doesNotContain("budget-ref");
	}

	/** 前の季節の予算が未設定のセルは、薄い灰色の「—」（budget-dash）。設定済みのセルには付けない */
	@Test
	void unsetPreviousBudgetIsAGrayDash() throws Exception {
		designExample();

		String body = winter("1");
		Matcher rows = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(budgetTable(body));
		int medical = 0;
		int food = 0;
		while (rows.find()) {
			String name = plain(rows.group(1));
			if (name.startsWith("医療費")) {
				medical = rows.group(1).split("budget-dash", -1).length - 1;
				assertThat(rows.group(1)).containsPattern("<span class=\"budget-dash\">—</span>");
			}
			if (name.startsWith("食費")) {
				food = rows.group(1).split("budget-dash", -1).length - 1;
			}
		}
		assertThat(medical).isEqualTo(1);
		assertThat(food).isZero();
		assertThat(body).doesNotContain("未設定</td>");
	}

	/** 自作の CSS：参考の列は灰色で小さい、予算額の列は太い、「—」は薄い灰色、合計の行は 1.25rem で上の線が太い */
	@Test
	void styleSheetStylesTheTable() throws Exception {
		String css = new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);

		assertThat(css).containsPattern("(?s)\\.budget-ref[^{]*\\{[^}]*color:\\s*var\\(--hb-text-sub\\)");
		assertThat(css).containsPattern("(?s)\\.budget-amount[^{]*\\{[^}]*font-weight:\\s*700");
		assertThat(css).containsPattern("(?s)\\.budget-dash\\s*\\{[^}]*color:\\s*#");
		assertThat(css).containsPattern("(?s)tfoot[^{]*\\{[^}]*font-size:\\s*1\\.25rem");
		assertThat(css).containsPattern("(?s)tfoot[^{]*\\{[^}]*border-top:\\s*[3-9]px");
	}

	/** 根拠の見出しは「根拠（過去 3 年の○の中央値）」。○は画面の季節（秋のとき「秋」） */
	@Test
	void basisHeaderNamesTheScreenSeason() throws Exception {
		Category food = category("食費");
		spend(food, p(2026, Season.SUMMER), 15_200);
		flush();

		assertThat(headerCells(page("2026", "autumn", "1")).get(3)).isEqualTo("根拠（過去 3 年の秋の中央値）");
	}

	// ================================================================
	// 「自動予算」ボタンの見た目（ターコイズ、きらきらのアイコン。設計書 2.3。ブラウザで見て決めた）
	// ================================================================

	/** 「自動予算」ボタンの <a> から </a> まで */
	private static String autoButtonHtml(String body) {
		String tag = autoButtonTag(body);
		assertThat(tag).as("自動予算ボタン").isNotNull();
		int start = body.indexOf(tag);
		return body.substring(start, body.indexOf("</a>", start) + 4);
	}

	/** 青（Bootstrap の primary 系）を使わず、アプリのターコイズ用のクラス。文字の左に、飾りのアイコン（aria-hidden）。絵文字は使わない */
	@ParameterizedTest(name = "auto={0}：自動予算ボタンはターコイズで、左にきらきらのアイコン")
	@ValueSource(strings = { "", "1" })
	void autoButtonIsTealWithASparkleIcon(String auto) throws Exception {
		designExample();

		String button = autoButtonHtml(winter(auto.isEmpty() ? null : auto));
		String tag = autoButtonTag(winter(auto.isEmpty() ? null : auto));

		assertThat(tag).contains("btn").contains("auto-budget-button");
		assertThat(tag).as("青は予実比較の意味の色なので使わない").doesNotContain("btn-outline-primary").doesNotContain("btn-primary").doesNotContain("btn-outline-secondary");
		int svg = button.indexOf("<svg");
		int text = button.indexOf("自動予算");
		assertThat(svg).as("アイコンがある").isPositive();
		assertThat(svg).as("文字の左").isLessThan(text);
		assertThat(button.substring(svg, button.indexOf("</svg>") + 6)).contains("aria-hidden=\"true\"").contains("M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z");
		assertThat(button).as("絵文字は使わない").doesNotContainPattern("[\\x{2600}-\\x{27BF}\\x{1F300}-\\x{1FAFF}\\x{FE0F}]");
		assertThat(plain(button)).as("読み上げは「自動予算」だけ").isEqualTo("自動予算");
	}

	/** 自作の CSS：ターコイズの枠 2px、濃いターコイズ（#0B6E6B）の文字を太字、少し大きく。保存（塗り）と切りかえ（細い灰色の枠）の間の強さ */
	@Test
	void styleSheetStylesTheAutoButton() throws Exception {
		String css = new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);
		Matcher rule = Pattern.compile("(?s)\\.auto-budget-button\\s*\\{([^}]*)\\}").matcher(css);

		assertThat(rule.find()).as(".auto-budget-button の規則がある").isTrue();
		String body = rule.group(1);
		assertThat(body).as("枠 2px のターコイズ（--hb-teal は border の宣言にだけ使う決まり）").containsPattern("(?m)^\\s*border:\\s*2px solid var\\(--hb-teal\\)");
		assertThat(body).containsPattern("--bs-btn-color:\\s*var\\(--hb-teal-text\\)");
		assertThat(body).containsPattern("--bs-btn-font-weight:\\s*700");
		assertThat(body).as("少し大きく").containsPattern("--bs-btn-padding-y:").containsPattern("--bs-btn-font-size:");
		assertThat(body).as("アイコンと文字を横に並べる").contains("inline-flex");
		assertThat(css).as("--hb-teal-text は #0B6E6B").containsPattern("--hb-teal-text:\\s*#0B6E6B");
		assertThat(css).as("青（primary の枠線ボタン）の上書きは、このボタンでは使わない").doesNotContain(".btn-outline-primary");
	}

	// ================================================================
	// そろえる（ブラウザで見て決めた。設計書 2.3）
	// ================================================================

	private static String styleSheet() throws Exception {
		return new String(new org.springframework.core.io.ClassPathResource("static/css/style.css").getContentAsByteArray(),
				java.nio.charset.StandardCharsets.UTF_8);
	}

	/** 規則（selector { … }）の中身を、セレクタに selectorPart を含むものから集める */
	private static List<String> rulesWith(String css, String selectorPart) {
		List<String> bodies = new ArrayList<>();
		Matcher m = Pattern.compile("(?s)([^{}]+)\\{([^}]*)\\}").matcher(css.replaceAll("(?s)/\\*.*?\\*/", ""));
		while (m.find()) {
			if (m.group(1).contains(selectorPart)) {
				bodies.add(m.group(2));
			}
		}
		return bodies;
	}

	/** 予算額の入力欄の右端を、見出し・合計の数字の右端にそろえる（入力欄を、セルの右に寄せる） */
	@Test
	void amountInputIsPushedToTheRightEdgeOfItsCell() throws Exception {
		List<String> rules = rulesWith(styleSheet(), "td.budget-amount input");

		assertThat(rules).as("入力欄の規則がある").isNotEmpty();
		assertThat(String.join(" ", rules)).containsPattern("margin-left:\\s*auto");
	}

	/** 表の見出しは、すべて同じ大きさ（本文と同じ）・同じ太さ（700）。列ごとに大きさを変える規則を持たない */
	@Test
	void everyTableHeaderHasTheSameSizeAndWeight() throws Exception {
		String css = styleSheet();

		String head = String.join(" ", rulesWith(css, ".budget-table thead th"));
		assertThat(head).containsPattern("font-size:\\s*var\\(--hb-font-size-body\\)").containsPattern("font-weight:\\s*700");
		for (String selector : List.of(".budget-ref", ".budget-amount", "th.num")) {
			assertThat(String.join(" ", rulesWith(css, selector))).as(selector + " は文字の大きさを変えない").doesNotContain("font-size");
		}
		assertThat(String.join(" ", rulesWith(css, ".budget-ref"))).as("参考の 3 列は小さくしない").doesNotContain("font-size");
	}

	/** 合計の行の数字は、どの列も同じ色・同じ大きさ・同じ太さ（budget-sum）。予算額の合計だけ違う、をなくす */
	@Test
	void totalRowNumbersShareOneStyle() throws Exception {
		designExample();

		String body = winter("1");
		Matcher foot = Pattern.compile("(?s)<tfoot>(.*?)</tfoot>").matcher(body);
		assertThat(foot.find()).isTrue();
		Matcher sums = Pattern.compile("<(span|strong)[^>]*budget-sum[^>]*>\\s*([0-9,]+)\\s*</").matcher(foot.group(1));
		List<String> numbers = new ArrayList<>();
		while (sums.find()) {
			numbers.add(sums.group(2));
		}
		assertThat(numbers).as("直前の予算・実績・予算額の合計の 3 つが、同じクラス").containsExactly("173,000", "80,400", "186,000");

		String sum = String.join(" ", rulesWith(styleSheet(), ".budget-sum"));
		assertThat(sum).containsPattern("color:\\s*var\\(--hb-text\\)").containsPattern("font-size:\\s*1\\.25rem").containsPattern("font-weight:\\s*700");
	}

	private static double luminance(String hex) {
		double[] c = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
			c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	private static double ratio(String a, String b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static String variable(String css, String name) {
		Matcher m = Pattern.compile("--" + Pattern.quote(name) + ":\\s*(#[0-9A-Fa-f]{6})").matcher(css);
		assertThat(m.find()).as("--" + name).isTrue();
		return m.group(1);
	}

	/** 参考の 3 列の色：補足の文字の色（#56625F）。背景（#F6F8F8）・白との明るさの比が 4.5 以上。本文より少しだけ落ちている */
	@Test
	void referenceColumnsColorIsDimmerThanTheBodyButStillReadable() throws Exception {
		String css = styleSheet();
		String sub = variable(css, "hb-text-sub");
		String text = variable(css, "hb-text");
		String background = variable(css, "hb-bg");
		String white = variable(css, "hb-input-bg");

		assertThat(String.join(" ", rulesWith(css, ".budget-ref"))).containsPattern("color:\\s*var\\(--hb-text-sub\\)");
		assertThat(sub.toUpperCase()).isEqualTo("#56625F");
		assertThat(ratio(sub, background)).as("背景との比").isGreaterThanOrEqualTo(4.5).isBetween(5.9, 6.0);
		assertThat(ratio(sub, white)).as("白との比").isGreaterThanOrEqualTo(4.5);
		assertThat(ratio(sub, background)).as("本文より落ちている").isLessThan(ratio(text, background));
	}

	// ================================================================
	// 合計と「消える前の確認」の JavaScript（budget-total.js）に渡す HTML の印（設計書 2.3、4.3、issue 019 の案 C）
	// ================================================================

	private static final String SCRIPT_TAG = "<script defer src=\"/js/budget-total.js\"></script>";
	private static final String CONFIRM_TEXT = "手で直した額は消えます。よろしいですか？";

	private static int countOf(String body, String needle) {
		return body.split(Pattern.quote(needle), -1).length - 1;
	}

	/** budget-total.js は、予算設定の画面（ふだんも、案を入れたあとも）で 1 回だけ読む */
	@ParameterizedTest(name = "auto={0} の画面で budget-total.js を読む")
	@ValueSource(strings = { "", "1", "2", "3" })
	void budgetScreenLoadsTheTotalScriptOnce(String auto) throws Exception {
		designExample();

		String body = winter(auto.isEmpty() ? null : auto);

		assertThat(countOf(body, "/js/budget-total.js")).isEqualTo(1);
		assertThat(body).contains(SCRIPT_TAG);
	}

	/** ほかの画面では読まない（この画面だけ） */
	@ParameterizedTest(name = "{0} では budget-total.js を読まない")
	@ValueSource(strings = { "/", "/transactions", "/transactions/new", "/comparison", "/categories" })
	void otherScreensDoNotLoadTheTotalScript(String path) throws Exception {
		designExample();

		String body = mockMvc.perform(get(path).with(login)).andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("budget-total.js");
	}

	/** 予算額の入力欄すべてに印 data-budget-amount（科目の数だけ）。保存のための name はそのまま */
	@ParameterizedTest(name = "auto={0} の予算額の欄に印がある")
	@ValueSource(strings = { "", "1" })
	void everyAmountInputIsMarked(String auto) throws Exception {
		designExample();

		String body = winter(auto.isEmpty() ? null : auto);

		Matcher inputs = Pattern.compile("<input[^>]*name=\"rows\\[\\d+\\]\\.amount\"[^>]*>").matcher(body);
		int n = 0;
		while (inputs.find()) {
			n++;
			assertThat(inputs.group()).contains("data-budget-amount");
		}
		assertThat(n).as("科目は 6 つ").isEqualTo(6);
		assertThat(countOf(body, "data-budget-amount")).isEqualTo(6);
	}

	/** 合計の数字の入れ物に data-budget-total。固定の 2 つの文にも、それぞれ印。2 つとも hidden のまま */
	@Test
	void totalAndTheTwoFixedSentencesAreMarked() throws Exception {
		designExample();

		String body = winter(null);

		assertThat(countOf(body, "data-budget-total")).isEqualTo(1);
		assertThat(body).containsPattern("(?s)<strong[^>]*\\bdata-budget-total\\b[^>]*>\\s*5,000\\s*</strong>");
		assertThat(body).containsPattern("(?s)<(?=[^>]*\\bhidden\\b)[^>]*\\bdata-budget-not-number\\b[^>]*>\\s*数字でない欄があります\\s*</");
		assertThat(body).containsPattern("(?s)<(?=[^>]*\\bhidden\\b)[^>]*\\bdata-budget-empty\\b[^>]*>\\s*空の欄があります\\s*</");
		assertThat(countOf(body, "data-budget-not-number")).isEqualTo(1);
		assertThat(countOf(body, "data-budget-empty")).isEqualTo(1);
	}

	/** 確認の印は「自動予算」ボタンだけ（ふだんの画面）。文は固定で、HTML に書いてある */
	@Test
	void onlyTheAutoButtonAsksForConfirmationOnThePlainScreen() throws Exception {
		designExample();

		String body = winter(null);

		assertThat(countOf(body, "data-budget-confirm")).isEqualTo(1);
		assertThat(autoButtonTag(body)).contains("data-budget-confirm=\"" + CONFIRM_TEXT + "\"");
	}

	/** 案を入れたあと：「自動予算」ボタンと切りかえの 1〜3 の、合わせて 4 つだけ。保存・前後の季節・年季節の選びには付けない */
	@ParameterizedTest(name = "auto={0} の画面の確認の印は 4 つ")
	@ValueSource(strings = { "1", "2", "3" })
	void autoButtonAndTheThreeChoicesAskForConfirmation(String auto) throws Exception {
		designExample();

		String body = winter(auto);

		assertThat(countOf(body, "data-budget-confirm")).isEqualTo(4);
		assertThat(autoButtonTag(body)).contains("data-budget-confirm=\"" + CONFIRM_TEXT + "\"");
		for (int n = 1; n <= 3; n++) {
			assertThat(choiceTag(body, n)).as("切りかえ " + n).contains("data-budget-confirm=\"" + CONFIRM_TEXT + "\"");
		}
		Matcher others = Pattern.compile("<(?:button|form|select|a)[^>]*data-budget-confirm[^>]*>").matcher(body);
		int onLinks = 0;
		while (others.find()) {
			assertThat(others.group()).as("確認が付くのは <a> のリンクだけ").startsWith("<a ");
			onLinks++;
		}
		assertThat(onLinks).isEqualTo(4);
	}

	/** 確認の文は messages.properties から出る固定の文で、JavaScript のファイルには書かない */
	@Test
	void confirmSentenceComesFromMessagesNotFromTheScript() throws Exception {
		String script = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "resources", "static", "js", "budget-total.js"));

		assertThat(script).doesNotContain("手で直した額は消えます");
	}

	/** 入力エラーで表示し直した画面でも、印と読み込みは同じ（合計は「—」で、JavaScript が開いたときに計算する） */
	@Test
	void markersAlsoExistOnTheRedisplayedScreenAfterAnInputError() throws Exception {
		Category food = category("食費");
		flush();

		String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/budgets")
				.param("year", "2026").param("season", "winter").param("rows[0].categoryId", String.valueOf(food.getId()))
				.param("rows[0].amount", "abc").with(login).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains(SCRIPT_TAG);
		assertThat(body).contains("data-budget-amount").contains("data-budget-total");
		assertThat(body).containsPattern("(?s)<strong[^>]*\\bdata-budget-total\\b[^>]*>\\s*—\\s*</strong>");
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
