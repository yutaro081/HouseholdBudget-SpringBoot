package io.github.yutaro081.householdbudget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.exception.DeletionNotAllowedException;
import io.github.yutaro081.householdbudget.exception.DuplicateException;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.VendorForm;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import jakarta.persistence.EntityManager;

/**
 * VendorService のテスト（設計書 5.4、5.5、5.6、3.9）。開発用の DB につなぐが、
 * 各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 科目（CategoryServiceTest）と同じ内容。違う点：予算に関するテストはない。
 */
@SpringBootTest
@Transactional
class VendorServiceTest {

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
	private BudgetRepository budgetRepository;
	@Autowired
	private VendorService service;
	@Autowired
	private EntityManager entityManager;

	private Long createUser(String username) {
		return userRepository.save(new User(username, "dummy-hash-for-test")).getId();
	}

	private static VendorForm form(String name) {
		VendorForm form = new VendorForm();
		form.setName(name);
		return form;
	}

	private boolean exists(Long id) {
		entityManager.flush();
		entityManager.clear();
		return service.list(userIdOfAny(id)).stream().anyMatch(x -> x.getId().equals(id));
	}

	/** id の持ち主を調べる（テスト用） */
	private Long userIdOfAny(Long id) {
		return userRepository.findAll().stream()
				.filter(u -> service.list(u.getId()).stream().anyMatch(x -> x.getId().equals(id)))
				.map(User::getId).findFirst().orElse(-1L);
	}

	// ---- 一覧（登録順、自分の分だけ） ----

	@Test
	void listReturnsOwnItemsInRegistrationOrder() {
		Long a = createUser("vdrtest1");
		Long b = createUser("vdrtest2");
		service.add(a, form("コンビニ"));
		service.add(b, form("他人のもの"));
		service.add(a, form("ガソリンスタンド"));
		service.add(a, form("Amazon"));

		assertThat(service.list(a)).extracting(x -> x.getName()).containsExactly("コンビニ", "ガソリンスタンド", "Amazon");
		assertThat(service.list(b)).extracting(x -> x.getName()).containsExactly("他人のもの");
	}

	// ---- 追加 ----

	@Test
	void addSavesItForTheUserAndCreatesNoBudget() {
		Long userId = createUser("vdrtest3");
		long budgetsBefore = budgetRepository.count();

		var saved = service.add(userId, form("コンビニ"));

		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getName()).isEqualTo("コンビニ");
		assertThat(budgetRepository.count()).isEqualTo(budgetsBefore);      // 予算の行は作らない
	}

	@Test
	void addAcceptsTheMaximumLength() {
		Long userId = createUser("vdrtest4");

		assertThatCode(() -> service.add(userId, form("あ".repeat(20)))).doesNotThrowAnyException();
		assertThat(service.list(userId)).hasSize(1);
	}

	@ParameterizedTest(name = "[{0}] の後に [{1}] は重複")
	@CsvSource({
			"Amazon, AMAZON",
			"food, Food",
			"ひらがな, ヒラガナ",
			"ヒラガナ, ひらがな",
			"ｱｲｳ, アイウ",
			"アイウ, ｱｲｳ",
			"コンビニ, ｺﾝﾋﾞﾆ",
			"コンビニ, コンビニ"
	})
	void addDuplicateIsRejected(String first, String second) {
		Long userId = createUser("vdrtest5");
		service.add(userId, form(first));
		int before = service.list(userId).size();

		assertThatThrownBy(() -> service.add(userId, form(second))).isInstanceOf(DuplicateException.class);

		assertThat(service.list(userId)).hasSize(before);               // 増えない
	}

	/** 濁点の違いは別の名前（設計書 3.12） */
	@Test
	void addWithDifferentVoicedMarkIsAllowed() {
		Long userId = createUser("vdrtest6");
		service.add(userId, form("カ"));

		assertThatCode(() -> service.add(userId, form("ガ"))).doesNotThrowAnyException();
		assertThat(service.list(userId)).extracting(x -> x.getName()).containsExactly("カ", "ガ");
	}

	/** 同じ名前でも、別のユーザーなら追加できる（ユーザーごとに持つ） */
	@Test
	void sameNameIsAllowedForDifferentUsers() {
		Long a = createUser("vdrtest7");
		Long b = createUser("vdrtest8");
		service.add(a, form("コンビニ"));

		assertThatCode(() -> service.add(b, form("コンビニ"))).doesNotThrowAnyException();
		assertThat(service.list(b)).extracting(x -> x.getName()).containsExactly("コンビニ");
	}

	// ---- 変更 ----

	@Test
	void updateChangesTheName() {
		Long userId = createUser("vdrtest9");
		var item = service.add(userId, form("古い名前"));

		service.update(userId, item.getId(), form("新しい名前"));

		assertThat(service.get(userId, item.getId()).getName()).isEqualTo("新しい名前");
	}

	@Test
	void updateWithTheSameNameIsNotADuplicate() {
		Long userId = createUser("vdrtest10");
		var item = service.add(userId, form("コンビニ"));

		assertThatCode(() -> service.update(userId, item.getId(), form("コンビニ"))).doesNotThrowAnyException();
		assertThat(service.get(userId, item.getId()).getName()).isEqualTo("コンビニ");
	}

	/** 大文字・小文字だけの変更は、自分自身なので重複にならない */
	@Test
	void updateCaseOnlyChangeIsAllowed() {
		Long userId = createUser("vdrtest11");
		var item = service.add(userId, form("Amazon"));

		assertThatCode(() -> service.update(userId, item.getId(), form("AMAZON"))).doesNotThrowAnyException();
		assertThat(service.get(userId, item.getId()).getName()).isEqualTo("AMAZON");
	}

	@Test
	void updateToAnotherItemsNameIsRejected() {
		Long userId = createUser("vdrtest12");
		service.add(userId, form("コンビニ"));
		var other = service.add(userId, form("ガソリンスタンド"));

		assertThatThrownBy(() -> service.update(userId, other.getId(), form("コンビニ"))).isInstanceOf(DuplicateException.class);
		// 拒否されたあとは、名前は変わっていない。別の名前になら変更できる
		assertThat(service.get(userId, other.getId()).getName()).isEqualTo("ガソリンスタンド");
		assertThatCode(() -> service.update(userId, other.getId(), form("別の名前"))).doesNotThrowAnyException();
		assertThat(service.get(userId, other.getId()).getName()).isEqualTo("別の名前");
	}

	@Test
	void updateToDifferentUsersNameIsAllowed() {
		Long a = createUser("vdrtest13");
		Long b = createUser("vdrtest14");
		service.add(a, form("コンビニ"));
		var mine = service.add(b, form("別の名前"));

		assertThatCode(() -> service.update(b, mine.getId(), form("コンビニ"))).doesNotThrowAnyException();
	}

	// ---- 他人のID・存在しないID（3.9、7.8） ----

	@Test
	void otherUsersItemIsNotFound() {
		Long a = createUser("vdrtest15");
		Long b = createUser("vdrtest16");
		var aItem = service.add(a, form("Aのもの"));
		Long id = aItem.getId();

		assertThatThrownBy(() -> service.get(b, id)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(b, id, form("書き換え"))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(b, id)).isInstanceOf(NotFoundException.class);

		// 他人のデータは、変わっていない
		assertThat(service.get(a, id).getName()).isEqualTo("Aのもの");
		assertThat(exists(id)).isTrue();
	}

	@Test
	void nonexistentIdIsNotFound() {
		Long userId = createUser("vdrtest17");
		Long missing = 999_999_999L;

		assertThatThrownBy(() -> service.get(userId, missing)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.update(userId, missing, form("x"))).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.delete(userId, missing)).isInstanceOf(NotFoundException.class);
	}

	// ---- 削除 ----

	@Test
	void deleteRemovesTheItem() {
		Long userId = createUser("vdrtest18");
		var item = service.add(userId, form("消すもの"));

		service.delete(userId, item.getId());

		assertThat(service.list(userId)).isEmpty();
	}

	@Test
	void deleteIsRejectedWhenUsedByATransaction() {
		Long userId = createUser("vdrtest19");
		var item = service.add(userId, form("コンビニ"));
		saveTransactionUsing(userId, item.getId());

		assertThatThrownBy(() -> service.delete(userId, item.getId()))
				.isInstanceOfSatisfying(DeletionNotAllowedException.class,
						e -> assertThat(e.getMessageKey()).isEqualTo("error.delete.inUse"));

		assertThat(service.list(userId)).hasSize(1);                     // 残っている
	}

	/** 同じユーザーの別のもの（使われていないもの）は、削除できる */
	@Test
	void unusedItemOfTheSameUserCanBeDeleted() {
		Long userId = createUser("vdrtest20");
		var used = service.add(userId, form("使用中"));
		var unused = service.add(userId, form("未使用"));
		saveTransactionUsing(userId, used.getId());

		assertThatCode(() -> service.delete(userId, unused.getId())).doesNotThrowAnyException();

		assertThat(service.list(userId)).extracting(x -> x.getName()).containsExactly("使用中");
	}

	/** 他のユーザーの取引は、削除の判定に関係しない */
	@Test
	void otherUsersTransactionDoesNotAffectDeletion() {
		Long a = createUser("vdrtest21");
		Long b = createUser("vdrtest22");
		var aItem = service.add(a, form("コンビニ"));
		var bItem = service.add(b, form("コンビニ"));
		saveTransactionUsing(b, bItem.getId());                          // B のものは使用中

		assertThatCode(() -> service.delete(a, aItem.getId())).doesNotThrowAnyException();

		assertThat(service.list(a)).isEmpty();
		assertThat(service.list(b)).hasSize(1);                          // B のものは、そのまま
	}

	/** このテストが扱う種類(取引先)のIDを使って、取引を1件作る（ほかの2種類は新しく作る） */
	private void saveTransactionUsing(Long userId, Long targetId) {
		Category category = categoryRepository.save(new Category(userId, "科目"));
		PaymentMethod paymentMethod = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		Vendor vendor = vendorRepository.findById(targetId).orElseThrow();
		transactionRepository.save(new Transaction(userId, LocalDate.of(2026, 10, 27), 1000, category, null, vendor, paymentMethod));
		entityManager.flush();
	}

}
