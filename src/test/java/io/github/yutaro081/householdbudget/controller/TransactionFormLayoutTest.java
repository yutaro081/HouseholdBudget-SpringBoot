package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

	/**
	 * 上の「← 取引一覧に戻る」は、issue 035 で外した（同じ行き先へは、ヘッダーの「取引一覧」と右下の「キャンセル」で行けるため。
	 * 科目などの変更画面にもないので、そろう）。キャンセルはフォームの中、送信ボタンの前
	 */
	@Test
	void thereIsNoBackLinkAndCancelIsInsideTheFormBeforeTheSubmitButton() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			int form = formStart(body);
			int cancel = body.indexOf("キャンセル");
			int submit = body.indexOf("<button type=\"submit\"", form);
			assertThat(body).as("上の戻るリンクはない").doesNotContain("取引一覧に戻る", "back-link");
			assertThat(body).as("ヘッダーの「取引一覧」から戻れる").containsPattern("(?s)<a [^>]*href=\"/transactions\"[^>]*>(?:(?!</a>).)*取引一覧</a>");
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
		assertThat(count(formPart, "<button")).as("フォームの中は、送信ボタン 1 つのほかは、type=button のボタン（電卓・行を足す・行を消す）だけ").isEqualTo(count(formPart, "type=\"button\"") + 1);
	}

	/** 科目などが空でフォームを出さないときも、ヘッダーの「取引一覧」から戻れる（上の戻るリンクは issue 035 で外した）。.back-link の CSS もなくした */
	@Test
	void whenTheMastersAreEmptyAndNoFormIsShownTheHeaderStillLeadsBackToTheList() throws Exception {
		Long id = userRepository.save(new User("formlay2", "dummy-hash-for-test")).getId();
		String body = mockMvc.perform(get("/transactions/new").with(user(new LoginUser(id, "formlay2", "x"))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(body).contains("先に科目を追加してください").doesNotContain("キャンセル", "name=\"lines[0].amount\"", "取引一覧に戻る");
		assertThat(body).containsPattern("(?s)<a [^>]*href=\"/transactions\"[^>]*>(?:(?!</a>).)*取引一覧</a>");
		assertThat(css()).doesNotContain(".back-link");
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

	/**
	 * 金額の欄の下の範囲の補足（「1〜1,000,000 の整数」。issue 017）は、issue 035 で外した（欄に式も打てるので「整数」が合わない。
	 * 上限は保存のときのエラーで分かる。5 行ぶん同じ文がくり返し出ていた）。.field-hint の CSS も外した。範囲のエラーの文は変えない（MessagesTest）
	 */
	@Test
	void theAmountRangeHintIsGoneFromEveryLine() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			assertThat(body).doesNotContain("1〜1,000,000 の整数", "1〜1000000", "field-hint");
		}
		assertThat(css()).doesNotContain(".field-hint");
		assertThat(count(newPage(), "class=\"field-error\"")).as("エラーがない画面に field-error はない").isZero();
	}

	// ---- 並び（HTML の順 = Tab の順。issue 032） ----

	/**
	 * 上に共通の項目（日付 → 取引先 → 支払方法）、その下に明細の行（行ごとに 科目 → 金額 → 計算できないときの文 → 摘要）、
	 * そのあと「行を足す」と電卓（issue 035。明細の右の列）、明細の合計、最後に「キャンセル」「保存」。HTML の順が Tab で進む順
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
			int previous = pay;
			for (int i = 0; i < lines; i++) {
				int cat = body.indexOf("id=\"lines" + i + ".categoryId\"");
				int amount = body.indexOf("id=\"lines" + i + ".amount\"");
				int calcError = body.indexOf("data-calc-target=\"lines" + i + ".amount\"");
				int desc = body.indexOf("id=\"lines" + i + ".description\"");
				assertThat(cat).as((i + 1) + " 行目の科目は、前の項目のあと").isGreaterThan(previous);
				assertThat(amount).isGreaterThan(cat);
				assertThat(calcError).as("計算できないときの文は、金額の欄と摘要の間").isGreaterThan(amount).isLessThan(desc);
				previous = desc;
			}
			assertThat(body.indexOf("id=\"lines" + lines + ".categoryId\"")).as("行は " + lines + " つ").isNegative();
			int calc = body.indexOf("<div class=\"calc\" data-calc");
			assertThat(calc).as("電卓は、はじめは明細 1 の金額の欄と摘要の間（issue 035 の段階 3-2。選んだ行へ JavaScript が移す）")
					.isGreaterThan(body.indexOf("id=\"lines0.amount\"")).isLessThan(body.indexOf("id=\"lines0.description\""));
			int total = body.indexOf("data-split-total-box");
			assertThat(total).as("明細の合計は、最後の行と「行を足す」のあと").isGreaterThan(previous).isGreaterThan(body.indexOf("data-split-add"));
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

	/**
	 * 電卓のボタン（issue 035。issue 009 の金額ボタンの見た目を引きつぐ）：4 列の格子。白い地、灰色の枠（#56625F）、角丸。
	 * 数字・記号は 20px、C と税込は 16px（4 段の大きさだけ）。押せないとき（税込）は、灰色の地と文字、押せない形のカーソル。新しい色は使わない
	 */
	@Test
	void theCalculatorKeysAreAGridOfOutlinedButtonsWithTheExistingColors() throws Exception {
		String css = css();
		assertThat(rule(css, ".calc-keys")).contains("display: grid", "grid-template-columns: repeat(4, 1fr)", "gap: var(--hb-calc-gap)");
		assertThat(rule(css, ".calc-key")).as("ボタンの高さは変数（右に出すときのずらす量の計算にも使う）").contains("height: var(--hb-calc-key-height)");
		assertThat(rule(css, ".calc-tax")).contains("display: grid", "grid-template-columns: repeat(2, 1fr)");
		String key = rule(css, ".calc-key");
		assertThat(key).contains("border: 1px solid var(--hb-text-sub)", "border-radius: var(--hb-radius-control)", "background-color: var(--hb-input-bg)",
				"color: var(--hb-text)", "font-size: var(--hb-font-size-h2)");
		assertThat(key).doesNotContainPattern("#[0-9A-Fa-f]{3,6}");
		assertThat(rule(css, ".calc-key-small")).contains("font-size: var(--hb-font-size-body)");
		assertThat(rule(css, ".calc-key:hover:not(:disabled)")).contains("background-color: var(--hb-row-hover)");
		assertThat(rule(css, ".calc-key:disabled")).as("押せないとき（税込）").contains("background-color: var(--hb-bg)", "color: var(--hb-text-sub)", "cursor: not-allowed");
		assertThat(css).as("行ごとの金額ボタンの決まりは、なくした").doesNotContain(".amount-btn", ".amount-group", ".amount-row", ".amount-clear", ".amount-buttons");
	}

	/**
	 * 電卓の入れ物（issue 035 の段階 3-2）：金額の欄の入れ物（.calc-place。position: relative）の下の端に、浮かせて重ねる（position: absolute、top: 100%、left: 0、
	 * z-index は「設定」のメニューと同じ 10）。並びは動かない。白い地、細い線の枠と角丸。幅は 18rem まで、入れ物の幅を超えない（max-width: 100%。約 375px でも横にはみ出さない）。
	 * 右の列の sticky はやめた。電卓の中の文（押せないとき・説明）は 13px の補足の色
	 */
	@Test
	void theCalculatorFloatsBelowTheAmountFieldWithinItsPlaceAndItsNotesAre13pxGray() throws Exception {
		String css = css();
		assertThat(rule(css, ".calc-place")).contains("position: relative");
		String calc = rule(css, ".calc");
		assertThat(calc).contains("position: absolute", "top: 100%", "left: 0", "z-index: 30", "width: 18rem", "max-width: 100%",
				"background-color: var(--hb-input-bg)", "border: 1px solid var(--hb-line)", "border-radius: var(--hb-radius-card)", "padding: var(--hb-space-4)");
		assertThat(css).as("右の列の sticky はやめた").doesNotContainPattern("\\.calc \\{[^}]*sticky");
		assertThat(rule(css, ".calc-note")).contains("font-size: var(--hb-font-size-small)", "color: var(--hb-text-sub)");
	}

	/**
	 * 992px 以上では、電卓は金額の欄のすぐ右（入れ物の右の端から 8px。「1 2 3 ＋」の段の中心を、欄の中心にそろえる。段階 3-2 の直し（3））。
	 * 992px 未満は、上の規則のまま、すぐ下（段階 3-2 の直し。本人が使ってみて、右のほうが打ちやすいため）
	 */
	@Test
	void from992pxTheCalculatorIsRightOfTheAmountFieldWithItsThirdRowBesideTheField() throws Exception {
		Matcher media = Pattern.compile("(?s)@media \\(min-width: 992px\\) \\{\\s*\\.calc \\{([^}]*)\\}").matcher(css());
		assertThat(media.find()).as("992px 以上の .calc の規則").isTrue();
		assertThat(media.group(1)).contains("left: 100%", "margin-left: var(--hb-space-2)");
		// 段階 3-2 の直し（3）：「1 2 3 ＋」の段（上から 3 段目）の上下の中心を、金額の欄の上下の中心にそろえる。ずらす量は決め打ちにせず、変数から calc() で求める
		assertThat(media.group(1)).doesNotContain("top: 2rem").containsPattern("top: calc\\(var\\(--hb-amount-field-top\\) \\+ var\\(--hb-amount-input-height\\) / 2 - .*\\);");
		Matcher top = Pattern.compile("top: (calc\\(.*\\));").matcher(media.group(1));
		assertThat(top.find()).isTrue();
		assertThat(top.group(1)).as("ボタンの高さ・すき間・電卓の内側の余白から計算する（2 段分＋半段）")
				.contains("2 * (var(--hb-calc-key-height) + var(--hb-calc-gap))", "var(--hb-calc-key-height) / 2", "var(--hb-space-4)");
		String place = rule(css(), ".calc-place");
		assertThat(place).as("変数は、金額の欄の入れ物に置く（電卓が引きつぐ）")
				.contains("--hb-calc-key-height:", "--hb-calc-gap: var(--hb-space-2)", "--hb-amount-field-top:", "--hb-amount-input-height:");
	}

	/** 金額の欄は、ブラウザの入力の候補（前に打った値の一覧）を出さない（全部の行。電卓と重なり、前の値は役に立たないため。段階 3-2 の直し） */
	@Test
	void everyAmountFieldTurnsOffTheBrowsersSuggestions() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			for (int i = 0; i < 5; i++) {
				Matcher input = Pattern.compile("<input [^>]*id=\"lines" + i + "\\.amount\"[^>]*>").matcher(body);
				assertThat(input.find()).isTrue();
				assertThat(input.group()).as((i + 1) + " 行目の金額の欄").contains("autocomplete=\"off\"");
			}
			assertThat(count(body, "autocomplete=\"off\"")).as("金額の欄の 5 つだけ（ほかの欄は変えない）").isEqualTo(5);
		}
	}

	/**
	 * issue 035 の段階 3-3：明細の合計・キャンセル・保存は、画面の下に固定する帯（form-bar。この画面だけのクラス）の中。
	 * 帯はフォームの最後。position: sticky で bottom: 0。段階 3-3 の直し：地は白、上の端に区切りの線とうすい影（本文の色を透かしたもの）、文字は本文の色、
	 * 中は右寄せ（合計 → キャンセル → 保存）。保存とキャンセルは、ほかの画面と同じふつうの見た目（帯だけの色の上書きはしない）。
	 * 科目などの変更画面のための .form-actions（区切り線の下に右寄せ）は残す
	 */
	@Test
	void theTotalCancelAndSaveAreInTheStickyBarAtTheBottomOfTheForm() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			int bar = body.indexOf("<div class=\"form-bar\">");
			assertThat(bar).as("帯がある").isPositive().isGreaterThan(body.indexOf("data-split-add"));
			assertThat(body.indexOf("data-split-total-box")).as("合計は帯の中").isGreaterThan(bar);
			assertThat(body.indexOf(">キャンセル</a>")).isGreaterThan(body.indexOf("data-split-total-box"));
			assertThat(body.indexOf("<button type=\"submit\"", bar)).isGreaterThan(body.indexOf(">キャンセル</a>"));
			assertThat(body.indexOf("</form>", bar)).as("帯はフォームの中（ヘッダーのログアウトのフォームは、帯より前）").isGreaterThan(body.indexOf("<button type=\"submit\"", bar));
			assertThat(body).as("この画面では .form-actions を使わない").doesNotContain("class=\"form-actions\"");
		}
		String css = css();
		String bar = rule(css, ".form-bar");
		assertThat(bar).contains("position: sticky", "bottom: 0", "z-index: 20", "background-color: var(--hb-input-bg)", "color: var(--hb-text)",
				"border-top: 1px solid var(--hb-line)", "box-shadow: 0 -2px 8px rgba(36, 48, 47, 0.08)",
				"min-height: var(--hb-form-bar-height)");
		assertThat(bar).doesNotContainPattern("#[0-9A-Fa-f]{3,6}");
		assertThat(css).as("保存とキャンセルは、ほかの画面と同じふつうの見た目（帯だけの上書きをやめた）")
				.doesNotContain(".form-bar .btn-primary", ".form-bar .btn-outline-secondary");
		assertThat(rule(css, ".form-actions")).as("科目などの変更画面のために残す").contains("border-top: 1px solid var(--hb-line)", "justify-content: flex-end");
	}

	/**
	 * 帯の外側に当たる要素（html、body、登録画面の main〔container・form-wide〕、フォームの白いカード〔form-card〕）の規則に、
	 * はみ出しの指定（overflow）がないこと。外側に overflow があると、sticky の帯が画面の下に貼り付かなくなるため（段階 3-3）。
	 * style.css 全体は禁止しない（帯と関係ない所、たとえば表の overflow-x は足してよい）。992px 以上などの @media の中の規則も見る
	 */
	@Test
	void theElementsAroundTheBarHaveNoOverflowRule() throws Exception {
		String css = stripComments(css());
		List<String> around = List.of("html", "body", "main", "main.container", "main.form-wide", "form", ".form-card", "html:has(.form-bar)");
		Matcher block = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(css);
		List<String> found = new java.util.ArrayList<>();
		int checked = 0;
		while (block.find()) {
			for (String selector : block.group(1).split(",")) {
				if (around.contains(selector.strip())) {
					checked++;
					if (block.group(2).contains("overflow")) {
						found.add(selector.strip());
					}
				}
			}
		}
		assertThat(checked).as("外側の要素の規則が見つかる（検索の形が合っている）").isGreaterThanOrEqualTo(4);
		assertThat(found).as("帯の外側の要素に overflow の指定がある").isEmpty();
	}

	/** 帯の高さの変数を、この画面だけページに置き、Tab で移った欄（scroll-padding-bottom）と電卓（scroll-margin-bottom）が帯の下に隠れないようにする */
	@Test
	void theBarDoesNotHideTheFocusedFieldOrTheCalculator() throws Exception {
		String css = css();
		Matcher page = Pattern.compile("(?m)^html:has\\(\\.form-bar\\)\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(page.find()).as("この画面だけの規則").isTrue();
		assertThat(page.group(1)).contains("--hb-form-bar-height:", "scroll-padding-bottom: var(--hb-form-bar-height)");
		assertThat(rule(css, ".calc")).contains("scroll-margin-bottom: var(--hb-form-bar-height)");
		// 段階 3-3 の直し：電卓は帯より上（手前）。画面が短いと、下に伸びた電卓が帯の下に隠れたため
		assertThat(zIndex(rule(css, ".calc"))).as("電卓の z-index は、帯より大きい").isGreaterThan(zIndex(rule(css, ".form-bar")));
	}

	private static int zIndex(String rule) {
		Matcher m = Pattern.compile("z-index:\\s*(\\d+)").matcher(rule);
		assertThat(m.find()).as("z-index がある").isTrue();
		return Integer.parseInt(m.group(1));
	}

	/** 数字でない欄があるときの文は、帯を 1 行に保つため短くした（「（数字でない欄を除く）」）。JavaScript が見せる・隠すだけ */
	@Test
	void theNotANumberNoteIsShortInTheBar() throws Exception {
		String body = newPage();
		assertThat(body).contains("<span class=\"split-total-note\" data-split-not-number hidden>（数字でない欄を除く）</span>")
				.doesNotContain("合計に入れていません");
		assertThat(rule(css(), ".split-total-note")).as("補足の色（白い帯の上。段階 3-3 の直し）").contains("color: var(--hb-text-sub)", "font-size: var(--hb-font-size-small)");
	}

	/**
	 * 明細の合計を目立たせる（段階 3-3 の直し（2））：金額の数字は 20px の太字（4 段の 2 番目）。「明細の合計」の言葉は 16px のまま（数字の strong だけを大きくする）。
	 * 合計とキャンセルの間は 32px（--hb-space-6。前の倍）にして、合計と操作のボタンを別のまとまりに見せる
	 */
	@Test
	void theTotalAmountIs20pxBoldAndStandsApartFromTheButtons() throws Exception {
		String css = css();
		assertThat(rule(css, ".split-total strong")).contains("font-size: var(--hb-font-size-h2)", "font-weight: 700");
		assertThat(rule(css, ".split-total")).as("言葉は本文の大きさのまま（大きさを変えない）").doesNotContain("font-size");
		// 合計とキャンセルの間（992px 未満で 32px）は、帯の中の列の余白で作る（段階 3-3 の直し（4）。from992px… のテストが見る）
		assertThat(newPage()).as("数字と「円」は strong の中").contains("明細の合計 <strong><span data-split-total>0</span>円</strong>");
	}

	/**
	 * 576px 未満では、帯をはじめから 2 行にする（上の行に合計、下の行にボタン。どちらも右寄せ）。段階 3-3 の直し（3）。
	 * 合計が見えているときだけ、帯の高さの変数を 2 行の高さ（6.5rem）にする（電卓とページの余白も、この変数を使う）。
	 * 合計が hidden（JavaScript が無効）のときは、ボタンの行だけで、高さは 4rem のまま（空の行を作らない）
	 */
	@Test
	void belowSmTheBarIsAlwaysTwoRowsWhenTheTotalIsShown() throws Exception {
		Matcher narrow = Pattern.compile("(?s)@media \\(max-width: 575\\.98px\\) \\{\\s*(html:has\\(\\.form-bar \\.split-total:not\\(\\[hidden\\]\\)\\) \\{[^}]*\\}\\s*\\.form-bar-total \\{[^}]*\\})\\s*\\}")
				.matcher(css());
		assertThat(narrow.find()).as("576px 未満の帯の規則").isTrue();
		assertThat(narrow.group(1)).as("合計の列は幅いっぱい（1 行目）").contains("--hb-form-bar-height: 6.5rem", "width: 100%", "margin-right: 0");
		assertThat(newPage()).as("2 行の間は 8px（gy-2）。6.5rem の高さの計算と同じ").contains("<div class=\"row gx-3 gy-2 form-bar-row\">");
		Matcher base = Pattern.compile("(?m)^html:has\\(\\.form-bar\\)\\s*\\{([^}]*)\\}").matcher(css());
		assertThat(base.find()).isTrue();
		assertThat(base.group(1)).as("ふだん（576px 以上、または合計が出ないとき）は 1 行の高さ").contains("--hb-form-bar-height: 4rem");
	}

	/**
	 * 992px 以上では、帯の中を明細の行と同じ列の分け方にし、明細の合計を金額の列（2 列目）に右寄せで入れる（段階 3-3 の直し（4））。
	 * 合計の数字の右の端が、金額の欄の右の端にそろう（電卓は金額の欄の右に出るので、帯の右側の合計が隠れていたため）。キャンセルと保存は 3 列目の右寄せ。
	 * 横の間は、明細の行（g-3）と同じ 1rem（gx-3）。帯の内側の左右の余白は、カードの内側の余白と同じ（992px 以上は --hb-space-5）。
	 * 992px 未満は、列の幅を中身の幅に戻して、今までの右寄せの並び。JavaScript が無効で合計が出ないときは、空の合計の列を出さない
	 */
	@Test
	void from992pxTheTotalSitsUnderTheAmountColumnAndBelowItTheOldOrderStays() throws Exception {
		for (String body : new String[] { newPage(), editPage(), errorPage() }) {
			int bar = body.indexOf("<div class=\"form-bar\">");
			Matcher row = Pattern.compile("(?s)<div class=\"form-bar\">\\s*<div class=\"row gx-3 gy-2 form-bar-row\">\\s*"
					+ "<div class=\"col-lg-4 offset-lg-4 form-bar-total\">\\s*<p class=\"split-total\" data-split-total-box hidden>.*?</p>\\s*</div>\\s*"
					+ "<div class=\"col-lg-4 form-bar-actions\">\\s*<a [^>]*>キャンセル</a>\\s*<button type=\"submit\"[^>]*>保存</button>\\s*</div>").matcher(body.substring(bar));
			assertThat(row.find()).as("帯の中の列：合計は 2 列目、ボタンは 3 列目").isTrue();
			assertThat(body).as("明細の行の列の分け方（横の間 1rem の g-3）").contains("<div class=\"row g-3\">");
		}
		String css = css();
		assertThat(rule(css, ".form-bar-total")).as("合計は列の中で右寄せ（数字の右の端を、金額の欄の右の端にそろえる）").contains("text-align: right");
		assertThat(rule(css, ".form-bar-actions")).contains("display: flex", "justify-content: flex-end");
		Matcher wide = Pattern.compile("(?s)@media \\(min-width: 992px\\) \\{\\s*\\.form-bar \\{([^}]*)\\}").matcher(css);
		assertThat(wide.find()).isTrue();
		assertThat(wide.group(1)).as("帯の内側の左右の余白は、カードの内側の余白（992px 以上は --hb-space-5）と同じ").contains("padding: var(--hb-space-3) var(--hb-space-5)");
		assertThat(rule(css, ".form-card")).contains("padding: var(--hb-space-4)");
		Matcher below = Pattern.compile("(?s)@media \\(max-width: 991\\.98px\\) \\{(.*?\\})\\s*\\}").matcher(css);
		boolean found = false;
		while (below.find()) {
			String block = below.group(1);
			if (block.contains(".form-bar-row")) {
				found = true;
				assertThat(block).as("992px 未満：列の幅を中身の幅に戻し、右寄せ。合計とボタンの間は 32px").contains("justify-content: flex-end", "width: auto",
						"margin-right: var(--hb-space-4)");
				assertThat(block).as("合計が hidden のときは、空の列を出さない").contains(".form-bar-total:has(> [hidden])", "display: none");
			}
		}
		assertThat(found).as("992px 未満の帯の規則").isTrue();
	}
}
