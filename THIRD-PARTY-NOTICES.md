# サードパーティのソフトウェアの表示（THIRD-PARTY NOTICES）

このアプリは、次の第三者のソフトウェアを含んでいます。Bootstrap はそのまま（改変せずに）含んでいます。Zen Kaku Gothic New は、ファイルの形式を TTF から woff2 に変えたものです（文字と字形は同じ。下の「改変」を見る）。

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

## Zen Kaku Gothic New（フォント）

- 名前・版：Zen Kaku Gothic New、Version 1.002（Regular〔太さ 400〕と Bold〔太さ 700〕の 2 つ）
- 含んでいるファイル：`src/main/resources/static/fonts/ZenKakuGothicNew-Regular.woff2`、`src/main/resources/static/fonts/ZenKakuGothicNew-Bold.woff2`、ライセンス全文 `src/main/resources/static/fonts/OFL.txt`
- 出どころ：https://github.com/googlefonts/zen-kakugothic の commit `2705757e17e42954f3acbdf921ac0ae24d1270cd`（`fonts/ttf/ZenKakuGothicNew-Regular.ttf`、`fonts/ttf/ZenKakuGothicNew-Bold.ttf`、`OFL.txt`）。Google Fonts（https://github.com/google/fonts の `ofl/zenkakugothicnew/`）も同じファイルを配っている（`OFL.txt` は同じ内容。TTF は大きさが同じ）。デザイン：Yoshimichi Ohira
- ライセンス：SIL Open Font License, Version 1.1（下に全文。`OFL.txt` と同じ。フォントと同じ場所に置いてある）。Reserved Font Name（改変版に使えない名前）の指定は、ない
- 著作権表示：`OFL.txt` の表記は「Copyright 2022 The Zen Kaku Gothic Project Authors (https://github.com/googlefonts/zen-kakugothic)」。**フォントのファイルの中の表記は「Copyright 2022 The Zen Project Authors (…)」で、少し違う**。どちらも配布元のままで、ここでは `OFL.txt` の表記を使う
- **改変**：形式の変換だけ。TTF を woff2 に変えた（文字の数、字形、フォントの中の名前・著作権・ライセンスの表記は、変えていない）。OFL では「改変版」にあたるが、Reserved Font Name がないので、名前は変えていない。フォント単体では販売しない
- **変換の方法**（同じ道具・同じ版なら、同じファイルができる）：
  - 道具：Python 3.10.6、fonttools 4.65.0、brotli 1.2.1（`python -m venv venv`、`venv/Scripts/pip install fonttools==4.65.0 brotli==1.2.1`）
  - コマンド（TTF を置いたフォルダで、Regular と Bold のそれぞれに）：`python -m fontTools.ttLib.woff2 compress -o ZenKakuGothicNew-Regular.woff2 ZenKakuGothicNew-Regular.ttf`（Bold も同じ形）
  - 確認：TTF の SHA-256 が下の表と一致すること。2 回変換して、できた woff2 の SHA-256 が同じになること。変換後のファイルの文字の数・字形・名前の表記が、もとのとおりであること

  | ファイル | 大きさ（バイト） | SHA-256 |
  |---|---|---|
  | `ZenKakuGothicNew-Regular.ttf`（変換前） | 2,360,248 | `b840cd07a67d89cacca44249ae49aa99ee7640eb5ce623be8d8983d6aabac801` |
  | `ZenKakuGothicNew-Regular.woff2`（変換後） | 1,052,404 | `82947c8de422edf4804a2976163f03cfb8a283fb3e1262c89c1bebe1364d1c2b` |
  | `ZenKakuGothicNew-Bold.ttf`（変換前） | 2,314,424 | `0081cedabc4921982fcd061f845a005664ac7fb642af2dd34b4007bc63ccd235` |
  | `ZenKakuGothicNew-Bold.woff2`（変換後） | 1,083,376 | `751171e0c0e30d0b51c2f51ddc54362f082fcb49d6f0d144b9ae119a4ec3a978` |
  | `OFL.txt`（配布元のまま） | 4,401 | `0fac78a235c98d640cb06332eb5362c211d86fa03c011df438c35005d22ad2c7` |
- アプリが実行時に外部のサーバー（CDN）から読むことはない。

### ライセンス全文（SIL Open Font License, Version 1.1）

Copyright 2022 The Zen Kaku Gothic Project Authors (https://github.com/googlefonts/zen-kakugothic)

```
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded, 
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```
