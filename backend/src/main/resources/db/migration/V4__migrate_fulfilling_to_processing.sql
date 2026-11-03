-- v1.13 订单状态机重构：FULFILLING → PROCESSING
--
-- 背景：FULFILLING（履约中）语义模糊——商家/消费者看到后不知道订单是已发货、
--       已分配履约、还是在备货。统一为 PROCESSING（处理中），暗示"商家已分配
--       履约，正在备货/发货/运输"。
--
-- 迁移范围：
--   ① orders 表：order_status 列的 FULFILLING → PROCESSING
--   ② order_status_log 表：from_status / to_status 列的 FULFILLING → PROCESSING
--   ③ 注意：fulfillment 表的 fulfillment_status 列**不动**（那是履约自身的状态）
--
-- 兼容：OrderStatusConstant.label() 对 FULFILLING 旧值仍返回"处理中"，
--       即使边缘 case 还有 FULFILLING 值也能正确显示。

UPDATE orders
SET order_status = 'PROCESSING',
    update_time = NOW()
WHERE order_status = 'FULFILLING';

UPDATE order_status_log
SET from_status = 'PROCESSING'
WHERE from_status = 'FULFILLING';

UPDATE order_status_log
SET to_status = 'PROCESSING'
WHERE to_status = 'FULFILLING';

-- 迁移结果：0 行或多行取决于历史数据（预期当前为 0，因为 seed 没塞 FULFILLING）