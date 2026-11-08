package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * グラフへのひとこと（設計書 5.12、要件定義書 4.5）。今週の数字から、決まった型で作る一言の「型と部品」を返す。
 * 文そのものは作らない（文は messages.properties。事実だけを述べ、評価・命令の言い方はしない）。
 * 画面にもデータベースにも依存しない（P1）。数字は、画面に出ている額（100円単位に切り上げた額）を使う。
 * <p>
 * 型
 * <ul>
 * <li>最多：今週いちばん多い科目（今週の取引が1件以上）。同額は科目の登録順で先。</li>
 * <li>残り：週の予算の残りと、土曜までの日数（週の予算が1科目以上に設定済み）。マイナスのときは、こえた額。</li>
 * <li>未使用：週の予算があるのに、今週まだ使っていない科目（今週の取引が1件以上ある週で）。週の予算が最大の1科目。</li>
 * </ul>
 * 選び方：出せる型を「最多 → 残り → 未使用」の順に並べ、日付（epochDay）÷ 出せる型の数 の余りで1つ選ぶ。
 * 同じ日は、いつも同じ型になる（再読み込みで変わらない）。
 */
public final class GraphComment {

	private GraphComment() {
	}

	public enum Type {
		/** 今週いちばん多い科目 */
		TOP,
		/** 週の予算の残りと、土曜までの日数 */
		REMAINING,
		/** 今週まだ使っていない科目 */
		UNUSED
	}

	/**
	 * 科目ごとの今週の数字。
	 *
	 * @param name   科目名
	 * @param budget 週の予算（未設定は null）
	 * @param actual 今週の実績（100円単位に切り上げた額）
	 */
	public record Row(String name, Long budget, long actual) {
	}

	/**
	 * 選んだ型と、文の部品。
	 *
	 * @param type         型
	 * @param categoryName 科目名（最多・未使用）。残りでは null
	 * @param amount       金額（最多：その科目の実績、残り：残りの額またはこえた額〔0以上〕）。未使用では0
	 * @param daysLeft     土曜まであと何日（日曜6〜土曜0）。残りで使う
	 * @param over         週の予算をこえているか（残り）
	 */
	public record Result(Type type, String categoryName, long amount, int daysLeft, boolean over) {
	}

	/** 出せる型を、「最多 → 残り → 未使用」の順に返す。 */
	public static List<Type> availableTypes(List<Row> rows, Long totalBudget, long totalActual) {
		List<Type> types = new ArrayList<>();
		if (rows.isEmpty()) {
			return types;
		}
		if (totalActual > 0) {
			types.add(Type.TOP);
		}
		if (totalBudget != null) {
			types.add(Type.REMAINING);
		}
		if (totalActual > 0 && unusedCategory(rows) != null) {
			types.add(Type.UNUSED);
		}
		return types;
	}

	/** 日付で型を1つ選んで、その部品を返す。出せる型がなければ空。 */
	public static Optional<Result> choose(List<Row> rows, Long totalBudget, long totalActual, LocalDate today) {
		List<Type> types = availableTypes(rows, totalBudget, totalActual);
		if (types.isEmpty()) {
			return Optional.empty();
		}
		Type type = types.get((int) Math.floorMod(today.toEpochDay(), (long) types.size()));
		return Optional.of(result(type, rows, totalBudget, totalActual, today));
	}

	/** 型を決めたあとの部品。その型が出せる材料があること（availableTypes に入っていること）が前提。 */
	public static Result result(Type type, List<Row> rows, Long totalBudget, long totalActual, LocalDate today) {
		return switch (type) {
			case TOP -> {
				Row top = rows.get(0);
				for (Row row : rows) {
					if (row.actual() > top.actual()) {      // 同額は先の科目のまま
						top = row;
					}
				}
				yield new Result(Type.TOP, top.name(), top.actual(), 0, false);
			}
			case REMAINING -> {
				long remaining = totalBudget - totalActual;
				int daysLeft = 7 - WeekPeriod.containing(today).dayNumber(today);
				yield new Result(Type.REMAINING, null, Math.abs(remaining), daysLeft, remaining < 0);
			}
			case UNUSED -> new Result(Type.UNUSED, unusedCategory(rows).name(), 0, 0, false);
		};
	}

	/** 週の予算が1円以上あって、今週の実績が0円の科目のうち、週の予算が最大のもの（同額は先の科目）。なければ null。 */
	private static Row unusedCategory(List<Row> rows) {
		Row best = null;
		for (Row row : rows) {
			if (row.budget() != null && row.budget() > 0 && row.actual() == 0
					&& (best == null || row.budget() > best.budget())) {
				best = row;
			}
		}
		return best;
	}

}
