package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * VendorForm の入力チェックの単体テスト（設計書 6.2、6.4）。Spring もデータベースも使わない。
 * 名前は、必須、20文字以下。前後の空白は、全角の空白も含めて取り除く。
 */
class VendorFormTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private static Set<String> errorsOf(String name) {
		VendorForm form = new VendorForm();
		form.setName(name);
		return VALIDATOR.validate(form).stream()
				.map(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
				.collect(Collectors.toSet());
	}

	@ParameterizedTest(name = "空の名前 [{0}] は必須エラーだけ")
	@ValueSource(strings = { "", "   ", "\u3000\u3000", "  \u3000 " })
	void blankNameIsRequiredError(String name) {
		assertThat(errorsOf(name)).containsExactlyInAnyOrder("NotBlank");
	}

	@ParameterizedTest(name = "[{0}] はエラーなし")
	@ValueSource(strings = { "コンビニ", " コンビニ ", "\u3000コンビニ\u3000", "ｱｲｳｴｵ", "A B" })
	void validNames(String name) {
		assertThat(errorsOf(name)).isEmpty();
	}

	@Test
	void lengthBoundary() {
		assertThat(errorsOf("あ".repeat(20))).isEmpty();
		assertThat(errorsOf("あ".repeat(20 + 1))).containsExactly("Size");
		assertThat(errorsOf("a".repeat(20))).isEmpty();
		assertThat(errorsOf("a".repeat(20 + 1))).containsExactly("Size");
	}

	/** 取り除いたあとの文字数で数える（前後の空白を含めて超えていても、取り除いて収まるなら通る） */
	@Test
	void lengthIsCountedAfterStripping() {
		assertThat(errorsOf(" " + "あ".repeat(20) + " ")).isEmpty();
	}

	private static String stripped(String input) {
		VendorForm form = new VendorForm();
		form.setName(input);
		return form.getName();
	}

	@Test
	void nameIsStrippedIncludingFullWidthSpaces() {
		assertThat(stripped(" コンビニ ")).isEqualTo("コンビニ");
		assertThat(stripped("\u3000コンビニ\u3000")).isEqualTo("コンビニ");
		assertThat(stripped(" \u3000コンビニ \u3000")).isEqualTo("コンビニ");
		assertThat(stripped("\u3000\u3000")).isEmpty();
		assertThat(stripped(null)).isNull();
	}

	/** 途中の空白は取り除かない */
	@Test
	void innerSpacesAreKept() {
		assertThat(stripped("A B")).isEqualTo("A B");
	}

	@Test
	void formHasNoUserIdField() {
		assertThat(VendorForm.class.getDeclaredFields()).extracting(f -> f.getName()).containsExactly("name");
	}

}
