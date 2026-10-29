package io.github.yutaro081.householdbudget.domain;

/**
 * 予実比較で行に付ける色の種類（設計書 4.5、5.9、F4）。
 */
public enum BudgetColor {

	/** 赤：実績が予算を超えている */
	RED,

	/** 青：実績の3倍が予算以下（予算の1/3以下） */
	BLUE,

	/** 色なし */
	NONE

}
