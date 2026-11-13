package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
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
 * 取引一覧の見出しの行・件数・カードの中の表（設計書 2.3、issue 017 の 2 回目。Q017-13、Q017-14）。
 * 今まで、取引一覧の &lt;table&gt;・見出しの列・件数を直接確かめるテストはなかったので、ここで足す。
 * 並び順・日付と金額の形・「詳細」のリンクなどの動きは TransactionControllerTest が確かめている。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionListLayoutTest {

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

	private Long userId;
	private Long otherId;
	private Category category;
	private Vendor vendor;
	private PaymentMethod payment;
	private RequestPostProcessor asUser;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("listlay1", "dummy-hash-for-test")).getId();
		otherId = userRepository.save(new User("listlay2", "dummy-hash-for-test")).getId();
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		asUser = user(new LoginUser(userId, "listlay1", "x"));
	}

	private void save(int amount, String description) {
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 19), amount, category, description, vendor, payment));
	}

	private String list() throws Exception {
		entityManager.flush();
		entityManager.clear();
		String body = mockMvc.perform(get("/transactions").with(asUser)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return body.substring(body.indexOf("<main"), body.indexOf("</main>"));
	}

	// ---- 見出しの行と件数 ----

	@Test
	void theHeadingRowHasTheTitleTheCountAndTheRegisterButtonWithAPlus() throws Exception {
		save(100, "a");
		save(200, "b");
		String main = list();

		assertThat(main).startsWith("<main class=\"container list-wide\">");
		assertThat(main).contains("<h1>取引一覧</h1>", "日付の新しい順に、2 件を表示しています。");
		assertThat(main).containsPattern(Pattern.compile(
				"<a [^>]*href=\"/transactions/new\"[^>]*><svg[^>]*stroke-width=\"2.4\"[^>]*>.*?</svg>取引を登録</a>", Pattern.DOTALL));
		assertThat(main).contains("class=\"page-head\"", "class=\"page-sub\"");
		assertThat(main.indexOf("<h1>")).isLessThan(main.indexOf("日付の新しい順に"));
		assertThat(main.indexOf("日付の新しい順に")).isLessThan(main.indexOf("取引を登録</a>"));
		assertThat(main.indexOf("取引を登録</a>")).as("ボタンは表より前").isLessThan(main.indexOf("<table"));
	}

	@Test
	void theCountIsOneForOneTransactionWithoutSpecialTreatment() throws Exception {
		save(100, "a");

		assertThat(list()).contains("日付の新しい順に、1 件を表示しています。");
	}

	@Test
	void theCountHasCommasAndDoesNotCountOtherUsers() throws Exception {
		for (int i = 0; i < 1000; i++) {
			save(100, null);
		}
		Category c = categoryRepository.save(new Category(otherId, "他人の科目"));
		Vendor v = vendorRepository.save(new Vendor(otherId, "他人の取引先"));
		PaymentMethod p = paymentMethodRepository.save(new PaymentMethod(otherId, "他人の支払"));
		for (int i = 0; i < 3; i++) {
			transactionRepository.save(new Transaction(otherId, LocalDate.of(2026, 10, 19), 1, c, null, v, p));
		}
		String main = list();

		assertThat(main).contains("日付の新しい順に、1,000 件を表示しています。");
		assertThat(main).as("他人の 3 件を足した数は出ない（詳細のリンクの ID に 1003 が混ざることがあるので、「件」まで含めて調べる）").doesNotContain("1,003 件", "1003 件");
	}

	@Test
	void withNoTransactionsThereIsNoCountSentenceNoTableNoCardButTheButton() throws Exception {
		String main = list();

		assertThat(main).contains("取引はまだありません", "href=\"/transactions/new\"");
		assertThat(main).doesNotContain("を表示しています", "<table", "list-card", "0 件");
	}

	// ---- カードの中の表 ----

	@Test
	void theTableIsInsideTheCardAndHasTheSevenColumnsInOrder() throws Exception {
		save(100, "昼食");
		String main = list();

		assertThat(main.indexOf("class=\"table-card list-card")).isPositive().isLessThan(main.indexOf("<table"));
		String head = main.substring(main.indexOf("<thead>"), main.indexOf("</thead>"));
		List<String> labels = new ArrayList<>();
		Matcher m = Pattern.compile("<th[^>]*>(.*?)</th>", Pattern.DOTALL).matcher(head);
		while (m.find()) {
			labels.add(m.group(1).replaceAll("<[^>]*>", "").strip());
		}
		assertThat(labels).containsExactly("日付", "科目", "取引先", "支払方法", "摘要", "金額（円）", "操作");
		assertThat(head).as("金額の見出しは右寄せの印。「円」は見出しにまとめる（issue 024）").contains("<th class=\"num\">金額（円）</th>");
	}

	@Test
	void dateMethodAndDescriptionAreMutedAndTheAmountKeepsTheNumClass() throws Exception {
		save(30000, "昼食");
		String body = list();
		String row = body.substring(body.indexOf("<td class=\"list-muted\">"), body.indexOf("</tbody>"));   // 月の見出しの行（issue 025）のあとの、取引の行

		assertThat(row).contains("<td class=\"list-muted\">2026/10/19</td>", "<td class=\"list-muted\">現金</td>", "<td class=\"list-muted\">昼食</td>");
		String cells = row.substring(0, row.indexOf("<div class=\"row-actions\">"));   // 操作のセル（削除の確認の文に「円」が入る。issue 025）の手前まで
		assertThat(cells).as("セルに「円」を付けない（issue 024）").contains("<td class=\"num\">30,000</td>").doesNotContain("円");
		assertThat(row).as("操作は、編集・複写・削除（issue 025）").containsPattern("(?s)<a class=\"row-btn\" title=\"[^\"]*\" href=\"/transactions/\\d+/edit\">(<svg.*?</svg>)?編集</a>").doesNotContain("詳細");
	}

	@Test
	void namesAndDescriptionsAreEscaped() throws Exception {
		Category c = categoryRepository.save(new Category(userId, "<b>x</b>"));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 19), 100, c, "<script>y</script>", vendor, payment));
		String main = list();

		assertThat(main).doesNotContain("<b>x</b>", "<script>y</script>");
		assertThat(main).contains("&lt;b&gt;x&lt;/b&gt;", "&lt;script&gt;y&lt;/script&gt;");
	}

	// ---- 月ごとの見出しの行（issue 025 の段階 2。設計書 2.3）----

	private void saveOn(Long owner, LocalDate date, int amount) {
		Category c = owner.equals(userId) ? category : categoryRepository.save(new Category(owner, "他人の科目" + amount));
		Vendor v = owner.equals(userId) ? vendor : vendorRepository.save(new Vendor(owner, "他人の取引先" + amount));
		PaymentMethod p = owner.equals(userId) ? payment : paymentMethodRepository.save(new PaymentMethod(owner, "他人の支払" + amount));
		transactionRepository.save(new Transaction(owner, date, amount, c, null, v, p));
	}

	/** 列の見出し（thead）はいちばん上の 1 つだけ。tbody は月の数だけ。見出しの行は、各 tbody の最初の行 */
	@Test
	void thereIsOneTheadAndOneTbodyPerMonthAndEachTbodyStartsWithTheMonthRow() throws Exception {
		saveOn(userId, LocalDate.of(2026, 11, 9), 100);
		saveOn(userId, LocalDate.of(2026, 11, 2), 200);
		saveOn(userId, LocalDate.of(2026, 10, 31), 300);
		String main = list();

		assertThat(count(main, "<thead>")).isEqualTo(1);
		assertThat(count(main, "<tbody>")).isEqualTo(2);
		assertThat(count(main, "class=\"month-row\"")).isEqualTo(2);
		assertThat(main).containsPattern("(?s)<tbody>\\s*<tr class=\"month-row\">");
		assertThat(count(main, "<td colspan=\"7\"")).as("見出しの行は 7 列ぶんの 1 つのマス").isEqualTo(2);
	}

	/** 見出しの行：左に年月（太字）、右に「○ 件・合計 ○円」（カンマ付き） */
	@Test
	void theMonthRowShowsTheMonthInBoldAndTheCountAndTheTotalOnTheRight() throws Exception {
		saveOn(userId, LocalDate.of(2026, 11, 9), 10_000);
		saveOn(userId, LocalDate.of(2026, 11, 2), 19_800);
		saveOn(userId, LocalDate.of(2026, 10, 31), 300);
		String main = list();

		assertThat(main).containsPattern("(?s)<strong class=\"month-name\">2026年 11月</strong>\\s*<span class=\"month-sum\">2 件・合計 <span class=\"month-amount\">29,800円</span></span>");
		assertThat(main).containsPattern("(?s)<strong class=\"month-name\">2026年 10月</strong>\\s*<span class=\"month-sum\">1 件・合計 <span class=\"month-amount\">300円</span></span>");
		assertThat(main.indexOf("2026年 11月")).as("新しい月が上").isLessThan(main.indexOf("2026年 10月"));
	}

	/** 年またぎ：「2026年 1月」が上、「2025年 12月」が下。月の数字は 0 埋めしない */
	@Test
	void theYearTurnsBetweenDecemberAndJanuary() throws Exception {
		saveOn(userId, LocalDate.of(2025, 12, 31), 600);
		saveOn(userId, LocalDate.of(2026, 1, 1), 500);
		String main = list();

		assertThat(main.indexOf("2026年 1月")).isPositive().isLessThan(main.indexOf("2025年 12月"));
		assertThat(main).contains("1 件・合計 <span class=\"month-amount\">500円</span>", "1 件・合計 <span class=\"month-amount\">600円</span>").doesNotContain("01月");
	}

	/** 月の件数と合計に、他人の取引が混ざらない（同じ月に他人の取引が多くあっても） */
	@Test
	void theMonthRowDoesNotIncludeOtherUsersTransactions() throws Exception {
		saveOn(userId, LocalDate.of(2026, 11, 9), 1_500);
		saveOn(otherId, LocalDate.of(2026, 11, 9), 70_000);
		saveOn(otherId, LocalDate.of(2026, 11, 10), 80_000);
		String main = list();

		assertThat(main).contains("1 件・合計 <span class=\"month-amount\">1,500円</span>");
		assertThat(main).doesNotContain("150,000", "151,500", "3 件・合計", "2 件・合計");
		assertThat(main).doesNotContain("他人の科目");
	}

	/** 日付の新しい順、同じ日は登録の新しい順は、月の見出しを入れても変わらない（取引の行の並び） */
	@Test
	void theTransactionRowsKeepTheirOrderAcrossMonths() throws Exception {
		saveOn(userId, LocalDate.of(2026, 10, 31), 1);
		saveOn(userId, LocalDate.of(2026, 11, 1), 2);
		saveOn(userId, LocalDate.of(2026, 11, 1), 3);
		String main = list();
		List<String> dates = new ArrayList<>();
		Matcher m = Pattern.compile("<td class=\"list-muted\">(\\d{4}/\\d{2}/\\d{2})</td>").matcher(main);
		while (m.find()) {
			dates.add(m.group(1));
		}

		assertThat(dates).containsExactly("2026/11/01", "2026/11/01", "2026/10/31");
		assertThat(main).contains("日付の新しい順に、3 件を表示しています。");
		assertThat(main.indexOf(">3</td>")).as("同じ日は登録の新しい順（金額 3 が 2 より上）").isLessThan(main.indexOf(">2</td>"));
	}

	/** 取引が 1 件もないとき：今のまま（見出しの行も表もない） */
	@Test
	void withNoTransactionsThereIsNoMonthRow() throws Exception {
		String main = list();

		assertThat(main).contains("取引はまだありません").doesNotContain("month-row", "<table", "<thead>");
	}

	/** テンプレートでは足し算しない（件数と合計は Service が計算して渡す） */
	@Test
	void theTemplateDoesNotCalculateTheTotals() throws Exception {
		String html = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "resources", "templates", "transactions", "list.html"));

		assertThat(html).contains("month.count", "month.total");
		assertThat(html).doesNotContain("#aggregates", ".sum(", "#lists.size(month", "+ transaction.amount");
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/**
	 * 摘要がない取引は、一覧で、摘要のセルが空欄になり、「null」と出ない（詳細ページにあった確認を、一覧に移した。issue 025 の段階 7）。
	 * 摘要のセルは、取引の行（&lt;td&gt; が 7 つ）の 5 番目。クラス名には頼らない。摘要がある行と並べて、空欄の行だけが空になることも確かめる。
	 */
	@Test
	void aTransactionWithoutDescriptionShowsAnEmptyDescriptionCellAndNeverNull() throws Exception {
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 20), 100, category, null, vendor, payment));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 19), 200, category, "昼食", vendor, payment));
		String main = list();

		Matcher rows = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(main);
		List<String> descriptions = new ArrayList<>();
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

		assertThat(descriptions).as("新しい日付（摘要なし）が先、その次が「昼食」").containsExactly("", "昼食");
		assertThat(main).as("「null」という文字は、どこにも出ない").doesNotContain("null", "NULL");
	}
}
