-- ====================================================================
-- V14: 跨商家脏订单修复 —— 约束文档 §8 数据隔离
-- ====================================================================
-- 背景：原始 OrderService.create() 只取第一个 item 的 merchantId 写入
-- orders.merchant_id，导致一个购物车里选了多个商家的商品时，生成 1 笔
-- orders 记录但混有多个商家的 order_item，破坏了订单-商家严格一对一。
--
-- 修复策略：
--   1. 查出所有跨商家脏订单
--   2. 把 orders.merchant_id 置为 NULL（标记历史脏数据，无法准确归属单一商家）
--   3. 未来不会再产生（OrderService.create() 已重构为按 merchantId 拆单）
--   4. 查询端（listByMerchant/detail）已按 item 归属过滤，GMV 统计也已改
--      为按 item 归属聚合（见 AnalyticsService.calcGmv 修改）
--
-- 影响范围：仅 1 笔脏订单（审计确认）
-- 关联表：orders（主）、order_item（只读，不修改）、
--         payment / order_status_log / fulfillment（只读，保留原引用）
-- ====================================================================

-- Step 1: 诊断（dry-run 确认脏订单清单，可忽略）
SELECT '=== 跨商家脏订单清单 ===' AS info;
SELECT
    o.id,
    o.order_no,
    o.merchant_id AS current_merchant_id,
    o.total_amount,
    (SELECT GROUP_CONCAT(DISTINCT p.merchant_id ORDER BY p.merchant_id)
     FROM order_item oi2
     JOIN product_sku sk2 ON sk2.id = oi2.sku_id
     JOIN product p ON p.id = sk2.product_id
     WHERE oi2.order_id = o.id AND oi2.is_deleted = 0
       AND sk2.is_deleted = 0 AND p.is_deleted = 0) AS item_merchant_ids,
    (SELECT COUNT(DISTINCT p.merchant_id)
     FROM order_item oi2
     JOIN product_sku sk2 ON sk2.id = oi2.sku_id
     JOIN product p ON p.id = sk2.product_id
     WHERE oi2.order_id = o.id AND oi2.is_deleted = 0
       AND sk2.is_deleted = 0 AND p.is_deleted = 0) AS distinct_merchant_cnt
FROM orders o
WHERE o.is_deleted = 0
  AND EXISTS (
    SELECT 1 FROM order_item oi
    JOIN product_sku sk ON sk.id = oi.sku_id
    JOIN product p ON p.id = sk.product_id
    WHERE oi.order_id = o.id AND oi.is_deleted = 0
      AND sk.is_deleted = 0 AND p.is_deleted = 0
    HAVING COUNT(DISTINCT p.merchant_id) > 1
  );

-- Step 2: 修复 —— 将跨商家脏订单的 merchant_id 置 NULL
--     语义说明：orders.merchant_id = NULL 表示这是历史遗留的脏数据，
--     查询端（listByMerchant/detail/Analytics）已改用 item 归属过滤，
--     不依赖 orders.merchant_id 字段。
UPDATE orders o
SET o.merchant_id = NULL,
    o.update_time = NOW()
WHERE o.is_deleted = 0
  AND EXISTS (
    SELECT 1 FROM order_item oi
    JOIN product_sku sk ON sk.id = oi.sku_id
    JOIN product p ON p.id = sk.product_id
    WHERE oi.order_id = o.id AND oi.is_deleted = 0
      AND sk.is_deleted = 0 AND p.is_deleted = 0
    HAVING COUNT(DISTINCT p.merchant_id) > 1
  );

-- Step 3: 验证修复结果
SELECT CONCAT('修复完成：已更新 ', ROW_COUNT(), ' 笔跨商家脏订单的 merchant_id 为 NULL') AS info;
SELECT
    o.id,
    o.order_no,
    o.merchant_id AS fixed_merchant_id,
    o.order_status
FROM orders o
WHERE o.id IN (SELECT o2.id FROM orders o2
               JOIN order_item oi ON oi.order_id = o2.id
               JOIN product_sku sk ON sk.id = oi.sku_id
               JOIN product p ON p.id = sk.product_id
               WHERE o2.is_deleted = 0 AND oi.is_deleted = 0
                 AND sk.is_deleted = 0 AND p.is_deleted = 0
               GROUP BY o2.id
               HAVING COUNT(DISTINCT p.merchant_id) > 1);
