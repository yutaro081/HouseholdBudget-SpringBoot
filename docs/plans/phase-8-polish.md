# Phase 8：仕上げ（総合テスト・セキュリティ確認）

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は Phase 8 を実際に進めるための手順書です。

- 前の Phase：[Phase 7 予実比較](phase-7-comparison.md)（☑ 完了。`main` に取り込み済み、タグ `phase-7-done`）
- 次の Phase：[Phase 9 完成・引き渡し](execution-plan.md#phase-9完成引き渡し)
- 状態：☐ 未着手（手順書を作り、「5. 決まっていない点」Q8-1〜Q8-23 は**すべて決定済み**。4.3 の G1〔年と季節を選ぶ欄〕と Bootstrap の導入を 4.11・4.12 に足した。4.11 からは、コードを変える）
- 作業ブランチ：`feature/phase-8-polish`

## 次に再開するところ（2026年11月3日の状態）

**4.20（issue 007）は済み**〔コード・テスト・ブラウザ。1255 件成功〕。**4.21（issue 005）の 💮 は、コード・テスト・ブラウザ確認まで済み〔1302 件成功〕。「4.21 の続き」〔💮 の行の背景を薄い緑に、💮 を大きく〕も、ブラウザの確認まで済み〔1306 件成功〕。issue 005・007 は対応済み。次は 4.14〜。**

- **済み**：4.1〜4.13、4.16（F2 の修正。iPad の Safari の確認は Phase 9）。`.\mvnw test` は **1255 件**成功。4.9・4.10・4.20 も済み〔`chkzero001` は残してある〕。
- **4.21 の文書は直した**：要件定義書〔3章と 4.4 の 💮〕、設計書〔2.3、2.4、4.4.2、4.5、`ComparisonRow`、5.9、P13、F5〕、issue 005・007。Q8-27〜Q8-35 は決定済み〔すべて A〕。
- **残り**：
  - **4.21**（💮）。コードはまだ書いていない。
  - **4.14**（自動テスト。登録画面のメッセージのテストを足す。`Cache-Control` のテストを足すかも決める）、**4.15**（コードの整理）、**4.17**（片付け）、**4.18**（文書）、**4.19**（コミット）。4.16 の iPad の確認は Phase 9。
- **4.17 で片付けるもの**：アカウント `chkipad`（と取引 3 件など）、`chkzero001`、4.6 で作った `T1科目`・`T2科目` などの確認用データ、4.7 の B6 の取引と 2030年春の予算、4.8 の E2 の取引と 2031年春の予算。
- **残っているもの**：Java の広い許可ルール 12 個は**無効にしてある**〔`Enable-NetFirewallRule` で戻せる〕。ネットワーク `Wi-Fi 2` は `Public`。
- **ブランチ**：`feature/phase-8-polish`。

## 1. 目的

要件を満たしているか、一通り確かめて、見つかった不具合を直し、コードを整理します。**新しい機能は作りません。**（4.3 で見つかった要件との食い違い G1 を直す「年と季節を選ぶ欄」と、見た目を整える Bootstrap の導入は、あなたの指示で足しました。）次の状態にします。

- 要件定義書の機能一覧（3章）と見比べて、**機能の漏れがない**ことを確かめた。
- 入力チェック（5章）の**全項目**を、境界値で、ブラウザで確かめた。
- セキュリティ（6章）の確認表（設計書 7.11）が、**すべて合格**している。2人のユーザーでのデータ分離を、全画面（取引・科目・取引先・支払方法・予算・予実比較）で確かめた。
- これまでの Phase から引き継いだ「ブラウザで未確認の項目」を、すべて確かめた。**科目が0件のアカウントの予実比較**を含む。
- 主要な処理の自動テスト（登録時の初期データ、データ分離、予実比較）に、足りないものがない。
- コードの整理が済んでいる。issue 001（`@RequestMapping` をクラスにまとめるか）を**決めて**、記録した。
- 年と季節を選ぶ欄を足した（要件 4.3 の「選べる」。4.3 の G1、4.11）。Bootstrap の CSS を入れて、見た目を整えた（4.12）。
- 面接で iPad から触ってもらえるか確かめた：パソコンで起動したアプリを、同じネットワークの iPad の Safari から `http://（パソコンの IP アドレス）:8080/` で開けて、ログインでき、全画面が使える（4.13）。

## 2. 前提と、この Phase でやらないこと

- Phase 7 が完了し、`main` に取り込み済み（タグ `phase-7-done`）。環境変数 `HB_DB_URL`、`HB_DB_USER`、`HB_DB_PASSWORD` が設定された VS Code で作業する。`root` はアプリで使わない（実行計画書 ルール8）。
- 自動テスト（`.\mvnw test`）は、Phase 7 の終わりの時点で **1205 件すべて成功**。この数が、Phase 8 の出発点（4.1 で確かめ直す）。
- 開発用の DB の状態（Phase 7 の終わり）：ユーザーは `test1`、`test2`。`transactions` は **0 行**（Phase 7 の確認のあと、残してあった1件も画面から削除した）。`budgets` は **9 行**（`test1` の2026年 秋・冬の各3科目、`test2` の2026年 秋の3科目）。
- 作成済みのもの：要件のすべての機能（ログイン・登録、取引、科目・取引先・支払方法、予算、予実比較）、エラー画面4種（404、403、その他の 4xx、5xx）、自動テスト。
- **やらないこと**：
  - 新しい機能（要件定義書 9章「今後の予定」のものは、Phase 9 が終わるまで手を付けない）。
  - README の仕上げ、画面のスクリーンショット、完成タグ（`v1.0.0`）、振り返り（Phase 9）。
  - インターネットへの公開（要件定義書 9章。デプロイは、実行計画書の Phase 10〔任意・候補〕）。4.13 の iPad の確認は、**同じネットワークの中だけ**で、公開ではない。
  - スマホ・タブレット向けの画面づくり（要件定義書 9章「スマホ向けの画面」）。4.13 では、今の画面が iPad で使えるかを**確かめるだけ**で、作り替えない（見つかった崩れの扱いは Q8-12）。
    - 4.12 の Bootstrap は「見た目を整える」ためで、**スマホ向けの画面づくりではない**（要件定義書 9章は変えない。Q8-23）。Bootstrap の JavaScript は入れない。
  - 確認で見つかった**要件の変更**を、確認なしに入れること（3.3 の決まり）。
- **この手順書にも、コードにも、設定ファイルにも、パスワードを書かない。** 確認用のアカウントのパスワードも書かない。
- 設計書で決まっていない点が出たら、勝手に決めず、「5. 決まっていない点」に載せて確認する。

## 3. 進め方の方針

### 3.1 確認の3つの方法

| 方法 | 向いているもの | この Phase での使い方 |
|---|---|---|
| 自動テスト（`.\mvnw test`） | 計算・入力チェック・URL ごとの動き・データ分離 | すでにあるテストを土台にして、足りない所を足す（4.14）。**自動テストは、`open-in-view=false` の読み込み忘れ（500）と、ブラウザの動き（Cookie、ダイアログ）は確かめられない** |
| コードの検索 | 「使っていないこと」「必ずやっていること」 | `findById`、`th:utext`、文字列連結の SQL など（4.4） |
| ブラウザ（と SQL） | 画面の見た目、500 が出ないこと、状態コード、Cookie、DB の中身 | 4.5〜4.10。**ブラウザの操作、MySQL へのログインと SQL の実行は、あなたが行う。** Claude Code は手順と期待値を示し、結果を受けて記録する |

### 3.2 確認用のアカウントとデータ

- 2人のユーザー：`test1`、`test2`（Phase 4 から使っているアカウント）。パスワードは、これまでどおり自分の控えを使う。文書には書かない。
- **科目が0件のアカウント**（4.9）と、**256個の上限の確認**（4.10）は、`test1`・`test2` を壊さないように、**新しいアカウントを1つ作って**使う（Q8-3。名前の案：`chkzero`。ユーザー名は半角英数字10文字以下）。
- 確認のために作ったデータ（取引、一時的な科目・取引先・支払方法、確認用のアカウント）は、4.17 で片付ける（Q8-4）。**アカウントを消す画面は無い**（要件にない）。消すときは、SQL（あなたが実行）で行う。

### 3.3 不具合が見つかったとき

1. **直す前に、報告する。** 何を操作して、何が起きたか（期待と実際）、どの画面・URL・メッセージかを書く。
2. `docs/issues/` に1件1ファイルで記録する（番号は 004 から。書き方は [docs/issues/README.md](../issues/README.md)）。小さいものは、この手順書の「実施の記録」に書くだけでもよい（Q8-2 の決定による）。
3. **要件や設計に関わる直し**（入力の範囲、表示の決まり、画面の項目）は、**先に文書**を直す。要件定義書は、直す前の文と直した後の文を見せてから直す。そのあとコード。
4. 直したら、再現した操作をもう一度行い、直ったことを確かめる。自動テストで防げるものは、テストを足す。
5. 「直さない」と決めたものは、理由を記録する（完了条件 6）。

### 3.4 確認結果の記録

- 確認の結果は、この手順書の各項目のチェックと、各節の末尾の「（実施の記録：…）」に書く（実行計画書 ルール6、Q8-2）。
- 期待どおりでなかった項目は、チェックを付けず、3.3 の手順で記録する。

## 4. 手順

> **進め方**：Phase 4〜7 と同じ。ファイルの作成・編集と、読むだけのコマンドは Claude Code が行う。パスワードの入力（MySQL へのログイン）、MySQL へ書き込む SQL、ブラウザの操作、コミット・push は自分で行う。*

### 4.1 作業ブランチと土台を確認する

- [x] `feature/phase-8-polish` にいる（`git branch --show-current`）
- [x] MySQL のサービス（`MySQL97`）が動いている（Running）
- [x] 環境変数 `HB_DB_URL`、`HB_DB_USER`、`HB_DB_PASSWORD` が設定されている（値は表示しない）
- [x] `.\mvnw test` が全部成功する（1205 件が出発点。件数を記録する）（結果：1205 件、失敗 0、エラー 0、スキップ 0、BUILD SUCCESS。ログの警告は、Q8-7 の2つ〔HV000271、Mockito〕だけ）
- [x] 開発用の DB の状態を控える（`hb_app` でログイン。読むだけの SQL。結果は、片付け〔4.17〕のときの比べ元になる）：

```sql
SELECT id, username FROM users ORDER BY id;

SELECT
  (SELECT COUNT(*) FROM users)           AS users_rows,
  (SELECT COUNT(*) FROM categories)      AS categories_rows,
  (SELECT COUNT(*) FROM vendors)         AS vendors_rows,
  (SELECT COUNT(*) FROM payment_methods) AS payment_methods_rows,
  (SELECT COUNT(*) FROM budgets)         AS budgets_rows,
  (SELECT COUNT(*) FROM transactions)    AS transactions_rows;
```

  期待：ユーザーは `test1`、`test2` の2行。`budgets_rows` は **9**、`transactions_rows` は **0**。ほかの件数は控える。

  （実施の記録：`users_rows` 2、`categories_rows` 6、`vendors_rows` 6、`payment_methods_rows` 4、`budgets_rows` 9、`transactions_rows` 0。期待どおり。4.17 の片付けのあとは、この値に戻る〔`test1`・`test2` の分だけ〕。）

### 4.2 issue 001 を決める（Controller の URL を、クラスの `@RequestMapping` にまとめるか）

[docs/issues/001](../issues/001-RequestMappingをクラスにまとめる.md) の「決めること」です。**動きは変わらない**（改善の案）。決めるのは、あなたです（Q8-1）。

**今の状態**（Controller のメソッドの URL は、どれも全体がそのまま書いてある。設計書 2.2 の URL の一覧と見比べやすい）：

| Controller | 共通の親のパス | メソッドの数 | まとめられるか |
|---|---|---|---|
| `CategoryController` | `/categories` | 5 | まとめられる（VS Code のお知らせが出ている） |
| `VendorController` | `/vendors` | 5 | 同上 |
| `PaymentMethodController` | `/payment-methods` | 5 | 同上 |
| `TransactionController` | `/transactions` | 7 | まとめられる |
| `BudgetController` | `/budgets` | 2 | まとめられる（メソッドは `GET` と `POST` の同じパス） |
| `ComparisonController` | `/comparison` | 1 | まとめられる（1つだけ） |
| `AuthController`、`HomeController` | なし（`/login`、`/register`、`/`） | 3、1 | まとめない（親のパスが違う） |

**直す場合（Q8-1 の A。★これに決定）の作業量**
- 変えるもの：上の6つの Controller。クラスに `@RequestMapping("/categories")` などを6行足し、各メソッドの URL から親のパスを取る（`@GetMapping("/categories/{id}/edit")` → `@GetMapping("/{id}/edit")`、`@GetMapping("/categories")` → `@GetMapping`）。**メソッドの注釈は 25 か所**（5＋5＋5＋7＋2＋1）。合計で約 31 行。
- 変えないもの：テンプレート（`th:href="@{/categories}"` などはそのまま）、URL の自動テスト（`…ControllerTest` は URL の文字列で呼ぶので、変えなくてよいはず）、`SecurityConfig`（URL の許可は別に書いてある）。
- 文書：設計書 4.4.1 に、「URL は、クラスの `@RequestMapping` と、メソッドの注釈を合わせて読む」と1〜2行足す。実行計画書 ルール8 に、書き方の決まりを1行足す。
- 確認：`.\mvnw test`（1205 件が成功のまま）。ブラウザで、各画面を1回ずつ開く（4.5 と同時にできる）。
- 目安：1つの手順。リスクは小さいが、**動いているコードを、最後の Phase で6ファイル触る**ことになる。

**直さない場合（Q8-1 の B。採らなかった）の作業量**
- コードは変えない。理由（設計書 2.2 の URL の一覧と、コードを見比べやすい）を、**設計書 4.4.1 に1行**残す。issue 001 の対応状況を「対応しない（理由…）」にする。
- VS Code のお知らせは、残る（エラーでも警告でもない）。
- 目安：文書の数行だけ。

**一部だけ直す場合（Q8-1 の C。採らなかった）**：お知らせが出た3つ（科目・取引先・支払方法）だけ直す。作業量は A の約 3 分の 2（15 か所）。ただし、書き方が Controller ごとにそろわなくなる（issue 001 に「全部で書き方をそろえる」とある）。

- [x] Q8-1 を決めた：**A**（6つの Controller すべて、クラスの `@RequestMapping` にまとめる。「5. 決まっていない点」に決定と理由を書いた）
- [x] 決めたとおりに行った（Q8-1 は A なので、コードと文書）。コードの整理（4.15）を待たず、先に 4.2 で行った
- [x] issue 001 の対応状況を更新した（「対応済み」。関係するコミットは、コミットのあとに書く）

（実施の記録：先に設計書 4.4.1 に URL の読み方を足し、次にコードを直した。6つの Controller に、クラスの `@RequestMapping`（`/categories`、`/vendors`、`/payment-methods`、`/transactions`、`/budgets`、`/comparison`）を足し、メソッドの注釈 25 か所（5＋5＋5＋7＋2＋1）から頭の部分を取った。`git grep` で、メソッドの注釈に残った URL を一覧にして、取り忘れがないことを確かめた。テスト・テンプレート・`SecurityConfig` は変えていない。`.\mvnw test` は 1205 件すべて成功〔`BudgetControllerTest` 86、`CategoryControllerTest` 40、`ComparisonControllerTest` 21、`PaymentMethodControllerTest` 38、`TransactionControllerTest` 107、`VendorControllerTest` 38 ほか〕。）

### 4.3 機能の漏れを確認する（要件定義書 3章、4章）

要件定義書の機能と、実際の画面を見比べる。表の「確かめ方」を、ブラウザで行う（`test1` でログイン。取引がまだ無いので、取引は4.5 で登録する）。

| 要件 | 画面・URL | 確かめ方 |
|---|---|---|
| ログイン／ログアウト／アカウント登録（4.1） | `/login`、`/register`、ログアウトボタン | 登録できる。ログインできる。ログアウトすると、ログイン画面で「ログアウトしました」 |
| 取引の一覧・登録・詳細・編集・削除（4.2） | `/transactions`、`/transactions/new`、`/transactions/{id}`、`/transactions/{id}/edit` | 5つの操作ができる。削除の前に確認ダイアログが出る。一覧は日付の新しい順 |
| 入力チェック、エラーで戻っても入力値が残る（3章、5章） | 各入力画面 | 4.7 で確かめる |
| 科目の追加・変更・削除（4.3） | `/categories` | 3つの操作ができる。削除の前に確認ダイアログ。取引で使用中・0円でない予算がある科目は削除できない |
| 取引先の追加・変更・削除（4.3） | `/vendors` | 同上（予算の条件は無い） |
| 支払方法の追加・変更・削除（4.3） | `/payment-methods` | 同上 |
| 初期データ（4.3） | 新規登録の直後 | 科目3つ（交通費・食費・雑費）、今の季節の予算が各30,000円、取引先3つ（コンビニ・ガソリンスタンド・Amazon）、支払方法2つ（現金・クレジットカード）が用意される（4.9 の新しいアカウントで確かめる） |
| 科目を追加すると、今の季節の予算が0円で作られる（4.3） | `/categories`、`/budgets` | 科目を追加し、予算設定で今の季節が0円で出る（4.9 の手順 8 で確かめる） |
| 予算の設定（季節ごと。季節を指定しないときは今の季節）（4.3） | `/budgets` | 4.5、4.7 で確かめる |
| 予実比較（季節ごと。予算・実績・差額・合計行。赤・青）（4.4） | `/comparison` | Phase 7 で確認済み。4.5、4.6 の(6)、4.9 で確かめ直す |
| ユーザーごとのデータ分離（4.1） | すべての画面 | 4.6 の(6) |

- [ ] 要件の機能で、画面から使えないものがない（漏れがない）
- [ ] 要件にない機能が、画面にない

（実施の記録：Claude Code が、要件定義書 3章・4章の各文を、Controller の URL 一覧、テンプレート 19 枚、Service（削除の条件、上限、初期データ）、`SecurityConfig` と見比べた。ブラウザでの動作の確認は、表のとおり 4.5〜4.9 で行うので、上の2つのチェックは、その後に付ける。）

**見比べの結果**

- 画面と処理が見つかったもの（要件の文と一致）：ログイン・ログアウト・登録（`/login`、`/register`、`POST /logout`、「ログアウトしました」）／取引の5操作（`/transactions` ほか。一覧は日付の新しい順＝ `findByUserIdOrderByTransactionDateDescIdDesc`）／削除前の確認ダイアログ（取引の詳細、科目、取引先、支払方法の4か所。`confirm('削除しますか？')`）／科目・取引先・支払方法の追加・変更・削除／削除できない条件（取引で使用中。科目は、0円でない予算があるときも）／上限 256 個（3 Service）／初期データ（交通費・食費・雑費＝30,000円、コンビニ・ガソリンスタンド・Amazon、現金・クレジットカード）／科目の追加で今の季節の予算を0円で作る（4.3 の文）／予算設定（`/budgets`、季節の指定なしは今の季節）／予実比較（`/comparison`）／ログイン前に開けるのは `/login`・`/register`（と `/css/**`、`/error`）だけ。
- 要件にない機能は、見つからなかった（画面は、要件の機能の画面と、エラー画面4種だけ）。

**食い違い（1件）**

| # | 要件定義書 | 実際 | 影響 |
|---|---|---|---|
| G1 | 4.3「予算の設定画面を作成する。**設定する年と季節を選べる**」 | 年・季節を**選ぶ入力欄（プルダウンなど）は無い**。「前の季節」「次の季節」のリンクで1つずつ動くか、URL の `year`・`season` を書き換える（設計書 4.4.2、Q6-5 で、この形に決めた） | 例：2026年の秋から 2100年の冬まで行くには、リンクを約 300 回押す。実際に使う範囲（前後数季節）では困らない。要件の文言は「選べる」だが、設計では「リンクで動く」に決まっていて、要件と設計が合っていない |

**G1 は (b) に決定。4.11 で対応する**（年と季節を選ぶ欄を足す）。

G1 の対応案（決めるのは本人。ここでは直していない）：(a) 今のまま。要件の文を「前後の季節へ移動して設定できる」に直す（要件定義書は、直す前後の文を見せてから直す）。(b) 年と季節を選ぶ入力欄を足す（予算設定と予実比較の両方。設計書 4.4.2 の変更を伴う）。

### 4.4 コードの検索で確かめる（読むだけ。Claude Code が行う）

`src/main` を対象に、次を検索する。結果は、期待と照らして記録する。

| 確かめること | 検索 | 期待 |
|---|---|---|
| `findById` などを使っていない（設計書 3.9） | `findById`、`getReferenceById`、`getById`、`findAllById` の**呼び出し** | 0 件（コメントの説明と、`findByIdAndUserId` は除く） |
| すべての Repository のメソッドが `userId` を引数に取る（`UserRepository` を除く） | `repository` パッケージのメソッドの一覧を読む | すべて `userId` あり。`@Query` は `sumByCategory` の1か所で、`:userId` を使っている |
| 文字列連結の SQL がない（7.4） | `createQuery`、`createNativeQuery`、`JdbcTemplate`、`nativeQuery`、`"select`、`"SELECT` | 0 件 |
| `th:utext` を使っていない（7.5） | `th:utext\s*=` | 0 件（コメントの説明だけ） |
| CSRF 対策を無効にしていない（7.6） | `csrf` | `disable` などが 0 件 |
| データを変える操作がすべて POST（2.1、7.6） | `@GetMapping` のメソッドの中身を読む | GET で保存・更新・削除をしているものがない |
| すべての `<form method="post">` が `th:action`（CSRF のトークンが入る） | `method="post"` と `th:action` | `method="post"` のフォームがすべて `th:action` |
| 秘密が書かれていない（7.9） | `git grep -n -i -e password -- . ":(exclude)docs" ":(exclude)mvnw" ":(exclude)mvnw.cmd"` | `phase-7-done` の時点（96 行）から**増えていない**。値ではなく名前・説明・ダミーだけ |
| 秘密が履歴に入っていない | `git log` で、`.env`・`*.dump`・`*.bak`・`application-local.properties` が追加された記録がない | 0 件 |
| `.gitignore` の内容（S7） | `.gitignore` を読む | `backup/`、`*.dump`、`*.dump.sql`、`*.bak`、`.env`、`application-local.properties` がある。`schema.sql` は除外されていない |
| 標準の英語エラーページが出ない設定（S2、S3） | `application.properties` の `server.error.*` | スタックトレース・メッセージ・バインディングエラー・例外を含めない設定がある |
| `open-in-view=false` | `application.properties` | `spring.jpa.open-in-view=false` |

- [x] 上の表のすべてが期待どおり（外れたものは、3.3 で記録する）。外れたのは F1 の 1 件で、(a) で直した（下の記録）

（実施の記録・4.4：2026年11月2日。Claude Code が `src/main` を検索した。コードは変えていない。**12 項目のうち 11 項目は期待どおり。1 項目〔秘密の書き方〕に、文書の指摘が 1 件ある。**）

| # | 確かめたこと | 結果 |
|---|---|---|
| 1 | `findById` などの呼び出し | 0 件。`findById`・`getReferenceById`・`getById`・`findAllById`・`deleteById`・`existsById`・`findOne` を検索して、あるのはコメントの説明と `findByIdAndUserId`〔科目・取引先・支払方法・取引の Service が各1か所〕だけ。継承されて使える `findAll`・`deleteAll`・`count()`・`saveAll` なども、呼び出しは 0 件 |
| 2 | Repository のメソッドが `userId` を取る | `UserRepository` を除く5つ〔Budget、Category、PaymentMethod、Transaction、Vendor〕の宣言すべてに `userId` がある。`@Query` は `TransactionRepository.sumByCategory` の1か所で、`where t.userId = :userId`、パラメータは `@Param` で渡している |
| 3 | 文字列連結の SQL | 0 件〔`createQuery`、`createNativeQuery`、`JdbcTemplate`、`nativeQuery`、`"select`、`"SELECT`、`"delete `、`"update `、`"insert `〕 |
| 4 | `th:utext` | 属性としての使用は 0 件。出るのは「使わない」というコメントの文字だけ〔Controller 6、テンプレート 4〕。`[(…)]` の形〔エスケープしない出力〕も 0 件 |
| 5 | CSRF 対策 | `csrf` の検索結果は説明のコメントだけ。`disable` などは 0 件。`SecurityConfig` に `csrf` の指定がなく、標準の有効のまま |
| 6 | `@GetMapping` のメソッドの中身 | 全 `@GetMapping` の中身を読んだ。`model.addAttribute`、読み取りの Service の呼び出し〔`list`、`get`、`edit`〔画面用の値を作るだけ。`save` は別〕、`compare`〕だけで、保存・更新・削除をしているものは 0 件 |
| 7 | `<form method="post">` が `th:action` | `<form>` タグ 16 個〔POST 15、GET 1〕をコメントを除いて調べた。POST の 15 個はすべて `th:action`〔CSRF のトークンが入る〕。GET の1個は `fragments/season.html` の年と季節を選ぶ欄〔何も変えないので `_csrf` は不要。設計書 2.4〕 |
| 8 | 秘密が書かれていない | `git grep -n -i -e password`〔`docs`・`mvnw` を除く〕は **96 行**で、`phase-7-done` の時点と同じ。ファイルごとの行数の差もなし。接続先・BCrypt のハッシュ・鍵らしき値の検索〔`jdbc:mysql`、`$2a$`、`BEGIN PRIVATE`、`api_key`、`secret=`〕は、`docs` を除いて 0 件。`application.properties` は `${HB_DB_URL}` などの環境変数の参照だけ。**ただし文書に1件の指摘あり〔下の F1〕** |
| 9 | 秘密が履歴に入っていない | `.env`・`*.dump`・`*.dump.sql`・`*.bak`・`application-local.properties`・`backup/`・`*.pem`・`*.key` が追加された記録は 0 件。追跡中のファイルにも該当なし |
| 10 | `.gitignore` | `backup/`、`*.dump`、`*.dump.sql`、`*.bak`、`.env`、`application-local.properties` がある。`schema.sql` は除外されていない〔`git check-ignore` が該当なし、追跡中〕 |
| 11 | 標準の英語エラーページが出ない設定 | `server.error.include-stacktrace=never`、`include-message=never`、`include-binding-errors=never`、`include-exception=false` |
| 12 | `open-in-view=false` | `spring.jpa.open-in-view=false` |

**F1（文書の指摘。(a) に決定して直した〔2026年11月2日〕。下の「直した内容」）**：`docs/plans/phase-2-base.md` の 292 行目の表に、接続先の URL の具体的な値〔ホスト、ポート、データベース名を含む〕が書いてあった〔ホストは自分のパソコン、パスワードはなし。データベース名は README にも載っている〕。CLAUDE.md の「パスワードや接続先の値は、コード・文書・コミットメッセージのどこにも書かない」に、文字どおりには当たる。Phase 3 の手順書の 494 行目には「`jdbc:mysql`、`3306`、`@localhost` はリポジトリにない」とあり、そことも食い違う。ほかに、`docs/plans/phase-2-base.md` には DB のユーザー名 `hb_app` と `'hb_app'@'localhost'` が多数ある〔ユーザー名は「接続先の値」とは書かれていないので、指摘には入れない〕。**案**：(a) 292 行目を `jdbc:mysql://（ホスト）:（ポート）/（データベース名）` の形に直す〔Phase 2 の記録の文書なので、直す前後の文を見せて確認〕。(b) 履歴にも残る〔Phase 2 からコミット済み〕ので、直しても履歴は消えない。値は自分のパソコンの既定のものなので、直さずに理由を記録する。

**F1 の直した内容**：`docs/plans/phase-2-base.md` の 292 行目〔4.6 の環境変数の表〕を、伏せた形に直した。`phase-3-auth.md` の 494 行目にも、同じ値を引用した文があったので、「伏せた形に直した」という説明に直した〔値は書かない〕。履歴〔Phase 2 のコミット〕には元の文が残る。値はパスワードなしの自分のパソコンの既定のものなので、履歴の書き換えはしない。
- 前：4.6 の環境変数の表の「接続先」の行に、接続先の URL の具体的な値〔ホスト、ポート、データベース名を含む〕が書いてあった。〔この手順書にも値は書かない。履歴には残る〕
- 後：`| 接続先 | `HB_DB_URL` | `jdbc:mysql://（ホスト）:（ポート）/（データベース名）` の形（ホストは自分のパソコン、ポートは MySQL の既定、データベース名は 4.4 に合わせる。具体的な値は文書に書かない） |`

### 4.5 すべての画面を、ブラウザで開く（`open-in-view=false` のまま）

**自動テストでは見つからない読み込み忘れ（`LazyInitializationException`、500）を探す**（Phase 5・6・7 から引き継ぎ。設計書 4.6）。`.\mvnw spring-boot:run` で起動し、`test1` でログインして、次の画面を**すべて**開く。500 の画面が出なければ合格。ターミナル（アプリのログ）にも `LazyInitializationException` が出ていないことを見る。

先に、取引を1件登録する（登録画面〔`/transactions/new`〕から。日付は今日の日付、金額 1,500円、食費、コンビニ、現金。確認のあと 4.17 で削除する）。

| 画面 | URL | 開き方・確認 |
|---|---|---|
| ログイン／登録 | `/login`、`/register` | ログアウト後に開く。登録画面で、わざと入力エラー（ユーザー名の空欄）にして、表示し直しを開く |
| 取引一覧 | `/transactions` | 登録した取引が出る（科目・取引先・支払方法の名前が出る） |
| 取引の登録 | `/transactions/new` | 開く。わざと入力エラー（金額に `abc`）にして、表示し直しを開く（入力値が残る） |
| 取引の詳細 | `/transactions/{id}` | 一覧の行から開く。科目・取引先・支払方法の名前が出る |
| 取引の編集 | `/transactions/{id}/edit` | 開く。わざと入力エラーにして、表示し直しを開く |
| 科目 | `/categories`、`/categories/{id}/edit` | 一覧。追加で入力エラー（空欄）にして表示し直し。変更画面を開き、入力エラーで表示し直し |
| 取引先 | `/vendors`、`/vendors/{id}/edit` | 同上 |
| 支払方法 | `/payment-methods`、`/payment-methods/{id}/edit` | 同上 |
| 予算設定 | `/budgets`（指定なし、`year=2026&season=autumn`、`season=winter`、`year=2000&season=spring`、`year=2100&season=winter`） | 開く。入力エラー（予算額に `abc`）で表示し直し。保存して、同じ季節へ戻る |
| 予実比較 | `/comparison`（上と同じ5つの指定） | 開く。取引の季節で実績が出る（食費 1,500円） |
| 404 | 存在しない URL（例：`/nope`）、`/transactions/abc` | 「ページが見つかりません」と取引一覧へのリンク |
| 403 | 4.6 の(5)で開く | 「操作を完了できませんでした。もう一度やり直してください」 |
| その他の 4xx | 4.6 の(7)で開く | 同じ文言（`error/4xx.html`） |
| 5xx | 4.6 の(7)で開く（Q8-5） | 「エラーが発生しました。しばらくしてからもう一度お試しください」 |

- [x] すべての画面が開く（500 が出ない。ログにも `LazyInitializationException` がない）
- [x] 入力エラーで表示し直した画面も、500 にならず、入力値が残っている

（実施の記録・4.5：2026年11月2日。あなたが行い、手順 1〜14 すべて OK。500 の画面も、ログの `LazyInitializationException` も出なかった。403・その他の 4xx・5xx の画面は、4.6 で開く。）

### 4.6 セキュリティの確認（要件定義書 6章、設計書 7.11）

#### (1) パスワードのハッシュ化

- [x] `hb_app` でログインし、次の SQL を実行する（**読むだけ**。ハッシュの値そのものは、書き写さない）：

```sql
SELECT username, LEFT(password_hash, 4) AS prefix, LENGTH(password_hash) AS len FROM users ORDER BY id;
```

  期待：どの行も、`prefix` が `$2a$`（または `$2b$`、`$2y$`）、`len` が **60**。元のパスワードが読める形で入っていない。
- [ ] 同じパスワードで登録した2人のハッシュが違う（`sameRawPasswordGivesDifferentHashes`、自動テストで確認済み。ブラウザでは、4.9 で作る新しいアカウントのハッシュが、ほかと違うことを見る）

#### (2) ログイン必須

- [x] ログアウトした状態で、次の URL を順に開く。**ログイン画面（`/login`）へ移動する**：`/`、`/transactions`、`/transactions/new`、`/transactions/1`、`/categories`、`/vendors`、`/payment-methods`、`/budgets`、`/comparison`、`/comparison?year=2026&season=autumn`
- [x] ログアウトした状態で、`/login`、`/register`、`/css/style.css` は開ける
- [x] ログイン成功後は、開こうとしていた URL に関係なく、取引一覧へ移動する（U5）
- [x] ログイン失敗は、「ユーザー名またはパスワードが違います」だけ（ユーザー名がない場合も、パスワードが違う場合も同じ文）

#### (3) SQL インジェクション（7.4）

次の文字列を入力して、**影響がない**ことを確かめる（`test1` で）。保存できたものは、ただの文字として一覧に出る。確認のあと、画面から削除する（4.17）。

| 入れる場所 | 入力 | 期待 |
|---|---|---|
| ログイン画面のユーザー名 | `' OR '1'='1` | 「ユーザー名またはパスワードが違います」。ログインできない |
| 取引の摘要（30文字以下） | `' OR '1'='1` と `'; DROP TABLE users;--` | そのまま保存される。詳細・編集・一覧で、ただの文字 |
| 科目名（10文字以下） | `x' OR 1=1` | そのまま保存される |
| 取引先名（20文字以下） | `' OR '1'='1` | そのまま保存される |
| 支払方法名（10文字以下） | `x' OR 1=1` | そのまま保存される |
| アカウント登録のユーザー名 | `' OR '1'='1` | 「ユーザー名は半角英数字で入力してください」（保存されない） |

- [x] 上の表のとおり。ほかのユーザーのデータが出てこない。`users` が消えていない
- [x] SQL で、表が6つ（`users`、`categories`、`vendors`、`payment_methods`、`budgets`、`transactions`）そろっている（`SHOW TABLES;`）
- [x] コードの検索（4.4）で、文字列連結の SQL が 0 件

#### (4) XSS（7.5）

次を入力して、**画面ではただの文字として出る**（`alert` が出ない）ことを、ブラウザで確かめる。「ページのソースを表示」で、`&lt;` になっていることも見る。

| 入れる場所 | 入力 | 確かめる画面 |
|---|---|---|
| 取引の摘要 | `<script>alert(1)</script>`（25文字） | 取引一覧、詳細、編集（入力値として入る）、入力エラーで表示し直した画面 |
| 科目名 | `<b>x</b>`（8文字） | 科目一覧、変更画面、取引の登録・編集のプルダウン、予算設定、**予実比較** |
| 取引先名 | `<b>x</b>` | 取引先一覧、変更画面、取引のプルダウン、取引一覧・詳細 |
| 支払方法名 | `<b>x</b>` | 支払方法一覧、変更画面、取引のプルダウン、取引一覧・詳細 |
| アカウントのユーザー名 | `<b>x</b>` | 登録できない（半角英数字のみ）。ログイン中のユーザー名の表示は、英数字だけ |

- [x] 上の表のとおり（太字にならない、`alert` が出ない）
- [x] コードの検索（4.4）で、`th:utext` の属性が 0 件

（実施の記録・4.6 の (1)〜(4)：2026年11月2日。あなたが行い、(1) ハッシュ化、(2) ログイン必須、(3) SQL インジェクション、(4) XSS は**全部 OK**。(1) の「ブラウザで、新しいアカウントのハッシュがほかと違うこと」は 4.9 で見るので、そのチェックは付けていない。確認のために作った科目・取引先・支払方法・取引は、4.17 で片付ける。）

#### (5) CSRF（7.6）

- [x] ブラウザの開発者ツールで、フォームに隠し項目 `_csrf` が入っている：取引の登録、科目の追加、予算設定、削除ボタンのフォーム、ログアウトのフォーム、ログインのフォーム、登録のフォーム
- [x] 開発者ツールで、その隠し項目 `_csrf` を**消して**送信する（取引の登録、科目の追加、削除、ログアウトのうち少なくとも2つ）。**403 の画面**（「操作を完了できませんでした。もう一度やり直してください」）になり、**状態コードが 403**（開発者ツールの「ネットワーク」で確認）。何も保存・削除されない
- [x] （自動テストで確認済み：`SecurityBehaviorTest`）

#### (6) データ分離（2人のユーザー。7.8、3.9）

`test1` と `test2` の**両方に**、目印になるデータを作る（取引は金額を変えて、例：`test1` は 1,500円、`test2` は 2,500円。科目・取引先・支払方法は、名前を変えて1つずつ追加。例：`T1科目`、`T2科目`）。IDは、一覧の「編集」「詳細」リンクの URL から読む。

| 操作（`test1` でログインして行う） | 期待 |
|---|---|
| `test2` の取引の ID で、`/transactions/{id}`、`/transactions/{id}/edit` を開く | **404**。存在しない ID（例：`999999`）を開いたときと、同じ画面・同じ状態コード |
| `test2` の科目・取引先・支払方法の ID で、`/categories/{id}/edit`、`/vendors/{id}/edit`、`/payment-methods/{id}/edit` を開く | 404（同上） |
| 自分の編集画面の送信先を、開発者ツールで `test2` の ID に書き換えて送信する（取引、科目、取引先、支払方法） | 404。`test2` のデータは変わらない（あとで `test2` で確認） |
| 自分の削除ボタンのフォームの送信先を、`test2` の ID に書き換えて送信する | 404。`test2` のデータは消えない |
| 取引の登録フォームで、科目・取引先・支払方法の選択肢の値（`value`）を、`test2` の ID に書き換えて送信する | 404。何も登録されない |
| 予算設定のフォームの、隠し項目の科目 ID を `test2` の ID に書き換えて送信する | 404。何も保存されない |
| 各一覧（取引一覧、科目、取引先、支払方法）、予算設定、**予実比較** | 自分のデータだけ。`test2` の名前・金額が出ない（予実比較は、`test2` の取引 2,500円が `test1` の実績に混ざらない） |

- [x] `test1` で上の表のとおり
- [x] `test2` でログインし直し、`test2` のデータが変わっていない・消えていないことを見る
- [x] 逆向き（`test2` で `test1` の ID を使う）を、取引と科目について1回ずつ行う

（実施の記録・4.6 の (5)〜(6)：2026年11月2日。あなたが行い、(5) CSRF、(6) データ分離は**全部 OK**。(6) で作った `test2` 側のデータと、`test1` の `T1科目` などは、4.17 で片付ける。）

#### (7) エラー画面と状態コード（7.7）

ブラウザの開発者ツールの「ネットワーク」で、**画面と状態コードがそろっている**ことを見る。

| 開く・行う | 期待する状態コード | 画面 |
|---|---|---|
| `/nope`（存在しない URL） | 404 | 「ページが見つかりません」 |
| `/transactions/abc`（ID が数字でない） | 404 | 同上 |
| 他人の ID（(6)） | 404 | 同上 |
| `/comparison?year=1999&season=winter`、`/budgets?year=2101&season=spring` | 404 | 同上 |
| `_csrf` を消した POST（(5)） | 403 | 「操作を完了できませんでした。もう一度やり直してください」 |
| 許されない方法：自分の科目の ID で、`GET /categories/{id}/delete`（削除は POST だけ） | 405 | その他の 4xx の画面（同じ文言） |
| 送信の形がおかしい：予算設定のフォームの項目名 `rows[0].amount` を、開発者ツールで `rows[-1].amount` に書き換えて送信 | 400 | その他の 4xx の画面。何も保存されない（R14、issue 002） |
| DB を止めて画面を開く（Q8-5。MySQL のサービス `MySQL97` を止める。**あなたが行う**） | 500 | 「エラーが発生しました。しばらくしてからもう一度お試しください」 |

- [x] 上の表のとおり。**どの画面にも、スタックトレース・例外の名前・SQL・ファイルのパス・DB の情報が出ない**
- [x] 英語の標準のエラーページ（Whitelabel）が出ない
- [x] DB を止めた確認をしたら、**MySQL のサービスを起動し直し**、アプリを再起動して、元に戻ったことを確かめる

#### (8) セッションと Cookie（7.10）

ブラウザの開発者ツール（「アプリケーション」→「Cookie」）で確かめる。

- [x] ログイン**前**と**後**で、`JSESSIONID` の値が**変わる**（C1。MockMvc では確かめられなかった）
- [x] `JSESSIONID` に `HttpOnly` が付いている。`SameSite` が `Lax`
- [x] 画面の URL に `;jsessionid=…` が付かない（S8）
- [x] ログアウトのあと、戻るボタンやブックマークで取引一覧を開いても、ログイン画面へ移動する
  - **8-4 の結果**：PC のブラウザで、「戻る」を押すと**取引一覧が表示されたまま**になった（一瞬ではない）。「詳細」などを押すと、ログイン画面へ移動する。**F2 に記録した**〔原因の調べ方と、扱いの案は F2 と Q8-25〕。PC のブラウザでは、案 F〔4.16〕を入れて確かめ、**OK**〔4.16 の実施の記録〕。iPad の Safari は Phase 9 で確かめる。

#### F2：ログアウトのあと、「戻る」で取引一覧が表示されたままになる（4.6 の 8-4。2026年11月2日。記録だけ。コードは変えていない）

**見つかったこと**〔あなたが PC のブラウザで確かめた。ブラウザの名前は、確かめて書く：Chrome ／ Edge →（　　）〕
- ログアウトのあと「戻る」を押すと、**取引一覧が表示されたまま**になる〔4.13 の iPad の Safari では「一瞬古い画面が出て、ログイン画面へ移動」だった。PC のほうが長く見える〕。
- 表示されている画面の「詳細」などを押すと、**ログイン画面へ移動する**。サーバーは正しく断っている。
- 画面に出るのは、**そのブラウザが覚えていた、ログアウト前の画面**。ほかの人に見える内容ではない。ただし、ログアウトしたあとに共用のパソコンで「戻る」を押されると、前の人の画面が見えてしまう。

**調べたこと**〔Claude Code が調べた。コードは変えていない〕
1. **ログアウトの設定**〔`SecurityConfig` の `.logout(...)`〕：`logoutUrl("/logout")`、`logoutSuccessUrl("/login?logout")`、`permitAll()` だけ。**`deleteCookies` はない**。`invalidateHttpSession` も書いていないが、標準でセッションは捨てる。つまり、**サーバー側のセッションは終わるが、ブラウザの `JSESSIONID` の Cookie は、そのまま残る**〔古い値の Cookie は、サーバーでは使えない〕。`Clear-Site-Data` の指定もない。
2. **`Cache-Control: no-store`** は、すべての画面に付いている〔4.13 の調べた結果〕。**これがあっても、ブラウザの「戻る・進む」の高速化〔バック・フォワード キャッシュ。bfcache〕は、使われることがある**。Chrome は、`no-store` の画面でも、**Cookie が変わらなければ** bfcache に入れる仕組みを段階的に入れている〔3 分で期限切れ。変わったときは覚えた画面を捨てる〕。Chrome 以外のブラウザは、今までどおり `no-store` の画面を入れないこともある。出典：[Chrome for Developers：Cache-Control: no-store の bfcache の有効化](https://developer.chrome.com/docs/web-platform/bfcache-ccns?hl=ja)。
3. 以上から、**原因は bfcache の可能性が高い**が、**まだ確かめていない**〔下の「原因を確かめる手順」で、あなたが確かめる〕。

**案の比較**〔(b)〜(d) の「直る見込み」は、調べた範囲の見込み。**実際に試すまで分からない**〕

| 案 | 内容 | PC のブラウザ | iPad の Safari | 長所 | 短所 | 作業量 |
|---|---|---|---|---|---|---|
| (a) | 記録だけ。README の既知の制限に書く | 直らない | 直らない | コードを変えない。この Phase の「新しい機能は作らない」に合う | 共用のパソコンで、ログアウト後に前の画面が見える | 文書だけ（Phase 9 の README） |
| (b) | ログアウトで `JSESSIONID` の Cookie を消す〔`.deleteCookies("JSESSIONID")`〕 | **直る見込みがある**。Chrome は、Cookie が変わると bfcache の画面を捨てる。ただし、ログアウトの直後に開くログイン画面が、すでに新しい `JSESSIONID` を渡している可能性があり、そのときは効かない可能性がある〔試さないと分からない〕 | 見込みは小さい〔Safari の bfcache が、Cookie の変化を見るかは、調べた範囲では確認できなかった〕 | 1 行。標準の機能。副作用がほぼない〔Cookie は、ログアウトで不要になる〕 | 効かないことがある。Cookie を消すだけで、画面の内容は消せない | 設定 1 行。テスト 1 件〔`POST /logout` の応答に、`JSESSIONID` を消す `Set-Cookie`〔`Max-Age=0`〕が付くこと〕。ブラウザでの確認は、あなたが行う |
| (c) | ログアウトの応答に `Clear-Site-Data` を付ける〔`ClearSiteDataHeaderWriter`。`cache`・`cookies`・`storage`〕 | 効く可能性がある。ただし、**このヘッダーは、HTTPS か `localhost` のページでだけ働く**と理解している〔この調べでは確認できなかった。確かめが要る〕。今は HTTP なので、`localhost` なら働くかもしれないが、IP アドレスでは働かない可能性が高い | 上と同じ理由で、IP アドレスでは働かない可能性が高い。Safari の対応も、2023 年（Safari 17）から、という資料があるが、Apple 自身の文書では確認できていない | 画面の覚えを消す、という目的に最も近い | HTTP では働かない可能性が高い〔Phase 10 の公開で HTTPS にするなら働く〕。`cache` の消去は、ほかの画面も毎回読み直させる。ブラウザで実際に動くかの確認が要る | 設定 3〜5 行。テスト 1 件〔応答にヘッダーが付くこと〕。ブラウザでの確認は、あなたが行う |
| (d) | (b) と (c) の両方 | (b) か (c) のどちらかが効けば直る | 上と同じ | 直る見込みが最も大きい | 設定が増える。どちらが効いたのか分かりにくい | (b) と (c) の合計 |

**原因を確かめる手順**〔あなたが PC のブラウザの開発者ツールで行う。Chrome／Edge のどちらでも同じ〕
1. `test1` でログインし、取引一覧を開く。開発者ツール〔F12〕を開く。
2. 「アプリケーション」〔Application〕→ 左の一覧の「バックフォワード キャッシュ」〔Back/forward cache〕を開く。「テストを実行」〔Test back/forward cache〕を押す。**ログイン中の取引一覧が、bfcache に入れるか**が出る。入れないときは、理由が出る〔例：`Cache-Control: no-store` を持つページは入れない〕。結果を書き留める。
3. 「ログアウト」を押し、ログイン画面が出たら、**ブラウザの「戻る」**を押す。取引一覧が出たら、もう一度「アプリケーション」→「バックフォワード キャッシュ」を開き、「**このページは、バックフォワード キャッシュから復元されました**」〔Restored from back/forward cache〕と出るかを見る。
4. 出る → 原因は bfcache。出ない → 別の原因〔ふつうのキャッシュ。「ネットワーク」で、その取引一覧が「（ディスク キャッシュ）」〔disk cache〕などになっているか、状態コード 200 で再取得されているかを見る〕。
5. 「ネットワーク」で `Disable cache` にチェックを入れ、同じ操作をしても、同じになるかを見る〔同じなら bfcache。ふつうのキャッシュではない〕。
6. 「アプリケーション」→「Cookie」で、ログアウトの前と後の `JSESSIONID` の値を見比べる〔ログアウトの直後のログイン画面で、値が変わるか。(b) が効くかの手がかりになる〕。

- [ ] 上の手順 1〜6 の結果を、ここに書く〔**行わなかった**。案 F は原因にかかわらず試せるので、この確認は必須にしない、と決めた。原因の確認は、必要になったときに行う〕
- [x] Q8-25 が決まった：**新しい案 F**（下）

**決定：Q8-25 は案 F**（細部の Q8-26 は **B**）〔2026年11月2日〕
- 内容：JavaScript で、「戻る」で覚えていた画面を見せたとき（`pageshow` の `event.persisted` が `true`）に、同じ URL を GET で開き直す（当初は `location.reload()`。**Q8-26 で `location.replace(location.href)` に変えた**）。
- 理由：Chrome・Edge・Safari のどれでも、HTTP でも効く。(b)(c) はブラウザによって効くか分からない。削除の確認で、すでに JavaScript（`confirm`）を使っている。
- 進め方：①先に文書〔設計書 1.1・1.2・2.1・2.2・4.3・7.3・7.10 を直した。要件定義書は、7章の 1 行を直す案を出して確認待ち〕。②JavaScript は `static/js/back-reload.js` の別ファイルにし、`fragments/common.html` の `head` から読む。HTML の中に直接は書かない。③全画面で読むので `/js/**` を `SecurityConfig` でログインなしに開けるようにする。④テストを足す。手順は **4.16 の「F2 の修正」**。
- 調べたこと：ログインなしで開く画面〔ログイン、登録、エラー〕も `common.html` の `head` を使っているので、`/js/**` の許可は**必要**〔許可しないと、未ログインでは JavaScript の代わりにログイン画面が返る〕。既存の削除の確認〔`onsubmit="return confirm(…)"`〕は、テストが HTML の文字列を確かめているので、**そのまま**にする。

#### (9) 秘密情報（7.9）

- [x] 4.4 の検索の結果（`git grep`、履歴、`.gitignore`）が期待どおり
- [x] `git status` に、`target/`、`.env`、ダンプ、`application-local.properties` が出ない
- [x] ログ（アプリを起動したターミナル）に、パスワードやハッシュが出ていない（SQL の値を出す設定がオフ。S5）

（実施の記録・4.6 の (7)〜(9)：2026年11月2日。あなたが行った。
- (7) 7-1〜7-8 は**全部 OK**〔DB を止めた確認のあと、MySQL のサービスを起動し直し、アプリを再起動して、元に戻った〕。
- (8) 8-1〜8-3 は**全部 OK**。8-4 は、F2 を案 F〔4.16〕で直し、PC のブラウザで OK。iPad の Safari は Phase 9 で確かめる。
- (9) ログに、パスワードやハッシュは出ていなかった。(9) の上の 2 項目〔4.4 の検索の結果、`git status`〕は、Claude Code の報告をあなたも確認して、チェックを付けた。）

### 4.7 入力チェックの確認（要件定義書 5章。境界値、ブラウザ）

入力の内容と、期待するメッセージ。**エラーのときは、入力した値が残る**ことも、毎回見る。メッセージは `messages.properties` のとおり。「OK」は、エラーなしで保存される。自動テストで確かめてある内容を、画面でもう一度確かめる。

**進め方**：下の表（今までの表）は、各項目を**どの自動テストで確かめているか**を右の列に書いた〔2026年11月2日に Claude Code が照合した〕。そのあとの「**ブラウザで確かめる最小限の手順 A〜D**」を、あなたが順に行う。表の項目のうち、自動テストで確かめてあるものは、A〜D で代表の値だけを画面で見る。

テストの置き場所：`RegisterFormTest`、`TransactionFormTest`、`BudgetFormTest`、`CategoryFormTest`・`VendorFormTest`・`PaymentMethodFormTest`、`StrictIntegerEditorTest`（金額・予算額の文字の読み取り）は `form/`。`…ControllerTest`（画面の HTML とメッセージ、入力値が残ること）は `controller/`。`…ServiceTest` は `service/`。`SecurityBehaviorTest` は `security/`。`MessagesTest`（`messages.properties` の文）は直下。

#### アカウント登録（`/register`。新しいアカウントを作る前に、エラーのものを先に行う）

| 入力 | 期待 | 確かめている自動テスト |
|---|---|---|
| ユーザー名が空欄 | 「ユーザー名を入力してください」 | `RegisterFormTest.usernameErrors`（空欄・空白だけ・全角空白だけが「必須」のエラー）。**画面の文は、まだ確かめていない（4.14 で足す）** |
| ユーザー名 `abc-1`、`あいう`、`a b` | 「ユーザー名は半角英数字で入力してください」 | `RegisterFormTest.usernameErrors`。画面：`SecurityBehaviorTest.registerErrorRedisplaysWithUsernameAndWithoutPassword`（`あいう`） |
| ユーザー名 11文字（`abcdefghijk`） | 「ユーザー名は10文字以内で入力してください」 | `RegisterFormTest.usernameErrors`、`usernameLengthBoundaries`。**画面の文は 4.14 で足す** |
| ユーザー名 10文字（`abcdefghij`） | OK（エラーなし。この名前で登録してもよい。4.9 の名前に使ってもよい） | `RegisterFormTest.usernameLengthBoundaries`。登録の成功：`SecurityBehaviorTest.registerSuccessRedirectsToLoginWithMessage`、`RegistrationServiceTest.createsInitialData` |
| すでにあるユーザー名（`test1`）、大文字（`TEST1`）、前後に全角の空白をつけた `test1` | 「このユーザー名はすでに使われています」（大文字・小文字は区別しない。前後の空白は取り除かれる） | `RegistrationServiceTest.duplicateUsernameIsRejected`（`regdup1`／`REGDUP2`／`RegDup3`）、`RegisterFormTest.usernameIsStrippedIncludingFullWidthSpaces`。画面：`SecurityBehaviorTest.registerDuplicateShowsMessageNextToUsername`（ユーザー名が残り、パスワードは残らない） |
| パスワードが空欄 | 「パスワードを入力してください」（ほかのエラーが重なって出ることがある。G7） | `RegisterFormTest.passwordErrors`、`requiredErrorDoesNotOverlapWithOthers`。**画面の文は 4.14 で足す** |
| パスワード 7文字 | 「パスワードは8文字以上30文字以内で入力してください」 | `RegisterFormTest.passwordLengthBoundaries`、`passwordErrors`。画面：`SecurityBehaviorTest.registerErrorRedisplaysWithUsernameAndWithoutPassword` |
| パスワード 8文字、30文字 | OK | `RegisterFormTest.passwordLengthBoundaries` |
| パスワード 31文字 | 「パスワードは8文字以上30文字以内で入力してください」 | 同上 |
| パスワードに空白、日本語、全角 | 「パスワードは半角の英数字と記号で入力してください」 | `RegisterFormTest.passwordErrors`（`Pattern`）、`passwordIsNotStripped`。**画面の文は 4.14 で足す** |

#### 取引（`/transactions/new`、`/transactions/{id}/edit`）

登録の画面のテストは `TransactionControllerTest.registerWithInvalidInputRedisplaysTheFormAndSavesNothing`（メッセージの文、エラーが1項目に1つ、何も保存されない）と `registerWithInvalidInputKeepsTheEnteredValue`（入力値が残る）。編集は `updateWithInvalidInputRedisplaysTheEditFormKeepsValuesAndChangesNothing`。下の表では、この 2 つを「上の画面のテスト」と呼ぶ。

| 項目 | 入力 | 期待 | 確かめている自動テスト |
|---|---|---|---|
| 日付 | 空欄 | 「日付を入力してください」 | `TransactionFormTest.dateNullIsOnlyRequired`、上の画面のテスト |
| 日付 | 2000-02-29 | 「日付は2000年3月1日から2100年12月31日までで入力してください」（R15） | `TransactionFormTest.dateRange`、上の画面のテスト |
| 日付 | 2000-03-01、2100-12-31 | OK | `TransactionFormTest.dateRange`、`TransactionControllerTest.registerAcceptsBoundaryValues` |
| 日付 | 2101-01-01 | 上と同じエラー | 同上 |
| 日付 | `2026-02-30` など存在しない日付 | 「日付を正しく入力してください」（欄が `type="date"` で入力できないときは、開発者ツールで値を書き換えて送る） | `TransactionFormTest.dateBinding`、上の画面のテスト。**ブラウザの日付欄の動きは、ブラウザだけ**（B4 で見る） |
| 金額 | 空欄 | 「金額を入力してください」 | `TransactionFormTest.amountNullIsOnlyRequired`、`amountNotSentIsRequired`、上の画面のテスト |
| 金額 | 0、-1 | 「金額は1円以上1,000,000円以下で入力してください」 | `TransactionFormTest.amountRange`、上の画面のテスト |
| 金額 | 1、1000000 | OK | `TransactionFormTest.amountRange`、`TransactionControllerTest.registerAcceptsBoundaryValues` |
| 金額 | 1000001 | 上と同じエラー | 同上 |
| 金額 | `1.5`、`1,000`、`abc`、`0x10`、`+5`、`1 0`（途中の空白） | 「金額は整数で入力してください」 | `StrictIntegerEditorTest`（文字の読み取りの全パターン）、`TransactionFormTest.amountBinding`、上の画面のテスト |
| 金額 | `１２３`（全角の数字） | OK（半角の 123 として受け付ける。R13） | `StrictIntegerEditorTest`、`TransactionControllerTest.registerAcceptsAFullWidthAmount`、`updateAcceptsAFullWidthAmount`。**IME で打つ動きは、ブラウザだけ**（B6 で見る） |
| 摘要 | 30文字 | OK | `TransactionFormTest.descriptionOf30CharactersIsAllowedAnd31IsNot`、`TransactionControllerTest.registerAcceptsBoundaryValues` |
| 摘要 | 31文字 | 「摘要は30文字以内で入力してください」 | 同上、`registerWithTooLongDescriptionShowsErrorAndKeepsIt` |
| 摘要 | 空欄、前後に空白だけ | OK（未入力として保存。詳細で空欄） | `TransactionFormTest.blankDescriptionBecomesNull`、`TransactionControllerTest.registerStripsAmountAndDescriptionSpacesAndStoresNullForBlankDescription`、`detailWithoutDescriptionShowsBlankNotNull` |
| 科目・取引先・支払方法 | 未選択（開発者ツールで `value` を空にして送る） | 「科目を選択してください」「取引先を選択してください」「支払方法を選択してください」 | `TransactionFormTest.idsNullAreOnlyRequired`、`idBinding`、上の画面のテスト（空・数字でない値）。**ブラウザでは省く** |

- 取引の入力エラーでは、入力した日付・金額・摘要と、選んだ科目などが残る。→ `TransactionControllerTest.registerWithInvalidInputKeepsTheEnteredValue`、`registerWithErrorKeepsOtherValuesAndSelectedOptions`、`registerWithSeveralErrorsShowsEachOnItsField`。

#### 科目・取引先・支払方法（`/categories`、`/vendors`、`/payment-methods`。追加と変更）

画面のテストは `CategoryControllerTest`・`VendorControllerTest`・`PaymentMethodControllerTest` の `addWith…`、`edit…`、`delete…`。

| 項目 | 入力 | 期待 | 確かめている自動テスト |
|---|---|---|---|
| 名前 | 空欄、空白だけ | 「科目名を入力してください」「取引先名を…」「支払方法名を…」 | `CategoryFormTest.errors`、`VendorFormTest.blankNameIsRequiredError`、`PaymentMethodFormTest`。画面：`…ControllerTest.addWithBlankNameShowsErrorAndRedisplaysList`、`editWithBlankOrTooLongNameKeepsTheInputAndShowsError` |
| 科目・支払方法の名前 | 10文字 / 11文字 | OK / 「科目名は10文字以内で入力してください」「支払方法名は10文字以内で…」 | `…FormTest`（長さの境界）、`…ControllerTest.addWithTheMaximumLengthSucceeds`、`addWithTooLongNameKeepsTheInputAndShowsError`（入力値が残る） |
| 取引先の名前 | 20文字 / 21文字 | OK / 「取引先名は20文字以内で入力してください」 | `VendorFormTest.lengthBoundary`、`VendorControllerTest` の同じテスト |
| 名前の重複 | すでにある名前、大文字小文字・ひらがなカタカナ・全角半角だけが違う名前 | 「その科目名はすでにあります」「その取引先名は…」「その支払方法名は…」 | `CategoryServiceTest.addDuplicateIsRejected`（`Food`／`FOOD`、`ひらがな`／`ヒラガナ`、`ｱｲｳ`／`アイウ`、`コンビニ`／`ｺﾝﾋﾞﾆ`。取引先・支払方法も同じ）。画面：`…ControllerTest.addDuplicateShowsErrorAndKeepsTheInput`、`addDuplicateIgnoresCaseAndOtherUsersNames`、`editToAnother…NameShowsErrorAndKeepsTheInput` |
| 削除 | 取引で使われている科目・取引先・支払方法 | 「取引で使われているため削除できません」 | `…ServiceTest.deleteIsRejectedWhenUsedByATransaction`、`CategoryControllerTest.deleteOfCategoryInUseGoesBackToListWithTheReason`（取引先・支払方法は `deleteOfItemInUseGoesBackToListWithTheReason`） |
| 削除 | 0円でない予算がある科目 | 「予算が0円ではない季節があるため削除できません」 | `CategoryServiceTest.deleteIsRejectedWhenAnyBudgetIsNotZero`、`CategoryControllerTest.deleteOfCategoryWithNonZeroBudgetGoesBackToListWithTheReason`、`whenBothReasonsApplyOnlyInUseIsShown` |
| 削除 | 使われていないもの | 確認ダイアログのあと「削除しました」 | `…ControllerTest.deleteSucceedsAndRedirectsToList`、`listHasMenuAddFormEditLinkAndDeleteFormWithConfirm`（確認の文言がフォームにあること）。ダイアログが実際に出ることは、4.16 で確認済み |

- 画面のメッセージの文そのものは、`MessagesTest`（`messages.properties` の文）でも確かめている。**ただし登録画面〔`registerForm`〕の文と、ログインの失敗の文は、`MessagesTest` に入っていない**（4.14 で足す）。

#### 予算設定（`/budgets`）

画面のテストは `BudgetControllerTest.invalidAmountRedisplaysKeepsAllInputsAndSavesNothing`（メッセージの文、エラーが1行に1つ、全行の入力値が残る、何も保存されない）、`typeMismatchKeepsTheTypedText`、`eachBadRowGetsItsOwnMessageInThePostedOrderWithNamesAndMarks`。下の表では「上の画面のテスト」と呼ぶ。

| 入力（予算額） | 期待 | 確かめている自動テスト |
|---|---|---|
| 0、3000000 | OK（保存しました） | `BudgetFormTest.amountRange`、`BudgetControllerTest.saveBoundaryAmountsAndSpaces` |
| 3000001、-1 | 「予算額は0円以上3,000,000円以下で入力してください」 | `BudgetFormTest.amountRange`、上の画面のテスト |
| 空欄 | 「予算額を入力してください」 | `BudgetFormTest.nullAmountIsOnlyRequired`、上の画面のテスト |
| `1.5`、`1,000`、`abc`、`0x10`、`+5` | 「予算額は整数で入力してください」 | `StrictIntegerEditorTest`、`BudgetFormTest.amountBinding`、上の画面のテスト |
| `１２３`（全角） | OK（半角の 123 として保存） | `BudgetControllerTest.fullWidthAmountsAreSavedAsHalfWidth`、`BudgetFormTest.fullWidthAmountsAreConvertedOnEveryRow` |
| 1行でもエラー | 何も保存されない。全行の入力値が残り、エラーの行の横にメッセージが出る | 上の画面のテスト、`BudgetFormTest.eachBadRowGetsItsOwnSingleError`、`rejectedValuesAreKept` |

#### ブラウザで確かめる最小限の手順 A〜D

自動テストで確かめられないもの〔エラーの文字の見え方、入力欄の動き、パスワード欄が空になること、IME の全角、画面に出る文そのもの〕を見る。**各行で「入力 → 期待」を見て、OK／NG を知らせる**。エラーのときは、**入力した値が残ること**も毎回見る。ログインする人は、取引・科目・取引先・支払方法・予算は `test1`、登録はログアウトして行う。パスワードは、文書に書かない。

##### A. アカウント登録（`/register`。ログアウト状態で）

| # | 入力 | 期待 |
|---|---|---|
| A1 | 両方とも空欄で登録 | 「ユーザー名を入力してください」「パスワードを入力してください」が、それぞれの欄の下に出る |
| A2 | ユーザー名 `abc-1`、パスワード `abc12` | 「ユーザー名は半角英数字で入力してください」と「パスワードは8文字以上30文字以内で入力してください」。**ユーザー名は残り、パスワード欄は空** |
| A3 | ユーザー名 `abcdefghijk`（11文字）、パスワード 31文字 | 「ユーザー名は10文字以内で入力してください」と「パスワードは8文字以上30文字以内で入力してください」 |
| A4 | ユーザー名 `abcdefghij`、パスワード `abc 1234`（空白あり） | パスワードに「パスワードは半角の英数字と記号で入力してください」 |
| A5 | ユーザー名 `TEST1`、パスワード `abcd1234` | 「このユーザー名はすでに使われています」。ユーザー名が残る |
| A6 | 登録できる例。ユーザー名はちょうど 10 文字、パスワードは 30 文字（例：`chkzero001`） | 「登録しました。ログインしてください」。**このアカウントは 4.9 で使う**。パスワードは文書に書かない |

- 7・8・31 文字のパスワードの境界は、自動テストで確かめてあるので、ブラウザでは省く。

##### B. 取引（`test1` でログイン → 取引の登録）

| # | 入力 | 期待 |
|---|---|---|
| B1 | 日付を消し、金額も空欄で登録 | 「日付を入力してください」「金額を入力してください」。選んだ科目などが残る |
| B2 | 金額 `0`、次に `1000001` | 「金額は1円以上1,000,000円以下で入力してください」。打った数字が残る |
| B3 | 金額 `1,000`、次に `1.5` | 「金額は整数で入力してください」。打った文字が残る |
| B4 | 日付 `2000-02-29`（または 2101-01-01） | 「日付は2000年3月1日から2100年12月31日までで入力してください」。日付が残る |
| B5 | 摘要 31 文字 | 「摘要は30文字以内で入力してください」。摘要が残る |
| B6 | OK の例。日付 `2100-12-31`、金額を IME で `１２３`（全角）、摘要 30 文字 | 登録でき、一覧に 123 円と出る |
| B7 | B6 の取引を編集し、金額を `0` にして保存 | 編集画面に戻り、メッセージが出る。ほかの値は残り、一覧の金額は変わらない |

B6 で作った取引は、あとで削除して片付ける（4.17）。

##### C. 科目・取引先・支払方法（`test1`）

| # | 入力 | 期待 |
|---|---|---|
| C1 | 科目：空白だけで追加 | 「科目名を入力してください」 |
| C2 | 科目：11 文字で追加 | 「科目名は10文字以内で入力してください」。名前が残る |
| C3 | 科目：すでにある名前で追加 | 「その科目名はすでにあります」。名前が残る |
| C4 | 科目：変更画面で、ほかの科目と同じ名前に変更 | 変更画面に戻り、同じメッセージが出る |
| C5 | 取引先：21 文字で追加 | 「取引先名は20文字以内で入力してください」 |
| C6 | 支払方法：11 文字で追加 | 「支払方法名は10文字以内で入力してください」 |
| C7 | 取引で使っている科目を削除（ダイアログで「OK」） | 「取引で使われているため削除できません」 |

##### D. 予算設定（`test1`）

| # | 入力 | 期待 |
|---|---|---|
| D1 | 2026年の冬。1 行目に `abc`、2 行目に `777` で保存 | 1 行目の横に「予算額は整数で入力してください」。**全行の入力値が残る**。ページを開き直すと、何も保存されていない |
| D2 | 1 行目に `3000001` | 「予算額は0円以上3,000,000円以下で入力してください」 |
| D3 | OK の例。2030年の春。1 行目に `０`、2 行目に `3000000`、3 行目に `１２３`（全角） | 「保存しました」。**3 行目が 123 と表示される** |

D3 で作った 2030 年春の予算の行は、4.17 の SQL で消す〔予算の行が増えるので、「9 行」の確認のときに分かるようにする〕。

- [x] 手順 A（A1〜A6）が、期待どおり
- [x] 手順 B（B1〜B7）が、期待どおり
- [x] 手順 C（C1〜C7）が、期待どおり
- [x] 手順 D（D1〜D3）が、期待どおり
- [x] 上の表のうち、A〜D で見ていない項目は、右の列の自動テストで確かめてある〔未選択の送信、存在しない日付、7・8・31 文字のパスワード、ユーザー名の前後の全角空白など〕
- [x] 外れたものは 3.3 で記録した（なし）
- [x] A6、B6 などで作ったデータを、4.17 の片付けの対象として控えた（`chkzero001` など。パスワードは書かない）

（実施の記録・4.7：2026年11月2日。あなたが A〜D を行い、**すべて期待どおり**。外れたものは**なし**。A6 で新しいアカウント `chkzero001` を作った〔4.9 で使う。パスワードは書かない〕。B6 の取引〔2100-12-31、123円〕と、D3 の 2030年春の予算の行〔`test1`〕は、4.17 で片付ける。登録画面のメッセージの文の自動テストは、4.14 で足す。）

### 4.8 これまでの Phase から引き継いだ、ブラウザで未確認の項目

実行計画書の Phase 8「引き継ぐこと」の項目のうち、4.5〜4.7 に含まれないものです。4.7 と同じように、各項目を **①すでに済んでいるもの**、**②自動テストで確かめているもの**、**③まだブラウザや SQL で見ていないもの** に分けて整理した〔2026年11月2日に Claude Code が照合した。コードは変えていない〕。

#### 一覧（元の 10 項目の整理）

| # | 項目（Phase） | 分類 | 済んでいる確認／確かめている自動テスト |
|---|---|---|---|
| 1 | 別タブで科目を削除したあとに、古い画面から取引を送信する → 404、何も保存されない（5） | ②＋③ | 自動：`TransactionControllerTest.registerWithNonexistentItemIsNotFound`（存在しない科目・取引先・支払方法の ID → 404、何も保存されない）。**別タブでの実際の操作は、まだ見ていない → 手順 E1** |
| 2 | 取引で使われている科目・取引先・支払方法が削除できない（5） | ①＋② | 済み：4.7 の C7（取引で使っている科目。ブラウザ）。取引先・支払方法は自動テストだけ。自動：`…ServiceTest.deleteIsRejectedWhenUsedByATransaction`、`…ControllerTest.deleteOfCategoryInUseGoesBackToListWithTheReason`（取引先・支払方法は `deleteOfItemInUseGoesBackToListWithTheReason`） |
| 3 | SQL：摘要を空で登録したとき `transactions.description` が NULL（5） | ②＋③ | 自動：`TransactionControllerTest.registerStripsAmountAndDescriptionSpacesAndStoresNullForBlankDescription`、`TransactionFormTest.blankDescriptionBecomesNull`（実際の DB に保存して確かめている）。**画面から登録した行を SQL で見るのは、まだ → 手順 E2** |
| 4 | プルダウンが空のとき（B2）の案内（5） | ②＋③（4.9 で行う） | 自動：`TransactionControllerTest.newFormWithNoMastersShowsGuidanceAndNoForm`、`newFormWithOnlySomeMastersMissingNamesOnlyTheMissingOnes`、`BudgetControllerTest.noCategoriesShowsGuidanceAndNoSaveButton`。ブラウザ：**科目が0件の分は 4.9 の手順 8。取引先・支払方法が0件の分は、4.9 の手順 10-2・10-3**（下の「4.9 に足すこと」）。**どちらも 4.9 を行うまで未確認** |
| 5-a | 2100年冬の端（`/budgets?year=2100&season=winter`）（6） | ① | 済み：4.5（予算設定・予実比較の5つの指定）、4.6 の(7)（範囲外は 404） |
| 5-b | `test2` から科目 ID を書き換えた送信（6） | ① | 済み：4.6 の(6) |
| 5-c | `budgets` の SQL での確認（保存した季節だけ行がある）（6） | ②＋③ | 自動：`BudgetControllerTest.showingDoesNotCreateRows`、`categoriesNotSentAndOtherSeasonsAndUsersAreUntouched`、`saveSucceedsRedirectsToTheSameSeasonAndCreatesAllRows`。**実際の DB を SQL で見るのは、まだ → 手順 E3** |
| 6-a | 257行以上を送ったとき → 400、何も保存されない（R14）（6） | ② | 自動：`BudgetControllerTest.sending257RowsIs400AndSavesNothing`、`saving256RowsWorks`、`row255IsStillBoundSoTheCategoryIdIsChecked`、`the400PageHasALinkBack`。**ブラウザでは省く**（開発者ツールで 257 行を作る手間が大きい。同じ 400 の画面と仕組みを、`rows[-1]` で 4.6 の(7)で確認済み） |
| 6-b | `rows[-1]`、`rows[abc]` も 400（6） | ①＋② | 済み：`rows[-1]` は 4.6 の(7)。自動：`BudgetControllerTest.tamperedRowIndexIs400AndSavesNothing`（`rows[abc]` など）。`rows[abc]` のブラウザは、`rows[-1]` と同じ仕組みなので省く |
| 6-c | `rows=`（空）を送ったとき、行が空のまま表示される（直さないと決めた）（6） | ② | 自動：`BudgetControllerTest.postWithNoRowsSavesNothingAndRedirects`。ブラウザでは省く（通常の画面からは送れない） |
| 7 | 全角の数字が半角に直る。`0x10`・`+5`・途中の空白・全角のマイナスが型変換のエラー（6） | ① | 済み：4.7（取引の B6・B3、予算設定の D3）。自動：`StrictIntegerEditorTest`、`BudgetControllerTest`、`TransactionControllerTest` |
| 8 | 保存ボタンを素早く2回押したとき、二重には保存されない（Q6-6 の A）。同時保存で一意制約に拒否されたときの動きは、再現が難しい（Q8-11）（6） | ②＋③ | 自動：`BudgetControllerTest.marksDisappearAfterSavingAndSavingAgainDoesNotAddRows`（続けて2回保存しても行は増えない）。**素早い二度押しは、まだ → 手順 E4**。同時保存の拒否の再現は難しいので、行わない（Q8-11） |
| 9 | 範囲の端と 404（予算設定と予実比較の両方）（7） | ① | 済み：4.5、4.6 の(7) |
| 10 | 取引の日付の下限（R15）：`2000-02-29` がエラー、`2000-03-01` と `2100-12-31` が登録できる。DB の CHECK 制約（`ck_transactions_date`）が `schema.sql` と同じ範囲（7） | ①＋②＋③ | 済み：4.7 の B4 ほか。自動：`TransactionRepositoryTest`（DB の CHECK が 2000-02-29 を拒否する。実際の開発用 DB で確かめている）、`TransactionFormTest.dateRange`、`TransactionControllerTest.registerAcceptsBoundaryValues`。**`SHOW CREATE TABLE` で見るのは、まだ → 手順 E3 の SQL（読むだけ）** |

#### 4.9 に足すこと（項目 4 の残り）

**4.9 に足した**〔手順 10-2・10-3。あなたの了承済み〕。内容：新しいアカウントで、**取引先を3つとも削除**して取引の登録画面を開く（「取引先」を追加する案内とリンクが出る）→ 取引先を1つ追加し、**支払方法を2つとも削除**して開く（「支払方法」の案内）。科目・取引先・支払方法の「どれが足りないか」の案内が出分けられることを見る。

#### まだ見ていないもの：手順 E1〜E4（あなたが順に行う）

ログインするのは `test1`。**E1 → E2 → E4 → E3 の順**がよい〔E3 の SQL で、E2・E4 の結果もまとめて見るため〕。パスワードや接続先は、文書に書かない。SQL は、あなたが MySQL にログインして実行する。

##### E1. 別タブで科目を削除したあとに、古い画面から取引を送信する（項目 1）

1. 科目の画面（`/categories`）で、科目 `別タブ` を追加する（使われていない科目。削除できる）。
2. **タブ 1**：`/transactions/new` を開く。日付・金額（例：100円）・摘要を入れ、科目で `別タブ` を選ぶ。取引先・支払方法も選ぶ。**まだ送信しない**。
3. **タブ 2**：`/categories` を開き、`別タブ` を削除する（確認で OK）。「削除しました」。
4. **タブ 1**：そのまま「登録」を押す。
5. 期待：**「ページが見つかりません」の画面（404）**。何も保存されない（取引一覧に 100円の取引が増えていない）。

##### E2. 摘要が空の取引が、DB で NULL になる（項目 3）

1. `/transactions/new` で、日付・金額（例：200円）を入れ、**摘要は空欄**にして登録する。一覧に出る。詳細を開くと、摘要が空欄（「null」と出ない）。
2. SQL（読むだけ）。

   ```sql
   SELECT id, description IS NULL AS is_null, description FROM transactions;
   SELECT COUNT(*) FROM transactions WHERE description IS NOT NULL AND TRIM(description) = '';
   ```

   期待：1 つ目で、今登録した取引の `is_null` が **1**（`description` は NULL）。摘要を入れた取引は `is_null` が 0。2 つ目は **0**（空文字や空白だけの行がない）。

##### E4. 保存ボタンを素早く2回押す（項目 8）

1. 予算設定を、まだ保存したことのない季節で開く：`/budgets?year=2031&season=spring`。
2. 各科目の予算額を入れる（例：すべて `100`）。
3. 「保存」を**素早く2回**押す（ダブルクリック）。〔押しにくければ、入力欄で Enter を2回続けて押す。〕
4. 期待：「保存しました」が出る。**500 のエラー画面にならない**のが望ましい。ただし、同時に拒否される場合の画面は、特別な扱いをしない〔500 番台の画面。何も二重には保存されない。Q6-6 の A〕と決めてある。どちらになったか、**見たとおり**を知らせる。
5. 保存後に `/budgets?year=2031&season=spring` を開き直して、各科目の額が入っている。行が重なって増えていない（E3 の SQL で数える）。

##### E3. SQL で、`budgets` と `transactions` を見る（項目 5-c、10）

読むだけの SQL。

```sql
SELECT u.username, b.season_year, b.season, COUNT(*) AS rows_count
FROM budgets b JOIN users u ON u.id = b.user_id
GROUP BY u.username, b.season_year, b.season
ORDER BY u.username, b.season_year, b.season;

SHOW CREATE TABLE transactions;
```

期待：
- 1 つ目：**保存した季節の行だけ**がある〔`season` は `SPRING`・`AUTUMN` など大文字で保存されている〕。`test1` では、初期の 2026年秋、4.5〜4.6 で保存した季節、4.7 の D3 の 2030年春、E4 の 2031年春、など。**見ただけの季節〔2000年春、2100年冬、前後の季節など〕の行がない**。各季節の `rows_count` は、そのアカウントの科目の数と同じ。E4 の 2031年春が、科目の数と同じ件数〔二重になっていない〕。**保存した覚えのない季節の行があれば NG**。
- 2 つ目：`ck_transactions_date` が `BETWEEN '2000-03-01' AND '2100-12-31'`、`ck_transactions_amount` が `BETWEEN 1 AND 1000000`。`schema.sql` と同じ範囲。

##### 手順 E で作るデータ（4.17 で片付ける）

- 科目 `別タブ`（E1。手順の中で削除済み。残っていれば削除）。
- 取引：E2 の 200円（摘要なし）。E1 でエラーになった 100円は、保存されていないはず。
- 予算：2031年春の `test1` の行（E4）。4.7 の D3 の 2030年春と合わせて、4.17 の SQL で消す。

#### チェック

- [x] 項目 1（E1）：別タブで削除した科目で送信 → 404、何も保存されない
- [x] 項目 2：使われている科目・取引先・支払方法が削除できない（科目は 4.7 の C7〔ブラウザ〕、取引先・支払方法は自動テスト）。〔4.9 の手順 3 は「予算が0円でない」理由の確認で、この項目とは別。4.9 で行う〕
- [x] 項目 3（E2）：摘要が空の取引が NULL で保存される
- [ ] 項目 4：プルダウンが空の案内。科目は 4.9 の手順 8、取引先・支払方法は 4.9 に足した手順で確認する
- [x] 項目 5-a：2100年冬の端（4.5、4.6 の(7)）
- [x] 項目 5-b：`test2` から科目 ID を書き換えた送信（4.6 の(6)）
- [x] 項目 5-c（E3）：`budgets` に、保存した季節だけ行がある
- [x] 項目 6-a〜6-c：257行以上の 400、`rows[abc]`、`rows=`（自動テストで確認。`rows[-1]` は 4.6 の(7)）
- [x] 項目 7：全角の数字、`0x10`・`+5`・途中の空白・全角のマイナス（4.7）
- [x] 項目 8（E4）：保存ボタンの二度押しで、二重には保存されない
- [x] 項目 9：範囲の端と 404（4.5、4.6 の(7)）
- [x] 項目 10（E3）：DB の CHECK 制約が `schema.sql` と同じ範囲〔日付の下限の画面の確認は 4.7 で済み〕
- [x] E で作ったデータを、4.17 の片付けの対象として控えた

（実施の記録・4.8：2026年11月2日。あなたが E1〜E4 を行い、**すべて期待どおり**。
- E1：別タブで科目を削除したあと、古い画面から送信すると **404**。取引は増えなかった。
- E2：金額 200円の取引の摘要は **NULL**。空文字・空白だけの行は **0 件**。
- E4：`/budgets?year=2031&season=spring` で保存を素早く 2 回押したが、**500 にならず保存された**。二重には保存されなかった。
- E3：`budgets` は、**保存した季節にだけ行があった**〔`test1`：2026年秋 6 行、2026年冬 3 行、2030年春 6 行、2031年春 6 行／`test2`：2026年秋 4 行／`chkipad`・`chkzero001`：2026年秋 3 行ずつ〕。2031年春は二重にならず 6 行〔いまの `test1` の科目の数と同じ〕。2026年冬が 3 行なのは、冬を保存したときの科目が 3 つだったため〔その後に科目を足した〕。見ただけの季節の行はなかった。`transactions` の CHECK は、日付が 2000-03-01〜2100-12-31 〔`schema.sql` と同じ〕。金額の CHECK〔1〜1,000,000〕は、報告になかったので確認していない〔`TransactionRepositoryTest` が確かめている〕。
- 項目 4（プルダウンが空の案内）は、4.9 の手順 8・10-2・10-3 で確かめるので、まだチェックしていない。
- E で作ったデータ〔E2 の 200円の取引、2031年春の `test1` の予算 6 行、科目 `別タブ`（削除済み）〕は、4.17 で片付ける。）

### 4.9 科目が0件のアカウントで、予実比較をブラウザで確かめる

**この確認は、必ず行う**（Phase 7 では自動テストだけで確認した。実行計画書の「Phase 7 から引き継ぐこと」）。`test1`・`test2` は壊さず、新しいアカウントで行う（Q8-3）。

**なぜ手順が長いか**：新しく登録したアカウントには、科目が3つあり、今の季節の予算が各 30,000円で入っている。**0円でない予算がある科目は削除できない**（要件 4.3）ので、先に予算を0円にしてから、科目を削除する。この途中の「削除できない」メッセージも、ブラウザで確かめられる。

「今の季節」は、今日が属する季節（2026年9月〜11月は **2026年 秋**。11月中は秋のまま）。日付が変わっているときは、その季節に読み替える。

1. **ログインする**：アカウント **`chkzero001`** は、4.7 の A6 で作成済み〔2026年11月2日。パスワードは文書に書かない〕。ログアウトして `/login` を開き、`chkzero001` でログインする。以前に何も操作していない〔科目・予算・取引を変えていない〕ことが前提。
2. **初期データを確かめる**（4.3 の「初期データ」）：
   - 科目：交通費・食費・雑費の3つ。取引先：コンビニ・ガソリンスタンド・Amazon。支払方法：現金・クレジットカード。
   - 予算設定（今の季節）：3科目とも 30,000円。
   - 予実比較（今の季節）：3科目とも 予算 `30,000円`、実績 `0円`、差額 `30,000円`、青。合計行は `90,000円`／`0円`／`90,000円`、青。
3. **科目を削除しようとする**：科目一覧で「交通費」の削除を押す（確認ダイアログで OK）。期待：**「予算が0円ではない季節があるため削除できません」**。科目は残る。
4. **予算を0円にする**：予算設定（今の季節）で、3科目とも `0` にして保存する。期待：「保存しました」。
5. **科目を3つとも削除する**：科目一覧で、3つの削除を順に押す（確認ダイアログで OK）。期待：その都度「削除しました」。科目一覧は空になる（追加フォームだけが出る）。
6. **予実比較を開く**（メニューの「予実比較」）。期待：
   - 見出しが出る：「2026年 秋（9月〜11月）」。「前の季節」「次の季節」のリンクが出る。
   - 「**先に科目を追加してください。**」と、「**科目の画面へ**」のリンクが出る。
   - **表が出ない。合計行が出ない。色の意味の注記（「赤：予算超過／青：…」）も出ない。**
   - 500 の画面にならない。状態コードは 200。ターミナルのログに `LazyInitializationException` などの例外が出ない。
7. **リンクをたどる**：
   - 「科目の画面へ」を押すと、科目一覧（`/categories`）が開く。戻る。
   - 「前の季節」「次の季節」を押しても、同じ案内が出る（見出しの季節が変わる）。
   - 範囲の端（`/comparison?year=2000&season=spring`、`/comparison?year=2100&season=winter`）でも、案内が出る（「前の季節」「次の季節」の出方も、4.5 のとおり）。
8. **同じ状態の、ほかの画面を開く**（B2 の案内。4.8 の「プルダウンが空のとき」と同じ確認）：
   - 予算設定（`/budgets`）：「先に科目を追加してください」の案内。保存ボタンが出ない。
   - 取引の登録（`/transactions/new`）：科目がないので、案内とリンクが出て、フォームが出ない。
9. **科目を1つ足して、戻る**：科目一覧で「食費」を追加する。期待：「追加しました」。
   - 予算設定（今の季節）：食費が **0円**（科目を追加すると、今の季節の予算が0円で自動で作られる。要件 4.3）。
   - 予実比較（今の季節）：食費の1行と合計行が出る。予算 `0円`、実績 `0円`、差額 `0円`、**色なし**（予算0円・実績0円は色なし。要件 4.4 の②）。合計行も同じ。色の注記が出る。
   - 「前の季節」（2026年 夏）：食費の予算は **`未設定`**（追加した科目に、前の季節の予算の行はない）、差額は空欄、色なし。
10. **（任意）予算0円で実績があるときの赤を、画面で見る**〔**やったら、この取引を必ず削除してから 10-2 へ進む**。取引で使っている取引先・支払方法は削除できない〕：取引の登録で、今日の日付・金額 100円・食費・（初期の取引先と支払方法）を登録する。予実比較（今の季節）：食費は予算 `0円`、実績 `100円`、差額 `-100円`、**赤**（要件 4.4 の①。予算が0円でも、実績があれば赤）。合計行も赤。確認のあと、この取引を削除する。
10-2. **取引先が0件のとき**（4.8 の項目 4）：取引先の画面で、取引先を3つとも削除する（確認ダイアログで OK。取引で使っていなければ削除できる）。取引の登録（`/transactions/new`）を開く。期待：「**先に取引先を追加してください。**」と「取引先の画面へ」のリンクだけが出る（科目と支払方法は足りているので、案内に出ない）。**フォームが出ない**。取引先を1つ追加して、取引の登録画面にフォームが出ることを確かめる。
10-3. **支払方法が0件のとき**（同上）：支払方法の画面で、支払方法を2つとも削除する。取引の登録を開く。期待：「**先に支払方法を追加してください。**」と「支払方法の画面へ」のリンクだけが出る。フォームが出ない。支払方法を1つ追加して、フォームが出ることを確かめる。
11. **片付け**は 4.17（Q8-4）。この新しいアカウントを、4.10 の確認（256個の上限）にも使うなら、残しておく。

- [x] 手順 3：「予算が0円ではない季節があるため削除できません」が出た
- [x] 手順 6：案内とリンクが出て、表・合計行・注記が出ない。500 にならない
- [x] 手順 7：リンクと前後の季節が動く
- [x] 手順 8：予算設定・取引の登録の案内が出る
- [x] 手順 9：科目を足すと、予実比較に1行と合計行が出る（予算0円・実績0円で色なし）
- [ ] 手順 10（任意）：予算0円・実績100円で赤〔**行わなかった**。自動テストで確認済み〕
- [x] 手順 10-2：取引先が0件のとき、取引先だけの案内が出て、フォームが出ない（4.8 の項目 4）
- [x] 手順 10-3：支払方法が0件のとき、支払方法だけの案内が出て、フォームが出ない（4.8 の項目 4）

（実施の記録・4.9：2026年11月3日。あなたが `chkzero001` で行い、手順 1〜9、10-2、10-3 は**すべて期待どおり**。手順 10（任意）は行わなかった〔予算 0円で実績ありなら赤、は自動テストで確認済み〕。
- 10-2 の最初は、科目も 0 件だったため、「先に科目を追加してください」と「先に取引先を追加してください」の 2 つが出た〔支払方法の案内は出なかった〕。食費を足すと、取引先の案内だけになった。**足りないものだけが、足りない数だけ出る**ことも確かめられた。
- `chkzero001` は、4.10 で使うので残してある。4.17 で片付けるかを決める。）

### 4.10 科目の個数の上限（256個）の確認（Q8-10）

要件 5章：科目・取引先・支払方法は、それぞれ1人あたり256個まで。256個あるときに追加しようとすると、「科目は256個までです」などと出る。**256行の予算設定が、保存できる**ことも確かめる（行の並びの上限は、Spring の標準で256行。R14）。

画面から256個を追加するのは手間が大きいので、確認用のアカウント **`chkzero001`**〔4.7 の A6 で作成。4.9 で使った〕に、**SQL で科目を足す**（`hb_app` でログインして、あなたが実行する。Q8-10 の A）。**SQL は、実行前にあなたが確かめる。書き込むのは ③ の 1 つだけ**で、① ② ④ は読むだけ。

〔この SQL は、`schema.sql` の `categories` の定義〔`user_id`、`name VARCHAR(10)`、一意制約 `(user_id, name)`〕と見比べて作った。Claude Code は DB に接続しないので、**実際には実行して確かめていない**。〕

#### 準備

- アプリを起動したままでよい。SQL は、MySQL に `hb_app` でログインして実行する（パスワードの入力は、あなたが行う）。
- 足す科目の名前は `c1`〜`c…`（10文字以下）。`chkzero001` にある科目〔`食費` など〕と重ならない。
- 4.9 の終わりで、`chkzero001` の科目は **1 個**（`食費`）のはず。① でちょうどの数を確かめ、③ は「256 − 今の数」だけ足す作りにしてある〔1 個なら 255 個足す〕。

#### SQL（そのまま貼れる。上から順に）

```sql
-- ① 読むだけ：対象のアカウントの ID と、今の科目の数。足す数 = 256 - categories_now
SELECT u.id AS user_id,
       (SELECT COUNT(*) FROM categories c WHERE c.user_id = u.id) AS categories_now,
       256 - (SELECT COUNT(*) FROM categories c WHERE c.user_id = u.id) AS to_add
FROM users u WHERE u.username = 'chkzero001';
```

期待：1 行だけ出る。`categories_now` が 1（ちがうなら、その数）、`to_add` が 255（256 − `categories_now`）。**1 行でないとき〔0 行など〕は ② 以降に進まず、教えてください。**

```sql
-- ②（読むだけ。DB は書き換えない。この画面の中だけの変数）：ID と、足す数を変数に入れて、表示する
-- ※ ②・③ は、同じ MySQL の接続（同じ画面）で続けて実行する
SET @uid = (SELECT id FROM users WHERE username = 'chkzero001');
SET @to_add = 256 - (SELECT COUNT(*) FROM categories WHERE user_id = @uid);
SELECT @uid AS user_id, @to_add AS to_add;

-- ②b 読むだけ：③ で足す行を、書き込まずに数える（名前は c1 から。10文字以下）
SELECT COUNT(*) AS rows_to_add, MIN(i) AS first_i, MAX(i) AS last_i, MAX(CHAR_LENGTH(CONCAT('c', i))) AS longest_name
FROM (WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 256) SELECT i FROM n) t
WHERE i <= @to_add;
```

期待：`user_id` が ① の `user_id` と同じ。`to_add` が ① の `to_add` と同じ（255）。②b は `rows_to_add` が 255、`first_i` が 1、`last_i` が 255、`longest_name` が 4（`c255`）。

```sql
-- ③ 書き込む：256 個になるまで、科目 c1, c2, … を足す（1 回だけ実行する。②の変数を使う）
INSERT INTO categories (user_id, name)
SELECT @uid, CONCAT('c', i)
FROM (WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 256) SELECT i FROM n) t
WHERE i <= @to_add;
```

期待：`Query OK, 255 rows affected`（① の `to_add` と同じ数）。**エラーが出たら、続けずに、エラーの文を教えてください。** 変数が空〔別の接続で実行した〕だと、`0 rows affected` になるか、エラーになる〔何も壊れない〕。

```sql
-- ④ 読むだけ：256 個になったことを確かめる
SELECT COUNT(*) AS categories_now, MIN(name) AS min_name, MAX(name) AS max_name
FROM categories c JOIN users u ON u.id = c.user_id WHERE u.username = 'chkzero001';
```

期待：`categories_now` が **256**。

> 補足：SQL で足した科目には、今の季節の予算の行が**ない**〔画面から追加すると、予算 0円の行が自動で作られる。要件 4.3〕。そのため予算設定では「未設定」の印が付いた 0円で出て、予実比較では「未設定」と出る。これは正しい動き。

#### ブラウザでの確認（`chkzero001` でログインして行う）

1. **科目一覧を開く**（`/categories`）。期待：科目が多数並ぶ〔`c1`〜`c255` と `食費`。ID の順〕。**500 にならない**。ターミナルのログに例外が出ない。
2. **257 個目を追加しようとする**：追加欄に `x` と入れて追加。期待：「**科目は256個までです**」。追加されない〔④ をもう一度実行すると 256 のまま〕。
3. **予算設定を開く**（`/budgets`、今の季節）。期待：256 行が出る。SQL で足した科目の行は、予算 `0` に「未設定」の印。500 にならない。
4. **保存する**：そのまま「保存」を押す。期待：「**保存しました**」〔256 行は保存できる。400 の画面にならない〕。開き直すと、印が消えている。SQL で確かめる（読むだけ）。

   ```sql
   -- ⑤ 読むだけ：今の季節（2026年秋）の予算の行が 256 行
   SELECT COUNT(*) AS budget_rows FROM budgets b JOIN users u ON u.id = b.user_id
   WHERE u.username = 'chkzero001' AND b.season_year = 2026 AND b.season = 'AUTUMN';
   ```

   期待：`budget_rows` が **256**。
5. **予実比較を開く**（今の季節）。期待：256 行と合計行が出る。500 にならない。ログに例外が出ない。〔予算も実績も 0円なので、色は付かない。合計行は予算 `0円`・実績 `0円`・差額 `0円`〕
6. **削除して、また追加できる**：科目 `c1` の削除を押す〔ダイアログで OK。取引がなく、予算が 0円なので削除できる〕。期待：「削除しました」。続けて、`c1` を追加。期待：「追加しました」。〔255 個になったので、追加できた。追加すると 256 個に戻る〕。もう一度追加しようとすると、また「科目は256個までです」。
7. （取引先・支払方法の上限〔256個〕は、自動テスト〔`MasterLimitControllerTest`、`MasterLimitServiceTest`〕で確認済み。ブラウザでは行わない。）

#### 元に戻す方法

`chkzero001` の科目を、確認の前の状態〔`食費` の 1 個〕に戻す。**実行前に ⑥ で消す対象を確かめる**〔読むだけ〕。

```sql
-- ⑥ 読むだけ：消す対象（名前が c＋数字 の科目。食費は含まれない）。256 - 1 = 255 件のはず
SELECT COUNT(*) AS to_delete, MIN(name) AS min_name, MAX(name) AS max_name
FROM categories c JOIN users u ON u.id = c.user_id
WHERE u.username = 'chkzero001' AND c.name REGEXP '^c[0-9]{1,3}$';
```

期待：`to_delete` が 255（手順 6 で `c1` を消して追加し直したので、256 − 1 = 255）。`食費` が含まれていない〔`min_name`・`max_name` が `c…`〕。**数が違うときは、実行せずに教えてください。**

```sql
-- ⑦ 書き込む：対象の科目の予算の行（外部キーがあるので先に消す）
DELETE FROM budgets
WHERE category_id IN (SELECT id FROM (SELECT c.id FROM categories c JOIN users u ON u.id = c.user_id
                                      WHERE u.username = 'chkzero001' AND c.name REGEXP '^c[0-9]{1,3}$') x);

-- ⑧ 書き込む：対象の科目
DELETE FROM categories
WHERE user_id = (SELECT id FROM (SELECT id FROM users WHERE username = 'chkzero001') x)
  AND name REGEXP '^c[0-9]{1,3}$';

-- ⑨ 読むだけ：元に戻ったことを確かめる
SELECT (SELECT COUNT(*) FROM categories c JOIN users u ON u.id = c.user_id WHERE u.username = 'chkzero001') AS categories_now,
       (SELECT COUNT(*) FROM budgets b JOIN users u ON u.id = b.user_id WHERE u.username = 'chkzero001' AND b.season_year = 2026 AND b.season = 'AUTUMN') AS budget_rows_autumn;
```

期待：⑦ で `255 rows affected`（今の季節の `c…` の予算の行。手順 4 で作った分）、⑧ で `255 rows affected`、⑨ で `categories_now` が **1**、`budget_rows_autumn` が **1**〔食費の分〕。

〔`chkzero001` のアカウント自体と、ほかの科目〔`食費`〕・取引先・支払方法は、4.17 で片付けるかを決める（Q8-4）。〕

#### チェック

- [x] SQL ①②（②b を含む）を実行し、期待どおりだった
- [x] SQL ③を実行し、255 行（① の `to_add`）が足された。④で 256
- [x] 手順 1：科目一覧に 256 個が出る（500 にならない）
- [x] 手順 2：257 個目で「科目は256個までです」（追加されない）
- [x] 手順 3〜4：予算設定に 256 行が出て、保存できる（⑤で 256 行）
- [x] 手順 5：予実比較に 256 行と合計行が出る（500 にならない）
- [x] 手順 6：1 つ削除すると、また 1 つ追加できる。もう一度で「科目は256個までです」
- [x] 元に戻した（⑥〜⑨で、科目 1・今の季節の予算の行 1）
- [x] （取引先・支払方法の上限は、自動テスト〔`MasterLimitControllerTest`、`MasterLimitServiceTest`〕で確認済み）

（実施の記録・4.10：2026年11月3日。あなたが `chkzero001` で行い、**すべて期待どおり**。
- SQL ①〜④：科目を 255 個足して、256 個にした。
- ブラウザ 1〜6：257 個目は「科目は256個までです」で追加されない。予算設定で 256 行を**一度に保存でき**〔400 にならない〕、⑤で `budget_rows` は 256。予実比較も 256 行と合計行が出た。`c1` を消して足し直すと、また上限で止まった。
- 元に戻す ⑥〜⑨：255 個の科目と予算の行を消し、`categories_now` は 1、`budget_rows_autumn` は 1 に戻った。
- `chkzero001` は残してある。4.17 で片付けるかを決める。）

### 4.11 年と季節を選ぶ欄を足す（4.3 の G1。予算設定と予実比較）

4.3 で見つかった食い違い G1（要件 4.3「設定する年と季節を**選べる**」に対して、選ぶ欄が無い）を、**(b) 年と季節を選ぶ欄を足す**で直す（決定済み）。これは**要件を満たすための直し**で、新しい機能ではない。要件定義書の文は、このままで合う（「選べる」が本当になる）ので、**直さない**。

**決まっていること（あなたの指示）**

- `fragments/season.html` に、**GET のフォーム**を足す。予算設定（`/budgets`）と予実比較（`/comparison`）の**両方**に付く（部品を共有しているため）。
- 年は **2000〜2100 の選択肢**、季節は**春・夏・秋・冬の選択肢**。初期値は**今表示している季節**。
- 送る URL は、今のリンクと同じ形（`?year=…&season=…`）。受け取りと 404 は、**今の `SeasonPeriod.resolve` をそのまま使う**（Controller は変えない）。
- 「前の季節」「次の季節」のリンクは**残す**。
- **JavaScript は使わない**。「表示」ボタンで送る。

**作業量の見積もり**：テンプレート 1 枚（`fragments/season.html`、約 25 行）、`style.css` に数行、メッセージは `season.*` を再利用、テストの追加（各画面 5 件ほど）、設計書の数か所。Controller・Service・Repository は変えない。

**手順**（先に文書、次にコード。**文書は、要件定義書、設計書、手順書の順**）

1. 文書
   - [x] 要件定義書：**変更なし**（理由：4.3 の「選べる」を、この対応で満たす。4.4 には「選ぶ」の記述がなく、書き足す必要もない）
   - [x] 設計書 2.3「予算設定 `/budgets`」と「予実比較 `/comparison`」の表示項目に、「年と季節を選ぶ欄と『表示』ボタン」を足す。2.3 の「保存していない入力は…捨てられる（Q27 の d）」に、「表示」ボタンで移動したときも同じ、と足す
   - [x] 設計書 2.4「切り替え」：リンクに加えて、年と季節を選ぶ欄（GET）がある。送る先は同じ URL の形で、404 の判定も同じ（B15）。年の選択肢は 2000〜2100、季節は 4 つ
   - [x] 設計書 4.4.2 の `fragments/season.html` の説明に、フォームを足す
   - [x] 手順書（この文書）：4.3 の記録に、G1 を「(b) に決定。4.11 で対応」と書く
2. コード
   - [x] `fragments/season.html`：`<form method="get" th:action="@{${path}}">`、年の `<select name="year">`（2000〜2100）、季節の `<select name="season">`（`spring`・`summer`・`autumn`・`winter`。表示名は `messages.properties` の `season.*`）、`<button type="submit">表示</button>`。`<label>` を付ける。初期値は `period` の年と季節（`th:selected`）。CSRF のトークンは要らない（GET で、何も変えないため）
   - [x] 年の選択肢の作り方は **Q8-15** の決定による。冬の年の説明（ヒント）と、フォームの置き場所は **Q8-16** の決定による
   - [x] `style.css`：フォームの並び（横に並べる。幅が狭いときは折り返す）。Bootstrap を入れたあと（4.12）に整えるので、ここでは最小限
3. テスト（既存の `BudgetControllerTest`・`ComparisonControllerTest` に足す。既存のテストは変えない）
   - [x] 指定なしで開くと、今の季節が選ばれている
   - [x] `year=2026&season=winter` で開くと、年 2026・季節 冬が選ばれている
   - [x] 年の選択肢が 2000 から 2100 まで 101 個（`SeasonPeriod.FIRST_YEAR`・`LAST_YEAR` と同じ）、季節が 4 個
   - [x] フォームは `method="get"` で、送り先が `/budgets`、`/comparison`。`_csrf` を含まない
   - [x] 前・次のリンクが残っている。2000年春で「前の季節」が出ず、2100年冬で「次の季節」が出ないが、フォームは出る
   - [x] フォームの送信と同じ URL（`?year=…&season=…`）で、期待どおりの画面が出る（200）。範囲外・片方だけは今までどおり 404（既存のテストで確かめ済みの動き）
   - [x] `.\mvnw test` が全部成功（件数を記録する）
4. ブラウザ（あなたが行う）。`.\mvnw spring-boot:run` で起動し、`test1` でログインする。**予算設定（`/budgets`）と予実比較（`/comparison`）の両方**で、次を行う。

   | # | 操作 | 期待 |
   |---|---|---|
   | ① | メニューから開く（指定なし） | 見出しのすぐ下に「年 [ ▼ ] 季節 [ ▼ ] [表示] 冬は、12月がある年を選びます」の1行がある。選ばれているのは、今の季節（今日は 2026年10月30日なので **2026年・秋**）。見出しも「2026年 秋（9月〜11月）」 |
   | ② | 年を「2027年」、季節を「春」にして、「表示」を押す | 見出しが「2027年 春（3月〜5月）」。URL が `?year=2027&season=spring`。選ぶ欄の最初の値も「2027年」「春」 |
   | ③ | 年を「2026年」、季節を「冬」にして「表示」 | 見出しが「2026年 冬（2026年12月〜2027年2月）」（冬は12月の年） |
   | ④ | 年を「2000年」、季節を「春」にして「表示」 | 「2000年 春」。「前の季節」リンクが**出ない**。選ぶ欄は出る |
   | ⑤ | 年を「2100年」、季節を「冬」にして「表示」 | 「2100年 冬（2100年12月〜2101年2月）」。「次の季節」リンクが**出ない**。選ぶ欄は出る |
   | ⑥ | 「前の季節」「次の季節」のリンクを押す | 今までどおり動く。移ったあと、選ぶ欄の最初の値も、移った先の季節になる |
   | ⑦ | **予算設定だけ**：予算額の欄に別の数字（例：`12345`）を入れ、**保存せずに**年と季節を変えて「表示」 | 別の季節に移り、入れた数字は残らない（捨てられる）。確認の画面は出ない（設計書 2.3） |
   | ⑧ | **予算設定だけ**：⑦のあと、元の季節に戻る | 保存していないので、数字は保存済みの額（または前の季節の額、未設定の印）のまま。「保存」を押すと今までどおり保存できる |
   | ⑨ | URL を手で書き換える：`/budgets?year=1999&season=spring`、`/comparison?year=2026`（片方だけ） | 今までどおり 404 の画面 |
   | ⑩ | 開発者ツールの「ネットワーク」を開いて「表示」を押す | リクエストは **GET**（`POST` ではない）。URL は `?year=…&season=…` の形だけで、`_csrf` は付かない |
   | ⑪ | キーボードだけで操作する（Tab で選ぶ欄に移り、矢印キーで選び、Enter で送る） | 送れる。年の選択肢をキーボードで選べる |
   | ⑫ | ブラウザの幅を狭くする（約 744px） | 選ぶ欄が折り返して、切れずに全部見える（見た目は 4.12 で整える。ここでは崩れで使えなくなっていないかだけ） |
   | ⑬ | ログアウトして `/budgets` を開く | ログイン画面へ移動する（今までどおり） |

   - [x] ①〜⑬ が期待どおり（あなたが確認。すべて期待どおり）
   - [x] 500 や 404 が出ていない（⑨ の 404 を除く）

（実施の記録：Claude Code が、文書→コード→テストの順で行った。設計書の 2.3〔予算設定・予実比較の表示項目、保存していない入力の文〕、2.4〔切り替え〕、4.4.2〔`fragments/season.html` の説明〕を直した。要件定義書は変えていない。コードは `fragments/season.html`〔GET のフォームを足した。部品の根を `th:block` にして、リンクの行とフォームを並べた〕と `style.css`〔`.season-select`、`.season-hint`〕だけ。Controller・Service・Repository は変えていない。テストは `SeasonSelectAssertions`〔共有の確認用の部品〕と、`BudgetControllerTest`・`ComparisonControllerTest` に 29 件〔各画面 14 件と 15 件。選ぶ欄の中身、初期値、端、リンクが残ること、科目 0 件、送る形〕を足した。既存のテストは変えていない。`.\mvnw test` は **1234 件すべて成功**〔出発点 1205 件 ＋ 29 件〕。ブラウザの確認〔4〕は、あなたが行う。）

### 4.12 Bootstrap（CSS だけ）を入れる

**目的**：見た目を整えることと、iPad で見やすくすること（Q8-12 にも関係する）。**4.13（iPad の確認）より前に行う。** Phase 9 の画面の画像は、これが済んだあとに撮る。

**決まっていること（あなたの指示）**

- Bootstrap の **CSS だけ**を使う。**Bootstrap の JavaScript は入れない。**
- **CDN は使わず**、アプリの中に入れる（面接会場でインターネットにつながらなくても、見た目が崩れないように）。
- 自分の CSS（`row-over`・`row-low` など、**テストが確かめているクラス**）は残す。
- 4.13 より前に行う。

**JavaScript を使わないことで、できなくなること**：Bootstrap の「ハンバーガーメニュー（折りたたみ）」「ドロップダウン」「モーダル」は使えない（どれも Bootstrap の JavaScript が要る）。メニューは**いつも開いた横並び**で、幅が足りないときは折り返す（`flex-wrap`）。削除の確認ダイアログは、今までどおり `confirm()`（ブラウザの標準。Bootstrap と関係ない）。

**テストが確かめている HTML（Bootstrap のクラスを足しても、壊してはいけない所）**

| 場所 | 確かめている文字列 | 守り方 |
|---|---|---|
| 入力エラー | `class="field-error"`（属性が**これだけ**。数を数えている） | クラスを足さない。色は `style.css` のまま |
| 金額の列 | `<td class="num">`、`<th class="num">` | クラスを足さない。右寄せは `style.css`（または、テストを変える決定をしたとき） |
| 予実比較の行 | `<tr class="row-over">`、`<tr class="row-low">`、`<tr class="total-row">`、`<tr class="total-row row-over">`、`<tr class="total-row row-low">` | `<tr>` にクラスを足さない。Bootstrap のクラスは `<table>` に付ける |
| メニューのリンク | `<a href="/comparison">予実比較</a>`（`<a>` にクラスが無い形） | `<a>` にクラスを足さない。見た目は `.menu a` の CSS で当てる |
| 保存ボタン | `<button type="submit">保存` が**無い**ことを確かめる所がある（科目が 0 件のとき） | ボタンの出し分けは変えない |

方針（**Q8-20** の決定による）：**テストは変えない。** 上の文字列を壊す所は、Bootstrap のクラスを足さず、`style.css` で当てる。どうしても変えたい所が出たら、一覧にして確認する。

**手順**（先に文書、次にコード）

1. 文書（**要件定義書は、直す前と後の文を見せてから直す**）
   - [x] 要件定義書 7章「使用技術」に Bootstrap を足す（文面は、確認用にこの手順書の下の「文書の直し方」に書く）
   - [x] 設計書 1.2 の技術の表に Bootstrap の行を足す。同じ 1.2 の「CSS の部品集は使わない。（要件にないので、増やさない）」を書き直す。2.2/4.3 の置き場所（`static/css/`）の表、4.4.2 の共通部品の説明を直す。6章・7章の「`/css/**` はログイン前から読める」は、**Q8-17 が B なら変更なし**（A なら `/webjars/**` を足す）
   - [x] 実行計画書の Phase 8 の作業に「年と季節を選ぶ欄（G1）」「Bootstrap の導入」を足す。ルール 8 の共通事項に、画面の作り方（Bootstrap のクラス＋テストが確かめるクラスは残す）を足す
   - [x] この手順書：決めた内容（Q8-15〜Q8-23）を書く（「5. 決まっていない点」の決定欄）
2. Bootstrap を入手して置く（**Q8-17**、**Q8-18** の決定による）
   - [x] 公式の配布物から `bootstrap.min.css` を取得する（版を文書に書く）
     （版の確認の記録・2026年10月30日：**5.3.8**。npm の `latest` が 5.3.8〔2025年8月26日公開〕、GitHub の最新リリースも v5.3.8、6 系は `6.0.0-alpha.1`〔2026年10月8日公開の試作版〕だけで安定版はない。5.4 系はまだ無い。Q8-18 の A のとおり、5.3.8 を使う。取得元は、jsDelivr の `https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css` を**ダウンロード元として1回だけ**使う〔アプリが実行時に読むのではない〕。jsDelivr が公開している SHA-256〔`2FMn2Zx6PuH5tdBQDRNwrOo60ts5wWPC9R8jK67b3t4=`、232,111 バイト〕と、npm の配布物〔tarball。npm が公開している `integrity` と照らす〕の中の同じファイルの中身との**両方**が一致することを確かめてから置く。）
   - [x] 取得したファイルが本物か、**2 つ以上の出どころの値**（例：npm の公開情報の `integrity`、jsDelivr の公開情報の SRI）と照らして確かめる。**ダウンロードしたファイルは、信用できないデータとして扱い、実行しない**（CSS なので実行されないが、中身は先頭の著作権表示を目で確かめる）
   - [x] `src/main/resources/static/css/bootstrap.min.css` に置く（Q8-17 が B のとき）。先頭の著作権表示（`Copyright 2011-… The Bootstrap Authors`、`Licensed under MIT`）を**消さない**
   - [x] ライセンスの扱い（**Q8-21**）：リポジトリ直下に `THIRD-PARTY-NOTICES.md` を作る（名前、版、出どころの URL、MIT ライセンスの全文）
3. 読み込む
   - [x] `fragments/common.html` の `head` で、`bootstrap.min.css` を読み込み、そのあとに `style.css` を読み込む（自分の CSS が後なので、上書きできる）
   - [x] `SecurityBehaviorTest` の「ログイン前に開ける」に `/css/bootstrap.min.css` を足す（ログイン画面にも使うため）
   - [x] 画面を開いて、開発者ツールの「ネットワーク」で、**外部のドメインへの要求が 0 件**であること（Bootstrap がアプリ自身から配られていること）
4. クラスの対応表を、**先に一覧にして確認する**（コードを変える前。Q8-8 と同じ進め方）
   - [x] 画面の部品ごとに「今のクラス」と「足す Bootstrap のクラス」の表を作る：メニューとヘッダー（`.app-header`、`.menu`）、メッセージ（`.message`）、フォーム（`.field`・`label`・`input`・`select`・ボタン）、表（`table.list`）、案内（`.notice`）、詳細（`dl.detail`）、季節のフォーム（4.11）、エラー画面。上の「守る文字列」に当たる所は、足さない欄に入れる
   - [x] あなたが確認してから、コードを変える
     （実施の記録：ご指示「全画面に当て終わるまで続けて」により、**確認を待たずに**コードを変えた。下の表は、事後の確認用。）

     | 部品 | 今のクラス（残す） | 足した Bootstrap のクラス | 足さない（理由） |
     |---|---|---|---|
     | 本体 | `main`、`main.narrow` | `container` | — |
     | ヘッダー | `.app-header`、`.menu`、`.user-area` | `bg-body-tertiary border-bottom`、`d-flex flex-wrap`、`d-flex align-items-center gap-2` | メニューの `<a>`（テスト：`<a href="/comparison">予実比較</a>`）。見た目は `.menu a` |
     | ログアウト | — | `btn btn-outline-secondary btn-sm` | — |
     | メッセージ | `.message success`、`.message error` | `alert alert-success`、`alert alert-danger` | — |
     | 案内 | `.notice` | `alert alert-warning` | — |
     | ラベル・入力欄 | `.field` | `form-label`、`form-control`、`form-select` | — |
     | 項目のエラー | `.field-error` | なし | `class="field-error"` は属性がこれだけであること（テストが数える） |
     | 主ボタン（登録・追加・保存・変更・更新・ログイン） | — | `btn btn-primary` | — |
     | 削除ボタン | `.inline-form` | `btn btn-outline-danger`（一覧は `btn-sm`） | — |
     | 変更・編集リンク | — | `btn btn-outline-secondary`（一覧は `btn-sm`） | — |
     | 一覧の表 | `table.list` | `table table-hover align-middle`、表を `table-responsive` の `<div>` で包む | `<th class="num">`、`<td class="num">`（テスト） |
     | 操作の列 | `td.actions` | 中に `d-flex align-items-center gap-2` の `<div>` | — |
     | 予算設定の表 | `table.list.budget-table` | `table align-middle`、`table-responsive` | — |
     | 予実比較の表 | `table.list.comparison-table` | `table align-middle`、`table-responsive` | `<tr>` の `row-over`・`row-low`・`total-row`（テスト）。`style.css` が `td` に色を直接指定 |
     | 季節の欄 | `.season-nav`、`.season-select` | 前後のリンク `btn btn-outline-secondary btn-sm`、選択 `form-select form-select-sm w-auto`、ボタン `btn btn-primary btn-sm` | リンクの `href="…">前の季節`（テスト）。`class` は `href` の前に置いた |
     | 詳細 | `dl.detail`、`.button-row` | — | — |

     気づいた点（対応済み）：科目が 0 件のとき `<button type="submit">保存` が**ない**ことを確かめる既存のテスト（`BudgetControllerTest.noCategoriesShowsGuidanceAndNoSaveButton`）は、ボタンにクラスが付いたため、**ボタンがあっても通る**形になっていた。あなたの指示で直した：否定の文字列を `>保存</button>` にした（クラスが付いても成り立つ）。同じ文字列が、科目があるときの画面にあることを、同じファイルの肯定のテストにも足した。確かめ：ボタンを 0 件でも出すように一時的に変えると、直したテストが**落ちる**ことを確認して、元に戻した。
     テスト全体の「〜が無い」ことを確かめる箇所〔`doesNotContain`、`not(containsString)`、`isFalse` など〕の見直し：画面の HTML を読むテストの `doesNotContain` 54 行〔1行に複数の文字列を確かめるものを含む〕と、`isFalse` など画面を読まないものを一覧にして調べた。`service`・`repository`・`form` の 7 行は HTML を読まないので対象外。Bootstrap のクラスでいつでも成り立つようになっていたのは、上の 1 か所だけ。残りは、文字（「他人の科目」、`>null<`、`<b>太</b>`、`前の季節` など）か、変えていない属性（`action="/transactions/new"`、`name="amount"`、`value="…"`、`row-`〔`<tr>` の中。`<tr>` にはクラスを足していない〕、`<table`〔`<table` で始まる形は変わっていない〕）の否定で、肯定の側が同じ形で成り立っていることを確かめた。
5. 画面に当てる（範囲は **Q8-19** の決定による）
   - [x] 共通の枠（`fragments/common.html` の `header`、`messages`）
   - [x] ログイン、アカウント登録
   - [x] 取引の一覧・登録／編集・詳細
   - [x] 科目、取引先、支払方法（一覧、変更）
   - [x] 予算設定、予実比較（赤・青の行、合計行、注記、季節のフォーム）
   - [x] エラー画面 4 種（404、403、4xx、5xx）
   - [x] 変えるのは **HTML のクラスと `style.css` だけ**。`th:` の式、フォームの項目名、URL、`_csrf`、`th:text`（`th:utext` は使わない）は変えない
6. `style.css` を整える（**Q8-20** の決定による）
   - [x] Bootstrap と重なる規則（余白、表の線、ボタン、メッセージの背景など）を消す。残すのは、Bootstrap に無いもの（`row-over`・`row-low`・`total-row`・`field-error`・`legend`・`unset-mark`・`num` など）と、上の表の「守る文字列」のための規則
   - [x] 残す規則に、短い理由のコメントを付ける
7. 確認
   - [x] `.\mvnw test` が全部成功（件数を記録する。4.11 の件数から**減っていない**こと）
     （実施の記録：**1235 件すべて成功**〔4.11 の 1234 件 ＋ `SecurityBehaviorTest` の `/css/bootstrap.min.css` 1 件〕。既存のテストは変えていない。自分が 4.11 で作った `SeasonSelectAssertions` だけ、`<select>` の探し方〔`id="…"` を探す〕と「表示」ボタンの文字列〔`>表示</button>`〕を、クラスが付いた形に合わせた。）
   - [x] **赤と青の行が、Bootstrap の表の文字色に負けない**（Bootstrap の表は、セルの文字色を自分で決める。`row-over`・`row-low` が効いているか、ブラウザで確かめる）
   - [x] 4.5 の表の画面を**すべて開き直す**（見た目が変わっただけで、動きは同じ。入力エラーの表示し直し、404、403 も）。500 が出ない
   - [x] ブラウザの幅を狭くして（開発者ツールのデバイス表示で、約 744px〔iPad の縦〕と約 1024px〔横〕）、メニューが折り返して押せる、表がはみ出さない
   - [x] パソコンのネットワークを切った状態（またはオフライン表示）でも、見た目が変わらない（CDN を使っていない確認）
   - [x] `git grep -n "cdn\|https\?://" -- src/main/resources/templates src/main/resources/static`：外部の URL が 0 件（名前空間の `http://www.thymeleaf.org` を除く）
     （実施の記録：`templates` では、外部の URL は 0 件〔`http://www.thymeleaf.org` を除く。あとは「CDN は使わない」というコメントの文字だけ〕。`static` には、`bootstrap.min.css` の著作権表示と、`data:` で埋め込まれた図の名前空間の URL〔`http://www.w3.org/2000/svg`〕があるが、どちらも**読み込みはしない**ただの文字。`static` を含めて 0 件を期待していたが、配布物そのままなので、この説明に直す。）
   - [x] `git status` で、Bootstrap の JavaScript（`*.js`）が入っていない

（実施の記録・ブラウザの確認：あなたが行い、すべて問題なし。赤・青の行の色、幅を狭めたときの折り返し、ネットワークを切った状態の見た目、外部への要求 0 件。4.5 の画面の開き直しは、「すべて問題なし」に含めてチェックした。対応表〔4〕は、事後に確認済み。）

（実施の記録・入手と配置：Bootstrap **5.3.8**。`https://cdn.jsdelivr.net/npm/bootstrap@5.3.8/dist/css/bootstrap.min.css` を作業用の一時フォルダーに1回だけダウンロードし、2つの照らし合わせをした。①**SHA-256**：`2FMn2Zx6PuH5tdBQDRNwrOo60ts5wWPC9R8jK67b3t4=` ＝ jsDelivr の公開値〔232,111 バイト〕と**一致**。②**npm の配布物**：`bootstrap-5.3.8.tgz` の SHA-512 が npm の公開 `integrity`〔`sha512-HP1SZDqaLDPwsNiqRqi5NcP0SSXciX2s9E+RyqJIIqGo+vJeN5AJVM98CXmW/Wux0nQ5L7jeWUdplCEf0Ee+tg==`〕と**一致**し、その中の `dist/css/bootstrap.min.css` と `LICENSE` が、ダウンロードしたものと**バイト単位で同一**〔`cmp`〕。先頭の著作権表示〔`Copyright 2011-2025 The Bootstrap Authors`、MIT〕を確かめて、`src/main/resources/static/css/bootstrap.min.css` に置いた。末尾の `sourceMappingURL` の行はそのまま〔`.map` は置かない〕。`.gitattributes` に `src/main/resources/static/css/bootstrap.min.css -text` を足した〔Git が改行を変換して、確かめたファイルと違う中身にならないように〕。`THIRD-PARTY-NOTICES.md` を作った。）

（実施の記録・画面：19 枚のテンプレートのうち、17 枚〔エラー画面 4 種を含む〕と、共通の `fragments/common.html`〔`head` に `bootstrap.min.css` を足し、`style.css` はその後〕、`fragments/season.html` に Bootstrap のクラスを付けた。変えたのは HTML のクラスだけで、`th:` の式、フォームの項目名、URL、`_csrf` は変えていない。`style.css` は、Bootstrap と重なる規則を消し、残す規則に理由のコメントを付けた。ブラウザでの見た目の確認〔上の「確認」のうち未チェックの 5 項目〕は、あなたが行う。）

**文書の直し方（要件定義書：直す前と後。確認のあとで直す）**

直す前（要件定義書 7章）：

```
## 7. 使用技術
- Java
- Spring Boot
- MySQL
- VS Code
- Git / GitHub
- Claude Code
```

直した後：

```
## 7. 使用技術
- Java
- Spring Boot
- MySQL
- Bootstrap（見た目を整えるための CSS だけを使う。JavaScript は使わない）
- VS Code
- Git / GitHub
- Claude Code
```

- 要件定義書のほかの章は**変えない**。9章の「スマホ向けの画面」も、そのまま残す（**Q8-23** の決定による。Bootstrap を入れても、スマホ向けの画面づくりをするわけではない）。
- 3章・4.3 の「年と季節を選べる」は、4.11 で本当になるので、文は変えない。

直す前と後（設計書 1.2 の最後の項目）：

- 前：`画面の見た目は、自作の CSS 1ファイル（static/css/style.css）で作る。CSS の部品集は使わない。（要件にないので、増やさない）`
- 後：`画面の見た目は、Bootstrap の CSS（static/css/bootstrap.min.css。アプリの中に置き、CDN は使わない。JavaScript は入れない）と、自作の CSS（static/css/style.css。Bootstrap にないものと、テストが確かめるクラスだけ）で作る。（Phase 8、Q8-17〜Q8-21）`

### 4.13 パソコンのアプリを、iPad の Safari から開けるか確かめる（面接用）

面接で iPad から触ってもらうため、パソコンで起動したアプリを、**同じネットワーク**の iPad の Safari から `http://（パソコンの IP アドレス）:8080/` で開けるかを確かめる。**コードは変えない。** インターネットへの公開ではない（4.13 は、同じ Wi-Fi の中だけ。2 のやらないこと）。

#### 先に、コードと設定を読んで分かったこと（実行はしていない）

結論：**開ける見込みが高い。ログインもできるはず。コードと設定を変える必要はない。** ただし、iPad の Safari の実際の動きは、実機で確かめるまで分からない。

| 見たもの | 分かったこと |
|---|---|
| 待ち受ける口 | `application.properties` に `server.address` の指定がない。Spring Boot（Tomcat）の標準は、パソコンのすべてのネットワークの口（`0.0.0.0`）で待ち受ける。そのため、パソコンの IP アドレスでも開ける。**実際の待ち受けは、手順 C の `netstat` で確かめる** |
| ホスト名の決め打ち | `src/main` に `localhost`、`127.0.0.1`、`http://` の直書きがない。画面のリンクは `@{…}`（アプリの中の相対）、ログイン失敗の移動も相対パス。IP アドレスで開いたら、移動のあとも IP アドレスのままになる |
| Cookie の設定 | `server.servlet.session.cookie.http-only=true`、`same-site=lax`。**`secure` の指定はない**（設計書 7.10：HTTP のあいだは付けない）。`secure` が付いていると、Safari は HTTP の接続では Cookie を保存せず、ログインできなくなる。今は付いていないので、HTTP の IP アドレスでも Cookie は保存される。`domain` の指定もない（IP アドレスで開いたそのホストの Cookie になる） |
| `SameSite=Lax` | 同じ `http://IP:8080` の中の移動と POST では、Cookie が付く（同じサイト）。ログインの POST、CSRF のトークン（セッションに入っている）も、Cookie が保存されれば動く |
| セッションIDの渡し方 | `tracking-modes=cookie`（URL には付けない）。Safari でも同じ |
| Spring Security の設定 | `requiresChannel`（HTTPS への強制）はない。HSTS は HTTPS のときだけ付く。HTTP のままで動く |
| 画面の幅 | 全画面の共通の `<head>` に `viewport`（`width=device-width, initial-scale=1`）がある。CSS は本体が `max-width: 960px`、ログイン・登録が `max-width: 420px` で、`@media`（画面の幅による切り替え）は**ない**。iPad の幅（縦で約 744px 以上）なら収まるはずだが、**表の列が多い画面〔取引一覧、予算設定、予実比較〕は実機で確かめる** |
| 注意 | HTTP のログイン画面では、Safari が「保護されていない通信」と表示する。**正常**（暗号化していないため）。パスワードも暗号化されずにネットワークを流れる（Q8-14） |

#### 手順

> 管理者の PowerShell が要るもの（ファイアウォールの設定）は、**あなたが行う**。Claude Code は手順と期待値を示し、結果を受けて記録する。パソコンの IP アドレスの値は、文書に書かない。

**A. 準備**
1. パソコンと iPad を、**同じ Wi-Fi** につなぐ。
2. パソコンがつないでいるネットワークの種類を確かめる。**許可ルールは `Private` に絞っているので、`Private` でないと効かない**。確かめ方と、`Private` にする手順・戻す手順は、下の「**ネットワークの種類を `Private` にする手順（共通）**」。

**B. IP アドレスを調べる**
```powershell
ipconfig
```
使っている Wi-Fi のアダプターの「IPv4 アドレス」を読む（例の形：`192.168.…`）。この値は控えるだけで、文書には書かない。

**C. アプリを起動し、待ち受けを確かめる**
1. `.\mvnw spring-boot:run` で起動する。
2. 別のターミナルで：

```powershell
netstat -an | findstr :8080
```

  期待：`0.0.0.0:8080`（または `[::]:8080`）が `LISTENING`。`127.0.0.1:8080` だけのときは、パソコンの外から開けない（設定を確かめる）。

**D. パソコン自身から、IP アドレスで開く**
ブラウザで `http://（パソコンの IP）:8080/` を開く。ログイン画面が出る。ファイアウォールを通らない確認で、アプリが IP アドレスで動いていることが分かる。

**E. ファイアウォールで 8080 番を許可する（管理者の PowerShell。あなたが行う）**

```powershell
New-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private -RemoteAddress LocalSubnet
```

- `-Profile Private`：プライベートのネットワークだけ。`-RemoteAddress LocalSubnet`：同じネットワークの中の機器だけ（外から開けないようにする）。
- 確かめる：

```powershell
Get-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp" | Format-Table DisplayName, Enabled, Direction, Action, Profile
```

  期待：`Enabled` が `True`、`Direction` が `Inbound`、`Action` が `Allow`、`Profile` が `Private`。
- Java を初めて動かしたとき、Windows が「ファイアウォールで許可しますか」と出すことがある。**「キャンセル」（許可しない）を選ぶ**のが安全（アプリ全体を許可する広いルールができてしまうため）。許可してしまったときは、手順 I で確かめて消す。

**F. iPad の Safari で開く**
1. `http://（パソコンの IP）:8080/` を入力して開く。
2. 期待：ログイン画面が出る（「保護されていない通信」の表示は正常）。
3. 開けないときは、7 の「つまずいたとき」を見る。

**G. ログインして、全画面を見る**（アカウントは Q8-14 の決定による）

ログインして、取引一覧が出ることを確かめる（ログインできる＝Cookie が保存されている）。そのあと、次の画面を、iPad を**縦**と**横**の両方で見る：

| 画面 | 見るところ |
|---|---|
| ログイン、アカウント登録 | 入力欄が押せる。キーボードが出る。エラーが読める |
| 取引一覧 | 表が画面に収まる、または横にスクロールして全部の列を読める。文字が小さすぎない |
| 取引の登録・編集 | 日付の欄で iPad の日付の選択が出る。金額の欄で数字のキーボードが出る。**日本語入力のまま全角の数字で入力しても受け付けられる**（R13）。プルダウンが選べる |
| 取引の詳細 | 「編集」「削除」が押せる。削除の確認ダイアログが出る |
| 科目・取引先・支払方法（一覧と変更） | 追加・変更・削除ができる。ボタンが押しやすい |
| 予算設定 | 科目の行が全部読める。入力欄が押せる。「前の季節」「次の季節」が押せる。保存できる |
| 予実比較 | 4列（科目・予算・実績・差額）と合計行が読める。**赤と青の行が見分けられる**。「未設定」の行、科目が0件の案内も読める。色の注記が読める。「前の季節」「次の季節」が押せる |
| メニュー | 7つの項目（取引一覧、科目、取引先、支払方法、予算、予実比較、ログアウト）が折り返しても押せる |
| 404 の画面 | 読める。「取引一覧へ戻る」が押せる |

見つけたことは、次の3つに分けて記録する（直すかどうかは Q8-12 の決定による）：
- **操作できない**（ボタンが押せない、入力できない）
- **読めない**（文字が切れる、重なる、画面の外にはみ出す）
- **見た目の好み**（余白が狭い、など）
- **物足りなさ**（メニューや削除の確認などで、「こうだったらよいのに」と感じたこと。例：メニューが折りたためない、削除の確認が簡素）。**記録だけして、その場では直さない。**

- [x] メニューや削除の確認などで物足りなさを感じたら、記録した（どの画面の、どの操作で、何が物足りなかったか）。**結果：物足りなさは「なし」。Q8-24「Bootstrap の JavaScript を入れるか」は「入れない」で決定**（設計書 1.2、要件定義書 7章の「JavaScript は使わない」はそのまま）。その結果で、**Bootstrap の JavaScript を入れるかを決める**（今は CSS だけ。Q8-23、4.12 の決まり）。決めるときは、新しい Q（Q8-24）として、選択肢とおすすめと理由を一覧にして確認する。入れる場合は、設計書 1.2 と要件定義書 7章の文（「JavaScript は使わない」）も直す

**H. ログアウトして、後片付けの前に確かめる**
ログアウトすると、ログイン画面に戻る。戻るボタンで前の画面を開いても、ログイン画面へ移動する。

**I. 後片付け：ファイアウォールの許可を外す（管理者の PowerShell。あなたが行う）**

```powershell
Remove-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp"
```

- 確かめる：

```powershell
Get-NetFirewallRule -DisplayName "HouseholdBudget-8080-temp"
```

  期待：「見つかりません」のエラー（ルールがない）。
- Java の広い許可ルールができていないか確かめる：

```powershell
Get-NetFirewallRule -Direction Inbound -Enabled True | Where-Object { $_.DisplayName -like "*java*" -or $_.DisplayName -like "*OpenJDK*" } | Format-Table DisplayName, Profile, Action
```

  期待：何も出ない。出たら、内容を見て、このアプリのために自動でできたものなら消す（`Remove-NetFirewallRule -DisplayName "…"`。消す前に、内容を教えてください）。
- アプリを止める（起動したターミナルで `Ctrl+C`）。
- iPad で、もう一度同じ URL を開いて、**開けなくなっている**ことを確かめる（許可を外した確認）。

#### ネットワークの種類を `Private` にする手順（共通。自宅の Wi-Fi、ホットスポット、テザリングのどれでも使う）

Windows は、新しくつないだネットワークを「パブリック」にしやすい（ホットスポットやテザリングは特に）。パブリックのままだと、手順 E の許可ルール（`-Profile Private`）が効かず、iPad から開けない。

1. **確かめる**（変える前の種類を控える）：

```powershell
Get-NetConnectionProfile
```

   使っているネットワークの `Name`、`InterfaceAlias`（アダプターの名前）、`NetworkCategory`（`Private`／`Public`）を読む。**変える前の `NetworkCategory` を、メモしておく**（戻すとき使う）。
   - iPad をつなぐ側のネットワークを見る。自宅や会場の Wi-Fi、テザリングなら、パソコンがつないでいる Wi-Fi。ホットスポットなら、パソコンが親機になっているアダプター（名前に `Local Area Connection*` や `Wi-Fi Direct` が付くことが多い）。
2. **`Public` なら、`Private` にする**（管理者の PowerShell。あなたが行う）：

```powershell
Set-NetConnectionProfile -InterfaceAlias "（1 で読んだ InterfaceAlias）" -NetworkCategory Private
```

   - 設定の画面からも変えられる（「ネットワークとインターネット」→ Wi-Fi → つないでいるネットワーク → 「ネットワーク プロファイルの種類」→ 「プライベート ネットワーク」）。
   - もう一度 `Get-NetConnectionProfile` で、`NetworkCategory` が `Private` になったことを確かめる。
   - **ホットスポットのアダプターは、種類を変えられないことがある。** そのときは、許可ルールの `-Profile Private` を、そのネットワークの種類に合わせて作り直す（`-RemoteAddress LocalSubnet` は残す）。終わったら、手順 I でルールごと消す。
3. **終わったら、元に戻す**：手順 I のあとで、1 で控えた種類に戻す。

```powershell
Set-NetConnectionProfile -InterfaceAlias "（1 で読んだ InterfaceAlias）" -NetworkCategory Public
```

   - **変える前が `Public` だったネットワークは、必ず `Public` に戻す**。`Private` のままだと、そのネットワークでは、パソコンが「ほかの機器から見える」状態になる（ファイル共有などの規則がゆるくなる）。信頼できない会場の Wi-Fi は、特に戻す。
   - 自宅など、もともと `Private` だったネットワークは、触らない。
   - ホットスポットやテザリングのネットワークは、Windows が次回以降も覚えていることがある。戻したことを、`Get-NetConnectionProfile` で確かめる。

#### 代わりの方法（Q8-13 の決定：C）

同じ Wi-Fi でも、会場の Wi-Fi が端末どうしの通信を禁止している（ゲスト用など）と、IP アドレスで開けない。次の2つの方法を、**自宅で先に試しておく**。どちらも、手順 C〜I は同じに行い、手順 A の 2 を「上の共通の手順」に置き換える。

**方法 1：Windows の「モバイル ホットスポット」（パソコンを Wi-Fi の親機にして、iPad をつなぐ）**

1. 「設定」→「ネットワークとインターネット」→「モバイル ホットスポット」を入れる。表示された名前とパスワードで、iPad をつなぐ（このパスワードは、文書に書かない）。
   - **パソコン自身がインターネット（Wi-Fi、有線、モバイル回線）につながっていないと、ホットスポットを始められないことがある**（共有するもとの回線が要るため）。始められなかったら、その表示を記録して、方法 2 に進む。**始められるかどうかは、実機で確かめる。**
2. `ipconfig` で、ホットスポット側のアダプターの IPv4 アドレスを読む。
3. 上の共通の手順で、ホットスポットのネットワークの種類を確かめ、`Private` にする（変えられないときは、許可ルールの `-Profile` を合わせる）。
4. 手順 C〜I を行う。
5. 終わったら、ホットスポットを切る。種類を元に戻す（共通の手順の 3）。

**方法 2：スマホのテザリング（インターネット共有）に、パソコンと iPad の**両方**をつなぐ**

1. スマホのテザリングを入れる。パソコンと iPad の両方を、そのスマホの Wi-Fi につなぐ。
   - スマホの機種や設定によっては、つないだ端末どうしの通信を許可しないことがある。**パソコンから iPad、iPad からパソコンへの通信ができるかは、実機で確かめる**。開けなかったら、その旨を記録する。
   - モバイル回線のデータ通信量は、アプリがパソコンの中で動くので、ほとんど増えない。
2. パソコンで `ipconfig` を実行し、スマホの Wi-Fi につないだアダプターの IPv4 アドレスを読む。
3. 上の共通の手順で、そのネットワークの種類を確かめ、`Private` にする（新しいネットワークは `Public` になりやすい）。
4. 手順 C〜I を行う。
5. 終わったら、スマホのテザリングを切る。種類を元に戻す（共通の手順の 3）。

- [x] 手順 A〜D：IP アドレスで、パソコン自身から開ける。`netstat` で `0.0.0.0:8080`（または `[::]:8080`）が待ち受けている
- [x] 手順 E〜F：ファイアウォールの許可のあと、iPad の Safari でログイン画面が開く
- [x] 手順 G：ログインできる（取引一覧が出る）。**Cookie 設定のまま、コードも設定も変えずに**ログインできた
- [x] 手順 G：全画面を縦・横で見た。見つけたことを3つに分けて記録した（操作できない／読めない／見た目の好み）
- [x] 手順 H〜I：ログアウトできる。許可ルールを外した。Java の広い許可ルールは、あった（12 個）ので**消さずに無効にした**（下の記録）。外したあと、iPad から開けない
- [x] ネットワークの種類を確かめ、`Private` にした。終わったあと、元の種類に戻した（`Get-NetConnectionProfile` で確かめた）
- [ ] （Q8-13 は C）方法 1（ホットスポット）で開けた。**今回は行わなかった**（同じ Wi-Fi で開けたため）。面接会場で開けないときのために、自宅で試すかは Phase 9 で決める。**パソコンがインターネットにつながっていなくても始められるか**を記録した
- [ ] （Q8-13 は C）方法 2（スマホのテザリングにパソコンと iPad をつなぐ）で開けた。端末どうしの通信が許可されるかを記録した（**今回は行わなかった**。方法 1 と同じ）

（実施の記録・iPad の確認〔手順 A〜J。2026年10月30日。あなたが実行し、結果を知らせた〕
- A：ネットワーク `Wi-Fi 2` の `NetworkCategory` は `Public` だった。`Private` に変えた〔4.17 で戻す→J で戻した〕。
- B：IPv4 アドレスを確認した〔値は書かない〕。C：`0.0.0.0:8080` と `[::]:8080` が `LISTENING`。D：パソコンのブラウザから IP アドレスで開き、`test1` でログインできた。アドレス欄は IP のまま。
- E：許可ルール `HouseholdBudget-8080-temp` を作った。確認の出力は期待どおり〔`Enabled` True、`Inbound`、`Allow`、`Private`〕。
- F：iPad の Safari で開けた。G：使い捨てのアカウント `chkipad` でログインし、全画面を縦・横で見た。**期待どおり。操作できない・読めない所はなかった。** 物足りなさは「なし」〔Q8-24：Bootstrap の JavaScript は入れない〕。
- H：ログアウトできた。ただし、Safari の「戻る」で、**一瞬古い画面が出て、そのあとログイン画面へ移動した**〔調べた結果は下〕。
- I：許可ルール `HouseholdBudget-8080-temp` を消し、消えたことを確かめた。**Java の広い許可ルールが 12 個あった**〔`OpenJDK Platform binary`、`Java(TM) Platform SE binary`、`java.exe`。Public 10 個、Private 2 個。以前に別の Java アプリで「許可」を押したときのものと思われる〕。**消さずに `Disable-NetFirewallRule` で無効にした**。無効にしたあと、有効なものは 0 件で、iPad からは開けなくなった。アプリを止めた。**戻すときは `Enable-NetFirewallRule`**〔無効にしたルールの `DisplayName`・`Profile` を指定する。ほかの Java アプリが、ネットワーク越しの接続を受けるときに必要になる〕。
- J：ネットワーク `Wi-Fi 2` を `Public` に戻した。

（調べた結果・H の「一瞬古い画面」〔Claude Code が調べた。コードは変えていない〕
- **設定**：`SecurityConfig` に `headers(...)` の指定はなく、`application.properties` にも `spring.web.resources.cache` などの指定がない。Spring Security の標準のヘッダーを**外していない**。
- **実際の応答**〔一時的な確認用のテストで、MockMvc の応答ヘッダーを出力して確かめ、そのテストは削除した〕：ログイン中の `/transactions`、`/budgets`、`/comparison`、404 の画面、`/login`、未ログインの移動〔302〕、`POST /logout` の応答のすべてに `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`、`Pragma: no-cache`、`Expires: 0` が付いていた。`/css/style.css` と `/css/bootstrap.min.css` にも付いている〔CSS を毎回読み直す。ローカルのアプリなので問題にしない〕。
- **自動テスト**：`Cache-Control` を確かめるテストは**ない**〔`src/test` に該当する検索結果なし〕。
- **考えられる原因**：サーバーの設定は標準のとおりなので、Safari の「戻る」が、ページを一時的に覚えている仕組み〔戻る・進むの高速化〕で、直前の画面を先に見せたと考えられる。そのあとにログイン画面へ移動したので、**サーバーは古い内容を返していない**〔そのあと、ログイン画面へ移動した〕。同じ iPad の画面にだけ一瞬出るもので、ほかの人に見えるものではない。ただし、実機で応答ヘッダーを見たわけではない。
- **扱いの案**〔決めるのは本人。ここでは直していない〕：(a) 記録だけ。ブラウザの動きなので、README の既知の制限に載せるかを Phase 9 で決める。(b) 4.14 で「ログイン中の画面の応答に `Cache-Control: no-store` が付く」ことの自動テストを足す。設定を外したときに気づける。(c) ログアウトの応答に `Clear-Site-Data` を付ける。Safari が対応しているか確認が要るので、おすすめしない。）

（実施の記録：Phase 8 の作業中に書く。）

### 4.14 主要な処理の自動テストを整える

実行計画書：「主要な処理の自動テストを整える（登録時の初期データ、データ分離、予実比較）」。Q8-6 の決定に従う。

1. **一覧を作る**（読むだけ。Claude Code が行う）：要件定義書 3章・4章・5章・6章の各項目に、確かめている自動テストがあるかを、表にする。

   | 項目 | 今あるテスト（候補） |
   |---|---|
   | 登録時の初期データ（科目3つ、予算、取引先、支払方法。別ユーザーに別々に作られる） | `RegistrationServiceTest`（`createsInitialData`、`initialBudgetUsesCurrentSeason`、`eachUserGetsSeparateInitialData` など） |
   | データ分離（取引、科目、取引先、支払方法、予算、予実比較） | 各 `…ControllerTest`、`…ServiceTest`、`…RepositoryTest` の「他人」「別ユーザー」のテスト |
   | 予実比較（集計、色、合計行、季節の境目、未設定） | `ComparisonServiceTest`、`ComparisonControllerTest`、`BudgetColorJudgeTest`、`SeasonPeriodTest`、`TransactionRepositorySumTest` |
   | ログイン必須、CSRF、ログイン失敗 | `SecurityBehaviorTest` |
   | 入力チェック（要件5章） | 各 `…FormTest`、`…ControllerTest`、`MessagesTest`、`StrictIntegerEditorTest` |
   | エラー画面と状態コード | `GlobalExceptionHandlerTest`、`SecurityBehaviorTest` |

2. **足りないものを、一覧にして報告する**（足すかどうかは、Q8-6 の決定による。勝手に足さない）。
3. 足すと決めたものを、**テストを先に書く**。失敗することを確かめる必要があるのは、直す不具合があるときだけ（新しく書くテストが、すでに合格するのは、普通）。
4. `.\mvnw test` が全部成功する（件数を記録する）。テストのあと、DB にテスト用の行が残っていない（4.1 の SQL と同じ結果）。

- [x] 一覧を作り、足りないものを報告した〔下の記録〕
- [x] **`Cache-Control: no-store` のテストを足す**〔4.13 の案 (b)。あなたが「足す」と決めた。2026年11月3日〕：`CacheControlHeaderTest`
- [x] **登録画面のメッセージのテストを足す**〔4.7 で決めた。2026年11月2日〕：①`MessagesTest` に、登録画面の文〔`NotBlank.registerForm.username`、`Pattern.registerForm.username`、`Size.registerForm.username`、`NotEmpty.registerForm.password`、`Pattern.registerForm.password`、`Size.registerForm.password`〕と、`error.duplicate.user`、`error.login.failed`、`success.register`、`success.logout` を足す。②`SecurityBehaviorTest`〔または登録画面のテスト〕に、`POST /register` で、ユーザー名が空欄・11文字、パスワードが空欄・空白入りのとき、**画面に**それぞれの文が出ることを確かめるテストを足す〔今は `あいう`／`short` と重複の2ケースだけ〕。テストを先に書く。
- [x] 足すと決めたテストを足した（登録画面のメッセージ、`Cache-Control`。そのほかの足りないものは、下の記録のとおり、足していない）
- [x] `.\mvnw test` が全部成功する

（実施の記録・4.14：2026年11月3日。
**① 対応表**〔要件の各項目 → 確かめている自動テスト〕：

| 要件 | 確かめている自動テスト |
|---|---|
| 3・4.1 アカウント登録・ログイン・ログアウト | `SecurityBehaviorTest`（登録の成功・入力エラー・重複、ログイン成功・失敗、ログアウト）、`RegisterFormTest`、`RegistrationServiceTest` |
| 4.1 データ分離 | 各 `…ControllerTest`・`…ServiceTest`・`…RepositoryTest` の「他人」「別ユーザー」のテスト（取引、科目、取引先、支払方法、予算、予実比較） |
| 4.2 取引（登録・一覧・詳細・編集・削除、削除の確認） | `TransactionControllerTest`、`TransactionServiceTest`、`TransactionFormTest`、`TransactionRepository*Test` |
| 4.3 科目・取引先・支払方法（追加・変更・削除、使用中・予算 0円でない科目の削除不可、初期データ、追加時の予算 0円の行） | 各 `…ControllerTest`・`…ServiceTest`、`RegistrationServiceTest`、`CategoryServiceTest` |
| 4.3 予算（季節ごと、年と季節を選ぶ欄、指定なしは今の季節） | `BudgetServiceTest`、`BudgetControllerTest`、`BudgetFormTest`、`SeasonPeriodTest` |
| 4.4 予実比較（集計、色、💮、合計行、未設定、季節の境目） | `ComparisonServiceTest`、`ComparisonControllerTest`、`BudgetColorJudgeTest`、`SeasonPeriodTest`、`TransactionRepositorySumTest` |
| 5 入力チェック（文字数、範囲、整数、全角、日付、256 個の上限） | 各 `…FormTest`、各 `…ControllerTest`、`StrictIntegerEditorTest`、`MasterLimit*Test`、`MessagesTest` |
| 6 ハッシュ化 | `RegistrationServiceTest.passwordIsHashed`、`sameRawPasswordGivesDifferentHashes` |
| 6 ログイン必須、CSRF | `SecurityBehaviorTest`、各 `…ControllerTest` の未ログイン・トークンなしのテスト |
| 6 XSS（画面での無害化） | `CategoryControllerTest`・`VendorControllerTest`・`PaymentMethodControllerTest`・`TransactionControllerTest`・`BudgetControllerTest` の `…AreEscaped…` |
| 画面の応答ヘッダー（`no-store`）、404・400 の画面 | `CacheControlHeaderTest`〔今回足した〕、`GlobalExceptionHandlerTest`、`BudgetControllerTest`（400） |

**② 足りないもの**〔Q8-6 の A：一覧にして報告する。勝手に足さない。4.6 のブラウザで済んでいるものが多い〕：
1. **予実比較の画面で、科目名が無害化される**こと〔`<b>x</b>` が文字として出る〕の自動テストがない〔ほかの画面にはある。ブラウザは 4.6 の(4)で済み〕。
2. **`th:utext` を使っていない**ことの自動テスト〔テンプレートの検索テスト〕がない〔4.4 の検索で 0 件を確認済み〕。
3. **403 の画面**〔CSRF のトークンなし〕と **5xx の画面**、**405 の画面**の文言・画面名の自動テストがない〔403 は状態コードだけ確かめている。400 は `BudgetControllerTest`。ブラウザは 4.6 の(5)(7)で済み〕。
4. **セッションの Cookie の設定**〔`HttpOnly`、`SameSite=Lax`、URL に `;jsessionid` が付かない〕を確かめる自動テストがない〔`application.properties`。ブラウザは 4.6 の(8)で済み〕。
5. **SQL インジェクション用の文字列**〔`' OR '1'='1` など〕を入れる自動テストがない〔JPA のパラメータ化で守っている。4.4 の検索と 4.6 の(3)で済み〕。
6. ログイン後にセッション ID が変わること〔MockMvc では確かめられない。ブラウザで済み〕。
→ **あなたの決定**〔2026年11月3日〕：
- **1（予実比較の科目名の無害化）：足す。** → `ComparisonControllerTest.categoryNamesAreEscaped`
- **2（`th:utext` を使っていない）：足す。** → `TemplateSafetyTest`
- **3：403 と 405 の画面の文言だけ足す。5xx は足さない**〔4.6 の(7)のブラウザ確認で済んでいる〕。 → `SecurityBehaviorTest.errorScreenHasTheMessageAndTheStatus`、`deleteByGetIsMethodNotAllowed`
- **4（セッションの Cookie の設定）：足さない。** 理由：`HttpOnly`・`SameSite=Lax`・`;jsessionid` が付かないことは、**ブラウザで確認済み**〔4.6 の(8)〕。設定は `application.properties` の数行で、プロパティの文字列を確かめるテストは、設定を写すだけで、動きの保証にならない。
- **5（SQL インジェクション用の文字列）：足さない。** 理由：**仕組みで守られている**〔JPA のパラメータ化。文字列連結の SQL を作っていないことは、4.4 の検索で確認済み。ブラウザでも 4.6 の(3)で確認済み〕。個別の文字列を入れるテストは、仕組みの代わりにならない。
- **6（ログイン後にセッション ID が変わる）：足さない。** 理由：**MockMvc では確かめられない**〔本物のサーバーと動きが違う。設計書 7.10。ブラウザで確認済み〕。

**③ 足したテスト**〔あなたの指示：登録画面のメッセージ、`Cache-Control`〕：
- `MessagesTest`：+10 件〔`registerForm` の 6 つの文、`error.duplicate.user`、`error.login.failed`、`success.register`、`success.logout`〕
- `SecurityBehaviorTest`：+9 件〔登録の入力エラーで、画面に文が出る：ユーザー名の空欄・空白だけ・全角・11 文字、パスワードの空欄・7 文字以下・31 文字・空白入り・日本語。入力エラーでも、ユーザー名は残り、パスワードは残らない〕
- `CacheControlHeaderTest`〔新しいクラス〕：+15 件〔ログイン中の画面 10 か所〔一覧、入力、設定、比較、404〕、ログイン前の画面 4 か所、未ログインの移動とログアウトの応答に `Cache-Control: no-store`〕。**確かめ方**：`SecurityConfig` に一時的に `headers(h -> h.cacheControl(c -> c.disable()))` を足して実行すると **15 件すべて失敗**した〔元に戻した。差分なし〕。
- これらは、テストが既存の動きの確認なので、足した直後から合格する〔不具合を直すものではない〕。

**⑥ 足りないものの追加分**〔上の決定の 1〜3。同日〕：
- `ComparisonControllerTest.categoryNamesAreEscaped`〔+1。`<b>x</b>` と `<script>` が、`&lt;…&gt;` として出る。科目名は 10 文字までなので、`<script>` だけ〕。
- `TemplateSafetyTest`〔新しいクラス。+1。全テンプレートに、`th:utext=`・`data-th-utext=`・`[( … )]` がない。コメントの「th:utext は使わない」には `=` が続かないので引っかからない。テンプレートが 15 個より多く見つかることも確かめる〕。**確かめ方**：`login.html` に一時的に `th:utext` を足すと **失敗**した〔元に戻した。差分なし〕。
- `SecurityBehaviorTest`〔+6。403 と 405 の画面の文言：`/error` に状態コードを付けて、ブラウザと同じ `Accept: text/html` で開く〔付けないと JSON が返る〕。画面の名前は MockMvc から分からないので、文言と状態コードで確かめる。削除の URL〔科目・取引先・支払方法・取引〕を GET で開くと 405〕。

**④ Q8-7 の ①**〔手順書では 4.15 の項目だが、「手順書の 4.14 のとおりに」のご指示で、ここで行った〕：`BudgetForm.rows` を `@Valid private List<Row>` から `private List<@Valid Row>` に直した〔動きは変わらない〕。ログの `HV000271`〔2 回出ていた〕が **0 回**になった。`BudgetFormTest`・`BudgetControllerTest` は全部通る。② Mockito の警告は、決めたとおり直さない〔ログに残っている〕。4.15 のチェックは、そこで付ける。

**⑤ 結果**：`.\mvnw test` は ①〜④ の時点で 1340 件、⑥ を足して **1348 件成功**〔1306 → 1348：+42 件〕、失敗 0、エラー 0。テスト後に DB に行が残っていないことの確認は、4.17 の片付けのあとに行う〔4.17 に書いた〕。）

### 4.15 コードの整理（使っていないコードの削除、分かりにくい名前の修正、コメント）

**勝手に直さない。** まず**一覧にして報告**し、あなたが「直す」と決めたものだけを直す（Q8-8）。

見つける方法（読むだけ）：

- 使っていないコード：クラス・メソッド・メッセージのキー（`messages.properties`）・CSS のクラスで、参照が1つもないものを検索する。IDE の「未使用」の表示も使う。
- 分かりにくい名前：クラス名・メソッド名・変数名で、Phase の途中の仮の名前や、意味とずれた名前を拾う。
- コメント：作る前のことを書いたまま残っているもの（たとえば `messages.properties` の「Phase 5 の 4.4 で作る」「Phase 6 の 4.5 の実験で…確定する」は、もう作成・確定済みなので、書き直す候補）。古い Phase の表記（「Phase 7 で足す」など）で、事実とずれたもの。
- 警告：`.\mvnw test` のログの警告（Q8-7）：
  - `HV000271: Using @Valid on a container (java.util.List) is deprecated`：`BudgetForm.rows` の `@Valid`（行の中の注釈を働かせるために付けている）。`List<@Valid Row>` の形にする案。
  - Mockito の「self-attaching」の警告（将来の JDK で動かなくなる）：テストの実行設定（`pom.xml`）に関わる。
- issue 001（4.2 の決定どおり）。

直すと決めたものの手順：文書（設計書・コメント）→ コード → `.\mvnw test`（件数は変わらないか、減る理由を説明できる）→ ブラウザで関係する画面を開く。

- [ ] 一覧を作り、報告した（直すもの・直さないものを、あなたが決めた）
- [ ] 直すと決めたものを直した。`.\mvnw test` が全部成功する
- [ ] issue 001 を決めたとおりに処理した（4.2）

### 4.16 見つかった不具合を直し、記録する

- [ ] 4.4〜4.10 で見つかった不具合を、3.3 の手順で、`docs/issues/`（004 から）か、この手順書に記録した
- [ ] 直したものは、直ったことを確かめた（再現した操作をもう一度行った）
- [ ] 直さないものは、理由を記録した（完了条件 6 の「既知の不具合が残っていない（残る場合は理由を記録）」）

（実施の記録：Phase 8 の作業中に書く。）


#### F2 の修正（Q8-25 の案 F。ログアウト後の「戻る」で、前の画面を見せない）

**文書（先に行う）**
- [x] 設計書：1.1、1.2（表と文）、2.1（削除の確認の文）、2.2（`/js/**`）、4.3（リソース）、7.3（ログインなしで開ける URL）、7.10（理由と限界）を直した〔2026年11月2日〕
- [x] 要件定義書：7章の 1 行〔`Bootstrap（見た目を整えるための CSS だけを使う。JavaScript は使わない）`〕を「`Bootstrap の JavaScript は使わない`」に直した〔案のとおり。あなたの確認済み。直す前：`…CSS だけを使う。JavaScript は使わない`〕
- [x] この手順を、あなたが確認した

**コード**〔Q8-26 は B で決まった〕
- [x] `src/main/resources/static/js/back-reload.js` を作る。中身は次の短いもの〔利用者の入力は扱わない〕。
  ```js
  // 「戻る」で覚えていた画面が出たとき、同じ URL を GET で開き直す（設計書 7.10、Q8-26）
  window.addEventListener('pageshow', function (event) {
      if (event.persisted) {
          window.location.replace(window.location.href);
      }
  });
  ```
- [x] `fragments/common.html` の `head` に、`<script defer th:src="@{/js/back-reload.js}"></script>` を足す〔HTML の中に直接は書かない。削除の確認の `onsubmit` は変えない〕
- [x] `SecurityConfig` の `permitAll` に `"/js/**"` を足す〔コメントの「ログインなしで開けるのは…」も直す〕

**テスト**
- [x] `SecurityBehaviorTest`：`publicUrlsAreOpen` に `/js/back-reload.js` を足す〔未ログインで 200〕。ファイルの中身に `pageshow`、`persisted`、`replace(location.href)` が含まれ、`reload(` がないこと
- [x] 画面に `src="/js/back-reload.js"` の script タグがあること：ログイン画面、登録画面、ログイン中の画面〔取引一覧など〕、エラー画面〔404〕。HTML の中に、`src` のない `<script>` がないこと
- [x] `.\mvnw test` が全部成功する〔**1241 件**成功、失敗 0。既存の削除の確認のテストは変えない〕

**ブラウザでの確認**〔あなたが行う。手順は、コードのあとに示す〕
- [x] PC のブラウザ〔F2 の記録のブラウザ〕：ログアウト → 「戻る」で、取引一覧が**残らない**〔ログイン画面になる〕
- [ ] iPad の Safari：**Phase 9 の面接前のリハーサルでまとめて行う**〔あなたの決定。2026年11月2日。Phase 9 への引き継ぎ（8）に書いた〕。同じ。**一瞬古い画面が出ても、そのあとログイン画面になる**ことが、今と変わらないか、よくなるかを見る
- [x] ログアウトしていないときの「戻る」：画面が読み直されて、問題なく表示される。入力エラーの画面〔入力エラーのあと別の画面へ移り、「戻る」で戻る〕で、再送信の確認が出ないか〔Q8-26〕
- [x] ログインなしで開く画面〔ログイン、登録〕で、`/js/back-reload.js` が 200 で読み込まれる〔開発者ツールの「ネットワーク」〕
- [x] 画面の見た目、削除の確認ダイアログが、今までどおり

（実施の記録・Q8-26 の確認：POST の結果を画面で返す URL を、同じ URL の GET で開いたときの動き〔Claude Code が調べた。GET の動きは一時的なテストで確かめ、そのテストは削除した〕
- 入力エラーで出し直す画面：`POST /register`〔GET は 200〕、`POST /categories`・`/vendors`・`/payment-methods`〔GET は 200 の一覧〕、`POST /categories|vendors|payment-methods/{id}/edit`〔GET は 200 の変更画面〕、`POST /transactions/new`・`/transactions/{id}/edit`〔GET は 200 の入力画面〕、`POST /budgets?year=…&season=…`〔問い合わせの文字列が付いたままなので、GET は同じ季節の画面〕。**すべて 405・404 にならない**。
- 問題の可能性があるもの：**POST だけで開ける URL〔削除 `POST …/{id}/delete`、`POST /logout`〕が、エラー画面〔403・404〕を返したとき**。その画面を覚えていて「戻る」で開き直すと、`GET …/delete` は **405**〔その他の 4xx の画面〕、`GET /logout` は **404** になる。ふだんの削除・ログアウトは移動〔リダイレクト〕で終わるので、この画面が出るのは CSRF のトークンがない、他人の ID、などの異常のときだけ。どちらもエラー画面から別のエラー画面になるだけで、データは出ない。**直さずに記録する**〔設計書 7.10 の限界に書いた〕。
- そのほか：ログインの失敗は `GET /login?error`〔移動〕、ログアウトは `GET /login?logout`〔移動〕で、どちらも GET。

（実施の記録・ブラウザでの確認〔2026年11月2日。あなたが PC のブラウザで行い、**OK**〕
1. ログアウトのあと「戻る」を押すと、ログイン画面が出た〔取引一覧は残らない〕。
2. ログイン中に「戻る」を押すと、ふつうに前の画面が出た。
3. 入力エラーの画面へ「戻る」と、「フォームを再送信しますか」は出なかった〔Q8-26 の B のとおり〕。
4. 削除の確認ダイアログは、今までどおり出た。
- iPad の Safari の確認は、**Phase 9 の面接前のリハーサルでまとめて行う**。
- ログイン画面と登録画面で、`/js/back-reload.js` が 200 で読み込まれた〔開発者ツールの「ネットワーク」〕。

（実施の記録：コード・テストは 2026年11月2日。`.\mvnw test` は 1241 件成功〔1235 件 + 6 件：未ログインで `/js/back-reload.js` が 200、JS の中身、ログイン・登録・予実比較・404 画面の script タグと「HTML の中に直接書いた script がない」こと〕。404 の画面は、MockMvc では未ログインの `/nope` の本文が空のため、予実比較の範囲外の年で確かめた。ブラウザでの確認は、あなたが行う。）

### 4.17 片付け（Q8-4）

- [ ] 確認のために作った取引、一時的な科目・取引先・支払方法（`T1科目`、`T2科目`、`<b>x</b>`、`x' OR 1=1` など）を、画面から削除する。`test1`・`test2` の予算の行は残す（消せない）
- [ ] 新しいアカウント（`chkzero` など）をどうするか、Q8-4 の決定に従う（残す／SQL で消す）。消すとき：そのユーザーの `budgets`、`transactions`、`categories`、`vendors`、`payment_methods` を先に消し、最後に `users` の行を消す（外部キーのため。あなたが実行する SQL を、そのとき示す）
- [x] ファイアウォールの許可ルール `HouseholdBudget-8080-temp` が残っていない（4.13 の手順 I）。**Java の広い許可ルール 12 個は、消さずに無効にした**〔戻すときは `Enable-NetFirewallRule`。4.13 の記録〕。モバイル ホットスポット・スマホのテザリングを使ったときは、オフにした
- [x] ネットワークの種類（`Private`／`Public`）を、4.13 で変える前の状態に戻した（`Wi-Fi 2` を `Public` に戻した。4.13 の手順 J）
- [ ] **片付けのあとの確認（この順に行う）**〔2026年11月3日。あなたの決定。4.14 の「テストのあと DB に行が残っていない」の確認も、ここで行う〕：
  1. **件数の SQL**〔4.1 の 2 つ目の SQL。読むだけ〕を実行し、結果を控える。期待：4.1 で控えた値〔`users_rows` 2、`categories_rows` 6、`vendors_rows` 6、`payment_methods_rows` 4、`budgets_rows` 9、`transactions_rows` 0〕と一致する〔片付けの方法による。Q8-4〕。`users` も、`test1`・`test2` の 2 行だけ〔1 つ目の SQL〕
  2. **`.\mvnw test`** を実行する。全部成功する〔件数を記録する〕
  3. **もう一度、件数の SQL**〔同じもの〕を実行する。期待：手順 1 の結果と**同じ**〔テストが行を残さない〕
- [ ] 手順 1 と 3 の件数が一致した〔違ったら、どの表が増えたかを教えてください〕

### 4.18 文書に反映する

設計書が確定したあとの変更は、先に設計書を直す（Phase 1 の決まり）。

| 直す場所 | 書くこと |
|---|---|
| 設計書 4.4.1 | issue 001 の決定（A・C：`@RequestMapping` の読み方、B：まとめない理由を1行）|
| 設計書 7.11、8章 | 確認で、設計と違った点、新しく出た未決事項があれば（なければ変えない） |
| 要件定義書・設計書 | 確認で見つかった**仕様の変更**があれば、先に直す（要件定義書は、直す前と後の文を見せる） |
| `docs/issues/` | issue 001 の対応状況。新しい issue（004〜） |
| README.md | 確認で、使い方の説明とずれた点があれば（仕上げは Phase 9） |
| README.md（Phase 9）/ 面接の準備 | 4.13 で分かった、iPad から開く手順（IP アドレスの調べ方、ファイアウォールの許可と解除、ホットスポット）。Phase 9 の README か運用メモに載せるかは、Phase 9 で決める |
| 実行計画書 | 進捗表で Phase 8 を ☑ にし、Phase 8 の節を概要とリンクだけに縮める。Phase 9 に引き継ぐことを反映する（8章） |

- [ ] 文書が、実際の状態と合っている

### 4.19 コミット前の確認と、コミット・push

- [ ] `git status` で、含めたくないファイル（`target/`、`.env`、ダンプ、`application-local.properties`）が入っていない（7.9）
- [ ] パスワード・実際の接続先・確認用アカウントのパスワードが、コード・設定・テスト・ドキュメントのどこにも書かれていない。次で確かめる：

```powershell
git grep -n -i -e password -- . ":(exclude)docs" ":(exclude)mvnw" ":(exclude)mvnw.cmd"
```

  期待：Phase 7 のタグ（`phase-7-done`）の時点から**増えていない**こと（ダミーのハッシュを増やした場合は、何の行かを一覧にする）
- [ ] 1つのタスクごとにコミットする（例：issue 001 の処理、整理、テストの追加、不具合の修正、文書）
- [ ] `git push` する

### 4.20 「今の季節」に戻るボタンを足す（issue 007。Phase 8 の中で、4.10 のあと・4.14 の前に行う）

[issue 007](../issues/007-今の季節に戻るリンク.md)。予算設定（`/budgets`）と予実比較（`/comparison`）の、見出しの行に「今の季節」ボタンを足す。**表示だけの追加**で、Controller・Service・`SeasonPeriod.resolve` は変えない。決定（2026年11月2日）：ラベルは「今の季節」、置く場所は「前の季節」「次の季節」と同じ行の真ん中、見た目は前後のリンクと同じボタン。今の季節を見ているときも、範囲の端でも出す。

**文書（先に行う）**
- [x] 要件定義書：直さない〔4.3・4.4 の「季節を指定せずに開いたときは今の季節」の別の入り口でしかない。あなたの確認済み〕
- [x] 設計書：2.3〔予算設定・予実比較の「表示」の行、保存していない入力の文〕、2.4〔「切り替え」の説明と「今の季節」ボタンの bullet〕、4.4.2〔`fragments/season.html` の説明〕を直した〔2026年11月3日〕
- [x] この手順を、あなたが確認した

**コード**（確認のあとに行う）
- [x] `fragments/season.html`：見出しの行の真ん中に、`<a class="btn btn-outline-secondary btn-sm" th:href="@{${path}}">今の季節</a>` を足す。行き先は `path` のまま〔`/budgets`、`/comparison`。`year`・`season` を付けない〕。範囲の端でも、今の季節を見ているときも出す〔`th:if` を付けない〕。**見出しとボタンの並び**〔見出しの右に同じ行、または見出しの下〕は、画面で見て決める。折り返しても崩れないこと〔iPad の縦〕。部品の引数は増えない
- [x] 必要なら `style.css` に最小限の指定を足す〔Bootstrap のクラスで足りれば足さない〕
- [x] コメント〔`season.html` の先頭〕に、「今の季節」ボタンのことを書く

**テスト**（先に書く。`SeasonSelectAssertions` に、確認の部品を足す）
- [x] `SeasonSelectAssertions`：「今の季節」のリンクがあり、`href` が季節を指定しない URL〔`/budgets`、`/comparison`。`?` を含まない〕であること
- [x] `BudgetControllerTest`・`ComparisonControllerTest`：季節の指定なし、ほかの季節、範囲の端〔2000年春、2100年冬〕で、リンクが出る
- [x] 既存のテストを壊さない：メニューの `<a href="/comparison">予実比較</a>`、`href="/budgets">予算`、前後リンクの有無〔端で「前の季節」「次の季節」が出ないこと〕、`class="field-error"` など
- [x] `.\mvnw test` が全部成功する〔件数を記録する〕

**ブラウザでの確認**（あなたが行う。コードのあとに手順を示す）
- [x] 予算設定と予実比較で、遠い季節〔例：2030年夏〕から「今の季節」を押すと、今の季節に戻る
- [x] 今の季節で押しても問題ない〔同じ画面が開き直る〕
- [ ] 範囲の端〔2000年春、2100年冬〕でも出る
- [x] 画面の幅を狭くしても〔PC のブラウザの幅を狭める〕、崩れない。iPad の縦・横は Phase 9 のリハーサルで確かめる
- [x] 予算設定で、入力途中の値は、押すと確認なしで捨てられる〔前後リンクと同じ〕

（実施の記録・コードとテスト：2026年11月3日。
- **テストを先に書いて、失敗を確かめた**：`SeasonSelectAssertions.assertCurrentSeasonLink`〔リンクが 1 つだけ、`href` が `/budgets`・`/comparison`〔`?` を含まない〕、前後のリンクと同じ `class="btn btn-outline-secondary btn-sm"`、「前の季節」より後・「次の季節」より前〕と、`BudgetControllerTest` 7 件・`ComparisonControllerTest` 7 件〔指定なし・今の季節・ほかの季節・2027年冬、範囲の端 2000年春・2100年冬、科目が 0 件〕。**14 件が失敗**した〔1241 → 1255 件のうち 14 件〕。
- **直したもの**：`fragments/season.html`〔見出しを `<div class="season-center">` で包み、見出しの下に「今の季節」のボタンを足した。`th:if` なし〕、`style.css`〔`.season-nav .season-center`：縦に並べて中央〕。見出しとボタンの並びは「見出しの下」にした。見え方は、あなたのブラウザでの確認で見て、直すなら言ってください。
- **`.\mvnw test` は 1255 件成功**、失敗 0。既存のテストは変えていない。
- ブラウザでの確認は、あなたが行う。）

（実施の記録・4.20 のブラウザでの確認：2026年11月3日。あなたが予算設定・予実比較の**両方**で行い、**すべて OK**。ほかの季節から「今の季節」を押すと今の季節に戻る／今の季節で押しても問題ない／予算設定で保存していない入力は確認なしで捨てられる／画面の幅を狭くしても崩れない。**見出しの下に縦に並べる形で OK**〔あなたの確認〕。「範囲の端〔2000年春、2100年冬〕でも出る」は、報告になかったのでブラウザのチェックは付けていない〔自動テストで確かめてある：`BudgetControllerTest.currentSeasonLinkIsShownAtTheEdges`、`ComparisonControllerTest.currentSeasonLinkIsShownAtTheEdges`〕。iPad の縦・横は Phase 9 のリハーサルで確かめる。）

### 4.21 予算内の行に 💮 を出す（issue 005。Phase 8 の中で、4.20 のあと・4.14 の前に行う）

[issue 005](../issues/005-予算内の行に花丸を出す.md)。予実比較の表の右端に、予算内の行の 💮 を出す。**表示だけ**で、赤・青の決まりは変えない。決定は Q8-27〜Q8-35、すべて A：合計行にも付ける／未設定・実績 0円の行には付けない／実績が予算ちょうどの行にも付ける／新しい右端の列／読み上げ用の文字を添える／注記に足す／`BudgetColorJudge.isWithinBudget` を足す／絵文字そのまま。

**文書（先に行う）**
- [x] 要件定義書：3章〔予実比較の一覧の文〕と 4.4〔💮 の決まりを 1 つ〕を直した〔2026年11月3日。前後の文は、あなたが確認済み〕
- [x] 設計書：2.3〔表の列、表示の行、注記〕、4.4.2〔`comparison/index.html`〕、4.5〔`BudgetColor`、`BudgetColorJudge.isWithinBudget`〕、`ComparisonRow` の形、5.9〔図、判定の例の表〕、P13、F5 を直した〔2026年11月3日〕
- [x] この手順を、あなたが確認した

**コード**（確認のあとに行う。テストを先に書く）
1. [x] `BudgetColorJudgeTest`：`isWithinBudget` の表〔設計書 5.9 の 9 行〕と境界値を書く。未設定、予算 0円、実績 0円、ちょうど、実績の 3倍が予算ちょうど〔青で 💮 なし〕、1 円多い・1 円少ない、大きな値〔`long` で計算し、`int` の範囲を超えても正しい〕。**失敗する**ことを確かめる〔メソッドがまだない〕
2. [x] `BudgetColorJudge.isWithinBudget(Long budget, long actual)` を足す〔`budget != null && budget > 0 && actual > 0 && actual <= budget && actual * 3 > budget`〕。`judge` と `BudgetColor` は変えない
3. [x] `ComparisonRow` に `boolean withinBudget` を足す。`ComparisonService.row()` が `isWithinBudget` を呼ぶ〔科目の行も合計行も同じ`row()`〕。`ComparisonServiceTest`：行と合計行の `withinBudget`
4. [x] `comparison/index.html`：表に 5 列目を足す。見出しは `<th><span class="visually-hidden">評価</span></th>`。各行・合計行のセルに、`withinBudget` のときだけ `<span aria-hidden="true">💮</span><span class="visually-hidden">予算内</span>`。既存の 3 つの `<td class="num">` と `<tr class="row-over">` などは**変えない**。注記を「赤：予算超過／青：予算の1/3以下／💮：予算内（予算の1/3を超え、予算以下）」にする。科目が 0 件のときは表が出ないのは今のまま
5. [x] `ComparisonControllerTest`：💮 の行にだけ出る〔色なしの予算内・ちょうど・合計行〕、赤・青・未設定・実績 0円・予算 0円の行には出ない、読み上げ用の文字〔`aria-hidden`、`visually-hidden`、見出しの「評価」〕、注記の新しい文字列。**既存の期待〔`<td class="num">…</td>`、`<tr class="row-over">` など〕を変えない**
6. [x] `.\mvnw test` が全部成功する〔件数を記録する〕

**ブラウザでの確認**（あなたが行う。コードのあとに手順を示す）
- [x] 1 つの画面に、赤・青・💮・色なし〔未設定、実績 0円〕の行がそろい、💮 が右端の列に出る
- [x] 実績が予算ちょうどの行、合計行にも出る
- [x] 💮 が絵文字として表示される〔PC。四角や空白になっていない〕。iPad の Safari は Phase 9 のリハーサルで確かめる
- [x] 注記の文が出る。表が幅の狭い画面で横に動く〔`table-responsive`〕

**Phase 9 への引き継ぎ**：画面の画像は、4.20・4.21 のあとに撮る〔「今の季節」ボタンと 💮 が写る〕。iPad の Safari での 💮 の表示を、リハーサルで確かめる。

（実施の記録・4.21 のブラウザでの確認：2026年11月3日。あなたが行い、**OK**。交通費 25,000円に実績 10,000円で 💮 が出る／ほかの行には出ない／注記／絵文字の表示〔PC〕／幅を狭めても崩れない。「実績が予算ちょうどの行、合計行にも出る」は、報告に明記がなかったので、ブラウザのチェックは付けていない〔自動テストでは確かめてある〕。iPad の Safari の表示は Phase 9 のリハーサル。）

（実施の記録・コードとテスト：2026年11月3日。
- **テストを先に書いて、失敗を確かめた**：`BudgetColorJudgeTest`〔39 件が、メソッドがなくて失敗〕、`ComparisonServiceTest` 2 件、`ComparisonControllerTest` 6 件〔このうち、実装前に失敗したのは 5 件。`noMarkWhenNothingIsWithinBudget` は「出ない」ことの確認なので、実装前も通る〕。
- **実装**：`BudgetColorJudge.isWithinBudget(Long, long)`、`ComparisonRow` に `boolean withinBudget`、`ComparisonService.row()` が呼ぶ〔科目の行も合計行も同じ`row()`〕、`comparison/index.html`〔5 列目：見出し `<th><span class="visually-hidden">評価</span></th>`、各行・合計行の `<td class="text-center">` に、`withinBudget` のときだけ `<span aria-hidden="true">💮</span><span class="visually-hidden">予算内</span>`。注記を新しい文に〕。`BudgetColor`・`judge`・既存の 4 つのセルは変えていない。
- **`.\mvnw test` は 1302 件成功**〔1255 → 1302。47 件増：判定 39、サービス 2、画面 6〕、失敗 0。既存のテストは変えていない〔`everyRow…` などの新しいテストの途中で、`<th` を数える範囲に `<thead>` が入っていた書き間違いだけ直した〕。
- ブラウザでの確認は、あなたが行う。）

#### 4.21 の続き：💮 の行を目立たせる（背景を薄い緑に、💮 を大きく。Q8-36〜Q8-41）

4.21 のブラウザ確認は OK〔2026年11月3日。交通費 25,000円・実績 10,000円で 💮、ほかの行には出ない、注記、絵文字の表示、幅を狭めても崩れない〕。そのうえで、**予算内に収まったことがひと目で分かる**ように、次の 2 つを足す〔あなたの要望。2026年11月3日〕。

- 💮 の行〔合計行も〕の**背景を薄い緑**にする。
- 右端の **💮 を今より大きく**する。

**決定**：Q8-36〜Q8-41、すべて A〔2026年11月3日〕。要件定義書の直し〔①3章、②4.4〕は案のとおりで、あなたが確認済み。

**文書（Q8-36〜Q8-41 が決まってから行う）**
- [x] 要件定義書：3章の予実比較の一覧の文と、4.4 の 💮 の文に、背景のことを足す〔Q8-41 が A のとき。前後の文は報告に書いた〕
- [x] 設計書：2.3〔💮 の行の説明に、背景と大きさ〕、4.4.2〔`comparison/index.html` の説明に `row-within`。クラスの付け方の注意に `<tr class="row-within">` を足す〕、5.9〔「色を付ける場所」の bullet〕、F5〔背景と大きさ〕、P13〔CSS のクラス名に置き換えるだけ、に `row-within` を加える〕、`style.css` の説明があれば。**大きさの値は設計書だけ**に書く
- [x] この手順を、あなたが確認した

**コード**（確認のあとに行う）
- [x] `comparison/index.html`：`<tr>` の `th:classappend` に、`withinBudget` のときの `row-within` を足す〔赤・青と同時にはならない。合計行は `total-row row-within`〕。右端のセルのクラスに `within-mark` を足す〔`text-center within-mark`。絵文字の `<span>` の形は変えない〕
- [x] `style.css`：次の 2 つの規則を足す〔値は Q8-36〜Q8-38 のおすすめの場合。決定で変わる〕。先頭のコメント〔テストが確かめるクラスの一覧〕に `row-within` を加える。
  ```css
  /* 予算内の行（💮。設計書 2.3、F5）：薄い緑の背景。赤・青は文字の色なので、重ならない。Bootstrap の表の変数を上書きするので、hover の色や合計行の線は残る */
  .comparison-table tr.row-within {
      --bs-table-bg: #e3f8e9;
  }

  /* 右端の 💮 を大きくする（絵文字の span の形は変えない） */
  .comparison-table td.within-mark {
      font-size: 1.5em;
      line-height: 1;
  }
  ```

**テスト**（先に書く。`ComparisonControllerTest`）
- [x] 💮 の行〔科目の行、合計行〕が `<tr class="row-within">`・`<tr class="total-row row-within">` で始まる
- [x] 赤・青・予算が未設定・実績 0円・予算 0円の行には、`row-within` がない〔`startsWith("<tr class=\"row-over\">")` などは今のまま〕
- [x] **直す既存のテスト**〔Q8-39 の A。2 か所〕：`colorClassIsOnTheWholeRow` の ①`ふつう`〔色なしで 💮 の行〕`startsWith("<tr>")` → `startsWith("<tr class=\"row-within\">")`、②合計行 `startsWith("<tr class=\"total-row\">")` → `startsWith("<tr class=\"total-row row-within\">")`。テストの説明のコメントも「色なし〔予算内〕」に直す。ほかの `row-` を確かめるテスト〔未設定の行に `doesNotContain("row-")`〕は、そのまま通る
- [x] 右端のセルが `class="text-center within-mark"` である〔印のある行も、ない行も〕
- [x] `style.css` に、`tr.row-within` と `td.within-mark` の規則がある〔`/css/style.css` を読んで、クラス名の食い違いに気づけるように。値は確かめない〕
- [x] `.\mvnw test` が全部成功する〔件数を記録する〕

**ブラウザでの確認**（あなたが行う。コードのあとに手順を示す）
- [x] 💮 の行〔合計行も〕の背景が薄い緑で、文字が読みやすい
- [x] 赤・青の行の文字の色、合計行の上の線、行にマウスを載せたときの色が、今までどおり
- [x] 右端の 💮 が大きくなり、行の高さが極端に変わらない
- [x] 幅を狭くしても崩れない。iPad の見え方は Phase 9 のリハーサルで確かめる

（実施の記録・コードとテスト：2026年11月3日。
- **文書**：要件定義書〔3章、4.4：「行の背景を薄い緑にする」〕、設計書〔2.3、4.4.2〔`row-within`、`within-mark`、クラスの注意〕、5.9、P13、F5〕を直した。
- **テストを先に書いて、失敗を確かめた**：`colorClassIsOnTheWholeRow`〔直した 2 か所〕、新しい `onlyTheWithinBudgetRowsGetTheRowWithinClass`、`theRightmostCellHasTheMarkClass`、`styleSheetHasTheRulesForTheWithinBudgetRowAndMark` の **4 件が失敗**した。`totalRowKeepsItsColorClassWhenItIsNotWithinBudget`〔合計行が赤・青のときは `row-within` が付かない〕は、動きが変わらない確認なので実装前も通る。
- **実装**：`comparison/index.html`〔`th:classappend` に `row-within`、右端のセルを `text-center within-mark`〕、`style.css`〔`.comparison-table tr.row-within { --bs-table-bg: var(--bs-success-bg-subtle); }`、`.comparison-table td.within-mark { font-size: 1.5em; line-height: 1; }`、冒頭のクラスの一覧に `row-within`〕。
- **背景の色の値を変えた**〔2026年11月3日〕：ブラウザの確認で、もう少し薄く明るくしたいとなり、開発者ツールで見比べて **`#e3f8e9`** に決めた〔Q8-36 を A から変更〕。`style.css` の `--bs-table-bg` を `#e3f8e9` の直接指定にした〔Bootstrap の変数 `--bs-success-bg-subtle` は使わない〕。設計書 2.3・5.9 を直した。文字の明るさの比：`#e3f8e9` と `#212529` は約 **13.9 対 1**〔以前の `#d1e7dd` は約 11.9 対 1。読みやすさの目安の 4.5 対 1 を大きく超える〕。`#e3f8e9` と白〔ページの背景〕は約 1.11 対 1 で、背景の区別はかなり弱い〔あなたが画面で見て決めた。iPad の明るい画面での見え方は Phase 9 で確かめる〕。テストは、色の値も `--bs-success-bg-subtle` も確かめていないので、直していない〔`--bs-table-bg` と `font-size` の規則があることだけ確かめている〕。
- **`.\mvnw test` は 1306 件成功**〔1302 → 1306。新しいテスト 4 件の増。既存の 2 か所は期待を直した〕、失敗 0。
- ブラウザでの確認は、あなたが行う〔「予算ちょうど・合計行にも出る」も一緒に確かめる〕。）

（実施の記録・4.21 の続きのブラウザでの確認：2026年11月3日。あなたが行い、**すべて OK**。薄い緑の背景と大きい 💮／赤・青の文字の色／合計行の線／マウスを載せたときの色／予算ちょうどの行と合計行にも 💮／25,001円で赤に変わる〔4.21 の「予算ちょうど・合計行にも出る」の項目も、これで確認できた〕。「幅を狭くしても崩れない」は、のちに確認した〔下〕。背景の色は、この確認のあとで `#e3f8e9` に直した〔上の記録〕。直したあとの見え方は、あなたの画面で確認済み〔開発者ツールで見比べて決めた〕。iPad の見え方は Phase 9 のリハーサル。）

（実施の記録・幅を狭くした確認：2026年11月3日。あなたが行い、**OK**。予実比較の表は崩れず、右端の 💮 の列も見えていた〔`table-responsive`〕。iPad の縦・横は Phase 9 のリハーサル。）

## 5. 決まっていない点（設計書だけでは決まらないこと）

設計書とあなたの指示だけでは決まらない点です。**決まるまで、該当する手順は進めません。** 「あなたの決定」欄に書き込んでください。

| 番号 | 決めること | 選択肢 | おすすめ | あなたの決定 |
|---|---|---|---|---|
| Q8-1 | **issue 001：Controller の URL を、クラスの `@RequestMapping` にまとめるか**（4.2。動きは変わらない改善の案） | A：直す。6つの Controller（科目・取引先・支払方法・取引・予算・予実比較）をすべて、クラスの `@RequestMapping` にまとめる（メソッドの注釈25か所、約31行。テストとテンプレートは変えない）<br>B：直さない。理由（設計書 2.2 の URL の一覧と見比べやすい）を設計書 4.4.1 に1行残し、issue 001 を「対応しない」にする（文書だけ）<br>C：お知らせが出た3つ（科目・取引先・支払方法）だけ直す（15か所。書き方がそろわなくなる） | **B**（issue 001 に書いたとおり、今の書き方は URL の全体がそのまま見えて、設計書の一覧と見比べやすい。動きは変わらず、直さなくても正しく動く。最後の Phase で、動いているコードを6ファイル触るメリットが小さい。C は書き方がそろわなくなるので避ける。A にしたいなら、テスト1205件が守ってくれるので、リスクは小さい） | ☑ **A**（6つの Controller すべて、クラスの `@RequestMapping` にまとめる）。**おすすめ（B）とは違う。** 理由：Spring でふつうの書き方で、コードを読む人にとって読みやすい。URL が変わらないことは、既存の Controller のテスト（`…ControllerTest`）で確かめられる。作業は 4.2 で行う |
| Q8-2 | **確認の結果を、どこに記録するか**（3.4。実行計画書の成果物「確認結果の記録（チェックリスト）」） | A：この手順書のチェックと、各節の末尾の「実施の記録」に書く（Phase 4〜7 と同じ）<br>B：`docs/reviews/phase-8-review.md` を新しく作り、確認表をそこに書く（手順書は手順だけにする） | **A**（実行計画書 ルール6：各 Phase の確認結果は `plans` の Phase ファイルに書く。Phase 7 までと同じで、手順と結果が1か所で読める） | ☑ **A**（おすすめどおり） |
| Q8-3 | **科目が0件のアカウントと、256個の上限の確認に、どのアカウントを使うか**（4.9、4.10。0件にするには、予算を0円にして、科目を削除する） | A：新しいアカウントを1つ作る（名前の案 `chkzero`）<br>B：`test2` の科目を削除して0件にする | **A**（`test1`・`test2` は、データ分離の確認〔4.6 の(6)〕の相手として、科目・予算を残したい。256個の科目を足すのも、確認専用のアカウントのほうが後始末しやすい。新規登録の初期データの確認〔4.3〕も同時にできる） | ☑ **A**（おすすめどおり） |
| Q8-4 | **確認のために作ったアカウントとデータを、最後にどうするか**（4.17。アカウントを消す画面は無い） | A：取引と一時的な科目などは画面から削除する。確認用の新しいアカウントは、SQL（あなたが実行）で関連する行ごと消す。`test1`・`test2` の予算9行は残す<br>B：取引と一時的な科目などだけ片付け、新しいアカウントは残す（Phase 9 の確認でも使える。ただし、`users` に1行増える）<br>C：データベースを作り直す（`root` で。`test1`・`test2` も消えるので、Phase 9 の確認のとき登録し直す） | **A**（Phase 7 の終わりと同じ状態〔ユーザー2人、取引0件、予算9行〕に戻せて、Phase 9 の「まっさらな環境」の確認の前提が崩れない。SQL は実行前に読むだけで確かめ、あなたが実行する） | ☑ **A**（おすすめどおり） |
| Q8-5 | **DB を止めて 500 の画面を確かめるか**（4.6 の(7)。設計書 7.11：「DB を止めた状態で画面を開き、詳しい中身が出ないこと」。Phase 3 で一度確かめた） | A：確かめる。MySQL のサービス（`MySQL97`）を止めて画面を開き、画面と状態コードを見て、起動し直す（**あなたが行う**。管理者の権限が要る）<br>B：確かめない（Phase 3 で確認済み） | **A**（Phase 5 で `open-in-view=false` に変えた。実行計画書が「D5 の結果で、エラー画面〔5xx〕の確認に影響がないかを見る」と書いている。数分で済む。止めたあとは、サービスを起動し直し、アプリを再起動して、元に戻ったことを確かめる） | ☑ **A**（おすすめどおり） |
| Q8-6 | **自動テストを、どこまで整えるか**（4.14。実行計画書：「主要な処理の自動テストを整える〔登録時の初期データ、データ分離、予実比較〕」。すでに1205件ある） | A：要件との対応表を作り、**足りないものだけ**を足す（足す前に一覧にして報告する）<br>B：A に加えて、画面を通した通しのテスト（登録 → ログイン → 取引 → 予算 → 予実比較）を新しく書く<br>C：足さない（すでに十分とみなす） | **A**（各 Phase で、機能ごとにテストを先に書いて作ってきた。足りない所だけ足せば十分。B は、同じ内容を重ねて書くことになり、メンテナンスが増える。C は、一覧を作らずに「十分」と決めることになる） | ☑ **A**（おすすめどおり） |
| Q8-7 | **テストのログに出る警告を、どうするか**（4.15）<br>① `HV000271: Using @Valid on a container (java.util.List) is deprecated`（`BudgetForm.rows`）<br>② Mockito の「self-attaching」警告（将来の JDK で動かなくなる） | A：①だけ直す（`List<@Valid Row>` の形に。動きは変わらない。`BudgetFormTest`・`BudgetControllerTest` で確かめる）。②は直さず、記録する<br>B：①と②の両方を直す（②は `pom.xml` のテストの実行設定を変える）<br>C：どちらも直さず、記録する | **A**（①は、将来の Hibernate Validator で動かなくなるかもしれない書き方で、直す範囲が1か所。②は、今の JDK 25 では動いていて、直すには `pom.xml` のテスト設定を変える必要があり、効果に比べて変更が大きい） | ☑ **A**（おすすめどおり） |
| Q8-8 | **コードの整理（使っていないコードの削除、分かりにくい名前、コメント）の進め方**（4.15） | A：先に**一覧にして報告**し、あなたが「直す」と決めたものだけ直す<br>B：使っていないコードの削除と、古くなったコメントの修正は、Claude Code が直して報告する。名前の変更は一覧にして確認する<br>C：整理はしない | **A**（CLAUDE.md：頼まれていない変更はしない。名前や削除の変更は、仕様駆動の手順書や設計書の記述とずれるおそれがある。件数は多くないので、一覧を見て決めるほうが安全） | ☑ **A**（おすすめどおり） |
| Q8-9 | **古い下限（2000-01-01）が書かれた過去の文書を、どうするか**（R15 のあと。`phase-1-review.md`、`phase-1-design.md`、`phase-5-transactions.md` に、当時の決定として残っている） | A：直さない。当時の決定の記録なので、そのまま残す（設計書・要件定義書・README が現在の決まりを示している）<br>B：各ファイルの該当箇所に「※のちに R15 で 2000-03-01 に変更」と注記を足す | **A**（過去の Phase の記録は、当時の状態を残すもの。Phase 7 で、直さないと決めてある。読む人が迷わないよう、現在の決まりは設計書 2.4・6.6 にある。注記を足すなら B） | ☑ **A**（おすすめどおり） |
| Q8-10 | **256個の上限と、256行の予算設定を、ブラウザで確かめる方法**（4.10。画面から256個を足すのは手間が大きい） | A：確認用の新しいアカウントに、**SQL で科目を足して**256個にし、画面で確かめる（SQL は私が示し、あなたが実行する。読むだけの確認用の SQL を先に実行して、足す行数を確かめる）<br>B：ブラウザでは行わず、自動テスト（`MasterLimitControllerTest`、`MasterLimitServiceTest`）だけで確認とする<br>C：画面から、手で256個まで追加する | **A**（256行の予算設定が保存できること、257個目が追加できないこと、256行の予実比較が 500 にならないことは、画面でしか確かめられない。SQL で足すのは確認用のアカウントだけで、片付けも SQL でできる。C は現実的でない） | ☑ **A**（おすすめどおり） |
| Q8-11 | **同時保存のとき（予算の一意制約に拒否されたとき）の動きを、確かめるか**（Phase 6 の Q6-6 の A：500 番台の画面になり、二重には保存されない。引き継ぎ項目） | A：再現はしない。DB に一意制約 `uk_budgets_category_season` があることを SQL で確かめ、「同時の再現は行わない」と記録する（保存ボタンを素早く2回押す確認は、4.8 で行う）<br>B：2つの処理が同時に保存するテストを新しく書く（スレッドを使う自動テスト）<br>C：2つのブラウザのタブで、できるだけ同時に保存して試す | **A**（ほぼ同時の送信は、手では再現しにくく、B のテストは作りが重くなる。「最後の守り」が DB の制約として働くことは、SQL で確かめられる。まれにしか起きず、起きても二重には保存されない） | ☑ **A**（おすすめどおり） |
| Q8-12 | **iPad で見にくい・操作しにくい所が見つかったとき、どうするか**（4.13。今の画面は、スマホ・タブレット向けには作っていない。要件定義書 9章「スマホ向けの画面」） | A：記録だけして、直さない（README の「既知の制限」に載せるかは Phase 9 で決める）<br>B：**操作できない・読めない**重大な崩れだけ、CSS で直す（HTML の構造・機能は変えない。直す前に一覧にして報告する）。見た目の好みは直さない<br>C：iPad 向けに全画面を整える（画面の幅による切り替えなど。要件 9章の「スマホ向けの画面」に近い作業で、Phase 9 のあとの仕事になる） | **B**（面接で iPad から触ってもらうので、ボタンが押せない・文字が読めないと困る。CSS だけで機能は変わらず、自動テスト〔1205件〕で守れる。C は要件の範囲を広げることになるので、今はしない） | ☑ **B**（おすすめどおり。操作できない・読めない重大な崩れだけ、CSS で直す）|
| Q8-13 | **面接会場の Wi-Fi で開けないときの、代わりの方法を手順書に載せるか**（4.13。会場の Wi-Fi は、端末どうしの通信を禁止している〔ゲスト用など〕ことがあり、その場合は IP アドレスで開けない） | A：載せない。同じネットワークで開けるかだけ確かめる<br>B：Windows の「モバイル ホットスポット」（パソコンを Wi-Fi の親機にして iPad をつなぐ）の手順を、4.13 に載せる。試すのはあなた<br>C：B に加えて、携帯のテザリングや持ち運びのルーターも載せる | **B**（会場のネットワークは事前に分からず、当日つながらないのが一番困る。ホットスポットはパソコン1台で済み、会場の設定に左右されない。手順は4.13 に短く書いてある〔Q8-13 の決定による〕。C は機器を増やすので、今は載せない） | ☑ **C**（ホットスポットに加えて、スマホのテザリングに PC と iPad の両方をつなぐ方法も載せる）。**おすすめ（B）とは違う。** 理由：Windows のモバイル ホットスポットは、PC 自身がインターネットにつながっていないと始められない可能性がある（実機で確かめる）。追加：テザリングなど新しいネットワークは、Windows で「パブリック」になりやすい。許可ルールは `Private` に絞っているので、つないだネットワークの種類を確かめて `Private` にする手順と、終わったら戻す手順を、ホットスポットとテザリングの両方に入れる（4.13）|
| Q8-14 | **iPad の確認でログインするアカウント**（4.13。HTTP のため、パスワードが**暗号化されずに**同じネットワークを流れる。Safari に「保護されていない通信」と出るのは、そのため） | A：使い捨てのアカウントを使う（4.9 で作る `chkzero` などを使うか、別に作る）。パスワードは、**ほかのサービスと同じものにしない**。確認のあと、4.17 で片付ける<br>B：`test1` を使う | **A**（暗号化されていない通信に、ふだん使うパスワードを流さないため。`test1`・`test2` は、データ分離の確認〔4.6 の(6)〕に使い、ほかの用途と分けられる。ファイアウォールの許可は、`Private` と `LocalSubnet` に限っているが、同じ Wi-Fi の機器からは見える） | ☑ **A**（おすすめどおり。使い捨てのアカウントを使う）。あわせて、**本番の面接用に、見せるためのデータを入れたデモ用アカウント**を用意することを、Phase 9 への引き継ぎ（8）に書く|
| Q8-15 | **年の選択肢（2000〜2100）を、どこで作るか**（4.11。範囲は `SeasonPeriod.FIRST_YEAR`・`LAST_YEAR` が持つ） | A：テンプレートで `#numbers.sequence(2000, 2100)` と書く。範囲の数字がテンプレートにも出るので、**テストで、最初と最後が `SeasonPeriod` の定数と同じ**ことを確かめて、ずれたら気づけるようにする<br>B：2つの Controller が、年の一覧をモデルに入れて渡す（数字は `SeasonPeriod` の1か所。ただし Controller とそのテストを変える。URL・動きは変わらない） | **A**。Controller を変えずに済む（指示：「受け取りは今のまま」）。範囲は仕様で決まっていて、変わる見込みが小さい。ずれはテストで見つかる | ☑ **A**（おすすめどおり）。テンプレートで `#numbers.sequence(2000, 2100)` と書く。テストで、最初と最後が `SeasonPeriod.FIRST_YEAR`・`LAST_YEAR` と同じことを確かめる |
| Q8-16 | **季節を選ぶフォームの置き場所と、冬の年の説明**（4.11。冬は「12月がある年」を選ぶので、「2026年 冬」は 2026年12月〜2027年2月。見出しには期間が出る） | A：見出し（前・次のリンクと年・季節の名前）の**すぐ下に1行**で置く。フォームの横に小さく「冬は、12月がある年を選びます」と書く<br>B：A と同じ場所。説明は書かない（見出しの期間で分かる）<br>C：表の下など、見出しから離れた場所に置く | **A**。冬だけ年のずれがあり、選んだ結果が見出しの期間（「2026年12月〜2027年2月」）で分かるが、選ぶ前に分かるほうが迷わない。見出しの下なら、季節の操作が1か所にまとまる | ☑ **A**（おすすめどおり）。見出しのすぐ下に1行で置き、「冬は、12月がある年を選びます」と添える |
| Q8-17 | **Bootstrap の入れ方**（4.12） | A：WebJars。`pom.xml` に `org.webjars:bootstrap` を足す。`/webjars/bootstrap/（版）/css/bootstrap.min.css` で読む（版なしの URL にするには、さらに `webjars-locator-lite` が要る。Spring Boot 4.1 で動くかを確かめる必要がある）。`SecurityConfig` に `/webjars/**` を足す<br>B：公式の配布物の `bootstrap.min.css` を `src/main/resources/static/css/` に1ファイル置く。`/css/**` はすでにログイン前から読めるので、`SecurityConfig` は変えない | **B**。①依存関係を増やさない。②WebJars の jar には Bootstrap の JavaScript も入っていて、「JavaScript は入れない」という方針と相性が悪い（使わなくても配られる状態になる）。③`SecurityConfig` を変えない（セキュリティの設定を触らない）。④置くのは1ファイルで、版とライセンスが目で見える。弱み：更新は手作業（Phase 9 までの期間では問題にならない）。Maven の脆弱性チェックの対象にならない点は、CSS だけなので影響が小さい | ☑ **B**（おすすめどおり）。公式の `bootstrap.min.css` を `src/main/resources/static/css/` に1ファイル置く。`pom.xml` と `SecurityConfig` は変えない |
| Q8-18 | **Bootstrap のバージョン**（4.12） | A：5.3 系の、着手時点の**最新の安定版**（調べた範囲では **5.3.8** が最新の可能性が高い。公式サイトと Maven Central / npm で、着手時に確かめる）。版を文書に書く<br>B：5.3 系の少し前の版（5.3.3 など）に固定する<br>C：次の大きな版（6 系）。安定版が出ていれば | **A**。5.3 系は長く使われていて情報が多く、iPad の Safari に対応している。最新の安定版の修正を受けられる。6 系は、安定版かどうかと変更点を確かめる手間が大きく、今回の目的（見た目を整える）に対して大きすぎる。**確認できていない点**：最新版の番号は検索結果が食い違っていて、まだ確定していない（着手時に確かめる） | ☑ **A**（おすすめどおり）。5.3 系の、着手時点の最新の安定版。着手時に、公式と npm で番号を確かめて、文書に書く |
| Q8-19 | **どの画面に、どこまで当てるか**（4.12） | A：**全画面**。共通の枠（メニュー・メッセージ）、ログイン・登録、取引 4 画面、科目・取引先・支払方法 6 画面、予算設定、予実比較、エラー画面 4 種（HTML 19 枚中、部品の `fragments` を含む）<br>B：主要な画面だけ（取引・予算設定・予実比較・共通の枠）。残りは今の見た目<br>C：共通の枠とログインだけ | **A**。画面ごとに見た目が違うと、面接で見せるときに不ぞろいに見える。画面は同じ部品（フォーム、表、ボタン）の繰り返しなので、全画面でも作業は部品ごとの対応表（4.12 の4）にまとまる。範囲を絞ると、境目を作るぶん文書とテストの確認が増える | ☑ **A**（おすすめどおり）。全画面（共通の枠、ログイン・登録、取引、科目・取引先・支払方法、予算設定、予実比較、エラー画面4種） |
| Q8-20 | **既存の `style.css` との分け方、テストが確かめる文字列の守り方**（4.12） | A：クラス名は**残す**。`bootstrap.min.css` を先に、`style.css` を後に読む。Bootstrap と重なる規則は `style.css` から消し、残すのは Bootstrap に無いものと、テストが確かめる文字列のための規則。`<tr>`・`<td class="num">`・`field-error`・メニューの `<a>` には Bootstrap のクラスを**足さない**（`<table>` や周りの要素に付ける）。**テストは変えない**<br>B：Bootstrap のクラスに全面的に置き換える（`num` → `text-end` など）。テストの文字列も書き直す<br>C：A のあとで、特定のクラスだけ、必要ならテストを書き直す（その都度、一覧にして確認） | **A**（C への移りやすさを残す）。「自分の CSS を残す」というご指示に合う。テストを変えなければ、`.\mvnw test` が「見た目の変更で動きが変わっていない」ことの証拠になる。B はテストが多く（`class="num"` など）、動きの変更と見分けにくくなる | ☑ **A**（おすすめどおり）。クラス名は残す。`bootstrap.min.css` の後に `style.css` を読み込む。重なる規則は消す。テストが確かめる文字列の要素には、Bootstrap のクラスを足さない。テストは変えない |
| Q8-21 | **Bootstrap のライセンスの扱い**（4.12。Bootstrap は MIT ライセンス。MIT は、複製物に著作権表示と許諾の文を含めることを条件にしている。**法的な助言ではなく**、一般的な扱いの案） | A：①`bootstrap.min.css` 先頭の著作権表示を**消さない**。②リポジトリ直下に `THIRD-PARTY-NOTICES.md` を作り、名前・版・出どころ・MIT の全文を書く。③README（Phase 9）の使用技術に Bootstrap を書く<br>B：①と③だけ（全文のファイルは作らない）<br>C：書かない | **A**。MIT の条件を満たす最も確実な形で、ファイルが1つ増えるだけ。面接でも「第三者のコードのライセンスを確認した」と説明できる。C は条件を満たさない可能性がある | ☑ **A**（おすすめどおり）。CSS 先頭の著作権表示を消さない。`THIRD-PARTY-NOTICES.md` を作る。README（Phase 9）の使用技術にも書く |
| Q8-22 | **Bootstrap を入れる位置と、確認のやり直し**（4.12。4.5〜4.10 の確認は、見た目が変わる前の画面で行う） | A：ご指示のとおり、4.10 の次（4.11・4.12）に置く。4.6（セキュリティ）・4.7（入力チェック）・4.9・4.10 は動きの確認なので、見た目が変わっても有効。4.12 の最後に、4.5 の画面一覧を**もう一度開く**簡易な確認を入れる（手順に入れてある）<br>B：4.5 の前に移す。確認が1回で済むが、すでに書いた番号が大きく付け替わる | **A**。ご指示の順で、変えるのは HTML のクラスと CSS だけ（`th:`・URL・フォームの項目は変えない）ので、動きの確認をやり直す必要は小さい。心配な所（赤・青の行、フォームの `_csrf`、入力エラーの表示し直し）は、4.12 の確認とテストで見る | ☑ **A**（おすすめどおり）。4.10 の次（4.11・4.12）に置く。4.12 の最後に、4.5 の画面一覧を開き直す |
| Q8-23 | **Bootstrap と、要件定義書 9章「スマホ向けの画面」、Q8-12 の関係**（4.12、4.13） | A：9章は**変えない**。Bootstrap は「見た目を整える」「iPad で見やすくする」ためで、スマホ向けの画面づくりはしない（スマホの幅の確認も、この Phase の対象にしない）。4.13 の iPad の確認で見つかった崩れは、Q8-12 の B（重大な崩れだけ CSS で直す）のまま<br>B：9章の「スマホ向けの画面」を、一部できた扱いに書き直す<br>C：スマホの幅も確認の対象に加える（4.13 に項目を足す） | **A**。要件定義書の範囲を変えず、Phase 8 の「新しい機能は作らない」を守れる。Bootstrap は幅に合わせて並びを変える仕組みを持つので、結果的にスマホでも見やすくなる可能性はあるが、確かめていないものを要件として書かない | ☑ **A**（おすすめどおり）。要件定義書 9章は変えない。Q8-12 は B のまま |
| Q8-25 | **ログアウトのあと「戻る」で取引一覧が表示されたままになることの扱い**（4.6 の F2。ブラウザの bfcache が原因と考えられるが、**原因は未確認**。F2 の手順で確かめてから決める） | A：記録だけ。README の既知の制限に書く（(a)）<br>B：ログアウトで `JSESSIONID` を消す（(b)）<br>C：ログアウトで `Clear-Site-Data` を返す（(c)）<br>D：B と C の両方（(d)）<br>E：B をまず入れ、PC のブラウザで直るかを見る。直らなければ A にする<br>**F（新しい案）：JavaScript で、「戻る」で覚えていた画面を見せたとき〔`pageshow` の `event.persisted`〕に `location.reload()` で読み直す** | **E**（まず F2 の手順 2〜6 で原因を確かめる。bfcache なら、B は 1 行とテスト 1 件で、副作用がほぼない。C は今の HTTP〔IP アドレス〕では働かない可能性が高いので、HTTPS にする Phase 10 まで見送る。直らなくても A に落ち着けばよい）。決めるときの観点：①共用のパソコンを想定するか〔要件・設計書にその想定はない〕。②Phase 8 に「新しい機能は作らない」があるが、これは不具合の修正〔セキュリティの強化〕なので 4.16 に入れられる。③設計書 7.10 の追記が要るか〔Cookie の扱いの文〕。④4.14 で、`Cache-Control: no-store` の自動テストを足すか〔4.13 の案 (b)〕も、一緒に決める | ☑ **F**（新しい案）。理由：Chrome・Edge・Safari のどれでも、HTTP でも効く。(b)(c) はブラウザによって効くか分からない。削除の確認で、すでに JavaScript を使っている。細部は Q8-26 |
| Q8-26 | **案 F の「読み直し」の細部**（4.16 の F2 の修正。①入力エラーで表示し直した画面〔POST の応答のまま表示された画面〕を「戻る」で見せて読み直すと、ブラウザが「フォームを再送信しますか」と聞くことがある。②入力途中の値は、読み直しで消える） | A：`location.reload()` のまま。①はブラウザで確かめ、問題が出たら B にする<br>B：読み直しを `location.replace(location.href)` にする〔GET で同じ URL を開き直す。再送信の確認は出ない。ただし、POST の URL が一覧と同じ場合は、一覧が出る〕<br>C：ログイン後の画面だけで読み込む〔入力画面でも読み直しは起きる。効果は同じ〕 | **A**（ご指示のとおり。まず確かめる。ログアウト後に「戻る」で見える画面は、ログアウト前の GET の画面が主なので、①は起きにくい。問題が出たら B を検討する）。②は、ログアウトしていないときの「戻る」でも起きる点を、4.16 の確認で見る | ☑ **B**（`location.replace(location.href)`）。理由：A だと、入力エラーの画面で「フォームを再送信しますか」と聞かれることがあり、使う人が戸惑う。B なら GET で開き直すので、この質問が出ない。確認：POST の結果を画面で返す URL を一覧にし、同じ URL を GET で開いても 405・404 にならないかを調べた〔4.16 の記録〕 |
| Q8-27 | **issue 005（💮）：合計行にも付けるか**（issue 005。合計行は、合計の予算と合計の実績で、色を同じ順番で判定している。要件 4.4） | A：付ける（合計の予算・合計の実績で、科目の行と同じ判定をする）<br>B：付けない（科目の行だけ。合計行は「全体のまとめ」で、意味が違う） | **A**（要件 4.4 は「合計行も同じ順番で判定する」と決めていて、赤・青は合計行にも付く。💮 だけ付かないと、同じ表の中で決まりが食い違う。判定は同じ 1 つのメソッドを使うので、合計行のために増えるコードはほぼない。例：合計の予算 75,000円・合計の実績 40,000円なら、合計行にも 💮） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-28 | **issue 005：付けない行の確認（未設定・予算 0円・実績 0円）**（予算が未設定の行、予算が 0円の行〔実績が 0円なら色なし②、実績があれば赤〕、実績が 0円の行〔予算があれば青〕） | A：付けない（💮 は「使ったうえで、予算に収まった」行だけ。実績が 0円の行・予算が 0円の行・未設定の行は対象外）<br>B：予算 0円・実績 0円の行（色なし②）にも付ける | **A**（issue 005 の案のとおり。比べる予算がない〔未設定〕行や、使っていない〔実績 0円〕行に付けると、「収まった」の意味が薄れる。予算 0円・実績 0円は「予算を立てていない・使っていない」で、褒める場面ではない。数式で言うと、💮 は **④に当たる行だけ**：予算が設定されていて、実績が 0円より大きく、予算以下で、実績の 3倍が予算より大きい） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-29 | **issue 005：実績が予算ちょうどの行**（実績 ＝ 予算は、赤ではなく〔①は「実績 ＞ 予算」〕、青でもない → 色なし。例：予算 30,000円・実績 30,000円） | A：付ける（予算以下＝予算内。要件 4.4 の「超過」に当たらない）<br>B：付けない（ちょうどは「ぴったり」で、別扱い） | **A**（要件 4.4 は、実績が予算を**超えた**ときだけ赤にする。ちょうどは超えていない＝予算内。issue 005 の文も「実績 ≦ 予算」。B にすると、「予算より 1 円少ない行は 💮、ちょうどは付かない」という分かりにくい境目が増える） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-30 | **issue 005：💮 の位置と表の見出し**（右端。列を足すと、表の列の数と見出し、幅の狭い画面〔iPad の縦〕での見え方が変わる。テストは `<td class="num">` などを文字列で確かめている） | A：**新しい列**を右端に足す（5 列目。見出しは見た目では空に見えるように、読み上げ用の文字「評価」だけを `visually-hidden` で入れる。既存の 3 つの `<td class="num">` は変えない）<br>B：「差額」の欄の中に添える（同じセルに 💮 を足す）<br>C：科目名の横に付ける | **A**（ご要望の「右端」に合う。既存のセルの文字列〔`<td class="num">-1,000円</td>` など。テストが確かめている〕を変えずに済む。`table-responsive` で、狭い画面では横に動く。B は差額のセルの中身が変わり、テストの期待を書き換えることになる。C は「右端」ではない） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-31 | **issue 005：画面の読み上げ（スクリーンリーダー）向けの文字**（絵文字をそのまま読ませると「白い花」などと読まれて、意味が通らない） | A：絵文字は飾りとして読ませず〔`aria-hidden="true"`〕、「予算内」を画面に出さない文字〔Bootstrap の `visually-hidden`〕で添える<br>B：`aria-label="予算内"` だけを付ける<br>C：何もしない（絵文字だけ） | **A**（読み上げでは「予算内」と読まれ、画面には 💮 だけが出る。`aria-label` は `span` に付けても読まれない読み上げソフトがあり〔B は不安定〕、C は意味が通らない。Bootstrap の CSS にある `visually-hidden` を使えるので、自作の CSS が増えない） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-32 | **issue 005：色の注記に 💮 の説明を足すか**（今の注記：「赤：予算超過／青：予算の1/3以下」。テストは、この文字列が含まれることを確かめている） | A：足す。「赤：予算超過／青：予算の1/3以下／💮：予算内（予算の1/3を超え、予算以下）」<br>B：足す。短く「💮：予算内」<br>C：足さない | **A**（青の行も「予算内」なので、短い「予算内」だけだと、青の行に 💮 がない理由が伝わらない。「1/3を超え、予算以下」と書けば、青との境目が分かる。今の注記は、先頭の部分がそのまま残るので、既存のテストの `contains` は変わらず通る） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-33 | **issue 005：判定の置き場所**（設計書 P13：色の判定は `BudgetColorJudge` の 1 か所だけ。画面は判定しない。`ComparisonRow` は `(科目名, 予算, 実績, 差額, 色)` の `record`。色の判定表のテストが多数ある） | A：`BudgetColorJudge` に**新しいメソッド** `isWithinBudget(Long budget, long actual)` を足し、`ComparisonRow` に真偽値 `withinBudget` を足す。`BudgetColor` は変えない<br>B：`BudgetColor` に `WITHIN`（予算内）を足し、`judge` が返す〔今の「色なし」④の行が `WITHIN` になる〕<br>C：画面で判定する | **A**（💮 は「色」ではないので、`BudgetColor`〔赤・青・色なし〕の意味を変えない。`BudgetColorJudgeTest` の既存の期待〔色なし〕を書き換えずに済み、判定は同じクラスの 1 か所のまま〔P13 を守る〕。`ComparisonRow` を作る場所は `ComparisonService` の 1 か所だけ〔合計行と科目の行〕。B は、今の「色なし」の期待をいくつも書き換える。C は P13 に反する） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-34 | **issue 005：絵文字の表示の確認と、出ないときの扱い**（💮 は Unicode の絵文字 U+1F4AE。Bootstrap の文字の指定には絵文字のフォントが含まれる） | A：絵文字そのままで入れる。PC のブラウザで確かめる。iPad の Safari は、Phase 9 の面接前のリハーサルで確かめる。出ない・四角になる機器があれば、そのとき対応を決める<br>B：最初から SVG や CSS で描く（機器に左右されない）<br>C：絵文字の代わりに、普通の文字〔「◎」など〕にする | **A**（Windows の Segoe UI Emoji、iPad の Apple Color Emoji のどちらにも含まれる絵文字。まず入れて、出なければ B・C を考える。B は自作の部品が増え、ご要望の「💮 を出したい」から外れる。画像〔README の画面〕を撮る前〔Phase 9〕に PC で確かめられる） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-35 | **issue 005・007：手順書への足し方と順序**（今の 4.11〜4.19 は、実行計画書・「次に再開するところ」・各所から番号で参照されている。4.11〜4.13 も、あとから足して先に行った） | A：**4.20（issue 007）、4.21（issue 005）** として、末尾に足す。行う順は 4.10 の次、4.14 の前（007 → 005。各自のテストを含む）。番号の付け替えをしない<br>B：4.10-2・4.10-3 のように、枝番にする<br>C：4.11 以降を付け替えて、4.11・4.12 にする | **A**（4.11〜4.13 を足したときと同じやり方で、既存の参照を壊さない。007 は小さく要件定義書も直さないので先に、005 は要件定義書・設計書を直すので後に。4.14〔自動テストを整える〕の前に両方のテストが入るので、4.14 の一覧に含められる） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-36 | **💮 の行の背景の色の値**（4.21 の続き。文字は今の色〔`#212529`〕のまま。赤・青は**文字の色**で、こちらは**背景**なので、同じ行には重ならない〔💮 の行は色なしの行だけ〕） | A：Bootstrap の薄い緑 `var(--bs-success-bg-subtle)`（`#d1e7dd`）<br>B：もっと薄い緑 `#e8f5e9`<br>C：もっと濃い緑 `#c8e6c9` | **A**（Bootstrap 5.3 が決めている色なので、アプリの他の部分〔`alert-success` の「保存しました」など〕と同じ緑になり、新しい色の値を自分で持たずに済む。文字との明るさの比は約 12 対 1 で、読みやすさの目安〔4.5 対 1 以上〕を大きく超える。B は薄すぎて、iPad の明るい画面や日差しで白と見分けにくい。C は文字は読めるが、濃いので赤・青の行の文字色と並んだときに目立ちすぎる） | ☑ **`#e3f8e9`**（ブラウザで見比べて、本人が A から変更。2026年11月3日）。Bootstrap の変数ではなく、この値を直接使う。文字 `#212529` との明るさの比は約 13.9 対 1〔A の `#d1e7dd` は約 11.9 対 1。白い背景との比は約 1.11 対 1〕 |
| Q8-37 | **背景のつけ方**（Bootstrap の表〔`table`、`table-hover`〕は、セルの背景を自分の変数で塗り、行にマウスを載せたときの色も重ねている。赤・青は `td` の文字色を直接指定している） | A：`<tr>` に付けるクラスの規則で、Bootstrap の変数 `--bs-table-bg` を上書きする〔`.comparison-table tr.row-within { --bs-table-bg: …; }`〕<br>B：`td` に `background-color` を直接指定する | **A**（Bootstrap が背景を塗る仕組みのまま色だけ変えるので、`table-hover` のマウスを載せたときの色の重なり〔暗くなる〕も、合計行の上の線〔`border-top: 2px solid #999`。`style.css`〕も壊れない。B は Bootstrap の規則と優先順位を競い、hover の色が消えることがある） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-38 | **右端の 💮 の大きさ**（今は文字と同じ大きさ。大きくすると、行が高くなる） | A：`1.5em`（文字の 1.5 倍〔約 24px〕）。右端のセルに、`style.css` の規則で指定する〔絵文字の `<span>` の形は変えない〕<br>B：`1.25em`<br>C：`2em` | **A**（ひと目で分かる大きさで、行の高さの増えは小さい〔Bootstrap の表の行は約 40px、1.5 倍の絵文字でも収まる〕。C は行が目に見えて高くなり、💮 の行だけ行間が広がる。B は今との差が小さい。絵文字の `<span>` を Bootstrap の `fs-4` で大きくすると、テストが確かめる `<span aria-hidden="true">💮</span>` の形が変わるので、セルに指定する） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-39 | **💮 の行に付けるクラスの名前と、テストの直し方**（赤・青の行は `row-over`・`row-low`。今のテストは、色なしの行が `<tr>` だけ〔クラスなし〕、予算が未設定の行に `row-` で始まるクラスがない、ことを確かめている） | A：`row-within`（`<tr>` に付ける。合計行は `total-row row-within`）。テストは、💮 の行が `<tr class="row-within">`、**それ以外の色なしの行は今までどおり `<tr>`**、赤・青・未設定・実績 0円の行に `row-within` がない、を確かめる形に直す<br>B：Bootstrap の `table-success` を `<tr>` に付ける〔自作の CSS を足さずに済む〕<br>C：`<tr>` にはクラスを付けず、右端のセルだけで表す | **A**（`row-over`・`row-low` と同じ付け方で、設計書の「テストが確かめるクラスには Bootstrap のクラスを足さない」〔Q8-20〕とも合う。B は Bootstrap のクラス名にテストが縛られ、背景以外〔文字色・枠線の色〕も変わる。C は行全体の背景にならない）。**直すテストは `ComparisonControllerTest` の 2 か所**：①`ふつう`〔色なしで 💮 の行〕が `startsWith("<tr>")` → `startsWith("<tr class=\"row-within\">")`。②合計行〔その表では予算内になる〕が `startsWith("<tr class=\"total-row\">")` → `startsWith("<tr class=\"total-row row-within\">")`。`予算なし`〔未設定〕が `<tr>` のまま、`doesNotContain("row-")`〔未設定の 2 行〕はそのまま通る | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-40 | **色の注記を変えるか**（今：「赤：予算超過／青：予算の1/3以下／💮：予算内（予算の1/3を超え、予算以下）」。背景の緑のことは書いていない） | A：変えない（💮 の説明があれば、緑の行の意味は分かる）<br>B：「緑の背景と💮：…」のように、背景のことを足す | **A**（行を見れば 💮 と緑がセットで出るので、説明は 💮 だけで足りる。文字列を変えないので、テストが確かめている注記〔2 つの `contains`〕も変わらない） | ☑ **A**（おすすめどおり。2026年11月3日） |
| Q8-41 | **要件定義書に、背景を書くか**（要件 4.4 は、赤・青を「赤字」「青字」と、見た目まで書いている） | A：3章〔一覧の文〕と 4.4〔💮 の文〕に「その行（合計行も）の背景を薄い緑にする」と足す。大きさは設計書だけに書く<br>B：要件定義書は直さず、設計書だけに書く | **A**（背景は、赤字・青字と同じ「行の見た目」の決まりで、画面を見た人に分かる。大きさ〔1.5 倍〕は実現の細かい値なので要件に書かない。前後の文は下の報告のとおり） | ☑ **A**（おすすめどおり。2026年11月3日） |

> **注**：Q8-1 は、issue 001 が Phase 8 で決めると書いている点です。直すかどうかで、4.15 のコードの変更の量が変わります。

## 6. 完了条件

実行計画書の完了条件に、確認方法を加えたものです。次のすべてを満たしたら Phase 8 は完了です。

- [ ] 要件定義書の機能一覧（3章、4章）と見比べて、機能の漏れがない（4.3）
- [ ] 入力チェック（5章）の全項目を、境界値でブラウザで確かめた（4.7）
- [ ] セキュリティの確認表（4.6）がすべて合格している：パスワードのハッシュ化、ログイン必須、SQL インジェクション、XSS、CSRF、データ分離（全画面、2人のユーザー）、エラー画面と状態コード（404・403・その他の 4xx・500）、セッション、秘密情報
- [ ] 全画面を `open-in-view=false` のまま開いて、500 が出ない（4.5）
- [ ] 前の Phase から引き継いだ項目（4.8）を確かめた。**科目が0件のアカウントの予実比較**（4.9）をブラウザで確かめた
- [ ] 256個の上限と、256行の予算設定を確かめた（Q8-10 の決定による。4.10）
- [ ] 主要な処理の自動テストに、足りないものがない（4.14。足りないものは、足したか、足さない理由を記録した）
- [ ] `.\mvnw test` が全部成功する（件数を記録する）。DB にテストの行が残っていない
- [ ] コードの整理を、決めた範囲で行った（4.15）。**issue 001 を決めて、記録した**（4.2）
- [ ] 見つかった不具合を直した。または、直さない理由を記録した。**既知の不具合が残っていない**（残る場合は理由を記録）
- [ ] 年と季節を選ぶ欄が、予算設定と予実比較にある（4.11。G1）。`.\mvnw test` が全部成功する
- [ ] Bootstrap の CSS をアプリの中に入れた（CDN なし、JavaScript なし）。自分の CSS とテストが確かめるクラスは残っている。全画面を開き直して崩れがない。ライセンスの扱いを文書にした（4.12）
- [ ] **iPad の Safari から、`http://（パソコンの IP）:8080/` で開けて、ログインでき、全画面を縦・横で見た**（4.13）。見つけた崩れは、Q8-12 の決定に従って、直したか記録した。ファイアウォールの許可を外した。ネットワークの種類を元に戻した
- [ ] 確認のために作ったデータを片付けた（4.17）
- [ ] 文書（設計書・要件定義書・README・実行計画書・issue）が、実際の状態と合っている（4.18）
- [ ] 実行計画書の進捗表で Phase 8 を ☑ にし、Phase 8 の節を概要とリンクだけに縮めた
- [ ] GitHub に push されていて、パスワード・接続先・確認用アカウントのパスワードが含まれていない（4.19）
- [ ] `main` に取り込み、タグ `phase-8-done` を付けて push した

## 7. つまずいたとき

| 症状 | 考えられる原因 | 対処 |
|---|---|---|
| 科目が削除できない（0件にできない） | 0円でない予算がある季節が残っている | 予算設定で、その科目の予算をすべての季節で0円にしてから削除する（要件 4.3）。新しいアカウントは、今の季節だけに予算がある（4.9 の手順 3〜5） |
| ブラウザで開くと 500（`LazyInitializationException`） | 画面が読む関連を、Service / Repository がまとめて読み込んでいない | 画面が読む値を DTO に入れて渡す、または `@EntityGraph` で読み込む（設計書 4.6）。自動テストでは見つからない |
| `_csrf` を消して送ったのに、403 にならない | 別のフォームを消した／ブラウザが古い画面を使っている | 画面を開き直して、そのフォームの隠し項目を確かめる。コードで CSRF を無効にしていないか検索（4.4） |
| 他人の ID を開いたとき、404 でなく 200 や 500 になる | Service が `userId` で絞っていない | 検索の条件に `userId` があるか確認する（設計書 3.9、7.8）。見つけたら 3.3 で記録して直す |
| DB を止めたあと、アプリが動かない | MySQL を起動し直していない／アプリの接続が切れたまま | `MySQL97` を起動し、アプリを再起動する |
| 256個を足す SQL がエラーになる | MySQL の書き方の違い（`WITH RECURSIVE` の位置）／科目名が10文字を超えた | エラーの文を教えてください。まず ② の読むだけの SQL で、足す行数を確かめる |
| 256行の予算設定で保存できない | 行の数が256を超えている（257個以上の科目がある） | 科目の数を256以下にする（要件 5章。R14） |
| `.\mvnw test` のあと、DB に行が残る | トランザクションで包んでいないテストがある | 各テストを `@Transactional` にする（Phase 3 の Q3-8） |
| 手順 6 で案内でなく表が出る | 科目が残っている（削除に失敗していた） | 科目一覧を確かめる。0件になるまで、予算を0円にして削除する |
| iPad で開けない（接続できない、タイムアウト） | 同じネットワークでない／会場の Wi-Fi が端末どうしの通信を禁止している／ファイアウォールの許可がない（または `Public` のネットワーク）／アプリが起動していない／IP アドレスが違う | まず、パソコン自身から IP アドレスで開けるか（4.13 の D）。次に `Get-NetConnectionProfile` が `Private` か、許可ルールがあるか（E）。会場の Wi-Fi の可能性は、ホットスポット（Q8-13） |
| パソコン自身から IP アドレスで開けない | アプリが `127.0.0.1` だけで待ち受けている／IP アドレスが違う | `netstat -an \| findstr :8080` で `0.0.0.0:8080` を確かめる。`ipconfig` で IP アドレスを読み直す |
| iPad でログイン画面は出るが、ログインしても戻される | Cookie が保存されていない（`secure` を付けた／プライベートブラウズ／Cookie をブロックしている設定） | `application.properties` に `secure=true` がないか確かめる。Safari の設定で Cookie のブロックが入っていないか確かめる |
| iPad の Safari に「保護されていない通信」と出る | HTTP で開いているため | 正常。気になるときは、使い捨てのアカウントだけを使う（Q8-14） |
| 許可ルールを作ったのに、iPad から開けない（ホットスポット・テザリング） | ネットワークの種類が `Public` のまま（許可ルールは `Private` だけ） | `Get-NetConnectionProfile` で種類を確かめて、`Private` にする（4.13 の共通の手順）。変えられないときは、許可ルールの `-Profile` を合わせる |
| ホットスポットを始められない | パソコン自身がインターネットにつながっていない | 方法 2（スマホのテザリングに両方をつなぐ）にする（Q8-13） |
| テザリングにつないだのに、パソコンと iPad が通信できない | スマホが、つないだ端末どうしの通信を許可していない | 別の方法（方法 1、または別のスマホ・機器）にする。記録して、面接では使わない |
| Bootstrap を入れたら、赤・青の行の色が出ない | Bootstrap の表がセルの文字色を決めていて、`row-over`・`row-low` が負けている | `style.css` の規則を、Bootstrap より後に読む。それでも負けるときは、規則の選び方を具体的にする（例：`.comparison-table tr.row-over > td`）。`<tr>` にクラスは足さない（テストが確かめている） |
| Bootstrap を入れたら、既存のテストが落ちる（`class="num"`、`<tr class="row-over">`、`<a href="/comparison">予実比較</a>` など） | 守る文字列のある要素に、Bootstrap のクラスを足した | その要素のクラスを元に戻し、`style.css` で当てる（4.12 の「守る文字列」の表） |
| ログイン画面だけ見た目が崩れる | `bootstrap.min.css` がログイン前に読めない（ログイン画面にも使う） | 置き場所が `static/css/` か確かめる。`SecurityBehaviorTest` に `/css/bootstrap.min.css` を足す |
| 年と季節を選んで「表示」を押したら 404 | `year` か `season` の片方しか送っていない／`name` の書き間違い | `<select>` の `name` が `year` と `season` か確かめる（Controller は変えない） |
| 許可ルールを外したあとも、iPad から開ける | 別の広いルール（Java のダイアログで許可したもの）が残っている | 4.13 の手順 I のコマンドで確かめて、内容を見てから消す |
| パスワードをコミットしてしまった | — | ファイルを消すだけでは履歴に残る。**そのパスワードを変更してから**、相談する（7.9） |

## 8. 次の Phase への引き継ぎ

- Phase 9（完成・引き渡し）：
  - README を仕上げる。起動方法の説明を、まっさらな環境で、手順どおりに実行して確かめる（データベースとユーザーの作り方、環境変数、`.\mvnw spring-boot:run`）。
  - Phase 8 の確認で分かった、使い方の注意（たとえば、科目を0件にする方法、日付の範囲、季節の決まり）があれば、README に反映する。
  - **画面のスクリーンショットは、Bootstrap を入れたあと（4.12）に撮る。** 年と季節を選ぶ欄（4.11）も画像に入る。
  - README の使用技術に Bootstrap（CSS のみ）を書き、`THIRD-PARTY-NOTICES.md` を案内する（Q8-21）。
  - 画面のスクリーンショットを撮る前に、パスワードや接続情報が写らないようにする（設計書 7.9）。表示するデータは、確認用のものを片付けたあとの状態、または見せるための入力にする。
  - 開発用の DB の状態（4.17 の結果）を、Phase 9 の最初に確かめる。
  - 採用担当者などが自分で登録して試す（既定のアカウントはない。S6）ことを、README に書く。
  - **本番の面接用に、見せるためのデータを入れたデモ用アカウントを用意する**（Q8-14）。Phase 8 の確認用の使い捨てのアカウントとは別にする。取引・予算・予実比較で、赤・青・色なし・「未設定」がそろって見えるデータ（Phase 7 の手順書 4.9 の(3)のような取引）を入れる。パスワードは、ほかのサービスと同じものにせず、文書に書かない。いつ、どのデータを作るか、面接のあとどうするか（残す／消す）を、Phase 9 の最初に決める。
  - 面接で iPad から触ってもらう手順（4.13 の結果：IP アドレスの調べ方、ファイアウォールの許可と解除、うまくいかないときのホットスポット）を、README か運用メモに載せるかを決める。iPad で見つけた崩れで直さなかったものは、「既知の制限」に載せるかを決める（Q8-12）。
  - **iPad の Safari の確認を、面接前のリハーサルでまとめて行う**〔4.16 の F2 の修正：ログアウト後の「戻る」で前の画面が残らないこと。4.13 の項目も合わせて〕。
  - **画面の画像は、4.20・4.21 のあとに撮る**〔「今の季節」ボタンと 💮 が写る〕。iPad の Safari での 💮 の表示、💮 の行の薄い緑の背景〔`#e3f8e9`。白との差が小さいので、明るい画面や日差しで見分けられるか〕、「今の季節」ボタンの折り返しも、リハーサルで確かめる。
  - Phase 8 で「直さない」と決めた既知の事項（あれば）を、README の「既知の制限」に載せるかを決める。〔候補：POST だけで開ける URL のエラー画面を覚えていて「戻る」で開き直すと、別のエラー画面になる（F2、設計書 7.10）〕
- インターネットへの公開（デプロイ）は、実行計画書の Phase 10（任意・候補）。Phase 9 のあとに、やるかどうかを決める。
- 設計書の章8に残る未決事項は、Phase 8 の終了時点でなくなる見込み（新しく出たものがあれば追記する）。
