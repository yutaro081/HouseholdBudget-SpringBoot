# テスト

[← README に戻る](../README.md)

自動テストは **2433 件**（2026 年 11 月 12 日の時点）で、すべて成功しています。古いレポートが混ざらないよう、`.\mvnw clean test` で流して数えています（`.\mvnw test` でも実行できます）（MySQL と環境変数が要ります。各テストは、データベースの変更を最後に取り消すので、データは残りません）。

| 種類 | 件数 | 何を確かめるか |
|---|---|---|
| 画面（Controller） | 878 | 画面の表示、エラーのメッセージ、入力値が残ること、移動先、他人のデータが見えないこと、金額ボタンの HTML、取引の複写、色とメニューの印、フォント（CSS の指定、ファイル、キャッシュ）、取引の登録・編集画面の 2 列（HTML の並び、印、ボタン、CSS の規則）、トップ（表示、空のときの案内、`style` 属性が数字と固定の文字だけであること、色の変数）、きょうのひとこと（枠の位置、暦の文の出し分け、行事と節気が重なる日の並び、年末・表の外の年、科目が 0 件のとき、科目名のエスケープ、他の人のデータが混ざらないこと、枠の CSS）、ヘッダー（ロゴ、メニューのアイコン、ヘッダーが 1 つだけであること）、ログイン・登録の 2 列（紹介、入力欄の `autocomplete`、CSS）、取引一覧（件数、カードの中の表）、トップの季節のメーター（見込みの一文と、メーターの数字との一致）、円グラフの一覧の今週の判定、予実比較の着地見込みの 1 行（今の季節だけ、「上回ります」が出る・出ない、警告の言葉がない）、予算設定の自動予算（`auto` の 1〜3 以外は 404、案の文、5 列の表と合計の行、ボタンと切りかえの表示、保存のボタンと CSRF、合計・確認の JavaScript に渡す HTML の印）、ログイン・登録の縦の位置（CSS）、メーター（縮尺、予算の線、超えた部分の赤、「+○%」、「今日」の名前）、予実比較の今の季節の列（残り・見込み・見込みとの差、チップ、列の幅、区切り線）、予算設定の列の並びと、案のもとにした数字の下線、トップの季節の文（2 行、赤い部分）と今週のひとこと（置き場所、アイコン、科目名の無害化）、ログイン・登録の紹介のチェック 3 つ（文、順番、印の数）、取引一覧の金額の見出し（「金額（円）」）とセルに「円」がないこと、行にマウスを載せたときの色（薄いターコイズを使わないこと、本文・リンク・補足の文字との明るさの比が 4.5 以上）、科目・取引先・支払方法の追加ボタンが入力欄と同じ行にあること（3 画面が同じ形、エラーの文は行の下）、ボタンの言葉（追加と保存。登録する・更新・変更の送信ボタンがなく、ログイン・登録・削除などはそのまま） |
| 入力チェックと値の変換 | 321 | 必須・文字数・範囲、全角の数字、整数でない入力 |
| 計算 | 684 | 季節の決まり（前後の季節、範囲、冬の年）、色の判定の境目、今週の期間、週の予算と切り上げの境目、「今日」の線の位置、円グラフの並びと割合、二十四節気の日と七十二候の境目（年をまたぐ、表の外の年）、年中行事の日（節分・お彼岸入りは節気から計算）、グラフへのひとことの型と選び方、季節の終わりまでの日数、使った割合と日の進み方の％、着地見込みの式（整理前の式との一致、予定どおりなら予算、100 円切り上げ、桁あふれは例外）、「予算の ○%」と「上回ります」の境目、円グラフの一覧の判定の引き継ぎ（「その他」は判定なし）、自動予算の計算（過去の同じ季節の組み立て、中央値、傾向の％と上限、ペース、案の選び方と 1,000 円切り上げ・上限、桁あふれ）、メーターの縮尺と赤い部分（予算 0 円を含む）、今週いちばん進んでいる科目の選び方、最初の取引の日から数える着地見込みとペース（予算の日割りを含む） |
| 業務処理（Service） | 334 | 登録・更新・削除の決まり、初期データ、予実比較の集計、256 個の上限、トップの集計（週の境目、季節をまたぐ週、他の人のデータが混ざらないこと）、きょうのひとこと（文面 107 個の件数・番号・空の文、二十四節気の日付の表を国立天文台の日付と照らすこと、グラフへのひとことの文に禁止の言葉がないこと）、トップの数字（残り日数、％、状態の文、円グラフの一覧の判定が今週の進み具合と同じこと、着地見込みが予実比較の合計と同じ値から計算されること）、自動予算（「記録のある年」の判定、根拠の文、他人のデータが混ざらないこと、案を入れても保存しないこと）、予算設定の初期値、最初の取引の日から数えた着地見込みが、トップ・予実比較・自動予算に届くこと、案のもとにした数字をサーバーが決めること |
| セキュリティ | 66 | ログイン必須、CSRF、ログイン・ログアウト、応答のヘッダー、フォントがログインなしで開けること |
| データベース | 70 | 読み書き、制約、集計の SQL（月ごとの科目別の集計を含む）、最初の取引の日（自分の取引だけ） |
| そのほか | 80 | メッセージの文、テンプレートの検査、JavaScript のソースの検査（金額ボタン、予算の合計）、エラー処理、設定、起動 |

自動テストでは確かめにくいものは、ブラウザで確かめました（Phase 8）。全画面を開く、入力チェックの境界値、セキュリティ（ログイン前後のセッション ID の変化、Cookie、エラー画面）、2 人のユーザーでのデータ分離です。確認の記録は、[docs/plans/phase-8-polish.md](plans/phase-8-polish.md) にあります。

## データの分離を確かめているテスト

「他の人のデータが見えない・変更できない」ことは、2 人のユーザーを作って、片方から他方のデータを開く・変更する・削除する・混ぜて送る、という形で確かめています。主なテスト（`クラス名.メソッド名`）は次のとおりです。

**取引**

- 一覧に、自分の取引だけが出る
  - `TransactionControllerTest.listShowsOwnTransactionsNewestDateFirstAndNotOthers`
- 他人の取引の詳細は、存在しない ID・数字でない ID と同じ 404 になる
  - `TransactionControllerTest.detailOfOtherUsersMissingAndNonNumericIdsAreTheSame404`
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

**アカウント登録**

- 初期データ（科目・取引先・支払方法・予算）は、ユーザーごとに別の行として作る
  - `RegistrationServiceTest.eachUserGetsSeparateInitialData`

**データベースの検索**

- 「ID」と「持ち主」の両方で探すと、自分の取引だけが見つかる
  - `TransactionRepositoryTest.findByIdAndUserIdFindsOnlyOwn`

ブラウザでも、2 人のユーザーで、別のユーザーの ID を URL に入れる・送信の内容を書き換える、という確認をしました（Phase 8）。
