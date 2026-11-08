package io.github.yutaro081.householdbudget.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 円グラフの並び・まとめ方・割合・区切り（設計書 5.11、要件定義書 4.5）。
 * 画面にもデータベースにも依存しない計算。整数（long、int）だけで行う。
 * <p>
 * 入力は、科目の登録順に並べた「科目名と、100円単位に切り上げた額」。
 * <ol>
 * <li>0円の科目は出さない</li>
 * <li>金額の多い順に並べる（同額は登録順。並べ替えは安定）</li>
 * <li>6科目以下は全部出す。7科目以上は、上位5科目＋「その他」（残りの合計）にまとめる</li>
 * <li>割合 ＝ 額 × 100 ÷ 合計 を四捨五入した整数％（足して100％にならなくてもよい）</li>
 * <li>円の区切りは1000分率。累積の額 × 1000 ÷ 合計（切り捨て）。最後はちょうど 1000</li>
 * <li>色の番号は 1〜6（ターコイズの濃い→薄い 5 段＋灰色）。6番目と「その他」は灰色</li>
 * </ol>
 */
public final class PieSlices {

	/** まとめた科目の名前 */
	public static final String OTHER_NAME = "その他";

	/** 円に出す最大の数（色の数） */
	public static final int MAX_SLICES = 6;

	/** 7科目以上のとき、そのまま出す上位の数 */
	private static final int TOP_WHEN_MERGED = MAX_SLICES - 1;

	private PieSlices() {
	}

	/** 入力：科目名と、切り上げた額 */
	public record Entry(String name, long amount) {
	}

	/**
	 * 出力：円の1切れ。
	 *
	 * @param name          科目名（まとめたときは「その他」）
	 * @param amount        額（まとめたときは合計）
	 * @param percent       割合（整数％。四捨五入）
	 * @param startPermille 円の中の開始位置（1000分率）
	 * @param endPermille   円の中の終了位置（1000分率）
	 * @param colorNumber   色の番号（1〜6。--hb-chart-N）
	 * @param mergedCount   まとめた科目の数（まとめていなければ 1）
	 */
	public record Slice(String name, long amount, int percent, int startPermille, int endPermille, int colorNumber,
			int mergedCount) {

		/** conic-gradient の1区間。数字と固定の文字だけで組み立てる（科目名は入れない。設計書 7.5）。例：var(--hb-chart-1) 0.0% 60.0% */
		public String cssStop() {
			return "var(--hb-chart-" + colorNumber + ") " + percentText(startPermille) + " " + percentText(endPermille);
		}
	}

	/** 1000分率を「整数部.小数1桁%」の文字にする（例：375 → 37.5%）。 */
	public static String percentText(int permille) {
		return (permille / 10) + "." + (permille % 10) + "%";
	}

	/** conic-gradient の中身（区間をカンマでつないだもの）。 */
	public static String cssStops(List<Slice> slices) {
		List<String> stops = new ArrayList<>();
		for (Slice slice : slices) {
			stops.add(slice.cssStop());
		}
		return String.join(", ", stops);
	}

	public static List<Slice> of(List<Entry> entries) {
		List<Entry> used = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.amount() > 0) {
				used.add(entry);
			}
		}
		if (used.isEmpty()) {
			return List.of();
		}
		// List.sort は安定なので、同額は入力の順（登録順）のまま
		used.sort(Comparator.comparingLong(Entry::amount).reversed());

		List<Entry> shown = new ArrayList<>();
		List<Integer> merged = new ArrayList<>();
		if (used.size() <= MAX_SLICES) {
			for (Entry entry : used) {
				shown.add(entry);
				merged.add(1);
			}
		} else {
			for (int i = 0; i < TOP_WHEN_MERGED; i++) {
				shown.add(used.get(i));
				merged.add(1);
			}
			long rest = 0;
			for (int i = TOP_WHEN_MERGED; i < used.size(); i++) {
				rest += used.get(i).amount();
			}
			shown.add(new Entry(OTHER_NAME, rest));
			merged.add(used.size() - TOP_WHEN_MERGED);
		}

		long total = 0;
		for (Entry entry : shown) {
			total += entry.amount();
		}

		List<Slice> slices = new ArrayList<>();
		long cumulative = 0;
		int start = 0;
		for (int i = 0; i < shown.size(); i++) {
			Entry entry = shown.get(i);
			cumulative += entry.amount();
			boolean last = i == shown.size() - 1;
			int end = last ? PeriodProgress.FULL : (int) (cumulative * PeriodProgress.FULL / total);
			int percent = (int) ((entry.amount() * 200 + total) / (total * 2));   // 四捨五入（0.5 は切り上げ）
			slices.add(new Slice(entry.name(), entry.amount(), percent, start, end, i + 1, merged.get(i)));
			start = end;
		}
		return slices;
	}

}
