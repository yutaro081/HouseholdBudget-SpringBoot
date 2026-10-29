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

未対応（Phase 8 で決める）
