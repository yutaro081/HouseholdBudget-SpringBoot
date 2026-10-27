package io.github.yutaro081.householdbudget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

/**
 * messages.properties の、科目・取引先・支払方法のメッセージが、設計書 6.2、2.6 の文のとおりかの確認。
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
			"success.deleted|削除しました"
	})
	void messageText(String key, String expected) {
		assertThat(messageSource.getMessage(key, null, Locale.JAPAN)).isEqualTo(expected);
	}

}
