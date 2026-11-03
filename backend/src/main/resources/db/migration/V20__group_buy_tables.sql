-- =============================================================
-- V20 团购模块 DDL
-- 约束对照：项目名含"团购"必须体现团特征 / L62-68 状态一致性 / L282-285 库存不变量
-- 设计原则：SKU × Community × Cycle = 一个团实例
-- =============================================================

-- 3. group_buy_activity（团购活动模板 + 周期实例）
CREATE TABLE IF NOT EXISTS `group_buy_activity` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `activity_no`       VARCHAR(32)   NOT NULL UNIQUE COMMENT 'GB + 雪花ID',
    `title`             VARCHAR(100)  NOT NULL COMMENT '活动标题',
    `product_id`        BIGINT        NOT NULL,
    `sku_id`            BIGINT        NOT NULL,
    `merchant_id`       BIGINT        NOT NULL COMMENT '数据隔离',
    `community_id`      BIGINT        NOT NULL COMMENT '团唯一键维度之一',
    `min_group_qty`     INT           NOT NULL DEFAULT 50  COMMENT '最小成团量',
    `max_group_qty`     INT           NOT NULL DEFAULT 500 COMMENT '封顶',
    `group_price`       DECIMAL(10,2) NOT NULL COMMENT '团购价（≤ product_sku.price）',
    `original_price`    DECIMAL(10,2) NOT NULL COMMENT '原价展示',
    `discount_percent`  DECIMAL(5,2)  NULL     COMMENT '冗余折扣',
    `cycle_start`       DATETIME      NOT NULL COMMENT '拼团开始',
    `cycle_end`         DATETIME      NOT NULL COMMENT '截单时间',
    `expected_arrive`   DATETIME      NOT NULL COMMENT '预计到货自提',
    `pickup_point_id`   BIGINT        NULL     COMMENT '固定自提点（可为空=下单时选）',
    `status`            VARCHAR(16)   NOT NULL DEFAULT 'OPEN'
                          COMMENT 'OPEN/FULFILLED/SHIPPED/COMPLETED/CANCELLED',
    `auto_cutoff`       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '自动定时处理',
    `create_by`         BIGINT        NULL,
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`        TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_community_cycle` (`community_id`, `cycle_start`, `cycle_end`),
    INDEX `idx_sku_community`    (`sku_id`, `community_id`),
    INDEX `idx_status`           (`status`),
    CONSTRAINT `chk_cycle` CHECK (`cycle_end` > `cycle_start` AND `expected_arrive` > `cycle_end`),
    CONSTRAINT `chk_qty`   CHECK (`min_group_qty` > 0 AND `max_group_qty` >= `min_group_qty`),
    CONSTRAINT `chk_price` CHECK (`group_price` > 0 AND `original_price` >= `group_price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区团购活动';

-- 4. group_buy_member（团成员）
CREATE TABLE IF NOT EXISTS `group_buy_member` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `activity_id`   BIGINT        NOT NULL,
    `user_id`       BIGINT        NOT NULL,
    `order_id`      BIGINT        NULL     COMMENT '成团后生成的订单',
    `quantity`      INT           NOT NULL,
    `group_price`   DECIMAL(10,2) NOT NULL COMMENT '快照价（遵守 L274 订单快照思想）',
    `is_leader`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否团长',
    `join_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `pay_status`    VARCHAR(16)   NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAID/REFUNDED',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_activity_user` (`activity_id`, `user_id`),
    INDEX `idx_user`          (`user_id`),
    CONSTRAINT `fk_gb_activity` FOREIGN KEY (`activity_id`) REFERENCES `group_buy_activity`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区团购参团成员';

-- 5. group_buy_progress（冗余加速，实时进度也可直接聚合 member 表）
CREATE TABLE IF NOT EXISTS `group_buy_progress` (
    `activity_id`   BIGINT PRIMARY KEY,
    `joined_qty`    INT    NOT NULL DEFAULT 0,
    `joined_count`  INT    NOT NULL DEFAULT 0,
    `progress_pct`  INT    NOT NULL DEFAULT 0,
    `is_formed`     TINYINT(1) NOT NULL DEFAULT 0,
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT `fk_gb_activity2` FOREIGN KEY (`activity_id`) REFERENCES `group_buy_activity`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购进度（冗余加速）';

-- =============================================================
-- 种子：10 条团购活动（用已有的商品 + SKU，社区 1-4）
-- =============================================================
INSERT INTO `group_buy_activity`
    (`id`, `activity_no`, `title`, `product_id`, `sku_id`, `merchant_id`, `community_id`,
     `min_group_qty`, `max_group_qty`, `group_price`, `original_price`, `discount_percent`,
     `cycle_start`, `cycle_end`, `expected_arrive`, `status`, `create_time`)
SELECT
    @i := @i + 1 AS id,
    CONCAT('GB', DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD(@i, 3, '0')) AS activity_no,
    CONCAT(LEFT(p.product_name, 12), ' 团购专场') AS title,
    p.id AS product_id,
    ps.id AS sku_id,
    p.merchant_id,
    p.community_id,
    30, 300,
    ROUND(ps.price * 0.8, 2) AS group_price,
    ps.price AS original_price,
    ROUND(80.0, 2) AS discount_percent,
    NOW() - INTERVAL 1 DAY AS cycle_start,
    NOW() + INTERVAL 2 DAY AS cycle_end,
    NOW() + INTERVAL 4 DAY AS expected_arrive,
    'OPEN' AS status,
    NOW() AS create_time
FROM (SELECT @i := 0) vars,
     (SELECT id, product_name, merchant_id, community_id FROM product WHERE status = 'ON_SHELF' AND is_deleted = 0 LIMIT 10) p
JOIN product_sku ps ON ps.product_id = p.id AND ps.status = 'ON_SHELF';
