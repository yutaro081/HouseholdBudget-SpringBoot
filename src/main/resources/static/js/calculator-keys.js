// 取引の登録・編集画面の、共有の電卓のボタン（設計書 2.3、issue 035 の段階 3。issue 009 の amount-buttons.js を置きかえた）。
// ボタン（数字・00・000・＋・−・←・C・税込 10%・8%）と文は、HTML にはじめからある。ここでは作らない。見せる・隠すと、押せる・押せないだけ。
// 入れる先は、最後にカーソルを置いた金額の欄（最初は明細 1。その行が隠れたら〔行を消す〕明細 1 に戻す）。
// 電卓のボタンは、押してもカーソルを欄から動かさない（押す瞬間の決まった動きを止める）。そのため、欄から離れたときの計算（calculator.js）は起きない。
// ＋ − 税込は、自分で先に欄の式を計算してから効く。計算は calculator.js が出す window.Calculator を使う（このファイルは calculator.js の後に読む）。
// 税込のボタンの押せる・押せないは、欄ごとに持つ（税込みの金額に、もう一度税を掛けないため）。
// 範囲（1〜1000000）は判断しない。サーバーが、今までどおり入力チェックを行う。利用者の入力をファイルの中に埋め込まない。通信しない。
(function () {
    'use strict';

    var calc = document.querySelector('[data-calc]');
    var Calculator = window.Calculator;
    if (!calc || !Calculator) {
        return; // 電卓がない画面、または calculator.js が読めていないときは、何もしない（電卓は hidden のまま）
    }
    var fields = Array.prototype.slice.call(document.querySelectorAll('[data-split-amount]'));
    if (fields.length === 0) {
        return;
    }
    var taxButtons = calc.querySelectorAll('[data-calc-tax]');
    var taxNote = calc.querySelector('[data-calc-tax-note]');
    var errorTexts = document.querySelectorAll('[data-calc-error]');
    // 画面を開いたとき、欄に値が入っていれば、その金額が税込みかもしれないので、最初は押せない（空なら押せる）。
    // 編集画面（保存した金額）と、入力エラーで表示し直した画面（入力した金額）の、両方がこれで決まる。
    var taxLocked = fields.map(function (field) {
        return field.value.trim() !== '';
    });
    var current = fields[0];
    var writing = false; // 電卓が自分で input のできごとを起こしている間だけ true

    // 入れる先の欄。その行が隠れていたら（行を消す）、明細 1 に戻す。
    function target() {
        if (current.closest('[hidden]')) {
            current = fields[0];
        }
        return current;
    }

    // 入れる先の欄の状態を、税込の 2 つのボタンと、押せないときの文に出す。
    function showTaxState() {
        var locked = taxLocked[fields.indexOf(target())];
        taxButtons.forEach(function (button) {
            button.disabled = locked;
        });
        if (taxNote) {
            taxNote.hidden = !locked;
        }
    }

    // 欄に値を書き、input のできごとを起こして、明細の合計（split-lines.js）と、計算できないときの文（calculator.js）に知らせる。
    // 自分で起こしたできごとは、手で書き換えたことにしない（writing で見分ける）。空になったときだけ、税込を押せるようにする。
    function write(field, value) {
        field.value = value;
        writing = true;
        field.dispatchEvent(new Event('input'));
        writing = false;
        if (value === '') {
            taxLocked[fields.indexOf(field)] = false;
        }
    }

    // その欄の、計算できないときの文（HTML の固定の文）を見せる。
    function showError(field) {
        errorTexts.forEach(function (errorText) {
            if (errorText.getAttribute('data-calc-target') === field.id) {
                errorText.hidden = false;
            }
        });
    }

    // 欄の式を先に計算して、結果を書く。計算できたら true。計算できない式なら、文を見せて false。
    function computeFirst(field) {
        var result = Calculator.evaluate(field.value);
        if (result === null) {
            showError(field);
            return false;
        }
        write(field, String(result));
        return true;
    }

    // ボタン 1 つを押したとき。
    function press(button) {
        var field = target();
        var index = fields.indexOf(field);
        if (button.hasAttribute('data-calc-key')) {
            var key = button.getAttribute('data-calc-key');
            // ＋ −：欄が空なら何もしない。先に式を計算してから、最後に付け足す。数字・00・000：最後に付け足す。
            if ((key === '+' || key === '-') && (field.value.trim() === '' || !computeFirst(field))) {
                return;
            }
            write(field, field.value + key); // 書き足しても、税込を押せない状態は変えない
        } else if (button.hasAttribute('data-calc-back')) {
            write(field, field.value.slice(0, -1)); // 最後の 1 文字を消す
        } else if (button.hasAttribute('data-calc-clear')) {
            write(field, ''); // 0 ではなく空
        } else if (button.hasAttribute('data-calc-tax') && !taxLocked[index]) {
            // 税込：式を先に計算して、税抜として税込にする。空・0 以下は何もしない。計算できない式なら、文を見せる。
            var result = Calculator.withTax(field.value, parseInt(button.getAttribute('data-calc-tax'), 10));
            if (result === null) {
                if (field.value.trim() !== '' && Calculator.evaluate(field.value) === null) {
                    showError(field);
                }
                return;
            }
            write(field, String(result));
            taxLocked[index] = true; // 税込みの金額に、もう一度税をかけない
        }
        showTaxState();
        current.focus();
    }

    // 押す瞬間の決まった動き（カーソルがボタンに移る）を止める。カーソルは欄に残り、今の枠の色で入れる先が分かる。
    calc.querySelectorAll('[data-calc-key], [data-calc-back], [data-calc-clear], [data-calc-tax]').forEach(function (button) {
        button.addEventListener('mousedown', function (event) {
            event.preventDefault();
        });
        button.addEventListener('click', function () {
            press(button);
        });
    });

    fields.forEach(function (field, index) {
        // カーソルを置いた金額の欄が、入れる先になる。
        field.addEventListener('focus', function () {
            current = field;
            showTaxState();
        });
        // 手で書き換えたとき（空にしたとき、「行を消す」〔split-lines.js〕、式の計算〔calculator.js〕を含む）は、税込を押せるようにする。
        // 電卓が自分で書いたとき（writing）は、手で書き換えたことにしない。
        field.addEventListener('input', function () {
            if (writing) {
                return;
            }
            taxLocked[index] = false;
            showTaxState();
        });
    });

    showTaxState();
    calc.hidden = false; // JavaScript が動くときだけ、電卓を見せる
})();
