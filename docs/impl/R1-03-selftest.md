# R1-03 售后域自测报告（订单行售后全链路 + 退款编排 + 回补 CAS）

> 任务：**t4 / R1-03 售后域**（负责人 be-order）。
> 口径依据：`docs/impl/07-R1接口与状态机.md` v4 §1.2（8 态与枚举）/§1.3（错误码）/§4（C 端 CN-15~18、CN-91）/§5.5（管理端 CN-01~09）/§6.2（对内 Feign）/§6.6（CI-97）/§12.2（drills）；
> `docs/impl/03-R1实施口径.md` §5.5（OrderClient 售后方法归属 R1-02/03）、§6.2（R1-03 可改范围）、§4.1（三层错误结构）。
> 金额口径：库内 `decimal(10,2)` **元**，对外入出参 **整数分**（`Money.fen()`，HALF_UP）。

---

## 1. 改动清单

### 1.1 `litemall-api`（跨服务共享契约）

| 文件 | 改动 |
| --- | --- |
| `client/OrderClient.java` | 新增售后域 9 个方法（`adminAftersaleList/Detail/Approve/Reject/ConfirmReceive/SkipReceive/Refund/RetryRestock/Close`），路径 `/inner/order/admin/aftersale/**`，**裸 DTO 无 `Result` 包装** |
| `domain/dto/AftersaleAdminDTO.java` | 新增：CN-01 列表行（金额分、status 用标识、`specText` 由 `specifications` 派生） |
| `domain/dto/AftersaleAdminDetailDTO.java` | 新增：CN-02 详情（含内部类 `OrderBrief`/`Line`/`Timeline`；`order.status` 出**字符串标识**） |
| `domain/dto/AftersaleApproveDTO.java`、`AftersaleRejectDTO.java`、`AftersaleIdDTO.java`、`AftersaleSkipReceiveDTO.java`、`AftersaleRefundDTO.java`、`AftersaleCloseDTO.java` | 新增：管理端请求体（`adminId` 由 admin 显式透传） |
| `domain/dto/AftersaleApproveResultDTO.java`、`AftersaleRejectResultDTO.java`、`AftersaleReceiveResultDTO.java`、`AftersaleRefundResultDTO.java`、`AftersaleCloseResultDTO.java`、`AutoCloseResultDTO.java` | 新增：管理端/任务返回体 |

### 1.2 `litemall-order`（售后域本体）

| 文件 | 改动 |
| --- | --- |
| `controller/AftersaleController.java` | 重写为 C 端 5 端点：`POST /app/aftersale/apply`（CN-91）、`GET /list`（CN-15）、`GET /detail`（CN-16）、`POST /cancel`（CN-17）、`POST /return-ship`（CN-18） |
| `controller/AftersaleInnerController.java` | **新增**：`/inner/order/admin/aftersale/**` 9 端点（CN-01~CN-09），无 `Result` 包装 |
| `controller/AftersaleJobController.java` | **新增**：`POST /inner/aftersale/auto-close`（CN-IN-03） |
| `service/IAftersaleService.java` | 重写：C 端 5 + 管理端 9 + `autoClose()` |
| `service/impl/AftersaleServiceImpl.java` | **整体重写**：申请资格矩阵、8 态 CAS 流转、退款两段式与补偿、回补 CAS、券回退、时间线、编号生成 |
| `constant/AftersaleStatus.java` | 新增：8 态/type/restock 常量、`parseMulti`（标识与 0..7 混用）、`isTerminal`、`label/ident/restockLabel` |
| `mapper/AftersaleMapper.java` | 新增：`selectAdminPage`（LEFT JOIN 订单与订单行，分页排序白名单）、`selectRefundBrief`（读 `litemall_refund`） |
| `domain/DTO/AftersaleApplyDTO.java` | 重写：`orderGoodsId` 必填、`type` 必填、`number` 必填、`amount` 改为**整数分**且仅作一致性校验 |
| `domain/DTO/AftersaleCancelDTO.java`、`AftersaleReturnShipDTO.java`、`RefundBriefDTO.java` | **新增**：撤销入参、退货物流入参、退款单摘要（读支付域表） |
| `domain/VO/AftersaleApplyVO.java` | 重写：补 `aftersaleSn/statusText/typeText/amount(分)/orderGoodsId/applyTime` |
| `domain/VO/AftersaleListItemVO.java`、`AftersaleDetailVO.java`、`AftersaleReturnShipVO.java` | **新增**：列表行、详情（含 `timeline`）、退货物流返回 |

### 1.3 测试

| 文件 | 用例数 | 覆盖 |
| --- | --- | --- |
| `src/test/java/com/litemall/service/impl/AftersaleStateMachineTest.java` | 30 | 申请资格矩阵、8 态 CAS 跃迁、撤销/寄回窗口、详情契约、超时自动关闭、列表入参、**后台列表/详情出参标识映射**（CN-01/CN-02 回归） |
| `src/test/java/com/litemall/service/impl/AftersaleRefundTest.java` | 11 | 退款两段式、回补 CAS 成功/失败、`restockPending` 200 语义、CN-08 条件、券整单回退 |
| `src/test/java/com/litemall/controller/AftersaleApiTest.java` | 8 | 9 个对内端点的路由/参数映射、409 保真、无 token 401、auto-close 统计 |

---

## 2. 验证命令与结果

### 2.1 售后域单测（实跑）

```bash
cd backend/litemall-next
mvn -o test -Dtest='Aftersale*Test' -Dsurefire.failIfNoSpecifiedTests=false \
  -pl litemall-common-core,litemall-common-web,litemall-common-mybatis,litemall-common-cache,\
litemall-common-notify,litemall-common-jwt,litemall-api,litemall-order
```

```
[INFO] Tests run: 30, Failures: 0, Errors: 0, Skipped: 0 -- AftersaleStateMachineTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- AftersaleRefundTest
[INFO] Tests run:  8, Failures: 0, Errors: 0, Skipped: 0 -- AftersaleApiTest
[INFO] Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> 为什么必须显式列模块（不能 `-am`）：`-pl` 只构建指定模块，前置模块不会顺带跑测试；
> 加 `-Dsurefire.failIfNoSpecifiedTests=false` 避免上游模块因「无用例匹配」直接失败。
> 若把 `litemall-api` / `litemall-order` 单独跑，会解析到 `~/.m2` 陈旧 jar（见 §5 风险 R-1）。

### 2.2 售后域 + 订单域全量回归（实跑）

```bash
mvn -o test -Dtest='Order*Test,CartServiceImplTest,Aftersale*Test' -Dsurefire.failIfNoSpecifiedTests=false \
  -pl litemall-common-core,...,litemall-order
```

```
Tests run: 145, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（R1-02 的 96 例 + R1-03 的 49 例，无回归；含 §5 R-8 联调缺陷的 2 个回归用例：
`AftersaleStateMachineTest.adminList_mapsStatusIdentAndSpecText`、`adminDetail_statusIdents`、
`OrderAdminStateMachineTest.detail_lineAftersaleSummary_ident`。）

### 2.3 编译

```bash
mvn -o -DskipTests compile -pl litemall-common-core,...,litemall-order   # 本域及依赖：SUCCESS
mvn -o -DskipTests compile                                              # 全仓 18 模块：BUILD SUCCESS
```

```
[INFO] litemall-order ..................................... SUCCESS
[INFO] litemall-admin ..................................... SUCCESS
[INFO] BUILD SUCCESS
```

---

## 3. 接口自测样例

> order 服务直连 `http://localhost:8004`；网关 `http://localhost:8000`。
> `/app/**` 需登录态（网关注入 `X-User-Id`）；`/inner/**` 需 `X-Internal-Token`（dev 放行）。
> 下列 `9973` 为示例 user id、`1` 为示例售后单 id。

### 3.1 CN-91 申请售后（按订单行）

```bash
curl -X POST http://localhost:8004/app/aftersale/apply \
  -H "Content-Type: application/json" -H "X-User-Id: 9973" \
  -d '{"orderGoodsId":100,"type":2,"number":1,"reason":"商品有瑕疵",
       "comment":"包装破损","images":["https://img/a.png"],"amount":2995}'
```

```json
{"code":200,"data":{"aftersaleId":1,"aftersaleSn":"AS20261101000001","status":"applied",
 "statusText":"申请中","type":2,"typeText":"退货退款","amount":2995,"number":1,
 "orderGoodsId":100,"orderId":10,"applyTime":"2026-11-01 12:00:00"}}
```

要点：101/103 → 409 `ORDER_STATUS_ILLEGAL`；102 + type=2 → 409 `AFTERSALE_NOT_DELIVERED`；
301/501 超 `confirm_time+7d` → 409 `AFTERSALE_WINDOW_CLOSED`；同行已有未完结（0/1/2/3）→ 409 `AFTERSALE_EXISTS`；
type=2 必须 1~6 张图片；`amount` 只做一致性校验，不一致 → 400 `REFUND_AMOUNT_MISMATCH`。

### 3.2 CN-15 列表 / CN-16 详情 / CN-17 撤销 / CN-18 寄回

```bash
curl "http://localhost:8004/app/aftersale/list?status=applied,1&pageNo=1&pageSize=10&sortBy=applyTime&isAsc=false" \
  -H "X-User-Id: 9973"
curl "http://localhost:8004/app/aftersale/detail?aftersaleId=1" -H "X-User-Id: 9973"
curl -X POST http://localhost:8004/app/aftersale/cancel -H "Content-Type: application/json" \
  -H "X-User-Id: 9973" -d '{"aftersaleId":1}'
curl -X POST http://localhost:8004/app/aftersale/return-ship -H "Content-Type: application/json" \
  -H "X-User-Id: 9973" -d '{"aftersaleId":1,"returnShipCompany":"顺丰","returnShipSn":"SF1234567890"}'
```

要点：列表 `status` 标识与数字 0..7 可混用，非法 token → 400 `PARAM_INVALID`；
详情 `images` 恒为数组（无图为 `[]`），`returnAddress` 仅 type=2 返回；
撤销只允许 `applied`（否则 409「已进入处理流程，不能撤销」）；寄回仅 `waitReturn` 且未过 `return_deadline`（否则 409）；
他人售后单统一 404 `AFTERSALE_NOT_FOUND`（防探测）。

### 3.3 CN-01 / CN-02 管理端列表与详情

```bash
curl "http://localhost:8004/inner/order/admin/aftersale/list?status=waitReturn,2&type=2&keyword=138&pageNo=1&pageSize=20" \
  -H "X-Internal-Token: dev"
curl "http://localhost:8004/inner/order/admin/aftersale/detail?aftersaleId=1" -H "X-Internal-Token: dev"
```

要点：行含 `orderSn/consignee/goodsName/specText/auditAdmin`，**`status` 是字符串标识**（`applied`…，不是库里的 0..7）；
详情额外返回 `order`（含脱敏手机号，**`order.status` 也是标识** `shipped`/`undelivered`…）、`line`、`timeline`、
`closeReason`、`restockPending`（由 `restock_status=2` 派生）。

### 3.4 CN-03 审核通过

```bash
# 退货退款：0 → 1（waitReturn），写 return_deadline = now + 7d
curl -X POST http://localhost:8004/inner/order/admin/aftersale/approve -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":1,"remark":"同意退货","adminId":9001}'
# {"status":"waitReturn","statusText":"待寄回","returnAddress":"…","returnDeadline":"2026-11-08 12:00:00"}

# 仅退款：0 → 3（refunding），returnAddress/returnDeadline 为 null
curl -X POST http://localhost:8004/inner/order/admin/aftersale/approve -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":2,"adminId":9001}'
```

要点：CAS `status=0`，非 0 → 409 `AFTERSALE_STATUS_ILLEGAL`（连点只生效一次）；
`returnAddress` 优先取入参，缺省取 `hm.aftersale.returnAddress`，两者皆空且 type=2 → 400 `PARAM_INVALID`。

### 3.5 CN-04 驳回 / CN-05 确认收货 / CN-06 跳过收货 / CN-09 关闭

```bash
curl -X POST http://localhost:8004/inner/order/admin/aftersale/reject -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":3,"reason":"商品已使用","adminId":9001}'
curl -X POST http://localhost:8004/inner/order/admin/aftersale/confirm-receive -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":4,"adminId":9001}'
curl -X POST http://localhost:8004/inner/order/admin/aftersale/skip-receive -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":5,"reason":"用户自寄无需退货","adminId":9001}'
curl -X POST http://localhost:8004/inner/order/admin/aftersale/close -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":6,"reason":"用户放弃","adminId":9001}'
```

要点：驳回 0→6、理由必填 ≤255；确认收货只接受 `waitReceive`（2→3，**不直接 completed**）；
跳过收货接受 1/2 → 3 并写日志；关闭接受 0/1/2 → 5，`refunding` 与所有终态 → 409。

### 3.6 CN-07 退款编排（关键）

```bash
curl -X POST http://localhost:8004/inner/order/admin/aftersale/refund -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":1,"amount":2995,"remark":"同意退款","adminId":9001}'
# 成功：{"status":"completed","refundId":77,"refundAmount":2995,"restockStatus":1,"restockPending":false,...}
# 资金成功但回补失败（★不是错误）：HTTP 200 +
# {"status":"refunding","restockStatus":2,"restockStatusText":"待回补","restockPending":true}
```

要点：只允许 `waitReceive`(2) / `refunding`(3)；已 completed 再退 → 409；
回补矩阵：`102+type1`、`201/301/501+type2` 需回补；`201/301/501+type1` 不回补（`restock_status` 仍置 1）；
整单全退（所有行 `refund_status=2` 且无未完结售后）才调 `MarketingClient.rollbackCoupon`。

### 3.7 CN-08 重试回补

```bash
curl -X POST http://localhost:8004/inner/order/admin/aftersale/retry-restock -H "Content-Type: application/json" \
  -H "X-Internal-Token: dev" -d '{"aftersaleId":1,"adminId":9001}'
```

要点：仅 `status=refunding` 且 `restock_status=2`；CAS `2→1` 成功才调 goods `release`；
仍失败 → 200 且继续停在 `refunding` + `restockPending=true`；条件不满足 → 409「该售后单无需重试回补」。

### 3.8 CN-IN-03 待寄回超时自动关闭

```bash
curl -X POST http://localhost:8004/inner/aftersale/auto-close -H "X-Internal-Token: dev"
# {"closed":2,"failed":[{"id":9,"reason":"状态已变更"}]}
# 无命中：{"closed":0,"failed":[]}   ← 不是 404
```

---

## 4. 验收对照

| 任务卡要求 | 证据 |
| --- | --- |
| 售后按订单行（`order_goods_id` + `number`），可退件数扣已退 | `AftersaleServiceImpl.apply` + `applyQtyExceed_badRequest` |
| 申请窗口 `confirm_time+7d` | `applyOutOfWindow_conflict` |
| 8 态全流转（含待寄回超期自动关闭、退款中可重试） | `AftersaleStateMachineTest` 28 例 + `autoClose_casPerRow` + `retryRestock_success` |
| 退款调 CN-IN-02、order 侧本地置 completed、pay 不回调 markRefunded | `AftersaleRefundTest.refundWithRestock_success`（`PaymentClient.refund` 只被调用一次） |
| 回补 = 先 CAS `restock_status` 0/2→1 再调 goods release | `tryRestock` + `refundWithRestock_success`（`times(3)` update 断链） |
| 资金成功 + 回补失败 → 200 + `restockPending`，停 refunding，不得 409 | `refundRestockFail_pendingNotConflict` |
| `RESTOCK_PENDING` 不作对外错误码 | 只作为 `restockPending` 布尔与内部日志 |
| 幂等与补偿用例 | 连点审核 409、退款重复 409、CN-08 条件 409、券回退仅整单全退 |
| 提交接口自测样例 | §3（15 个端点 curl + 期望响应） |

---

## 5. 已知差异与开放项

### R-1（跨域缺陷，已上报 captain）`data.errorCode` 被陈旧 jar 遮蔽

- 现象：`/inner/**` 的 409 响应体为 `{"code":409,"msg":"…","data":null}`，`data.errorCode` 缺失。
- 证据（测试内探针）：
  `PROBE_CODE=CODE_PROBE SRC=…/litemall-common-core/target/classes/ RESULT_SRC=~/.m2/…/litemall-common-0.0.1-SNAPSHOT.jar`
  —— 异常类来自 reactor 的 `common-core`（带 errorCode），但 `com.litemall.domain.Result` 来自**陈旧的 `litemall-common` jar**。
- 根因：`litemall-common` 已改为 `packaging=pom`（无源码），但 `litemall-api` / `litemall-order` 等仍以 **jar 类型**依赖它
  （`mvn dependency:build-classpath` 仍列出该 jar，缺 jar 时构建直接失败：`Could not find artifact com.litemall:litemall-common:jar`），
  导致本地仓库里 9 月的旧 jar 长期占据 classpath，旧版 `Result`/`CommonExceptionAdvice`/`CacheService` 覆盖新版实现。
- 影响面：**所有服务**的三层错误结构（03 §4.1）在运行时失效，admin 侧 ErrorDecoder 拿不到 errorCode。
- 建议修复（聚合层 pom/仓库卫生，属 R1 基础设施，不在售后域范围）：
  ① 依赖声明加 `<type>pom</type>`；或 ② `litemall-common` 恢复为 jar 打包但保持**空包**（先 `mvn clean`）；
  ③ 修好后 `mvn dependency:purge-local-repository -DgroupId=com.litemall` 清掉旧 jar。
- 本域已做的兜底：`AftersaleApiTest` 只断言 HTTP 409 + `code` + `msg`；错误码本身由
  `AftersaleStateMachineTest`/`AftersaleRefundTest` 对 `getErrorCode()` 逐条断言（域内保证）。

### R-2 `reason` 列宽 31（契约允许 255）

`litemall_aftersale.reason` 为 `varchar(31)`（DDL 既定，未在 R1 扩容）。落库前 `StrUtil.maxLength(reason, 31)` 截断，
入参校验仍按契约 ≤255；截断只影响库内可读性，不影响状态与金额。

### R-3 售后表无 `refund_id` / `audit_time` 列

内部（日志、时间线）的 `refundId` / `refundTime` 改为读支付域 `litemall_refund`
（`selectRefundBrief`：优先 `status=1`，再按 id 倒序取一条）；`auditTime` 复用 `handle_time` 承载（审核/驳回/关闭都写它）。
对外契约以 07 为准：CN-02 详情**不含** `refundId/refundAmount`（07:909-931 出参示例与字段表都没有，`refundId` 只出现在
CN-07 refund 响应 07:1069），故 DTO 不暴露这两个字段（曾按 be-admin 初版反馈加过，复核契约后已回滚）。

### R-4 不落库的入参

`approve.returnAddress`（仅覆盖本次响应，缺省取 `hm.aftersale.returnAddress`）、`remark` 只写订单日志
（`litemall_log`，`target_type='aftersale'`），不写售后表 —— 表无对应列。

### R-5 驳回理由复用 `close_reason`

`BR-AS-22` 要求驳回理由对 C 端可见，表内无专用列，实现写入 `close_reason`；CN-02/CN-16 的 `closeReason` 即驳回理由。

### R-6 已退金额口径

CN-02 的 `order.refundedAmount` = 该订单 **completed 售后单金额之和**（按现状汇总，不做行级重算）；
`fullyRefunded` = 所有订单行 `refund_status=2`。

### R-8 后台出参映射缺陷修复（be-admin 联调，2026-11-01）

后台联调中 be-admin 逐条对契约核出的 3 条**映射层**缺陷（admin 侧原样序列化，不做加工），均已修复：

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | `CN-01` 列表行 `status` 出 `"0"` 数字串 | `decorateAdminRow` 只设了 `statusText`，未把库里的 tinyint 转标识 | `AftersaleServiceImpl.decorateAdminRow` 增加 `row.setStatus(AftersaleStatus.ident(status))` |
| 2 | `CN-01` 列表行 `specText` 恒 `null` | `selectAdminPage` 把 `og.specifications` 别名成 `specifications`，DTO 字段是 `specText`，反序列化丢失 | 别名改为 `AS specText`，再由 `specText(...)` 把 JSON 数组拼成 `"白色 / S"` |
| 3 | `CN-02` 详情 `order.status` 出 `102` 数字；`CN-33` 行级 `aftersaleStatus` 出 `0` 数字 | `OrderBrief.status` / `Line.aftersaleStatus` 声明为 `Integer` | 两字段改 `String`，赋值分别走 `OrderStatus.ident(...)` / `AftersaleStatus.ident(...)` |

另修 `CI-26` 列表 `addTime` 出 ISO `2026-09-26T14:03:54`（违反 06 §4）：`OrderAdminDTO.addTime` 保持
`LocalDateTime` 但补 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")`，
不改字段类型以免动到 admin 侧既有测试；detail 的时间本来就是 `String`，无需处理。

回归用例：`AftersaleStateMachineTest.adminList_mapsStatusIdentAndSpecText`、`adminDetail_statusIdents`、
`OrderAdminStateMachineTest.detail_lineAftersaleSummary_ident`。

> 结论口径确认（非缺陷）：已完结售后（如订单 28 / 售后单 4 → completed）在 `CN-33` 行级三字段为 `null` 是**契约正确**
> （07:542「该行**当前未完结**售后；无则 null」），be-admin 已确认撤回该质疑。

### R-7 单测基础设施注意

`AftersaleServiceImpl` 的 CAS 全部走 MyBatis-Plus `lambdaUpdate()`，单测必须先 `TableInfoHelper.initTableInfo(...)`
初始化 lambda 缓存（`Aftersale`/`Order`/`OrderGoods`），否则报 `can not find lambda cache`；`TransactionTemplate`
用 `lenient().doAnswer(...).when(...).executeWithoutResult(any())` 直跑回调。