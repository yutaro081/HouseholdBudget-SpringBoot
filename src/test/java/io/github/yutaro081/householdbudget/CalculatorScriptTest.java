package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 取引の登録・編集画面の、金額の欄の式を計算する JavaScript（static/js/calculator.js。issue 035 の段階 2）が、
 * 決まりを守っていることの確認（設計書 2.3、7.5。要件定義書 4.2・7 章）。
 * ブラウザで動かすものではなく、ファイルの文字を検索するだけ。Spring もデータベースも使わない。
 * 計算の答えは、本人がブラウザの Console で式の一覧を流して確かめる（issue 035 の手順書 5 章）。
 */
class CalculatorScriptTest {

	private static final Path SCRIPT = Path.of("src", "main", "resources", "static", "js", "calculator.js");

	/** コメント行（// で始まる行）を除いた、コードの部分。説明の文章に、禁止の言葉が出てきても、引っかからないようにする。 */
	private String code() throws IOException {
		return String.join("\n", Files.readAllLines(SCRIPT).stream().filter(line -> !line.strip().startsWith("//")).toList());
	}

	@Test
	void scriptExistsAndIsShort() throws IOException {
		List<String> lines = Files.readAllLines(SCRIPT);
		assertThat(lines).as("短い自作の JavaScript（200 行以内。split-lines.js と同じ）").hasSizeBetween(20, 200);
	}

	/** 文字列をプログラムとして実行しない（式も、数字と記号に分けて足し引きする）。HTML として画面に入れない */
	@Test
	void scriptDoesNotBuildHtmlOrRunStrings() throws IOException {
		assertThat(code()).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(",
				"new Function", "Function(", "setTimeout(\"", "setInterval(\"");
	}

	@Test
	void scriptDoesNotTouchOtherSitesOrSendData() throws IOException {
		assertThat(code()).doesNotContain("http://", "https://", "XMLHttpRequest", "fetch(", "sendBeacon", "WebSocket",
				"localStorage", "sessionStorage", "document.cookie", "FormData", ".submit(");
	}

	/** 画面に出す文は、HTML に書いてある固定の文。JavaScript は、見せる・隠すだけ。要素を作らない */
	@Test
	void scriptDoesNotCreateTextOrElements() throws IOException {
		String code = code();
		assertThat(code).doesNotContain("createElement", "createTextNode", "textContent", "innerText", "cloneNode", "appendChild");
		assertThat(code).as("文をここに書かない").doesNotContain("計算できない", "使えるのは");
		assertThat(code).contains(".hidden = ");
	}

	/** data- の印だけに頼る（クラス名・見た目に頼らない） */
	@Test
	void scriptUsesOnlyDataMarks() throws IOException {
		String code = code();
		assertThat(code).contains("[data-calc-error]", "data-calc-target");
		assertThat(code).doesNotContain("classList", "className", "getElementsByClassName", "querySelector('.", "querySelectorAll('.");
	}

	/** 式の形は「数字 記号 数字 …」だけ。記号は + と - だけ（× ÷ は使わない） */
	@Test
	void formulaIsOnlyNumbersWithPlusAndMinus() throws IOException {
		String code = code();
		assertThat(code).contains("/^[0-9]+([+-][0-9]+)*$/");
		assertThat(code).as("全角の数字は半角に直す（ほかの JavaScript・サーバーと同じ）").contains("[０-９]", "0xFF10");
		assertThat(code).as("1 つの数字は 9 桁まで（ほかの JavaScript と同じ）").contains("MAX_DIGITS = 9");
		assertThat(code).as("整数だけの値は式と見なさない").contains("/^-?[0-9]+$/");
	}

	/** Enter：式があるときは送らない。日本語入力の確定の Enter では何もしない */
	@Test
	void enterComputesWithoutSubmittingAndIgnoresImeComposition() throws IOException {
		String code = code();
		assertThat(code).contains("'keydown'", "'Enter'", "isComposing", "preventDefault()");
	}

	/** 欄から離れたとき、フォームを送るときにも計算する */
	@Test
	void computesWhenLeavingTheFieldAndWhenSubmitting() throws IOException {
		String code = code();
		assertThat(code).contains("'blur'", "'submit'");
	}

	/** 結果を書いたら input のできごとを起こす（明細の合計と、税込のボタンの状態が、新しい値に合わせて動く。読む順に頼らない） */
	@Test
	void writingTheResultTellsTheOtherScripts() throws IOException {
		assertThat(code()).contains("dispatchEvent(new Event('input'))");
	}

	/** Console で確かめるために、計算の関数だけを出す（画面の部品には触れない）。段階 3 で、税込（withTax）も出す。calculator-keys.js も、これを使う */
	@Test
	void onlyTheCalculationIsExposed() throws IOException {
		assertThat(code()).contains("window.Calculator = { evaluate: evaluate, withTax: withTax }");
	}

	/** 税込：式を先に計算し、整数だけで、110 か 108 を掛けて 100 で割り、端数を切り捨てる（今までの金額ボタンと同じ計算。issue 035 の段階 3） */
	@Test
	void withTaxCalculatesTheFormulaFirstAndRoundsDownWithIntegers() throws IOException {
		String code = code();
		assertThat(code).contains("var amount = evaluate(text)", "Math.floor(amount * percent / 100)");
		assertThat(code).as("空・計算できない・0 以下は税込にしない").contains("amount === null || amount <= 0");
	}

}
