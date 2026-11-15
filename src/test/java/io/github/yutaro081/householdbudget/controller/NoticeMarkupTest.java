package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.Transaction;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.TransactionRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * お知らせの枠の HTML（issue 021。設計書 2.3）。文の左に、種類ごとのアイコンが 1 つ入る。アイコンは飾りなので aria-hidden（読み上げない）。
 * 成功＝check、失敗＝alert（丸の中に「！」）、案内（科目などが足りない）と複写のお知らせ＝info（丸の中に「i」）。文は今のまま、画面に見える文字として出る。
 * 色（白い地、左の線）は ColorSchemeStyleSheetTest が確かめる。見た目そのものは、ブラウザで確かめる。
 * 開発用の DB につなぐが、各テストはトランザクションで包まれ、終わると取り消される。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoticeMarkupTest {

	/** check、alert、info の形の目印（IconsFragmentTest と同じ） */
	private static final String CHECK = "M5 12l5 5 9-10";
	private static final String ALERT = "M12 16.5h.01";
	private static final String INFO = "M12 7.5h.01";

	@Autowired
	private SpringTemplateEngine engine;
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
	private TransactionRepository transactionRepository;
	@Autowired
	private EntityManager entityManager;

	/** 成功・失敗のメッセージの部品を、キーを渡して描く */
	private String messages(String successKey, String errorKey) {
		Context context = new Context(Locale.JAPAN);
		context.setVariable("successMessageKey", successKey);
		context.setVariable("errorMessageKey", errorKey);
		return engine.process("notices-test", context);
	}

	/** クラスの頭（例：class="message success alert alert-success"）で始まる要素の中身（最初の閉じタグの手前まで、入れ子を数える） */
	private static String element(String html, String classStart) {
		int start = html.indexOf(classStart);
		assertThat(start).as(classStart + " がある").isGreaterThanOrEqualTo(0);
		start = html.lastIndexOf('<', start);
		String tag = html.substring(start + 1, html.indexOf(' ', start));
		Matcher m = Pattern.compile("<(/?)" + tag + "\\b").matcher(html);
		m.region(start, html.length());
		int depth = 0;
		while (m.find()) {
			depth += m.group(1).isEmpty() ? 1 : -1;
			if (depth == 0) {
				return html.substring(start, m.end());
			}
		}
		throw new AssertionError(classStart + " が閉じていない");
	}

	/** 文の左に、アイコンの四角が 1 つ。アイコンは飾り（aria-hidden）。形の目印 shape を持つ */
	private static void hasOneDecorativeIcon(String notice, String shape) {
		assertThat(notice.split("class=\"alert-icon\"", -1).length - 1).as("アイコンの四角は 1 つ").isEqualTo(1);
		assertThat(notice.split("<svg", -1).length - 1).as("アイコンは 1 つ").isEqualTo(1);
		assertThat(notice).contains("aria-hidden=\"true\"", shape);
		assertThat(notice.indexOf("alert-icon")).as("アイコンは文の左（先頭）").isLessThan(notice.indexOf("</svg>"));
	}

	@Test
	void theSuccessMessageHasTheCheckIconAndItsText() {
		String notice = element(messages("success.saved", null), "class=\"message success alert alert-success\"");

		hasOneDecorativeIcon(notice, CHECK);
		assertThat(notice).contains("保存しました").doesNotContain(ALERT, INFO);
		assertThat(notice.indexOf("</svg>")).isLessThan(notice.indexOf("保存しました"));
	}

	@Test
	void theErrorMessageHasTheAlertIconAndItsText() {
		String notice = element(messages(null, "error.delete.inUse"), "class=\"message error alert alert-danger\"");

		hasOneDecorativeIcon(notice, ALERT);
		assertThat(notice).contains("取引で使われているため削除できません").doesNotContain(CHECK, INFO);
		assertThat(notice.indexOf("</svg>")).isLessThan(notice.indexOf("取引で使われているため"));
	}

	@Test
	void noKeyGivesNoMessage() {
		assertThat(messages(null, null)).doesNotContain("alert", "<svg");
	}

	// ---- 案内（科目などが足りないとき）と、複写のお知らせ ----

	private record Owner(Long id, RequestPostProcessor login) {
	}

	private Owner createOwner(String username) {
		Long id = userRepository.save(new User(username, "dummy-hash-for-test")).getId();
		return new Owner(id, user(new LoginUser(id, username, "x")));
	}

	private String body(Owner owner, String url) throws Exception {
		entityManager.flush();
		entityManager.clear();
		return mockMvc.perform(get(url).with(owner.login())).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void theGuideNoticesOfTheFourScreensHaveTheInfoIcon() throws Exception {
		Owner a = createOwner("notice01");    // 科目・取引先・支払方法が 0 件

		for (String url : new String[] { "/", "/comparison", "/budgets", "/transactions/new" }) {
			String notice = element(body(a, url), "class=\"notice alert alert-warning\"");

			hasOneDecorativeIcon(notice, INFO);
			assertThat(notice).as(url).contains("先に科目を追加してください。", "科目の画面へ").doesNotContain(CHECK, ALERT);
		}
	}

	@Test
	void theCopyNoticeHasTheInfoIconLikeAGuideNotASuccess() throws Exception {
		Owner a = createOwner("notice02");
		Category food = categoryRepository.save(new Category(a.id(), "食費"));
		Vendor vendor = vendorRepository.save(new Vendor(a.id(), "取引先"));
		PaymentMethod payment = paymentMethodRepository.save(new PaymentMethod(a.id(), "現金"));
		Transaction t = transactionRepository.save(new Transaction(a.id(), LocalDate.of(2026, 11, 9), 1_500, food, null, vendor, payment));

		String notice = element(body(a, "/transactions/new?copyFrom=" + t.getId()), "class=\"alert alert-info\"");

		hasOneDecorativeIcon(notice, INFO);
		assertThat(notice).contains("この取引を写しています。", "「保存」を押すまで、保存されません。").doesNotContain(CHECK);
	}

}
