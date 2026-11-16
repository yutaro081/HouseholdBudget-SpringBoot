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
 * ヘッダーのメニューの「設定」（設計書 2.3「ヘッダー」、issue 029 の段階 4）。
 * ・メニューは 5 つ：トップ、取引一覧、予算、予実比較、設定（「設定」はいちばん右）
 * ・「設定」は &lt;details&gt;（JavaScript なし）。中に「科目」「取引先」「支払方法」の 3 つのリンク
 * ・「予算」は予算設定の画面へ。画面の見出し・タブの名前は「予算設定」のまま
 * ・科目・取引先・支払方法の画面（一覧と変更）では、「設定」に aria-current="true"、中のその画面の項目に aria-current="page"。ほかの画面では「設定」に印がない
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeaderSettingsMenuTest {

	private static final Pattern NAV = Pattern.compile("<nav class=\"menu[^>]*>(.*?)</nav>", Pattern.DOTALL);
	private static final Pattern GROUP = Pattern.compile("<details class=\"menu-group\">(.*?)</details>", Pattern.DOTALL);
	private static final Pattern SUMMARY = Pattern.compile("<summary[^>]*>.*?</summary>", Pattern.DOTALL);
	private static final Pattern SVG = Pattern.compile("<svg.*?</svg>", Pattern.DOTALL);

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

	private RequestPostProcessor asUser;
	private Category category;
	private Vendor vendor;
	private PaymentMethod payment;
	private Transaction transaction;

	@BeforeEach
	void setUp() {
		Long userId = userRepository.save(new User("hdrset1", "dummy-hash-for-test")).getId();
		asUser = user(new LoginUser(userId, "hdrset1", "x"));
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transaction = transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, null, vendor, payment));
		entityManager.flush();
		entityManager.clear();
	}

	private String body(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String nav(String body) {
		Matcher m = NAV.matcher(body);
		assertThat(m.find()).as("メニュー（nav）がある").isTrue();
		return m.group(1);
	}

	/** 「設定」の &lt;details&gt; の中身。ちょうど 1 つだけ */
	private static String group(String nav) {
		List<String> groups = GROUP.matcher(nav).results().map(r -> r.group(1)).toList();
		assertThat(groups).as("「設定」（details class=\"menu-group\"）は 1 つだけ").hasSize(1);
		return groups.get(0);
	}

	private static String summary(String group) {
		Matcher m = SUMMARY.matcher(group);
		assertThat(m.find()).as("「設定」の summary がある").isTrue();
		return m.group();
	}

	/** アイコンを除いた、文字だけ */
	private static String text(String html) {
		return SVG.matcher(html).replaceAll("").replaceAll("<[^>]*>", "").strip();
	}

	// ---- 5 つの項目と、「設定」の中の 3 つ ----

	@Test
	void theMenuHasFiveItemsAndTheSettingsHasThreeLinks() throws Exception {
		String nav = nav(body("/transactions"));

		// メニューの直下の項目：details の中を「設定」の 1 つとして数える
		String top = GROUP.matcher(nav).replaceAll("<a href=\"#\">設定</a>");
		List<String> items = new ArrayList<>();
		Matcher a = Pattern.compile("(?s)<a [^>]*>(.*?)</a>").matcher(top);
		while (a.find()) {
			items.add(text(a.group(1)));
		}
		assertThat(items).as("メニューは 5 つ。「設定」はいちばん右").containsExactly("トップ", "取引一覧", "予算", "予実比較", "設定");

		String group = group(nav);
		assertThat(text(summary(group))).as("まとめた項目の名前").isEqualTo("設定");
		assertThat(summary(group)).as("「設定」にもアイコン").containsPattern("(?s)<svg[^>]*>.*?</svg>設定");

		List<String> hrefs = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		Matcher link = Pattern.compile("(?s)<a href=\"([^\"]*)\"[^>]*>(.*?)</a>").matcher(group);
		while (link.find()) {
			hrefs.add(link.group(1));
			labels.add(text(link.group(2)));
		}
		assertThat(labels).as("「設定」の中の 3 つ").containsExactly("科目", "取引先", "支払方法");
		assertThat(hrefs).containsExactly("/categories", "/vendors", "/payment-methods");
	}

	@Test
	void theBudgetItemOpensTheBudgetScreenWhoseTitleStaysYosanSettei() throws Exception {
		String nav = nav(body("/transactions"));
		assertThat(nav).as("メニューの文字は「予算」").containsPattern("(?s)<a href=\"/budgets\"><svg[^>]*>.*?</svg>予算</a>");
		assertThat(nav).as("メニューに「予算設定」の文字はない").doesNotContain("予算設定");

		String budgets = body("/budgets");
		assertThat(budgets).as("画面の見出しとタブの名前は「予算設定」のまま").contains("<title>予算設定</title>", "<h1>予算設定</h1>");
		assertThat(nav(budgets)).contains("<a href=\"/budgets\" aria-current=\"page\">");
	}

	// ---- 今の印 ----

	@Test
	void theThreeScreensMarkTheSettingsAndTheirOwnItem() throws Exception {
		String[][] screens = {
				{ "/categories", "/categories" }, { "/categories/" + category.getId() + "/edit", "/categories" },
				{ "/vendors", "/vendors" }, { "/vendors/" + vendor.getId() + "/edit", "/vendors" },
				{ "/payment-methods", "/payment-methods" }, { "/payment-methods/" + payment.getId() + "/edit", "/payment-methods" } };
		for (String[] screen : screens) {
			String nav = nav(body(screen[0]));
			String group = group(nav);

			assertThat(summary(group)).as(screen[0] + "：「設定」に印（true）").startsWith("<summary aria-current=\"true\">");
			assertThat(group).as(screen[0] + "：中のその画面の項目に印（page）").contains("<a href=\"" + screen[1] + "\" aria-current=\"page\">");
			assertThat(nav.split("aria-current=\"page\"", -1)).as(screen[0] + "：page の印は 1 つだけ").hasSize(2);
			assertThat(nav.split("aria-current", -1)).as(screen[0] + "：印は「設定」と、その項目の 2 つだけ").hasSize(3);
		}
	}

	@Test
	void otherScreensDoNotMarkTheSettings() throws Exception {
		for (String path : List.of("/", "/transactions", "/transactions/new", "/transactions/" + transaction.getId() + "/edit",
				"/budgets", "/comparison")) {
			assertThat(group(nav(body(path)))).as(path + "：「設定」にも中の項目にも印はない").doesNotContain("aria-current");
		}
	}

	// ---- JavaScript を使わない ----

	@Test
	void theSettingsOpensWithDetailsAndNoJavaScript() throws Exception {
		String body = body("/categories");
		String nav = nav(body);

		assertThat(group(nav)).as("summary のあとに、3 つのリンクの入れ物").containsPattern("(?s)^\\s*<summary[^>]*>.*?</summary>\\s*<div class=\"menu-group-items\">");
		assertThat(nav).as("閉じた形で出す（open を付けない）").doesNotContain("<details class=\"menu-group\" open");
		assertThat(nav).doesNotContain("<script", "onclick", "ontoggle", "data-bs-", "javascript:", "role=\"menu\"");
		assertThat(body).as("Bootstrap の JavaScript を読まない").doesNotContain("bootstrap.bundle", "bootstrap.min.js");
	}
}
