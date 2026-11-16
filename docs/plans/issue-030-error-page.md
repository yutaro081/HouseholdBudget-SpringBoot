# issue 030：エラーの画面とログイン切れ

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は、[issue 030](../issues/030-エラーの画面とログイン切れ.md)（ログインが切れたときにログイン画面へ案内する、エラーの画面の見た目をそろえる）を進めるための手順書です。

> **状態（2026年11月17日）**：◐ **段階 1 はコミット済み。段階 2〔エラーの画面の形と文〕を行った**〔確認待ち。ブラウザでの確かめと、コミット〔本人〕が残っている〕。`.\mvnw clean test` は 2,697 件成功。

- ブランチ：`feature/issue-030-error-page`。秘密情報：パスワード・接続先の値は、どこにも書かない。
- 進め方：1 つの段階が終わったら止まって、確認を待つ。変更は、文書が先〔要件定義書 → 設計書 → この手順書〕、コードは後。**テストが先**〔失敗を確かめてから実装〕。コミットと push は本人。
- 今の決まりのまま：JavaScript は自分で書いた短いものだけ。Bootstrap の JavaScript・CDN は使わない。`th:utext` は使わない。検索は必ず `user_id` で絞る〔`findById` は使わない〕。**CSRF 対策は無効にしない**。
- **例外の書き方**：新しいメソッド名のせいでテストがコンパイルできないときだけ、先に空の形〔メソッドの宣言〕を作ってよい〔記録に書く〕。

## 1. 決まったこと〔2026年11月16日・本人〕

| 番号 | 決まったこと |
|---|---|
| 1 | 番号は 030 |
| 2 | ログイン切れの作り方は **案B**：Spring Security の標準の部品〔`DelegatingAuthenticationEntryPoint`、`LoginUrlAuthenticationEntryPoint`、`RequestMatcherDelegatingAccessDeniedHandler`、`AccessDeniedHandlerImpl`〕を組み合わせ、「古いセッションID で来たか」〔`getRequestedSessionId() != null` かつ `!isRequestedSessionIdValid()`〕で分ける。古ければ、GET も POST も `/login?expired` へ移す。それ以外は今までどおり〔GET は `/login`、トークンが合わない POST は 403〕。`invalidSessionUrl` は使わない〔設計書 7.10、S10〕 |
| 3 | ログインし直したあとは、今のまま**いつもトップへ**〔U5。`defaultSuccessUrl("/", true)` は変えない〕 |
| 4 | ログイン画面の文は「**ログインが切れました。もう一度ログインしてください。**」。**案内の形**〔`alert-info`：補足の灰色の線と `info` のアイコン〕で出す。`fragments/common.html` の `messages` に、案内のメッセージ〔例：`infoMessageKey`〕を足す。新しい色は増やさない |
| 5 | ログインが切れるまでの時間は、標準の **30 分**のまま〔`application.properties` に書かない〕。設計書 7.10 に書く |
| 6 | エラーの画面〔404・403・4xx・5xx〕は 4 つとも同じ形：**ロゴだけのヘッダー**〔`fragments/public-header`〕、**1 行の見出し**〔h1、28px〕、**失敗のお知らせ**〔`alert-danger` と `alert` のアイコン、16px〕、**主ボタン「トップへ」**〔`/`〕。幅は 420px のまま。文は設計書 7.7 の表のとおり。新しい色は増やさない |
| 7 | 入力の途中の値は守れない〔保存もされない〕。要件定義書 4.1 と 8 章に書く〔済み〕。要件定義書には時間〔30 分〕を書かず、「しばらく操作しなかったとき（時間は設計書 7.10）」とする〔2026年11月16日・本人。時間は設計で決める値で、2 か所に書くと、変えたときに食い違うもとになるため〕 |
| 8 | 「登録しました。ログインしてください」〔`messages.properties` 23 行〕も、段階 2 で「登録しました。ログインしてください。」に直す |
| 9 | 案B でも残ること〔ログイン画面を開いたまま切れた人に「ログインが切れました」と出る、(d) が 403 のまま〕は、5 章「分かっている限界」に書く |
| 10 | 段階は ① ログイン切れ → ② エラーの画面。理由：段階 1 で、403 の画面になる場合が決まり、それで 403 の文が決まるため |
| 11 | 403 のお知らせの文は「別の画面でログインし直したか、画面が古くなったため、受け付けられませんでした。内容は保存されていません。画面を開き直して、もう一度操作してください。」〔段階 1 のあと、時間がたった場合はログイン画面へ移るので 403 にならないため〕。404 のお知らせの文は「開こうとしたページはありません。トップから開き直してください。」〔エラーの画面にはメニューがないため〕〔2026年11月16日・本人〕 |

## 2. 調べた結果〔2026年11月16日〕

根拠は、設定・コード・文書の行と、Spring Security 7.1.2・Spring Boot 4.1.2 の jar の中身〔`javap` で読んだ〕。

### 2.1 この画面はどこから出ているか

- テンプレートは `error/403.html`〔12-13 行〕。文は `messages.properties` 30 行の `error.page.forbidden`。`error/4xx.html` も同じ文〔31 行〕。
- 流れ：POST → Spring Security の CSRF の確認〔`CsrfFilter`。`SecurityConfig` に CSRF の設定はなく、標準のまま〕→ トークンがセッションにない〔`MissingCsrfTokenException`〕か、違う〔`InvalidCsrfTokenException`〕→ 標準の `AccessDeniedHandlerImpl` が `sendError(403)` → Spring Boot の `/error`〔`SecurityConfig` 37 行で誰でも開ける〕→ 状態コード用の画面 `error/403`。
- `GlobalExceptionHandler`〔27-44 行。404 と 400 だけを扱う〕は通らない。
- 権限の決まりは「誰でも開ける」と「ログイン必須」だけ〔`SecurityConfig` 35-39 行〕なので、403 になるのは実際には CSRF のときだけ。

403 になる場合：

| 記号 | 場合 |
|---|---|
| (a) | アプリの再起動〔セッションを持ち越す設定はない。devtools も入っていない〕 |
| (b) | 30 分操作がない〔Spring Boot 4.1.2 の標準 `Duration.ofMinutes(30)`。`application.properties` に時間の設定はない〕 |
| (c) | ログイン画面〔や登録画面〕を開いたまま (a)・(b) が起きて、ボタンを押した。ログアウトのボタンも同じ |
| (d) | 別のタブでログアウトしてログインし直したあと、古いタブでボタンを押した〔トークンが新しくなっていて `Invalid`〕 |
| (e) | トークンを書き換えた、またはトークンがない送信 |

ほかのエラーの画面：

| 画面 | なる場合 | 今の文 |
|---|---|---|
| 404 | ログイン中に、ない URL。データがない・他人のデータ。ID が数字でない〔未ログインでない URL は、ログイン画面へ移る〕 | ページが見つかりません |
| 4xx | 405〔削除の URL を GET で開いたなど〕、400〔送った形がおかしい〕 | 403 と同じ文 |
| 5xx | 思いがけない例外、DB が止まっている | エラーが発生しました。しばらくしてからもう一度お試しください |

4 つとも、見出し〔h1〕に文をまるごと入れ、「取引一覧へ戻る」のリンクだけ。ヘッダーはない。

### 2.2 ログインが切れたあと、今どうなるか

| 操作 | 今 |
|---|---|
| 画面を開く〔GET〕 | 302 でログイン画面へ。文は出ない。ログイン後はいつもトップ〔`SecurityConfig` 44 行〕 |
| ボタンを押す〔POST〕 | CSRF の確認で 403 になり、`error/403`。保存しない |

- 確認の順〔jar で確かめた〕：`CsrfFilter` → `LogoutFilter` → `UsernamePasswordAuthenticationFilter` → … → `SessionManagementFilter` → `ExceptionTranslationFilter` → `AuthorizationFilter`〔ログインの確認〕。CSRF が先なので、POST はログインの確認まで届かない。
- `SecurityConfig` に `sessionManagement`・`exceptionHandling` の設定はない。CSRF のエラーとログイン切れは、区別していない。

### 2.3 作り方の案〔案B に決まった〕

| 案 | 中身 | 良い所 | 悪い所 |
|---|---|---|---|
| A | `sessionManagement(s -> s.invalidSessionUrl("/login?expired"))` と、ログアウトの `deleteCookies("JSESSIONID")` | 標準の設定 2 つ。GET も POST も | セッションID のないトークンなし POST も 403 でなく移動になる〔今の 403 のテスト 19 か所と設計書 7.6 が変わる〕。`InvalidSessionAccessDeniedHandler` は、セッションID を確かめずに移すため。Cookie を消さないと「ログアウトしました」の代わりに「切れました」が出る。開こうとした URL を覚えない |
| **B〔決定〕** | `exceptionHandling` に、標準の部品を組み合わせた `authenticationEntryPoint`〔GET〕と `accessDeniedHandler`〔POST〕を置き、「古いセッションID か」で分ける | 今の 403 のテストは、そのまま通る見込み。Cookie を消す設定は要らない。開こうとした URL も覚える〔U5 のまま使わない〕 | 案A より長い。(d) は 403 のまま |
| C | 設定は変えず、403 の文とボタンだけ変える | いちばん小さい | 自動では移らない。GET は文なしのまま。ログイン中の人にも「ログインして」と出る |
| D | CSRF のトークンを Cookie に置く | 再起動でもトークンが合う | 守りの置き場所が変わる。すすめない |

- 案B の部品は、すべて Spring Security 7.1.2 にあることを確かめた。CSRF の確認〔`CsrfConfigurer`〕は、`exceptionHandling` で設定した `accessDeniedHandler` を使う〔jar で確かめた〕。
- 形の例〔細かい書き方は、作るときに確かめる〕：

```java
// 古いセッションID（サーバーにもうないログインの番号）で来たか
RequestMatcher expired = req -> req.getRequestedSessionId() != null && !req.isRequestedSessionIdValid();
.exceptionHandling(e -> e
    // 画面を開く（GET）：古ければ /login?expired、そうでなければ今までどおり /login
    .authenticationEntryPoint(new DelegatingAuthenticationEntryPoint(
        new LoginUrlAuthenticationEntryPoint("/login"),
        new RequestMatcherEntry<>(expired, new LoginUrlAuthenticationEntryPoint("/login?expired"))))
    // ボタン（POST）の CSRF エラー：古ければ /login?expired、そうでなければ今までどおり 403
    .accessDeniedHandler(new RequestMatcherDelegatingAccessDeniedHandler(
        new LinkedHashMap<>(Map.of(expired, /* /login?expired へ移す */)), new AccessDeniedHandlerImpl())))
```

### 2.4 入力の途中で切れたとき

- 今：POST は CSRF の確認で止まり、Controller まで届かないので保存されない〔`TransactionControllerTest` 181-190 行が確かめている〕。「戻る」は開き直しになり〔`back-reload.js`、または `no-store` で読み直し〕、ログイン画面へ移るので、入れた値は消える〔設計書 7.10 にも書いてある〕。
- 案B でも同じ。守る方法〔ブラウザに値を残す JavaScript、ログイン後の送り直し、時間を長くする〕は使わない〔設計書 7.10〕。

### 2.5 エラーの画面の見た目

- 今：`main.container.narrow`〔420px〕の中で、h1〔28px〕が文をまるごと持ち 2 行になる。リンクだけ。ヘッダーなし。
- 決まった形と文は、設計書 2.5・7.7。

### 2.6 文書で直した所

- 要件定義書：4.1〔ログインが切れたときの 1 項目〕、8 章〔入力の途中の値を残すこと〕。
- 設計書：2.3〔お知らせの枠の表、ログイン画面の表示〕、2.5、2.6 の表、4.4.1〔`AuthController`〕、4.4.2〔`common.html`、`public-header.html`、`error/404.html`・`error/403.html`〕、4.5〔`SecurityConfig`〕、7.3、7.6、7.7、7.10、7.11、7.12〔S10〕。

### 2.7 今あるテスト

| テスト | 確かめていること |
|---|---|
| `SecurityBehaviorTest` 61-69 行〔9 件〕 | 未ログインの GET がログイン画面へ |
| `SecurityBehaviorTest` 100-111 行〔4 件〕 | トークンなしの POST が 403 |
| `SecurityBehaviorTest` 203-213 行〔2 件〕 | 403・405 の画面の文と状態コード〔`/error` を直接開く〕 |
| `SecurityBehaviorTest` 230-252 行 | ログイン後はいつもトップ〔U5〕 |
| `SecurityBehaviorTest` 316-324 行 | ログアウトの文 |
| Category・Vendor・PaymentMethod の各 132-136 行〔各 3 件〕、`TransactionControllerTest` 175-190 行〔4 件〕、`BudgetControllerTest` 182-189 行〔1 件〕、`TransactionRowActionsTest` 351 行〔1 行〕 | トークンなしの POST が 403〔`SecurityBehaviorTest` の 4 件と合わせて 19 か所〕 |
| `BudgetControllerTest` 794-838 行 | 400 の画面の文、`href="/transactions"` |
| `GlobalExceptionHandlerTest`、`HeaderCountTest` 101-110 行、`ComparisonControllerTest` 187-196 行など | 404 の画面 |
| `CacheControlHeaderTest` 59-64 行 | `/login?error`・`?logout` に `no-store` |
| `MessagesTest` 68-78 行 | 認証のメッセージ〔`error.page.*` はない〕 |

ないもの：ログイン切れ〔古いセッションID〕、5xx の画面の文、エラーの画面のボタン・ヘッダー。

## 3. 段階〔1 段階 ＝ 1 コミット〕

各段階の流れ：**文書 → テスト〔先に失敗を確かめる〕→ コード → 関係するテストだけを流す → 止まって確認 → コミット〔本人〕**。全体のテスト〔`.\mvnw clean test`〕は、各段階の最後に 1 回だけ。

### 3.1 段階 1：ログインが切れたら、ログイン画面へ案内する

- **文書**：要件定義書 4.1・8 章〔済み〕、設計書 2.3・2.6・4.4・4.5・7.3・7.6・7.10・7.11・7.12〔済み〕。
- **変えるもの**：
  - `SecurityConfig`：`exceptionHandling` に、決まったこと 2 の部品を置く。「古いセッションID か」の判定は 1 か所にまとめる。`defaultSuccessUrl("/", true)` は変えない。
  - `AuthController.login`：`?expired` があれば、案内のメッセージのキーを渡す。
  - `messages.properties`：ログインが切れたときの文のキーを足す〔例：`info.session.expired=ログインが切れました。もう一度ログインしてください。`。キーの名前は作るときに決め、記録に書く〕。
  - `fragments/common.html` の `messages`：案内〔`alert alert-info` と `info` のアイコン〕を足す。成功・失敗は変えない。
- **変えないもの**：CSRF の設定〔無効にしない〕、`invalidSessionUrl` は使わない、`application.properties`〔30 分のまま〕、エラーの画面〔段階 2〕。
- **先に書くテスト**〔新しいクラスにまとめる。例：`SessionExpiredTest`〕：
  - 古いセッションID の形は、MockMvc で `with(req -> { req.setRequestedSessionId("old"); req.setRequestedSessionIdValid(false); return req; })` で作る〔`MockHttpServletRequest` にあるメソッド。spring-test の jar で確かめた〕。
  - 古いセッションID の GET〔`/`、`/transactions` など〕→ `/login?expired` へ移る。
  - 古いセッションID のトークンなし POST〔取引の登録など〕→ `/login?expired` へ移る。**保存されない**。
  - 古いセッションID の POST `/login`・`/logout`・`/register` → `/login?expired`。
  - `/login?expired` に「ログインが切れました。もう一度ログインしてください。」が、`alert-info` と `info` のアイコンで出る。成功・失敗の形は出ない。
  - 古いセッションID でないとき：今までどおり〔未ログインの GET は `/login`、トークンなしの POST は 403〕。今ある 19 か所のテストが、1 行も変えずに通る。
  - `CacheControlHeaderTest` に `/login?expired` を足す。`MessagesTest` に新しいキーを足す。
- **わざと壊して確かめる**〔守りに関わる所なので行う〕：判定の `!isRequestedSessionIdValid()` を外すと、「古いセッションID でないときは 403」のテストが失敗すること。確かめたら手で戻す。
- **完了の条件**：上のテストが、直す前は失敗し、直した後は通る。今ある 403 のテスト 19 か所が変えずに通る。全体のテストが失敗 0。4 章のブラウザでの確かめを、本人が行う。

### 3.2 段階 2：エラーの画面の形と文

- **文書**：設計書 2.5・2.6・4.4.2・7.7〔済み〕。
- **変えるもの**：
  - `error/404.html`・`403.html`・`4xx.html`・`5xx.html`：ロゴだけのヘッダー〔`fragments/public-header :: publicHeader`〕、1 行の見出し、`alert alert-danger` と `alert` のアイコン、主ボタン「トップへ」〔`th:href="@{/}"`〕。
  - `messages.properties`：`error.page.*` を、見出しとお知らせの文に分ける〔文は設計書 7.7 の表のとおり。文ごとに「。」〕。`error.page.backToTransactions` は「トップへ」のキーに置きかえる。`success.accountRegistered` を「登録しました。ログインしてください。」に直す〔決まったこと 8〕。
  - `style.css`：要るときだけ、お知らせとボタンの間の余白など。新しい色は増やさない。
- **テスト**：
  - 先に直すテスト：`SecurityBehaviorTest` 203-213 行〔文〕、`BudgetControllerTest` 805 行〔文〕・831-838 行〔リンク先 `/transactions` → `/`〕、登録の文を確かめているテスト〔「登録しました。ログインしてください」〕、`MessagesTest`。
  - 足すテスト：4 つの画面に、ヘッダーが 1 つ〔ロゴだけ。メニュー・ユーザー名・ログアウトなし〕、見出し、`alert-danger` と `alert` のアイコン、`href="/"` の主ボタンがあること。5xx の画面の文〔今はテストがない。`/error` に状態コード 500 を付けて開く〕。詳しい中身が出ないこと〔今のテストと同じ〕。
- **完了の条件**：全体のテストが失敗 0。本人がブラウザで、4 つの画面〔404：ない URL、403：(d) の操作、4xx：削除の URL を GET で開く、5xx：DB を止める〕の見た目を確かめる。

## 4. ブラウザでの確かめ方〔本人〕

1. **再起動**：ログインして、取引の登録画面に入力する。アプリを止めて、もう一度起動する。「保存」を押す → ログイン画面に「ログインが切れました。もう一度ログインしてください。」が出る。取引は保存されていない。ログインするとトップへ移る。
2. **画面を開く**：同じく再起動のあと、メニューのリンクを押す → 同じ文のログイン画面。
3. **時間で切れる**：ファイルは変えずに、起動のときだけ 1 分にする。

   ```
   .\mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.servlet.session.timeout=1m"
   ```

   ログインして、2 分ほど何もしないで待ってから、ボタンを押す〔サーバーは、切れたセッションをしばらくの間隔で片付けるので、1 分ちょうどでは切れないことがある〕→ 1 と同じ。
4. **ログアウト**：ログアウトする → 「ログアウトしました」が出る〔「ログインが切れました」ではない〕。
5. **限界の確かめ**：別のタブでログアウトしてログインし直し、古いタブでボタンを押す → 403 の画面〔段階 2 のあとは、新しい文と「トップへ」〕。

## 5. 分かっている限界

1. **まだログインしていない人にも「ログインが切れました」と出る**：ログイン画面〔や登録画面〕を開いたまま、再起動か 30 分の放置でセッションが消えたあと、「ログイン」〔や「登録」〕を押すと、`/login?expired` へ移り、「ログインが切れました。もう一度ログインしてください。」と出る。登録画面に入れていた値は残らない。ログアウトのボタンも同じ。理由：サーバーからは、消えたセッションがログイン済みだったかどうか分からないため。
2. **(d) は 403 の画面のまま**：別のタブでログアウトしてログインし直したあと、古いタブでボタンを押したときは、セッションID は新しく有効なので、ログイン切れとは見分けられず、403 の画面になる。403 の画面の文〔「別の画面でログインし直したか、画面が古くなったため、受け付けられませんでした。内容は保存されていません。画面を開き直して、もう一度操作してください。」〕で案内する。
3. **入力の途中の値は残らない**：ログインが切れたあとに送った内容は保存されず、入力していた値も残らない〔要件定義書 4.1・8 章〕。
4. **元の画面には戻らない**：ログインし直したあとは、いつもトップへ移る〔U5〕。
5. MockMvc のテストでは、本物のブラウザの Cookie とサーバーのセッションの動きはそのまま再現できない〔古いセッションID の形を作って確かめる〕。本物の動きは、4 章のブラウザの確かめで見る。

## 6. 決まっていない点

なし〔キーの名前、テストのクラスの名前は、作るときに決めて記録に書く〕。

## 7. 記録

- 2026年11月16日：調べものと案を報告〔ファイルは変えなかった〕。決めてほしいこと 1〜6 は、すべておすすめどおりに決まった。決まったこと 8・9 が足された。
- 2026年11月16日：issue 030 とこの手順書を作った。要件定義書 4.1〔ログインが切れたときの 1 項目〕・8 章〔入力の途中の値を残すこと〕を直した。設計書 2.3・2.5・2.6・4.4・4.5・7.3・7.6・7.7・7.10・7.11・7.12〔S10〕を直した。コードとテストは変えていない。
- 2026年11月16日：本人の確認で、文書を 3 か所直した。要件定義書 4.1 は「30分」を書かず「しばらく操作しなかったとき（時間は設計書 7.10）」に〔決まったこと 7〕。403 と 404 のお知らせの文〔設計書 7.7、決まったこと 11〕。
- 2026年11月16日：**段階 1 を行った**。
  - キーの名前は `info.loginExpired`、Model の名前は `infoMessageKey`、テストのクラスは `SessionExpiredTest`〔`security`〕にした。
  - テスト：新しい `SessionExpiredTest`〔31 件：古いセッションID の GET 6、POST 10、保存されない 2、古くないときは今までどおり 9、ログイン画面の文 4〕。`MessagesTest` に 1 件、`CacheControlHeaderTest` に 1 件〔`/login?expired`〕。作る前に、`SessionExpiredTest` 19 件と `MessagesTest` 1 件が失敗した〔`CacheControlHeaderTest` の 1 件は、作る前から通る。ログイン画面は前から `no-store` のため〕。既存のテストは 1 行も変えていない〔トークンなしの POST が 403 の 19 か所も、そのまま通った〕。
  - コード：`SecurityConfig`〔`exceptionHandling` に `DelegatingAuthenticationEntryPoint` と `RequestMatcherDelegatingAccessDeniedHandler`。判定は `OLD_SESSION` の 1 か所〕、`AuthController`〔`?expired`〕、`messages.properties`、`fragments/common.html`〔案内の形〕。
  - **わざと壊して確かめた**：`OLD_SESSION` を常に `false` にすると、`SessionExpiredTest` の 18 件〔GET 6、POST 10、保存されない 2〕が失敗した。手で戻した。
  - `.\mvnw clean test`：2,629 件 → 2,662 件〔+33〕、失敗 0。`docs/testing.md`〔セキュリティ 66 → 98、そのほか 82 → 83〕と README の件数を直した。
- 2026年11月17日：**段階 2 を行った**。
  - コード：`error/404.html`・`403.html`・`4xx.html`・`5xx.html` を同じ形に〔ロゴだけのヘッダー `public-header`、h1、`alert alert-danger` と `alert` のアイコン、主ボタン「トップへ」〔`/`〕。幅 420px のまま〕。`messages.properties`：`error.page.*` を見出しと `.message`〔お知らせの文〕に分け、`error.page.backToTransactions` を `error.page.toTop` に置きかえ、`success.accountRegistered` の終わりに「。」。`style.css` は変えていない〔新しい色も規則もなし〕。
  - テスト：新しい `ErrorScreensTest`〔35 件：見出しと文 6、お知らせの形 4、「トップへ」4、「取引一覧へ戻る」がない 4、ロゴだけのヘッダー 4、ユーザー名・メニュー・ログアウトがない 4、文の「。」9〕。5xx の画面の文〔500・503〕を含む。作る前に、34 件が失敗した〔見出しの短さの 1 件〔404〕だけ通った〕。直した既存のテスト：`BudgetControllerTest` の `the400PageHasALinkBack` → `the400PageHasAButtonBackToTop`〔前：`href="/transactions"` がある。後：「トップへ」の主ボタンがあり、`/transactions` のリンクはない〕、`MessagesTest`〔登録の文に「。」〕、`SecurityBehaviorTest`〔`CLIENT_ERROR_MESSAGE` を見出しだけに〕、`HeaderCountTest`〔コメントだけ〕。
  - **わざと壊して確かめた**：`403.html` からお知らせを消すと、`ErrorScreensTest` の 2 件〔見出しと文、お知らせの形〕が失敗した。手で戻した。
  - `.\mvnw clean test`：2,662 件 → 2,697 件〔+35〕、失敗 0。`docs/testing.md`〔画面 1011 → 1046〕と README の件数を直した。
