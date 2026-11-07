-- =============================================================
-- R3 演示种子数据（DML，幂等，可重复执行）
-- 2026-11-07 经用户明确授权，在演示库 litemall 执行。
-- 红线：只用精确 id，禁止区间删除；DDL 一律不在此文件执行。
-- 依赖：先执行 db/migrations/2026-11-07-r3-01-collect.sql 与 ...r3-02-ad-sort-order.sql
--       （litemall_ad.sort_order 列、litemall_collect 唯一键须已存在）。
-- =============================================================

-- 1) 三条首页轮播：跳商品 / 跳专题 / 无跳转。
-- position=1 即 C 端传入的 home_banner；时间窗留空 → 后端「空即不限」，避免过期导致首页恒空。
-- link 只存目标 ID（或空串），绝不存 /pages/xxx 这类小程序路径。
INSERT INTO litemall_ad
    (id, name, link, link_type, url, position, content,
     start_time, end_time, enabled, sort_order, deleted, add_time, update_time)
VALUES
    (995001, 'R2 新品耳机首发', '993003', 1,
     'http://yanxuan.nosdn.127.net/658f09b7ec522d31742b47b914d64338.png', 1, '无线降噪耳机 Pro 上新，前 100 名赠收纳包',
     NULL, NULL, 1, 10, 0, NOW(), NOW()),
    (995002, '秋日家居焕新', '996001', 2,
     'http://yanxuan.nosdn.127.net/658f09b7ec522d31742b47b914d64338.png', 1, '香薰、抱枕套、收纳箱全场 9 折',
     NULL, NULL, 1, 20, 0, NOW(), NOW()),
    (995003, '满减券限时领', '', 0,
     'http://yanxuan.nosdn.127.net/658f09b7ec522d31742b47b914d64338.png', 1, '满 99 减 10，满 299 减 50',
     NULL, NULL, 1, 30, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    link       = VALUES(link),
    link_type  = VALUES(link_type),
    sort_order = VALUES(sort_order),
    start_time = NULL,
    end_time   = NULL,
    enabled    = 1,
    deleted    = 0,
    update_time = NOW();

-- 2) 历史演示广告 id=1/2/3 时间窗已过期或为 NULL，会在首页多出一张，
--    污染 R3-02「后台配 3 张 → 首页恰好 3 张」与 R3-04「停用 1 张 → 剩 2 张」的验收。
--    【可恢复】精确 id 停用，需要时把 enabled 改回 1 即可，不删除任何行。
UPDATE litemall_ad SET enabled = 0, update_time = NOW() WHERE id IN (1, 2, 3);

-- 3) 两条收藏演示数据（user_id=1，type=0 商品收藏）。
--    唯一键 uk_collect_user_value_type 保证重复执行不产生重复行。
INSERT INTO litemall_collect
    (id, user_id, value_id, type, add_time, update_time, deleted)
VALUES
    (998001, 1, 993001, 0, NOW(), NOW(), 0),
    (998002, 1, 993005, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    user_id     = VALUES(user_id),
    value_id    = VALUES(value_id),
    type        = VALUES(type),
    deleted     = 0,
    update_time = NOW();
