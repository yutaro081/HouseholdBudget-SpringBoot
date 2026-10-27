package io.github.yutaro081.householdbudget.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * セキュリティの設定（設計書 4.5、7.2、7.3）。
 * <p>
 * 【作りかけ】4.6 の時点では、パスワードのハッシュ化に使う PasswordEncoder だけを用意している
 * （RegistrationService が使うため）。ログイン必須の設定、ログイン・ログアウト、CSRF は、4.7 で足す。
 * 全 URL を許可する仮の設定は作らない（Phase 2 の決定）。
 */
@Configuration
public class SecurityConfig {

	/** パスワードを BCrypt でハッシュ化する。アプリで1つだけ用意する（7.2）。 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
