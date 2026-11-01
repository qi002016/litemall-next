# R1-02 订单域自测报告（锁量下单 / 状态机 / 超时与立即关单 / 锁定量）

> 任务：R1-02（团队 r1-impl / t3，owner: be-order）
> 口径依据：`docs/design/07-接口契约-订单售后支付.md` §2/§3/§6、`docs/impl/03-R1实施口径.md` §1（状态码与错误码）、§4.1（对内接口不加 `Result`）
> 金额口径：**一律 int 分**（`Integer`），元只在 `litemall_order*` 落库与存量 `OrderPayInfoDTO.actualPrice` 上出现
> 编号说明：任务卡写的「CN-01~05」在 03 文档中属售后域后台端点（t4 / R1-03 范围）；订单域按 07 §3 的 `CI-80/81/82/83/84/85/86`、`CI-27`、`CI-97`、`CN-10/11/12/20/33`、`CN-IN-01/04` 实现。

## 1. 改动清单

### 1.1 `litemall-order`（新增）

| 文件 | 改动 |
| --- | --- |
| `constant/OrderStatus.java` | 主状态常量（101/102/103/201/301/501/401/402）、`ident()`/`label()`/`parse()`/`parseMulti()`/`isValidIdent()`/`closeTypeLabel()`；401/402 只读兼容，R1 不写 |
| `constant/AftersaleStatus.java` | 售后状态/类型常量 + `UNFINISHED={0,1,2,3}` + `label/ident/typeLabel` |
| `support/Money.java` | `fen(BigDecimal)`：元→分（HALF_UP） |
| `support/OrderRules.java` | `allocateCoupon`（D9 券额分摊，尾差入末行、单行不超行金额）、`isFullyRefunded`、`refundedAmount`、`hasUnreviewed`、`canMarkCompleted` |
| `domain/PO/OrderLog.java` + `mapper/OrderLogMapper.java` | 订单操作日志（`@TableName("litemall_log")`），关单/发货/改单号落档 |
| `domain/VO/OrderConfirmLineVO, OrderConfirmVO, OrderSubmitVO, OrderListItemVO, OrderDetailLineVO, OrderTimelineVO, OrderAftersaleBriefVO` | 8 个端点的出参（金额 Integer 分、`status`+`statusText` 双出） |

### 1.2 `litemall-order`（修改）

| 文件 | 改动 |
| --- | --- |
| `service/IOrderService.java` | 方法集按 07 重写：`createIdemToken/confirmOrder/submitOrder/listOrder/getOrderDetail/cancelOrder/confirmReceive/hideOrder/adminListOrders(11 参)/adminOrderDetail/adminOrderStats/adminShip/adminClose/adminCloseExpired/adminUpdateShip/markPaid/orderGoodsStatus/lockedStock`；**删除 `adminRefund`（CX-03）** |
| `service/impl/OrderServiceImpl.java` | 整体重写（约 1100 行）：下单草稿解析、锁量、幂等状态机、CAS 状态跃迁、三路径关单与资源释放、支付回调闸门、锁定量查询 |
| `service/impl/CartServiceImpl.java` | `confirmOrder` 标 `@Deprecated(since="R1")`，金额改分并适配新 `OrderConfirmVO`，javadoc 指向 `IOrderService#confirmOrder`（下单试算唯一入口） |
| `controller/OrderController.java` | 依赖收敛为 `IOrderService`；`/list` 改 `tab/status/pageNo/pageSize/sortBy/isAsc`；新增 `POST /hide` |
| `controller/OrderInnerController.java` | 新增 `/admin/list`(11 参)、`/admin/order/{id}`、`/admin/close`、`/admin/close-expired`、`/admin/update-ship`、`/admin/order-goods/{id}`、`/locked-stock`；删除 `/admin/refund`、`/pay/refund` |
| `mapper/OrderMapper.java` | 新增 `selectLockedStock(productIds)`：`order_status=101` 未删订单按 `product_id` 求 `SUM(number)` |
| `domain/DTO/OrderConfirmDTO.java`、`OrderSubmitDTO.java` | `cartIds`/`buyNow` 二选一（无 `@NotEmpty`）、`@NotNull addressId`、`OrderSubmitDTO.idemToken` 必填 |
| `domain/VO/OrderConfirmVO/OrderDetailVO/OrderGoodsVO/OrderListItemVO/OrderSubmitVO` | 金额 Integer 分、行级券分摊与退款字段、`goodsList` 保留 `@Deprecated` 兼容 |

### 1.3 `litemall-api`（跨服务契约）

| 文件 | 改动 |
| --- | --- |
| `client/OrderClient.java` | 重写：`getPayInfo`、`markPaid→PaySuccessResultDTO`、`adminOrderList`(11 参)、`adminOrderDetail`、`adminOrderStats`、`adminShip`、`adminClose`、`adminCloseExpired`、`adminUpdateShip`、`orderGoodsStatus`、`lockedStock`；`adminListOrders`/`adminRefund`/`markRefunded` 标 `@Deprecated` |
| `domain/dto/OrderAdminDTO.java` | 管理端列表行（Integer 分 + status/statusText + 售后状态 + goodsSummary） |
| `domain/dto/OrderAdminDetailDTO.java`、`OrderCloseDTO.java`、`CloseExpiredResultDTO.java`、`OrderGoodsStatusDTO.java`、`LockedStockDTO.java`、`PaySuccessResultDTO.java` | 新增对内契约 |
| `domain/dto/OrderShipDTO.java` | 加 `reason`（CN-12 改单号留痕） |

### 1.4 测试（`litemall-order/src/test/java/com/litemall/`）

| 文件 | 覆盖 |
| --- | --- |
| `support/OrderRulesTest.java` | D9 分摊、`fullyRefunded`、`refundedAmount`、`hasUnreviewed`、`canMarkCompleted` |
| `service/impl/OrderStateMachineTest.java` | CI-85/86/CN-20/CI-97/CN-IN-01 状态机与支付闸门 |
| `service/impl/OrderCloseConsistencyTest.java` | 三路径关单（用户/超时/运营）释放一致性 |
| `service/impl/OrderConfirmAllocationTest.java` | CI-80/82 试算、分摊、锁量失败、行快照 |
| `service/impl/OrderLockedStockTest.java` | CN-IN-04 参数校验与聚合 |
| `service/impl/OrderIdempotentTest.java` | CI-82 幂等状态机（MISSING/USED 回放/CONFLICT/回滚） |
| `service/impl/OrderAdminStateMachineTest.java` | CI-27 发货、CN-10/11/12 |
| `service/impl/CartServiceImplTest.java`、`controller/OrderAdminInnerApiTest.java` | 旧用例适配新契约（金额分、删除 refund 端点） |

## 2. 验证命令与结果

```bash
# 1) 全仓编译（reactor 必须整仓：litemall-common 是 pom 打包，api 以 jar 依赖它，-pl 子集会解析失败）
cd backend/litemall-next
mvn -o -DskipTests compile
```

```
[INFO] litemall-order ........................................ SUCCESS
[INFO] BUILD FAILURE
[ERROR] litemall-admin/src/main/java/com/litemall/controller/GoodsController.java:[51,41] 不兼容的类型: java.math.BigDecimal 无法转换为 java.lang.Integer
[ERROR] litemall-admin/src/main/java/com/litemall/controller/GoodsController.java:[63,41] 不兼容的类型: java.math.BigDecimal 无法转换为 java.lang.Integer
```

- 订单域及其全部依赖（common-core/web/mybatis/cache/notify/jwt、api、order）**编译通过**；唯一失败在 `litemall-admin`，根因是 `GoodsAdminDTO.minPrice` 改为整数分（be-goods 改动），属 **R1-05（be-admin）** 范围，不在本任务可改范围。

```bash
# 2) 订单域单测（-pl 必须显式列依赖模块，不能 -am；-Dsurefire.failIfNoSpecifiedTests=false 让前置模块“无匹配用例”不失败）
mvn -o test -Dtest='Order*Test,CartServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -pl litemall-common-core,litemall-common-web,litemall-common-mybatis,litemall-common-cache,litemall-common-notify,litemall-common-jwt,litemall-api,litemall-order
```

```
[INFO] Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0  -- OrderAdminInnerApiTest
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0  -- CartServiceImplTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0  -- OrderAdminStateMachineTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0  -- OrderCloseConsistencyTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0  -- OrderConfirmAllocationTest
[INFO] Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0  -- OrderIdempotentTest
[INFO] Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0  -- OrderLockedStockTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0  -- OrderStateMachineTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0  -- OrderRulesTest
[INFO] Tests run: 95, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## 3. 接口自测样例

> 端口：C 端经网关 `http://127.0.0.1:8000`；对内直连 `http://127.0.0.1:8004`（`X-Internal-Token` 由网关/内部约定）。示例 id 按本地种子数据调整。

### 3.1 CI-81 `POST /app/order/idem-token` — 申请下单幂等 token

```bash
curl -s -X POST "http://127.0.0.1:8000/app/order/idem-token" \
  -H "Authorization: Bearer <user-jwt>"
```

```json
{ "code": 200, "msg": "OK", "data": { "idemToken": "9f2c4b7e8a1d4c6f9b0e3a5d7c1f2e4b" } }
```

- token 由 `StringRedisTemplate` 生成并落 `order:idem:token:<token>`（TTL 300s）；未登录 → 401。

### 3.2 CI-80 `POST /app/order/confirm` — 试算（可售不足**只提示不阻断**）

```bash
curl -s -X POST "http://127.0.0.1:8000/app/order/confirm" \
  -H "Content-Type: application/json" -H "Authorization: Bearer <user-jwt>" \
  -d '{"cartIds":[11,12],"addressId":3,"couponId":7}'
```

```json
{ "code": 200, "msg": "OK", "data": {
  "goodsAmount": 15800, "freightAmount": 0, "couponAmount": 1000, "orderAmount": 14800,
  "lines": [
    { "productId": 1001, "goodsName": "轻奢真丝衬衫", "specText": "白色 / M", "price": 9900,
      "number": 1, "lineAmount": 9900, "couponShare": 627, "actualAmount": 9273, "availableStock": 12 },
    { "productId": 1002, "goodsName": "轻奢真丝衬衫", "specText": "黑色 / L", "price": 5900,
      "number": 1, "lineAmount": 5900, "couponShare": 373, "actualAmount": 5527, "availableStock": 0 }
  ]
} }
```

- `Σ couponShare = couponAmount`（D9 尾差入末行）；`actualAmount = lineAmount - couponShare`。
- 第二行 `availableStock = 0` 仍返回 200，只做前端提示。
- 也可用「立即购买」：`{"buyNow":{"productId":1001,"number":2},"addressId":3}`；`cartIds` 与 `buyNow` 同时给 → `buyNow` 优先。
- 错误：购物车行不存在/已删/他人 → 400 `RESOURCE_NOT_FOUND`；地址不存在 → 400 `ADDRESS_INVALID`；券不可用 → 400 `COUPON_NOT_USABLE`；行数 > 50 → 400 `ORDER_ITEM_LIMIT`。

### 3.3 CI-82 `POST /app/order/submit` — 提交订单（幂等 + 锁量）

```bash
curl -s -X POST "http://127.0.0.1:8000/app/order/submit" \
  -H "Content-Type: application/json" -H "Authorization: Bearer <user-jwt>" \
  -d '{"cartIds":[11,12],"addressId":3,"couponId":7,
       "idemToken":"9f2c4b7e8a1d4c6f9b0e3a5d7c1f2e4b","message":"请下午送达"}'
```

```json
{ "code": 200, "msg": "OK", "data": {
  "orderId": 9001, "orderSn": "20261101163000001", "orderAmount": 14800,
  "expireTime": "2026-11-01 17:00:00"
} }
```

- 同一 token + 同参数重放 → **同一份 `OrderSubmitVO`**（Redis 存 `指纹|VO`，TTL 300s），不重复建单。
- 同 token + 改参数 → 409 `IDEMPOTENT_CONFLICT`「幂等token已用于不同参数的提交，请刷新后重试」。
- 缺 `idemToken` / token 不存在或已过期 → 400（`PARAM_INVALID`「幂等token无效或已过期」）。
- 锁量失败 → 409 `STOCK_NOT_ENOUGH`，`msg` 形如「白色 / M 仅剩 2 件」；主表不落库。
- 成功后：订单 101、`expire_time = now + 30min`、行快照写 `sku_code/specifications/actual_amount/coupon_share/refund_status=0`、券置已用、购物车行逻辑删。
- 副作用失败回滚：`@GlobalTransactional`，券已用/库存已扣而落单失败时整体回滚，幂等置 `ROLLED_BACK`。

### 3.4 CI-83 `GET /app/order/list` — 我的订单（7 栏）

```bash
curl -s "http://127.0.0.1:8000/app/order/list?tab=unpaid&pageNo=1&pageSize=10&sortBy=addTime&isAsc=false" \
  -H "Authorization: Bearer <user-jwt>"
```

```json
{ "code": 200, "msg": "OK", "data": { "total": 1, "pages": 1, "list": [ {
  "orderId": 9001, "orderSn": "20261101163000001", "status": "unpaid", "statusText": "未付款",
  "goodsAmount": 15800, "freightAmount": 0, "couponAmount": 1000, "orderAmount": 14800,
  "fullyRefunded": false, "refundedAmount": 0, "hasUnreviewed": false,
  "expireTime": "2026-11-01 17:00:00", "addTime": "2026-11-01 16:30:00",
  "goodsList": [ { "orderGoodsId": 5001, "goodsId": 5, "productId": 1001, "goodsName": "轻奢真丝衬衫",
    "picUrl": "https://…/1.jpg", "number": 1, "price": 9900, "specText": "白色 / M",
    "refundStatus": 0, "refundStatusText": "无退款", "specifications": ["白色","M"], "commentStatus": 0 } ]
} ] } }
```

- `tab`：`all/unpaid/undelivered/shipped/confirmed/completed/refund`；`status`：逗号多值（`ident` 名），非法 → 400 `PARAM_INVALID`。
- `sortBy` 只允许 `addTime`/`orderAmount`（其余忽略回退 `addTime`）；已隐藏（`user_deleted=true`）订单不出现。

### 3.5 CI-84 `GET /app/order/detail` — 订单详情

```bash
curl -s "http://127.0.0.1:8000/app/order/detail?orderId=9001" \
  -H "Authorization: Bearer <user-jwt>"
```

```json
{ "code": 200, "msg": "OK", "data": {
  "orderId": 9001, "orderSn": "20261101163000001", "status": "unpaid", "statusText": "未付款",
  "consignee": "张三", "mobile": "13800000000", "address": "北京市朝阳区xx路1号",
  "goodsAmount": 15800, "freightAmount": 0, "couponAmount": 1000, "orderAmount": 14800,
  "actualPrice": 14800, "payTime": null, "shipChannel": null, "shipSn": null, "shipTime": null,
  "expireTime": "2026-11-01 17:00:00", "addTime": "2026-11-01 16:30:00",
  "fullyRefunded": false, "refundedAmount": 0,
  "lines": [ { "orderGoodsId": 5001, "productId": 1001, "goodsName": "轻奢真丝衬衫", "specText": "白色 / M",
    "price": 9900, "number": 1, "actualAmount": 9273, "couponShare": 627,
    "refundedNumber": 0, "refundStatus": 0, "refundStatusText": "无退款",
    "canApply": { "types": [], "maxQty": 0, "reason": "未付款订单不可申请售后" } } ],
  "timeline": [ { "time": "2026-11-01 16:30:00", "actor": "user", "actorText": "用户", "action": "创建订单" } ],
  "aftersale": null
} }
```

- `canApply` 简化矩阵（R1）：101/103/501/401/402 → 空 + 原因；102 → `[1]`（仅退款）；201/301 → `[1,2]`；行已全额退款 / 无可申请件数 / 超 7 天 → 空 + 原因。
- 他人订单 → 404 `ORDER_NOT_FOUND`（防探测）。

### 3.6 CI-85 / CI-86 / CN-20

```bash
# 取消未付款订单（101 → 103，close_type=1，退券；已支付订单必须先走售后）
curl -s -X POST "http://127.0.0.1:8000/app/order/cancel?orderId=9001" -H "Authorization: Bearer <user-jwt>"
# 确认收货（201 → 301）
curl -s -X POST "http://127.0.0.1:8000/app/order/confirm-receive?orderId=9001" -H "Authorization: Bearer <user-jwt>"
# 删除订单（C 端隐藏，幂等）
curl -s -X POST "http://127.0.0.1:8000/app/order/hide?orderId=9001" -H "Authorization: Bearer <user-jwt>"
```

```json
{ "code": 200, "msg": "OK", "data": null }
```

- 取消非 101 → 409「该订单当前状态不可取消」；确认收货非 201 → 409「只有已发货订单可以确认收货」；他人/不存在 → 404。
- `hide`：有处理中售后（状态 0/1/2/3）→ 409 `AFTERSALE_EXISTS`「有处理中的售后，暂不能删除」；重复调用直接 200（幂等，`user_deleted` CAS）。

### 3.7 对内：关单与发货（admin 服务调用）

```bash
# CI-27 发货（102 → 201）
curl -s -X POST "http://127.0.0.1:8004/inner/order/admin/ship" -H "Content-Type: application/json" \
  -d '{"orderId":9001,"shipChannel":"SF","shipSn":"SF1234567890"}'
# CN-10 运营立即关单（101 → 103，close_type=3，写 order:close 日志）
curl -s -X POST "http://127.0.0.1:8004/inner/order/admin/close" -H "Content-Type: application/json" \
  -d '{"orderId":9001,"reason":"缺货取消","adminId":9001}'
# CN-11 超时关单（调度每分钟；成功返回空体）
curl -s -X POST "http://127.0.0.1:8004/inner/order/admin/close-expired?limit=100&dryRun=false"
# CN-12 修改物流单号（仅 201 且改过 0 次，写 order:ship-update 日志）
curl -s -X POST "http://127.0.0.1:8004/inner/order/admin/update-ship" -H "Content-Type: application/json" \
  -d '{"orderId":9001,"shipChannel":"YTO","shipSn":"YTO99887766","reason":"快递改派"}'
```

```json
/* CN-11 出参 */
{ "total": 3, "succeeded": 2, "failed": 1,
  "items": [ { "id": 9001, "ok": true, "reason": null }, { "id": 9002, "ok": false, "reason": "订单状态已变更" } ] }
```

- `adminShip` 先校验 `shipChannel`/`shipSn`（空 → 400「快递公司不能为空」/「快递单号不能为空」），再 CAS `order_status=102`；未命中 → 409「只有待发货订单可以发货」。
- `adminClose`：`reason` > 255 → 400；非 101 → 409「该订单当前状态不可关闭」；成功后 `close_type=3`、`end_time`、回退券、写日志（admin=9001）。已支付订单回退券 + 释放库存。
- `adminCloseExpired`：`limit` 1–200（越界 400），默认 100；`dryRun=true` 只统计不落库；单条 CAS 失败计入 `failed` 不中断整批。
- `adminUpdateShip`：非 201 → 409；已改过 1 次（`litemall_log` 中 `order:ship-update` 计数）→ 409「物流单号已修改过，不能再次修改」。

### 3.8 CN-33 / CI-97 / CN-IN-01 / CN-IN-04

```bash
# CN-33 管理端订单详情（含行级售后与改单号能力）
curl -s "http://127.0.0.1:8004/inner/order/admin/order/9001"
# CI-97 支付成功回调（重复回调 200 + applied:false；已关闭不扣库存）
curl -s -X POST "http://127.0.0.1:8004/inner/order/pay/success" -H "Content-Type: application/json" \
  -d '{"orderId":9001,"payId":"wx202611011630"}'
# CN-IN-01 订单行评价校验
curl -s "http://127.0.0.1:8004/inner/order/admin/order-goods/5001?userId=7"
# CN-IN-04 SKU 锁定量（productIds 省略 = 全部）
curl -s "http://127.0.0.1:8004/inner/order/locked-stock?productIds=1001,1002"
```

```json
/* CI-97 首次回调 */      { "applied": true,  "deducted": true,  "shortage": [] }
/* CI-97 重复回调 */      { "applied": false, "deducted": false, "shortage": [] }
/* CI-97 库存不足 */      { "applied": false, "deducted": false, "shortage": [ { "productId": 1001, "need": 3, "available": 1 } ] }
/* CN-IN-04 */            { "list": [ { "productId": 1001, "lockedQty": 2 }, { "productId": 1002, "lockedQty": 1 } ] }
```

- CI-97 闸门：订单不存在 → 404；`pay_time` 已写 → 200 + `applied:false`（幂等）；101 且库存不足 → **200 + shortage、订单保持 101**；101 且充足 → CAS 101→102 + 一次性实扣，实扣失败 → `deducted:false`（订单已 102，留 error 日志）；已关闭（103）→ 200 + `applied:false`、不扣库存，按 03 §2.5 记 DR-11 日志（**自动退款待 R1-04 接线，见 §5**）。
- CN-IN-01：可评价 = `order_status ∈ {301,401}` 且行 `refund_status ≠ 2` 且未评价；`userId` 不匹配 → 404（不 403）。
- CN-IN-04：`productIds` 空白/省略 → 查全部；空段/非数字 → 400「productIds 格式非法」；重复 → 400「productIds 不能重复」；段数 > 200 → 400「productIds 数量不能超过 200」；结果按 `productId` 升序，无锁量 → `{"list":[]}`。

## 4. 验收对照

| 验收项 | 用例 |
| --- | --- |
| 状态跃迁 CAS，`affected=0 → 409` | `OrderStateMachineTest`（CI-85/86/CN-20/CI-97 各 CAS miss 分支）、`OrderAdminStateMachineTest`（CI-27/CN-10/CN-12）、`OrderCloseConsistencyTest` |
| 三种关闭路径释放一致性 | `OrderCloseConsistencyTest`：用户 `close_type=1`/超时 `=2`/运营 `=3`，均写 `end_time`；券 `coupon_price>0` 才回退；仅 `pay_time!=null` 才释放库存；回退异常不阻断关单 |
| D9 分摊 | `OrderRulesTest.allocateCoupon*`（尾差入末行、Σ 恒等、单行不超行金额）、`OrderConfirmAllocationTest`（落 `coupon_share`/`actual_amount`） |
| 提交接口自测样例 | §3.3（正常/幂等回放/冲突/锁量失败/缺 token） |
| CN-IN-04 | `OrderLockedStockTest`（7 例）+ `OrderAdminInnerApiTest` 端点映射 |

## 5. 已知差异与风险

1. **`litemall-admin` 编译受阻（非本域）**：`GoodsController:51,63` 取 `GoodsAdminDTO.minPrice` 仍按 `BigDecimal`，需 R1-05 改为整数分；另有 `AdminOrderFeignTest:62` 用 `setOrderAmount(new BigDecimal("19.90"))`、admin `OrderController` 仍透传已下线的 `adminRefund`（`OrderClient.adminRefund` 暂标 `@Deprecated` 保编译），一并由 R1-05 处理。
2. **地址归属无法校验**：`UserClient.getAddressById(Long)` 出参 `AddressInfoDTO` 无 `userId`，CI-80/82 的「他人地址 → 403」在订单侧无法判定；当前地址不存在/已删 → 400 `ADDRESS_INVALID`。越权防护依赖 user 服务侧补齐（建议 R1-05 或后续迭代把 `userId` 加入出参）。
3. **CI-97 已关闭订单自动退款未接线**：07 要求「已关闭走自动退款 + 日志」，退款入口在 pay 服务（`PayClient`，R1-04）；本批只写 DR-11 异常日志并返回 200，避免跨域未就绪时阻塞支付回调。
4. **501 已完成 R1 不可达**：R1 未实现「确认收货后自动完成 / 定时完成」；`OrderRules.canMarkCompleted` 已就绪，留作 R2 CI-36 接线。
5. **`canApply` 为 R1 简化矩阵**：仅按主状态与行退款额给 `types`，未叠加「售后单已存在」「7 天窗口」以外的业务限制（如特殊品类不支持退货）；R2 收敛到售后域统一规则。
6. **`litemall_order_goods.spec_value_ids` 列不存在**：规格快照只靠 `specifications`（JSON）与 `sku_code`，规格筛选类需求需另建列（35 列口径未含此列）。
7. **对 `-pl` 的依赖**：`mvn -pl litemall-order -am` 会因 `litemall-common` 是 `pom` 打包而解析 `jar` 失败；CI/本地验证统一走整仓 reactor（命令见 §2）。