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

		entityManager.clear();
		Budget earlierYear = budgetRepository.findByUserIdAndSeasonYearLessThan(a.userId(), 2027).get(0);
		assertThat(util.isLoaded(earlierYear, "category")).isTrue();

		entityManager.clear();
		Budget sameYear = budgetRepository
				.findByUserIdAndSeasonYearAndSeasonIn(a.userId(), 2026, Season.WINTER.earlierInYear()).get(0);
		assertThat(util.isLoaded(sameYear, "category")).isTrue();
	}

	// ---- 「表示季節より前」（初期値用）：年が前 ＋ 同じ年で季節が前 ----

	/**
	 * 2026年の夏より前を求めるとき：年が前（2025年のすべて、2024年のすべて…）と、同じ年で季節が前（2026年の春）。
	 * 2026年の夏・秋・冬と、2027年は含まれない。他のユーザーも含まれない。
	 * 季節の文字の順（AUTUMN < SPRING < SUMMER < WINTER）ではなく、期間の順で「前」が決まる。
	 */
	@Test
	void earlierThanSummer2026IsFoundByPeriodOrderNotByTextOrder() {
		Owner a = createOwner("budtest5");
		Owner b = createOwner("budtest6");
		Budget w2025 = save(a, 2025, Season.WINTER, 1);
		Budget a2025 = save(a, 2025, Season.AUTUMN, 2);
		Budget sp2026 = save(a, 2026, Season.SPRING, 3);
		save(a, 2026, Season.SUMMER, 4);       // 同じ季節は含まれない
		save(a, 2026, Season.AUTUMN, 5);       // 後は含まれない（文字の順では AUTUMN が先だが、期間の順では後）
		save(a, 2026, Season.WINTER, 6);       // 後
		save(a, 2027, Season.SPRING, 7);       // 後の年
		save(b, 2025, Season.WINTER, 999);     // 他のユーザー

		List<Budget> earlier = earlierThan(a.userId(), 2026, Season.SUMMER);

		assertThat(earlier).extracting(Budget::getId).containsExactlyInAnyOrder(w2025.getId(), a2025.getId(), sp2026.getId());
	}

	/** 年をまたぐ：2026年の春より前は、2025年の冬（12月がある年が冬の年）。同じ年の春以前はない */
	@Test
	void earlierThanSpring2026IsTheWinterOfTheYearBefore() {
		Owner a = createOwner("budtest7");
		Budget w2025 = save(a, 2025, Season.WINTER, 1);
		save(a, 2026, Season.SPRING, 2);
		save(a, 2026, Season.WINTER, 3);

		List<Budget> earlier = earlierThan(a.userId(), 2026, Season.SPRING);

		assertThat(earlier).extracting(Budget::getId).containsExactly(w2025.getId());
	}

	@Test
	void earlierThanWinter2026IncludesTheWholeYearBeforeInTheSameYear() {
		Owner a = createOwner("budtest8");
		Budget sp = save(a, 2026, Season.SPRING, 1);
		Budget su = save(a, 2026, Season.SUMMER, 2);
		Budget au = save(a, 2026, Season.AUTUMN, 3);
		save(a, 2026, Season.WINTER, 4);
		save(a, 2027, Season.SPRING, 5);

		List<Budget> earlier = earlierThan(a.userId(), 2026, Season.WINTER);

		assertThat(earlier).extracting(Budget::getId).containsExactlyInAnyOrder(sp.getId(), su.getId(), au.getId());
	}

	/** 春のときは、同じ年の中の「前の季節」が空。空の一覧で検索を呼んでも、エラーにならず、何も返らない */
	@Test
	void emptySeasonListFindsNothing() {
		Owner a = createOwner("budtest9");
		save(a, 2026, Season.SPRING, 1);

		assertThat(budgetRepository.findByUserIdAndSeasonYearAndSeasonIn(a.userId(), 2026, Season.SPRING.earlierInYear())).isEmpty();
	}

	/** 「表示季節より前」を、2つの検索を合わせて求める（Service が行うのと同じ） */
	private List<Budget> earlierThan(Long userId, int year, Season season) {
		List<Budget> result = new java.util.ArrayList<>(budgetRepository.findByUserIdAndSeasonYearLessThan(userId, year));
		List<Season> earlierInYear = season.earlierInYear();
		if (!earlierInYear.isEmpty()) {
			result.addAll(budgetRepository.findByUserIdAndSeasonYearAndSeasonIn(userId, year, earlierInYear));
		}
		return result;
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
