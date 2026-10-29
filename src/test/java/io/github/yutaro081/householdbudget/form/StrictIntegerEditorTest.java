package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;

/**
 * StrictIntegerEditor（金額の厳しい変換）の単体テスト（設計書 6.5、Q5-1 の C、R13）。Spring の起動もデータベースも使わない。
 * 入力は、Q5-1 の実験（Spring 標準の変換で試した入力）をすべて含む。
 * R13（Phase 6）：全角の数字（０〜９）は、半角に直して受け付ける。それ以外（全角のマイナス・プラス・カンマ・小数点・空白など）は、エラーのまま。
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
			// ---- 全角の数字は、半角に直して読む（R13）
			"１２３|123",
			"０|0",
			"００１２|12",
			"０１０|10",
			"９９９|999",
			"１０００００００|10000000",
			"２１４７４８３６４７|2147483647",
			// 半角と全角が混ざっていてもよい
			"１2３|123",
			"1２3|123",
			"１２3４5|12345",
			// 前後の空白（半角・全角）は取り除かれる
			"'　１２３　'|123",
			"' １２３ '|123",
			// 半角のマイナス＋全角の数字は、半角のマイナスなので読める
			"-１２|-12",
			"-０|0",
			"-２１４７４８３６４８|-2147483648",
			"-５|-5",
			// ---- 型変換のエラー（Q5-1 の実験で、標準の変換が通したもの。全角の数字だけは R13 で通すことに変えた）
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
			"2147483648|ERROR",
			"-2147483649|ERROR",
			// ---- 全角でも、数字以外はエラーのまま（R13：直すのは、全角の０〜９だけ）
			"２１４７４８３６４８|ERROR",
			"９９９９９９９９９９９|ERROR",
			"－５|ERROR",                  // 全角のマイナス（U+FF0D）
			"−５|ERROR",                   // マイナス記号（U+2212）
			"ー５|ERROR",                  // 長音（U+30FC）
			"‐５|ERROR",                   // ハイフン（U+2010）
			"＋５|ERROR",                  // 全角のプラス
			"+５|ERROR",                   // 半角のプラス＋全角の数字
			"＋5|ERROR",
			"１　２|ERROR",                // 途中に全角の空白
			"１ ２|ERROR",                 // 途中に半角の空白
			"1　000|ERROR",
			"１，０００|ERROR",             // 全角のカンマ
			"1，000|ERROR",
			"'１,０００'|ERROR",            // 半角のカンマ
			"１．５|ERROR",                // 全角のピリオド
			"１.５|ERROR",
			"１２円|ERROR",
			"０ｘ１０|ERROR",              // 全角の x
			"0x１０|ERROR",
			"０x10|ERROR",
			"＃１０|ERROR",
			"#１０|ERROR",
			"１e3|ERROR",
			"１Ｅ３|ERROR",
			// ---- ほかの国の数字・漢数字・丸数字・上付きの数字は、数字として読まない
			"١٢٣|ERROR",                 // アラビア・インドの数字（U+0661〜）
			"१२३|ERROR",                 // デーヴァナーガリーの数字
			"一二三|ERROR",
			"①②③|ERROR",
			"１２³|ERROR"                 // 上付きの 3（U+00B3）
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

	// ---- 全角の数字を半角に直す部分（R13）：直すのは、０〜９ の10文字だけ ----

	@org.junit.jupiter.api.Test
	void toHalfWidthDigitsConvertsOnlyTheTenFullWidthDigits() {
		assertThat(StrictIntegerEditor.toHalfWidthDigits("０１２３４５６７８９")).isEqualTo("0123456789");
		assertThat(StrictIntegerEditor.toHalfWidthDigits("0123456789")).isEqualTo("0123456789");
		assertThat(StrictIntegerEditor.toHalfWidthDigits("a１b２c")).isEqualTo("a1b2c");
		assertThat(StrictIntegerEditor.toHalfWidthDigits("")).isEmpty();
	}

	/** 範囲の両端の外（０ の1つ前の ／ U+FF0F と、９ の1つ後の ： U+FF1A）は、直さない */
	@org.junit.jupiter.api.Test
	void toHalfWidthDigitsDoesNotTouchTheCharactersNextToTheRange() {
		assertThat(StrictIntegerEditor.toHalfWidthDigits("／")).isEqualTo("／");
		assertThat(StrictIntegerEditor.toHalfWidthDigits("：")).isEqualTo("：");
		assertThat(StrictIntegerEditor.toHalfWidthDigits("０")).isEqualTo("0");
		assertThat(StrictIntegerEditor.toHalfWidthDigits("９")).isEqualTo("9");
		// 全角のマイナス・プラス・空白も直さない
		assertThat(StrictIntegerEditor.toHalfWidthDigits("－＋　，．")).isEqualTo("－＋　，．");
	}

	/** 全角で入力された値は、読んだあと、半角の数字で入力欄に出る（getAsText） */
	@org.junit.jupiter.api.Test
	void fullWidthInputIsShownAsHalfWidthAfterReading() {
		StrictIntegerEditor editor = new StrictIntegerEditor();
		editor.setAsText("１２３");

		assertThat(editor.getValue()).isEqualTo(123);
		assertThat(editor.getAsText()).isEqualTo("123");
	}

}
