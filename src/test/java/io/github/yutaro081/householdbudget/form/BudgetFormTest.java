package io.github.yutaro081.householdbudget.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.WebDataBinder;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * BudgetForm の入力チェックの単体テスト（設計書 6.2、6.5、Q6-1、Q6-7）。Spring の起動もデータベースも使わない。
 * <ul>
 * <li>注釈だけの確認（Validator）：予算額の範囲と必須。</li>
 * <li>画面から送られる文字列を束縛してから確かめる（WebDataBinder）：行ごとの予算額に、StrictIntegerEditor が効くこと、
 * 型変換のエラーと「必須」「範囲」が重ならないこと（G3）、メッセージのキーが行の番号なしの形でも当たること。</li>
 * </ul>
 * 【Q6-1 の実験の結果】金額の項目の名前を、行の番号を除いた形（rows.amount）で登録すると、全行に効く。
 * 行の番号つき（rows[0].amount）で登録すると、その行にしか効かない（ほかの行は標準の変換になり、全角数字・0x10・+5 が通る）。
 * 標準の変換のままでも、通ってしまう（設計書 6.5）。この結果を、下のテストで残している。
 */
class BudgetFormTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	// ---------------------------------------------------------------
	// 注釈だけの確認（束縛をしない）
	// ---------------------------------------------------------------

	private static Set<String> errorsOf(BudgetForm form) {
		return VALIDATOR.validate(form).stream()
				.map(v -> v.getPropertyPath() + ":" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private static BudgetForm formOf(Integer... amounts) {
		List<BudgetForm.Row> rows = new ArrayList<>();
		for (int i = 0; i < amounts.length; i++) {
			rows.add(new BudgetForm.Row(100L + i, amounts[i]));
		}
		return new BudgetForm(rows);
	}

	@Test
	void validFormHasNoErrors() {
		assertThat(errorsOf(formOf(0, 30000, 3_000_000))).isEmpty();
	}

	@Test
	void formWithNoRowsHasNoErrors() {
		assertThat(errorsOf(new BudgetForm())).isEmpty();
	}

	@ParameterizedTest(name = "予算額 {0} → {1}")
	@CsvSource(delimiter = '|', value = {
			"0|", "1|", "3000000|", "30000|",
			"-1|rows[0].amount:Min", "3000001|rows[0].amount:Max", "-1000|rows[0].amount:Min"
	})
	void amountRange(Integer amount, String expected) {
		assertThat(errorsOf(formOf(amount))).isEqualTo(expectedSet(expected));
	}

	@Test
	void nullAmountIsOnlyRequired() {
		assertThat(errorsOf(formOf((Integer) null))).containsExactly("rows[0].amount:NotNull");
	}

	@Test
	void nullCategoryIdIsOnlyRequired() {
		BudgetForm form = new BudgetForm(List.of(new BudgetForm.Row(null, 100)));
		assertThat(errorsOf(form)).containsExactly("rows[0].categoryId:NotNull");
	}

	/** 行の中の注釈が、すべての行で働く（@Valid が効いている）。エラーは、その行にだけ付く */
	@Test
	void annotationsWorkOnEveryRowAndOnlyOnTheBadOnes() {
		assertThat(errorsOf(formOf(100, -1, 200, 3_000_001, 0)))
				.containsExactly("rows[1].amount:Min", "rows[3].amount:Max");
	}

	// ---------------------------------------------------------------
	// 画面から送られる文字列を束縛してから確かめる
	// ---------------------------------------------------------------

	/** 本番の Controller と同じ付け方：予算額を、行の番号を除いた名前（rows.amount）で登録する（Q6-1 の A） */
	private static void registerEditorLikeTheController(WebDataBinder binder) {
		binder.registerCustomEditor(Integer.class, "rows.amount", new StrictIntegerEditor());
	}

	private static BindingResult bind(java.util.function.Consumer<WebDataBinder> register, String[] categoryIds, String[] amounts, BudgetForm form) {
		WebDataBinder binder = new WebDataBinder(form, "budgetForm");
		binder.setConversionService(new DefaultFormattingConversionService());
		binder.setValidator(new SpringValidatorAdapter(VALIDATOR));
		register.accept(binder);
		Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i < amounts.length; i++) {
			values.put("rows[" + i + "].categoryId", categoryIds[i]);
			values.put("rows[" + i + "].amount", amounts[i]);
		}
		binder.bind(new MutablePropertyValues(values));
		binder.validate();
		return binder.getBindingResult();
	}

	/** 行0..n-1 の科目IDは正しい値にして、予算額だけを差し替えて束縛する。結果を「行:エラー」の集合で返す */
	private static Set<String> errorsAfterBinding(String... amounts) {
		String[] ids = new String[amounts.length];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = String.valueOf(100 + i);
		}
		BindingResult result = bind(BudgetFormTest::registerEditorLikeTheController, ids, amounts, new BudgetForm());
		return result.getFieldErrors().stream()
				.map((FieldError e) -> e.getField() + ":" + e.getCode())
				.collect(Collectors.toCollection(TreeSet::new));
	}

	@ParameterizedTest(name = "予算額 [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			// 通る
			"0|", "1|", "3000000|", "' 100 '|", "'　100　'|", "010|",
			// 必須（型変換のエラーと重ならない）
			"''|rows[0].amount:NotNull",
			"' '|rows[0].amount:NotNull",
			"'　'|rows[0].amount:NotNull",
			// 範囲
			"-1|rows[0].amount:Min", "-5|rows[0].amount:Min", "3000001|rows[0].amount:Max",
			// 型変換のエラー（範囲・必須のエラーは重ならない。Q5-1 の実験で、標準の変換が通した入力を含む）
			"abc|rows[0].amount:typeMismatch",
			"'1,000'|rows[0].amount:typeMismatch",
			"１２３|rows[0].amount:typeMismatch",
			"1 000|rows[0].amount:typeMismatch",
			"0x10|rows[0].amount:typeMismatch",
			"#10|rows[0].amount:typeMismatch",
			"+5|rows[0].amount:typeMismatch",
			"1.5|rows[0].amount:typeMismatch",
			"10円|rows[0].amount:typeMismatch",
			"1e3|rows[0].amount:typeMismatch",
			"99999999999|rows[0].amount:typeMismatch"
	})
	void amountBinding(String input, String expected) {
		assertThat(errorsAfterBinding(input == null ? "" : input)).isEqualTo(expectedSet(expected));
	}

	/** Q6-1：どの行でも変換が効く（0番目だけでなく、1番目・5番目も）。エラーは、その行にだけ付く */
	@Test
	void strictConversionWorksOnEveryRow() {
		// 6行：行0=正常、行1=全角、行2=正常、行3=+5、行4=0x10、行5=途中の空白
		Set<String> errors = errorsAfterBinding("100", "１２３", "200", "+5", "0x10", "1 000");

		assertThat(errors).containsExactly(
				"rows[1].amount:typeMismatch", "rows[3].amount:typeMismatch",
				"rows[4].amount:typeMismatch", "rows[5].amount:typeMismatch");
	}

	@Test
	void allRowsAreConvertedWhenAllAreValid() {
		BudgetForm form = new BudgetForm();
		BindingResult result = bind(BudgetFormTest::registerEditorLikeTheController,
				new String[] { "100", "101", "102", "103", "104", "105" },
				new String[] { "0", " 50 ", "3000000", "1", "30000", "010" }, form);

		assertThat(result.hasErrors()).isFalse();
		assertThat(form.getRows()).extracting(BudgetForm.Row::getAmount).containsExactly(0, 50, 3_000_000, 1, 30000, 10);
		assertThat(form.getRows()).extracting(BudgetForm.Row::getCategoryId).containsExactly(100L, 101L, 102L, 103L, 104L, 105L);
	}

	/** 複数の行にエラーがあるとき、それぞれの行に、その行のエラーが付く（1行に1つだけ） */
	@Test
	void eachBadRowGetsItsOwnSingleError() {
		Set<String> errors = errorsAfterBinding("abc", "", "-1", "3000001", "100");

		assertThat(errors).containsExactly(
				"rows[0].amount:typeMismatch", "rows[1].amount:NotNull",
				"rows[2].amount:Min", "rows[3].amount:Max");
	}

	/** 入力した文字は、エラーのときもそのまま残る（rejectedValue。画面で入力欄に戻す、設計書 6.5、6.9） */
	@Test
	void rejectedValuesAreKept() {
		BudgetForm form = new BudgetForm();
		BindingResult result = bind(BudgetFormTest::registerEditorLikeTheController,
				new String[] { "100", "101", "102" }, new String[] { "abc", "１２３", "-1" }, form);

		assertThat(result.getFieldValue("rows[0].amount")).isEqualTo("abc");
		assertThat(result.getFieldValue("rows[1].amount")).isEqualTo("１２３");
		assertThat(result.getFieldValue("rows[2].amount")).isEqualTo("-1");    // 範囲のエラーは、変換できた値を入力欄の文字にして残す
	}

	// ---- Q6-1 の実験の結果を残す：登録の仕方で、効く行が変わる ----

	private static Set<String> typeMismatchRows(java.util.function.Consumer<WebDataBinder> register, String... amounts) {
		String[] ids = new String[amounts.length];
		Arrays.setAll(ids, i -> String.valueOf(100 + i));
		BindingResult result = bind(register, ids, amounts, new BudgetForm());
		return result.getFieldErrors().stream()
				.filter(e -> "typeMismatch".equals(e.getCode()))
				.map(FieldError::getField)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	/** 行の番号つき（rows[0].amount）で登録すると、その行にしか効かない。ほかの行は標準の変換で、全角数字などが通ってしまう */
	@Test
	void registeringWithARowNumberOnlyAffectsThatRow() {
		Set<String> mismatchRows = typeMismatchRows(
				binder -> binder.registerCustomEditor(Integer.class, "rows[0].amount", new StrictIntegerEditor()),
				"１２３", "１２３", "+5");

		assertThat(mismatchRows).containsExactly("rows[0].amount");   // 行1・行2 は、標準の変換で通ってしまう
	}

	/** 登録しないと、標準の変換になり、全角数字・0x10・+5 が通る（Q5-1 の実験と同じ） */
	@Test
	void withoutRegistrationTheStandardConversionLetsBadInputsThrough() {
		Set<String> mismatchRows = typeMismatchRows(binder -> { }, "１２３", "0x10", "+5", "abc");

		assertThat(mismatchRows).containsExactly("rows[3].amount");   // abc だけがエラー
	}

	/** 型で登録する案（B）でも同じ結果になる（A が使えない場合の代わり。今は A を使う） */
	@Test
	void registeringByTypeAlsoWorksOnEveryRow() {
		Set<String> mismatchRows = typeMismatchRows(
				binder -> binder.registerCustomEditor(Integer.class, new StrictIntegerEditor()),
				"１２３", "+5", "0x10", "100");

		assertThat(mismatchRows).containsExactly("rows[0].amount", "rows[1].amount", "rows[2].amount");
	}

	// ---- 科目ID の束縛（Q6-3：数字でない・空のときは、Controller が 404 にする。ここでは、束縛の結果を確かめる） ----

	@ParameterizedTest(name = "科目ID [{0}] → {1}")
	@CsvSource(delimiter = '|', value = {
			"5|", "123456789|",
			"''|rows[0].categoryId:NotNull",
			"abc|rows[0].categoryId:typeMismatch",
			"1.5|rows[0].categoryId:typeMismatch",
			"99999999999999999999|rows[0].categoryId:typeMismatch"
	})
	void categoryIdBinding(String categoryId, String expected) {
		BindingResult result = bind(BudgetFormTest::registerEditorLikeTheController,
				new String[] { categoryId == null ? "" : categoryId }, new String[] { "100" }, new BudgetForm());
		Set<String> errors = result.getFieldErrors().stream()
				.map(e -> e.getField() + ":" + e.getCode())
				.collect(Collectors.toCollection(TreeSet::new));

		assertThat(errors).isEqualTo(expectedSet(expected));
	}

	/** 科目IDには、予算額の厳しい変換は効かない（Long の項目）。+5 のような値は、標準の変換で 5 になる（科目IDの確認は Service） */
	@Test
	void strictConversionDoesNotApplyToCategoryId() {
		BudgetForm form = new BudgetForm();
		BindingResult result = bind(BudgetFormTest::registerEditorLikeTheController,
				new String[] { "5" }, new String[] { "100" }, form);

		assertThat(result.hasErrors()).isFalse();
		assertThat(form.getRows().get(0).getCategoryId()).isEqualTo(5L);
	}

	// ---- Q6-7：メッセージのキーが、行の番号なしの形で当たる ----

	@Test
	void messageCodesIncludeTheKeyWithoutRowNumber() {
		BindingResult result = bind(BudgetFormTest::registerEditorLikeTheController,
				new String[] { "100", "101", "102", "103" }, new String[] { "abc", "", "-1", "3000001" }, new BudgetForm());

		assertThat(result.getFieldError("rows[0].amount").getCodes()).contains("typeMismatch.budgetForm.rows.amount");
		assertThat(result.getFieldError("rows[1].amount").getCodes()).contains("NotNull.budgetForm.rows.amount");
		assertThat(result.getFieldError("rows[2].amount").getCodes()).contains("Min.budgetForm.rows.amount");
		assertThat(result.getFieldError("rows[3].amount").getCodes()).contains("Max.budgetForm.rows.amount");
	}

	private static Set<String> expectedSet(String expected) {
		return (expected == null || expected.isEmpty()) ? Set.of() : new TreeSet<>(Set.of(expected.split(",")));
	}

}
