package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
 * 取引の登録・編集画面の、金額の入力を助けるボタン（issue 009、設計書 2.3）の、HTML の確認。
 * ここで確かめるのは、画面に、ボタンと印（data- 属性）と、固定の文が出ること。
 * ボタンを押したときの動き（計算、押せる・押せない）は、MockMvc では確かめられないので、ブラウザで確かめる（手順書 4.7）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionAmountButtonsTest {

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

	private Long userId;
	private Category category;
	private Vendor vendor;
	private PaymentMethod payment;
	private Transaction transaction;
	private RequestPostProcessor asUser;

	@BeforeEach
	void createUserWithATransaction() {
		userId = userRepository.save(new User("amtbtn1", "dummy-hash-for-test")).getId();
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transaction = transactionRepository.save(
				new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, "昼食", vendor, payment));
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(userId, "amtbtn1", "x"));
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	/** ボタンの部品がそろっていること（登録・編集・エラーで表示し直した画面で共通） */
	private void assertButtonsArePresent(String body) {
		assertThat(body).contains("data-amount-buttons", "data-amount-target=\"amount\"");
		for (String step : new String[] { "1", "10", "100", "1000", "10000" }) {
			assertThat(body).contains("data-amount-add=\"" + step + "\"");
		}
		assertThat(body).contains("data-amount-tax=\"110\"", "data-amount-tax=\"108\"");
		assertThat(body).as("足すボタンの文字は、3桁ごとにカンマ付き").contains(">+1<", ">+10<", ">+100<", ">+1,000<", ">+10,000<");
		assertThat(body).as("ボタンの文字だけがカンマ付き。値はカンマなしの数字").doesNotContain(">+1000<", ">+10000<", "data-amount-add=\"1,000\"", "data-amount-add=\"10,000\"");
		assertThat(body).contains("税込（10%）", "税込（8%）");
		assertThat(body).as("クリアのボタンがある").contains("data-amount-clear", ">クリア</button>");
		assertThat(body.indexOf("data-amount-add=\"10000\"")).as("1 行目（足すボタン）→ 2 行目（税込 10% → 税込 8% → クリア）の順")
				.isLessThan(body.indexOf("data-amount-tax=\"110\""));
		assertThat(body.indexOf("data-amount-tax=\"110\"")).isLessThan(body.indexOf("data-amount-tax=\"108\""));
		assertThat(body.indexOf("data-amount-tax=\"108\"")).as("クリアは、税込（8%）のすぐ右（足すボタンの行には置かない）")
				.isLessThan(body.indexOf("data-amount-clear"));
		assertThat(body.indexOf("data-amount-add=\"1\"")).as("足すボタンは 1 → 10 → 100 → 1000 → 10000 の順")
				.isLessThan(body.indexOf("data-amount-add=\"10\"")).isLessThan(body.indexOf("data-amount-add=\"100\""))
				.isLessThan(body.indexOf("data-amount-add=\"1000\"")).isLessThan(body.indexOf("data-amount-add=\"10000\""));
		assertThat(body).as("クリアは右の端に寄せず（ms-auto なし）、少しすき間を空ける（ms-3）").containsPattern("class=\"[^\"]*\\bms-3\\b[^\"]*\"\\s+data-amount-clear")
				.doesNotContain("ms-auto");
		assertThat(body).as("クリアのボタンも、送信しない").containsPattern("type=\"button\"[^>]*data-amount-clear");
		assertThat(body).as("ボタンは送信しない").contains("type=\"button\"");
		assertThat(body).as("固定の文が、HTML に書いてある").contains(NOTE);
		assertThat(body).as("税率が混ざるレシートの案内").contains("税率ごとに取引を分けて登録してください");
		assertThat(body).as("この画面だけで JavaScript を読む").contains("src=\"/js/amount-buttons.js\"");
	}

	@Test
	void newFormShowsTheButtonsHiddenUntilJavaScriptRunsAndTaxIsNotLocked() throws Exception {
		String body = page("/transactions/new");
		assertButtonsArePresent(body);
		assertThat(body).as("最初に押せるかどうかは、JavaScript が欄の値で決める。サーバーの印（data-tax-locked）は出さない（Q009-18）").doesNotContain("data-tax-locked");
		// JavaScript が無効のとき、ボタンと文は出ない（hidden のまま。Q009-11）
		assertThat(body).containsPattern("data-amount-buttons[^>]*\\shidden");
		assertThat(body).containsPattern("data-amount-tax-note\\s+hidden");
	}

	@Test
	void editFormShowsTheButtonsAndTheSavedAmountIsInTheField() throws Exception {
		String body = page("/transactions/" + transaction.getId() + "/edit");
		assertButtonsArePresent(body);
		assertThat(body).as("編集画面も、サーバーの印は出さない。保存した金額が欄に入っているので、JavaScript が押せない状態で始める（Q009-18）").doesNotContain("data-tax-locked").contains("value=\"1200\"");
	}

	@Test
	void formRedisplayedAfterAnInputErrorStillShowsTheButtonsAndKeepsTheValue() throws Exception {
		String body = mockMvc.perform(post("/transactions/new").with(asUser).with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "abc")
				.param("categoryId", String.valueOf(category.getId())).param("description", "昼食")
				.param("vendorId", String.valueOf(vendor.getId()))
				.param("paymentMethodId", String.valueOf(payment.getId())))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertButtonsArePresent(body);
		assertThat(body).as("入力値が残る（欄の th:field は変えていない）。値が入っているので、JavaScript が税込を押せない状態で始める（Q009-18）").contains("value=\"abc\"").doesNotContain("data-tax-locked");
		assertThat(body).contains("金額は整数で入力してください");
	}

	@Test
	void buttonsDoNotChangeTheAmountField() throws Exception {
		String body = page("/transactions/" + transaction.getId() + "/edit");
		assertThat(body).as("欄は今までどおり：文字の欄、数字のキーボード、保存した金額").contains("type=\"text\" inputmode=\"numeric\"");
		assertThat(body).contains("value=\"1200\"");
	}

	@Test
	void screenHasNoInlineScriptHandlersAndNoExternalScripts() throws Exception {
		for (String path : new String[] { "/transactions/new", "/transactions/" + transaction.getId() + "/edit" }) {
			String body = page(path);
			assertThat(body).as(path + "：HTML の中に、スクリプトや onclick を書かない").doesNotContain("onclick", "<script>");
			assertThat(body).as(path + "：外部の URL の JavaScript（CDN）を読まない").doesNotContainPattern("<script[^>]*src=\"https?:");
		}
	}

	@Test
	void budgetFormAndOtherScreensDoNotLoadTheAmountButtonsScript() throws Exception {
		for (String path : new String[] { "/budgets", "/transactions", "/categories", "/comparison" }) {
			String body = page(path);
			assertThat(body).as(path).doesNotContain("amount-buttons", "data-amount-add");
		}
	}

}
