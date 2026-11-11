package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.User;
import jakarta.persistence.EntityManager;

/**
 * BudgetRepository のテスト（設計書 3.6、3.9、4.6、5.7）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストのパスワードのハッシュは、ダミーの値（実在するアカウントのものではない）。
 */
@SpringBootTest
@Transactional
class BudgetRepositoryTest {

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long userId, Category category) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, categoryRepository.save(new Category(id, "科目")));
	}

	private Budget save(Owner owner, int year, Season season, int amount) {
		return save(owner, owner.category(), year, season, amount);
	}

	private Budget save(Owner owner, Category category, int year, Season season, int amount) {
		Budget saved = budgetRepository.saveAndFlush(new Budget(owner.userId(), category, year, season, amount));
		entityManager.clear();
		return saved;
	}

	// ---- その季節の自分の予算 ----

	@Test
	void findsOnlyThatSeasonOfThatYearOfThatUser() {
		Owner a = createOwner("budtest1");
		Owner b = createOwner("budtest2");
		Budget target = save(a, 2026, Season.WINTER, 30000);
		save(a, 2026, Season.AUTUMN, 1);       // 別の季節
		save(a, 2025, Season.WINTER, 2);       // 別の年
		save(a, 2027, Season.WINTER, 3);       // 別の年
		save(b, 2026, Season.WINTER, 999);     // 他のユーザー

		List<Budget> found = budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.WINTER);

		assertThat(found).extracting(Budget::getId).containsExactly(target.getId());
		assertThat(found.get(0).getAmount()).isEqualTo(30000);
	}

	@Test
	void findsNothingWhenThereAreNoRowsForThatSeason() {
		Owner a = createOwner("budtest3");
		save(a, 2026, Season.AUTUMN, 100);

		assertThat(budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.WINTER)).isEmpty();
	}

	/** 結果の予算は、科目も読み込み済み（open-in-view=false でも、科目のIDを読める） */
	@Test
	void categoryIsLoadedTogether() {
		Owner a = createOwner("budtest4");
		save(a, 2026, Season.AUTUMN, 100);
		var util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

		Budget same = budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.AUTUMN).get(0);
		assertThat(util.isLoaded(same, "category")).isTrue();
		assertThat(same.getCategory().getId()).isEqualTo(a.category().getId());
	}

	// ---- 金額の更新 ----

	@Test
	void changeAmountChangesOnlyTheAmount() {
		Owner a = createOwner("budtest10");
		Budget saved = save(a, 2026, Season.AUTUMN, 30000);

		Budget found = budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.AUTUMN).get(0);
		found.changeAmount(45000);
		entityManager.flush();
		entityManager.clear();

		Budget reread = budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.AUTUMN).get(0);
		assertThat(reread.getId()).isEqualTo(saved.getId());
		assertThat(reread.getAmount()).isEqualTo(45000);
		assertThat(reread.getUserId()).isEqualTo(a.userId());
		assertThat(reread.getCategory().getId()).isEqualTo(a.category().getId());
		assertThat(reread.getSeasonYear()).isEqualTo(2026);
		assertThat(reread.getSeason()).isEqualTo(Season.AUTUMN);
	}

	// ---- 一意制約：1科目・1年・1季節に予算は1つだけ ----

	@Test
	void secondRowForTheSameCategoryYearAndSeasonIsRejected() {
		Owner a = createOwner("budtest11");
		save(a, 2026, Season.AUTUMN, 100);

		assertThatThrownBy(() -> save(a, 2026, Season.AUTUMN, 200))
				.rootCause().hasMessageContaining("uk_budgets_category_season");
	}

	@Test
	void sameCategoryInOtherSeasonsOrYearsIsAllowed() {
		Owner a = createOwner("budtest12");
		save(a, 2026, Season.AUTUMN, 100);
		save(a, 2026, Season.WINTER, 100);
		save(a, 2027, Season.AUTUMN, 100);
		Category other = categoryRepository.save(new Category(a.userId(), "別の科目"));
		save(a, other, 2026, Season.AUTUMN, 100);
	}

	// ---- DB の CHECK 制約（3.6）：最後の守り。失敗するテストは1つずつ別にする ----

	@Test
	void checkAllowsAmountBoundaries() {
		Owner a = createOwner("budtest13");
		save(a, 2026, Season.SPRING, 0);
		save(a, 2026, Season.SUMMER, 3_000_000);
	}

	@Test
	void checkAllowsYearBoundaries() {
		Owner a = createOwner("budtest14");
		save(a, 2000, Season.SPRING, 100);
		save(a, 2100, Season.WINTER, 100);
	}

	@Test
	void checkRejectsNegativeAmount() {
		Owner a = createOwner("budtest15");
		assertThatThrownBy(() -> save(a, 2026, Season.AUTUMN, -1))
				.rootCause().hasMessageContaining("ck_budgets_amount");
	}

	@Test
	void checkRejectsAmountOver3000000() {
		Owner a = createOwner("budtest16");
		assertThatThrownBy(() -> save(a, 2026, Season.AUTUMN, 3_000_001))
				.rootCause().hasMessageContaining("ck_budgets_amount");
	}

	@Test
	void checkRejectsYearBefore2000() {
		Owner a = createOwner("budtest17");
		assertThatThrownBy(() -> save(a, 1999, Season.WINTER, 100))
				.rootCause().hasMessageContaining("ck_budgets_season_year");
	}

	@Test
	void checkRejectsYearAfter2100() {
		Owner a = createOwner("budtest18");
		assertThatThrownBy(() -> save(a, 2101, Season.SPRING, 100))
				.rootCause().hasMessageContaining("ck_budgets_season_year");
	}

	// ---- Phase 4 の2つのメソッドが、これまでどおり動く（新しい検索を足しても変わらない） ----

	@Test
	void existingMethodsStillWork() {
		Owner a = createOwner("budtest19");
		save(a, 2026, Season.AUTUMN, 0);
		save(a, 2026, Season.WINTER, 5);

		assertThat(budgetRepository.existsByUserIdAndCategoryIdAndAmountGreaterThan(a.userId(), a.category().getId(), 0)).isTrue();
		budgetRepository.deleteByUserIdAndCategoryId(a.userId(), a.category().getId());
		entityManager.flush();
		entityManager.clear();
		assertThat(budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.AUTUMN)).isEmpty();
		assertThat(budgetRepository.findByUserIdAndSeasonYearAndSeason(a.userId(), 2026, Season.WINTER)).isEmpty();
	}

}
