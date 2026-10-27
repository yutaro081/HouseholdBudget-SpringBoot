package io.github.yutaro081.householdbudget.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * セキュリティの設定（設計書 4.5、7.2、7.3、7.6、7.10）。
 * <p>
 * ・パスワードを BCrypt でハッシュ化する PasswordEncoder を、アプリで1つだけ用意する。
 * ・ログインなしで開けるのは、/login、/register、/css/**、/error だけ。それ以外はログイン必須。
 * ・CSRF 対策は、無効にしない（標準のまま。POST にはトークンが要る）。
 * ・ログイン後にセッションIDを新しくする標準の動きも、無効にしない（7.10）。
 * ・全 URL を許可する仮の設定は作らない。
 * ・セッション Cookie の設定（S1）は application.properties に書く（Phase 3 の 4.11）。
 */
@Configuration
public class SecurityConfig {

	/** パスワードを BCrypt でハッシュ化する。アプリで1つだけ用意する（7.2）。 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						// ログインなしで開けるもの（7.3）
						.requestMatchers("/login", "/register", "/css/**", "/error").permitAll()
						// それ以外は、ログイン必須。未ログインなら /login へ移動する
						.anyRequest().authenticated())
				.formLogin(form -> form
						// 自作のログイン画面（POST /login は Spring Security が処理する）
						.loginPage("/login")
						// 成功後は、開こうとしていた URL に関係なく、取引一覧へ（U5）
						.defaultSuccessUrl("/transactions", true)
						// 失敗後は、ログイン画面へ。ユーザー名の有無に関わらず同じ（B8）
						.failureUrl("/login?error")
						.permitAll())
				.logout(logout -> logout
						// POST /logout（CSRF のトークンが要る、7.6）。セッションを捨てて、ログイン画面へ
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout")
						.permitAll());
		return http.build();
	}

}
