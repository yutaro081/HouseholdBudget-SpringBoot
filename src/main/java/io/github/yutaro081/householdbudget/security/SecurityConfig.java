package io.github.yutaro081.householdbudget.security;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.InvalidSessionAccessDeniedHandler;
import org.springframework.security.web.session.SimpleRedirectInvalidSessionStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcherEntry;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

/**
 * セキュリティの設定（設計書 4.5、7.2、7.3、7.6、7.10）。
 * <p>
 * ・パスワードを BCrypt でハッシュ化する PasswordEncoder を、アプリで1つだけ用意する。
 * ・ログインなしで開けるのは、/login、/register、/css/**、/js/**、/fonts/**、/error だけ。それ以外はログイン必須。
 * ・CSRF 対策は、無効にしない（標準のまま。POST にはトークンが要る）。
 * ・ログイン後にセッションIDを新しくする標準の動きも、無効にしない（7.10）。
 * ・全 URL を許可する仮の設定は作らない。
 * ・セッション Cookie の設定（S1）は application.properties に書く（Phase 3 の 4.11）。
 * ・ログインが切れたあと（古いセッションID で来たとき）は、画面を開く（GET）もボタンを押す（POST）も、
 *   /login?expired へ移す（7.10、S10、issue 030）。標準の部品を組み合わせ、自分で書くのは「古いセッションID か」の判定だけ。
 *   invalidSessionUrl は使わない（セッションID を持たないトークンなしの POST まで、403 でなくなるため）。
 */
@Configuration
public class SecurityConfig {

	/** パスワードを BCrypt でハッシュ化する。アプリで1つだけ用意する（7.2）。 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** ログインが切れたあとに移る先（ログイン画面が「ログインが切れました。もう一度ログインしてください。」を出す） */
	private static final String EXPIRED_LOGIN_URL = "/login?expired";

	/**
	 * 古いセッションID で来たか（7.10）。ブラウザがセッションID（JSESSIONID）を送ってきたのに、サーバーにはもうない
	 * （30 分操作がなくて切れた、アプリを再起動した）とき true。セッションID を持たずに来たときは false。
	 */
	static final RequestMatcher OLD_SESSION = request -> request.getRequestedSessionId() != null
			&& !request.isRequestedSessionIdValid();

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						// ログインなしで開けるもの（7.3）
						.requestMatchers("/login", "/register", "/css/**", "/js/**", "/fonts/**", "/error").permitAll()
						// それ以外は、ログイン必須。未ログインなら /login へ移動する
						.anyRequest().authenticated())
				.formLogin(form -> form
						// 自作のログイン画面（POST /login は Spring Security が処理する）
						.loginPage("/login")
						// 成功後は、開こうとしていた URL に関係なく、トップへ（U5、issue 010）
						.defaultSuccessUrl("/", true)
						// 失敗後は、ログイン画面へ。ユーザー名の有無に関わらず同じ（B8）。
						// 入力したユーザー名は残し、パスワードは残さない（2.3）
						.failureHandler(loginFailureHandler())
						.permitAll())
				.logout(logout -> logout
						// POST /logout（CSRF のトークンが要る、7.6）。セッションを捨てて、ログイン画面へ
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout")
						.permitAll())
				.exceptionHandling(exceptions -> exceptions
						// 画面を開く（GET）でログインが要るとき：古いセッションID なら /login?expired、それ以外は今までどおり /login
						.authenticationEntryPoint(loginEntryPoint())
						// ボタンを押す（POST）で CSRF のトークンが合わないとき：古いセッションID なら /login?expired（保存しない）、
						// それ以外は今までどおり 403（7.6）。CSRF の確認（CsrfFilter）も、この部品を使う
						.accessDeniedHandler(accessDeniedHandler()));
		return http.build();
	}

	/** 未ログインで、ログインが要る画面を開いたときの移動先（7.3、7.10） */
	private static AuthenticationEntryPoint loginEntryPoint() {
		return new DelegatingAuthenticationEntryPoint(
				new LoginUrlAuthenticationEntryPoint("/login"),
				// 可変長の引数ではなく List で渡す（総称型の配列の警告が出ないため）
				List.of(new RequestMatcherEntry<AuthenticationEntryPoint>(OLD_SESSION, new LoginUrlAuthenticationEntryPoint(EXPIRED_LOGIN_URL))));
	}

	/**
	 * 拒否したとき（今の設定では、CSRF のトークンが合わない POST だけ。7.6、7.10）。
	 * ログインが切れたあとは、サーバーにトークンが残っていないので、POST はここに来る。そのときは /login?expired へ移す。
	 */
	private static AccessDeniedHandler accessDeniedHandler() {
		LinkedHashMap<RequestMatcher, AccessDeniedHandler> handlers = new LinkedHashMap<>();
		handlers.put(OLD_SESSION, new InvalidSessionAccessDeniedHandler(new SimpleRedirectInvalidSessionStrategy(EXPIRED_LOGIN_URL)));
		return new RequestMatcherDelegatingAccessDeniedHandler(handlers, new AccessDeniedHandlerImpl());
	}

	/**
	 * ログイン失敗のとき、/login?error へ移動する。移動先の画面で入力したユーザー名を表示できるように、
	 * ユーザー名だけを、次の1回の表示に限って渡す（フラッシュ属性）。パスワードは渡さない。
	 */
	private AuthenticationFailureHandler loginFailureHandler() {
		return (request, response, exception) -> {
			FlashMap flashMap = new FlashMap();
			String username = request.getParameter("username");
			if (username != null) {
				flashMap.put("loginUsername", username);
			}
			new SessionFlashMapManager().saveOutputFlashMap(flashMap, request, response);
			response.sendRedirect(request.getContextPath() + "/login?error");
		};
	}

}
