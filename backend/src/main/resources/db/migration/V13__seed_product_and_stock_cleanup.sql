-- V13 种子数据清理：product_name 去规格冗余 + 补 community_id=2 stock 行 + 修复农夫山泉 spec 字段
-- ======================================================

-- ① 清 product_name 里混入的规格描述
UPDATE product SET product_name = '蓝月亮洗衣液'        WHERE id = 8  AND product_name = '蓝月亮洗衣液 3kg';
UPDATE product SET product_name = '清风原木抽纸'          WHERE id = 9  AND product_name = '清风抽纸 24包';
UPDATE product SET product_name = '金龙鱼调和油'          WHERE id = 10 AND product_name = '金龙鱼调和油 5L';
UPDATE product SET product_name = '香雪高筋面粉'         WHERE id = 11 AND product_name = '香雪高筋面粉 5kg';
UPDATE product SET product_name = '海天酿造酱油'         WHERE id = 12 AND product_name = '海天酿造酱油 1L';
UPDATE product SET product_name = '农夫山泉天然水'       WHERE id = 14 AND product_name = '农夫山泉 24瓶';

-- ② 修复农夫山泉 spec 字段（当前是 '550ml*24瓶'，不规范）
UPDATE product_sku SET spec = '550ml*24瓶/箱' WHERE id = 20 AND spec = '550ml*24瓶';
UPDATE product_sku SET spec = '500ml*24瓶/箱' WHERE id = 20 AND spec = '24瓶/箱';  -- 兜底

-- ③ 补 community_id=2（绿地香颂）ON_SHELF SKU 的库存行
-- 比 community_id=1 略少，模拟真实差异
SET @base_stock_id := COALESCE((SELECT MAX(id) FROM stock), 0);

INSERT INTO stock (id, sku_id, community_id, pickup_point_id, total_qty, locked_qty, available_qty, warn_qty, version, create_time, update_time, is_deleted) VALUES
(@base_stock_id := @base_stock_id + 1, 1,  2, 3, 380, 15, 365, 25, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 2,  2, 3,  90,  8,  82, 15, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 13, 2, 3, 210, 14, 196, 30, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 14, 2, 3, 175, 10, 165, 25, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 15, 2, 3, 140, 12, 128, 20, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 16, 2, 3, 125,  8, 117, 20, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 17, 2, 3, 240, 14, 226, 30, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 18, 2, 3, 270, 20, 250, 40, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 19, 2, 3, 105,  6,  99, 15, 1, NOW(), NOW(), 0),
(@base_stock_id := @base_stock_id + 1, 20, 2, 3, 340, 15, 325, 45, 1, NOW(), NOW(), 0);

-- ④ product_sku_attribute 缺品牌/保质期/重量等维度（约束文档 4.5 要求规格外应有品牌、保质期等）
-- 当前每条 sku 只有 attr_key='规格' 一条，补品牌 + 保质期
INSERT INTO product_sku_attribute (sku_id, attr_name, attr_value, create_time, update_time, is_deleted) VALUES
-- sku 13 蓝月亮 3kg
(13, '品牌', '蓝月亮',      NOW(), NOW(), 0),
(13, '保质期', '3年',       NOW(), NOW(), 0),
-- sku 14 清风抽纸
(14, '品牌', '清风',        NOW(), NOW(), 0),
(14, '保质期', '3年',       NOW(), NOW(), 0),
-- sku 15 金龙鱼 5L
(15, '品牌', '金龙鱼',      NOW(), NOW(), 0),
(15, '保质期', '18个月',    NOW(), NOW(), 0),
-- sku 16 香雪面粉 5kg
(16, '品牌', '香雪',        NOW(), NOW(), 0),
(16, '保质期', '12个月',    NOW(), NOW(), 0),
-- sku 17 海天酱油 1L
(17, '品牌', '海天',        NOW(), NOW(), 0),
(17, '保质期', '2年',       NOW(), NOW(), 0),
-- sku 18/19 奥利奥
(18, '品牌', '奥利奥',      NOW(), NOW(), 0),
(18, '保质期', '9个月',     NOW(), NOW(), 0),
(19, '品牌', '奥利奥',      NOW(), NOW(), 0),
(19, '保质期', '9个月',     NOW(), NOW(), 0),
-- sku 20 农夫山泉
(20, '品牌', '农夫山泉',    NOW(), NOW(), 0),
(20, '保质期', '12个月',    NOW(), NOW(), 0),
-- sku 1/2 有机青菜
(1,  '品牌', '本地有机蔬菜', NOW(), NOW(), 0),
(1,  '保质期', '3-5天',     NOW(), NOW(), 0),
(2,  '品牌', '本地有机蔬菜', NOW(), NOW(), 0),
(2,  '保质期', '3-5天',     NOW(), NOW(), 0);
