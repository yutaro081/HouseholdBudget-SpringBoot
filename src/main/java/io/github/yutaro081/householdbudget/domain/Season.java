package io.github.yutaro081.householdbudget.domain;

import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 季節（春・夏・秋・冬）と、月との対応（設計書 4.5、5.8）。
 * 画面にもデータベースにも依存しない計算だけを持つ（P1）。
 * <p>
 * 春は3〜5月、夏は6〜8月、秋は9〜11月、冬は12〜2月。
 * データベースには、この列挙の名前（SPRING など）を文字で保存する（Q27 の a）。
 * <p>
 * 列挙の宣言の順（春→夏→秋→冬）が、期間の順（1年の中での古い新しい）。
 * DB に保存する文字の順（AUTUMN < SPRING < SUMMER < WINTER）とは違うので、古い新しいの比べには使わない（設計書 5.7）。
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

	/**
	 * URL の名前（spring、summer、autumn、winter）から季節を求める（設計書 2.4、Q6-2 の A）。
	 * 小文字の4つだけを受け付ける。大文字（Spring、SPRING）、前後の空白、空、null は「なし」。
	 */
	public static Optional<Season> fromUrlName(String urlName) {
		if (urlName == null) {
			return Optional.empty();
		}
		return Arrays.stream(values()).filter(season -> season.urlName.equals(urlName)).findFirst();
	}

	/**
	 * 同じ年の中で、この季節より前の季節（期間の順：春→夏→秋→冬）。
	 * 例：秋なら春と夏。春なら空。冬の年は12月がある年なので、前の年の冬は含まない。
	 * 「表示季節より前の予算」を DB から探す検索に使う（設計書 4.6）。
	 */
	public List<Season> earlierInYear() {
		return Arrays.stream(values()).filter(season -> season.ordinal() < ordinal()).toList();
	}

}
