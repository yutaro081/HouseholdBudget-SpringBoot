package io.github.yutaro081.householdbudget.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 科目の追加・変更の入力を受け取るクラス（設計書 4.7、6.2）。
 * 名前は、必須、10文字以下（要件5章）。同じユーザーの中での重複は、Service が確認する（5.6）。
 * 注釈の組み合わせは D4 の B のとおり（「必須」を他のエラーと重ねない。文字数は別の注釈）。
 * エラーメッセージのキーは、注釈の名前.categoryForm.name（messages.properties、G6）。
 * <p>
 * user_id など、画面から指定させたくない項目は持たない（6.1）。
 */
public class CategoryForm {

	@NotBlank
	@Size(max = 10)
	private String name;

	public CategoryForm() {
	}

	/** 変更画面で、今の名前を入れた状態で表示するために使う。 */
	public CategoryForm(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	/** 前後の空白は、全角の空白も含めて取り除く（B9、6.4）。trim() は全角の空白を取らないため strip() を使う。 */
	public void setName(String name) {
		this.name = (name == null) ? null : name.strip();
	}

}
