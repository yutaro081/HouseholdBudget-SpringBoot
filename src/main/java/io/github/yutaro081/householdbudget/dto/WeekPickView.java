package io.github.yutaro081.householdbudget.dto;

/**
 * トップの「今週の科目のひとこと」の中身（要件定義書 4.5、設計書 5.15、issue 020）。
 *
 * @param categoryName    今週いちばん進んでいる科目の名前（画面では th:text で出す）
 * @param forecastAmount  その科目の季節の見込み（円。100 円単位に切り上げ。予実比較の科目ごとの見込みと同じ値）
 * @param forecastPercent 見込みの、その科目の季節の予算に対する％（四捨五入）
 * @param overBudget      見込みの額が、その科目の季節の予算を超えるか（予実比較の「見込みとの差」の赤と同じ決まり）。画面は、これが true のときだけ「予算の ○%」を赤にする（テンプレートでは計算しない）
 */
public record WeekPickView(String categoryName, long forecastAmount, int forecastPercent, boolean overBudget) {
}
