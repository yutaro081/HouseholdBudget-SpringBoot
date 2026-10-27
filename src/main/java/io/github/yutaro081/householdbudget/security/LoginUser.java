package io.github.yutaro081.householdbudget.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * ログイン中のユーザーの情報（設計書 4.5、P3）。ログイン情報（セッション）に入る。
 * <p>
 * Controller は、ここからユーザーID（getUserId）を取り出して Service に渡す。
 * 画面から送られたユーザーIDは使わない（3.9）。毎回データベースを引かずに取り出せる。
 * <p>
 * 権限（管理者など）は、要件にないので持たない（4.8）。
 */
public class LoginUser implements UserDetails {

	private static final long serialVersionUID = 1L;

	private final Long userId;
	private final String username;
	private final String passwordHash;

	public LoginUser(Long userId, String username, String passwordHash) {
		this.userId = userId;
		this.username = username;
		this.passwordHash = passwordHash;
	}

	/** ログイン中のユーザーのID（users.id）。 */
	public Long getUserId() {
		return userId;
	}

	@Override
	public String getUsername() {
		return username;
	}

	/** BCrypt のハッシュ。Spring Security が、入力のパスワードと照らすために使う。 */
	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

}
