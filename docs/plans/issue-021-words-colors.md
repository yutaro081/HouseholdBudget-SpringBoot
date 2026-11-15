# issue 021：お知らせの色、言葉と色、円グラフの色をそろえる

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は [issue 021](../issues/021-お知らせの色をそろえる.md) を進めるための手順書です。

> **状態（2026年11月16日）**：◐ **段階 1〔色〕はコミット済み。段階 2〔言葉〕・3〔円グラフ〕を行った。ブラウザでの確かめと、コミット〔本人〕が残っている**。`.\mvnw clean test` は 2,607 件成功。段階 2〔言葉〕・3〔円グラフ〕の文書は、その段階で直す。

- ブランチ：`feature/issue-021-words-colors`。秘密情報：パスワード・接続先の値は、どこにも書かない。
- 進め方：1 つの段階が終わったら止まって、確認を待つ。変更は、文書が先〔要件定義書 → 設計書 → この手順書〕、コードは後。**テストが先**〔失敗を確かめてから実装〕。コミットと push は本人。
- 守ること：意味の色〔赤 `--hb-over`・青 `--hb-low`・あんず色〕の値は変えない。新しい色は増やさない。JavaScript・CDN は使わない。`th:utext` は使わない。

## 1. 決まったこと〔2026年11月16日・本人〕

issue 021 の文書の「決めたこと」1〜7 のとおり。段階 1 に関わるものは次のとおり。

| 番号 | 決まったこと |
|---|---|
| 1 | お知らせは、白い地・本文の色の文字・左に 4px の線。線の色だけで分けず、アイコンも付ける〔飾りなので `aria-hidden`〕。成功＝ターコイズの線と `check`、失敗＝エラーの赤の線と `alert`、案内＝補足の灰色の線と `info` |
| 1-2 | アイコンの部品に、`alert`〔丸の中に「！」〕と `info`〔丸の中に「i」〕を足す〔同じ作り：線の SVG、`currentColor`、`aria-hidden`〕。17 個 → 19 個 |
| 1-3 | **複写のお知らせも、案内の形にそろえる**〔`info` と `--hb-text-sub` の線〕。issue 019 で決めたターコイズの線を変える。理由：成功〔ターコイズの線と `check`〕は「保存できた」の意味になるので、「まだ保存されていない」複写のお知らせが、成功に見えないようにするため |
| 2 | 失敗の線は `--hb-danger`〔`#B02A37`〕 |
| 3 | Bootstrap の標準の緑・黄は、やめる |
| 6 | 一覧の「削除」ボタンを `--hb-danger` にする〔予算超えの赤を別の用途に使っているまちがいを直す〕 |
| 実装で決めたこと | 成功のアイコンの色は `--hb-teal-text`〔`--hb-teal` は枠の線だけに使う決まりのため。線は `--hb-teal`〕 |

## 2. 段階

| 段階 | 中身 |
|---|---|
| **1. 色** | お知らせの見た目とアイコン、`#c62828` を `var(--hb-over)` に〔4 か所〕、「—」を `--hb-text-sub` に、「削除」ボタンを `--hb-danger` に |
| 2. 言葉 | 「予算なし」をなくす〔要件定義書 4.5〕、トップの凡例、文書の古い説明〔その段階で、この手順書に書き足す〕 |
| 3. 円グラフ | 一覧の色の四角の枠〔その段階で、この手順書に書き足す〕 |

### 2.1 段階 1：色

- **変えるもの**
  - `fragments/icons.html`：`alert` と `info` を足す。
  - `fragments/common.html`〔成功・失敗のメッセージ〕と、案内の 4 か所〔`budgets/form.html`、`comparison/index.html`、`top/index.html`、`transactions/form.html`〕、複写のお知らせ〔`transactions/form.html`〕：文の左に `<span class="alert-icon">` とアイコンを入れる。文は今のまま。
  - `style.css`：`.alert-success`・`.alert-danger`・`.alert-warning`・`.alert-info` を、白い地・本文の色・左の線にする。アイコンの色。`#c62828` の 4 か所を `var(--hb-over)` に。`.budget-dash` を `var(--hb-text-sub)` に。`.row-btn-danger` を `var(--hb-danger)` に。
- **先に書くテスト**
  - `IconsFragmentTest`：19 個になる。`alert` と `info` の形。
  - `ColorSchemeStyleSheetTest`：4 種類のお知らせの規則〔地・文字・線〕。アイコンの色。Bootstrap の標準の色の値を書かないこと。予算超えの赤を値で直接書かないこと〔変数の定義のほか〕。「—」と「削除」ボタンの色。
  - 新しいクラス〔お知らせの HTML〕：成功・失敗・案内・複写のお知らせに、それぞれのアイコンが `aria-hidden` で入ること。文はそのまま出ること。
  - 赤を見張っているテスト〔`HitokotoStyleSheetTest`、`LayoutStyleSheetTest`、`AuthAndListStyleSheetTest`〕が、`#c62828`〔大文字・小文字を問わない〕と `var(--hb-over)` の両方を見つけられるようにする。**わざと赤を入れて、見つけられることを確かめる**〔確かめたら戻す〕。
- **完了の条件**
  - `.\mvnw clean test` が全部成功。件数の前と後を報告する。
  - ブラウザで、本人が確かめる：成功・失敗・案内・複写のお知らせの見た目〔白い地、左の線、アイコン〕。一覧の「削除」ボタンの赤。予算設定の「—」。

## 3. 決まっていない点

なし。

## 4. 実施の記録

- 2026年11月16日：021 の文書を、決めたこと 1〜7 を入れた形に直した。設計書 2.3〔お知らせの枠の行、入力エラーの赤の行、赤・青の行、補足の文字の行、アイコン〕を直した。この手順書を作った。
- 2026年11月16日：**段階 1 を行った**。
  - 先にテストを書いて、21 件の失敗を確かめた：`IconsFragmentTest` 5 件、`ColorSchemeStyleSheetTest` 6 件、`NoticeMarkupTest`〔新規〕4 件、`MeterStyleSheetTest` 2 件、`TopSeasonSentenceTest` 1 件、`TopWeekPickStyleSheetTest` 1 件、`TransactionRowActionsTest` 1 件、`BudgetAutoControllerTest` 1 件。実装の前から通ったもの：`ColorSchemeStyleSheetTest.noticesDoNotUseTheBootstrapGreenRedOrYellow`〔今も style.css に標準の色の値を書いていないため〕、`NoticeMarkupTest.noKeyGivesNoMessage`、赤を見張る 3 つのテスト〔守りのテスト〕。
  - 実装で決めたこと：成功のアイコンの色は `--hb-teal-text`〔1 章〕。
  - 赤を見張るテスト〔`HitokotoStyleSheetTest`、`LayoutStyleSheetTest`、`AuthAndListStyleSheetTest`〕を、`#c62828`〔大文字・小文字を問わない〕と `var(--hb-over)`〔青の `--hb-low` も〕を見つけるようにした。わざと 3 つの規則に赤を入れる〔`.hitokoto-frame` と `.auth-card` に `var(--hb-over)`、`.top-season-pace` に大文字の `#C62828`。どれも、前のテストでは見落とす書き方〕と、3 つとも失敗することを確かめて、戻した。
  - `.\mvnw clean test`：2,593 件 → 2,605 件〔`ColorSchemeStyleSheetTest` +6、`NoticeMarkupTest` +5、`IconsFragmentTest` +1〕、失敗 0 件。`docs/testing.md` と README の件数を直した。
  - 段階 2 で直す、と残したもの：`budgets/form.html` の古いコメント「（この季節は未設定）」〔言葉 3〕。
- 2026年11月16日：段階 1 のコミット〔`63800f5`〕が `main` に入っていたので、本人がブランチを直した〔`feature/issue-021-words-colors` に移し、`main` を `origin/main` に戻した〕。Claude Code は、段階 1 のあいだ、ブランチを切りかえる git の操作をしていない〔使ったのは `git branch --show-current` と `git status --short` だけ〕。
- 2026年11月16日：**段階 2〔言葉〕と段階 3〔円グラフ〕を行った**。作業の最初と最後の `git branch --show-current` は、どちらも `feature/issue-021-words-colors`。
  - 言葉 1：トップの今週の進み具合の「予算なし」をなくし、「/ 未設定」だけにした〔合計の行と科目の行〕。要件定義書 4.5、設計書 2.3・5.11 を直した。
  - 言葉 2：トップの凡例を「予算内（週の予算の1/3を超え、週の予算以下）」にした。
  - 言葉 3：設計書の古い説明を、今の画面に合わせた〔ログイン・登録の説明文、合計行の上の線、`diff-low`・`row-within`。指示の 3 か所のほかに、4.4.2 の「テストが確かめるクラス」の一覧にも `row-within`・`diff-low` が残っていたので直した〕。`budgets/form.html` のコメントを「未保存」のチップに直した。
  - `style.css` のお知らせのコメントの「issue 019」は正しかった〔根拠：`git log -S".alert-info {"` で、`.alert-info` の規則を足したのは `1bccfce issue 019：複写のお知らせの色をそろえ、issue 021 を記録`。手順書 issue-019-auto-budget.md の実施の記録〔2026年11月11日〕にも同じことが書いてある〕。
  - 円グラフ 1：一覧の色の四角〔`.top-chip`〕に、1px の枠〔`--hb-input-border`〕を付けた。設計書 2.3 の円グラフの行に書いた。
  - 先にテストを書いて、4 件の失敗を確かめた：`TopLayoutTest` 3 件〔「予算なし」が出ている 2 件、凡例 1 件〕、`TopStyleSheetTest` 1 件〔枠がない〕。
  - `.\mvnw clean test`：2,605 件 → 2,607 件〔`TopLayoutTest` +1、`TopStyleSheetTest` +1〕、失敗 0 件。`docs/testing.md` と README の件数を直した。
