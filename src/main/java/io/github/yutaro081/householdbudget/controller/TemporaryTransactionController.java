package io.github.yutaro081.householdbudget.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 【仮】Phase 3 の取引一覧（Q3-1 の A）。Phase 5 で、本物の TransactionController に置き換える。
 * ログイン成功後の移動先（/transactions、U5）が、取引一覧がまだ無くても開けるようにするためだけに使う。
 * 画面の中身は、共通の枠とユーザー名だけ。
 */
@Controller
public class TemporaryTransactionController {

	@GetMapping("/transactions")
	public String list() {
		return "transactions/list";
	}

}
