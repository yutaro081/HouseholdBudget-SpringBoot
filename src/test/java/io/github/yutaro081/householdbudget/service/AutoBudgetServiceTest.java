package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.ProposalChoice;
import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView.BasisKind;
import io.github.yutaro081.householdbudget.dto.AutoBudgetView.TrendKind;
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
 * AutoBudgetService のテスト（設計書 5.14、3.9、要件定義書 4.3、issue 019）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストのパスワードのハッシュは、ダミーの値（実在するアカウントのものではない）。
 * <p>
 * 確かめること：「記録のある年」の数え方、根拠の年の並び、最初に出す文の元になる値（傾向の 5 通りほか）、
 * ルール①〜④が科目ごとに選ばれること、画面の季節にすでに保存した額を 0 円で上書きしないこと、
 * 他人のデータが入らないこと、保存されないこと、設計書 5.14 の「計算の合った例」。
 * 文そのもの（messages.properties の固定の文）は、画面のテスト（Controller）で確かめる。
 */
@SpringBootTest
@Transactional
class AutoBudgetServiceTest {

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
	private AutoBudgetService service;
	@Autowired
	private EntityManager entityManager;

	private static final LocalDate NOV_11_2026 = LocalDate.of(2026, 11, 11);
	private static final SeasonPeriod WINTER_2026 = p(2026, Season.WINTER);
	private static final SeasonPeriod AUTUMN_2026 = p(2026, Season.AUTUMN);

	private static SeasonPeriod p(int year, Season season) {
		return new SeasonPeriod(year, season);
	}

	/** 1人分のユーザーと、取引に使う取引先・支払方法 */
	private record Owner(Long id, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id,
				vendorRepository.save(new Vendor(id, "取引先")),
				paymentMethodRepository.save(new PaymentMethod(id, "支払方法")));
	}

	private Category category(Owner owner, String name) {
		return categoryRepository.save(new Category(owner.id(), name));
	}

	private void budget(Owner owner, Category category, SeasonPeriod period, int amount) {
		budgetRepository.save(new Budget(owner.id(), category, period.year(), period.season(), amount));
	}

	private void spendOn(Owner owner, Category category, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(owner.id(), date, amount, category, null,
				owner.vendor(), owner.paymentMethod()));
	}

	/** その季節の真ん中あたり（開始日の 30 日あと）に、取引を 1 件入れる */
	private void spendIn(Owner owner, Category category, SeasonPeriod season, int amount) {
		spendOn(owner, category, season.startDate().plusDays(30), amount);
	}

	private AutoBudgetView propose(Owner owner, SeasonPeriod period, ProposalChoice choice, LocalDate today) {
		entityManager.flush();
		entityManager.clear();
		return service.propose(owner.id(), period, choice, today);
	}

	private AutoBudgetView proposeOk(Owner owner, SeasonPeriod period, LocalDate today) {
		return propose(owner, period, ProposalChoice.OK, today);
	}

	/** 案の合計（行の案の足し算）。画面は、この合計を入力欄の値から出す */
	private static long totalOf(AutoBudgetView view) {
		return view.rows().stream().mapToLong(AutoBudgetView.Row::amount).sum();
	}

	private static AutoBudgetView.Row rowOf(AutoBudgetView view, Category category) {
		return view.rows().stream().filter(r -> r.categoryId().equals(category.getId())).findFirst().orElseThrow();
	}

	/** 傾向の 12 季節（2026/11/11 に 2026 年 冬を開くとき）：2023 秋〜2026 夏（古い順） */
	private static List<SeasonPeriod> twelveSeasons() {
		List<SeasonPeriod> list = new ArrayList<>();
		SeasonPeriod season = p(2023, Season.AUTUMN);
		for (int i = 0; i < 12; i++) {
			list.add(season);
			season = season.next().orElseThrow();
		}
		return list;
	}

	/** 12 季節のすべてに、その科目の取引を入れる（T1 の 4 季節は a1、T2 は a2、T3 は a3）。skipIndex の季節は入れない（-1 ならなし） */
	private void fillTrend(Owner owner, Category category, int a1, int a2, int a3, int skipIndex) {
		List<SeasonPeriod> seasons = twelveSeasons();
		for (int i = 0; i < 12; i++) {
			if (i == skipIndex) {
				continue;
			}
			int amount = i < 4 ? a1 : (i < 8 ? a2 : a3);
			spendIn(owner, category, seasons.get(i), amount);
		}
	}

	private int budgetCount(Owner owner) {
		entityManager.flush();
		entityManager.clear();
		return (int) budgetRepository.findAll().stream().filter(b -> b.getUserId().equals(owner.id())).count();
	}

	private int transactionCount(Owner owner) {
		entityManager.flush();
		entityManager.clear();
		return transactionRepository.findByUserIdOrderByTransactionDateDescIdDesc(owner.id()).size();
	}

	// ================================================================
	// 記録のある年の数え方
	// ================================================================

	/** 記録のある年（自分の取引が 1 件以上ある年）の、その科目の実績 0 円は、0 円として数える */
	@Test
	void aYearWithRecordsButNothingForTheCategoryCountsAsZero() {
		Owner a = createOwner("autosvc1");
		Category food = category(a, "食費");
		Category misc = category(a, "雑費");
		spendIn(a, food, p(2023, Season.WINTER), 90_000);
		spendIn(a, misc, p(2024, Season.WINTER), 5_000);          // 2024 冬は食費の取引なし（雑費だけ）→ 食費は 0 円として数える
		spendIn(a, food, p(2025, Season.WINTER), 30_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		// 90,000・0・30,000 の中央値 ＝ 30,000（0 を数えず平均にすると 60,000 になってしまう）
		assertThat(row.rule()).isEqualTo(1);
		assertThat(row.basisKind()).isEqualTo(BasisKind.MEDIAN);
		assertThat(row.years()).containsExactly(2023, 2024, 2025);
		assertThat(row.yearsConsecutive()).isTrue();
		assertThat(row.baseAmount()).isEqualTo(30_000L);
		assertThat(row.amount()).isEqualTo(30_000L);
	}

	/** 取引が 1 件もない年は、記録のある年に入れない（0 円として数えない）。年が飛ぶので「2023・2025 年の冬の平均」 */
	@Test
	void aYearWithoutAnyTransactionIsNotCountedAndTheGapIsMarked() {
		Owner a = createOwner("autosvc2");
		Category food = category(a, "食費");
		spendIn(a, food, p(2023, Season.WINTER), 90_000);
		spendIn(a, food, p(2025, Season.WINTER), 30_000);         // 2024 冬は取引なし

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);
		AutoBudgetView.Row row = rowOf(view, food);

		assertThat(view.hasRecordYears()).isTrue();
		assertThat(row.basisKind()).isEqualTo(BasisKind.AVERAGE);
		assertThat(row.years()).containsExactly(2023, 2025);
		assertThat(row.yearsConsecutive()).isFalse();             // 年が飛ぶ：「2023・2025 年の冬の平均」
		assertThat(row.baseAmount()).isEqualTo(60_000L);
		assertThat(row.amount()).isEqualTo(60_000L);
	}

	/** 続いている 2 年：「2024〜2025 年の冬の平均」。平均は 21,500 円 */
	@Test
	void twoConsecutiveYearsAreAnAverageWithoutAGap() {
		Owner a = createOwner("autosvc3");
		Category food = category(a, "食費");
		spendIn(a, food, p(2024, Season.WINTER), 21_000);
		spendIn(a, food, p(2025, Season.WINTER), 22_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.basisKind()).isEqualTo(BasisKind.AVERAGE);
		assertThat(row.years()).containsExactly(2024, 2025);
		assertThat(row.yearsConsecutive()).isTrue();
		assertThat(row.baseAmount()).isEqualTo(21_500L);
		assertThat(row.amount()).isEqualTo(22_000L);
	}

	/** 3 年そろっていて飛ばない：「2023〜2025 年の冬の中央値」 */
	@Test
	void threeConsecutiveYearsAreAMedian() {
		Owner a = createOwner("autosvc4");
		Category food = category(a, "食費");
		spendIn(a, food, p(2023, Season.WINTER), 20_000);
		spendIn(a, food, p(2024, Season.WINTER), 21_000);
		spendIn(a, food, p(2025, Season.WINTER), 22_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.basisKind()).isEqualTo(BasisKind.MEDIAN);
		assertThat(row.years()).containsExactly(2023, 2024, 2025);
		assertThat(row.yearsConsecutive()).isTrue();
		assertThat(row.baseAmount()).isEqualTo(21_000L);
	}

	/** 記録のある年が 1 つだけ：「去年の冬 22,050 円」 */
	@Test
	void oneYearOnlyIsShownAsTheSingleYear() {
		Owner a = createOwner("autosvc5");
		Category food = category(a, "食費");
		spendIn(a, food, p(2025, Season.WINTER), 22_050);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		// 去年の冬 22,050 円
		assertThat(row.basisKind()).isEqualTo(BasisKind.SINGLE_YEAR);
		assertThat(row.years()).containsExactly(2025);
		assertThat(row.yearsConsecutive()).isTrue();
		assertThat(row.baseAmount()).isEqualTo(22_050L);
		assertThat(row.amount()).isEqualTo(23_000L);
	}

	@Test
	void anOlderSingleYearKeepsItsYear() {
		Owner a = createOwner("autosvc6");
		Category food = category(a, "食費");
		spendIn(a, food, p(2023, Season.WINTER), 10_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.basisKind()).isEqualTo(BasisKind.SINGLE_YEAR);
		assertThat(row.years()).containsExactly(2023);
	}

	/** 画面の季節の名前は、冬以外の季節ではその季節 */
	@Test
	void theScreenSeasonNameFollowsTheSeasonOfTheScreen() {
		Owner a = createOwner("autosvc7");
		Category food = category(a, "食費");
		spendIn(a, food, p(2025, Season.SPRING), 10_000);

		AutoBudgetView view = proposeOk(a, p(2026, Season.SPRING), NOV_11_2026);

		assertThat(view.screenSeason()).isEqualTo(Season.SPRING);
		assertThat(rowOf(view, food).years()).containsExactly(2025);
		assertThat(proposeOk(a, WINTER_2026, NOV_11_2026).screenSeason()).isEqualTo(Season.WINTER);
	}

	/** 冬の 1 月・2 月は前の年の冬。3 月 1 日は春。年は取引の日付ではなく、季節の年で数える */
	@Test
	void januaryAndFebruaryBelongToThePreviousYearsWinterAndMarchFirstIsSpring() {
		Owner a = createOwner("autosvc8");
		Category food = category(a, "食費");
		spendOn(a, food, LocalDate.of(2025, 2, 28), 20_000);      // 2024 年の冬
		spendOn(a, food, LocalDate.of(2025, 3, 1), 800_000);      // 2025 年の春（冬に入れない）
		spendOn(a, food, LocalDate.of(2026, 1, 15), 30_000);      // 2025 年の冬
		spendOn(a, food, LocalDate.of(2026, 3, 1), 999_000);      // 2026 年の春（冬に入れない）

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.years()).containsExactly(2024, 2025);
		assertThat(row.baseAmount()).isEqualTo(25_000L);
		assertThat(row.amount()).isEqualTo(25_000L);
	}

	/** 同じ年の冬でも、12 月と 1〜2 月の取引は足し合わせる */
	@Test
	void decemberAndJanuaryOfTheSameWinterAreSummed() {
		Owner a = createOwner("autosvc9");
		Category food = category(a, "食費");
		spendOn(a, food, LocalDate.of(2025, 12, 20), 10_000);     // 2025 年の冬
		spendOn(a, food, LocalDate.of(2026, 1, 20), 5_000);       // 2025 年の冬

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.years()).containsExactly(2025);
		assertThat(row.baseAmount()).isEqualTo(15_000L);
	}

	/** 画面の季節そのもの（と、それより後）の取引は、過去の実績に入れない */
	@Test
	void theScreenSeasonItselfIsNotUsedAsHistory() {
		Owner a = createOwner("autosvc10");
		Category food = category(a, "食費");
		spendOn(a, food, LocalDate.of(2026, 12, 5), 500_000);     // 画面の季節（2026 冬）の中

		AutoBudgetView view = proposeOk(a, WINTER_2026, LocalDate.of(2026, 12, 10));

		assertThat(view.hasRecordYears()).isFalse();
		assertThat(rowOf(view, food).rule()).isEqualTo(4);
	}

	/** 2000 年 春より前は数えない：2002 年 冬を開くと、同じ季節は 2000・2001 年の 2 年だけ */
	@Test
	void yearsBeforeSpring2000AreNotCounted() {
		Owner a = createOwner("autosvc11");
		Category food = category(a, "食費");
		spendOn(a, food, LocalDate.of(2000, 12, 15), 10_000);     // 2000 年の冬
		spendOn(a, food, LocalDate.of(2001, 12, 15), 20_000);     // 2001 年の冬

		AutoBudgetView.Row row = rowOf(proposeOk(a, p(2002, Season.WINTER), NOV_11_2026), food);

		assertThat(row.basisKind()).isEqualTo(BasisKind.AVERAGE);
		assertThat(row.years()).containsExactly(2000, 2001);
		assertThat(row.baseAmount()).isEqualTo(15_000L);
	}

	/** 記録のある年が 1 つもない：hasRecordYears は false（「過去の○の実績がないので…」の文） */
	@Test
	void noRecordYearsMeansNoHistoryAndTheRulesFallToTwoThreeFour() {
		Owner a = createOwner("autosvc12");
		Category food = category(a, "食費");
		Category fare = category(a, "交通費");
		Category misc = category(a, "雑費");
		spendIn(a, food, AUTUMN_2026, 3_600);                     // 直前の季節の実績あり → ②
		budget(a, fare, AUTUMN_2026, 8_000);                      // 直前の予算あり → ③
		// 雑費：何もなし → ④

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.hasRecordYears()).isFalse();
		assertThat(view.trendKind()).isEqualTo(TrendKind.NOT_CALCULATED);
		assertThat(rowOf(view, food).rule()).isEqualTo(2);
		assertThat(rowOf(view, fare).rule()).isEqualTo(3);
		assertThat(rowOf(view, misc).rule()).isEqualTo(4);
		assertThat(view.noMaterial()).isFalse();
	}

	// ================================================================
	// 傾向の 5 通り（2 行目の文の元）。2026/11/11 に 2026 年 冬を開く。12 季節は 2023 秋〜2026 夏
	// ================================================================

	/** 増える：T1＝400,000、T2＝440,000、T3＝480,000 → 118% */
	@Test
	void trendUp() {
		Owner a = createOwner("autosvc13");
		Category food = category(a, "食費");
		fillTrend(a, food, 100_000, 110_000, 120_000, -1);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.trendKind()).isEqualTo(TrendKind.UP);
		assertThat(view.trendPercent()).isEqualTo(118);
		assertThat(view.trendCapped()).isFalse();
		// 過去の冬 100,000・110,000・120,000 の中央値 110,000 × 118% ＝ 129,800 → 130,000
		AutoBudgetView.Row row = rowOf(view, food);
		assertThat(row.baseAmount()).isEqualTo(110_000L);
		assertThat(row.amount()).isEqualTo(130_000L);
		assertThat(totalOf(view)).isEqualTo(130_000L);
	}

	/** 減る：T1＝480,000、T2＝440,000、T3＝400,000 → 82% */
	@Test
	void trendDown() {
		Owner a = createOwner("autosvc14");
		Category food = category(a, "食費");
		fillTrend(a, food, 120_000, 110_000, 100_000, -1);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.trendKind()).isEqualTo(TrendKind.DOWN);
		assertThat(view.trendPercent()).isEqualTo(82);
		assertThat(view.trendCapped()).isFalse();
		// 110,000 × 82% ＝ 90,200 → 91,000
		assertThat(rowOf(view, food).amount()).isEqualTo(91_000L);
	}

	/** ほぼ同じ：ちょうど 100% */
	@Test
	void trendSame() {
		Owner a = createOwner("autosvc15");
		Category food = category(a, "食費");
		fillTrend(a, food, 100_000, 100_000, 100_000, -1);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.trendKind()).isEqualTo(TrendKind.SAME);
		assertThat(view.trendPercent()).isEqualTo(100);
		assertThat(view.trendCapped()).isFalse();
		assertThat(rowOf(view, food).amount()).isEqualTo(100_000L);
	}

	/** 上限：T1＝40,000、T2＝40,000、T3＝1,600,000 → 式では 378%、130% で止める */
	@Test
	void trendUpCappedAt130() {
		Owner a = createOwner("autosvc16");
		Category food = category(a, "食費");
		fillTrend(a, food, 10_000, 10_000, 400_000, -1);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.trendKind()).isEqualTo(TrendKind.UP);
		assertThat(view.trendPercent()).isEqualTo(130);
		assertThat(view.trendCapped()).isTrue();
		// 過去の冬 10,000・10,000・400,000 の中央値 10,000 × 130% ＝ 13,000
		assertThat(rowOf(view, food).amount()).isEqualTo(13_000L);
	}

	/** 計算しなかった：12 季節のうち 1 季節（2024 春）に取引がない → 100%。記録のある年は 3 つある */
	@Test
	void trendNotCalculatedWhenOneOfTheTwelveSeasonsHasNoTransactions() {
		Owner a = createOwner("autosvc17");
		Category food = category(a, "食費");
		fillTrend(a, food, 100_000, 110_000, 120_000, 2);         // 添字 2 ＝ 2024 春

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.hasRecordYears()).isTrue();
		assertThat(view.trendKind()).isEqualTo(TrendKind.NOT_CALCULATED);
		assertThat(view.trendPercent()).isEqualTo(100);
		assertThat(view.trendCapped()).isFalse();
		assertThat(rowOf(view, food).amount()).isEqualTo(110_000L);   // 傾向をかけない
	}

	/** 12 季節が 2000 年 春より前にかかるときも、計算しない */
	@Test
	void trendNotCalculatedWhenTheTwelveSeasonsReachBeforeSpring2000() {
		Owner a = createOwner("autosvc18");
		Category food = category(a, "食費");
		spendOn(a, food, LocalDate.of(2000, 12, 15), 10_000);
		spendOn(a, food, LocalDate.of(2001, 12, 15), 10_000);

		AutoBudgetView view = proposeOk(a, p(2002, Season.WINTER), NOV_11_2026);

		assertThat(view.trendKind()).isEqualTo(TrendKind.NOT_CALCULATED);
		assertThat(view.trendPercent()).isEqualTo(100);
	}

	/** 画面の季節より後の季節や、まだ終わっていない季節（秋 2026）は、傾向の 12 季節に入れない */
	@Test
	void theSeasonThatIsNotFinishedYetIsNotPartOfTheTrend() {
		Owner a = createOwner("autosvc19");
		Category food = category(a, "食費");
		fillTrend(a, food, 100_000, 100_000, 100_000, -1);
		spendIn(a, food, AUTUMN_2026, 900_000);                   // 今日を含む秋 2026：傾向に入れない

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.trendKind()).isEqualTo(TrendKind.SAME);
		assertThat(view.trendPercent()).isEqualTo(100);
	}

	// ================================================================
	// 最初に出す 3 行の元になる値
	// ================================================================

	/** 1 行目：直前の季節の予算の合計（設定済みの科目だけ。0 円も設定済み）・実績の合計・今日を含むか */
	@Test
	void firstLineValuesComeFromThePreviousSeason() {
		Owner a = createOwner("autosvc20");
		Category food = category(a, "食費");
		Category fare = category(a, "交通費");
		Category misc = category(a, "雑費");
		budget(a, food, AUTUMN_2026, 95_000);
		budget(a, fare, AUTUMN_2026, 0);
		spendIn(a, food, AUTUMN_2026, 49_200);
		spendIn(a, fare, AUTUMN_2026, 5_600);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.previousSeason()).isEqualTo(AUTUMN_2026);
		assertThat(view.previousBudgetTotal()).isEqualTo(95_000L);   // 雑費は未設定。交通費の 0 円は設定済みとして足す
		assertThat(view.previousActualTotal()).isEqualTo(54_800L);
		assertThat(view.previousContainsToday()).isTrue();
		assertThat(view.today()).isEqualTo(NOV_11_2026);
		assertThat(view.choice()).isEqualTo(ProposalChoice.OK);
		assertThat(rowOf(view, food).previousBudget()).isEqualTo(95_000L);
		assertThat(rowOf(view, fare).previousBudget()).isEqualTo(0L);
		assertThat(rowOf(view, misc).previousBudget()).isNull();
		assertThat(rowOf(view, food).previousActual()).isEqualTo(49_200L);
		assertThat(rowOf(view, misc).previousActual()).isZero();
	}

	/** 直前の季節の予算が 1 科目も設定されていない：「予算 未設定」（null） */
	@Test
	void previousBudgetTotalIsNullWhenNothingIsSet() {
		Owner a = createOwner("autosvc21");
		category(a, "食費");

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.previousBudgetTotal()).isNull();
		assertThat(view.previousActualTotal()).isZero();
	}

	/** 直前の季節が終わっているとき：今日を含まない。実績はそのまま（ペースの「このペースなら」は出さない） */
	@Test
	void aFinishedPreviousSeasonIsUsedAsItIsWithoutAPaceNote() {
		Owner a = createOwner("autosvc22");
		Category food = category(a, "食費");
		spendIn(a, food, AUTUMN_2026, 15_200);

		AutoBudgetView view = proposeOk(a, WINTER_2026, LocalDate.of(2026, 12, 5));
		AutoBudgetView.Row row = rowOf(view, food);

		assertThat(view.previousContainsToday()).isFalse();
		assertThat(view.previousPaceTotal()).isNull();
		assertThat(row.paceAmount()).isNull();
		assertThat(row.rule()).isEqualTo(2);
		assertThat(row.basisKind()).isEqualTo(BasisKind.ACTUAL);
		assertThat(row.baseAmount()).isEqualTo(15_200L);
		assertThat(row.amount()).isEqualTo(16_000L);
	}

	/** 過ぎた日数が 14 日未満（13 日目）：ペースは使えない。②を飛ばして③へ。「このペースなら」も出さない */
	@Test
	void thePaceIsNotUsedOnThe13thDayOfThePreviousSeason() {
		Owner a = createOwner("autosvc23");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);
		spendOn(a, food, LocalDate.of(2026, 9, 2), 5_000);

		AutoBudgetView view = proposeOk(a, WINTER_2026, LocalDate.of(2026, 9, 13));
		AutoBudgetView.Row row = rowOf(view, food);

		assertThat(view.previousContainsToday()).isTrue();
		assertThat(view.previousPaceTotal()).isNull();
		assertThat(row.paceAmount()).isNull();
		assertThat(row.rule()).isEqualTo(3);
		assertThat(row.amount()).isEqualTo(30_000L);
	}

	/** 14 日目：ペースを使う。5,000 × 91 ÷ 14 ＝ 32,500（表は 32,500、案は 33,000） */
	@Test
	void thePaceIsUsedFromThe14thDay() {
		Owner a = createOwner("autosvc24");
		Category food = category(a, "食費");
		budget(a, food, AUTUMN_2026, 30_000);
		spendOn(a, food, LocalDate.of(2026, 9, 2), 5_000);

		AutoBudgetView view = proposeOk(a, WINTER_2026, LocalDate.of(2026, 9, 14));
		AutoBudgetView.Row row = rowOf(view, food);

		assertThat(view.previousPaceTotal()).isEqualTo(32_500L);
		assertThat(row.paceAmount()).isEqualTo(32_500L);
		assertThat(row.rule()).isEqualTo(2);
		assertThat(row.basisKind()).isEqualTo(BasisKind.PACE);
		assertThat(row.baseAmount()).isEqualTo(32_500L);
		assertThat(row.amount()).isEqualTo(33_000L);
	}

	/**
	 * 記録のある年はあるが、全科目が②〜④：どの科目も 3 年のうち 1 年しか使っていないので、中央値がすべて 0 円になる。
	 * 「過去の冬の実績がない」わけではない（hasRecordYears は true）。
	 */
	@Test
	void recordsExistButEveryCategoryIsRuleTwoToFour() {
		Owner a = createOwner("autosvc47");
		Category food = category(a, "食費");
		Category fare = category(a, "交通費");
		Category misc = category(a, "雑費");
		spendIn(a, food, p(2023, Season.WINTER), 50_000);         // 食費は 2023 冬だけ → 中央値 0 円
		spendIn(a, fare, p(2024, Season.WINTER), 50_000);         // 交通費は 2024 冬だけ
		spendIn(a, misc, p(2025, Season.WINTER), 50_000);         // 雑費は 2025 冬だけ
		spendIn(a, food, AUTUMN_2026, 3_600);                     // 直前の実績あり → ②
		budget(a, fare, AUTUMN_2026, 8_000);                      // 直前の予算あり → ③
		// 雑費：直前の実績も予算もなし → ④

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.hasRecordYears()).isTrue();
		assertThat(rowOf(view, food).rule()).isEqualTo(2);
		assertThat(rowOf(view, fare).rule()).isEqualTo(3);
		assertThat(rowOf(view, misc).rule()).isEqualTo(4);
		assertThat(view.noMaterial()).isFalse();
	}

	/** 全科目が④：「案を作れる材料がありません」（noMaterial）。案は今の値のまま */
	@Test
	void noMaterialWhenEveryCategoryIsRuleFour() {
		Owner a = createOwner("autosvc26");
		Category food = category(a, "食費");
		Category fare = category(a, "交通費");
		budget(a, food, WINTER_2026, 12_000);                     // 画面の季節に保存済み

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.noMaterial()).isTrue();
		assertThat(rowOf(view, food).rule()).isEqualTo(4);
		assertThat(rowOf(view, food).amount()).isEqualTo(12_000L);
		assertThat(rowOf(view, fare).rule()).isEqualTo(4);
		assertThat(rowOf(view, fare).amount()).isZero();
		assertThat(totalOf(view)).isEqualTo(12_000L);
	}

	// ================================================================
	// ルール①〜④が科目ごとに選ばれる
	// ================================================================

	/** 中央値が 0 円（記録のある年に、その科目の実績が 0 円）のとき、①ではなく②（直前の実績あり） */
	@Test
	void aMedianOfZeroFallsThroughToRuleTwo() {
		Owner a = createOwner("autosvc27");
		Category food = category(a, "食費");
		Category medical = category(a, "医療費");
		spendIn(a, food, p(2023, Season.WINTER), 10_000);
		spendIn(a, food, p(2024, Season.WINTER), 10_000);
		spendIn(a, food, p(2025, Season.WINTER), 10_000);
		spendIn(a, medical, AUTUMN_2026, 3_600);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), medical);

		assertThat(row.rule()).isEqualTo(2);
		assertThat(row.basisKind()).isEqualTo(BasisKind.PACE);
		assertThat(row.years()).isEmpty();
		assertThat(row.amount()).isEqualTo(5_000L);               // 3,600 × 91 ÷ 72 ＝ 4,550 → 5,000
	}

	/** ①は②より先：中央値があれば、直前の実績・予算があっても① */
	@Test
	void ruleOneIsChosenBeforeTheOthers() {
		Owner a = createOwner("autosvc28");
		Category food = category(a, "食費");
		spendIn(a, food, p(2025, Season.WINTER), 20_000);
		spendIn(a, food, AUTUMN_2026, 3_600);
		budget(a, food, AUTUMN_2026, 8_000);
		budget(a, food, WINTER_2026, 5_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.rule()).isEqualTo(1);
		assertThat(row.amount()).isEqualTo(20_000L);
	}

	/** ②は③より先：直前の実績があれば、直前の予算があっても② */
	@Test
	void ruleTwoIsChosenBeforeRuleThree() {
		Owner a = createOwner("autosvc29");
		Category food = category(a, "食費");
		spendIn(a, food, AUTUMN_2026, 3_600);
		budget(a, food, AUTUMN_2026, 8_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), food);

		assertThat(row.rule()).isEqualTo(2);
		assertThat(row.amount()).isEqualTo(5_000L);
	}

	/** 実績 0 円で予算がある：③（予算 8,000 円） */
	@Test
	void ruleThreeUsesThePreviousBudgetWhenThereIsNoActual() {
		Owner a = createOwner("autosvc30");
		Category daily = category(a, "日用品");
		budget(a, daily, AUTUMN_2026, 8_000);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), daily);

		assertThat(row.rule()).isEqualTo(3);
		assertThat(row.basisKind()).isEqualTo(BasisKind.BUDGET);
		assertThat(row.baseAmount()).isEqualTo(8_000L);
		assertThat(row.amount()).isEqualTo(8_000L);
	}

	/** ④：直前の予算が未設定・実績 0 円。画面の季節に保存済みなら、その額のまま。未保存なら 0 円 */
	@Test
	void ruleFourKeepsTheCurrentValue() {
		Owner a = createOwner("autosvc31");
		Category saved = category(a, "娯楽");
		Category unsaved = category(a, "雑費");
		Category other = category(a, "食費");
		budget(a, saved, WINTER_2026, 5_000);
		spendIn(a, other, p(2025, Season.WINTER), 10_000);        // 記録のある年を作る（娯楽・雑費の実績は 0 円）

		for (ProposalChoice choice : ProposalChoice.values()) {
			AutoBudgetView view = propose(a, WINTER_2026, choice, NOV_11_2026);

			assertThat(rowOf(view, saved).rule()).isEqualTo(4);
			assertThat(rowOf(view, saved).basisKind()).isEqualTo(BasisKind.NONE);
			assertThat(rowOf(view, saved).amount()).isEqualTo(5_000L);      // 割合もかけない
			assertThat(rowOf(view, unsaved).rule()).isEqualTo(4);
			assertThat(rowOf(view, unsaved).amount()).isZero();
		}
	}

	/** 画面の季節にすでに保存した額を、0 円で上書きしない（Q019-1）。直前の予算が 0 円（設定済み）でも④ */
	@Test
	void aSavedAmountIsNotOverwrittenWithZeroWhenThePreviousBudgetIsZero() {
		Owner a = createOwner("autosvc32");
		Category fun = category(a, "娯楽");
		budget(a, fun, AUTUMN_2026, 0);                           // 直前の予算は 0 円（設定済み）
		budget(a, fun, WINTER_2026, 7_000);                       // 画面の季節にすでに保存した額

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), fun);

		assertThat(row.previousBudget()).isEqualTo(0L);
		assertThat(row.rule()).isEqualTo(4);
		assertThat(row.amount()).isEqualTo(7_000L);
	}

	/** 未保存の科目の「今の値」は、前の季節の額ではなく 0 円（Q6-4 の変更）。直前の予算が 0 円なら④で、案も 0 円 */
	@Test
	void anUnsavedCategoryStartsFromZeroNotFromThePreviousSeason() {
		Owner a = createOwner("autosvc33");
		Category fun = category(a, "娯楽");
		budget(a, fun, AUTUMN_2026, 0);

		AutoBudgetView.Row row = rowOf(proposeOk(a, WINTER_2026, NOV_11_2026), fun);

		assertThat(row.rule()).isEqualTo(4);
		assertThat(row.amount()).isZero();                        // Q6-4：未保存の今の値は 0 円
	}

	/** 選ぶ割合：①②③に効く。④には効かない。毎回もとから計算する */
	@Test
	void theChoiceChangesTheProposalFromScratchEachTime() {
		Owner a = createOwner("autosvc34");
		Category food = category(a, "食費");
		Category daily = category(a, "日用品");
		Category fun = category(a, "娯楽");
		spendIn(a, food, p(2025, Season.WINTER), 100_000);
		budget(a, daily, AUTUMN_2026, 8_000);
		budget(a, fun, WINTER_2026, 5_000);

		AutoBudgetView ok = propose(a, WINTER_2026, ProposalChoice.OK, NOV_11_2026);
		AutoBudgetView save = propose(a, WINTER_2026, ProposalChoice.SAVE, NOV_11_2026);
		AutoBudgetView extra = propose(a, WINTER_2026, ProposalChoice.EXTRA, NOV_11_2026);
		AutoBudgetView saveAgain = propose(a, WINTER_2026, ProposalChoice.SAVE, NOV_11_2026);

		assertThat(rowOf(ok, food).amount()).isEqualTo(100_000L);
		assertThat(rowOf(save, food).amount()).isEqualTo(90_000L);
		assertThat(rowOf(extra, food).amount()).isEqualTo(110_000L);
		assertThat(rowOf(save, daily).amount()).isEqualTo(8_000L);      // 7,200 → 8,000
		assertThat(rowOf(extra, daily).amount()).isEqualTo(9_000L);     // 8,800 → 9,000
		assertThat(rowOf(save, fun).amount()).isEqualTo(5_000L);
		assertThat(rowOf(extra, fun).amount()).isEqualTo(5_000L);
		assertThat(save.choice()).isEqualTo(ProposalChoice.SAVE);
		assertThat(extra.choice()).isEqualTo(ProposalChoice.EXTRA);
		assertThat(totalOf(saveAgain)).isEqualTo(totalOf(save));   // 2 回目も 81% にならない
		assertThat(totalOf(save)).isEqualTo(90_000L + 8_000L + 5_000L);
		assertThat(totalOf(extra)).isEqualTo(110_000L + 9_000L + 5_000L);
	}

	/** 行は、科目の登録順 */
	@Test
	void rowsAreInRegistrationOrder() {
		Owner a = createOwner("autosvc35");
		Category first = category(a, "交通費");
		Category second = category(a, "食費");
		Category third = category(a, "雑費");

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(view.rows()).extracting(AutoBudgetView.Row::categoryId)
				.containsExactly(first.getId(), second.getId(), third.getId());
		assertThat(view.rows()).extracting(AutoBudgetView.Row::categoryName).containsExactly("交通費", "食費", "雑費");
	}

	// ================================================================
	// 範囲の端・範囲の外
	// ================================================================

	@Test
	void outOfRangeSeasonOrNullIsNotFound() {
		Owner a = createOwner("autosvc36");

		assertThatThrownBy(() -> service.propose(a.id(), p(1999, Season.WINTER), ProposalChoice.OK, NOV_11_2026))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.propose(a.id(), p(2101, Season.SPRING), ProposalChoice.OK, NOV_11_2026))
				.isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.propose(a.id(), null, ProposalChoice.OK, NOV_11_2026))
				.isInstanceOf(NotFoundException.class);
	}

	/** 2000 年 春（最初の季節）：直前の季節も過去の同じ季節もない。全科目が④（Q019-3） */
	@Test
	void theFirstSeasonHasNothingToUse() {
		Owner a = createOwner("autosvc37");
		Category food = category(a, "食費");
		budget(a, food, p(2000, Season.SPRING), 9_000);

		AutoBudgetView view = proposeOk(a, p(2000, Season.SPRING), NOV_11_2026);

		assertThat(view.previousSeason()).isNull();
		assertThat(view.previousBudgetTotal()).isNull();
		assertThat(view.hasRecordYears()).isFalse();
		assertThat(view.noMaterial()).isTrue();
		assertThat(rowOf(view, food).rule()).isEqualTo(4);
		assertThat(rowOf(view, food).amount()).isEqualTo(9_000L);
	}

	/** 2000 年 夏：過去の同じ季節は範囲の外だけ。それでも直前の季節（2000 春）の予算で③の案が入る（Q019-3） */
	@Test
	void whenOnlyOutOfRangeHistoryExistsRuleThreeAndFourStillWork() {
		Owner a = createOwner("autosvc38");
		Category daily = category(a, "日用品");
		Category misc = category(a, "雑費");
		budget(a, daily, p(2000, Season.SPRING), 8_000);

		AutoBudgetView view = proposeOk(a, p(2000, Season.SUMMER), NOV_11_2026);

		assertThat(view.hasRecordYears()).isFalse();
		assertThat(view.previousSeason()).isEqualTo(p(2000, Season.SPRING));
		assertThat(rowOf(view, daily).rule()).isEqualTo(3);
		assertThat(rowOf(view, daily).amount()).isEqualTo(8_000L);
		assertThat(rowOf(view, misc).rule()).isEqualTo(4);
		assertThat(view.noMaterial()).isFalse();
	}

	// ================================================================
	// 他人のデータが入らない（3.9）／保存されない
	// ================================================================

	@Test
	void otherUsersTransactionsAndBudgetsAreNeverUsed() {
		Owner a = createOwner("autosvc39");
		Owner b = createOwner("autosvc40");
		Category aFood = category(a, "食費");
		Category bFood = category(b, "食費");
		// b：過去の冬の取引、直前の季節の取引と予算、画面の季節の予算
		spendIn(b, bFood, p(2025, Season.WINTER), 900_000);
		spendIn(b, bFood, p(2024, Season.WINTER), 900_000);
		spendIn(b, bFood, AUTUMN_2026, 700_000);
		budget(b, bFood, AUTUMN_2026, 800_000);
		budget(b, bFood, WINTER_2026, 600_000);

		AutoBudgetView forA = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(forA.rows()).hasSize(1);
		assertThat(forA.hasRecordYears()).isFalse();
		assertThat(forA.previousActualTotal()).isZero();
		assertThat(forA.previousBudgetTotal()).isNull();
		assertThat(forA.previousPaceTotal()).isNull();
		assertThat(forA.noMaterial()).isTrue();
		assertThat(totalOf(forA)).isZero();
		assertThat(rowOf(forA, aFood).rule()).isEqualTo(4);
		assertThat(rowOf(forA, aFood).amount()).isZero();
	}

	@Test
	void eachUserGetsOnlyTheirOwnNumbersWhenBothHaveData() {
		Owner a = createOwner("autosvc41");
		Owner b = createOwner("autosvc42");
		Category aFood = category(a, "食費");
		Category bFood = category(b, "食費");
		spendIn(a, aFood, p(2025, Season.WINTER), 10_000);
		spendIn(b, bFood, p(2025, Season.WINTER), 70_000);

		AutoBudgetView forA = proposeOk(a, WINTER_2026, NOV_11_2026);
		AutoBudgetView forB = proposeOk(b, WINTER_2026, NOV_11_2026);

		assertThat(rowOf(forA, aFood).amount()).isEqualTo(10_000L);
		assertThat(totalOf(forA)).isEqualTo(10_000L);
		assertThat(rowOf(forB, bFood).amount()).isEqualTo(70_000L);
		assertThat(totalOf(forB)).isEqualTo(70_000L);
	}

	/** 案を作っても保存しない：予算の行も取引の行も増えず、画面の季節の保存済みの額も変わらない */
	@Test
	void proposingDoesNotWriteAnything() {
		Owner a = createOwner("autosvc43");
		Category food = category(a, "食費");
		Category fun = category(a, "娯楽");
		spendIn(a, food, p(2025, Season.WINTER), 40_000);
		budget(a, fun, WINTER_2026, 5_000);
		budget(a, food, AUTUMN_2026, 30_000);
		int budgetsBefore = budgetCount(a);
		int transactionsBefore = transactionCount(a);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);
		propose(a, WINTER_2026, ProposalChoice.SAVE, NOV_11_2026);
		propose(a, p(2027, Season.SPRING), ProposalChoice.EXTRA, NOV_11_2026);

		assertThat(view.rows()).hasSize(2);                                  // 案はできている
		assertThat(rowOf(view, food).amount()).isEqualTo(40_000L);
		assertThat(budgetCount(a)).isEqualTo(budgetsBefore).isEqualTo(2);
		assertThat(transactionCount(a)).isEqualTo(transactionsBefore).isEqualTo(1);
		List<Budget> winter = budgetRepository.findByUserIdAndSeasonYearAndSeason(a.id(), 2026, Season.WINTER);
		assertThat(winter).hasSize(1);                                       // 食費の冬の行は作られない
		assertThat(winter.get(0).getAmount()).isEqualTo(5_000);
	}

	// ================================================================
	// 設計書 5.14 の「計算の合った例」（2026/11/11 に 2026 年 冬を開く）
	// ================================================================

	/** 科目 6 つ。過去の冬の実績（2023・2024・2025）、直前（秋 2026）の予算（null ＝ 未設定）と実績、冬 2026 に保存済みの額 */
	private record Fixture(String name, int w2023, int w2024, int w2025, Integer autumnBudget, int autumnActual,
			Integer savedWinter) {
	}

	private static final List<Fixture> DESIGN_EXAMPLE = List.of(
			new Fixture("食費", 84_000, 96_000, 90_000, 95_000, 49_200, null),
			new Fixture("交通費", 41_000, 38_000, 33_000, 40_000, 5_600, null),
			new Fixture("雑費", 30_000, 24_000, 18_000, 30_000, 22_000, null),
			new Fixture("医療費", 0, 0, 0, null, 3_600, null),
			new Fixture("日用品", 0, 0, 0, 8_000, 0, null),
			new Fixture("娯楽", 0, 0, 0, null, 0, 5_000));

	/** 設計書の例のデータを作る。T1＝570,000、T2＝600,000、T3＝630,000 になるよう、食費の冬以外の季節で調整する */
	private Owner createDesignExample(String username, List<Category> categories) {
		Owner owner = createOwner(username);
		for (Fixture f : DESIGN_EXAMPLE) {
			categories.add(category(owner, f.name()));
		}
		List<SeasonPeriod> seasons = twelveSeasons();
		// 過去の冬（添字 1＝2023 冬、5＝2024 冬、9＝2025 冬）。0 円の科目は取引を入れない
		for (int i = 0; i < DESIGN_EXAMPLE.size(); i++) {
			Fixture f = DESIGN_EXAMPLE.get(i);
			if (f.w2023() > 0) {
				spendIn(owner, categories.get(i), seasons.get(1), f.w2023());
				spendIn(owner, categories.get(i), seasons.get(5), f.w2024());
				spendIn(owner, categories.get(i), seasons.get(9), f.w2025());
			}
		}
		// 冬以外の 9 季節（食費）：T1 の残り 415,000、T2 の残り 442,000、T3 の残り 489,000
		Category food = categories.get(0);
		int[] others = { 138_000, 138_000, 139_000, 147_000, 147_000, 148_000, 163_000, 163_000, 163_000 };
		int[] indexes = { 0, 2, 3, 4, 6, 7, 8, 10, 11 };
		for (int i = 0; i < indexes.length; i++) {
			spendIn(owner, food, seasons.get(indexes[i]), others[i]);
		}
		// 直前の季節（秋 2026）の予算と実績、冬 2026 に保存済みの額
		for (int i = 0; i < DESIGN_EXAMPLE.size(); i++) {
			Fixture f = DESIGN_EXAMPLE.get(i);
			if (f.autumnBudget() != null) {
				budget(owner, categories.get(i), AUTUMN_2026, f.autumnBudget());
			}
			if (f.autumnActual() > 0) {
				spendIn(owner, categories.get(i), AUTUMN_2026, f.autumnActual());
			}
			if (f.savedWinter() != null) {
				budget(owner, categories.get(i), WINTER_2026, f.savedWinter());
			}
		}
		return owner;
	}

	@Test
	void designExampleOk() {
		List<Category> categories = new ArrayList<>();
		Owner a = createDesignExample("autosvc44", categories);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		// 全体
		assertThat(view.choice()).isEqualTo(ProposalChoice.OK);
		assertThat(view.previousSeason()).isEqualTo(AUTUMN_2026);
		assertThat(view.previousBudgetTotal()).isEqualTo(173_000L);          // 95,000 ＋ 40,000 ＋ 30,000 ＋ 8,000
		assertThat(view.previousActualTotal()).isEqualTo(80_400L);
		assertThat(view.previousContainsToday()).isTrue();
		assertThat(view.previousPaceTotal()).isEqualTo(101_700L);            // 80,400 × 91 ÷ 72 ＝ 101,616.6… → 101,700
		assertThat(view.screenSeason()).isEqualTo(Season.WINTER);
		assertThat(view.hasRecordYears()).isTrue();
		assertThat(view.trendPercent()).isEqualTo(110);
		assertThat(view.trendKind()).isEqualTo(TrendKind.UP);
		assertThat(view.trendCapped()).isFalse();
		assertThat(totalOf(view)).isEqualTo(186_000L);
		assertThat(view.noMaterial()).isFalse();

		// ルールと案
		assertThat(view.rows()).extracting(AutoBudgetView.Row::rule).containsExactly(1, 1, 1, 2, 3, 4);
		assertThat(view.rows()).extracting(AutoBudgetView.Row::amount)
				.containsExactly(99_000L, 42_000L, 27_000L, 5_000L, 8_000L, 5_000L);
		assertThat(view.rows()).extracting(AutoBudgetView.Row::categoryName)
				.containsExactly("食費", "交通費", "雑費", "医療費", "日用品", "娯楽");

		// 表の「このペースなら」（実績 0 円の日用品・娯楽は出さない）
		assertThat(view.rows()).extracting(AutoBudgetView.Row::paceAmount)
				.containsExactly(62_200L, 7_100L, 27_900L, 4_600L, null, null);
		assertThat(view.rows()).extracting(AutoBudgetView.Row::previousActual)
				.containsExactly(49_200L, 5_600L, 22_000L, 3_600L, 0L, 0L);
		assertThat(view.rows()).extracting(AutoBudgetView.Row::previousBudget)
				.containsExactly(95_000L, 40_000L, 30_000L, null, 8_000L, null);

		// 案の根拠
		AutoBudgetView.Row food = view.rows().get(0);
		assertThat(food.basisKind()).isEqualTo(BasisKind.MEDIAN);
		assertThat(food.years()).containsExactly(2023, 2024, 2025);
		assertThat(food.baseAmount()).isEqualTo(90_000L);
		assertThat(view.rows().get(3).basisKind()).isEqualTo(BasisKind.PACE);
		assertThat(view.rows().get(3).baseAmount()).isEqualTo(4_600L);
		assertThat(view.rows().get(4).basisKind()).isEqualTo(BasisKind.BUDGET);
		assertThat(view.rows().get(5).basisKind()).isEqualTo(BasisKind.NONE);
		assertThat(view.rows().get(5).baseAmount()).isEqualTo(5_000L);
	}

	@Test
	void designExampleSaveAndExtra() {
		List<Category> categories = new ArrayList<>();
		Owner a = createDesignExample("autosvc45", categories);

		AutoBudgetView save = propose(a, WINTER_2026, ProposalChoice.SAVE, NOV_11_2026);
		AutoBudgetView extra = propose(a, WINTER_2026, ProposalChoice.EXTRA, NOV_11_2026);

		assertThat(save.rows()).extracting(AutoBudgetView.Row::amount)
				.containsExactly(90_000L, 38_000L, 24_000L, 5_000L, 8_000L, 5_000L);
		assertThat(totalOf(save)).isEqualTo(170_000L);
		assertThat(extra.rows()).extracting(AutoBudgetView.Row::amount)
				.containsExactly(109_000L, 46_000L, 30_000L, 6_000L, 9_000L, 5_000L);
		assertThat(totalOf(extra)).isEqualTo(205_000L);
		// 傾向・ルールは選ぶ割合に関わらず同じ
		assertThat(save.trendPercent()).isEqualTo(110);
		assertThat(extra.rows()).extracting(AutoBudgetView.Row::rule).containsExactly(1, 1, 1, 2, 3, 4);
	}

	@Test
	void designExampleDoesNotSaveAnything() {
		List<Category> categories = new ArrayList<>();
		Owner a = createDesignExample("autosvc46", categories);
		int budgetsBefore = budgetCount(a);
		int transactionsBefore = transactionCount(a);

		AutoBudgetView view = proposeOk(a, WINTER_2026, NOV_11_2026);

		assertThat(totalOf(view)).isEqualTo(186_000L);
		assertThat(budgetCount(a)).isEqualTo(budgetsBefore);
		assertThat(transactionCount(a)).isEqualTo(transactionsBefore);
	}

}
