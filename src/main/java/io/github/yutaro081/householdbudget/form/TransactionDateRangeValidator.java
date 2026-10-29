package io.github.yutaro081.householdbudget.form;

import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link TransactionDateRange} の確認。2000-01-01 以上、2100-12-31 以下なら正しい（両端を含む）。
 * DB の CHECK 制約（ck_transactions_date）と同じ範囲。
 */
public class TransactionDateRangeValidator implements ConstraintValidator<TransactionDateRange, LocalDate> {

	static final LocalDate MIN = LocalDate.of(2000, 1, 1);
	static final LocalDate MAX = LocalDate.of(2100, 12, 31);

	@Override
	public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		return !value.isBefore(MIN) && !value.isAfter(MAX);
	}

}
