package io.github.yutaro081.householdbudget.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.yutaro081.householdbudget.domain.PieSlices;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;

/**
 * トップの画面全体の表示データ（設計書 4.7、5.11）。
 * 画面が読む値は、すべてこの中に入れて渡す（open-in-view=false。実行計画書 ルール8）。
 * 長さ・位置は、1000分率の整数（0〜1000。500 なら 50.0％）。画面で「整数部.小数1桁%」にして、style 属性に入れる。
 *
 * @param season              今日が属する季節（季節のメーターの季節。週の予算の元の季節）
 * @param seasonBudget        今の季節の予算の合計（予実比較の合計行と同じ）。全科目が未設定なら null
 * @param seasonActual        今の季節の使った額の合計（1円単位。切り上げない）
 * @param seasonRemaining     残り ＝ 予算 − 使った額（超過するとマイナス）。予算が未設定なら null
 * @param seasonBarPermille   季節の棒の長さ。予算が未設定なら 0
 * @param seasonTodayPermille 季節の「今日」の線の位置
 * @param weekStart           今週の開始日（日曜）
 * @param weekEnd             今週の最後の日（土曜）
 * @param weekTodayPermille   週の「今日」の線の位置（週の何日目 ÷ 7）
 * @param hasCategories       科目が1つでもあるか。ないときは、画面は案内だけを出す
 * @param slices              円グラフの並び（使った科目だけ。7科目以上は上位5科目＋その他）。今週の取引がなければ空
 * @param pieTotal            円の真ん中に出す今週の合計（切り上げた額の和）
 * @param pieStops            conic-gradient の中身（数字と固定の文字だけ。{@link PieSlices#cssStops}）。円がなければ空
 * @param rows                科目ごとの行（科目の登録順。使っていない科目も含む）
 * @param total               合計の行。科目が0件のときも値は作るが、画面は出さない
 * @param hitokoto            きょうのひとこと（暦のひとこと、グラフへのひとこと。設計書 5.12）
 */
public record TopView(SeasonPeriod season, Long seasonBudget, long seasonActual, Long seasonRemaining,
		int seasonBarPermille, int seasonTodayPermille, LocalDate weekStart, LocalDate weekEnd,
		int weekTodayPermille, boolean hasCategories, List<PieSlices.Slice> slices, long pieTotal, String pieStops,
		List<WeekRow> rows, WeekRow total, HitokotoView hitokoto) {
}
