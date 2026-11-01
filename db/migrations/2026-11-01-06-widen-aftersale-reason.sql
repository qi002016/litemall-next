-- =============================================================================
-- 2026-11-01-06-widen-aftersale-reason.sql
-- 目的：把 litemall_aftersale.reason 由 varchar(31) 放宽到 varchar(255)，与契约对齐
-- 依据：docs/design/07-接口契约-订单售后支付.md 的售后申请入参 reason（≤255 字）；
--       docs/impl/R1-03-selftest.md §5 已把「reason 列宽 31，落库截断」列为已知差异。
-- 起因：R1-08 联调发现库为 varchar(31)，而线上 sql_mode 含 STRICT_TRANS_TABLES，
--       超过 31 字的原因不会截断，而是直接抛 1406 Data too long → 申请售后 500。
-- 影响面：仅放宽长度，无数据丢失、无索引变更；litemall_aftersale 现有 3 行，
--         reason 最长 4 字，扩容不影响存量数据。
-- 幂等性：MODIFY COLUMN 重复执行结果一致（幂等）。
-- 回滚：ALTER TABLE litemall_aftersale MODIFY COLUMN reason varchar(31) NULL;
--       （回滚前需确认没有超过 31 字的数据，否则会失败或截断）
-- 执行记录：2026-11-01 captain 执行成功，见文件末尾核对输出。
-- =============================================================================

-- 前置核对：确认列定义与现有数据长度
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'litemall'
  AND TABLE_NAME = 'litemall_aftersale'
  AND COLUMN_NAME = 'reason';

SELECT COUNT(*) AS total, IFNULL(MAX(CHAR_LENGTH(reason)), 0) AS max_reason_len
FROM litemall_aftersale;

ALTER TABLE litemall_aftersale
    MODIFY COLUMN reason varchar(255) NULL COMMENT '申请原因（≤255 字）';

-- 执行后核对
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'litemall'
  AND TABLE_NAME = 'litemall_aftersale'
  AND COLUMN_NAME = 'reason';