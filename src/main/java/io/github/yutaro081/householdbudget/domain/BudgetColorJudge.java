package io.github.yutaro081.householdbudget.domain;

/**
 * 予算と実績から、行の色を決める（設計書 4.5、5.9、要件定義書 4.4）。
 * 画面にもデータベースにも依存しない計算。各科目の行も、合計行も、同じ判定を使う（Q21、Q22）。
 * <p>
 * 予算が未設定（null）なら、色なし。設定されていれば、次の順番で判定する（順番は変えない）。
 * <ol>
 * <li>実績 ＞ 予算 → 赤（予算が0円でも、実績があれば赤）</li>
 * <li>予算 ＝ 0円 → 色なし（実績も0円のとき。③を通すと 0 ≦ 0 で青になってしまうので、先に除く）</li>
 * <li>実績 × 3 ≦ 予算 → 青（ちょうど1/3も青）</li>
 * <li>それ以外 → 色なし</li>
 * </ol>
 * 金額の合計は INT を超えることがあるので、計算は long で行う（設計書 3.1）。
 * <p>
 * 予算内の印（💮。Phase 8 の issue 005、設計書 5.9、Q8-33）は、色とは別に {@link #isWithinBudget(Long, long)} で決める。
 * 色の種類（{@link BudgetColor}）は変えない。
 */
public final class BudgetColorJudge {

	private BudgetColorJudge() {
	}

	/**
	 * 色を決める。
	 *
	 * @param budget 予算。未設定なら null（0円とは別。0円は設定済み）
	 * @param actual 実績（取引の合計。取引がなければ0）
	 */
	public static BudgetColor judge(Long budget, long actual) {
		if (budget == null) {
			return BudgetColor.NONE;
		}
		if (actual > budget) {
			return BudgetColor.RED;
		}
		if (budget == 0) {
			return BudgetColor.NONE;
		}
		if (actual * 3 <= budget) {
			return BudgetColor.BLUE;
		}
		return BudgetColor.NONE;
	}

	/**
	 * 予算内の印（💮）を付けるかどうか。色の判定で④（それ以外は色なし）に当たり、かつ予算が設定されていて、予算 ＞ 0、実績 ＞ 0 の行。
	 * 式にすると、予算が設定されていて、実績 ≦ 予算 かつ 実績 × 3 ＞ 予算（このとき予算 ＞ 0、実績 ＞ 0 になる）。
	 * 予算ちょうど（実績 ＝ 予算）も付ける（Q8-29）。赤・青の行、予算が未設定の行、予算が0円の行、実績が0円の行には付けない（Q8-28）。
	 * 各科目の行も、合計行も、同じ判定を使う（Q8-27）。計算は long で行う。
	 *
	 * @param budget 予算。未設定なら null
	 * @param actual 実績（取引の合計。取引がなければ0）
	 */
	public static boolean isWithinBudget(Long budget, long actual) {
		return budget != null && budget > 0 && actual > 0 && actual <= budget && actual * 3 > budget;
	}

}
