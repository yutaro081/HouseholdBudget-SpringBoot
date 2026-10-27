package io.github.yutaro081.householdbudget.exception;

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
 * </ul>
 * DuplicateException と DeletionNotAllowedException は、ここでは扱わない。
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

}
