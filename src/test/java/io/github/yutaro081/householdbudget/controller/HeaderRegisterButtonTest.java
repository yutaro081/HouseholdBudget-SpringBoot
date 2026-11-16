package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * ヘッダーの「取引を登録」（要件定義書 4.5、設計書 2.3「ヘッダー」、issue 029 の段階 3）。
 * ・ログイン後の全画面のヘッダーに 1 つだけ。&lt;nav&gt; の外で、メニューの右、ユーザー名の左。主ボタン（btn btn-primary）、＋のアイコン（plus、線の太さ 2.4）
 * ・トップと取引一覧の本文（main）には、もう置かない
 * ・取引の登録画面（新規・複写・入力エラーで出し直したとき）は、ボタンに aria-current="page"。メニューの「取引一覧」には印を付けない
 * ・取引の編集画面（入力エラーで出し直したときも）は、今どおり「取引一覧」に印。ボタンには付けない
 * ・ログイン前の画面（ログイン、アカウント登録）のヘッダーには出ない
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HeaderRegisterButtonTest {

	private static final Pattern HEADER = Pattern.compile("<header.*?</header>", Pattern.DOTALL);
	private static final Pattern NAV = Pattern.compile("<nav class=\"menu[^>]*>(.*?)</nav>", Pattern.DOTALL);
	/** ヘッダーの「取引を登録」（開きタグから </a> まで） */
	private static final Pattern BUTTON = Pattern.compile("<a [^>]*href=\"/transactions/new\"[^>]*>.*?</a>", Pattern.DOTALL);

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
		Long userId = userRepository.save(new User("hdrreg1", "dummy-hash-for-test")).getId();
		asUser = user(new LoginUser(userId, "hdrreg1", "x"));
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transaction = transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, "昼食", vendor, payment));
		entityManager.flush();
		entityManager.clear();
	}

	private String body(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String header(String body) {
		Matcher m = HEADER.matcher(body);
		assertThat(m.find()).as("ヘッダー（header）がある").isTrue();
		return m.group();
	}

	private static String nav(String header) {
		Matcher m = NAV.matcher(header);
		assertThat(m.find()).as("メニュー（nav）がある").isTrue();
		return m.group(1);
	}

	private static String main(String body) {
		return body.substring(body.indexOf("<main"), body.indexOf("</main>"));
	}

	/** ヘッダーの中の「取引を登録」。ちょうど 1 つだけあること */
	private static String button(String header) {
		List<String> buttons = BUTTON.matcher(header).results().map(MatchResult::group).toList();
		assertThat(buttons).as("ヘッダーの「取引を登録」は 1 つだけ").hasSize(1);
		return buttons.get(0);
	}

	private static String openingTag(String button) {
		return button.substring(0, button.indexOf('>') + 1);
	}

	/** 取引の登録画面：ボタンに印、メニューには印なし */
	private static void assertTheButtonIsCurrent(String body, String screen) {
		String header = header(body);
		assertThat(openingTag(button(header))).as(screen + "：ボタンに今の印").contains("aria-current=\"page\"");
		assertThat(nav(header)).as(screen + "：メニュー（「取引一覧」を含む）には印を付けない").doesNotContain("aria-current");
	}

	/** 取引の編集画面：「取引一覧」に印、ボタンには印なし */
	private static void assertTheListItemIsCurrent(String body, String screen) {
		String header = header(body);
		assertThat(openingTag(button(header))).as(screen + "：ボタンには印を付けない").doesNotContain("aria-current");
		assertThat(nav(header)).as(screen + "：「取引一覧」に印").contains("<a href=\"/transactions\" aria-current=\"page\">");
	}

	// ---- どの画面にも 1 つ ----

	@Test
	void everyScreenAfterLoginHasOneRegisterButtonInTheHeaderOutsideTheMenu() throws Exception {
		List<String> paths = List.of("/", "/transactions", "/transactions/new", "/transactions/" + transaction.getId() + "/edit",
				"/categories", "/categories/" + category.getId() + "/edit", "/vendors", "/vendors/" + vendor.getId() + "/edit",
				"/payment-methods", "/payment-methods/" + payment.getId() + "/edit", "/budgets", "/comparison");
		for (String path : paths) {
			String header = header(body(get(path)));
			String button = button(header);

			assertThat(button).as(path + "：主ボタン、＋のアイコン、「取引を登録」").containsPattern(Pattern.compile(
					"<a class=\"btn btn-primary header-register\" href=\"/transactions/new\"[^>]*><svg[^>]*stroke-width=\"2.4\"[^>]*>.*?</svg>取引を登録</a>",
					Pattern.DOTALL));
			assertThat(nav(header)).as(path + "：メニュー（nav）の中には置かない").doesNotContain("/transactions/new", "取引を登録");
			assertThat(header.indexOf("</nav>")).as(path + "：メニューの右").isLessThan(header.indexOf(button));
			assertThat(header.indexOf(button)).as(path + "：ユーザー名の左").isLessThan(header.indexOf("class=\"user-name\""));
		}
	}

	/**
	 * 「取引を登録」は、メニュー（nav）のすぐあと、ユーザー名・ログアウトのまとまり（user-area）の前。user-area の中には入れない
	 * （2026年11月16日に、ユーザー名のそばから、メニューの「設定」の近くへ移した）
	 */
	@Test
	void theRegisterButtonComesRightAfterTheMenuAndOutsideTheUserArea() throws Exception {
		for (String path : List.of("/", "/transactions", "/categories")) {
			String header = header(body(get(path)));
			String button = button(header);
			int userArea = header.indexOf("<div class=\"user-area");

			assertThat(header).as(path + "：メニューのすぐあと").containsPattern("</nav>\\s*<a class=\"btn btn-primary header-register\"");
			assertThat(userArea).as(path + "：user-area がある").isPositive();
			assertThat(header.indexOf(button) + button.length()).as(path + "：user-area の前（中には入れない）").isLessThan(userArea);
			assertThat(header.substring(userArea)).as(path + "：user-area はユーザー名とログアウトだけ").doesNotContain("header-register", "/transactions/new");
		}
	}

	// ---- 本文には置かない ----

	@Test
	void theTopAndTheListHaveNoRegisterButtonInTheMain() throws Exception {
		for (String path : List.of("/", "/transactions")) {
			String main = main(body(get(path)));

			assertThat(main).as(path + "：本文に「取引を登録」はない").doesNotContain("取引を登録", "href=\"/transactions/new\"");
		}
		assertThat(main(body(get("/")))).as("トップの見出しの右は「予実比較を見る」だけ")
				.containsPattern("<div class=\"top-actions\">\\s*<a class=\"btn btn-outline-secondary\" href=\"/comparison\">予実比較を見る</a>\\s*</div>");
	}

	// ---- 今の印 ----

	@Test
	void theRegisterScreensMarkTheButtonAndNotTheListItem() throws Exception {
		assertTheButtonIsCurrent(body(get("/transactions/new")), "新規");
		assertTheButtonIsCurrent(body(get("/transactions/new").param("copyFrom", String.valueOf(transaction.getId()))), "複写");
		String error = body(post("/transactions/new").with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "1200").param("categoryId", "")
				.param("vendorId", String.valueOf(vendor.getId())).param("paymentMethodId", String.valueOf(payment.getId())));
		assertThat(error).as("入力エラーで出し直した画面").contains("field-error");
		assertTheButtonIsCurrent(error, "新規の入力エラー");
	}

	@Test
	void theEditScreenMarksTheListItemAndNotTheButton() throws Exception {
		assertTheListItemIsCurrent(body(get("/transactions/" + transaction.getId() + "/edit")), "編集");
		String error = body(post("/transactions/" + transaction.getId() + "/edit").with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "abc").param("categoryId", String.valueOf(category.getId()))
				.param("vendorId", String.valueOf(vendor.getId())).param("paymentMethodId", String.valueOf(payment.getId())));
		assertThat(error).as("入力エラーで出し直した画面").contains("field-error");
		assertTheListItemIsCurrent(error, "編集の入力エラー");
	}

	@Test
	void otherScreensDoNotMarkTheButton() throws Exception {
		for (String path : List.of("/", "/transactions", "/categories", "/budgets", "/comparison")) {
			assertThat(openingTag(button(header(body(get(path)))))).as(path + "：ボタンには印を付けない").doesNotContain("aria-current");
		}
	}

	// ---- ログイン前 ----

	@Test
	void screensBeforeLoginHaveNoRegisterButton() throws Exception {
		for (String path : List.of("/login", "/register")) {
			String page = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

			assertThat(page).as(path).doesNotContain("取引を登録", "/transactions/new", "header-register");
		}
	}
}
