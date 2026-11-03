-- ============================================================
-- V10__fix_stock_record_before_after.sql
-- 修复历史库存流水「前后值」错误（旧版 StockService.writeRecord 的 bug 遗留）
--
-- 背景：旧实现写入 stock_record 时，before_* 与 after_* 都取自变更前的同一份
--       stock 快照，导致 51 行真实流水的「前后值」恒等，无法反映本次变更。
--       代码侧已在 v1.13 修复（更新后重读 stock 作为 after 快照），此处回填历史数据。
--
-- 口径（与 StockChangeTypeConstant 一致）：
--   total_qty 变化：DEDUCT / SUPPLY_IN / INBOUND / ROLLBACK → 等于 change_qty
--                  LOCK / UNLOCK → 不变（只动预占）
--   locked_qty 变化：LOCK / UNLOCK / DEDUCT → 等于 change_qty
--                   其余类型 → 不变
--
-- 幂等：仅更新"当前 after 值与 before + 变化量不一致"的行，重复执行不会二次修改。
-- 保护：预占修正带非负守卫（before_locked + change_qty >= 0），
--       避免把 V8 模拟流水（其 change_qty 大于当行预占值）改成负数。
-- ============================================================

SET NAMES utf8mb4;

USE `community_group_buy`;

-- 1) 修正在库总数前后值
UPDATE `stock_record`
SET `after_total` = `before_total` + `change_qty`
WHERE `is_deleted` = 0
  AND `change_type` IN ('DEDUCT', 'SUPPLY_IN', 'INBOUND', 'ROLLBACK')
  AND `change_qty` <> 0
  AND `after_total` <> `before_total` + `change_qty`;

-- 2) 修正预占数量前后值
UPDATE `stock_record`
SET `after_locked` = `before_locked` + `change_qty`
WHERE `is_deleted` = 0
  AND `change_type` IN ('LOCK', 'UNLOCK', 'DEDUCT')
  AND `change_qty` <> 0
  AND `after_locked` <> `before_locked` + `change_qty`
  AND `before_locked` + `change_qty` >= 0;
