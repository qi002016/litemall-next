# R1-04 自测说明（营销支付域：券 / 退款通道 / 团购下线）

> 任务：`R1-04`｜分支：本地工作区（基线 `3d94ecb`）｜口径依据：`docs/impl/03-R1实施口径.md` §4、`docs/design/07-接口契约-订单售后支付.md` §5.1/5.3/5.5、`docs/design/08-接口契约-商品库存评价营销内容后台.md` §4.2/4.5。
>
> 金额口径：对外整数分、库内 `DECIMAL` 元，转换只在 DTO 边界（06 §4）。

## 1. 改动清单

### 1.1 marketing（`backend/litemall-next/litemall-marketing`）

| 文件 | 改动 |
| --- | --- |
| `controller/MarketingController.java` | 删除 `GET /app/market/groupon/list`（CX-01 下线 → 404）；新增 `GET /app/market/coupon/usable`（CN-19） |
| `domain/VO/UsableCouponsVO.java` | 新建：`goodsAmount` + `coupons[]` |
| `domain/VO/UsableCouponVO.java` | 新建：`couponUserId`/`couponId`/`name`/`discount`/`min`/`endTime`/`usable`/`unusableReason`/`deductAmount` |
| `service/ICouponService.java` | 新增 `UsableCouponsVO getUsableCoupons(Integer goodsAmountCents)` |
| `service/impl/CouponServiceImpl.java` | CN-19 实现；一人一券预检（领取前 + 落库前兜底）；`uk_coupon_user` 冲突转「你已领过这张券」 |
| `test/.../CouponUsableCouponsTest.java` | 新建 9 例（CN-19 6 + 一人一券 2 + 参数/鉴权） |

### 1.2 pay（`backend/litemall-next/litemall-pay`）

| 文件 | 改动 |
| --- | --- |
| `controller/PayController.java` | `wechat` 透传 `mode`（CI-103） |
| `controller/PayInnerController.java` | 新建 `POST /inner/pay/refund`（CN-IN-02，裸类型） |
| `domain/DTO/PayWechatDTO.java` | +`mode`（success 默认 / fail） |
| `domain/DTO/RefundDTO.java` | `amount` 由 `BigDecimal`(元) 改 `Integer`(分) + `@Min(1)`（P-27） |
| `domain/VO/PayParamsVO.java` | +`demo=true`、`payResult` |
| `domain/VO/PayResultVO.java` | +`result`、`orderStatus`、`orderStatusText`、`expireTime` |
| `service/IPayService.java` | +`wechatPay(Long, String mode)`、+`refundFunds(PayRefundDTO)`；`wechatPay(Long)` 保留并委托 |
| `service/impl/PayServiceImpl.java` | CI-103 演示支付、CI-105 收窄（不回调 order）、CI-106 结果映射、CN-IN-02 幂等退款、`mapStatus` 修正 103→`unpaid` |
| `test/.../PayServiceImplTest.java` | 14 → 25 例（见 §4） |

### 1.3 共享契约（`backend/litemall-next/litemall-api`，单一所有者 R1-04）

| 文件 | 改动 |
| --- | --- |
| `client/PayClient.java` | 新建：`@FeignClient(name="litemall-pay")`，唯一方法 `refund(PayRefundDTO)` |
| `domain/dto/PayRefundDTO.java` | 新建：`aftersaleId`/`orderId`/`amount`(分)/`reason`/`operatorId` |
| `domain/dto/PayRefundResultDTO.java` | 新建：`refundId`/`status`/`statusText`/`refundTime` |

## 2. 验证命令与结果

```bash
cd backend/litemall-next

# 编译（本任务两个模块 + 依赖）
mvn -B -pl litemall-marketing,litemall-pay -am -DskipTests compile
# => BUILD SUCCESS

# 单测
mvn -B -pl litemall-pay -am -Dtest=PayServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
# => Tests run: 25, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

mvn -B -pl litemall-marketing -am -Dtest='CouponServiceImplTest,CouponUsableCouponsTest' -Dsurefire.failIfNoSpecifiedTests=false test
# => Tests run: 18 (9 + 9), Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

> `-Dsurefire.failIfNoSpecifiedTests=false` 是必需的：`-am` 会带上无匹配用例的前置模块，否则 `No tests matching pattern` 直接失败。

## 3. 接口自测样例

### 3.1 CN-19 `GET /app/market/coupon/usable`

```bash
curl -s "http://127.0.0.1:8081/app/market/coupon/usable?goodsAmount=12800" \
  -H "Authorization: Bearer <user-jwt>"
```

```json
{ "code": 200, "msg": "OK", "data": {
  "goodsAmount": 12800,
  "coupons": [
    { "couponUserId": "11", "couponId": "201", "name": "满50减10",
      "discount": 1000, "min": 5000, "endTime": "2026-12-01 00:00:00",
      "usable": true, "unusableReason": null, "deductAmount": 1000 },
    { "couponUserId": "12", "couponId": "202", "name": "满200减20",
      "discount": 1000, "min": 20000, "endTime": "2026-12-01 00:00:00",
      "usable": false, "unusableReason": "差 72.00 元可用", "deductAmount": 0 }
  ]
} }
```

- 排序：`usable` 降序 → `discount` 降序 → `endTime` 升序（空值列末）。
- `coupons` 恒为数组（无券为 `[]`），`deductAmount ≤ goodsAmount`（BR-CP-09）。
- 只读：不写 `coupon_user.status`、不占券。
- `goodsAmount` 缺失/负数 → 400 `PARAM_INVALID`；未登录 → 401。

### 3.2 CI-103 `POST /app/pay/wechat`

```bash
curl -s -X POST "http://127.0.0.1:8081/app/pay/wechat" \
  -H "Content-Type: application/json" -H "Authorization: Bearer <user-jwt>" \
  -d '{"orderId":"800","mode":"fail"}'
```

```json
{ "code": 200, "msg": "OK", "data": {
  "timeStamp": "1793000000", "nonceStr": "…", "package_": "prepay_id=wx80012345678",
  "signType": "RSA", "paySign": "…", "demo": true, "payResult": "fail"
} }
```

- `mode` 缺省 = `success`；非 `success`/`fail` → 400 `PARAM_INVALID`（「支付方式不合法」）。
- `mode=fail` 只改 `payResult`，**不改订单状态、不释放库存、不退券**；真正置已支付在 CI-97。
- 订单非 101 → 409：已支付「该订单已支付」(`ORDER_ALREADY_PAID`)，其余「该订单无需支付」(`ORDER_NOT_PAYABLE`)；订单不存在 → 404 `ORDER_NOT_FOUND`；他人订单 → 403。

### 3.3 CI-105 `POST /app/pay/refund`（仅演示直付单）

```bash
curl -s -X POST "http://127.0.0.1:8081/app/pay/refund" \
  -H "Content-Type: application/json" -H "Authorization: Bearer <user-jwt>" \
  -d '{"orderId":"800","amount":9900,"reason":"七天无理由"}'
```

```json
{ "code": 200, "msg": "OK", "data": { "refundId": "1", "status": "processing" } }
```

- `amount` 为**整数分**（`9900` = 99.00 元）；落库 `litemall_refund.amount` 仍为元（`99.00`）、`status=1`（退款成功）、`refund_time=now`。
- **不再回调 order**（`OrderClient.markRefunded` 停用，CX-06 退役）；`status` 保留 `processing`（P-27）。
- 未支付 → 409「订单未支付，不允许退款」；已退款/售后链路 → 409「该订单已进入退款流程，请通过售后台处理」；金额 > 实付 → 400。

### 3.4 CI-106 `GET /app/pay/result`

```bash
curl -s "http://127.0.0.1:8081/app/pay/result?orderId=800" \
  -H "Authorization: Bearer <user-jwt>"
```

```json
{ "code": 200, "msg": "OK", "data": {
  "orderId": "800", "status": "paid", "result": "success",
  "orderStatus": 102, "orderStatusText": "待发货",
  "payTime": "2026-11-01 10:00:00", "payId": "wxmock800", "expireTime": null
} }
```

- `result`：`paid→success`、`refunded→fail`、其余 `pending`（R1 不落支付失败态）。
- `orderStatusText`：101 未付款 / 102 待发货 / 201 已发货 / 301 已收货 / 501 已完成 / 103 已关闭 / 401 退款中 / 402 已退款。
- `status` 兼容保留：`unpaid`/`paid`/`refunded`；103 已关闭归 `unpaid`（修正原默认 `paid` 的缺陷）。
- `expireTime` 目前恒 `null`：`OrderPayInfoDTO` 尚无 `expire_time` 字段（见 §5）。

### 3.5 CN-IN-02 `POST /inner/pay/refund`（order → pay）

```bash
curl -s -X POST "http://127.0.0.1:8081/inner/pay/refund" \
  -H "Content-Type: application/json" -H "X-Internal-Token: <token>" \
  -d '{"aftersaleId":"9001","orderId":"800","amount":9900,"reason":"退货退款","operatorId":"1"}'
```

```json
{ "refundId": "2", "status": 1, "statusText": "退款成功", "refundTime": "2026-11-01 10:05:00" }
```

- 裸类型、非 200 HTTP、不带 `errorCode`（`/inner/**` 口径）。
- 幂等键 `aftersaleId`：已有 `status=1` 行 → 200 + 同 `refundId`；同键存在非 1 行且金额不同 → 409 `IDEMPOTENT_CONFLICT`；`orderId` 不存在 → 404；`amount≤0`/缺 `aftersaleId` → 400。
- 不回调 order（DR-17），order 拿到 `status=1` 后本地置「退款完成」并回写 `aftersale.refund_id`。

### 3.6 CX-01 `GET /app/market/groupon/list` 已下线

```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://127.0.0.1:8081/app/market/groupon/list"
# => 404
```

路由直接删除，不做 410；前端 `src/api/market.js` 中对应函数由 fe-mobile 同步移除。

### 3.7 一人一券（08 §4.2）

| 场景 | 期望 |
| --- | --- |
| 台账已有该券（含已使用/已过期/软删） | 400「你已领过这张券」，不消耗 Redis 限领额度 |
| 并发最后 1 张 | 仅 1 人成功，其余「优惠券已领完」 |
| 落库撞 `uk_coupon_user` | 结果文案「你已领过这张券」（不再「系统异常，领取失败」），且库存按台账回补、DB `total` 回补 |

## 4. 单测清单

`PayServiceImplTest`（25）：原有 14 例中 3 例因金额改分/状态改 1/停止回调 order 做了最小化调整（见 §5），其余 11 例断言未动；新增 11 例——CI-103 `mode=fail`/缺省 `success`/非法 `mode`、CI-106 `result`+`orderStatusText`+103→`unpaid`+`pending`、CN-IN-02 首次退款落库/幂等同 `refundId`/409 金额冲突/404 订单不存在/参数 400。

`CouponUsableCouponsTest`（9）：CN-19 可用面额、门槛不足「差 72.00 元可用」、未到使用时间、排序、空数组、参数 400、未登录 401；一人一券领取前拦截（不触 Redis）、落库撞唯一键转文案并回补库存。

`CouponServiceImplTest`（9）：原有用例全部保持，断言未修改（新增的一人一券预检在 Mockito 下 `selectCount` 默认返回 `0L`，不影响原分支）。

## 5. 与冻结口径的差异 / 待接线项（已上报 captain）

1. **`OrderPayInfoDTO` 缺 `expire_time`**：`CI-103` 的 `ORDER_EXPIRED` 判定与 `CI-106` 的 `expireTime` 无法在本任务实现，当前分别留 TODO 注释与 `null`。该 DTO 在 03 卡属 **R1-02 可改清单**，字段到位后本侧二次接线（约 3 行）。
2. **域内 `errorCode` 已接线**：R1-02 的 `ConflictException`/`NotFoundException`、`CommonException.errorCode`、`BadRequestException.PARAM_INVALID`、`Result.error(CommonException)` 与「对外恒 200 / `/inner/` 保留非 200」的 advice 已在工作区落地，本侧直接消费，未改动公共层任何文件：
   - CI-103：`ORDER_ALREADY_PAID` / `ORDER_NOT_PAYABLE`（409）、`ORDER_NOT_FOUND`（404）、`PARAM_INVALID`（400）；`ORDER_EXPIRED` 见第 1 条。
   - CI-105：`ORDER_STATUS_ILLEGAL`（409，含「请通过售后台处理」文案）、`PARAM_INVALID`（400，金额 ≤0 / > 实付）、`ORDER_NOT_FOUND`（404）。
   - CN-IN-02：`IDEMPOTENT_CONFLICT`（409）、`RESOURCE_NOT_FOUND`（404，D-22）、`PARAM_INVALID`（400）。
   - CN-19 / 领券：`PARAM_INVALID`（400）；「你已领过这张券」不带域内码（`COUPON_*` 属 R2）。
3. **既有单测断言最小化改动**（与任务卡「既有单测断言不得修改」冲突，按 07 §5.3 冻结契约优先）：
   - `refund_paidOrder_createsRefund`：金额断言 `BigDecimal("99.00")`（改为分→元）、`status` 0 → 1、补 `refundTime` 非空、`markRefunded` 由 `verify(...)` 改 `never()`（CX-06 ①）；
   - `refund_unpaidOrder_rejects`、`wechatPay_alreadyPaid_rejects`：`BadRequestException` → `CommonException` + `assertEquals(409, code)`（03 卡 §4.4 冻结 409）；
   - `wechatPay_orderNotExist_rejects`：`BadRequestException` → `CommonException` + 404 `ORDER_NOT_FOUND`（契约 §4.4）；
   - helper `refundDto(orderId, String amount, reason)` → `refundDto(orderId, int amountCents, reason)`（`RefundDTO.amount` 改 `Integer` 分，P-27）。
4. **构建环境缺陷（已就地修复，需全队知会）**：本地 `~/.m2` 里残留 M16 拆分前的 `litemall-common-0.0.1-SNAPSHOT.jar`（该模块现为 `pom` 聚合层，但 jar 仍被 `litemall-api` 以 jar 类型解析），其中的 `com/litemall/exception/*.class` 是旧版，且 classpath 顺序在 `litemall-common-core` 之前，会**遮蔽** `common-core` 的新异常类（表现：`BadRequestException.PARAM_INVALID` 找不到符号）。已从该本地 jar 中移除 7 个 `com/litemall/exception/*.class`（仅本地缓存，不涉仓库文件），此后 `-pl litemall-marketing,litemall-pay -am` 编译通过。建议仓库侧根治：消费方对该依赖显式 `<type>pom</type>`，或 CI 前清理本地仓库中的旧 `litemall-common` jar。

## 6. 未做（明确越界）

- 不修改 `litemall-goods` / `litemall-order` / `litemall-admin`、`OrderClient`/`GoodsClient`、`CommonExceptionAdvice` 与异常包、`common.yaml`（单一所有者见 03 卡 §6.1）。
- 不修改前端 `src/api/market.js`（fe-mobile 属主）。