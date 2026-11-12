// 予算設定画面の、予算額の合計と、「消える前の確認」と、「保存の確認」（設計書 2.3、issue 019、issue 024）。
// 合計：予算額の欄の値を読んで足し、合計の数字に書く。画面を開いたときと、欄を書き換えるたびに計算し直す。
// 確認：「自動予算」と 1〜3 の切りかえのリンクを押したとき、開いたときから変わった欄があれば、確認の窓を出す。
// 保存の確認（issue 024）：保存のフォームを送るとき（submit。上の「保存」ボタンも、Enter キーも同じ）、確認の窓を出す。
// 文は、サーバーが年・季節を入れた固定の文を、フォームの属性で渡す（ここには書かない）。合計ありの文の {total} の所に、上と同じ計算の合計を入れる。
// 数字でない欄・空の欄があるときは、合計なしの文を使う。「はい」なら送り、「いいえ」なら送らない（入力は残る）。JavaScript が動かないときは、確認なしで送れる。
// このファイルは固定で、利用者の入力をファイルの中に埋め込まない。サーバーとは通信しない。
// 画面に出す文は、HTML に書いてある固定の文。ここでは、合計の数字を書く（textContent）ことと、固定の文の表示・非表示だけ行う。
// 範囲（0〜3,000,000）は、ここでは判断しない。サーバーが、今までどおり入力チェックを行う。
(function () {
    'use strict';

    // 桁が多すぎる値は、数字でない扱いにする（サーバーでも範囲のエラー）。
    var MAX_DIGITS = 9;

    // 全角の数字（０〜９）を、半角の数字に直す。サーバーの StrictIntegerEditor と同じ。ほかの文字は直さない。
    function toHalfWidthDigits(text) {
        return text.replace(/[０-９]/g, function (c) {
            return String.fromCharCode(c.charCodeAt(0) - 0xFF10 + 0x30);
        });
    }

    // 欄の文字を、読む。'empty'（空）、'invalid'（数字でない）、または整数を返す。
    // 前後の空白を取る。半角の数字だけが数字（全角は直す）。マイナス、小数、カンマ、「円」、途中の空白などは数字でない。
    function readField(text) {
        var stripped = toHalfWidthDigits(String(text).trim());
        if (stripped === '') {
            return 'empty';
        }
        if (!/^[0-9]+$/.test(stripped) || stripped.replace(/^0+/, '').length > MAX_DIGITS) {
            return 'invalid';
        }
        return parseInt(stripped, 10);
    }

    // 欄の文字の並びから、合計を出す。空の欄と数字でない欄は、合計に入れない。
    function sumAmounts(texts) {
        var total = 0;
        var hasEmpty = false;
        var hasNotNumber = false;
        texts.forEach(function (text) {
            var value = readField(text);
            if (value === 'empty') {
                hasEmpty = true;
            } else if (value === 'invalid') {
                hasNotNumber = true;
            } else {
                total += value;
            }
        });
        return { total: total, hasEmpty: hasEmpty, hasNotNumber: hasNotNumber };
    }

    // 3 桁ごとにカンマを付ける。
    function formatTotal(total) {
        return String(total).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }

    // 開いたときの文字の並びと、今の文字の並びを、文字のまま比べる（数字として読み直さない）。
    function anyChanged(originals, currents) {
        for (var i = 0; i < originals.length; i++) {
            if (originals[i] !== currents[i]) {
                return true;
            }
        }
        return false;
    }

    // 画面の部品をつなぐ。合計の入れ物がない画面では、何もしない。
    function setUp() {
        var totalElement = document.querySelector('[data-budget-total]');
        if (!totalElement) {
            return;
        }
        var fields = Array.prototype.slice.call(document.querySelectorAll('[data-budget-amount]'));
        var notNumberNote = document.querySelector('[data-budget-not-number]');
        var emptyNote = document.querySelector('[data-budget-empty]');

        function currentTexts() {
            return fields.map(function (field) {
                return field.value;
            });
        }

        function update() {
            var result = sumAmounts(currentTexts());
            totalElement.textContent = formatTotal(result.total);
            if (notNumberNote) {
                notNumberNote.hidden = !result.hasNotNumber;
            }
            if (emptyNote) {
                emptyNote.hidden = !result.hasEmpty;
            }
        }

        fields.forEach(function (field) {
            field.addEventListener('input', update);
        });
        update();

        // 開いたときの値を覚えておく（画面の欄から読む）。
        var originals = currentTexts();
        var links = document.querySelectorAll('[data-budget-confirm]');
        Array.prototype.forEach.call(links, function (link) {
            link.addEventListener('click', function (event) {
                if (anyChanged(originals, currentTexts()) && !window.confirm(link.getAttribute('data-budget-confirm'))) {
                    event.preventDefault();
                }
            });
        });

        // 保存の確認。画面を開いたときから変わっていなくても、毎回出す。
        var saveForm = document.querySelector('form[data-budget-save-confirm]');
        if (saveForm) {
            saveForm.addEventListener('submit', function (event) {
                var result = sumAmounts(currentTexts());
                var message;
                if (result.hasEmpty || result.hasNotNumber) {
                    message = saveForm.getAttribute('data-budget-save-confirm');
                } else {
                    var withTotal = saveForm.getAttribute('data-budget-save-confirm-total');
                    message = withTotal === null ? null : withTotal.replace('{total}', formatTotal(result.total));
                }
                if (message && !window.confirm(message)) {
                    event.preventDefault();
                }
            });
        }
    }

    // Console で計算を確かめるために、計算の関数だけを出しておく（画面の部品には触れない）。
    window.BudgetTotal = { sumAmounts: sumAmounts, formatTotal: formatTotal, anyChanged: anyChanged };

    setUp();
})();
