package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.form.PaymentMethodForm;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.PaymentMethodService;
import jakarta.persistence.EntityManager;

/**
 * PaymentMethodController（支払方法の画面）のテスト（設計書 2.2、2.3、2.6、3.9、7.8）。科目（CategoryControllerTest）と同じ内容。
 * 違う点：予算に関するテストはない。文字数の上限は10文字。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentMethodControllerTest {

	private static final String TOO_LONG = "あ".repeat(10 + 1);

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
	private PaymentMethodService paymentMethodService;
	@Autowired
	private EntityManager entityManager;

	private Long aId;
	private Long bId;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@BeforeEach
	void createUsers() {
		User a = userRepository.save(new User("pmtctrl1", "dummy-hash-for-test"));
		User b = userRepository.save(new User("pmtctrl2", "dummy-hash-for-test"));
		aId = a.getId();
		bId = b.getId();
		asA = user(new LoginUser(aId, "pmtctrl1", "x"));
		asB = user(new LoginUser(bId, "pmtctrl2", "x"));
	}

	private static PaymentMethodForm form(String name) {
		PaymentMethodForm form = new PaymentMethodForm();
		form.setName(name);
		return form;
	}

	private PaymentMethod addFor(Long userId, String name) {
		return paymentMethodService.add(userId, form(name));
	}

	private String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	private boolean exists(Long id) {
		entityManager.flush();
		entityManager.clear();
		return userRepository.findAll().stream()
				.anyMatch(u -> paymentMethodService.list(u.getId()).stream().anyMatch(x -> x.getId().equals(id)));
	}

	private String nameOf(Long id) {
		entityManager.flush();
		entityManager.clear();
		return paymentMethodService.list(aId).stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow().getName();
	}

	// ---- ログイン必須 ----

	@ParameterizedTest(name = "未ログインで GET {0} は、ログイン画面へ移動する")
	@ValueSource(strings = { "/payment-methods", "/payment-methods/1/edit" })
	void notLoggedInIsRedirected(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "未ログインで POST {0} は、ログイン画面へ移動する（トークンがあっても）")
	@ValueSource(strings = { "/payment-methods", "/payment-methods/1/edit", "/payment-methods/1/delete" })
	void notLoggedInPostIsRedirected(String path) throws Exception {
		mockMvc.perform(post(path).with(csrf()).param("name", "x"))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "トークンなしの POST {0} は 403")
	@ValueSource(strings = { "/payment-methods", "/payment-methods/1/edit", "/payment-methods/1/delete" })
	void postWithoutCsrfTokenIsForbidden(String path) throws Exception {
		mockMvc.perform(post(path).with(asA).param("name", "x"))
				.andExpect(status().isForbidden());
	}

	// ---- 一覧 ----

	@Test
	void listShowsOwnItemsInOrderAndNotOthers() throws Exception {
		addFor(aId, "現金");
		addFor(bId, "他人のもの");
		addFor(aId, "クレジットカード");
		addFor(aId, "Visa");

		MvcResult result = mockMvc.perform(get("/payment-methods").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("現金", "クレジットカード", "Visa");
		assertThat(body).doesNotContain("他人のもの");
		assertThat(body.indexOf("現金")).isLessThan(body.indexOf("クレジットカード"));       // 登録順
		assertThat(body.indexOf("クレジットカード")).isLessThan(body.indexOf("Visa"));
	}

	@Test
	void listShowsMessageWhenThereAreNone() throws Exception {
		assertThat(body(mockMvc.perform(get("/payment-methods").with(asA)).andReturn())).contains("まだありません");
	}

	@Test
	void listHasMenuAddFormEditLinkAndDeleteFormWithConfirm() throws Exception {
		var item = addFor(aId, "現金");

		String body = body(mockMvc.perform(get("/payment-methods").with(asA)).andReturn());

		assertThat(body).contains("pmtctrl1");                                                  // ヘッダーのユーザー名
		assertThat(body).contains("href=\"/categories\"", "href=\"/vendors\"", "href=\"/payment-methods\"");   // メニュー
		assertThat(body).contains("action=\"/payment-methods\"");                                          // 追加フォーム
		assertThat(body).contains("href=\"/payment-methods/" + item.getId() + "/edit\"");                  // 変更リンク
		assertThat(body).contains("action=\"/payment-methods/" + item.getId() + "/delete\"");              // 削除フォーム
		assertThat(body).contains("onsubmit=\"return confirm('削除しますか？')\"");                 // 確認ダイアログ（固定の文言）
		assertThat(body).contains("name=\"_csrf\"");                                             // CSRF のトークン
	}

	/** 使用中のものにも、削除ボタンを出す（押したあとで、理由を表示する、設計書 2.3） */
	@Test
	void deleteButtonIsShownForItemInUse() throws Exception {
		var item = addFor(aId, "現金");
		saveTransactionUsing(aId, item.getId());

		String body = body(mockMvc.perform(get("/payment-methods").with(asA)).andReturn());

		assertThat(body).contains("action=\"/payment-methods/" + item.getId() + "/delete\"");
	}

	/** 利用者が入力した値は、無害化されて表示される（th:text、設計書 7.5） */
	@Test
	void namesAreEscapedOnScreen() throws Exception {
		var item = addFor(aId, "<b>太</b>");

		String list = body(mockMvc.perform(get("/payment-methods").with(asA)).andReturn());
		String edit = body(mockMvc.perform(get("/payment-methods/" + item.getId() + "/edit").with(asA)).andReturn());

		assertThat(list).contains("&lt;b&gt;太&lt;/b&gt;").doesNotContain("<b>太</b>");
		assertThat(edit).contains("&lt;b&gt;太&lt;/b&gt;").doesNotContain("<b>太</b>");
	}

	// ---- 追加 ----

	@Test
	void addSucceedsAndRedirectsToList() throws Exception {
		mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", " 現金 "))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment-methods"))
				.andExpect(flash().attribute("successMessageKey", "success.added"));

		assertThat(paymentMethodService.list(aId)).extracting(x -> x.getName()).containsExactly("現金");   // 前後の空白は取り除かれる
	}

	@Test
	void addSuccessMessageIsShownOnList() throws Exception {
		String body = body(mockMvc.perform(get("/payment-methods").with(asA).flashAttr("successMessageKey", "success.added")).andReturn());

		assertThat(body).contains("追加しました");
	}

	@ParameterizedTest(name = "追加の入力エラー [{0}]")
	@ValueSource(strings = { "", "   ", "\u3000\u3000" })
	void addWithBlankNameShowsErrorAndRedisplaysList(String name) throws Exception {
		addFor(aId, "現金");

		MvcResult result = mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", name))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("支払方法名を入力してください");
		assertThat(body).contains("現金");                           // 一覧も取り直して表示される
		assertThat(paymentMethodService.list(aId)).hasSize(1);                    // 追加されていない
	}

	@Test
	void addWithTooLongNameKeepsTheInputAndShowsError() throws Exception {
		addFor(aId, "現金");

		MvcResult result = mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", TOO_LONG))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("支払方法名は10文字以内で入力してください");
		assertThat(body).contains("value=\"" + TOO_LONG + "\"");          // 入力値が残る
		assertThat(body).contains("現金");                           // 一覧も表示される
		assertThat(paymentMethodService.list(aId)).hasSize(1);
	}

	@Test
	void addWithTheMaximumLengthSucceeds() throws Exception {
		String max = "あ".repeat(10);

		mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", max))
				.andExpect(redirectedUrl("/payment-methods"));

		assertThat(paymentMethodService.list(aId)).extracting(x -> x.getName()).containsExactly(max);
	}

	@ParameterizedTest(name = "追加の重複 [{0}]")
	@ValueSource(strings = { "現金", " 現金 ", "\u3000現金\u3000" })
	void addDuplicateShowsErrorAndKeepsTheInput(String name) throws Exception {
		addFor(aId, "現金");

		MvcResult result = mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", name))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("その支払方法名はすでにあります");
		assertThat(body).contains("value=\"現金\"");                // 取り除いた名前が入力欄に残る
		assertThat(paymentMethodService.list(aId)).hasSize(1);
	}

	@Test
	void addDuplicateIgnoresCaseAndOtherUsersNames() throws Exception {
		addFor(aId, "Food");
		addFor(bId, "現金");

		mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", "FOOD"))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/list"));
		// 他のユーザーが同じ名前を持っていても、追加できる
		mockMvc.perform(post("/payment-methods").with(asA).with(csrf()).param("name", "現金"))
				.andExpect(redirectedUrl("/payment-methods"));
	}

	// ---- 変更 ----

	@Test
	void editFormShowsTheCurrentName() throws Exception {
		var item = addFor(aId, "現金");

		MvcResult result = mockMvc.perform(get("/payment-methods/" + item.getId() + "/edit").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("payment-methods/edit"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("value=\"現金\"");
		assertThat(body).contains("action=\"/payment-methods/" + item.getId() + "/edit\"");
		assertThat(body).contains("href=\"/payment-methods\"");                    // 戻るリンク
		assertThat(body).contains("name=\"_csrf\"");
	}

	@Test
	void editSucceedsAndRedirectsToList() throws Exception {
		var item = addFor(aId, "古い名前");

		mockMvc.perform(post("/payment-methods/" + item.getId() + "/edit").with(asA).with(csrf()).param("name", "新しい名前"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment-methods"))
				.andExpect(flash().attribute("successMessageKey", "success.updated"));

		assertThat(nameOf(item.getId())).isEqualTo("新しい名前");
	}

	@Test
	void editWithTheSameNameIsNotAnError() throws Exception {
		var item = addFor(aId, "現金");

		mockMvc.perform(post("/payment-methods/" + item.getId() + "/edit").with(asA).with(csrf()).param("name", "現金"))
				.andExpect(redirectedUrl("/payment-methods"));
	}

	@Test
	void editWithBlankOrTooLongNameKeepsTheInputAndShowsError() throws Exception {
		var item = addFor(aId, "現金");
		String path = "/payment-methods/" + item.getId() + "/edit";

		String blank = body(mockMvc.perform(post(path).with(asA).with(csrf()).param("name", " "))
				.andExpect(status().isOk()).andExpect(view().name("payment-methods/edit")).andReturn());
		assertThat(blank).contains("支払方法名を入力してください");
		assertThat(blank).contains("action=\"" + path + "\"");            // 送り先は変わらない

		String tooLong = body(mockMvc.perform(post(path).with(asA).with(csrf()).param("name", TOO_LONG))
				.andExpect(status().isOk()).andExpect(view().name("payment-methods/edit")).andReturn());
		assertThat(tooLong).contains("支払方法名は10文字以内で入力してください");
		assertThat(tooLong).contains("value=\"" + TOO_LONG + "\"");        // 入力値が残る

		assertThat(nameOf(item.getId())).isEqualTo("現金");           // 変更されていない
	}

	@Test
	void editToAnotherItemsNameShowsErrorAndKeepsTheInput() throws Exception {
		addFor(aId, "クレジットカード");
		var item = addFor(aId, "現金");

		String body = body(mockMvc.perform(post("/payment-methods/" + item.getId() + "/edit").with(asA).with(csrf()).param("name", "クレジットカード"))
				.andExpect(status().isOk()).andExpect(view().name("payment-methods/edit")).andReturn());

		assertThat(body).contains("その支払方法名はすでにあります");
		assertThat(body).contains("value=\"クレジットカード\"");
		assertThat(nameOf(item.getId())).isEqualTo("現金");
	}

	// ---- 他人のID・存在しないID・数字でないID（設計書 3.9、7.8、S4） ----

	@Test
	void otherUsersItemIsNotFoundForViewEditAndDelete() throws Exception {
		var aItem = addFor(aId, "Aのもの");
		String id = String.valueOf(aItem.getId());

		// B が、A のものの ID を URL に入れる
		mockMvc.perform(get("/payment-methods/" + id + "/edit").with(asB))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(post("/payment-methods/" + id + "/edit").with(asB).with(csrf()).param("name", "書き換え"))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		// 入力が間違っていても、同じ 404（他人のデータの有無が分かる差を作らない）
		mockMvc.perform(post("/payment-methods/" + id + "/edit").with(asB).with(csrf()).param("name", ""))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(post("/payment-methods/" + id + "/delete").with(asB).with(csrf()))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));

		// A のものは、変わっていない
		assertThat(exists(aItem.getId())).isTrue();
		assertThat(nameOf(aItem.getId())).isEqualTo("Aのもの");
	}

	@Test
	void nonexistentIdIsNotFound() throws Exception {
		String id = "999999999";

		mockMvc.perform(get("/payment-methods/" + id + "/edit").with(asA)).andExpect(status().isNotFound());
		mockMvc.perform(post("/payment-methods/" + id + "/edit").with(asA).with(csrf()).param("name", "x")).andExpect(status().isNotFound());
		mockMvc.perform(post("/payment-methods/" + id + "/edit").with(asA).with(csrf()).param("name", "")).andExpect(status().isNotFound());
		mockMvc.perform(post("/payment-methods/" + id + "/delete").with(asA).with(csrf())).andExpect(status().isNotFound());
	}

	@ParameterizedTest(name = "ID が数字でない URL [{0}] は 404")
	@ValueSource(strings = { "abc", "1a", "1.5", "99999999999999999999" })
	void nonNumericIdIsNotFound(String id) throws Exception {
		mockMvc.perform(get("/payment-methods/" + id + "/edit").with(asA))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(post("/payment-methods/" + id + "/edit").with(asA).with(csrf()).param("name", "x"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/payment-methods/" + id + "/delete").with(asA).with(csrf()))
				.andExpect(status().isNotFound());
	}

	/** 他人のID・存在しないID・数字でないIDは、画面も状態コードも同じ（他人のデータの有無を教えない、7.8） */
	@Test
	void otherUsersNonexistentAndNonNumericLookTheSame() throws Exception {
		var aItem = addFor(aId, "Aのもの");

		MvcResult others = mockMvc.perform(get("/payment-methods/" + aItem.getId() + "/edit").with(asB)).andReturn();
		MvcResult missing = mockMvc.perform(get("/payment-methods/999999999/edit").with(asB)).andReturn();
		MvcResult nonNumeric = mockMvc.perform(get("/payment-methods/abc/edit").with(asB)).andReturn();

		assertThat(others.getResponse().getStatus()).isEqualTo(404);
		assertThat(missing.getResponse().getStatus()).isEqualTo(404);
		assertThat(nonNumeric.getResponse().getStatus()).isEqualTo(404);
		assertThat(body(missing)).isEqualTo(body(others));
		assertThat(body(nonNumeric)).isEqualTo(body(others));
		assertThat(body(others)).contains("ページが見つかりません").doesNotContain("Aのもの");
	}

	// ---- 削除 ----

	@Test
	void deleteSucceedsAndRedirectsToList() throws Exception {
		var item = addFor(aId, "消すもの");

		mockMvc.perform(post("/payment-methods/" + item.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment-methods"))
				.andExpect(flash().attribute("successMessageKey", "success.deleted"));

		assertThat(exists(item.getId())).isFalse();
	}

	@Test
	void deleteOfItemInUseGoesBackToListWithTheReason() throws Exception {
		var item = addFor(aId, "現金");
		saveTransactionUsing(aId, item.getId());

		mockMvc.perform(post("/payment-methods/" + item.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment-methods"))
				.andExpect(flash().attribute("errorMessageKey", "error.delete.inUse"))
				.andExpect(result -> assertThat(result.getFlashMap().containsKey("successMessageKey")).isFalse());

		assertThat(exists(item.getId())).isTrue();
		// 一覧に戻ったとき、理由が画面の上に表示される
		String body = body(mockMvc.perform(get("/payment-methods").with(asA).flashAttr("errorMessageKey", "error.delete.inUse")).andReturn());
		assertThat(body).contains("取引で使われているため削除できません");
		assertThat(body).contains("現金");
	}

	/** このテストが扱う種類(支払方法)のIDを使って、取引を1件作る（ほかの2種類は新しく作る） */
	private void saveTransactionUsing(Long userId, Long targetId) {
		Category category = categoryRepository.save(new Category(userId, "科目"));
		Vendor vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		PaymentMethod paymentMethod = paymentMethodRepository.findById(targetId).orElseThrow();
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 27), 1000, category, null, vendor, paymentMethod));
		entityManager.flush();
	}

}
