package io.github.yutaro081.householdbudget.exception;

/**
 * 科目・取引先・支払方法が、1人あたりの上限（256個、Limits）に達していて、追加できないときの例外（設計書 4.5、5.1、5.4、R14）。
 * Controller が、入力エラーとして同じ画面を表示し直す（名前の項目の横に「科目は256個までです」などを出す。入力値は残す）。
 * 実行時例外なので、トランザクションの中で投げると、その処理の変更はすべて取り消される。
 */
public class LimitExceededException extends RuntimeException {

	public LimitExceededException(String message) {
		super(message);
	}

}
