-- 【需人工确认】本文件含 DDL/DML，须人工确认后执行。
-- 执行记录：2026-11-07 经用户明确授权，在演示库 litemall 执行；执行前已备份
-- .dbwork/r3-backup/before-ddl-20261107.sql，并确认 litemall_collect 共 0 行、deleted=1 共 0 行、无重复键。
-- 上线前执行；执行前必须确认 litemall_collect 无重复 (user_id,value_id,type)
-- 执行前清理逻辑删除的历史行（物理删除方案不再需要 deleted=true 的行）
DELETE FROM litemall_collect WHERE deleted = 1;

-- 若已存在重复行，先合并：保留 add_time 最早的一条，删除其余（下面这句会报错，报错即说明有重复，需人工处理）
ALTER TABLE litemall_collect ADD UNIQUE INDEX uk_collect_user_value_type (user_id, value_id, type);
