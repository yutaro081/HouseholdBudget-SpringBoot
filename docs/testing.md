# テスト

[← README に戻る](../README.md)

自動テストは **3,057 件**（2026 年 11 月 25 日の時点）で、すべて成功しています。古いレポートが混ざらないよう、`.\mvnw clean test` で流して数えています（`.\mvnw test` でも実行できます）（MySQL と環境変数が要ります。各テストは、データベースの変更を最後に取り消すので、データは残りません）。

テストのときの DB の接続の数は、テストのときだけ読まれる `src/test/resources/config/application.properties` で、本体より小さくしています（テストの準備〔ApplicationContext〕は十数種類あり、準備ごとに接続の入れ物を持つため、MySQL の接続の上限に届かないようにする。issue 032 の段階 0）。本体の `src/main/resources/application.properties` はそのまま読まれ、アプリを起動したときの接続の数は変わりません。

| 種類 | 件数 | 何を確かめるか |
|---|---|---|
| 画面（Controller） | 1277 | 取引の CSV 出力（応答のヘッダーとファイル名、BOM・見出しの行・CRLF、`"` の囲み、数式の印が文字の 4 列だけにかかること、分割の番号、一覧と同じ行・同じ並び、他人の行が入らないこと、一覧の「CSV で出力」のリンクに値のある条件だけが付くこと、0 件ではリンクを出さないこと。404 と未ログインは、一覧と同じテストを CSV の URL でも流す）、取引一覧の絞り込み（他人・存在しない科目と不正な年・季節は 404、見出しの文、予実比較の実績との一致〔季節の境目〕、科目で絞ったときの削除の確認の文がレシート全体の値になること）、予実比較の実績のリンク（表示している季節で組む、リンク先の合計 ＝ セルの数字）、明細の行に分けた登録（5 行、空の行、一部だけ入った行、行ごとの入力チェック、6 行目は 400、「行を足す」「行を消す」と明細の合計の HTML の印）、同じレシートの取引（印の付け方、印は画面から受け取らないこと、どの行から入っても編集・削除・複写がレシート全体になること、編集で行を足す・消す・直すときに残る行の id が変わらないこと、日付・取引先・支払方法が全部の行で変わること、一覧の並びと印「分割」、削除の確認の文、印のない行は 1 件ずつのままであること、他人のレシートの行は 404）、画面の表示、予実比較が「今日」を 1 回だけ取ること、エラーのメッセージ、入力値が残ること、移動先、他人のデータが見えないこと、金額の入力を助ける電卓の HTML（ボタンの並び、Tab で止まるのは C と税込だけ、はじめは明細 1 の金額の欄の入れ物の中）、金額の欄の式（計算できないときの文、式のままでは保存しないこと）、画面の下に固定する帯（合計・キャンセル・保存、金額の列の下の合計、576px 未満の 2 行、帯の外側に overflow がないこと）、取引の複写、色とメニューの印、フォント（CSS の指定、ファイル、キャッシュ）、取引の登録・編集画面の 2 列（HTML の並び、印、ボタン、CSS の規則）、トップ（表示、空のときの案内、`style` 属性が数字と固定の文字だけであること、色の変数）、きょうのひとこと（枠の位置、暦の文の出し分け、行事と節気が重なる日の並び、年末・表の外の年、科目が 0 件のとき、科目名のエスケープ、他の人のデータが混ざらないこと、枠の CSS）、ヘッダー（ロゴ、メニューのアイコン、ヘッダーが 1 つだけであること）、ログイン・登録の 2 列（紹介、入力欄の `autocomplete`、CSS）、取引一覧（件数、カードの中の表）、トップの季節のメーター（見込みの一文と、メーターの数字との一致）、円グラフの一覧の今週の判定、予実比較の着地見込みの 1 行（今の季節だけ、「上回ります」が出る・出ない、警告の言葉がない）、予算設定の自動予算（`auto` の 1〜3 以外は 404、案の文、5 列の表と合計の行、ボタンと切りかえの表示、保存のボタンと CSRF、合計・確認の JavaScript に渡す HTML の印）、ログイン・登録の縦の位置（CSS）、メーター（縮尺、予算の線、超えた部分の赤、「+○%」、「今日」の名前）、予実比較の今の季節の列（残り・見込み・見込みとの差、チップ、列の幅、区切り線）、予算設定の列の並びと、案のもとにした数字の目印（薄いターコイズの背景）、トップの季節の文（2 行、赤い部分）と今週のひとこと（置き場所、アイコン、科目名の無害化）、ログイン・登録の紹介のチェック 3 つ（文、順番、印の数）、取引一覧の金額の見出し（「金額（円）」）とセルに「円」がないこと、行にマウスを載せたときの色（薄いターコイズを使わないこと、本文・リンク・補足の文字との明るさの比が 4.5 以上）、科目・取引先・支払方法の追加ボタンが入力欄と同じ行にあること（3 画面が同じ形、エラーの文は行の下）、ボタンの言葉（追加と保存。登録する・更新・変更の送信ボタンがなく、ログイン・登録・削除などはそのまま）、取引一覧の月ごとの見出し（件数と合計。他人の取引が混ざらないこと）、操作の列（編集・複写・削除のボタンとアイコン、削除の確認の文が科目名の記号で壊れないこと）、取引の詳細ページがなく 404 になること、電卓の CSS（4 列の格子、浮かせて出す位置、992px 以上は欄の右で「1 2 3 ＋」の段が欄の横、帯より手前）、科目・取引先・支払方法の一覧と変更の画面の白いカード、予算設定・予実比較の「前の季節」「次の季節」の並び、ログイン・登録の上の余白と説明のチップ、取引の登録・編集画面の取引先・支払方法の並び（取引の件数の多い順、ほかの人の取引を数えないこと、編集・複写・入力エラーで選んでいた値が残ること、一覧の画面は登録順のまま）、ヘッダーの「取引を登録」（全画面に 1 つ、メニューの外、トップと取引一覧の本文にはないこと、登録画面だけで今の印が付くこと、ログイン前の画面には出ないこと、メニューのすぐあとでユーザー名のまとまりの外にあること、「設定」との間の 36px）、メニューの「設定」（5 つの項目、中の 3 つのリンク、「予算」から予算設定の画面へ、科目・取引先・支払方法の画面だけで「設定」に印、JavaScript を使わないこと） |
| 入力チェックと値の変換 | 349 | 明細の行ごとの入力チェック（空の行は無視、一部だけ入った行、型変換のエラーのある行、1 行以上、5 行まで）、必須・文字数・範囲、全角の数字、整数でない入力 |
| 計算 | 709 | 季節の決まり（前後の季節、範囲、冬の年）、色の判定の境目、今週の期間、週の予算と切り上げの境目、「今日」の線の位置、円グラフの並びと割合、二十四節気の日と七十二候の境目（年をまたぐ、表の外の年）、年中行事の日（節分・お彼岸入りは節気から計算）、グラフへのひとことの型と選び方、季節の終わりまでの日数、使った割合と日の進み方の％、着地見込みの式（整理前の式との一致、予定どおりなら予算、100 円切り上げ、桁あふれは例外）、「予算の ○%」と「上回ります」の境目、円グラフの一覧の判定の引き継ぎ（「その他」は判定なし）、自動予算の計算（過去の同じ季節の組み立て、中央値、傾向の％と上限、ペース、案の選び方と 1,000 円切り上げ・上限、桁あふれ）、メーターの縮尺と赤い部分（予算 0 円を含む）、今週いちばん進んでいる科目の選び方、最初の取引の日から数える着地見込みとペース（予算の日割りを含む）、予実比較の見込みの差の文（少ない・多い・同じ） |
| 業務処理（Service） | 416 | CSV の値 1 つの書き方（`"` で囲む・`""` に重ねる、数式になりうる 12 の文字で始まる値に「'」、ほかの値には付けないこと）とファイルにする所（BOM、見出し、列の形、分割の番号）、CSV 出力の SQL の本数が行の数・絞り込みで増えないこと、明細の行の登録（全部の行を確かめてから書くこと）、同じレシートを読む・登録する・更新するときの SQL の本数が行の数で増えないこと、登録・更新・削除の決まり、初期データ、予実比較の集計、予実比較の画面用のまとまり（科目ごとの見込みと見込みとの差、要約カードが出る条件、過去・未来の季節では出さないこと）、予実比較・トップ・予算設定の SQL の本数が科目の数で増えないこと、取引の登録・編集画面の SQL の本数が科目・取引先・支払方法・取引の数で増えないこと、256 個の上限、トップの集計（週の境目、季節をまたぐ週、他の人のデータが混ざらないこと）、きょうのひとこと（文面 107 個の件数・番号・空の文、二十四節気の日付の表を国立天文台の日付と照らすこと、グラフへのひとことの文に禁止の言葉がないこと）、トップの数字（残り日数、％、状況のチップ、円グラフの一覧の判定が今週の進み具合と同じこと、着地見込みが予実比較の合計と同じ値から計算されること）、自動予算（「記録のある年」の判定、根拠の文、他人のデータが混ざらないこと、案を入れても保存しないこと）、予算設定の初期値、最初の取引の日から数えた着地見込みが、トップ・予実比較・自動予算に届くこと、案のもとにした数字をサーバーが決めること、取引の月ごとの集計（月末と月初、うるう年、年またぎ、他人の取引が混ざらないこと） |
| セキュリティ | 101 | ログイン必須、CSRF、ログイン・ログアウト、ログインが切れたあとの移動（issue 030。CSV 出力の URL も）、応答のヘッダー（CSV 出力にも `no-store`）、フォントがログインなしで開けること |
| データベース | 85 | 取引一覧の絞り込みの検索（値が null なら絞らない、他人の行を読まない、並びが絞らない一覧と同じ）、同じレシートの合計を「印と持ち主」で読む検索、同じレシートの取引を「印と持ち主」で読む検索（他人の行を読まないこと）、一覧の並び（日付 → レシート → 行の順）、印の列の読み書き、読み書き、制約、集計の SQL（月ごとの科目別の集計を含む）、最初の取引の日（自分の取引だけ） |
| そのほか | 120 | エラーの詳細を画面に含めない設定が読まれていること（設定の出どころが本体の `application.properties`、古い名前 `server.error.*` がないこと、読み込んだ値。issue 033）、明細の行の JavaScript（split-lines.js）のソースの検査（行を作らない・写さない、文を持たない、合計は input のできごとだけで計算し直す）、電卓の JavaScript（calculator.js・calculator-keys.js）のソースの検査（文字列をプログラムとして実行しない、式は ＋ − だけ、要素は作らず移すだけ、style を書かない、Enter・Esc・欄から離れたときの決まり）、メッセージの文、テンプレートの検査（無害化しない出力がないこと、`onsubmit` などのイベントの属性と `javascript:` の URL がないこと）、JavaScript のソースの検査（電卓と金額の欄の式、予算の合計、`static/js` の全ファイルに `innerHTML`・`eval` などがないこと）、エラー処理、設定、起動 |

自動テストでは確かめにくいものは、ブラウザで確かめました（Phase 8）。全画面を開く、入力チェックの境界値、セキュリティ（ログイン前後のセッション ID の変化、Cookie、エラー画面）、2 人のユーザーでのデータ分離です。確認の記録は、[docs/plans/phase-8-polish.md](plans/phase-8-polish.md) にあります。

## データの分離を確かめているテスト

「他の人のデータが見えない・変更できない」ことは、2 人のユーザーを作って、片方から他方のデータを開く・変更する・削除する・混ぜて送る、という形で確かめています。主なテスト（`クラス名.メソッド名`）は次のとおりです。

**取引**

- 一覧に、自分の取引だけが出る
  - `TransactionControllerTest.listShowsOwnTransactionsNewestDateFirstAndNotOthers`
- 他人の取引の編集・複写・削除は、存在しない ID・数字でない ID と同じ 404 になる。`/transactions/{id}`（詳細）は、画面をなくしたので、誰が開いても 404
  - `TransactionRowActionsTest.editCopyAndDeleteOfOtherUsersMissingAndNonNumericIdsAreAllTheSame404`
  - `TransactionRowActionsTest.theDetailUrlIsNotFoundForEveryone`
- 他人の取引は、変更も削除もできない（データは残る）
  - `TransactionControllerTest.updateOfOtherUsersTransactionIsNotFoundWhetherOrNotTheInputIsValid`
  - `TransactionControllerTest.deleteOfOtherUsersTransactionIsNotFoundAndKeepsIt`
- 他人の取引は、「写して登録」の元にも指定できない（存在しない ID・数字でない ID と同じ 404）。開いただけでは、取引は増えない
  - `TransactionCopyTest.copyOfOtherUsersMissingAndInvalidIdsIsTheSame404`
  - `TransactionCopyTest.openingTheCopyFormDoesNotCreateOrChangeAnything`
- 登録のとき、他人の科目・取引先・支払方法の ID を混ぜて送られても、保存しない
  - `TransactionControllerTest.registerWithOtherUsersItemIsNotFound`
  - `TransactionServiceTest.registerWithOtherUsersItemsIsNotFoundAndSavesNothing`

**科目・取引先・支払方法**

- 一覧は自分のものだけ。他人のものは、変更・削除の画面も操作も 404
  - `CategoryControllerTest.listShowsOwnCategoriesInOrderAndNotOthers`
  - `CategoryControllerTest.otherUsersCategoryIsNotFoundForViewEditAndDelete`
- 取引先・支払方法も同じ
  - `VendorControllerTest.otherUsersItemIsNotFoundForViewEditAndDelete`
  - `PaymentMethodControllerTest.otherUsersItemIsNotFoundForViewEditAndDelete`
- 他のユーザーと同じ名前でも追加できる。256 個の上限は、ユーザーごとに数える
  - `CategoryControllerTest.addDuplicateIgnoresCaseAndOtherUsersNames`
  - `MasterLimitControllerTest.anotherUsersFullListDoesNotBlockMe`

**予算**

- 他人の科目の ID が 1 つでも混ざっていたら、何も保存しない
  - `BudgetControllerTest.otherUsersCategoryIdIsNotFoundAndSavesNothingEvenIfOtherRowsAreValid`
  - `BudgetServiceTest.otherUsersCategoryIdMakesTheWholeSaveNotFound`
- 2 人が同じ季節の予算を保存しても、別々に保存される。他人の予算は変わらない
  - `BudgetControllerTest.twoUsersSaveTheSameSeasonSeparately`
  - `BudgetServiceTest.saveDoesNotTouchOtherSeasonsOrOtherUsers`

**予実比較**

- 他人の取引・予算が、実績・予算に混ざらない
  - `ComparisonControllerTest.otherUsersDataIsNotShown`
  - `ComparisonServiceTest.otherUsersCategoriesBudgetsAndTransactionsAreNotIncluded`
  - `TransactionRepositorySumTest.otherUsersTransactionsAreNotCounted`

**同じレシートの取引（issue 032）**

- 他人の科目・取引先・支払方法が 1 行でも入っていたら、ほかの行も含めて、何も保存・変更しない
  - `TransactionSplitEntryTest.anotherUsersCategoryOnOneLineSavesNoLineAtAll`
  - `TransactionReceiptTest.anotherUsersCategoryOnTheSecondLineOfAnEditChangesNothing`
- 他人のレシートの行の ID は、編集の画面・複写・削除・更新（送った明細の行の数にかかわらず）とも 404。他人のレシートは変わらない
  - `TransactionReceiptTest.openingDeletingOrCopyingAnotherUsersReceiptRowIsNotFound`
  - `TransactionReceiptTest.updatingAnotherUsersReceiptRowIsNotFoundWhateverTheLineCount`
- 同じ印の値が他人の行に入っていても、その行を読まない・変えない・消さない（レシートは「印と持ち主」で読む）
  - `TransactionReceiptTest.anotherUsersRowWithTheSameMarkValueIsNeverRead`
  - `TransactionRepositoryTest.receiptRowsAreReadByMarkAndUserIdInIdOrder`
- 印は画面から受け取らない（送っても使わない）
  - `TransactionReceiptTest.aMarkSentOnRegistrationIsIgnored`
  - `TransactionReceiptTest.aMarkSentOnEditIsIgnored`

**取引一覧の絞り込みと、予実比較の実績のリンク（issue 023）**

- 他人の科目・存在しない科目・数字でない値は、同じ 404 になる
  - `TransactionFilterTest.otherUsersMissingAndInvalidCategoriesAreTheSame404`
- 他人の行に自分の科目の ID が入っていても（SQL で作る）、絞り込んだ一覧に出ない
  - `TransactionFilterTest.otherUsersRowsAreNeverShownEvenWithMyCategoryId`
  - `TransactionRepositoryFilterTest.otherUsersRowsAreNeverReturned`
- 科目で絞っても、削除の確認の文の合計・行の数は、実際に消える行（レシート全体）と同じ。他人の行に同じ印の値が入っていても数えない
  - `TransactionFilterTest.theDeleteConfirmationOfAPartlyShownReceiptCountsTheWholeReceipt`
  - `TransactionFilterTest.anotherUsersRowWithTheSameMarkIsNotCounted`
- 予実比較のリンクに出るのは、自分の科目の ID だけ
  - `ComparisonControllerTest.linksCarryOnlyMyOwnCategoryIds`

**取引の CSV 出力（issue 004）**

- 他人の行は、CSV に入らない（他人の行に自分の科目の ID が入っていても。絞り込みなし・あり）
  - `TransactionCsvExportTest.otherUsersRowsAreNeverInTheFile`
  - `TransactionFilterTest.otherUsersRowsAreNeverShownEvenWithMyCategoryId`（一覧と CSV の両方の URL）
- 他人の科目・存在しない科目・数字でない値は、一覧と同じ 404
  - `TransactionFilterTest.otherUsersMissingAndInvalidCategoriesAreTheSame404`（一覧と CSV の両方の URL）

**アカウント登録**

- 初期データ（科目・取引先・支払方法・予算）は、ユーザーごとに別の行として作る
  - `RegistrationServiceTest.eachUserGetsSeparateInitialData`

**データベースの検索**

- 「ID」と「持ち主」の両方で探すと、自分の取引だけが見つかる
  - `TransactionRepositoryTest.findByIdAndUserIdFindsOnlyOwn`

ブラウザでも、2 人のユーザーで、別のユーザーの ID を URL に入れる・送信の内容を書き換える、という確認をしました（Phase 8）。
