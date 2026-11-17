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
		assertThat(body).contains("data-amount-buttons", "data-amount-target=\"lines0.amount\"");
		for (String step : new String[] { "1", "10", "100", "1000", "10000" }) {
			assertThat(body).contains("data-amount-add=\"" + step + "\"");
		}
		assertThat(body).contains("data-amount-tax=\"110\"", "data-amount-tax=\"108\"");
		assertThat(body).as("足すボタンの文字は、3桁ごとにカンマ付き").contains(">+1<", ">+10<", ">+100<", ">+1,000<", ">+10,000<");
		assertThat(body).as("ボタンの文字だけがカンマ付き。値はカンマなしの数字").doesNotContain(">+1000<", ">+10000<", "data-amount-add=\"1,000\"", "data-amount-add=\"10,000\"");
		assertThat(body).as("税込にするのボタンは「10%」「8%」（issue 025 で、「税込（10%）」「税込（8%）」から変えた）").contains(">10%<", ">8%<").doesNotContain("税込（10%）", "税込（8%）");
		assertThat(body).as("「金額をクリア」のボタンがある（issue 025 で、「クリア」から変えた）").contains("data-amount-clear", ">金額をクリア</button>");
		assertThat(body.indexOf("data-amount-add=\"10000\"")).as("1 行目（足すボタン）→ 2 行目（税込 10% → 税込 8% → クリア）の順")
				.isLessThan(body.indexOf("data-amount-tax=\"110\""));
		assertThat(body.indexOf("data-amount-tax=\"110\"")).isLessThan(body.indexOf("data-amount-tax=\"108\""));
		assertThat(body.indexOf("data-amount-tax=\"108\"")).as("クリアは、税込（8%）のすぐ右（足すボタンの行には置かない）")
				.isLessThan(body.indexOf("data-amount-clear"));
		assertThat(body.indexOf("data-amount-add=\"1\"")).as("足すボタンは 1 → 10 → 100 → 1000 → 10000 の順")
				.isLessThan(body.indexOf("data-amount-add=\"10\"")).isLessThan(body.indexOf("data-amount-add=\"100\""))
				.isLessThan(body.indexOf("data-amount-add=\"1000\"")).isLessThan(body.indexOf("data-amount-add=\"10000\""));
		assertThat(body).as("「金額をクリア」は、右の端に寄せない（ms-auto なし）。税込にするのまとまりのすぐ右").containsPattern("class=\"row-btn amount-clear\"\\s+data-amount-clear").doesNotContain("ms-auto");
		assertThat(body).as("クリアのボタンも、送信しない").containsPattern("type=\"button\"[^>]*data-amount-clear");
		assertThat(body).as("ボタンは送信しない").contains("type=\"button\"");
		assertThat(body).as("固定の文が、HTML に書いてある").contains(NOTE);
		assertThat(body).as("税率が混ざるレシートの案内（issue 032：明細の行を分ける）").contains("税率ごとに明細の行を分けて登録してください").doesNotContain("税率ごとに取引を分けて");
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
				.param("transactionDate", "2026-10-20").param("lines[0].amount", "abc")
				.param("lines[0].categoryId", String.valueOf(category.getId())).param("lines[0].description", "昼食")
				.param("vendorId", String.valueOf(vendor.getId()))
				.param("paymentMethodId", String.valueOf(payment.getId())))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertButtonsArePresent(body);
		assertThat(body).as("入力値が残る（欄の th:field は変えていない）。値が入っているので、JavaScript が税込を押せない状態で始める（Q009-18）").contains("name=\"lines[0].amount\" value=\"abc\"").doesNotContain("data-tax-locked");
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

	// ---- issue 025 の段階 4：金額ボタンを 2 つのまとまりに（「足す」「税込にする」）。動きは変えない ----

	/** 1 行目のボタンの入れ物（data-amount-buttons）から、1 行目の摘要のラベルの前まで（issue 032：ボタンは行ごとにある） */
	private static String boxOf(String body) {
		int start = body.lastIndexOf("<div", body.indexOf("data-amount-buttons"));
		int end = body.indexOf("for=\"lines0.description\"", start);
		assertThat(start).isPositive();
		assertThat(end).isGreaterThan(start);
		return body.substring(start, end);
	}

	/** 「足す」のまとまり（5 つ）と、「税込にする」のまとまり（2 つ）。それぞれ、左に 13px の灰色のラベル。まとまりは role="group" */
	@Test
	void thereAreTwoGroupsEachWithItsLabelOnTheLeft() throws Exception {
		String box = boxOf(page("/transactions/new"));

		java.util.regex.Matcher rows = java.util.regex.Pattern
				.compile("(?s)<span class=\"amount-row-label\">([^<]*)</span>\\s*<div class=\"amount-group\" role=\"group\" aria-label=\"([^\"]*)\">(.*?)</div>")
				.matcher(box);
		java.util.List<String> labels = new java.util.ArrayList<>();
		java.util.List<String> groups = new java.util.ArrayList<>();
		while (rows.find()) {
			assertThat(rows.group(1)).as("ラベルと aria-label は同じ言葉").isEqualTo(rows.group(2));
			labels.add(rows.group(1));
			groups.add(rows.group(3));
		}
		assertThat(labels).containsExactly("足す", "税込にする");
		assertThat(count(groups.get(0), "data-amount-add=")).as("足すのまとまりは 5 つ").isEqualTo(5);
		assertThat(groups.get(0)).doesNotContain("data-amount-tax", "data-amount-clear");
		assertThat(count(groups.get(1), "data-amount-tax=")).as("税込にするのまとまりは 2 つ").isEqualTo(2);
		assertThat(groups.get(1)).doesNotContain("data-amount-add", "data-amount-clear").contains(">10%<", ">8%<");
		assertThat(groups.get(0)).contains(">+1<", ">+10<", ">+100<", ">+1,000<", ">+10,000<");
		assertThat(box.indexOf("足す")).as("足す → 税込にする の順").isLessThan(box.indexOf("税込にする"));
	}

	/**
	 * 「金額をクリア」は、「税込にする」のまとまりの右端（すぐ右）。つながった形（</div>）の外に置く、灰色の枠の小さいボタン
	 * （一覧の「編集」と同じ row-btn。type="button"。issue 029 で、文字のリンクの見た目から変えた）。Bootstrap の btn は使わない
	 */
	@Test
	void theClearButtonIsASmallOutlinedButtonRightAfterTheTaxGroup() throws Exception {
		String box = boxOf(page("/transactions/new"));

		assertThat(box).containsPattern("(?s)data-amount-tax=\"108\"[^>]*>8%</button>\\s*</div>\\s*<button type=\"button\" class=\"row-btn amount-clear\" data-amount-clear>金額をクリア</button>");
		assertThat(box).doesNotContain("class=\"btn", ">クリア<", "ms-3", "ms-auto");
		assertThat(count(box, "data-amount-clear")).isEqualTo(1);
	}

	/**
	 * 組の中の段落は、押せないときの文だけ（hidden）。「税込にする」の説明と、税率が混ざったレシートの案内は、全部の組で同じ文なので、
	 * 明細の行の外に 1 回だけ、明細の上（共通の項目と 1 行目の間）に出す（issue 032）。JavaScript が動いたときだけ見せる（hidden）
	 */
	@Test
	void theLockNoteIsInEachSetAndTheExplanationIsOnceAboveTheLines() throws Exception {
		for (String path : new String[] { "/transactions/new", "/transactions/" + transaction.getId() + "/edit" }) {
			String body = page(path);
			String box = boxOf(body);
			assertThat(count(box, "<p")).as(path + "：組の中の段落は 1 つ").isEqualTo(1);
			assertThat(box).contains("<p class=\"amount-note\" data-amount-tax-note hidden>" + NOTE + "</p>");
			assertThat(box).doesNotContain("税率", "端数は切り捨て");

			String explanation = "「税込にする」のボタンは、欄の金額を税抜として計算します（端数は切り捨て）。8%と10%が混ざるレシートは、税率ごとに明細の行を分けて登録してください。";
			assertThat(count(body, explanation)).as(path + "：説明は 1 回だけ").isEqualTo(1);
			assertThat(body).containsPattern("<p class=\"amount-note\" data-amount-explain hidden>" + java.util.regex.Pattern.quote(explanation) + "</p>");
			int at = body.indexOf("data-amount-explain");
			assertThat(at).as(path + "：明細の上（支払方法のあと、1 行目の前）").isGreaterThan(body.indexOf("id=\"paymentMethodId\""))
					.isLessThan(body.indexOf("data-split-line"));
			assertThat(body).doesNotContain("form-text");
		}
	}

	/** JavaScript が頼る印が、形を変えても全部ある（足す 5、税込 2、クリア 1、固定の文 1、欄の id）。JavaScript は、クラス名に頼らない */
	@Test
	void everyMarkTheScriptNeedsIsStillThereAndTheScriptDoesNotDependOnClassNames() throws Exception {
		for (String path : new String[] { "/transactions/new", "/transactions/" + transaction.getId() + "/edit" }) {
			String body = page(path);
			String box = boxOf(body);
			assertThat(count(box, "data-amount-add=")).as(path).isEqualTo(5);
			assertThat(count(box, "data-amount-tax=\"")).as(path).isEqualTo(2);
			assertThat(count(box, "data-amount-clear")).as(path).isEqualTo(1);
			assertThat(count(box, "data-amount-tax-note")).as(path).isEqualTo(1);
			assertBoxTag(body, "lines0.amount");
			assertThat(body).as("入力欄の id（data-amount-target の指す先）").contains("id=\"lines0.amount\"");
			assertThat(count(box, "type=\"button\"")).as("ボタンは 8 つ。送信しない").isEqualTo(8);
		}
		String script = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "resources", "static", "js", "amount-buttons.js"));
		assertThat(script).as("JavaScript は、クラス名・見た目に頼らない（data- の印だけ）")
				.doesNotContain("classList", "className", "getElementsByClassName", "querySelector('.", "querySelectorAll('.");
		assertThat(script).contains("[data-amount-buttons]", "[data-amount-add]", "[data-amount-tax]", "[data-amount-clear]", "[data-amount-tax-note]", "[data-amount-explain]");
		assertThat(script).as("issue 032：画面の全部の入れ物（行ごとに 1 組）に、それぞれ効く").contains("querySelectorAll('[data-amount-buttons]')");
	}

	/** issue 032：ボタンの組は、明細の行ごとに 1 組（登録 5 組、編集 1 組）。各組は、同じ行の金額の欄を指し、その欄がある */
	@Test
	void eachLineHasItsOwnSetOfButtonsPointingToItsAmountField() throws Exception {
		String[][] screens = { { page("/transactions/new"), "5" }, { page("/transactions/" + transaction.getId() + "/edit"), "1" } };
		for (String[] screen : screens) {
			String body = screen[0];
			int lines = Integer.parseInt(screen[1]);
			assertThat(count(body, "data-amount-buttons")).as("組の数").isEqualTo(lines);
			assertThat(count(body, "data-amount-add=\"1\"")).isEqualTo(lines);
			assertThat(count(body, "data-amount-tax-note")).as("押せないときの文も、組ごとに 1 つ").isEqualTo(lines);
			for (int i = 0; i < lines; i++) {
				assertBoxTag(body, "lines" + i + ".amount");
				assertThat(body).contains("id=\"lines" + i + ".amount\"");
				assertThat(body.indexOf("data-amount-target=\"lines" + i + ".amount\"")).as("組は、その行の金額の欄のあと")
						.isGreaterThan(body.indexOf("id=\"lines" + i + ".amount\""));
			}
		}
	}

	/** 「キャンセル」「保存」は、今までどおり右下に並ぶ（送り先・method・CSRF は変わらない） */
	@Test
	void cancelAndSaveStayAtTheBottomRight() throws Exception {
		String body = page("/transactions/new");

		assertThat(body).containsPattern("(?s)<div class=\"form-actions\">\\s*<a class=\"btn btn-outline-secondary\" href=\"/transactions\">キャンセル</a>\\s*<button type=\"submit\" class=\"btn btn-primary\">保存</button>");
		assertThat(body).contains("method=\"post\"", "name=\"_csrf\"");
	}

	/** その欄を指す組の開始タグに、data-amount-buttons と hidden がある（属性の順は問わない。Thymeleaf は、作った属性を最後に置く） */
	private static void assertBoxTag(String body, String target) {
		java.util.regex.Matcher tag = java.util.regex.Pattern.compile("<div[^>]*data-amount-target=\"" + java.util.regex.Pattern.quote(target) + "\"[^>]*>").matcher(body);
		assertThat(tag.find()).as(target + " を指す組がある").isTrue();
		assertThat(tag.group()).as(target + " の組").contains("data-amount-buttons").containsPattern("\\shidden[\\s>]");
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}
}
