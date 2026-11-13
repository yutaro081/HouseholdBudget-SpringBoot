package io.github.yutaro081.householdbudget.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.entity.PaymentMethod;
import io.github.yutaro081.householdbudget.entity.User;
import io.github.yutaro081.householdbudget.entity.Vendor;
import io.github.yutaro081.householdbudget.repository.CategoryRepository;
import io.github.yutaro081.householdbudget.repository.PaymentMethodRepository;
import io.github.yutaro081.householdbudget.repository.UserRepository;
import io.github.yutaro081.householdbudget.repository.VendorRepository;
import io.github.yutaro081.householdbudget.security.LoginUser;
import jakarta.persistence.EntityManager;

/**
 * 科目・取引先・支払方法の画面の形（設計書 2.3「科目一覧」「科目変更」、issue 025 の段階 5）。
 * 一覧：追加の欄が白いカード（ラベル「○○名」は太字、「○ 文字以内」は 13px）。表の操作の列は右寄せで、「変更」は取引一覧の「編集」と同じボタン（えんぴつ）、
 * 「削除」は取引一覧の「削除」と同じ赤い枠のボタン（ごみ箱）。削除の確認（<code>onsubmit</code> の固定の文）と、削除できないときの動きは今のまま。
 * 変更の画面：白いカードに、入力欄と「キャンセル」「保存」（取引の登録画面と同じ並び）。
 * 追加・重複・他人のデータ・削除の動きは、CategoryControllerTest などが確かめている。見た目の並びそのものは、ブラウザで確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasterListLayoutTest {

	/** 画面の名前、URL、名前の欄のラベル、「○ 文字以内」の文字、ボタンの title に入る名前 */
	private record Screen(String path, String label, String limit, String noun) {
	}

	private static final List<Screen> SCREENS = List.of(
			new Screen("/categories", "科目名", "10 文字以内", "科目"),
			new Screen("/vendors", "取引先名", "20 文字以内", "取引先"),
			new Screen("/payment-methods", "支払方法名", "10 文字以内", "支払方法"));

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
	private EntityManager entityManager;

	private RequestPostProcessor asUser;
	private Long categoryId;
	private Long vendorId;
	private Long paymentId;

	@BeforeEach
	void setUp() {
		Long userId = userRepository.save(new User("masterlay1", "dummy-hash-for-test")).getId();
		categoryId = categoryRepository.save(new Category(userId, "食費")).getId();
		vendorId = vendorRepository.save(new Vendor(userId, "コンビニ")).getId();
		paymentId = paymentMethodRepository.save(new PaymentMethod(userId, "現金")).getId();
		entityManager.flush();
		entityManager.clear();
		asUser = user(new LoginUser(userId, "masterlay1", "x"));
	}

	private Long idOf(Screen screen) {
		return switch (screen.path()) {
			case "/categories" -> categoryId;
			case "/vendors" -> vendorId;
			default -> paymentId;
		};
	}

	private String page(String path) throws Exception {
		return mockMvc.perform(get(path).with(asUser)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String main(String body) {
		return body.substring(body.indexOf("<main"), body.indexOf("</main>"));
	}

	// ---- 一覧：追加の欄は白いカード ----

	@Test
	void theAddFormIsAWhiteCardWithABoldLabelAndTheLimitInSmallGrayText() throws Exception {
		for (Screen screen : SCREENS) {
			String main = main(page(screen.path()));
			Matcher form = Pattern.compile("(?s)<form[^>]*class=\"add-form form-card\"[^>]*>(.*?)</form>").matcher(main);

			assertThat(form.find()).as(screen.path() + "：白いカード（form-card）に入っている").isTrue();
			assertThat(form.group(1)).as(screen.path()).containsPattern("<label class=\"form-label\" for=\"name\">" + screen.label() + " <span class=\"field-chip\">"
					+ screen.limit() + "</span></label>");
			assertThat(form.group(1)).as(screen.path() + "：前の「（10文字以内）」の書き方は使わない").doesNotContain("文字以内）");
			assertThat(form.group(1)).contains("class=\"add-row\"", ">追加</button>", "name=\"_csrf\"");
		}
	}

	// ---- 一覧：表の操作の列 ----

	@Test
	void theActionColumnHasTheSameButtonsAsTheTransactionList() throws Exception {
		for (Screen screen : SCREENS) {
			String main = main(page(screen.path()));
			Long id = idOf(screen);

			assertThat(main).as(screen.path() + "：見出し「操作」は右寄せの印").containsPattern("<th class=\"row-actions-head\">操作</th>");
			Matcher cell = Pattern.compile("(?s)<td><div class=\"row-actions\">(.*?)</div></td>").matcher(main);
			assertThat(cell.find()).as(screen.path()).isTrue();
			String actions = cell.group(1);

			assertThat(actions).as(screen.path() + "：変更（えんぴつ）").containsPattern("(?s)<a class=\"row-btn\" title=\"この" + screen.noun() + "を直す\" href=\"" + screen.path() + "/" + id
					+ "/edit\"><svg[^>]*aria-hidden=\"true\"[^>]*width=\"14\".*?M14.5 6.5l3 3.*?</svg>変更</a>");
			assertThat(actions).as(screen.path() + "：削除（赤い枠、ごみ箱）").containsPattern("(?s)<button type=\"submit\" class=\"row-btn row-btn-danger\" title=\"この" + screen.noun()
					+ "を削除する\"><svg[^>]*aria-hidden=\"true\"[^>]*width=\"14\".*?M9 7V4h6v3.*?</svg>削除</button>");
			assertThat(actions.indexOf("変更")).as("変更が先、削除があと").isLessThan(actions.indexOf("削除"));
		}
	}

	/** 削除の確認（onsubmit の固定の文）と、POST・CSRF は、今のまま。確認の文に data-confirm は使わない（取引一覧だけ） */
	@Test
	void theDeleteFormKeepsItsFixedConfirmationAndThePostAndTheToken() throws Exception {
		for (Screen screen : SCREENS) {
			String main = main(page(screen.path()));
			Matcher form = Pattern.compile("(?s)<form[^>]*action=\"" + screen.path() + "/" + idOf(screen) + "/delete\"[^>]*>(.*?)</form>").matcher(main);

			assertThat(form.find()).as(screen.path()).isTrue();
			assertThat(form.group()).contains("method=\"post\"", "onsubmit=\"return confirm('削除しますか？')\"").doesNotContain("data-confirm");
			assertThat(form.group(1)).contains("name=\"_csrf\"");
		}
	}

	// ---- 変更の画面 ----

	@Test
	void theEditScreenIsAWhiteCardWithTheInputAndCancelAndSave() throws Exception {
		for (Screen screen : SCREENS) {
			String main = main(page(screen.path() + "/" + idOf(screen) + "/edit"));
			Matcher form = Pattern.compile("(?s)<form[^>]*class=\"form-card\"[^>]*>(.*?)</form>").matcher(main);

			assertThat(form.find()).as(screen.path() + "：白いカード").isTrue();
			String body = form.group(1);
			assertThat(body).containsPattern("<label class=\"form-label\" for=\"name\">" + screen.label() + " <span class=\"field-chip\">" + screen.limit() + "</span></label>");
			assertThat(body).contains("name=\"_csrf\"").contains("autofocus");
			assertThat(body).containsPattern("(?s)<div class=\"form-actions\">\\s*<a class=\"btn btn-outline-secondary\" href=\"" + screen.path()
					+ "\">キャンセル</a>\\s*<button type=\"submit\" class=\"btn btn-primary\">保存</button>\\s*</div>");
			assertThat(main).as(screen.path() + "：下の「戻る」リンクはなくした（キャンセルに変えた）").doesNotContain(">戻る</a>");
		}
	}

	/** 3 つの画面で、変更の画面の形（ラベルと欄の名前以外）が同じ */
	@Test
	void theThreeEditScreensShareTheSameShape() throws Exception {
		String shape = null;
		for (Screen screen : SCREENS) {
			String main = main(page(screen.path() + "/" + idOf(screen) + "/edit"));
			String s = main.replaceAll("(?s)<label.*?</label>", "<label/>").replaceAll("action=\"[^\"]*\"", "").replaceAll("href=\"[^\"]*\"", "")
					.replaceAll("<input[^>]*>", "<input/>").replaceAll("<h1>[^<]*</h1>", "<h1/>").replaceAll("\\s+", " ");
			if (shape == null) {
				shape = s;
			}
			assertThat(s).as(screen.path()).isEqualTo(shape);
		}
	}

	// ---- CSS ----

	private static String css() throws Exception {
		return Files.readString(Path.of("src", "main", "resources", "static", "css", "style.css")).replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", "");
	}

	private static String rule(String css, String selector) {
		Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
		assertThat(m.find()).as("規則 " + selector + " がある").isTrue();
		return m.group(1);
	}

	/** 見出し「操作」の右寄せは、取引一覧（.list-card）だけでなく、白いカードの表すべてで効く */
	@Test
	void theActionHeadingIsRightAlignedInEveryTableCard() throws Exception {
		String css = css();

		assertThat(rule(css, ".table-card th.row-actions-head")).contains("text-align: right");
		assertThat(css).as("取引一覧だけの規則は、白いカードの規則にまとめた").doesNotContain(".list-card th.row-actions-head");
	}

	/** 古い操作の列の作り（actions、d-flex、btn-sm）は、科目・取引先・支払方法の一覧から、なくなった */
	@Test
	void theOldActionMarkupIsGone() throws Exception {
		for (String name : List.of("categories", "vendors", "payment-methods")) {
			String html = Files.readString(Path.of("src", "main", "resources", "templates", name, "list.html"));

			assertThat(html).as(name).doesNotContain("class=\"actions\"", "d-flex", "btn-sm", "btn-outline-secondary", "btn-outline-danger", "inline-form");
		}
	}
}
