-- ============================================================================
-- 【需人工确认】P1 数据库变更交付物（本文件由整改过程生成，**未被任何任务执行过**）
-- 生成依据：本地 litemall 库 information_schema 只读查询（127.0.0.1:3306, 库名 litemall）
-- 适用范围：M3（订单号唯一索引）、M5/M6（用户券唯一索引）、M10（ID 类型 int -> bigint）、
--           H5（数据库账号最小权限，见文件末尾）
-- 执行前置条件：
--   1. 必须由人工在维护窗口执行，并先做全库备份（mysqldump）；
--   2. 必须先在预发环境验证，尤其是 M10 的 76 列类型变更（大表会锁表/重写，需评估在线 DDL 工具）；
--   3. 执行顺序建议：M3/M5 索引 -> M10 类型 -> H5 账号权限；
--   4. 回滚方案：类型变更可用反向 ALTER 回退（前提是值未超过 int 上限）；索引可直接 DROP INDEX。
-- 状态：**未执行**（本次整改全部命令均为只读查询或应用层构建/测试）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- M3：litemall_order.order_sn 唯一索引
-- 原因：订单号原先"count 查重 + 冲突递归"，并发下仍可重号，且无任何索引可证唯一。
--      代码已改为"低碰撞组合号 + 捕获唯一冲突换号重试"，唯一性最终依赖此索引兜底。
-- 风险：若历史数据已存在重复 order_sn，此语句会失败——需先执行下面的检查语句。
-- ---------------------------------------------------------------------------
-- 检查语句（只读，可先执行确认是否存在重复）：
-- SELECT order_sn, COUNT(*) c FROM litemall_order GROUP BY order_sn HAVING c > 1;
ALTER TABLE `litemall_order`
  ADD UNIQUE INDEX `uk_order_sn` (`order_sn`);

-- ---------------------------------------------------------------------------
-- M5/M6：litemall_coupon_user(coupon_id, user_id) 唯一索引
-- 原因：领券限领一致性目前完全依赖 Redis 计数，DB 侧无唯一约束兜底（Redis 与 DB 漂移时无最后防线）。
-- 风险：若历史数据同一用户同一券已有多条记录，此语句会失败——需先执行下面的检查语句。
-- ★★ 重要前置确认（执行前必须澄清产品语义）★★
--   1) 本索引在"同一用户可领多张"的券（litemall_coupon.limit > 1）上会**直接禁止**第 2 次领取，
--      与现有"limit 控制领取张数"的业务语义冲突；
--   2) 取消订单回退券（coupon_user 记录被置为不可用/已回退）后用户重新领取，
--      若旧记录仍留在表中，本索引同样会拦掉合法重领。
--   因此本索引**不是 M5 修复的必要条件**：M5 的代码修复（预减台账 + 对称回滚）已独立生效。
--   只有在确认"每个用户每张券至多一条 coupon_user 记录"后，才可执行本语句。
-- ---------------------------------------------------------------------------
-- SELECT coupon_id, user_id, COUNT(*) c FROM litemall_coupon_user GROUP BY coupon_id, user_id HAVING c > 1;
-- ALTER TABLE `litemall_coupon_user`
--   ADD UNIQUE INDEX `uk_coupon_user` (`coupon_id`, `user_id`);

-- ---------------------------------------------------------------------------
-- M10：ID 及相关外键列 int -> bigint（36 张表 / 76 列）
-- 原因：PO 侧为 Integer，API/DTO 侧为 Long；ID 超过 21 亿会静默截断。
--      应用层已统一按 Long 处理（Long 写进 int 列仍兼容），因此**本 DDL 不执行也不会立即出错**，
--      只有实际出现 >2^31 的 ID 时才必须执行。
-- 注意：AUTO_INCREMENT 主键的 MODIFY 会重建表；大表请用 gh-ost/pt-online-schema-change。
-- ---------------------------------------------------------------------------
-- M10 DDL (generated from local litemall schema, read-only).
-- EVERY STATEMENT NEEDS HUMAN CONFIRMATION. DO NOT EXECUTE AUTOMATICALLY.
-- Applies to all 36 tables / 76 id-like int columns.

ALTER TABLE `litemall_ad`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_address`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `litemall_admin`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_aftersale`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `order_id` BIGINT NOT NULL,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `litemall_brand`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_cart`
  MODIFY COLUMN `goods_id` BIGINT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `product_id` BIGINT NULL,
  MODIFY COLUMN `user_id` BIGINT NULL;

ALTER TABLE `litemall_category`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_collect`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `value_id` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `litemall_comment`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `value_id` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `litemall_coupon`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_coupon_user`
  MODIFY COLUMN `coupon_id` BIGINT NOT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `order_id` BIGINT NULL,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `litemall_feedback`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `litemall_footprint`
  MODIFY COLUMN `goods_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `litemall_goods`
  MODIFY COLUMN `brand_id` BIGINT NULL,
  MODIFY COLUMN `category_id` BIGINT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_goods_attribute`
  MODIFY COLUMN `goods_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_goods_product`
  MODIFY COLUMN `goods_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_goods_specification`
  MODIFY COLUMN `goods_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_groupon`
  MODIFY COLUMN `creator_user_id` BIGINT NOT NULL,
  MODIFY COLUMN `groupon_id` BIGINT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `order_id` BIGINT NOT NULL,
  MODIFY COLUMN `rules_id` BIGINT NOT NULL,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `litemall_groupon_rules`
  MODIFY COLUMN `goods_id` BIGINT NOT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_issue`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_keyword`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_log`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_notice`
  MODIFY COLUMN `admin_id` BIGINT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_notice_admin`
  MODIFY COLUMN `admin_id` BIGINT NULL,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `notice_id` BIGINT NULL;

ALTER TABLE `litemall_order`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `litemall_order_goods`
  MODIFY COLUMN `goods_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `order_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `product_id` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `litemall_permission`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `role_id` BIGINT NULL;

ALTER TABLE `litemall_refund`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `order_id` BIGINT NOT NULL,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `litemall_region`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_role`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_search_history`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

ALTER TABLE `litemall_storage`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_system`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_topic`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_user`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `litemall_user_message`
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT,
  MODIFY COLUMN `msg_id` BIGINT NOT NULL DEFAULT 0,
  MODIFY COLUMN `user_id` BIGINT NOT NULL;

-- ---------------------------------------------------------------------------
-- H5：数据库账号最小权限（**这不能替代 H5 的代码整改**）
-- 现状（实测）：存在 root@% 与 litemall@%，且 litemall@% 拥有 GRANT ALL ON litemall.*，
--              因此"数据所有权"问题无法靠收紧现有账号解决；必须新建按服务隔离的账号。
-- 前置：H5 代码整改（admin 改为经 Feign 调用各服务 inner 接口、不再直写他域表）完成后再执行，
--      否则 admin 会因缺少他域表权限而报错。
-- 【需人工确认】下面的授权范围只是模板：每个服务的准确表清单必须由其 Mapper/实体实际访问的
--      表推导出（可按服务 grep @TableName 得到），不要照抄。
-- ---------------------------------------------------------------------------
-- CREATE USER 'litemall_order'@'%'   IDENTIFIED BY '<强口令_来自环境变量/密管>';
-- CREATE USER 'litemall_goods'@'%'   IDENTIFIED BY '<强口令>';
-- CREATE USER 'litemall_user'@'%'    IDENTIFIED BY '<强口令>';
-- CREATE USER 'litemall_marketing'@'%' IDENTIFIED BY '<强口令>';
-- CREATE USER 'litemall_admin'@'%'   IDENTIFIED BY '<强口令>';
--
-- -- 示例：订单服务只允许访问自己拥有的表
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_order        TO 'litemall_order'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_order_goods  TO 'litemall_order'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_cart         TO 'litemall_order'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_refund       TO 'litemall_order'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_aftersale    TO 'litemall_order'@'%';
-- -- 商品服务
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_goods                TO 'litemall_goods'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_goods_product        TO 'litemall_goods'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_goods_specification  TO 'litemall_goods'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_goods_attribute      TO 'litemall_goods'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_brand                TO 'litemall_goods'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.litemall_category             TO 'litemall_goods'@'%';
-- -- 其余服务同法推导；Seata 参与方还需 litemall.undo_log 权限
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.undo_log TO 'litemall_order'@'%';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON litemall.undo_log TO 'litemall_goods'@'%';
--
-- -- 收紧完成后，回收宽权限账号（**风险操作，需确认无其他调用方**）：
-- -- REVOKE ALL PRIVILEGES ON litemall.* FROM 'litemall'@'%';
-- --   （root@% 建议直接删除或限制为 localhost；应用配置里的账号需同步切换到新账号）

