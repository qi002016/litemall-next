-- ============================================================
-- 02 · 回填（阶段二，可重复执行、幂等）
-- 前置：01-add-columns.sql 已执行
-- 实测结果（2026-11-01）：spec_value_ids 244 行、sku_code 244 行、order_goods.actual_amount 2 行
-- ============================================================

-- B-01 SKU 维度值 ID 串：把 specifications(JSON 值数组) 映射为 litemall_goods_specification.id 升序串
UPDATE litemall_goods_product p
JOIN (
  SELECT p2.id, GROUP_CONCAT(DISTINCT s.id ORDER BY s.id) ids
  FROM litemall_goods_product p2
  JOIN litemall_goods_specification s
    ON s.goods_id = p2.goods_id AND s.deleted = 0
   AND JSON_VALID(p2.specifications)
   AND JSON_CONTAINS(p2.specifications, JSON_QUOTE(s.value))
  WHERE p2.deleted = 0
  GROUP BY p2.id
) t ON t.id = p.id
SET p.spec_value_ids = t.ids
WHERE p.deleted = 0;

-- B-02 SKU 稳定业务码（确定性、可用同一规则重算/校验）
UPDATE litemall_goods_product
SET sku_code = CONCAT(goods_id, '-', REPLACE(COALESCE(NULLIF(spec_value_ids, ''), 'D'), ',', '_'))
WHERE deleted = 0;

-- B-03 订单行：行实付 = 单价 × 数量（存量无券分摊 → coupon_share = 0）
UPDATE litemall_order_goods
SET actual_amount = ROUND(price * number, 2), coupon_share = 0.00
WHERE deleted = 0;

-- B-04 订单行 SKU 快照（由 product_id 反查）
UPDATE litemall_order_goods og
JOIN litemall_goods_product p ON p.id = og.product_id
SET og.sku_code = p.sku_code
WHERE og.sku_code IS NULL;

-- B-05 存量未付款订单补关单时刻（下单 + 30 分钟，BR-ORDER-06 / D1）
UPDATE litemall_order
SET expire_time = DATE_ADD(add_time, INTERVAL 30 MINUTE)
WHERE order_status = 101 AND expire_time IS NULL;

-- B-06 商品状态由 is_on_sale 派生（0 草稿 / 1 在售 / 2 已下架）
UPDATE litemall_goods SET status = CASE WHEN is_on_sale = 1 THEN 1 ELSE 2 END;