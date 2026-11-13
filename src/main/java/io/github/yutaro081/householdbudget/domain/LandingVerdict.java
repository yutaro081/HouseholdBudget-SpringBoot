package io.github.yutaro081.householdbudget.domain;

/**
 * 予実比較の要約カードの文「予算より 約 ○円 少ない（多い）見込みです。」「予算どおりの見込みです。」の元（要件定義書 4.4、設計書 2.3・4.5、issue 024）。
 * 差 ＝ 予算 − 見込みの額（表示する額。100 円単位に切り上げたもの。表の「見込みとの差」の合計行と同じ値）。
 * プラスなら見込みは予算より少なく（LESS）、マイナスなら多く（MORE）、0 なら予算どおり（SAME）。額は絶対値。
 * 赤にするのは MORE のときだけ（見込みが予算を超えるとき。見込みとの差・トップの文・今週のひとことと同じ決まり）。
 * 多いか少ないか・赤にするかは「決まり」なので、色の判定（BudgetColorJudge）と同じく domain に置く（画面にも DB にも頼らない）。
 * Controller が差を渡して作り、テンプレートでは判定しない。
 *
 * @param direction 見込みが予算より少ないか、多いか、同じか
 * @param amount    差の額（円。0 以上。向きは direction）
 */
public record LandingVerdict(Direction direction, long amount) {

	public enum Direction {
		LESS, MORE, SAME
	}

	/** 差（予算 − 見込みの額）から作る。長い値でもあふれない（絶対値は Math.abs ではなく、符号で分ける） */
	public static LandingVerdict of(long gap) {
		if (gap > 0) {
			return new LandingVerdict(Direction.LESS, gap);
		}
		if (gap < 0) {
			return new LandingVerdict(Direction.MORE, -gap);
		}
		return new LandingVerdict(Direction.SAME, 0);
	}

	/** 文のメッセージの名前の最後の部分（comparison.verdict.less / more / same） */
	public String key() {
		return direction.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** 見込みが予算を超える（赤にする）か */
	public boolean over() {
		return direction == Direction.MORE;
	}
}
