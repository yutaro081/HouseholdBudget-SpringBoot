package io.github.yutaro081.householdbudget.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.yutaro081.householdbudget.domain.LandingForecast;
import io.github.yutaro081.householdbudget.domain.LandingVerdict;
import io.github.yutaro081.householdbudget.domain.MeterScale;

/**
 * 予実比較の画面が読む値の全部（設計書 4.7、issue 027）。ComparisonService.view が作り、Controller はそのまま画面に渡す。
 * 画面が読む値は、すべてこの中に入れて渡す（open-in-view=false。計算も判定も、テンプレートではしない）。
 * <p>
 * <b>showForecast と card は、条件が違う。</b>
 * <ul>
 * <li>showForecast：表示している季節が、今日の季節。表を 7 列（残り・見込み・見込みとの差）にする</li>
 * <li>card が null でない：それに加えて、合計の予算が設定されていて 0 円より大きい。要約カードを出す</li>
 * </ul>
 * 今の季節でも、予算が未設定・0 円なら、表は 7 列（見込みは「—」）で、カードは出ない（card は null）。
 *
 * @param comparison   季節（前・次の季節も）と、表の行・合計行（ComparisonService.compare の結果そのまま）
 * @param showForecast 今の季節を表示しているか
 * @param lines        科目ごとの行（科目の登録順。comparison.rows と同じ並び）
 * @param total        合計の行
 * @param card         要約カード。出さないときは null
 */
public record ComparisonView(ComparisonResult comparison, boolean showForecast, List<Line> lines, Line total, Card card) {

	/**
	 * 表の 1 行：予実比較の行と、その見込み・見込みとの差。
	 *
	 * @param row      予算・実績・差額・色（科目の行、または合計の行）
	 * @param forecast 見込み（設計書 5.13）。出さないとき（過去・未来の季節、予算が未設定・0 円）は null（画面は「—」）
	 * @param gap      見込みとの差（円）＝ 予算 − 表示する見込みの額。マイナスなら、見込みが予算を超える。見込みを出さないときは null
	 */
	public record Line(ComparisonRow row, LandingForecast.Result forecast, Long gap) {
	}

	/**
	 * 要約カード（表の上。設計書 2.3）。
	 *
	 * @param forecast       合計の見込み（合計の行の見込みと同じ値）
	 * @param verdict        差の文（「予算より 約 ○円 少ない／多い見込みです。」）の向き・額・赤にするか
	 * @param usedPercent    予算のうち、使った割合（％。整数）
	 * @param elapsedPercent 季節のうち、過ぎた日の割合（％。整数。「今日（79%）」）
	 * @param meter          メーターの棒の長さと線の位置
	 * @param countingFrom   最初の取引の日から数えたとき（その日が季節の初日より後のとき）だけ、その日。季節の初日から数えるときは null（知らせない）
	 */
	public record Card(LandingForecast.Result forecast, LandingVerdict verdict, Integer usedPercent, int elapsedPercent,
			MeterScale.Meter meter, LocalDate countingFrom) {
	}

}
