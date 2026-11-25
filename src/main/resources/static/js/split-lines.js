// 取引の登録・編集画面の、明細の行（設計書 2.3、issue 032）。
// 1. 空の行を隠す：最後の「空でない行」より後ろの、空の行を隠す（1 行目は隠さない）。「行を足す」で、隠れている次の行を見せる。
// 2. 明細の合計：行の金額を読んで足し、合計の数字に書く。画面を開いたときと、金額を書き換えるたびに計算し直す。
// 行は、はじめから HTML にある（ここでは作らない・写さない。要件定義書 7 章「画面を JavaScript で組み立てない」）。
// 隠した行も、送信はされる（空なので、サーバーが無視する）。JavaScript が動かないときは、全部の行が見え、合計は出ない。
// このファイルは固定で、利用者の入力をファイルの中に埋め込まない。サーバーとは通信しない。文は HTML に書いてある固定の文。
// 合計は、見せるだけ。合計の入力も、サーバーでの確かめもしない（レシートと合っているかは、利用者が見て確かめる）。
(function () {
    'use strict';

    // 桁が多すぎる値は、数字でない扱いにする（calculator.js、budget-total.js と同じ）。
    var MAX_DIGITS = 9;

    // 全角の数字（０〜９）を、半角の数字に直す。サーバーの StrictIntegerEditor と同じ。ほかの文字は直さない。
    function toHalfWidthDigits(text) {
        return text.replace(/[０-９]/g, function (c) {
            return String.fromCharCode(c.charCodeAt(0) - 0xFF10 + 0x30);
        });
    }

    // 欄の文字を読む。'empty'（空）、'invalid'（数字でない）、または整数を返す。
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
        var hasNotNumber = false;
        texts.forEach(function (text) {
            var value = readField(text);
            if (value === 'invalid') {
                hasNotNumber = true;
            } else if (value !== 'empty') {
                total += value;
            }
        });
        return { total: total, hasNotNumber: hasNotNumber };
    }

    // 3 桁ごとにカンマを付ける。
    function formatTotal(total) {
        return String(total).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }

    // 行が空か：入力欄（科目・金額・摘要）が全部空。空の行には、サーバーはエラーを付けない（入力エラーの行は、値が入っている）。
    function isEmptyRow(row) {
        var fields = row.querySelectorAll('input:not([type="button"]), select');
        for (var i = 0; i < fields.length; i++) {
            if (fields[i].value.trim() !== '') {
                return false;
            }
        }
        return true;
    }

    function setUp() {
        var rows = Array.prototype.slice.call(document.querySelectorAll('[data-split-line]'));
        if (rows.length === 0) {
            return;
        }
        var addButton = document.querySelector('[data-split-add]');
        var totalBox = document.querySelector('[data-split-total-box]');
        var totalElement = document.querySelector('[data-split-total]');
        var notNumberNote = document.querySelector('[data-split-not-number]');

        // 1. 空の行を隠す（「行を足す」があるときだけ。編集の画面は 1 行なので、何もしない）
        if (addButton) {
            var lastFilled = 0;
            rows.forEach(function (row, index) {
                if (!isEmptyRow(row)) {
                    lastFilled = index;
                }
            });
            rows.forEach(function (row, index) {
                row.hidden = index > lastFilled;
            });
            var updateAddButton = function () {
                addButton.hidden = rows.every(function (row) {
                    return !row.hidden;
                });
            };
            addButton.addEventListener('click', function () {
                var next = rows.find(function (row) {
                    return row.hidden;
                });
                if (next) {
                    next.hidden = false;
                    var first = next.querySelector('select, input');
                    if (first) {
                        first.focus();
                    }
                }
                updateAddButton();
            });

            // 行を消す（明細 2〜5 だけにある）：確認なしで、その行の科目・金額・摘要を空にして、行を隠す。
            // 欄に input のできごとを起こして、ほかの処理に「空になった」ことを知らせる
            // （calculator-keys.js が、その欄の電卓の「税込」を押せる状態に戻し、下の合計が計算し直す）。
            rows.forEach(function (row) {
                var removeButton = row.querySelector('[data-split-remove]');
                if (!removeButton) {
                    return;
                }
                removeButton.addEventListener('click', function () {
                    row.querySelectorAll('select').forEach(function (select) {
                        select.value = '';
                    });
                    row.querySelectorAll('input:not([type="button"])').forEach(function (input) {
                        input.value = '';
                        input.dispatchEvent(new Event('input'));
                    });
                    row.hidden = true;
                    updateAddButton();
                    // 押したボタンは隠れるので、カーソルを「行を足す」に移す
                    addButton.focus();
                });
                removeButton.hidden = false; // JavaScript が動くときだけ見せる
            });
            updateAddButton();
        }

        // 2. 明細の合計
        if (totalBox && totalElement) {
            var amountFields = rows.map(function (row) {
                return row.querySelector('[data-split-amount]');
            }).filter(function (field) {
                return field !== null;
            });
            var update = function () {
                var result = sumAmounts(amountFields.map(function (field) {
                    return field.value;
                }));
                totalElement.textContent = formatTotal(result.total);
                if (notNumberNote) {
                    notNumberNote.hidden = !result.hasNotNumber;
                }
            };
            // 欄の input のできごとで計算し直す。電卓のボタン（calculator-keys.js）と式の計算（calculator.js）も、欄を書いたあとに
            // input のできごとを起こして知らせるので、読む順に頼らない（issue 035 の段階 3 で、ボタンを押したときの計算し直しをやめた）。
            amountFields.forEach(function (field) {
                field.addEventListener('input', update);
            });
            update();
            totalBox.hidden = false;
        }
    }

    // Console で計算を確かめるために、計算の関数だけを出しておく（画面の部品には触れない）。
    window.SplitLines = { sumAmounts: sumAmounts, formatTotal: formatTotal };

    setUp();
})();
