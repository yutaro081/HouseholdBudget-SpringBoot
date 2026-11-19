package io.github.yutaro081.householdbudget.dto;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.entity.Category;

/**
 * 取引一覧の絞り込みの条件の入れ物（issue 023。設計書 4.7、5.17）。
 * <p>
 * 【決まり】Controller が URL の値を確かめてから作る（季節は SeasonPeriod.parse、科目は CategoryService.get で得た自分の科目）。
 * 画面の値を直接受け取る Form ではないので、form ではなく dto に置く（form に置くと、@ModelAttribute で直接受け取る形にされ、
 * 確かめを飛ばす道ができるため）。科目は ID ではなく、確かめて得た科目そのものを持つ（確かめていない ID が入らない。見出しの科目名にも使う）。
 * <b>user_id は持たない</b>（Service の最初の引数で、別に渡す。設計書 3.9）。
 * <p>
 * あとで取引先・摘要で絞るときは、ここに項目を足す（手順書 issue-023-drilldown.md 3.6）。
 *
 * @param category 絞り込む科目（自分の科目）。指定がなければ null
 * @param period   絞り込む季節。指定がなければ null（全期間）
 */
public record TransactionFilter(Category category, SeasonPeriod period) {

	/** 条件なし（今の一覧。全期間・全科目） */
	public static final TransactionFilter NONE = new TransactionFilter(null, null);

	/** 条件が 1 つもないか（そのときは、絞らない一覧と同じ） */
	public boolean isEmpty() {
		return category == null && period == null;
	}

	/** 絞り込む科目の ID。指定がなければ null（検索に「値が null なら絞らない」として渡す） */
	public Long categoryId() {
		return category == null ? null : category.getId();
	}

}
