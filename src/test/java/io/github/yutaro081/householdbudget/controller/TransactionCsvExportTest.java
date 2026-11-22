package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

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
import jakarta.persistence.EntityManager;

/**
 * 取引の CSV 出力（issue 004。要件定義書 4.2・6 章、設計書 2.2、2.3、3.9、5.18、7.10、7.12）。
 * <p>
 * 画面（HTTP）から確かめる：応答のヘッダー、ファイルの形（BOM・見出しの行・CRLF・囲み・数式の印・分割の番号）、並び、他人のデータ、
 * 取引一覧の「CSV で出力」のリンク。
 * 404 と未ログインは、一覧と同じ確かめ（filterOf）なので、TransactionFilterTest が一覧と CSV の両方の URL で確かめる（Q004-6）。
 * 値 1 つの書き方の細かい所（数式になりうる 12 の文字のそれぞれなど）は TransactionCsvWriterTest（単体テスト）が確かめる。
 * ここでは、ファイルにしたとき、どの列にかかり、どの列にかからないかを確かめる。
 * <p>
 * 「今日」は 2026-11-11（FixedClock20261111Config。ファイル名の日付）。印（receipt_group）は SQL で付ける（TransactionFilterTest と同じ）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock20261111Config.class)
@Transactional
class TransactionCsvExportTest {

	private static final String EXPORT = "/transactions/export";
	private static final LocalDate DAY = LocalDate.of(2026, 11, 9);
	private static final String HEADER = "日付,科目,取引先,支払方法,摘要,金額,分割";
	private static final String CRLF = "\r\n";
	private static final String LINK_TITLE = "表示している取引を、CSV ファイルに保存する";

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
	private EntityManager entityManager;

	private record Owner(Long userId, String username, Category food, Category daily, Vendor vendor, PaymentMethod payment) {
	}

	private Owner a;
	private Owner b;
	private RequestPostProcessor asA;

	@BeforeEach
	void createUsers() {
		a = createOwner("csvxa");
		b = createOwner("csvxb");
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
		return saveRow(o.userId(), date, category, amount, description, o.vendor(), o.payment());
	}

	private Long saveRow(Long userId, LocalDate date, Category category, int amount, String description, Vendor vendor, PaymentMethod payment) {
		Long id = transactionRepository.save(new Transaction(userId, date, amount, category, description, vendor, payment)).getId();
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

	private static MockHttpServletRequestBuilder at(String path, String... params) {
		MockHttpServletRequestBuilder builder = get(path);
		for (int i = 0; i < params.length; i += 2) {
			builder.param(params[i], params[i + 1]);
		}
		return builder;
	}

	/** CSV の応答（200 を確かめる） */
	private MockHttpServletResponse export(String... params) throws Exception {
		return mockMvc.perform(at(EXPORT, params).with(asA)).andExpect(status().isOk()).andReturn().getResponse();
	}

	/** CSV のバイトの並び（BOM を含む） */
	private byte[] bytes(String... params) throws Exception {
		return export(params).getContentAsByteArray();
	}

	/** CSV の中身（UTF-8 で読み、先頭の BOM〔U+FEFF〕は外す。BOM があるかは bytes で確かめる） */
	private String csv(String... params) throws Exception {
		String text = new String(bytes(params), StandardCharsets.UTF_8);
		return text.startsWith("﻿") ? text.substring(1) : text;
	}

	/** CSV の行（CRLF で分ける。どの行も CRLF で終わることを確かめてから、最後の空を除く） */
	private static List<String> lines(String csv) {
		assertThat(csv).as("どの行も CRLF で終わる（最後の行も）").endsWith(CRLF);
		List<String> lines = new ArrayList<>(Arrays.asList(csv.split(CRLF, -1)));
		lines.remove(lines.size() - 1);
		return lines;
	}

	/** 取引の行（見出しの行を除く） */
	private static List<String> rows(String csv) {
		List<String> lines = lines(csv);
		assertThat(lines.get(0)).isEqualTo(HEADER);
		return lines.subList(1, lines.size());
	}

	/** 取引の行の、その列（0 から数える）の値。カンマを含まない値だけのときに使う。囲みの「"」は外す */
	private static List<String> column(String csv, int index) {
		return rows(csv).stream().map(row -> row.split(",", -1)[index].replace("\"", "")).toList();
	}

	/** 取引一覧の画面の、取引の行（7 つのセル）の摘要（5 つ目のセル）を、上から順に */
	private List<String> descriptionsInTheList(String... params) throws Exception {
		String body = mockMvc.perform(at("/transactions", params).with(asA)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		List<String> descriptions = new ArrayList<>();
		Matcher rows = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(body);
		while (rows.find()) {
			Matcher cells = Pattern.compile("(?s)<td[^>]*>(.*?)</td>").matcher(rows.group(1));
			List<String> texts = new ArrayList<>();
			while (cells.find()) {
				texts.add(cells.group(1).replaceAll("(?s)<[^>]*>", "").strip());
			}
			if (texts.size() == 7) {
				descriptions.add(texts.get(4));
			}
		}
		return descriptions;
	}

	/** 取引一覧の画面の、本体（main） */
	private String listMain(String query) throws Exception {
		String body = mockMvc.perform(get("/transactions" + query).with(asA)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		return body.substring(body.indexOf("<main"), body.indexOf("</main>"));
	}

	private static String id(Category category) {
		return String.valueOf(category.getId());
	}

	// ---------------------------------------------------------------
	// 応答のヘッダー（設計書 5.18、7.12 の S11）
	// ---------------------------------------------------------------

	/** text/csv（UTF-8）の添付ファイルで、名前は transactions-（今日の日付）.csv。中身の種類を推測させない（nosniff） */
	@Test
	void theResponseIsAUtf8CsvAttachmentNamedWithTheDateOfToday() throws Exception {
		saveRow(a, DAY, a.food(), 100, "昼食");

		MockHttpServletResponse response = export();

		assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
		assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"transactions-20261111.csv\"");
		assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
	}

	/** 絞り込んでも、ファイル名は同じ（科目名などの利用者の入力を入れない。記号の多い科目名で絞っても変わらない） */
	@Test
	void theFileNameNeverContainsUserInput() throws Exception {
		Category odd = categoryRepository.save(new Category(a.userId(), "x\";y.csv"));
		saveRow(a, DAY, odd, 100, "記号の科目");

		for (String[] params : List.of(new String[] { "category", id(odd) },
				new String[] { "category", id(odd), "year", "2026", "season", "autumn" },
				new String[] { "year", "2026", "season", "autumn" })) {
			assertThat(export(params).getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"transactions-20261111.csv\"");
		}
	}

	// ---------------------------------------------------------------
	// ファイルの形（設計書 5.18）
	// ---------------------------------------------------------------

	/** 先頭は UTF-8 の BOM（EF BB BF）。そのすぐあとが見出しの行（取引が 0 件でも） */
	@Test
	void theFileStartsWithTheUtf8BomAndThenTheHeaderLine() throws Exception {
		byte[] empty = bytes();
		assertThat(Arrays.copyOf(empty, 3)).containsExactly(0xEF, 0xBB, 0xBF);
		assertThat(new String(empty, 3, empty.length - 3, StandardCharsets.UTF_8)).isEqualTo(HEADER + CRLF);

		saveRow(a, DAY, a.food(), 100, "昼食");
		byte[] one = bytes();
		assertThat(Arrays.copyOf(one, 3)).containsExactly(0xEF, 0xBB, 0xBF);
		assertThat(new String(one, 3, one.length - 3, StandardCharsets.UTF_8)).startsWith(HEADER + CRLF);
	}

	/** どの行も CRLF で終わる（最後の行も。Q004-4）。CR だけ・LF だけの改行はない */
	@Test
	void everyLineEndsWithCrlfIncludingTheLast() throws Exception {
		saveRow(a, DAY, a.food(), 100, "昼食");
		saveRow(a, DAY, a.daily(), 200, "洗剤");

		String csv = csv();

		assertThat(lines(csv)).hasSize(3);
		assertThat(csv.replace(CRLF, "")).doesNotContain("\r", "\n");
	}

	/** 取引の行：日付（yyyy-MM-dd。0 で埋める）、文字の 4 列は " で囲む、金額はカンマなしの整数、分割でない行の分割の列は空 */
	@Test
	void aRowHasTheDateTheQuotedNamesTheDescriptionTheAmountAndAnEmptySplit() throws Exception {
		saveRow(a, DAY, a.food(), 3000, "昼食");
		saveRow(a, LocalDate.of(2026, 1, 5), a.daily(), 1_000_000, "冷蔵庫");

		assertThat(rows(csv())).containsExactly(
				"2026-11-09,\"食費\",\"スーパー\",\"現金\",\"昼食\",3000,",
				"2026-01-05,\"日用品\",\"スーパー\",\"現金\",\"冷蔵庫\",1000000,");
	}

	/** 摘要がない取引は、空の文字を囲んだ値（""） */
	@Test
	void anEmptyDescriptionIsAnEmptyQuotedValue() throws Exception {
		saveRow(a, DAY, a.food(), 500, null);

		assertThat(rows(csv())).containsExactly("2026-11-09,\"食費\",\"スーパー\",\"現金\",\"\",500,");
	}

	/** 「"」は「""」に重ねる。カンマと途中の改行は、囲みの中にそのまま入り、1 つの値のまま（RFC 4180） */
	@Test
	void quotesCommasAndLineBreaksStayInsideOneValue() throws Exception {
		saveRow(a, DAY, a.food(), 100, "a\"b,c\r\nd");

		assertThat(csv()).isEqualTo(HEADER + CRLF + "2026-11-09,\"食費\",\"スーパー\",\"現金\",\"a\"\"b,c\r\nd\",100," + CRLF);
	}

	/**
	 * 数式の印（' を 1 つ）は、文字の 4 列（科目・取引先・支払方法・摘要）にかかる。半角の = + - @、全角の ＝ ＋ － ＠、' のどれで始まっても。
	 * 日付・金額・分割の番号・見出しの行にはかからない（設計書 5.18、7.2）。
	 * 全角と半角は同じ名前とみなされる（設計書 3.12）ので、全角の行は、別の名前にする
	 */
	@Test
	void theFormulaMarkIsOnlyOnTheFourTextColumns() throws Exception {
		Long me = a.userId();
		saveRow(me, DAY, categoryRepository.save(new Category(me, "=食費")), 100, "@SUM(1,1)",
				vendorRepository.save(new Vendor(me, "+店")), paymentMethodRepository.save(new PaymentMethod(me, "-現金")));
		saveRow(me, DAY.minusDays(1), categoryRepository.save(new Category(me, "＝日用")), 200, "＠合計",
				vendorRepository.save(new Vendor(me, "＋屋")), paymentMethodRepository.save(new PaymentMethod(me, "－カード")));
		saveRow(a, DAY.minusDays(2), a.food(), 300, "'abc");

		String csv = csv();

		assertThat(lines(csv).get(0)).isEqualTo(HEADER);
		assertThat(rows(csv)).containsExactly(
				"2026-11-09,\"'=食費\",\"'+店\",\"'-現金\",\"'@SUM(1,1)\",100,",
				"2026-11-08,\"'＝日用\",\"'＋屋\",\"'－カード\",\"'＠合計\",200,",
				"2026-11-07,\"食費\",\"スーパー\",\"現金\",\"''abc\",300,");
	}

	/** 取引が 0 件なら、見出しの行だけ（絞り込んで 0 件のときも） */
	@Test
	void noTransactionsGiveOnlyTheHeaderLine() throws Exception {
		assertThat(csv()).isEqualTo(HEADER + CRLF);

		saveRow(a, DAY, a.food(), 100, "昼食");
		assertThat(csv("category", id(a.daily()), "year", "2026", "season", "autumn")).isEqualTo(HEADER + CRLF);
	}

	// ---------------------------------------------------------------
	// 並び：一覧に出ている行と同じ行・同じ並び（設計書 5.18）
	// ---------------------------------------------------------------

	/** 絞らない・科目と季節・科目だけ・季節だけのどれでも、CSV の行は、一覧の画面に出ている行と同じで、並びも同じ */
	@Test
	void theRowsAreTheRowsOfTheListInTheSameOrder() throws Exception {
		saveRow(a, DAY.minusDays(1), a.food(), 10, "前日");
		saveRow(a, DAY, a.food(), 20, "単独1");
		saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily(), a.food() }, 30, 40, 50);
		saveRow(a, DAY, a.daily(), 60, "単独2");
		saveRow(a, DAY, a.food(), 70, "単独3");
		saveRow(a, DAY.plusDays(1), a.food(), 80, "翌日");
		saveRow(a, LocalDate.of(2026, 12, 1), a.food(), 90, "冬");

		for (String[] params : List.of(new String[0],
				new String[] { "category", id(a.food()), "year", "2026", "season", "autumn" },
				new String[] { "category", id(a.food()) },
				new String[] { "year", "2026", "season", "autumn" })) {
			List<String> list = descriptionsInTheList(params);
			assertThat(list).as("一覧に行がある").isNotEmpty();
			assertThat(column(csv(params), 4)).as(String.join(",", params)).isEqualTo(list);
		}
		assertThat(column(csv(), 4)).containsExactly("冬", "翌日", "単独3", "単独2", "R-1", "R-2", "R-3", "単独1", "前日");
	}

	// ---------------------------------------------------------------
	// 分割の列：ファイルの中だけの番号（設計書 3.9、5.18、7.12 の S12）
	// ---------------------------------------------------------------

	/** 同じレシートの行に、出てきた順に 1・2… を振る。分割でない行は空。DB の印の値は出さない */
	@Test
	void theSplitColumnNumbersReceiptsInTheOrderTheyAppear() throws Exception {
		List<Long> older = saveReceipt(a, DAY, "X", new Category[] { a.food(), a.daily() }, 100, 200);
		saveRow(a, DAY, a.food(), 300, "単独");
		List<Long> newer = saveReceipt(a, DAY, "Y", new Category[] { a.food(), a.daily(), a.food() }, 400, 500, 600);

		String csv = csv();

		assertThat(column(csv, 4)).containsExactly("Y-1", "Y-2", "Y-3", "単独", "X-1", "X-2");
		assertThat(column(csv, 6)).containsExactly("1", "1", "1", "", "2", "2");
		assertThat(column(csv, 6)).as("DB の印（最初の行の id）は出さない")
				.doesNotContain(String.valueOf(older.get(0)), String.valueOf(newer.get(0)));
	}

	/** 科目で絞って、レシートの一部の行だけが出るときも、番号を付ける（画面の「分割」の印と同じ） */
	@Test
	void aPartlyShownReceiptKeepsItsNumberWhenFilteredByCategory() throws Exception {
		saveReceipt(a, DAY, "R", new Category[] { a.food(), a.daily() }, 2200, 800);
		saveRow(a, DAY, a.food(), 50, "単独");

		String csv = csv("category", id(a.food()));

		assertThat(column(csv, 4)).containsExactly("単独", "R-1");
		assertThat(column(csv, 6)).containsExactly("", "1");
	}

	// ---------------------------------------------------------------
	// 他人のデータ（設計書 3.9、7.8）
	// ---------------------------------------------------------------

	/**
	 * 他人の行は、CSV に入らない。他人の行に、自分の科目の ID や、同じ期間の日付が入っていても（アプリでは作れない。SQL で作る）。
	 * 絞らない・科目だけ・季節だけ・両方のどれでも
	 */
	@Test
	void otherUsersRowsAreNeverInTheFile() throws Exception {
		saveRow(a, DAY, a.food(), 100, "自分の食費");
		Long theirs = saveRow(b, DAY, b.food(), 999, "他人の行");
		setCategory(theirs, a.food().getId());
		saveRow(b, DAY, b.daily(), 888, "他人の日用品");

		for (String[] params : List.of(new String[0],
				new String[] { "category", id(a.food()) },
				new String[] { "year", "2026", "season", "autumn" },
				new String[] { "category", id(a.food()), "year", "2026", "season", "autumn" })) {
			String csv = csv(params);
			assertThat(column(csv, 4)).as(String.join(",", params)).containsExactly("自分の食費");
			assertThat(csv).doesNotContain("他人の", "999", "888");
		}
	}

	// ---------------------------------------------------------------
	// 取引一覧の「CSV で出力」（設計書 2.3。Q004-2、Q004-3）
	// ---------------------------------------------------------------

	/** 取引が 1 件以上なら、見出しの行の右端（見出しのあと、表の前）に、小さい枠のボタンの見た目で「CSV で出力」。アイコンはない */
	@Test
	void theListHasTheExportButtonInTheHeadingRow() throws Exception {
		saveRow(a, DAY, a.food(), 100, "昼食");

		String main = listMain("");

		String link = "<a class=\"row-btn\" title=\"" + LINK_TITLE + "\" href=\"/transactions/export\">CSV で出力</a>";
		assertThat(main).contains(link);
		assertThat(main.indexOf("class=\"page-head\"")).isLessThan(main.indexOf("<h1>"));
		assertThat(main.indexOf("<h1>")).isLessThan(main.indexOf(link));
		assertThat(main.indexOf(link)).isLessThan(main.indexOf("<table"));
	}

	/**
	 * 絞り込んだ一覧のリンクには、同じ条件を付ける。付けるのは、確かめたあとの値のある条件だけ（空の category、知らない引数は付けない。
	 * 値のない year などを付けると 404 になるため。Q004-2）。{food} は自分の食費の科目の ID
	 */
	@ParameterizedTest(name = "/transactions?{0} のリンクは /transactions/export{1}")
	@CsvSource(delimiter = '|', value = {
			"category={food}|?category={food}",
			"year=2026&season=autumn|?year=2026&amp;season=autumn",
			"category={food}&year=2026&season=autumn|?category={food}&amp;year=2026&amp;season=autumn",
			"category=&year=2026&season=autumn|?year=2026&amp;season=autumn",
			"category=|''",
			"category={food}&foo=bar|?category={food}",
			"year=2026&season=winter&category={food}|?category={food}&amp;year=2026&amp;season=winter" })
	void theLinkCarriesTheSameFilterWithOnlyTheGivenValues(String query, String expected) throws Exception {
		saveRow(a, DAY, a.food(), 100, "秋の食費");
		saveRow(a, LocalDate.of(2026, 12, 1), a.food(), 200, "冬の食費");
		String food = id(a.food());

		String main = listMain("?" + query.replace("{food}", food));

		assertThat(main).contains("href=\"/transactions/export" + expected.replace("{food}", food) + "\">CSV で出力</a>");
		assertThat(main).doesNotContain("export?year&", "export?category&", "=&amp;", "foo=");
	}

	/** 取引が 0 件のとき（取引がまだない・絞り込んで 0 件）は、「CSV で出力」を出さない */
	@Test
	void thereIsNoExportLinkWhenThereAreNoTransactions() throws Exception {
		assertThat(listMain("")).contains("取引はまだありません").doesNotContain("/transactions/export", "CSV で出力");

		saveRow(a, DAY, a.food(), 100, "昼食");
		assertThat(listMain("?category=" + id(a.daily())))
				.contains("この条件に当てはまる取引はありません。").doesNotContain("/transactions/export", "CSV で出力");
	}
}
