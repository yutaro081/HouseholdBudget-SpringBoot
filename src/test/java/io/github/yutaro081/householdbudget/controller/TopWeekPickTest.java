package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.domain.Season;
import io.github.yutaro081.householdbudget.entity.Budget;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.BudgetRepository;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * トップの「今週の科目のひとこと」（要件定義書 4.5、設計書 5.15、issue 020）の確かめ。
 * 「今日」は 2026-11-11（水曜。秋の 72 日目。D ＝ 91、L ＝ 19。今週は 11/8〜11/14）に固定する（FixedClock20261111Config）。
 * 週の予算 ＝ 季節の予算 ÷ 13（100 円単位に切り上げ）。季節の見込み ＝ 切り上げ 100（（実績 × 91 × 110 ＋ 予算 × 361）÷ 8,281）。
 * 食費（季節 130,000 → 週 10,000）、交通費（季節 65,000 → 週 5,000）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClock20261111Config.class)
@Transactional
class TopWeekPickTest {

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
	private static final String SENTENCE_START = "今週いちばん進んでいる";

	@Autowired
	private MockMvc mockMvc;
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
	private EntityManager entityManager;

	private Long userId;
	private Vendor vendor;
	private PaymentMethod payment;
	private RequestPostProcessor login;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("weekpick1", "dummy-hash-for-test")).getId();
		vendor = vendorRepository.save(new Vendor(userId, "取引先"));
		payment = paymentMethodRepository.save(new PaymentMethod(userId, "支払方法"));
		login = user(new LoginUser(userId, "weekpick1", "x"));
	}

	private Category category(String name, Integer seasonBudget) {
		Category c = categoryRepository.save(new Category(userId, name));
		if (seasonBudget != null) {
			budgetRepository.save(new Budget(userId, c, 2026, Season.AUTUMN, seasonBudget));
		}
		return c;
	}

	private void spend(Category c, LocalDate date, int amount) {
		transactionRepository.save(new Transaction(userId, date, amount, c, null, vendor, payment));
	}

	/** 「記録は、ずっと昔に始めた」状態にする：昔の日付に 1 円の取引（最初の取引の日が季節の途中だと、見込みの数え始めの日が変わるため） */
	private void recordingStartedLongAgo(Category c) {
		spend(c, LocalDate.of(2001, 3, 1), 1);
	}

	private String page(String path) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get(path).with(login)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	/** 食費（週の予算 10,000 円。30％）と交通費（週の予算 5,000 円。50％）を作る。交通費がいちばん高い */
	private void foodAndFare() {
		Category food = category("食費", 130_000);
		Category fare = category("交通費", 65_000);
		recordingStartedLongAgo(food);
		spend(food, SUNDAY, 3_000);
		spend(fare, SUNDAY, 2_500);
	}

	@Test
	void theSentenceNamesTheCategoryWithTheHighestWeeklyRatioAndItsSeasonForecast() throws Exception {
		foodAndFare();

		String body = page("/");

		// 交通費の季節の見込み：（2,500 × 10,010 ＋ 65,000 × 361）÷ 8,281 ＝ 5,855.6… → 5,900。予算 65,000 円の 9％（9.08％）
		assertThat(body).contains("今週いちばん進んでいる交通費は、季節では約 5,900円（予算の 9%）の見込みです。");
		assertThat(body).as("食費（30％）ではない").doesNotContain(SENTENCE_START + "食費");
	}

	/** 季節の見込みは、予実比較の科目ごとの見込みと同じ値 */
	@Test
	void theSeasonForecastIsTheSameValueAsTheComparisonScreen() throws Exception {
		foodAndFare();

		String comparison = page("/comparison");

		assertThat(comparison).contains("<span>5,900</span><span class=\"cell-sub\"> / 65,000</span>");
	}

	/** 置き場所（issue 020 の見直し）：「今週の進み具合」のメーターのすぐ下。注記（「点線：今日／実線：予算」）より上 */
	@Test
	void theSentenceIsRightBelowTheWeeklyMetersAndAboveTheirLegend() throws Exception {
		foodAndFare();

		String body = page("/");
		int sentence = body.indexOf("class=\"top-week-pick\"");

		assertThat(sentence).isGreaterThanOrEqualTo(0);
		assertThat(sentence).as("今週のメーターの下").isGreaterThan(body.indexOf("class=\"top-weeks"));
		assertThat(sentence).as("メーター（科目の行）の下").isGreaterThan(body.indexOf("</ul>", body.indexOf("class=\"top-weeks")));
		assertThat(sentence).as("注記より上").isLessThan(body.indexOf("点線：今日／実線：予算"));
		assertThat(body.split("class=\"top-week-pick\"", -1).length - 1).as("1 つだけ").isEqualTo(1);
	}

	/** 「きょうのひとこと」の行（「今週はまだ…」）と同じように、左に小さなアイコンの四角（hitokoto-icon。棒グラフのアイコン）を付ける */
	@Test
	void theSentenceHasASmallIconLikeTheHitokotoRows() throws Exception {
		foodAndFare();

		String body = page("/");
		int row = body.indexOf("class=\"top-week-pick-row hitokoto-row\"");

		assertThat(row).isGreaterThanOrEqualTo(0);
		String segment = body.substring(row, body.indexOf("class=\"top-week-pick\"", row) + 30);
		assertThat(segment).contains("<span class=\"hitokoto-icon\"><svg", "class=\"hitokoto-body\"");
		assertThat(segment.indexOf("hitokoto-icon")).as("アイコンは文の左（前）").isLessThan(segment.indexOf("top-week-pick\""));
	}

	/** 週の予算を超えていなくても出す。超えているときも出す */
	@Test
	void theSentenceIsShownEvenWhenNothingIsOverTheWeeklyBudget() throws Exception {
		Category food = category("食費", 130_000);
		recordingStartedLongAgo(food);
		spend(food, SUNDAY, 100);                  // 週の予算 10,000 円の 1％

		assertThat(page("/")).contains(SENTENCE_START + "食費は、");
	}

	@Test
	void theSentenceIsShownWhenTheWeeklyBudgetIsExceeded() throws Exception {
		Category food = category("食費", 10_000);  // 週の予算 800 円
		recordingStartedLongAgo(food);
		spend(food, SUNDAY, 12_000);

		// 季節の見込み：（12,000 × 10,010 ＋ 10,000 × 361）÷ 8,281 ＝ 14,941.0… → 15,000（予算の 150％）
		assertThat(page("/")).contains("今週いちばん進んでいる食費は、季節では約 15,000円（予算の 150%）の見込みです。");
	}

	/** 割合が同じなら、科目の並び順で先のもの */
	@Test
	void whenTheRatiosAreEqualTheEarlierCategoryIsNamed() throws Exception {
		Category food = category("食費", 130_000);
		Category fare = category("交通費", 65_000);
		recordingStartedLongAgo(food);
		spend(food, SUNDAY, 5_000);                // 10,000 円の 50％
		spend(fare, SUNDAY, 2_500);                // 5,000 円の 50％

		assertThat(page("/")).contains(SENTENCE_START + "食費は、").doesNotContain(SENTENCE_START + "交通費");
	}

	/** 今週の取引が 1 件もないとき（先週の取引だけ）は出さない */
	@Test
	void noSentenceWhenThereIsNoTransactionThisWeek() throws Exception {
		Category food = category("食費", 130_000);
		recordingStartedLongAgo(food);
		spend(food, LocalDate.of(2026, 11, 7), 3_000);       // 土曜（先週）

		assertThat(page("/")).doesNotContain("top-week-pick").doesNotContain(SENTENCE_START);
	}

	@Test
	void noSentenceWhenThereAreNoTransactionsAtAll() throws Exception {
		category("食費", 130_000);

		assertThat(page("/")).doesNotContain("top-week-pick");
	}

	/** 週の予算が 0 円・未設定の科目は除く。予算のある科目が今週使っていなければ、出さない */
	@Test
	void categoriesWithoutAWeeklyBudgetAreLeftOutAndNothingIsSaidIfNoBudgetedCategorySpent() throws Exception {
		Category zero = category("ゼロ", 0);
		Category unset = category("未設定", null);
		category("食費", 130_000);
		spend(zero, SUNDAY, 9_000);
		spend(unset, SUNDAY, 9_000);

		assertThat(page("/")).doesNotContain("top-week-pick");
	}

	@Test
	void budgetlessCategoriesDoNotHideTheOneWithABudget() throws Exception {
		Category zero = category("ゼロ", 0);
		Category food = category("食費", 130_000);
		recordingStartedLongAgo(food);
		spend(zero, SUNDAY, 9_000);
		spend(food, SUNDAY, 100);

		assertThat(page("/")).contains(SENTENCE_START + "食費は、");
	}

	/** 科目名は th:text で出す：HTML を入れても、画面にタグとしては出ない（XSS。設計書 7.5） */
	@Test
	void theCategoryNameIsEscaped() throws Exception {
		Category tagged = category("<b>x</b>", 130_000);
		recordingStartedLongAgo(tagged);
		spend(tagged, SUNDAY, 3_000);

		String body = page("/");

		assertThat(body).contains(SENTENCE_START + "&lt;b&gt;x&lt;/b&gt;は、");
		assertThat(body).doesNotContain("<b>x</b>");
	}

	@Test
	void theSentenceHasNoColorClassAndNoAlertWords() throws Exception {
		foodAndFare();

		String body = page("/");
		int start = body.indexOf("class=\"top-week-pick\"");
		String sentence = body.substring(start, body.indexOf("</p>", start));

		assertThat(sentence).doesNotContain("week-over", "week-low", "text-danger", "alert", "color:");
		assertThat(sentence).doesNotContain("超えそう", "超えます", "着地", "予測");
	}

	/** 他の人のデータは使わない */
	@Test
	void otherUsersDataIsNeverUsed() throws Exception {
		foodAndFare();
		Long otherId = userRepository.save(new User("weekpick2", "dummy-hash-for-test")).getId();
		Category other = categoryRepository.save(new Category(otherId, "他人の科目"));
		budgetRepository.save(new Budget(otherId, other, 2026, Season.AUTUMN, 13_000));
		transactionRepository.save(new Transaction(otherId, SUNDAY, 9_000, other, null,
				vendorRepository.save(new Vendor(otherId, "取引先")), paymentMethodRepository.save(new PaymentMethod(otherId, "支払方法"))));

		String body = page("/");

		assertThat(body).contains(SENTENCE_START + "交通費は、").doesNotContain("他人の科目");
	}
}
