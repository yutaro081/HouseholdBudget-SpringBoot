package io.github.yutaro081.householdbudget.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.dto.TransactionMonth;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.TransactionForm;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;

/**
 * 取引の一覧・1件の取得（編集・複写の元）・登録・更新・削除（設計書 4.5、1.4 の例1、3.9、5.5）。
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
	 * 自分の取引を、月ごとにまとめて返す（取引一覧の月の見出し用。issue 025）。
	 * 新しい月が先。月の中は、{@link #list(Long)} と同じ並び。
	 */
	@Transactional(readOnly = true)
	public List<TransactionMonth> listByMonth(Long userId) {
		// 検索は list と同じ 1 回（ユーザーの絞り込みも同じ）。並びを保ったまま、月ごとに分ける
		Map<YearMonth, List<Transaction>> byMonth = new LinkedHashMap<>();
		for (Transaction transaction : list(userId)) {
			byMonth.computeIfAbsent(YearMonth.from(transaction.getTransactionDate()), key -> new ArrayList<>()).add(transaction);
		}
		List<TransactionMonth> months = new ArrayList<>();
		byMonth.forEach((month, transactions) -> {
			long total = 0;
			for (Transaction transaction : transactions) {
				total += transaction.getAmount();
			}
			months.add(new TransactionMonth(month, transactions.size(), total, List.copyOf(transactions)));
		});
		return List.copyOf(months);
	}

	/**
	 * 自分の取引を1件返す（編集・複写・削除のとき。編集画面を開くときと、複写の元を取り出すときにも使う）。
	 *
	 * @throws NotFoundException 存在しない、または他人の取引
	 */
	@Transactional(readOnly = true)
	public Transaction get(Long userId, Long id) {
		return find(userId, id);
	}

	/**
	 * 取引を登録する。空でない明細の行ごとに 1 件（issue 032）。日付・取引先・支払方法は、全部の行で同じ。行の順に登録する。
	 * 持ち主は userId（画面の値は使わない）。
	 * <p>
	 * 【守り】先に、取引先・支払方法と、全部の行の科目が自分のものかを確かめ、そのあとで書く（行ごとに確かめて書く形にしない）。
	 * 1 つでも他人のもの・存在しないものがあれば、1 件も書かない。1 つのトランザクションなので、途中で失敗しても全部取り消される（二重の守り）。
	 *
	 * @return 登録した取引（行の順）
	 * @throws NotFoundException 科目・取引先・支払方法のどれかが、存在しない、または他人のもの（何も保存しない）
	 */
	@Transactional
	public List<Transaction> register(Long userId, TransactionForm form) {
		Vendor vendor = vendorService.get(userId, form.getVendorId());
		PaymentMethod paymentMethod = paymentMethodService.get(userId, form.getPaymentMethodId());
		List<TransactionForm.Line> lines = form.filledLines();
		List<Category> categories = new ArrayList<>();
		for (TransactionForm.Line line : lines) {
			categories.add(categoryService.get(userId, line.getCategoryId()));
		}

		List<Transaction> saved = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			TransactionForm.Line line = lines.get(i);
			saved.add(transactionRepository.save(new Transaction(userId, form.getTransactionDate(), line.getAmount(),
					categories.get(i), line.getDescription(), vendor, paymentMethod)));
		}
		return List.copyOf(saved);
	}

	/**
	 * 取引を更新する。持ち主は変わらない。明細は 1 行（issue 032 の段階 2。Controller が 2 行以上を 400 にしてある）。
	 *
	 * @throws NotFoundException 取引が、存在しない、または他人のもの。科目・取引先・支払方法のどれかが、
	 *                           存在しない、または他人のもの（どちらの場合も、取引は変わらない）
	 */
	@Transactional
	public void update(Long userId, Long id, TransactionForm form) {
		Transaction transaction = find(userId, id);
		TransactionForm.Line line = form.filledLines().get(0);
		Category category = categoryService.get(userId, line.getCategoryId());
		Vendor vendor = vendorService.get(userId, form.getVendorId());
		PaymentMethod paymentMethod = paymentMethodService.get(userId, form.getPaymentMethodId());

		transaction.update(form.getTransactionDate(), line.getAmount(), category, line.getDescription(),
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
