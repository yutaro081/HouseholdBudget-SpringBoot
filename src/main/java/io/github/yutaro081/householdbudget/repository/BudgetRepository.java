package io.github.yutaro081.householdbudget.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yutaro081.householdbudget.entity.Budget;

/**
 * Budget の読み書き（設計書 4.6）。
 * <p>
 * すべての検索・削除は user_id で絞る（設計書 3.9）。
 * 【Phase 4 の時点】科目の削除に必要なメソッドだけを持つ。予算設定の検索は Phase 6 で足す。
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

	/** その科目に、0円でない予算がある季節があるか（どの季節のものも見る）。科目の削除の確認に使う（設計書 5.5）。 */
	boolean existsByUserIdAndCategoryIdAndAmountGreaterThan(Long userId, Long categoryId, int amount);

	/** その科目の予算の行を、どの季節のものもすべて消す。科目を消す前に呼ぶ（設計書 3.7）。 */
	void deleteByUserIdAndCategoryId(Long userId, Long categoryId);

}
