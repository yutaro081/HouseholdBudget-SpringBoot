package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 取引の登録・編集画面の 2 列の作り（issue 015 の 3 回目。設計書 2.3、4.4.2）の、HTML と CSS の確認。
 * 確かめるのは、HTML の文字列〔2 列の入れ物、項目の順、ラベルの横の印、右下のボタン、上の戻るリンク〕と、style.css の規則。
 * 実際の並び〔2 列になること、992px の前後、金額ボタンが 1 行に収まること〕は、MockMvc では確かめられないので、ブラウザで確かめる。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionFormLayoutTest {

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
		Long userId = userRepository.save(new User("formlay1", "dummy-hash-for-test")).getId();
		category = categoryRepository.save(new Category(userId, "食費"));
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		transaction = transactionRepository.save(
				new Transaction(userId, LocalDate.of(2026, 10, 20), 1200, category, "昼食", vendor, payment));
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(userId, "formlay1", "x"));
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private String css() throws Exception {
		return mockMvc.perform(get("/css/style.css")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
			n++;
		}
		return n;
	}

	/** 画面の本体（&lt;main&gt;）の中のフォームの位置。ヘッダーにもログアウトのフォームと&lt;button&gt;があるので、&lt;main&gt; 以降で探す */
	private static int formStart(String body) {
		return body.indexOf("<form", body.indexOf("<main"));
	}

	private String newPage() throws Exception {
		return page("/transactions/new");
	}

	private String editPage() throws Exception {
		return page("/transactions/" + transaction.getId() + "/edit");
	}

	private String errorPage() throws Exception {
		return mockMvc.perform(post("/transactions/new").with(asUser).with(csrf())
				.param("transactionDate", "2026-10-20").param("amount", "abc")
				.param("categoryId", String.valueOf(category.getId())).param("description", "昼食")
				.param("vendorId", String.valueOf(vendor.getId()))
				.param("paymentMethodId", String.valueOf(payment.getId())))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	// ---- ボタンの文字と、戻る行き先 ----

	@Test
	void submitButtonSaysHozonOnNewAndOnEdit() throws Exception {
		assertThat(newPage()).containsPattern("<button type=\"submit\"[^>]*>保存</button>").doesNotContainPattern("<button type=\"submit\"[^>]*>(登録する|登録|更新)</button>");
		assertThat(editPage()).containsPattern("<button type=\"submit\"[^>]*>保存</button>").doesNotContain("登録する</button>", "更新</button>");
		assertThat(errorPage()).as("入力エラーで表示し直した登録画面").containsPattern("<button type=\"submit\"[^>]*>保存</button>");
	}

	@Test
	void backLinkIsAboveTheFormAndCancelIsInsideTheFormBeforeTheSubmitButton() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			int back = body.indexOf("← 取引一覧に戻る");
			int form = formStart(body);
			int cancel = body.indexOf("キャンセル");
			int submit = body.indexOf("<button type=\"submit\"", form);
			assertThat(back).as("上の戻るリンクがある").isPositive();
			assertThat(body).containsPattern("<a [^>]*href=\"/transactions\"[^>]*>← 取引一覧に戻る</a>");
			assertThat(body.indexOf("<h1")).as("見出しの上").isGreaterThan(back);
			assertThat(form).as("戻るリンクはフォームの外（フォームの前）").isGreaterThan(back);
			assertThat(cancel).as("キャンセルはフォームの中").isGreaterThan(form);
			assertThat(submit).as("キャンセルは送信ボタンの前（右下は、キャンセル → 送信の順）").isGreaterThan(cancel);
			assertThat(body).containsPattern("<a [^>]*href=\"/transactions\"[^>]*>キャンセル</a>");
			assertThat(body).as("下にあった「戻る」のリンクは、なくなった").doesNotContain(">戻る</a>");
		}
	}

	@Test
	void cancelIsALinkNotAButtonSoEnterStillSubmitsTheForm() throws Exception {
		String body = newPage();
		String formPart = body.substring(formStart(body), body.indexOf("</form>", formStart(body)));
		assertThat(count(formPart, "<button")).as("フォームの中は、送信ボタン 1 つのほかは、金額ボタン（type=button）だけ").isEqualTo(count(formPart, "type=\"button\"") + 1);
	}

	@Test
	void backLinkIsShownEvenWhenTheMastersAreEmptyAndNoFormIsShown() throws Exception {
		Long id = userRepository.save(new User("formlay2", "dummy-hash-for-test")).getId();
		String body = mockMvc.perform(get("/transactions/new").with(user(new LoginUser(id, "formlay2", "x"))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body).contains("← 取引一覧に戻る", "先に科目を追加してください").doesNotContain("キャンセル", "name=\"amount\"");
	}

	// ---- ラベルの横の印 ----

	@Test
	void fiveRequiredMarksAndOneOptionalMarkAndNoHtmlRestrictions() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(count(body, "field-chip-required\">必須</span>")).as("必須の印：日付・金額・科目・取引先・支払方法").isEqualTo(5);
			assertThat(count(body, "field-chip-optional\">任意・30 文字以内</span>")).as("摘要の印").isEqualTo(1);
			assertThat(body).as("印は表示だけ（G1）：required と maxlength は付けない").doesNotContainPattern("\\srequired[\\s=>]").doesNotContain("maxlength");
			assertThat(body).as("ラベルの文から、補足を外した").doesNotContain("（任意。30文字以内）", "金額（円。1〜1000000の整数）");
		}
	}

	/** issue 017：金額のラベルは「金額（円）」 */
	@Test
	void amountLabelHasTheYenInParentheses() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).containsPattern("<label[^>]*for=\"amount\"[^>]*>金額（円） <span class=\"field-chip field-chip-required\">必須</span>");
		}
	}

	/** issue 017：日付の欄に date-input（992px 以上で幅を半分にする目印）、左の列に form-col-left（項目の間隔を広げる目印）を付ける */
	@Test
	void dateInputAndLeftColumnCarryTheLayoutHooks() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).containsPattern("<input class=\"form-control date-input\" type=\"date\"");
			assertThat(body).contains("<div class=\"col-lg-6 form-col-left\">");
			assertThat(count(body, "form-col-left")).isEqualTo(1);
		}
	}

	@Test
	void marksAreInsideTheLabelsOfTheirFields() throws Exception {
		String body = newPage();
		for (String id : new String[] { "transactionDate", "amount", "categoryId", "vendorId", "paymentMethodId" }) {
			assertThat(body).as(id).containsPattern("<label[^>]*for=\"" + id + "\"[^>]*>[^<]*<span class=\"field-chip field-chip-required\">必須</span>");
		}
		assertThat(body).containsPattern("<label[^>]*for=\"description\"[^>]*>[^<]*<span class=\"field-chip field-chip-optional\">任意・30 文字以内</span>");
	}

	@Test
	void amountRangeHintIsBelowTheAmountFieldWithTheSameWords() throws Exception {
		String body = newPage();
		assertThat(body).contains("1〜1,000,000 の整数");
		// issue 017：ラベルが「金額（円）」になったので、補足の頭の「円。」はなくした。カンマ付き
		assertThat(body).doesNotContain("円。1〜1000000の整数", "円。1〜");
		assertThat(body.indexOf("id=\"amount\"")).isLessThan(body.indexOf("1〜1,000,000 の整数"));
		assertThat(body.indexOf("1〜1,000,000 の整数")).as("金額ボタンの前").isLessThan(body.indexOf("data-amount-buttons"));
		assertThat(count(newPage(), "class=\"field-error\"")).as("エラーがない画面に field-error はない").isZero();
	}

	// ---- 2 列の作り（HTML の順 = 1 列のときの順 = Tab の順） ----

	@Test
	void twoColumnsLeftHasDateCategoryVendorPaymentAndRightHasAmountButtonsDescription() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).containsPattern("<div class=\"row[^\"]*\"");
			assertThat(count(body, "col-lg-6")).as("列は 2 つ").isEqualTo(2);
			assertThat(body).as("切り替えは lg（992px）。md（768px）にはしない").doesNotContain("col-md-");
			int right = body.indexOf("col-lg-6", body.indexOf("col-lg-6") + 1);
			int date = body.indexOf("id=\"transactionDate\"");
			int cat = body.indexOf("id=\"categoryId\"");
			int vend = body.indexOf("id=\"vendorId\"");
			int pay = body.indexOf("id=\"paymentMethodId\"");
			int amount = body.indexOf("id=\"amount\"");
			int buttons = body.indexOf("data-amount-buttons");
			int desc = body.indexOf("id=\"description\"");
			assertThat(date).isPositive().isLessThan(cat);
			assertThat(cat).isLessThan(vend);
			assertThat(vend).isLessThan(pay);
			assertThat(pay).as("左の列の終わりまでに支払方法がある").isLessThan(right);
			assertThat(right).as("右の列は、金額の前").isLessThan(amount);
			assertThat(amount).isLessThan(buttons);
			assertThat(buttons).as("金額ボタンは、金額の欄と摘要の間").isLessThan(desc);
			assertThat(body.indexOf("<button type=\"submit\"", formStart(body))).as("送信ボタンは、2 列のあと").isGreaterThan(desc);
		}
	}

	@Test
	void amountInputHasTheBigClassAndKeepsTheAttributesTheOtherTestsCheck() throws Exception {
		String body = editPage();
		assertThat(body).containsPattern("<input class=\"form-control amount-input\" type=\"text\" inputmode=\"numeric\"");
		assertThat(body).contains("name=\"amount\" value=\"1200\"");
	}

	@Test
	void mainUsesTheWideFormClassInsteadOfNarrow() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).contains("<main class=\"container form-wide\"").doesNotContain("container narrow");
		}
		assertThat(page("/categories")).as("ほかの画面は変えない").doesNotContain("form-wide");
	}

	@Test
	void formIsInsideACardAndNoticesAreOutsideTheColumns() throws Exception {
		String body = newPage();
		assertThat(body).containsPattern("<form [^>]*class=\"form-card\"");
		assertThat(formStart(body)).isLessThan(body.indexOf("<div class=\"row"));
	}

	@Test
	void copyNoticeIsAboveTheFormInFullWidth() throws Exception {
		String body = mockMvc.perform(get("/transactions/new").param("copyFrom", String.valueOf(transaction.getId())).with(asUser))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body.indexOf("この取引を写しています")).isLessThan(formStart(body));
		assertThat(body).contains("「保存」を押すまで、保存されません");
	}

	// ---- style.css ----

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則「" + selector + "」がある").isTrue();
		return m.group(1);
	}

	@Test
	void formWidthIs420pxAndBecomes960pxFrom992px() throws Exception {
		String css = css();
		assertThat(rule(css, "main.form-wide")).contains("max-width: 420px");
		Matcher media = Pattern.compile("@media \\(min-width: 992px\\)\\s*\\{\\s*main\\.form-wide\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(media.find()).as("992px 以上の規則").isTrue();
		assertThat(media.group(1)).contains("max-width: 960px");
		// 768px の規則は、トップの季節のメーター（issue 017）にある。取引の登録・編集画面の幅は、768px では切り替えない
		assertThat(css).as("768px では切り替えない").doesNotContainPattern("(?s)@media \\(min-width: 768px\\)\\s*\\{[^@]*?form-wide");
	}

	@Test
	void spaceAndRadiusVariablesHaveTheValuesOfTheDesign() throws Exception {
		String css = css();
		String root = rule(css, ":root");
		String[][] spaces = { { "1", "0.25rem" }, { "2", "0.5rem" }, { "3", "0.75rem" }, { "4", "1rem" }, { "5", "1.5rem" },
				{ "6", "2rem" }, { "7", "3rem" } };
		for (String[] s : spaces) {
			assertThat(root).contains("--hb-space-" + s[0] + ": " + s[1] + ";");
		}
		assertThat(root).contains("--hb-radius-control: 10px;", "--hb-radius-card: 16px;",
				"--bs-border-radius: var(--hb-radius-control);");
	}

	@Test
	void amountInputIsBigRightAlignedAndHasATealBorder() throws Exception {
		String r = rule(css(), ".amount-input");
		assertThat(r).contains("border: 2px solid var(--hb-teal)", "text-align: right", "font-size: var(--hb-font-size-h1)");
		assertThat(r).as("文字の大きさは rem の変数。px を書かない").doesNotContainPattern("font-size:\\s*[0-9.]+px");
	}

	@Test
	void cardUsesTheDesignedColorsAndRadius() throws Exception {
		String css = css();
		assertThat(rule(css, ".form-card")).contains("background-color: var(--hb-input-bg)", "border-radius: var(--hb-radius-card)", "var(--hb-line)");
	}

	/** issue 017：「必須」「任意・30 文字以内」は、薄いターコイズのチップをやめて、灰色の小さな文字（補足の文字の色）だけ。背景も枠もなし */
	@Test
	void marksAreSmallGrayTextOnly() throws Exception {
		String css = stripComments(css());
		String mark = rule(css, ".field-chip");

		assertThat(mark).contains("color: var(--hb-text-sub)", "font-size: var(--hb-font-size-small)", "font-weight: 400");
		assertThat(mark).doesNotContain("background").doesNotContain("border").doesNotContain("padding").doesNotContain("teal");
		assertThat(css).as("チップごとの規則（薄いターコイズの地、枠）を作らない").doesNotContain(".field-chip-required").doesNotContain(".field-chip-optional");
		assertThat(css).as("「必須」の印に赤を使わない（赤は意味の色）").doesNotContainPattern("field-chip[^{]*\\{[^}]*(danger|c62828)");
	}

	/** issue 017：項目のラベルは太字（700）。この画面のフォームのラベルだけ。ボタンの文字は 400 のまま */
	@Test
	void labelsOfTheFormAreBold() throws Exception {
		String css = stripComments(css());

		assertThat(rule(css, ".form-card .form-label")).contains("font-weight: 700");
		assertThat(css).containsPattern("\\.btn\\s*\\{[^}]*--bs-btn-font-weight:\\s*400");
	}

	/** issue 017：日付の欄は、992px 以上のとき、列の半分（最大 220px）。それより小さいときは今のまま（規則は 992px 以上の中にだけある） */
	@Test
	void dateFieldIsHalfTheColumnUpTo220pxFrom992px() throws Exception {
		String css = stripComments(css());

		Matcher media = Pattern.compile("@media \\(min-width: 992px\\)\\s*\\{[^@]*?\\.date-input\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(media.find()).as("992px 以上の規則").isTrue();
		assertThat(media.group(1)).contains("width: 50%", "max-width: 220px");
		assertThat(count(css, ".date-input")).as("992px 以上の中にだけある").isEqualTo(1);
	}

	/** issue 017：992px 以上で、左の列の項目の間隔を広げる（左の列の終わりが、右の列の摘要の下より少し下に来るように） */
	@Test
	void leftColumnFieldsAreSpacedWiderFrom992px() throws Exception {
		String css = stripComments(css());

		Matcher media = Pattern.compile("@media \\(min-width: 992px\\)\\s*\\{[^@]*?\\.form-col-left \\.field\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(media.find()).as("992px 以上の規則").isTrue();
		assertThat(media.group(1)).contains("margin-bottom: var(--hb-space-6)");
		assertThat(css).as("1 列のときの間隔（.field の 1rem）は変えない").containsPattern("\\.field\\s*\\{\\s*margin-bottom: 1rem;");
	}

	private static String stripComments(String css) {
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}
}
