package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * 取引一覧の操作の列「編集・複写・削除」と、削除の確認（設計書 2.2・2.3、issue 025 の段階 3）。
 * 「詳細」のリンクはなくした（詳細ページそのものは、段階 7 まで残る）。
 * 削除の確認の文には、日付・科目名・金額が入る。科目名は利用者の入力なので、<code>onsubmit</code> の文字列には入れず、
 * HTML の属性 <code>data-confirm</code> に入れて、<code>confirm-submit.js</code> が読む。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionRowActionsTest {

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
	private Long otherId;
	private Vendor vendor;
	private PaymentMethod payment;
	private RequestPostProcessor asUser;
	private RequestPostProcessor asOther;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("rowact1", "dummy-hash-for-test")).getId();
		otherId = userRepository.save(new User("rowact2", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "コンビニ"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "現金"));
		asUser = user(new LoginUser(userId, "rowact1", "x"));
		asOther = user(new LoginUser(otherId, "rowact2", "x"));
	}

	private Transaction save(LocalDate date, String categoryName, int amount) {
		Category category = categoryRepository.save(new Category(userId, categoryName));
		return transactionRepository.save(new Transaction(userId, date, amount, category, null, vendor, payment));
	}

	private String list() throws Exception {
		entityManager.flush();
		entityManager.clear();
		String body = mockMvc.perform(get("/transactions").with(asUser)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return body.substring(body.indexOf("<main"), body.indexOf("</main>"));
	}

	/** 操作のセルの中身（取引の行の 7 つ目の <td>）。クラス名には頼らない */
	private static String actionCell(String main) {
		Matcher rows = Pattern.compile("(?s)<tr[^>]*>(.*?)</tr>").matcher(main);
		List<String> cells = new ArrayList<>();
		while (rows.find()) {
			Matcher td = Pattern.compile("(?s)<td[^>]*>(.*?)</td>").matcher(rows.group(1));
			List<String> texts = new ArrayList<>();
			while (td.find()) {
				texts.add(td.group(1));
			}
			if (texts.size() == 7) {
				cells.add(texts.get(6));
			}
		}
		assertThat(cells).as("取引の行が 1 つ").hasSize(1);
		return cells.get(0);
	}

	private static String unescape(String attribute) {
		return attribute.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
	}

	private static String confirmOf(String main) {
		Matcher m = Pattern.compile("<form[^>]*\\bdata-confirm=\"([^\"]*)\"[^>]*>").matcher(main);
		assertThat(m.find()).as("data-confirm がある").isTrue();
		return unescape(m.group(1));
	}

	// ---- 操作の列 ----

	/** 「編集」「複写」「削除」の順。「詳細」のリンクはない。見出しは「操作」で、右寄せの印がある。文字は、アイコンの svg を除くと、編集・複写・削除のまま */
	@Test
	void theActionCellHasEditCopyAndDeleteInThatOrderAndNoDetailLink() throws Exception {
		Transaction t = save(LocalDate.of(2026, 10, 19), "食費", 1500);
		String main = list();
		String cell = actionCell(main);

		assertThat(cell).containsPattern("(?s)<a [^>]*href=\"/transactions/" + t.getId() + "/edit\"[^>]*>(<svg.*?</svg>)?編集</a>.*<a [^>]*href=\"/transactions/new\\?copyFrom="
				+ t.getId() + "\"[^>]*>(<svg.*?</svg>)?複写</a>.*<button[^>]*>(<svg.*?</svg>)?削除</button>");
		assertThat(cell.replaceAll("(?s)<svg.*?</svg>", "").replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", "")).as("ボタンの言葉は変えない").isEqualTo("編集複写削除");
		assertThat(main).doesNotContain(">詳細<").doesNotContain("href=\"/transactions/" + t.getId() + "\"");
		assertThat(main).containsPattern("<th class=\"row-actions-head\">操作</th>");
	}

	/** 3 つのボタンに、小さなアイコン（えんぴつ・2 枚重ねの紙・ごみ箱。14px、飾り）と、title の説明 */
	@Test
	void theThreeButtonsHaveAnIconAndATitle() throws Exception {
		save(LocalDate.of(2026, 10, 19), "食費", 100);
		String cell = actionCell(list());

		Matcher a = Pattern.compile("(?s)<a [^>]*title=\"([^\"]*)\"[^>]*>(<svg.*?</svg>)").matcher(cell);
		List<String> titles = new ArrayList<>();
		List<String> svgs = new ArrayList<>();
		while (a.find()) {
			titles.add(a.group(1));
			svgs.add(a.group(2));
		}
		Matcher b = Pattern.compile("(?s)<button [^>]*title=\"([^\"]*)\"[^>]*>(<svg.*?</svg>)").matcher(cell);
		assertThat(b.find()).isTrue();
		titles.add(b.group(1));
		svgs.add(b.group(2));

		assertThat(titles).containsExactly("この取引を直す", "この取引を写して、新しく登録する", "この取引を削除する");
		assertThat(svgs).hasSize(3);
		for (String svg : svgs) {
			assertThat(svg).contains("aria-hidden=\"true\"", "width=\"14\"", "height=\"14\"", "stroke=\"currentColor\"");
		}
		assertThat(svgs.get(0)).as("編集：えんぴつ").contains("M14.5 6.5l3 3");
		assertThat(svgs.get(1)).as("複写：2 枚重ねの紙").contains("<rect x=\"8\" y=\"8\" width=\"12\" height=\"12\" rx=\"2\">");
		assertThat(svgs.get(2)).as("削除：ごみ箱").contains("M9 7V4h6v3");
	}

	/** 削除は <button type="submit"> のまま、POST。CSRF のトークンがフォームの中にある。onsubmit・onclick は使わない */
	@Test
	void deleteIsAPostFormWithTheCsrfTokenAndNoInlineScript() throws Exception {
		Transaction t = save(LocalDate.of(2026, 10, 19), "食費", 1500);
		String cell = actionCell(list());

		Matcher form = Pattern.compile("(?s)<form[^>]*action=\"/transactions/" + t.getId() + "/delete\"[^>]*>(.*?)</form>").matcher(cell);
		assertThat(form.find()).isTrue();
		assertThat(form.group()).contains("method=\"post\"").contains("data-confirm=\"").doesNotContain("onsubmit").doesNotContain("onclick");
		assertThat(form.group(1)).contains("name=\"_csrf\"").containsPattern("(?s)<button[^>]*type=\"submit\"[^>]*>(<svg.*?</svg>)?削除</button>");
		assertThat(cell).as("科目などの一覧の「削除の確認」は、今のまま（この画面には持ち込まない）").doesNotContain("return confirm(");
	}

	// ---- 削除の確認の文 ----

	@Test
	void theConfirmSentenceNamesTheDateTheCategoryAndTheAmount() throws Exception {
		save(LocalDate.of(2026, 11, 9), "水道光熱費", 1500);

		assertThat(confirmOf(list())).isEqualTo("2026/11/09 水道光熱費 1,500円 を削除します。よろしいですか？");
	}

	/** 科目名に ' や " や < や & があっても、属性が壊れず、無害化される。文は、そのままの文字で読める */
	@Test
	void quotesAndTagsInTheCategoryNameStayInsideTheAttribute() throws Exception {
		save(LocalDate.of(2026, 10, 19), "a'b\"c<d>&", 100);
		String main = list();

		assertThat(confirmOf(main)).isEqualTo("2026/10/19 a'b\"c<d>& 100円 を削除します。よろしいですか？");
		assertThat(main).as("生の < > が属性に入らない").doesNotContain("c<d>");
		assertThat(main).containsPattern("data-confirm=\"[^\"<>]*\"");
		assertThat(actionCell(main)).as("属性が壊れて、文字がセルに漏れていない").doesNotContain("d>&");
	}

	/** 日本語の科目名や、金額の桁区切り（100 万円）も正しい */
	@Test
	void theAmountHasCommas() throws Exception {
		save(LocalDate.of(2026, 1, 2), "旅行", 1_000_000);

		assertThat(confirmOf(list())).isEqualTo("2026/01/02 旅行 1,000,000円 を削除します。よろしいですか？");
	}

	// ---- confirm-submit.js ----

	private static String script() throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "static", "js", "confirm-submit.js"));
	}

	/** 一覧の画面で読み込む（1 回だけ）。JavaScript が無効のときは、何も起きない（確認なしで削除される。今と同じ） */
	@Test
	void theListLoadsTheConfirmScriptOnce() throws Exception {
		save(LocalDate.of(2026, 10, 19), "食費", 100);
		String html = mockMvc.perform(get("/transactions").with(asUser)).andReturn().getResponse().getContentAsString();

		assertThat(html.split("/js/confirm-submit.js", -1).length - 1).isEqualTo(1);
		assertThat(html).contains("<script defer src=\"/js/confirm-submit.js\"></script>");
	}

	/** submit で、属性 data-confirm を getAttribute で読み、window.confirm に渡す。「いいえ」なら送らない */
	@Test
	void theScriptReadsTheAttributeAndAsksOnSubmit() throws Exception {
		String script = script();

		assertThat(script).contains("addEventListener('submit'", "data-confirm", "getAttribute", "window.confirm(", "preventDefault()");
	}

	/** 決まり：eval・innerHTML・通信を使わない。文を JavaScript に書かない。利用者の入力を埋め込まない */
	@Test
	void theScriptKeepsTheRules() throws Exception {
		String code = script().replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");

		assertThat(code).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function", "textContent");
		assertThat(code).doesNotContain("fetch(", "XMLHttpRequest", "sendBeacon", "WebSocket");
		assertThat(script()).doesNotContain("を削除します");
	}

	// ---- 見た目（CSS） ----

	private static String css() throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css")).replace("\r\n", "\n")
				.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 「編集」「複写」「削除」は、小さい枠のボタン（13px、角丸、上下に少し余白、背景は白）。編集・複写は、枠 #56625F・文字 #24302F。削除は、枠と文字 #C62828。3 つの間は 8px（余白の目盛り）で、右端に詰める。1 行に収まる。アイコンは文字の左 */
	@Test
	void theRowActionsAreSmallOutlinedButtons() throws Exception {
		String css = css();

		assertThat(css).contains("--hb-text-sub: #56625F", "--hb-text: #24302F", "--hb-over: #C62828", "--hb-input-bg: #FFFFFF", "--hb-space-2: 0.5rem");
		String button = rule(css, ".row-btn");
		assertThat(button).contains("font-size: var(--hb-font-size-small)", "border: 1px solid var(--hb-text-sub)", "color: var(--hb-text)",
				"background-color: var(--hb-input-bg)", "border-radius: var(--hb-radius-control)", "padding: 0.25rem 0.75rem");
		assertThat(button).as("アイコンと文字を横に並べる（アイコンは文字の左）").contains("display: inline-flex", "align-items: center", "gap: var(--hb-space-1)");
		String danger = rule(css, ".row-btn-danger");
		assertThat(danger).contains("color: var(--hb-over)", "border-color: var(--hb-over)");
		String actions = rule(css, ".row-actions");
		assertThat(actions).as("間は 8px（余白の目盛り。目盛りの外の例外を増やさない）、右端に詰める、折り返さない")
				.contains("gap: var(--hb-space-2)", "justify-content: flex-end", "white-space: nowrap").doesNotContain("0.375rem");
		assertThat(rule(css, ".table-card th.row-actions-head")).as("見出し「操作」も右寄せ").contains("text-align: right");
		assertThat(css).as("文字だけの見た目（前の形）は、なくした").doesNotContain(".row-link", ".row-delete");
	}

	/** 取引一覧の編集・複写（a）と削除（button）に、同じボタンの印がある。削除だけ赤の印がある。Bootstrap の btn は使わない */
	@Test
	void theThreeActionsCarryTheButtonClasses() throws Exception {
		save(LocalDate.of(2026, 10, 19), "食費", 100);
		String cell = actionCell(list());

		assertThat(cell).containsPattern("<a class=\"row-btn\" title=\"[^\"]*\" href=\"/transactions/\\d+/edit\">");
		assertThat(cell).containsPattern("<a class=\"row-btn\" title=\"[^\"]*\" href=\"/transactions/new\\?copyFrom=\\d+\">");
		assertThat(cell).containsPattern("<button type=\"submit\" class=\"row-btn row-btn-danger\" title=\"[^\"]*\">");
		assertThat(cell).doesNotContain("class=\"btn");
	}

	/** 文字の色が、白の上・行にマウスを載せた色（#EEF3F3）の上で、4.5 対 1 以上 */
	@Test
	void theButtonTextStaysReadableOnWhiteAndOnTheRowHoverColor() {
		assertThat(ratio("#24302F", "#FFFFFF")).isGreaterThanOrEqualTo(4.5);
		assertThat(ratio("#C62828", "#FFFFFF")).isGreaterThanOrEqualTo(4.5);
		assertThat(ratio("#C62828", "#EEF3F3")).isGreaterThanOrEqualTo(4.5);
	}

	private static double luminance(String hex) {
		double[] c = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
			c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	private static double ratio(String a, String b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	// ---- 守り：編集・複写・削除の、他人・存在しない・数字でない ID は、同じ 404 ----

	private String notFoundBody(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isNotFound()).andExpect(view().name("error/404")).andReturn().getResponse()
				.getContentAsString();
	}

	/** 編集（GET）・複写（GET の copyFrom）・削除（POST）のそれぞれで、他人の取引・存在しない ID・数字でない ID は、同じ 404 の画面。他人の取引は残る */
	@Test
	void editCopyAndDeleteOfOtherUsersMissingAndNonNumericIdsAreAllTheSame404() throws Exception {
		Transaction mine = save(LocalDate.of(2026, 10, 19), "食費", 100);
		entityManager.flush();
		entityManager.clear();
		String mineId = String.valueOf(mine.getId());

		List<String> editBodies = new ArrayList<>();
		List<String> copyBodies = new ArrayList<>();
		List<String> deleteBodies = new ArrayList<>();
		for (String id : List.of(mineId, "999999999", "abc")) {
			editBodies.add(notFoundBody(get("/transactions/" + id + "/edit").with(asOther)));
			copyBodies.add(notFoundBody(get("/transactions/new").param("copyFrom", id).with(asOther)));
			deleteBodies.add(notFoundBody(post("/transactions/" + id + "/delete").with(asOther).with(csrf())));
		}

		for (List<String> bodies : List.of(editBodies, copyBodies, deleteBodies)) {
			assertThat(bodies.get(1)).as("存在しない ID と他人の ID").isEqualTo(bodies.get(0));
			assertThat(bodies.get(2)).as("数字でない ID と他人の ID").isEqualTo(bodies.get(0));
		}
		assertThat(transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId)).as("他人の削除で、消えていない").hasSize(1);
	}

	/** 自分の取引の削除は、今までどおり一覧に戻る（POST と CSRF は変わらない） */
	@Test
	void deletingMyOwnTransactionStillWorksAndReturnsToTheList() throws Exception {
		Transaction mine = save(LocalDate.of(2026, 10, 19), "食費", 100);
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(post("/transactions/" + mine.getId() + "/delete").with(asUser).with(csrf())).andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/transactions/" + mine.getId() + "/delete").with(asUser)).andExpect(status().isForbidden());

		entityManager.flush();
		entityManager.clear();
		assertThat(transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId)).isEmpty();
	}

	// ---- issue 025 の段階 7：取引の詳細ページをなくした ----

	/** /transactions/{id}（GET）は、誰が開いても 404：自分の取引、他人の取引、存在しない ID、数字でない ID、桁あふれの数字。状態コードがすべて同じ */
	@Test
	void theDetailUrlIsNotFoundForEveryone() throws Exception {
		Transaction mine = save(LocalDate.of(2026, 10, 19), "食費", 1500);
		entityManager.flush();
		entityManager.clear();

		for (String id : List.of(String.valueOf(mine.getId()), "999999999", "abc", "99999999999999999999")) {
			mockMvc.perform(get("/transactions/" + id).with(asUser)).andExpect(status().isNotFound());
			mockMvc.perform(get("/transactions/" + id).with(asOther)).andExpect(status().isNotFound());
		}
	}

	/** 詳細をなくしても、/transactions/new は「ID が new の取引」と読まれない（登録画面が開く） */
	@Test
	void transactionsNewIsStillTheRegisterScreenAndNotReadAsAnId() throws Exception {
		save(LocalDate.of(2026, 10, 19), "食費", 100);
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get("/transactions/new").with(asUser)).andExpect(status().isOk()).andExpect(view().name("transactions/form"));
	}

	/** 詳細の画面・処理・専用の CSS は、なくなった */
	@Test
	void theDetailTemplateTheControllerMethodAndTheOldCssAreGone() throws Exception {
		assertThat(Files.exists(Path.of("src", "main", "resources", "templates", "transactions", "detail.html"))).isFalse();
		assertThat(java.util.Arrays.stream(TransactionController.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName)).doesNotContain("detail");
		String css = Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css"));
		assertThat(css).doesNotContain("dl.detail", ".button-row");
		for (String name : List.of("transactions/list.html", "transactions/form.html", "categories/list.html", "vendors/list.html", "payment-methods/list.html")) {
			assertThat(Files.readString(Path.of("src", "main", "resources", "templates", name))).as(name).doesNotContain("button-row", "class=\"detail\"");
		}
	}

	/** 取引の編集の画面は、今までどおり開く（他の画面から詳細へ移る所は、なくなった。一覧の各行の「編集」「複写」「削除」だけ） */
	@Test
	void nothingInTheListLinksToTheDetailUrlAnyMore() throws Exception {
		Transaction t = save(LocalDate.of(2026, 10, 19), "食費", 100);
		String main = list();

		assertThat(main).doesNotContain("href=\"/transactions/" + t.getId() + "\"");
		assertThat(main).containsPattern("href=\"/transactions/" + t.getId() + "/edit\"");
	}
}
