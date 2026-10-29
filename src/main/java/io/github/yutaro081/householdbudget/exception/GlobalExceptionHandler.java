package io.github.yutaro081.householdbudget.exception;

import org.springframework.beans.InvalidPropertyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 例外を、画面と状態コードに変える（設計書 4.5、7.7、7.8）。
 * <ul>
 * <li>NotFoundException（存在しない・他人のデータ）→ 404 の画面、状態コードも 404（B7）</li>
 * <li>ID が数字でない URL（例：/categories/abc）、数値の範囲を超える ID → 同じ 404 の画面と状態コード（S4）</li>
 * <li>送信の形がおかしくて、入力を Form に束縛できない（InvalidPropertyException。例：行の番号を書き換えた
 * rows[-1]、rows[abc]、257行目以降）→ 400 の画面（error/4xx）、状態コードも 400（R14、issue 002）。
 * 500 はサーバーの不具合、400 は送信の形がおかしい、という意味で、区別するため。何も保存されない。</li>
 * </ul>
 * DuplicateException、LimitExceededException、DeletionNotAllowedException は、ここでは扱わない。
 * Controller が受けて、画面の動き（入力エラーとして表示し直す／一覧へ戻って理由を表示する）に変える（5.1）。
 * <p>
 * 画面にも、ログにも、他人のデータの有無が分かる情報（ID など）を出さない（7.8）。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleNotFound() {
		return "error/404";
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleIdTypeMismatch() {
		return "error/404";
	}

	/** 入力の束縛に失敗した（送信の形がおかしい）。500 ではなく 400。画面にも、ログにも、詳しい中身は出さない（7.7）。 */
	@ExceptionHandler(InvalidPropertyException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleBindingFailure() {
		return "error/4xx";
	}

}
