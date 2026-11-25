package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 取引の登録・編集画面の、共有の電卓のボタンの JavaScript（static/js/calculator-keys.js。issue 035 の段階 3）が、
 * 決まりを守っていることの確認（設計書 2.3、7.5。要件定義書 4.2・7 章）。issue 009 の amount-buttons.js を置きかえた。
 * ブラウザで動かすものではなく、ファイルの文字を検索するだけ。Spring もデータベースも使わない。動きは、本人がブラウザで確かめる。
 */
class CalculatorKeysScriptTest {

	private static final Path SCRIPT = Path.of("src", "main", "resources", "static", "js", "calculator-keys.js");

	/** コメント行（// で始まる行）を除いた、コードの部分。説明の文章に、禁止の言葉が出てきても、引っかからないようにする。 */
	private String code() throws IOException {
		return String.join("\n", Files.readAllLines(SCRIPT).stream().filter(line -> !line.strip().startsWith("//")).toList());
	}

	@Test
	void scriptExistsAndIsShort() throws IOException {
		List<String> lines = Files.readAllLines(SCRIPT);
		assertThat(lines).as("短い自作の JavaScript（200 行以内。split-lines.js と同じ）").hasSizeBetween(20, 200);
	}

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

	/** ボタンも文も、HTML にはじめからある。ここでは作らない・写さない。見せる・隠すと、押せる・押せないだけ */
	@Test
	void scriptDoesNotCreateTextOrElements() throws IOException {
		String code = code();
		assertThat(code).doesNotContain("createElement", "createTextNode", "textContent", "innerText", "cloneNode", "appendChild",
				"insertBefore", ".remove(", "<template");
		assertThat(code).as("文をここに書かない").doesNotContain("税をかけられません", "計算できない");
		assertThat(code).contains("calc.hidden = false", "button.disabled = locked");
	}

	/** data- の印だけに頼る（クラス名・見た目に頼らない） */
	@Test
	void scriptUsesOnlyDataMarks() throws IOException {
		String code = code();
		assertThat(code).contains("[data-calc]", "[data-calc-key]", "[data-calc-back]", "[data-calc-clear]", "[data-calc-tax]",
				"[data-calc-tax-note]", "[data-split-amount]", "[data-calc-error]");
		assertThat(code).doesNotContain("classList", "className", "getElementsByClassName", "querySelector('.", "querySelectorAll('.");
	}

	/** 計算は calculator.js（window.Calculator）を使い、ここで式を読まない（2 つのファイルで決まりがずれないように） */
	@Test
	void calculationIsLeftToCalculatorJs() throws IOException {
		String code = code();
		assertThat(code).contains("window.Calculator", "Calculator.evaluate(", "Calculator.withTax(");
		assertThat(code).as("式の形の正規表現は、ここに書かない").doesNotContain("[+-][0-9]+");
	}

	/** 電卓のボタンは、押してもカーソルを欄から動かさない（押す瞬間の決まった動きを止める）。押したあと、入れる先の欄にカーソルを置く */
	@Test
	void buttonsDoNotTakeTheCursorAwayFromTheField() throws IOException {
		String code = code();
		assertThat(code).contains("'mousedown'", "event.preventDefault()", "current.focus()");
	}

	/** 入れる先：最後にカーソルを置いた金額の欄（focus）。その行が隠れたら（行を消す）、明細 1 に戻す */
	@Test
	void theTargetIsTheLastFocusedFieldAndFallsBackToTheFirstLine() throws IOException {
		String code = code();
		assertThat(code).contains("'focus'", "closest('[hidden]')", "fields[0]");
	}

	/** 税込を押せない状態は、欄ごと。最初は、欄に値があれば押せない。手で書き換えたら押せる。電卓が自分で書いたときは押せるようにしない */
	@Test
	void taxLockIsKeptPerFieldAndOnlyHandEditsOrEmptyingUnlockIt() throws IOException {
		String code = code();
		assertThat(code).as("最初の状態は、欄に値が入っているか（空白だけは空とみなす）で決める").contains("field.value.trim() !== ''");
		assertThat(code).as("サーバーからの印（data-tax-locked）は使わない").doesNotContain("data-tax-locked");
		assertThat(code).as("電卓が書いたときも input を起こして、合計などに知らせる").contains("dispatchEvent(new Event('input'))");
		assertThat(code).as("自分で起こした input は、手で書き換えたことにしない").contains("if (writing)");
	}

}
