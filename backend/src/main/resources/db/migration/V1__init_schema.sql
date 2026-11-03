-- ============================================================
-- 社区团购电商系统 数据库初始化脚本
-- 版本: V1__init_schema.sql
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- 引擎: InnoDB
-- 时区: Asia/Shanghai
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 0. 创建数据库
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `community_group_buy`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `community_group_buy`;

-- ============================================================
-- 一、用户与权限模块
-- ============================================================

-- 1. user 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`        VARCHAR(64)  NOT NULL                COMMENT '登录名',
  `password`        VARCHAR(128) NOT NULL                COMMENT 'BCrypt 加密',
  `nickname`        VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
  `phone`           VARCHAR(20)  DEFAULT NULL            COMMENT '脱敏存储或加密',
  `avatar`          VARCHAR(255) DEFAULT NULL            COMMENT '头像',
  `user_type`       VARCHAR(32)  NOT NULL                COMMENT 'CONSUMER/MERCHANT/ADMIN',
  `merchant_id`     BIGINT       DEFAULT NULL            COMMENT '商家 ID，消费者为空',
  `community_id`    BIGINT       DEFAULT NULL            COMMENT '所属社区',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `last_login_time` DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_user_type` (`user_type`),
  KEY `idx_community_id` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 2. role 角色表
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code`   VARCHAR(64)  NOT NULL                COMMENT '角色编码',
  `role_name`   VARCHAR(64)  NOT NULL                COMMENT '角色名',
  `description` VARCHAR(255) DEFAULT NULL            COMMENT '描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

-- 3. permission 权限表
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `permission_code`  VARCHAR(128) NOT NULL                COMMENT '权限编码，如 product:add',
  `permission_name`  VARCHAR(64)  NOT NULL                COMMENT '权限名',
  `module`           VARCHAR(64)  DEFAULT NULL            COMMENT '所属模块',
  `description`      VARCHAR(255) DEFAULT NULL            COMMENT '描述',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限表';

-- 4. user_role 用户角色关联表
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `role_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

-- 5. role_permission 角色权限关联表
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `role_id`         BIGINT   NOT NULL,
  `permission_id`   BIGINT   NOT NULL,
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色权限关联表';

-- 6. operation_log 操作日志表
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       DEFAULT NULL            COMMENT '操作人',
  `role_code`      VARCHAR(64)  DEFAULT NULL            COMMENT '角色',
  `module`         VARCHAR(64)  NOT NULL                COMMENT '模块',
  `operation_type` VARCHAR(64)  NOT NULL                COMMENT '操作类型',
  `target_id`      BIGINT       DEFAULT NULL            COMMENT '对象 ID',
  `target_type`    VARCHAR(64)  DEFAULT NULL            COMMENT '对象类型',
  `before_status`  VARCHAR(32)  DEFAULT NULL            COMMENT '前状态',
  `after_status`   VARCHAR(32)  DEFAULT NULL            COMMENT '后状态',
  `request_params` TEXT         DEFAULT NULL            COMMENT '参数摘要，脱敏',
  `result`         VARCHAR(32)  DEFAULT NULL            COMMENT 'SUCCESS/FAIL',
  `error_msg`      VARCHAR(512) DEFAULT NULL            COMMENT '错误信息',
  `ip`             VARCHAR(64)  DEFAULT NULL            COMMENT 'IP',
  `trace_id`       VARCHAR(64)  DEFAULT NULL            COMMENT '链路 ID',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';

-- ============================================================
-- 二、社区与自提点模块
-- ============================================================

-- 7. community 社区表
DROP TABLE IF EXISTS `community`;
CREATE TABLE `community` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `community_name` VARCHAR(128) NOT NULL,
  `community_code` VARCHAR(64)  NOT NULL,
  `address`       VARCHAR(255) DEFAULT NULL,
  `contact_name`  VARCHAR(64)  DEFAULT NULL,
  `contact_phone` VARCHAR(20)  DEFAULT NULL            COMMENT '脱敏',
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_code` (`community_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='社区表';

-- 8. pickup_point 自提点表
DROP TABLE IF EXISTS `pickup_point`;
CREATE TABLE `pickup_point` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `community_id`  BIGINT       NOT NULL,
  `point_name`    VARCHAR(128) NOT NULL,
  `address`       VARCHAR(255) DEFAULT NULL,
  `contact_name`  VARCHAR(64)  DEFAULT NULL,
  `contact_phone` VARCHAR(20)  DEFAULT NULL            COMMENT '脱敏',
  `open_time`     VARCHAR(64)  DEFAULT NULL            COMMENT '营业时间，如 08:00-20:00',
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_community_id` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='自提点表';

-- ============================================================
-- 三、商品模块
-- ============================================================

-- 9. category 分类表
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id`   BIGINT       DEFAULT NULL            COMMENT '父分类，NULL 为一级',
  `category_name` VARCHAR(64) NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `sort_order`  INT          DEFAULT 0,
  `icon`        VARCHAR(255) DEFAULT NULL,
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分类表';

-- 10. product 商品表
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `product_code`  VARCHAR(64)  NOT NULL,
  `product_name`  VARCHAR(128) NOT NULL,
  `category_id`   BIGINT       NOT NULL,
  `merchant_id`   BIGINT       NOT NULL,
  `community_id`  BIGINT       DEFAULT NULL            COMMENT '所属社区，NULL 表示全社区',
  `main_image`    VARCHAR(255) DEFAULT NULL,
  `images`        TEXT         DEFAULT NULL            COMMENT '图片 JSON 数组',
  `description`   TEXT         DEFAULT NULL,
  `unit`          VARCHAR(16)  DEFAULT NULL            COMMENT '单位，如 斤/个/盒',
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON_SHELF/OFF_SHELF/SOLD_OUT',
  `deadline_time` DATETIME     DEFAULT NULL            COMMENT '截单时间',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_community_id` (`community_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品表';

-- 11. product_sku SKU 表
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `product_id`   BIGINT        NOT NULL,
  `sku_code`     VARCHAR(64)   NOT NULL,
  `sku_name`     VARCHAR(128)  NOT NULL,
  `spec`         VARCHAR(128)  DEFAULT NULL           COMMENT '规格描述',
  `price`        DECIMAL(12,2) NOT NULL               COMMENT '团购价',
  `original_price` DECIMAL(12,2) DEFAULT NULL          COMMENT '原价',
  `cost_price`   DECIMAL(12,2) DEFAULT NULL           COMMENT '成本价',
  `image`        VARCHAR(255)  DEFAULT NULL,
  `status`       VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU 表';

-- 12. product_attribute 商品特色属性表
DROP TABLE IF EXISTS `product_attribute`;
CREATE TABLE `product_attribute` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `product_id`     BIGINT       NOT NULL,
  `attr_key`       VARCHAR(64)  NOT NULL                COMMENT '属性键，预定义字典：产地/品牌/保质期/规格/重量/适用人群/特色标签',
  `attr_value`     VARCHAR(255) NOT NULL                COMMENT '属性值',
  `attr_unit`      VARCHAR(16)  DEFAULT NULL            COMMENT '单位（可为空）',
  `icon_url`       VARCHAR(255) DEFAULT NULL            COMMENT '标签图标',
  `is_filterable`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否用于分类检索/筛选',
  `is_highlight`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否在商品卡片/详情页高亮',
  `sort_order`     INT          DEFAULT 0,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_attr_key` (`attr_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品特色属性表';

-- 13. product_sku_attribute SKU 固有属性表
DROP TABLE IF EXISTS `product_sku_attribute`;
CREATE TABLE `product_sku_attribute` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `sku_id`       BIGINT       NOT NULL,
  `attr_name`    VARCHAR(64)  NOT NULL                COMMENT '属性名，如 颜色/尺寸',
  `attr_value`   VARCHAR(255) NOT NULL                COMMENT '属性值，如 红/120ml',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU 固有属性表';

-- ============================================================
-- 四、供应与库存模块
-- ============================================================

-- 14. supply_plan 供应计划表
DROP TABLE IF EXISTS `supply_plan`;
CREATE TABLE `supply_plan` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `plan_no`        VARCHAR(64)   NOT NULL,
  `sku_id`         BIGINT        NOT NULL,
  `merchant_id`    BIGINT        DEFAULT NULL,
  `community_id`   BIGINT        NOT NULL,
  `plan_date`      DATE          NOT NULL                 COMMENT '计划日期/截单日',
  `forecast_qty`   DECIMAL(12,2) DEFAULT NULL            COMMENT '关联需求预测值（forecast_result）',
  `plan_qty`       INT           NOT NULL                 COMMENT '计划采购量',
  `adjusted_qty`   INT           DEFAULT NULL             COMMENT '调整后数量',
  `adjust_reason`  VARCHAR(255)  DEFAULT NULL             COMMENT '调整原因',
  `status`         VARCHAR(32)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/ARRIVED/CANCELLED',
  `arrive_time`    DATETIME      DEFAULT NULL             COMMENT '到货时间',
  `operator_id`    BIGINT        DEFAULT NULL             COMMENT '操作人',
  `remark`         VARCHAR(255)  DEFAULT NULL,
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_no` (`plan_no`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_community_id` (`community_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_plan_date` (`plan_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应计划表';

-- 15. stock 库存表
DROP TABLE IF EXISTS `stock`;
CREATE TABLE `stock` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `sku_id`          BIGINT   NOT NULL,
  `community_id`    BIGINT   NOT NULL,
  `pickup_point_id` BIGINT   DEFAULT NULL              COMMENT '自提点，NULL 表示社区级库存',
  `total_qty`       INT      NOT NULL DEFAULT 0,
  `locked_qty`      INT      NOT NULL DEFAULT 0,
  `available_qty`   INT      NOT NULL DEFAULT 0,
  `warn_qty`        INT      DEFAULT NULL              COMMENT '预警值',
  `version`         INT      NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_community_pickup` (`sku_id`, `community_id`, `pickup_point_id`),
  KEY `idx_community_id` (`community_id`),
  KEY `idx_available_qty` (`available_qty`),
  CONSTRAINT `chk_available_qty_ge0` CHECK (`available_qty` >= 0),
  CONSTRAINT `chk_locked_qty_ge0`    CHECK (`locked_qty` >= 0),
  CONSTRAINT `chk_total_qty_ge0`     CHECK (`total_qty` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存表';

-- 16. stock_record 库存流水表
DROP TABLE IF EXISTS `stock_record`;
CREATE TABLE `stock_record` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `stock_id`     BIGINT       NOT NULL,
  `sku_id`       BIGINT       NOT NULL,
  `community_id` BIGINT       NOT NULL,
  `change_type`  VARCHAR(32)  NOT NULL                COMMENT 'LOCK/UNLOCK/DEDUCT/ROLLBACK/INBOUND/SUPPLY_IN',
  `change_qty`   INT          NOT NULL                COMMENT '变更数量，正数增加，负数减少',
  `before_total` INT          DEFAULT NULL,
  `after_total`  INT          DEFAULT NULL,
  `before_locked` INT         DEFAULT NULL,
  `after_locked`  INT         DEFAULT NULL,
  `order_no`     VARCHAR(64)  DEFAULT NULL            COMMENT '关联订单号',
  `remark`       VARCHAR(255) DEFAULT NULL,
  `operator_id`  BIGINT       DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_stock_id` (`stock_id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存流水表';

-- ============================================================
-- 五、购物车模块
-- ============================================================

-- 17. cart_item 购物车表
DROP TABLE IF EXISTS `cart_item`;
CREATE TABLE `cart_item` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT        NOT NULL,
  `sku_id`         BIGINT        NOT NULL,
  `product_id`     BIGINT        NOT NULL,
  `community_id`   BIGINT        NOT NULL,
  `quantity`       INT           NOT NULL,
  `price_snapshot` DECIMAL(12,2) NOT NULL                 COMMENT '加入购物车时的价格快照',
  `selected`       TINYINT       NOT NULL DEFAULT 1        COMMENT '是否选中，0/1',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`, `is_deleted`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='购物车表';

-- ============================================================
-- 六、订单模块
-- ============================================================

-- 18. orders 订单表
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `order_no`           VARCHAR(64)   NOT NULL,
  `user_id`            BIGINT        NOT NULL,
  `merchant_id`        BIGINT        DEFAULT NULL,
  `community_id`       BIGINT        NOT NULL,
  `pickup_point_id`    BIGINT        DEFAULT NULL,
  `total_amount`       DECIMAL(12,2) NOT NULL                 COMMENT '商品总额',
  `discount_amount`    DECIMAL(12,2) NOT NULL DEFAULT 0,
  `freight_amount`     DECIMAL(12,2) NOT NULL DEFAULT 0,
  `pay_amount`         DECIMAL(12,2) NOT NULL                 COMMENT '实付金额',
  `refunded_amount`    DECIMAL(12,2) NOT NULL DEFAULT 0        COMMENT '已退款金额',
  `order_status`       VARCHAR(32)   NOT NULL                 COMMENT '订单状态',
  `pay_status`         VARCHAR(32)   NOT NULL                 COMMENT '支付状态',
  `fulfillment_status` VARCHAR(32)   DEFAULT NULL             COMMENT '履约状态',
  `aftersale_status`   VARCHAR(32)   DEFAULT NULL             COMMENT '售后状态',
  `receiver_name`      VARCHAR(64)   DEFAULT NULL,
  `receiver_phone`     VARCHAR(20)   DEFAULT NULL             COMMENT '脱敏',
  `receiver_address`   VARCHAR(255)  DEFAULT NULL,
  `remark`             VARCHAR(255)  DEFAULT NULL,
  `expire_time`        DATETIME      DEFAULT NULL             COMMENT '支付过期时间',
  `pay_time`           DATETIME      DEFAULT NULL             COMMENT '支付时间',
  `finish_time`        DATETIME      DEFAULT NULL             COMMENT '完成时间',
  `cancel_time`        DATETIME      DEFAULT NULL             COMMENT '取消时间',
  `cancel_reason`      VARCHAR(255)  DEFAULT NULL,
  `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`         TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  KEY `idx_community_id` (`community_id`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_pay_status` (`pay_status`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `chk_pay_amount_ge0`      CHECK (`pay_amount` >= 0),
  CONSTRAINT `chk_refund_le_pay`       CHECK (`refunded_amount` <= `pay_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';

-- 19. order_item 订单明细表
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `order_id`       BIGINT        NOT NULL,
  `order_no`       VARCHAR(64)   NOT NULL,
  `product_id`     BIGINT        NOT NULL,
  `sku_id`         BIGINT        NOT NULL,
  `product_name`   VARCHAR(128)  NOT NULL                 COMMENT '商品名快照',
  `sku_name`       VARCHAR(128)  NOT NULL                 COMMENT 'SKU 名快照',
  `spec`           VARCHAR(128)  DEFAULT NULL             COMMENT '规格快照',
  `image`          VARCHAR(255)  DEFAULT NULL             COMMENT '图片快照',
  `category_id`    BIGINT        DEFAULT NULL             COMMENT '分类快照',
  `price`          DECIMAL(12,2) NOT NULL                 COMMENT '单价快照',
  `quantity`       INT           NOT NULL,
  `subtotal`       DECIMAL(12,2) NOT NULL,
  `attr_snapshot`  JSON          DEFAULT NULL             COMMENT '特色属性快照 JSON',
  `refunded_qty`   INT           NOT NULL DEFAULT 0,
  `refunded_amount` DECIMAL(12,2) NOT NULL DEFAULT 0,
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_sku_id` (`sku_id`),
  CONSTRAINT `chk_refund_qty_le_qty`  CHECK (`refunded_qty` <= `quantity`),
  CONSTRAINT `chk_refund_amt_le_sub`  CHECK (`refunded_amount` <= `subtotal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细表';

-- 20. order_status_log 订单状态日志表
DROP TABLE IF EXISTS `order_status_log`;
CREATE TABLE `order_status_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT       NOT NULL,
  `order_no`     VARCHAR(64)  NOT NULL,
  `from_status`  VARCHAR(32)  DEFAULT NULL,
  `to_status`    VARCHAR(32)  NOT NULL,
  `status_type`  VARCHAR(32)  NOT NULL COMMENT 'ORDER/PAY/FULFILLMENT/AFTERSALE',
  `operator_id`  BIGINT       DEFAULT NULL,
  `operator_role` VARCHAR(64) DEFAULT NULL,
  `reason`       VARCHAR(255) DEFAULT NULL,
  `trace_id`     VARCHAR(64)  DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单状态日志表';

-- ============================================================
-- 七、支付模块
-- ============================================================

-- 21. payment 支付表
DROP TABLE IF EXISTS `payment`;
CREATE TABLE `payment` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `payment_no`   VARCHAR(64)   NOT NULL                 COMMENT '支付流水号',
  `order_id`     BIGINT        NOT NULL,
  `order_no`     VARCHAR(64)   NOT NULL,
  `user_id`      BIGINT        NOT NULL,
  `amount`       DECIMAL(12,2) NOT NULL,
  `pay_type`     VARCHAR(32)   NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/WECHAT/ALIPAY',
  `pay_status`   VARCHAR(32)   NOT NULL                 COMMENT 'PENDING/SUCCESS/FAILED/TIMEOUT/REFUNDING/REFUNDED',
  `callback_time` DATETIME     DEFAULT NULL,
  `refund_amount` DECIMAL(12,2) NOT NULL DEFAULT 0,
  `refund_time`  DATETIME      DEFAULT NULL,
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_pay_status` (`pay_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付表';

-- 22. payment_callback_log 支付回调日志表
DROP TABLE IF EXISTS `payment_callback_log`;
CREATE TABLE `payment_callback_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `payment_no`     VARCHAR(64)  NOT NULL,
  `callback_type`  VARCHAR(32)  NOT NULL                COMMENT 'PAY/REFUND',
  `request_body`   TEXT         DEFAULT NULL             COMMENT '请求体，脱敏',
  `response_body`  TEXT         DEFAULT NULL,
  `result`         VARCHAR(32)  DEFAULT NULL             COMMENT 'SUCCESS/FAIL/DUPLICATE',
  `error_msg`      VARCHAR(512) DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_payment_no` (`payment_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付回调日志表';

-- ============================================================
-- 八、履约模块
-- ============================================================

-- 23. fulfillment 履约表
DROP TABLE IF EXISTS `fulfillment`;
CREATE TABLE `fulfillment` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`           BIGINT       NOT NULL,
  `order_no`           VARCHAR(64)  NOT NULL,
  `fulfillment_type`   VARCHAR(32)  NOT NULL                 COMMENT 'PICKUP/DELIVERY',
  `pickup_point_id`    BIGINT       DEFAULT NULL,
  `receiver_name`      VARCHAR(64)  DEFAULT NULL,
  `receiver_phone`     VARCHAR(20)  DEFAULT NULL             COMMENT '脱敏',
  `receiver_address`   VARCHAR(255) DEFAULT NULL,
  `fulfillment_status` VARCHAR(32)  NOT NULL                 COMMENT '履约状态',
  `assign_time`        DATETIME     DEFAULT NULL,
  `pickup_time`        DATETIME     DEFAULT NULL,
  `delivery_time`      DATETIME     DEFAULT NULL,
  `complete_time`      DATETIME     DEFAULT NULL,
  `exception_reason`   VARCHAR(255) DEFAULT NULL,
  `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_fulfillment_status` (`fulfillment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='履约表';

-- 24. fulfillment_record 履约记录表
DROP TABLE IF EXISTS `fulfillment_record`;
CREATE TABLE `fulfillment_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `fulfillment_id` BIGINT      NOT NULL,
  `from_status`   VARCHAR(32)  DEFAULT NULL,
  `to_status`     VARCHAR(32)  NOT NULL,
  `operator_id`    BIGINT       DEFAULT NULL,
  `operator_role` VARCHAR(64)  DEFAULT NULL,
  `remark`        VARCHAR(255) DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fulfillment_id` (`fulfillment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='履约记录表';

-- ============================================================
-- 九、售后模块
-- ============================================================

-- 25. aftersale 售后表
DROP TABLE IF EXISTS `aftersale`;
CREATE TABLE `aftersale` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `aftersale_no`      VARCHAR(64)   NOT NULL,
  `order_id`          BIGINT        NOT NULL,
  `order_no`          VARCHAR(64)   NOT NULL,
  `order_item_id`     BIGINT        DEFAULT NULL,
  `user_id`           BIGINT        NOT NULL,
  `merchant_id`       BIGINT        DEFAULT NULL,
  `aftersale_type`    VARCHAR(32)   NOT NULL COMMENT 'REFUND/REFUND_ONLY/RETURN',
  `reason`            VARCHAR(255)  DEFAULT NULL,
  `description`       TEXT          DEFAULT NULL,
  `images`            TEXT          DEFAULT NULL,
  `apply_amount`      DECIMAL(12,2) NOT NULL,
  `approved_amount`   DECIMAL(12,2) DEFAULT NULL,
  `aftersale_status`  VARCHAR(32)   NOT NULL COMMENT 'APPLYING/APPROVED/REJECTED/REFUNDING/REFUNDED',
  `audit_operator_id` BIGINT        DEFAULT NULL,
  `audit_time`        DATETIME      DEFAULT NULL,
  `audit_remark`      VARCHAR(255)  DEFAULT NULL,
  `refund_time`       DATETIME      DEFAULT NULL,
  `refund_no`         VARCHAR(64)   DEFAULT NULL,
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aftersale_no` (`aftersale_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_aftersale_status` (`aftersale_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='售后表';

-- 26. aftersale_record 售后记录表
DROP TABLE IF EXISTS `aftersale_record`;
CREATE TABLE `aftersale_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `aftersale_id`   BIGINT       NOT NULL,
  `from_status`    VARCHAR(32)  DEFAULT NULL,
  `to_status`      VARCHAR(32)  NOT NULL,
  `operator_id`    BIGINT       DEFAULT NULL,
  `operator_role`  VARCHAR(64)  DEFAULT NULL,
  `remark`         VARCHAR(255) DEFAULT NULL,
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_aftersale_id` (`aftersale_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='售后记录表';

-- ============================================================
-- 十、评价模块
-- ============================================================

-- 27. review 评价表
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT       NOT NULL,
  `order_item_id` BIGINT      NOT NULL,
  `user_id`      BIGINT       NOT NULL,
  `sku_id`       BIGINT       NOT NULL,
  `merchant_id`  BIGINT       DEFAULT NULL,
  `rating`       TINYINT      NOT NULL                 COMMENT '1-5 星',
  `content`      VARCHAR(512) DEFAULT NULL,
  `images`       TEXT         DEFAULT NULL,
  `is_anonymous` TINYINT      NOT NULL DEFAULT 0,
  `reply_content` VARCHAR(512) DEFAULT NULL,
  `reply_time`   DATETIME     DEFAULT NULL,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item` (`order_item_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_merchant_id` (`merchant_id`),
  CONSTRAINT `chk_rating_between` CHECK (`rating` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价表';

-- ============================================================
-- 十一、预测模块
-- ============================================================

-- 28. forecast_task 预测任务表
DROP TABLE IF EXISTS `forecast_task`;
CREATE TABLE `forecast_task` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `task_no`       VARCHAR(64)  NOT NULL,
  `model_type`    VARCHAR(32)  NOT NULL                COMMENT 'MA/LR/RF/XGB',
  `model_version` VARCHAR(32)  NOT NULL,
  `forecast_date` DATE         NOT NULL,
  `community_id`  BIGINT       DEFAULT NULL,
  `category_id`   BIGINT       DEFAULT NULL,
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  `start_time`    DATETIME     DEFAULT NULL,
  `end_time`      DATETIME     DEFAULT NULL,
  `error_msg`     VARCHAR(512) DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_forecast_date` (`forecast_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预测任务表';

-- 29. forecast_result 预测结果表
DROP TABLE IF EXISTS `forecast_result`;
CREATE TABLE `forecast_result` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `task_id`        BIGINT        NOT NULL,
  `sku_id`         BIGINT        NOT NULL,
  `community_id`   BIGINT        NOT NULL,
  `forecast_date`  DATE          NOT NULL,
  `forecast_value` DECIMAL(12,2) NOT NULL,
  `lower_bound`    DECIMAL(12,2) DEFAULT NULL,
  `upper_bound`    DECIMAL(12,2) DEFAULT NULL,
  `model_version`  VARCHAR(32)   DEFAULT NULL,
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_sku_community_date` (`task_id`, `sku_id`, `community_id`, `forecast_date`),
  KEY `idx_forecast_date` (`forecast_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预测结果表';

-- 30. forecast_metric 预测指标表
DROP TABLE IF EXISTS `forecast_metric`;
CREATE TABLE `forecast_metric` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `task_id`       BIGINT        NOT NULL,
  `model_type`    VARCHAR(32)   NOT NULL,
  `model_version` VARCHAR(32)   DEFAULT NULL,
  `baseline_type` VARCHAR(32)   DEFAULT NULL             COMMENT 'YESTERDAY/SAME_DAY_LAST_WEEK/MA7',
  `mae`           DECIMAL(12,4) DEFAULT NULL,
  `rmse`          DECIMAL(12,4) DEFAULT NULL,
  `smape`         DECIMAL(12,4) DEFAULT NULL,
  `sample_count`  INT           DEFAULT NULL,
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预测指标表';

SET FOREIGN_KEY_CHECKS = 1;
