# サードパーティのソフトウェアの表示（THIRD-PARTY NOTICES）

このアプリは、次の第三者のソフトウェアを、そのまま（改変せずに）含んでいます。

## Bootstrap

- 名前・版：Bootstrap v5.3.8（CSS のみ。JavaScript は含まない）
- 含んでいるファイル：`src/main/resources/static/css/bootstrap.min.css`
- 出どころ：https://getbootstrap.com/ 、https://github.com/twbs/bootstrap （npm パッケージ `bootstrap@5.3.8` の `dist/css/bootstrap.min.css`）
- ライセンス：MIT License（下に全文）
- 確認：取得したファイルは、jsDelivr が公開している SHA-256 と、npm が公開している配布物の `integrity`（SHA-512）の両方と一致することを確かめて置いた（Phase 8、手順書 4.12）。
  - SHA-256（base64）：`2FMn2Zx6PuH5tdBQDRNwrOo60ts5wWPC9R8jK67b3t4=`（232,111 バイト）
- アプリが実行時に外部のサーバー（CDN）から読むことはない。

### ライセンス全文

```
The MIT License (MIT)

Copyright (c) 2011-2025 The Bootstrap Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

```
