package io.github.yutaro081.householdbudget.domain;

import java.time.Month;

/**
 * 季節（春・夏・秋・冬）と、月との対応（設計書 4.5、5.8）。
 * 画面にもデータベースにも依存しない計算だけを持つ（P1）。
 * <p>
 * 春は3〜5月、夏は6〜8月、秋は9〜11月、冬は12〜2月。
 * データベースには、この列挙の名前（SPRING など）を文字で保存する（Q27 の a）。
 */
public enum Season {

	SPRING("spring"),
	SUMMER("summer"),
	AUTUMN("autumn"),
	WINTER("winter");

	private final String urlName;

	Season(String urlName) {
		this.urlName = urlName;
	}

	/** URL で使う名前（spring、summer、autumn、winter） */
	public String getUrlName() {
		return urlName;
	}

	/** 月から季節を求める（冬は12月、1月、2月）。 */
	public static Season fromMonth(Month month) {
		return switch (month) {
			case MARCH, APRIL, MAY -> SPRING;
			case JUNE, JULY, AUGUST -> SUMMER;
			case SEPTEMBER, OCTOBER, NOVEMBER -> AUTUMN;
			case DECEMBER, JANUARY, FEBRUARY -> WINTER;
		};
	}

}
