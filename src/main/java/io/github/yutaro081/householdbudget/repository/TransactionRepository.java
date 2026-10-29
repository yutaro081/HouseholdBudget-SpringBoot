package io.github.yutaro081.householdbudget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yutaro081.householdbudget.entity.Transaction;

/**
 * transactions の読み書き（設計書 4.6）。
 * <p>
 * 科目・取引先・支払方法が「取引で使われているか」の確認（設計書 5.5、Phase 4）と、
 * 一覧・1件取得（Phase 5）を持つ。期間内の科目ごとの合計は Phase 7 で足す。
 * <p>
 * 【決まり】すべての検索を user_id で絞る。ID だけで探すメソッド（findById など）は、アプリのコードから使わない
 * （設計書 3.9）。
 * 一覧・1件取得では、科目・取引先・支払方法もまとめて読み込む（設計書 4.6、Q5-5 の A：@EntityGraph）。
 * 取引1件ごとに別々に読み込まないため。
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	/** 自分の取引で、その科目が使われているか。 */
	boolean existsByUserIdAndCategoryId(Long userId, Long categoryId);

	/** 自分の取引で、その取引先が使われているか。 */
	boolean existsByUserIdAndVendorId(Long userId, Long vendorId);

	/** 自分の取引で、その支払方法が使われているか。 */
	boolean existsByUserIdAndPaymentMethodId(Long userId, Long paymentMethodId);

	/** 自分の取引を、日付の新しい順（同じ日は id の新しい順）で全件。一覧に使う（Q4・Q5）。 */
	@EntityGraph(attributePaths = { "category", "vendor", "paymentMethod" })
	List<Transaction> findByUserIdOrderByTransactionDateDescIdDesc(Long userId);

	/** ID と user_id の両方で1件探す。他人のものや、存在しない ID は見つからない。詳細・編集・更新・削除に使う。 */
	@EntityGraph(attributePaths = { "category", "vendor", "paymentMethod" })
	Optional<Transaction> findByIdAndUserId(Long id, Long userId);

}
