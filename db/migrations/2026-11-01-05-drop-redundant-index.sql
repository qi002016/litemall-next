-- =====================================================================
-- 2026-11-01-05 · 清理与既有索引重复的冗余索引（补记，回应评审 DR-27）
-- =====================================================================
-- 背景（如实记录，勿美化）
--   `-01-add-columns.sql` 里原本写了 `ADD KEY idx_og_order (order_id)`，
--   执行时发现 `litemall_order_goods` 上**已有**一个同列索引（建表自带的 `KEY order_id`），
--   两者完全重复。为避免浪费写放大，当场执行了 `DROP INDEX idx_og_order`。
--
--   问题：这次 DROP 当时是**手工执行**的，没有落成脚本，
--   导致「四个迁移脚本全跑一遍」与「当前库」对不上（脚本里没有 DROP，
--   而库里的 `idx_og_order` 已经不存在）——即评审所说的「执行产物不可复现」。
--   本文件即为该 DROP 的补记，使脚本集合可以重放出当前库结构。
--
-- 现状核对（2026-11-01 14:04 后的 schema-after 快照）
--   `litemall_order_goods` 上只剩 `KEY order_id`，`idx_og_order` 不存在。
--
-- 账目（评审 DR-27 要求的口径）
--   普通索引：**定义 10 条 → 实际净增 9 条**
--     - 保留（9）：idx_goods_status / idx_gp_sku / idx_comment_og / idx_comment_goods /
--                  idx_order_expire / idx_order_user / idx_as_order_goods /
--                  idx_refund_as / idx_log_admin
--     - 建后又删（1）：idx_og_order（与 `KEY order_id` 同列）
--   唯一键：定义 8 条，全部保留（7 条在 -03，`uk_admin_username` 在 -04）
--
-- 状态：**已执行**（2026-11-01，与 -01 同一次执行窗口内手工完成，此处补记）
-- 幂等：DROP INDEX 不幂等；若在别的环境重放，需先确认该索引存在
--       （见下方预检 SELECT，返回 0 行则跳过本文件）
-- =====================================================================

-- 【预检】确认 idx_og_order 存在（返回 0 行 = 已清理，可跳过本文件）
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'litemall'
  AND TABLE_NAME   = 'litemall_order_goods'
  AND INDEX_NAME   = 'idx_og_order';

-- 【预检】确认 order_id 上仍有可用索引（必须先有替代，才允许删）
SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'litemall'
  AND TABLE_NAME   = 'litemall_order_goods'
  AND COLUMN_NAME  = 'order_id'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 【执行】删除与 `KEY order_id` 重复的冗余索引
ALTER TABLE litemall_order_goods DROP INDEX idx_og_order;

-- 【执行后核对】应只剩 order_id 一个索引覆盖该列
SELECT INDEX_NAME, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'litemall'
  AND TABLE_NAME   = 'litemall_order_goods'
  AND COLUMN_NAME  = 'order_id';