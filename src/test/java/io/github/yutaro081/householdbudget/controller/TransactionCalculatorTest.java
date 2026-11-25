package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 取引の登録・編集画面の、共有の電卓（issue 035 の段階 3。設計書 2.3、要件定義書 4.2）の、HTML の確認。
 * issue 009・032 の行ごとの金額ボタン（TransactionAmountButtonsTest）を置きかえた。
 * ここで確かめるのは、画面に、電卓のボタンと印（data- 属性）と、固定の文が出ること、並びと Tab で止まるボタン、読む JavaScript。
 * ボタンを押したときの動き（入れる先、計算、押せる・押せない）は、MockMvc では確かめられないので、本人がブラウザで確かめる（手順書 6 章）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionCalculatorTest {

	private static final String NOTE = "この金額には、もう税をかけられません。税抜きの金額を入れ直すと、押せるようになります。";

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
	void createUserWithATransaction() {
		Long userId = userRepository.save(new User("calckey1", "dummy-hash-for-test")).getId();
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transaction = transactionRepository.save(
				new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, "昼食", vendor, payment));
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(userId, "calckey1", "x"));
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String errorPage() throws Exception {
		return mockMvc.perform(post("/transactions/new").with(asUser).with(csrf())
				.param("transactionDate", "2026-10-20").param("lines[0].amount", "abc")
				.param("lines[0].categoryId", String.valueOf(category.getId())).param("lines[0].description", "昼食")
				.param("vendorId", String.valueOf(vendor.getId())).param("paymentMethodId", String.valueOf(payment.getId())))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 登録・編集・複写・入力エラーで表示し直した画面 */
	private List<String> formPages() throws Exception {
		return List.of(page("/transactions/new"), page("/transactions/" + transaction.getId() + "/edit"),
				page("/transactions/new?copyFrom=" + transaction.getId()), errorPage());
	}

	/** 電卓の入れ物（<div class="calc" data-calc …>）から、その閉じタグの手前まで。中の div は 2 つ（ボタンの格子、税込の行） */
	private static String calcOf(String body) {
		Matcher m = Pattern.compile("(?s)<div class=\"calc\" data-calc[^>]*>(.*?</div>.*?</div>.*?)</div>").matcher(body);
		assertThat(m.find()).as("電卓の入れ物がある").isTrue();
		return m.group(1);
	}

	private static int count(String text, String word) {
		return text.split(Pattern.quote(word), -1).length - 1;
	}

	@Test
	void eachFormScreenHasOneCalculatorHiddenUntilJavaScriptRuns() throws Exception {
		for (String body : formPages()) {
			assertThat(count(body, "<div class=\"calc\" data-calc")).as("電卓は 1 つ（行ごとではない）").isEqualTo(1);
			assertThat(body).as("JavaScript が無効のときは出ない（hidden）。まとまりの名前").containsPattern(
					"<div class=\"calc\" data-calc role=\"group\" aria-label=\"電卓\" hidden>");
		}
	}

	/** ボタンの並びは、もとの構想どおり：7 8 9 ← ／ 4 5 6 C ／ 1 2 3 ＋ ／ 0 00 000 − */
	@Test
	void theKeysAreInTheOrderOfTheIdea() throws Exception {
		String calc = calcOf(page("/transactions/new"));
		Matcher m = Pattern.compile("data-calc-(key=\"([^\"]*)\"|back|clear)").matcher(calc);
		List<String> order = new ArrayList<>();
		while (m.find()) {
			order.add(m.group(2) != null ? m.group(2) : m.group(1));
		}
		assertThat(order).containsExactly("7", "8", "9", "back", "4", "5", "6", "clear", "1", "2", "3", "+", "0", "00", "000", "-");
		assertThat(calc).contains(">←</button>", ">C</button>", ">＋</button>", ">−</button>", ">00</button>", ">000</button>");
	}

	/** 税込は 10% と 8% の 2 つ（ボタンの格子の下）。文字は「税込 10%」「税込 8%」 */
	@Test
	void theTaxButtonsAreTenAndEightPercentBelowTheKeys() throws Exception {
		String calc = calcOf(page("/transactions/new"));
		assertThat(calc).containsPattern("data-calc-tax=\"110\">税込 10%</button>").containsPattern("data-calc-tax=\"108\">税込 8%</button>");
		assertThat(calc.indexOf("data-calc-key=\"-\"")).isLessThan(calc.indexOf("data-calc-tax=\"110\""));
		assertThat(calc.indexOf("data-calc-tax=\"110\"")).isLessThan(calc.indexOf("data-calc-tax=\"108\""));
	}

	/** ボタンは全部 type="button"（送信しない）で 18 個。Tab で止まるのは C と税込の 2 つだけ（ほかは tabindex="-1"） */
	@Test
	void allButtonsDoNotSubmitAndOnlyClearAndTaxAreTabStops() throws Exception {
		String calc = calcOf(page("/transactions/new"));
		assertThat(count(calc, "<button type=\"button\"")).isEqualTo(18);
		assertThat(calc).doesNotContain("type=\"submit\"");
		Matcher tags = Pattern.compile("<button [^>]*>").matcher(calc);
		while (tags.find()) {
			String tag = tags.group();
			if (tag.contains("data-calc-clear") || tag.contains("data-calc-tax")) {
				assertThat(tag).as("Tab で止まる：" + tag).doesNotContain("tabindex");
			} else {
				assertThat(tag).as("Tab で止まらない：" + tag).contains("tabindex=\"-1\"");
			}
		}
		assertThat(calc).as("← の読み上げの名前").containsPattern("data-calc-back[^>]*aria-label=\"1 文字消す\"");
	}

	/** 税込を押せないときの文は、電卓の中に 1 つ（hidden。JavaScript が見せる・隠すだけ）。サーバーの印は出さない */
	@Test
	void theLockNoteIsOnceInTheCalculator() throws Exception {
		for (String body : formPages()) {
			assertThat(count(body, NOTE)).isEqualTo(1);
			assertThat(calcOf(body)).contains("<p class=\"calc-note\" data-calc-tax-note hidden>" + NOTE + "</p>");
			assertThat(body).doesNotContain("data-tax-locked");
		}
	}

	/**
	 * 説明の文は、電卓のボタンの下（電卓と一緒に、JavaScript が動くときだけ見える）。明細の上の説明の段落は、短くしてここに移した。
	 * 2 つの段落で、13px の補足の文字（calc-note）
	 */
	@Test
	void theShortExplanationIsInsideTheCalculatorBelowTheButtons() throws Exception {
		String calc = calcOf(page("/transactions/new"));
		String tax = "<p class=\"calc-note\">税込は、欄の金額を税抜として計算します（端数は切り捨て）。税率の違う品は、行を分けて登録してください。</p>";
		String formula = "<p class=\"calc-note\">欄には 1280+398-300 のような式も打てます（Enter で計算）。</p>";
		assertThat(calc).contains(tax, formula);
		assertThat(calc.indexOf(tax)).as("ボタンの下").isGreaterThan(calc.indexOf("data-calc-tax=\"108\"")).isLessThan(calc.indexOf(formula));
		assertThat(page("/transactions/new")).doesNotContain("data-amount-explain", "class=\"amount-note\"", "8%と10%が混ざるレシート");
	}

	/** 行ごとの金額ボタン（issue 009・032）は、なくした */
	@Test
	void thePerLineAmountButtonsAreGone() throws Exception {
		for (String body : formPages()) {
			assertThat(body).doesNotContain("data-amount-", "amount-buttons", ">+1,000<", "金額をクリア", "class=\"amount-btn\"");
		}
	}

	/** 読む JavaScript：split-lines.js、calculator.js、calculator-keys.js の順（calculator-keys.js は calculator.js の window.Calculator を使う） */
	@Test
	void theScriptsAreLoadedAndTheKeysScriptComesAfterTheCalculator() throws Exception {
		for (String body : formPages()) {
			int split = body.indexOf("<script defer src=\"/js/split-lines.js\"></script>");
			int calc = body.indexOf("<script defer src=\"/js/calculator.js\"></script>");
			int keys = body.indexOf("<script defer src=\"/js/calculator-keys.js\"></script>");
			assertThat(split).isPositive();
			assertThat(calc).isPositive().isLessThan(keys);
			assertThat(body).as("HTML の中にスクリプトや onclick を書かない").doesNotContain("<script>", "onclick", "onmousedown");
		}
	}

	@Test
	void otherScreensDoNotLoadTheCalculatorKeysScript() throws Exception {
		for (String path : new String[] { "/budgets", "/transactions", "/categories", "/comparison", "/" }) {
			assertThat(page(path)).as(path).doesNotContain("calculator-keys.js", "data-calc-key");
		}
	}

	/** 並び：明細の行と「行を足す」は左の列（col-lg-8）、電卓は右の列（col-lg-4）。明細の合計と「保存」は、そのあと（全幅） */
	@Test
	void theCalculatorIsInTheRightColumnAfterTheLinesAndBeforeTheTotal() throws Exception {
		for (String body : formPages()) {
			int left = body.indexOf("<div class=\"col-lg-8\">");
			int right = body.indexOf("<div class=\"col-lg-4\">", left);
			int calc = body.indexOf("<div class=\"calc\" data-calc");
			assertThat(left).as("左の列").isPositive().isLessThan(body.indexOf("data-split-line"));
			assertThat(right).as("右の列は、最後の行と「行を足す」のあと").isGreaterThan(body.indexOf("id=\"lines4.description\""))
					.isGreaterThan(body.indexOf("data-split-add"));
			assertThat(calc).isGreaterThan(right).isLessThan(body.indexOf("data-split-total-box"));
		}
	}

}
