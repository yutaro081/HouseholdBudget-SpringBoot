-- issue 019：demo の過去の取引を入れたあとの確かめ（SELECT だけ。何も書き換えない）。
-- 期待する数字は、docs/plans/issue-019-auto-budget.md の 4 章。

-- 1. demo の取引の全件数。期待：99
SELECT COUNT(*) AS n
FROM transactions t JOIN users u ON u.id = t.user_id
WHERE u.username = 'demo';

-- 2. 季節ごとの件数と合計。期待：2023 秋〜2026 夏の 12 季節は 7 件ずつ（2026 夏は新しい 6 件＋今の 1 件）、2026 秋は 15 件・107800 のまま
SELECT
  CASE WHEN MONTH(t.transaction_date) = 12 THEN YEAR(t.transaction_date)
       WHEN MONTH(t.transaction_date) <= 2 THEN YEAR(t.transaction_date) - 1
       ELSE YEAR(t.transaction_date) END AS season_year,
  CASE WHEN MONTH(t.transaction_date) IN (12, 1, 2) THEN 'WINTER'
       WHEN MONTH(t.transaction_date) <= 5 THEN 'SPRING'
       WHEN MONTH(t.transaction_date) <= 8 THEN 'SUMMER'
       ELSE 'AUTUMN' END AS season,
  COUNT(*) AS n,
  SUM(t.amount) AS total
FROM transactions t JOIN users u ON u.id = t.user_id
WHERE u.username = 'demo'
GROUP BY season_year, season
ORDER BY season_year, FIELD(season, 'SPRING', 'SUMMER', 'AUTUMN', 'WINTER');

-- 3. 傾向の 3 つの窓の合計。期待：T1 = 449000、T2 = 568300、T3 = 476500
SELECT
  CASE WHEN t.transaction_date BETWEEN '2023-09-01' AND '2024-08-31' THEN 'T1'
       WHEN t.transaction_date BETWEEN '2024-09-01' AND '2025-08-31' THEN 'T2'
       WHEN t.transaction_date BETWEEN '2025-09-01' AND '2026-08-31' THEN 'T3' END AS w,
  COUNT(*) AS n, SUM(t.amount) AS total
FROM transactions t JOIN users u ON u.id = t.user_id
WHERE u.username = 'demo' AND t.transaction_date BETWEEN '2023-09-01' AND '2026-08-31'
GROUP BY w
ORDER BY w;

-- 4. 過去の冬（2023・2024・2025 年の冬）の科目ごとの合計。期待は手順書 4.3 の表
SELECT c.name,
  SUM(CASE WHEN t.transaction_date BETWEEN '2023-12-01' AND '2024-02-29' THEN t.amount ELSE 0 END) AS winter_2023,
  SUM(CASE WHEN t.transaction_date BETWEEN '2024-12-01' AND '2025-02-28' THEN t.amount ELSE 0 END) AS winter_2024,
  SUM(CASE WHEN t.transaction_date BETWEEN '2025-12-01' AND '2026-02-28' THEN t.amount ELSE 0 END) AS winter_2025
FROM categories c JOIN users u ON u.id = c.user_id
LEFT JOIN transactions t ON t.category_id = c.id AND t.user_id = u.id
WHERE u.username = 'demo'
GROUP BY c.id, c.name
ORDER BY c.id;

-- 5. 医療費・娯楽は、2026 年 秋より前に 0 件。期待：0 行
SELECT c.name, COUNT(*) AS n
FROM transactions t JOIN categories c ON c.id = t.category_id JOIN users u ON u.id = t.user_id
WHERE u.username = 'demo' AND c.name IN ('医療費', '娯楽') AND t.transaction_date < '2026-09-01'
GROUP BY c.name;

-- 6. 2026 年 秋の予算が変わっていないこと。期待：8 行、合計 175000
SELECT c.name, b.amount
FROM budgets b JOIN categories c ON c.id = b.category_id JOIN users u ON u.id = b.user_id
WHERE u.username = 'demo' AND b.season_year = 2026 AND b.season = 'AUTUMN'
ORDER BY c.id;
