package io.github.yutaro081.householdbudget.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yutaro081.householdbudget.entity.Transaction;

/**
 * transactions の読み書き（設計書 4.6）。
 * <p>
 * 【Phase 4 の時点】科目・取引先・支払方法が「取引で使われているか」の確認（設計書 5.5）だけを持つ。
 * 一覧・1件取得・集計などの検索は、Phase 5・7 で足す。
 * すべての検索を user_id で絞る（設計書 3.9）。
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	/** 自分の取引で、その科目が使われているか。 */
	boolean existsByUserIdAndCategoryId(Long userId, Long categoryId);

	/** 自分の取引で、その取引先が使われているか。 */
	boolean existsByUserIdAndVendorId(Long userId, Long vendorId);

	/** 自分の取引で、その支払方法が使われているか。 */
	boolean existsByUserIdAndPaymentMethodId(Long userId, Long paymentMethodId);

}
