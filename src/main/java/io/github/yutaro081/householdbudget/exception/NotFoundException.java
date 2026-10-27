package io.github.yutaro081.householdbudget.exception;

/**
 * 存在しない、または他人のデータを指定されたときの例外（設計書 4.5、B7）。
 * GlobalExceptionHandler が 404 の画面（状態コードも 404）に変える。
 * 「存在しない」と「他人のもの」は区別しない（他人のデータの有無を教えない、7.8）。
 * そのため、メッセージにも、IDやユーザーIDなどの情報を入れない。
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException() {
		super("not found");
	}

}
