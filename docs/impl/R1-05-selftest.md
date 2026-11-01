# R1-05 后台接口（admin）自测记录

> 任务：`t6 / R1-05 后台接口（发货 / 审核 / 库存视图 / 商品内容管理）`
> 依据：`docs/design/08-接口契约-后台admin.md` v4、`docs/design/07-接口契约-订单售后支付.md`、
> `docs/impl/03-R1实施口径.md` §6.2、`docs/design/06-接口契约-通用规范.md` §3/§4
> 实现范围：`backend/litemall-next/litemall-admin/**`（纯转发 + 入参校验 + 权限，不含业务判定）
> 环境：dev profile，nacos `192.168.96.129:8848`，admin `:8008` → goods `:8002` / order `:8004`

## 1. 验证命令与结果

| # | 命令 | 结果 |
|---|---|---|
| 1 | `mvn -o -pl litemall-admin test`（`backend/litemall-next`） | **Tests run: 58, Failures: 0, Errors: 0, Skipped: 0**，BUILD SUCCESS |
| 2 | `mvn -o -q -pl litemall-admin -am -DskipTests compile` | 成功（编译面含 api/common 依赖） |
| 3 | `mvn -o -q -pl litemall-admin -DskipTests package` | 成功，`litemall-admin/target/litemall-admin.jar` 胖包 ≈172MB |
| 4 | `PYTHONIOENCODING=utf-8 python .dbwork/r105-smoke.py` | **PASSED=57 FAILED=0 KNOWN_DEFECT=0**，`ALL R1-05 SMOKE ASSERTIONS PASSED`，exit 0（be-order 修复 X3~X5 后，原先 5 条 `KNOWN_DEFECT` 守卫全部转 PASS；见 §3.2） |

单测分布（`mvn test` 实测）：

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `AdminOrderFeignTest` | 10 | CI-26 入参归一/标识白名单、CN-33、CI-27、CN-10（adminId 透传）、CN-11、CN-12、dashboard |
| `AdminAftersaleFeignTest` | 12 | CN-01 归一与单/双 l 归一、CN-02、CN-03（adminId/地址 trim）、CN-04~CN-09 |
| `AdminGoodsStockFeignTest` | 11 | CI-09~CI-12（含部分更新不 NPE、status↔onSale、返回 `{id}`）、CN-13、CN-14 |
| `AdminSupportTest` | 11 | 分页/排序/CSV/文本/时间归一、`GoodsStatusSupport`、`GoodsWriteSupport`（create/update 两套价格规则） |
| `AdminDtoValidationTest` | 14 | `@Valid` 层（Spring MockMvc + Validator） |

真实服务冒烟脚本：`.dbwork/r105-smoke.py`（自带清理，可重复执行；覆盖见 §2）。

## 2. 接口自测矩阵（真实 HTTP，`:8008` 直连；网关 `:8000` 同路由已验证）

### 2.1 订单（`/admin/order/**`）

| 契约 | 端点 | 冒烟断言 | 结果 |
|---|---|---|---|
| CI-26 | `GET /admin/order/list` | 11 个可选筛选全转发；`status` 只认 8 个标识，**传 `closed` 必须 400**（回归点：改造前 parseStatus 不认 103/501 → 静默返回全部）；`pageSize=500` 截断为 100；`addTimeMin=2026/01/01` 格式非法 → 400 | PASS |
| CN-33 | `GET /admin/order/{id}` | 详情结构；不存在 → 404 `RESOURCE_NOT_FOUND` | PASS |
| CI-27 | `POST /admin/order/ship` | 非 102 → 409；`shipChannel/shipSn` 空白 → 400（P-18 已补 `@NotBlank`） | PASS |
| CN-10 | `POST /admin/order/close` | `adminId` 由 `UserContext` 透传（单测断言 88）；不存在 → 404 | PASS |
| CN-11 | `POST /admin/order/close-expired` | `dryRun=true` 不落库；`limit=500` → 400 | PASS |
| CN-12 | `POST /admin/order/update-ship` | 不存在 → 404；非 201 / 已改过 → 409（order 侧） | PASS |
| CX-02 | `POST /admin/order/refund` | **已删除**：任意请求都不得返回成功（实测 400，因端点不存在） | PASS |

### 2.2 售后（`/admin/aftersale/**`，9 端点）

| 契约 | 端点 | 冒烟断言 | 结果 |
|---|---|---|---|
| CN-01 | `GET /list` | 分页结构；`status=applied` 过滤生效；`status=notastatus` → 400 `PARAM_INVALID`；`type=9` → 400 | PASS |
| CN-02 | `GET /detail` | 不存在 → 404 `AFTERSALE_NOT_FOUND` | PASS |
| CN-03 | `POST /approve` | 不存在 → 404；`returnAddress` 只透传不落库（单测断言 trim 后透传） | PASS |
| CN-04 | `POST /reject` | 不存在 → 404；缺 `reason` → 400 | PASS |
| CN-05 | `POST /confirm-receive` | 不存在 → 404 | PASS |
| CN-06 | `POST /skip-receive` | 不存在 → 404 | PASS |
| CN-07 | `POST /refund` | 不存在 → 404；`amount` 原样透传（不做金额换算/判定） | PASS |
| CN-08 | `POST /retry-restock` | 不存在 → 404 | PASS |
| CN-09 | `POST /close` | 不存在 → 404 | PASS |

> `P-25` 语义确认：`refund` 在「资金已退、库存回补失败」时**是 200**（`restockPending=true`、`status=refunding`），
> admin 不把它改写为错误 —— 由 be-order 侧实现，admin 原样透传（单测 `refund` 用例断言 `restockPending` 不丢）。

### 2.3 库存（`/admin/stock/**`）

| 契约 | 端点 | 冒烟断言 | 结果 |
|---|---|---|---|
| CN-13 | `GET /list` | 行含 `number/locked/sellable/lowStockThreshold=5`；`sellable = number - locked`（锁量可用时）；`lowStockOnly` / `soldOutOnly` 过滤生效；`sortBy` 缺省 `sellable` | PASS |
| CN-14 | `PUT /update` | `number` 为**绝对值**回写一致；出参含 `productId/number/locked/sellable/lowStock/updateTime`；`number<0` → 400 `PARAM_INVALID`；货品不存在 → 404 `RESOURCE_NOT_FOUND`；**BR-ADM-03** 操作日志含 `comment`/`targetId` | PASS |

### 2.4 商品（`/admin/goods/**`）

| 契约 | 端点 | 冒烟断言 | 结果 |
|---|---|---|---|
| CI-09 | `GET /list` | 参数名 `keyword`（旧 `name` 已废弃，D-18）；返回分页结构 | PASS |
| CI-10 | `POST /create` | 返回 `{id}`（`JsonConfig` 使 `id` 为字符串）；`status` 与 `onSale` 矛盾 → 400 `STATUS_ON_SALE_CONFLICT`；无 SKU 且无 `minPrice` → 400 `PARAM_INVALID`；新建成功 | PASS |
| CI-11 | `PUT /update` | **部分更新**：只发 `{id,status}` 上下架成功（前端 `GoodsList.vue:166` 真实载荷）；`updateTime` 不匹配 → 409 `CONCURRENT_MODIFIED`；`status=0` + `onSale=true` 矛盾 → 400；`minPrice<1` → 400 `PRICE_ILLEGAL`（单测覆盖） | PASS |
| CI-12 | `DELETE /delete?id=` | 草稿可删；重复删 → 404 `RESOURCE_NOT_FOUND`；被订单引用 → 409 `GOODS_IN_USE`（由 goods 侧抛出，**本次冒烟未造出被引用草稿，仅代码路径 + 单测口径覆盖**） | PASS |

### 2.5 鉴权与错误面

- 无 token 调受保护端点 → 被 `AdminAuthFilter` 拦截（冒烟断言 `status ∈ {401,403}`）。
- 失败响应统一 `Result{code,msg,data}`，HTTP 恒 200（对外）；错误码经 `AdminFeignErrorConfig` 保真透传
  （改造前 404/409 被压成 400 且 `data.errorCode` 丢弃，现已分别映射为 `NotFoundException`/`ConflictException`）。
- 网关 `/admin/**` 单条通配已覆盖新增的 `/admin/aftersale/**`、`/admin/stock/**`（实测经 `:8000` 四条路由全 200），
  03「网关无需改」结论成立。

## 3. 契约偏离与需协同事项

### 3.1 本任务内的偏离（均为**向后兼容的收窄/兼容**，不改契约语义）

| # | 项 | 说明 |
|---|---|---|
| D1 | `pageNo` 兼容别名 | 06 §3.1 对外参数名是 `pageNum`；前端 `AftersaleList.vue:186-195` 同时传 `pageNo`+`pageNum`、`Dashboard.vue:207` **只传 `pageNo`**。为不阻塞前端，列表端点同时接受 `pageNo`（`pageNum` 优先）。**建议 fe-admin 修 Dashboard 那处**，`pageNo` 别名保留到 R2 再评估下线。 |
| D2 | 售后第 8 态 `canceled` / `cancelled` | 07 §1.2 写 `canceled`，order 侧 `AftersaleStatus.ident()` 返回 `cancelled`（双 l）。admin **两者都接受**，并在透传前统一归一为 `cancelled`（以 order 实现为准）。文档待 R1-08 统一。 |
| D3 | `status` 与 `onSale` 同传 | 08 CI-09「以 status 为准」：admin 在 `status` 非空时置空 `onSale` 再转发，矛盾只在写接口（CI-10/11）报 `STATUS_ON_SALE_CONFLICT`。 |
| D4 | CI-12 保留端点 | 后台前端**不提供删除入口**（D-05），端点保留供接口层与 R2 使用。 |

### 3.2 外部依赖缺陷（**不在本任务文件面**，已按域上报）

| # | 缺陷 | 归属 | 状态 |
|---|---|---|---|
| X1 | goods `GoodsApiExceptionAdvice` 对所有异常恒返回 HTTP 200（违反 03 §4.1 `/inner/**` 保留非 200）→ admin 把 404/409 当成功、反序列化成全 null 并回 200 | be-goods | **已修复并重启 8002**（按 URI 前缀分流）；admin 侧复测 404/409 已正确抛出 |
| X2 | `data.errorCode` 恒 null：`~/.m2` 中 9 月的旧 `litemall-common-0.0.1-SNAPSHOT.jar` 抢占 `com.litemall.domain.Result`，打进 admin 胖 jar 与 `litemall-common-core` 并存 | 聚合层 pom / 安装顺序（be-order 上报 captain） | **未修复**；冒烟已用 `err_ok()` 降级为 code/msg 判定，修好后自动收紧 |
| X3 | order 列表 `addTime` 输出 ISO（`2026-09-26T14:03:54`），违反 06 §4 `yyyy-MM-dd HH:mm:ss`；CN-33 detail 格式正确 | be-order | **已修复**（`OrderAdminDTO.addTime` 加 `@JsonFormat(pattern="yyyy-MM-dd HH:mm:ss", timezone="Asia/Shanghai")`，不动字段类型）；17:50 冒烟实测 `"2026-11-01 17:29:38"` |
| X4 | CN-01 list 行 `status` 输出数字码（`"0"`/`"4"`）而契约要求标识；`specText` 恒 null | be-order | **已修复**（`decorateAdminRow` 补 `AftersaleStatus.ident()`；SQL 别名 `specifications` 改 `AS specText`）；冒烟实测 `"status":"applied"`、`specText:"白色 / S"` |
| X5 | CN-02 detail 的 `order.status` 输出数字码（`102`）；CN-33 行级 `aftersaleStatus` 输出数字码（`0`） | be-order | **已修复**（`AftersaleAdminDetailDTO.OrderBrief.status`、`OrderAdminDetailDTO.Line.aftersaleStatus` 由 Integer 改 String）；冒烟实测 `"status":"undelivered"`、`"aftersaleStatus":"applied"` |

> 曾误判并已自我纠正（勿再按此排期）：CN-02 **不含** `refundId`/`refundAmount`（那是 CN-07 退款动作响应），
> CN-16/CN-02 **不含** `applyTime`；CN-33 已完成售后的行级三字段为 `null` 是契约要求（「当前未完结售后，无则 null」）。

冒烟脚本对 X3~X5 设了 5 条 `check_known(..., owner="be-order")` 守卫：修复前计 `KNOWN_DEFECT`、修复后自动转 `PASS`。
2026-11-01 17:50 复跑：**5 条守卫全部 PASS**（`PASSED=57 FAILED=0 KNOWN_DEFECT=0`），X3~X5 已闭环。

## 4. 依赖与遗留（R2/R3 缺口披露，**本任务未扩范围**）

### 4.1 未实现的 R2/R3 后台接口（R1 后台页面会缺功能，属计划内）

- **CN-21~CN-28**（类目/品牌 CRUD、区域、运费模板等）：后台商品表单的类目/品牌下拉当前走 C 端只读接口
  （`/app/goods/category`、`/app/goods/brand/list`，由 fe-admin 按 R1 口径处理）；新增/编辑类目与品牌不可用。
- **CN-29~CN-31**（营销/优惠券后台）：`/admin/coupon/**` 等不存在，营销管理页（若入口开放）不可用。
- **CN-34~CN-37**、**CI-08/18/20/21**：用户/评价/报表等后台接口不在 R1 面。
- 后台「订单详情 → 修改物流单号」入口依赖 CN-33 的 `canUpdateShip`（已实现）；`shipUpdateCount` 由 order 侧从
  `litemall_log` 派生（P-22），admin 不自算。

### 4.2 已知不可达 / 数据前提

- 订单主状态 **501（completed）在 R1 不可达**（确认收货 → 301，无自动完成）；`status=completed` 过滤可传但恒空集，
  不得用 402 冒充。
- 冒烟环境当前 `locked=0`（无锁定库存），故 CN-14 的 `number<locked → 409 STOCK_ADJUST_CONFLICT` 分支**未覆盖**，
  仅由 goods 侧单测覆盖；补一条锁量后可复跑该断言。
- 售后写操作的状态冲突面（409 `AFTERSALE_STATUS_ILLEGAL` 等）在冒烟里只覆盖了 404 面（需要真实状态机推进才能造出
  各态），状态机语义由 order 域单测（47 例）覆盖。

## 5. dev 环境启动/运维备忘（本次实测踩坑）

- 端口实况：gateway `8000`、user `8001`、goods `8002`、order `8004`、pay `8005`、admin `8008`
  （goods **不是** 18003；`18003` 是文档里另一套端口约定，勿混用）。
- **重启服务前必须 `taskkill` 旧 java 进程**，否则 `mvn package` 无法 rename jar，会把 `target/*.jar` 覆盖成
  100KB 级瘦包（本次踩到 3 次；已发生一次 order jar 事故，按 be-order 授权用 8 个 `-pl` 的 reactor 命令重建）。
- 重建 order 的正确命令（必须带齐 8 个模块，否则解析 `~/.m2` 旧 jar）：
  ```
  mvn -o -DskipTests package -pl litemall-common-core,litemall-common-web,litemall-common-mybatis,litemall-common-cache,litemall-common-notify,litemall-common-jwt,litemall-api,litemall-order
  ```
- gateway 本次**从未被启动过**（jar 存在、无进程），已用 dev profile 起在 `8000`，日志 `/tmp/r105/gateway.log`。
- **admin 胖 jar 会「静默保留旧依赖」**（本次踩坑，务必注意）：`litemall-admin` 依赖 `litemall-api` 的 DTO，而
  `mvn package -pl litemall-admin` 解析的是 `~/.m2` 里的 `litemall-api`。be-order 改了 api 里的
  `OrderAdminDetailDTO.Line.aftersaleStatus`（Integer→String）后，即使 `mvn verify` 全仓 SUCCESS，admin 胖包
  `BOOT-INF/lib/litemall-api-0.0.1-SNAPSHOT.jar` 仍是旧的（Integer），运行时解码 order 出参直接 500：
  `JSON parse error: Cannot deserialize value of type java.lang.Integer from String "applied"`。
  另有一次 admin 进程还占用着 jar，`mvn package` 打印 `Replacing main artifact` + BUILD SUCCESS，但 `target/litemall-admin.jar`
  的 mtime 与内容都没变（打包结果被旧文件顶住）。
  可靠做法（实测有效）：
  ```bash
  taskkill //PID <admin_pid> //F
  mvn -o -DskipTests install -pl litemall-api        # 先把新 api 装进 ~/.m2
  rm -f litemall-admin/target/litemall-admin.jar*    # 删掉旧胖包（否则可能不被替换）
  mvn -o -DskipTests package -pl litemall-admin
  # 校验：unzip -l target/litemall-admin.jar | grep litemall-api  → 大小应是新的 138489
  ```
  同理，**任何改过 `litemall-api` 的联调/验收前，都必须 install api + 重打 admin**，不能只重启服务。

## 6. 变更清单（本任务文件面）

### 6.1 新增

- `litemall-admin/.../controller/AftersaleController.java`（售后 9 端点）
- `litemall-admin/.../controller/StockController.java`（库存 2 端点）
- `litemall-admin/.../support/AdminQuerySupport.java`（分页/排序/CSV/文本/时间归一，纯函数）
- `litemall-admin/.../support/GoodsStatusSupport.java`（`status↔onSale` 映射与矛盾 400）
- `litemall-admin/.../support/GoodsWriteSupport.java`（`minPrice` 条件必填 / 编辑下限）
- `litemall-admin/.../config/OperationLogContext.java`（把 body 里的 `remark`/目标对象补进同一条操作日志）
- `litemall-admin/.../domain/DTO/`：`OrderCloseReqDTO`、`OrderCloseExpiredReqDTO`、`OrderUpdateShipReqDTO`、
  `StockUpdateReqDTO`、`AftersaleApproveReqDTO`、`AftersaleRejectReqDTO`、`AftersaleIdReqDTO`、
  `AftersaleSkipReceiveReqDTO`、`AftersaleRefundReqDTO`、`AftersaleCloseReqDTO`
- `litemall-admin/.../domain/VO/IdVO.java`（CI-10 出参 `{id}`）、`StockUpdateVO.java`（CN-14 出参 6 字段）
- 测试：`AdminAftersaleFeignTest`、`AdminGoodsStockFeignTest`、`AdminSupportTest`
- `.dbwork/r105-smoke.py`（真实服务冒烟，内置清理）

### 6.2 修改

- `controller/OrderController.java`（列表 11 参、`{id}`、ship、close、close-expired、update-ship；删除 `/refund`）
- `controller/GoodsController.java`（`keyword`、全字段 create/update、部分更新、返回 id、清空 NPE）
- `config/AdminFeignErrorConfig.java`（404/409 保真 + 解析 `data.errorCode`）
- `config/OperationLogInterceptor.java`（合并 `OperationLogContext` 的 comment/targetType/targetId）
- `domain/DTO/OrderShipReqDTO.java`（补 `@NotBlank`）、`GoodsCreateDTO.java`、`GoodsUpdateDTO.java`（部分更新语义）
- 测试：`AdminOrderFeignTest`、`AdminDtoValidationTest`
- **删除** `domain/DTO/OrderRefundReqDTO.java`（CX-02）