package io.github.yutaro081.householdbudget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 取引先（テーブル vendors、設計書 3.4）。
 * user_id は、ユーザーの Entity ではなく数値のIDで持つ（P7）。
 */
@Entity
@Table(name = "vendors")
public class Vendor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "name", nullable = false, length = 20)
	private String name;

	/** JPA が使う。アプリのコードからは使わない。 */
	protected Vendor() {
	}

	public Vendor(Long userId, String name) {
		this.userId = userId;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	/** 名前を変える（変更、設計書 5.6）。持ち主（user_id）は変えない。 */
	public void rename(String name) {
		this.name = name;
	}

}
