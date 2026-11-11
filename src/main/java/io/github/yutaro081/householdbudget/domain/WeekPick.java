package io.github.yutaro081.householdbudget.domain;

import java.util.List;
import java.util.OptionalInt;

/**
 * 今週の科目のひとこと：「今週いちばん進んでいる科目」の選び方（要件定義書 4.5、設計書 5.15、issue 020）。
 * 画面にもデータベースにも依存しない。整数（long）だけで比べる。
 * <ol>
 * <li>週の予算が未設定（null）・0 円の科目は除く。</li>
 * <li>週の予算に対する今週の実績の割合（どちらも 100 円単位に切り上げた額）がいちばん高い科目。週の予算を超えていなくても選ぶ。</li>
 * <li>割合が同じなら、並びが先（リストの前のほう）のもの。割合は、かけ算で比べる（小数を使わない）。</li>
 * <li>今週の実績が 0 円の科目は選ばない（「進んでいる」と言えないため）。今週の取引が 1 件もなければ、どの科目も選ばれない。</li>
 * </ol>
 */
public final class WeekPick {

	private WeekPick() {
	}

	/**
	 * 候補の 1 科目。
	 *
	 * @param name         科目名
	 * @param weeklyBudget 週の予算（円。100 円単位）。未設定なら null
	 * @param weeklyActual 今週の実績（円。100 円単位に切り上げた額）
	 */
	public record Candidate(String name, Long weeklyBudget, long weeklyActual) {
	}

	/**
	 * 選んだ科目の位置（candidates の何番目か。0 始まり）。選ばれる科目がないときは空。
	 *
	 * @throws ArithmeticException あふれたとき
	 */
	public static OptionalInt choose(List<Candidate> candidates) {
		int best = -1;
		for (int i = 0; i < candidates.size(); i++) {
			Candidate c = candidates.get(i);
			if (c.weeklyBudget() == null || c.weeklyBudget() <= 0 || c.weeklyActual() <= 0) {
				continue;
			}
			// c の割合 ＞ 今の最高の割合 ⇔ c.実績 × 最高.予算 ＞ 最高.実績 × c.予算（同じなら、先のものを残す）
			if (best < 0 || isHigher(c, candidates.get(best))) {
				best = i;
			}
		}
		return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
	}

	private static boolean isHigher(Candidate c, Candidate best) {
		return Math.multiplyExact(c.weeklyActual(), best.weeklyBudget()) > Math.multiplyExact(best.weeklyActual(), c.weeklyBudget());
	}
}
