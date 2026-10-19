# Phase 0：開発環境の準備

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は Phase 0 を実際に進めるための手順書です。

- 次の Phase：[Phase 1 設計の確定](execution-plan.md#phase-1設計の確定)
- 状態：☑ 完了

## 1. 目的

コードを書き始められる状態にします。具体的には、Java・MySQL・VS Code・Git/GitHub・Claude Code の5つが、**このパソコンで動くこと**を確認します。

## 2. 前提と、この Phase でやらないこと

- パソコンは Windows 11。シェルは PowerShell（VS Code のターミナルで開く）を使う。
- リポジトリ（`HouseholdBudget-SpringBoot`）はクローン済みで、`main` ブランチにいる。
- やらないこと：Spring Boot プロジェクトの作成、データベース・テーブルの作成（どちらも Phase 2）。ここでは「道具が使える」ことの確認だけを行う。

## 3. 使う道具と、選んだ理由

| 道具 | 版（提案） | 役割 | 選んだ理由 |
|---|---|---|---|
| JDK（Java開発キット） | 25（LTS） | Java のプログラムを作って動かす | 導入済みの長期サポート版で、入れ直す手間がないため。Spring Boot が対応しているかは Phase 2 で確認する。 |
| Maven | 別途インストールしない | 部品の管理とビルド | Spring Boot のプロジェクトに同梱の `mvnw`（Maven Wrapper）を使えば、事前の準備が不要なため。 |
| MySQL Server | 9.7 | データベース | 要件で指定。導入済みでサービスが動いており、入れ直す手間がないため。 |
| VS Code | 最新版 | コードを書く場所 | 要件で指定。 |
| Git / GitHub | 最新版 | 変更履歴の管理と保管 | 要件で指定。 |
| Claude Code | 最新版 | AI による開発の補助 | 要件で指定。 |

> 版は「提案」です。すでに別の版が入っていて動くなら、そのままでかまいません（ただし JDK は **17以上**）。このパソコンには JDK 25 と MySQL 9.7 が入っているため、その版を使います。

## 4. 手順

各手順の最後に **確認** があります。確認ができたら、チェックを付けて次へ進みます。

### 4.1 JDK 25 を確認する

- [x] JDK 25 がインストールされている（Eclipse Temurin 25.0.2。確認済み）
- [x] 環境変数 `JAVA_HOME` が JDK のフォルダを指している（確認済み）
- [ ] 別の JDK を入れた場合は、VS Code を**開き直して**から確認する（環境変数の変更は、開き直さないと反映されない）

**確認**：ターミナルで次の2つを実行し、どちらも「25」から始まる版が表示される。

```
java -version
javac -version
```

> `javac` が見つからないと表示された場合は、JDK ではなく JRE（実行だけの版）が入っている可能性があります。JDK を入れ直してください。

### 4.2 MySQL を入れる

- [x] MySQL Server 9.7 がインストールされている（確認済み）
- [ ] `root`（管理者）ユーザーのパスワードを、**自分だけが分かる場所にメモする**（このリポジトリには書かない）
- [x] MySQL が「サービス」として起動している（サービス名 `MySQL97`、確認済み）
- [ ] `mysql` コマンドが使えるようにする（`bin` フォルダを環境変数 `Path` に追加し、VS Code を開き直す）
- [ ] 文字コードが `utf8mb4` であることを確認する（日本語を保存するため）

**確認**：ターミナルで次を実行し、パスワードを入力してログインできる。

```
mysql -u root -p
```

ログイン後に次を入力して、文字コードの項目が `utf8mb4` であることを見る（確認後は `exit` で抜ける）。

```
SHOW VARIABLES LIKE 'character_set_server';
```

> `mysql` が見つからないと表示された場合は、MySQL の `bin` フォルダを環境変数 `Path` に追加するか、スタートメニューの「MySQL Command Line Client」を使ってください。

### 4.3 VS Code の拡張機能を入れる

- [ ] 「Extension Pack for Java」（Java の入力補助・実行・デバッグ）
- [ ] 「Spring Boot Extension Pack」（Spring Boot の入力補助・起動）
- [ ] Phase 2 で使うため、VS Code が JDK 25 を認識していることを確認する（Java の拡張機能の画面で使用中の JDK を見る）

**確認**：VS Code の拡張機能の画面で、上の2つが「有効」になっている。エラーの表示（赤いバッジなど）が出ていない。

### 4.4 Git と GitHub を確認する

- [ ] Git がインストールされている
- [ ] 自分の名前とメールアドレスが Git に設定されている
- [ ] GitHub の認証ができている（`push` できる）

**確認**：次を実行して、期待どおりの結果になる。

| コマンド | 期待する結果 |
|---|---|
| `git --version` | 版が表示される |
| `git config user.name` | 自分のユーザー名が表示される |
| `git status` | 「On branch main」「working tree clean」と表示される |
| `git fetch` | エラーなく終わる（GitHub に接続できている） |

### 4.5 Claude Code を確認する

- [ ] VS Code から Claude Code が開ける
- [ ] このリポジトリを開いた状態で、簡単な質問に答えが返ってくる（例：`docs/reviews/requirements.md` を読んで、機能を3行で要約して）

**確認**：Claude Code がこのリポジトリのファイルを読めている。

### 4.6 動作確認の記録を残す

- [x] 次の表に記入する。パスワードや個人情報は**書かない**。
- [x] Git でコミットして GitHub に push する

動作確認の記録：

| 項目 | 確認した版・結果 |
|---|---|
| OS | Windows 11 |
| JDK（`java -version`） | OpenJDK 25.0.2 2026-01-20 LTS（Eclipse Temurin 25.0.2+10） |
| javac（`javac -version`） | javac 25.0.2 |
| MySQL（`mysql --version`） | 9.7.2 for Win64 on x86_64（MySQL Community Server - GPL） |
| MySQL の文字コード | utf8mb4（確認済み） |
| VS Code の版（`code --version`） | 1.142.0（x64） |
| Git の版（`git --version`） | 2.41.0.windows.1 |
| Claude Code | 動作した |

## 5. 完了条件

次のすべてを満たしたら Phase 0 は完了です。

- [ ] `java -version` と `javac -version` が 25（17以上）を示す
- [ ] `mysql -u root -p` でログインでき、文字コードが `utf8mb4` である
- [ ] VS Code に Java と Spring Boot の拡張機能が入り、JDK を認識している
- [ ] `git status` が `main` で、GitHub へ `push` できる
- [ ] Claude Code がリポジトリのファイルを読める
- [x] 動作確認の記録がコミットされている
- [ ] 実行計画書の進捗表で Phase 0 を ☑ にした

## 6. つまずいたとき

| 症状 | 考えられる原因 | 対処 |
|---|---|---|
| `java` や `mysql` が「認識されません」と出る | 環境変数 `Path` に入っていない / VS Code を開き直していない | `Path` の設定を確認し、VS Code を再起動する |
| `java -version` が 25 でない | 古い JDK が先に見つかっている | `JAVA_HOME` と `Path` の順番を確認し、古い JDK の設定を外す |
| MySQL にログインできない | パスワード違い / サービスが止まっている | サービス画面で起動状態を確認する。パスワードを忘れたら再設定する（この環境にしか影響しない） |
| 日本語が `?` になる | 文字コードが `utf8mb4` でない | MySQL の設定（`my.ini`）で `character-set-server=utf8mb4` を指定して再起動する |
| `git push` が拒否される | GitHub の認証が未設定 | VS Code または Git Credential Manager でサインインし直す |

### 実際にあったこと

- Oracle の Java 26 が環境変数 `Path` の先にあったため、`java -version` が 25 にならなかった。システムの `Path` から Oracle の Java 26 を外して、Java 25 にそろえた。

## 7. 次の Phase への引き継ぎ

- Phase 1 では、この環境で何も作らず、**設計書の作成と確定**だけを行います。
- Phase 2 で使うために、**MySQL の `root` パスワードの保管場所**と、**VS Code が認識している JDK の版**を覚えておいてください。
