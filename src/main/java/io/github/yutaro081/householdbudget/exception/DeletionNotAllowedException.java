package io.github.yutaro081.householdbudget.exception;

/**
 * 科目・取引先・支払方法を削除できないときの例外（設計書 4.5、5.5、要件4.3）。
 * 取引で使われている、または（科目の場合）いずれかの季節に0円でない予算がある。
 * 理由を表す、messages.properties のキー（error.delete.inUse など）を持つ。
 * Controller が受けて、一覧へ戻り、理由を画面に表示する。
 */
public class DeletionNotAllowedException extends RuntimeException {

	private final String messageKey;

	public DeletionNotAllowedException(String messageKey) {
		super(messageKey);
		this.messageKey = messageKey;
	}

	public DeletionNotAllowedException(String messageKey, Throwable cause) {
		super(messageKey, cause);
		this.messageKey = messageKey;
	}

	/** 理由を表す messages.properties のキー。 */
	public String getMessageKey() {
		return messageKey;
	}

}
