package io.github.yutaro081.householdbudget.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 【仮】Phase 2 の確認用の Controller。Phase 3 で、本物の HomeController
 * （GET / を取引一覧へ移動する、設計書 4.4.1）に置き換える。
 * messages.properties の日本語が文字化けしないか（C2）を、画面で確かめるためだけに使う。
 */
@Controller
public class TemporaryHomeController {

	@GetMapping("/")
	public String index() {
		return "temporary";
	}

}
