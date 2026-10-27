package io.github.yutaro081.householdbudget.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.CategoryForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 科目の一覧・追加・変更・削除（設計書 4.5、5.4、5.5、5.6）。
 * <p>
 * 【決まり】すべてのメソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * ID で探すときは、必ず「ID と user_id」の両方で絞る。見つからなければ NotFoundException
 * （他人のものか、存在しないかは区別しない、設計書 3.9、7.8）。
 * 更新するメソッドは、1つのトランザクションで行い、途中で失敗したら全部取り消される。
 */
@Service
public class CategoryService {

	/** 科目を追加したときに作る、今の季節の予算の額（0円、要件4.3） */
	static final int NEW_CATEGORY_BUDGET_AMOUNT = 0;

	private final CategoryRepository categoryRepository;
	private final BudgetRepository budgetRepository;
	private final TransactionRepository transactionRepository;
	private final Clock clock;

	public CategoryService(CategoryRepository categoryRepository,
			BudgetRepository budgetRepository,
			TransactionRepository transactionRepository,
			Clock clock) {
		this.categoryRepository = categoryRepository;
		this.budgetRepository = budgetRepository;
		this.transactionRepository = transactionRepository;
		this.clock = clock;
	}

	/** 自分の科目を、登録順で返す。 */
	@Transactional(readOnly = true)
	public List<Category> list(Long userId) {
		return categoryRepository.findByUserIdOrderByIdAsc(userId);
	}

	/** 自分の科目を1件返す（変更画面用）。 */
	@Transactional(readOnly = true)
	public Category get(Long userId, Long id) {
		return find(userId, id);
	}

	/**
	 * 科目を追加する。同じトランザクションで、今の季節の予算0円の行も作る（ほかの季節は作らない）。
	 *
	 * @throws DuplicateException 自分の中に、同じ名前がすでにあるとき
	 */
	@Transactional
	public Category add(Long userId, CategoryForm form) {
		String name = form.getName();
		if (categoryRepository.existsByUserIdAndName(userId, name)) {
			throw new DuplicateException("category name already exists");
		}

		Category category;
		try {
			category = categoryRepository.saveAndFlush(new Category(userId, name));
		} catch (DataIntegrityViolationException e) {
			// 同時に追加されて、確認をすり抜けた場合は、DB の一意制約が拒否する（5.6）
			throw new DuplicateException("category name already exists", e);
		}

		SeasonPeriod current = SeasonPeriod.from(LocalDate.now(clock));
		budgetRepository.save(new Budget(userId, category, current.year(), current.season(), NEW_CATEGORY_BUDGET_AMOUNT));
		return category;
	}

	/**
	 * 科目の名前を変える。自分自身の今の名前は、重複としない（名前を変えずに保存してもエラーにしない）。
	 *
	 * @throws NotFoundException  存在しない、または他人の科目
	 * @throws DuplicateException この科目以外で、自分の中に同じ名前があるとき
	 */
	@Transactional
	public void update(Long userId, Long id, CategoryForm form) {
		Category category = find(userId, id);
		String name = form.getName();
		if (categoryRepository.existsByUserIdAndNameAndIdNot(userId, name, id)) {
			throw new DuplicateException("category name already exists");
		}

		category.rename(name);
		try {
			categoryRepository.saveAndFlush(category);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateException("category name already exists", e);
		}
	}

	/**
	 * 科目を削除する（設計書 5.5、要件4.3）。次の順番で確認する。
	 * <ol>
	 * <li>取引で使われていれば削除できない（error.delete.inUse）</li>
	 * <li>いずれかの季節に0円でない予算があれば削除できない（error.delete.budgetNotZero）。
	 * 予算の行がない季節（未設定）は、0円として扱うので、さまたげない</li>
	 * </ol>
	 * 理由が重なるときは、最初の「使用中」だけを知らせる（F1）。
	 * 削除できるときは、その科目の予算の行をどの季節のものもすべて消してから、科目を消す（3.7）。
	 *
	 * @throws NotFoundException           存在しない、または他人の科目
	 * @throws DeletionNotAllowedException 削除できないとき（理由のメッセージのキーを持つ）
	 */
	@Transactional
	public void delete(Long userId, Long id) {
		Category category = find(userId, id);

		if (transactionRepository.existsByUserIdAndCategoryId(userId, id)) {
			throw new DeletionNotAllowedException("error.delete.inUse");
		}
		if (budgetRepository.existsByUserIdAndCategoryIdAndAmountGreaterThan(userId, id, 0)) {
			throw new DeletionNotAllowedException("error.delete.budgetNotZero");
		}

		budgetRepository.deleteByUserIdAndCategoryId(userId, id);
		categoryRepository.delete(category);
		try {
			categoryRepository.flush();
		} catch (DataIntegrityViolationException e) {
			// 判定をすり抜けて、DB の外部キーに拒否された場合（同時操作など）も、使用中として扱う（5.5）
			throw new DeletionNotAllowedException("error.delete.inUse", e);
		}
	}

	/** ID と user_id の両方で探す。なければ NotFoundException。 */
	private Category find(Long userId, Long id) {
		return categoryRepository.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
	}

}
