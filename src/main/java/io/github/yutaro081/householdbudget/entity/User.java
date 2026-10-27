package io.github.yutaro081.householdbudget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 利用者（テーブル users、設計書 3.2）。パスワードは BCrypt のハッシュだけを持つ。 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "username", nullable = false, length = 10)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	/** JPA が使う。アプリのコードからは使わない。 */
	protected User() {
	}

	public User(String username, String passwordHash) {
		this.username = username;
		this.passwordHash = passwordHash;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

}
