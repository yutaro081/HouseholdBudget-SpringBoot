# issue 033：Spring Boot を 4.2 に上げる

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は、[issue 033](../issues/033-Spring-Bootを4.2に上げる.md)（Spring Boot 4.1.3 → 4.2.0）を進めるための手順書です。

> **状態（2026年11月22日）**：● **完了**。Spring Boot 4.2.0 に上げた〔段階 0〜4〕。ブラウザでの確かめ〔4 章の 1〜8〕は、すべて期待どおり。`.\mvnw clean test` は 2,921 件成功。後回しの候補が 1 つある〔7 章〕。

- ブランチ：`feature/issue-033-spring-boot-4.2`。秘密情報：パスワード・接続先の値は、どこにも書かない。
- 進め方：1 つの段階が終わったら止まって、確認を待つ。変更は、文書が先〔設計書 → この手順書〕、コードは後。**テストが先**〔失敗を確かめてから直す〕。コミットと push は本人。
- 今の決まりのまま：`findById` は使わない、`th:utext` は使わない、CSRF 対策は無効にしない。
- 実行計画書の進捗表には、033 の行を足さない〔表は 015 で止まっていて、019〜032 の行もないため。033 だけ足すと表が食い違う〕。

## 1. 決まったこと〔2026年11月22日・本人〕

| 番号 | 決まったこと |
|---|---|
| 1 | **4.2.0 に今上げる**〔4.2.1 は待たない〕。4.2.1 が出たら、あとで 3 番目の数字の版上げとして上げる |
| 2 | `server.error.include-*` の 4 行は、**版上げの前に、4.1.3 のままで** `spring.web.error.include-*` に直す〔段階 1〕 |
| 3 | Tomcat の相対リダイレクトは、**新しい標準〔true〕のまま**〔`application.properties` に書かない〕 |
| 4 | issue のファイルとこの手順書を新しく作る。**要件定義書は直さない**〔版の数字が書かれていない。135〜136 行は「Java」「Spring Boot」だけ〕。実行計画書の進捗表には 033 の行を足さない。共通事項の版の記述〔23 行〕は、段階 2 で直す |

## 2. 調べた結果〔2026年11月22日〕

根拠は、手元の Maven の保管場所〔`~/.m2`〕にある 4.1.3 と 4.2.0 の BOM・jar の中身、コードの grep、公式のリリースノート〔2.6 の一覧〕。

### 2.1 版が変わる部品〔BOM の全項目を比べた〕

| 部品 | 4.1.3 | 4.2.0 | このアプリ |
|---|---|---|---|
| Spring Framework | 7.0.11 | **7.1.0** | 使う |
| Spring Security | 7.1.3 | **7.2.0** | 使う |
| Spring Data〔BOM〕 | 2026.0.3 | **2026.1.0**〔Spring Data JPA 4.1 → 4.2〕 | 使う |
| HikariCP | 7.0.2 | 7.1.0 | 使う〔テストで接続の数を設定〕 |
| Logback | 1.5.38 | 1.6.6 | 使う〔設定ファイルなし〕 |
| JUnit Jupiter | 6.0.3 | 6.1.3 | 使う |
| Mockito | 5.23.0 | 5.24.0 | 使う〔1 か所〕 |
| Surefire／Compiler プラグイン | 3.5.6／3.15.0 | 3.6.1／3.16.0 | 使う |
| Spring Session | 4.1.2 | 4.2.0 | 使っていない〔`pom.xml` にない〕 |
| Hibernate ORM 7.4.15、Tomcat 11.0.27、Thymeleaf と Extras 3.1.7、Hibernate Validator 9.1.6、Jackson 3.1.8、MySQL Connector/J、AssertJ | 同じ | 同じ | — |

アプリが使う starter〔10 個〕と `thymeleaf-extras-springsecurity6` は、4.2.0 の BOM にもすべてある。

### 2.2 関係する変更

| 変更 | 当たる所 | 確かめたこと〔事実〕 | 推測 |
|---|---|---|---|
| Tomcat の `use-relative-redirects` の標準が false → true〔Boot 4.2〕 | すべてのリダイレクト〔`SecurityConfig` のログイン失敗の `sendRedirect`、Controller の `redirect:`、ログイン画面への移動、`/login?expired`〕 | 4.1.3 の jar は標準 false、4.2.0 は true。Spring Security 7.1.3 の `LoginUrlAuthenticationEntryPoint` は、もともと相対の URL を作る〔`favorRelativeUris` が標準で true〕。MockMvc は Tomcat を通らない | ブラウザが受け取る `Location` が `/login` のような相対の形になるだけで、動きは同じ |
| 不正な添字の例外をそろえる〔Framework #37655〕 | `GlobalExceptionHandler`〔`InvalidPropertyException` → 400〕、`TransactionFormBinding`〔`setAutoGrowCollectionLimit`〕、`BudgetForm.rows` | 変更の commit では、外側の型は `InvalidPropertyException` のまま。変わるのはメッセージと原因だけ。`rows[abc]` の処理は変わらない。フォームは setter 型〔`bind()` の経路。#37654 の `construct()` は当たらない〕 | 400 のまま。`BudgetControllerTest`〔9 件〕と `TransactionFormTest` が見張る |
| メッセージの読み込みに `ResourceBasedMessageSourceConfigurer` が入る〔Boot 4.2〕 | `spring.messages.encoding=UTF-8`、`messages.properties` | basename は標準のまま。`common-messages` は使っていない | 文字化けは `MessagesTest` が見つける |
| 親クラス・外側のクラスの設定も読む〔Framework #31456〕 | 入れ子の `@TestConfiguration` を持つテスト 9 クラス | テストクラスの継承・`@Nested` はない | 当たらない |
| Surefire 3.6 でテストを動かす仕組みが 1 つになる | テスト全体 | JUnit 4・`runOrder`・`@TestInstance` は使っていない | 件数が変わらないかを比べる |
| 設定の Set・Map がファイルの順を保つ | `server.servlet.session.tracking-modes` | 値は 1 つ、コードから読んでいない | 当たらない |

### 2.3 関係しない変更〔使っていないことを grep で確かめた〕

- RestTemplate・TestRestTemplate〔0 件〕、WebClient・RestClient。
- Boot 4.2：Pulsar、AMQP、forwarded header、`server.tomcat.*`、`spring.mvc.pathmatch`、Buildpacks、LDAP、Redis、WebFlux、SSL bundle、Janino、Micrometer、テストのスライス〔`@DataJpaTest`・`@WebMvcTest` はない〕。
- Framework 7.1：`DataBinder.disallowedFields`、`HandlerMappingIntrospector`、ProblemDetail、JAXB、MIME の厳しい解析〔テストは `MediaType.TEXT_HTML` だけ〕、`classpath:` 直下の静的ファイル〔`WebConfig` は `classpath:/static/fonts/`〕。
- Spring Security 7.2：新しい項目は One-Time Token、リアクティブの firewall、OAuth2 だけ。7.2.0 の直しは「標準のログイン画面の CSP nonce」だけ〔このアプリは自作のログイン画面〕。Servlet の移行の手順は 6 → 7 の話だけ。→ `SecurityConfig` は変えなくてよい見込み〔推測。`SecurityBehaviorTest`・`SessionExpiredTest`・`CacheControlHeaderTest` が確かめる〕。
- Spring Data JPA 4.2：載っている変更は `@Query(name=…)`・`countName` の確認が厳しくなる 1 件だけ〔使っていない〕。JPQL の `(:x is null or …)`・`@EntityGraph`・`select new` は載っていない〔「変わらない」とまでは言えないが、Hibernate の版は同じ〕。
- Thymeleaf・Bean Validation：版が同じ。

### 2.4 前からある問題：`server.error.*`〔段階 1 で直す〕

- `application.properties` の `server.error.include-stacktrace`・`include-message`・`include-binding-errors`・`include-exception` は、4.1.3・4.2.0 のどちらの jar の設定の一覧でも「4.0.0 から使えない〔`level: error`〕、代わりは `spring.web.error.include-*`」。
- この名前を読む仕組みはない。読まれるのは `spring.web.error`〔`WebProperties` の中の `ErrorProperties`〕だけ。
- 標準の値〔never・false〕が書いた値と同じなので、画面は安全なままだった。ただし、設計書 S2〔「標準の動きが変わっても、中身が画面に出ないようにするため、標準任せにせず明示する」〕が守られていなかった。

### 2.5 4.2.0 の既知の不具合と 4.2.1

- 4.2.0 は 2026年11月19日〔Spring の公式ブログ〕。
- milestone 4.2.1 は 24 件すべて完了。出る日は決まっていない。関係しそうなもの〔#52829 `common-messages` の文字コード、#52886 basename の空白、#52871 `ResourceHandlerRegistrationCustomizer`、#52860 クラスローダーの違い〕は、どれもこのアプリに当たらない。
- 4.2.0 への「regression」の報告は、検索では 0 件〔ラベルの名前が正しいかは確かめていない〕。リリースノートの既知の不具合は、Paketo と Spring Cloud Bindings の組み合わせだけ〔使っていない〕。
- 4.1 の保守：minor 版は少なくとも 12 か月〔Supported-Versions の wiki。終わる日付は見つからなかった〕。

### 2.6 読んだもの・読めなかったもの

- 読んだもの：Spring Boot 4.2 Release Notes〔wiki〕、Spring Boot 4.2.0 available now〔ブログ〕、Spring Boot v4.2.0 のリリースのページ、milestone 4.2.1、Supported Versions〔wiki〕、Spring Framework 7.1 Release Notes〔wiki〕と v7.1.0 のリリースのページ、Framework #37655・#37654、Spring Security の What's New と Migration〔版のない今の文書。見出しは 7.2.0〕と 7.2.0 のリリースのページ、Spring Data 2026.1 Release Notes〔wiki〕、HikariCP の CHANGES、JUnit 6.1.0〜6.1.3 のリリースノート、Surefire 3.6.x、Mockito 5.24.0。
- 読めなかったもの：Spring Security 7.2 の版つきの文書〔転送された〕、Boot 4.2 のリリースノートの後ろの約 3 割〔wiki の目次の部分〕、milestone 4.2.1 の 24 件のうち 16 件〔要約でだけ読んだ〕、4.2.1 が出る日〔カレンダーが読めなかった〕。
- 読まなかったもの：Spring Data JPA の reference の各章、Logback 1.6 のリリースノート、Hibernate・Tomcat・Thymeleaf の移行の手引き〔版が同じため〕、Spring Session〔使っていないため〕。

## 3. 段階〔1 段階 ＝ 1 コミット〕

### 3.0 段階 0：文書

- [x] issue のファイル〔`docs/issues/033-Spring-Bootを4.2に上げる.md`〕と、この手順書を作る。
- [x] 設計書 7.7 と S2 に、設定の名前〔`spring.web.error.include-*`〕と確かめ方を書く〔段階 1 の分〕。
- 段階 2 の分〔設計書 1.2 の版、7.10 の 2101 行の書き足し〕は、5 章に下書きとして残し、設計書にはまだ書かない。

### 3.1 段階 1：`server.error` の設定の名前を直す〔4.1.3 のまま〕

1. テストを先に書く〔`ErrorSettingsTest`〕：
   - ① 4 つの設定 `spring.web.error.include-*` が、**本体の `application.properties` から**読まれ、書いた値になっている〔設定の出どころを確かめる。標準の値と書いた値が同じでも見分けられる〕。
   - ② 古い名前 `server.error.include-*` が、どの設定の出どころにもない〔読まれない名前を書いて、効いているつもりにならないため〕。
   - ③ アプリが読み込んだ値〔`WebProperties.getError()`〕が、書いた値になっている。
2. テストが失敗することを確かめる〔①②が失敗、③は標準の値と同じなので通る〕。
3. `application.properties` の 4 行を `spring.web.error.include-*` に直す。
4. 関係するテスト〔`ErrorScreensTest`、`SecurityBehaviorTest`、`ErrorSettingsTest`〕を流し、最後に `.\mvnw clean test` を流す。
5. わざと壊して確かめる：直した行を古い名前に戻す → `ErrorSettingsTest` が失敗する。確かめたら元に戻す。

### 3.2 段階 2：版上げ

1. 文書を先に直す：設計書 1.2〔5 章の下書き〕→ 実行計画書の共通事項〔23 行〕。
2. `pom.xml` の `<parent>` の版を 4.2.0 にする。
3. `.\mvnw clean compile` で、使われなくなる API の警告がないかを見る。
4. 関係するテスト〔security、form、repository、`MessagesTest`、`ErrorScreensTest`、`ErrorSettingsTest`〕を流す。
5. 最後に `.\mvnw clean test` を 1 回流し、件数が段階 1 のあとと同じかを比べる。
6. 失敗したら、文書と `pom.xml` を元に戻す〔4.1.3 のまま使える〕。

### 3.3 段階 3：ブラウザでの確認〔本人。4 章〕

### 3.4 段階 4：版の記述を直す

- `README.md`〔102 行の「使用技術」の表〕と `CLAUDE.md`〔7 行〕を 4.2.0 にする。
- 直さないもの：要件定義書、`phase-*.md`・ほかの issue の手順書〔昔の記録〕、`docs/reviews/phase-1-review.md`、`THIRD-PARTY-NOTICES.md`〔Boot の版が書かれていない〕。`docs/testing.md` は、件数が変わったときだけ直す。

## 4. ブラウザでの確かめ方〔本人。段階 2 のあと〕

| 番号 | 確かめること |
|---|---|
| 1 | ログインの成功 → トップ。失敗 → `/login?error` で、ユーザー名は残り、パスワードは空 |
| 2 | ログアウト → `/login?logout` |
| 3 | ログインしないで `/transactions` を開く → `/login` |
| 4 | ログイン切れ〔issue 030〕：ログインしたあとアプリを再起動 → 画面を開く〔GET〕と `/login?expired` の案内。別の画面で登録ボタンを押す〔POST〕と `/login?expired` へ移り、保存されない |
| 5 | `/transactions/999999` と `/categories/abc` → 日本語の 404 画面。英語の画面や、例外の名前が出ない |
| 6 | すべての画面を 1 回ずつ開く〔`open-in-view=false` の読み込み忘れは、テストで見つからないため〕：トップ〔グラフ・ひとこと〕、取引一覧〔科目・季節の絞り込み〕、登録〔5 行〕・編集・複写・削除〔同じレシート〕、予算〔保存・自動予算〕、予実比較〔実績のリンク〕、科目・取引先・支払方法 |
| 7 | 日本語が文字化けしない |
| 8 | DevTools：`JSESSIONID` が HttpOnly・SameSite=Lax。URL に `;jsessionid=` が出ない。`/fonts/**` が `max-age=2592000`。リダイレクトの `Location` が相対の形〔`/login` など〕でも、正しく移る |

## 5. 段階 2 の下書き〔まだ設計書に書かない〕

- 設計書 1.2 の Spring Boot の行：
  - 版：`4.1.3` → `4.2.0`
  - 選んだ理由の最後：
    - 前：「…4.1.2 は 4.1.1 の不具合修正版で、Spring Initializr で選べる安定版のため、こちらを使った。その後、不具合修正版の 4.1.3 に上げた（3 番目の数字だけの版上げで、機能は変えない。4.2 には上げない）」
    - 後：「…4.1.2 は 4.1.1 の不具合修正版で、Spring Initializr で選べる安定版のため、こちらを使った。その後、不具合修正版の 4.1.3 に上げ、issue 033 で 4.2.0 に上げた（2 番目の数字の版上げ。移行の手引きで、このアプリに関わる変更を確かめた。機能は変えない）」
- 設計書 7.10〔2101 行〕：「CSRF の確認は、この `accessDeniedHandler` を使う（Spring Security 7.1.2 で確かめた）。」は記録として残し、「Spring Security 7.2.0〔Spring Boot 4.2.0〕でも、テストで確かめた。」を足す。
- 実行計画書 23 行：「Spring Boot は 4.1.3（設計書 1.2。当初は 4.1.2）。」→「Spring Boot は 4.2.0（設計書 1.2。当初は 4.1.2、その後 4.1.3。issue 033）。」

## 6. 決まっていない点

| 番号 | 決めること | 案 | おすすめ | 決定 |
|---|---|---|---|---|
| Q033-1 | `ErrorSettingsTest` の確かめ方〔段階 1〕 | A：設定の出どころ〔本体の `application.properties`〕と、古い名前がないことと、読み込んだ値の 3 つ<br>B：読み込んだ値だけ〔標準の値と同じなので、読まれていないことを見分けられない〕 | **A** | ☑ **A**〔2026年11月22日・本人〕 |

## 7. 後回しの候補

- テストを流すと、Mockito の「自分で自分を読み込んでいる。将来の JDK では動かないので、agent として設定する」という注意が出る〔4.1.3 のときから〕。原因をなくす直しは、別の issue で行う。

## 8. 記録

- **段階 0・段階 1〔2026年11月22日〕**：
  - 文書：issue のファイルとこの手順書を作り、設計書 7.7〔設定の名前・古い名前のこと・テストでの確かめ方〕と S2〔理由の欄〕を直した。要件定義書は直していない。
  - テストを先に書いた：`ErrorSettingsTest` を 9 件〔① 本体の `application.properties` から読まれる 4 件、② 古い名前がどこにもない 4 件、③ アプリが読み込んだ値 1 件〕。
  - 直す前に流すと、**9 件のうち 8 件が失敗**〔①②。③は標準の値と同じなので通る〕。
  - `application.properties` の 4 行を `spring.web.error.include-*` に直した。
  - 関係するテストは、`ErrorSettingsTest` 9 件、`ErrorScreensTest` 35 件、`SecurityBehaviorTest` 50 件が、すべて成功。
  - わざと壊した：古い名前に戻すと、`ErrorSettingsTest` の 8 件が失敗した〔VS Code も、その 4 行に「Deprecated」の警告を出した〕。確かめたあと、元に戻した。
  - `.\mvnw clean test` は **2,921 件成功**〔2,912 件 ＋ `ErrorSettingsTest` 9 件〕。
- **段階 2〔2026年11月22日〕**：
  - 決めたこと〔本人〕：版上げのあいだだけ、Spring Boot の公式の道具 `spring-boot-properties-migrator`〔scope は runtime〕を `pom.xml` に足し、名前が変わった・使えなくなった設定がないかを確かめる。確かめたら外す〔コミットする形には残さない〕。理由：`ErrorSettingsTest` は「ファイルに書いてある」ことは確かめるが、Spring が将来また名前を変えたときは見つけられないため。
  - 文書を先に直した：設計書 1.2〔5 章の下書きのとおり〕→ 実行計画書の共通事項〔23 行〕。次に `pom.xml` の `<parent>` の版を 4.2.0 にした。
  - 実際に使われる版〔`dependency:list`〕：Spring Boot 4.2.0、Spring Framework 7.1.0、Spring Security 7.2.0、Spring Data JPA 4.2.0、Hibernate 7.4.15、Tomcat 11.0.27、Thymeleaf 3.1.7、HikariCP 7.1.0、JUnit 6.1.3〔2.1 の表のとおり〕。
  - `.\mvnw clean test-compile -Dmaven.compiler.showDeprecation=true`〔本体 93 ファイル・テスト 128 ファイル〕：**使われなくなる API の警告は 0 件**。
  - 関係するテスト〔security、form、repository、`MessagesTest`、`ErrorScreensTest`、`ErrorSettingsTest`。Framework #37655 に当たる `BudgetControllerTest` も足した〕：**732 件、すべて成功**。
  - properties-migrator の報告：**なし**〔本体とテスト用の `application.properties` に、名前が変わった・使えなくなった設定はない〕。道具が動いていることは、ファイルを変えずに、古い名前を 1 回だけ `-Dserver.error.include-stacktrace=never` で渡して確かめた〔`MessagesTest` だけを流した。「名前が変わった設定：`server.error.include-stacktrace` → `spring.web.error.include-stacktrace`」と報告が出た〕。
  - properties-migrator を `pom.xml` から外した〔`pom.xml` の差分は、版の 1 行だけ〕。
  - `.\mvnw clean test` は **2,921 件成功**〔段階 1 のあとと同じ。増減なし〕。
  - テストが通ったので、設計書 7.10〔2101 行〕に「Spring Security 7.2.0〔Spring Boot 4.2.0〕でも、テストで確かめた」を足した。
  - ログに出る、前からの注意〔4.1.3 でも出ていた。版上げとは別〕：Mockito が「自分で自分を読み込んでいる。将来の JDK では動かなくなるので、Mockito を agent として設定する」と出す。今は動く。
  - 残り：段階 3〔ブラウザでの確認、4 章〕、段階 4〔README・CLAUDE.md〕。
- **段階 3〔2026年11月22日・本人〕**：4 章の 1〜8 が、すべて期待どおりだった〔ログイン・ログアウト・ログイン前・ログイン切れ・404・全画面・日本語・DevTools〕。リダイレクトの `Location` が相対の形でも、正しく移ることも確かめた。
- **段階 4〔2026年11月22日〕**：
  - 版の記述を 4.2.0 にした：`README.md`〔使用技術の表〕、`CLAUDE.md`〔7 行〕。Spring Security の版の数字が書かれた所は、設計書 7.10〔段階 2 で書き足した〕のほかになかった〔README は名前だけ〕。昔の記録は直していない。
  - テストの件数を 2,921 件にそろえた：`README.md`〔2 か所。日付も 11 月 22 日に〕、`docs/testing.md`〔総数と、種類ごとの表〕。`ErrorSettingsTest` の 9 件は、表の「そのほか」に数えた〔92 → 101。`MessagesTest` と同じ、設定・起動の確かめのため〕。表の合計は 2,921 件で合う。
  - `.\mvnw clean test` は **2,921 件成功**〔段階 2 のあとと同じ〕。
