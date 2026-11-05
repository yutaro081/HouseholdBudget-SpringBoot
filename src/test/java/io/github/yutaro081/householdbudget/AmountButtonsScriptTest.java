package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 金額の入力を助けるボタンの JavaScript（static/js/amount-buttons.js。issue 009）が、決まりを守っていることの確認
 * （設計書 1.1、7.5）。ブラウザで動かして確かめるものではなく、ファイルの文字を検索するだけ。Spring もデータベースも使わない。
 * <ul>
 * <li>利用者の入力を、HTML として画面に入れる書き方（innerHTML など）、文字列を実行する書き方（eval など）を使っていない</li>
 * <li>外部の URL に触れていない（CDN、通信）</li>
 * <li>画面に出す文を、このファイルで作っていない（文は HTML に書いてある。ここでは、見せる・隠すだけ）</li>
 * </ul>
 */
class AmountButtonsScriptTest {

	private static final Path SCRIPT = Path.of("src", "main", "resources", "static", "js", "amount-buttons.js");

	/** コメント行（// で始まる行）を除いた、コードの部分。説明の文章に、禁止の言葉が出てきても、引っかからないようにする。 */
	private String code() throws IOException {
		return String.join("\n", Files.readAllLines(SCRIPT).stream().filter(line -> !line.strip().startsWith("//")).toList());
	}

	@Test
	void scriptExistsAndIsShort() throws IOException {
		List<String> lines = Files.readAllLines(SCRIPT);
		assertThat(lines).as("短い自作の JavaScript（100 行台まで）").hasSizeBetween(20, 150);
	}

	@Test
	void scriptDoesNotBuildHtmlOrRunStrings() throws IOException {
		assertThat(code()).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(",
				"new Function", "setTimeout(\"", "setInterval(\"");
	}

	@Test
	void scriptDoesNotTouchOtherSitesOrSendData() throws IOException {
		assertThat(code()).doesNotContain("http://", "https://", "XMLHttpRequest", "fetch(", "sendBeacon", "WebSocket",
				"localStorage", "sessionStorage", "document.cookie");
	}

	@Test
	void scriptDoesNotCreateTheMessageText() throws IOException {
		// 固定の文は HTML に書いてある。JavaScript は、文を作らない（createElement、createTextNode、textContent への代入など）
		assertThat(code()).doesNotContain("createElement", "createTextNode", "textContent", "innerText");
		assertThat(code()).as("画面に出す文を、ここに書いていない").doesNotContain("税をかけられません");
	}

	@Test
	void scriptChangesOnlyTheFieldValueAndTheButtonState() throws IOException {
		String code = code();
		assertThat(code).contains("field.value = result");
		assertThat(code).as("クリアは、欄を空にする（0 ではなく空）").contains("field.value = ''");
		assertThat(code).as("税込のボタンの押せる・押せないは disabled で切り替える").contains("button.disabled = locked");
		assertThat(code).as("JavaScript が動くときだけ、ボタンを見せる").contains("box.hidden = false");
	}

	@Test
	void onlyTheClearButtonMovesTheCursorBackToTheField() throws IOException {
		String code = code();
		assertThat(code.split(java.util.regex.Pattern.quote("field.focus()"), -1)).as("focus() を呼ぶのは 1 か所だけ（クリアのボタン。Q009-16）").hasSize(2);
		String clearHandler = code.substring(code.indexOf("clearButtons.forEach"), code.indexOf("field.addEventListener('input'"));
		assertThat(clearHandler).as("クリアの処理の中で、欄を空にし、税込のボタンを押せるようにし、カーソルを戻す")
				.contains("field.value = ''", "setTaxLocked(false)", "field.focus()");
	}

	@Test
	void taxButtonsStartLockedWhenTheFieldHasAValueAndUnlockedWhenItIsEmpty() throws IOException {
		String code = code();
		assertThat(code).as("最初の状態は、欄に値が入っているか（空白だけは空とみなす）で決める（Q009-18）").contains("setTaxLocked(field.value.trim() !== '')");
		assertThat(code).as("サーバーからの印（data-tax-locked）は使わない").doesNotContain("data-tax-locked");
	}

}
