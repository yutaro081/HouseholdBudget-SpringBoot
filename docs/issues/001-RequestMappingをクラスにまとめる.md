# 001：Controller の URL を、クラスの @RequestMapping にまとめるか

## 概要

VS Code の Spring Boot の拡張機能が、`CategoryController`・`VendorController`・`PaymentMethodController` について、「すべての URL が共通の親のパス（`/categories` など）で始まる。クラスの `@RequestMapping` にまとめられる」とお知らせを出している。動きは変わらない（改善の案）。

## 要望の内容

- お知らせの内容：3つの Controller は、メソッドの URL がどれも `/categories`、`/vendors`、`/payment-methods` で始まる。この共通の部分を、クラスに付ける `@RequestMapping("/categories")` などに移し、各メソッドには残りの部分（`/{id}/edit` など）だけを書ける。
- 動きは変わらない。直さなくても、アプリは正しく動く。
- **今は直していない。** 理由：今の書き方は、各メソッドの URL の全体（例：`/categories/{id}/edit`）がそのまま見えるので、設計書 章2（2.2 の URL の一覧）と見比べやすい。まとめると、クラスの先頭とメソッドを合わせて読まないと、URL の全体が分からなくなる。
- 取引の Controller（`TransactionController`、Phase 5）も、同じお知らせが出る可能性がある。

## 決めること

- Phase 8（仕上げ）の「コードの整理」のときに、直すか決める。
- 判断の材料：
  - 直す場合：Controller 全部で書き方をそろえる（科目・取引先・支払方法・取引。作っていれば予算・予実比較も）。URL のテスト（`…ControllerTest`）は変えなくてよいはず。設計書 4.4.1 の説明は、URL の見え方が変わるなら見直す。
  - 直さない場合：理由（設計書と見比べやすさを優先する）を、コードの近くか設計書に1行残す。

## 対応状況

対応済み（Phase 8 の 4.2。Q8-1 の A）

- 決定（Phase 8 の Q8-1）：`CategoryController`・`VendorController`・`PaymentMethodController`・`TransactionController`・`BudgetController`・`ComparisonController` の6つすべてを、クラスの `@RequestMapping` にまとめる（`AuthController`・`HomeController` は親のパスが違うので対象外）。
- 理由：Spring でふつうの書き方で、コードを読む人にとって読みやすい。URL が変わらないことは、既存の Controller のテスト（`…ControllerTest`）で確かめられる。
- 以前の「今は直していない」理由（メソッドごとに URL の全体が見え、設計書 2.2 の一覧と見比べやすい）より、書き方がそろうことを優先した。
- 直した内容：6つの Controller に、クラスの `@RequestMapping("/categories")` などを足し、メソッドの注釈 25 か所から、共通の頭の部分を取った（頭の部分だけのメソッドは `@GetMapping` / `@PostMapping`）。URL は1つも変えていない。テンプレート・テスト・`SecurityConfig` は変えていない。
- 文書：設計書 4.4.1 に、URL の読み方（クラスの `@RequestMapping` ＋ メソッドの注釈を合わせて読む）を足した。実行計画書 ルール8 にも書いた。
- 確認：`.\mvnw test` が 1205 件すべて成功（`…ControllerTest` はそのまま通った）。関係するコミットは、コミットのあとに書く。
