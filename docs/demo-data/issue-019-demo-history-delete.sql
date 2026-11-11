DELETE t FROM transactions t JOIN users u ON u.id = t.user_id
WHERE u.username = 'demo'
  AND t.transaction_date BETWEEN '2023-09-01' AND '2026-08-31'
  AND NOT (t.transaction_date = '2026-08-20' AND t.amount = 12000);
