package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.yutaro081.householdbudget.domain.LandingForecast;
import io.github.yutaro081.householdbudget.domain.PeriodProgress;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ComparisonResult;
import io.github.yutaro081.householdbudget.dto.ComparisonRow;
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
@RequestMapping("/comparison")
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
	@GetMapping
	public String show(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season,
			Model model) {
		SeasonPeriod period = SeasonPeriod.resolve(year, season, LocalDate.now(clock))
				.orElseThrow(NotFoundException::new);
		ComparisonResult comparison = comparisonService.compare(loginUser.getUserId(), period);
		model.addAttribute("comparison", comparison);
		LocalDate today = LocalDate.now(clock);
		LocalDate countFrom = comparisonService.countingStart(loginUser.getUserId(), period, today);
		// 着地見込み（issue 018、020）：合計行の予算・実績から。今の季節だけ。予算が未設定・0円のときは出さない（null）。
		// 数え始めの日は、最初の取引の日が季節の途中にあるときだけ、その日（設計書 5.13）
		Optional<LandingForecast.Result> landing = LandingForecast.of(comparison.total().budget(), comparison.total().actual(), period, today, countFrom);
		model.addAttribute("landing", landing.orElse(null));
		// 科目ごとの見込みの列（issue 020）：今の季節だけ。行と同じ並び。見込みを出さない科目（予算が未設定・0円）は null
		boolean showForecast = period.equals(SeasonPeriod.from(today));
		model.addAttribute("showForecast", showForecast);
		List<LandingForecast.Result> rowLandings = new ArrayList<>();
		for (ComparisonRow row : comparison.rows()) {
			rowLandings.add(showForecast ? LandingForecast.of(row.budget(), row.actual(), period, today, countFrom).orElse(null) : null);
		}
		model.addAttribute("rowLandings", rowLandings);
		// 要約カードの棒と文（トップの季節のメーターと同じ値の求め方。PeriodProgress）
		if (landing.isPresent()) {
			model.addAttribute("cardUsedPercent", PeriodProgress.usedPercent(comparison.total().budget(), comparison.total().actual()));
			model.addAttribute("cardElapsedPercent", PeriodProgress.seasonElapsedPercent(period, today));
			model.addAttribute("cardBarPermille", PeriodProgress.barPermille(comparison.total().budget(), comparison.total().actual()));
			model.addAttribute("cardTodayPermille", PeriodProgress.seasonElapsedPermille(period, today));
			// 最初の取引の日から数えたとき（その日が季節の初日より後のとき）だけ、カードに知らせる
			model.addAttribute("countingFrom", countFrom.isAfter(period.startDate()) ? countFrom : null);
		}
		return "comparison/index";
	}

}
