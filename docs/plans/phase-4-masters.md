# Phase 4：科目・取引先・支払方法

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は Phase 4 を実際に進めるための手順書です。

- 前の Phase：[Phase 3 アカウント登録・ログイン](phase-3-auth.md)（☑ 完了）
- 次の Phase：[Phase 5 取引](execution-plan.md#phase-5取引)
- 状態：☐ 未着手（手順書の決定を反映済み）
- 作業ブランチ：`feature/phase-4-masters`

## 1. 目的

取引の入力に必要なマスタ（選択肢）である、科目・取引先・支払方法の管理を作ります。3つは同じ形なので、**最初の1つ（科目）をていねいに作り、残りの2つに応用します。** 具体的には、次の4つがそろった状態にします。

- 3つとも、一覧・追加・変更・削除ができる。入力エラーのときは、入力値が残る（設計書 2.3）。
- **すべての検索・更新・削除を「ID と `user_id` の両方」で絞る型**を、ここで確立する（設計書 3.9）。他人のID・存在しないIDは、同じ 404 になる（B7、7.8）。以降の Phase 5〜7 は、この型をそのまま使う。
- 削除の制限が、画面のメッセージとして表示される（要件4.3）。取引で使用中のものは削除できない。科目は、いずれかの季節に0円でない予算があっても削除できない。
- 科目を追加すると、今の季節の予算0円の行が、同じトランザクションで作られる（Q3、R11）。

## 2. 前提と、この Phase でやらないこと

- Phase 3 が完了し、`main` に取り込み済み（タグ `phase-3-done`）。環境変数 `HB_DB_URL`、`HB_DB_USER`、`HB_DB_PASSWORD` が設定された VS Code で作業する。`root` はアプリで使わない（実行計画書 ルール8）。
- Phase 3 で作成済みのもの：`User`、`Category`、`Vendor`、`PaymentMethod`、`Budget` の Entity と Repository（Repository は `save` などの標準メソッドだけ）、`DuplicateException`、`LoginUser`、`SecurityConfig`、共通の枠（`fragments/common.html`）、404 / 403 / 4xx / 5xx の画面、`AppConfig` の `Clock`、`SeasonPeriod.from`。
- やらないこと：取引の画面と処理（Phase 5）、予算の画面（Phase 6）、予実比較（Phase 7）。`TemporaryTransactionController`（仮の取引一覧）は、Phase 5 まで残す。
- **この手順書にも、コードにも、設定ファイルにも、パスワードを書かない。**
- 設計書で決まっていない点が出たら、勝手に決めず、「5. 決まっていない点」に載せて確認する。

## 3. 作るものと、設計書との対応

| 作るもの | 置き場所 | 設計書での根拠 |
|---|---|---|
| `Transaction`（Entity）と `TransactionRepository`（使用中の確認だけ） | `entity`、`repository` | 3.6、4.6、Q4-1 |
| `NotFoundException`、`DeletionNotAllowedException`、`GlobalExceptionHandler` | `exception` | 4.5、5.1、S4、Q3-5 |
| 科目：`CategoryRepository`（検索を足す）、`CategoryForm`、`CategoryService`、`CategoryController` | `repository`、`form`、`service`、`controller` | 4.4〜4.7、5.4〜5.6 |
| 取引先：`VendorRepository`（検索を足す）、`VendorForm`、`VendorService`、`VendorController` | 同上 | 同上 |
| 支払方法：`PaymentMethodRepository`（検索を足す）、`PaymentMethodForm`、`PaymentMethodService`、`PaymentMethodController` | 同上 | 同上 |
| `BudgetRepository`（検索を足す） | `repository` | 4.6、5.5 |
| `categories/list.html`、`categories/edit.html`、`vendors/list.html`、`vendors/edit.html`、`payment-methods/list.html`、`payment-methods/edit.html` | `templates/` | 4.4.2、2.3 |
| メニューに、科目・取引先・支払方法を足す | `fragments/common.html` | 2.3、Q3-4 |
| メッセージ | `messages.properties` | 6.2、6.8 |

### 3.1 「ID と `user_id` の両方で絞る」型（Phase 4 で確立する）

設計書 3.9、4.1 の決まりを、3つのマスタで同じ形にします。

| 層 | 決まり |
|---|---|
| Controller | ログイン中のユーザーのID（`LoginUser.getUserId()`）を取り出し、**Service のメソッドに必ず渡す**。画面から送られたユーザーIDは使わない（Form にも `user_id` の項目を作らない、6.1） |
| Service | メソッドの最初の引数は `userId`。ID で探すときは、必ず Repository の「ID と `userId`」の検索を使う。見つからなければ `NotFoundException`（他人のものか、存在しないかは区別しない、7.8） |
| Repository | **メソッドは、すべて `userId` を引数に取る**（`id` だけで探すメソッドを、アプリのコードから使わない）。`findById`（標準）は使わない |

Repository に足す検索メソッド（名前は作るときに決める。次は案）：

| Repository | メソッド（案） | 使う場面 |
|---|---|---|
| `CategoryRepository` | `findByUserIdOrderByIdAsc(userId)` | 一覧（登録順、Q14） |
| | `findByIdAndUserId(id, userId)` | 変更画面、変更、削除（ID と `user_id` の両方） |
| | `existsByUserIdAndName(userId, name)` | 追加のときの重複確認（5.6） |
| | `existsByUserIdAndNameAndIdNot(userId, name, id)` | 変更のときの重複確認（自分自身の今の名前は重複としない、5.6） |
| `VendorRepository`、`PaymentMethodRepository` | 同上 | 同上 |
| `BudgetRepository` | `existsByUserIdAndCategoryIdAndAmountGreaterThan(userId, categoryId, 0)` | 0円でない予算がある季節があるか（5.5） |
| | `deleteByUserIdAndCategoryId(userId, categoryId)` | 科目の削除の前に、その科目の予算の行をすべて消す（3.7） |
| `TransactionRepository` | `existsByUserIdAndCategoryId`、`existsByUserIdAndVendorId`、`existsByUserIdAndPaymentMethodId` | 使用中の確認（5.5） |

- 検索は、名前付きの検索（Spring Data JPA のメソッド）かパラメータ付きの検索で書く。文字列を連結した SQL は書かない（7.4）。
- 重複の比べ方（大文字・小文字、かな、全角半角）は、DB の照合順序（`utf8mb4_0900_as_ci`）で決まる。アプリ側でも、DB に問い合わせて同じ基準で確認する（5.6）。

## 4. 手順

各手順の最後に **確認** があります。確認ができたら、チェックを付けて次へ進みます。

> **進め方**：ファイルの作成・編集と、読むだけのコマンドは Claude Code が行う。パスワードの入力（MySQL へのログイン）、MySQL へ書き込む SQL、コミット・push は自分で行う。**科目を最後まで作って確認してから、取引先・支払方法に進む。**

### 4.1 作業ブランチと土台を確認する

- [x] `feature/phase-4-masters` にいる
- [x] MySQL のサービス（`MySQL97`）が動いている
- [x] 環境変数 `HB_DB_URL`、`HB_DB_USER`、`HB_DB_PASSWORD` が設定されている（値は表示しない）
- [x] 確認用のアカウントが2つ以上ある（Phase 3 の `test1`、`test2`。データ分離の確認に使う）

**確認**：
- [x] `git status` が `On branch feature/phase-4-masters` と `working tree clean` を示す
- [x] `.\mvnw test` が成功する（Phase 3 の 105 件）

### 4.2 `Transaction` の Entity と `TransactionRepository`（Q4-1）

「取引で使用中なら削除できない」の判定に、`transactions` を調べる必要がある。取引の画面は Phase 5 だが、使用中の確認のために、Entity と Repository の一部を前倒しで作る。

> 作る範囲は Q4-1 の決定による。下は、**推奨（A）の場合**の内容。

- [x] `entity` に `Transaction`：設計書 3.6 の全列（`id`、`user_id`、`transaction_date`、`amount`、`category_id`、`description`、`vendor_id`、`payment_method_id`）。`user_id` は数値のID、科目・取引先・支払方法は関連（`ManyToOne`）で持つ（P7）
- [x] `repository` に `TransactionRepository`：**使用中の確認の3メソッドだけ**（3.1 の表）。一覧などの検索は Phase 5 で足す
- [x] `TransactionRepositoryTest` で、`Transaction` の保存と読み戻し（列名・型・摘要が NULL／30文字になること）と、使用中の確認の3メソッド（`user_id` でも絞っていること）を確かめる

**確認**：
- [x] `.\mvnw test` が成功する（`Transaction` の定義が、表と食い違っていない）
- [x] 摘要が空（NULL）の取引が保存でき、読み戻せる（3.6）

### 4.3 例外と 404 の変換（Q3-5）

設計書 4.5、5.1、7.7、7.8、S4 のとおりに作る。

- [x] `exception` に `NotFoundException`：存在しない、または他人のデータ（B7）
- [x] `exception` に `DeletionNotAllowedException`：削除できない理由のメッセージのキー（`error.delete.inUse` など）を持つ
- [x] `exception` に `GlobalExceptionHandler`：
  - `NotFoundException` を `error/404.html` に変える。**状態コードも 404 にする**（画面だけ 404 で、状態コードが 200 にならないように、7.7）
  - ID が数字でない URL（例：`/categories/abc`）、数値の範囲を超える ID も、同じ 404 にする（S4）
  - 画面にも、ログにも、他人のデータの有無が分かる情報を出さない（7.8）
- [x] `DuplicateException`（Phase 3 で作成済み）と `DeletionNotAllowedException` は、`GlobalExceptionHandler` では扱わない。Controller が受けて、画面の動きに変える（5.1 の表）

**確認**：
- [x] 例外の変換のテスト（`GlobalExceptionHandlerTest`）で、`NotFoundException` と数字でない ID が、画面も状態コードも 404 になる。まだ本物の画面がないので、テスト用の URL（`testsupport.TestErrorEndpoints`。アプリのコンポーネントスキャンの外に置き、テストが読み込んだときだけ有効）を使う。本物の画面での確認は 4.9 で行う

### 4.4 メッセージ

`messages.properties` に、Phase 4 で使うメッセージを書く（6.2、6.8、2.6）。文末は、ていねいな言い方に統一する。キーの付け方は G6。

- [x] 入力チェック（Form）：`NotBlank.categoryForm.name`（科目名を入力してください）、`Size.categoryForm.name`（科目名は10文字以内で入力してください）。取引先は `vendorForm`（取引先名… 20文字）、支払方法は `paymentMethodForm`（支払方法名… 10文字）
- [x] 重複（Service）：`error.duplicate.category`（その科目名はすでにあります）、`error.duplicate.vendor`（その取引先名はすでにあります）、`error.duplicate.paymentMethod`（その支払方法名はすでにあります）
- [x] 削除できない理由：`error.delete.inUse`（取引で使われているため削除できません）、`error.delete.budgetNotZero`（予算が0円ではない季節があるため削除できません）
- [x] 成功：「追加しました」「変更しました」「削除しました」のキー（3つのマスタで共通に使う。キー名は作るときに決める）

**確認**：
- [x] ファイルが UTF-8 で、画面で文字化けしない

### 4.5 科目：Repository と Form

- [x] `CategoryRepository` に、3.1 の検索メソッドを足す（`userId` を引数に取る型）
- [x] `BudgetRepository` に、3.1 の2つのメソッドを足す
- [x] `form` に `CategoryForm`：`name` の1項目。必須、10文字以下（要件5章、6.2）。注釈は D4 の B（必須は他のエラーと重ねない。文字数は別の注釈）。`user_id` の項目は作らない（6.1）
- [x] 前後の空白の取り除き（全角の空白を含む、B9、6.4）：`String.strip()` を使う。**`trim()` と、Spring 標準の `StringTrimmerEditor` は、全角の空白を取らないので使わない**。取り除く仕組みは Q4-3 の決定による

**確認**：
- [x] `CategoryForm` の入力チェックの単体テスト（Spring を使わない）：空、空白だけ（半角・全角）、10文字、11文字、前後に空白（半角・全角）のある名前（取り除かれる）

### 4.6 科目：Service

設計書 5.4、5.5、5.6 のとおりに作る。**すべてのメソッドの最初の引数は `userId`。更新するメソッドは、1つのトランザクションにする。**

- [x] 一覧：自分の科目を登録順（`id` 順）で返す
- [x] 1件取得（変更画面用）：ID と `userId` で探す。なければ `NotFoundException`
- [x] 追加：
  1. 自分の中に同じ名前があれば `DuplicateException`（5.6）
  2. 名前を保存（`user_id` はログイン中のユーザー）。同時に追加されて確認をすり抜けた場合は、DB の一意制約のエラーも `DuplicateException`（5.6）
  3. 同じトランザクションで、**今の季節の予算0円の行**を保存する。今の季節は、`SeasonPeriod.from(LocalDate.now(clock))` と `AppConfig` の `Clock` で求める。ほかの季節の予算は作らない（5.4）
- [x] 変更：ID と `userId` で探す（なければ `NotFoundException`）。**この行以外**で同じ名前があれば `DuplicateException`。自分自身の今の名前は重複としない（名前を変えずに保存してもエラーにしない、5.6）
- [x] 削除（5.5）：
  1. ID と `userId` で探す（なければ `NotFoundException`）
  2. その科目が取引で使われていれば `DeletionNotAllowedException`（`error.delete.inUse`）
  3. いずれかの季節に0円でない予算があれば `DeletionNotAllowedException`（`error.delete.budgetNotZero`）
  4. 「使用中」と「0円でない予算」が重なるときは、**「使用中」のメッセージだけ**を出す（F1。上の順番どおりに判定すればそうなる）
  5. 同じトランザクションで、その科目の予算の行（どの季節のものも）をすべて消してから、科目を消す（3.7）
  6. 判定をすり抜けて DB の外部キーに拒否された場合も、`DeletionNotAllowedException`（`error.delete.inUse`）として扱う
- [x] 予算が「未設定」の季節（行がない季節）は、0円として扱うので、削除をさまたげない（要件4.3）

**確認**：
- [x] `.\mvnw test` が成功する（テストは 4.8 で書く）

### 4.7 科目：Controller と画面

設計書 2.2、2.3、2.6、4.4 のとおりに作る。**画面の値は `th:text` で出す。`th:utext` は使わない**（7.5）。フォームは `th:action` で書く（CSRF トークンが入る、7.6）。

- [x] `controller` に `CategoryController`（受け持つ URL は 4.4.1）。ログイン中のユーザーのIDを `LoginUser` から取り出して、Service に渡す
  - `GET /categories`：一覧と、上の追加フォーム
  - `POST /categories`：追加。入力エラー・重複のときは、**リダイレクトせず**一覧を表示し直す（フォームに入力値とエラーを入れ、一覧は取り直して最新にする、6.9）。成功したら `/categories` へリダイレクト（「追加しました」）
  - `GET /categories/{id}/edit`：名前の入った変更フォーム
  - `POST /categories/{id}/edit`：変更。入力エラー・重複のときは、変更画面を表示し直す。成功したら `/categories` へリダイレクト（「変更しました」）
  - `POST /categories/{id}/delete`：削除。成功したら `/categories` へリダイレクト（「削除しました」）。`DeletionNotAllowedException` のときは、`/categories` へリダイレクトし、理由を画面の上部に表示する（2.3、2.6）
- [x] `templates/categories/list.html`：
  - 共通の枠のヘッダー、成功・エラーメッセージの表示場所
  - 追加フォーム（名前の項目の横にエラーを表示）
  - 一覧（登録順）：名前、「変更」リンク、「削除」ボタン。**削除ボタンは、使用中のものにも表示する**（押したときに確認ダイアログのあと、理由を表示する、2.3）
  - 0件のときは「まだありません」
- [x] `templates/categories/edit.html`：名前の入ったフォーム、エラー表示、「戻る」リンク（一覧へ）
- [x] 削除ボタンの確認ダイアログ：「削除しますか？」。メッセージは**固定の文言**にして、利用者の入力（科目名など）を JavaScript に埋め込まない（7.5）。置き場所は Q4-5 の決定による
- [x] メニューに「科目」を足す（`fragments/common.html`、2.3、Q3-4）。順番は、取引一覧、科目（取引先、支払方法は 4.10、4.11 で足す）

**確認**：
- [x] `.\mvnw test` が成功する
- [x] `th:utext` を使っていない（検索して確かめる）

### 4.8 科目：自動テスト（Q4-4）

> どこまで書くかは Q4-4 の決定による。下は、**推奨（A）の場合**の内容。各テストはトランザクションで包み、終わると取り消される形にする（Phase 3 の Q3-8 と同じ）。テストに出てくるパスワードは、ダミーの値にする。

**Service のテスト（`CategoryServiceTest`）**（4.6 のあとに前倒しで書いた。31件）
- [x] 一覧：自分の科目だけが、登録順で返る（他のユーザーの科目は返らない）
- [x] 追加：科目が増え、**今の季節の予算0円の行が1つ**できる（`Clock` を固定して、季節を確かめる。冬の1月・2月は前の年）。ほかの季節の行はできない
- [x] 追加の重複：同じ名前、大文字・小文字違い（`Food` と `FOOD`）、ひらがなとカタカナ（`ひ` と `ヒ`）、全角と半角（`ｱ` と `ア`）は `DuplicateException`。**濁点違い（`カ` と `ガ`）は別の名前として追加できる**（設計書 3.12）。**他のユーザーが同じ名前を持っていても、追加できる**
- [x] 変更：名前が変わる／名前を変えずに保存してもエラーにならない（自分自身の今の名前は重複としない）／他の科目と同じ名前に変えると `DuplicateException`／大文字・小文字だけ変える（`Amazon` → `AMAZON` のような変更）ができる
- [x] 他人のID・存在しないIDで、1件取得・変更・削除が `NotFoundException`（**他人のデータは変更されていない**ことも確かめる）
- [x] 削除できる：取引がなく、予算がすべて0円の科目を削除すると、**その科目の予算の行（どの季節も）も消え、科目も消える**
- [x] 削除できる：予算の行がない（未設定の）季節がある科目を削除できる
- [x] 削除できない：取引で使われている科目は `DeletionNotAllowedException`（`error.delete.inUse`）。**科目も予算も残っている**
- [x] 削除できない：いずれかの季節に0円でない予算がある科目は `DeletionNotAllowedException`（`error.delete.budgetNotZero`）。**科目も予算も残っている**（複数の季節のうち1つだけが0円でない場合も）
- [x] 重なるとき：使用中で、かつ0円でない予算もある科目は、「使用中」の理由になる（F1）

**画面のテスト（`CategoryControllerTest`。MockMvc）**
- [x] 未ログインで `/categories`、`/categories/1/edit` を開くと、ログイン画面へ移動する
- [x] 一覧：自分の科目だけが出る。他のユーザーの科目は出ない
- [x] 追加：成功すると `/categories` へリダイレクト。入力エラー（空、11文字）・重複のときは、200 で一覧を表示し直し、**入力値が残り**、項目の横にエラーが出る
- [x] **他人のIDを URL に入れる**：`GET /categories/{他人のID}/edit`、`POST /categories/{他人のID}/edit`、`POST /categories/{他人のID}/delete` が、すべて **404**（状態コードも 404）。存在しないID、数字でないID（`abc`）も同じ 404
- [x] 削除できないとき：`/categories` へリダイレクトし、理由が表示される
- [x] トークンなしの POST（追加・変更・削除）は 403

**確認**：
- [x] `.\mvnw test` が全部成功する
- [ ] テストのあとで、`users`、`categories`、`budgets` に、テスト用の行が残っていない（4.9 の SQL で確かめる）

### 4.9 科目：画面で確かめる

アプリを起動して（`.\mvnw spring-boot:run`）、ブラウザで確かめる。確認用アカウントは `test1`、`test2`（Phase 3 で登録済み）を使う。

#### (1) 一覧・追加・変更

- [ ] `test1` でログインし、メニューの「科目」を開く。交通費、食費、雑費が、この順で出る
- [ ] 「間食」を追加する。「追加しました」が出て、一覧の最後に出る。**今の季節の予算0円の行ができている**（SQL で確かめる、下の(4)）
- [ ] 入力エラー（空、11文字）：項目の横にエラーが出て、**入力した値が残る**
- [ ] 重複（`食費`、`食費 `（後ろに半角の空白）、`食費`（前に全角の空白））：「その科目名はすでにあります」。前後の空白は取り除かれて比べられる
- [ ] 変更：「間食」→「おやつ」に変更できる。名前を変えずに保存してもエラーにならない。他の科目と同じ名前（`交通費`）に変えると、エラー（入力値が残る）
- [ ] 確認ダイアログ：削除ボタンを押すと「削除しますか？」が出る。「キャンセル」で削除されない

#### (2) 削除の制限（SQL で「使用中」の状態を作る。Q4-2）

- [ ] 初期の科目（交通費、食費、雑費）は、今の季節の予算が 30,000 円なので、削除すると「予算が0円ではない季節があるため削除できません」と画面の上部に出て、一覧に戻る。科目は消えない
- [ ] 追加した科目（予算0円）は、削除できる。「削除しました」が出る。**予算の行も消えている**（SQL で確かめる）
- [ ] 「使用中」の確認：自分で `hb_app` でログインし、`test1` の科目・取引先・支払方法の `id` を調べて、取引を1件入れる（書き込み。確認後に必ず消す）

```sql
SELECT id, username FROM users;
SELECT id, name FROM categories WHERE user_id = <test1 の id>;
SELECT id, name FROM vendors WHERE user_id = <test1 の id>;
SELECT id, name FROM payment_methods WHERE user_id = <test1 の id>;

INSERT INTO transactions (user_id, transaction_date, amount, category_id, description, vendor_id, payment_method_id)
VALUES (<test1 の id>, '2026-10-27', 1000, <科目の id>, NULL, <取引先の id>, <支払方法の id>);
```

  その科目を削除すると、「取引で使われているため削除できません」だけが出る（予算が30,000円でも、「使用中」の理由だけ。F1）。科目は消えない
- [ ] 確認後、入れた取引を消す

```sql
DELETE FROM transactions WHERE id = <入れた取引の id>;
SELECT COUNT(*) FROM transactions;
```

  期待：2つ目は 0（取引を残さない）

#### (3) 別ユーザーのデータが見えない・触れない

- [ ] `test2` でログインし、科目の一覧に `test1` の科目（「おやつ」など）が出ない
- [ ] `test1` の科目の `id` を調べ（SQL）、`test2` でログインしたまま、`/categories/<test1 の科目の id>/edit` を開くと、**404 の専用画面**（状態コードも 404）。存在しない `id`（例：`999999`）、`/categories/abc` も同じ 404 画面
- [ ] `test2` の画面から、`test1` の科目の削除や変更を送れない（フォームの `action` の ID を書き換えて送っても 404 になる。開発者ツールで `action` を書き換えて試す）。`test1` の科目は変わっていない
- [ ] `test2` が、`test1` と同じ名前（例：「おやつ」）の科目を追加できる（ユーザーごとに別。重複にならない）

#### (4) MySQL で確認（`hb_app` でログイン。読むだけ）

```sql
SELECT id, user_id, name FROM categories ORDER BY id;
SELECT user_id, category_id, season_year, season, amount FROM budgets ORDER BY id;
```

- [ ] 追加した科目の予算が、**今の季節（今日の日付の季節と年）、0円**で1件できている
- [ ] 削除した科目の予算の行が、残っていない（`budgets` にその `category_id` がない）

#### (5) 状態コードと画面（7.7）

- [ ] 開発者ツールの「ネットワーク」で、他人のID・存在しないID・`abc` の URL が、画面だけでなく**状態コードも 404** になっている
- [ ] 404 の画面に、他人のデータがあるかどうかが分かる情報が出ていない（他人のIDと存在しないIDで、画面も状態コードも同じ）

### 4.10 取引先に応用する

科目で確かめた型を、取引先に当てはめる。**科目と同じ形で作るが、取引先は予算を持たない。**

- [ ] 科目と違う点：
  - 名前は **20文字以下**（要件5章）。メッセージは「取引先名は20文字以内で入力してください」
  - 追加のとき、予算の行は作らない
  - 削除は、「取引で使われていない」ことだけを確認する（予算の確認はない）
  - メッセージのキーは、`vendorForm`、`error.duplicate.vendor`
- [ ] `VendorRepository`（検索を足す）、`VendorForm`、`VendorService`、`VendorController`、`vendors/list.html`、`vendors/edit.html` を作る（科目のクラス・テンプレートを写して、違う点だけ直す。3つを1つの共通クラスにまとめない。理由は設計書 4.4.1）
- [ ] 科目の自動テスト（4.8）と同じ内容のテストを、取引先に当てはめて書く（予算に関するものを除く）
- [ ] メニューに「取引先」を足す

**確認**：
- [ ] `.\mvnw test` が成功する
- [ ] 画面で、4.9 の(1)〜(3)を取引先に当てはめて確かめる（初期の取引先は、コンビニ、ガソリンスタンド、Amazon。20文字は追加でき、21文字はエラー。使用中の取引先は削除できない）

### 4.11 支払方法に応用する

- [ ] 科目と違う点：名前は **10文字以下**。メッセージは「支払方法名は10文字以内で入力してください」。予算の行は作らない。削除は「取引で使われていない」ことだけ。キーは `paymentMethodForm`、`error.duplicate.paymentMethod`
- [ ] URL は `/payment-methods`、テンプレートのフォルダは `payment-methods/`（設計書 4.4.2）
- [ ] `PaymentMethodRepository`（検索を足す）、`PaymentMethodForm`、`PaymentMethodService`、`PaymentMethodController`、`payment-methods/list.html`、`payment-methods/edit.html` を作る
- [ ] 科目の自動テスト（4.8）と同じ内容のテストを書く（予算に関するものを除く）
- [ ] メニューに「支払方法」を足す。メニューの順番は、取引一覧、科目、取引先、支払方法（2.3）

**確認**：
- [ ] `.\mvnw test` が成功する
- [ ] 画面で、4.9 の(1)〜(3)を支払方法に当てはめて確かめる（初期の支払方法は、現金、クレジットカード）

### 4.12 3つをまとめて確かめる

- [ ] 3つのマスタで、Repository のメソッドが**すべて `userId` を引数に取っている**（`findById` や、`userId` のない検索を、アプリのコードで使っていない）。検索して確かめる
- [ ] `th:utext` が、どのテンプレートにもない。文字列を連結した SQL がない（7.4、7.5）
- [ ] 重複の判定が、3つとも設計書 3.12 のとおり：`A` と `a`、`ひ` と `ヒ`、`ｱ` と `ア`、`コンビニ` と `ｺﾝﾋﾞﾆ` は**同じ名前（重複）**、`カ` と `ガ` は**別の名前**
- [ ] 2人のユーザーで、一覧・変更・削除が互いに見えない・触れない（4.9 の(3)を3つで確かめた）
- [ ] 3つとも、入力エラー時に入力値が残り、削除の制限が画面のメッセージとして出る

### 4.13 設計書・README・実行計画書に反映する

設計書が確定したあとの変更は、先に設計書を直す（Phase 1 の決まり）。

| 直す場所 | 書くこと |
|---|---|
| 6.4 | 前後の空白の取り除き方（Q4-3 の決定）。`strip()` を使うこと、`trim()` と `StringTrimmerEditor` は全角の空白を取らないこと |
| 4.6 | `TransactionRepository` を Phase 4 で前倒しで作ったこと、検索メソッドの名前（Q4-1） |
| 4.5 | `NotFoundException`、`GlobalExceptionHandler` の実際の動き |
| 2.1、2.3 | 確認ダイアログの置き場所（Q4-5）。決定で変わった点があれば直す |
| 8.2 | 新しく出た未決事項があれば追記する |
| README.md | 科目・取引先・支払方法が使えるようになったことを、短く足す |
| 実行計画書 | 進捗表で Phase 4 を ☑ にし、Phase 4 の節を概要とリンクだけに縮める。Phase 5 以降に引き継ぐこと（`TransactionRepository` を Phase 5 で広げること、メニューなど）を反映する |

**確認**：
- [ ] 設計書の記述が、実際に作ったものと合っている

### 4.14 コミット前の確認と、コミット・push

- [ ] `git status` で、含めたくないファイル（`target/`、`.env`、ダンプ、`application-local.properties`）が入っていないことを確かめる（7.9）
- [ ] パスワード・実際の接続先・確認用アカウントのパスワードが、コード・設定・テスト・ドキュメントのどこにも書かれていない。次で確かめる（`mvnw`、`mvnw.cmd` の `MVNW_PASSWORD` は標準の記述で、問題ない）

```powershell
git grep -n -i -e password -- . ":(exclude)docs" ":(exclude)mvnw" ":(exclude)mvnw.cmd"
```

  期待：Phase 3 の結果から**増えていない**こと（ダミーのパスワードを増やした場合は、何の行かを一覧にする）
- [ ] 1つのタスクごとにコミットする（例：`Transaction` の前倒し、例外、科目、科目のテスト、取引先、支払方法、設計書）
- [ ] `git push` する

## 5. 決まっていない点（設計書だけでは決まらないこと）

設計書とあなたの指示だけでは決まらない点です。**決まるまで、該当する手順は進めません。** 「あなたの決定」欄に書き込んでください。

| 番号 | 決めること | 選択肢 | おすすめ | あなたの決定 |
|---|---|---|---|---|
| Q4-1 | **「使用中」の判定のために、`Transaction` をどこまで Phase 4 で作るか**。判定には `transactions` を調べる必要があるが、`Transaction` の Entity と取引の画面は Phase 5 の予定 | A：`Transaction` の **Entity を全列分（3.6、P7 の関連つき）**で前倒しで作り、`TransactionRepository` は**使用中の確認の3メソッドだけ**。画面と、一覧などの検索は Phase 5<br>B：Entity は作らず、`EntityManager` のネイティブ SQL（パラメータ付き）で件数を数える専用のクラスを作る。Phase 5 で Entity を作ったら、置き換える<br>C：Entity に、`category_id` などを関連でなく**数値の列だけ**で持たせて作る。Phase 5 で関連に直す | **A**（設計書 4.6 のとおりの形で、Phase 5 でやり直しが出ない。`EntityMappingTest` で列の対応も確かめられる。B・C は Phase 5 で作り直す手間と、設計書 P7 からのずれがある） | ☑ **A**（おすすめどおり） |
| Q4-2 | **「使用中」の状態の作り方と、確かめ方**。取引を登録する画面は Phase 5 なので、Phase 4 の画面では取引を作れない | A：自動テストでは、`Transaction` を Repository で保存して作る（取り消される形）。**画面の確認では、`hb_app` で `INSERT` の SQL を自分で実行して取引を1件入れ、確認後に `DELETE` で消す**（4.9 の(2)）<br>B：画面での「使用中」の確認は、Phase 5 の取引の登録ができてからにする（Phase 4 は自動テストだけで確かめる）<br>C：A に加えて、Phase 5 の完了条件にも「使用中は削除できない」の画面確認を足す | **A**（Phase 4 の完了条件が「削除の制限が、画面のメッセージとして表示される」なので、Phase 4 の中で確認する。SQL の書き込みは `hb_app` の権限（`INSERT`、`DELETE`）の範囲で、あなたが行う）。**C も足してよい**（Phase 5 で取引の画面ができたら、もう一度確かめられる） | ☑ **A に C を足す**：自動テストでは `Transaction` を Repository で保存して作る。画面の確認は、SQL で取引を1件入れて確かめる（確認後に消す）。**Phase 5 で取引の画面ができたら、画面からも確かめ直す**（実行計画書の Phase 5 にも書いた） |
| Q4-3 | **前後の空白を取り除く仕組み**（Q3-7 から持ち越し）。`strip()` を使う（全角の空白を取るため）。Phase 4 の Form は3つで、名前が1項目ずつ | A：**各 Form のセッターで `strip()`**（Phase 3 の `RegisterForm` と同じ）。3つの Form に同じ数行を書く<br>B：Controller に共通の仕組み（`@InitBinder` と、`strip()` を使う自作の変換）を置き、パスワード以外の文字列を取り除く。Phase 5 の摘要（空なら NULL、3.6）にも使える<br>C：Spring 標準の `StringTrimmerEditor` を使う（**全角の空白を取らないので使えない**） | **A**（Form が3つで1項目ずつなので、共通の仕組みを作る手間のほうが大きい。`RegisterForm` と同じ形で、単体テストで確かめられる）。Phase 5 の摘要を作るとき、B が要るかを改めて判断する。決まったら設計書 6.4 に書く | ☑ **A**（おすすめどおり。各 Form のセッターで `strip()`）。Phase 5 の摘要を作るときに、共通の仕組みが要るかを改めて判断する |
| Q4-4 | **自動テストの範囲**（Phase 3 の Q3-8 と同じ後始末：トランザクションで取り消す） | A：**科目も取引先も支払方法も**、Service のテストと画面のテスト（MockMvc）を、同じ内容で書く（3つの Controller・Service は別々のクラスなので、それぞれ確かめる。他人のID の 404、重複、削除の制限、入力値が残る、を含む）<br>B：科目は A と同じように全部書き、取引先・支払方法は Service のテストと、代表的な画面のテスト（他人のID の 404、削除の制限）だけにする<br>C：科目の Service のテストだけ書き、画面は手で確かめる | **A**（3つは同じ形のコピーなので、書き写し間違いが起きやすい。他人のデータが見えないことは、実行計画書のリスク対策で最重要としているため、3つとも自動で確かめる） | ☑ **A**（おすすめどおり） |
| Q4-5 | **削除の確認ダイアログ（JavaScript）の置き場所**。設計書 2.1 は「削除ボタンを押すと確認ダイアログ（『削除しますか？』）」、章1は「確認ダイアログにだけ、少量の標準の JavaScript を使う」としているが、置き場所は未記載 | A：削除フォームの `onsubmit` 属性に、固定の文言で書く（例：`onsubmit="return confirm('削除しますか？')"`）。別のファイルは作らない<br>B：小さな JavaScript のファイル（`static/js/…`）を作り、削除フォームに印を付けて読み込む。ファイルが1つ増える | **A**（ファイルを増やさず、一番簡単なため。固定の文言で、利用者の入力を埋め込まないので、7.5 にも合う） | ☑ **A**。ただし理由を直した：ログイン後の画面から読む JavaScript のファイルは、ログイン必須のままでも読める（ブラウザが Cookie を付けて読みに行く）ので、`/js/**` を許可する必要はない（手順書の初版の「設計書 7.3 を変える」という説明は誤りだった）。A を選ぶ理由は「ファイルを増やさず、一番簡単なため」 |
| Q4-6 | **成功メッセージのキーの名前**（設計書 2.6 に文言はあるが、キーの名前は未記載） | A：3つのマスタで共通のキー（`success.added`（追加しました）、`success.updated`（変更しました）、`success.deleted`（削除しました））を使う。Phase 5 の取引（「登録しました」など）は、別のキーを足す<br>B：マスタごとにキーを分ける（`success.category.added` など） | **A**（文言が同じなので、1か所で直せる、6.8） | ☑ **A**（おすすめどおり） |

## 6. 完了条件

実行計画書の完了条件に、確認方法を加えたものです。次のすべてを満たしたら Phase 4 は完了です。

- [ ] 科目・取引先・支払方法の3つとも、一覧・追加・変更・削除ができる（4.9、4.10、4.11）
- [ ] 3つとも、入力エラー（空、文字数超過、重複）のとき、入力値が残り、項目の横にエラーが出る（科目10文字、取引先20文字、支払方法10文字）
- [ ] 別ユーザーのデータが一覧に出ない。他人のID・存在しないID・数字でないIDを URL に直接入れても、**404（画面も状態コードも）**で、他人のデータがあるかどうかは分からない。変更・削除の送信でも同じ（4.9 の(3)(5)、4.8 のテスト）
- [ ] すべての検索・更新・削除が「ID と `user_id` の両方」で絞られている（Repository のメソッドがすべて `userId` を引数に取る、4.12）
- [ ] 取引で使用中の科目・取引先・支払方法は削除できず、「取引で使われているため削除できません」が画面に出る（4.9 の(2)）
- [ ] いずれかの季節に0円でない予算がある科目は削除できず、「予算が0円ではない季節があるため削除できません」が画面に出る。両方に当てはまるときは、「使用中」だけが出る（F1）
- [ ] 削除できる科目は、その科目の予算の行（どの季節も）が消えてから、科目が消える。予算が未設定の季節は削除をさまたげない
- [ ] 科目を追加すると、今の季節の予算0円の行が1つ作られる（同じトランザクション。ほかの季節は作らない）
- [ ] 重複の判定が、設計書 3.12 のとおり（`A`/`a`、`ひ`/`ヒ`、`ｱ`/`ア` は同じ、`カ`/`ガ` は別）。他のユーザーが同じ名前を持っていても追加できる
- [ ] 変更で、名前を変えずに保存してもエラーにならない
- [ ] 前後の空白（全角を含む）が取り除かれて保存される。`strip()` を使い、`trim()` と `StringTrimmerEditor` を使っていない
- [ ] 削除ボタンで確認ダイアログが出る（3つとも）
- [ ] メニューに、科目・取引先・支払方法が出る
- [ ] `.\mvnw test` が全部成功し、開発用の DB にテストの行が残っていない（取引の行も残っていない）
- [ ] 設計書の記述が、実際に作ったものと合っている（4.13）
- [ ] GitHub に push されていて、パスワード・接続先・確認用アカウントのパスワードが含まれていない（4.14）
- [ ] 実行計画書の進捗表で Phase 4 を ☑ にし、Phase 4 の節を概要とリンクだけに縮めた。後続 Phase に影響する決定（Q4-1〜Q4-6）を反映した
- [ ] `main` に取り込み、タグ `phase-4-done` を付けて push した

## 7. つまずいたとき

| 症状 | 考えられる原因 | 対処 |
|---|---|---|
| 他のユーザーの科目が、一覧に出る | 一覧の検索が `user_id` で絞られていない | Repository の一覧が `findByUserIdOrderByIdAsc` になっているか、Service が `userId` を渡しているか確認する |
| 他人のIDの変更画面が、404 でなく開ける | 「ID だけ」で探している | `findById` を使っていないか確認する。`findByIdAndUserId` にする（3.1） |
| 他人のIDで、404 でなく 500 になる | `NotFoundException` を `GlobalExceptionHandler` が受けていない／`Optional` を `get()` している | `GlobalExceptionHandler` と、Service の `orElseThrow` を確認する |
| 404 の画面は出るが、状態コードが 200 | 例外の変換で、状態コードを設定していない | `GlobalExceptionHandler` で、状態コードも 404 にする（7.7）。開発者ツールの「ネットワーク」で確かめる |
| `/categories/abc` が 400 の画面になる | ID が数字でないときの変換が無い | `GlobalExceptionHandler` で、型の変換エラーも 404 にする（S4） |
| 名前を変えずに保存すると、「すでにあります」になる | 変更の重複確認に、自分自身を除いていない | `existsByUserIdAndNameAndIdNot` で、この行を除く（5.6） |
| 全角の空白のついた名前が、別の名前として登録できる | `trim()` や `StringTrimmerEditor` を使っている | `strip()` を使う。全角の空白（`U+3000`）の単体テストで確かめる |
| `カ` と `ガ` が重複になる／ならない | DB の照合順序（`utf8mb4_0900_as_ci`）と食い違っている | Phase 2 の結果（設計書 3.12）を確認する。`schema.sql` の `COLLATE` が表に付いているか、`SHOW CREATE TABLE` で確かめる |
| 科目を削除すると、外部キーのエラー（500）になる | 先に予算の行を消していない | 削除の順番（予算の行を消してから科目）と、同じトランザクションになっているかを確認する（3.7、5.5） |
| 科目を追加したが、予算の行ができない | トランザクションが付いていない／季節が合わない | `CategoryService` の追加にトランザクションがあるか、`Clock` と `SeasonPeriod` を確認する |
| 削除できないはずの科目が、削除できてしまう | 「いずれかの季節」でなく、今の季節だけを見ている | 判定は、**すべての季節**の予算（`user_id` と `category_id` で絞る）を見る |
| 削除ボタンに確認ダイアログが出ない | `onsubmit` の書き方／JavaScript の無効化 | 固定の文言になっているか、`return confirm(…)` の形か確認する |
| 画面に科目名の `<script>` がそのまま動く | `th:utext` を使っている | `th:text` にする。検索して `th:utext` が無いことを確かめる（7.5） |
| 取引の行を SQL で入れたまま、`.\mvnw test` が失敗する | 開発用の DB に取引の行が残っている | 自分で `DELETE` で消す。確認後は必ず消す（4.9 の(2)） |
| `Transaction` の追加後、`.\mvnw test` が失敗する | Entity の列名・型が `transactions` の表と食い違っている | `EntityMappingTest` の失敗の内容と、`schema.sql` を見比べる |
| パスワードをコミットしてしまった | — | ファイルを消すだけでは履歴に残る。**そのパスワードを変更してから**、相談する（7.9） |

## 8. 次の Phase への引き継ぎ

- Phase 5 では、`TransactionRepository`（Phase 4 では使用中の確認の3メソッドだけ）に、一覧・1件取得・集計などの検索を足します。仮の取引一覧（`TemporaryTransactionController`、`transactions/list.html`）を、本物に置き換えます。
- Phase 5 の取引の登録・更新では、選んだ科目・取引先・支払方法が**自分のものか**を、Phase 4 で作った型（ID と `userId` の両方で探す）で Service が確認します（3.9）。
- Phase 5 で、取引の画面ができたら、「使用中は削除できない」を画面から確かめ直します（Q4-2 の C。実行計画書の Phase 5 にも書いた）。
- Phase 6 では、`BudgetRepository` に、予算設定の検索を足します。科目を追加すると今の季節の予算0円の行ができること（Phase 4）が、予算設定画面の「初期値」に関係します。
- 設計書の章8に残るのは D5（`open-in-view`、Phase 5 で決める）です。
