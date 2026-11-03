-- ====================================================================
-- V17: community=2 绿地香颂备货补齐 —— merchant01 生鲜 SKU 对称
-- ====================================================================

INSERT INTO stock (sku_id, community_id, pickup_point_id, total_qty, locked_qty, available_qty, warn_qty, version, create_time, update_time, is_deleted)
SELECT
    c1.sku_id,
    2 AS community_id,
    3 AS pickup_point_id,
    0 AS total_qty,
    0 AS locked_qty,
    0 AS available_qty,
    c1.warn_qty,
    0 AS version,
    NOW(),
    NOW(),
    0 AS is_deleted
FROM stock c1
WHERE c1.community_id = 1
  AND c1.is_deleted = 0
  AND c1.sku_id IN (3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
  AND NOT EXISTS (
    SELECT 1 FROM stock c2
    WHERE c2.sku_id = c1.sku_id
      AND c2.community_id = 2
      AND c2.is_deleted = 0
  );

SELECT ROW_COUNT() AS inserted_rows;