-- V10 修复 order_item 订单快照 image 字段（约束 4.3：快照必须包含商品图片）
-- 背景：ProductSku.image 全 NULL（所有 SKU 共用 product.main_image），
--      历史订单创建时 oi.setImage(sku.getImage()) 拿到 NULL，
--      导致订单列表/详情页商品图全灰块。
-- 策略：按 product_id 回填 product.main_image，存量 18 条。
UPDATE order_item AS oi
INNER JOIN product AS p ON oi.product_id = p.id AND p.is_deleted = 0
SET oi.image = p.main_image
WHERE oi.is_deleted = 0
  AND (oi.image IS NULL OR oi.image = '');
