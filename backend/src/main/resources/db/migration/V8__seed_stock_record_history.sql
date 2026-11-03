-- ============================================================
-- V8__seed_stock_record_history.sql
-- 库存流水历史模拟数据（近 30 天），用于「库存可视化 · 近 30 天出入库趋势」
--
-- 依据：约束文档 13.3 测试数据必须使用模拟数据 / 可重复生成
-- 口径：入库 SUPPLY_IN（正数流水）、出库 DEDUCT（负数流水）
--       before_total / after_total 通过窗口函数从当前库存值回推，
--       保证序列最后一行 after_total == stock.total_qty（与真实库存对账一致）
-- 注意：只写 stock_record 流水账，不修改 stock 现有库存值
-- ============================================================

SET NAMES utf8mb4;

USE `community_group_buy`;

INSERT INTO `stock_record`
  (`stock_id`, `sku_id`, `community_id`, `change_type`, `change_qty`,
   `before_total`, `after_total`, `before_locked`, `after_locked`,
   `order_no`, `remark`, `operator_id`, `create_time`)
SELECT f.stock_id, f.sku_id, f.community_id, f.change_type, f.change_qty,
       GREATEST(0, s.total_qty - SUM(f.change_qty) OVER (
           PARTITION BY f.stock_id ORDER BY f.day_offset, f.seq
           ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING)) AS before_total,
       GREATEST(0, s.total_qty - SUM(f.change_qty) OVER (
           PARTITION BY f.stock_id ORDER BY f.day_offset, f.seq
           ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) + f.change_qty) AS after_total,
       0, 0,
       NULL, f.remark, NULL, f.ts
FROM (
    -- 入库（每天 08:00）
    SELECT s2.id AS stock_id, s2.sku_id, s2.community_id,
           v.i AS day_offset, 0 AS seq,
           'SUPPLY_IN' AS change_type,
           GREATEST(1, FLOOR(s2.total_qty / 20)) + MOD(s2.sku_id + v.i, 4) AS change_qty,
           '历史供应到货入库（模拟数据）' AS remark,
           DATE_SUB(CURDATE(), INTERVAL (30 - v.i) DAY) + INTERVAL 8 HOUR AS ts
    FROM stock s2
    JOIN (
        SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
        UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
        UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
        UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29
    ) v ON 1 = 1
    UNION ALL
    -- 出库（每天 17:00），净变动受控在 ±1/天，避免回推出现负值
    SELECT s2.id, s2.sku_id, s2.community_id,
           v.i, 1,
           'DEDUCT',
           -(GREATEST(1, FLOOR(s2.total_qty / 20)) + MOD(s2.sku_id + v.i, 4) - (MOD(s2.sku_id, 3) - 1)),
           '历史销售出库（模拟数据）',
           DATE_SUB(CURDATE(), INTERVAL (30 - v.i) DAY) + INTERVAL 17 HOUR
    FROM stock s2
    JOIN (
        SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
        UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
        UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
        UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29
    ) v ON 1 = 1
) f
JOIN stock s ON s.id = f.stock_id;
