package io.github.yutaro081.householdbudget.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.yutaro081.householdbudget.domain.MeterScale;
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
 * @param seasonMeter         季節のメーターの値（棒、予算の線、「今日」の線、「+○%」。縮尺は MeterScale。issue 020）
 * @param weekStart           今週の開始日（日曜）
 * @param weekEnd             今週の最後の日（土曜）
 * @param hasCategories       科目が1つでもあるか。ないときは、画面は案内だけを出す
 * @param slices              円グラフの並び（使った科目だけ。7科目以上は上位5科目＋その他）。今週の取引がなければ空
 * @param pieTotal            円の真ん中に出す今週の合計（切り上げた額の和）
 * @param pieStops            conic-gradient の中身（数字と固定の文字だけ。{@link PieSlices#cssStops}）。円がなければ空
 * @param rows                科目ごとの行（科目の登録順。使っていない科目も含む）
 * @param total               合計の行。科目が0件のときも値は作るが、画面は出さない
 * @param hitokoto            きょうのひとこと（暦のひとこと、グラフへのひとこと。設計書 5.12）
 * @param today               今日の日付（見出しの行「今日は ○月○日」。issue 017）
 * @param seasonFirstMonth    今の季節の最初の月（秋＝9、冬＝12）。見出し「（9月〜11月）」
 * @param seasonLastMonth     今の季節の最後の月（秋＝11、冬＝2）
 * @param seasonDaysLeft      季節の終わりまであと何日（今日を含める。最後の日は 1）
 * @param seasonUsedPercent   「予算のうち ○% を使った」の％（四捨五入。100％をこえてもそのまま）。予算が未設定・0円なら null
 * @param seasonElapsedPercent 「縦の線は『今日』（季節の ○%）」の％（四捨五入）
 * @param weekDayNumber       今日が今週の何日目か（日曜＝1、土曜＝7）。「点線は『今日』（週の ○ 日目）」
 * @param landingAmount      着地見込みの額（円。100 円単位。予算が未設定・0 円なら null）〔issue 018。issue 017 のペースの一言のキー `paceKey` を置きかえた〕
 * @param landingPercent     着地見込みの、予算に対する％（なければ null）
 * @param weekPick           今週の科目のひとこと（今週いちばん進んでいる科目と、その季節の見込み。出さないときは null。issue 020）
 */
public record TopView(SeasonPeriod season, Long seasonBudget, long seasonActual, Long seasonRemaining,
		MeterScale.Meter seasonMeter, LocalDate weekStart, LocalDate weekEnd,
		boolean hasCategories, List<PieSlices.Slice> slices, long pieTotal, String pieStops,
		List<WeekRow> rows, WeekRow total, HitokotoView hitokoto, LocalDate today, int seasonFirstMonth,
		int seasonLastMonth, int seasonDaysLeft, Integer seasonUsedPercent, int seasonElapsedPercent, int weekDayNumber,
		Long landingAmount, Integer landingPercent, WeekPickView weekPick) {
}
