-- ====================================================================
-- V18: 自提码统一为 8 位纯数字（移除 ZT 前缀）
-- ====================================================================
-- 背景：历史订单自提码格式为 "ZT" + 8位数字 = 10 位
--       规范要求统一 8 位纯数字。PaymentService.java 已同步修改。
--
-- 影响：orders.pickup_code（Fulfillment 是 join 查 orders，无独立存储）
-- 策略：直接 REPLACE 去掉 "ZT" 前缀，结果天然是 8 位数字
-- ====================================================================

SELECT '=== V18 前: 格式分布 ===' AS info;
SELECT 
  CASE 
    WHEN pickup_code LIKE 'ZT%' THEN 'ZT 前缀（旧格式）'
    WHEN pickup_code REGEXP '^[0-9]{8}$' THEN '8 位纯数字（规范）'
    WHEN pickup_code IS NULL THEN 'NULL'
    ELSE '其他'
  END AS format_type,
  COUNT(*) AS cnt
FROM orders
WHERE is_deleted = 0
GROUP BY format_type;

UPDATE orders 
SET pickup_code = REPLACE(pickup_code, 'ZT', '')
WHERE pickup_code LIKE 'ZT%';

SELECT CONCAT('UPDATE 影响行数: ', ROW_COUNT()) AS info;

SELECT '=== V18 后: 验证 ===' AS info;
SELECT 
  CASE 
    WHEN pickup_code REGEXP '^[0-9]{8}$' THEN '8 位纯数字 ✅'
    ELSE CONCAT('异常: ', pickup_code)
  END AS format_type,
  COUNT(*) AS cnt
FROM orders
WHERE is_deleted = 0 AND pickup_code IS NOT NULL
GROUP BY format_type;