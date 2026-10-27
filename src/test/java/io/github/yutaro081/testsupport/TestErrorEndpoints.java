package io.github.yutaro081.testsupport;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import io.github.yutaro081.householdbudget.exception.NotFoundException;

/**
 * 【テスト用】GlobalExceptionHandlerTest が使う、テスト用の URL（/test-handler/…）。
 * まだ NotFoundException を投げる本物の画面がないので、用意した。
 * アプリのコンポーネントスキャンの範囲（io.github.yutaro081.householdbudget の下）の外に置き、
 * テストが明示的に読み込んだときだけ有効になるようにしている（他のテストや本物のアプリには入らない）。
 */
@Controller
@RequestMapping("/test-handler")
public class TestErrorEndpoints {

	/** 存在しない・他人のデータ */
	@GetMapping("/not-found")
	public String notFound() {
		throw new NotFoundException();
	}

	/** ID が Long の URL。数字なら普通に表示される（比べるための対照） */
	@GetMapping("/ids/{id}")
	public String ids(@PathVariable Long id) {
		return "login";
	}

}
