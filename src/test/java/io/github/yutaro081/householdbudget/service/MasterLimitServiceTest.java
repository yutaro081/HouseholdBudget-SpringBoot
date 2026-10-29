package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Limits;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.LimitExceededException;
import io.github.yutaro081.householdbudget.form.CategoryForm;
import io.github.yutaro081.householdbudget.form.PaymentMethodForm;
import io.github.yutaro081.householdbudget.form.VendorForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * 科目・取引先・支払方法の、1人あたりの個数の上限（256個、R14）のテスト（設計書 5.4、6.2）。
 * 256個目までは追加でき、257個目は追加できない。ほかのユーザーの個数は数えない。削除すれば、また追加できる。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@Transactional
class MasterLimitServiceTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private CategoryService categoryService;
	@Autowired
	private VendorService vendorService;
	@Autowired
	private PaymentMethodService paymentMethodService;
	@Autowired
	private EntityManager entityManager;

	private static final int LIMIT = 256;

	private Long createUser(String username) {
		return userRepository.save(new User(username, "dummy-hash-for-test")).getId();
	}

	private static CategoryForm categoryForm(String name) {
		CategoryForm form = new CategoryForm();
		form.setName(name);
		return form;
	}

	private static VendorForm vendorForm(String name) {
		VendorForm form = new VendorForm();
		form.setName(name);
		return form;
	}

	private static PaymentMethodForm paymentMethodForm(String name) {
		PaymentMethodForm form = new PaymentMethodForm();
		form.setName(name);
		return form;
	}

	/** その人の科目を、count 個まで、直接（Repository で）作る。名前は 「c0」「c1」… */
	private void fillCategories(Long userId, int count) {
		List<Category> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new Category(userId, "c" + i));
		}
		categoryRepository.saveAll(list);
		entityManager.flush();
	}

	private void fillVendors(Long userId, int count) {
		List<Vendor> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new Vendor(userId, "v" + i));
		}
		vendorRepository.saveAll(list);
		entityManager.flush();
	}

	private void fillPaymentMethods(Long userId, int count) {
		List<PaymentMethod> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new PaymentMethod(userId, "p" + i));
		}
		paymentMethodRepository.saveAll(list);
		entityManager.flush();
	}

	// ---- 上限の数 ----

	@Test
	void limitIs256WhichEqualsTheNumberOfRowsTheBudgetScreenCanReceive() {
		assertThat(Limits.MAX_MASTERS_PER_USER).isEqualTo(256);
		assertThat(Limits.MAX_MASTERS_PER_USER).isEqualTo(LIMIT);
	}

	// ================================================================
	// 科目
	// ================================================================

	/** 256個目は追加できる（255個ある → 追加できて256個）。257個目は追加できない */
	@Test
	void categoryThe256thCanBeAddedAndThe257thCannot() {
		Long u = createUser("lim1");
		fillCategories(u, LIMIT - 1);

		categoryService.add(u, categoryForm("256個目"));
		assertThat(categoryService.list(u)).hasSize(LIMIT);

		assertThatThrownBy(() -> categoryService.add(u, categoryForm("257個目")))
				.isInstanceOf(LimitExceededException.class);
		assertThat(categoryService.list(u)).hasSize(LIMIT);
		assertThat(categoryService.list(u)).extracting(Category::getName).doesNotContain("257個目");
	}

	/** 追加できなかったとき、予算の行（科目を追加すると作られる、今の季節の0円）も作られない */
	@Test
	void categoryRejectedByTheLimitCreatesNoBudgetRow() {
		Long u = createUser("lim2");
		fillCategories(u, LIMIT);
		long before = budgetRepository.count();

		assertThatThrownBy(() -> categoryService.add(u, categoryForm("追加できない")))
				.isInstanceOf(LimitExceededException.class);

		assertThat(budgetRepository.count()).isEqualTo(before);
	}

	/** 256個ある人が、同じ名前を追加しようとしたとき、上限のほうが先に確かめられる */
	@Test
	void categoryLimitIsCheckedBeforeDuplicate() {
		Long u = createUser("lim3");
		fillCategories(u, LIMIT);

		assertThatThrownBy(() -> categoryService.add(u, categoryForm("c0"))).isInstanceOf(LimitExceededException.class);
	}

	/** 256個未満のときは、これまでどおり、同じ名前は重複 */
	@Test
	void categoryDuplicateIsStillDuplicateBelowTheLimit() {
		Long u = createUser("lim4");
		fillCategories(u, LIMIT - 1);

		assertThatThrownBy(() -> categoryService.add(u, categoryForm("c0"))).isInstanceOf(DuplicateException.class);
	}

	/** ほかのユーザーの個数は、数えない */
	@Test
	void categoryLimitIsPerUser() {
		Long a = createUser("lim5");
		Long b = createUser("lim6");
		fillCategories(a, LIMIT);

		categoryService.add(b, categoryForm("Bの科目"));

		assertThat(categoryService.list(b)).hasSize(1);
		assertThatThrownBy(() -> categoryService.add(a, categoryForm("Aの追加"))).isInstanceOf(LimitExceededException.class);
	}

	/** 変更はできる。削除すると、また追加できる */
	@Test
	void categoryCanBeRenamedAtTheLimitAndAddedAgainAfterDeleting() {
		Long u = createUser("lim7");
		fillCategories(u, LIMIT);
		Long first = categoryService.list(u).get(0).getId();

		categoryService.update(u, first, categoryForm("改名"));
		assertThat(categoryService.get(u, first).getName()).isEqualTo("改名");

		categoryService.delete(u, first);
		categoryService.add(u, categoryForm("また追加"));
		assertThat(categoryService.list(u)).hasSize(LIMIT);
		assertThatThrownBy(() -> categoryService.add(u, categoryForm("さらに追加"))).isInstanceOf(LimitExceededException.class);
	}

	// ================================================================
	// 取引先
	// ================================================================

	@Test
	void vendorThe256thCanBeAddedAndThe257thCannot() {
		Long u = createUser("lim8");
		fillVendors(u, LIMIT - 1);

		vendorService.add(u, vendorForm("256個目"));
		assertThat(vendorService.list(u)).hasSize(LIMIT);

		assertThatThrownBy(() -> vendorService.add(u, vendorForm("257個目"))).isInstanceOf(LimitExceededException.class);
		assertThat(vendorService.list(u)).hasSize(LIMIT);
	}

	@Test
	void vendorLimitIsCheckedBeforeDuplicateAndIsPerUserAndFreedByDeleting() {
		Long a = createUser("lim9");
		Long b = createUser("lim10");
		fillVendors(a, LIMIT);

		assertThatThrownBy(() -> vendorService.add(a, vendorForm("v0"))).isInstanceOf(LimitExceededException.class);
		vendorService.add(b, vendorForm("Bの取引先"));
		assertThat(vendorService.list(b)).hasSize(1);

		vendorService.delete(a, vendorService.list(a).get(0).getId());
		vendorService.add(a, vendorForm("また追加"));
		assertThat(vendorService.list(a)).hasSize(LIMIT);
	}

	@Test
	void vendorDuplicateIsStillDuplicateBelowTheLimit() {
		Long u = createUser("lim11");
		fillVendors(u, LIMIT - 1);

		assertThatThrownBy(() -> vendorService.add(u, vendorForm("v0"))).isInstanceOf(DuplicateException.class);
	}

	// ================================================================
	// 支払方法
	// ================================================================

	@Test
	void paymentMethodThe256thCanBeAddedAndThe257thCannot() {
		Long u = createUser("lim12");
		fillPaymentMethods(u, LIMIT - 1);

		paymentMethodService.add(u, paymentMethodForm("256個目"));
		assertThat(paymentMethodService.list(u)).hasSize(LIMIT);

		assertThatThrownBy(() -> paymentMethodService.add(u, paymentMethodForm("257個目"))).isInstanceOf(LimitExceededException.class);
		assertThat(paymentMethodService.list(u)).hasSize(LIMIT);
	}

	@Test
	void paymentMethodLimitIsCheckedBeforeDuplicateAndIsPerUserAndFreedByDeleting() {
		Long a = createUser("lim13");
		Long b = createUser("lim14");
		fillPaymentMethods(a, LIMIT);

		assertThatThrownBy(() -> paymentMethodService.add(a, paymentMethodForm("p0"))).isInstanceOf(LimitExceededException.class);
		paymentMethodService.add(b, paymentMethodForm("B払"));
		assertThat(paymentMethodService.list(b)).hasSize(1);

		paymentMethodService.delete(a, paymentMethodService.list(a).get(0).getId());
		paymentMethodService.add(a, paymentMethodForm("また追加"));
		assertThat(paymentMethodService.list(a)).hasSize(LIMIT);
	}

	@Test
	void paymentMethodDuplicateIsStillDuplicateBelowTheLimit() {
		Long u = createUser("lim15");
		fillPaymentMethods(u, LIMIT - 1);

		assertThatThrownBy(() -> paymentMethodService.add(u, paymentMethodForm("p0"))).isInstanceOf(DuplicateException.class);
	}

	/** 3つの上限は、それぞれ別（科目が256個でも、取引先・支払方法は追加できる） */
	@Test
	void theThreeLimitsAreIndependent() {
		Long u = createUser("lim16");
		fillCategories(u, LIMIT);

		vendorService.add(u, vendorForm("取引先"));
		paymentMethodService.add(u, paymentMethodForm("支払"));

		assertThat(vendorService.list(u)).hasSize(1);
		assertThat(paymentMethodService.list(u)).hasSize(1);
	}

}
