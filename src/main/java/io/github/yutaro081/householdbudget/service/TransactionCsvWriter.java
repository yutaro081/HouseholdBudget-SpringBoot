package io.github.yutaro081.householdbudget.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yutaro081.householdbudget.entity.Transaction;

/**
 * 取引の行の並びを、CSV ファイルのバイトの並びにする（issue 004。設計書 4.5、5.18）。
 * <p>
 * ファイルの形：UTF-8 で、先頭に BOM。1 行目は見出しの行 {@link #HEADER}。2 行目から、取引（行）1 件ごとに 1 行。
 * どの行も CRLF で終える（最後の行も）。合計の行は付けない。行の並びは、渡された順のまま（取引一覧と同じ並び。呼ぶ側が渡す）。
 * <ul>
 * <li>文字の 4 列（科目・取引先・支払方法・摘要）は {@link #cell} で書く（" で囲む・"" に重ねる・数式の印）</li>
 * <li>日付は yyyy-MM-dd、金額はカンマなしの整数。どちらも囲まず、数式の印もかけない（サーバーが作る数字と「-」だけ。
 * 金額は 1〜1,000,000 でマイナスにならない）</li>
 * <li>分割の列：同じレシートの取引（印 receipt_group が同じ行）に、出てきた順に 1・2・3…。分割でない行は空。
 * <b>DB の印の値は出さない</b>（設計書 3.9、7.12 の S12）</li>
 * </ul>
 * <b>domain に置かない理由</b>（手順書 issue-004-csv-export の Q004-1）：domain は季節など家計の決まりの置き場で、" や ' は
 * CSV というファイルの形の決まりのため。CSV の取り込み（issue 013）で読み取りを作るとき、書き方と読み方を同じ場所に並べられるため。
 * <p>
 * 状態を持たない（static なメソッドだけ）。DB は読まない（科目・取引先・支払方法は、渡された行が一緒に読み込んであるものを使う）。
 */
public final class TransactionCsvWriter {

	/** 見出しの行（固定の文字。囲まない。数式の印もかけない） */
	public static final String HEADER = "日付,科目,取引先,支払方法,摘要,金額,分割";

	/** UTF-8 の BOM（EF BB BF）。Excel は、これで UTF-8 と見分ける（設計書 5.18） */
	private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	/** 行の終わり（RFC 4180） */
	private static final String CRLF = "\r\n";

	/**
	 * 数式になりうる、値の先頭の文字（CSV インジェクション対策。設計書 5.18、7.2）：半角の = + - @、タブ、CR、LF、全角の ＝ ＋ － ＠、'。
	 * ' 自身も入れる：このアプリが出したファイルをそのまま取り込むとき、先頭の ' を 1 つ外すだけで、どの値も元に戻るようにするため
	 */
	private static final String FORMULA_STARTS = "=+-@\t\r\n＝＋－＠'";

	private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private TransactionCsvWriter() {
	}

	/**
	 * 取引の行の並びを、CSV ファイルのバイトの並び（BOM ＋ UTF-8）にする。ファイルは、全部作ってから返す（設計書 5.18）。
	 *
	 * @param rows 取引の行（取引一覧と同じ並び。科目・取引先・支払方法は読み込み済み）。0 件なら、見出しの行だけ
	 */
	public static byte[] write(List<Transaction> rows) {
		StringBuilder csv = new StringBuilder();
		csv.append(HEADER).append(CRLF);
		Map<Long, Integer> splitNumbers = new HashMap<>();   // 印 → ファイルの中の番号（出てきた順に 1・2・3…）
		for (Transaction transaction : rows) {
			String split = "";
			if (transaction.getReceiptGroup() != null) {
				Integer number = splitNumbers.get(transaction.getReceiptGroup());
				if (number == null) {
					number = splitNumbers.size() + 1;
					splitNumbers.put(transaction.getReceiptGroup(), number);
				}
				split = String.valueOf(number);
			}
			csv.append(transaction.getTransactionDate().format(DATE)).append(',')
					.append(cell(transaction.getCategory().getName())).append(',')
					.append(cell(transaction.getVendor().getName())).append(',')
					.append(cell(transaction.getPaymentMethod().getName())).append(',')
					.append(cell(transaction.getDescription())).append(',')
					.append(transaction.getAmount()).append(',')
					.append(split).append(CRLF);
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bytes.writeBytes(BOM);
		bytes.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
		return bytes.toByteArray();
	}

	/**
	 * 文字の値 1 つの書き方（RFC 4180 と、CSV インジェクション対策）。
	 * <ol>
	 * <li>値の先頭が、数式になりうる文字（{@link #FORMULA_STARTS}）なら、先頭に ' を 1 つ付ける（例：=1+1 → '=1+1、'abc → ''abc）</li>
	 * <li>" を "" に重ね、全体を " で囲む（カンマ・途中の改行も、囲みの中にそのまま入る）</li>
	 * </ol>
	 * null（摘要がない取引）と空は、空の文字を囲んだ値（""）。
	 */
	public static String cell(String value) {
		String text = value == null ? "" : value;
		if (!text.isEmpty() && FORMULA_STARTS.indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}
}
