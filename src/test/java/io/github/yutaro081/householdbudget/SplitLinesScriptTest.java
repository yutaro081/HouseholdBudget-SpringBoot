package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 取引の登録・編集画面の、明細の行の隠す・見せると、明細の合計の JavaScript（static/js/split-lines.js。issue 032）が、
 * 決まりを守っていることの確認（設計書 1.1、2.3、7.5。要件定義書 7 章「画面を JavaScript で組み立てない」）。
 * ブラウザで動かすものではなく、ファイルの文字を検索するだけ。Spring もデータベースも使わない。動きは、本人がブラウザで確かめる。
 */
class SplitLinesScriptTest {

	private static final Path SCRIPT = Path.of("src", "main", "resources", "static", "js", "split-lines.js");

	/** コメント行（// で始まる行）を除いた、コードの部分。説明の文章に、禁止の言葉が出てきても、引っかからないようにする。 */
	private String code() throws IOException {
		return String.join("\n", Files.readAllLines(SCRIPT).stream().filter(line -> !line.strip().startsWith("//")).toList());
	}

	@Test
	void scriptExistsAndIsShort() throws IOException {
		List<String> lines = Files.readAllLines(SCRIPT);
		assertThat(lines).as("短い自作の JavaScript（200 行以内。budget-total.js と同じ）").hasSizeBetween(20, 200);
	}

	@Test
	void scriptDoesNotBuildHtmlOrRunStrings() throws IOException {
		assertThat(code()).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(",
				"new Function", "setTimeout(\"", "setInterval(\"");
	}

	@Test
	void scriptDoesNotTouchOtherSitesOrSendData() throws IOException {
		assertThat(code()).doesNotContain("http://", "https://", "XMLHttpRequest", "fetch(", "sendBeacon", "WebSocket",
				"localStorage", "sessionStorage", "document.cookie", "FormData", ".submit(");
	}

	/** 行を作らない（5 行は HTML にある）。要素を作る・写す・足す・消す書き方を使わない。書き込むのは hidden と、合計の textContent だけ */
	@Test
	void scriptDoesNotCreateOrCopyRows() throws IOException {
		String code = code();
		assertThat(code).doesNotContain("createElement", "createTextNode", "cloneNode", "importNode", "appendChild", "insertBefore",
				".append(", ".prepend(", ".remove(", "removeChild", "replaceChild", "innerText", "<template");
		assertThat(code).contains(".hidden = ", ".textContent = ");
	}

	/** 画面に出す文は、HTML に書いてある。ここには書かない */
	@Test
	void scriptHasNoSentencesOfItsOwn() throws IOException {
		assertThat(code()).doesNotContain("行を足す", "行を消す", "明細の合計", "数字でない欄があります", "円");
	}

	/** 行を消すときは、確認を出さない。金額の欄に input のできごとを起こして、金額ボタンの状態（税込の押せない状態）と合計を、空の欄に合わせる */
	@Test
	void removingALineAsksNothingAndTellsTheAmountButtonsTheFieldIsEmpty() throws IOException {
		String code = code();
		assertThat(code).doesNotContain("confirm(");
		assertThat(code).contains("dispatchEvent(new Event('input'))");
	}

	/** data- の印だけに頼る（クラス名・見た目に頼らない） */
	@Test
	void scriptUsesOnlyDataMarks() throws IOException {
		String code = code();
		assertThat(code).contains("[data-split-line]", "[data-split-add]", "[data-split-total]", "[data-split-total-box]", "[data-split-remove]");
		assertThat(code).doesNotContain("classList", "className", "getElementsByClassName", "querySelector('.", "querySelectorAll('.");
	}

	/** 金額の欄の読み方は、amount-buttons.js・budget-total.js と同じ（全角の数字を半角に直す。桁が多すぎる値は数字でない扱い） */
	@Test
	void scriptReadsAmountsLikeTheOtherScripts() throws IOException {
		String code = code();
		assertThat(code).contains("[０-９]", "0xFF10", "MAX_DIGITS = 9", "/^[0-9]+$/");
	}

}
