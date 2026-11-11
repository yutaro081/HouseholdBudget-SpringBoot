package io.github.yutaro081.householdbudget.domain;

import java.util.Optional;

/**
 * 自動予算で選ぶ割合（設計書 4.2、5.14、issue 019）。
 * 1＝これで OK（100%）、2＝節約したい（90%）、3＝上乗せしたい（110%）。
 * auto の検査（守りに関わる所）の元になるので、"1"・"2"・"3" と完全に同じ文字だけを受け付ける。
 */
public enum ProposalChoice {

	/** 1. これで OK（100%） */
	OK(100),
	/** 2. 節約したい（90%） */
	SAVE(90),
	/** 3. 上乗せしたい（110%） */
	EXTRA(110);

	private final int percent;

	ProposalChoice(int percent) {
		this.percent = percent;
	}

	/** 割合（%）。 */
	public int percent() {
		return percent;
	}

	/** URL の auto の文字列から求める。"1"・"2"・"3" と完全に同じものだけ。null・空・"01"・全角・前後の空白などは空。 */
	public static Optional<ProposalChoice> fromParam(String value) {
		if (value == null) {
			return Optional.empty();
		}
		return switch (value) {
			case "1" -> Optional.of(OK);
			case "2" -> Optional.of(SAVE);
			case "3" -> Optional.of(EXTRA);
			default -> Optional.empty();
		};
	}

}
