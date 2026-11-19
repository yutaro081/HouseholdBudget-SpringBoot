package io.github.yutaro081.householdbudget.dto;

import java.util.List;

/**
 * 取引一覧の画面が読む値（issue 023。設計書 2.3、4.7、5.17）。TransactionService.listView が作る。
 * 件数と合計は Service が出す（Controller・画面では足し算しない）。
 *
 * @param filter 絞り込みの条件（なければ {@link TransactionFilter#NONE}）
 * @param months 月ごとのまとまり（新しい月が先）
 * @param count  全体の件数（行の数）。絞り込んだときは、見出しの「○ 件」
 * @param total  全体の金額の合計（円）。絞り込んだときは、見出しの「合計 ○円」
 */
public record TransactionListView(TransactionFilter filter, List<TransactionMonth> months, int count, long total) {
}
