# HouseholdBudget-SpringBoot
家計・予実管理アプリ（Spring Boot版・仕様駆動開発）

## 起動方法（Phase 2 時点の簡易版。Phase 9 で仕上げる）

### 必要な環境

- JDK 25
- MySQL 9.7
- VS Code など（`mvnw` が付いているので、Maven の別途インストールは不要）

### 環境変数

DB に接続するための設定を、環境変数から読みます。**値は各自のパソコンで設定してください。この README やリポジトリには書きません。**

| 環境変数の名前 | 用途 |
|---|---|
| `HB_DB_URL` | DB の接続先 |
| `HB_DB_USER` | アプリ専用の DB ユーザー名 |
| `HB_DB_PASSWORD` | そのユーザーのパスワード |

環境変数を設定したあとは、VS Code（ターミナル）を開き直してください。

### データベースとユーザーの作り方（概要）

MySQL に管理者（`root`）でログインして、次を行います。

1. データベース `householdbudget` を作る（文字コード `utf8mb4`、照合順序 `utf8mb4_0900_as_ci`）。
2. アプリ専用のユーザー `hb_app`（接続元は `localhost` のみ）を作る。パスワードは自分で決める。
3. このデータベースだけに、次の権限を与える：`SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES`。

テーブルは、アプリの起動時に `schema.sql` が作ります（`root` はアプリでは使いません）。

### 起動

MySQL のサービスが動いている状態で、リポジトリの直下で次を実行し、ブラウザで `http://localhost:8080/` を開きます。

```
.\mvnw spring-boot:run
```

- 今は Spring Security の標準のログイン画面が出ます。ユーザー名は `user`、パスワードは起動時にコンソールに出る値です（起動のたびに変わります）。
- ログイン機能は Phase 3 で作ります。

## docs ディレクトリの構成

| ディレクトリ | 置くもの |
|---|---|
| `docs/issues/` | 実装してほしい機能要望、解決してほしい不具合・エラー内容の記録（書き方は [docs/issues/README.md](docs/issues/README.md)） |
| `docs/plans/` | 対応方針、作業手順など、実行計画のまとめ（全体は [execution-plan.md](docs/plans/execution-plan.md)） |
| `docs/reviews/` | レビュー観点、指摘事項、修正結果など、レビューの記録。要件定義書（[requirements.md](docs/reviews/requirements.md)）、設計書（[design.md](docs/reviews/design.md)）、各 Phase のレビュー記録（`phase-N-review.md`）もここに置く |
