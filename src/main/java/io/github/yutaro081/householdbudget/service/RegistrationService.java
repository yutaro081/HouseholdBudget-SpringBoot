package io.github.yutaro081.householdbudget.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
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
 * アカウント登録（設計書 4.5、5.3、3.11）。
 * ユーザーと初期データ（科目・今の季節の予算・取引先・支払方法）を、1つのトランザクションで作る。
 * 途中で失敗したら、ユーザーも含めてすべて取り消される。
 * <p>
 * 初期データは CategoryService ではなく、ここで Repository を使って直接作る。
 * 科目の追加（CategoryService）は予算を0円で作るが、初期の科目は30,000円で作るため（5.3）。
 */
@Service
public class RegistrationService {

	/** 初期の予算額（要件4.3：今の季節の予算の初期値は、それぞれ3万円） */
	static final int INITIAL_BUDGET_AMOUNT = 30_000;

	/** 初期の科目（3.11 の順番。id の順＝表示の順、Q14） */
	static final String[] INITIAL_CATEGORIES = { "交通費", "食費", "雑費" };

	/** 初期の取引先 */
	static final String[] INITIAL_VENDORS = { "コンビニ", "ガソリンスタンド", "Amazon" };

	/** 初期の支払方法 */
	static final String[] INITIAL_PAYMENT_METHODS = { "現金", "クレジットカード" };

	private final UserRepository userRepository;
	private final CategoryRepository categoryRepository;
	private final BudgetRepository budgetRepository;
	private final VendorRepository vendorRepository;
	private final PaymentMethodRepository paymentMethodRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public RegistrationService(UserRepository userRepository,
			CategoryRepository categoryRepository,
			BudgetRepository budgetRepository,
			VendorRepository vendorRepository,
			PaymentMethodRepository paymentMethodRepository,
			PasswordEncoder passwordEncoder,
			Clock clock) {
		this.userRepository = userRepository;
		this.categoryRepository = categoryRepository;
		this.budgetRepository = budgetRepository;
		this.vendorRepository = vendorRepository;
		this.paymentMethodRepository = paymentMethodRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * ユーザーと初期データを作る。
	 *
	 * @throws DuplicateException 同じユーザー名がすでにあるとき（大文字・小文字の違いは同じとみなす、Q10）
	 */
	@Transactional
	public void register(RegisterForm form) {
		String username = form.getUsername();

		// 1. 同じユーザー名があるか確認する
		if (userRepository.existsByUsername(username)) {
			throw new DuplicateException("username already exists");
		}

		// 2. パスワードを BCrypt でハッシュ化する（元のパスワードは保存しない・ログに出さない、7.2）
		String passwordHash = passwordEncoder.encode(form.getPassword());

		// 3. ユーザーを保存する。同時に同じユーザー名で登録されて確認をすり抜けた場合は、
		//    DB の一意制約が拒否する。そのエラーも DuplicateException にする（5.3）
		User user;
		try {
			user = userRepository.saveAndFlush(new User(username, passwordHash));
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateException("username already exists", e);
		}
		Long userId = user.getId();

		// 4. 科目と、それぞれの今の季節の予算（日本時間の今日から求める、B6）
		SeasonPeriod current = SeasonPeriod.from(LocalDate.now(clock));
		for (String name : INITIAL_CATEGORIES) {
			Category category = categoryRepository.save(new Category(userId, name));
			budgetRepository.save(new Budget(userId, category, current.year(), current.season(), INITIAL_BUDGET_AMOUNT));
		}

		// 5. 取引先
		for (String name : INITIAL_VENDORS) {
			vendorRepository.save(new Vendor(userId, name));
		}

		// 6. 支払方法
		for (String name : INITIAL_PAYMENT_METHODS) {
			paymentMethodRepository.save(new PaymentMethod(userId, name));
		}
	}

}
