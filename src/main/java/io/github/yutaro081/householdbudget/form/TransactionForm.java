package io.github.yutaro081.householdbudget.form;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import io.github.yutaro081.householdbudget.entity.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 取引の登録・編集の入力を受け取るクラス（設計書 4.7、6.2、6.4〜6.7。issue 032）。
 * <p>
 * 共通の項目（日付・取引先・支払方法）と、明細の行の並び（lines。各行は科目・金額・摘要）を持つ。
 * 登録の画面は 5 行（{@link #MAX_LINES}）、編集の画面は 1 行。保存すると、空でない行ごとに 1 件の取引になる。
 * <p>
 * 注釈の入力チェック（日付、取引先・支払方法の必須、行の金額の範囲、摘要の文字数）は、Bean Validation が行う。
 * 「空でない行だけ、科目と金額が必須」「空でない行が 1 行以上」は、注釈ではなく、{@link TransactionFormBinding} の Validator が確かめる
 * （空の行は無視するため。型変換のエラーがある行を、空の行と見分けるため）。
 * エラーメッセージのキーは、注釈の名前.transactionForm.項目名（行は lines.amount の形。messages.properties、G6）。
 * <p>
 * user_id、行の ID など、画面から指定させたくない項目は持たない（6.1、3.9）。
 */
public class TransactionForm {

	/** 1 回の登録で入れられる明細の行の数（要件定義書 5 章） */
	public static final int MAX_LINES = 5;

	/** 取引の日付。画面の日付入力は 2026-10-19 の形で送る（6.6）。 */
	@NotNull
	@TransactionDateRange
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate transactionDate;

	@NotNull
	private Long vendorId;

	@NotNull
	private Long paymentMethodId;

	/** 明細の行。束縛のときは、送られた番号の行まで、空の行が自動で作られる（5 行まで。TransactionFormBinding）。 */
	private List<@Valid Line> lines = new ArrayList<>();

	/** 束縛（Spring）が使う。行は空。 */
	public TransactionForm() {
	}

	/** 登録の画面：明細の行は、空の 5 行。 */
	public static TransactionForm forNew() {
		TransactionForm form = new TransactionForm();
		form.fillLinesUpTo(MAX_LINES);
		return form;
	}

	/** 編集の画面：登録済みの値。明細は 1 行（段階 2 では、1 つの取引ずつ直す）。 */
	public static TransactionForm forEdit(Transaction transaction) {
		TransactionForm form = new TransactionForm();
		form.transactionDate = transaction.getTransactionDate();
		form.vendorId = transaction.getVendor().getId();
		form.paymentMethodId = transaction.getPaymentMethod().getId();
		form.lines.add(new Line(transaction.getCategory().getId(), transaction.getAmount(), transaction.getDescription()));
		return form;
	}

	/** 複写の画面：写す元の値を共通の項目と明細の 1 行目に入れ、残りは空の行（5 行）。日付は、呼ぶ側が今日にする。 */
	public static TransactionForm copyOf(Transaction transaction) {
		TransactionForm form = forEdit(transaction);
		form.fillLinesUpTo(MAX_LINES);
		return form;
	}

	/** 行が count 行より少なければ、空の行を足す（減らさない）。入力エラーで出し直すとき、登録は 5 行、編集は 1 行にそろえる。 */
	public void fillLinesUpTo(int count) {
		while (lines.size() < count) {
			lines.add(new Line());
		}
	}

	/** 空でない行（科目・金額・摘要のどれかに値がある行）を、並びのまま返す。保存に使う（入力チェックが通ったあと）。 */
	public List<Line> filledLines() {
		return lines.stream().filter(line -> line != null && !line.isBlank()).toList();
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(LocalDate transactionDate) {
		this.transactionDate = transactionDate;
	}

	public Long getVendorId() {
		return vendorId;
	}

	public void setVendorId(Long vendorId) {
		this.vendorId = vendorId;
	}

	public Long getPaymentMethodId() {
		return paymentMethodId;
	}

	public void setPaymentMethodId(Long paymentMethodId) {
		this.paymentMethodId = paymentMethodId;
	}

	public List<Line> getLines() {
		return lines;
	}

	public void setLines(List<Line> lines) {
		this.lines = lines;
	}

	/**
	 * 明細の 1 行（科目・金額・摘要）。科目と金額の必須は、空でない行だけ（TransactionFormBinding の Validator）。
	 * 金額の範囲と摘要の文字数は、注釈で確かめる（値がないときは、注釈は何も言わない）。
	 */
	public static class Line {

		/** 科目のID。数字でない値も、未選択と同じメッセージにする（G5）。 */
		private Long categoryId;

		/** 金額。1円以上1,000,000円以下（要件5章）。Controller の @InitBinder で StrictIntegerEditor を付けて読む（6.5）。 */
		@Min(1)
		@Max(1_000_000)
		private Integer amount;

		/** 摘要（任意）。30文字以下。前後の空白を取り除き、空なら null にする（D6、6.4、3.6）。 */
		@Size(max = 30)
		private String description;

		public Line() {
		}

		public Line(Long categoryId, Integer amount, String description) {
			this.categoryId = categoryId;
			this.amount = amount;
			setDescription(description);
		}

		/** 科目・金額・摘要のどれにも値がない行（摘要は、前後の空白を取ったあと）。型変換のエラーは、ここでは分からない（Validator が見る）。 */
		public boolean isBlank() {
			return categoryId == null && amount == null && description == null;
		}

		public Long getCategoryId() {
			return categoryId;
		}

		public void setCategoryId(Long categoryId) {
			this.categoryId = categoryId;
		}

		public Integer getAmount() {
			return amount;
		}

		public void setAmount(Integer amount) {
			this.amount = amount;
		}

		public String getDescription() {
			return description;
		}

		/**
		 * 前後の空白は、全角の空白も含めて取り除く。取り除いて空なら null にする（B9、D6、Q5-6 の A）。
		 * trim() は全角の空白を取らないため strip() を使う（6.4）。
		 */
		public void setDescription(String description) {
			if (description == null) {
				this.description = null;
				return;
			}
			String stripped = description.strip();
			this.description = stripped.isEmpty() ? null : stripped;
		}

	}

}
