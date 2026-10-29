package io.github.yutaro081.householdbudget.domain;

/**
 * 上限の数（設計書 5.4、5.7、P11、要件定義書 5章 R14）。画面にもデータベースにも依存しない定数だけを持つ。
 */
public final class Limits {

	/**
	 * 科目・取引先・支払方法の、1人あたりの個数の上限（それぞれ256個まで）。
	 * 予算設定画面は、行を Spring の標準で256行までしか受け取れない（BudgetForm.rows）。
	 * 科目が257個以上あると、普通に使っていても予算を保存できなくなるため、その行数と同じ256個までにした。
	 * 予算の画面で受け取れる行の数は、標準の256のまま変えない（BudgetControllerTest で、256行は保存でき、257行は400になることを確かめている）。
	 */
	public static final int MAX_MASTERS_PER_USER = 256;

	private Limits() {
	}

}
