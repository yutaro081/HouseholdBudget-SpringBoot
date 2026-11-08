package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.config.AppConfig;
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
 * トップの「きょうのひとこと」の画面のテスト（設計書 2.3、5.12、7.5、要件定義書 4.5）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は、テストごとに日本時間の日付を決められる Clock で固定する。
 * 見た目（枠の色、線、余白）は MockMvc では見えないので、ブラウザで確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TopHitokotoControllerTest {

	/** 日付を決められる Clock（日本時間） */
	static class SettableClock extends Clock {
		private volatile LocalDate date = LocalDate.of(2026, 11, 8);

		void set(LocalDate date) {
			this.date = date;
		}

		@Override
		public ZoneId getZone() {
			return AppConfig.JAPAN;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return date.atStartOfDay(AppConfig.JAPAN).toInstant();
		}
	}

	@TestConfiguration
	static class SettableClockConfig {
		@Bean
		@Primary
		SettableClock settableClock() {
			return new SettableClock();
		}
	}

	private static final String HEADING = "きょうのひとこと";

	@Autowired
	private SettableClock clock;
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

	@BeforeEach
	void resetDate() {
		clock.set(LocalDate.of(2026, 11, 8));
	}

	private record Owner(Long id, RequestPostProcessor login, Vendor vendor, PaymentMethod paymentMethod) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, user(new LoginUser(id, username, "x")),
				vendorRepository.save(new Vendor(id, "取引先")),
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

	private String topBody(Owner o) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get("/").with(o.login()))
				.andExpect(status().isOk())
				.andExpect(view().name("top/index"))
				.andReturn().getResponse().getContentAsString();
	}

	/** 枠（<section class="hitokoto-frame">）の中身だけを取り出す */
	private static String frame(String body) {
		int start = body.indexOf("hitokoto-frame");
		assertThat(start).as("きょうのひとことの枠がある").isGreaterThanOrEqualTo(0);
		start = body.lastIndexOf("<section", start);
		int end = body.indexOf("</section>", start);
		return body.substring(start, end);
	}

	// ---- 位置・見出し ----

	@Test
	void theFrameIsBetweenThePieAndTheWeeklyMeters() throws Exception {
		Owner a = createOwner("hitokoto01");
		Category food = category(a, "食費", 30_000);
		spend(a, food, LocalDate.of(2026, 11, 8), 1_234);

		String body = topBody(a);

		int pie = body.indexOf("今週 何に使ったか");
		int frame = body.indexOf(HEADING);
		int weeks = body.indexOf("今週の進み具合");
		assertThat(pie).isGreaterThanOrEqualTo(0);
		assertThat(frame).isGreaterThan(pie);
		assertThat(weeks).isGreaterThan(frame);
		assertThat(frame(body)).contains("<h2");
	}

	// ---- 暦のひとこと ----

	@Test
	void anOrdinaryDayShowsTheKoWithItsKindNameReadingAndTheNextSekki() throws Exception {
		Owner a = createOwner("hitokoto02");
		category(a, "食費", 30_000);

		String frame = frame(topBody(a));

		assertThat(frame).contains("七十二候（立冬の初候）", "山茶始開", "つばきはじめてひらく");
		assertThat(frame).contains("冬のはじまりに合わせて、サザンカの花が咲きはじめます。");
		assertThat(frame).contains("次の節気：小雪まであと14日");
	}

	@Test
	void aSekkiDayShowsTheSekkiTextAndNoNextSekki() throws Exception {
		clock.set(LocalDate.of(2026, 11, 7));
		Owner a = createOwner("hitokoto03");
		category(a, "食費", 30_000);

		String frame = frame(topBody(a));

		assertThat(frame).contains("二十四節気（立冬）", "今日は立冬。");
		assertThat(frame).doesNotContain("山茶始開").doesNotContain("次の節気");
	}

	@Test
	void whenAnEventAndASekkiFallOnTheSameDayTheEventIsFirst() throws Exception {
		clock.set(LocalDate.of(2026, 7, 7));
		Owner a = createOwner("hitokoto04");
		category(a, "食費", 30_000);

		String frame = frame(topBody(a));

		assertThat(frame).contains("年中行事", "今日は七夕。", "二十四節気", "今日は小暑。");
		assertThat(frame.indexOf("今日は七夕。")).isLessThan(frame.indexOf("今日は小暑。"));
		assertThat(frame).doesNotContain("七十二候");
	}

	@Test
	void anEventDayShowsNoKo() throws Exception {
		clock.set(LocalDate.of(2026, 1, 1));
		Owner a = createOwner("hitokoto05");
		category(a, "食費", null);

		String frame = frame(topBody(a));

		assertThat(frame).contains("あけましておめでとうございます。").doesNotContain("雪下出麦");
	}

	@Test
	void newYearsEveShowsTheEventAndTheNextSekkiAcrossTheYear() throws Exception {
		clock.set(LocalDate.of(2026, 12, 31));
		Owner a = createOwner("hitokoto15");
		category(a, "食費", null);

		String frame = frame(topBody(a));

		assertThat(frame).contains("年中行事", "今日は大晦日。", "次の節気：小寒まであと5日");
		assertThat(frame).doesNotContain("七十二候");
	}

	@Test
	void aDateOutsideTheTableStillShowsTheKoButNoNextSekki() throws Exception {
		clock.set(LocalDate.of(2028, 3, 1));
		Owner a = createOwner("hitokoto16");
		category(a, "食費", null);

		String frame = frame(topBody(a));

		assertThat(frame).contains("七十二候", "草木萌動");
		assertThat(frame).doesNotContain("次の節気");
	}

	@Test
	void anEventAndASekkiOnTheSameDayInGoLeaveTheTangoFirst() throws Exception {
		clock.set(LocalDate.of(2026, 5, 5));
		Owner a = createOwner("hitokoto17");
		category(a, "食費", null);

		String frame = frame(topBody(a));

		assertThat(frame.indexOf("今日は端午の節句。")).isGreaterThanOrEqualTo(0)
				.isLessThan(frame.indexOf("今日は立夏。"));
	}

	// ---- グラフへのひとこと ----

	@Test
	void showsOneGraphCommentMadeFromTheWeeklyNumbers() throws Exception {
		Owner a = createOwner("hitokoto06");
		Category food = category(a, "食費", 30_000);
		spend(a, food, LocalDate.of(2026, 11, 8), 1_234);

		String frame = frame(topBody(a));

		// 出せる型は「最多」と「残り」。どちらが出るかは日付で決まる
		boolean top = frame.contains("今週いちばん多いのは食費（1,300円）です。");
		boolean remaining = frame.contains("週の予算の残りは1,100円、土曜まであと6日です。");
		assertThat(top ^ remaining).as("どちらか1つだけ").isTrue();
	}

	@Test
	void theTopCommentEscapesTheCategoryName() throws Exception {
		Owner a = createOwner("hitokoto07");
		Category evil = category(a, "<i>x</i>", null);      // 予算なし → 出せる型は「最多」だけ
		spend(a, evil, LocalDate.of(2026, 11, 8), 500);

		String frame = frame(topBody(a));

		assertThat(frame).contains("今週いちばん多いのは&lt;i&gt;x&lt;/i&gt;（500円）です。");
		assertThat(frame).doesNotContain("<i>x</i>");
	}

	@Test
	void noGraphCommentWhenThereIsNoTransactionAndNoBudget() throws Exception {
		Owner a = createOwner("hitokoto08");
		category(a, "食費", null);

		String frame = frame(topBody(a));

		assertThat(frame).contains("山茶始開");
		assertThat(frame).doesNotContain("今週いちばん", "週の予算の", "を使っていません");
	}

	@Test
	void withNoTransactionThisWeekButABudgetOnlyTheRemainingTypeIsShown() throws Exception {
		Owner a = createOwner("hitokoto09");
		category(a, "食費", 30_000);

		String frame = frame(topBody(a));

		assertThat(frame).contains("週の予算の残りは2,400円、土曜まであと6日です。");
	}

	// ---- 科目が 0 件 ----

	@Test
	void withNoCategoryTheFrameShowsOnlyTheCalendarBelowTheNotice() throws Exception {
		Owner a = createOwner("hitokoto10");

		String body = topBody(a);

		assertThat(body).contains("先に科目を追加してください");
		assertThat(body.indexOf(HEADING)).isGreaterThan(body.indexOf("先に科目を追加してください"));
		String frame = frame(body);
		assertThat(frame).contains("山茶始開");
		assertThat(frame).doesNotContain("今週いちばん", "週の予算の", "を使っていません");
	}

	// ---- 安全・データの分離 ----

	@Test
	void theFrameHasNoStyleAttributeScriptOrExternalUrl() throws Exception {
		Owner a = createOwner("hitokoto11");
		Category food = category(a, "食費", 30_000);
		spend(a, food, LocalDate.of(2026, 11, 8), 1_234);

		String frame = frame(topBody(a));

		assertThat(frame).doesNotContain(" style=", "<script", "http://", "https://");
	}

	@Test
	void anotherUsersCategoryNeverAppearsInTheComment() throws Exception {
		Owner a = createOwner("hitokoto12");
		category(a, "食費", null);
		Owner b = createOwner("hitokoto13");
		Category secret = category(b, "秘密費", 30_000);
		spend(b, secret, LocalDate.of(2026, 11, 8), 9_900);

		String body = topBody(a);

		assertThat(body).doesNotContain("秘密費");
	}

	@Test
	void notLoggedInIsRedirectedToLoginAndTheTopIsNotStored() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection());

		Owner a = createOwner("hitokoto14");
		category(a, "食費", null);
		entityManager.flush();
		String cacheControl = mockMvc.perform(get("/").with(a.login())).andReturn().getResponse().getHeader("Cache-Control");
		assertThat(cacheControl).contains("no-store");
	}

}
