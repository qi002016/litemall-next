-- ============================================================
-- 01 · 加列（阶段一，纯增量、非破坏性）
-- 依据：docs/design/03-数据与接口变更方案.md  CD-01 ~ CD-09
-- 已核：库为 MySQL 8.0.46 / utf8mb4，存量行数少（goods 215 / product 244 / comment 1004）
-- 备份：.dbwork/backup-20261101-135455.sql（2.0MB / 37 表）
-- 说明：本阶段只加列与普通索引，唯一键全部放到 03-add-keys.sql（那里会先做重复校验）
-- ============================================================

-- CD-01 litemall_goods
ALTER TABLE `litemall_goods`
  ADD COLUMN `merchant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '归属商户，单商户期固定 1（BR-ADM-06）' AFTER `id`,
  ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0 草稿 / 1 在售 / 2 已下架（需求 5.1）' AFTER `is_on_sale`;

UPDATE `litemall_goods` SET `status` = CASE WHEN `is_on_sale` = 1 THEN 1 ELSE 2 END;

-- CD-02 litemall_goods_product（M-2 选 A：不加 locked_number）
ALTER TABLE `litemall_goods_product`
  ADD COLUMN `sku_code` VARCHAR(64) NULL DEFAULT NULL COMMENT 'SKU 稳定业务码（BR-GOODS-07）' AFTER `goods_id`,
  ADD COLUMN `spec_value_ids` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '维度值ID升序逗号串（BR-GOODS-02）' AFTER `specifications`;

-- CD-03 litemall_ad
ALTER TABLE `litemall_ad`
  ADD COLUMN `link_type` TINYINT NOT NULL DEFAULT 0 COMMENT '0 无跳转 / 1 商品 / 2 专题（BR-CMS-02）' AFTER `link`;

-- CD-04 litemall_comment
ALTER TABLE `litemall_comment`
  ADD COLUMN `order_id` BIGINT NULL COMMENT '来源订单（BR-CM-01）' AFTER `value_id`,
  ADD COLUMN `order_goods_id` BIGINT NULL COMMENT '来源订单行，唯一（BR-CM-01）' AFTER `order_id`,
  ADD COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 显示 / 1 已隐藏（BR-CM-04）' AFTER `star`,
  ADD COLUMN `reply_time` DATETIME NULL COMMENT '商家回复时间（BR-CM-03）' AFTER `admin_content`;

-- CD-05 litemall_order
ALTER TABLE `litemall_order`
  ADD COLUMN `merchant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '归属商户，单商户期固定 1（BR-ADM-06）' AFTER `id`,
  ADD COLUMN `expire_time` DATETIME NULL COMMENT '未付款自动关闭时刻=下单时间+30min（BR-ORDER-06）' AFTER `order_status`,
  ADD COLUMN `close_type` TINYINT NULL COMMENT '1 用户取消 / 2 超时 / 3 运营立即关单（Q8）' AFTER `end_time`,
  ADD COLUMN `user_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'C 端本人隐藏（BR-ORD-07）' AFTER `close_type`;

-- CD-06 litemall_order_goods
ALTER TABLE `litemall_order_goods`
  ADD COLUMN `sku_code` VARCHAR(64) NULL COMMENT 'SKU 稳定码快照（BR-GOODS-07）' AFTER `product_id`,
  ADD COLUMN `actual_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '行实付=行商品金额-分摊券额（7.2/D9）' AFTER `price`,
  ADD COLUMN `coupon_share` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该行分摊券额（D9）' AFTER `actual_amount`,
  ADD COLUMN `refunded_number` INT NOT NULL DEFAULT 0 COMMENT '已退数量（BR-AS-02）' AFTER `number`,
  ADD COLUMN `refund_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 未退 / 1 部分退 / 2 全退（BR-AS-17）' AFTER `refunded_number`;

-- CD-07 litemall_aftersale（Q3 选①：加 2 列；另按售后全流程补 8 列）
ALTER TABLE `litemall_aftersale`
  ADD COLUMN `order_goods_id` BIGINT NULL COMMENT '订单行（BR-AS-01）' AFTER `order_id`,
  ADD COLUMN `number` INT NOT NULL DEFAULT 0 COMMENT '申请退款数量（BR-AS-02）' AFTER `type`,
  ADD COLUMN `return_ship_company` VARCHAR(64) NULL COMMENT '退货物流公司（BR-AS-08）' AFTER `pictures`,
  ADD COLUMN `return_ship_sn` VARCHAR(64) NULL COMMENT '退货物流单号（BR-AS-08）' AFTER `return_ship_company`,
  ADD COLUMN `return_ship_time` DATETIME NULL COMMENT '用户填单号时间（BR-AS-08）' AFTER `return_ship_sn`,
  ADD COLUMN `receive_time` DATETIME NULL COMMENT '运营确认收到退货时间（BR-AS-08/09）' AFTER `return_ship_time`,
  ADD COLUMN `close_reason` VARCHAR(255) NULL COMMENT '关闭/驳回原因（BR-AS-10/22）' AFTER `comment`,
  ADD COLUMN `audit_admin` BIGINT NULL COMMENT '审核操作人 admin.id（BR-ADM-03）' AFTER `close_reason`,
  ADD COLUMN `restock_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0 未回补 / 1 已回补 / 2 待回补（BR-AS-12/16）' AFTER `status`,
  ADD COLUMN `return_deadline` DATETIME NULL COMMENT '待寄回截止=同意+7天（BR-AS-10）' AFTER `restock_status`;

-- CD-08 litemall_refund
ALTER TABLE `litemall_refund`
  ADD COLUMN `aftersale_id` BIGINT NULL COMMENT '关联售后单（BR-AS-23）' AFTER `order_id`,
  ADD COLUMN `refund_time` DATETIME NULL COMMENT '退款完成时间（BR-RPT-05）' AFTER `status`;

-- CD-09 litemall_log
ALTER TABLE `litemall_log`
  ADD COLUMN `target_type` VARCHAR(32) NULL COMMENT '操作对象类型（BR-ADM-03）' AFTER `action`,
  ADD COLUMN `target_id` BIGINT NULL COMMENT '操作对象 ID（BR-ORDER-13）' AFTER `target_type`;

-- ------------------------------------------------------------
-- 普通索引（10 条，唯一键见 03-add-keys.sql）
-- ------------------------------------------------------------
ALTER TABLE `litemall_order`     ADD KEY `idx_order_expire` (`order_status`,`expire_time`);
ALTER TABLE `litemall_order`     ADD KEY `idx_order_user` (`user_id`,`user_deleted`);
-- 【执行后修正】`idx_og_order (order_id)` 与 `litemall_order_goods` 既有 `KEY order_id` 同列，
-- 已执行并随后 DROP，故保留为注释（普通索引净增 9 条）。
-- ALTER TABLE `litemall_order_goods` ADD KEY `idx_og_order` (`order_id`);
ALTER TABLE `litemall_order_goods` ADD KEY `idx_og_sku` (`sku_code`);
ALTER TABLE `litemall_aftersale` ADD KEY `idx_as_order_goods` (`order_goods_id`,`status`);
ALTER TABLE `litemall_aftersale` ADD KEY `idx_as_deadline` (`status`,`return_deadline`);
ALTER TABLE `litemall_refund`    ADD KEY `idx_refund_aftersale` (`aftersale_id`);
ALTER TABLE `litemall_log`       ADD KEY `idx_log_target` (`target_type`,`target_id`);
ALTER TABLE `litemall_comment`   ADD KEY `idx_comment_value` (`value_id`,`type`);
ALTER TABLE `litemall_cart`      ADD KEY `idx_cart_user_product` (`user_id`,`product_id`);