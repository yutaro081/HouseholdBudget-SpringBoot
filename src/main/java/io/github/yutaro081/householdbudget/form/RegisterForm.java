package io.github.yutaro081.householdbudget.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * アカウント登録画面の入力を受け取るクラス（設計書 4.7、6.2）。
 * <p>
 * 注釈の組み合わせは、D4 の B のとおり：「必須」を他のエラーと重ねないため、
 * 形式の正規表現は空文字を通す。必須以外のエラーは、重なったら全部出る（G7）。
 * エラーメッセージのキーは、注釈の名前.registerForm.項目名（messages.properties、G6）。
 * <p>
 * user_id など、画面から指定させたくない項目は持たない（6.1）。
 */
public class RegisterForm {

	/** 半角英数字のみ（Q20）、10文字以下（要件5章）。空欄は不可。 */
	@NotBlank
	@Pattern(regexp = "[A-Za-z0-9]*")
	@Size(max = 10)
	private String username;

	/** 半角の英数字と記号のみ（空白は不可）、8文字以上30文字以下（要件5章、H1）。 */
	@NotEmpty
	@Pattern(regexp = "[!-~]*")
	@Size(min = 8, max = 30)
	private String password;

	public String getUsername() {
		return username;
	}

	/** ユーザー名の前後の空白は、全角の空白も含めて取り除く（B9、6.4）。trim() は全角の空白を取らないため strip() を使う。 */
	public void setUsername(String username) {
		this.username = (username == null) ? null : username.strip();
	}

	public String getPassword() {
		return password;
	}

	/** パスワードは、入力したとおりに照合するため、空白を取り除かない（G2）。 */
	public void setPassword(String password) {
		this.password = password;
	}

}
