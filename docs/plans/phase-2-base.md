# Phase 2：プロジェクトの土台づくり

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は Phase 2 を実際に進めるための手順書です。

- 前の Phase：[Phase 1 設計の確定](phase-1-design.md)（☑ 完了）
- 次の Phase：[Phase 3 アカウント登録・ログイン](execution-plan.md#phase-3アカウント登録ログイン)
- 状態：☐ 未着手（手順書の決定を反映済み。Q2-6 の照合順序だけ、実験のあとに決める）
- 作業ブランチ：`feature/phase-2-base`

## 1. 目的

機能を載せるための「空のアプリ」を動かします。具体的には、次の4つがそろった状態にします。

- Spring Boot のプロジェクトがあり、`mvnw` で起動する。
- アプリ専用のデータベースと DB ユーザーがあり、`root` を使わずに接続できる。
- `schema.sql` で6つのテーブルが作られる（照合順序は実験で決めてから確定する）。
- パスワードなどの秘密が、コードにもリポジトリにも入っていない。

## 2. 前提と、この Phase でやらないこと

- Phase 0・1 が完了している（JDK 25、MySQL 9.7.2、VS Code、Git が使える。設計書が確定している）。
- パソコンは Windows 11。シェルは PowerShell（VS Code のターミナル）。
- `root` のパスワードは、自分だけが分かる場所に控えてある（Phase 0）。**この手順書にも、コードにも、設定ファイルにも書かない。**
- やらないこと：Entity・Repository・Service・Controller（機能のコード）、ログインの作り込み、画面の見た目（CSS）。これらは Phase 3 以降。
- 設計書で決まっていない点が出たら、勝手に決めず、「5. 決まっていない点」に載せて確認する。

## 3. 使う道具と設計書との対応

| 作るもの | 設計書での根拠 |
|---|---|
| Spring Boot プロジェクト（Maven、Java 25） | 章1.2、Q18、Q19 |
| アプリ専用のデータベースと DB ユーザー | 1.5、7.9、D3 |
| 照合順序の実験 | 3.12、D1 |
| 環境変数 | 1.5、7.9、D2 |
| `schema.sql` | 章3、Q17 |
| `messages.properties` | 4.3、6.8、C2 |
| `.gitignore` | 7.9、S7 |
| パッケージ構成 | 4.2 |

### 3.1 Spring Initializr で確認した版（2026-10-27 に確認）

プロジェクトの作成画面（Spring Initializr）が実際に選ばせる値を、`start.spring.io` で確かめました。

| 項目 | 選べる値 |
|---|---|
| Spring Boot | 4.1.2、4.0.9（安定版）。ほかに 4.2.0-RC1、4.2.0-SNAPSHOT、4.1.3-SNAPSHOT、4.0.10-SNAPSHOT |
| Java | 17、21、25、27 |
| ビルド | Maven、Gradle |
| 形式 | jar、war |
| 依存関係の名前 | Spring Web、Thymeleaf、Spring Security、Spring Data JPA、Validation、MySQL Driver（すべて存在する）。ほかに Spring Boot DevTools など |

- **当初の設計書にあった 4.1.1 は、この一覧にありません。** 安定版の最新は 4.1.2 です。→ Q2-1 で **4.1.2** に決め、設計書 1.2 と Phase 1 手順書の Q18 を先に直しました。
- VS Code の画面に出る版は、この一覧と同じとは限りません。**手順 4.2 で、画面に出た版を必ず見比べます。**

## 4. 手順

各手順の最後に **確認** があります。確認ができたら、チェックを付けて次へ進みます。

### 4.1 作業ブランチを確認する

- [ ] `feature/phase-2-base` にいる
- [ ] MySQL のサービス（`MySQL97`）が動いている

**確認**：`git status` が `On branch feature/phase-2-base` と `working tree clean` を示す。

### 4.2 Spring Boot プロジェクトを作る（VS Code）

Phase 0 で入れた「Spring Boot Extension Pack」の中の Spring Initializr を使います。

> Q2-1（版）、Q2-2（名前）、Q2-3（置き場所）、Q2-4（DevTools）は決定済みです（「5. 決まっていない点」）。**作る場所は、リポジトリの外の空のフォルダ**です（Q2-3）。例：`C:\Users\yutar\dev\hb-initializr-tmp`（この名前は例。作業が終わったら消す）。

- [ ] VS Code でコマンドパレットを開く（`Ctrl + Shift + P`）
- [ ] `Spring Initializr: Create a Maven Project...` を選ぶ
- [ ] 次の順に答える。**各画面で、選べる値の一覧を見て、決めた値と同じものがあることを確かめてから選ぶ。**

| 順 | 画面の質問 | 選ぶ・入力する値 |
|---|---|---|
| 1 | Specify Spring Boot version | **4.1.2**（Q2-1）。`SNAPSHOT`・`RC` の付いたものは選ばない。**一覧に 4.1.2 がなければ、ここで止めて相談する** |
| 2 | Specify project language | Java |
| 3 | Input Group Id | `io.github.yutaro081`（Q19） |
| 4 | Input Artifact Id | `household-budget`（Q2-2） |
| 5 | Specify packaging type | Jar |
| 6 | Specify Java version | 25 |
| 7 | Search for dependencies | 下の6つを選ぶ（選び終えたら Enter を押さずに、`Selected N dependencies` を選んで進む） |
| 8 | 保存先のフォルダ | リポジトリの外の空のフォルダ（上の例）。フォルダの中に `household-budget` フォルダができる |

選ぶ依存関係（実行計画書の提案、設計書 1.2 と同じ）：

| 画面での名前 | 役割 |
|---|---|
| Spring Web | URL を受けて Controller を呼ぶ（Spring MVC） |
| Thymeleaf | 画面（HTML）を作る |
| Spring Security | ログイン・ログアウト・CSRF 対策 |
| Spring Data JPA | データベースの読み書き |
| Validation | 入力チェック |
| MySQL Driver | MySQL につなぐ |

- テスト用の部品（JUnit など）は、Initializr が自動で付ける。
- DevTools などの上の6つ以外は、設計書にないので選ばない（Q2-4：DevTools は入れない）。

**作ったものを、リポジトリの直下にコピーする（Q2-3）**

Initializr が作ったフォルダの中身を、リポジトリ（`HouseholdBudget-SpringBoot`）の直下にコピーします。**隠しファイル・隠しフォルダ（`.mvn` フォルダ、`.gitignore`、あれば `.gitattributes`）も必ずコピーします。** 隠しファイルは、エクスプローラーや `*` を使ったコピーでは、見えない・コピーされないことがあります。これが抜けると、`mvnw` が動かない（`.mvn`）、`target/` がコミットされる（`.gitignore`）事故になります。

- [ ] まず、コピー元に隠しファイルがあることを確かめる（`-Force` が隠しファイルも表示する印）

```powershell
Get-ChildItem -Force C:\Users\yutar\dev\hb-initializr-tmp\household-budget
```

- [ ] リポジトリの直下で、次のようにコピーする。**`README.md` は上書きしない**。`HELP.md` は不要なのでコピーしない（コピー元に `README.md` があっても除く）

```powershell
Get-ChildItem -Force C:\Users\yutar\dev\hb-initializr-tmp\household-budget | Where-Object { $_.Name -notin 'HELP.md', 'README.md' } | Copy-Item -Destination . -Recurse -Force
```

- [ ] コピーした後、隠しファイルが入ったかを確かめる

```powershell
Get-ChildItem -Force | Select-Object Name
```

- [ ] コピーが終わったら、リポジトリの外の作業用フォルダ（`hb-initializr-tmp`）を消す

**確認**：
- [ ] リポジトリの直下に **`.mvn` フォルダ**（中に `wrapper`）と **`.gitignore`** がある（`.gitattributes` は、コピー元にあれば）
- [ ] `README.md` の中身が変わっていない（`git diff README.md` に何も出ない）
- [ ] リポジトリの直下に `pom.xml`、`mvnw`、`mvnw.cmd`、`src/main/java`、`src/main/resources/application.properties` がある
- [ ] `pom.xml` の Spring Boot の版（`<parent>` の `<version>`）が **4.1.2**
- [ ] `pom.xml` の `<java.version>` が `25`
- [ ] 起動クラスの名前を見る。設計書 4.2 は `HouseholdBudgetApplication`、パッケージは `io.github.yutaro081.householdbudget`。**違っていたら、名前とパッケージを設計書に合わせて直す**（クラス名とパッケージを直したら、起動クラスのファイル名・`package` 行・テストクラスの `package` 行も合わせる）
- [ ] ターミナルで `.\mvnw -v` を実行し、Maven と Java 25 が表示される（初回は Maven のダウンロードで少し時間がかかる）

> Initializr は `.gitignore` と `HELP.md` を作ります。`.gitignore` は 4.9 で整えます。`HELP.md` は説明書なので、不要なら消します（コミットしない）。

### 4.3 パッケージ構成を作る

設計書 4.2 のパッケージを、`io.github.yutaro081.householdbudget` の下に作る。

- [ ] `controller`、`service`、`repository`、`entity`、`form`、`dto`、`security`、`exception`、`domain`、`config`

> Git は空のフォルダを記録しません。何も入っていないフォルダは、コミットしても GitHub に出ません。これは問題ありません（Phase 3 以降でクラスを置いたときに入ります。`.gitkeep` は置かない、Q2-9）。別のパソコンで clone したときは、フォルダが無くてもかまいません。

**確認**：VS Code のエクスプローラーで、10個のパッケージが見える。

### 4.4 アプリ専用のデータベースと DB ユーザーを作る（D3）

`root` は、**この手順のように管理の作業にだけ**使います。アプリは `root` を使いません（1.5、7.9）。

> **パスワードの扱い（重要）**
> - アプリ用 DB ユーザーのパスワードは、**自分で新しく決めた長めのもの**にする。`root` と同じものにしない。パスワード管理ツールか、自分だけが分かる場所に控える。
> - パスワードは、**手順書・コード・設定ファイル・コミットメッセージ・チャットに書かない**。この文書の `〔DBパスワード〕` は、実際の値を入れるための印であり、**値をここに書き戻さない**。
> - SQL を書いたファイルにパスワードを入れない。`mysql` のログイン後に、手で入力する。

- [ ] ターミナルで `mysql -u root -p` を実行し、`root` のパスワードを入力してログインする（パスワードはコマンドに続けて書かず、聞かれてから入力する）
- [ ] 文字コードを指定してデータベースを作る（名前は `householdbudget`、Q2-5）

```sql
CREATE DATABASE householdbudget CHARACTER SET utf8mb4;
```

> 照合順序（`COLLATE`）はここでは指定しません。4.5 の実験で決めてから、確定します（実験の結果で、データベースを作り直す場合があります）。

- [ ] アプリ専用のユーザーを作る（ユーザー名は `hb_app`、接続元は `localhost` のみ、Q2-5）。パスワードの部分は、ログイン後の画面で、自分で決めた値を入力する

```sql
CREATE USER 'hb_app'@'localhost' IDENTIFIED BY '〔DBパスワード〕';
```

- [ ] 権限を与える。**このデータベースだけ**に、最初は次の権限を与える（D3 の初期案。`ALL PRIVILEGES` は使わない）

```sql
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES ON householdbudget.* TO 'hb_app'@'localhost';
```

| 権限 | 必要な理由 |
|---|---|
| SELECT / INSERT / UPDATE / DELETE | アプリがデータを読み書きする |
| CREATE | `schema.sql` の `CREATE TABLE IF NOT EXISTS` |
| INDEX | `schema.sql` の索引・一意制約（3.6） |
| REFERENCES | `schema.sql` の外部キー（3.7） |

- `DROP`・`ALTER`・`GRANT OPTION` は与えない（`schema.sql` に `DROP TABLE` を入れない、Q17 のため）。
- 4.8 で `schema.sql` を実行して、**足りなければ足し、与えすぎていないかを見直す**。最終的な権限は、設計書 7.9 に書く（4.11）。

- [ ] 確認する

```sql
SHOW GRANTS FOR 'hb_app'@'localhost';
SHOW CREATE DATABASE householdbudget;
```

- [ ] `exit` で抜ける

**確認**：
- [ ] `SHOW GRANTS` に、`householdbudget` だけが出る（`*.*` に `ALL PRIVILEGES` が付いていない）
- [ ] `SHOW CREATE DATABASE` に `utf8mb4` が出る
- [ ] 新しいターミナルで `mysql -u hb_app -p householdbudget` を実行し、ログインできる（`SHOW DATABASES;` にこのデータベースしか出ない）

### 4.5 照合順序の実験をする（D1、3.12）

目的：名前の重複判定（`UNIQUE`）で、どの文字の違いを「同じ」とみなすかを決める材料を集めます。**結果の判断は、ここで集めた結果を見せて確認します（勝手に決めない）。**

実験は、**アプリ用データベースとは別の使い捨てのデータベース**で行います。`root` で入ります。実験後は消します。実験用の SQL ファイルは**リポジトリに入れません**（記録は設計書 3.12 に書く）。

- [ ] 使える照合順序を調べる

```sql
SHOW COLLATION WHERE Charset = 'utf8mb4' AND (Collation LIKE 'utf8mb4_0900%' OR Collation LIKE 'utf8mb4_ja%' OR Collation = 'utf8mb4_bin');
```

- [ ] 実験用のデータベースを作る

```sql
CREATE DATABASE collation_test CHARACTER SET utf8mb4;
USE collation_test;
```

- [ ] 候補ごとに試しの表を作る。**表の名前と列の照合順序だけを変えて**、次のひな形を使う。照合順序は、上の `SHOW COLLATION` に出たものだけを使う

| 試しの表 | 列の照合順序 | 狙い |
|---|---|---|
| `t_default` | `utf8mb4_0900_ai_ci`（標準） | 案1：標準のまま |
| `t_cs` | `utf8mb4_0900_as_cs` | 案2：大文字小文字・濁点を区別 |
| `t_ja` | `utf8mb4_ja_0900_as_cs_ks`（あれば） | 案2：かなも区別 |
| `t_bin` | `utf8mb4_bin` | 案2：すべて区別（バイト単位） |

```sql
CREATE TABLE t_default (name VARCHAR(20) COLLATE utf8mb4_0900_ai_ci NOT NULL, UNIQUE KEY uk_name (name));
```

- [ ] 各表に、**組みの1つ目を登録し、続けて2つ目を登録**する。2つ目が `Duplicate entry`（エラー 1062）になれば「同じとみなされる」、登録できれば「別の名前として扱われる」

| 組み | 1つ目 | 2つ目 |
|---|---|---|
| a | `A` | `a` |
| b | `カ` | `ガ` |
| c | `ひ` | `ヒ` |
| d | `ｱ`（半角） | `ア`（全角） |
| e | `コンビニ` | `ｺﾝﾋﾞﾆ`（半角） |

```sql
INSERT INTO t_default (name) VALUES ('A');
INSERT INTO t_default (name) VALUES ('a');
```

> 半角カナや全角文字を `mysql` コマンドに入力して化けるときは、`SET NAMES utf8mb4;` を先に実行します。それでも化けるときは、結果が信用できないので、先に文字化けを直します（6. つまずいたとき）。

- [ ] 結果を次の表に書き込む（○＝同じとみなされる（重複エラー）、×＝別の名前として登録できる）。**実験の都度、書く**

| 組み | `t_default` | `t_cs` | `t_ja` | `t_bin` |
|---|---|---|---|---|
| a `A` と `a` | | | | |
| b `カ` と `ガ` | | | | |
| c `ひ` と `ヒ` | | | | |
| d `ｱ` と `ア` | | | | |
| e `コンビニ` と `ｺﾝﾋﾞﾆ` | | | | |

- [ ] 結果を見せて、次を確認する（Q2-6）。
  - `username`：`A` と `a` を同じとみなす（Q10）ことができる照合順序はどれか
  - 科目・取引先・支払方法の名前：b〜e のどれを「同じ」とみなすか（案1〜3、3.12）
  - 列ごとに照合順序を変える（案2）場合、`schema.sql` でどの列にどの照合順序を書くか
- [ ] 決まったら、結果と選んだ案・理由を**1行ずつ**、設計書 3.12 に書く。章8の D1 を消す
- [ ] 実験用のデータベースを消す

```sql
DROP DATABASE collation_test;
```

**確認**：
- [ ] 上の結果表が埋まっている（5組み × 候補）
- [ ] 選んだ案と理由が、設計書 3.12 に書かれている
- [ ] `SHOW DATABASES;` に `collation_test` が残っていない

> `root` で入って作業するのは、ここまでです。実験のあと、`householdbudget` の既定の照合順序を変える場合は、決定に合わせて作り直します（データはまだ無いので、`DROP DATABASE householdbudget;` して 4.4 の最初の SQL をやり直してかまいません。権限は、データベースを作り直すと付け直しが必要です。ユーザーは消えません）。

### 4.6 環境変数を設定する（D2）

アプリは、DB への接続先・ユーザー名・パスワードを環境変数から読みます（1.5）。名前は次のとおりです（Q2-7）。値がないときは、起動を失敗させます（既定値は付けない。設定漏れに気づけるため）。

| 用途 | 環境変数の名前 | 入れる値 |
|---|---|---|
| 接続先 | `HB_DB_URL` | `jdbc:mysql://localhost:3306/householdbudget`（データベース名は 4.4 に合わせる） |
| DB ユーザー名 | `HB_DB_USER` | 4.4 で作ったユーザー名（`root` ではない） |
| DB パスワード | `HB_DB_PASSWORD` | 4.4 で決めたパスワード |

- [ ] Windows の「環境変数を編集」画面で、**ユーザー環境変数**として3つを追加する（スタートメニューで「環境変数」と検索）
- [ ] VS Code を**閉じて開き直す**（環境変数の変更は、開き直さないと反映されない。Phase 0 と同じ）

> PowerShell の `setx` や `$env:...` でパスワードを設定しない方がよいです。入力したコマンドが、PowerShell の履歴ファイルに平文で残るためです。画面（GUI）から入力すれば、履歴に残りません。
> 環境変数の「名前」だけが設計書・README に載ります。値は載せません（1.5、Phase 9）。

**確認**：新しいターミナルで次を実行する。

```powershell
$env:HB_DB_URL
$env:HB_DB_USER
```

- [ ] 接続先とユーザー名が表示される
- [ ] パスワードは、**画面に表示して確認しない**。代わりに次を実行して、設定されている（`True`）ことだけ確かめる

```powershell
[bool]$env:HB_DB_PASSWORD
```

> **これ以降の手順（アプリの起動、`.\mvnw test`）は、環境変数を設定して VS Code を開き直した後に行います。** 開き直す前に実行すると、環境変数が空になり、起動もテストも失敗します（7. つまずいたとき）。

### 4.7 `application.properties` を書く

設定ファイルはリポジトリに入れます。**パスワードも、接続先も、ユーザー名も値は書かず、環境変数の名前だけ**を書きます（1.5、7.9）。

書く項目（設計書にあるもの）：

| 項目 | 内容 | 根拠 |
|---|---|---|
| アプリ名 | `spring.application.name`（Initializr が書いている値のまま） | Initializr |
| DB 接続先・ユーザー・パスワード | `spring.datasource.url` / `username` / `password`。値は `${環境変数名}` の形で、4.6 の名前を参照する | 1.5、D2 |
| 起動のたびに `schema.sql` を実行 | `spring.sql.init.mode=always` | 1.6、Q17。MySQL のような外部のデータベースでは、これを書かないと `schema.sql` は実行されない |
| `messages.properties` の文字コード | `spring.messages.encoding=UTF-8` | 6.8、C2 |
| テーブルの自動作成・変更を止める | `spring.jpa.hibernate.ddl-auto=none`（テーブルは `schema.sql` で作る） | Q17、Q2-8 |
| SQL の値を表示するログをオンにしない | 特に何も書かない（`spring.jpa.show-sql` などを `true` にしない、ログの `TRACE` 設定を入れない） | S5 |

> **Phase 2 では書かない**（Phase 3 で行う、Q2-8）：セッション Cookie の設定（S1）。
> **未決（Q2-11）**：エラーの詳細を画面に含めない設定（S2：`server.error.include-stacktrace` など）を Phase 2 で書くか、`spring.jpa.open-in-view` を書くか。決まるまで書きません。

**確認**：
- [ ] `application.properties` を開いて、パスワード・実際のユーザー名・実際の接続先の値が書かれていない（`${...}` だけ）
- [ ] ファイルを検索して確認する（結果が空であること）

```powershell
Select-String -Path src\main\resources\application.properties -Pattern "password" -SimpleMatch
```

（`spring.datasource.password=${...}` の行だけがヒットするのは正常。`=` のあとに実際の値がないことを目で確かめる）

### 4.8 `schema.sql` を書いて、アプリを起動する

- [ ] `src/main/resources/schema.sql` を作る。次の決まりで、設計書 章3 のとおりに書く

| 決まり | 根拠 |
|---|---|
| 6つのテーブル：`users`、`categories`、`vendors`、`payment_methods`、`budgets`、`transactions` | 3.1 |
| すべて `CREATE TABLE IF NOT EXISTS`。**`DROP TABLE` は入れない** | Q17、3.1 |
| 作る順番：外部キーの参照先から（`users` → `categories`・`vendors`・`payment_methods` → `budgets`・`transactions`） | 3.7 |
| InnoDB、文字コード `utf8mb4`、`id` は `BIGINT` の `AUTO_INCREMENT` | 3.1 |
| 各列の型・NULL 可否・制約は 3.2〜3.6 の表のとおり。CHECK 制約（`budgets` の年・季節・額、`transactions` の金額）も入れる | 3.6 |
| 一意制約と索引：`users.username`、各マスタの `(user_id, name)`、`budgets` の `(category_id, season_year, season)`、`budgets` の `(user_id, season_year, season)`、`transactions` の `(user_id, transaction_date)` | 3.2〜3.6 |
| 外部キーの削除時の動きは、すべて `RESTRICT` | 3.7 |
| 照合順序は、4.5 の結果で決めたとおり | 3.12、D1 |
| **パスワードや、入力値を含めない**。初期データ（科目など）は入れない（Phase 3 でアプリが作る） | 7.4、7.9、S6、3.11 |

- [ ] アプリを起動する（VS Code のターミナルで、環境変数が入った状態で）

```powershell
.\mvnw spring-boot:run
```

- [ ] 起動のログを読む。

**確認**：
- [ ] ログの終わりに `Started HouseholdBudgetApplication`（起動クラス名）が出て、エラーで止まっていない
- [ ] `schema.sql` の実行でエラー（`Access denied`、`command denied` など）が出ていない。**出たら、足りない権限を確認して追加し（4.4）、追加した権限と理由を記録する**
- [ ] `schema.sql` を実行した状態で、**もう一度アプリを起動してもエラーにならない**（`IF NOT EXISTS` のため）
- [ ] ログに、パスワードやその値が出ていない

> **Spring Security の標準の動き（Q2-8）**：Spring Security は、設定が無い状態では、全 URL をログイン必須にし、起動のたびにコンソールに「生成されたパスワード」（`Using generated security password: ...`）を出します。Phase 2 の確認は、**この標準の動きのまま**行います（全 URL を許可する仮の設定は作らない。置き換え忘れると、ログインなしで全部見られる状態が残るため）。
> - ユーザー名は `user`、パスワードは**起動のたびに変わる**、コンソールの値を使う。
> - このパスワードは、手順書・コード・設定ファイル・コミットメッセージ・チャットに**書き写さない**。使うのは、画面でログインするときだけ。
>
> **起動できないとき**は、7. つまずいたとき を見る。

- [ ] アプリを止める（ターミナルで `Ctrl + C`）

**自動テスト（`contextLoads`）を実行する（Q2-10）**

Initializr が作った空のテスト（`contextLoads`）は、アプリ全体を起動します。**DB に接続できないと失敗する**ので、アプリが DB につながって起動できることの確認になります。

> 環境変数（4.6）を設定したあとで、**VS Code を開き直してから**実行します。開き直す前だと、環境変数が空で失敗します。

- [ ] 新しいターミナルを開き（VS Code を開き直した後）、リポジトリの直下で実行する

```powershell
.\mvnw test
```

**確認**：
- [ ] 最後に `BUILD SUCCESS` と、`Tests run: 1, Failures: 0, Errors: 0` が出る
- [ ] 環境変数が空の状態では失敗する（`Failed to configure a DataSource` など）。**失敗の例を一度見ておく必要はない**。成功すればよい

### 4.9 テーブルが作られたことを、アプリ用ユーザーで確かめる

`root` ではなく、**アプリ用ユーザー**で確かめます（4.4 で作ったユーザーで、必要な操作ができる確認にもなる）。

- [ ] `mysql -u hb_app -p householdbudget` でログインする
- [ ] 次を実行する

```sql
SHOW TABLES;
SHOW CREATE TABLE budgets;
SHOW CREATE TABLE users;
SELECT TABLE_NAME, CONSTRAINT_NAME FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = 'householdbudget';
```

**確認**：
- [ ] `SHOW TABLES` に6つ出る
- [ ] `SHOW CREATE TABLE` で、型・制約・文字コード・照合順序が、設計書 章3 と同じ
- [ ] CHECK 制約が、`budgets` と `transactions` に出る
- [ ] `users` に、行が1件も入っていない（既定のアカウントを作らない、S6）

> `schema.sql` を直して作り直したいときは、`CREATE TABLE IF NOT EXISTS` は、**すでにあるテーブルを変更しません**。開発中に作り直すときは、`root` で `DROP DATABASE`・`CREATE DATABASE`・`GRANT` をやり直します（今は中身が無いので安全です。Phase 3 以降は、データが消えることに注意）。

### 4.10 仮のページを表示し、`messages.properties` の文字化けを確かめる（C2）

Spring Security の標準の動き（4.8）のまま、**ログインしてから**仮のページを開きます。Security の設定は書き換えません（Q2-8）。

- [ ] `src/main/resources/messages.properties` を作る。**VS Code の右下で、文字コードが `UTF-8` になっていること**を確かめる（違うときは、クリックして「エンコード付きで保存」から UTF-8 を選ぶ）
- [ ] 確認用のメッセージを1つ、日本語で書く（例：`check.hello=日本語の表示確認：科目、取引先、支払方法`）。濁点・半濁点・長音・全角記号・ひらがな・カタカナ・漢字を混ぜる
- [ ] 仮のページを用意する（仮の Controller 1つと、仮のテンプレート 1つ。`GET /` に答える）。テンプレートで `th:text="#{check.hello}"` を使い、メッセージを表示する。ページの見た目は作らない
- [ ] アプリを起動する（`.\mvnw spring-boot:run`）。ブラウザで `http://localhost:8080/` を開く
- [ ] Spring Security の標準のログイン画面が出る（これは Spring Security の画面で、英語。正しい動き）。ユーザー名 `user`、パスワードはコンソールに出た値でログインする
- [ ] 仮のページが表示される

**確認**：
- [ ] 日本語のメッセージが、**そのまま読める形**で画面に表示されている（`????` や `ã` のような文字になっていない）
- [ ] 文字コードの設定（`spring.messages.encoding=UTF-8`、ファイルは UTF-8）を、設計書 6.8 に書く。章8の C2 を消す
- [ ] ログアウトしたい（ログイン状態を捨てたい）ときは、アプリを止めて起動し直す。起動し直すとパスワードが変わる

> 仮の Controller と仮のテンプレート、確認用のメッセージ（`check.hello`）は、Phase 3 で本物の `HomeController` と画面に置き換えます。**Security の設定には、仮のものを置きません**ので、置き換え忘れても、ログインなしでは見られない状態のままです。

### 4.11 設計書に結果を反映する

設計書が確定したあとの変更は、先に設計書を直します（Phase 1 の決まり）。決まった内容を書き、章8の項目を消します。

| 直す場所 | 書くこと |
|---|---|
| 1.2（版の表）、Phase 1 手順書の Q18 | Spring Boot の版（4.1.2）。**手順 4.2 の前に直し済み**。作成後に `pom.xml` の版が 4.1.2 であることを確かめる |
| 1.5 | 環境変数の名前（D2、Q2-7） |
| 3.12 | 実験の結果、選んだ案と理由（D1） |
| 3.2〜3.5 | 照合順序を列に指定した場合、その旨 |
| 6.8 | `messages.properties` の文字コード（C2） |
| 7.9 | アプリ用 DB ユーザーの最終的な権限（D3）。データベース名・ユーザー名の名前 |
| 8.2・8.3 | D1・D2・D3・C2 を消す。新しく出た未決事項があれば、章8に追記する |

**確認**：章8に、D1・D2・D3・C2 が残っていない。

### 4.12 `.gitignore` を整える（S7）

Initializr が作った `.gitignore` に、設計書 7.9 の S7 の6つを**足します**（Initializr の分は消さない）。

- [ ] 次の6つが入っている
  - `backup/`、`*.dump`、`*.dump.sql`、`*.bak`、`.env`、`application-local.properties`
- [ ] **`.sql` 全体は除外しない**（`schema.sql` はコミットする、S7）
- [ ] Initializr の既定で、`target/` などが除外されている

**確認**：次の確認を行う。実験用のダミーファイルは、確認後に消す。

```powershell
git check-ignore -v backup/x.sql a.dump a.dump.sql a.bak .env src/main/resources/application-local.properties
git check-ignore -v src/main/resources/schema.sql
git check-ignore -v target/x
```

- [ ] 1つ目：6つすべてが `.gitignore` の規則つきで表示される（除外されている）
- [ ] 2つ目：**何も表示されない**（`schema.sql` は除外されていない）
- [ ] 3つ目：`target/` が除外されている

### 4.13 コミット前の確認と、コミット・push

- [ ] `git status` で、含めたくないファイルが入っていないことを確かめる（7.9）。特に次を確かめる。
  - `target/`、`.env`、ダンプの `.sql`、`application-local.properties` が無い
  - 実験用の SQL ファイルが無い
- [ ] 変更の中身を目で見る（`git diff` と、新しいファイルの中身）。パスワード・実際の接続先・実際のユーザー名が、どこにも書かれていない
- [ ] 次の文字列を、リポジトリ全体から検索して、パスワードが含まれていないことを確かめる

```powershell
git grep -n -i -e "password" -- . ":(exclude)docs"
```

（`password_hash` の列名と、`${HB_DB_PASSWORD}` の参照だけが出るのが正常）

- [ ] 1つのタスクごとにコミットする（例：プロジェクト作成、`.gitignore`、`schema.sql`、`messages.properties`、設計書の反映）
- [ ] `git push` する

**確認**：GitHub の画面で、`application.properties` にパスワードや接続先の値が無いことを目で見る。

### 4.14 README に起動方法を書く

- [ ] `README.md` に、次を書く（実行計画書の Phase 2 のタスク。Phase 9 で仕上げる）。
  - 必要な環境（JDK 25、MySQL 9.7）
  - 設定する環境変数の**名前**（値は書かない）
  - データベースとユーザーの作り方の概要（**パスワードは書かない**）
  - `.\mvnw spring-boot:run` で起動する方法

> 共通の画面の枠、404/403/500 の画面、Cookie の設定（S1）は Phase 3 で行います（Q2-8）。README には、Phase 2 で確認した範囲（起動、ログインは標準の動き）だけを書きます。

## 5. 決まっていない点（設計書だけでは決まらないこと）

設計書とあなたの指示だけでは決まらない点です。**決まるまで、該当する手順は進めません。** 決定は、手順書の確認時に受け取った内容です。

| 番号 | 決めること | 選択肢 | おすすめ | あなたの決定 |
|---|---|---|---|---|
| Q2-1 | **Spring Boot の版**。設計書は 4.1.1 だが、Spring Initializr では選べない（3.1）。選べる安定版は 4.1.2 と 4.0.9 | A：4.1.2（4.1.1 と同じ 4.1 系の最新）<br>B：4.0.9<br>C：4.1.1 を、プロジェクト作成後に `pom.xml` で手で指定する | **A** | ☑ **A：4.1.2**。理由：4.1.1 は Initializr で選べなくなったため（4.1.2 は 4.1.1 の不具合修正版）。先に設計書 1.2 と Phase 1 手順書の Q18 を直した（反映済み） |
| Q2-2 | **Artifact Id**（Maven のプロジェクト名）と、起動クラス名。設計書にあるのはベースパッケージ `io.github.yutaro081.householdbudget`（Q19）と、起動クラス `HouseholdBudgetApplication`（4.2）だけ | A：`household-budget`（起動クラスは作成後に `HouseholdBudgetApplication` かを確認し、違えば直す）<br>B：`householdbudget` | **A** | ☑ **A**（提案どおり） |
| Q2-3 | **プロジェクトを置く場所**。Initializr は、選んだフォルダの下に新しいフォルダを作る。設計書の `docs` はリポジトリの直下にある | A：リポジトリの外で作ってから、中身をリポジトリの直下にコピーする（README.md は上書きしない。`.gitignore` は 4.12 で合わせる）<br>B：リポジトリの中に作り、サブフォルダのまま使う（`docs` はサブフォルダの外に残る） | **A** | ☑ **A**（提案どおり）。**補足**：コピーのとき、隠しファイル・隠しフォルダ（`.mvn`、`.gitignore`）も忘れずにコピーする（4.2 に明記） |
| Q2-4 | **DevTools**（コード変更時の自動再起動）を入れるか。設計書 1.2 にはない | A：入れない<br>B：入れる | **A**（設計書にない） | ☑ **A**（提案どおり。入れない） |
| Q2-5 | **データベース名と DB ユーザー名**。設計書に名前の指定がない | データベース名：`householdbudget`、ユーザー名：`hb_app`（提案）。接続元は `localhost` のみ | 提案どおり | ☑ 提案どおり |
| Q2-6 | **照合順序の最終判断**（D1）。実験の結果（4.5）を見たうえで、案1〜3のどれにするか。ユーザー名は `A` と `a` を同じとみなす（Q10） | 実験後に、結果を見せて決める | — | ☑ 実験の結果を見てから決める（**未決**。4.5 のあと） |
| Q2-7 | **環境変数の名前**（D2） | `HB_DB_URL`、`HB_DB_USER`、`HB_DB_PASSWORD`（提案）。値がないときは起動を失敗させる（既定値は付けない）。理由：設定漏れに気づけるため | 提案どおり | ☑ 提案どおり |
| Q2-8 | **Phase 2 に入れる範囲と、仮のページの扱い**。（a）Spring Security が入ると、全 URL がログイン必須になり、仮のページが見られない。（b）実行計画書の Phase 2 にある「共通の画面の枠（ヘッダー、メニュー）」「404/403/500 画面」（4.4.2）、Cookie の設定（S1、7.10）、Hibernate の `ddl-auto` を Phase 2 に入れるか | （a）<br>A：仮のページのためだけに、全 URL を許可する最小の設定を仮に置き、Phase 3 で本物に置き換える<br>B：Spring Security の標準のログイン画面が出ることをもって「画面が表示された」とする<br>（b）項目ごとに Phase 2／Phase 3 以降 | （a）A（手順書の初版の提案）<br>（b）`ddl-auto` は `none`。それ以外は実行計画書どおり | ☑ **（a）は A にしない**：仮に全 URL を許可する設定は作らない。理由：置き換え忘れると、ログインなしで全部見られる状態が残るため。Phase 2 は **Spring Security の標準の動き**（起動時にコンソールに出る仮のパスワードで、ユーザー名 `user` でログイン）で確認する。仮のページは、ログインしたあとに見る（4.10）<br>**（b）** `ddl-auto` は Phase 2 で `none`（テーブルは `schema.sql` で作るため）。共通の画面の枠、404/403/500 の画面、Cookie の設定（S1）は **Phase 3** で行う |
| Q2-9 | **空のパッケージの扱い**（4.3） | A：何も置かない（クラスを作ったときにフォルダが入る）<br>B：`.gitkeep` を置く | **A** | ☑ **A**（提案どおり） |
| Q2-10 | **自動テスト**。Initializr は、アプリ全体を起動する空のテスト（`contextLoads`）を作る。これは DB につながるので、環境変数が無い環境では失敗する | A：そのまま残す（`mvnw test` を実行する人にも、環境変数が必要）<br>B：Phase 8 まで外す／無効にする<br>C：テスト用の DB を別に用意する（設計書に決定なし） | **A** | ☑ **A**：`contextLoads` は残す。理由：アプリが DB につながって起動できることの確認になるため。環境変数を設定したあと、VS Code を開き直してから実行する（4.6、4.8 に手順を入れた） |
| Q2-11 | **（新しく出た点）エラー詳細を画面に含めない設定（S2）と、`spring.jpa.open-in-view` の扱い**。Q2-8 で「404/403/500 の画面は Phase 3」と決まったが、S2 の設定（`server.error.include-stacktrace=never` など）は画面を作らなくても書ける。`open-in-view` は設計書に記載がない（Spring Boot の既定は `true`） | S2：A：Phase 2 の `application.properties` に書く／B：Phase 3 の 404/403/500 画面と一緒に書く<br>`open-in-view`：A：書かない（既定のまま）／B：`false` にする | S2：**A**（設定の行だけで、早く入れておくほうが安全）<br>`open-in-view`：**A**（設計書にない設定は、足さない） | （未決。4.7 の該当行は、決まるまで書かない） |

> 設計書 1.2 と Phase 1 手順書の Q18 の「4.1.1」は、Q2-1 の決定に従い、**先に 4.1.2 へ直しました**。`docs/reviews/phase-1-review.md` の Q18 の行は、Phase 1 当時のレビュー記録なので、直していません。

## 6. 完了条件

実行計画書の完了条件に、確認方法を加えたものです。次のすべてを満たしたら Phase 2 は完了です。

- [ ] `.\mvnw spring-boot:run` でアプリが起動し、ブラウザで Spring Security の標準のログイン画面が出る。ユーザー名 `user` とコンソールの仮のパスワードでログインすると、画面（仮のページ）が表示される
- [ ] `.\mvnw test` が成功する（`contextLoads`。DB につながって起動できる、4.8）
- [ ] 全 URL を許可する仮の設定を作っていない（`SecurityConfig` を作っていない）
- [ ] アプリは、`root` ではなく、アプリ専用ユーザーで接続している（`HB_DB_USER` が `root` でない。4.6）
- [ ] 6つのテーブルが MySQL に作られていることを、アプリ用ユーザーで確認できる（4.9）
- [ ] 2回続けて起動しても、`schema.sql` でエラーにならない
- [ ] 照合順序の実験をして、結果と選んだ案が設計書 3.12 に書かれている（D1）
- [ ] 環境変数の名前が設計書 1.5 に、アプリ用ユーザーの権限が 7.9 に書かれている（D2・D3）
- [ ] `messages.properties` の日本語が文字化けせず画面に出て、文字コードの設定が設計書 6.8 に書かれている（C2）
- [ ] 章8に、D1・D2・D3・C2 が残っていない
- [ ] `.gitignore` に S7 の6つが入り、`schema.sql` は除外されていない（4.12）
- [ ] GitHub に push されていて、パスワード・接続先・ダンプ・`target/` が含まれていない（4.13）
- [ ] README に、起動方法と環境変数の名前が書かれている（値は無い）
- [ ] 実行計画書の進捗表で Phase 2 を ☑ にし、Phase 2 の節を概要とリンクだけに縮めた
- [ ] `main` に取り込み、タグ `phase-2-done` を付けて push した

## 7. つまずいたとき

| 症状 | 考えられる原因 | 対処 |
|---|---|---|
| Initializr に決めた版が出ない | 版の一覧が変わった | 一覧の版を控えて、Q2-1 を決め直す。推測で別の版を選ばない |
| `.\mvnw` が「認識されません」 | プロジェクトのフォルダにいない | `pom.xml` のあるフォルダで実行する |
| `mvnw` が Java を見つけられない / 版が違う | `JAVA_HOME` が古い | Phase 0 の 4.1 を見直す |
| 起動で `Access denied for user` | 環境変数の値の誤り、ユーザー名・ホストの不一致 | 環境変数を確認し、VS Code を開き直す。`SHOW GRANTS` と `SELECT user, host FROM mysql.user;`（`root` で）を見る。パスワードを画面やログに貼らない |
| 起動で `Communications link failure` | MySQL のサービスが止まっている、ポート違い | サービス `MySQL97` を確認する。接続先のポートを確認する |
| 起動で `Public Key Retrieval is not allowed` | 認証方式（`caching_sha2_password`）と接続の組み合わせ | 原因をログで確認し、**接続先に付けるパラメータを変える前に相談する**（設計書にない設定のため） |
| 起動で `command denied` / `CREATE command denied` | アプリ用ユーザーの権限不足 | 足りない権限を 4.4 の `GRANT` に足し、理由を記録する。`ALL PRIVILEGES` で済ませない |
| 起動で `Table ... doesn't exist` や、`schema.sql` が実行されない | `spring.sql.init.mode=always` が無い | 4.7 を確認する |
| 起動時に `Failed to configure a DataSource`、または `.\mvnw test` が失敗 | 環境変数が空（設定したあと VS Code を開き直していない） | 4.6 を確認する。VS Code を**閉じて開き直し**、新しいターミナルで実行する |
| ブラウザで `http://localhost:8080/` が英語のログイン画面になる | Spring Security の標準の動き（正しい） | ユーザー名 `user`、パスワードはコンソールの `Using generated security password` の値。起動のたびに変わる |
| `Using generated security password` が見当たらない | ログが流れた | 起動し直し、ログの上のほうを探す。パスワードは、どこにも書き写さない |
| 日本語が `?` や化けた文字になる（`mysql` コマンド） | 端末の文字コード | `SET NAMES utf8mb4;` を先に実行する |
| 日本語が化ける（画面） | `messages.properties` が UTF-8 で保存されていない | VS Code の右下の文字コードを確認する。`spring.messages.encoding` を確認する |
| `schema.sql` を直したのに反映されない | `IF NOT EXISTS` は、あるテーブルを変更しない | 4.9 の末尾のとおり、`root` でデータベースを作り直す |
| `git status` に `target/` が出る | `.gitignore` に入っていない | 4.12 を確認する |
| パスワードをコミットしてしまった | — | ファイルを消すだけでは履歴に残る。**そのパスワードを変更してから**、相談する（7.9） |

## 8. 次の Phase への引き継ぎ

- Phase 3 では、この土台の上に、`User` の Entity・登録・ログインを作ります。**環境変数が設定された VS Code で作業する**こと、**`root` を使わないこと**を覚えておいてください。
- Phase 3 で行うこと（Q2-8）：`SecurityConfig`（Phase 2 では作っていない）、共通の画面の枠、404/403/500 の画面、Cookie の設定（S1）。仮の Controller・テンプレート・`check.hello` は、本物の `HomeController` と画面に置き換える。
- 未決のまま残るもの：Q2-6（照合順序。4.5 のあと）、Q2-11（S2 と `open-in-view`）。
- 設計書の章8に残るのは、D4（Phase 3 で決める）と C1（Phase 3 で確かめる）です。
