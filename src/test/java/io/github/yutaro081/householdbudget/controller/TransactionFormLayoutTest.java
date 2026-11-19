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
 * 取引の登録・編集画面の作り（設計書 2.3、4.4.2）の、HTML と CSS の確認。issue 032 で、2 列〔issue 015 の 3 回目〕をやめ、
 * 上に共通の項目〔日付・取引先・支払方法〕、その下に明細の行〔科目・金額・摘要。登録は 5 行、編集は 1 行〕、いちばん下に明細の合計、の並びにした。
 * 確かめるのは、HTML の文字列〔項目の順、ラベルの横の印、右下のボタン、上の戻るリンク〕と、style.css の規則。
 * 実際の見た目〔項目の幅、金額ボタンの折り返し、重く見えないか〕は、MockMvc では確かめられないので、ブラウザで確かめる。
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
				.param("transactionDate", "2026-10-20").param("lines[0].amount", "abc")
				.param("lines[0].categoryId", String.valueOf(category.getId())).param("lines[0].description", "昼食")
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
		assertThat(body).contains("← 取引一覧に戻る", "先に科目を追加してください").doesNotContain("キャンセル", "name=\"lines[0].amount\"");
	}

	// ---- ラベルの横の印 ----

	/** 必須の印：共通の 3 つ（日付・取引先・支払方法）＋ 行ごとに 2 つ（科目・金額）。任意の印：行ごとに 1 つ（摘要）。登録は 5 行、編集は 1 行 */
	@Test
	void requiredAndOptionalMarksAreThreeCommonPlusTwoPerLineAndNoHtmlRestrictions() throws Exception {
		String[][] screens = { { newPage(), "5" }, { editPage(), "5" }, { errorPage(), "5" } };
		for (String[] screen : screens) {
			String body = screen[0];
			int lines = Integer.parseInt(screen[1]);
			assertThat(count(body, "field-chip-required\">必須</span>")).as("必須の印").isEqualTo(3 + 2 * lines);
			assertThat(count(body, "field-chip-optional\">任意・30 文字以内</span>")).as("摘要の印").isEqualTo(lines);
			assertThat(body).as("印は表示だけ（G1）：required と maxlength は付けない").doesNotContainPattern("\\srequired[\\s=>]").doesNotContain("maxlength");
			assertThat(body).as("ラベルの文から、補足を外した").doesNotContain("（任意。30文字以内）", "金額（円。1〜1000000の整数）");
		}
	}

	/** issue 017：金額のラベルは「金額（円）」 */
	@Test
	void amountLabelHasTheYenInParentheses() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).containsPattern("<label[^>]*for=\"lines0.amount\"[^>]*>金額（円） <span class=\"field-chip field-chip-required\">必須</span>");
		}
	}

	/** issue 032：2 列の目印（col-lg-6、form-col-left、date-input）は、もう使わない */
	@Test
	void theTwoColumnHooksAreGone() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).doesNotContain("col-lg-6", "form-col-left", "date-input");
		}
	}

	@Test
	void marksAreInsideTheLabelsOfTheirFields() throws Exception {
		String body = newPage();
		for (String id : new String[] { "transactionDate", "vendorId", "paymentMethodId", "lines0.categoryId", "lines0.amount", "lines4.categoryId", "lines4.amount" }) {
			assertThat(body).as(id).containsPattern("<label[^>]*for=\"" + id + "\"[^>]*>[^<]*<span class=\"field-chip field-chip-required\">必須</span>");
		}
		for (String id : new String[] { "lines0.description", "lines4.description" }) {
			assertThat(body).as(id).containsPattern("<label[^>]*for=\"" + id + "\"[^>]*>[^<]*<span class=\"field-chip field-chip-optional\">任意・30 文字以内</span>");
		}
	}

	@Test
	void amountRangeHintIsBelowTheAmountFieldWithTheSameWords() throws Exception {
		String body = newPage();
		assertThat(body).contains("1〜1,000,000 の整数");
		// issue 017：ラベルが「金額（円）」になったので、補足の頭の「円。」はなくした。カンマ付き
		assertThat(body).doesNotContain("円。1〜1000000の整数", "円。1〜");
		assertThat(count(body, "1〜1,000,000 の整数")).as("金額の欄ごとに 1 つ").isEqualTo(5);
		assertThat(body.indexOf("id=\"lines0.amount\"")).isLessThan(body.indexOf("1〜1,000,000 の整数"));
		assertThat(body.indexOf("1〜1,000,000 の整数")).as("金額ボタンの前").isLessThan(body.indexOf("data-amount-buttons"));
		assertThat(count(newPage(), "class=\"field-error\"")).as("エラーがない画面に field-error はない").isZero();
	}

	// ---- 並び（HTML の順 = Tab の順。issue 032） ----

	/**
	 * 上に共通の項目（日付 → 取引先 → 支払方法）、その下に明細の行（行ごとに 科目 → 金額 → 金額ボタン → 摘要）、
	 * そのあと「行を足す」（登録だけ）と明細の合計、最後に「キャンセル」「保存」。HTML の順が Tab で進む順
	 */
	@Test
	void commonFieldsComeFirstThenEachLineThenTheTotalThenTheButtons() throws Exception {
		String[][] screens = { { newPage(), "5" }, { editPage(), "5" }, { errorPage(), "5" } };
		for (String[] screen : screens) {
			String body = screen[0];
			int lines = Integer.parseInt(screen[1]);
			int date = body.indexOf("id=\"transactionDate\"");
			int vend = body.indexOf("id=\"vendorId\"");
			int pay = body.indexOf("id=\"paymentMethodId\"");
			assertThat(date).isPositive().isLessThan(vend);
			assertThat(vend).isLessThan(pay);
			int explain = body.indexOf("data-amount-explain");
			assertThat(explain).as("金額ボタンの説明は、明細の上に 1 回だけ（issue 032）").isGreaterThan(pay);
			int previous = explain;
			for (int i = 0; i < lines; i++) {
				int cat = body.indexOf("id=\"lines" + i + ".categoryId\"");
				int amount = body.indexOf("id=\"lines" + i + ".amount\"");
				int buttons = body.indexOf("data-amount-target=\"lines" + i + ".amount\"");
				int desc = body.indexOf("id=\"lines" + i + ".description\"");
				assertThat(cat).as((i + 1) + " 行目の科目は、前の項目のあと").isGreaterThan(previous);
				assertThat(amount).isGreaterThan(cat);
				assertThat(buttons).as("金額ボタンは、金額の欄と摘要の間").isGreaterThan(amount).isLessThan(desc);
				previous = desc;
			}
			assertThat(body.indexOf("id=\"lines" + lines + ".categoryId\"")).as("行は " + lines + " つ").isNegative();
			int total = body.indexOf("data-split-total-box");
			assertThat(total).as("明細の合計は、最後の行のあと").isGreaterThan(previous);
			assertThat(body.indexOf("<button type=\"submit\"", formStart(body))).as("送信ボタンは、合計のあと").isGreaterThan(total);
		}
	}

	/** 明細の行は、行ごとに fieldset と legend（「明細 1」…）でまとめる（どの行の科目・金額かが、読み上げでも分かる） */
	@Test
	void eachLineIsAFieldsetWithANumberedLegend() throws Exception {
		String body = newPage();
		for (int i = 1; i <= 5; i++) {
			assertThat(body).containsPattern("<fieldset[^>]*data-split-line[^>]*>\\s*<legend[^>]*>明細 " + i + "</legend>");
		}
		assertThat(count(body, "<fieldset")).isEqualTo(5);
		assertThat(count(editPage(), "<fieldset")).isEqualTo(5);
	}

	/** 「行を足す」は、登録・編集の画面の両方で、最後の行のあと、合計の前（issue 032 の段階 3 で、編集の画面にも出す） */
	@Test
	void theAddButtonIsBetweenTheLastLineAndTheTotal() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			int add = body.indexOf("data-split-add");
			assertThat(add).isGreaterThan(body.indexOf("id=\"lines4.description\"")).isLessThan(body.indexOf("data-split-total-box"));
		}
	}

	@Test
	void amountInputHasTheBigClassAndKeepsTheAttributesTheOtherTestsCheck() throws Exception {
		String body = editPage();
		assertThat(body).containsPattern("<input class=\"form-control amount-input\" type=\"text\" inputmode=\"numeric\"");
		assertThat(body).contains("name=\"lines[0].amount\" value=\"1200\"");
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
		assertThat(r).contains("border: 2px solid var(--hb-teal)", "text-align: right", "font-size: var(--hb-font-size-h2)", "font-weight: 700");   // issue 025：28px から 20px の太字に
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

	/** issue 032：2 列の規則（日付の欄を半分にする、左の列の間隔）は、なくした */
	@Test
	void theTwoColumnRulesAreGone() throws Exception {
		String css = stripComments(css());
		assertThat(css).doesNotContain(".date-input", ".form-col-left");
	}

	/** issue 032：明細の行の区切りと、明細の合計の規則がある */
	@Test
	void theLineAndTotalRulesExist() throws Exception {
		String css = stripComments(css());
		assertThat(rule(css, ".split-line")).isNotBlank();
		assertThat(rule(css, ".split-total")).isNotBlank();
	}

	private static String stripComments(String css) {
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	// ---- issue 025 の段階 4：金額の欄と、金額ボタン、操作の行の CSS ----

	/** 金額の欄：数字は 20px の太字、右寄せ。枠は今の 2px のターコイズ */
	@Test
	void theAmountInputIs20pxBoldRightAlignedWithTheTealBorder() throws Exception {
		String r = rule(css(), ".amount-input");

		assertThat(r).contains("font-size: var(--hb-font-size-h2)", "font-weight: 700", "text-align: right", "border: 2px solid var(--hb-teal)");
		assertThat(r).doesNotContain("--hb-font-size-h1");
	}

	/** 「足す」「税込にする」のまとまり：外枠 1 つ（1px。枠は #56625F）、角丸、ボタンの間は線。4 段の大きさだけ */
	@Test
	void theGroupsAreOneConnectedShapeWithOneOuterBorderAndLinesBetween() throws Exception {
		String css = css();
		String group = rule(css, ".amount-group");

		assertThat(group).contains("display: inline-flex", "border: 1px solid var(--hb-text-sub)", "border-radius: var(--hb-radius-control)", "overflow: hidden",
				"background-color: var(--hb-input-bg)");
		String button = rule(css, ".amount-btn");
		assertThat(button).contains("border: 0", "font-size: var(--hb-font-size-body)", "color: var(--hb-text)");
		assertThat(rule(css, ".amount-btn + .amount-btn")).as("ボタンの間の線").contains("border-left: 1px solid var(--hb-text-sub)");
		assertThat(rule(css, ".amount-btn:disabled")).as("押せないとき（税込）：灰色の地と文字、押せない形のカーソル").contains("cursor: not-allowed", "color: var(--hb-text-sub)");
		assertThat(css).doesNotContain("btn-sm");
	}

	/**
	 * ラベル「足す」「税込にする」は 13px の灰色。説明は 13px。
	 * 「金額をクリア」は、row-btn（灰色の枠、13px）の見た目で、文字のリンクの見た目（枠なし・下線）をやめる。
	 * 8% との間は、まとまりの間（12px）＋ 左の 4px で 16px（押し間違いを防ぐため。issue 029）
	 */
	@Test
	void theLabelsAreSmallGrayTheClearIsAnOutlinedButton16pxAwayAndTheNoteIs13px() throws Exception {
		String css = css();

		assertThat(rule(css, ".amount-row-label")).contains("font-size: var(--hb-font-size-small)", "color: var(--hb-text-sub)");
		assertThat(rule(css, ".amount-row")).contains("gap: var(--hb-space-2) var(--hb-space-3)");
		String clear = rule(css, ".amount-clear");
		assertThat(clear).contains("margin-left: var(--hb-space-1)")
				.doesNotContain("border: 0", "background: none", "text-decoration: underline");
		assertThat(rule(css, ".row-btn")).contains("border: 1px solid var(--hb-text-sub)", "font-size: var(--hb-font-size-small)");
		assertThat(rule(css, ".amount-note")).contains("font-size: var(--hb-font-size-small)", "color: var(--hb-text-sub)");
	}

	/** 「キャンセル」「保存」の上に区切り線（1px、--hb-line） */
	@Test
	void aSeparatorLineIsAboveTheCancelAndSaveButtons() throws Exception {
		assertThat(rule(css(), ".form-actions")).contains("border-top: 1px solid var(--hb-line)", "padding-top: var(--hb-space-4)", "justify-content: flex-end");
	}
}
