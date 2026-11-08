package io.github.yutaro081.householdbudget.dto;

import java.util.List;

/**
 * トップの「きょうのひとこと」の表示データ（設計書 4.7、5.12）。TopView が持つ。
 *
 * @param items     暦のひとこと（1〜2項目。行事があれば行事が先、その下に節気）
 * @param nextSekki 次の節気の補足。節気の当日と、表にない年（代わりの計算）は null
 * @param graph     グラフへのひとこと。出せる型がないときは null
 */
public record HitokotoView(List<CalendarItem> items, NextSekki nextSekki, GraphLine graph) {

	/** 次の節気の補足（「次の節気：立冬まであと3日」） */
	public record NextSekki(String name, int days) {
	}

	/**
	 * グラフへのひとこと。文は messages.properties のキーで持ち、画面が引数を入れて出す。
	 * 引数は、科目名（th:text で出す）、金額、日数。使わない引数は null。
	 */
	public record GraphLine(String key, Object arg0, Object arg1) {
	}

	/** 枠に出すものがあるか（何もなければ、枠ごと出さない） */
	public boolean hasContent() {
		return !items.isEmpty() || graph != null;
	}

}
