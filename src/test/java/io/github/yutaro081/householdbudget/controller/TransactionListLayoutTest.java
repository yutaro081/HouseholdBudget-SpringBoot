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

		assertThat(main.indexOf("class=\"list-card")).isPositive().isLessThan(main.indexOf("<table"));
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
		String row = body.substring(body.indexOf("<tbody>"), body.indexOf("</tbody>"));

		assertThat(row).contains("<td class=\"list-muted\">2026/10/19</td>", "<td class=\"list-muted\">現金</td>", "<td class=\"list-muted\">昼食</td>");
		assertThat(row).as("セルに「円」を付けない（issue 024）").contains("<td class=\"num\">30,000</td>").doesNotContain("円");
		assertThat(row).containsPattern("<td><a href=\"/transactions/\\d+\">詳細</a></td>");
	}

	@Test
	void namesAndDescriptionsAreEscaped() throws Exception {
		Category c = categoryRepository.save(new Category(userId, "<b>x</b>"));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 19), 100, c, "<script>y</script>", vendor, payment));
		String main = list();

		assertThat(main).doesNotContain("<b>x</b>", "<script>y</script>");
		assertThat(main).contains("&lt;b&gt;x&lt;/b&gt;", "&lt;script&gt;y&lt;/script&gt;");
	}

}
