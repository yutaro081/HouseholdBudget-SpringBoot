package io.github.yutaro081.householdbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.form.PaymentMethodForm;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.PaymentMethodService;
import jakarta.validation.Valid;

/**
 * 支払方法の一覧・追加・変更・削除の画面と処理（設計書 4.4.1、2.2、2.3、2.6）。科目（CategoryController）と同じ形。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 存在しない・他人の支払方法のIDは、Service が NotFoundException を投げ、404 の画面になる（GlobalExceptionHandler）。
 * <p>
 * 画面に出す値（支払方法名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
public class PaymentMethodController {

	private final PaymentMethodService paymentMethodService;

	public PaymentMethodController(PaymentMethodService paymentMethodService) {
		this.paymentMethodService = paymentMethodService;
	}

	/** 支払方法の一覧（登録順）と、上の追加フォーム。 */
	@GetMapping("/payment-methods")
	public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("paymentMethods", paymentMethodService.list(loginUser.getUserId()));
		model.addAttribute("paymentMethodForm", new PaymentMethodForm());
		return "payment-methods/list";
	}

	/**
	 * 支払方法の追加。入力エラー・重複のときは、リダイレクトせずに一覧を表示し直す
	 * （フォームに入力値とエラーを入れ、一覧は取り直して最新にする、設計書 6.9）。
	 */
	@PostMapping("/payment-methods")
	public String add(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @ModelAttribute("paymentMethodForm") PaymentMethodForm paymentMethodForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		if (!bindingResult.hasErrors()) {
			try {
				paymentMethodService.add(userId, paymentMethodForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.added");
				return "redirect:/payment-methods";
			} catch (DuplicateException e) {
				bindingResult.rejectValue("name", "error.duplicate.paymentMethod");
			}
		}
		model.addAttribute("paymentMethods", paymentMethodService.list(userId));
		return "payment-methods/list";
	}

	/** 変更画面。今の名前の入ったフォームを表示する。 */
	@GetMapping("/payment-methods/{id}/edit")
	public String showEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		PaymentMethod paymentMethod = paymentMethodService.get(loginUser.getUserId(), id);
		model.addAttribute("paymentMethodForm", new PaymentMethodForm(paymentMethod.getName()));
		model.addAttribute("paymentMethodId", paymentMethod.getId());
		return "payment-methods/edit";
	}

	/**
	 * 支払方法の変更。入力エラー・重複のときは、変更画面を表示し直す（入力値を残す）。
	 * 入力エラーのときでも、先に ID と user_id で探して、存在しない・他人の支払方法なら 404 にする
	 * （入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）。
	 */
	@PostMapping("/payment-methods/{id}/edit")
	public String edit(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			@Valid @ModelAttribute("paymentMethodForm") PaymentMethodForm paymentMethodForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		paymentMethodService.get(userId, id);

		if (!bindingResult.hasErrors()) {
			try {
				paymentMethodService.update(userId, id, paymentMethodForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.updated");
				return "redirect:/payment-methods";
			} catch (DuplicateException e) {
				bindingResult.rejectValue("name", "error.duplicate.paymentMethod");
			}
		}
		model.addAttribute("paymentMethodId", id);
		return "payment-methods/edit";
	}

	/**
	 * 支払方法の削除。成功したら一覧へ戻る。削除できないときも、一覧へ戻り、理由を画面の上に表示する
	 * （設計書 2.3、2.6）。確認ダイアログは、画面（ブラウザ）で行う。
	 */
	@PostMapping("/payment-methods/{id}/delete")
	public String delete(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		try {
			paymentMethodService.delete(loginUser.getUserId(), id);
			redirectAttributes.addFlashAttribute("successMessageKey", "success.deleted");
		} catch (DeletionNotAllowedException e) {
			redirectAttributes.addFlashAttribute("errorMessageKey", e.getMessageKey());
		}
		return "redirect:/payment-methods";
	}

}
