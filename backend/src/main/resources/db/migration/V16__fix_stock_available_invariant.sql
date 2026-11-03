-- ====================================================================
-- V16: 库存不变量修复 —— available_qty = total_qty - locked_qty
-- ====================================================================
-- 背景：
--   1. StockMapper.lockStock / unlockStock 历史 bug：只改 locked_qty 不改 available_qty
--      下单→锁库存但"可用"没减少；取消→释放但"可用"没增加
--   2. V15 迁移副作用：GREATEST(..., 0) 把 total/locked 截到 0 但没同步 available
--      造成 20 行 total=0, locked=0, available=原值（幽灵可用库存 4825 个）
--   3. 预警口径依赖 available_qty，available 不准 → 预警/空仓判断全错
--
-- 修复策略：一条 UPDATE 重建全表不变量
--   available_qty = GREATEST(total_qty - locked_qty, 0)
--   用 GREATEST 兜底防止负数（V15 后部分行 total < locked）
-- ====================================================================

SELECT '=== V16 修复前 ===' AS info;
SELECT COUNT(*) AS total_rows,
       SUM(CASE WHEN available_qty != (total_qty - locked_qty) THEN 1 ELSE 0 END) AS inconsistent_rows,
       SUM(CASE WHEN total_qty = 0 AND available_qty > 0 THEN 1 ELSE 0 END) AS phantom_available_rows,
       SUM(CASE WHEN total_qty = 0 AND available_qty > 0 THEN available_qty ELSE 0 END) AS phantom_available_qty
FROM stock WHERE is_deleted = 0;

-- 一次性修复全表
UPDATE stock SET
    available_qty = GREATEST(total_qty - locked_qty, 0),
    update_time = NOW()
WHERE is_deleted = 0;

SELECT CONCAT('UPDATE 影响行数: ', ROW_COUNT()) AS info;

SELECT '=== V16 修复后 ===' AS info;
SELECT COUNT(*) AS total_rows,
       SUM(CASE WHEN available_qty != (total_qty - locked_qty) THEN 1 ELSE 0 END) AS inconsistent_rows,
       SUM(CASE WHEN total_qty = 0 AND available_qty > 0 THEN 1 ELSE 0 END) AS phantom_available_rows
FROM stock WHERE is_deleted = 0;
