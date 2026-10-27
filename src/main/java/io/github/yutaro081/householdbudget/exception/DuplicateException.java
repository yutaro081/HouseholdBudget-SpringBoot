package io.github.yutaro081.householdbudget.exception;

/**
 * ユーザー名（または、後の Phase で科目・取引先・支払方法の名前）が重複したときの例外（設計書 4.5、5.1）。
 * Controller が、入力エラーとして同じ画面を表示し直す（入力値は残す）。
 * 実行時例外なので、トランザクションの中で投げると、その処理の変更はすべて取り消される。
 */
public class DuplicateException extends RuntimeException {

	public DuplicateException(String message) {
		super(message);
	}

	public DuplicateException(String message, Throwable cause) {
		super(message, cause);
	}

}
