package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.WebDataBinder;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * TransactionForm の入力チェックの単体テスト（設計書 6.2、6.4〜6.7、3.4 の境界値）。Spring の起動もデータベースも使わない。
 * <ul>
 * <li>Form の注釈だけの確認（Validator）：範囲・文字数・必須。</li>
 * <li>画面から送られる文字列を束縛してから確かめる（WebDataBinder）：型変換のエラーと、必須・範囲が重ならないこと（G3、G7）。
 * 日付と ID の変換は、Spring の標準の変換サービスを使う（本番と同じ）。金額は StrictIntegerEditor を付ける。</li>
 * </ul>
 */
class TransactionFormTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	// ---------------------------------------------------------------
	// 注釈だけの確認（束縛をしない）
	// ---------------------------------------------------------------

	private static TransactionForm validForm() {
		TransactionForm form = new TransactionForm();
		form.setTransactionDate(LocalDate.of(2026, 10, 27));
		form.setAmount(1000);
		form.setCategoryId(1L);
		form.setVendorId(2L);
		form.setPaymentMethodId(3L);
		form.setDescription("昼食");
		return form;
	}

	private static Set<String> errorsOf(TransactionForm form) {
		return VALIDATOR.validate(form).stream()
				.map(v -> v.getPropertyPath() + ":" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
				.collect(Collectors.toCollection(TreeSet::new));
	}

	@Test
	void validFormHasNoErrors() {
		assertThat(errorsOf(validForm())).isEmpty();
	}

	@ParameterizedTest(name = "金額 {0} → {1}")
	@CsvSource(delimiter = '|', value = {
			"1|", "1000000|", "500|",
			"0|amount:Min", "-1|amount:Min", "1000001|amount:Max"
	})
	void amountRange(Integer amount, String expected) {
		TransactionForm form = validForm();
		form.setAmount(amount);
		assertThat(errorsOf(form)).isEqualTo(expectedSet(expected));
	}

	@Test
	void amountNullIsOnlyRequired() {
		TransactionForm form = validForm();
		form.setAmount(null);
		assertThat(errorsOf(form)).containsExactly("amount:NotNull");
	}

	@ParameterizedTest(name = "日付 {0} → {1}")
	@CsvSource(delimiter = '|', value = {
			"2000-01-01|", "2100-12-31|", "2026-10-27|", "2000-02-29|",
			"1999-12-31|transactionDate:TransactionDateRange",
			"2101-01-01|transactionDate:TransactionDateRange",
			"0226-05-01|transactionDate:TransactionDateRange",
			"9999-12-31|transactionDate:TransactionDateRange"
	})
	void dateRange(LocalDate date, String expected) {
		TransactionForm form = validForm();
		form.setTransactionDate(date);
		assertThat(errorsOf(form)).isEqualTo(expectedSet(expected));
	}

	@Test
	void dateNullIsOnlyRequired() {
		TransactionForm form = validForm();
		form.setTransactionDate(null);
		assertThat(errorsOf(form)).containsExactly("transactionDate:NotNull");
	}

	@Test
	void idsNullAreOnlyRequired() {
		TransactionForm form = validForm();
		form.setCategoryId(null);
		form.setVendorId(null);
		form.setPaymentMethodId(null);
		assertThat(errorsOf(form))
				.containsExactly("categoryId:NotNull", "paymentMethodId:NotNull", "vendorId:NotNull");
	}

	@Test
	void descriptionOf30CharactersIsAllowedAnd31IsNot() {
		TransactionForm form = validForm();
		form.setDescription("あ".repeat(30));
		assertThat(errorsOf(form)).isEmpty();
		form.setDescription("あ".repeat(31));
		assertThat(errorsOf(form)).containsExactly("description:Size");
	}

	@Test
	void descriptionOf30HalfWidthCharactersIsAllowedAnd31IsNot() {
		TransactionForm form = validForm();
		form.setDescription("a".repeat(30));
		assertThat(errorsOf(form)).isEmpty();
		form.setDescription("a".repeat(31));
		assertThat(errorsOf(form)).containsExactly("description:Size");
	}

	// ---------------------------------------------------------------
	// 摘要：前後の空白の取り除きと、空なら null（D6）
	// ---------------------------------------------------------------

	private static String strippedDescription(String input) {
		TransactionForm form = new TransactionForm();
		form.setDescription(input);
		return form.getDescription();
	}

	@ParameterizedTest(name = "摘要 [{0}] → [{1}]")
	@CsvSource(delimiter = '|', value = {
			"昼食|昼食",
			"' 昼食 '|昼食",
			"'　昼食　'|昼食",
			"'  　昼食 　'|昼食",
			"'昼 食'|昼 食",
			"'昼　食'|昼　食"
	})
	void descriptionIsStripped(String input, String expected) {
		assertThat(strippedDescription(input)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "摘要 [{0}] → null")
	@CsvSource(delimiter = '|', value = { "''", "' '", "'　'", "'  　 '" })
	void blankDescriptionBecomesNull(String input) {
		assertThat(strippedDescription(input)).isNull();
	}

	@Test
	void nullDescriptionStaysNull() {
		assertThat(strippedDescription(null)).isNull();
	}

	/** 取り除いたあとの長さで数える：30文字＋前後の空白（全角を含む）は、エラーにならない */
	@Test
	void descriptionLengthIsCountedAfterStrip() {
		TransactionForm form = validForm();
		form.setDescription("　 " + "あ".repeat(30) + " 　");
		assertThat(form.getDescription()).hasSize(30);
		assertThat(errorsOf(form)).isEmpty();
	}

	@Test
	void descriptionOver30AfterStripIsAnError() {
		TransactionForm form = validForm();
		form.setDescription(" " + "あ".repeat(31) + " ");
		assertThat(errorsOf(form)).containsExactly("description:Size");
	}

	@Test
	void blankDescriptionIsNotAnError() {
		TransactionForm form = validForm();
		form.setDescription("　　");
		assertThat(form.getDescription()).isNull();
		assertThat(errorsOf(form)).isEmpty();
	}

	// ---------------------------------------------------------------
	// 画面から送られる文字列を束縛してから確かめる（型変換のエラーと注釈のエラーが重ならないこと、G3、G7）
	// ---------------------------------------------------------------

	/** 正しい値の送信に、1項目だけ差し替えて束縛し、項目ごとのエラーの名前（typeMismatch か注釈の名前）を返す。 */
	private static Set<String> bindAndValidate(String field, String value) {
		Map<String, Object> values = new HashMap<>();
		values.put("transactionDate", "2026-10-27");
		values.put("amount", "1000");
		values.put("categoryId", "1");
		values.put("vendorId", "2");
		values.put("paymentMethodId", "3");
		values.put("description", "昼食");
		if (value == null) {
			values.remove(field);
		} else {
			values.put(field, value);
		}

		TransactionForm form = new TransactionForm();
		WebDataBinder binder = new WebDataBinder(form, "transactionForm");
		binder.setConversionService(new DefaultFormattingConversionService());
		binder.setValidator(new SpringValidatorAdapter(VALIDATOR));
		binder.registerCustomEditor(Integer.class, "amount", new StrictIntegerEditor());
		binder.bind(new MutablePropertyValues(values));
		binder.validate();
		return binder.getBindingResult().getFieldErrors().stream()
				.map((FieldError e) -> e.getField() + ":" + e.getCode())
				.collect(Collectors.toCollection(TreeSet::new));
	}

	@Test
	void allValidValuesBindWithoutErrors() {
		assertThat(bindAndValidate("description", "昼食")).isEmpty();
	}

	@ParameterizedTest(name = "金額 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			// 必須（型変換のエラーと重ならない）
			"''|amount:NotNull",
			"' '|amount:NotNull",
			"'　'|amount:NotNull",
			// 通る
			"1|", "1000000|", "' 100 '|", "'　100　'|", "010|",
			// 全角の数字は、半角に直して読む（R13）。範囲のチェックも、直した値で行う
			"１２３|", "１|", "１００００００|", "１2３|", "'　１００　'|",
			"０|amount:Min", "-１|amount:Min", "１０００００１|amount:Max",
			// 範囲
			"0|amount:Min", "-1|amount:Min", "-5|amount:Min", "1000001|amount:Max",
			// 型変換のエラー（範囲・必須のエラーは重ならない）
			"abc|amount:typeMismatch",
			"'1,000'|amount:typeMismatch",
			"1.5|amount:typeMismatch",
			// 全角でも、数字以外はエラーのまま（R13）
			"－５|amount:typeMismatch",
			"１，０００|amount:typeMismatch",
			"１　２|amount:typeMismatch",
			"１．５|amount:typeMismatch",
			"＋５|amount:typeMismatch",
			"１２円|amount:typeMismatch",
			"10円|amount:typeMismatch",
			"99999999999|amount:typeMismatch",
			"1 000|amount:typeMismatch",
			"0x10|amount:typeMismatch",
			"#10|amount:typeMismatch",
			"+5|amount:typeMismatch"
	})
	void amountBinding(String input, String expected) {
		assertThat(bindAndValidate("amount", input == null ? "" : input)).isEqualTo(expectedSet(expected));
	}

	@Test
	void amountNotSentIsRequired() {
		assertThat(bindAndValidate("amount", null)).containsExactly("amount:NotNull");
	}

	@ParameterizedTest(name = "日付 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"2000-01-01|", "2100-12-31|", "2026-10-27|", "2024-02-29|",
			// 必須
			"''|transactionDate:NotNull",
			// 範囲外
			"1999-12-31|transactionDate:TransactionDateRange",
			"2101-01-01|transactionDate:TransactionDateRange",
			"0226-05-01|transactionDate:TransactionDateRange",
			// 型変換のエラー（日付の形でない・存在しない日付・5桁の年。Q5-3 の A）
			"abc|transactionDate:typeMismatch",
			"2026-02-30|transactionDate:typeMismatch",
			"2025-02-29|transactionDate:typeMismatch",
			"2026-13-01|transactionDate:typeMismatch",
			"2026/10/27|transactionDate:typeMismatch",
			"20026-05-01|transactionDate:typeMismatch"
	})
	void dateBinding(String input, String expected) {
		assertThat(bindAndValidate("transactionDate", input == null ? "" : input)).isEqualTo(expectedSet(expected));
	}

	@ParameterizedTest(name = "{0} [{1}] → {2}")
	@CsvSource(delimiter = '|', value = {
			"categoryId|''|categoryId:NotNull",
			"categoryId|abc|categoryId:typeMismatch",
			"categoryId|99999999999999999999|categoryId:typeMismatch",
			"categoryId|5|",
			"vendorId|''|vendorId:NotNull",
			"vendorId|abc|vendorId:typeMismatch",
			"vendorId|5|",
			"paymentMethodId|''|paymentMethodId:NotNull",
			"paymentMethodId|abc|paymentMethodId:typeMismatch",
			"paymentMethodId|5|"
	})
	void idBinding(String field, String input, String expected) {
		assertThat(bindAndValidate(field, input == null ? "" : input)).isEqualTo(expectedSet(expected));
	}

	@Test
	void descriptionBinding() {
		assertThat(bindAndValidate("description", "")).isEmpty();
		assertThat(bindAndValidate("description", "　")).isEmpty();
		assertThat(bindAndValidate("description", "あ".repeat(30))).isEmpty();
		assertThat(bindAndValidate("description", "あ".repeat(31))).containsExactly("description:Size");
	}

	private static Set<String> expectedSet(String expected) {
		return (expected == null || expected.isEmpty()) ? Set.of() : new TreeSet<>(Set.of(expected.split(",")));
	}

}
