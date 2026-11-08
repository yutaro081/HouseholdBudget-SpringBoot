package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * ヘッダーのメニューで、今開いている画面の項目だけに aria-current="page" が付くことの確認（設計書 2.3、issue 015 の 1 回目）。
 * 取引の登録・編集・詳細の画面は、「取引一覧」の項目を示す。メニューの &lt;a&gt; にクラスは付けない。
 * ログイン前の画面（ログイン、登録）には、メニューが出ない。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MenuCurrentItemTest {

	private static final Pattern NAV = Pattern.compile("<nav class=\"menu[^>]*>(.*?)</nav>", Pattern.DOTALL);

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

	private Map<String, String> pageToMenuHref;
	private RequestPostProcessor asUser;

	@BeforeEach
	void createUserWithData() {
		Long userId = userRepository.save(new User("menucur1", "dummy-hash-for-test")).getId();
		Category category = categoryRepository.save(new Category(userId, "食費"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		Transaction transaction = transactionRepository.save(
				new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, "昼食", vendor, payment));
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(userId, "menucur1", "x"));

		pageToMenuHref = new LinkedHashMap<>();
		pageToMenuHref.put("/", "/");   // トップ（issue 010）。メニューの先頭の「トップ」が今の項目
		pageToMenuHref.put("/transactions", "/transactions");
		pageToMenuHref.put("/transactions/new", "/transactions");
		pageToMenuHref.put("/transactions/" + transaction.getId(), "/transactions");
		pageToMenuHref.put("/transactions/" + transaction.getId() + "/edit", "/transactions");
		pageToMenuHref.put("/categories", "/categories");
		pageToMenuHref.put("/categories/" + category.getId() + "/edit", "/categories");
		pageToMenuHref.put("/vendors", "/vendors");
		pageToMenuHref.put("/vendors/" + vendor.getId() + "/edit", "/vendors");
		pageToMenuHref.put("/payment-methods", "/payment-methods");
		pageToMenuHref.put("/payment-methods/" + payment.getId() + "/edit", "/payment-methods");
		pageToMenuHref.put("/budgets", "/budgets");
		pageToMenuHref.put("/comparison", "/comparison");
	}

	private String body(String path, boolean loggedIn) throws Exception {
		var request = loggedIn ? get(path).with(asUser) : get(path);
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String nav(String body) {
		Matcher m = NAV.matcher(body);
		assertThat(m.find()).as("メニュー（nav）がある").isTrue();
		return m.group(1);
	}

	@Test
	void onlyTheItemOfTheOpenScreenIsMarkedAsCurrent() throws Exception {
		for (Map.Entry<String, String> entry : pageToMenuHref.entrySet()) {
			String nav = nav(body(entry.getKey(), true));
			assertThat(nav.split("aria-current", -1)).as(entry.getKey() + "：印は 1 つだけ").hasSize(2);
			assertThat(nav).as(entry.getKey() + "：「" + entry.getValue() + "」の項目に印が付く")
					.contains("<a href=\"" + entry.getValue() + "\" aria-current=\"page\">");
		}
	}

	@Test
	void menuLinksHaveNoClassAttribute() throws Exception {
		for (String path : pageToMenuHref.keySet()) {
			assertThat(nav(body(path, true))).as(path + "：メニューの <a> にクラスを付けない").doesNotContainPattern("<a [^>]*class=");
		}
	}

	/** メニューは 7 項目で、先頭が「トップ」（issue 010）。そのあと、取引一覧から予実比較まで、今までの順 */
	@Test
	void menuHasAllSevenItemsInOrderAndTopIsFirst() throws Exception {
		String nav = nav(body("/transactions", true));

		Matcher links = Pattern.compile("<a [^>]*href=\"([^\"]*)\"[^>]*>([^<]*)</a>").matcher(nav);
		List<String> hrefs = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		while (links.find()) {
			hrefs.add(links.group(1));
			labels.add(links.group(2));
		}

		assertThat(labels).as("メニューの項目（<a>）は、ちょうど 7 個")
				.containsExactly("トップ", "取引一覧", "科目", "取引先", "支払方法", "予算", "予実比較");
		assertThat(hrefs).containsExactly("/", "/transactions", "/categories", "/vendors", "/payment-methods",
				"/budgets", "/comparison");
	}

	@Test
	void screensWithoutTheHeaderHaveNoMenu() throws Exception {
		assertThat(body("/login", false)).doesNotContain("class=\"menu", "aria-current");
		assertThat(body("/register", false)).doesNotContain("class=\"menu", "aria-current");
	}

}
