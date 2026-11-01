# R1-08 端到端验收报告（R1 十三步 + 30 分钟回归 + AI 入口 + 构建门禁）

> 验收人：qa-verify（独立于实现）　｜　验收时间：首轮 2026-11-01 18:08–18:20；复验 18:28–18:36（§10）；**D3 定向复验 18:42–18:48（§11）**　｜　验收对象：工作区当前 HEAD（未提交改动 = R1 实现期全部改动）
> 依据：`需求文档.md` §6.1（`:465-486`）、`docs/impl/03-R1实施口径.md`（下称 03 口径）、`docs/design/07-接口契约-订单售后支付.md`（下称 07 契约）、R1-00 冻结的四条典型缺陷
> 原则：只读验收，不改实现、不改测试；以下所有输出均为真实运行结果（日志/DB/HTTP 原始返回）。

---

## 0. 结论：**通过**

> **最终判定（§11，D3 修复后定向复验）**：D1 / D2 / D3 **三项阻塞全部关闭**，13 步 + 30 分钟关单 + 回归断言 **76/76 PASS**，构建门禁 **18/18 模块 SUCCESS、312 例 Failures 0 / Errors 0**，AI 入口 7/7 PASS。R1-08 **判通过**。

| 编号 | 级别 | 状态 | 问题 | 定位 |
|---|---|---|---|---|
| **D1** | 阻塞（业务） | ✅ **已关闭**（t11 修复，§10.2 复验） | 全额退款订单曾出现在 C 端「已完成」tab（R1-00 冻结缺陷②）；`finished` 分支误用 `notExists` | `backend/litemall-next/litemall-order/src/main/java/com/litemall/service/impl/OrderServiceImpl.java:451-454`（现为 `exists`） |
| **D2** | 阻塞（门禁） | ✅ **已关闭**（t12 修复，§10.1 复验） | `mvn verify` 曾红：`litemall-admin` 2 个用例断言「HTTP 400」，与 03 口径「对外恒 200」冲突 | `backend/litemall-next/litemall-admin/src/test/java/com/litemall/controller/AdminDtoValidationTest.java:84-103` |
| **D3** | 阻塞（契约/运营可用性） | ✅ **已关闭**（t14 代码 + t15 文档，§11 复验） | 后台订单列表曾默认**升序（最旧在前）**，与 07 契约 P-11「订单列表 `addTime desc`（新单在前）」相反；待发货超 20 单时新单落末页，运营漏单（S5 即因此失败） | 实现 `backend/litemall-next/litemall-admin/src/main/java/com/litemall/controller/OrderController.java:121`（现 `AdminQuerySupport.bool(isAsc, false)`）；契约 `docs/design/07-接口契约-订单售后支付.md:46`、`:312`、`:1531` ＋ 06 §3.1 例外登记 |

结论口径：`需求文档.md` §6.1「不通过判定」（`:485`）未被触发；首轮 D1/D2、复验轮 D3 三项阻塞均已修复并在 §10/§11 逐项复验关闭；最终数字 —— **76/76 断言 PASS、312 例全绿（18/18 模块 BUILD SUCCESS）、D1/D2/D3 全关闭**。

---

## 1. 验收环境与命令

| 项 | 值 |
|---|---|
| JDK / Maven / Node | Java 25.0.2（`D:\jdk`）／ Maven 3.9.16 ／ Node v24.14.0 |
| 后端 | 7 个服务全部由 `bash .dbwork/r1-08-start-backend.sh` 启动，jar 构建于 18:00–18:01（R-1 修复后的全量 `clean install`） |
| 端口 | gateway 8000 / user 8001 / goods 8002 / marketing 8003 / order 8004 / pay 8005 / admin 8008 |
| 前端 | `frontend/mobile-web` `npm run dev` @ 5173（仅 AI 入口 UI 检查期间） |
| DB / Redis | MySQL `litemall`（127.0.0.1，root/123456）／ Redis 6379 |
| 验收脚本 | `.dbwork/r1-08-acceptance.py`（13 步 + REG-30 + 冻结缺陷 4 项，真实 HTTP + 真实 DB 断言） |
| UI 脚本 | `.dbwork/r1-08-ui-ai.mjs`（CDP headless，390×844） |
| 构建门禁 | `cd backend/litemall-next && mvn -o -B verify`（串行，验收期间无其它构建/服务占用 target） |

7 端口健康检查与冒烟（起服后）：`gateway/user/goods/marketing/order/pay/admin` 健康接口全部 HTTP 200；admin 登录 200；`/admin/order/99999999` → HTTP 200 + code 404 + `errorCode=ORDER_NOT_FOUND`；`/inner/**` 无令牌 → 401。

---

## 2. 十三步结果（`需求文档.md` §6.1）

脚本输出：**75/76 断言通过**，唯一失败即 D1（见 §4）。13 步对应断言 **55/55 通过**。

| 步 | 操作 | 判定（需求 §6.1） | 结果 | 关键证据（真实输出） |
|---|---|---|---|---|
| 1 | 后台新建 T 恤，黑色/L 99 元 10 件、白色/M 99 元 8 件 | 列表出现、展示价 99、两规格各一行 | ✅ | `goodsId=1181022`，`displayPrice=9900`，`skuCount=2 totalStock=18`；详情 `specs=[颜色:黑色/白色, 尺码:L/M]`，SKU 矩阵 `387 黑色/L sellable=10`、`388 白色/M sellable=8` |
| 2 | C 端搜「T 恤」→ 详情 → 选黑色/L | 价格/库存随规格变化；未选规格不可加购 | ✅ | 搜索命中 3 行含目标商品；黑色/L `(price,sellable)=(9900,10)`、白色/M `(9900,8)`；无 specId 加购 → `code=400 msg=规格ID不能为空 errorCode=PARAM_INVALID` |
| 3 | 加购黑色/L，再加购白色/M | 购物车两行、规格文字可辨 | ✅ | `cart rows=[{id:48,spec:"黑色 / L"},{id:49,spec:"白色 / M"}]` |
| 4 | 结算 → 用一张券 → 提交 → 演示支付成功 | 待发货、券后金额正确、演示模式标注可见 | ✅ | `goodsAmount=19800 couponAmount=500 orderAmount=19300`；`orderId=37`；支付 `demo=true payResult=success`；订单 `status=undelivered`，`couponAmount=500` |
| 5 | 后台订单列表 | 在待发货、金额与 C 端一致 | ✅ | 后台 `status=undelivered` 命中该单，`orderAmount=19300`（与 C 端一致），`statusText=待发货` |
| 6 | 发货，填物流公司与单号 | C 端显示已发货与单号 | ✅ | `status=shipped shipChannel=顺丰速运 shipSn=SF1080000001` |
| 7 | C 端确认收货 | 已收货，出现申请售后入口 | ✅ | `status=confirmed`；两行 `canApply.types=[1,2]`，黑色/L 行 `actualAmount=9650 couponShare=250` |
| 8 | 对黑色/L 行申请退货退款 + 1 张凭证 | 生成售后单，进度申请中 | ✅ | `aftersaleId=11 amount=9650 type=2(退货退款) status=applied`；C 端我的售后可见；支付实扣后 `黑色/L=9、白色/M=7` |
| 9 | 后台同意给地址 → 填单号 → 确认收货 → 退款完成 | 售后完成；订单行已退款；**主状态仍已收货** | ✅ | `waitReturn`（退货地址+截止 7 天）→`waitReceive`→`refunding`→`completed refundAmount=9650 restockStatus=1`；行 `refundStatus=2`；订单 `status=confirmed fullyRefunded=false refundedAmount=9650` |
| 10 | 后台库存视图两行规格 | 黑色/L 实际与可售 9→10；白色/M 仍 7 | ✅ | `黑色/L number=10 sellable=10 locked=0`；`白色/M number=7 sellable=7`（回补到**正确规格**） |
| 11 | 新单不付款 → 后台「立即关单」 | 已关闭；锁量释放、券回未使用；日志记录 | ✅ | `orderId=38`→`status=103 closeType=3`；`locked=0 number=10`；券 `status=0 orderId=None`；日志 `{id:438, admin:"1", action:"order:close", target_id:38}` |
| 12 | 同形态未付款单手动取消 | 与第 11 步一致 | ✅ | `orderId=39`→`status=103 closeType=1`；`locked=0`；券释放；对比 `S11 103/0/0 == S12 103/0/0` |
| 13 | 演示支付失败分支 | 仍未付款，可重试 | ✅ | `pay wechat mode=fail → payResult=fail`，订单 `unpaid`，`/app/pay/result status=unpaid`；重试成功后 `status=undelivered`、`payResult=paid` |

> 断链数 = 0：以上每步判定均由脚本对真实接口返回值 + 真实 DB 行断言，无「只能刷新」「空白页」「只显示错误码」项。

---

## 3. BR-ORDER-06「30 分钟自动关单」回归（时间篡改等价取证）

不等待真实 30 分钟：直接把订单的 `add_time/expire_time` 回拨 31 分钟（等价于系统时间前进 31 分钟），再触发后台批量关单接口。

| 断言 | 结果 | 证据 |
|---|---|---|
| 时间篡改后该单成为超时单 | ✅ | `orderId=41` 原 `expireTime=18:43:27` → 改后 `17:42:26`，`locked=1`，券 `status=1` |
| `dryRun=true` 预演命中且不落库 | ✅ | `{"succeeded":1,"failed":0,"total":1,"items":[{"id":"41","ok":true,"reason":"dryRun：未落库"}]}` |
| 批量关单转「已关闭」(103, close_type=2) | ✅ | `status=103 closeType=2`，`{"succeeded":1,"failed":0}` |
| 与第 11 步同一套释放：锁量 0、券回未使用、日志 | ✅ | `locked=0 coupon.status=0 coupon.orderId=None`，日志 `{action:"order:close-expired", target_id:41}` |
| 已关闭订单不可再支付（CI-103） | ✅ | `code=409 errorCode=ORDER_NOT_PAYABLE`，订单仍 103 |

---

## 4. R1-00 冻结四缺陷回归

| # | 冻结口径 | 结果 | 证据 |
|---|---|---|---|
| ① | 后台 `status=closed` 不得静默返回全部订单 | ✅ | `code=400 msg=status 取值非法：closed errorCode=PARAM_INVALID rows=0`；`status=canceled` 返回 17 行、全部 `canceled`、含 S11/S12 两单 |
| ② | 全退款订单不得出现在「已完成/待评价」 | ❌ **D1** | 见下 |
| ③ | `/inner/**` 失败保留非 2xx + errorCode | ✅ | 无令牌 → 401（鉴权层，无 errorCode，已知边界）；业务/类型错误 → `400 code=400 errorCode=PARAM_INVALID` |
| ④ | CI-97 重复回调 200 + `applied:false`（不得 409） | ✅ | 首发 `200 applied=true deducted=true`，订单 102、库存 9→8；重复 `200 applied=false deducted=false`，库存仍 8（不重复扣） |

### D1（阻塞）：全退款订单进入「已完成」tab —— 判定条 SQL 语义写反

**现象**：订单 37（主状态 301 已收货，两行均 `refund_status=2`，`fullyRefunded=true`、`refundedAmount=19300`）出现在 `/app/order/list?tab=finished`（`finished` 命中 `[37]`），而同单在 `tab=unreviewed` 正确为 0。

**定位**：`backend/litemall-next/litemall-order/src/main/java/com/litemall/service/impl/OrderServiceImpl.java:451-454`

```java
case "finished" -> qw.and(w -> w.eq(Order::getOrderStatus, OrderStatus.COMPLETED)
        .or(x -> x.eq(Order::getOrderStatus, OrderStatus.CONFIRMED)
                .notExists(SQL_NOT_FULLY_REFUNDED)     // ← 语义反了
                .notExists(SQL_UNREVIEWED)));
```

常量（同文件 `:188-190`）：

```java
SQL_NOT_FULLY_REFUNDED = "SELECT 1 FROM litemall_order_goods og WHERE og.order_id=litemall_order.id
                          AND og.deleted=0 AND og.refund_status <> 2"
```

即该 SQL 是「**存在未全退行**」= `NOT fullyRefunded`。因此 `notExists(NOT fullyRefunded)` = `fullyRefunded`，`finished` 被写成了「只收全退款单、把正常已评价单排除」。

**实际生成的 SQL**（MySQL general_log 抓取）：

```sql
... AND (order_status = 501 OR (order_status = 301
      AND NOT EXISTS (SELECT 1 FROM litemall_order_goods og
                      WHERE og.order_id = litemall_order.id AND og.deleted = 0 AND og.refund_status <> 2)
      AND NOT EXISTS (...)))
```

**口径依据**：03 口径 `docs/impl/03-R1实施口径.md:130`（`finished` = `501` 或 `301` 且 `NOT fullyRefunded` 且 `NOT EXISTS(未评价行)`）、`:136`（必须显式排除 fullyRefunded）、`:139`（`fullyRefunded = NOT EXISTS(og.deleted=0 AND og.refund_status<>2)`）。

**修复建议**（单行，勿连改）：`finished` 分支把 `notExists(SQL_NOT_FULLY_REFUNDED)` 改为 `exists(SQL_NOT_FULLY_REFUNDED)`；**`:455-456` 的 `aftersale` 分支用 `notExists` 是正确的（=完全退款单也在售后 tab 可见），不要一起改。**

复验方式：构造一张两行全退的 301 订单，`GET /app/order/list?tab=finished` 不含该单、`tab=aftersale` 含该单，且一张「已评价且无退款」的 301 订单出现在 `finished`。

---

## 5. AI 助手入口（BR-AI-01，仅前端）

脚本 `.dbwork/r1-08-ui-ai.mjs`，真实 Chrome headless 390×844：

| 断言 | 结果 | 证据 |
|---|---|---|
| 首页可渲染 | ✅ | `cards=20` |
| 首页无「AI/助手/智能」入口 | ✅ | `body.innerText` 无匹配（`hasAiText=false, aiMatches=[]`） |
| 底部菜单不含 AI/助手 | ✅ | `tabs=["首页","消息","购物车","我的"]` |
| 菜单/各 tab 页均无 AI 入口 | ✅ | 逐 tab 点击后 `hasAi=false`（消息页跳登录，同样无） |
| 直接访问 `/assistant` 提示未启用 | ✅ | 文案「AI 助手／AI 助手未启用／R1 阶段不提供 AI 对话能力……本页为直达占位页：不会返回任何模拟回答，也不会调用对话接口」 |
| 无对话输入、无假回答气泡 | ✅ | `inputs=0 bubbles=0` |
| 无 AI 后端能力 | ✅ | `POST /app/assistant/chat` → 404 |

截图：`docs/evidence/r1-08/ai-01-home.png`、`docs/evidence/r1-08/ai-02-assistant-disabled.png`。

---

## 6. 构建与测试门禁 `mvn -o -B verify`：首轮 **红**（D2，已由 t12 修复，复验转绿见 §10.1）

```bash
cd backend/litemall-next && mvn -o -B verify
```

- 结果：**exit=1**，`Total time: 03:01 min`；18 个 reactor 模块中 **17 SUCCESS、`litemall-admin` FAILURE**。
- 用例统计（surefire XML 汇总）：**38 个测试类 / 312 个用例，失败 2、错误 0、跳过 0**（order 域 148 例全绿；admin 58 例中 2 例失败）。

失败用例（`litemall-admin/target/surefire-reports/com.litemall.controller.AdminDtoValidationTest.txt`）：

```
login_blankUsername_returns400 -- java.lang.AssertionError: Status expected:<400> but was:<200>
  at AdminDtoValidationTest.java:90
login_blankPassword_returns400 -- java.lang.AssertionError: Status expected:<400> but was:<200>
  at AdminDtoValidationTest.java:101
```

**定性**：不是实现回退，而是**测试断言过期**。03 口径 `:51`、`:307`、`:323` 明确「对外端点（`/app/**`、`/admin/**`）异常响应 `ResponseEntity.ok(...)` → HTTP 恒 200，body 携带真实 `code`；`/inner/**` 保留非 200」；`CommonExceptionAdvice.processResponse` 正是这样实现的（`litemall-common-web/src/main/java/com/litemall/advice/CommonExceptionAdvice.java:93-104`），运行时也验证过（`/admin/order/99999999` → HTTP 200 + code 404 + `errorCode=ORDER_NOT_FOUND`）。

**为什么 17:44 的全量 verify 是「假绿」**：修复前 `litemall-common` 以 jar 形式遮蔽了 `common-web` 的新 advice，admin 测试拿到的是旧 advice（异常返回 400），所以这两个用例当时通过（`AdminDtoValidationTest` 14 例 0 失败）。R-1（`7c9b6ff`）把 `litemall-common` 改为 `<type>pom</type>` 后真实 advice 生效，才暴露这两个断言仍按旧行为写。

**修复建议（D2）**：把这两个用例改为断言冻结口径 —— `status().isOk()` + `jsonPath("$.code").value(400)` + `jsonPath("$.data.errorCode").value("PARAM_INVALID")`（`@DisplayName` 里「不再是 200+错误码」的旧说法一并更新）。不要为了迁就测试把 advice 改回 400（那会破坏 `/app` `/admin` 的统一口径与前端「只判 code」的前提）。

---

## 7. 信息项（实测观察，不判缺陷）

1. **CI-97 成功出参是裸 DTO**：`/inner/order/pay/success` 返回 `{"applied":true,"deducted":true,"shortage":[]}`，而 07 契约 §6.3 示例是 `{code,msg,data:{...}}`。`OrderInnerController` 所有 inner 成功端点均返回裸 DTO（Feign 约定，错误路径才带 `Result` envelope）。行为与冻结缺陷④一致，**建议在 07 §6.3 注明「inner 成功出参为裸 DTO」**，或统一包 envelope；属文档/实现口径差异，不影响链路。
2. **CI-85 用户取消不写 `order:close` 日志**：07 契约 `:412-426` 只要求「与超时同一套释放」，日志要求仅出现在 CN-10/CN-11（后台关单 / 超时关单）。实测取消单 `close_type=1`、锁量与券均释放，与第 11 步结果一致，判定通过；日志缺失不判缺陷。
3. **`/inner/**` 无令牌 401 不带 `data.errorCode`**：属鉴权层（网关/拦截器）拒绝，03 口径已知边界，不计入「对外异常必须有 errorCode」。
4. **全退款订单在后台「已收货」主状态列表仍可见**：符合 03 口径 `:130`「全退/部分退只体现为订单级 `fullyRefunded`，主状态不变」，且后台列表带 `fullyRefunded/refundedAmount` 字段。
5. **领券限频**：`CouponServiceImpl.java:127-132` 按 `coupon:receive:<couponId>:<userId>` 60 秒防刷；发券走 MQ 异步，`receive` 返回 `PROCESSING`，`mylist` 需轮询（本轮实测 ≤4 秒到账）。验收脚本因此只领 1/2 号券并轮询，S11/S12/REG-30 复用 2 号券（关单即释放）。

---

## 8. 复验清单（修复后按序执行）

```bash
# 1) 服务全部停掉（避免 target jar 占用）后重打
cd D:/litemall-next/backend/litemall-next
mvn -o -B -DskipTests install -pl litemall-api
mvn -o -B -DskipTests package -pl litemall-order litemall-admin
bash D:/litemall-next/.dbwork/r1-08-start-backend.sh     # 7 端口

# 2) 13 步 + 冻结缺陷回归（应 76/76）
cd D:/litemall-next && python .dbwork/r1-08-acceptance.py

# 3) AI 入口（应全 PASS）
cd D:/litemall-next/frontend/mobile-web && npm run dev &   # 5173
cd D:/litemall-next && node .dbwork/r1-08-ui-ai.mjs

# 4) 构建门禁（应 18/18 SUCCESS、312/312 用例通过）
cd D:/litemall-next/backend/litemall-next && mvn -o -B verify
```

判据：`acceptance-result.json` 中 `failed=0`；`mvn verify` 的 Reactor Summary 全 SUCCESS；`AdminDtoValidationTest` 14 例 0 失败。

---

## 9. 证据文件

| 文件 | 内容 |
|---|---|
| `docs/evidence/r1-08/acceptance-log.txt` | 13 步 + 回归的逐条 PASS/FAIL 与原始返回值（**当前内容为复验轮，2026-11-01 18:32 起；首轮 18:13 的同名文件已被复验覆盖**，首轮结论见 §2/§4） |
| `docs/evidence/r1-08/acceptance-result.json` | 机器可读断言结果（**当前内容为复验轮：total=74, passed=73, 失败=S5→D3**） |
| `docs/evidence/r1-08/ai-01-home.png` / `ai-02-assistant-disabled.png` | 首页无 AI 入口 / `/assistant` 未启用落地页 |
| `.dbwork/r1-08-acceptance.py` / `.dbwork/r1-08-ui-ai.mjs` | 可复跑的验收脚本（只读实现，直连 HTTP + DB + Redis） |
| `.dbwork/r1-08-verify.log` | 首轮 `mvn -o -B verify` 全量输出（17/18，admin 红） |
| `.dbwork/r1-08-verify-round2.log` | 复验 `mvn -o -B verify` 全量输出（18/18 SUCCESS，BUILD SUCCESS，03:02 min） |
| `.dbwork/r1-13-recheck.py` | D1 定向复验脚本（订单 37 在 finished/aftersale/unreviewed 三 tab 的出现情况） |
| `.dbwork/r1-13-count-tests.py` | surefire 报告汇总脚本（38 类 / 312 例 / 0 失败） |
| `docs/evidence/r1-08/sort-recheck.json` | §11.2 默认/显式排序 7 条断言结果（18:47） |
| `docs/evidence/r1-08/admin-order-default-sort.png` / `.json` | §11.3 后台「订单管理」默认路径首屏截图与页面/接口对照数据 |
| `.dbwork/r1-16-order-sort.py` / `.dbwork/r1-16-admin-ui-sort.mjs` | §11 定向复验脚本（接口排序断言 / 真实浏览器默认路径抽样） |
| `backend/litemall-next/litemall-admin/target/surefire-reports/` | 首轮 D2 的失败用例明细 |

---

## 10. 复验记录（2026-11-01 18:28–18:36，D1/D2 修复后）

前置：D1 由 t11 修复（`OrderServiceImpl.applyTab` 的 `finished` 分支 `notExists` → `exists`，`aftersale` 分支与两个 SQL 常量未动）；D2 由 t12 修复（`AdminDtoValidationTest:84-103` 两条 login 用例改按 R1 契约断言 `HTTP 200 + code=400 + data.errorCode=PARAM_INVALID`，main 代码未动）。复核代码确认改动与任务书一致后开始复跑。环境同 §1；7 服务与 5173 均由本轮自行起停。

### 10.1 构建门禁：**绿**（原 D2 关闭）

命令：`cd backend/litemall-next && mvn -o -B verify > .dbwork/r1-08-verify-round2.log`

```
[INFO] Reactor Summary for litemall-next 1.0.0:
... 18 行全部 SUCCESS，含 [INFO] litemall-admin ......... SUCCESS [ 19.389 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  03:02 min
```

- **18/18 模块 SUCCESS，Failures 0 / Errors 0**；surefire 报告汇总（`.dbwork/r1-13-count-tests.py`）：**38 个测试类 / 312 个用例 / failures=0 / errors=0 / skipped=0**。
- 分模块用例数：order 14 类 148、admin 5 类 58、pay 2/26、goods 4/23、marketing 3/19、message 2/8、storage 2/8、gateway 2/7、common-jwt 1/6、common-cache 1/4、common-web 1/4、user 1/1（合计 312）。
- 首轮失败的 `AdminDtoValidationTest` 14 例本轮全绿，`isBadRequest/is4xx` 类断言在 admin 测试中已无残留（t12 已 grep 确认）。

### 10.2 D1 定向复验（`.dbwork/r1-13-recheck.py`）：**已修复**

前置事实：订单 37 = `user_id 43`（用户名 `qa003317`）、`order_sn 202611011813253fa391e0`、`order_status 301`、两行 `litemall_order_goods`（40/41）均 `refund_status=2 refunded_number=1 comment=0`（即 fullyRefunded=true）。

| 请求（用户 JWT） | 实测 | 期望 | 判定 |
|---|---|---|---|
| `GET /app/order/list?tab=finished` | `total=0`，`ids=[]`（**不含 37**） | 不含全退单 | PASS |
| `GET /app/order/list?tab=aftersale` | `total=1`，`ids=['37']`，`orderSn=202611011813253fa391e0` | 仍含该单（03 口径 `:126`） | PASS |
| `GET /app/order/list?tab=unreviewed` | `total=0`，`ids=[]` | 不含全退单 | PASS |

R1-00 冻结缺陷②（全退款订单不得出现在「已完成/待评价」）**已关闭**。

### 10.3 断言重跑（`python .dbwork/r1-08-acceptance.py`）：73/74，唯一失败 S5

- 13 步主链路 S1–S4、S6–S13 全 PASS；S4 券后 `orderAmount=19300`（券 500）、S8 黑/L 扣 9 白/M 扣 7、S9 退款后 `refundStatus=2` 且主状态仍 confirmed、S10 库存回滚 `10/7`、S11 关单 103/closeType=3、S12 取消 103/closeType=1、S13 支付失败后重试 paid、REG-30 时间篡改 31 分钟关单 closeType=2 且券释放 —— 与首轮一致。
- **唯一 FAIL：S5「后台待发货列表含该单」**，`GET /admin/order/list?status=undelivered&pageNum=1&pageSize=20` 返回 20 行，本轮新单（`orderId=43`）不在其中；改为 `isAsc=false` 后第 1 页即命中，`?orderSn=20261101183204cb494bb2` 精确检索也能命中（`total=1, id=43`）→ 数据齐全，问题在默认排序（见 10.4）。S5 失败使其 3 条从属断言跳过，故本轮总数为 74（首轮 76）。
- 本轮 context：商品 `1181023`、新单 `orderId=43`、SKU `397/398`、关单 `44`、取消 `45`、支付重试 `46`、REG-30 `47`、售后 `13/14`；REG-2 全退款单 43 已不在 finished/unreviewed（PASS），后台 confirmed 列表仍含该单（主状态不变，符合 03 口径 `:130`）。

### 10.4 新缺陷 D3：后台订单列表默认升序（与 P-11 相反）

- **实际**：`GET /admin/order/list?pageNum=1&pageSize=20`（不传 sortBy/isAsc）→ `total=23`，`ids=[4,12,13,...,40]`（最旧在前，`42/46/48` 在第 2 页）；显式 `?isAsc=false` → `ids=[48,46,42,40,36,...]`（新单在前）。
- **期望**：07 契约 `docs/design/07-接口契约-订单售后支付.md:46` 探针 P-11「端点可自定默认排序：订单列表 `addTime desc`（§2.3、§3.1）」；`:312` 参数表「`isAsc` 默认 `false`（订单列表新单在前）」；`:1531` P-11「✅ 已裁定」。
- **定位**：`backend/litemall-next/litemall-admin/src/main/java/com/litemall/controller/OrderController.java:118` `AdminQuerySupport.bool(isAsc, true)`（javadoc `:91` 同写「默认 true」）。同文件 `:91` 注释与本行默认值均需一并改，否则后人再按注释改回去。
- **影响面**：`frontend/admin-web/src/views/order/OrderList.vue:136-144` 的 `params` 从不传 `sortBy/isAsc` → 后台「订单管理」默认按最旧在前展示；`status=undelivered` 超过 20 单时新单落在末页，运营按默认列表发货会漏单（S5 即此场景）。C 端 `/app/order/list` 默认倒序正常（`ids=[42,40,39,38,37,41]`），不受影响。
- **复现命令**（服务已起、admin 登录取 token 后）：
  ```bash
  curl -s -H "Authorization: Bearer $TOKEN" \
    "http://127.0.0.1:8008/admin/order/list?pageNum=1&pageSize=20" | jq '.data.list[].id'
  # → 4,12,13,...，新单在末页
  curl -s -H "Authorization: Bearer $TOKEN" \
    "http://127.0.0.1:8008/admin/order/list?pageNum=1&pageSize=20&isAsc=false" | jq '.data.list[].id'
  # → 48,46,42,...，新单在前
  ```
- **收敛路径（二选一，交 captain/架构侧裁定）**：
  1. **改实现**（推荐，1 行）：`OrderController.java:118` → `AdminQuerySupport.bool(isAsc, false)`，同步 `:91` javadoc「默认 false（订单列表新单在前，P-11）」，并在 `litemall-admin` 侧补/改一条单测锁定默认倒序。
  2. **改契约**：若认定后台沿用 06 总则默认 `true`（07 `:487-488` 确有「`isAsc` 同 06 §3.1（默认 true）」的表述，与 `:46/:312/:1531` 自相矛盾），则由架构侧统一 07 内部冲突并明确后台订单列表默认顺序——但即便如此，运营可用性问题仍在（新单沉底）。
- 本条**不属于**「测试断言与契约冲突」：验收断言写的是 P-11 的明示裁定，实现未按裁定执行。

### 10.5 回归三条（本轮点名项）：全 PASS

| 项 | 实测 |
|---|---|
| 后台 `status=closed` 不得静默返回全部 | `http=200 code=400 msg=status 取值非法：closed errorCode=PARAM_INVALID rows=0`；`status=canceled` 20 行全为 `canceled` 且含 S11/S12 单 → PASS |
| `/inner/**` 失败非 2xx + errorCode | 无令牌 `http=401 {"code":401,"msg":"内部接口鉴权失败","data":null}`（鉴权层无 errorCode，已知边界）；业务失败/类型错误均 `http=400 code=400 errorCode=PARAM_INVALID` → PASS |
| CI-97 重复回调 | 首发 `http=200 applied=true deducted=true orderStatus=102`（库存 9→8）；重复 `http=200 applied=false deducted=false`（非 409），库存仍 8（只扣 1 次）→ PASS |

### 10.6 AI 入口（BR-AI-01）：7/7 PASS

`npm run dev` @5173 后 `node .dbwork/r1-08-ui-ai.mjs`：首页 `cards=20` 且 body 无「AI/助手/智能」文案、底部 tab `["首页","消息","购物车","我的"]`、逐 tab 点击 `hasAi=false`、直达 `/assistant` 文案含「AI 助手未启用…本页为直达占位页：不会返回任何模拟回答」且 `inputs=0 bubbles=0`、`POST /app/assistant/chat` → `404`；截图已覆盖 `docs/evidence/r1-08/ai-01-home.png`、`ai-02-assistant-disabled.png`。

### 10.7 环境恢复

5173（Vite）与 8000/8001/8002/8003/8004/8005/8008 七个后端端口均已停止，验收后无残留监听；未改任何业务/测试代码，未提交 git。

### 10.8 复验结论

| 复验项 | 结果 |
|---|---|
| ① 全仓 `mvn -o -B verify` | ✅ 18/18 SUCCESS，312 例，Failures 0 / Errors 0 |
| ② D1 定向（finished 不含 37 / aftersale 含 37） | ✅ PASS |
| ③ 76 条断言重跑 | ⚠️ 73/74，S5 失败 → **D3**（新缺陷） |
| ④ 回归三条 | ✅ PASS |

按任务口径「1 全绿且 2/3/4 无新失败才判通过」，因 ③ 出现 D3，**R1-08 本轮判不通过**，D3 修复（或契约裁定后调整断言）再复验即可转通过；D1/D2 已正式关闭。

> 后续：D3 由 t14（`OrderController` 默认 `isAsc=false` + 单测）、t15（07 v6 / 06 v2 文档统一）修复，**§11 定向复验 76/76 全绿，D3 关闭，总判定转「通过」**。

---

## 11. D3 定向复验（2026-11-01 18:42–18:48）→ **通过**

前置：D3 由 t14 改 `backend/litemall-next/litemall-admin/src/main/java/com/litemall/controller/OrderController.java:121`（`AdminQuerySupport.bool(isAsc, false)`）＋ javadoc `:93-94`／类注释 `:47-48`，并在 `backend/litemall-next/litemall-admin/src/test/java/com/litemall/controller/AdminOrderFeignTest.java` 补 `listDefaultsToAddTimeDescButHonoursExplicitAsc`；文档面由 t15 统一（`docs/design/07-接口契约-订单售后支付.md:497`、`docs/design/06-接口设计-总则与错误码.md:119` 例外登记、`docs/design/05-待用户确认清单.md:349`）。按任务口径**不再重跑全仓 verify**（已绿 18/18、312 例）。本轮复跑前源码复核：`:121` 为 `false`，javadoc 已写「默认 false（P-11）」。

### 11.1 断言全量重跑：**76/76 PASS**（S5 恢复）

命令：`python .dbwork/r1-08-acceptance.py`（7 服务全起：gateway 46828 / user 53516 / goods 2204 / marketing 54728 / order 29812 / pay 54148 / admin 50264）

```
==== 总计：76/76 全部通过 ====
→ 证据写入 docs/evidence/r1-08/acceptance-result.json（generatedAt 2026-11-01 18:43:11, total=76, passed=76）
→ 日志写入 docs/evidence/r1-08/acceptance-log.txt
```

S5 三条原子断言（此前失败项）本轮全 PASS：

| 断言 | 实测 |
|---|---|
| 后台待发货列表含该单 | `status=undelivered` 默认首页 20 行，命中本单 `id=49 / orderSn=20261101184308c5b52743 / addTime=2026-11-01 18:43:08 / orderAmount=19300` |
| 后台金额与 C 端一致 | `admin.orderAmount=19300 = C 端 19300`，`statusText=待发货` |
| 行状态为待发货标识 | `status=undelivered / statusText=待发货` |

其余轮次断言（13 步主链路、REG-30 时间篡改关单、REG-1/2/3/4/5）与 §10.3 一致，本轮无一失败；REG-2 全退款单仍不在 finished/unreviewed，后台主状态列表仍含该单（03 口径 `:130`）。

### 11.2 默认排序断言（新增）：7/7 PASS

脚本 `.dbwork/r1-16-order-sort.py`（admin 登录 → `GET /admin/order/list`），结果写入 `docs/evidence/r1-08/sort-recheck.json`：

| 断言 | 实测 |
|---|---|
| 默认列表有数据 | `total=54` |
| 默认 addTime 递减（新单在前） | 首 `2026-11-01 18:43:11` → 末 `2026-11-01 18:12:23`（20 行） |
| 默认首条 = 全局最新单 | 首条 `id=54 / orderSn=20261101184311c5bab293 / 18:43:11 / undelivered`；前 3 条 `54, 52, 51`（`18:43:11 / 18:43:10 / 18:43:10`） |
| `isAsc=true` 显式升序生效（未被写死） | 首 3 条 `1, 2, 3`（`2026-09-26 14:03:54` 起，最旧在前） |
| `isAsc=false` 与默认完全一致 | 默认首 3 `[54,52,51]` = 显式 desc 首 3 `[54,52,51]` |
| 三种调用 total 一致 | default 54 / desc 54 / asc 54 |
| 待发货默认首页均 undelivered 且 addTime 倒序（S5 场景） | `total=25`，首 `id=54 @18:43:11`，末 `17:07:48`，20 行全 undelivered |

### 11.3 前端默认路径抽样（真实浏览器）：5/5 PASS

`npm run dev`（admin-web @5174，Vite 8.3.0）＋ `.dbwork/r1-16-admin-ui-sort.mjs`（CDP headless 1440×900）：登录 → 订单管理页（不点任何排序）→ 读取首 3 行并拦截实际请求。

| 抽样项 | 实测 |
|---|---|
| 登录成功 | `/dashboard`，localStorage 有 token |
| 页面默认列表渲染 | 首行 `20261101184311c5bab293 / 2026-11-01 18:43:11 / 待发货`；前 3 行 `18:43:11 / 18:43:10 / 18:43:10`；分页「共 54 条」 |
| 实际请求未带 isAsc/sortBy | `["/admin/order/list?pageNum=1&pageSize=5&status=undelivered", "/admin/order/list?pageNum=1&pageSize=10", ".../pageNum=1&pageSize=10"]` —— 均无 `isAsc`/`sortBy` |
| 页面首行 = 接口默认首条 | `ui=20261101184311c5bab293` = `api=20261101184311c5bab293`（`total=54`） |
| 页面时间倒序 | 首 `18:43:11` → 末 `18:32:05`（当页 10 行） |

结论：`frontend/admin-web/src/views/order/OrderList.vue:136-144` 的 `params` 只含 `pageNum/pageSize/status/keyword/addTimeMin/addTimeMax`，页面默认走接口默认 = `addTime desc`，与 §11.2 接口断言一致；截图 `docs/evidence/r1-08/admin-order-default-sort.png`。

### 11.4 环境恢复

本轮自起的 user(8001)/goods(8002)/marketing(8003)/pay(8005)/gateway(8000) 与 t14 遗留的 order(8004)/admin(8008)，以及 admin-web Vite(5174) **全部停止**，`netstat` 复查无残留监听；未改业务/测试代码，未提交 git。

### 11.5 最终判定

| 项 | 结果 |
|---|---|
| D1（C 端已完成含全退单） | ✅ 关闭（§10.2） |
| D2（admin 测试断言过期） | ✅ 关闭（§10.1） |
| D3（后台订单默认升序） | ✅ 关闭（§11.1–11.3） |
| 13 步 + 30 分钟关单 + 回归断言 | ✅ **76/76 PASS** |
| 构建门禁 | ✅ 18/18 模块 SUCCESS，**312 例 Failures 0 / Errors 0** |
| AI 入口 | ✅ 7/7 PASS（§10.6） |

**R1-08 判定：通过。**