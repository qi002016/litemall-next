-- ============================================================
-- 03 · 唯一键（阶段三）· 2026-11-01 已执行
-- 前置校验：以下 5 条查重必须全为 0，否则 ADD UNIQUE 会失败
-- ============================================================

-- P-1 预检（执行前跑过，结果全 0）
-- SELECT 'brand.name',                          COUNT(*) FROM (SELECT name FROM litemall_brand GROUP BY name HAVING COUNT(*)>1) a
-- UNION ALL SELECT 'category(pid,name)',        COUNT(*) FROM (SELECT pid,name FROM litemall_category GROUP BY pid,name HAVING COUNT(*)>1) b
-- UNION ALL SELECT 'spec(goods_id,spec,value)', COUNT(*) FROM (SELECT goods_id,specification,value FROM litemall_goods_specification GROUP BY goods_id,specification,value HAVING COUNT(*)>1) c
-- UNION ALL SELECT 'coupon_user(coupon,user)',  COUNT(*) FROM (SELECT coupon_id,user_id FROM litemall_coupon_user GROUP BY coupon_id,user_id HAVING COUNT(*)>1) d
-- UNION ALL SELECT 'comment.order_goods_id',    COUNT(*) FROM (SELECT order_goods_id FROM litemall_comment WHERE order_goods_id IS NOT NULL GROUP BY order_goods_id HAVING COUNT(*)>1) e;
--
-- P-2 SKU 组合查重（回填后跑，结果 0）
-- SELECT COUNT(*) FROM (SELECT goods_id,spec_value_ids,COUNT(*) c FROM litemall_goods_product WHERE deleted=0 GROUP BY goods_id,spec_value_ids HAVING c>1) x;

-- 7 个唯一键（全部执行成功，2026-11-01）
ALTER TABLE `litemall_goods_product`
  ADD UNIQUE KEY `uk_goods_product_sku_code` (`sku_code`),
  ADD UNIQUE KEY `uk_goods_product_combo`    (`goods_id`,`spec_value_ids`);

ALTER TABLE `litemall_comment`             ADD UNIQUE KEY `uk_comment_order_goods` (`order_goods_id`);
ALTER TABLE `litemall_goods_specification`  ADD UNIQUE KEY `uk_goods_specification_spec` (`goods_id`,`specification`,`value`);
ALTER TABLE `litemall_brand`                ADD UNIQUE KEY `uk_brand_name` (`name`);
ALTER TABLE `litemall_category`             ADD UNIQUE KEY `uk_category_name` (`pid`,`name`);
ALTER TABLE `litemall_coupon_user`          ADD UNIQUE KEY `uk_coupon_user` (`coupon_id`,`user_id`);

-- 注：`litemall_order` 的 `uk_order_sn (order_sn)` 为**存量已有**，本脚本不重复创建（Q14 实测结论）。
-- 注：普通索引里的 `idx_og_order (order_id)` 与 `litemall_order_goods` 既有 `KEY order_id` 同列，
--     执行后已 DROP 该冗余索引；故普通索引净增 9 条（10 条 DDL − 1 条冗余）。