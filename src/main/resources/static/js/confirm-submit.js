// 取引一覧の「削除」の確認（設計書 2.2、issue 025 の段階 3）。
// 「確認の文」を持つフォーム（属性 data-confirm）を送る（submit）とき、確認の窓を出す。「いいえ」なら送らない。
// 文は、サーバーが、日付・科目名・金額を入れた固定の形で、HTML の属性に入れて渡す（HTML として無害化されている）。ここでは、属性から読むだけ。
// 科目名は利用者の入力なので、このファイルの中にも、onsubmit の文字列にも、埋め込まない。画面には何も書かない。サーバーとは通信しない。
// JavaScript が動かないときは、確認なしで、今までどおり削除される（POST と CSRF のトークンは、そのまま効く）。
(function () {
    'use strict';

    function setUp() {
        var forms = document.querySelectorAll('form[data-confirm]');
        Array.prototype.forEach.call(forms, function (form) {
            form.addEventListener('submit', function (event) {
                var message = form.getAttribute('data-confirm');
                if (message && !window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });
    }

    setUp();
})();
