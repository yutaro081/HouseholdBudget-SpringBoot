package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

/**
 * messages.properties の、科目・取引先・支払方法・取引・予算のメッセージが、設計書 6.2、2.6 の文のとおりかの確認。
 * キーの綴り違いや、文字化けに気づくためのテスト。
 */
@SpringBootTest
class MessagesTest {

	@Autowired
	private MessageSource messageSource;

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"NotBlank.categoryForm.name|科目名を入力してください",
			"Size.categoryForm.name|科目名は10文字以内で入力してください",
			"NotBlank.vendorForm.name|取引先名を入力してください",
			"Size.vendorForm.name|取引先名は20文字以内で入力してください",
			"NotBlank.paymentMethodForm.name|支払方法名を入力してください",
			"Size.paymentMethodForm.name|支払方法名は10文字以内で入力してください",
			"error.duplicate.category|その科目名はすでにあります",
			"error.duplicate.vendor|その取引先名はすでにあります",
			"error.duplicate.paymentMethod|その支払方法名はすでにあります",
			"error.delete.inUse|取引で使われているため削除できません",
			"error.delete.budgetNotZero|予算が0円ではない季節があるため削除できません",
			"success.added|追加しました",
			"success.updated|変更しました",
			"success.deleted|削除しました",
			"NotNull.transactionForm.transactionDate|日付を入力してください",
			"typeMismatch.transactionForm.transactionDate|日付を正しく入力してください",
			"TransactionDateRange.transactionForm.transactionDate|日付は2000年1月1日から2100年12月31日までで入力してください",
			"NotNull.transactionForm.amount|金額を入力してください",
			"typeMismatch.transactionForm.amount|金額は整数で入力してください",
			"Min.transactionForm.amount|金額は1円以上1,000,000円以下で入力してください",
			"Max.transactionForm.amount|金額は1円以上1,000,000円以下で入力してください",
			"NotNull.transactionForm.categoryId|科目を選択してください",
			"typeMismatch.transactionForm.categoryId|科目を選択してください",
			"NotNull.transactionForm.vendorId|取引先を選択してください",
			"typeMismatch.transactionForm.vendorId|取引先を選択してください",
			"NotNull.transactionForm.paymentMethodId|支払方法を選択してください",
			"typeMismatch.transactionForm.paymentMethodId|支払方法を選択してください",
			"Size.transactionForm.description|摘要は30文字以内で入力してください",
			"success.registered|登録しました",
			"success.transactionUpdated|更新しました",
			"NotNull.budgetForm.rows.amount|予算額を入力してください",
			"typeMismatch.budgetForm.rows.amount|予算額は整数で入力してください",
			"Min.budgetForm.rows.amount|予算額は0円以上3,000,000円以下で入力してください",
			"Max.budgetForm.rows.amount|予算額は0円以上3,000,000円以下で入力してください",
			"success.saved|保存しました",
			"season.spring|春",
			"season.summer|夏",
			"season.autumn|秋",
			"season.winter|冬",
			"error.limit.category|科目は256個までです",
			"error.limit.vendor|取引先は256個までです",
			"error.limit.paymentMethod|支払方法は256個までです"
	})
	void messageText(String key, String expected) {
		assertThat(messageSource.getMessage(key, null, Locale.JAPAN)).isEqualTo(expected);
	}

}
