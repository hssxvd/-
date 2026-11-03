-- ============================================================
-- V11__aftersale_index_and_seed.sql
-- 售后管理模块（v1.14）配套：查询索引 + 幂等种子数据
--
-- 依据：
--   约束文档 5.5 售后状态机（APPLYING → APPROVED|REJECTED；APPROVED → REFUNDING → REFUNDED）
--   约束文档 11   列表接口必须分页（需 merchant_id + aftersale_status 组合索引支撑）
--   约束文档 13.3 测试数据必须基于真实业务数据派生
--
-- 背景：aftersale / aftersale_record 两表结构已含 is_deleted / update_time（V1 已建，无需加列），
--       但 aftersale 表 0 行数据，且缺少 (merchant_id, aftersale_status) 与 create_time 索引，
--       商家端/Admin 端列表分页与状态聚合统计无索引支撑。
--
-- 幂等：
--   索引  —— 先查 information_schema.statistics，存在则跳过（MySQL 不支持 ADD INDEX IF NOT EXISTS）
--   种子  —— 用 NOT EXISTS 按 aftersale_no 守卫，重复执行不产生重复行
--   归一化 —— orders.aftersale_status 的 NULL 统一为 'NONE'（WHERE 条件天然幂等）
--   状态回填 —— 仅更新"尚未回填"的行（refunded_amount = 0 / pay_status <> 'REFUNDED'）
-- ============================================================

SET NAMES utf8mb4;

USE `community_group_buy`;

-- ============================================================
-- 一、索引（幂等）
-- ============================================================

-- 1) 商家端「我的售后」列表 + Admin 端按商家筛选：merchant_id + aftersale_status
SET @exist := (SELECT COUNT(1) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'aftersale'
                 AND index_name = 'idx_merchant_status');
SET @sql := IF(@exist = 0,
               'ALTER TABLE `aftersale` ADD INDEX `idx_merchant_status` (`merchant_id`, `aftersale_status`)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 列表默认按申请时间倒序：create_time
SET @exist := (SELECT COUNT(1) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'aftersale'
                 AND index_name = 'idx_create_time');
SET @sql := IF(@exist = 0,
               'ALTER TABLE `aftersale` ADD INDEX `idx_create_time` (`create_time`)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 二、归一化：orders.aftersale_status 的 NULL → 'NONE'
--     前端与统计口径统一按 NONE 处理，避免 NULL 分支
-- ============================================================
UPDATE `orders` SET `aftersale_status` = 'NONE'
WHERE `aftersale_status` IS NULL AND `is_deleted` = 0;

-- ============================================================
-- 三、种子数据（基于真实 COMPLETED 订单派生，覆盖 3 种可自然到达的售后状态）
--     APPLYING 待审核 → 商家端有可审核项
--     REJECTED 已驳回 → 展示驳回轨迹
--     REFUNDED 已退款 → 展示退款闭环 + 订单聚合状态 REFUNDED
--     注：APPROVED / REFUNDING 是审核事务内的瞬时态，不会持久化，故不造种子
--     注：COMPLETED 订单发生售后后 order_status 保持 COMPLETED（约束 5.2），
--         售后进度由 orders.aftersale_status 表达
-- ============================================================

-- 3.1 APPLYING：订单 ORD1790065070583（merchant 3）
INSERT INTO `aftersale`
  (`aftersale_no`, `order_id`, `order_no`, `order_item_id`, `user_id`, `merchant_id`,
   `aftersale_type`, `reason`, `description`, `images`, `apply_amount`, `approved_amount`,
   `aftersale_status`, `create_time`)
SELECT 'AS20260924000001', o.id, o.order_no, oi.id, o.user_id, o.merchant_id,
       'REFUND_ONLY', '商品与描述不符', '收到的商品规格与页面描述不一致，申请仅退款', NULL,
       oi.subtotal, NULL, 'APPLYING', NOW()
FROM `orders` o
JOIN `order_item` oi ON oi.order_id = o.id AND oi.sku_id = 1
WHERE o.order_no = 'ORD1790065070583' AND o.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `aftersale` a WHERE a.aftersale_no = 'AS20260924000001')
LIMIT 1;

-- 3.2 REJECTED：订单 ORD1790065113686（merchant 3）
INSERT INTO `aftersale`
  (`aftersale_no`, `order_id`, `order_no`, `order_item_id`, `user_id`, `merchant_id`,
   `aftersale_type`, `reason`, `description`, `images`, `apply_amount`, `approved_amount`,
   `aftersale_status`, `audit_operator_id`, `audit_time`, `audit_remark`, `create_time`)
SELECT 'AS20260924000002', o.id, o.order_no, oi.id, o.user_id, o.merchant_id,
       'REFUND_ONLY', '临时不需要了', '下单后临时改变主意，申请退款', NULL,
       oi.subtotal, NULL, 'REJECTED', o.merchant_id, NOW(), '商品已按时送达自提点且已核销，不符合退款条件', NOW()
FROM `orders` o
JOIN `order_item` oi ON oi.order_id = o.id AND oi.sku_id = 1
WHERE o.order_no = 'ORD1790065113686' AND o.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `aftersale` a WHERE a.aftersale_no = 'AS20260924000002')
LIMIT 1;

-- 3.3 REFUNDED（部分退款）：订单 ORD1790065413357（merchant 4）明细 sku 20
--     部分退款不改变 order_status（保持 COMPLETED），仅 orders.aftersale_status = REFUNDED
INSERT INTO `aftersale`
  (`aftersale_no`, `order_id`, `order_no`, `order_item_id`, `user_id`, `merchant_id`,
   `aftersale_type`, `reason`, `description`, `images`, `apply_amount`, `approved_amount`,
   `aftersale_status`, `audit_operator_id`, `audit_time`, `audit_remark`,
   `refund_no`, `refund_time`, `create_time`)
SELECT 'AS20260924000003', o.id, o.order_no, oi.id, o.user_id, o.merchant_id,
       'REFUND_ONLY', '商品破损', '到货时发现外包装破损，申请仅退款', NULL,
       oi.subtotal, oi.subtotal, 'REFUNDED', o.merchant_id, NOW(), '核实属实，同意退款',
       'REF20260924000003', NOW(), NOW()
FROM `orders` o
JOIN `order_item` oi ON oi.order_id = o.id AND oi.sku_id = 20
WHERE o.order_no = 'ORD1790065413357' AND o.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM `aftersale` a WHERE a.aftersale_no = 'AS20260924000003')
LIMIT 1;

-- ============================================================
-- 四、售后轨迹（aftersale_record，幂等：按 aftersale_id + to_status 守卫）
-- ============================================================

-- 4.1 APPLYING 轨迹
INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, NULL, 'APPLYING', a.user_id, 'CONSUMER', '消费者提交售后申请', a.create_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000001'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'APPLYING');

-- 4.2 REJECTED 轨迹（申请 → 驳回）
INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, NULL, 'APPLYING', a.user_id, 'CONSUMER', '消费者提交售后申请', a.create_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000002'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'APPLYING');

INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, 'APPLYING', 'REJECTED', a.audit_operator_id, 'MERCHANT',
       CONCAT('商家驳回：', IFNULL(a.audit_remark, '')), a.audit_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000002'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'REJECTED');

-- 4.3 REFUNDED 轨迹（申请 → 通过 → 退款中 → 已退款）
INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, NULL, 'APPLYING', a.user_id, 'CONSUMER', '消费者提交售后申请', a.create_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000003'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'APPLYING');

INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, 'APPLYING', 'APPROVED', a.audit_operator_id, 'MERCHANT',
       CONCAT('商家审核通过：', IFNULL(a.audit_remark, '')), a.audit_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000003'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'APPROVED');

INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, 'APPROVED', 'REFUNDING', NULL, 'SYSTEM', CONCAT('发起退款：', IFNULL(a.refund_no, '')), a.audit_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000003'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'REFUNDING');

INSERT INTO `aftersale_record`
  (`aftersale_id`, `from_status`, `to_status`, `operator_id`, `operator_role`, `remark`, `create_time`)
SELECT a.id, 'REFUNDING', 'REFUNDED', NULL, 'SYSTEM', '退款完成', a.refund_time
FROM `aftersale` a
WHERE a.aftersale_no = 'AS20260924000003'
  AND NOT EXISTS (SELECT 1 FROM `aftersale_record` r WHERE r.aftersale_id = a.id AND r.to_status = 'REFUNDED');

-- ============================================================
-- 五、订单聚合状态与退款金额回填（与审核事务写入口径一致，幂等）
-- ============================================================

-- 5.1 APPLYING 订单：orders.aftersale_status = 'APPLYING'
UPDATE `orders` o
SET o.`aftersale_status` = 'APPLYING'
WHERE o.`order_no` = 'ORD1790065070583'
  AND o.`aftersale_status` <> 'APPLYING';

-- 5.2 REJECTED 订单：驳回后售后聚合状态回落 'NONE'
UPDATE `orders` o
SET o.`aftersale_status` = 'NONE'
WHERE o.`order_no` = 'ORD1790065113686'
  AND o.`aftersale_status` <> 'NONE';

-- 5.3 REFUNDED 订单：累加 refunded_amount + 聚合状态 REFUNDED
UPDATE `orders` o
JOIN `aftersale` a ON o.`id` = a.`order_id`
SET o.`refunded_amount` = o.`refunded_amount` + a.`approved_amount`,
    o.`aftersale_status` = 'REFUNDED'
WHERE a.`aftersale_no` = 'AS20260924000003'
  AND o.`refunded_amount` = 0;

-- 5.4 REFUNDED 明细：refunded_qty / refunded_amount 回填
UPDATE `order_item` oi
JOIN `aftersale` a ON a.`order_item_id` = oi.`id`
SET oi.`refunded_qty` = oi.`quantity`,
    oi.`refunded_amount` = a.`approved_amount`
WHERE a.`aftersale_no` = 'AS20260924000003'
  AND oi.`refunded_amount` = 0;

-- 5.5 REFUNDED 支付单：pay_status / refund_amount 回填
UPDATE `payment` p
JOIN `aftersale` a ON a.`order_id` = p.`order_id`
SET p.`pay_status` = 'REFUNDED',
    p.`refund_amount` = p.`refund_amount` + a.`approved_amount`,
    p.`refund_time` = a.`refund_time`
WHERE a.`aftersale_no` = 'AS20260924000003'
  AND p.`pay_status` = 'SUCCESS';
