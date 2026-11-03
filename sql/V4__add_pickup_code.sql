-- V4: 自提功能补全 — 订单表加自提核销码字段
-- 约束文档 5.2 订单状态机 PENDING_PICKUP 配套

ALTER TABLE `orders`
  ADD COLUMN `pickup_code` VARCHAR(32) DEFAULT NULL COMMENT '自提核销码，支付成功后为自提订单生成'
  AFTER `pay_status`;

-- 给历史已支付/履约中订单补生成自提码（方便演示）
UPDATE orders
SET pickup_code = CONCAT('ZT', RIGHT(order_no, 6), LPAD(FLOOR(RAND() * 100), 2, '0'))
WHERE pickup_code IS NULL
  AND pickup_point_id IS NOT NULL
  AND order_status IN ('PAID', 'FULFILLING', 'PENDING_PICKUP');
