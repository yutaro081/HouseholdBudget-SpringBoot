package io.github.yutaro081.householdbudget.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.form.RegisterForm;
import io.github.yutaro081.householdbudget.service.RegistrationService;
import jakarta.validation.Valid;

/**
 * ログイン画面の表示と、アカウント登録の画面・処理（設計書 4.4.1、2.2、2.6）。
 * POST /login と POST /logout は、Spring Security が処理する（SecurityConfig）。
 */
@Controller
public class AuthController {

	private final RegistrationService registrationService;

	public AuthController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	/**
	 * ログイン画面。?error は失敗のあと、?logout はログアウトのあと（Spring Security が付ける）。
	 * 登録成功のあとの「登録しました」は、リダイレクト前に渡された値（フラッシュ属性）で表示される。
	 */
	@GetMapping("/login")
	public String login(@RequestParam Map<String, String> params, Model model) {
		// ?error や ?logout は値がないので、値ではなく、名前があるかどうかで見分ける
		if (params.containsKey("error")) {
			model.addAttribute("errorMessageKey", "error.login.failed");
		}
		if (params.containsKey("logout")) {
			model.addAttribute("successMessageKey", "success.logout");
		}
		return "login";
	}

	@GetMapping("/register")
	public String showRegister(Model model) {
		model.addAttribute("registerForm", new RegisterForm());
		return "register";
	}

	/**
	 * アカウント登録。入力エラー・重複のときは、リダイレクトせずに同じ画面を表示し直す。
	 * ユーザー名は残し、パスワードは残さない（設計書 2.3、6.9）。
	 */
	@PostMapping("/register")
	public String register(@Valid @ModelAttribute("registerForm") RegisterForm registerForm,
			BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (!bindingResult.hasErrors()) {
			try {
				registrationService.register(registerForm);
				redirectAttributes.addFlashAttribute("successMessageKey", "success.register");
				return "redirect:/login";
			} catch (DuplicateException e) {
				// ユーザー名の欄の横に「このユーザー名はすでに使われています」
				bindingResult.rejectValue("username", "error.duplicate.user");
			}
		}
		registerForm.setPassword(null);
		return "register";
	}

}
