package io.github.yutaro081.householdbudget.entity;

import io.github.yutaro081.householdbudget.domain.Season;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 予算（テーブル budgets、設計書 3.6）。科目・年・季節ごとに1つ。
 * user_id は数値のIDで持ち、科目は関連で持つ（P7）。
 * season は、Season の名前（SPRING など）を文字で保存する（Q27 の a）。
 * season_year は季節の年で、冬は12月がある年（2.4、5.8）。
 */
@Entity
@Table(name = "budgets")
public class Budget {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(name = "season_year", nullable = false)
	private int seasonYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "season", nullable = false, length = 6)
	private Season season;

	@Column(name = "amount", nullable = false)
	private int amount;

	/** JPA が使う。アプリのコードからは使わない。 */
	protected Budget() {
	}

	public Budget(Long userId, Category category, int seasonYear, Season season, int amount) {
		this.userId = userId;
		this.category = category;
		this.seasonYear = seasonYear;
		this.season = season;
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Category getCategory() {
		return category;
	}

	public int getSeasonYear() {
		return seasonYear;
	}

	public Season getSeason() {
		return season;
	}

	public int getAmount() {
		return amount;
	}

}
