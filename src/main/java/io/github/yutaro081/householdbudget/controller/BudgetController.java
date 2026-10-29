package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.BudgetEditView;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.BudgetForm;
import io.github.yutaro081.householdbudget.form.StrictIntegerEditor;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.BudgetService;
import jakarta.validation.Valid;

/**
 * 予算設定の表示と、まとめての保存（設計書 4.4.1、2.2、2.3、2.4、2.6、5.7）。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 年と季節は URL で受け取る（Form には持たせない）。範囲外・不正な指定、他人・存在しない科目IDは、404 の画面になる。
 * <p>
 * 画面に出す値（科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
public class BudgetController {

	private final BudgetService budgetService;
	private final Clock clock;

	public BudgetController(BudgetService budgetService, Clock clock) {
		this.budgetService = budgetService;
		this.clock = clock;
	}

	/**
	 * 予算額に、厳しい変換を付ける（設計書 6.5、Q5-1 の C、Q6-1 の A）。
	 * 行ごとの金額の名前は rows[0].amount、rows[1].amount、… なので、行の番号を除いた名前（rows.amount）で登録すると、全行に効く。
	 * （行の番号つきで登録すると、その行にしか効かない。BudgetFormTest で確かめた）
	 */
	@InitBinder("budgetForm")
	void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(Integer.class, "rows.amount", new StrictIntegerEditor());
	}

	/**
	 * 予算設定画面。年と季節を指定しないときは、今の季節（日本時間）。指定が範囲外・不正・片方だけなら 404。
	 * 開いただけでは、データベースに予算の行を作らない（設計書 5.7）。
	 */
	@GetMapping("/budgets")
	public String show(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season,
			Model model) {
		SeasonPeriod period = resolve(year, season);
		BudgetEditView view = budgetService.edit(loginUser.getUserId(), period);

		List<BudgetForm.Row> rows = view.rows().stream()
				.map(row -> new BudgetForm.Row(row.categoryId(), row.amount()))
				.toList();
		model.addAttribute("budgetView", view);
		model.addAttribute("budgetForm", new BudgetForm(rows));
		return "budgets/form";
	}

	/**
	 * 予算のまとめての保存。順番は、年と季節の確認 → 科目IDの確認 → 入力チェックの結果 → 保存（設計書 5.7、Q6-3）。
	 * <ul>
	 * <li>年と季節が不正なら 404（何も保存しない）</li>
	 * <li>送られた科目IDが、自分の科目でない・存在しない・重複・数字でない・空なら 404（何も保存しない）。
	 * 入力チェックの結果を見る前に確かめる（入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）</li>
	 * <li>予算額にエラーが1つでもあれば、何も保存せず、送られた順のまま、入力値を残して表示し直す</li>
	 * <li>すべて正しければ、まとめて保存して、同じ季節の予算設定画面へリダイレクトする（「保存しました」、U4）</li>
	 * </ul>
	 */
	@PostMapping("/budgets")
	public String save(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season,
			@Valid @ModelAttribute("budgetForm") BudgetForm budgetForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		SeasonPeriod period = resolve(year, season);

		// 科目IDの確認が先（だめなら 404）。表示し直すための行（科目名と印）も、ここで作る
		BudgetEditView view = budgetService.rowsOf(userId, period, budgetForm);

		if (bindingResult.hasErrors()) {
			model.addAttribute("budgetView", view);
			return "budgets/form";
		}

		budgetService.save(userId, period, budgetForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.saved");
		redirectAttributes.addAttribute("year", period.year());
		redirectAttributes.addAttribute("season", period.season().getUrlName());
		return "redirect:/budgets";
	}

	/**
	 * URL の年と季節から、季節を決める（設計書 2.4、Q6-2 の A）。
	 * 両方とも指定されていなければ、今の季節。それ以外で、不正・片方だけ・空文字・範囲外なら NotFoundException（404）。
	 */
	private SeasonPeriod resolve(String year, String season) {
		if (year == null && season == null) {
			return SeasonPeriod.from(LocalDate.now(clock));
		}
		return SeasonPeriod.parse(year, season).orElseThrow(NotFoundException::new);
	}

}
