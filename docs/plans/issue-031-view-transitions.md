# issue 031：ページ移動のなめらかな切りかわり（View Transitions）

全体の位置づけは [実行計画書](execution-plan.md) を見てください。この文書は、[issue 031](../issues/031-ページ移動のなめらかな切りかわり.md)（CSS だけで、画面移動の切りかえをなめらかにする）を進める手順書です。

> **状態（2026年11月17日）**：● **完了**。View Transitions を入れた〔`@view-transition` は `common.html` の head の `<style>`、ほかは style.css〕。スクロールバーによる左右のずれを直した〔`scrollbar-gutter`〕。実機は Chrome〔Windows〕で確かめた〔iPad の Safari は確かめずに終えた〕。登録・更新のあとの移動は、切りかえなしで移る〔推測：POST のあとのリダイレクトは対象外〕。`.\mvnw clean test` は 2,708 件成功。

- ブランチ：`feature/issue-031-view-transitions`。秘密情報：パスワード・接続先の値は、どこにも書かない。
- 進め方：1 つの段階が終わったら止まって、確認を待つ。変更は、文書が先〔要件定義書 → 設計書 → この手順書〕、コードは後。**テストが先**〔失敗を確かめてから実装〕。コミットと push は本人。
- 今の決まりのまま：JavaScript は自分で書いた短いものだけ〔この issue では、新しい JavaScript は書かない〕。Bootstrap の JavaScript・CDN は使わない。`th:utext` は使わない。検索は必ず `user_id` で絞る。**CSRF 対策は無効にしない**。

## 1. 決まったこと〔2026年11月17日・本人〕

| 番号 | 決まったこと |
|---|---|
| 1 | 番号は 031 |
| 2 | やる。**ただし小さく**。 |
| 3 | **外す決まり**：実機で見て、よくならなければ〔違和感が出る、遅く感じる、など〕、外す〔段階 ④で決める〕。外し方は 5 章：②③ はコミットしていないので、`git restore`〔本人〕で戻し、文書に「試してやめた」と記録し、設計書 2.3 の項目も外す。**ただし、`html { scrollbar-gutter: stable; }`〔決まったこと 10〕は外さない**〔もともとあった左右のずれの直しのため〕。 |
| 4 | 書く場所は style.css の**末尾**。コメントつきの **1 つのまとまり**。**ただし、`@view-transition` の指定〔`@media (prefers-reduced-motion: no-preference)` の条件ごと〕は、`fragments/common.html` の head の部品の `<style>` だけに置き、style.css からは外す**〔2026年11月17日の見直し。2.6〕。ヘッダーの名前〔`.app-header`〕と、切りかえの時間の規則は style.css に置く。同じ指定を 2 か所に置かない。 |
| 5 | `@view-transition { navigation: auto; }` は、**`@media (prefers-reduced-motion: no-preference)` の中**に入れる。「動きを減らす」設定のときは、切りかえを始めない。**動きを減らす設定への対応には、`animation: none` を使わない**〔条件の中に入れて、切りかえを始めないだけにする〕。ヘッダーの古い像・新しい像の動きを消す書き方は、決まったこと 6 のとおり、必要になったら足す〔これとは別〕。 |
| 6 | ヘッダーを固定する書き方は、最初は **`.app-header` に `view-transition-name: app-header` を付ける 1 行だけ**。古い像〔old〕・新しい像〔new〕の動きを消す書き方は、**1 行だけで違和感が出た場合に足す。必要になるまで書かない**。 |
| 7 | `view-transition-name` は、**`app-header` の 1 つだけ**。ほかの要素に付けない〔同じ名前が 2 つあると、切りかえそのものが取りやめになるため〕。 |
| 8 | **値〔秒数など〕は、設計書に書かない**。style.css だけに置く〔値の重複を作らない〕。切りかえの時間は、style.css のまとまりの `@media (prefers-reduced-motion: no-preference)` の中で、`::view-transition-old(root)`・`::view-transition-new(root)` の `animation-duration` を少し短くする〔2026年11月17日の見直し〕。秒数そのものを確かめるテストは足さない。 |
| 9 | 要件定義書は変えない〔機能や守りに関わらないため〕。 |
| 10 | **スクロールバーによる左右のずれの直し**〔2026年11月17日・本人〕：実機で、取引一覧へ移ったときだけ、画面全体が左に動いたように見えた。`html { scrollbar-gutter: stable; }` を style.css に足す。**View Transitions を外すことになっても残す**。置き場所は、style.css の `html` の規則〔末尾のまとまりには入れない〕。設計書は、2.3 の「画面の切りかえ」ではなく、本文の幅の決まり〔issue 020〕のすぐ後に書く。 |
| 11 | **インライン CSS の例外**〔2026年11月17日〕：`common.html` の head の `<style>` 1 つだけを、インライン CSS の例外にする。中身は `@view-transition` の指定だけ〔ほかの見た目の規則を増やさない〕。理由は設計書 2.3「画面の切りかえ」に書く。CSP を入れるときは、この `<style>` を許す指定〔ハッシュなど〕が要る〔設計書 7.5 に注意として書く〕。 |

## 2. 調べた結果〔2026年11月17日〕

### 2.1 ブラウザの対応

| ブラウザ | 対応 | 出典 |
|---|---|---|
| Chrome・Edge | 126 から | Chrome の公式ドキュメント |
| Safari | 18.2 から〔Chrome のドキュメントの表示。**Safari 側の公式の資料では、確かめていない**〕 | 同上 |
| Firefox | 対応していない | 同上 |

- MDN の `@view-transition` は「Limited availability」〔Baseline ではない〕。最終更新 2026年4月20日。MDN の対応表の版番号は、取得できなかった。
- 出典の URL は、[issue 031](../issues/031-ページ移動のなめらかな切りかわり.md) の「調べた結果」にある。
- 対応していないブラウザ：規則が無視されて、今までどおりふつうに移動する。困ることはない。

### 2.2 動きの条件

- 対象：同じオリジンの移動。戻る・進む、と、ページの中の操作〔リンク、フォームの送信〕で始まった移動。対象外：アドレスバー、ブックマーク、リロード。
- 4 秒を超える移動は、切りかえが取りやめになる。
- Chrome では、切りかえの間、新しい画面の表示が少し待たされる〔render-blocking〕。表示の遅い画面では、待ちが長く感じられる可能性がある。
- `view-transition-name` の名前が重複すると、切りかえが取りやめになる〔MDN〕。ヘッダーは 1 画面に 1 つだけ〔`HeaderCountTest` が確かめている〕で、ログイン後のヘッダーもログイン前のヘッダーも、同じ `app-header` クラスなので、名前は重複しない。
- `.app-header` には、`position: fixed`・`sticky` がない。名前を付けると、その要素は独立した層になるが、今の画面に影響はないはず〔推測。実機で確かめる〕。

### 2.3 推測〔確かめた事実ではない〕

- **推測**：メニューの「今開いている画面」の印〔`aria-current`〕は、切りかえの瞬間に入れかわり、ふわっとは変わらない。
- **推測**：ヘッダーが違う画面の間〔ログイン画面 → トップ〕では、ヘッダーの位置や大きさが少し動いて見える。
- 実機で見て、どう見えるかを段階 ④で確かめる。

### 2.4 今あるテストへの影響

- style.css の文字を見ているテストの多くは、規則を取り出して、含まれる値を確かめている。末尾に足す規則と、ぶつからない。
- `LayoutStyleSheetTest`：`rule(css(), ".app-header")` が `padding: 8px 0` を含むことを確かめている。同じ規則に 1 行足しても通る。ただし、**`.app-header` の規則は、すでに 2 か所〔padding の規則と、帯の色の規則〕にある**。名前の 1 行は、**末尾のまとまりの中**に、別の規則として書く〔既存の規則は変えない〕。
- `ColorSchemeStyleSheetTest`：`rule(css, ":root")` は `:root {` の最初の規則を探す。`:root { … }` の規則を新しく書かない〔この issue では書かない〕。
- `FontAssetsTest`：`@import` と外の URL がないこと。今回の規則は、どちらも書かない。
- 流してはいない〔読んで確かめた〕。段階 ③ で、`.\mvnw clean test` を 1 回流して確かめる。

### 2.5 スクロールバーの場所〔`scrollbar-gutter`。2026年11月17日〕

| ブラウザ | `scrollbar-gutter`〔`stable` も同じ〕が使える最初の版 |
|---|---|
| Chrome | 94 |
| Edge | Chrome と同じ〔MDN のデータでは Chrome の値と同じ扱い〕 |
| Firefox | 97 |
| Safari〔Mac〕 | 18.2 |
| iPad の Safari | Safari と同じ 18.2〔MDN のデータでは Safari の値と同じ扱い〕 |

- 出典：[MDN：scrollbar-gutter](https://developer.mozilla.org/en-US/docs/Web/CSS/scrollbar-gutter)〔Baseline 2024、Newly available。2024年12月から、最新の端末・ブラウザで使える〕。版の数字は、[MDN の対応データ（browser-compat-data）](https://raw.githubusercontent.com/mdn/browser-compat-data/main/css/properties/scrollbar-gutter.json)。
- `stable`：昔ながらのスクロールバーでは、はみ出していなくても、`overflow` が `auto`・`scroll`・`hidden` なら、スクロールバーの場所をあけておく。オーバーレイ型のスクロールバー〔場所をとらない型。iPad など〕では、あけない〔MDN〕。**オーバーレイ型では、効果がない**〔何も起きない。もともと本文はずれない〕。iPad・iPadOS について書いた所は、MDN にない。
- 対応していないブラウザでは、規則が無視されるだけで、今までどおり〔ずれる〕。
- **既存の規則とのぶつかり**：style.css に、`html`・`body` の規則はない。`overflow` は、部品〔`.amount-group`〕の `overflow: hidden` だけ。bootstrap.min.css の `body{…overflow-y:…}` は、モーダルなどの部品の中の `body`〔`.modal-body` など〕で、画面の `html`・`body` ではない。`html` の `overflow` を書いた所は、ない。bootstrap の `:root{scroll-behavior:smooth}` は別の指定〔ぶつからない〕。ぶつかる所は、見つからなかった。
- **推測**：ずれの原因は、取引一覧が縦に長く、スクロールバーが出て、その分だけ、中央そろえの本文とヘッダーが左にずれること。もともとあったずれが、画面の切りかえで目に見えるようになった。直したあと、実機で消えたかを確かめる〔4 章の 9〕。

### 2.6 取引一覧へ移るときだけ、切りかえが取りやめになった件〔2026年11月17日〕

- **起きたこと**〔実機。Chrome〕：取引一覧へ移るときだけ、切りかえが起きずに一瞬で入れかわり、Console に `InvalidStateError: Transition was aborted because of invalid state. ViewTransition opt-in disabled` が出た。取引一覧からほかの画面へ移るときと、ほかの画面の間では、切りかわった。
- **ソースで確かめたこと**：Chromium の `ViewTransitionSupplement::ResolveCrossDocumentViewTransition()` は、**移った先の画面**で、切りかえの指定〔opt-in〕が「無効」なら、`kRejectInvalidState`・`kOptInDisabled` で切りかえを取りやめる。指定の値は、スタイルの計算のたびに更新される。出典：[Chromium：view_transition_supplement.cc](https://raw.githubusercontent.com/chromium/chromium/main/third_party/blink/renderer/core/view_transition/view_transition_supplement.cc)。
- **試した結果**〔実機。Chrome〕：同じ指定を `common.html` の head の `<style>` に置くと、取引一覧でも切りかわり、エラーも出なくなった。ほかのプロジェクトの記録にも、指定の入った CSS が遅れて届くと同じエラーになり、head に直接書いて直した例がある〔公式ではない：[anuarhdz/surface-elements PR #24](https://github.com/anuarhdz/surface-elements/pull/24)〕。
- **推測**：取引一覧は縦に長く、移った先で切りかえを判定するときに、`style.css` の指定がまだ使われていなかった。`style.css` は head の中にあり、描画を止める〔render-blocking〕はずなので、なぜ間に合わないのか、仕組みの細部は確かめていない。
- **決めたこと**：決まったこと 4・11。

## 3. 確かめられていないこと

| 番号 | 確かめられていないこと | 確かめ方 |
|---|---|---|
| 1 | Safari の版〔18.2 は、Chrome のドキュメントの表示。iPad の Safari の版との対応〕 | 実機〔iPad があれば〕 |
| 2 | 登録・更新の POST のあとの移動〔リダイレクト〕で、切りかえが動くか。公式のページに、書いていなかった | 実機〔4 章の表の 4〕。**結果**〔2026年11月17日、本人。Chrome〔Windows〕〕：ふつうに移動した〔切りかえは起きない。壊れない〕。理由は確かめていない〔推測：POST のあとのリダイレクトは対象外〕 |
| 3 | メニューの印の見え方〔2.3 の推測〕 | 実機 |
| 4 | 「動きを減らす」設定がオンのとき、切りかえが始まらないか。この書き方〔`no-preference` の中に入れる〕は、公式のページに載っていなかった | 実機 |
| 5 | 1 行だけで、ヘッダーが落ち着くか〔決まったこと 6〕 | 実機 |
| 6 | ずれの原因がスクロールバーか〔2.5 の推測〕。`scrollbar-gutter: stable` で、ずれが消えるか | 実機〔2026年11月17日：`scrollbar-gutter: stable` で、ずれは消えた〔Chrome〕。スクロールバーが原因という見込みに合う〕 |

## 4. 実機で確かめること〔本人。段階 ④〕

| 番号 | 確かめること | どうなれば〔よい〕 | 結果 |
|---|---|---|---|
| 1 | Chrome〔Windows〕：メニューのリンクで、トップ → 取引一覧 → 予算、と移る | 本文がやわらかく切りかわる。ヘッダーは動かない〔落ち着いて見える〕 | 切りかわる〔取引一覧へ移るときも。2.6 の直しのあと。2026年11月17日・Chrome〕 |
| 2 | Chrome〔Windows〕：**Windows の「アニメーション効果」の設定**を、オン／オフにして、1 と同じことをする。〔設定 → アクセシビリティ → 視覚効果 → アニメーション効果。オフにすると、ブラウザの「動きを減らす」の設定になる〕 | オフのとき、切りかえが始まらない〔一瞬で入れかわる〕 | OK〔2026年11月17日、本人。Chrome〔Windows〕〕 |
| 3 | iPad の Safari〔あれば〕：1 と同じことをする | 同じ。または、ふつうに移動する〔困らない〕 | 確かめずに終えた |
| 4 | **登録や更新のあとの移動**：取引を登録して保存 → 一覧へ移る。予算を保存、削除の確認 → 一覧へ戻る | 切りかえが動く。または、ふつうに移動する〔どちらでも壊れない〕。どちらかを記録する | ふつうに移動した〔切りかえは起きない。壊れない〕。理由は確かめていない〔推測：POST のあとのリダイレクトは対象外〕。3 章の 2 番の答え〔2026年11月17日、本人。Chrome〔Windows〕〕 |
| 5 | **メニューの印の見え方**：移動の前後で、「今開いている画面」の印〔下線・色〕が、どう変わるか | 違和感がない〔2.3 の推測の確かめ〕 | OK〔2026年11月17日、本人。Chrome〔Windows〕〕 |
| 6 | ログイン画面 → トップ〔ヘッダーが違う画面の間〕 | ヘッダーの動きに、違和感がない | OK〔2026年11月17日、本人。Chrome〔Windows〕〕 |
| 7 | 表示の遅い画面〔トップ、予実比較〕へ移るとき、待ちが長く感じないか | 感じない | OK〔2026年11月17日、本人。Chrome〔Windows〕〕 |
| 8 | 戻る・進むのボタン | 戻る・進むで、画面が壊れない。ログアウトのあとの「戻る」で、ログアウト前の画面が残らない〔`back-reload.js` が働く〕 | OK〔2026年11月17日、本人。Chrome〔Windows〕〕 |
| 9 | **取引一覧へ移ったとき、左右にずれない**〔トップ → 取引一覧、取引一覧 → トップ。Chrome。切りかえを動かしたまま、ヘッダーと本文が左右に動かない〕 | 左右にずれない。短い画面〔スクロールバーが出ない〕と長い画面を行き来しても、ヘッダーのロゴの位置が変わらない | ずれは消えた〔2026年11月17日・Chrome〕 |

- 1 行だけでヘッダーに違和感が出たら、古い像〔old〕・新しい像〔new〕の動きを消す書き方を足す〔決まったこと 6〕。足した書き方も、実機で確かめる。
- よくならなければ、外す〔決まったこと 3〕。

## 5. 段階とコミット〔コミットは本人〕

- **① 文書だけを先にコミットする**〔1 コミット〕。
- **②③ はコミットしない**。作業中のまま、段階 ④〔実機〕に進む。
  - **続けると決めたら**：②③ を **1 コミット**にする。
  - **外すと決めたら**：`git restore`〔本人〕で、②③ の変更を戻す。文書〔issue 031、この手順書の記録〕に「**試してやめた**」と書き、設計書 2.3 の「画面の切りかえ」の項目も外す。これらの文書の直しは、別のコミットにする。
- 各段階の流れ：文書 → テスト〔先に失敗を確かめる〕→ コード → 関係するテストだけを流す → 止まって確認。全体のテスト〔`.\mvnw clean test`〕は、段階 ③ の最後に 1 回だけ。

### 5.1 段階 ①：文書

- 設計書 2.3「画面の切りかえ」〔1 項目〕、issue 031、この手順書。要件定義書は変えない。
- **完了の条件**：本人が文書を確かめる。

### 5.2 段階 ②：テスト〔先に失敗を確かめる。コミットしない〕

- 新しいクラス `ViewTransitionStyleSheetTest`〔`controller` パッケージ。ほかの `…StyleSheetTest` と同じ置き場所〕を作る。読み方は、ほかの `…StyleSheetTest` と同じ〔コメントを取って、`Files.readString` で読む〕。
- **追加するテストの案**：「あるべきもの」と「あってはいけないもの」を対にする。

| 番号 | 種類 | 確かめること |
|---|---|---|
| T1 | あるべきもの | `common.html` の head の `<style>` に `@view-transition` があり、中に `navigation: auto` がある |
| T2 | あるべきもの、かつ あってはいけないもの〔1 つにまとめる〕 | **ファイル全体の `@view-transition` の数 ＝ `@media (prefers-reduced-motion: no-preference)` の中の数、かつ 1 つ以上**〔条件の外に、1 つもない〕。**取り出し方**：コメントを取ったあと、`@media (prefers-reduced-motion: no-preference) {` の `{` から、`{` と `}` の入れ子を数えて〔深さが 0 に戻るまで〕、条件の中身を取り出す〔`LayoutStyleSheetTest` の `mediaBlocks` と同じ方法。同じ条件の `@media` が複数あれば、全部集める〕。数は、ファイル全体と、取り出した中身のそれぞれで、`@view-transition` の出てくる回数を数える。文字の検索だけで「条件の近くにある」ことを確かめて済ませない |
| T3 | あるべきもの | `.app-header` の規則に、`view-transition-name: app-header` がある |
| T4 | あってはいけないもの | style.css の `view-transition-name` は、**`app-header` のほかにない**〔すべての `view-transition-name` の値が `app-header`。個数は 1 つ〕 |
| T5 | あってはいけないもの | `templates` に `<meta name="view-transition"` がない。**理由**：切りかえの指定を `common.html` の head の `<style>` の 1 か所〔T1・T2〕にそろえ、2 つ目の指定を作らないため |
| T6 | あってはいけないもの | `static/js` と `templates` に、`startViewTransition`・`pagereveal`・`pageswap` がない〔JavaScript は使わない〕 |
| T7 | あってはいけないもの | style.css に `@view-transition` がない〔同じ指定を 2 か所に置かない〕 |
| T8 | あってはいけないもの | テンプレートの中で、`<style>` は `common.html` の 1 つだけ |
| T9 | あってはいけないもの | その `<style>` の中身は、`@view-transition` の指定だけ〔ほかの見た目の規則を増やさない〕 |

- **2026年11月17日の見直し〔2.6〕**：T1・T2 の読む先は、`common.html` の head の `<style>` の中身に変えた〔入れ子を数えて取り出す方法は同じ〕。T7〜T9 を足した〔上の表〕。
- 外の URL や `@import` が増えていないことは、`FontAssetsTest` が確かめているので、ここでは足さない〔重なるため〕。
- **「あってはいけないもの」のテスト**〔T2 の条件の外の確かめ、T4、T5、T6〕は、書く前から通る。通るだけでは確かめになっていないので、**悪い例を一時的に書いて、失敗することを確かめ、手で戻す**。
  - 悪い例：条件の外の `@view-transition`〔T2〕、`app-header` 以外の `view-transition-name`〔T4〕、`<meta name="view-transition"`〔T5〕、`startViewTransition` など〔T6〕。
  - 一時的に変えたあとは、`clean` を付けて流す〔古いレポートが混ざらないように〕。
- 「あるべきもの」のテスト〔T1、T2 の数、T3〕は、書く前〔実装の前〕に失敗することを確かめる。
- **完了の条件**：「書く前に失敗したテスト」の件数を、報告に書く。

### 5.3 段階 ③：実装〔コミットしない〕

- **変えるもの**：style.css の**末尾**に、コメントつきの 1 つのまとまりを足す。
  - `@media (prefers-reduced-motion: no-preference) { @view-transition { navigation: auto; } }`
  - `.app-header { view-transition-name: app-header; }`〔既存の `.app-header` の規則は変えず、末尾に別の規則として書く〕
  - コメント：設計書 2.3 を指す。
- **変えないもの**：テンプレート、JavaScript、既存の規則、Bootstrap の CSS。
- **完了の条件**：段階 ② のテストが通る。`.\mvnw clean test` が失敗 0。**コミットしない**〔段階 ④へ進む〕。

### 5.4 段階 ④：実機で確かめて、続けるか外すかを決める

- 4 章の表を、本人が確かめる。
- 決めること：**続ける**／**外す**／**足す**〔ヘッダーの古い像・新しい像の動きを消す書き方〕。
  - 続ける：②③ を **1 コミット**にする〔本人〕。
  - 外す：`git restore`〔本人〕で ②③ を戻す。文書に「試してやめた」と記録し、設計書 2.3 の項目も外す〔5 章〕。
  - 足す：書き方を足して、もう一度、実機で確かめる。〔足したあとも、②③ はコミットしていない〕
- **完了の条件**：本人が、結果を表に書いて、決める。

## 6. 決まっていない点

なし。

## 7. 記録

- 2026年11月17日：調べものを報告〔ファイルは変えなかった〕。決まったことが、本人から示された。
- 2026年11月17日：**段階 ① を行った**。issue 031 とこの手順書を作り、設計書 2.3 に「画面の切りかえ」を 1 項目、足した。要件定義書は変えていない。コードとテストは、まだ変えていない。
- 2026年11月17日：本人の指示で、文書を直した〔コードとテストは変えていない〕。5 章を「段階とコミット」に直した〔① だけ先にコミット。②③ はコミットせず段階 ④へ。続けるなら ②③ を 1 コミット、外すなら `git restore`〔本人〕で戻し「試してやめた」と記録〕。決まったこと 3・5 と設計書 2.3 の 2 つ目の項目を直した〔`animation: none` を使わないのは、動きを減らす設定への対応だけ〕。T6〔動きを消す書き方がない〕を削除して T1〜T6 に振り直し、T2 と T3 を 1 つにまとめた〔`@media` の `{ }` の入れ子を数えて取り出す〕。T5〔`<meta>` がない〕は理由を書いて残した。5.2 の「わざと壊して」の説明を直し、4 章の 6 番を削除して振り直した。設計書 2.3 の最後の項目〔外す〕を削除した。
- 2026年11月17日：**段階 ② を行った**〔コミットしない〕。新しい `ViewTransitionStyleSheetTest`〔`controller`。6 件：T1〜T6〕。書いた直後は、T1・T2・T3〔あるべきもの〕が失敗、T4・T5・T6 は通った。一時的に実装を書くと 6 件とも通り、その上に悪い例を 1 つずつ置くと、ねらった 1 件だけが失敗した〔T2：条件の外の `@view-transition`、T4：`app-header` 以外の名前、T5：`common.html` の `<meta>`、T6：`back-reload.js` の `startViewTransition`〕。すべて戻した〔`git diff` に差分なし〕。style.css は変えていない。
- 2026年11月17日：**段階 ③ を行った**〔コミットしない〕。style.css の末尾に、コメントつきの 1 つのまとまりを足した〔`@media (prefers-reduced-motion: no-preference)` の中の `@view-transition { navigation: auto; }` と、`.app-header { view-transition-name: app-header; }`〕。テンプレート・JavaScript・既存の規則は変えていない。`ViewTransitionStyleSheetTest` 6 件が通り、`.\mvnw clean test` は 2,697 件 → 2,703 件〔+6〕、失敗 0。次は段階 ④〔実機〕。
- 2026年11月17日：実機で、取引一覧へ移ったときだけ画面全体が左に動いて見えた〔スクロールバーが原因と推測〕。決まったこと 10 を足し、決まったこと 3 に「scrollbar-gutter は外さない」を足した。2.5〔対応ブラウザと、既存の規則とのぶつかりの確認〕、3 章の 6、4 章の 9 を足した。設計書 2.3 の「本文の幅」のすぐ後に「スクロールバーの場所」を 1 項目、issue 031 の概要に 1 段落を足した。`LayoutStyleSheetTest` に 2 件〔`html` に `scrollbar-gutter: stable` がある／`html`・`body` に `overflow(-y): scroll` がない〕を足し、書く前に前者が失敗することと、悪い例〔`html`、`body` の `overflow-y: scroll`〕で後者が失敗することを確かめて戻した。style.css の `main.container` の前〔View Transitions のまとまりの外〕に `html { scrollbar-gutter: stable; }` を足した。`.\mvnw clean test` は 2,703 件 → 2,705 件〔+2〕、失敗 0。実機での確かめは、まだ〔4 章の 9〕。
- 2026年11月17日：取引一覧へ移るときだけ、切りかえが取りやめになる件〔`InvalidStateError … opt-in disabled`〕を調べ、`common.html` の head に `<style>` を一時的に置いて試すと、取引一覧でも切りかわり、エラーも出なくなった〔実機・Chrome〕。これを正式にした：決まったこと 4・8 を直し、11〔インライン CSS の例外〕を足した。2.6 を足し、3 章の 6 と 4 章の 1・9 に結果を書いた。設計書 2.3「画面の切りかえ」〔書く場所、例外の理由〕と 7.5〔CSP の注意〕、issue 031 の概要を直した。
- 2026年11月17日：上の見直しのテストと実装。`ViewTransitionStyleSheetTest` の T1・T2 を `common.html` の `<style>` を読む形に直し、T7〜T9 を足した〔6 件 → 9 件〕。書いた直後は T7 だけが失敗〔style.css にまだ `@view-transition` があったため〕。悪い例を 1 つずつ置いて、T1・T2・T7・T8・T9 が失敗することを確かめ、戻した〔T1・T2 の悪い例では、中身の形を見る T9 も一緒に失敗した〕。style.css の `@view-transition` を外し、`::view-transition-old(root)`・`::view-transition-new(root)` の時間の規則を足した。`common.html` の `<style>` に、コメント〔設計書 2.3 を指す。例外であること〕を付けた。`.\mvnw clean test` は 2,705 件 → 2,708 件〔+3〕、失敗 0。
- 2026年11月17日：実機の確かめを終えた〔4 章。iPad の Safari は確かめずに終えた〕。効果を少し弱めたいので、切りかえの時間をさらに短くした〔値は style.css〕。
- 2026年11月17日：時間をさらに短くした〔値は style.css〕。
- 2026年11月17日：仕上げ。issue 031 とこの手順書の状態を「完了」にした。5.2 の T7〜T9 を表に移し、T1・T5 の書き方を今の形〔`common.html` の `<style>`〕に合わせた。README のテストの件数を 2,708 件にした。
- 2026年11月17日：`docs/testing.md` の件数を直した〔総数 2,697 件 → 2,708 件、日付を 11 月 17 日に、「画面（Controller）」1046 件 → 1057 件。足したのは `ViewTransitionStyleSheetTest` 9 件と `LayoutStyleSheetTest` 2 件〕。
