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

	/**
	 * ボタンも文も、HTML にはじめからある。ここでは作らない・写さない。見せる・隠すと、押せる・押せないと、
	 * 決まった場所（選んだ行の金額の欄の入れ物）へ電卓を移すことだけ（段階 3-2。要件定義書 7 章）。移すのは 1 か所だけ
	 */
	@Test
	void scriptDoesNotCreateTextOrElementsAndOnlyMovesTheCalculator() throws IOException {
		String code = code();
		assertThat(code).doesNotContain("createElement", "createTextNode", "textContent", "innerText", "cloneNode", "importNode",
				"insertBefore", ".remove(", "removeChild", "replaceChild", ".append(", ".prepend(", "<template");
		assertThat(code.split(java.util.regex.Pattern.quote("appendChild("), -1)).as("要素を移すのは 1 か所だけ").hasSize(2);
		assertThat(code).contains("place.appendChild(calc)");
		assertThat(code).as("文をここに書かない").doesNotContain("税をかけられません", "計算できない");
		assertThat(code).contains("calc.hidden = false", "calc.hidden = true", "button.disabled = locked");
	}

	/** 位置と見た目は CSS だけで決める。JavaScript で style を書かない（インライン CSS の例外は 1 つだけ。段階 3-2） */
	@Test
	void scriptDoesNotWriteInlineStyles() throws IOException {
		assertThat(code()).doesNotContain(".style", "'style'", "cssText", "getBoundingClientRect", "offsetTop");
	}

	/** 消すとき：カーソルが欄と電卓の外へ出たとき（focusout の移る先で見分ける）と、Esc キー。Esc ではカーソルを欄に残す */
	@Test
	void theCalculatorHidesWhenTheCursorLeavesBothOrOnEscape() throws IOException {
		String code = code();
		assertThat(code).contains("'focusout'", "relatedTarget", "calc.contains(", "'Escape'");
	}

	/**
	 * Esc キー：先にカーソルを欄に戻してから隠す。逆の順だと、電卓の C・税込にカーソルがあるとき、欄に戻ったときの focus のできごとで
	 * 電卓がまた出てしまう（段階 3-2 の直し）
	 */
	@Test
	void escapeMovesTheCursorBackToTheFieldBeforeHiding() throws IOException {
		String code = code();
		int start = code.indexOf("function hideOnEscape");
		String handler = code.substring(start, code.indexOf("\n    }", start));
		assertThat(handler).contains("current.focus()", "hide()");
		assertThat(handler.indexOf("current.focus()")).as("欄に戻すのが先、隠すのが後").isLessThan(handler.indexOf("hide()"));
	}

	/**
	 * 出し直し：電卓が隠れているときに金額の欄を押すと（click）、また出す。Esc のあとは、カーソルがもう欄にあるので focus のできごとが起きないため
	 * （段階 3-2 の直し（2））
	 */
	@Test
	void clickingTheFieldShowsTheHiddenCalculatorAgain() throws IOException {
		String code = code();
		int start = code.indexOf("field.addEventListener('click'");
		assertThat(start).as("金額の欄を押したときの処理がある").isPositive();
		String handler = code.substring(start, code.indexOf("});", start));
		assertThat(handler).contains("if (calc.hidden)", "show(field)");
	}

	/** 画面からはみ出すときは、いつも下に出したまま、ページを動かして全部見せる（上に出す決まりは作らない） */
	@Test
	void theCalculatorIsScrolledIntoViewWhenShown() throws IOException {
		assertThat(code()).contains("calc.scrollIntoView({ block: 'nearest' })");
	}

	/** data- の印だけに頼る（クラス名・見た目に頼らない） */
	@Test
	void scriptUsesOnlyDataMarks() throws IOException {
		String code = code();
		assertThat(code).contains("[data-calc]", "[data-calc-key]", "[data-calc-back]", "[data-calc-clear]", "[data-calc-tax]",
				"[data-calc-tax-note]", "[data-split-amount]", "[data-calc-error]", "[data-calc-place]");
		assertThat(code).doesNotContain("classList", "className", "getElementsByClassName", "querySelector('.", "querySelectorAll('.");
	}

	/** 計算は calculator.js（window.Calculator）を使い、ここで式を読まない（2 つのファイルで決まりがずれないように） */
	@Test
	void calculationIsLeftToCalculatorJs() throws IOException {
		String code = code();
		assertThat(code).contains("window.Calculator", "Calculator.evaluate(", "Calculator.withTax(");
		assertThat(code).as("式の形の正規表現は、ここに書かない").doesNotContain("[+-][0-9]+");
	}

	/**
	 * 電卓の全体（ボタン、すき間、説明の文）は、押してもカーソルを欄から動かさない（電卓の入れ物で、押す瞬間の決まった動きを止める。段階 3-2）。
	 * 押しそこねても、「電卓の外を押した」扱いにならない。ボタンを押したあと、入れる先の欄にカーソルを置く
	 */
	@Test
	void theWholeCalculatorDoesNotTakeTheCursorAwayFromTheField() throws IOException {
		String code = code();
		assertThat(code).contains("calc.addEventListener('mousedown'", "event.preventDefault()", "current.focus()");
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
