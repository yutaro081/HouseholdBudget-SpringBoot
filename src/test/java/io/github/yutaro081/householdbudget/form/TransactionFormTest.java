package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.WebDataBinder;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * TransactionForm の入力チェックの単体テスト（設計書 6.2、6.4〜6.7。issue 032 で、金額・科目・摘要を明細の行〔lines〕に移した）。
 * Spring の起動もデータベースも使わない。
 * <ul>
 * <li>Form の注釈だけの確認（Validator）：範囲・文字数・必須。</li>
 * <li>画面から送られる文字列を束縛してから確かめる（WebDataBinder。設定は本番の @InitBinder と同じ TransactionFormBinding.configure）：
 * 型変換のエラーと、範囲のエラーが重ならないこと（G3、G7）、どの行でも同じに効くこと、空の行の扱い、行の数の上限。</li>
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
		form.setVendorId(2L);
		form.setPaymentMethodId(3L);
		form.setLines(new java.util.ArrayList<>(List.of(new TransactionForm.Line(1L, 1000, "昼食"))));
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
			"0|lines[0].amount:Min", "-1|lines[0].amount:Min", "1000001|lines[0].amount:Max"
	})
	void amountRange(Integer amount, String expected) {
		TransactionForm form = validForm();
		form.getLines().get(0).setAmount(amount);
		assertThat(errorsOf(form)).isEqualTo(expectedSet(expected));
	}

	@ParameterizedTest(name = "日付 {0} → {1}")
	@CsvSource(delimiter = '|', value = {
			"2000-03-01|", "2100-12-31|", "2026-10-27|", "2028-02-29|",
			"1999-12-31|transactionDate:TransactionDateRange",
			"2000-01-01|transactionDate:TransactionDateRange",
			"2000-02-29|transactionDate:TransactionDateRange",
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
	void commonIdsNullAreOnlyRequired() {
		TransactionForm form = validForm();
		form.setVendorId(null);
		form.setPaymentMethodId(null);
		assertThat(errorsOf(form)).containsExactly("paymentMethodId:NotNull", "vendorId:NotNull");
	}

	@Test
	void descriptionOf30CharactersIsAllowedAnd31IsNot() {
		TransactionForm form = validForm();
		form.getLines().get(0).setDescription("あ".repeat(30));
		assertThat(errorsOf(form)).isEmpty();
		form.getLines().get(0).setDescription("あ".repeat(31));
		assertThat(errorsOf(form)).containsExactly("lines[0].description:Size");
	}

	@Test
	void descriptionOf30HalfWidthCharactersIsAllowedAnd31IsNot() {
		TransactionForm form = validForm();
		form.getLines().get(0).setDescription("a".repeat(30));
		assertThat(errorsOf(form)).isEmpty();
		form.getLines().get(0).setDescription("a".repeat(31));
		assertThat(errorsOf(form)).containsExactly("lines[0].description:Size");
	}

	/** 行の範囲・文字数の注釈は、どの行にも効く（5 行目で確かめる） */
	@Test
	void theLineAnnotationsWorkOnTheFifthLineToo() {
		TransactionForm form = validForm();
		for (int i = 1; i < 4; i++) {
			form.getLines().add(new TransactionForm.Line());
		}
		form.getLines().add(new TransactionForm.Line(1L, 0, "あ".repeat(31)));
		assertThat(errorsOf(form)).containsExactly("lines[4].amount:Min", "lines[4].description:Size");
	}

	// ---------------------------------------------------------------
	// 摘要：前後の空白の取り除きと、空なら null（D6）
	// ---------------------------------------------------------------

	private static String strippedDescription(String input) {
		TransactionForm.Line line = new TransactionForm.Line();
		line.setDescription(input);
		return line.getDescription();
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
		form.getLines().get(0).setDescription("　 " + "あ".repeat(30) + " 　");
		assertThat(form.getLines().get(0).getDescription()).hasSize(30);
		assertThat(errorsOf(form)).isEmpty();
	}

	// ---------------------------------------------------------------
	// 画面から送られる文字列を束縛してから確かめる（本番の @InitBinder と同じ設定）
	// ---------------------------------------------------------------

	/** 共通の項目と、1 行目の正しい値。lines に「名前, 値, ...」を渡すと、足す・差し替える。値が null の名前は、送らない */
	private static Map<String, Object> values(String... lines) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("transactionDate", "2026-10-27");
		values.put("vendorId", "2");
		values.put("paymentMethodId", "3");
		values.put("lines[0].categoryId", "1");
		values.put("lines[0].amount", "1000");
		values.put("lines[0].description", "昼食");
		for (int i = 0; i < lines.length; i += 2) {
			if (lines[i + 1] == null) {
				values.remove(lines[i]);
			} else {
				values.put(lines[i], lines[i + 1]);
			}
		}
		return values;
	}

	/** 束縛して入力チェックし、エラーのある項目と、その種類（typeMismatch か注釈の名前）を返す */
	private static WebDataBinder bind(Map<String, Object> values) {
		TransactionForm form = new TransactionForm();
		WebDataBinder binder = new WebDataBinder(form, "transactionForm");
		binder.setConversionService(new DefaultFormattingConversionService());
		binder.setValidator(new SpringValidatorAdapter(VALIDATOR));
		TransactionFormBinding.configure(binder);
		binder.bind(new MutablePropertyValues(values));
		binder.validate();
		return binder;
	}

	private static Set<String> codes(Map<String, Object> values) {
		return bind(values).getBindingResult().getFieldErrors().stream()
				.map((FieldError e) -> e.getField() + ":" + e.getCode())
				.collect(Collectors.toCollection(TreeSet::new));
	}

	/** エラーのある項目の名前だけ（空の行の扱いの確認。どの仕組みで出すかに頼らない） */
	private static Set<String> fields(Map<String, Object> values) {
		return bind(values).getBindingResult().getFieldErrors().stream().map(FieldError::getField)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	@Test
	void allValidValuesBindWithoutErrors() {
		assertThat(codes(values())).isEmpty();
	}

	/** 今までの金額の決まり（範囲・型変換・全角の数字）は、行に移っても、今までどおり効く。エラーは重ならない */
	@ParameterizedTest(name = "金額 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			// 通る
			"1|", "1000000|", "' 100 '|", "'　100　'|", "010|",
			// 全角の数字は、半角に直して読む（R13）。範囲のチェックも、直した値で行う
			"１２３|", "１|", "１００００００|", "１2３|", "'　１００　'|",
			"０|Min", "-１|Min", "１０００００１|Max",
			// 範囲
			"0|Min", "-1|Min", "-5|Min", "1000001|Max",
			// 型変換のエラー（範囲・必須のエラーは重ならない）
			"abc|typeMismatch",
			"'1,000'|typeMismatch",
			"1.5|typeMismatch",
			"－５|typeMismatch",
			"１，０００|typeMismatch",
			"１　２|typeMismatch",
			"１．５|typeMismatch",
			"＋５|typeMismatch",
			"１２円|typeMismatch",
			"10円|typeMismatch",
			"99999999999|typeMismatch",
			"1 000|typeMismatch",
			"0x10|typeMismatch",
			"#10|typeMismatch",
			"+5|typeMismatch"
	})
	void amountBindingOnTheFirstLine(String input, String expected) {
		assertThat(codes(values("lines[0].amount", input))).isEqualTo(expectedSet(expected == null ? "" : "lines[0].amount:" + expected));
	}

	/** 同じ決まりが、ほかの行（4 行目）にも、今までどおり効く */
	@ParameterizedTest(name = "4 行目の金額 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"1|", "1000000|", "１２３|", "'　100　'|",
			"0|Min", "1000001|Max", "１０００００１|Max",
			"abc|typeMismatch", "'1,000'|typeMismatch", "１２円|typeMismatch", "+5|typeMismatch", "99999999999|typeMismatch"
	})
	void amountBindingOnTheFourthLine(String input, String expected) {
		assertThat(codes(values("lines[3].categoryId", "1", "lines[3].amount", input)))
				.isEqualTo(expectedSet(expected == null ? "" : "lines[3].amount:" + expected));
	}

	@ParameterizedTest(name = "日付 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"2000-03-01|", "2100-12-31|", "2026-10-27|", "2024-02-29|",
			"''|transactionDate:NotNull",
			"1999-12-31|transactionDate:TransactionDateRange",
			"2000-02-29|transactionDate:TransactionDateRange",
			"2101-01-01|transactionDate:TransactionDateRange",
			"0226-05-01|transactionDate:TransactionDateRange",
			"abc|transactionDate:typeMismatch",
			"2026-02-30|transactionDate:typeMismatch",
			"2025-02-29|transactionDate:typeMismatch",
			"2026-13-01|transactionDate:typeMismatch",
			"2026/10/27|transactionDate:typeMismatch",
			"20026-05-01|transactionDate:typeMismatch"
	})
	void dateBinding(String input, String expected) {
		assertThat(codes(values("transactionDate", input == null ? "" : input))).isEqualTo(expectedSet(expected));
	}

	@ParameterizedTest(name = "{0} [{1}] → {2}")
	@CsvSource(delimiter = '|', value = {
			"lines[0].categoryId|abc|lines[0].categoryId:typeMismatch",
			"lines[0].categoryId|99999999999999999999|lines[0].categoryId:typeMismatch",
			"lines[0].categoryId|5|",
			"lines[2].categoryId|abc|lines[2].categoryId:typeMismatch",
			"vendorId|''|vendorId:NotNull",
			"vendorId|abc|vendorId:typeMismatch",
			"vendorId|5|",
			"paymentMethodId|''|paymentMethodId:NotNull",
			"paymentMethodId|abc|paymentMethodId:typeMismatch",
			"paymentMethodId|5|"
	})
	void idBinding(String field, String input, String expected) {
		List<String> extra = new java.util.ArrayList<>(List.of(field, input == null ? "" : input));
		if (field.startsWith("lines[2]")) {
			extra.addAll(List.of("lines[2].amount", "300"));
		}
		Set<String> result = codes(values(extra.toArray(String[]::new)));
		if (expected != null && expected.contains("typeMismatch") && field.startsWith("lines[")) {
			// 科目が型変換のエラーの行は、科目のエラーだけ（必須のエラーとは重ならない）
			assertThat(result).contains(expected).allMatch(e -> e.startsWith(field + ":"));
			assertThat(result).hasSize(1);
		} else {
			assertThat(result).isEqualTo(expectedSet(expected));
		}
	}

	@Test
	void descriptionBinding() {
		assertThat(codes(values("lines[0].description", ""))).isEmpty();
		assertThat(codes(values("lines[0].description", "　"))).isEmpty();
		assertThat(codes(values("lines[0].description", "あ".repeat(30)))).isEmpty();
		assertThat(codes(values("lines[0].description", "あ".repeat(31)))).containsExactly("lines[0].description:Size");
		assertThat(codes(values("lines[4].categoryId", "1", "lines[4].amount", "1", "lines[4].description", "あ".repeat(31))))
				.as("5 行目でも同じ").containsExactly("lines[4].description:Size");
	}

	// ---------------------------------------------------------------
	// 空の行・一部だけ入った行・行の数（issue 032）
	// ---------------------------------------------------------------

	/** 全部が空の行は無視する（エラーにしない）。摘要が空白だけでも、空の行 */
	@ParameterizedTest(name = "2 行目が空：{0}")
	@ValueSource(strings = { "送らない", "空で送る", "摘要が空白だけ", "金額が空白だけ" })
	void blankLinesAreIgnored(String how) {
		String[] second = switch (how) {
			case "送らない" -> new String[0];
			case "空で送る" -> new String[] { "lines[1].categoryId", "", "lines[1].amount", "", "lines[1].description", "" };
			case "摘要が空白だけ" -> new String[] { "lines[1].categoryId", "", "lines[1].amount", "", "lines[1].description", "　 " };
			default -> new String[] { "lines[1].categoryId", "", "lines[1].amount", " ", "lines[1].description", "" };
		};
		assertThat(fields(values(second))).isEmpty();
	}

	/** 一部だけ入った行は、足りない項目にエラー。金額に型変換のエラーがある行は、空の行として扱わない（科目のエラーも出る） */
	@ParameterizedTest(name = "2 行目：{0} = [{1}] → {2}")
	@CsvSource(delimiter = '|', value = {
			"lines[1].amount|500|lines[1].categoryId",
			"lines[1].categoryId|7|lines[1].amount",
			"lines[1].description|メモ|lines[1].amount,lines[1].categoryId",
			"lines[1].amount|abc|lines[1].amount,lines[1].categoryId",
			"lines[1].amount|0|lines[1].amount,lines[1].categoryId",
			"lines[1].categoryId|abc|lines[1].amount,lines[1].categoryId"
	})
	void aPartlyFilledLineHasErrorsOnItsMissingFields(String field, String value, String expected) {
		assertThat(fields(values(field, value))).isEqualTo(expectedSet(expected));
	}

	/** 金額だけ空・科目だけ空のときの必須のエラーは、1 項目に 1 つ（ほかのエラーと重ならない） */
	@Test
	void requiredErrorsAreOnePerField() {
		Map<String, Object> values = values("lines[0].amount", "");
		List<FieldError> errors = bind(values).getBindingResult().getFieldErrors();
		assertThat(errors).hasSize(1);
		assertThat(errors.get(0).getField()).isEqualTo("lines[0].amount");
	}

	/** 1 行もなければ、明細（lines）にエラー。行のどの項目にもエラーは付けない */
	@ParameterizedTest(name = "明細が空：{0}")
	@ValueSource(strings = { "行を送らない", "1 行目を空で送る", "5 行とも空で送る" })
	void noFilledLineIsAnErrorOnLines(String how) {
		Map<String, Object> values = values("lines[0].categoryId", null, "lines[0].amount", null, "lines[0].description", null);
		if (!how.startsWith("行を")) {
			int count = how.startsWith("5") ? 5 : 1;
			for (int i = 0; i < count; i++) {
				values.put("lines[" + i + "].categoryId", "");
				values.put("lines[" + i + "].amount", "");
				values.put("lines[" + i + "].description", "");
			}
		}
		assertThat(fields(values)).containsExactly("lines");
	}

	/** 行のエラーがあるときは、「1 行以上」のエラーは出さない（入れた行があるため） */
	@Test
	void aFilledLineWithAnErrorIsNotTheNoLineError() {
		assertThat(fields(values("lines[0].amount", "abc"))).doesNotContain("lines");
	}

	/** 6 行目（lines[5]）は束縛できない（行の数は 5 行まで。画面は 400 にする） */
	@ParameterizedTest(name = "{0} は束縛できない")
	@ValueSource(strings = { "lines[5].amount", "lines[5].categoryId", "lines[255].description" })
	void theSixthLineCannotBeBound(String name) {
		assertThatThrownBy(() -> bind(values(name, "1"))).isInstanceOf(InvalidPropertyException.class);
	}

	/** 5 行目（lines[4]）までは束縛できる */
	@Test
	void theFifthLineCanBeBound() {
		WebDataBinder binder = bind(values("lines[4].categoryId", "1", "lines[4].amount", "5"));
		assertThat(binder.getBindingResult().hasErrors()).isFalse();
		assertThat(((TransactionForm) binder.getTarget()).getLines()).hasSize(5);
	}

	// ---------------------------------------------------------------
	// 画面に渡す形（issue 032）
	// ---------------------------------------------------------------

	@Test
	void theNewFormHasFiveBlankLines() {
		TransactionForm form = TransactionForm.forNew();
		assertThat(form.getLines()).hasSize(TransactionForm.MAX_LINES).allMatch(TransactionForm.Line::isBlank);
		assertThat(TransactionForm.MAX_LINES).isEqualTo(5);
	}

	@Test
	void filledLinesSkipBlankLinesAndKeepTheOrder() {
		TransactionForm form = TransactionForm.forNew();
		form.getLines().set(1, new TransactionForm.Line(7L, 200, null));
		form.getLines().set(3, new TransactionForm.Line(8L, 400, "メモ"));
		assertThat(form.filledLines()).extracting(TransactionForm.Line::getAmount).containsExactly(200, 400);
	}

	@Test
	void fillLinesUpToAddsBlankLinesOnly() {
		TransactionForm form = new TransactionForm();
		form.getLines().add(new TransactionForm.Line(7L, 200, null));
		form.fillLinesUpTo(TransactionForm.MAX_LINES);
		assertThat(form.getLines()).hasSize(5);
		assertThat(form.getLines().get(0).getAmount()).isEqualTo(200);
		assertThat(form.getLines().subList(1, 5)).allMatch(TransactionForm.Line::isBlank);
		form.fillLinesUpTo(1);
		assertThat(form.getLines()).as("減らさない").hasSize(5);
	}

	private static Set<String> expectedSet(String expected) {
		return (expected == null || expected.isEmpty()) ? Set.of() : new TreeSet<>(Set.of(expected.split(",")));
	}

}
