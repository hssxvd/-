-- =============================================================
-- V19 推荐模块 DDL + 种子数据（幂等 ON DUPLICATE KEY UPDATE）
-- 约束对照：L42-43 核心功能 / L260-272 核心表清单 / L292 预定义字典 / L759-L763 个性化合规
-- =============================================================

-- 1. 用户浏览历史（只存 user_id + product_id + category_id + 时间，不含任何隐私字段 —— 约束 L760）
CREATE TABLE IF NOT EXISTS `user_browse_history` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL COMMENT 'JWT subject，不含真实姓名/手机号',
    `product_id`    BIGINT       NOT NULL,
    `category_id`   BIGINT       NULL     COMMENT '冗余自 product.category_id（聚合偏好用）',
    `community_id`  BIGINT       NULL     COMMENT '浏览时所在社区（分社区统计爆款用）',
    `duration_ms`   INT          DEFAULT 0,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_time`     (`user_id`, `create_time` DESC),
    INDEX `idx_product`       (`product_id`),
    INDEX `idx_category`      (`category_id`),
    INDEX `idx_user_category` (`user_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览历史（推荐数据源，不含隐私字段）';

-- 2. 用户分类偏好快照（加速 relevance_score 计算）
CREATE TABLE IF NOT EXISTS `user_category_preference` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `category_id`   BIGINT       NOT NULL,
    `browse_weight` DECIMAL(8,2) DEFAULT 0 COMMENT '浏览权重，每次浏览 +1 / 天衰减 0.95',
    `cart_weight`   DECIMAL(8,2) DEFAULT 0 COMMENT '加购权重，每次 +3',
    `buy_weight`    DECIMAL(8,2) DEFAULT 0 COMMENT '下单权重，每次 +5',
    `total_weight`  DECIMAL(8,2) DEFAULT 0 COMMENT '浏览+加购+下单加权总分',
    `last_visit_at` DATETIME     NULL,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_category`       (`user_id`, `category_id`),
    INDEX `idx_user_weight_desc`        (`user_id`, `total_weight` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户分类偏好快照（推荐加速）';

-- =============================================================
-- 3. 社区扩容（当前 community 仅 2 条，扩到 4 条）
-- =============================================================
INSERT INTO `community` (`id`, `community_name`, `community_code`, `address`, `contact_name`, `contact_phone`, `status`, `create_time`, `is_deleted`)
VALUES
  (3, '浦东张江社区',    'PUDONG_ZJ',   '浦东新区张江高科技园区博云路 2 号', '李阿姨', '1380000****', 'ACTIVE', NOW(), 0),
  (4, '浦东联洋社区',    'PUDONG_LY',   '浦东新区联洋国际社区',               '王阿姨', '1390000****', 'ACTIVE', NOW(), 0)
ON DUPLICATE KEY UPDATE `community_name`=VALUES(`community_name`);

-- 4. 商家扩容（从 2 → 5）
INSERT INTO `merchant` (`id`, `merchant_name`, `merchant_code`, `contact_name`, `contact_phone`, `address`, `status`, `create_time`, `is_deleted`)
VALUES
  (3, '鲜蔬果品店',     'FRESH03', '张老板', '1370000****', '浦东新区张江路 222 号',      'ACTIVE', NOW(), 0),
  (4, '崇明粮油直销',   'GRAIN04', '赵老板', '1360000****', '崇明县堡镇工农路 56 号',    'ACTIVE', NOW(), 0),
  (5, '烘焙小作坊',     'BAKE05',  '陈老板', '1350000****', '徐汇区漕河泾创业园 B 座 3F','ACTIVE', NOW(), 0)
ON DUPLICATE KEY UPDATE `merchant_name`=VALUES(`merchant_name`);

-- 5. 分类扩容（从 ~8 → 12，三级结构：根分类 3 + 子分类 9）
INSERT INTO `category` (`id`, `parent_id`, `category_name`, `category_code`, `sort_order`, `icon`, `status`, `create_time`, `is_deleted`)
VALUES
  -- 根分类（parent_id=NULL）
  (11, NULL, '时令生鲜', 'FRESH_SEASON', 1, '🥬', 'ACTIVE', NOW(), 0),
  (12, NULL, '粮油副食', 'GRAIN_OIL',    2, '🌾', 'ACTIVE', NOW(), 0),
  (13, NULL, '烘焙点心', 'BAKE_SNACK',   3, '🍞', 'ACTIVE', NOW(), 0),
  -- 子分类（各根 × 3 = 9）
  (14, 11, '有机蔬菜', 'VEG_ORGANIC',  1, '🥬', 'ACTIVE', NOW(), 0),
  (15, 11, '时令水果', 'FRUIT_SEASON', 2, '🍎', 'ACTIVE', NOW(), 0),
  (16, 11, '肉蛋家禽', 'MEAT_EGG',     3, '🥩', 'ACTIVE', NOW(), 0),
  (17, 12, '米面粮油', 'STAPLE_GRAIN', 1, '🌾', 'ACTIVE', NOW(), 0),
  (18, 12, '干货调料', 'DRY_SEASON',   2, '🧂', 'ACTIVE', NOW(), 0),
  (19, 12, '乳制品',   'DAIRY',        3, '🥛', 'ACTIVE', NOW(), 0),
  (20, 13, '面包吐司', 'BREAD_TOAST',  1, '🍞', 'ACTIVE', NOW(), 0),
  (21, 13, '蛋糕甜品', 'CAKE_DESSERT', 2, '🍰', 'ACTIVE', NOW(), 0),
  (22, 13, '休闲零食', 'SNACK',        3, '🍪', 'ACTIVE', NOW(), 0)
ON DUPLICATE KEY UPDATE `category_name`=VALUES(`category_name`);
