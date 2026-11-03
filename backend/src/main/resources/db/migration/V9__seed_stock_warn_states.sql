-- ============================================================
-- V9__seed_stock_warn_states.sql
-- 库存预警状态模拟数据：让「预警 / 空仓 / 正常」三种口径在页面上均可观察
--
-- 依据：约束文档 13.3 测试数据必须使用模拟数据
-- 背景：V3 种子数据中所有 stock.warn_qty 均非空且 available_qty > warn_qty，
--       预警状态恒为 NORMAL，「安全阈值红灯预警 / 空仓」在页面上无法体现。
--
-- 口径（与 StockWarnStatusConstant 完全一致）：
--   WARN   预警   warn_qty 非空 且 available_qty <= warn_qty
--   EMPTY  空仓   warn_qty 为空 且 available_qty = 0
--   NORMAL 正常   其余情况
--   注：locked_qty > 0 是列表的独立筛选维度（lockedOnly），本次不调整
--
-- 幂等：按 stock.id 定点更新；空仓调整配套写一条 INBOUND 负数流水，
--       保证 stock_record 账目链与 stock.total_qty 依然对账一致
-- ============================================================

SET NAMES utf8mb4;

USE `community_group_buy`;

-- 1) 预警：把安全阈值抬到当前可用量之上
--    只改 warn_qty，不动库存数量，因此不产生流水、不影响账目链
UPDATE `stock` SET `warn_qty` = `available_qty` + 10, `update_time` = NOW()
WHERE `id` = 12 AND `is_deleted` = 0;

UPDATE `stock` SET `warn_qty` = `available_qty` + 15, `update_time` = NOW()
WHERE `id` = 10 AND `is_deleted` = 0;

-- 2) 空仓：先把可用库存清零这一步登记成流水（临期报损），保持账目链连续
--    change_qty = -available_qty，after_total = total_qty - available_qty = locked_qty
INSERT INTO `stock_record`
  (`stock_id`, `sku_id`, `community_id`, `change_type`, `change_qty`,
   `before_total`, `after_total`, `before_locked`, `after_locked`,
   `order_no`, `remark`, `operator_id`, `create_time`)
SELECT s.id, s.sku_id, s.community_id, 'INBOUND', -s.available_qty,
       s.total_qty, s.total_qty - s.available_qty, s.locked_qty, s.locked_qty,
       NULL, '模拟数据：临期报损清仓，可用库存置零', 3, NOW()
FROM `stock` s
WHERE s.id = 9 AND s.is_deleted = 0 AND s.available_qty > 0;

-- 3) 空仓：未设置安全阈值 + 可用量为 0，清空 warn_qty 以匹配 EMPTY 口径
UPDATE `stock` SET `warn_qty` = NULL, `total_qty` = `locked_qty`,
       `available_qty` = 0, `version` = `version` + 1, `update_time` = NOW()
WHERE `id` = 9 AND `is_deleted` = 0;
