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

import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.LimitExceededException;
import io.github.yutaro081.householdbudget.form.VendorForm;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.VendorService;
import jakarta.validation.Valid;

/**
 * 取引先の一覧・追加・変更・削除の画面と処理（設計書 4.4.1、2.2、2.3、2.6）。科目（CategoryController）と同じ形。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 存在しない・他人の取引先のIDは、Service が NotFoundException を投げ、404 の画面になる（GlobalExceptionHandler）。
 * <p>
 * 画面に出す値（取引先名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
public class VendorController {

	private final VendorService vendorService;

	public VendorController(VendorService vendorService) {
		this.vendorService = vendorService;
	}

	/** 取引先の一覧（登録順）と、上の追加フォーム。 */
	@GetMapping("/vendors")
	public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("vendors", vendorService.list(loginUser.getUserId()));
		model.addAttribute("vendorForm", new VendorForm());
		return "vendors/list";
	}

	/**
	 * 取引先の追加。入力エラー・重複のときは、リダイレクトせずに一覧を表示し直す
	 * （フォームに入力値とエラーを入れ、一覧は取り直して最新にする、設計書 6.9）。
	 */
	@PostMapping("/vendors")
	public String add(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @ModelAttribute("vendorForm") VendorForm vendorForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		if (!bindingResult.hasErrors()) {
			try {
				vendorService.add(userId, vendorForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.added");
				return "redirect:/vendors";
			} catch (LimitExceededException e) {
				bindingResult.rejectValue("name", "error.limit.vendor");
			} catch (DuplicateException e) {
				bindingResult.rejectValue("name", "error.duplicate.vendor");
			}
		}
		model.addAttribute("vendors", vendorService.list(userId));
		return "vendors/list";
	}

	/** 変更画面。今の名前の入ったフォームを表示する。 */
	@GetMapping("/vendors/{id}/edit")
	public String showEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		Vendor vendor = vendorService.get(loginUser.getUserId(), id);
		model.addAttribute("vendorForm", new VendorForm(vendor.getName()));
		model.addAttribute("vendorId", vendor.getId());
		return "vendors/edit";
	}

	/**
	 * 取引先の変更。入力エラー・重複のときは、変更画面を表示し直す（入力値を残す）。
	 * 入力エラーのときでも、先に ID と user_id で探して、存在しない・他人の取引先なら 404 にする
	 * （入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）。
	 */
	@PostMapping("/vendors/{id}/edit")
	public String edit(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			@Valid @ModelAttribute("vendorForm") VendorForm vendorForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		vendorService.get(userId, id);

		if (!bindingResult.hasErrors()) {
			try {
				vendorService.update(userId, id, vendorForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.updated");
				return "redirect:/vendors";
			} catch (DuplicateException e) {
				bindingResult.rejectValue("name", "error.duplicate.vendor");
			}
		}
		model.addAttribute("vendorId", id);
		return "vendors/edit";
	}

	/**
	 * 取引先の削除。成功したら一覧へ戻る。削除できないときも、一覧へ戻り、理由を画面の上に表示する
	 * （設計書 2.3、2.6）。確認ダイアログは、画面（ブラウザ）で行う。
	 */
	@PostMapping("/vendors/{id}/delete")
	public String delete(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		try {
			vendorService.delete(loginUser.getUserId(), id);
			redirectAttributes.addFlashAttribute("successMessageKey", "success.deleted");
		} catch (DeletionNotAllowedException e) {
			redirectAttributes.addFlashAttribute("errorMessageKey", e.getMessageKey());
		}
		return "redirect:/vendors";
	}

}
