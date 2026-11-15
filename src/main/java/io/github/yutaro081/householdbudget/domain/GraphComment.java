package io.github.yutaro081.householdbudget.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * グラフへのひとこと（設計書 5.12、要件定義書 4.5、issue 028）。今週の数字から、決まった型で作る一言の「型と部品」を返す。
 * 文そのものは作らない（文は messages.properties。事実だけを述べ、評価・命令の言い方はしない）。
 * 画面にもデータベースにも依存しない（P1）。金額は、科目ごとに100円単位に切り上げた額を使う（今週の実績も、先週の実績も）。
 * <p>
 * 型（並べる順）
 * <ul>
 * <li>1日あたり：（週の予算の合計 − 今週の実績の合計）÷ 土曜までの日数（今日を含む）を、100円単位で切り捨て。0円以下なら出さない。</li>
 * <li>週の予算以下の数：週の予算が0円より大きい科目のうち、実績が週の予算以下の科目の数。今週の取引が1件以上で、1科目以上のとき。</li>
 * <li>残りの割合：週の予算が0円より大きく、今週使った科目のうち、残りの割合（1割単位で切り捨て）がいちばん大きい科目。1割以上のとき。</li>
 * <li>先週と比べる：今週の実績の合計 − 先週の同じ曜日までの実績の合計。先週の分が0円より大きいとき。</li>
 * <li>記録の件数：今週の取引の件数。1件以上のとき。</li>
 * <li>未使用：週の予算があるのに、今週まだ使っていない科目（今週の取引が1件以上ある週で）。週の予算が最大の1科目。</li>
 * </ul>
 * 週の始め（日曜で、今週の取引が0件で、週の予算の合計が0円より大きい）の日は、ほかの型を出さず、これだけを出す。
 * 選び方：出せる型を上の順に並べ、日付（epochDay）÷ 出せる型の数 の余りで1つ選ぶ。同じ日は、いつも同じ型になる（再読み込みで変わらない）。
 */
public final class GraphComment {

	/** 割合の単位（1割 ＝ 10分の1） */
	private static final long TENTHS = 10;

	private GraphComment() {
	}

	public enum Type {
		/** 残りを日数で割った、1日あたりの額 */
		PER_DAY,
		/** 週の予算以下の科目の数 */
		NOT_OVER,
		/** 週の予算の残りの割合がいちばん大きい科目 */
		ROOM,
		/** 先週の同じ曜日までとの差 */
		LAST_WEEK,
		/** 今週の取引の件数 */
		COUNT,
		/** 今週まだ使っていない科目 */
		UNUSED,
		/** 新しい週（日曜で、まだ使っていない） */
		WEEK_START
	}

	/**
	 * 科目ごとの数字。
	 *
	 * @param name           科目名
	 * @param budget         週の予算（未設定は null）
	 * @param actual         今週の実績（100円単位に切り上げた額）
	 * @param count          今週の取引の件数
	 * @param lastWeekActual 先週の日曜から、今日と同じ曜日までの実績（100円単位に切り上げた額）
	 */
	public record Row(String name, Long budget, long actual, long count, long lastWeekActual) {
	}

	/**
	 * 選んだ型と、文の部品。使わない部品は 0 か null。
	 *
	 * @param type         型
	 * @param categoryName 科目名（残りの割合・未使用）
	 * @param amount       金額か数（1日あたり：1日あたりの額、週の予算以下の数：週の予算がある科目の数、
	 *                     先週と比べる：今週 − 先週〔マイナスは少ない〕、記録の件数：件数、週の始め：週の予算の合計）
	 * @param number       小さな数（1日あたり：今日を含めた残りの日数、週の予算以下の数：そのうち週の予算以下の科目の数、残りの割合：割）
	 */
	public record Result(Type type, String categoryName, long amount, int number) {
	}

	/** 出せる型を返す。週の始めの日は、週の始めだけ。ほかの日は「1日あたり → … → 未使用」の順。 */
	public static List<Type> availableTypes(List<Row> rows, Long totalBudget, long totalActual, LocalDate today) {
		List<Type> types = new ArrayList<>();
		if (rows.isEmpty()) {
			return types;
		}
		long count = count(rows);
		if (isWeekStart(totalBudget, count, today)) {
			types.add(Type.WEEK_START);
			return types;
		}
		if (totalBudget != null && perDay(totalBudget, totalActual, today) > 0) {
			types.add(Type.PER_DAY);
		}
		if (count > 0 && notOverCount(rows) > 0) {
			types.add(Type.NOT_OVER);
		}
		Row room = roomCategory(rows);
		if (room != null && tenthsLeft(room) > 0) {
			types.add(Type.ROOM);
		}
		if (lastWeekTotal(rows) > 0) {
			types.add(Type.LAST_WEEK);
		}
		if (count > 0) {
			types.add(Type.COUNT);
		}
		if (count > 0 && unusedCategory(rows) != null) {
			types.add(Type.UNUSED);
		}
		return types;
	}

	/** 日付で型を1つ選んで、その部品を返す。出せる型がなければ空。 */
	public static Optional<Result> choose(List<Row> rows, Long totalBudget, long totalActual, LocalDate today) {
		List<Type> types = availableTypes(rows, totalBudget, totalActual, today);
		if (types.isEmpty()) {
			return Optional.empty();
		}
		Type type = types.get((int) Math.floorMod(today.toEpochDay(), (long) types.size()));
		return Optional.of(result(type, rows, totalBudget, totalActual, today));
	}

	/** 型を決めたあとの部品。その型が出せる材料があること（availableTypes に入っていること）が前提。 */
	public static Result result(Type type, List<Row> rows, Long totalBudget, long totalActual, LocalDate today) {
		return switch (type) {
			case PER_DAY -> new Result(type, null, perDay(totalBudget, totalActual, today), daysLeft(today));
			case NOT_OVER -> new Result(type, null, budgetedCount(rows), (int) notOverCount(rows));
			case ROOM -> {
				Row room = roomCategory(rows);
				yield new Result(type, room.name(), 0, (int) tenthsLeft(room));
			}
			case LAST_WEEK -> new Result(type, null, totalActual - lastWeekTotal(rows), 0);
			case COUNT -> new Result(type, null, count(rows), 0);
			case UNUSED -> new Result(type, unusedCategory(rows).name(), 0, 0);
			case WEEK_START -> new Result(type, null, totalBudget, 0);
		};
	}

	/** 週の始め：日曜で、今週の取引が0件で、週の予算の合計が0円より大きい */
	private static boolean isWeekStart(Long totalBudget, long count, LocalDate today) {
		return WeekPeriod.containing(today).dayNumber(today) == 1 && count == 0 && totalBudget != null && totalBudget > 0;
	}

	/** 土曜までの日数（今日を含む。日曜 7 〜 土曜 1） */
	private static int daysLeft(LocalDate today) {
		return WeekPeriod.DAYS + 1 - WeekPeriod.containing(today).dayNumber(today);
	}

	/** 1日あたりの額（100円単位で切り捨て）。残りが0円以下なら0以下 */
	private static long perDay(long totalBudget, long totalActual, LocalDate today) {
		long remaining = totalBudget - totalActual;
		if (remaining <= 0) {
			return 0;
		}
		return remaining / daysLeft(today) / WeeklyAmount.UNIT * WeeklyAmount.UNIT;
	}

	private static long count(List<Row> rows) {
		return rows.stream().mapToLong(Row::count).sum();
	}

	private static long lastWeekTotal(List<Row> rows) {
		return rows.stream().mapToLong(Row::lastWeekActual).sum();
	}

	private static boolean hasBudget(Row row) {
		return row.budget() != null && row.budget() > 0;
	}

	/** 週の予算が0円より大きい科目の数 */
	private static long budgetedCount(List<Row> rows) {
		return rows.stream().filter(GraphComment::hasBudget).count();
	}

	/** 週の予算が0円より大きい科目のうち、予算超え（赤）でない科目の数（実績 ≦ 週の予算。今週使っていない科目も数える） */
	private static long notOverCount(List<Row> rows) {
		return rows.stream().filter(GraphComment::hasBudget)
				.filter(row -> BudgetColorJudge.judge(row.budget(), row.actual()) != BudgetColor.RED).count();
	}

	/** 残りの割合（1割単位で切り捨て。超えたときは0以下） */
	private static long tenthsLeft(Row row) {
		return (row.budget() - row.actual()) * TENTHS / row.budget();
	}

	/** 週の予算が0円より大きく、今週使った科目のうち、残りの割合がいちばん大きいもの（同じなら先の科目）。なければ null */
	private static Row roomCategory(List<Row> rows) {
		Row best = null;
		for (Row row : rows) {
			if (hasBudget(row) && row.actual() > 0 && (best == null || tenthsLeft(row) > tenthsLeft(best))) {
				best = row;
			}
		}
		return best;
	}

	/** 週の予算が1円以上あって、今週の実績が0円の科目のうち、週の予算が最大のもの（同額は先の科目）。なければ null。 */
	private static Row unusedCategory(List<Row> rows) {
		Row best = null;
		for (Row row : rows) {
			if (hasBudget(row) && row.actual() == 0 && (best == null || row.budget() > best.budget())) {
				best = row;
			}
		}
		return best;
	}

}
