package io.github.yutaro081.householdbudget.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** トップ（GET /）を取引一覧へ移動する（設計書 4.4.1、Q12）。ログインしていなければ、先にログイン画面へ移動する。 */
@Controller
public class HomeController {

	@GetMapping("/")
	public String index() {
		return "redirect:/transactions";
	}

}
