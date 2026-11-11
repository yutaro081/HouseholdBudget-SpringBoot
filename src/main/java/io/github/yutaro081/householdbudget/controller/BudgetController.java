package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.yutaro081.householdbudget.domain.AutoBudgetSeasons;
import io.github.yutaro081.householdbudget.domain.ProposalChoice;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView;
import io.github.yutaro081.householdbudget.dto.BudgetEditView;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.BudgetForm;
import io.github.yutaro081.householdbudget.form.StrictIntegerEditor;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.AutoBudgetService;
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
@RequestMapping("/budgets")
public class BudgetController {

	private final BudgetService budgetService;
	private final AutoBudgetService autoBudgetService;
	private final Clock clock;

	public BudgetController(BudgetService budgetService, AutoBudgetService autoBudgetService, Clock clock) {
		this.budgetService = budgetService;
		this.autoBudgetService = autoBudgetService;
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
	 * <p>
	 * 任意の auto（issue 019、設計書 5.14）：文字列で受け取り、"1"・"2"・"3" と完全に同じものだけを受け付ける。
	 * ほかは（空・0・4・01・abc・2 つ付き など）404。年と季節の検査のあとに行う。auto の値は画面に出さない。
	 * auto があるときは、自動予算の案を入力欄に入れて表示する（保存はしない）。
	 */
	@GetMapping
	public String show(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season,
			@RequestParam(required = false) String auto,
			Model model) {
		Long userId = loginUser.getUserId();
		SeasonPeriod period = resolve(year, season);
		Optional<ProposalChoice> choice = auto == null ? Optional.empty()
				: Optional.of(ProposalChoice.fromParam(auto).orElseThrow(NotFoundException::new));
		LocalDate today = LocalDate.now(clock);

		BudgetEditView view = budgetService.edit(userId, period);
		boolean hasRows = !view.rows().isEmpty();
		AutoBudgetView proposal = (choice.isPresent() && hasRows)
				? autoBudgetService.propose(userId, period, choice.get(), today)
				: null;

		List<BudgetForm.Row> rows = view.rows().stream()
				.map(row -> new BudgetForm.Row(row.categoryId(), row.amount()))
				.toList();
		if (proposal != null) {
			// 案の額を入力欄に入れる（ルール④の科目は、今の値のまま）
			Map<Long, Long> amounts = proposal.rows().stream()
					.collect(Collectors.toMap(AutoBudgetView.Row::categoryId, AutoBudgetView.Row::amount));
			rows = view.rows().stream()
					.map(row -> new BudgetForm.Row(row.categoryId(), Math.toIntExact(amounts.get(row.categoryId()))))
					.toList();
		}
		long total = rows.stream().mapToLong(BudgetForm.Row::getAmount).sum();

		model.addAttribute("budgetView", view.withAuto(autoAvailable(period, today, hasRows), total, proposal));
		model.addAttribute("budgetForm", new BudgetForm(rows));
		return "budgets/form";
	}

	/** 「自動予算」ボタンを出すか。過去の同じ季節が範囲（2000年春）より前しかないとき、科目が 0 件のときは出さない。 */
	private boolean autoAvailable(SeasonPeriod period, LocalDate today, boolean hasRows) {
		return hasRows && !AutoBudgetSeasons.of(period, today).sameSeasonYears().isEmpty();
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
	@PostMapping
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
			// 表示し直しでは、合計は出さない（欄の値が送られた文字のため。JavaScript が計算する、設計書 2.3）
			model.addAttribute("budgetView",
					view.withAuto(autoAvailable(period, LocalDate.now(clock), !view.rows().isEmpty()), null, null));
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
	 * 判定は SeasonPeriod.resolve（予実比較と共通の部品、Q7-1）。
	 */
	private SeasonPeriod resolve(String year, String season) {
		return SeasonPeriod.resolve(year, season, LocalDate.now(clock)).orElseThrow(NotFoundException::new);
	}

}
