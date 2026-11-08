package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.TopService;

/**
 * トップ（GET /）の表示（設計書 2.2、2.3、5.11、Q010-1）。ログイン後の最初の画面。表示だけで、データは変えない。
 * 以前の HomeController（取引一覧へ移動するだけ）を置き換えた。ログインしていなければ、先にログイン画面へ移動する。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す（設計書 3.9）。
 * 「今日」は日本時間の Clock から1回だけ取る（設計書 B6）。
 * 画面に出す値（科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
public class TopController {

	private final TopService topService;
	private final Clock clock;

	public TopController(TopService topService, Clock clock) {
		this.topService = topService;
		this.clock = clock;
	}

	@GetMapping("/")
	public String show(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("top", topService.top(loginUser.getUserId(), LocalDate.now(clock)));
		return "top/index";
	}

}
