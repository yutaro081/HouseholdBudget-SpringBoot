// ブラウザの「戻る」で、覚えていた画面（バック・フォワード キャッシュ）が出たときは、同じ URL を GET で開き直す。
// ログアウトのあとに、ログアウト前の画面が残らないようにするため（設計書 7.10、Phase 8 の Q8-25・Q8-26）。
// location.reload() ではなく replace を使うのは、入力エラーの画面（POST の応答）で「フォームを再送信しますか」と聞かれないため。
// 利用者の入力は扱わない。
window.addEventListener('pageshow', function (event) {
    if (event.persisted) {
        window.location.replace(window.location.href);
    }
});
