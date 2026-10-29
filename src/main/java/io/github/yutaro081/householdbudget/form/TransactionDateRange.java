package io.github.yutaro081.householdbudget.form;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 取引の日付が、2000年1月1日から2100年12月31日までであること（設計書 6.6、R12、G4、P9、Q5-2 の A）。
 * 値がない（null）ときは確かめない。「必須」は @NotNull が受け持つ（D4 の B：必須を他のエラーと重ねない）。
 * 画面に出す文は、messages.properties の TransactionDateRange.transactionForm.transactionDate。
 */
@Documented
@Constraint(validatedBy = TransactionDateRangeValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface TransactionDateRange {

	/** 画面では使われない（messages.properties のキーが先に探される）。単体テストなどで文が必要なときの代わり。 */
	String message() default "transaction date is out of range";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
