package io.github.yutaro081.householdbudget.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;

/**
 * Budget の読み書き（設計書 4.6）。
 * <p>
 * 【決まり】すべての検索・削除は user_id で絞る（設計書 3.9）。ID だけで探すメソッド（findById など）は、
 * アプリのコード（Service）から使わない。
 * <p>
 * Phase 4 で、科目の削除に必要な2つ（0円でない予算があるか、科目の予算をすべて消す）を作り、
 * Phase 6 で、予算設定の検索を足した。
 * <p>
 * 【季節の古い新しい】DB の season は文字（AUTUMN、SPRING、SUMMER、WINTER）で、文字の順は期間の順（春→夏→秋→冬）と違う。
 * 「表示季節より前」は、次の2つの検索（年が前／同じ年で季節が前）を合わせて求め、
 * 科目ごとにいちばん近いものを選ぶのは Service が期間の順で行う（Phase 6、Q6-4 の A）。
 * 検索の結果の予算は、科目も一緒に読み込む（画面や Service が科目のIDを読むため。open-in-view=false）。
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

	/** その科目に、0円でない予算がある季節があるか（どの季節のものも見る）。科目の削除の確認に使う（設計書 5.5）。 */
	boolean existsByUserIdAndCategoryIdAndAmountGreaterThan(Long userId, Long categoryId, int amount);

	/** その科目の予算の行を、どの季節のものもすべて消す。科目を消す前に呼ぶ（設計書 3.7）。 */
	void deleteByUserIdAndCategoryId(Long userId, Long categoryId);

	/** その季節の、自分の予算を全件。表示の「保存済みの額」と、保存の「作る・更新する」の判定に使う（設計書 5.7）。 */
	@EntityGraph(attributePaths = "category")
	List<Budget> findByUserIdAndSeasonYearAndSeason(Long userId, int seasonYear, Season season);

	/** 年が seasonYear より前の、自分の予算を全件。「表示季節より前」の一部（初期値用、設計書 5.7）。 */
	@EntityGraph(attributePaths = "category")
	List<Budget> findByUserIdAndSeasonYearLessThan(Long userId, int seasonYear);

	/**
	 * 年が同じで、季節が seasons のどれかである、自分の予算を全件。「表示季節より前」の、同じ年の中の部分（初期値用）。
	 * seasons には Season.earlierInYear() を渡す（春のときは空なので、呼ばなくてよい）。
	 */
	@EntityGraph(attributePaths = "category")
	List<Budget> findByUserIdAndSeasonYearAndSeasonIn(Long userId, int seasonYear, Collection<Season> seasons);

}
