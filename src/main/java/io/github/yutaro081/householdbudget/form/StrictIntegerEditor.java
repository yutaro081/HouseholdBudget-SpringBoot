package io.github.yutaro081.householdbudget.form;

import java.beans.PropertyEditorSupport;
import java.util.regex.Pattern;

/**
 * 金額（予算額）を整数に読む、厳しい変換（設計書 6.5、P9、Q5-1 の C、R13）。
 * Spring 標準の変換は、全角数字・途中の空白・16進（0x10、#10）・先頭の + を通してしまうため、使わない。
 * <p>
 * 規則：
 * <ol>
 * <li>前後の空白（全角の空白を含む）を strip() で取り除く。取り除いて空なら null（必須エラーになる）。</li>
 * <li>全角の数字（０〜９、U+FF10〜U+FF19）を、半角の数字（0〜9）に置き換える（R13。日本語入力がオンのまま打つと全角になるため）。
 * 置き換えるのは、この10文字だけ。全角のマイナス・プラス・カンマ・ピリオド、途中の全角の空白、ほかの国の数字、漢数字などは置き換えない。</li>
 * <li>半角の数字だけ、または先頭の - と半角の数字だけなら、整数として読む（010 は 10。8進数にしない）。</li>
 * <li>それ以外（途中の空白、0x10・#10、カンマ、小数、10円、+5、全角のマイナスなど）と、整数に収まらない数は、
 * IllegalArgumentException を投げる。Spring はこれを型変換のエラー（typeMismatch）にする。</li>
 * </ol>
 * 使い方：Controller の @InitBinder で、金額の項目にだけ登録する（registerCustomEditor）。他の整数の項目には使わない。
 */
public class StrictIntegerEditor extends PropertyEditorSupport {

	/** 半角の数字だけ、または先頭の - と半角の数字だけ。\d は使わない（文字の種類を明示するため）。 */
	private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

	/** 全角の数字の範囲（０ U+FF10 〜 ９ U+FF19） */
	private static final char FULL_WIDTH_ZERO = '０';
	private static final char FULL_WIDTH_NINE = '９';

	@Override
	public void setAsText(String text) {
		String stripped = (text == null) ? "" : text.strip();
		if (stripped.isEmpty()) {
			setValue(null);
			return;
		}
		stripped = toHalfWidthDigits(stripped);
		if (!INTEGER.matcher(stripped).matches()) {
			throw new IllegalArgumentException("not an integer");
		}
		// 整数に収まらない数は NumberFormatException（IllegalArgumentException の仲間）になり、型変換のエラーになる
		setValue(Integer.valueOf(stripped));
	}

	/**
	 * 全角の数字（０〜９、U+FF10〜U+FF19）だけを、半角の数字に置き換える（R13）。
	 * Character.digit などは使わない（ほかの国の数字（١٢٣ など）まで数字として読んでしまうため）。
	 */
	static String toHalfWidthDigits(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			sb.append((c >= FULL_WIDTH_ZERO && c <= FULL_WIDTH_NINE) ? (char) ('0' + (c - FULL_WIDTH_ZERO)) : c);
		}
		return sb.toString();
	}

	@Override
	public String getAsText() {
		Object value = getValue();
		return (value == null) ? "" : value.toString();
	}

}
