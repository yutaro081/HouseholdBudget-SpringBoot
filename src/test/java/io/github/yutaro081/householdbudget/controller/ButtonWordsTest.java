package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
 * ボタンの言葉（設計書 2.3「ボタンの言葉」、issue 024 の段階 7）。決まりは 2 つ：
 * 一覧の画面で、その場で 1 行足す → 「追加」。入力の画面の内容を確定する → 「保存」。
 * 「登録する」「更新」「変更」の送信ボタンはなくなり、ログイン・登録（アカウント）・削除と、見出し「取引を登録」などは、そのまま。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ButtonWordsTest {

	private static final Pattern SUBMIT = Pattern.compile("<button type=\"submit\"[^>]*>(.*?)</button>", Pattern.DOTALL);

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

	private Category category;
	private Vendor vendor;
	private PaymentMethod payment;
	private Transaction transaction;
	private RequestPostProcessor asUser;

	@BeforeEach
	void setUp() {
		Long id = userRepository.save(new User("btnword1", "dummy-hash-for-test")).getId();
		category = categoryRepository.save(new Category(id, "食費"));
		vendor = vendorRepository.save(new Vendor(id, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(id, "現金"));
		transaction = transactionRepository.save(new Transaction(id, LocalDate.of(2026, 10, 19), 100, category, null, vendor, payment));
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(id, "btnword1", "x"));
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 本体（main）の、送信ボタンの文字を、順に集める（ヘッダーのログアウトは含めない） */
	private String submitWords(String path) throws Exception {
		String body = page(path);
		String main = body.substring(body.indexOf("<main"), body.indexOf("</main>"));
		StringBuilder words = new StringBuilder();
		Matcher m = SUBMIT.matcher(main);
		while (m.find()) {
			words.append('[').append(m.group(1).replaceAll("<[^>]*>", "").strip()).append(']');
		}
		return words.toString();
	}

	/** 一覧の画面でその場で 1 行足す → 「追加」。行ごとの「削除」はそのまま */
	@Test
	void theListScreensAddWithTsuika() throws Exception {
		for (String path : new String[] { "/categories", "/vendors", "/payment-methods" }) {
			assertThat(submitWords(path)).as(path).isEqualTo("[追加][削除]");
		}
	}

	/** 入力の画面の内容を確定する → 「保存」。「変更」の送信ボタンはなくなる */
	@Test
	void theEditScreensOfTheMastersSaveWithHozon() throws Exception {
		String[] paths = { "/categories/" + category.getId() + "/edit", "/vendors/" + vendor.getId() + "/edit",
				"/payment-methods/" + payment.getId() + "/edit" };
		for (String path : paths) {
			assertThat(submitWords(path)).as(path).isEqualTo("[保存]");
		}
	}

	@Test
	void theTransactionNewAndEditScreensSaveWithHozon() throws Exception {
		assertThat(submitWords("/transactions/new")).isEqualTo("[保存]");
		assertThat(submitWords("/transactions/" + transaction.getId() + "/edit")).isEqualTo("[保存]");
	}

	/** 画面を開くリンク（確定するボタンではない）は、そのまま：「変更」「編集」「複写」「＋ 取引を登録」 */
	@Test
	void linksThatOpenAScreenKeepTheirWords() throws Exception {
		assertThat(page("/categories")).contains("/categories/" + category.getId() + "/edit\">").contains("変更</a>");   // issue 025：えんぴつのアイコンが前に入る
		assertThat(page("/transactions")).contains("取引を登録</a>", ">編集</a>", ">複写</a>").doesNotContain(">詳細</a>");   // issue 025：一覧の「詳細」は、「編集」「複写」に変えた
		assertThat(submitWords("/transactions")).as("一覧の各行の「削除」（issue 025。詳細の画面はなくした）").isEqualTo("[削除]");
	}

	/** ログイン・アカウント登録は、そのまま */
	@Test
	void loginAndRegisterKeepTheirWords() throws Exception {
		String login = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
		String register = mockMvc.perform(get("/register")).andReturn().getResponse().getContentAsString();

		assertThat(login).contains(">ログイン</button>");
		assertThat(register).contains(">登録</button>");
	}
}
