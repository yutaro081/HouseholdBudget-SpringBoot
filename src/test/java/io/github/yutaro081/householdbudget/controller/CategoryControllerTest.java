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

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.form.CategoryForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.CategoryService;
import jakarta.persistence.EntityManager;

/**
 * CategoryController（科目の画面）のテスト（設計書 2.2、2.3、2.6、3.9、7.8）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoryControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private CategoryService categoryService;
	@Autowired
	private EntityManager entityManager;

	private Long aId;
	private Long bId;
	private RequestPostProcessor asA;
	private RequestPostProcessor asB;

	@org.junit.jupiter.api.BeforeEach
	void createUsers() {
		User a = userRepository.save(new User("ctrltest1", "dummy-hash-for-test"));
		User b = userRepository.save(new User("ctrltest2", "dummy-hash-for-test"));
		aId = a.getId();
		bId = b.getId();
		asA = user(new LoginUser(aId, "ctrltest1", "x"));
		asB = user(new LoginUser(bId, "ctrltest2", "x"));
	}

	private static CategoryForm form(String name) {
		CategoryForm form = new CategoryForm();
		form.setName(name);
		return form;
	}

	private Category addFor(Long userId, String name) {
		return categoryService.add(userId, form(name));
	}

	private String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	private boolean exists(Long categoryId) {
		entityManager.flush();
		entityManager.clear();
		return categoryRepository.findAll().stream().anyMatch(c -> c.getId().equals(categoryId));
	}

	private String nameOf(Long categoryId) {
		entityManager.flush();
		entityManager.clear();
		return categoryRepository.findAll().stream().filter(c -> c.getId().equals(categoryId)).findFirst().orElseThrow().getName();
	}

	// ---- ログイン必須 ----

	@ParameterizedTest(name = "未ログインで GET {0} は、ログイン画面へ移動する")
	@ValueSource(strings = { "/categories", "/categories/1/edit" })
	void notLoggedInIsRedirected(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "未ログインで POST {0} は、ログイン画面へ移動する（トークンがあっても）")
	@ValueSource(strings = { "/categories", "/categories/1/edit", "/categories/1/delete" })
	void notLoggedInPostIsRedirected(String path) throws Exception {
		mockMvc.perform(post(path).with(csrf()).param("name", "x"))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@ParameterizedTest(name = "トークンなしの POST {0} は 403")
	@ValueSource(strings = { "/categories", "/categories/1/edit", "/categories/1/delete" })
	void postWithoutCsrfTokenIsForbidden(String path) throws Exception {
		mockMvc.perform(post(path).with(asA).param("name", "x"))
				.andExpect(status().isForbidden());
	}

	// ---- 一覧 ----

	@Test
	void listShowsOwnCategoriesInOrderAndNotOthers() throws Exception {
		addFor(aId, "交通費");
		addFor(bId, "他人の科目");
		addFor(aId, "食費");
		addFor(aId, "雑費");

		MvcResult result = mockMvc.perform(get("/categories").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("交通費", "食費", "雑費");
		assertThat(body).doesNotContain("他人の科目");
		assertThat(body.indexOf("交通費")).isLessThan(body.indexOf("食費"));       // 登録順
		assertThat(body.indexOf("食費")).isLessThan(body.indexOf("雑費"));
	}

	@Test
	void listShowsMessageWhenThereAreNone() throws Exception {
		mockMvc.perform(get("/categories").with(asA))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(body(mockMvc.perform(get("/categories").with(asA)).andReturn())).contains("まだありません");
	}

	@Test
	void listHasMenuAddFormEditLinkAndDeleteFormWithConfirm() throws Exception {
		Category category = addFor(aId, "食費");

		String body = body(mockMvc.perform(get("/categories").with(asA)).andReturn());

		assertThat(body).contains("ctrltest1");                                                  // ヘッダーのユーザー名
		assertThat(body).contains("href=\"/categories\"");                                       // メニューの「科目」
		assertThat(body).contains("action=\"/categories\"");                                     // 追加フォーム
		assertThat(body).contains("href=\"/categories/" + category.getId() + "/edit\"");         // 変更リンク
		assertThat(body).contains("action=\"/categories/" + category.getId() + "/delete\"");     // 削除フォーム
		assertThat(body).contains("onsubmit=\"return confirm('削除しますか？')\"");                // 確認ダイアログ（固定の文言）
		assertThat(body).contains("name=\"_csrf\"");                                              // CSRF のトークン
	}

	/** 使用中の科目にも、削除ボタンを出す（押したあとで、理由を表示する、設計書 2.3） */
	@Test
	void deleteButtonIsShownForCategoryInUse() throws Exception {
		Category category = addFor(aId, "食費");
		saveTransactionOf(aId, category);

		String body = body(mockMvc.perform(get("/categories").with(asA)).andReturn());

		assertThat(body).contains("action=\"/categories/" + category.getId() + "/delete\"");
	}

	/** 利用者が入力した値は、無害化されて表示される（th:text、設計書 7.5） */
	@Test
	void namesAreEscapedOnScreen() throws Exception {
		Category category = addFor(aId, "<b>太</b>");

		String list = body(mockMvc.perform(get("/categories").with(asA)).andReturn());
		String edit = body(mockMvc.perform(get("/categories/" + category.getId() + "/edit").with(asA)).andReturn());

		assertThat(list).contains("&lt;b&gt;太&lt;/b&gt;").doesNotContain("<b>太</b>");
		assertThat(edit).contains("&lt;b&gt;太&lt;/b&gt;").doesNotContain("<b>太</b>");
	}

	// ---- 追加 ----

	@Test
	void addSucceedsAndRedirectsToList() throws Exception {
		mockMvc.perform(post("/categories").with(asA).with(csrf()).param("name", " 間食 "))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("successMessageKey", "success.added"));

		assertThat(categoryService.list(aId)).extracting(Category::getName).containsExactly("間食");   // 前後の空白は取り除かれる
	}

	@Test
	void addSuccessMessageIsShownOnList() throws Exception {
		String body = body(mockMvc.perform(get("/categories").with(asA).flashAttr("successMessageKey", "success.added")).andReturn());

		assertThat(body).contains("追加しました");
	}

	@ParameterizedTest(name = "追加の入力エラー [{0}]")
	@ValueSource(strings = { "", "   ", "　　" })
	void addWithBlankNameShowsErrorAndRedisplaysList(String name) throws Exception {
		addFor(aId, "食費");

		MvcResult result = mockMvc.perform(post("/categories").with(asA).with(csrf()).param("name", name))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("科目名を入力してください");
		assertThat(body).contains("食費");                                  // 一覧も取り直して表示される
		assertThat(categoryService.list(aId)).hasSize(1);                  // 追加されていない
	}

	@Test
	void addWithTooLongNameKeepsTheInputAndShowsError() throws Exception {
		addFor(aId, "食費");

		MvcResult result = mockMvc.perform(post("/categories").with(asA).with(csrf()).param("name", "あいうえおかきくけこさ"))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("科目名は10文字以内で入力してください");
		assertThat(body).contains("value=\"あいうえおかきくけこさ\"");        // 入力値が残る
		assertThat(body).contains("食費");                                  // 一覧も表示される
		assertThat(categoryService.list(aId)).hasSize(1);
	}

	@ParameterizedTest(name = "追加の重複 [{0}]")
	@ValueSource(strings = { "食費", " 食費 ", "　食費　" })
	void addDuplicateShowsErrorAndKeepsTheInput(String name) throws Exception {
		addFor(aId, "食費");

		MvcResult result = mockMvc.perform(post("/categories").with(asA).with(csrf()).param("name", name))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/list"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("その科目名はすでにあります");
		assertThat(body).contains("value=\"食費\"");                         // 取り除いた名前が入力欄に残る
		assertThat(categoryService.list(aId)).hasSize(1);
	}

	@Test
	void addDuplicateIgnoresCaseAndOtherUsersNames() throws Exception {
		addFor(aId, "Food");
		addFor(bId, "食費");

		mockMvc.perform(post("/categories").with(asA).with(csrf()).param("name", "FOOD"))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/list"));
		// 他のユーザーが同じ名前を持っていても、追加できる
		mockMvc.perform(post("/categories").with(asA).with(csrf()).param("name", "食費"))
				.andExpect(redirectedUrl("/categories"));
	}

	// ---- 変更 ----

	@Test
	void editFormShowsTheCurrentName() throws Exception {
		Category category = addFor(aId, "食費");

		MvcResult result = mockMvc.perform(get("/categories/" + category.getId() + "/edit").with(asA))
				.andExpect(status().isOk())
				.andExpect(view().name("categories/edit"))
				.andReturn();

		String body = body(result);
		assertThat(body).contains("value=\"食費\"");
		assertThat(body).contains("action=\"/categories/" + category.getId() + "/edit\"");
		assertThat(body).contains("href=\"/categories\"");                  // 戻るリンク
		assertThat(body).contains("name=\"_csrf\"");
	}

	@Test
	void editSucceedsAndRedirectsToList() throws Exception {
		Category category = addFor(aId, "間食");

		mockMvc.perform(post("/categories/" + category.getId() + "/edit").with(asA).with(csrf()).param("name", "おやつ"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("successMessageKey", "success.updated"));

		assertThat(nameOf(category.getId())).isEqualTo("おやつ");
	}

	@Test
	void editWithTheSameNameIsNotAnError() throws Exception {
		Category category = addFor(aId, "食費");

		mockMvc.perform(post("/categories/" + category.getId() + "/edit").with(asA).with(csrf()).param("name", "食費"))
				.andExpect(redirectedUrl("/categories"));
	}

	@Test
	void editWithBlankOrTooLongNameKeepsTheInputAndShowsError() throws Exception {
		Category category = addFor(aId, "食費");
		String path = "/categories/" + category.getId() + "/edit";

		String blank = body(mockMvc.perform(post(path).with(asA).with(csrf()).param("name", " "))
				.andExpect(status().isOk()).andExpect(view().name("categories/edit")).andReturn());
		assertThat(blank).contains("科目名を入力してください");
		assertThat(blank).contains("action=\"" + path + "\"");              // 送り先は変わらない

		String tooLong = body(mockMvc.perform(post(path).with(asA).with(csrf()).param("name", "あいうえおかきくけこさ"))
				.andExpect(status().isOk()).andExpect(view().name("categories/edit")).andReturn());
		assertThat(tooLong).contains("科目名は10文字以内で入力してください");
		assertThat(tooLong).contains("value=\"あいうえおかきくけこさ\"");       // 入力値が残る

		assertThat(nameOf(category.getId())).isEqualTo("食費");             // 変更されていない
	}

	@Test
	void editToAnotherCategorysNameShowsErrorAndKeepsTheInput() throws Exception {
		addFor(aId, "交通費");
		Category category = addFor(aId, "食費");

		String body = body(mockMvc.perform(post("/categories/" + category.getId() + "/edit").with(asA).with(csrf()).param("name", "交通費"))
				.andExpect(status().isOk()).andExpect(view().name("categories/edit")).andReturn());

		assertThat(body).contains("その科目名はすでにあります");
		assertThat(body).contains("value=\"交通費\"");
		assertThat(nameOf(category.getId())).isEqualTo("食費");
	}

	// ---- 他人のID・存在しないID・数字でないID（設計書 3.9、7.8、S4） ----

	@Test
	void otherUsersCategoryIsNotFoundForViewEditAndDelete() throws Exception {
		Category aCategory = addFor(aId, "Aの科目");
		String id = String.valueOf(aCategory.getId());

		// B が、A の科目の ID を URL に入れる
		mockMvc.perform(get("/categories/" + id + "/edit").with(asB))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(post("/categories/" + id + "/edit").with(asB).with(csrf()).param("name", "書き換え"))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		// 入力が間違っていても、同じ 404（他人のデータの有無が分かる差を作らない）
		mockMvc.perform(post("/categories/" + id + "/edit").with(asB).with(csrf()).param("name", ""))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(post("/categories/" + id + "/delete").with(asB).with(csrf()))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));

		// A の科目は、変わっていない
		assertThat(exists(aCategory.getId())).isTrue();
		assertThat(nameOf(aCategory.getId())).isEqualTo("Aの科目");
		assertThat(budgetRepository.findAll()).anyMatch(b -> b.getCategory().getId().equals(aCategory.getId()));
	}

	@Test
	void nonexistentIdIsNotFound() throws Exception {
		String id = "999999999";

		mockMvc.perform(get("/categories/" + id + "/edit").with(asA)).andExpect(status().isNotFound());
		mockMvc.perform(post("/categories/" + id + "/edit").with(asA).with(csrf()).param("name", "x")).andExpect(status().isNotFound());
		mockMvc.perform(post("/categories/" + id + "/edit").with(asA).with(csrf()).param("name", "")).andExpect(status().isNotFound());
		mockMvc.perform(post("/categories/" + id + "/delete").with(asA).with(csrf())).andExpect(status().isNotFound());
	}

	@ParameterizedTest(name = "ID が数字でない URL [{0}] は 404")
	@ValueSource(strings = { "abc", "1a", "1.5", "99999999999999999999" })
	void nonNumericIdIsNotFound(String id) throws Exception {
		mockMvc.perform(get("/categories/" + id + "/edit").with(asA))
				.andExpect(status().isNotFound()).andExpect(view().name("error/404"));
		mockMvc.perform(post("/categories/" + id + "/edit").with(asA).with(csrf()).param("name", "x"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/categories/" + id + "/delete").with(asA).with(csrf()))
				.andExpect(status().isNotFound());
	}

	/** 他人のID・存在しないID・数字でないIDは、画面も状態コードも同じ（他人のデータの有無を教えない、7.8） */
	@Test
	void otherUsersNonexistentAndNonNumericLookTheSame() throws Exception {
		Category aCategory = addFor(aId, "Aの科目");

		MvcResult others = mockMvc.perform(get("/categories/" + aCategory.getId() + "/edit").with(asB)).andReturn();
		MvcResult missing = mockMvc.perform(get("/categories/999999999/edit").with(asB)).andReturn();
		MvcResult nonNumeric = mockMvc.perform(get("/categories/abc/edit").with(asB)).andReturn();

		assertThat(others.getResponse().getStatus()).isEqualTo(404);
		assertThat(missing.getResponse().getStatus()).isEqualTo(404);
		assertThat(nonNumeric.getResponse().getStatus()).isEqualTo(404);
		assertThat(body(missing)).isEqualTo(body(others));
		assertThat(body(nonNumeric)).isEqualTo(body(others));
		assertThat(body(others)).contains("ページが見つかりません").doesNotContain("Aの科目");
	}

	// ---- 削除 ----

	@Test
	void deleteSucceedsAndRedirectsToList() throws Exception {
		Category category = addFor(aId, "間食");           // 予算0円、取引なし

		mockMvc.perform(post("/categories/" + category.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("successMessageKey", "success.deleted"));

		assertThat(exists(category.getId())).isFalse();
		assertThat(budgetRepository.findAll()).noneMatch(b -> b.getCategory().getId().equals(category.getId()));   // 予算の行も消える
	}

	@Test
	void deleteOfCategoryInUseGoesBackToListWithTheReason() throws Exception {
		Category category = addFor(aId, "食費");
		saveTransactionOf(aId, category);

		mockMvc.perform(post("/categories/" + category.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("errorMessageKey", "error.delete.inUse"))
				.andExpect(result -> assertThat(result.getFlashMap().containsKey("successMessageKey")).isFalse());

		assertThat(exists(category.getId())).isTrue();
		// 一覧に戻ったとき、理由が画面の上に表示される
		String body = body(mockMvc.perform(get("/categories").with(asA).flashAttr("errorMessageKey", "error.delete.inUse")).andReturn());
		assertThat(body).contains("取引で使われているため削除できません");
		assertThat(body).contains("食費");
	}

	@Test
	void deleteOfCategoryWithNonZeroBudgetGoesBackToListWithTheReason() throws Exception {
		Category category = categoryRepository.save(new Category(aId, "交通費"));
		budgetRepository.save(new Budget(aId, category, 2026, Season.AUTUMN, 30000));

		mockMvc.perform(post("/categories/" + category.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(redirectedUrl("/categories"))
				.andExpect(flash().attribute("errorMessageKey", "error.delete.budgetNotZero"));

		assertThat(exists(category.getId())).isTrue();
		String body = body(mockMvc.perform(get("/categories").with(asA).flashAttr("errorMessageKey", "error.delete.budgetNotZero")).andReturn());
		assertThat(body).contains("予算が0円ではない季節があるため削除できません");
	}

	/** 理由が重なるときは、「使用中」だけ（F1） */
	@Test
	void whenBothReasonsApplyOnlyInUseIsShown() throws Exception {
		Category category = categoryRepository.save(new Category(aId, "交通費"));
		budgetRepository.save(new Budget(aId, category, 2026, Season.AUTUMN, 30000));
		saveTransactionOf(aId, category);

		mockMvc.perform(post("/categories/" + category.getId() + "/delete").with(asA).with(csrf()))
				.andExpect(flash().attribute("errorMessageKey", "error.delete.inUse"));
	}

	private void saveTransactionOf(Long userId, Category category) {
		Vendor vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		PaymentMethod paymentMethod = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 27), 1000, category, null, vendor, paymentMethod));
		entityManager.flush();
	}

}
