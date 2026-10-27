package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.form.RegisterForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;

/**
 * RegistrationService のテスト（設計書 5.3、3.11）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない、Q3-8）。
 * 「今の季節」は、日付を固定した Clock で確かめる。
 * <p>
 * 注意：このテストの中では、テスト全体が1つのトランザクションなので、
 * 「途中で失敗したら全部取り消す」ことは確かめられない（画面と SQL で確かめる、手順書 4.13 の(8)）。
 */
@SpringBootTest
@Transactional
class RegistrationServiceTest {

	/** テスト用のダミーの値（実在するアカウントのパスワードではない） */
	private static final String DUMMY_PASSWORD = "dummy-Pass-1";

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
	private PasswordEncoder passwordEncoder;

	/** 日本時間の指定した日の0時に固定した Clock で動く RegistrationService */
	private RegistrationService serviceOn(String isoDate) {
		Clock clock = Clock.fixed(LocalDate.parse(isoDate).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		return serviceWith(clock);
	}

	private RegistrationService serviceWith(Clock clock) {
		return new RegistrationService(userRepository, categoryRepository, budgetRepository,
				vendorRepository, paymentMethodRepository, passwordEncoder, clock);
	}

	private static RegisterForm form(String username) {
		RegisterForm form = new RegisterForm();
		form.setUsername(username);
		form.setPassword(DUMMY_PASSWORD);
		return form;
	}

	private User findUser(String username) {
		return userRepository.findByUsername(username).orElseThrow();
	}

	private List<Category> categoriesOf(Long userId) {
		return categoryRepository.findAll().stream()
				.filter(c -> c.getUserId().equals(userId)).sorted(Comparator.comparing(Category::getId)).toList();
	}

	private List<Budget> budgetsOf(Long userId) {
		return budgetRepository.findAll().stream()
				.filter(b -> b.getUserId().equals(userId)).sorted(Comparator.comparing(Budget::getId)).toList();
	}

	private List<Vendor> vendorsOf(Long userId) {
		return vendorRepository.findAll().stream()
				.filter(v -> v.getUserId().equals(userId)).sorted(Comparator.comparing(Vendor::getId)).toList();
	}

	private List<PaymentMethod> paymentMethodsOf(Long userId) {
		return paymentMethodRepository.findAll().stream()
				.filter(p -> p.getUserId().equals(userId)).sorted(Comparator.comparing(PaymentMethod::getId)).toList();
	}

	// ---- 初期データ（3.11） ----

	@Test
	void createsInitialData() {
		serviceOn("2026-10-27").register(form("regtest1"));

		Long userId = findUser("regtest1").getId();

		// 科目3つ（この順の id）
		List<Category> categories = categoriesOf(userId);
		assertThat(categories).extracting(Category::getName).containsExactly("交通費", "食費", "雑費");

		// 予算3つ。それぞれの科目に30,000円、今の季節（2026年の秋）の分だけ
		List<Budget> budgets = budgetsOf(userId);
		assertThat(budgets).hasSize(3);
		assertThat(budgets).extracting(b -> b.getCategory().getName()).containsExactly("交通費", "食費", "雑費");
		assertThat(budgets).extracting(Budget::getAmount).containsOnly(30000);
		assertThat(budgets).extracting(Budget::getSeasonYear).containsOnly(2026);
		assertThat(budgets).extracting(Budget::getSeason).containsOnly(Season.AUTUMN);

		// 取引先3つ、支払方法2つ（この順の id）
		assertThat(vendorsOf(userId)).extracting(Vendor::getName).containsExactly("コンビニ", "ガソリンスタンド", "Amazon");
		assertThat(paymentMethodsOf(userId)).extracting(PaymentMethod::getName).containsExactly("現金", "クレジットカード");
	}

	/** 「今の季節」は、固定した日付（日本時間）から決まる。冬の1月・2月は前の年 */
	@ParameterizedTest(name = "{0} → {1}年 {2}")
	@CsvSource({
			"2027-02-28, 2026, WINTER",
			"2027-01-15, 2026, WINTER",
			"2026-12-01, 2026, WINTER",
			"2026-11-30, 2026, AUTUMN",
			"2027-03-01, 2027, SPRING",
			"2028-02-29, 2027, WINTER"
	})
	void initialBudgetUsesCurrentSeason(String date, int expectedYear, Season expectedSeason) {
		serviceOn(date).register(form("regtest2"));

		List<Budget> budgets = budgetsOf(findUser("regtest2").getId());

		assertThat(budgets).hasSize(3);
		assertThat(budgets).extracting(Budget::getSeasonYear).containsOnly(expectedYear);
		assertThat(budgets).extracting(Budget::getSeason).containsOnly(expectedSeason);
	}

	/** 世界標準時（UTC）では2月28日の16時でも、日本時間では3月1日。日本時間で季節が決まる（B6） */
	@Test
	void seasonFollowsJapanTime() {
		Clock clock = Clock.fixed(Instant.parse("2027-02-28T16:00:00Z"), AppConfig.JAPAN);

		serviceWith(clock).register(form("regtest3"));

		List<Budget> budgets = budgetsOf(findUser("regtest3").getId());
		assertThat(budgets).extracting(Budget::getSeasonYear).containsOnly(2027);
		assertThat(budgets).extracting(Budget::getSeason).containsOnly(Season.SPRING);
	}

	// ---- パスワードのハッシュ化（7.2） ----

	@Test
	void passwordIsHashed() {
		serviceOn("2026-10-27").register(form("regtest4"));

		String hash = findUser("regtest4").getPasswordHash();

		assertThat(hash).isNotEqualTo(DUMMY_PASSWORD);
		assertThat(hash).doesNotContain(DUMMY_PASSWORD);
		assertThat(hash).startsWith("$2"); // BCrypt
		assertThat(hash).hasSize(60);
		assertThat(passwordEncoder.matches(DUMMY_PASSWORD, hash)).isTrue();
		assertThat(passwordEncoder.matches("another-Pass-9", hash)).isFalse();
	}

	@Test
	void sameRawPasswordGivesDifferentHashes() {
		RegistrationService service = serviceOn("2026-10-27");
		service.register(form("regtest5"));
		service.register(form("regtest6"));

		assertThat(findUser("regtest5").getPasswordHash()).isNotEqualTo(findUser("regtest6").getPasswordHash());
	}

	// ---- ユーザー名の重複（5.3、Q10） ----

	@ParameterizedTest(name = "[{0}] の後に [{1}] は重複")
	@CsvSource({
			"regdup1, regdup1",
			"regdup2, REGDUP2",
			"RegDup3, regdup3"
	})
	void duplicateUsernameIsRejected(String first, String second) {
		RegistrationService service = serviceOn("2026-10-27");
		service.register(form(first));
		int usersBefore = userRepository.findAll().size();
		int categoriesBefore = categoryRepository.findAll().size();

		assertThatThrownBy(() -> service.register(form(second))).isInstanceOf(DuplicateException.class);

		// 重複のときは、何も増えない
		assertThat(userRepository.findAll()).hasSize(usersBefore);
		assertThat(categoryRepository.findAll()).hasSize(categoriesBefore);
	}

	// ---- 2人分が別々（3.9、3.11） ----

	@Test
	void eachUserGetsSeparateInitialData() {
		RegistrationService service = serviceOn("2026-10-27");
		service.register(form("regtest7"));
		service.register(form("regtest8"));

		Long id7 = findUser("regtest7").getId();
		Long id8 = findUser("regtest8").getId();
		assertThat(id7).isNotEqualTo(id8);

		// それぞれ、科目3・予算3・取引先3・支払方法2
		for (Long id : List.of(id7, id8)) {
			assertThat(categoriesOf(id)).hasSize(3);
			assertThat(budgetsOf(id)).hasSize(3);
			assertThat(vendorsOf(id)).hasSize(3);
			assertThat(paymentMethodsOf(id)).hasSize(2);
		}

		// 行は共有されない（id が重ならない）。予算は自分の科目を指す
		List<Long> categoryIds7 = categoriesOf(id7).stream().map(Category::getId).toList();
		List<Long> categoryIds8 = categoriesOf(id8).stream().map(Category::getId).toList();
		assertThat(categoryIds7).doesNotContainAnyElementsOf(categoryIds8);
		assertThat(budgetsOf(id7)).extracting(b -> b.getCategory().getId()).containsExactlyElementsOf(categoryIds7);
		assertThat(budgetsOf(id8)).extracting(b -> b.getCategory().getId()).containsExactlyElementsOf(categoryIds8);
		assertThat(vendorsOf(id7)).extracting(Vendor::getId).doesNotContainAnyElementsOf(vendorsOf(id8).stream().map(Vendor::getId).toList());
		assertThat(paymentMethodsOf(id7)).extracting(PaymentMethod::getId).doesNotContainAnyElementsOf(paymentMethodsOf(id8).stream().map(PaymentMethod::getId).toList());
	}

}
