package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.Vendor;

/**
 * CSV の書き方（issue 004。設計書 4.5、5.18、7.2）の単体テスト。DB も Spring も使わない。
 * <ul>
 * <li>{@link TransactionCsvWriter#cell}：値 1 つの書き方（" で囲む・"" に重ねる・数式の印 '）</li>
 * <li>{@link TransactionCsvWriter#write}：BOM・見出しの行・CRLF・列の形・分割の番号</li>
 * </ul>
 * どの列に cell をかけるか（日付・金額にはかけない）、一覧と同じ並びかは、TransactionCsvExportTest が画面（HTTP）から確かめる。
 */
class TransactionCsvWriterTest {

	private static final String HEADER = "日付,科目,取引先,支払方法,摘要,金額,分割";
	private static final String CRLF = "\r\n";

	// ---------------------------------------------------------------
	// 値 1 つの書き方（cell）
	// ---------------------------------------------------------------

	/** ふつうの値は、" で囲むだけ */
	@Test
	void aPlainValueIsOnlyQuoted() {
		assertThat(TransactionCsvWriter.cell("食費")).isEqualTo("\"食費\"");
	}

	/** 空と null（摘要がない取引）は、空の文字を囲んだ値 */
	@Test
	void emptyAndNullAreAnEmptyQuotedValue() {
		assertThat(TransactionCsvWriter.cell("")).isEqualTo("\"\"");
		assertThat(TransactionCsvWriter.cell(null)).isEqualTo("\"\"");
	}

	/** " は "" に重ねる（RFC 4180）。いくつあっても、全部 */
	@Test
	void quotesAreDoubled() {
		assertThat(TransactionCsvWriter.cell("a\"b")).isEqualTo("\"a\"\"b\"");
		assertThat(TransactionCsvWriter.cell("\"\"")).isEqualTo("\"\"\"\"\"\"");
	}

	/** カンマと、途中の CR・LF・CRLF は、囲みの中にそのまま入る（1 つの値のまま） */
	@Test
	void commasAndLineBreaksStayInsideTheQuotes() {
		assertThat(TransactionCsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
		assertThat(TransactionCsvWriter.cell("a\rb")).isEqualTo("\"a\rb\"");
		assertThat(TransactionCsvWriter.cell("a\nb")).isEqualTo("\"a\nb\"");
		assertThat(TransactionCsvWriter.cell("a\r\nb")).isEqualTo("\"a\r\nb\"");
	}

	/** 絵文字などの、Shift_JIS にない文字も、そのまま（UTF-8 で書くので、変わらない） */
	@Test
	void emojiAndOtherCharactersStayAsTheyAre() {
		assertThat(TransactionCsvWriter.cell("🍙おにぎり")).isEqualTo("\"🍙おにぎり\"");
		assertThat(TransactionCsvWriter.cell("髙島屋")).isEqualTo("\"髙島屋\"");
	}

	/**
	 * 数式になりうる文字（半角の = + - @、タブ、CR、LF、全角の ＝ ＋ － ＠、' の 12 個）で始まる値は、先頭に ' を 1 つ付けてから囲む
	 * （CSV インジェクション対策。設計書 5.18）。' 自身も対象（取り込みで、先頭の ' を 1 つ外せば元に戻るようにするため）
	 */
	@ParameterizedTest(name = "[{0}] で始まる値には ' が付く")
	@ValueSource(strings = { "=", "+", "-", "@", "\t", "\r", "\n", "＝", "＋", "－", "＠", "'" })
	void aValueStartingWithAFormulaCharacterGetsTheMark(String first) {
		assertThat(TransactionCsvWriter.cell(first + "1+1")).isEqualTo("\"'" + first + "1+1\"");
	}

	/** 数式になりうる文字の 1 文字だけの値にも付く */
	@ParameterizedTest(name = "[{0}] だけの値にも ' が付く")
	@ValueSource(strings = { "=", "+", "-", "@", "＝", "＋", "－", "＠", "'" })
	void aSingleFormulaCharacterGetsTheMark(String value) {
		assertThat(TransactionCsvWriter.cell(value)).isEqualTo("\"'" + value + "\"");
	}

	/** 例：=1+1 → "'=1+1"、'abc → "''abc"、="x" → "'=""x"""（印を付けてから、" を重ねる） */
	@Test
	void examplesFromTheDesign() {
		assertThat(TransactionCsvWriter.cell("=1+1")).isEqualTo("\"'=1+1\"");
		assertThat(TransactionCsvWriter.cell("'abc")).isEqualTo("\"''abc\"");
		assertThat(TransactionCsvWriter.cell("=\"x\"")).isEqualTo("\"'=\"\"x\"\"\"");
		assertThat(TransactionCsvWriter.cell("=HYPERLINK(\"a\",\"b\")")).isEqualTo("\"'=HYPERLINK(\"\"a\"\",\"\"b\"\")\"");
	}

	/** 先頭でなければ、付けない。先頭でも、12 個のほかの記号（# % ＃、マイナスの記号 −〔U+2212〕、空白など）には付けない */
	@ParameterizedTest(name = "[{0}] には ' が付かない")
	@ValueSource(strings = { "a=b", "1+1", "x@y", "a'b", "#1", "%1", "＃1", "−1", " =1", "（=）", "食費" })
	void otherValuesDoNotGetTheMark(String value) {
		assertThat(TransactionCsvWriter.cell(value)).isEqualTo("\"" + value + "\"");
	}

	// ---------------------------------------------------------------
	// ファイルにする所（write）
	// ---------------------------------------------------------------

	private static final Category FOOD = new Category(1L, "食費");
	private static final Category DAILY = new Category(1L, "日用品");
	private static final Vendor SHOP = new Vendor(1L, "スーパー");
	private static final PaymentMethod CASH = new PaymentMethod(1L, "現金");

	private static Transaction row(LocalDate date, Category category, int amount, String description, Long receiptGroup) {
		Transaction transaction = new Transaction(1L, date, amount, category, description, SHOP, CASH);
		transaction.changeReceiptGroup(receiptGroup);
		return transaction;
	}

	/** 先頭の 3 バイトは UTF-8 の BOM（EF BB BF） */
	private static String withoutBom(byte[] bytes) {
		assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
		return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
	}

	/** 取引が 0 件：BOM と、見出しの行（CRLF で終わる）だけ */
	@Test
	void noRowsGiveTheBomAndTheHeaderLineOnly() {
		assertThat(withoutBom(TransactionCsvWriter.write(List.of()))).isEqualTo(HEADER + CRLF);
	}

	/** 見出しの行は、固定の文字（囲まない。数式の印もかけない） */
	@Test
	void theHeaderLineIsFixed() {
		assertThat(TransactionCsvWriter.HEADER).isEqualTo(HEADER);
	}

	/** 日付は yyyy-MM-dd（0 で埋める）、金額はカンマなしの整数、文字の 4 列は囲む、分割でない行は空。どの行も CRLF で終わる（最後の行も） */
	@Test
	void eachRowHasTheColumnsInOrderAndEndsWithCrlf() {
		byte[] bytes = TransactionCsvWriter.write(List.of(
				row(LocalDate.of(2026, 11, 9), FOOD, 3000, "昼食", null),
				row(LocalDate.of(2026, 1, 5), DAILY, 1_000_000, null, null)));

		assertThat(withoutBom(bytes)).isEqualTo(HEADER + CRLF
				+ "2026-11-09,\"食費\",\"スーパー\",\"現金\",\"昼食\",3000," + CRLF
				+ "2026-01-05,\"日用品\",\"スーパー\",\"現金\",\"\",1000000," + CRLF);
	}

	/** 分割の番号：印（receipt_group）の値は出さず、出てきた順に 1・2・3…。同じ印は同じ番号（隣でなくても）。印のない行は空 */
	@Test
	void theSplitNumbersFollowTheOrderOfAppearanceAndHideTheMarks() {
		LocalDate day = LocalDate.of(2026, 11, 9);
		byte[] bytes = TransactionCsvWriter.write(List.of(
				row(day, FOOD, 1, "a", 500L),
				row(day, DAILY, 2, "b", 500L),
				row(day, FOOD, 3, "c", null),
				row(day, FOOD, 4, "d", 120L),
				row(day, DAILY, 5, "e", 120L),
				row(day, FOOD, 6, "f", 999L),
				row(day, FOOD, 7, "g", 500L)));

		List<String> splits = Arrays.stream(withoutBom(bytes).split(CRLF)).skip(1).map(line -> line.substring(line.lastIndexOf(',') + 1)).toList();
		assertThat(splits).containsExactly("1", "1", "", "2", "2", "3", "1");
	}

	/** 名前と摘要の数式の印と囲みは、ファイルにしても同じ（cell をかける）。日付・金額にはかけない */
	@Test
	void theFourTextColumnsUseCell() {
		Transaction transaction = new Transaction(1L, LocalDate.of(2026, 11, 9), 100, new Category(1L, "=科目"), "a\"b",
				new Vendor(1L, "@店"), new PaymentMethod(1L, "＋払い"));

		assertThat(withoutBom(TransactionCsvWriter.write(List.of(transaction))))
				.isEqualTo(HEADER + CRLF + "2026-11-09,\"'=科目\",\"'@店\",\"'＋払い\",\"a\"\"b\",100," + CRLF);
	}
}
