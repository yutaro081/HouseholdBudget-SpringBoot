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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.yutaro081.householdbudget.dto.TransactionMonth;
import io.github.yutaro081.householdbudget.form.TransactionForm;
import io.github.yutaro081.householdbudget.form.TransactionFormBinding;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.CategoryService;
import io.github.yutaro081.householdbudget.service.PaymentMethodService;
import io.github.yutaro081.householdbudget.service.TransactionService;
import io.github.yutaro081.householdbudget.service.VendorService;
import jakarta.validation.Valid;

/**
 * 取引の一覧・登録・編集・複写・削除の画面と処理（設計書 4.4.1、2.2、2.3、2.6）。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 存在しない・他人の取引のIDや、他人の科目・取引先・支払方法のIDは、Service が NotFoundException を投げ、
 * 404 の画面になる（GlobalExceptionHandler）。
 * <p>
 * 画面に出す値（摘要、科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
@RequestMapping("/transactions")
public class TransactionController {

	private final TransactionService transactionService;
	private final CategoryService categoryService;
	private final VendorService vendorService;
	private final PaymentMethodService paymentMethodService;
	private final Clock clock;

	public TransactionController(TransactionService transactionService,
			CategoryService categoryService,
			VendorService vendorService,
			PaymentMethodService paymentMethodService,
			Clock clock) {
		this.transactionService = transactionService;
		this.categoryService = categoryService;
		this.vendorService = vendorService;
		this.paymentMethodService = paymentMethodService;
		this.clock = clock;
	}

	/**
	 * transactionForm という名前のフォームだけの設定（ほかのフォームには効かない。issue 032。設計書 6.2、6.5）。
	 * <ul>
	 * <li>明細の行の金額にだけ、厳しい変換を付ける（lines.amount。Q5-1 の C、R13）。科目ID など、他の整数の項目には付けない。</li>
	 * <li>明細の行は 5 行まで（6 行目以降は 400）。</li>
	 * <li>空の行の扱いの Validator を「足す」（addValidators）。注釈の入力チェック（@Valid）は、そのまま動く。</li>
	 * </ul>
	 */
	@InitBinder("transactionForm")
	void initBinder(WebDataBinder binder) {
		TransactionFormBinding.configure(binder);
	}

	/** 取引の一覧（日付の新しい順、同じ日は登録の新しい順）。 */
	@GetMapping
	public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		// 取引の検索は 1 回だけ（月ごとのまとまり。月の見出しの件数と合計は、Service が計算する。issue 025）。
		// 「○ 件を表示しています」の全件数は、月のまとまりの件数を足して、ここで出す（テンプレートでは足し算しない）。「取引がまだない」の判定は、まとまりが空かどうか
		List<TransactionMonth> months = transactionService.listByMonth(loginUser.getUserId());
		model.addAttribute("months", months);
		model.addAttribute("transactionCount", months.stream().mapToInt(TransactionMonth::count).sum());
		return "transactions/list";
	}

	/**
	 * 登録フォーム。日付の最初の値は今日（U7）。
	 * <p>
	 * 明細の行は、空の 5 行（issue 032）。
	 * {@code copyFrom}（写す元の取引のID）があるときは、その取引の取引先・支払方法と、明細の行に科目・金額・摘要を入れた状態で表示する
	 * （取引の複写、issue 012、U8。同じレシートの取引なら全部の行。issue 032）。日付だけは今日。GET なので、データベースには何も書かない。
	 * 写す元は、ID とログイン中のユーザーの両方で探す（getReceipt。他人・存在しない取引は NotFoundException で 404）。
	 * 数字でない・範囲外の値は型変換のエラーになり、GlobalExceptionHandler が 404 にする。空（{@code ?copyFrom=}）は「指定なし」。
	 */
	@GetMapping("/new")
	public String showNew(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(name = "copyFrom", required = false) Long copyFrom,
			Model model) {
		TransactionForm form;
		if (copyFrom == null) {
			form = TransactionForm.forNew();
		} else {
			form = TransactionForm.copyOf(transactionService.getReceipt(loginUser.getUserId(), copyFrom));
			model.addAttribute("copied", true);
		}
		form.setTransactionDate(LocalDate.now(clock));
		model.addAttribute("transactionForm", form);
		addFormModel(model, loginUser.getUserId(), null);
		return "transactions/form";
	}

	/**
	 * 取引の登録。空でない明細の行ごとに 1 件の取引にする（issue 032）。入力エラーのときは、リダイレクトせずにフォームを表示し直す
	 * （入力値を残し、明細は 5 行にそろえ、プルダウンの選択肢を取り直して、選んでいた値を残す、設計書 6.7、6.9）。
	 */
	@PostMapping("/new")
	public String register(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @ModelAttribute("transactionForm") TransactionForm transactionForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		if (bindingResult.hasErrors()) {
			transactionForm.fillLinesUpTo(TransactionForm.MAX_LINES);
			addFormModel(model, userId, null);
			return "transactions/form";
		}
		transactionService.register(userId, transactionForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.transactionRegistered");
		return "redirect:/transactions";
	}

	/**
	 * 編集画面。登録済みの値の入ったフォームを表示する。{@code id} は、同じレシートのどの行でもよく、
	 * そのレシートの全部の行を明細の行に入れる（残りは空の行で 5 行。issue 032 の段階 3）。
	 */
	@GetMapping("/{id}/edit")
	public String showEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		Long userId = loginUser.getUserId();
		model.addAttribute("transactionForm", TransactionForm.forEdit(transactionService.getReceipt(userId, id)));
		addFormModel(model, userId, id);
		return "transactions/form";
	}

	/**
	 * 取引の更新。入力エラーのときは、編集画面を表示し直す（入力値を残す）。
	 * 入力エラーのときでも、先に ID と user_id で探して、存在しない・他人の取引なら 404 にする
	 * （入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）。
	 * 同じレシートの取引は、まとめて更新する（行を足す・消す・直す。5 行まで。6 行目以降は束縛できずに 400。issue 032 の段階 3）。
	 */
	@PostMapping("/{id}/edit")
	public String update(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			@Valid @ModelAttribute("transactionForm") TransactionForm transactionForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		transactionService.get(userId, id);

		if (bindingResult.hasErrors()) {
			transactionForm.fillLinesUpTo(TransactionForm.MAX_LINES);
			addFormModel(model, userId, id);
			return "transactions/form";
		}
		transactionService.update(userId, id, transactionForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.transactionUpdated");
		return "redirect:/transactions";
	}

	/** 取引の削除。同じレシートの取引は、まとめて削除する（issue 032）。成功したら一覧へ戻る。確認ダイアログは、画面（ブラウザ）で行う。 */
	@PostMapping("/{id}/delete")
	public String delete(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		transactionService.delete(loginUser.getUserId(), id);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.deleted");
		return "redirect:/transactions";
	}

	/**
	 * 登録・編集のフォームの表示に必要なものを、model に入れる（表示と、入力エラーの表示し直しで共通）。
	 * プルダウンの選択肢は、ログイン中のユーザー自身のものだけを並べる（設計書 6.7）。科目は登録順（Q14）。
	 * 取引先・支払方法は、取引の件数が多い順、同じなら登録順（要件定義書 4.2、issue 029）。選んでいる値は th:field が id で決めるので、並びが変わっても選ばれたまま。
	 *
	 * @param transactionId 編集のときは取引のID、登録のときは null
	 */
	private void addFormModel(Model model, Long userId, Long transactionId) {
		model.addAttribute("categories", categoryService.list(userId));
		model.addAttribute("vendors", vendorService.listForChoices(userId));
		model.addAttribute("paymentMethods", paymentMethodService.listForChoices(userId));
		model.addAttribute("transactionId", transactionId);
		model.addAttribute("pageTitle", transactionId == null ? "取引の登録" : "取引の編集");
	}

}
