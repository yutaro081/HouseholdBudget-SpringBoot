package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.ComparisonService;

/**
 * 予実比較の表示（設計書 2.2、2.4、4.4、5.9）。表示だけで、データは変えない。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（設計書 3.9、6.1）。
 * 年と季節は URL で受け取る。指定しないときは今の季節。範囲外・不正・片方だけの指定は 404 の画面になる。
 * <p>
 * 画面に出す値（科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
public class ComparisonController {

	private final ComparisonService comparisonService;
	private final Clock clock;

	public ComparisonController(ComparisonService comparisonService, Clock clock) {
		this.comparisonService = comparisonService;
		this.clock = clock;
	}

	/**
	 * 予実比較の画面。年と季節を指定しないときは、今の季節（日本時間）。
	 * 年と季節の判定は、予算設定と同じ SeasonPeriod.resolve（Q7-1）。空なら NotFoundException（404）。
	 */
	@GetMapping("/comparison")
	public String show(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season,
			Model model) {
		SeasonPeriod period = SeasonPeriod.resolve(year, season, LocalDate.now(clock))
				.orElseThrow(NotFoundException::new);
		model.addAttribute("comparison", comparisonService.compare(loginUser.getUserId(), period));
		return "comparison/index";
	}

}
