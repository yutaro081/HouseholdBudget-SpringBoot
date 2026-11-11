package io.github.yutaro081.householdbudget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 自動予算で選ぶ割合の単体テスト（設計書 5.14、issue 019）。auto の検査の元になる（守りに関わる所）。
 * 「1」「2」「3」と完全に同じ文字だけを受け付ける。ほかはすべて空（Controller が 404 にする）。
 */
class ProposalChoiceTest {

	@Test
	void theThreeChoicesHaveTheirPercents() {
		assertThat(ProposalChoice.OK.percent()).isEqualTo(100);
		assertThat(ProposalChoice.SAVE.percent()).isEqualTo(90);
		assertThat(ProposalChoice.EXTRA.percent()).isEqualTo(110);
	}

	@Test
	void oneIsOkTwoIsSaveThreeIsExtra() {
		assertThat(ProposalChoice.fromParam("1")).contains(ProposalChoice.OK);
		assertThat(ProposalChoice.fromParam("2")).contains(ProposalChoice.SAVE);
		assertThat(ProposalChoice.fromParam("3")).contains(ProposalChoice.EXTRA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "0", "4", "-1", "01", "001", "1.0", "abc", "1a", "a1", " 1", "1 ", "１", "２", "３", "OK", "ok", "1,2", "10", "+1" })
	void anythingExceptTheExactStringsOneToThreeIsEmpty(String value) {
		assertThat(ProposalChoice.fromParam(value)).isEmpty();
	}

	@ParameterizedTest
	@NullSource
	void nullIsEmpty(String value) {
		Optional<ProposalChoice> result = ProposalChoice.fromParam(value);

		assertThat(result).isEmpty();
	}

}
