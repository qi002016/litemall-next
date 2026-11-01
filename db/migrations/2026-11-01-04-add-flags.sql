-- ============================================================
-- 04 · 需求驱动补充列（2026-11-01 执行）
-- 背景：08 契约盘点发现 3 处需求要求但现状无列可写
--   D-06 需求 §5.12 公告「启停」 → litemall_notice 无 enabled
--   D-07 需求专题「启停」        → litemall_topic  无 enabled
--   D-08 需求 §5.14 账号「禁用/启用」→ litemall_admin 无 status
-- 影响：列口径 32 → 35；唯一键 7 → 8（admin.username）
-- 全部 NOT NULL + DEFAULT，无需回填；无索引变更（这 3 列不参与筛选条件）
-- ============================================================

ALTER TABLE `litemall_notice`
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 / 0 停用（需求 §5.12）' AFTER `content`;

ALTER TABLE `litemall_topic`
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用 / 0 停用（需求 5.12 专题启停）' AFTER `goods`;

-- 1 启用 / 0 禁用；禁用后不得登录（网关登录校验），历史记录保留
ALTER TABLE `litemall_admin`
  ADD COLUMN `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号状态：1 启用 / 0 禁用（需求 5.14）' AFTER `password`;

-- 预检（执行前实际查过）：SELECT username,COUNT(*) c FROM litemall_admin GROUP BY username HAVING c>1; → 0 行
ALTER TABLE `litemall_admin` ADD UNIQUE KEY `uk_admin_username` (`username`);