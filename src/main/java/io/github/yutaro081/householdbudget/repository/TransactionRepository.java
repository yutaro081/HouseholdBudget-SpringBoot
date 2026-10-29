package io.github.yutaro081.householdbudget.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yutaro081.householdbudget.dto.CategoryActual;
import io.github.yutaro081.householdbudget.entity.Transaction;

/**
 * transactions の読み書き（設計書 4.6）。
 * <p>
 * 科目・取引先・支払方法が「取引で使われているか」の確認（設計書 5.5、Phase 4）と、
 * 一覧・1件取得（Phase 5）、期間内の科目ごとの合計（Phase 7）を持つ。
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

	/**
	 * 自分の取引を、期間内で科目ごとに合計する（予実比較の実績。設計書 5.9、4.6、Q7-3 の A）。
	 * <p>
	 * 期間は「開始日以上、終了日の翌日未満」（設計書 5.8）。SeasonPeriod.startDate() と endDateExclusive() を渡す。
	 * 値はすべてパラメータで渡し、文字列を連結しない（設計書 7.4）。
	 * 取引がない科目は結果に出ない（0円にするのは Service）。集計なので @EntityGraph は要らない（科目のIDと合計だけを返す）。
	 */
	@Query("""
			select new io.github.yutaro081.householdbudget.dto.CategoryActual(t.category.id, sum(t.amount))
			from Transaction t
			where t.userId = :userId
			  and t.transactionDate >= :start
			  and t.transactionDate < :endExclusive
			group by t.category.id
			""")
	List<CategoryActual> sumByCategory(@Param("userId") Long userId,
			@Param("start") LocalDate start,
			@Param("endExclusive") LocalDate endExclusive);

}
