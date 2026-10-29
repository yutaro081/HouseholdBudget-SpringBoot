package io.github.yutaro081.householdbudget.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.TransactionForm;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 取引の一覧・詳細・登録・更新・削除（設計書 4.5、1.4 の例1、3.9、5.5）。
 * <p>
 * 【決まり】すべてのメソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * ID で探すときは、必ず「ID と user_id」の両方で絞る。見つからなければ NotFoundException
 * （他人のものか、存在しないかは区別しない、設計書 3.9、7.8）。
 * 更新するメソッドは、1つのトランザクションで行い、途中で失敗したら全部取り消される。
 * <p>
 * 登録・更新で選ばれた科目・取引先・支払方法は、Phase 4 の各 Service の get(userId, id) で取り出す。
 * これが「自分のものか」の確認になる（他人のもの・存在しないものは NotFoundException。設計書 3.9、6.7）。
 * 自分で Repository の findById は呼ばない。
 */
@Service
public class TransactionService {

	private final TransactionRepository transactionRepository;
	private final CategoryService categoryService;
	private final VendorService vendorService;
	private final PaymentMethodService paymentMethodService;

	public TransactionService(TransactionRepository transactionRepository,
			CategoryService categoryService,
			VendorService vendorService,
			PaymentMethodService paymentMethodService) {
		this.transactionRepository = transactionRepository;
		this.categoryService = categoryService;
		this.vendorService = vendorService;
		this.paymentMethodService = paymentMethodService;
	}

	/**
	 * 自分の取引を、日付の新しい順（同じ日は登録の新しい順）で全件返す（Q4・Q5）。
	 * 科目・取引先・支払方法も一緒に読み込んである（設計書 4.6）。
	 */
	@Transactional(readOnly = true)
	public List<Transaction> list(Long userId) {
		return transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(userId);
	}

	/**
	 * 自分の取引を1件返す（詳細・編集画面用）。
	 *
	 * @throws NotFoundException 存在しない、または他人の取引
	 */
	@Transactional(readOnly = true)
	public Transaction get(Long userId, Long id) {
		return find(userId, id);
	}

	/**
	 * 取引を登録する。持ち主は userId（画面の値は使わない）。
	 *
	 * @throws NotFoundException 科目・取引先・支払方法のどれかが、存在しない、または他人のもの（何も保存しない）
	 */
	@Transactional
	public Transaction register(Long userId, TransactionForm form) {
		Category category = categoryService.get(userId, form.getCategoryId());
		Vendor vendor = vendorService.get(userId, form.getVendorId());
		PaymentMethod paymentMethod = paymentMethodService.get(userId, form.getPaymentMethodId());

		return transactionRepository.save(new Transaction(userId, form.getTransactionDate(), form.getAmount(),
				category, form.getDescription(), vendor, paymentMethod));
	}

	/**
	 * 取引を更新する。持ち主は変わらない。
	 *
	 * @throws NotFoundException 取引が、存在しない、または他人のもの。科目・取引先・支払方法のどれかが、
	 *                           存在しない、または他人のもの（どちらの場合も、取引は変わらない）
	 */
	@Transactional
	public void update(Long userId, Long id, TransactionForm form) {
		Transaction transaction = find(userId, id);
		Category category = categoryService.get(userId, form.getCategoryId());
		Vendor vendor = vendorService.get(userId, form.getVendorId());
		PaymentMethod paymentMethod = paymentMethodService.get(userId, form.getPaymentMethodId());

		transaction.update(form.getTransactionDate(), form.getAmount(), category, form.getDescription(),
				vendor, paymentMethod);
		transactionRepository.saveAndFlush(transaction);
	}

	/**
	 * 取引を削除する（物理削除。行そのものを DB から消す、Q2）。
	 *
	 * @throws NotFoundException 存在しない、または他人の取引
	 */
	@Transactional
	public void delete(Long userId, Long id) {
		Transaction transaction = find(userId, id);
		transactionRepository.delete(transaction);
	}

	/** ID と user_id の両方で探す。なければ NotFoundException。 */
	private Transaction find(Long userId, Long id) {
		return transactionRepository.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
	}

}
