package io.github.yutaro081.householdbudget.form;

import java.util.List;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;

/**
 * 取引のフォーム（TransactionForm）を束縛するときの設定（設計書 6.2、6.5。issue 032）。
 * 本番は TransactionController の {@code @InitBinder("transactionForm")} から呼ぶ（transactionForm という名前のフォームだけに効く）。
 * 単体テストも同じ設定を使う（本番と食い違わないように）。
 * <ul>
 * <li>明細の行は 5 行まで束縛する（6 行目以降、行の番号の書き換えは、束縛できずに InvalidPropertyException → 400。GlobalExceptionHandler）。</li>
 * <li>行の金額（lines.amount。行の番号を除いた名前で、全部の行に効く）を、StrictIntegerEditor で読む（6.5、R13）。</li>
 * <li>空の行の扱いの Validator を「足す」（addValidators）。注釈の入力チェック（Bean Validation）は置きかえず、そのまま動く。</li>
 * </ul>
 */
public final class TransactionFormBinding {

	private TransactionFormBinding() {
	}

	public static void configure(WebDataBinder binder) {
		binder.setAutoGrowCollectionLimit(TransactionForm.MAX_LINES);
		binder.registerCustomEditor(Integer.class, "lines.amount", new StrictIntegerEditor());
		binder.addValidators(new LinesValidator());
	}

	/**
	 * 空の行は無視し、空でない行だけ、科目と金額を必須にする。空でない行が 1 つもなければ、lines にエラー。
	 * <p>
	 * 束縛のあとに動くので、型変換のエラー（金額の abc など）が見える。型変換のエラーがある行は、値が null でも、空の行として扱わない。
	 * すでにエラーのある項目には、必須のエラーを重ねない（1 項目に 1 つ。G3、G7）。
	 */
	static final class LinesValidator implements Validator {

		@Override
		public boolean supports(Class<?> clazz) {
			return TransactionForm.class.isAssignableFrom(clazz);
		}

		@Override
		public void validate(Object target, Errors errors) {
			List<TransactionForm.Line> lines = ((TransactionForm) target).getLines();
			int filled = 0;
			for (int i = 0; i < lines.size(); i++) {
				TransactionForm.Line line = lines.get(i);
				String category = "lines[" + i + "].categoryId";
				String amount = "lines[" + i + "].amount";
				boolean hasBindingError = errors.hasFieldErrors(category) || errors.hasFieldErrors(amount);
				if ((line == null || line.isBlank()) && !hasBindingError) {
					continue;
				}
				filled++;
				if ((line == null || line.getCategoryId() == null) && !errors.hasFieldErrors(category)) {
					errors.rejectValue(category, "NotNull");
				}
				if ((line == null || line.getAmount() == null) && !errors.hasFieldErrors(amount)) {
					errors.rejectValue(amount, "NotNull");
				}
			}
			if (filled == 0) {
				errors.rejectValue("lines", "NotEmpty");
			}
		}

	}

}
