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

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.LimitExceededException;
import io.github.yutaro081.householdbudget.form.CategoryForm;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.CategoryService;
import jakarta.validation.Valid;

/**
 * 科目の一覧・追加・変更・削除の画面と処理（設計書 4.4.1、2.2、2.3、2.6）。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 存在しない・他人の科目のIDは、Service が NotFoundException を投げ、404 の画面になる（GlobalExceptionHandler）。
 * <p>
 * 画面に出す値（科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	/** 科目の一覧（登録順）と、上の追加フォーム。 */
	@GetMapping("/categories")
	public String list(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("categories", categoryService.list(loginUser.getUserId()));
		model.addAttribute("categoryForm", new CategoryForm());
		return "categories/list";
	}

	/**
	 * 科目の追加。入力エラー・重複のときは、リダイレクトせずに一覧を表示し直す
	 * （フォームに入力値とエラーを入れ、一覧は取り直して最新にする、設計書 6.9）。
	 */
	@PostMapping("/categories")
	public String add(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @ModelAttribute("categoryForm") CategoryForm categoryForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		if (!bindingResult.hasErrors()) {
			try {
				categoryService.add(userId, categoryForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.added");
				return "redirect:/categories";
			} catch (LimitExceededException e) {
				bindingResult.rejectValue("name", "error.limit.category");
			} catch (DuplicateException e) {
				bindingResult.rejectValue("name", "error.duplicate.category");
			}
		}
		model.addAttribute("categories", categoryService.list(userId));
		return "categories/list";
	}

	/** 変更画面。今の名前の入ったフォームを表示する。 */
	@GetMapping("/categories/{id}/edit")
	public String showEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		Category category = categoryService.get(loginUser.getUserId(), id);
		model.addAttribute("categoryForm", new CategoryForm(category.getName()));
		model.addAttribute("categoryId", category.getId());
		return "categories/edit";
	}

	/**
	 * 科目の変更。入力エラー・重複のときは、変更画面を表示し直す（入力値を残す）。
	 * 入力エラーのときでも、先に ID と user_id で探して、存在しない・他人の科目なら 404 にする
	 * （入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）。
	 */
	@PostMapping("/categories/{id}/edit")
	public String edit(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			@Valid @ModelAttribute("categoryForm") CategoryForm categoryForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		categoryService.get(userId, id);

		if (!bindingResult.hasErrors()) {
			try {
				categoryService.update(userId, id, categoryForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.updated");
				return "redirect:/categories";
			} catch (DuplicateException e) {
				bindingResult.rejectValue("name", "error.duplicate.category");
			}
		}
		model.addAttribute("categoryId", id);
		return "categories/edit";
	}

	/**
	 * 科目の削除。成功したら一覧へ戻る。削除できないときも、一覧へ戻り、理由を画面の上に表示する
	 * （設計書 2.3、2.6）。確認ダイアログは、画面（ブラウザ）で行う。
	 */
	@PostMapping("/categories/{id}/delete")
	public String delete(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		try {
			categoryService.delete(loginUser.getUserId(), id);
			redirectAttributes.addFlashAttribute("successMessageKey", "success.deleted");
		} catch (DeletionNotAllowedException e) {
			redirectAttributes.addFlashAttribute("errorMessageKey", e.getMessageKey());
		}
		return "redirect:/categories";
	}

}
