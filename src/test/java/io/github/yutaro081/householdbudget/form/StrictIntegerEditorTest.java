package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;

/**
 * StrictIntegerEditor（金額の厳しい変換）の単体テスト（設計書 6.5、Q5-1 の C）。Spring の起動もデータベースも使わない。
 * 入力は、Q5-1 の実験（Spring 標準の変換で試した入力）をすべて含む。
 * 結果の書き方：NULL＝値なし（必須エラーになる）、ERROR＝型変換のエラー（typeMismatch）、数字＝その整数。
 */
class StrictIntegerEditorTest {

	/** TransactionForm の amount に、editor を付けて束縛した結果を返す（本番の @InitBinder と同じ付け方）。 */
	private static String bind(String input) {
		TransactionForm form = new TransactionForm();
		WebDataBinder binder = new WebDataBinder(form, "transactionForm");
		binder.registerCustomEditor(Integer.class, "amount", new StrictIntegerEditor());
		binder.bind(new MutablePropertyValues(java.util.Map.of("amount", input)));
		FieldError error = binder.getBindingResult().getFieldError("amount");
		if (error != null) {
			assertThat(error.isBindingFailure()).isTrue();
			assertThat(error.getCode()).isEqualTo("typeMismatch");
			assertThat(error.getRejectedValue()).isEqualTo(input);
			return "ERROR";
		}
		return form.getAmount() == null ? "NULL" : String.valueOf(form.getAmount());
	}

	@ParameterizedTest(name = "[{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			// 入力 | 結果
			// ---- 空・空白だけ（必須エラーになる）
			"''|NULL",
			"' '|NULL",
			"'　'|NULL",
			"'  　 '|NULL",
			// ---- 整数として読むもの
			"123|123",
			"' 123 '|123",
			"'　123　'|123",
			"-5|-5",
			"-0|0",
			"0|0",
			"1|1",
			"1000000|1000000",
			"1000001|1000001",
			"010|10",
			"2147483647|2147483647",
			"-2147483648|-2147483648",
			// ---- 型変換のエラー（Q5-1 の実験で、標準の変換が通したもの）
			"１２３|ERROR",
			"1 000|ERROR",
			"0x10|ERROR",
			"#10|ERROR",
			"+5|ERROR",
			// ---- 型変換のエラー（標準の変換でもエラーだったもの）
			"'1,000'|ERROR",
			"1.5|ERROR",
			"10円|ERROR",
			"99999999999|ERROR",
			"1e3|ERROR",
			// ---- そのほか
			"abc|ERROR",
			"-|ERROR",
			"--5|ERROR",
			"5-|ERROR",
			"- 5|ERROR",
			"-５|ERROR",
			"2147483648|ERROR",
			"-2147483649|ERROR"
	})
	void bindsAsDecided(String input, String expected) {
		assertThat(bind(input == null ? "" : input)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "getAsText {0}")
	@CsvSource(delimiter = '|', value = { "1000|1000", "-5|-5" })
	void getAsTextReturnsTheNumber(Integer value, String expected) {
		StrictIntegerEditor editor = new StrictIntegerEditor();
		editor.setValue(value);
		assertThat(editor.getAsText()).isEqualTo(expected);
	}

	@org.junit.jupiter.api.Test
	void getAsTextIsEmptyForNull() {
		StrictIntegerEditor editor = new StrictIntegerEditor();
		editor.setValue(null);
		assertThat(editor.getAsText()).isEmpty();
	}

}
