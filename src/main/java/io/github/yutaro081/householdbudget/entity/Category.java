package io.github.yutaro081.householdbudget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 科目（テーブル categories、設計書 3.3）。
 * user_id は、ユーザーの Entity ではなく数値のIDで持つ（P7）。
 */
@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "name", nullable = false, length = 10)
	private String name;

	/** JPA が使う。アプリのコードからは使わない。 */
	protected Category() {
	}

	public Category(Long userId, String name) {
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

}
