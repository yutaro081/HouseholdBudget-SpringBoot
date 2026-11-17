package io.github.yutaro081.householdbudget.exception;

/**
 * 送信の形がおかしいときの例外（issue 032）。例：取引の編集（明細は 1 行）に、2 行以上が送られた。
 * GlobalExceptionHandler が 400 の画面（error/4xx。状態コードも 400）に変える。何も保存しない。
 * 入力の間違い（入力エラーとして画面に出すもの）には使わない。メッセージに、ID などの情報を入れない（7.8）。
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException() {
		super("bad request");
	}

}
