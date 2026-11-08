package io.github.yutaro081.householdbudget.domain;

import java.util.Optional;

/**
 * ペースの一言の判定（設計書 5.11、要件定義書 4.5、issue 017 での見直し〔issue 010 の Q010-3〕）。
 * 季節のメーターの下に出す、「使い方は日の進み方に比べてどうか」の一言を決める。画面にもデータベースにも依存しない。
 * <p>
 * 差 ＝ 使った割合（％）− 日の進み方（％）。どちらも四捨五入した整数（{@link PeriodProgress#usedPercent}、
 * {@link PeriodProgress#seasonElapsedPercent}）。
 * <ul>
 * <li>差が −5 以下（5ポイント以上少ない）＝ゆっくり</li>
 * <li>差が +5 以上（5ポイント以上多い）＝速め</li>
 * <li>差が −4〜+4 ＝ほぼ同じ</li>
 * </ul>
 * <b>ちょうど 5 ポイント差は、ゆっくり／速めのほうに入る</b>（「5ポイント以上」と決めたため）。
 * 使った割合がない（予算が未設定・0円）ときは、一言を出さない。
 * 今のペースを述べるだけで、予測（このままだと…）や前もっての知らせはしない。
 * 色は、3つとも同じ濃いターコイズ（赤・青は予実比較の意味の色なので使わない）。文は messages.properties（top.pace.…）。
 * あとで予実比較に「着地見込み」を作るときに、この一言を置きかえる。
 */
public final class PaceComment {

	/** ゆっくり／速めと見なす差（ポイント） */
	public static final int POINTS = 5;

	private PaceComment() {
	}

	public enum Pace {
		/** 日の進み方より、使い方がゆっくり */
		SLOW,
		/** 日の進み方と、使い方はほぼ同じ */
		SAME,
		/** 日の進み方より、使い方が速め */
		FAST
	}

	/**
	 * @param usedPercent    使った割合（％）。予算が未設定・0円のときは null
	 * @param elapsedPercent 日の進み方（％）
	 */
	public static Optional<Pace> judge(Integer usedPercent, int elapsedPercent) {
		if (usedPercent == null) {
			return Optional.empty();
		}
		int difference = usedPercent - elapsedPercent;
		if (difference <= -POINTS) {
			return Optional.of(Pace.SLOW);
		}
		if (difference >= POINTS) {
			return Optional.of(Pace.FAST);
		}
		return Optional.of(Pace.SAME);
	}

}
