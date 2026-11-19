package io.github.yutaro081.householdbudget.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.ReceiptTotal;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import jakarta.persistence.EntityManager;

/**
 * 取引一覧の絞り込みの検索（issue 023。設計書 4.6、5.17、7.4）。
 * <ul>
 * <li>findForListByUserIdAndFilter：決まった JPQL 1 本で、値が null の条件では絞らない。期間が null（LocalDate の null）でも、
 * 型が決まらないエラーにならないことを、ここで確かめる</li>
 * <li>sumByReceiptGroups：同じレシートの合計と行の数を「印と user_id」で読む</li>
 * </ul>
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * テストの準備（ApplicationContext）は、TransactionRepositoryTest と同じ（自分の設定を作らない）。
 */
@SpringBootTest
@Transactional
class TransactionRepositoryFilterTest {

	private static final SeasonPeriod AUTUMN_2026 = new SeasonPeriod(2026, Season.AUTUMN);

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CategoryRepository categoryRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private PaymentMethodRepository paymentMethodRepository;
	@Autowired
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;

	private record Owner(Long userId, Category food, Category daily, Vendor vendor, PaymentMethod payment) {
	}

	private Owner a;
	private Owner b;

	@BeforeEach
	void createUsers() {
		a = createOwner("txfilt1");
		b = createOwner("txfilt2");
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, categoryRepository.save(new Category(id, "食費")), categoryRepository.save(new Category(id, "日用品")),
				vendorRepository.save(new Vendor(id, "店")), paymentMethodRepository.save(new PaymentMethod(id, "現金")));
	}

	private Long save(Owner o, LocalDate date, Category category, int amount) {
		Long id = transactionRepository.save(new Transaction(o.userId(), date, amount, category, null, o.vendor(), o.payment())).getId();
		entityManager.flush();
		entityManager.clear();
		return id;
	}

	private void setGroup(Long group, List<Long> ids) {
		entityManager.flush();
		for (Long id : ids) {
			entityManager.createNativeQuery("UPDATE transactions SET receipt_group = ?1 WHERE id = ?2")
					.setParameter(1, group).setParameter(2, id).executeUpdate();
		}
		entityManager.clear();
	}

	private List<Long> filter(Owner o, Category category, SeasonPeriod period) {
		return transactionRepository.findForListByUserIdAndFilter(o.userId(), category == null ? null : category.getId(),
				period == null ? null : period.startDate(), period == null ? null : period.endDateExclusive())
				.stream().map(Transaction::getId).toList();
	}

	private List<Long> unfiltered(Owner o) {
		return transactionRepository.findForListByUserId(o.userId()).stream().map(Transaction::getId).toList();
	}

	// ---- 値が null の条件では絞らない ----

	/** 全部 null（科目も期間も指定なし）でも動き、絞らない一覧と同じ行・同じ並び（null の型でエラーにならない） */
	@Test
	void allNullGivesTheUnfilteredList() {
		save(a, LocalDate.of(2026, 8, 31), a.food(), 1);
		save(a, LocalDate.of(2026, 9, 1), a.daily(), 2);
		save(a, LocalDate.of(2026, 12, 1), a.food(), 3);
		save(b, LocalDate.of(2026, 10, 1), b.food(), 4);

		assertThat(filter(a, null, null)).hasSize(3).isEqualTo(unfiltered(a));
	}

	/** 科目だけ（期間は null）・期間だけ（科目は null）・両方 */
	@Test
	void eachConditionNarrowsOnlyWhenGiven() {
		Long summerFood = save(a, LocalDate.of(2026, 8, 31), a.food(), 1);
		Long autumnFood = save(a, LocalDate.of(2026, 9, 1), a.food(), 2);
		Long autumnDaily = save(a, LocalDate.of(2026, 11, 30), a.daily(), 3);
		Long winterFood = save(a, LocalDate.of(2026, 12, 1), a.food(), 4);

		assertThat(filter(a, a.food(), null)).containsExactly(winterFood, autumnFood, summerFood);
		assertThat(filter(a, null, AUTUMN_2026)).containsExactly(autumnDaily, autumnFood);
		assertThat(filter(a, a.food(), AUTUMN_2026)).containsExactly(autumnFood);
		assertThat(filter(a, a.daily(), new SeasonPeriod(2026, Season.WINTER))).isEmpty();
	}

	/** 他人の行は、自分の科目の ID が入っていても（SQL で作る）、どの条件でも入らない（user_id で絞る） */
	@Test
	void otherUsersRowsAreNeverReturned() {
		Long mine = save(a, LocalDate.of(2026, 10, 1), a.food(), 1);
		Long theirs = save(b, LocalDate.of(2026, 10, 1), b.food(), 2);
		entityManager.createNativeQuery("UPDATE transactions SET category_id = ?1 WHERE id = ?2")
				.setParameter(1, a.food().getId()).setParameter(2, theirs).executeUpdate();
		entityManager.clear();

		assertThat(filter(a, a.food(), AUTUMN_2026)).containsExactly(mine);
		assertThat(filter(a, a.food(), null)).containsExactly(mine);
		assertThat(filter(a, null, AUTUMN_2026)).containsExactly(mine);
		assertThat(filter(a, null, null)).containsExactly(mine);
	}

	/** 絞った結果の並びは、絞らない一覧から同じ行だけを取り出した並びと同じ（レシートの行は隣に、その中は id の順） */
	@Test
	void theOrderIsTheSameAsTheUnfilteredList() {
		LocalDate day = LocalDate.of(2026, 11, 9);
		save(a, day, a.food(), 1);
		Long r1 = save(a, day, a.food(), 2);
		Long r2 = save(a, day, a.daily(), 3);
		Long r3 = save(a, day, a.food(), 4);
		setGroup(r1, List.of(r1, r2, r3));
		save(a, day, a.food(), 5);
		save(a, day.minusDays(1), a.food(), 6);

		List<Long> food = filter(a, a.food(), AUTUMN_2026);
		List<Long> expected = unfiltered(a).stream().filter(food::contains).toList();

		assertThat(food).hasSize(5).isEqualTo(expected);
	}

	// ---- 同じレシートの合計と行の数（印と user_id） ----

	/** 印ごとに、合計と行の数。渡した印のレシートだけ。他人の行に同じ印の値が入っていても（SQL で作る）数えない */
	@Test
	void receiptTotalsAreReadByMarkAndUser() {
		LocalDate day = LocalDate.of(2026, 11, 9);
		Long r1 = save(a, day, a.food(), 2200);
		Long r2 = save(a, day, a.daily(), 800);
		setGroup(r1, List.of(r1, r2));
		Long s1 = save(a, day, a.food(), 100);
		Long s2 = save(a, day, a.daily(), 20);
		Long s3 = save(a, day, a.food(), 3);
		setGroup(s1, List.of(s1, s2, s3));
		Long other = save(a, day, a.food(), 5000);
		setGroup(other, List.of(other, save(a, day, a.food(), 5000)));
		Long theirs = save(b, day, b.food(), 999);
		setGroup(r1, List.of(theirs));

		List<ReceiptTotal> totals = transactionRepository.sumByReceiptGroups(a.userId(), List.of(r1, s1));

		assertThat(totals).extracting(ReceiptTotal::receiptGroup, ReceiptTotal::total, ReceiptTotal::lines)
				.containsExactlyInAnyOrder(tuple(r1, 3000L, 2L), tuple(s1, 123L, 3L));
	}

}
