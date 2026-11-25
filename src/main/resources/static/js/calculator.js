// 取引の登録・編集画面の、金額の欄の式（設計書 2.3、issue 035 の段階 2）。明細の行ごとに当てはめる。
// 欄に「1280+398-300」のような式（数字と ＋ − だけ）を打つと、Enter・欄から離れたとき・フォームを送るときに計算して、結果の整数を欄に書く。
// 式は、文字列をプログラムとして実行する書き方を使わず、数字と記号に分けて、左から順に足し引きする。
// 範囲（1〜1000000）は、ここでは判断しない。結果がマイナスや上限超えでも、そのまま書く。サーバーが、今までどおり入力チェックを行う。
// 計算できない式は、欄を変えず、HTML に書いてある固定の文を見せる（文はここで作らない。見せる・隠すだけ）。サーバーとは通信しない。
// 結果を書いたら input のできごとを起こす。明細の合計（split-lines.js）と、電卓の税込のボタンの押せる・押せない（calculator-keys.js）が、
// そのできごとで新しい値に合わせて動く。電卓のボタン（calculator-keys.js）は、このファイルが出す window.Calculator を使うので、このファイルの後に読む。
(function () {
    'use strict';

    // 1 つの数字の桁の上限（先頭のゼロを除く。split-lines.js、budget-total.js と同じ）。
    var MAX_DIGITS = 9;

    // 全角の数字と ＋ を半角に直し、引く記号（全角の －、数学の −、日本語入力のまま「-」を打つと出る ー）を - にそろえ、
    // 空白（半角・全角）を取り除く。ほかの文字は、そのまま残す（形の確かめで、計算できない式になる）。
    function normalize(text) {
        return String(text)
            .replace(/[０-９]/g, function (c) {
                return String.fromCharCode(c.charCodeAt(0) - 0xFF10 + 0x30);
            })
            .replace(/＋/g, '+')
            .replace(/[－−ー]/g, '-')
            .replace(/\s/g, '');
    }

    // 1 つの数字を読む。先頭のゼロを除いて、桁が多すぎるときは null。
    function readNumber(digits) {
        if (digits.replace(/^0+/, '').length > MAX_DIGITS) {
            return null;
        }
        return parseInt(digits, 10);
    }

    // 式を計算する。計算できれば整数、できなければ null（空も null）。
    // 形は「数字 記号 数字 記号 数字 …」だけ（記号は + と - の 1 つずつ。記号で始まらない・終わらない）。左から順に足し引きする。
    function evaluate(text) {
        var s = normalize(text);
        if (!/^[0-9]+([+-][0-9]+)*$/.test(s)) {
            return null;
        }
        var parts = s.split(/([+-])/); // 例：['1280', '+', '398', '-', '300']
        var total = readNumber(parts[0]);
        for (var i = 1; i < parts.length && total !== null; i += 2) {
            var value = readNumber(parts[i + 1]);
            total = (value === null) ? null : (parts[i] === '+' ? total + value : total - value);
        }
        return total;
    }

    // 税込にする（issue 035 の段階 3。電卓の「税込」が使う）。式を先に計算し、その数を税抜として percent / 100 倍にする（percent は 110 か 108）。
    // 整数だけで計算し、端数は切り捨てる（前の金額ボタンと同じ）。空・計算できない式・0 以下は null（税込にしない）。
    function withTax(text, percent) {
        var amount = evaluate(text);
        if (amount === null || amount <= 0) {
            return null;
        }
        return Math.floor(amount * percent / 100);
    }

    // 欄の値が式か。空と、整数だけの値（1280、１２８０、-200）は、式と見なさない（何もせず、そのまま送る。サーバーが読む）。
    function isFormula(text) {
        var s = normalize(text);
        return s !== '' && !/^-?[0-9]+$/.test(s);
    }

    // 1 行分：計算できないときの文（data-calc-error）を、その文が指す金額の欄（data-calc-target）につなぐ。
    // 返すのは、その欄の式を計算する関数（フォームを送るときに、全部の行で呼ぶ）。
    function setUpLine(errorText) {
        var field = document.getElementById(errorText.getAttribute('data-calc-target'));
        if (!field) {
            return null;
        }

        // 式なら計算して、結果を欄に書く。計算できない式なら、欄を変えずに文を見せる。式でなければ何もしない。
        function compute() {
            if (!isFormula(field.value)) {
                return;
            }
            var result = evaluate(field.value);
            if (result === null) {
                errorText.hidden = false;
                return;
            }
            field.value = String(result);
            errorText.hidden = true;
            field.dispatchEvent(new Event('input'));
        }

        // Enter：欄に式があるときは、計算だけして、フォームを送らない（数字だけ・空のときは、今までどおり送る）。
        // 日本語入力で文字を確定する Enter では、何もしない（isComposing。ブラウザによっては keyCode 229 で知らせる）。
        field.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229) {
                return;
            }
            if (isFormula(field.value)) {
                event.preventDefault();
                compute();
            }
        });
        // 欄から離れたとき（Tab、ほかの所を押したとき）にも計算する。
        // 電卓のボタンは、押してもカーソルを欄から動かさないので、ここは通らない（電卓の ＋ − 税込は、自分で先に計算する）。
        field.addEventListener('blur', compute);
        // 欄を書き換えたら、計算できないときの文を隠す。
        field.addEventListener('input', function () {
            errorText.hidden = true;
        });
        return compute;
    }

    // 画面の全部の行をつなぐ。文の入れ物がない画面では、何もしない。
    function setUp() {
        var errorTexts = document.querySelectorAll('[data-calc-error]');
        if (errorTexts.length === 0) {
            return;
        }
        var computes = [];
        errorTexts.forEach(function (errorText) {
            var compute = setUpLine(errorText);
            if (compute) {
                computes.push(compute);
            }
        });
        // フォームを送るとき（「保存」も、数字だけの欄での Enter も）、送る前に、全部の行の式を計算する。
        // 計算できない式は、そのまま送る（サーバーが「金額は整数で入力してください」のエラーを出す）。
        var form = errorTexts[0].closest('form');
        if (form) {
            form.addEventListener('submit', function () {
                computes.forEach(function (compute) {
                    compute();
                });
            });
        }
    }

    // 計算の関数だけを出しておく（画面の部品には触れない）。Console で計算を確かめるため（issue 035 の手順書 5 章）と、
    // 電卓のボタン（calculator-keys.js。このファイルの後に読む）が、同じ計算を使うため。
    window.Calculator = { evaluate: evaluate, withTax: withTax };

    setUp();
})();
