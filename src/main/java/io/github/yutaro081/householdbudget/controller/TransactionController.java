package io.github.yutaro081.householdbudget.controller;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.yutaro081.householdbudget.domain.SeasonPeriod;
import io.github.yutaro081.householdbudget.dto.TransactionFilter;
import io.github.yutaro081.householdbudget.dto.TransactionListView;
import io.github.yutaro081.householdbudget.entity.Category;
import io.github.yutaro081.householdbudget.exception.NotFoundException;
import io.github.yutaro081.householdbudget.form.TransactionForm;
import io.github.yutaro081.householdbudget.form.TransactionFormBinding;
import io.github.yutaro081.householdbudget.security.LoginUser;
import io.github.yutaro081.householdbudget.service.CategoryService;
import io.github.yutaro081.householdbudget.service.PaymentMethodService;
import io.github.yutaro081.householdbudget.service.TransactionCsvWriter;
import io.github.yutaro081.householdbudget.service.TransactionService;
import io.github.yutaro081.householdbudget.service.VendorService;
import jakarta.validation.Valid;

/**
 * 取引の一覧・登録・編集・複写・削除の画面と処理、取引の CSV 出力（issue 004）（設計書 4.4.1、2.2、2.3、2.6、5.18）。
 * <p>
 * 【決まり】ログイン中のユーザーのIDは、LoginUser から取り出して、Service に必ず渡す。
 * 画面から送られたユーザーIDは使わない（Form にも持たせない、設計書 3.9、6.1）。
 * 存在しない・他人の取引のIDや、他人の科目・取引先・支払方法のIDは、Service が NotFoundException を投げ、
 * 404 の画面になる（GlobalExceptionHandler）。
 * <p>
 * 画面に出す値（摘要、科目名など）は、テンプレートで th:text を使って出す（th:utext は使わない、設計書 7.5）。
 */
@Controller
@RequestMapping("/transactions")
public class TransactionController {

	/** CSV 出力の応答の種類（issue 004。設計書 5.18）：text/csv; charset=UTF-8 */
	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	/** CSV 出力の URL（一覧のリンクの行き先。issue 004） */
	private static final String EXPORT_PATH = "/transactions/export";

	private final TransactionService transactionService;
	private final CategoryService categoryService;
	private final VendorService vendorService;
	private final PaymentMethodService paymentMethodService;
	private final Clock clock;

	public TransactionController(TransactionService transactionService,
			CategoryService categoryService,
			VendorService vendorService,
			PaymentMethodService paymentMethodService,
			Clock clock) {
		this.transactionService = transactionService;
		this.categoryService = categoryService;
		this.vendorService = vendorService;
		this.paymentMethodService = paymentMethodService;
		this.clock = clock;
	}

	/**
	 * transactionForm という名前のフォームだけの設定（ほかのフォームには効かない。issue 032。設計書 6.2、6.5）。
	 * <ul>
	 * <li>明細の行の金額にだけ、厳しい変換を付ける（lines.amount。Q5-1 の C、R13）。科目ID など、他の整数の項目には付けない。</li>
	 * <li>明細の行は 5 行まで（6 行目以降は 400）。</li>
	 * <li>空の行の扱いの Validator を「足す」（addValidators）。注釈の入力チェック（@Valid）は、そのまま動く。</li>
	 * </ul>
	 */
	@InitBinder("transactionForm")
	void initBinder(WebDataBinder binder) {
		TransactionFormBinding.configure(binder);
	}

	/**
	 * 取引の一覧（日付の新しい順、同じ日は登録の新しい順）。任意の {@code category}・{@code year}・{@code season} で絞り込む（issue 023。設計書 2.2、5.17）。
	 * <p>
	 * 確かめる順番は「季節 → 科目 → 検索」。どこかで 404 なら、検索しない。
	 * <ul>
	 * <li>季節：年も季節もなければ、期間で絞らない（全期間。予実比較と違い、今の季節にはしない）。それ以外は SeasonPeriod.parse
	 * （予実比較と同じ決まり）で、片方だけ・不正・範囲外・空・2 回なら NotFoundException（404）</li>
	 * <li>科目：数字でない・範囲外の値は型変換のエラーで 404（GlobalExceptionHandler）。空は「指定なし」（copyFrom と同じ）。
	 * 2 回来たときは、Spring が最初の値を使う。どちらの値でも、自分の科目でなければ 404（CategoryService.get が ID と user_id で探す）</li>
	 * </ul>
	 * 取引の検索は 1 回だけ（絞らないときは今までと同じ。絞り込んだときは、分割の行があれば、レシート全体の合計を読む検索がもう 1 回）。
	 * 件数・合計・月のまとまりは、Service が作る（ここでもテンプレートでも足し算しない）。「取引がまだない」の判定は、まとまりが空かどうか。
	 * <p>
	 * 「CSV で出力」のリンクの URL（issue 004）は、確かめたあとの条件から作って渡す（{@link #exportUrlOf}。届いた文字列は使わない）
	 */
	@GetMapping
	public String list(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(name = "category", required = false) Long category,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season,
			Model model) {
		Long userId = loginUser.getUserId();
		TransactionListView view = transactionService.listView(userId, filterOf(userId, category, year, season));
		model.addAttribute("filter", view.filter());
		model.addAttribute("months", view.months());
		model.addAttribute("transactionCount", view.count());
		model.addAttribute("transactionTotal", view.total());
		model.addAttribute("exportUrl", exportUrlOf(view.filter()));
		return "transactions/list";
	}

	/**
	 * 取引の CSV 出力（issue 004。設計書 2.2、5.18）。取引一覧と同じ {@code category}・{@code year}・{@code season} で絞り、
	 * 一覧に表示する取引と同じ行・同じ並びを、CSV ファイル（transactions-yyyyMMdd.csv）として返す。データを変えないので GET。
	 * <ul>
	 * <li>確かめは、取引一覧と同じ {@link #filterOf}（季節 → 科目。どこかで 404 なら、検索しない）。404 のときは、CSV ではなく
	 * 404 の画面（GlobalExceptionHandler）</li>
	 * <li>ファイル名の日付は、出力した日（日本時間の Clock）。<b>利用者の入力（科目名など）は、ファイル名に入れない</b>（7.12 の S11）</li>
	 * <li><b>Cache-Control は付けない</b>。Spring Security の標準のヘッダー（no-store など）に任せる（自分で付けると、上書きされない。7.10）</li>
	 * <li>ファイルは全部作ってから返す（途中で失敗したとき、半分だけのファイルが「成功」で届かないように）</li>
	 * </ul>
	 */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(name = "category", required = false) Long category,
			@RequestParam(required = false) String year,
			@RequestParam(required = false) String season) {
		Long userId = loginUser.getUserId();
		byte[] csv = TransactionCsvWriter.write(transactionService.listForExport(userId, filterOf(userId, category, year, season)));
		String fileName = "transactions-" + LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
		return ResponseEntity.ok()
				.contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
				.body(csv);
	}

	/**
	 * 取引一覧と CSV 出力の絞り込みの確かめ（issue 023・004。設計書 5.17、5.18、7.8）。一覧と CSV の両方から呼ぶ（写しを作らない）。
	 * <ul>
	 * <li>季節：年も季節もなければ、期間で絞らない（全期間。予実比較と違い、今の季節にはしない）。それ以外は SeasonPeriod.parse
	 * （予実比較と同じ決まり）で、片方だけ・不正・範囲外・空・2 回なら NotFoundException（404）</li>
	 * <li>科目：数字でない・範囲外の値は型変換のエラーで 404（GlobalExceptionHandler）。空は「指定なし」（copyFrom と同じ）。
	 * 2 回来たときは、Spring が最初の値を使う。どちらの値でも、自分の科目でなければ 404（CategoryService.get が ID と user_id で探す）</li>
	 * </ul>
	 *
	 * @return 確かめたあとの条件（科目は自分の科目。user_id は持たない）
	 */
	private TransactionFilter filterOf(Long userId, Long category, String year, String season) {
		SeasonPeriod period = SeasonPeriod.isUnspecified(year, season) ? null
				: SeasonPeriod.parse(year, season).orElseThrow(NotFoundException::new);
		Category own = category == null ? null : categoryService.get(userId, category);
		return new TransactionFilter(own, period);
	}

	/**
	 * 「CSV で出力」のリンクの URL（issue 004。設計書 2.3、Q004-2）。確かめたあとの条件から、<b>値のある条件だけ</b>を付けて作る
	 * （値のない year などを付けると、一覧と同じ決まりで 404 になるため）。値は、自分の科目の ID と、確かめた年・季節（数字と小文字の英字）だけ
	 */
	private static String exportUrlOf(TransactionFilter filter) {
		UriComponentsBuilder url = UriComponentsBuilder.fromPath(EXPORT_PATH);
		if (filter.category() != null) {
			url.queryParam("category", filter.categoryId());
		}
		if (filter.period() != null) {
			url.queryParam("year", filter.period().year()).queryParam("season", filter.period().season().getUrlName());
		}
		return url.build().toUriString();
	}

	/**
	 * 登録フォーム。日付の最初の値は今日（U7）。
	 * <p>
	 * 明細の行は、空の 5 行（issue 032）。
	 * {@code copyFrom}（写す元の取引のID）があるときは、その取引の取引先・支払方法と、明細の行に科目・金額・摘要を入れた状態で表示する
	 * （取引の複写、issue 012、U8。同じレシートの取引なら全部の行。issue 032）。日付だけは今日。GET なので、データベースには何も書かない。
	 * 写す元は、ID とログイン中のユーザーの両方で探す（getReceipt。他人・存在しない取引は NotFoundException で 404）。
	 * 数字でない・範囲外の値は型変換のエラーになり、GlobalExceptionHandler が 404 にする。空（{@code ?copyFrom=}）は「指定なし」。
	 */
	@GetMapping("/new")
	public String showNew(@AuthenticationPrincipal LoginUser loginUser,
			@RequestParam(name = "copyFrom", required = false) Long copyFrom,
			Model model) {
		TransactionForm form;
		if (copyFrom == null) {
			form = TransactionForm.forNew();
		} else {
			form = TransactionForm.copyOf(transactionService.getReceipt(loginUser.getUserId(), copyFrom));
			model.addAttribute("copied", true);
		}
		form.setTransactionDate(LocalDate.now(clock));
		model.addAttribute("transactionForm", form);
		addFormModel(model, loginUser.getUserId(), null);
		return "transactions/form";
	}

	/**
	 * 取引の登録。空でない明細の行ごとに 1 件の取引にする（issue 032）。入力エラーのときは、リダイレクトせずにフォームを表示し直す
	 * （入力値を残し、明細は 5 行にそろえ、プルダウンの選択肢を取り直して、選んでいた値を残す、設計書 6.7、6.9）。
	 */
	@PostMapping("/new")
	public String register(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @ModelAttribute("transactionForm") TransactionForm transactionForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		if (bindingResult.hasErrors()) {
			transactionForm.fillLinesUpTo(TransactionForm.MAX_LINES);
			addFormModel(model, userId, null);
			return "transactions/form";
		}
		transactionService.register(userId, transactionForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.transactionRegistered");
		return "redirect:/transactions";
	}

	/**
	 * 編集画面。登録済みの値の入ったフォームを表示する。{@code id} は、同じレシートのどの行でもよく、
	 * そのレシートの全部の行を明細の行に入れる（残りは空の行で 5 行。issue 032 の段階 3）。
	 */
	@GetMapping("/{id}/edit")
	public String showEdit(@AuthenticationPrincipal LoginUser loginUser, @PathVariable Long id, Model model) {
		Long userId = loginUser.getUserId();
		model.addAttribute("transactionForm", TransactionForm.forEdit(transactionService.getReceipt(userId, id)));
		addFormModel(model, userId, id);
		return "transactions/form";
	}

	/**
	 * 取引の更新。入力エラーのときは、編集画面を表示し直す（入力値を残す）。
	 * 入力エラーのときでも、先に ID と user_id で探して、存在しない・他人の取引なら 404 にする
	 * （入力が正しいときと間違っているときで、他人のデータの有無が分かる差を作らないため、設計書 7.8）。
	 * 同じレシートの取引は、まとめて更新する（行を足す・消す・直す。5 行まで。6 行目以降は束縛できずに 400。issue 032 の段階 3）。
	 */
	@PostMapping("/{id}/edit")
	public String update(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			@Valid @ModelAttribute("transactionForm") TransactionForm transactionForm,
			BindingResult bindingResult,
			Model model,
			RedirectAttributes redirectAttributes) {
		Long userId = loginUser.getUserId();
		transactionService.get(userId, id);

		if (bindingResult.hasErrors()) {
			transactionForm.fillLinesUpTo(TransactionForm.MAX_LINES);
			addFormModel(model, userId, id);
			return "transactions/form";
		}
		transactionService.update(userId, id, transactionForm);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.transactionUpdated");
		return "redirect:/transactions";
	}

	/** 取引の削除。同じレシートの取引は、まとめて削除する（issue 032）。成功したら一覧へ戻る。確認ダイアログは、画面（ブラウザ）で行う。 */
	@PostMapping("/{id}/delete")
	public String delete(@AuthenticationPrincipal LoginUser loginUser,
			@PathVariable Long id,
			RedirectAttributes redirectAttributes) {
		transactionService.delete(loginUser.getUserId(), id);
		redirectAttributes.addFlashAttribute("successMessageKey", "success.deleted");
		return "redirect:/transactions";
	}

	/**
	 * 登録・編集のフォームの表示に必要なものを、model に入れる（表示と、入力エラーの表示し直しで共通）。
	 * プルダウンの選択肢は、ログイン中のユーザー自身のものだけを並べる（設計書 6.7）。科目は登録順（Q14）。
	 * 取引先・支払方法は、取引の件数が多い順、同じなら登録順（要件定義書 4.2、issue 029）。選んでいる値は th:field が id で決めるので、並びが変わっても選ばれたまま。
	 *
	 * @param transactionId 編集のときは取引のID、登録のときは null
	 */
	private void addFormModel(Model model, Long userId, Long transactionId) {
		model.addAttribute("categories", categoryService.list(userId));
		model.addAttribute("vendors", vendorService.listForChoices(userId));
		model.addAttribute("paymentMethods", paymentMethodService.listForChoices(userId));
		model.addAttribute("transactionId", transactionId);
		model.addAttribute("pageTitle", transactionId == null ? "取引の登録" : "取引の編集");
	}

}
