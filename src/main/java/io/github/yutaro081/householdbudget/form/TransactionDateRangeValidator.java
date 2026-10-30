package io.github.yutaro081.householdbudget.form;

import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link TransactionDateRange} の確認。2000-03-01 以上、2100-12-31 以下なら正しい（両端を含む）。
 * DB の CHECK 制約（ck_transactions_date）と同じ範囲。下限の 2000-03-01 は 2000年春の初日で、予実比較・予算設定で見られる季節の範囲（2000年春〜）とそろえた（R15）。
 */
public class TransactionDateRangeValidator implements ConstraintValidator<TransactionDateRange, LocalDate> {

	static final LocalDate MIN = LocalDate.of(2000, 3, 1);
	static final LocalDate MAX = LocalDate.of(2100, 12, 31);

	@Override
	public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		return !value.isBefore(MIN) && !value.isAfter(MAX);
	}

}
