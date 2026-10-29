# 002：予算設定の POST で、行（rows）がないとき・null のときに、NullPointerException（500）にならないか

## 概要

ChatGPT のコードレビューでの指摘。`BudgetForm` の `rows` が null のとき（`POST /budgets` で行を1つも送らないとき）、`BudgetService.verifyCategoryIds` などで `form.getRows()` を反復する箇所が `NullPointerException` になり、500 エラーになるのではないか。行（`Row`）そのものが null の場合もありうるのではないか。

- 出どころ：ChatGPT のレビュー（利用者が、ここまでのコードを ChatGPT に見せて得た指摘）。
- 別の指摘として、予算・取引から科目などへの複合外部キー（`(科目ID, user_id)` の組み合わせを外部キーにする案）も受けたが、**設計書 3.9 で採用しないと決めた点なので、この issue では対応しない**（Service での確認と、2ユーザーでのテストで守る、という決定のとおり）。

## 再現手順または要望の内容

使い捨てのテスト（MockMvc で `POST /budgets?year=2026&season=winter` を送る14通りと、`BudgetService` を直接呼ぶ9通り。テストは確認後に削除した）で確かめた（2026-10-30）。

### 画面からの送信（HTTP）：ChatGPT の指摘のとおりには、**再現しない**

| 送り方 | 結果 |
|---|---|
| `rows` を1つも送らない | 302（何も保存せず、同じ季節の画面へ戻る）。500 にならない |
| `rows[1]` だけ（`rows[0]` がない） | 404。Spring が `rows[0]` を空の行で埋めるため、科目IDなしとして 404 |
| `rows[0]=`、`rows[0]=abc`（行そのものを値として送る） | 404（空の行ができ、科目IDなしとして 404） |
| `rows[0].amount` だけ／`rows[2].amount` だけ | 404（科目IDなし） |
| `rows[0].categoryId` だけ（金額なし） | 200（「予算額を入力してください」で表示し直し） |
| `rows[0].categoryId` を2つ（`正しいID,999999`） | 302（保存できた。2つ目は使われない。自分の科目の確認は通っている） |
| 正常な送信（基準） | 302 |

- 理由：`BudgetForm.rows` は `new ArrayList<>()` で初期化されていて、`rows` が送られなければ `setRows` は呼ばれない。送られても、行は空の `Row` で埋められる。画面（HTTP）から、`rows` が null になる・行が null になる送り方は、見つからなかった。

### Service を直接呼ぶ：指摘のとおり、**NullPointerException になる**

| 呼び方 | 結果 |
|---|---|
| `rows` が null の Form で `rowsOf` / `save` | NullPointerException（`form.getRows()` の反復） |
| 行の中に null がある Form で `rowsOf` / `save`（2行目が null も同じ） | NullPointerException（`row.getCategoryId()`） |
| 金額が null（科目IDは正しい）で `save` | NullPointerException（`int` への取り出し） |
| `new BudgetForm(null)` | NullPointerException |
| `form` が null で `save` | NullPointerException |

- これらは、Controller が Form の入力チェック（`@NotNull` など）を済ませてから Service を呼ぶ前提で、Service が防がない作りであることによる。今の画面（HTTP）からは到達しない。将来、別の呼び出し元ができたとき・テストで null を渡したときに、落ちる。

### 別に分かったこと（ChatGPT の指摘とは別。500 または表示の乱れ）

| 送り方 | 結果 |
|---|---|
| `rows[-1].categoryId=…` | **500**（`InvalidPropertyException`。束縛が失敗する） |
| `rows[abc].categoryId=…` | **500**（`InvalidPropertyException`、`NumberFormatException`） |
| `rows[256]` 以上（257行目以降） | 500（束縛が失敗する。Q6-9 の A で既知。設計書 5.7） |
| `rows=`（空）、`rows=abc` | 200。型変換のエラーで「入力エラー」の画面になるが、行は空のまま表示される（テンプレートの作りから、「先に科目を追加してください」の案内が出るはず。画面の本文までは確認していない）。500 ではない |

- いずれも、画面の操作では起きない（書き換えた送信だけ）。何も保存されない。

## 追加の指摘と、決めたこと（2026-10-30）

最初の対応（案 A：`BudgetService` の null の守り）のあと、ChatGPT から追加の指摘を受けた。本人（利用者）が、次のとおり決めた。

### 追加の指摘（ChatGPT のレビュー）

1. `rows[-1]`・`rows[abc]` などの書き換えた送信で、束縛に失敗したとき（`InvalidPropertyException`）に、500 になる。500 は「サーバーの不具合」の意味なので、送信の形がおかしいだけのときは、区別できるように 400 にしたほうがよい。
2. 科目の数に上限がないと、科目が257個を超えたとき、予算設定の画面で行を受け取れず（Spring の標準で256行まで）、普通に使っていても予算を保存できなくなる。

### 決めたこと

| 番号 | 決めたこと | 反映 |
|---|---|---|
| 1 | `BudgetService` の入口で、form・rows・行・金額（保存のとき）の null を `NotFoundException` にする（前回の案 A） | 済み（`BudgetServiceTest` 6件） |
| 2 | `rows[-1]`・`rows[abc]` などの書き換えた送信で、束縛に失敗したときは、500 ではなく **400** にして、`error/4xx.html` を出す。理由：500 はサーバーの不具合、400 は送信の形がおかしい、という意味で、区別できるようにするため | 設計書 5.7、7.7。`GlobalExceptionHandler` |
| 3 | 科目・取引先・支払方法は、**それぞれ1人あたり256個まで**にする（要件定義書 5章、**R14**）。理由：要件に上限がないと、科目が257個を超えたとき、予算を保存できなくなる。256個は、予算の画面で受け取れる標準の256行とちょうど同じ | 要件定義書 5章、設計書 5.4・6.2、`phase-1-design.md` の R14 |
| 3-1 | 上限を超えて追加しようとしたら、「科目は256個までです」などのメッセージで、追加させない | 設計書 5.1・5.4・6.2 |
| 3-2 | 予算の画面で受け取れる行の数は、標準の**256のまま変えない** | 設計書 5.7 |
| 3-3 | 境目をテストで確かめる：256行の予算を保存できる／257行を送ると 400／256個目は追加できる／257個目は追加できない | `BudgetControllerTest`、`CategoryServiceTest` など |

- 以前の決定との関係：`rows[-1]` などの500を「直さない」、科目の上限なし（Q6-9 の A）は、この決定で見直した。Phase 6 手順書の Q6-9 に変更を追記してある。
- `rows=`（空）を送ると、行が空のまま表示される点は、今までどおり直さない（Phase 8 の確認表に入れる）。

## 対応状況

**対応済み（2026-10-30。追加の指摘への対応を含む）。**

- 画面（HTTP）からの `rows` なし・`rows[1]` だけの送信は、もともと 500 にならない（再現しなかった）。
- **対応1（`BudgetService` の null の守り）**：form・rows・行・金額（保存のとき）が null なら `NotFoundException`。表示し直す `rowsOf` では、金額が null でもよい。検査は何かを書く前に行う。`BudgetServiceTest` に6件。
- **対応2（束縛の失敗は 400）**：`rows[-1]`・`rows[abc]`・257行目以降などで束縛に失敗したとき（`InvalidPropertyException`）は、500 ではなく **400** にして、`error/4xx.html` を出す（`GlobalExceptionHandler`）。何も保存されない。画面に詳しい中身は出ない。`BudgetControllerTest` に、256行は保存できる・257行は400・行の番号の書き換え9通りは400、を確かめるテスト。
- **対応3（個数の上限、R14）**：科目・取引先・支払方法は、それぞれ**1人あたり256個まで**。`Limits.MAX_MASTERS_PER_USER`（256）、`LimitExceededException`、`countByUserId`（3つの Repository）、3つの Service の追加で上限を確かめる（同じ名前の確認より先）。3つの Controller は、名前の項目の横に「科目は256個までです」「取引先は256個までです」「支払方法は256個までです」を出し、入力値を残す。予算の画面で受け取れる行の数は、標準の256のまま変えていない。`MasterLimitServiceTest`（14件）と `MasterLimitControllerTest`（5件）で、256個目は追加でき・257個目は追加できないこと、ほかのユーザーの個数は数えないこと、削除すると追加できることを確かめた。
- 要件定義書 5章と `phase-1-design.md` の R14、設計書 5.1・5.4・5.7・6.2・6.8・7.7、Phase 6 手順書の Q6-9、実行計画書、README に反映した。`.\mvnw test` は 1061 件すべて成功。
- **直さないと決めた点（Phase 8 の確認表に入れる）**：`rows=`（空）を送ったときに、行が空のまま表示される点。
- **既知の制限**：個数の上限は、アプリ（Service）だけで守り、DB の制約にはしていない。同時に2つの追加が重なると、まれに257個になりうる。そのとき、予算設定の保存は、行が256行を超えるため 400 になる。
- `new BudgetForm(null)` が NullPointerException になる点は、コンストラクタの引数（プログラムの誤り）なので、そのままにした。
