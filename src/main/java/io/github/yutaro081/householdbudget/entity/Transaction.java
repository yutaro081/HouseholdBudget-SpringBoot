package io.github.yutaro081.householdbudget.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 取引（テーブル transactions、設計書 3.6）。
 * user_id は数値のIDで持ち、科目・取引先・支払方法は関連で持つ（P7）。
 * 摘要（description）だけ NULL を許す（要件4.2）。
 * <p>
 * 【Phase 4 の時点】科目・取引先・支払方法の「使用中」の確認のために前倒しで作った。
 * 取引の登録・一覧・編集・削除は Phase 5 で作る。
 */
@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "transaction_date", nullable = false)
	private LocalDate transactionDate;

	@Column(name = "amount", nullable = false)
	private int amount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(name = "description", length = 30)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_method_id", nullable = false)
	private PaymentMethod paymentMethod;

	/** JPA が使う。アプリのコードからは使わない。 */
	protected Transaction() {
	}

	public Transaction(Long userId, LocalDate transactionDate, int amount, Category category,
			String description, Vendor vendor, PaymentMethod paymentMethod) {
		this.userId = userId;
		this.transactionDate = transactionDate;
		this.amount = amount;
		this.category = category;
		this.description = description;
		this.vendor = vendor;
		this.paymentMethod = paymentMethod;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDate getTransactionDate() {
		return transactionDate;
	}

	public int getAmount() {
		return amount;
	}

	public Category getCategory() {
		return category;
	}

	public String getDescription() {
		return description;
	}

	public Vendor getVendor() {
		return vendor;
	}

	public PaymentMethod getPaymentMethod() {
		return paymentMethod;
	}

}
