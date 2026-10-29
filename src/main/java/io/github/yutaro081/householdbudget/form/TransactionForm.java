package io.github.yutaro081.householdbudget.form;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.github.yutaro081.householdbudget.entity.Transaction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 取引の登録・編集の入力を受け取るクラス（設計書 4.7、6.2、6.4〜6.7）。
 * 注釈の組み合わせは D4 の B のとおり（「必須」を他のエラーと重ねない。範囲・文字数は別の注釈）。
 * エラーメッセージのキーは、注釈の名前.transactionForm.項目名（messages.properties、G6）。
 * <p>
 * 金額は、Controller の @InitBinder で StrictIntegerEditor を付けて読む（Q5-1 の C、6.5）。
 * 自分の科目・取引先・支払方法かどうかは、Form ではなく Service が確かめる（6.3、3.9）。
 * <p>
 * user_id など、画面から指定させたくない項目は持たない（6.1）。
 */
public class TransactionForm {

	/** 取引の日付。画面の日付入力は 2026-10-19 の形で送る（6.6）。 */
	@NotNull
	@TransactionDateRange
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate transactionDate;

	/** 金額。1円以上1,000,000円以下（要件5章）。 */
	@NotNull
	@Min(1)
	@Max(1_000_000)
	private Integer amount;

	/** 科目のID。数字でない値も、未選択と同じメッセージにする（G5）。 */
	@NotNull
	private Long categoryId;

	/** 摘要（任意）。30文字以下。前後の空白を取り除き、空なら null にする（D6、6.4、3.6）。 */
	@Size(max = 30)
	private String description;

	@NotNull
	private Long vendorId;

	@NotNull
	private Long paymentMethodId;

	public TransactionForm() {
	}

	/** 編集画面で、登録済みの値を入れた状態で表示するために使う。 */
	public TransactionForm(Transaction transaction) {
		this.transactionDate = transaction.getTransactionDate();
		this.amount = transaction.getAmount();
		this.categoryId = transaction.getCategory().getId();
		this.description = transaction.getDescription();
		this.vendorId = transaction.getVendor().getId();
		this.paymentMethodId = transaction.getPaymentMethod().getId();
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public void setTransactionDate(LocalDate transactionDate) {
		this.transactionDate = transactionDate;
	}

	public Integer getAmount() {
		return amount;
	}

	public void setAmount(Integer amount) {
		this.amount = amount;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public void setCategoryId(Long categoryId) {
		this.categoryId = categoryId;
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

}
