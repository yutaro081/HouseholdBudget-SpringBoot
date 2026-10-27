package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * CategoryForm の入力チェックの単体テスト（設計書 6.2、6.4）。Spring もデータベースも使わない。
 * 名前は、必須、10文字以下。前後の空白は、全角の空白も含めて取り除く。
 */
class CategoryFormTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private static Set<String> errorsOf(String name) {
		CategoryForm form = new CategoryForm();
		form.setName(name);
		return VALIDATOR.validate(form).stream()
				.map(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
				.collect(Collectors.toSet());
	}

	@ParameterizedTest(name = "名前 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			// 入力 | 出るエラー（注釈の名前。なければ空）
			"''|NotBlank",
			"'   '|NotBlank",
			"'　　'|NotBlank",
			"'  　 '|NotBlank",
			"食費|",
			"' 食費 '|",
			"'　食費　'|",
			"あいうえおかきくけこ|",
			"あいうえおかきくけこさ|Size",
			"abcdefghij|",
			"abcdefghijk|Size",
			"'食 費'|",
			"ｱｲｳｴｵ|"
	})
	void errors(String name, String expected) {
		Set<String> expectedSet = (expected == null || expected.isEmpty()) ? Set.of() : Set.of(expected.split(","));
		assertThat(errorsOf(name)).isEqualTo(expectedSet);
	}

	private static String stripped(String input) {
		CategoryForm form = new CategoryForm();
		form.setName(input);
		return form.getName();
	}

	@Test
	void nameIsStrippedIncludingFullWidthSpaces() {
		assertThat(stripped(" 食費 ")).isEqualTo("食費");
		assertThat(stripped("　食費　")).isEqualTo("食費");
		assertThat(stripped(" 　食費 　")).isEqualTo("食費");
		assertThat(stripped("　　")).isEmpty();
		assertThat(stripped(null)).isNull();
	}

	/** 途中の空白は取り除かない */
	@Test
	void innerSpacesAreKept() {
		CategoryForm form = new CategoryForm();
		form.setName("食 費");
		assertThat(form.getName()).isEqualTo("食 費");
	}

	/** 取り除いたあとの文字数で数える（前後の空白を含めて11文字でも、取り除いて10文字なら通る） */
	@Test
	void lengthIsCountedAfterStripping() {
		assertThat(errorsOf(" あいうえおかきくけこ ")).isEmpty();
	}

	@Test
	void formHasNoUserIdField() {
		assertThat(CategoryForm.class.getDeclaredFields()).extracting(f -> f.getName()).containsExactly("name");
	}

}
