-- 【需人工确认】R3-02 / D5：litemall_ad 缺排序字段，BR-CMS-03「按排序取前 5」无处落地。
-- 红线：本文件需人工确认后方可执行，不得在演示/生产库自行跑 DDL。
-- 执行记录：2026-11-07 经用户明确授权，在演示库 litemall 执行；执行前已备份
-- .dbwork/r3-backup/before-ddl-20261107.sql。未执行前服务按 sort_order 查询会报 Unknown column。
ALTER TABLE litemall_ad
    ADD COLUMN sort_order smallint NOT NULL DEFAULT 0 COMMENT '排序值，越小越前' AFTER enabled;

-- 可选清理（D8）：种子广告 id=995003 的 link=/pages/coupon/list 是小程序脏路径，
-- H5 无法解析会形成死链。重复执行不影响结果（幂等）。
-- 文档与库偏差：SHOW COLUMNS 实测 link 是 varchar(255) NOT NULL（无显式默认值），
-- 需求书原文写 link = NULL 会报 "Column 'link' cannot be null"，故改为置空串，语义同为"无跳转且不留脏值"。
UPDATE litemall_ad SET link_type = 0, link = '' WHERE link LIKE '/pages/%';
