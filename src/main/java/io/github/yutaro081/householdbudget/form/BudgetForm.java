package io.github.yutaro081.householdbudget.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 予算設定の入力を受け取るクラス（設計書 4.7、6.2、6.5）。科目ごとの1行（科目IDと予算額）の並び。
 * <p>
 * 年と季節は、Form に持たせず、URL で受け取る（設計書 2.4、5.7）。user_id の項目も持たない（6.1）。
 * 予算額は、Controller の @InitBinder で StrictIntegerEditor を付けて読む（Q5-1 の C、Q6-1）。
 * エラーメッセージのキーは、注釈の名前.budgetForm.rows.amount（行の番号なしの形。messages.properties、Q6-7）。
 * <p>
 * 科目IDが自分の科目か、同じIDが2回ないかは、Form ではなく Service が確かめる（6.3、3.9、F3）。
 */
public class BudgetForm {

	/** 科目ごとの行。画面の並び（科目の登録順）で送られる。行の中の注釈が働くように @Valid を付ける。 */
	@Valid
	private List<Row> rows = new ArrayList<>();

	public BudgetForm() {
	}

	public BudgetForm(List<Row> rows) {
		this.rows = new ArrayList<>(rows);
	}

	public List<Row> getRows() {
		return rows;
	}

	public void setRows(List<Row> rows) {
		this.rows = rows;
	}

	/** 1科目分の行：科目ID（画面の隠し項目）と、予算額（0円以上3,000,000円以下。要件5章）。 */
	public static class Row {

		/** 科目のID。数字でない・空のときの扱いは、Controller が決める（Q6-3：404）。 */
		@NotNull
		private Long categoryId;

		/** 予算額。0円は可、マイナスは不可。 */
		@NotNull
		@Min(0)
		@Max(3_000_000)
		private Integer amount;

		public Row() {
		}

		public Row(Long categoryId, Integer amount) {
			this.categoryId = categoryId;
			this.amount = amount;
		}

		public Long getCategoryId() {
			return categoryId;
		}

		public void setCategoryId(Long categoryId) {
			this.categoryId = categoryId;
		}

		public Integer getAmount() {
			return amount;
		}

		public void setAmount(Integer amount) {
			this.amount = amount;
		}

	}

}
