# issue 034：取引先名を 30 文字に広げる

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は、[issue 034](../issues/034-取引先名を30文字に広げる.md)（取引先名の上限を 20 文字から 30 文字に広げる）を進めるための手順書です。

> **状態（2026年11月23日）**：◐ **作業中**。段階 1〔文書〕、段階 2〔本人の `ALTER`〕、段階 3〔テストとコード。`.\mvnw clean test` は 3,019 件成功〕を行った。次は段階 4〔仕上げと、本人のブラウザでの確かめ〕。

- ブランチ：`feature/issue-034-vendor-name-30`。秘密情報：パスワード・接続先の値は、どこにも書かない。DB のパスワードを入力する作業〔`mysqldump`、`root` での `ALTER`〕は本人が行う。
- 進め方：1 つの段階が終わったら止まって、確認を待つ。変更は、文書が先〔要件定義書 → 設計書 → この手順書〕、コードは後。**テストが先**〔失敗を確かめてから直す〕。コミットと push は本人。
- 今の決まりのまま：`findById` は使わない、`th:utext` は使わない、CSRF 対策は無効にしない。
- 変えるのは取引先だけ。科目・支払方法〔10 文字〕、摘要〔30 文字〕は変えない。
- 実行計画書の進捗表には、034 の行を足さない〔表は 015 で止まっていて、019 以降の行がないため。034 だけ足すと表が食い違う〕。

## 1. 決まったこと〔2026年11月23日・本人〕

| 番号 | 決まったこと |
|---|---|
| 1 | 段階は **1 文書 → 2 本人が `ALTER`〔コードは変えない〕→ 3 テストを先に書いてからコード → 4 仕上げ**。DB を先に広げるので、どこで止めても動く〔DB 30 文字 ≧ 入力チェック 20 文字〕。コードを先にすると、21〜30 文字で DB のエラー〔500〕になる |
| 2 | `ALTER` は、`NOT NULL` と照合順序を書く形〔3.2 の ③〕。前と後の確かめ〔件数・最長・fingerprint、`SHOW CREATE TABLE`、`information_schema`〕も 3.2 のとおり |
| 3 | 取引先の変更画面の欄の幅は、**今回は変えない**。段階 4 でブラウザを見てから決める |
| 4 | 昔の記録〔Phase 4・8 の手順書、`phase-1-review.md`、issue 025 の手順書〕の「20 文字」は直さない。実行計画書は、Phase 4 の概要に「（issue 034 で 30 文字に）」と書き足すだけ。進捗表には 034 の行を足さない |
| 5 | 理由：店名に支店名まで入れると、20 文字に収まらないため〔本人〕 |

## 2. 調べた結果〔2026年11月23日〕

### 2.1 「20 文字」を前提にしている所〔コードと文書を読んで確かめた〕

| 種類 | 場所 | 中身 | 直す段階 |
|---|---|---|---|
| DB | `schema.sql` 40 行 | `name VARCHAR(20) NOT NULL` | 2〔`ALTER`〕・3〔`schema.sql`〕 |
| Entity | `Vendor.java` 25 行 | `@Column(... length = 20)`〔`ddl-auto=none` なので動きには効かない。書いてあるだけ〕 | 3 |
| Form | `VendorForm.java` 8 行〔コメント〕、17 行 | `@Size(max = 20)` | 3 |
| メッセージ | `messages.properties` 51 行 | 取引先名は20文字以内で入力してください | 3 |
| 画面 | `vendors/list.html` 16 行、`vendors/edit.html` 14 行 | 「20 文字以内」の小さな文字 | 3 |
| テスト | `VendorFormTest` 17・45〜48・54 行 | 20 文字と 21 文字の境目、空白を取ったあとの 20 文字 | 3 |
| テスト | `VendorControllerTest` 44・52・248・256・343 行 | `TOO_LONG`〔21 文字〕、エラーの文、20 文字で追加できること | 3 |
| テスト | `VendorServiceTest` 115 行 | 20 文字を **DB に保存する** | 3 |
| テスト | `MessagesTest` 28 行、`MasterListLayoutTest` 52 行 | エラーの文。「20 文字以内」の表示〔一覧と変更の 2 つのテストで使う〕 | 3 |
| 文書 | 要件定義書 5 章、設計書 2.3・3.1・3.4・3.10・4.7・6.2、`docs/features.md`、issue 013 | 5 章 | 1〔済み〕 |

- 画面の `maxlength` は、どこにも使っていない〔取引の登録画面も「付けない」。G1〕。
- README には「20 文字」と書いていない。テストの件数〔2 か所〕と `docs/testing.md` の件数は、段階 4 で直す。

### 2.2 DB の変更の影響

- 確かめたこと：一意の決まり `uk_vendors_user_name` は (user_id, name) の組。取引から取引先へのつながり〔外部キー `fk_transactions_vendor`〕は `vendors.id` を指し、名前は使わない。テストは開発用の DB につなぐので、照合順序が変わっていないことは、今ある重複のテスト〔`VendorServiceTest.addDuplicateIsRejected`、`addWithDifferentVoicedMarkIsAllowed`〕が、段階 2 の ⑤ で確かめる。
- 推測〔MySQL の仕組みからの考え。試してはいない〕：広げるだけなので、今の値は変わらず、重複も新しくできない。一意の決まりに使う大きさは約 120 バイトで、上限〔3,072 バイト〕より十分小さい。長さを覚えておく所は 1 バイトのまま〔255 バイト以下〕なので、表を作り直さずに済む見込み。行が少ないので、作り直しになっても一瞬。

### 2.3 照合順序で「同じ名前」になるもの〔段階 1 で確かめ直した〕

調べものの報告〔2026年11月23日〕の 2 章の書き方が、「ひらがなとカタカナは別」と読める形になっていた。**正しくは「ひらがなとカタカナは同じ名前」**〔重複になる〕。根拠：

- 設計書 3.12：`utf8mb4_0900_as_ci` は「ひらがなとカタカナ（`ひ` と `ヒ`）」を同じとみなす。実験の表でも `ひ` と `ヒ` は 1〔同じ〕。
- `VendorService` の重複の確認は、`VendorRepository.existsByUserIdAndName`〔DB で比べる。DB の照合順序が効く〕。
- `VendorServiceTest.addDuplicateIsRejected` に、「ひらがな」と「ヒラガナ」〔両方の順〕が重複になる組がある。濁点だけが違う「カ」と「ガ」は別の名前〔`addWithDifferentVoicedMarkIsAllowed`〕。

| 同じ名前とみなす〔重複になる〕 | 別の名前 |
|---|---|
| 大文字と小文字、ひらがなとカタカナ、全角と半角 | 濁点の有無〔「カ」と「ガ」〕 |

### 2.4 取引先名が出る画面〔場所は確かめた。崩れ方は推測〕

| 画面 | 場所 | 崩れそうか〔推測〕 |
|---|---|---|
| 取引一覧の「取引先」の列 | `transactions/list.html` 70 行 | 折り返しを止める指定がないので、摘要も長い行では 2 行以上になりそう。横のスクロールは出にくい |
| 取引一覧の削除の確認〔分割の行だけ〕 | `transactions/list.html` 85 行 | 確認の窓の中で折り返すだけ |
| 取引の登録・編集・複写の選択肢 | `transactions/form.html` 49 行 | 幅 992px 以上では欄が約 280px で、見えるのは約 14 文字〔今の 20 文字でもすでに切れている見込み〕。開いた一覧には全部出る |
| 取引先の一覧の表 | `vendors/list.html` 38 行 | パソコンの幅では 1 行に入る見込み |
| 取引先の一覧の追加の欄 | 欄の幅は最大 28rem〔448px〕 | 見えるのは約 26 文字 |
| 取引先の変更 | `vendors/edit.html` 15 行〔画面の幅 420px〕 | 見えるのは約 20 文字〔決まったこと 3。段階 4 で見て決める〕 |
| 取引先の削除の確認 | `vendor.delete.confirm` | 窓の中で折り返すだけ |
| CSV | `TransactionCsvWriter.java` 72 行 | 長さで変わる所はない。先頭に「'」を付けた値は 31 文字になる〔issue 013 に書き足した〕 |

- 出ない所〔確かめた〕：トップ、予実比較、予算設定、きょうのひとこと、絞り込んだときの見出し〔科目名だけ〕、成功やエラーの文〔名前を入れない〕。
- 前からある性質〔推測。今回で変わらない〕：Java の文字数のチェックは、絵文字 1 つを 2 文字と数える。

## 3. 段階とコミット〔コミットは本人〕

| 段階 | すること | ここで止めたとき |
|---|---|---|
| 1 | 文書〔済み。4 章〕 | アプリは 20 文字のまま動く。段階 3 までは、文書〔30〕とアプリ〔20〕が食い違う |
| 2 | 本人が `ALTER`〔3.2〕。コードは変えない | DB が 30、入力チェックが 20 なので、問題なく動く |
| 3 | テストを先に書く → コード → 関係するテスト → 最後に `.\mvnw clean test`〔3.3〕 | 完成 |
| 4 | 仕上げ〔3.4〕 | — |

### 3.1 段階 1：文書〔2026年11月23日に行った〕

直したものは 4 章。

### 3.2 段階 2：本人が流す SQL〔コードより先〕

**注意**
- パスワードは書かない。`-p` を付けると、打つときに聞かれる。
- `ALTER` は、その場で確定する〔`ROLLBACK` で戻せない〕。先に控えを取る。
- アプリを止めてから行う〔`.\mvnw spring-boot:run` を動かしていれば、止める〕。
- 控えのファイルは、**リポジトリの外**に置く〔demo のデータとユーザー名・パスワードのハッシュが入るため〕。
- Phase 9 で作った「まっさらな環境」の DB は、もう残っていない〔2026年11月23日・本人が `SHOW DATABASES` で確かめた。`householdbudget` だけ〕。`ALTER` は `householdbudget` の 1 つだけでよい。新しく作る環境は、段階 3 で直した `schema.sql` で、最初から `VARCHAR(30)` になる。

**① 控えを取る**〔PowerShell。リポジトリの外で。`root` で行う〕

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\hb-backup"
mysqldump -u root -p --single-transaction --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file="$env:USERPROFILE\hb-backup\householdbudget-before-issue034.sql" householdbudget
Get-Item "$env:USERPROFILE\hb-backup\householdbudget-before-issue034.sql" | Select-Object Name, Length
Select-String -Path "$env:USERPROFILE\hb-backup\householdbudget-before-issue034.sql" -Pattern 'CREATE TABLE .vendors. ' | Select-Object -First 1
```

- `--result-file` を使う〔PowerShell 5.1 の `>` で書き出すと、文字コードが UTF-16 になり、戻すときに読めないことがあるため〕。`--set-gtid-purged=OFF` を付ける〔issue 032 の手順書 3.3 の 3-2〕。
- 確かめ：ファイルの大きさ〔Length〕が 0 でない。2 つ目の命令で、`vendors` のテーブルを作る行が 1 行出る。

**② 流す前の確かめ**〔`mysql -u root -p --default-character-set=utf8mb4 householdbudget` で入る〕

```sql
SHOW CREATE TABLE vendors;
SELECT COUNT(*) AS vendor_count,
       MAX(CHAR_LENGTH(name)) AS longest,
       BIT_XOR(CRC32(CONCAT_WS('|', id, user_id, name))) AS fingerprint
FROM vendors;
SELECT COUNT(*) AS transaction_count FROM transactions;
```

- `fingerprint` は、全部の行の id・持ち主・名前から作る数〔いわば指紋〕。1 文字でも変わると、ほぼ必ず違う数になる。
- 出た 4 つの数〔`vendor_count`、`longest`、`fingerprint`、`transaction_count`〕を、7 章に書く。`longest` は 20 以下のはず。

**③ 広げる**〔同じ `mysql` の中で〕

```sql
ALTER TABLE vendors
    MODIFY COLUMN name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL;
```

- `MODIFY` は列の決まりを丸ごと書きかえる。**`NOT NULL` を書き忘れると、空〔NULL〕を入れられる列になる**。照合順序は、設計書 3.12 の決まりどおり明記する。
- `hb_app` の権限は変えない〔データベースの単位で与えてあるので、そのまま効く〕。

**④ 流したあとの確かめ**〔同じ `mysql` の中で〕

```sql
SHOW CREATE TABLE vendors;
SELECT COLUMN_TYPE, IS_NULLABLE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendors' AND COLUMN_NAME = 'name';
SELECT COUNT(*) AS vendor_count,
       MAX(CHAR_LENGTH(name)) AS longest,
       BIT_XOR(CRC32(CONCAT_WS('|', id, user_id, name))) AS fingerprint
FROM vendors;
SELECT COUNT(*) AS transaction_count FROM transactions;
```

- `SHOW CREATE TABLE`：`name varchar(30) NOT NULL` の行がある。`UNIQUE KEY uk_vendors_user_name (user_id,name)` と外部キー `fk_vendors_user` は前と同じ。表の最後は `COLLATE=utf8mb4_0900_as_ci`。列の行に `COLLATE` が出ないのは、表と同じだから〔推測。違うときだけ出る〕。
- `information_schema`：`varchar(30)`・`NO`・`utf8mb4`・`utf8mb4_0900_as_ci`。
- 4 つの数が、②と同じ。

**⑤ 今のテストが通ることを確かめる**〔テストも同じ開発用の DB を使うため〕

- `exit` で `mysql` を出て、リポジトリで `.\mvnw clean test` を流す。**今の 3,017 件が、失敗 0 で通る**〔コードは変えていない。重複のテストが、照合順序が変わっていないことも確かめる〕。

**元に戻すとき**
- 列だけ戻す〔`ALTER` は成功したが、やめたいとき〕：

```sql
ALTER TABLE vendors
    MODIFY COLUMN name VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NOT NULL;
```

- 21 文字以上の名前があると、MySQL が受け付けないはず〔推測。切り詰めずにエラーになる〕。段階 3 のあとで長い名前を登録していたら、先にその名前を 20 文字以下に直す。
- データがおかしくなったとき：①の控えから戻す〔**控えを取ったあとに入れたデータは消える**〕。アプリを止めて、`mysql -u root -p --default-character-set=utf8mb4 householdbudget` で入り、`source C:/Users/（ユーザー名）/hb-backup/householdbudget-before-issue034.sql` を流す〔パスは `/` で区切って全部書く〕。そのあと `SHOW CREATE TABLE vendors;` で `varchar(20)` に戻ったこと、②の 4 つの数と同じことを確かめる。
- `ALTER` が途中でエラーになったとき：MySQL の `ALTER` は、全部か何もしないかのどちらか。`SHOW CREATE TABLE vendors;` で `varchar(20)` のままなことを確かめ、エラーの文を Claude Code に見せる〔パスワードや接続先は見せない〕。

**完了の条件**：④の確かめが全部合う。⑤で 3,017 件が通る。結果〔②と④の数、⑤の件数〕を、本人が Claude Code に伝え、7 章に書く。

### 3.3 段階 3：テストとコード〔段階 2 のあと〕

**変えるもの**
- コード：`schema.sql`〔`VARCHAR(30)` と、issue 034 で広げたことのコメント。issue 032 の `receipt_group` のコメントと同じ書き方〕、`Vendor.java`〔`length = 30`〕、`VendorForm.java`〔`@Size(max = 30)` とコメント〕、`messages.properties`〔30文字以内〕、`vendors/list.html`・`vendors/edit.html`〔「30 文字以内」〕。
- テスト：2.1 の表のテスト 5 クラス。

**テストの見込み**
- 中身だけ変えるもの〔件数は変わらない〕：`VendorFormTest` 2 つ、`VendorControllerTest` 3 つ〔`TOO_LONG` は 31 文字に〕、`VendorServiceTest` 1 つ、`MessagesTest` 1 行、`MasterListLayoutTest` 1 行〔2 つのテストで使う〕。
- 足す案：`VendorControllerTest` に「変更で 30 文字に変えて保存できる」〔+1。今は、変更で最大の長さを試すテストがない〕。`VendorServiceTest` に「更新で 30 文字を DB に保存できる」〔+1〕。
- 件数：3,017 件 → 3,018〜3,019 件の見込み。
- コードを書く前に失敗する見込み：約 9 件〔Form 2、Controller 4〔足す 1 を含む〕、Messages 1、MasterListLayout 2〕。`VendorServiceTest` の 30 文字のテストは、段階 2 のあとなので、コードの前から通る見込み〔Service は文字数を確かめず、DB が受け入れるため〕。
- `information_schema` で列を確かめるテストは足さない〔30 文字の保存のテストと、重複のテストで足りる〕。

**わざと壊して確かめる**〔入力チェックは守りに関わる所〕
- `VendorForm` を一時的に `@Size(max = 31)` にして、31 文字を使う 3 件〔`VendorFormTest.lengthBoundary`、`VendorControllerTest` の追加と変更の文字数超過〕が失敗することを確かめ、`30` に戻す。
- 他人のデータ・CSRF は今回変わらないので、壊して確かめない。

**完了の条件**：関係するテストが通る。`.\mvnw clean test` が失敗 0。

### 3.4 段階 4：仕上げ

- README のテストの件数〔2 か所〕、`docs/testing.md` の総数と種類ごとの表。
- issue 034 とこの手順書の状態。
- 本人のブラウザでの確かめ〔下〕と、変更画面の欄の幅を決める〔決まったこと 3〕。

**本人がブラウザで確かめる手順**〔例の名前は、`wc -m` で文字数を数えた〕

| 使う名前 | 文字数 | 期待すること |
|---|---|---|
| `ひだまり商店街中央通り二丁目はなまる青果店本店東側売場前の店` | 30 | 追加できる |
| `みどりが丘ショッピングセンター二階フードコート東口前の売店Ａ`〔最後は全角の Ａ〕 | 30 | 追加できる |
| `みどりが丘ショッピングセンター二階フードコート東口前の売店ＡＢ` | 31 | 「取引先名は30文字以内で入力してください」。入力が残る |
| `ヒダマリ商店街中央通り二丁目ハナマル青果店本店東側売場前ノ店` | 30 | 「その取引先名はすでにあります」〔1 つ目の名前の、ひらがなをカタカナにしたもの。ひらがなとカタカナは同じ名前とみなす。2.3〕 |

1. 確かめ用のアカウント `csvtest1` でログインする〔`yutaro` と `demo` は使わない。データを変えないため〕。
2. 取引先の画面：上の表の 4 つを、上から順に追加する。一覧で 1 行に入るか。追加の欄で、30 文字がどこまで見えるか。
3. 変更の画面：30 文字の取引先を開き、欄でどこまで見えるかを見る〔決まったこと 3〕。そのまま「保存」できる。
4. 取引の登録：取引先を選び、閉じた欄でどこまで見えるかと、開いた一覧で全部見えるかを見る。明細を 2 行に分け、摘要も 30 文字にして保存する。
5. 取引一覧：その行の高さを、ウィンドウの幅を 1280px・約 1000px・約 768px にして見る。分割の行の「削除」で確認の文を見て、「キャンセル」を押す。
6. 編集と複写の画面で、選んだ取引先が入っている。
7. 「CSV で出力」して、Excel か Google スプレッドシートで、30 文字が全部入っている。
8. 片付け：取引を消してから、取引先を消す。

## 4. 段階 1 で直した文書

| 文書 | 前 | 後 |
|---|---|---|
| 要件定義書 5 章 | 取引先名は20文字以下とする。 | 取引先名は30文字以下とする。 |
| 設計書 2.3〔科目一覧の表〕 | 取引先は20文字以下 | 取引先は30文字以下 |
| 設計書 3.1 | 列を足すときは…〔issue 032〕 | 列を足す・列を広げるときは…〔issue 032、issue 034。手順は両方の手順書〕 |
| 設計書 3.4 | `VARCHAR(20)`、取引先名。20文字以下 | `VARCHAR(30)`、取引先名。30文字以下（issue 034 で 20 から広げた） |
| 設計書 3.10〔ER 図〕 | `VARCHAR_20 name` | `VARCHAR_30 name` |
| 設計書 4.7 | 名前（20文字以下） | 名前（30文字以下） |
| 設計書 6.2〔表と、下の注〕 | 20文字以下／取引先名は20文字以内で入力してください | 30文字以下／取引先名は30文字以内で入力してください |
| `docs/features.md` | 取引先は 20 文字以内 | 取引先は 30 文字以内 |
| issue 013 | 取引先名は 20 文字以下など | 取引先名は 30 文字以下など〔issue 034 で広げた。CSV 出力で「'」を付けた値は、「'」を外してから数える〕 |
| 実行計画書〔Phase 4 の概要〕 | 取引先 20文字 | 取引先 20文字（issue 034 で 30 文字に） |
| 新しく作った | — | issue 034、この手順書 |

## 5. 決まっていない点

| 番号 | 内容 | 決める段階 |
|---|---|---|
| Q034-1 | 取引先の変更画面〔幅 420px〕の欄の幅を広げるか〔見えるのは約 20 文字の見込み〕 | 段階 4〔ブラウザを見てから〕 |

## 6. 記録

- 2026年11月23日：調べもの〔2 章〕。本人が決まったこと 1〜5 を決めた。
- 2026年11月23日：**段階 1 を行った**〔4 章。コード・テスト・`schema.sql` は変えていない〕。調べものの報告の「ひらがなとカタカナ」の書き方の食い違いを確かめ、「同じ名前」が正しいことを 2.3 に書いた。
- 2026年11月23日：**段階 2 を本人が行った**〔7 章〕。
- 2026年11月23日：**段階 3 を行った**〔8 章〕。

## 7. 段階 2 の記録〔2026年11月23日・本人〕

- 控え：`hb-backup` のフォルダーに `householdbudget-before-issue034.sql`〔リポジトリの外〕。
- ②と④の数は同じ：`vendor_count` 26、`longest` 20、`fingerprint` 1646731234、`transaction_count` 189。
- `ALTER` は「Query OK, 0 rows affected」〔表を作り直さずに済んだ。2.2 の推測のとおり〕。
- 列は `varchar(30)`・`NO`・`utf8mb4`・`utf8mb4_0900_as_ci`。`uk_vendors_user_name (user_id, name)` と `fk_vendors_user` は前と同じく残っている。
- ⑤ `.\mvnw clean test`：3,017 件・失敗 0。
- Phase 9 の「まっさらな環境」の DB は残っていない〔`SHOW DATABASES` で `householdbudget` だけ〕。3.2 の注意を、これに合わせて直した。

## 8. 段階 3 の記録〔2026年11月23日〕

**テストを先に直した**〔コードを直す前〕
- 中身を変えた：`VendorFormTest`〔境目を 30・31 文字に、空白を取ったあとの 30 文字〕、`VendorControllerTest`〔`TOO_LONG` を 31 文字に、エラーの文、30 文字で追加〕、`VendorServiceTest`〔30 文字で追加〕、`MessagesTest`〔文〕、`MasterListLayoutTest`〔「30 文字以内」〕。
- 足した：`VendorControllerTest.editWithTheMaximumLengthSucceeds`〔変更で 30 文字に変えて保存できる〕、`VendorServiceTest.updateAcceptsTheMaximumLength`〔更新で 30 文字が DB に保存される〕。
- コードの前に流した結果〔関係する 5 クラス、140 件〕：**9 件が失敗**。見込みどおり。
  - 30 文字が入力チェックで止められた：`VendorFormTest` 2 件、`VendorControllerTest` の追加・変更の 30 文字 2 件〔移動先がない〕。
  - 文が「20文字」のまま：`VendorControllerTest` の文字数超過 2 件、`MessagesTest` 1 件、`MasterListLayoutTest` 2 件〔一覧と変更の画面〕。
  - `VendorServiceTest`〔25 件〕は全部通った。段階 2 で DB が広がっていて、Service は文字数を確かめないため〔3.3 の見込みどおり〕。

**コード**：`schema.sql`〔`VARCHAR(30)` と、広げたことのコメント〕、`Vendor.java`〔`length = 30`〕、`VendorForm.java`〔`@Size(max = 30)` とコメント〕、`messages.properties`、`vendors/list.html`・`vendors/edit.html`〔「30 文字以内」〕。関係する 5 クラスの 140 件が通った。

**わざと壊した**：`@Size(max = 31)` にすると、31 文字を使う 3 件が失敗した〔`VendorFormTest.lengthBoundary`、`VendorControllerTest.editWithBlankOrTooLongNameKeepsTheInputAndShowsError`、`addWithTooLongNameKeepsTheInputAndShowsError`〕。追加の 1 件は、入力チェックをすり抜けた 31 文字を **DB が拒んで**エラーになった〔`VARCHAR(30)` が 2 つ目の守りとして効いている〕。`@Size(max = 30)` に戻した。

**全体のテスト**：`.\mvnw clean test` は **3,019 件・失敗 0**〔前 3,017 件。`VendorControllerTest` +1〔38 → 39〕、`VendorServiceTest` +1〔24 → 25〕〕。
