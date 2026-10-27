package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.CategoryForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * CategoryService のテスト（設計書 5.4、5.5、5.6、3.9）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今の季節」は、日付を固定した Clock で確かめる。
 */
@SpringBootTest
@Transactional
class CategoryServiceTest {

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
	private EntityManager entityManager;

	/** 日本時間の2026年10月27日（2026年の秋）に固定した Clock で動く CategoryService */
	private CategoryService service() {
		return serviceOn("2026-10-27");
	}

	private CategoryService serviceOn(String isoDate) {
		Clock clock = Clock.fixed(LocalDate.parse(isoDate).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		return new CategoryService(categoryRepository, budgetRepository, transactionRepository, clock);
	}

	private Long createUser(String username) {
		return userRepository.save(new User(username, "dummy-hash-for-test")).getId();
	}

	private static CategoryForm form(String name) {
		CategoryForm form = new CategoryForm();
		form.setName(name);
		return form;
	}

	private List<Budget> budgetsOf(Long userId, Long categoryId) {
		entityManager.flush();
		return budgetRepository.findAll().stream()
				.filter(b -> b.getUserId().equals(userId) && b.getCategory().getId().equals(categoryId))
				.sorted(Comparator.comparing(Budget::getId)).toList();
	}

	private boolean categoryExists(Long categoryId) {
		entityManager.flush();
		entityManager.clear();
		return categoryRepository.findAll().stream().anyMatch(c -> c.getId().equals(categoryId));
	}

	// ---- 一覧（登録順、自分の分だけ） ----

	@Test
	void listReturnsOwnCategoriesInRegistrationOrder() {
		Long a = createUser("cattest1");
		Long b = createUser("cattest2");
		CategoryService service = service();
		service.add(a, form("交通費"));
		service.add(b, form("他人の科目"));
		service.add(a, form("食費"));
		service.add(a, form("雑費"));

		assertThat(service.list(a)).extracting(Category::getName).containsExactly("交通費", "食費", "雑費");
		assertThat(service.list(b)).extracting(Category::getName).containsExactly("他人の科目");
	}

	// ---- 追加 ----

	@Test
	void addCreatesCategoryAndBudgetOfZeroForCurrentSeasonOnly() {
		Long userId = createUser("cattest3");

		Category category = service().add(userId, form("間食"));

		assertThat(category.getUserId()).isEqualTo(userId);
		assertThat(category.getName()).isEqualTo("間食");
		List<Budget> budgets = budgetsOf(userId, category.getId());
		assertThat(budgets).hasSize(1);                                  // ほかの季節は作らない
		assertThat(budgets.get(0).getAmount()).isZero();                 // 0円
		assertThat(budgets.get(0).getSeasonYear()).isEqualTo(2026);
		assertThat(budgets.get(0).getSeason()).isEqualTo(Season.AUTUMN);
		assertThat(budgets.get(0).getUserId()).isEqualTo(userId);
	}

	/** 「今の季節」は、固定した日付（日本時間）から決まる。冬の1月・2月は前の年 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2027-02-28, 2026, WINTER",
			"2027-01-15, 2026, WINTER",
			"2026-12-01, 2026, WINTER",
			"2026-11-30, 2026, AUTUMN",
			"2027-03-01, 2027, SPRING"
	})
	void addUsesCurrentSeason(String date, int expectedYear, Season expectedSeason) {
		Long userId = createUser("cattest4");

		Category category = serviceOn(date).add(userId, form("間食"));

		List<Budget> budgets = budgetsOf(userId, category.getId());
		assertThat(budgets).hasSize(1);
		assertThat(budgets.get(0).getSeasonYear()).isEqualTo(expectedYear);
		assertThat(budgets.get(0).getSeason()).isEqualTo(expectedSeason);
		assertThat(budgets.get(0).getAmount()).isZero();
	}

	@ParameterizedTest(name = "[{0}] の後に [{1}] は重複")
	@CsvSource({
			"食費, 食費",
			"Food, FOOD",
			"food, Food",
			"ひらがな, ヒラガナ",
			"ヒラガナ, ひらがな",
			"ｱｲｳ, アイウ",
			"アイウ, ｱｲｳ",
			"コンビニ, ｺﾝﾋﾞﾆ"
	})
	void addDuplicateIsRejected(String first, String second) {
		Long userId = createUser("cattest5");
		CategoryService service = service();
		service.add(userId, form(first));
		int before = service.list(userId).size();

		assertThatThrownBy(() -> service.add(userId, form(second))).isInstanceOf(DuplicateException.class);

		assertThat(service.list(userId)).hasSize(before);               // 増えない
	}

	/** 濁点の違いは別の名前（設計書 3.12） */
	@Test
	void addWithDifferentVoicedMarkIsAllowed() {
		Long userId = createUser("cattest6");
		CategoryService service = service();
		service.add(userId, form("カ"));

		assertThatCode(() -> service.add(userId, form("ガ"))).doesNotThrowAnyException();
		assertThat(service.list(userId)).extracting(Category::getName).containsExactly("カ", "ガ");
	}

	/** 同じ名前でも、別のユーザーなら追加できる（科目はユーザーごとに持つ） */
	@Test
	void sameNameIsAllowedForDifferentUsers() {
		Long a = createUser("cattest7");
		Long b = createUser("cattest8");
		CategoryService service = service();
		service.add(a, form("食費"));

		assertThatCode(() -> service.add(b, form("食費"))).doesNotThrowAnyException();
		assertThat(service.list(b)).extracting(Category::getName).containsExactly("食費");
	}

	// ---- 変更 ----

	@Test
	void updateChangesTheName() {
		Long userId = createUser("cattest9");
		CategoryService service = service();
		Category category = service.add(userId, form("間食"));

		service.update(userId, category.getId(), form("おやつ"));

		assertThat(service.get(userId, category.getId()).getName()).isEqualTo("おやつ");
	}

	@Test
	void updateWithTheSameNameIsNotADuplicate() {
		Long userId = createUser("cattest10");
		CategoryService service = service();
		Category category = service.add(userId, form("食費"));

		assertThatCode(() -> service.update(userId, category.getId(), form("食費"))).doesNotThrowAnyException();
		assertThat(service.get(userId, category.getId()).getName()).isEqualTo("食費");
	}

	/** 大文字・小文字だけの変更は、自分自身なので重複にならない */
	@Test
	void updateCaseOnlyChangeIsAllowed() {
		Long userId = createUser("cattest11");
		CategoryService service = service();
		Category category = service.add(userId, form("Amazon"));

		assertThatCode(() -> service.update(userId, category.getId(), form("AMAZON"))).doesNotThrowAnyException();
		assertThat(service.get(userId, category.getId()).getName()).isEqualTo("AMAZON");
	}

	@Test
	void updateToAnotherCategorysNameIsRejected() {
		Long userId = createUser("cattest12");
		CategoryService service = service();
		service.add(userId, form("交通費"));
		Category other = service.add(userId, form("食費"));

		assertThatThrownBy(() -> service.update(userId, other.getId(), form("交通費"))).isInstanceOf(DuplicateException.class);
		// 拒否されたあとは、名前は変わっていない。別の名前になら変更できる
		assertThat(service.get(userId, other.getId()).getName()).isEqualTo("食費");
		assertThatCode(() -> service.update(userId, other.getId(), form("KOUTSUUHI"))).doesNotThrowAnyException();
		assertThat(service.get(userId, other.getId()).getName()).isEqualTo("KOUTSUUHI");
	}

	@Test
	void updateToDifferentUsersNameIsAllowed() {
		Long a = createUser("cattest13");
		Long b = createUser("cattest14");
		CategoryService service = service();
		service.add(a, form("食費"));
		Category mine = service.add(b, form("間食"));

		assertThatCode(() -> service.update(b, mine.getId(), form("食費"))).doesNotThrowAnyException();
	}

	// ---- 他人のID・存在しないID（3.9、7.8） ----

	@Test
	void otherUsersCategoryIsNotFound() {
		Long a = createUser("cattest15");
		Long b = createUser("cattest16");
		CategoryService service = service();
		Category aCategory = service.add(a, form("Aの科目"));
		Long id = aCategory.getId();

		assertThatThrownBy(() -> service.get(b, id)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(b, id, form("書き換え"))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(b, id)).isInstanceOf(NotFoundException.class);

		// 他人のデータは、変わっていない
		assertThat(service.get(a, id).getName()).isEqualTo("Aの科目");
		assertThat(categoryExists(id)).isTrue();
		assertThat(budgetsOf(a, id)).hasSize(1);
	}

	@Test
	void nonexistentIdIsNotFound() {
		Long userId = createUser("cattest17");
		CategoryService service = service();
		Long missing = 999_999_999L;

		assertThatThrownBy(() -> service.get(userId, missing)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(userId, missing, form("x"))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(userId, missing)).isInstanceOf(NotFoundException.class);
	}

	// ---- 削除 ----

	@Test
	void deleteRemovesTheCategoryAndAllItsBudgets() {
		Long userId = createUser("cattest18");
		CategoryService service = service();
		Category category = service.add(userId, form("間食"));          // 今の季節の予算0円
		// ほかの季節にも、0円の予算がある
		budgetRepository.save(new Budget(userId, category, 2026, Season.SUMMER, 0));
		budgetRepository.save(new Budget(userId, category, 2027, Season.WINTER, 0));
		assertThat(budgetsOf(userId, category.getId())).hasSize(3);

		service.delete(userId, category.getId());

		assertThat(categoryExists(category.getId())).isFalse();
		assertThat(budgetRepository.findAll()).noneMatch(b -> b.getCategory().getId().equals(category.getId()));
	}

	/** 予算の行がない季節（未設定）は、0円として扱い、削除をさまたげない */
	@Test
	void deleteIsAllowedWhenNoBudgetRowsExist() {
		Long userId = createUser("cattest19");
		CategoryService service = service();
		Category category = service.add(userId, form("間食"));
		budgetRepository.deleteByUserIdAndCategoryId(userId, category.getId());   // 予算の行を、全部なくす（すべて未設定）
		entityManager.flush();

		assertThatCode(() -> service.delete(userId, category.getId())).doesNotThrowAnyException();

		assertThat(categoryExists(category.getId())).isFalse();
	}

	@Test
	void deleteIsRejectedWhenUsedByATransaction() {
		Long userId = createUser("cattest20");
		CategoryService service = service();
		Category category = service.add(userId, form("食費"));            // 予算0円
		saveTransactionOf(userId, category);

		assertThatThrownBy(() -> service.delete(userId, category.getId()))
				.isInstanceOfSatisfying(DeletionNotAllowedException.class,
						e -> assertThat(e.getMessageKey()).isEqualTo("error.delete.inUse"));

		assertThat(categoryExists(category.getId())).isTrue();            // 科目も
		assertThat(budgetsOf(userId, category.getId())).hasSize(1);       // 予算も残っている
	}

	@Test
	void deleteIsRejectedWhenAnyBudgetIsNotZero() {
		Long userId = createUser("cattest21");
		CategoryService service = service();
		Category category = service.add(userId, form("食費"));            // 今の季節は0円
		budgetRepository.save(new Budget(userId, category, 2026, Season.SUMMER, 0));
		budgetRepository.save(new Budget(userId, category, 2026, Season.SPRING, 5000));   // 別の季節だけ0円でない

		assertThatThrownBy(() -> service.delete(userId, category.getId()))
				.isInstanceOfSatisfying(DeletionNotAllowedException.class,
						e -> assertThat(e.getMessageKey()).isEqualTo("error.delete.budgetNotZero"));

		assertThat(categoryExists(category.getId())).isTrue();
		assertThat(budgetsOf(userId, category.getId())).hasSize(3);       // 予算の行も、1つも消えていない
	}

	@Test
	void deleteIsRejectedWhenTheCurrentSeasonBudgetIsNotZero() {
		Long userId = createUser("cattest22");
		CategoryService service = service();
		Category category = categoryRepository.save(new Category(userId, "交通費"));
		budgetRepository.save(new Budget(userId, category, 2026, Season.AUTUMN, 30000));   // 初期データと同じ

		assertThatThrownBy(() -> service.delete(userId, category.getId()))
				.isInstanceOfSatisfying(DeletionNotAllowedException.class,
						e -> assertThat(e.getMessageKey()).isEqualTo("error.delete.budgetNotZero"));
	}

	/** 理由が重なるときは、「使用中」だけを知らせる（F1） */
	@Test
	void whenBothReasonsApplyOnlyInUseIsReported() {
		Long userId = createUser("cattest23");
		CategoryService service = service();
		Category category = categoryRepository.save(new Category(userId, "交通費"));
		budgetRepository.save(new Budget(userId, category, 2026, Season.AUTUMN, 30000));    // 0円でない予算もある
		saveTransactionOf(userId, category);                                                  // 使用中

		assertThatThrownBy(() -> service.delete(userId, category.getId()))
				.isInstanceOfSatisfying(DeletionNotAllowedException.class,
						e -> assertThat(e.getMessageKey()).isEqualTo("error.delete.inUse"));
	}

	/** 他のユーザーの予算・取引は、削除の判定に関係しない */
	@Test
	void otherUsersDataDoesNotAffectDeletion() {
		Long a = createUser("cattest24");
		Long b = createUser("cattest25");
		CategoryService service = service();
		Category aCategory = service.add(a, form("間食"));
		Category bCategory = service.add(b, form("間食"));
		budgetRepository.save(new Budget(b, bCategory, 2026, Season.SPRING, 9999));   // B の予算は0円でない
		saveTransactionOf(b, bCategory);                                              // B の科目は使用中

		assertThatCode(() -> service.delete(a, aCategory.getId())).doesNotThrowAnyException();

		assertThat(categoryExists(aCategory.getId())).isFalse();
		assertThat(categoryExists(bCategory.getId())).isTrue();                       // B のものは、そのまま
		assertThat(budgetsOf(b, bCategory.getId())).hasSize(2);
	}

	private void saveTransactionOf(Long userId, Category category) {
		Vendor vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		PaymentMethod paymentMethod = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 27), 1000, category, null, vendor, paymentMethod));
		entityManager.flush();
	}

}
