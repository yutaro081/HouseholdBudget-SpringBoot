package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * TopController（トップ。GET /）のテスト（設計書 2.2、2.3、5.11、7.5、要件定義書 4.5）。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される（行は残らない）。
 * 「今日」は、日本時間の 2026-11-08（日曜。秋。今週は 11/8〜11/14）に固定した Clock で確かめる。
 * 計算は domain・TopServiceTest で確かめてあるので、ここでは画面に出る形と、安全（style 属性、th:text）を確かめる。
 * 円の角度や棒の位置の「見え方」は、MockMvc では見えないので、ブラウザで確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TopControllerTest {

	/** 日本時間の 2026-11-08（日曜）に固定した Clock */
	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(LocalDate.of(2026, 11, 8).atStartOfDay(AppConfig.JAPAN).toInstant(), AppConfig.JAPAN);
		}
	}

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);
	private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("\\sstyle=\"([^\"]*)\"");
	/** style の値として許す形：「プロパティ名: 数字・英小文字・% ( ) , . - と空白」だけ（; は区切りとして末尾にだけ） */
	private static final Pattern ALLOWED_STYLE = Pattern.compile("[a-z-]+: [0-9a-z%(),. -]*;?");

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

	private static List<String> styleValues(String body) {
		List<String> values = new ArrayList<>();
		Matcher m = STYLE_ATTRIBUTE.matcher(body);
		while (m.find()) {
			values.add(m.group(1));
		}
		return values;
	}

	// ---- ログイン必須・メニュー ----

	@Test
	void notLoggedInIsRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"));
	}

	@Test
	void topScreenMarksTheTopMenuItemAsCurrent() throws Exception {
		Owner a = createOwner("topctl01");
		category(a, "食費", 30_000);

		MenuAssertions.assertCurrentItem(topBody(a), "/", "トップ");
	}

	// ---- 表示の内容 ----

	@Test
	void showsTheThreePartsWithTheWordsThisWeekAndWeeklyBudget() throws Exception {
		Owner a = createOwner("topctl02");
		Category food = category(a, "食費", 30_000);
		category(a, "交通費", 30_000);
		spend(a, food, SUNDAY, 1_234);

		String body = topBody(a);

		assertThat(body).contains("トップ", "この季節に使った合計", "残り", "今日");
		assertThat(body).contains("今週 何に使ったか", "今週の進み具合", "週の予算");
		assertThat(body).contains("60,000円");                 // 季節の予算の合計（30,000 × 2）
		assertThat(body).contains("1,234円");                  // 季節の使った額（切り上げない）
		assertThat(body).contains("1,300円");                  // 今週の実績（100 円単位で切り上げ）
		assertThat(body).contains("2,400円");                  // 週の予算
		assertThat(body).contains("食費", "交通費", "合計");
		assertThat(body).contains("conic-gradient(");
	}

	@Test
	void hasTheTwoButtonsAtTheTop() throws Exception {
		Owner a = createOwner("topctl03");
		category(a, "食費", 30_000);

		String body = topBody(a);

		assertThat(body).containsPattern("<a [^>]*href=\"/comparison\"[^>]*>予実比較を見る</a>");
		assertThat(body).containsPattern(java.util.regex.Pattern.compile("<a [^>]*href=\"/transactions/new\"[^>]*><svg[^>]*>.*?</svg>取引を登録</a>", java.util.regex.Pattern.DOTALL));
	}

	@Test
	void noForecastOrAlertMessageIsShown() throws Exception {
		// issue 017 で、ペースの一言（ゆっくり／ほぼ同じ／速め）は出すことにした。予測や前もっての知らせ、「超えています」の言い方は出さない
		Owner a = createOwner("topctl04");
		category(a, "食費", 30_000);

		assertThat(topBody(a)).doesNotContain("予算を超えています", "このままだと", "超えそう", "着地", "早めです");
	}

	@Test
	void weekMeterUsesTheSameColorClassesAsTheComparisonRules() throws Exception {
		Owner a = createOwner("topctl05");
		Category over = category(a, "超過", 30_000);
		Category within = category(a, "予算内", 30_000);
		spend(a, over, SUNDAY, 2_401);       // 2,500 > 2,400 → 赤
		spend(a, within, SUNDAY, 2_000);     // 2,000 → 💮

		String body = topBody(a);

		assertThat(body).contains("week-over", "week-within");
		assertThat(body).contains("💮");
	}

	// ---- 0 件・未設定 ----

	@Test
	void noCategoriesShowsGuidanceAndNoMetersButKeepsTheButtons() throws Exception {
		Owner a = createOwner("topctl06");

		String body = topBody(a);

		assertThat(body).contains("先に科目を追加してください").contains("href=\"/categories\"");
		assertThat(body).doesNotContain("conic-gradient(").doesNotContain("今週の進み具合").doesNotContain("今週 何に使ったか");
		assertThat(body).contains("予実比較を見る", "取引を登録");
	}

	@Test
	void noTransactionsThisWeekShowsAMessageInsteadOfThePieButKeepsTheMeters() throws Exception {
		Owner a = createOwner("topctl07");
		category(a, "食費", 30_000);

		String body = topBody(a);

		assertThat(body).contains("今週はまだ使っていません");
		assertThat(body).doesNotContain("conic-gradient(");
		assertThat(body).contains("今週の進み具合", "食費");
	}

	@Test
	void allBudgetsUnsetShowsAMessageWithALinkToBudgets() throws Exception {
		Owner a = createOwner("topctl08");
		Category food = category(a, "食費", null);
		spend(a, food, SUNDAY, 500);

		String body = topBody(a);

		assertThat(body).contains("今の季節の予算がまだありません").contains("href=\"/budgets\"");
		assertThat(body).contains("未設定");
		assertThat(body).contains("500円");
	}

	// ---- 安全：th:text、style 属性（設計書 7.5） ----

	@Test
	void categoryNamesAreEscapedAndNeverPutIntoTheStyleAttribute() throws Exception {
		Owner a = createOwner("topctl09");
		String nasty = "<b>\";url(";   // 9 文字（科目名は 10 文字以内）
		Category c = category(a, nasty, 30_000);
		spend(a, c, SUNDAY, 700);

		String body = topBody(a);

		assertThat(body).doesNotContain(nasty);                  // そのままでは出ない
		assertThat(body).contains("&lt;b&gt;");                  // 文字として（無害化して）出る
		assertThat(styleValues(body)).isNotEmpty();
		for (String style : styleValues(body)) {
			assertThat(style).as("style の値は数字と固定の文字だけ").matches(ALLOWED_STYLE);
			assertThat(style).doesNotContain("<", ">", "\"", "&", "url(", "script");
		}
	}

	@Test
	void styleValuesDependOnlyOnTheAmountsNotOnTheNames() throws Exception {
		Owner a = createOwner("topctl10");
		Owner b = createOwner("topctl11");
		Category ca = category(a, "食費", 30_000);
		Category cb = category(b, "<i>別の名前", 30_000);
		spend(a, ca, SUNDAY, 700);
		spend(b, cb, SUNDAY, 700);

		assertThat(styleValues(topBody(a))).isEqualTo(styleValues(topBody(b)));
	}

	@Test
	void hasNoScriptNoInlineHandlersAndNoExternalUrls() throws Exception {
		Owner a = createOwner("topctl12");
		Category food = category(a, "食費", 30_000);
		spend(a, food, SUNDAY, 700);

		String body = topBody(a);

		// 共通の部品が読む「戻る」用の JavaScript（back-reload.js）のほかに、script を増やさない
		assertThat(body.split("<script", -1).length - 1).isEqualTo(1);
		assertThat(body).contains("/js/back-reload.js");
		assertThat(body).doesNotContainPattern("\\son[a-z]+=\"");
		assertThat(body).doesNotContain("http://", "https://");
	}

	// ---- データの分離 ----

	@Test
	void otherUsersDataDoesNotAppear() throws Exception {
		Owner a = createOwner("topctl13");
		Owner b = createOwner("topctl14");
		category(a, "食費", 30_000);
		Category secret = category(b, "Bだけの科目", 99_000);
		spend(b, secret, SUNDAY, 54_321);

		String body = topBody(a);

		assertThat(body).doesNotContain("Bだけの科目").doesNotContain("54,321").doesNotContain("99,000");
	}

}
