package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 予算設定画面の合計と「消える前の確認」の JavaScript（static/js/budget-total.js。issue 019）が、決まりを守っていることの確認
 * （設計書 1.1、2.3、7.5）。ブラウザで動かすものではなく、ファイルの文字を検索するだけ。Spring もデータベースも使わない。
 * 計算の動きは、手順書 8 章の Console の表で、本人が確かめる。
 */
class BudgetTotalScriptTest {

	private static final Path SCRIPT = Path.of("src", "main", "resources", "static", "js", "budget-total.js");

	/** コメント行（// で始まる行）を除いた、コードの部分。説明の文章に、禁止の言葉が出てきても、引っかからないようにする。 */
	private String code() throws IOException {
		return String.join("\n", Files.readAllLines(SCRIPT).stream().filter(line -> !line.strip().startsWith("//")).toList());
	}

	@Test
	void scriptExistsAndIsShort() throws IOException {
		List<String> lines = Files.readAllLines(SCRIPT);
		assertThat(lines).as("短い自作の JavaScript（200 行以内）").hasSizeBetween(20, 200);
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

	/** 書き込みは textContent と hidden だけ。要素を作らない。文は HTML にある固定の文 */
	@Test
	void scriptWritesOnlyTextContentAndHidden() throws IOException {
		String code = code();
		assertThat(code).doesNotContain("createElement", "createTextNode", "innerText", "appendChild", "insertBefore");
		assertThat(code).contains(".textContent = ").contains(".hidden = ");
	}

	@Test
	void scriptHasNoSentencesOfItsOwn() throws IOException {
		assertThat(code()).doesNotContain("数字でない欄があります", "空の欄があります", "手で直した額は消えます");
	}

	/** 確認の文は、リンクの印（data-budget-confirm）から読む。押したとき、キャンセルなら移らない */
	@Test
	void scriptAsksWithTheSentenceFromTheMarkAndStopsTheLinkOnCancel() throws IOException {
		String code = code();
		assertThat(code).contains("data-budget-confirm").contains("confirm(").contains("preventDefault()");
	}

	/** 合計の計算の関数は、Console で確かめるために window.BudgetTotal に出す */
	@Test
	void scriptExposesTheCalculationForTheConsole() throws IOException {
		assertThat(code()).contains("window.BudgetTotal = ");
	}

	/** 欄を書き換えるたびに計算し直す */
	@Test
	void scriptRecalculatesOnInput() throws IOException {
		assertThat(code()).contains("addEventListener('input'");
	}

	/** 全角の数字を半角に直して足す（amount-buttons.js と同じ読み方） */
	@Test
	void scriptConvertsFullWidthDigits() throws IOException {
		assertThat(code()).contains("[０-９]").contains("0xFF10");
	}

}
