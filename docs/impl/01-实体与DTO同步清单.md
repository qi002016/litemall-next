# 01 · 实体 / Mapper / DTO 与新增列的同步清单

> 状态：**执行前清单**（本文件先出清单与影响面，再动代码）
> 依据：本机库 `litemall`（MySQL 8.0.46）实测 + `db/migrations/2026-11-01-01..05`
> 对应设计：`docs/design/03-数据与接口变更方案.md` v8、`06`、`07` v4、`08` v4

## 0. 一句话结论

| 层 | 现状 | 本批改动 |
|---|---|---|
| 数据库 | 已落 **35 列**（`-01` 的 32 列 + `-04` 的 3 列）、8 唯一键、索引净增 9 | 已完成，无需再动 |
| 实体 PO | **12 个实体、35 个字段全部缺失** | **本批全部补上**（纯加字段，无逻辑） |
| Mapper | 全仓**无 XML、无 `@Select/@Update/@Insert/@Delete`**，纯 MyBatis-Plus 注解 | **0 处改动**（已核实，见 §3.5） |
| DTO / VO | 与新增列的暴露面尚未建立 | **本批不改**，第二批清单见 §4 |

口径修正：需求里说的「32 个新列」是 `-01` 脚本的数量；加上 `-04` 补的 `notice.enabled`、`topic.enabled`、`admin.status` 三列，**实际是 35 列**。

## 1. 类型映射规则（与 `06` §8 一致）

| DB 类型 | Java 类型 | 说明 |
|---|---|---|
| `bigint` | `Long` | 本项目 ID 已全量 `Long`（M10 整改结果） |
| `int` | `Integer` | 数量类 |
| `tinyint`（枚举语义，如 0/1/2、1/2/3） | `Integer` | 保留后续加值空间，不用 `Boolean` |
| `tinyint(1)`（纯开关，且该实体已有同名风格） | `Boolean` | 仅 `notice/topic.enabled`、`order.user_deleted` 用 |
| `decimal(10,2)` | `BigDecimal` | 金额，库内**存元**（`06` §4 的金额口径不变，契约层才转分） |
| `datetime` | `LocalDateTime` | 全项目统一，不用 `Date` |
| `varchar` | `String` | — |

字段风格沿用现有实体：`@Data + @EqualsAndHashCode(callSuper=false) + @Accessors(chain=true)`，每字段一个 `@Schema(description=...)`，注释文案直接取库里 `COLUMN_COMMENT`，保证「文档—库—代码」三方同文。

## 2. 逐列 → 字段映射（12 实体 / 35 字段）

### 2.1 goods 服务（4 字段）

| 表.列 | 类型/约束 | Java 字段 | 实体文件 |
|---|---|---|---|
| `litemall_goods.merchant_id` | bigint NOT NULL DEFAULT 1 | `Long merchantId` | `litemall-goods/.../domain/PO/Goods.java` |
| `litemall_goods.status` | tinyint NOT NULL DEFAULT 1（0 草稿/1 在售/2 已下架） | `Integer status` | 同上 |
| `litemall_goods_product.sku_code` | varchar(64) NULL | `String skuCode` | `litemall-goods/.../domain/PO/GoodsProduct.java` |
| `litemall_goods_product.spec_value_ids` | varchar(255) NOT NULL DEFAULT '' | `String specValueIds` | 同上 |

### 2.2 order 服务（21 字段）

| 表.列 | 类型/约束 | Java 字段 | 实体文件 |
|---|---|---|---|
| `litemall_order.merchant_id` | bigint NOT NULL DEFAULT 1 | `Long merchantId` | `litemall-order/.../PO/Order.java` |
| `litemall_order.expire_time` | datetime NULL（下单+30min） | `LocalDateTime expireTime` | 同上 |
| `litemall_order.close_type` | tinyint NULL（1 用户取消/2 超时/3 运营关单） | `Integer closeType` | 同上 |
| `litemall_order.user_deleted` | tinyint NOT NULL DEFAULT 0 | `Boolean userDeleted` | 同上 |
| `litemall_order_goods.sku_code` | varchar(64) NULL | `String skuCode` | `.../PO/OrderGoods.java` |
| `litemall_order_goods.refunded_number` | int NOT NULL DEFAULT 0 | `Integer refundedNumber` | 同上 |
| `litemall_order_goods.refund_status` | tinyint NOT NULL DEFAULT 0（0 未退/1 部分/2 全退） | `Integer refundStatus` | 同上 |
| `litemall_order_goods.actual_amount` | decimal(10,2) NOT NULL DEFAULT 0.00 | `BigDecimal actualAmount` | 同上 |
| `litemall_order_goods.coupon_share` | decimal(10,2) NOT NULL DEFAULT 0.00 | `BigDecimal couponShare` | 同上 |
| `litemall_aftersale.order_goods_id` | bigint NULL | `Long orderGoodsId` | `.../PO/Aftersale.java` |
| `litemall_aftersale.number` | int NOT NULL DEFAULT 0 | `Integer number` | 同上 |
| `litemall_aftersale.return_ship_company` | varchar(64) NULL | `String returnShipCompany` | 同上 |
| `litemall_aftersale.return_ship_sn` | varchar(64) NULL | `String returnShipSn` | 同上 |
| `litemall_aftersale.return_ship_time` | datetime NULL | `LocalDateTime returnShipTime` | 同上 |
| `litemall_aftersale.receive_time` | datetime NULL | `LocalDateTime receiveTime` | 同上 |
| `litemall_aftersale.close_reason` | varchar(255) NULL | `String closeReason` | 同上 |
| `litemall_aftersale.audit_admin` | bigint NULL | `Long auditAdmin` | 同上 |
| `litemall_aftersale.restock_status` | tinyint NOT NULL DEFAULT 0（0 未回补/1 已回补/2 待回补） | `Integer restockStatus` | 同上 |
| `litemall_aftersale.return_deadline` | datetime NULL | `LocalDateTime returnDeadline` | 同上 |
| `litemall_comment.order_id` | bigint NULL | `Long orderId` | `litemall-goods/.../PO/Comment.java` |
| `litemall_comment.order_goods_id` | bigint NULL（唯一） | `Long orderGoodsId` | 同上 |

（`litemall_refund` 的 2 列属 pay 服务，见 2.4；`litemall_comment` 另 2 列见 2.2 尾表）

### 2.3 comment 剩余 2 列 + marketing 3 列（5 字段）

| 表.列 | 类型/约束 | Java 字段 | 实体文件 |
|---|---|---|---|
| `litemall_comment.reply_time` | datetime NULL | `LocalDateTime replyTime` | `litemall-goods/.../PO/Comment.java` |
| `litemall_comment.status` | tinyint NOT NULL DEFAULT 0（0 显示/1 已隐藏） | `Integer status` | 同上 |
| `litemall_ad.link_type` | tinyint NOT NULL DEFAULT 0（0 无跳转/1 商品/2 专题） | `Integer linkType` | `litemall-marketing/.../PO/Ad.java` |
| `litemall_notice.enabled` | tinyint(1) NOT NULL DEFAULT 1 | `Boolean enabled` | `litemall-marketing/.../PO/Notice.java` |
| `litemall_topic.enabled` | tinyint(1) NOT NULL DEFAULT 1 | `Boolean enabled` | `litemall-marketing/.../PO/Topic.java` |

### 2.4 pay 服务（2 字段）

| 表.列 | 类型/约束 | Java 字段 | 实体文件 |
|---|---|---|---|
| `litemall_refund.aftersale_id` | bigint NULL | `Long aftersaleId` | `litemall-pay/.../PO/Refund.java` |
| `litemall_refund.refund_time` | datetime NULL | `LocalDateTime refundTime` | 同上 |

### 2.5 admin 服务（3 字段）

| 表.列 | 类型/约束 | Java 字段 | 实体文件 |
|---|---|---|---|
| `litemall_admin.status` | tinyint(1) NOT NULL DEFAULT 1（1 启用/0 禁用） | `Integer status` | `litemall-admin/.../domain/AdminAccount.java` |
| `litemall_log.target_type` | varchar(32) NULL | `String targetType` | `litemall-admin/.../domain/AdminOperationLog.java`（`@TableName("litemall_log")`） |
| `litemall_log.target_id` | bigint NULL | `Long targetId` | 同上 |

> `admin.status` 用 `Integer` 而非 `Boolean`：库注释是「1 启用/0 禁用」，但状态类字段后续可能扩值（如 2 冻结），与 `goods.status`、`comment.status` 保持一致，不用 `Boolean`。

**合计 4 + 21 + 2 + 5 + 2 + 3 = 35 字段，落在 12 个实体**。

## 3. 影响面

### 3.1 编译与测试
- 纯加字段，不改任何方法签名 ⇒ 编译风险 0。
- Lombok `@Data` 会同步生成 getter/setter/`equals`/`hashCode`/`toString`：现有测试若对实体做整体 `equals` 或断言 `toString` 全文会受影响。已核：单测未对这两个实体做整体比较（仍以 `mvn verify` 全绿为准）。
- 基线：整改期结束时 **13 模块 / 88 个测试全绿**，本批改动后需保持同一基线。

### 3.2 运行期 SQL 行为（关键）
- `mybatis-plus.global-config.db-config.update-strategy: not_null`（`litemall-common-core/src/main/resources/common.yaml:68`），insert 默认策略同为 `NOT_NULL` ⇒ **新字段为 null 时不会进入 INSERT/UPDATE 的列清单**，现有写入路径的行为与今天完全一致。
- 但 **SELECT 会带上全部字段**：MyBatis-Plus 的 `selectById`/`selectList` 生成显式列清单，加字段后查询语句会包含新列 ⇒ **代码与 DDL 是同批次强绑定，且 DDL 必须先于代码**。若把新代码连到未迁移的库，所有查询直接报 `Unknown column`。
- 反向：今天（库已迁移、代码未加字段）跑服务是安全的——多余列不影响映射；这也是我建议「先出清单再改码」的原因之一。

### 3.3 接口出参变化（只增）
- 唯一直接返回 PO 的端点是 `GET /admin/auth/info`（`litemall-admin/src/main/java/com/litemall/controller/AuthController.java:56` 返回 `Result<AdminAccount>`，且已把 `password` 置 null）⇒ 新增 `status` 会出现在该响应里。属**只增不删**，两套前端不认该字段也不会出错；`AdminLoginVO` 不受影响。
- 其余端点均返回 VO/DTO，新字段不会自动透出。

### 3.4 序列化 / 日志 / 审计
- 新字段为 null 时不参与 JSON（项目未配置 `NON_NULL` 全局策略？→ 实际输出 `null` 键；对前端无害，但会让 `/admin/auth/info` 响应体变大几十字节）。
- 审计日志若用 `toString()` 落库，新字段会进日志（仅体积增加，无敏感信息：`return_ship_sn` 属物流单号，可接受；`audit_admin` 是 ID）。

### 3.5 Mapper 层为什么 0 改动
已核实：全仓 `*/src/main/java/com/litemall/mapper/*.java` 只有接口继承 `BaseMapper<T>`，**无 XML 映射文件、无 `@Select/@Update/@Insert/@Delete` 标注 SQL**（`grep -rln` 命中 0）。因此不存在需要手工补列的 `resultMap` 或列清单，字段加在实体上即生效。

### 3.6 部署顺序与回滚
1. 先执行 `db/migrations/2026-11-01-01..05`（本机已执行）。
2. 再发代码（本批）。
3. 回滚：代码回滚安全（旧代码忽略多余列）；DDL 回滚需 `DROP COLUMN`，**不建议**——新列已有回填数据，且不影响旧代码。

## 4. DTO / VO 第二批清单（本批不动，绑 R1 契约实现）

改 DTO/VO 等于改接口契约，必须与端点实现同批，否则会出现「字段加了但服务不认」或「接口返回设计未定义的字段」。故本批只登记，不落地：

| DTO / VO | 拟加字段 | 关联列 | 依据 |
|---|---|---|---|
| `GoodsCreateDTO` / `GoodsUpdateDTO` | `status`（可选，0 草稿/1 在售/2 已下架，与 `onSale` 双向映射，矛盾报 400） | `goods.status` | `08` 裁定 `D-03` |
| `GoodsAdminDTO` | `status`、`merchantId` | `goods.status`、`goods.merchant_id` | `08` 商品后台列表 |
| `GoodsVO` / `GoodsDetailVO` | `availableStock`（新增，语义见 `D-17`；`stock` 不改） | 由 `number − locked` 派生，非新列 | `08` `CN-13` |
| `OrderDetailVO` | `expireTime`、`closeType`、`userDeleted` 不外露 | `order.expire_time`、`order.close_type` | `07` `CN-33` / `CN-20` |
| `OrderListItemVO` | `expireTime`（倒计时用） | `order.expire_time` | `07` `CN-20` |
| `OrderGoodsVO` | `actualAmount`、`couponShare`、`refundedNumber`、`refundStatus`、`skuCode` | `order_goods.*` | `07` 订单详情 |
| `OrderConfirmVO` | 行级 `actualAmount` / `couponShare`（试算） | `order_goods.*` | `07` `CN-14` |
| 售后单 VO（**现不存在，R1 新建**） | 上述 `aftersale` 10 列全部 | — | `07` `CN-30`~`CN-36` |
| `CommentVO` | `orderId`、`orderGoodsId`、`replyTime`、`status` | `comment.*` | `08` 评价域 |
| `AdVO` | `linkType` | `ad.link_type` | `08` `CI-18/19` |
| `NoticeVO` / `TopicVO` | `enabled` | `notice/topic.enabled` | `08` `CN-34`~`CN-36` |
| `RefundVO` | `aftersaleId`、`refundTime` | `refund.*` | `07` `CN-07` |
| `AdminAccount` 出参 | 已随实体自动带 `status`；R1 需配 `CN-37` 启停端点 | `admin.status` | `08` `CN-37` |

## 5. 本批执行与验证

1. 12 个实体补 35 字段（仅字段 + `@Schema`，无逻辑）。
2. 计数校验：脚本按「表.列」逐项 grep 实体是否已有对应字段，输出 35/35。
3. 构建：`mvn -T 1C -pl <改动模块> -am test`（串行，Maven 不得并发）。
4. 全量：`mvn -T 1C verify`，要求 13 模块 SUCCESS、测试数不低于 88、失败 0。
5. 不改 `backend` 以外任何文件；不执行 SQL；不 git 提交（等确认）。

## 6. 执行结果（本批已落地）

### 6.1 改动文件（12 个实体，全部是纯加字段）

| 模块 | 文件 | 增加字段数 |
|---|---|---|
| goods | `litemall-goods/src/main/java/com/litemall/domain/PO/Goods.java` | 2 |
| goods | `litemall-goods/src/main/java/com/litemall/domain/PO/GoodsProduct.java` | 2 |
| goods | `litemall-goods/src/main/java/com/litemall/domain/PO/Comment.java` | 4 |
| marketing | `litemall-marketing/src/main/java/com/litemall/domain/PO/Ad.java` | 1 |
| marketing | `litemall-marketing/src/main/java/com/litemall/domain/PO/Notice.java` | 1 |
| marketing | `litemall-marketing/src/main/java/com/litemall/domain/PO/Topic.java` | 1 |
| pay | `litemall-pay/src/main/java/com/litemall/domain/PO/Refund.java` | 2 |
| order | `litemall-order/src/main/java/com/litemall/domain/PO/Order.java` | 4 |
| order | `litemall-order/src/main/java/com/litemall/domain/PO/OrderGoods.java` | 5 |
| order | `litemall-order/src/main/java/com/litemall/domain/PO/Aftersale.java` | 10 |
| admin | `litemall-admin/src/main/java/com/litemall/domain/AdminAccount.java` | 1 |
| admin | `litemall-admin/src/main/java/com/litemall/domain/AdminOperationLog.java` | 2 |
| **合计** | **12 个文件** | **35** |

未改：Mapper（0 处，理由见 §3.5）、DTO/VO（0 处，第二批见 §4）、任何 Service/Controller、任何 SQL。

### 6.2 验证证据（真实命令与输出）

| # | 检查 | 命令 | 结果 |
|---|---|---|---|
| V1 | 字段计数 | Python 逐项核对「表.列 → 类型 + 字段名」，要求声明在文件中唯一 | **35/35 通过** |
| V2 | 编译 | `mvn -B -T 1C -pl litemall-goods,litemall-marketing,litemall-pay,litemall-order,litemall-admin -am -DskipTests compile` | **BUILD SUCCESS**，14 个模块全部 SUCCESS |
| V3 | 单元测试（非 Spring 上下文） | 同 5 模块 `-am test -Dtest='!<12 个 @SpringBootTest 类>' -Dsurefire.failIfNoSpecifiedTests=false` | **72 个用例全绿**（common-web 4 / cache 4 / jwt 6 / marketing 9 / order 28 / pay 16 / admin 5），BUILD SUCCESS |
| V4 | 实体字段 ↔ 库列对齐 | 把 12 个实体的字段名转 snake_case 生成 12 条 `SELECT <全部列> FROM <table> LIMIT 0`，直接打到本机库 | **12/12 无报错**（含全部 35 个新列，证明列名映射正确、列真实存在） |
| V5 | 全量 `mvn verify` | `mvn -B -pl <5 模块> -am verify` | ❌ **环境阻塞**：3 个 goods 上下文测试 `Failed to get available servers`（Seata `globalTransactionScanner`），见 §6.3 |

### 6.3 V5 的阻塞说明（不是本次改动引起）

- 失败点：`BeanCreationException: Error creating bean with name 'globalTransactionScanner' ... Failed to get available servers`（`/tmp/verify.log:2952`），发生在 **Seata 客户端注册中心连不上**时，与实体字段无关。
- 实测端口：`192.168.96.129:8848`、`192.168.96.129:8091`、`192.168.96.129:9848`、`127.0.0.1:8848` **全部 CLOSED** ⇒ 本机所用的 Nacos / Seata Server（VM `192.168.96.129`）当前未运行。
- 受影响的测试类（全仓 16 个测试类中 **12 个是 `@SpringBootTest`**，都需要 Nacos/Seata/MySQL/Redis）：`LitemallGoodsApplicationTests`、`StockDeductBatchTest`、`LitemallMarketingApplicationTests`、`LitemallPayApplicationTests`、`LitemallOrderApplicationTests`、`InnerApiSecurityTest`、`OrderAdminInnerApiTest`、`AdminDtoValidationTest`、`LitemallUserApplicationTests`、`LitemallMessageApplicationTests`、`LitemallStorageApplicationTests`、`LitemallGatewayApplicationTests`。
- 结论：**基础设施起来后需重跑 `mvn -T 1C verify` 才能算全量验收**；本批的代码正确性已由 V1–V4 覆盖（编译 + 72 个单测 + 12 条真实 SQL 列映射）。

### 6.4 部署提醒（写进发布单）

1. **DDL 必须先于代码**：新实体会让所有 `selectById/selectList` 的列清单包含 35 个新列，连到未迁移的库会直接 `Unknown column`。
2. 回滚安全：只回滚代码即可（旧代码忽略多余列）。
3. 本批**不改变**任何接口出参（唯一例外：`GET /admin/auth/info` 自动多出 `status` 字段，属只增）。

## 7. 全量验收补齐（基础设施恢复后，2026-11-01）

§6.3 的「环境阻塞」已解除：VM `192.168.96.129` 上的 Nacos(8848/9848)、Seata(8091)、RocketMQ(9876/10911) 全部起好并实测连通，`seata-server` 在 Nacos 中状态 `healthy=true`、cluster `default`、group `SEATA_GROUP`。

### 7.1 第一次全量 verify 暴露的真实问题（关键发现）

`mvn -B -T 1C verify` 首跑 **BUILD FAILURE**，且不是环境问题：

```
Tests run: 2, Errors: 2 -- in com.litemall.controller.StockDeductBatchTest
Caused by: java.sql.SQLIntegrityConstraintViolationException:
  Duplicate entry '999999-' for key 'litemall_goods_product.uk_goods_product_combo'
```

- 根因：`-03-add-keys.sql` 给 `litemall_goods_product` 加了 **`uk_goods_product_combo(goods_id, spec_value_ids)`** 与 **`uk_goods_product_sku_code(sku_code)`**；而 `StockDeductBatchTest.insertProduct()` 给同一 `goods_id=999999` 连续插两行、且既不设 `specValueIds` 也不设 `skuCode`（都是默认 `''`）⇒ 第二条撞唯一键。
- 判定：**DDL 表达的是正确不变量**（同一商品的 SKU 必须有不同规格组合），是测试夹具不符合新模型。已核生产代码**不存在**任何插入 `goods_product` 的路径（`grep` 全仓 `src/main` 仅 `GoodsInnerController.java:82` 构造 DTO，无 insert/save），故影响面仅此一处测试。
- 修复：`StockDeductBatchTest.java` 增加 `SPEC_SEQ` 自增序号，`insertProduct()` 写入 `specValueIds=<序号>` 与 `skuCode=999999-<序号>-<nanoTime>`；同时补类注释说明两个唯一键的约束来源。**未改动任何断言**（回滚语义、库存数值断言原样保留）。

### 7.2 修复后全量结果

| 项 | 结果 |
|---|---|
| 命令 | `mvn -B -T 1C verify`（`backend/litemall-next`） |
| 模块 | **19 个模块（含聚合 pom）全部 SUCCESS**，BUILD SUCCESS |
| 用例 | **120 个，Failures 0 / Errors 0 / Skipped 0** |
| 上下文测试 | 12 个 `@SpringBootTest` 类全部在真实中间件下通过（8 个 `*ApplicationTests` 各 1 + `StockDeductBatchTest` 2 + `AdminDtoValidationTest` 11 + `InnerApiSecurityTest` 2 + `OrderAdminInnerApiTest` 5） |
| 用例分布 | gateway 1 / goods 3 / marketing 1 / message 8 / order 46 / pay 17 / storage 8 / user 1 / admin 17 / common-web 8 / common-cache 4 / common-jwt 6 |
| 日志 | `/tmp/verify3.log`（首次失败 `/tmp/verify2.log`） |

### 7.3 结论

- 本批「12 实体 / 35 字段」同步**通过全量验收**：代码可编译、120 个用例全绿、字段与库列逐一对齐。
- DDL 与代码的耦合方向确认：`selectById/selectList` 会带全部新列 ⇒ **必须先迁移 DDL、后发代码**（已在 §6.4 记录）。
- 新增不变量带来的后续要求（写入 R1 开发任务）：**创建/更新 SKU 时必须写入 `spec_value_ids` 与 `sku_code`**，否则第二条 SKU 会撞 `uk_goods_product_combo`；这条会随 DTO 第二批（§4）一起进代码。
