package io.github.yutaro081.householdbudget.controller;

import java.time.Clock;
import java.time.LocalDate;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.form.StrictIntegerEditor;
import io.github.yutaro081.householdbudget.form.TransactionForm;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.CategoryService;
import io.github.yutaro081.householdbudget.service.PaymentMethodService;
import io.github.yutaro081.householdbudget.service.TransactionService;
import io.github.yutaro081.householdbudget.service.VendorService;
import jakarta.validation.Valid;

/**
 * 取引の一覧・登録・詳細・編集・削除の画面と処理（設計書 4.4.1、2.2、2.3、2.6）。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 存在しない・他人の取引のIDや、他人の科目・取引先・支払方法のIDは、Service が NotFoundException を投げ、
 * 404 の画面になる（GlobalExceptionHandler）。
 * <p>
 * 画面に出す値（摘要、科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
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
	 * 金額の項目にだけ、厳しい変換を付ける（設計書 6.5、Q5-1 の C）。
	 * 全角の数字は半角に直して受け付ける（R13）。途中の空白・16進・カンマ・小数などは、型変換のエラーになる。科目ID など、他の整数の項目には付けない。
	 */
	@InitBinder("transactionForm")
	void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(Integer.class, "amount", new StrictIntegerEditor());
	}

	/** 取引の一覧（日付の新しい順、同じ日は登録の新しい順）。 */
	@GetMapping("/transactions")
	public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("transactions", transactionService.list(loginUser.getUserId()));
		return "transactions/list";
	}

	/** 登録フォーム。日付の最初の値は今日（U7）。 */
	@GetMapping("/transactions/new")
	public String showNew(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		TransactionForm form = new TransactionForm();
		form.setTransactionDate(LocalDate.now(clock));
		model.addAttribute("transactionForm", form);
		addFormModel(model, loginUser.getUserId(), null);
		return "transactions/form";
	}

	/**
	 * 取引の登録。入力エラーのときは、リダイレクトせずにフォームを表示し直す
	 * （入力値を残し、プルダウンの選択肢を取り直して、選んでいた値を残す、設計書 6.7、6.9）。
	 */
	@PostMapping("/transactions/new")
	public String register(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @ModelAttribute("transactionForm") TransactionForm transactionForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		if (bindingResult.hasErrors()) {
			addFormModel(model, userId, null);
			return "transactions/form";
		}
		transactionService.register(userId, transactionForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.registered");
		return "redirect:/transactions";
	}

	/** 取引の詳細。編集・削除のボタンは、ここにだけ置く（U3）。 */
	@GetMapping("/transactions/{id}")
	public String detail(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		model.addAttribute("transaction", transactionService.get(loginUser.getUserId(), id));
		return "transactions/detail";
	}

	/** 編集画面。登録済みの値の入ったフォームを表示する。 */
	@GetMapping("/transactions/{id}/edit")
	public String showEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		Long userId = loginUser.getUserId();
		Transaction transaction = transactionService.get(userId, id);
		model.addAttribute("transactionForm", new TransactionForm(transaction));
		addFormModel(model, userId, transaction.getId());
		return "transactions/form";
	}

	/**
	 * 取引の更新。入力エラーのときは、編集画面を表示し直す（入力値を残す）。
	 * 入力エラーのときでも、先に ID と user_id で探して、存在しない・他人の取引なら 404 にする
	 * （入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）。
	 */
	@PostMapping("/transactions/{id}/edit")
	public String update(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			@Valid @ModelAttribute("transactionForm") TransactionForm transactionForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		transactionService.get(userId, id);

		if (bindingResult.hasErrors()) {
			addFormModel(model, userId, id);
			return "transactions/form";
		}
		transactionService.update(userId, id, transactionForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.transactionUpdated");
		return "redirect:/transactions";
	}

	/** 取引の削除。成功したら一覧へ戻る。確認ダイアログは、画面（ブラウザ）で行う。 */
	@PostMapping("/transactions/{id}/delete")
	public String delete(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		transactionService.delete(loginUser.getUserId(), id);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.deleted");
		return "redirect:/transactions";
	}

	/**
	 * 登録・編集のフォームの表示に必要なものを、model に入れる（表示と、入力エラーの表示し直しで共通）。
	 * プルダウンの選択肢は、ログイン中のユーザー自身のものだけを、登録順に並べる（設計書 6.7、Q14）。
	 *
	 * @param transactionId 編集のときは取引のID、登録のときは null
	 */
	private void addFormModel(Model model, Long userId, Long transactionId) {
		model.addAttribute("categories", categoryService.list(userId));
		model.addAttribute("vendors", vendorService.list(userId));
		model.addAttribute("paymentMethods", paymentMethodService.list(userId));
		model.addAttribute("transactionId", transactionId);
		model.addAttribute("pageTitle", transactionId == null ? "取引の登録" : "取引の編集");
	}

}
