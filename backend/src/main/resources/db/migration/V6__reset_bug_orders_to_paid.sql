-- V6 重置所有 bug PENDING_PICKUP 订单（无时间条件，直接全匹配 bug 特征：
--   PENDING_PICKUP + fulfillment.create_time 就是 PaymentService 自动 assign 时写入的
--   所有 PENDING_PICKUP 里 fulfillment 状态 = PENDING_PICKUP 的（不是 COMPLETED/PICKED_UP 的）都是 bug）

DROP TEMPORARY TABLE IF EXISTS _bug_orders;
CREATE TEMPORARY TABLE _bug_orders AS
SELECT o.id, o.order_no
FROM orders o
JOIN fulfillment f ON f.order_id = o.id
WHERE o.order_status = 'PENDING_PICKUP'
  AND f.fulfillment_type = 'PICKUP'
  AND f.fulfillment_status = 'PENDING_PICKUP';

SELECT COUNT(*) AS bug_orders_to_reset FROM _bug_orders;

UPDATE order_status_log SET is_deleted = 1
WHERE order_id IN (SELECT id FROM _bug_orders)
  AND to_status IN ('PENDING_PICKUP','PROCESSING','FULFILLING');

UPDATE fulfillment f
JOIN _bug_orders b ON f.order_id = b.id
SET f.fulfillment_status = 'PENDING_ASSIGN',
    f.assign_time = NULL,
    f.pickup_time = NULL,
    f.update_time = NOW();

UPDATE orders o
JOIN _bug_orders b ON o.id = b.id
SET o.order_status = 'PAID',
    o.fulfillment_status = NULL,
    o.update_time = NOW();

INSERT INTO order_status_log (order_id, order_no, from_status, to_status, operator_type, operator_id, reason, create_time)
SELECT b.id, b.order_no, 'PENDING_PICKUP', 'PAID', 'SYSTEM', 0, 'v1.13 bug 修复：直通 PENDING_PICKUP，重置回 PAID 等待商家重新 assign', NOW()
FROM _bug_orders b;

DROP TEMPORARY TABLE IF EXISTS _bug_orders;