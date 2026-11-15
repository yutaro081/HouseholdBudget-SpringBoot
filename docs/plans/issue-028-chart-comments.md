# issue 028：グラフへのひとことを増やす

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は、issue 028（トップの「きょうのひとこと」の、グラフへのひとことを増やす）を進めるための手順書です。もとの作りは [issue 016](issue-016-hitokoto.md)。

> **状態（2026年11月16日）**：◐ **段階 1〔検索の置きかえ〕はコミット済み。段階 2〔型と文の作り直し〕を行った。ブラウザでの確かめと、コミット〔本人〕が残っている**。`.\mvnw clean test` は 2,591 件成功。Q028-1〜5 は決まった〔1 章〕。

- ブランチ：`feature/issue-028-chart-comments`。秘密情報：パスワード・接続先の値は、どこにも書かない。
- 進め方：1 つの手順が終わったら止まって、確認を待つ。変更は、文書が先〔要件定義書 → 設計書 → この手順書〕、コードは後。**テストが先**〔失敗を確かめてから実装〕。コミットと push は本人。
- 今の決まりのまま：評価や命令の言い方をしない。サーバーで作って `th:text` で出す。JavaScript・外部の API・CDN は使わない。`th:utext` は使わない。検索は必ず `user_id` で絞る。
- **例外の書き方**：新しいメソッド名・項目名のせいでテストがコンパイルできないときだけ、先に空の形〔メソッドの宣言、`record`〕を作ってよい〔記録に書く〕。

## 1. 決まったこと〔2026年11月16日・本人〕

| 番号 | 決まったこと |
|---|---|
| 1 | 「予算内」は使わず、「今週は、週の予算がある ○ 科目のうち ○ 科目が、週の予算以下です。」とする〔「予算内」は、チップの言葉で、意味が違うため〕 |
| 2 | 「今週の予算」ではなく「週の予算」と書く。週の始めの文は「新しい週です。週の予算の合計は ○円 です。」 |
| 3 | 1 日あたりの文は「今日を入れて あと ○ 日。残りを日数で割ると、1 日あたり 約 ○円 です。」〔文が「です。」で終わる形〕 |
| 4 | 週の予算を超えたときの文〔「週の予算を○円こえています」〕は、なくす〔合計の行のチップで分かる〕 |
| 5 | 「あと○日」は、今日を入れて数える〔日曜 7 〜 土曜 1〕 |
| 6 | 今週の検索 1 本を、「先週と今週の 2 週間分を、科目と日付ごとに、合計と件数」の 1 本に置きかえる。トップの検索は 7 本のまま |
| 7 | 1 日あたりは、100 円単位で切り捨て〔0 円になったら出さない〕。先週との差は、切り上げた額どうしの差なので、丸めない |
| 8 | 先週との差が 0 円なら「…と同じ額です。」。先週の同じ曜日までが 0 円なら出さない。「少ないです／多いです」と書く |
| 9 | 週の予算以下の数は、今週の取引が 1 件以上のときだけ出す |
| 10 | 週の始めの文が出せる日〔日曜で、今週の取引が 0 件〕は、その文だけを出す |
| A | 土曜の文「今日は土曜です」はなくし、1 日あたりの文〔今日を入れて あと 1 日〕にまとめる |
| B | 今週の取引が 0 件のとき、「未使用」は出さない〔今のコードのまま。`GraphComment.availableTypes` の `totalActual > 0` の条件〕 |
| C | 要件定義書 4.5 の「数字は…」の文を、「科目ごとに 100 円単位に切り上げた額をもとにする」に直す〔1 日あたりと先週の額も、この文で説明できるようにする〕 |
| D | 新しい検索に置きかえたあとも、トップの画面のテスト〔HTML を見るテスト〕は、1 行も変えずに通す〔扱いは Q028-1〕 |
| Q028-1 | D は 2 段階で守る。段階 1〔検索の置きかえ〕は、既存のテストを例外なく 1 行も変えない。段階 2〔文の作り直し〕は、`TopHitokotoControllerTest` の、文そのものを確かめている 6 か所だけ直す |
| Q028-2 | 文の終わりの決まり〔「です。」「ます。」「せん。」〕は変えない。記録の件数の文を「今週の記録は ○ 件です。」にする |
| Q028-3 | 並べる順は、1 日あたり → 週の予算以下の数 → 残りの割合 → 先週と比べる → 記録の件数 → 未使用 |
| Q028-4 | 週の予算以下の科目が 0 科目のときは、「週の予算以下の数」を出さない〔1 科目以上のときだけ〕 |
| E | 「週の予算以下の数」は、全部の科目が週の予算以下のときだけ、文を「今週は、週の予算がある ○ 科目が、すべて週の予算以下です。」〔キー `notover.all`〕に分ける〔「6 科目のうち 6 科目」は不自然なため。Q028-4 と同じ考え〕。一部のときは今の文のまま。出す条件・型の数・並び順・選び方は変えない〔2026年11月16日・本人。ブラウザでの確かめのあと〕 |
| Q028-5 | 名前は、検索 `sumByCategoryAndDate`、入れ物 `DailyActual`、テスト `TransactionRepositoryDailySumTest`、文のキー `perday`・`notover`・`room`・`lastweek.less`／`more`／`same`・`count`・`weekstart` |

型の条件と文は、要件定義書 4.5 と、設計書 5.12 の表に書いた。ここには写さない。

## 2. 段階〔1 段階 ＝ 1 コミット〕

各段階の流れ：**テスト〔先に失敗を確かめる〕→ コード → 関係するテストだけを流す → 止まって確認 → コミット〔本人〕**。全体のテスト〔`.\mvnw clean test`〕は、段階 2 の最後に 1 回だけ。

### 2.1 段階 1：検索を置きかえる〔画面は何も変わらない〕

- **足すもの**：`TransactionRepository.sumByCategoryAndDate(userId, start, endExclusive)`〔設計書 4.6〕と、入れ物 `dto/DailyActual`〔科目ID、日付、合計、件数〕。
- **変えるもの**：`TopService.top` の今週の検索。`sumByCategory(今週)` をやめて、`sumByCategoryAndDate(先週の日曜, 次の日曜)` を 1 回呼ぶ。結果から、今週の科目ごとの実績を作る〔今週の日付の行を、科目ごとに足す。**今までと同じ値**〕。**ひとことの型と文は、この段階では変えない。**
- 同じ結果から作る「今週の取引の件数」と「先週の同じ曜日までの実績の合計」〔先週の日曜から「今日 − 7 日」までの行を、科目ごとに足して、100 円単位に切り上げ、全科目を足す〕は、**段階 2 で作る**〔段階 1 では使う所がなく、テストで確かめられないため。2026年11月16日、段階 1 の実装のときに変えた〕。
- `sumByCategory` は、予実比較が使うので、変えない・消さない。
- **先に書くテスト**
  - 新しいクラス `TransactionRepositoryDailySumTest`〔名前は案〕：科目と日付ごとに分かれること、同じ日の 2 件が 1 行〔合計と件数 2〕になること、開始日は入り終了日の翌日は入らないこと、取引のない科目・日は出ないこと、**他人の取引が入らないこと**。
  - `TopServiceTest` に足す：先週の取引があっても、今週の実績が変わらないこと〔先週の分が今週に混ざらない〕。
- **わざと壊して確かめる**〔守りに関わる所〕：新しい検索から `user_id` の条件を外すと、「他人の取引が入らないこと」のテストが失敗すること。確かめたら戻す。
- **完了の条件**
  - `ScreenStatementCountTest` を **1 行も変えずに**通る〔トップは 7 本のまま〕。
  - **トップの画面のテスト〔HTML を見るテスト〕を、1 行も変えずに、全部通る**：`TopControllerTest`、`TopLayoutTest`、`TopSeasonSentenceTest`、`TopWeekPickTest`、`TopHitokotoControllerTest`、`MeterMarkupTest`〔`controller` パッケージの、トップを開くテスト。ほかにもあれば、それも〕。この段階では、ひとことの文も変わらないので、例外なし。
  - `TopServiceTest` の今あるテストを、1 行も変えずに通る〔足すだけ〕。

### 2.2 段階 2：型と文を作り直す

- **変えるもの**
  - `GraphComment`：型を 7 つにする〔1 日あたり・週の予算以下の数・残りの割合・先週と比べる・記録の件数・未使用・週の始め〕。最多と残りをなくす。**`GraphComment.Row` に、今週の取引の件数と、先週の同じ曜日までの実績〔科目ごとに 100 円単位に切り上げた額〕を足す**。合計〔件数、先週の額〕は、`GraphComment` が行を足して出す。週の始めが出せる日は、それだけを返す。
  - `HitokotoService`：**`hitokoto(…)` の引数は変えない**〔件数と先週の額は、行〔`Row`〕に入って届く〕。`graphLine` を、新しい型のキーと引数にする〔引数は 2 つまで。`HitokotoView.GraphLine` の形は変えない〕。
  - `TopService`：段階 1 の検索の結果から、科目ごとに、今週の取引の件数と、先週の同じ曜日までの実績〔切り上げた額〕を作り、`GraphComment.Row` に入れて `HitokotoService` に渡す。`TopServiceTest` に、この 2 つのテストを足す〔先週の今日と同じ曜日は入り、次の曜日は入らない、など〕。
  - `messages.properties`：`hitokoto.graph.top`、`remaining`、`remaining.saturday`、`over`、`over.saturday` の 5 つを消し、`perday`、`notover`、`room`、`lastweek.less`、`lastweek.more`、`lastweek.same`、`count`、`weekstart` の 8 つを足す〔`unused` は残す〕。文は設計書 5.12。
  - `top/hitokoto.html`：見本の文字〔`th:text` で置きかわる、タグの中の文字〕だけを、新しい文に直す。作りは変えない。
- **先に書くテスト**
  - `GraphCommentTest`〔今 23 件〕：**「あと ○ 日」は、日曜は あと 7 日、土曜は あと 1 日〔今日を含む。日曜〜土曜の 7 日を並べて、7・6・5・4・3・2・1 になることを確かめる〕**。型ごとの条件と境目〔1 日あたりの切り捨てと 0 円、残りが 0 円・マイナス、割の切り捨てと 0 割、同じ割合は先の科目、週の予算以下が 0 科目なら出さない、先週が 0 円、差が 0 円・プラス・マイナス、件数 0、週の始めは日曜だけ・取引 0 件だけ・予算 0 円では出ない、週の始めの日はそれだけ〕、並べる順、日付で選ぶこと〔同じ日は同じ型〕。最多と残りのテストは、なくす。
  - `HitokotoServiceTest`〔今 18 件〕：グラフへのひとことの 3 件〔数字から作ること、出せる型がないとき、キーと引数〕を、新しい型に直す。**暦のひとことのテストは直さない**〔`hitokoto(…)` の引数を変えないため〕。
  - `HitokotoMessagesTest`〔今 9 件〕：キーの一覧、禁止の言葉、文の形〔数字の桁区切り〕を、新しい 9 つの文に直す。文の終わりの決まり〔61 行〕は変えない〔Q028-2〕。
  - `TopHitokotoControllerTest`〔今 16 件〕：文そのものを確かめている 6 か所だけを、新しい文に直す〔Q028-1〕。
- **完了の条件**
  - トップの画面のテスト〔HTML を見るテスト〕は、**ひとことの文そのものを確かめている所〔`TopHitokotoControllerTest` の 269・270・282・294・304・319 行の 6 か所〕を除いて**、1 行も変えずに通る〔Q028-1〕。
  - `ScreenStatementCountTest` を 1 行も変えずに通る〔7 本〕。
  - 科目名の無害化〔`<i>x</i>` が `&lt;i&gt;…` で出る〕のテストが、新しい文〔科目名が入る「残りの割合」か「未使用」〕で残っている。弱めない。
  - `.\mvnw clean test` が全部成功。件数の前と後、増えた・減ったクラスと件数を報告する〔合計が合うこと〕。
  - ブラウザで、本人が確かめる〔今日の日付のまま〕：
    - トップに、グラフへのひとことが 1 文だけ出る。
    - その文の数字が、同じ画面の数字と合う〔例：1 日あたりなら、「今週の進み具合」の合計の行の、週の予算の合計 − 今週の実績の合計 を、今日を入れた日数で割り、100 円単位で切り捨てた額〕。
    - なくした文〔「今週いちばん多いのは」、「週の予算の残りは○円」、「こえています」、「今日は土曜です」〕が出ない。
    - ほかの曜日・ほかの型の文は、ブラウザでは確かめない。日付を固定した自動テストで確かめる〔アプリに日付を変える入口は作らない。issue 016 の決まり。[issue-016-hitokoto.md](issue-016-hitokoto.md) の手順 6〕。
- 段階 2 のあとで直す文書：`docs/testing.md`〔件数〕、README〔テストの件数があれば〕。

## 3. 影響するファイルの数〔見込み〕

| 種類 | 数 | ファイル |
|---|---|---|
| コード | 6 と、見本の文字 1 | `GraphComment`、`HitokotoService`、`TopService`、`TransactionRepository`、`messages.properties`、新しい `DailyActual`。見本の文字だけ `top/hitokoto.html` |
| テスト | 直す 5、足す 1 | 直す：`GraphCommentTest`、`HitokotoServiceTest`、`HitokotoMessagesTest`、`TopHitokotoControllerTest`、`TopServiceTest`〔足すだけ〕。足す：`TransactionRepositoryDailySumTest` |
| 文書 | 5 | 要件定義書、設計書、この手順書、`features.md`〔直した〕、`testing.md`〔段階 2 のあと、件数だけ〕 |

## 4. 決まっていない点

なし〔Q028-1〜5 は、2026年11月16日に決まり、1 章に移した〕。

## 5. 実施の記録

- 2026年11月16日：調べものと案を報告。決めてほしいこと 1〜10 は、すべておすすめどおりに決まった。A〜D が足された。要件定義書 4.5、設計書〔4.5 の `TopService`・`HitokotoService`・`GraphComment`、4.6 の `TransactionRepository`、4.7 の `DailyActual`、5.11 の図、5.12〕、`features.md` を直し、この手順書を作った。実行計画書と `testing.md` は、今は直す所なし〔`testing.md` の件数は、段階 2 のあと〕。
- 2026年11月16日：Q028-1〜5 が決まった〔Q028-2 は文を「今週の記録は ○ 件です。」に、Q028-4 は 0 科目なら出さない〕。要件定義書・設計書・この手順書を直した。**段階 1 を行った**：先に `TransactionRepositoryDailySumTest`〔6 件〕と `TopServiceTest.lastWeeksTransactionsAreNotMixedIntoThisWeek` を書いた。前者は `DailyActual`・`sumByCategoryAndDate` がなくてコンパイルできないことを確かめた。後者は今の動きを守るテストで、TopService を変える前から通る。検索から `user_id` の条件をわざと外すと、6 件すべてが失敗することを確かめて、戻した。既存のテストは 1 行も変えていない。
- 2026年11月16日：段階 1 のあとの確認。`TopService` の「今週の日付の分だけを足す」条件をわざと外すと、`TopServiceTest` の 3 件〔足した `lastWeeksTransactionsAreNotMixedIntoThisWeek` と、前からある 2 件〕が失敗することを確かめて、戻した。`TransactionRepositoryDailySumTest.otherUsersTransactionsAreNotCounted` は、ほかの人の取引をテストの中で作っている〔開発用の DB のデータに頼らない〕。
- 2026年11月16日：**段階 2 を行った**。
  - 先にテストを書いて、62 件の失敗を確かめた：`GraphCommentTest` 43 件、`HitokotoServiceTest` 3 件〔どちらも、新しい型・`Row`・`Result` がなくてコンパイルできない〕、`HitokotoMessagesTest` 11 件〔新しいキーがない・なくすキーがある〕、`TopHitokotoControllerTest` 3 件、`TopServiceTest` 2 件。科目名の無害化のテスト〔`theUnusedCommentEscapesTheCategoryName`〕は、今のコードでも同じ日に「未使用」が選ばれるので、実装の前から通った。
  - 作りは、手順書の初めの案〔`hitokoto(…)` の引数を増やす〕ではなく、`GraphComment.Row` に件数と先週の額を足す形にした〔引数を増やすと、暦のひとことのテストの呼び出しまで直すことになるため。本人の確認を受けて、手順書を直した〕。
  - わざと壊して確かめた〔どちらも戻した〕：先週の「同じ曜日まで」の絞り込みを外すと、`TopServiceTest` の 2 件が失敗〔`expected: 1400L but was: 6400L` など〕。週の始めの「この文だけを出す」をやめると、`GraphCommentTest` と `TopHitokotoControllerTest` の `onASunday…` の 2 件が失敗。
  - 既存のテストで直したもの：`TopHitokotoControllerTest` の文を確かめている 6 か所〔2 つのテストの名前も変えた〕、`HitokotoMessagesTest` のキーの一覧と文、`HitokotoServiceTest` のグラフへのひとことの 3 件、`GraphCommentTest`〔書き直し〕。**決まりの外まで直したテスト**〔本人の確認済み〕：`Row` の作り方が変わったため、`GraphCommentTest` の「未使用」と「選び方」のテスト、`HitokotoServiceTest.noTypeAvailableGivesNoGraphComment` も直した〔確かめる中身は変えていない〕。
  - `.\mvnw clean test`：2,564 件 → 2,591 件〔`GraphCommentTest` +20、`HitokotoMessagesTest` +4、`TopServiceTest` +2、`TopHitokotoControllerTest` +1〕、失敗 0 件。`ScreenStatementCountTest` は 1 行も変えずに通った〔トップ 7 本〕。`docs/testing.md` と README の件数を直した。
- 2026年11月16日：本人がブラウザで確かめた〔今日の文は「今週は、週の予算がある 6 科目のうち 6 科目が、週の予算以下です。」。数字は画面と合っていた〕。「6 科目のうち 6 科目」が不自然なので、全部のときだけ文を分けることにした〔1 章の E〕。要件定義書 4.5、設計書 5.12 を直した。先にテストを書き、5 件の失敗を確かめた〔`HitokotoServiceTest.notOverUsesTheAllSentenceOnlyWhenEveryCategoryIsWithinItsBudget`、`HitokotoMessagesTest` の 3 件〔新しいキーがない〕、`TopHitokotoControllerTest.showsOneGraphCommentMadeFromTheWeeklyNumbers`〕。`HitokotoService.graphLine` と `messages.properties`〔`hitokoto.graph.notover.all` を足した〕を直した。既存のテストで直したのは、`showsOne…` の候補の文 1 つ〔「1 科目のうち 1 科目が」→「1 科目が、すべて」〕だけ。`.\mvnw clean test`：2,591 件 → 2,593 件〔`HitokotoServiceTest` +1、`HitokotoMessagesTest` +1〕、失敗 0 件。

## 6. 後回しの候補

- `GraphComment.Row` は、数字の引数〔今週の実績、件数、先週の実績〕が 3 つ並んでいて、入れ違えてもコンパイルで気づけない〔今回は直さない〕。
