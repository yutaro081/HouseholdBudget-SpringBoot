package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * RegisterForm の入力チェックの単体テスト（設計書 6.2、6.4、G7、D4 の B）。
 * Spring もデータベースも使わない。
 * 「必須」のエラーは他のエラーと重ならず、それ以外のエラーは、重なったら全部出る。
 */
class RegisterFormTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	/** テスト用のダミーの値（実在するアカウントのパスワードではない） */
	private static final String VALID_USERNAME = "abc123";
	private static final String VALID_PASSWORD = "dummy-Pass-1";

	/** 指定した項目に出たエラーの種類（注釈の名前）の集まり */
	private static Set<String> errorsOf(RegisterForm form, String field) {
		return VALIDATOR.validate(form).stream()
				.filter(v -> v.getPropertyPath().toString().equals(field))
				.map(RegisterFormTest::annotationName)
				.collect(Collectors.toSet());
	}

	private static String annotationName(ConstraintViolation<?> v) {
		return v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
	}

	private static RegisterForm form(String username, String password) {
		RegisterForm form = new RegisterForm();
		form.setUsername(username);
		form.setPassword(password);
		return form;
	}

	// ---- 前後の空白（B9、G2、6.4） ----

	@Test
	void usernameIsStrippedIncludingFullWidthSpaces() {
		assertThat(form(" abc ", VALID_PASSWORD).getUsername()).isEqualTo("abc");
		assertThat(form("　abc　", VALID_PASSWORD).getUsername()).isEqualTo("abc");
		assertThat(form(" 　abc 　", VALID_PASSWORD).getUsername()).isEqualTo("abc");
		assertThat(form("　　", VALID_PASSWORD).getUsername()).isEmpty();
		assertThat(form(null, VALID_PASSWORD).getUsername()).isNull();
	}

	@Test
	void passwordIsNotStripped() {
		assertThat(form(VALID_USERNAME, " abc ").getPassword()).isEqualTo(" abc ");
		assertThat(form(VALID_USERNAME, "　abc").getPassword()).isEqualTo("　abc");
	}

	// ---- ユーザー名 ----

	@ParameterizedTest(name = "ユーザー名 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			// 入力 | 出るエラー（注釈の名前。なければ空）
			"''|NotBlank",
			"'   '|NotBlank",
			"'　　'|NotBlank",
			"あいう|Pattern",
			"abcdefghijk|Size",
			"あいうえおかきくけこさ|Pattern,Size",
			"'abc def'|Pattern",
			"ａｂｃ|Pattern",
			"'abc-def'|Pattern",
			"' abc '|",
			"'　abc　'|",
			"abcdefghij|",
			"abc|",
			"ABCxyz789|"
	})
	void usernameErrors(String username, String expected) {
		assertThat(errorsOf(form(username, VALID_PASSWORD), "username")).isEqualTo(names(expected));
	}

	// ---- パスワード ----

	@ParameterizedTest(name = "パスワード [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"''|NotEmpty,Size",
			"'   '|Pattern,Size",
			"abc12|Size",
			"abc1234|Size",
			"あいうえおかきく|Pattern",
			"あ|Pattern,Size",
			"'abc 1234'|Pattern",
			"'dummy-Pass-1 '|Pattern",
			"abcd1234|",
			"Pass!@#$|",
			"dummy-Pass-1|"
	})
	void passwordErrors(String password, String expected) {
		assertThat(errorsOf(form(VALID_USERNAME, password), "password")).isEqualTo(names(expected));
	}

	@Test
	void passwordLengthBoundaries() {
		assertThat(errorsOf(form(VALID_USERNAME, "a".repeat(7)), "password")).containsExactly("Size");
		assertThat(errorsOf(form(VALID_USERNAME, "a".repeat(8)), "password")).isEmpty();
		assertThat(errorsOf(form(VALID_USERNAME, "a".repeat(30)), "password")).isEmpty();
		assertThat(errorsOf(form(VALID_USERNAME, "a".repeat(31)), "password")).containsExactly("Size");
	}

	@Test
	void usernameLengthBoundaries() {
		assertThat(errorsOf(form("a".repeat(10), VALID_PASSWORD), "username")).isEmpty();
		assertThat(errorsOf(form("a".repeat(11), VALID_PASSWORD), "username")).containsExactly("Size");
	}

	/** 必須のエラーは、他のエラーと重ならない（D4 の B）。 */
	@Test
	void requiredErrorDoesNotOverlapWithOthers() {
		assertThat(errorsOf(form("", VALID_PASSWORD), "username")).containsExactly("NotBlank");
	}

	private static Set<String> names(String commaSeparated) {
		if (commaSeparated == null || commaSeparated.isEmpty()) {
			return Set.of();
		}
		return Set.of(commaSeparated.split(","));
	}

}
