package io.github.yutaro081.householdbudget.dto;

import java.time.LocalDate;
import java.util.List;

import io.github.yutaro081.householdbudget.domain.ProposalChoice;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;

/**
 * 自動予算の表示データ（設計書 4.7、5.14、issue 019）。画面が読む値は、すべてこの中に入れて渡す（open-in-view=false）。
 * <p>
 * <b>【仮の形】形（フィールド）はテストのために決めた。AutoBudgetService はまだ中身が空で、正しい値を作らない。</b>
 *
 * @param choice              選んだ割合
 * @param previousSeason      直前の季節（画面の季節の 1 つ前）。範囲の外（2000 年 春を開いたとき）は null
 * @param previousBudgetTotal 直前の季節の予算の合計（設定済みの科目だけ。0 円も設定済み）。ひとつも設定されていなければ null
 * @param previousActualTotal 直前の季節の実績の合計（全科目）
 * @param previousContainsToday 直前の季節が今日を含むか（含むときだけ、実績に「今日まで」の日付を付ける）
 * @param today               今日
 * @param previousPaceTotal   実績の合計の「このペースなら」（100 円切り上げ）。直前の季節が今日を含み、過ぎた日数が 14 日以上で、
 *                            実績の合計が 0 円より大きいときだけ。ないときは null
 * @param screenSeason        画面の季節の名前（文の「過去の○」に使う）
 * @param hasRecordYears      記録のある年が 1 つ以上あるか
 * @param trendPercent        案にかけた傾向の %（計算しなかったときは 100）
 * @param trendKind           傾向の種類（文の書き分け）
 * @param trendCapped         傾向を上限（130）・下限（70）で止めたか
 * @param proposalTotal       全科目の案の合計（自動予算）
 * @param someFromPrevious    一部の科目だけがルール②〜④か（①と②〜④が混ざっているとき true。「（一部の科目は直前の季節をもとにしています）」）
 * @param allFromPrevious     記録のある年はあるが、全科目がルール②〜④か（全科目が④のときは false。「（すべての科目が直前の季節をもとにしています）」）
 * @param noMaterial          全科目がルール④か（「案を作れる材料がありません」。入力欄は何も変えない）
 * @param rows                科目ごとの行（科目の登録順）
 */
public record AutoBudgetView(
		ProposalChoice choice,
		SeasonPeriod previousSeason,
		Long previousBudgetTotal,
		long previousActualTotal,
		boolean previousContainsToday,
		LocalDate today,
		Long previousPaceTotal,
		Season screenSeason,
		boolean hasRecordYears,
		int trendPercent,
		TrendKind trendKind,
		boolean trendCapped,
		long proposalTotal,
		boolean someFromPrevious,
		boolean allFromPrevious,
		boolean noMaterial,
		List<Row> rows) {

	/** 傾向の種類（2 行目の文の書き分け）。 */
	public enum TrendKind {
		/** 100% より大きい（「増える傾向」） */
		UP,
		/** 100% より小さい（「減る傾向」） */
		DOWN,
		/** ちょうど 100%（「ほぼ同じ」） */
		SAME,
		/** 計算しなかった（12 季節がそろわない） */
		NOT_CALCULATED
	}

	/** 案の根拠の種類（表の「案の根拠」のセルの書き分け）。 */
	public enum BasisKind {
		/** ① 記録のある年が 3 つ：中央値 */
		MEDIAN,
		/** ① 記録のある年が 2 つ：平均 */
		AVERAGE,
		/** ① 記録のある年が 1 つ：その年の額 */
		SINGLE_YEAR,
		/** ② 直前の季節が今日を含む：ペース */
		PACE,
		/** ② 直前の季節が終わっている：実績 */
		ACTUAL,
		/** ③ 直前の季節の予算 */
		BUDGET,
		/** ④ 材料なし（今の値のまま） */
		NONE
	}

	/**
	 * 1 科目分の行。
	 *
	 * @param categoryId       科目のID
	 * @param categoryName     科目名
	 * @param previousBudget   直前の季節の予算（null ＝ 未設定）
	 * @param previousActual   直前の季節の実績
	 * @param paceAmount       「このペースなら」の額（100 円切り上げ）。直前の季節が今日を含み、14 日以上過ぎていて、実績が 0 円より大きいときだけ。ないときは null
	 * @param rule             当てはまったルールの番号（1〜4）
	 * @param basisKind        根拠の種類
	 * @param years            ルール①のとき、もとの額に使った年（季節の年。古い順）。ほかは空
	 * @param yearsConsecutive years が 1 年ずつ続いているか（飛んでいると false。「2023・2025 年」と書き分ける）
	 * @param baseAmount       根拠の額（ルール①：もとの額（中央値・平均・その年の額）、②：ペースの表示額または実績、③：直前の季節の予算、④：今の値）
	 * @param amount           案の額（円）
	 */
	public record Row(
			Long categoryId,
			String categoryName,
			Long previousBudget,
			long previousActual,
			Long paceAmount,
			int rule,
			BasisKind basisKind,
			List<Integer> years,
			boolean yearsConsecutive,
			long baseAmount,
			long amount) {
	}

}
