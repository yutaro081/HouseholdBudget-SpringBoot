package io.github.yutaro081.householdbudget.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ReceiptTotal;
import io.github.yutaro081.householdbudget.dto.TransactionFilter;
import io.github.yutaro081.householdbudget.dto.TransactionListView;
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
 * 同じレシートの取引（issue 032。設計書 5.16）は、編集・削除・複写をまとめて行う。
 * <p>
 * 【決まり】すべてのメソッドの最初の引数は userId（ログイン中のユーザーのID。Controller が LoginUser から取り出して渡す）。
 * ID で探すときは、必ず「ID と user_id」の両方で絞る。見つからなければ NotFoundException
 * （他人のものか、存在しないかは区別しない、設計書 3.9、7.8）。同じレシートの取引は、入口の行を「ID と user_id」で探してから、
 * 「印 と user_id」で読む。印は画面から受け取らない（ここで付ける・外す）。
 * 更新するメソッドは、1つのトランザクションで行い、途中で失敗したら全部取り消される。
 * <p>
 * 登録・更新で選ばれた取引先・支払方法は、Phase 4 の各 Service の get(userId, id) で取り出す。科目は、自分の科目を 1 回で読んで照らし合わせる
 * （行の数で検索を増やさない。issue 032）。これが「自分のものか」の確認になる（他人のもの・存在しないものは NotFoundException。設計書 3.9、6.7）。
 * どれも、書く前に全部を確かめる（行ごとに確かめながら書かない）。自分で Repository の findById は呼ばない。
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
	 * 自分の取引を、一覧の順で全件返す（Q4・Q5。issue 032：日付の新しい順 → まとまりの新しい順 → まとまりの中は id の順）。
	 * 科目・取引先・支払方法も一緒に読み込んである（設計書 4.6）。
	 */
	@Transactional(readOnly = true)
	public List<Transaction> list(Long userId) {
		return transactionRepository.findForListByUserId(userId);
	}

	/**
	 * 自分の取引を、月ごとにまとめて返す（取引一覧の月の見出し用。issue 025）。
	 * 新しい月が先。月の中は、{@link #list(Long)} と同じ並び。
	 * 同じレシートの取引の合計と行の数（削除の確認の文。issue 032）も、同じ 1 回の検索の結果から作る。
	 */
	@Transactional(readOnly = true)
	public List<TransactionMonth> listByMonth(Long userId) {
		// 検索は list と同じ 1 回（ユーザーの絞り込みも同じ）。全部の行があるので、レシートの合計と行の数も、この結果から作る
		List<Transaction> rows = list(userId);
		return byMonth(rows, receiptsOf(rows));
	}

	/**
	 * 取引一覧の画面の値（issue 023。設計書 2.3、5.17）。条件がなければ {@link #listByMonth(Long)} と同じ（検索は 1 回）。
	 * 条件があれば、絞り込み用の検索で読み、月ごとに分ける。月と全体の件数・合計は、出した行から作る。
	 * <p>
	 * 【守り】同じレシートの合計と行の数（削除の確認の文）は、絞り込む前のレシート全体の値にする（削除では、出ていない行も消えるため）。
	 * 出ている行の印を集め、「印 と user_id」で 1 回で読む。出ていない行の数も数える。分割の行がなければ、読まない。
	 *
	 * @param filter 絞り込みの条件。Controller が確かめてから作ったもの（科目は自分の科目）。user_id は持たない
	 */
	@Transactional(readOnly = true)
	public TransactionListView listView(Long userId, TransactionFilter filter) {
		List<TransactionMonth> months;
		if (filter.isEmpty()) {
			months = listByMonth(userId);
		} else {
			SeasonPeriod period = filter.period();
			List<Transaction> rows = transactionRepository.findForListByUserIdAndFilter(userId, filter.categoryId(),
					period == null ? null : period.startDate(), period == null ? null : period.endDateExclusive());
			months = byMonth(rows, wholeReceiptsOf(userId, rows));
		}
		int count = 0;
		long total = 0;
		for (TransactionMonth month : months) {
			count += month.count();
			total += month.total();
		}
		return new TransactionListView(filter, months, count, total);
	}

	/** 並びを保ったまま、月ごとに分ける。月の件数と合計は、渡した行から。レシートの合計と行の数は whole（レシート全体の値）から */
	private static List<TransactionMonth> byMonth(List<Transaction> rows, Map<Long, ReceiptTotal> whole) {
		Map<YearMonth, List<Transaction>> byMonth = new LinkedHashMap<>();
		for (Transaction transaction : rows) {
			byMonth.computeIfAbsent(YearMonth.from(transaction.getTransactionDate()), key -> new ArrayList<>()).add(transaction);
		}
		List<TransactionMonth> months = new ArrayList<>();
		byMonth.forEach((month, transactions) -> {
			long total = 0;
			Map<Long, Integer> shown = new LinkedHashMap<>();   // 印 → この一覧に出ている行の数
			for (Transaction transaction : transactions) {
				total += transaction.getAmount();
				if (transaction.getReceiptGroup() != null) {
					shown.merge(transaction.getReceiptGroup(), 1, Integer::sum);
				}
			}
			Map<Long, TransactionMonth.Receipt> receiptOf = new LinkedHashMap<>();
			shown.forEach((group, shownLines) -> {
				ReceiptTotal receipt = whole.get(group);
				if (receipt == null) {
					// 出ている行は自分の行なので、「印 と user_id」で読めば必ずある。ないときは不具合（少ない値で文を作らない）
					throw new IllegalStateException("receipt total not found");
				}
				int lines = receipt.lines().intValue();
				receiptOf.put(group, new TransactionMonth.Receipt(receipt.total(), lines, lines - shownLines));
			});
			months.add(new TransactionMonth(month, transactions.size(), total, List.copyOf(transactions), Map.copyOf(receiptOf)));
		});
		return List.copyOf(months);
	}

	/** 全部の行があるとき（絞らない一覧）：渡した行から、レシートの合計と行の数を作る（検索を増やさない） */
	private static Map<Long, ReceiptTotal> receiptsOf(List<Transaction> rows) {
		Map<Long, long[]> sums = new HashMap<>();   // 印 → { 合計, 行の数 }
		for (Transaction transaction : rows) {
			if (transaction.getReceiptGroup() != null) {
				long[] sum = sums.computeIfAbsent(transaction.getReceiptGroup(), key -> new long[2]);
				sum[0] += transaction.getAmount();
				sum[1]++;
			}
		}
		Map<Long, ReceiptTotal> receipts = new HashMap<>();
		sums.forEach((group, sum) -> receipts.put(group, new ReceiptTotal(group, sum[0], sum[1])));
		return receipts;
	}

	/** 絞り込んだとき：出ている行の印を集め、「印 と user_id」で、レシート全体の合計と行の数を 1 回で読む。分割の行がなければ読まない */
	private Map<Long, ReceiptTotal> wholeReceiptsOf(Long userId, List<Transaction> rows) {
		Set<Long> groups = new LinkedHashSet<>();
		for (Transaction transaction : rows) {
			if (transaction.getReceiptGroup() != null) {
				groups.add(transaction.getReceiptGroup());
			}
		}
		if (groups.isEmpty()) {
			return Map.of();
		}
		Map<Long, ReceiptTotal> receipts = new HashMap<>();
		for (ReceiptTotal receipt : transactionRepository.sumByReceiptGroups(userId, groups)) {
			receipts.put(receipt.receiptGroup(), receipt);
		}
		return receipts;
	}

	/**
	 * 自分の取引を1件返す（変更の POST で、入力の正誤より先に、ID と user_id を確かめるとき。設計書 7.8）。
	 *
	 * @throws NotFoundException 存在しない、または他人の取引
	 */
	@Transactional(readOnly = true)
	public Transaction get(Long userId, Long id) {
		return find(userId, id);
	}

	/**
	 * 入口の取引を含む、同じレシートの取引の全部を、id の順（明細の行の順）で返す（編集の画面・複写の元。issue 032。設計書 5.16）。
	 * 印のない取引なら、その 1 件だけ。入口は「ID と user_id」で探し、まとまりは「印 と user_id」で読む。
	 *
	 * @throws NotFoundException 入口が、存在しない、または他人の取引
	 */
	@Transactional(readOnly = true)
	public List<Transaction> getReceipt(Long userId, Long id) {
		return receiptOf(userId, find(userId, id));
	}

	/**
	 * 取引を登録する。空でない明細の行ごとに 1 件（issue 032）。日付・取引先・支払方法は、全部の行で同じ。行の順に登録する。
	 * 2 行以上なら、全部の行に同じ印（最初の行の id）を付ける。1 行なら印なし。持ち主は userId（画面の値は使わない）。
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
		List<Category> categories = ownCategories(userId, lines);

		List<Transaction> saved = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			TransactionForm.Line line = lines.get(i);
			saved.add(transactionRepository.save(new Transaction(userId, form.getTransactionDate(), line.getAmount(),
					categories.get(i), line.getDescription(), vendor, paymentMethod)));
		}
		mark(saved);
		return List.copyOf(saved);
	}

	/**
	 * 取引を更新する（issue 032：同じレシートの取引は、まとめて。設計書 5.16）。持ち主は変わらない。
	 * <p>
	 * まとまりの取引（id の小さい順）に、空でない明細の行を上から当てる。当たった取引はその場で更新し（id を変えない）、
	 * 明細の行が多ければ取引を足し、少なければ余った取引を消す（id の大きいほうから）。日付・取引先・支払方法は、全部の取引に入れる。
	 * 印は、2 行以上なら最初の取引（必ず残る）の id、1 行なら外す。書く前に、取引先・支払方法と全部の行の科目を確かめる。
	 *
	 * @throws NotFoundException 取引が、存在しない、または他人のもの。科目・取引先・支払方法のどれかが、
	 *                           存在しない、または他人のもの（どちらの場合も、取引は変わらない）
	 */
	@Transactional
	public void update(Long userId, Long id, TransactionForm form) {
		List<Transaction> rows = receiptOf(userId, find(userId, id));
		Vendor vendor = vendorService.get(userId, form.getVendorId());
		PaymentMethod paymentMethod = paymentMethodService.get(userId, form.getPaymentMethodId());
		List<TransactionForm.Line> lines = form.filledLines();
		List<Category> categories = ownCategories(userId, lines);

		List<Transaction> kept = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			TransactionForm.Line line = lines.get(i);
			if (i < rows.size()) {
				Transaction row = rows.get(i);
				row.update(form.getTransactionDate(), line.getAmount(), categories.get(i), line.getDescription(), vendor, paymentMethod);
				kept.add(row);
			} else {
				kept.add(transactionRepository.save(new Transaction(userId, form.getTransactionDate(), line.getAmount(),
						categories.get(i), line.getDescription(), vendor, paymentMethod)));
			}
		}
		if (rows.size() > lines.size()) {
			transactionRepository.deleteAll(rows.subList(lines.size(), rows.size()));
		}
		mark(kept);
		transactionRepository.flush();
	}

	/**
	 * 取引を削除する（物理削除。行そのものを DB から消す、Q2）。同じレシートの取引は、まとめて消す（issue 032）。
	 *
	 * @throws NotFoundException 存在しない、または他人の取引
	 */
	@Transactional
	public void delete(Long userId, Long id) {
		transactionRepository.deleteAll(receiptOf(userId, find(userId, id)));
	}

	/** ID と user_id の両方で探す。なければ NotFoundException。 */
	private Transaction find(Long userId, Long id) {
		return transactionRepository.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
	}

	/** 入口の取引の、同じレシートの取引の全部（id の順）。印がなければ、入口の 1 件だけ。「印 と user_id」で読む。 */
	private List<Transaction> receiptOf(Long userId, Transaction entry) {
		if (entry.getReceiptGroup() == null) {
			return List.of(entry);
		}
		return transactionRepository.findByUserIdAndReceiptGroupOrderByIdAsc(userId, entry.getReceiptGroup());
	}

	/**
	 * 行の科目が、全部自分のものかを、書く前に確かめる。自分の科目を 1 回で読んで照らし合わせる（行の数で検索を増やさない）。
	 *
	 * @return 行の順の科目
	 * @throws NotFoundException 1 つでも、存在しない、または他人の科目
	 */
	private List<Category> ownCategories(Long userId, List<TransactionForm.Line> lines) {
		if (lines.isEmpty()) {
			// 入力チェック（「明細を 1 行以上」）を通らずに呼ばれたとき。更新でレシートの全部を消してしまわないよう、ここで止める
			throw new IllegalArgumentException("no filled line");
		}
		Map<Long, Category> own = new HashMap<>();
		for (Category category : categoryService.list(userId)) {
			own.put(category.getId(), category);
		}
		List<Category> categories = new ArrayList<>();
		for (TransactionForm.Line line : lines) {
			Category category = own.get(line.getCategoryId());
			if (category == null) {
				throw new NotFoundException();
			}
			categories.add(category);
		}
		return categories;
	}

	/** 印を付ける・外す：2 件以上なら、全部に最初の取引の id。1 件なら外す（「印がある ⇔ 2 行以上」）。印は画面から受け取らない。 */
	private void mark(List<Transaction> rows) {
		Long group = rows.size() >= 2 ? rows.get(0).getId() : null;
		for (Transaction row : rows) {
			row.changeReceiptGroup(group);
		}
	}

}
