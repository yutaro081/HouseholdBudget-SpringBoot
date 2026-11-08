package io.github.yutaro081.householdbudget.dto;

/**
 * きょうのひとこと（暦のひとこと）の1項目（設計書 4.7、5.12）。
 * 画面が読む値は、すべて HitokotoView の中に入れて渡す。名前・読み・文は、アプリの中のファイルの文（利用者の入力ではない）。
 *
 * @param kind    種類（年中行事・二十四節気・七十二候）。画面の小さい見出しになる
 * @param name    名前（行事の名前、節気の名前、候の名前）
 * @param reading 読み（年中行事にはない。そのときは null）
 * @param text    その日の文
 * @param detail  題の補足。七十二候は「立冬の初候」、二十四節気は「立冬」、年中行事は null（画面の小さい題「七十二候（立冬の初候）」の括弧の中。issue 017）
 */
public record CalendarItem(Kind kind, String name, String reading, String text, String detail) {

	public enum Kind {
		/** 年中行事 */
		EVENT,
		/** 二十四節気 */
		SEKKI,
		/** 七十二候 */
		KO
	}

}
