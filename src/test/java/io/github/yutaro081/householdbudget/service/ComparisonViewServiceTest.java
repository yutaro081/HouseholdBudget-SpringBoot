package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.LandingVerdict;
import io.github.yutaro081.householdbudget.domain.MeterScale;
import io.github.yutaro081.householdbudget.domain.PeriodProgress;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ComparisonResult;
import io.github.yutaro081.householdbudget.dto.ComparisonView;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * 予実比較の画面用のまとまり ComparisonView（設計書 4.5・4.7、5.13、issue 027）のテスト。
 * 見込みの式そのものは domain のテスト（LandingForecastTest など）で確かめてあるので、ここでは、
 * 表の行と見込み・見込みとの差が 1 組になること、要約カードが出る条件、「今の季節か」との違い、他人のデータが混ざらないことを確かめる。
 * <p>
 * 「今日」は 2026-11-11（秋の 72 日目。季節は 91 日、残り 19 日）。季節の初日から数えるときの見込みは、
 * （実績 × 91 × 110 ＋ 予算 × 19²）÷ 91² を、100 円単位で切り上げた額。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 */
@SpringBootTest
@Transactional
class ComparisonViewServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);
	private static final SeasonPeriod SUMMER_2026 = new SeasonPeriod(2026, Season.SUMMER);

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private BudgetRepository budgetRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private ComparisonService service;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long id, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category category(Owner o, String name, Integer autumnBudget) {
		Category c = categoryRepository.save(new Category(o.id(), name));
		if (autumnBudget != null) {
			budgetRepository.save(new Budget(o.id(), c, 2026, Season.AUTUMN, autumnBudget));
		}
		return c;
	}

	private void spend(Owner o, Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(o.id(), date, amount, c, null, o.vendor(), o.paymentMethod()));
	}

	private void flush() {
		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * 今の季節：行ごとに、見込みと、見込みとの差（予算 − 見込み）が 1 組になる。合計の行も同じ。要約カードに、差の文・％・メーターが入る。
	 * 夏に取引があるので、秋の初日から数える（数え始めの日の知らせは出さない）。
	 * 食費：（20,000 × 10,010 ＋ 175,000 × 361）÷ 8,281 ＝ 31,804.7… → 31,900。差は 175,000 − 31,900 ＝ 143,100。
	 */
	@Test
	void theCurrentSeasonPairsEachRowWithItsForecastAndGapAndFillsTheCard() {
		Owner a = createOwner("cmpview1");
		Category food = category(a, "食費", 175_000);
		spend(a, food, LocalDate.of(2026, 8, 20), 1_000);        // 夏。秋の実績には入らない
		spend(a, food, LocalDate.of(2026, 10, 31), 20_000);
		flush();

		ComparisonView view = service.view(a.id(), AUTUMN_2026, TODAY);

		assertThat(view.showForecast()).isTrue();
		assertThat(view.lines()).hasSize(1);
		ComparisonView.Line line = view.lines().get(0);
		assertThat(line.row().categoryName()).isEqualTo("食費");
		assertThat(line.row().budget()).isEqualTo(175_000L);
		assertThat(line.row().actual()).isEqualTo(20_000L);
		assertThat(line.forecast().amount()).isEqualTo(31_900L);
		assertThat(line.gap()).isEqualTo(143_100L);

		assertThat(view.total().row()).isEqualTo(view.comparison().total());
		assertThat(view.total().forecast().amount()).isEqualTo(31_900L);
		assertThat(view.total().gap()).isEqualTo(143_100L);

		ComparisonView.Card card = view.card();
		assertThat(card).isNotNull();
		assertThat(card.forecast()).isEqualTo(view.total().forecast());
		assertThat(card.verdict()).isEqualTo(new LandingVerdict(LandingVerdict.Direction.LESS, 143_100L));
		assertThat(card.usedPercent()).isEqualTo(11);            // 20,000 ÷ 175,000 ＝ 11.4…%
		assertThat(card.elapsedPercent()).isEqualTo(79);         // 72 ÷ 91 ＝ 79.1…%
		assertThat(card.meter()).isEqualTo(MeterScale.of(175_000L, 20_000L, PeriodProgress.seasonElapsedPermille(AUTUMN_2026, TODAY)));
		assertThat(card.countingFrom()).as("季節の初日から数えるときは、知らせない").isNull();
	}

	/** 最初の取引が季節の途中（10/31）なら、その日から数える。見込みは 54,700（CountingStartServiceTest と同じ例）で、カードに、その日が入る */
	@Test
	void theCardTellsTheFirstTransactionDateWhenItIsInsideTheSeason() {
		Owner a = createOwner("cmpview2");
		Category food = category(a, "食費", 175_000);
		spend(a, food, LocalDate.of(2026, 10, 31), 20_000);
		flush();

		ComparisonView view = service.view(a.id(), AUTUMN_2026, TODAY);

		assertThat(view.total().forecast().amount()).isEqualTo(54_700L);
		assertThat(view.lines().get(0).forecast().amount()).isEqualTo(54_700L);
		assertThat(view.total().gap()).isEqualTo(175_000L - 54_700L);
		assertThat(view.card().countingFrom()).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	/**
	 * 見込みが予算を超える科目：差はマイナスで、差の文は「多い」。
	 * （40,000 × 10,010 ＋ 30,000 × 361）÷ 8,281 ＝ 49,659.5… → 49,700。差は 30,000 − 49,700 ＝ −19,700。
	 */
	@Test
	void aForecastOverTheBudgetGivesANegativeGapAndTheMoreVerdict() {
		Owner a = createOwner("cmpview3");
		Category food = category(a, "食費", 30_000);
		spend(a, food, LocalDate.of(2026, 8, 20), 1_000);
		spend(a, food, LocalDate.of(2026, 9, 5), 40_000);
		flush();

		ComparisonView view = service.view(a.id(), AUTUMN_2026, TODAY);

		assertThat(view.lines().get(0).forecast().amount()).isEqualTo(49_700L);
		assertThat(view.lines().get(0).gap()).isEqualTo(-19_700L);
		assertThat(view.total().gap()).isEqualTo(-19_700L);
		assertThat(view.card().verdict()).isEqualTo(new LandingVerdict(LandingVerdict.Direction.MORE, 19_700L));
		assertThat(view.card().verdict().over()).isTrue();
	}

	/**
	 * 予算が未設定・0 円の科目は、見込みも差も出さない（null）。合計とカードは、設定済みの予算の合計（30,000）で出す。
	 * 食費：（3,000 × 10,010 ＋ 30,000 × 361）÷ 8,281 ＝ 4,934.2… → 5,000。
	 * 合計（実績 3,800）：（3,800 × 10,010 ＋ 30,000 × 361）÷ 8,281 ＝ 5,901.2… → 6,000。
	 */
	@Test
	void categoriesWithoutABudgetOrWithAZeroBudgetHaveNoForecastButTheTotalDoes() {
		Owner a = createOwner("cmpview4");
		Category food = category(a, "食費", 30_000);
		Category zero = category(a, "ゼロ", 0);
		Category unset = category(a, "未設定", null);
		spend(a, food, LocalDate.of(2026, 8, 20), 1_000);
		spend(a, food, LocalDate.of(2026, 9, 10), 3_000);
		spend(a, zero, LocalDate.of(2026, 9, 10), 500);
		spend(a, unset, LocalDate.of(2026, 9, 10), 300);
		flush();

		ComparisonView view = service.view(a.id(), AUTUMN_2026, TODAY);

		assertThat(view.lines()).extracting(l -> l.row().categoryName()).containsExactly("食費", "ゼロ", "未設定");
		assertThat(view.lines().get(0).forecast().amount()).isEqualTo(5_000L);
		assertThat(view.lines().get(0).gap()).isEqualTo(25_000L);
		assertThat(view.lines().get(1).forecast()).isNull();
		assertThat(view.lines().get(1).gap()).isNull();
		assertThat(view.lines().get(2).forecast()).isNull();
		assertThat(view.lines().get(2).gap()).isNull();
		assertThat(view.total().forecast().amount()).isEqualTo(6_000L);
		assertThat(view.total().gap()).isEqualTo(24_000L);
		assertThat(view.card()).isNotNull();
	}

	/** 今の季節でも、予算がひとつも設定されていなければ、表は見込みの列つき（showForecast）のまま、カードは出さない（null）。この 2 つは条件が違う */
	@Test
	void theCurrentSeasonWithoutAnyBudgetShowsTheForecastColumnsButNoCard() {
		Owner a = createOwner("cmpview5");
		Category food = category(a, "食費", null);
		spend(a, food, LocalDate.of(2026, 9, 10), 3_000);
		flush();

		ComparisonView view = service.view(a.id(), AUTUMN_2026, TODAY);

		assertThat(view.showForecast()).isTrue();
		assertThat(view.card()).isNull();
		assertThat(view.lines().get(0).forecast()).isNull();
		assertThat(view.lines().get(0).gap()).isNull();
		assertThat(view.total().forecast()).isNull();
		assertThat(view.total().gap()).isNull();
	}

	/** 過去の季節：見込みの列もカードも出さない。行は compare と同じ */
	@Test
	void aPastSeasonHasNoForecastAndNoCard() {
		Owner a = createOwner("cmpview6");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		budgetRepository.save(new Budget(a.id(), food, 2026, Season.SUMMER, 30_000));
		spend(a, food, LocalDate.of(2026, 7, 10), 3_000);
		flush();

		ComparisonView view = service.view(a.id(), SUMMER_2026, TODAY);
		ComparisonResult comparison = service.compare(a.id(), SUMMER_2026);

		assertThat(view.showForecast()).isFalse();
		assertThat(view.card()).isNull();
		assertThat(view.comparison()).isEqualTo(comparison);
		assertThat(view.lines()).hasSize(1);
		assertThat(view.lines().get(0).row()).isEqualTo(comparison.rows().get(0));
		assertThat(view.lines().get(0).forecast()).isNull();
		assertThat(view.lines().get(0).gap()).isNull();
		assertThat(view.total().row()).isEqualTo(comparison.total());
		assertThat(view.total().forecast()).isNull();
		assertThat(view.total().gap()).isNull();
	}

	/**
	 * 過去の季節（夏）と未来の季節（冬）では、予算も実績もある行がいくつあっても、すべての行と合計の見込み・見込みとの差が null で、カードも null。
	 * 見込みを出すかどうかは LandingForecast が決める（表示する季節が、今日の季節でなければ空）。Service は、季節を見分けずに全部の行で呼ぶので、ここで確かめる。
	 */
	@Test
	void pastAndFutureSeasonsHaveNoForecastNoGapAndNoCardOnAnyRow() {
		Owner a = createOwner("cmpview10");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		Category daily = categoryRepository.save(new Category(a.id(), "日用品"));
		for (Season season : new Season[] { Season.SUMMER, Season.WINTER }) {
			budgetRepository.save(new Budget(a.id(), food, 2026, season, 30_000));
			budgetRepository.save(new Budget(a.id(), daily, 2026, season, 10_000));
		}
		spend(a, food, LocalDate.of(2026, 7, 10), 3_000);         // 夏
		spend(a, daily, LocalDate.of(2026, 7, 11), 20_000);       // 夏。予算を超えている
		spend(a, food, LocalDate.of(2026, 12, 5), 4_000);         // 冬（今日 11/11 より先の日付の取引）
		flush();

		for (SeasonPeriod period : new SeasonPeriod[] { SUMMER_2026, new SeasonPeriod(2026, Season.WINTER) }) {
			ComparisonView view = service.view(a.id(), period, TODAY);

			assertThat(view.showForecast()).as(period.toString()).isFalse();
			assertThat(view.card()).as(period.toString()).isNull();
			assertThat(view.lines()).as(period.toString()).hasSize(2);
			assertThat(view.lines()).as(period.toString()).allSatisfy(line -> {
				assertThat(line.row().budget()).isNotNull();
				assertThat(line.forecast()).isNull();
				assertThat(line.gap()).isNull();
			});
			assertThat(view.total().row().budget()).as(period.toString()).isEqualTo(40_000L);
			assertThat(view.total().forecast()).as(period.toString()).isNull();
			assertThat(view.total().gap()).as(period.toString()).isNull();
		}
	}

	/** 守り（設計書 3.9）：他人の予算・取引・もっと早い最初の取引の日は、見込みにもカードにも混ざらない */
	@Test
	void otherUsersDataIsNeverMixedIntoTheView() {
		Owner a = createOwner("cmpview7");
		Category food = category(a, "食費", 175_000);
		spend(a, food, LocalDate.of(2026, 10, 31), 20_000);
		Owner b = createOwner("cmpview8");
		Category others = category(b, "食費", 999_000);
		spend(b, others, LocalDate.of(2026, 8, 1), 50_000);       // a より早い。a の数え始めを動かさない
		spend(b, others, LocalDate.of(2026, 9, 2), 900_000);
		flush();

		ComparisonView view = service.view(a.id(), AUTUMN_2026, TODAY);

		assertThat(view.lines()).hasSize(1);
		assertThat(view.total().row().budget()).isEqualTo(175_000L);
		assertThat(view.total().row().actual()).isEqualTo(20_000L);
		assertThat(view.total().forecast().amount()).isEqualTo(54_700L);
		assertThat(view.card().countingFrom()).isEqualTo(LocalDate.of(2026, 10, 31));
	}

	/** 範囲の外の季節は、compare と同じく NotFoundException */
	@Test
	void anOutOfRangeSeasonIsNotFound() {
		Owner a = createOwner("cmpview9");
		flush();

		assertThatThrownBy(() -> service.view(a.id(), new SeasonPeriod(1999, Season.WINTER), TODAY)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.view(a.id(), null, TODAY)).isInstanceOf(NotFoundException.class);
	}

}
