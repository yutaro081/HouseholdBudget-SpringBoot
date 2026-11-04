// 取引の登録・編集画面の、金額の入力を助けるボタン（設計書 2.3、issue 009）。
// 足すボタン（+1・+10・+100・+1000）と、税込のボタン（10%・8%）と、クリアのボタン（欄を空にする）。
// このファイルは固定で、利用者の入力をファイルの中に埋め込まない。欄の値を読んで、書き戻すだけ。
// 画面に出す文は、HTML に書いてある固定の文。ここでは、表示と非表示、ボタンの押せる・押せないを切り替えるだけ。
// 範囲（1〜1000000）は、ここでは判断しない。サーバーが、今までどおり入力チェックを行う。
(function () {
    'use strict';

    // 桁が多すぎる値は、何もしない（計算が正しくなくなる前に止める）。
    var MAX_DIGITS = 9;

    // 全角の数字（０〜９）を、半角の数字に直す。サーバーの StrictIntegerEditor と同じ。ほかの文字は直さない。
    function toHalfWidthDigits(text) {
        return text.replace(/[０-９]/g, function (c) {
            return String.fromCharCode(c.charCodeAt(0) - 0xFF10 + 0x30);
        });
    }

    // 欄の文字を、整数に読む。読めないときは null。
    // 前後の空白を取る。空は 0。半角の数字だけ（全角は直す）。マイナス、小数、カンマ、「円」などは読めない。
    function readAmount(text) {
        var stripped = toHalfWidthDigits(String(text).trim());
        if (stripped === '') {
            return 0;
        }
        if (!/^[0-9]+$/.test(stripped) || stripped.replace(/^0+/, '').length > MAX_DIGITS) {
            return null;
        }
        return parseInt(stripped, 10);
    }

    // 足す。変えないときは null。
    function addAmount(text, step) {
        var amount = readAmount(text);
        if (amount === null) {
            return null;
        }
        return String(amount + step);
    }

    // 税込にする（欄の数を税抜として、percent / 100 倍にする。percent は 110 または 108）。
    // 整数だけで計算し、端数は切り捨てる。空と 0 は、変えないので null。
    function withTax(text, percent) {
        var amount = readAmount(text);
        if (amount === null || amount === 0) {
            return null;
        }
        return String(Math.floor(amount * percent / 100));
    }

    // 画面の部品をつなぐ。ボタンの入れ物がない画面では、何もしない。
    function setUp() {
        var box = document.querySelector('[data-amount-buttons]');
        if (!box) {
            return;
        }
        var field = document.getElementById(box.getAttribute('data-amount-target'));
        var note = box.querySelector('[data-amount-tax-note]');
        var addButtons = box.querySelectorAll('[data-amount-add]');
        var taxButtons = box.querySelectorAll('[data-amount-tax]');
        var clearButtons = box.querySelectorAll('[data-amount-clear]');
        if (!field) {
            return;
        }

        // 税込のボタンを、押せなくする・押せるようにする。押せないときは、理由の文を出す。
        function setTaxLocked(locked) {
            taxButtons.forEach(function (button) {
                button.disabled = locked;
            });
            if (note) {
                note.hidden = !locked;
            }
        }

        addButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                var result = addAmount(field.value, parseInt(button.getAttribute('data-amount-add'), 10));
                if (result !== null) {
                    field.value = result; // 足すボタンでは、税込のボタンの押せる・押せないを変えない
                }
            });
        });

        taxButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                var result = withTax(field.value, parseInt(button.getAttribute('data-amount-tax'), 10));
                if (result !== null) {
                    field.value = result;
                    setTaxLocked(true); // 税込みの金額に、もう一度税をかけない
                }
            });
        });

        // クリア：欄を空にする（0 ではなく空）。空にしたので、税込のボタンを押せるようにする（編集画面でも）。
        // ボタンが value を書き換えても input イベントは起きないので、ここで直接、押せるようにする。
        // 欄にカーソルを戻すのは、クリアのときだけ（クリアのあとは、すぐ数字を打つことが多い）。足すボタンでは戻さない。
        clearButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                field.value = '';
                setTaxLocked(false);
                field.focus();
            });
        });

        // 手で書き換えたとき（空にしたときを含む）に、税込のボタンを押せるようにする。
        // 足すボタン・税込のボタンが value を書き換えても、input イベントは起きないので、ここには来ない。
        field.addEventListener('input', function () {
            setTaxLocked(false);
        });

        // 編集画面は、欄の金額がすでに税込みなので、最初は押せない。
        setTaxLocked(box.getAttribute('data-tax-locked') === 'true');
        box.hidden = false; // JavaScript が動くときだけ、ボタンを見せる
    }

    // Console で計算を確かめるために、計算の 2 つの関数だけを出しておく（画面の部品には触れない）。
    window.AmountButtons = { addAmount: addAmount, withTax: withTax };

    setUp();
})();
