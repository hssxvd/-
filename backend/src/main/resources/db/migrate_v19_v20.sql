-- =============================================================
-- V19/V20 DDL（直接执行，不依赖 Flyway）
-- =============================================================

CREATE TABLE IF NOT EXISTS user_browse_history (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    product_id    BIGINT       NOT NULL,
    category_id   BIGINT       NULL,
    community_id  BIGINT       NULL,
    duration_ms   INT          DEFAULT 0,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_time     (user_id, create_time DESC),
    INDEX idx_product       (product_id),
    INDEX idx_category      (category_id),
    INDEX idx_user_category (user_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_category_preference (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    category_id   BIGINT       NOT NULL,
    browse_weight DECIMAL(8,2) DEFAULT 0,
    cart_weight   DECIMAL(8,2) DEFAULT 0,
    buy_weight    DECIMAL(8,2) DEFAULT 0,
    total_weight  DECIMAL(8,2) DEFAULT 0,
    last_visit_at DATETIME     NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_category (user_id, category_id),
    INDEX idx_user_weight_desc  (user_id, total_weight DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_buy_activity (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    activity_no       VARCHAR(32)   NOT NULL UNIQUE,
    title             VARCHAR(100)  NOT NULL,
    product_id        BIGINT        NOT NULL,
    sku_id            BIGINT        NOT NULL,
    merchant_id       BIGINT        NOT NULL,
    community_id      BIGINT        NOT NULL,
    min_group_qty     INT           NOT NULL DEFAULT 50,
    max_group_qty     INT           NOT NULL DEFAULT 500,
    group_price       DECIMAL(10,2) NOT NULL,
    original_price    DECIMAL(10,2) NOT NULL,
    discount_percent  DECIMAL(5,2)  NULL,
    cycle_start       DATETIME      NOT NULL,
    cycle_end         DATETIME      NOT NULL,
    expected_arrive   DATETIME      NOT NULL,
    pickup_point_id   BIGINT        NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    auto_cutoff       TINYINT(1)    NOT NULL DEFAULT 1,
    create_by         BIGINT        NULL,
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted        TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_community_cycle (community_id, cycle_start, cycle_end),
    INDEX idx_sku_community   (sku_id, community_id),
    INDEX idx_status          (status),
    CONSTRAINT chk_cycle CHECK (cycle_end > cycle_start AND expected_arrive > cycle_end),
    CONSTRAINT chk_qty   CHECK (min_group_qty > 0 AND max_group_qty >= min_group_qty),
    CONSTRAINT chk_price CHECK (group_price > 0 AND original_price >= group_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_buy_member (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    activity_id   BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    order_id      BIGINT        NULL,
    quantity      INT           NOT NULL,
    group_price   DECIMAL(10,2) NOT NULL,
    is_leader     TINYINT(1)    NOT NULL DEFAULT 0,
    join_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_status    VARCHAR(16)   NOT NULL DEFAULT 'UNPAID',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_activity_user (activity_id, user_id),
    INDEX idx_user          (user_id),
    UNIQUE KEY uk_activity_user (activity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_buy_progress (
    activity_id   BIGINT PRIMARY KEY,
    joined_qty    INT    NOT NULL DEFAULT 0,
    joined_count  INT    NOT NULL DEFAULT 0,
    progress_pct  INT    NOT NULL DEFAULT 0,
    is_formed     TINYINT(1) NOT NULL DEFAULT 0,
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
