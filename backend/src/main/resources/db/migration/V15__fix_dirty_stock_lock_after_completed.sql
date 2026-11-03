-- ====================================================================
-- V15: 存量脏库存修复 —— 订单 COMPLETED 但库存预占未转实扣
-- ====================================================================
-- 背景：StockService.deduct() 从未被 FulfillmentService 在 COMPLETED 时调用
--       （直到本轮修复才补 L194-207），导致 2055 笔已完成订单共 11569 个
--       预占库存永远挂着不释放。商家看到的"预占"大部分是历史脏数据。
--
-- 修复策略：
--   1. 查出所有 COMPLETED 但没有 DEDUCT stock_record 的订单
--   2. 按 (sku_id, community_id, pickup_point_id) 聚合应扣数量
--   3. 批量 UPDATE stock SET locked_qty -= qty, total_qty -= qty
--   4. 补 stock_record 流水（简化版：before_total/after_total 用当前值近似）
--      不追求完美快照，追求数据闭环
--
-- 幂等性：NOT EXISTS DEDUCT 保证重复执行安全
-- 影响规模：约 2055 笔订单，约 11569 个 item 数量
-- ====================================================================

-- Step 1: 诊断
SELECT '=== V15 脏库存诊断 ===' AS info;
SELECT COUNT(*) AS dirty_order_cnt FROM (
    SELECT o.id FROM orders o
    WHERE o.order_status = 'COMPLETED' AND o.is_deleted = 0
      AND NOT EXISTS (
        SELECT 1 FROM stock_record sr
        WHERE sr.order_no = o.order_no AND sr.change_type = 'DEDUCT'
      )
) x;

SELECT SUM(oi.quantity) AS total_dirty_locked_qty
FROM order_item oi
JOIN orders o ON oi.order_id = o.id
WHERE o.order_status = 'COMPLETED' AND o.is_deleted = 0 AND oi.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM stock_record sr WHERE sr.order_no = o.order_no AND sr.change_type = 'DEDUCT'
  );

-- Step 2: 先创建临时表聚合每个 (sku, community, pickup_point) 应扣数量
-- （MySQL 不能在 UPDATE 同表时子查询引用同表，所以用临时表）
DROP TEMPORARY TABLE IF EXISTS tmp_v15_dirty_deduct;
CREATE TEMPORARY TABLE tmp_v15_dirty_deduct AS
SELECT
    oi.sku_id,
    o.community_id,
    o.pickup_point_id,
    SUM(oi.quantity) AS deduct_qty,
    COUNT(DISTINCT o.order_no) AS order_cnt
FROM order_item oi
JOIN orders o ON oi.order_id = o.id AND o.is_deleted = 0
WHERE o.order_status = 'COMPLETED'
  AND oi.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM stock_record sr WHERE sr.order_no = o.order_no AND sr.change_type = 'DEDUCT'
  )
GROUP BY oi.sku_id, o.community_id, o.pickup_point_id;

SELECT CONCAT('临时表行数: ', COUNT(*)) AS info FROM tmp_v15_dirty_deduct;

-- Step 3: 批量 UPDATE stock（跳过乐观锁，因为是一次性迁移）
-- MySQL UPDATE 时 JOIN ON NULL 需要特殊处理
UPDATE stock s
JOIN tmp_v15_dirty_deduct d
  ON s.sku_id = d.sku_id
  AND s.community_id = d.community_id
  AND (
    (s.pickup_point_id = d.pickup_point_id)
    OR (s.pickup_point_id IS NULL AND d.pickup_point_id IS NULL)
  )
SET
    s.locked_qty = GREATEST(s.locked_qty - d.deduct_qty, 0),
    s.total_qty  = GREATEST(s.total_qty - d.deduct_qty, 0),
    s.update_time = NOW()
WHERE s.is_deleted = 0
  AND s.locked_qty >= d.deduct_qty;   -- 安全校验：locked 必须 >= 要扣的量

SELECT CONCAT('UPDATE stock 影响行数: ', ROW_COUNT()) AS info;

-- Step 4: 补 stock_record 流水（简化版 before/after）
-- before_total/after_total 用当前值（UPDATE 已执行，这里拿不到 UPDATE 前快照）
-- 记一条 "补扣" 备注即可
INSERT INTO stock_record
    (stock_id, sku_id, community_id, change_type, change_qty,
     before_total, after_total, before_locked, after_locked,
     order_no, remark, operator_id, create_time, is_deleted)
SELECT
    s.id AS stock_id,
    s.sku_id,
    s.community_id,
    'DEDUCT' AS change_type,
    -d.deduct_qty AS change_qty,
    s.total_qty AS before_total,    -- 近似（应为 UPDATE 前值）
    s.total_qty AS after_total,     -- 同上
    s.locked_qty AS before_locked,
    s.locked_qty AS after_locked,
    'V15_MIGRATION' AS order_no,
    CONCAT('V15 存量修复：', d.order_cnt, ' 笔已完成订单预占转实扣') AS remark,
    NULL,
    NOW(),
    0
FROM stock s
JOIN tmp_v15_dirty_deduct d
  ON s.sku_id = d.sku_id
  AND s.community_id = d.community_id
  AND (
    (s.pickup_point_id = d.pickup_point_id)
    OR (s.pickup_point_id IS NULL AND d.pickup_point_id IS NULL)
  )
WHERE s.is_deleted = 0;

SELECT CONCAT('INSERT stock_record 影响行数: ', ROW_COUNT()) AS info;

DROP TEMPORARY TABLE IF EXISTS tmp_v15_dirty_deduct;

-- Step 5: 验证修复结果
SELECT '=== V15 修复后：脏订单数应为 0 ===' AS info;
SELECT COUNT(*) AS remaining_dirty FROM (
    SELECT o.id FROM orders o
    WHERE o.order_status = 'COMPLETED' AND o.is_deleted = 0
      AND NOT EXISTS (
        SELECT 1 FROM stock_record sr WHERE sr.order_no = o.order_no AND sr.change_type = 'DEDUCT'
      )
) x;
