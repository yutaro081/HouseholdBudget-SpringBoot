-- HouseholdBudget のテーブルを作る SQL（設計書 章3、Q17）
-- アプリの起動のたびに実行される（spring.sql.init.mode=always）。
-- そのため、すべて CREATE TABLE IF NOT EXISTS で書く。DROP TABLE は入れない。
-- すでにあるテーブルは変更されない。初期データ（科目など）も入れない（初期データはアプリが作る、3.11）。
-- 秘密情報や、入力値は含めない（7.4、7.9）。
-- 作る順番は、外部キーの参照先から（users が最初、transactions が最後）。
-- 名前の付け方：uk_ = 一意制約、idx_ = 索引、fk_ = 外部キー、ck_ = CHECK 制約。
-- 照合順序は、すべてのテーブルで utf8mb4_0900_as_ci を明記する（3.1、3.12）。

-- ---------------------------------------------------------------
-- users（利用者）設計書 3.2
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(10)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------
-- categories（科目）設計書 3.3
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    user_id BIGINT      NOT NULL,
    name    VARCHAR(10) NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------
-- vendors（取引先）設計書 3.4
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vendors (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    user_id BIGINT      NOT NULL,
    name    VARCHAR(20) NOT NULL,
    CONSTRAINT pk_vendors PRIMARY KEY (id),
    CONSTRAINT uk_vendors_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_vendors_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------
-- payment_methods（支払方法）設計書 3.5
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payment_methods (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    user_id BIGINT      NOT NULL,
    name    VARCHAR(10) NOT NULL,
    CONSTRAINT pk_payment_methods PRIMARY KEY (id),
    CONSTRAINT uk_payment_methods_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_payment_methods_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------
-- budgets（予算）設計書 3.6、3.7
-- 予算は「科目・年・季節」ごとに1つ。season_year は冬なら12月がある年。
-- 行がない季節は「未設定」（0円とは別）。
-- 科目の削除は、アプリが先にその科目の予算の行をすべて削除してから行う（3.7）。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS budgets (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,
    season_year INT         NOT NULL,
    season      VARCHAR(6)  NOT NULL,
    amount      INT         NOT NULL,
    CONSTRAINT pk_budgets PRIMARY KEY (id),
    CONSTRAINT uk_budgets_category_season UNIQUE (category_id, season_year, season),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES categories (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_budgets_season_year CHECK (season_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_budgets_season CHECK (season IN ('SPRING', 'SUMMER', 'AUTUMN', 'WINTER')),
    CONSTRAINT ck_budgets_amount CHECK (amount BETWEEN 0 AND 3000000),
    INDEX idx_budgets_user_season (user_id, season_year, season)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;

-- ---------------------------------------------------------------
-- transactions（取引）設計書 3.6、3.7
-- 摘要（description）だけ NULL を許す。
-- 金額と取引の日付（2000年3月1日から2100年12月31日）の範囲は、入力チェック（章6）に加えて、CHECK 制約でも守る。
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    transaction_date  DATE        NOT NULL,
    amount            INT         NOT NULL,
    category_id       BIGINT      NOT NULL,
    description       VARCHAR(30) NULL,
    vendor_id         BIGINT      NOT NULL,
    payment_method_id BIGINT      NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_transactions_amount CHECK (amount BETWEEN 1 AND 1000000),
    CONSTRAINT ck_transactions_date CHECK (transaction_date BETWEEN '2000-03-01' AND '2100-12-31'),
    INDEX idx_transactions_user_date (user_id, transaction_date)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
