# R1-01 商品域后端自测记录

- 任务：R1-01 商品域后端（08 v4 + 03 R1 口径）
- 实施范围：CI-31 / CI-32 / CI-47 / CI-10 / CI-11 / CI-12、CN-13 / CN-14、CI-40 / CI-42 / CI-44（内部库存接口）、评价读路径
- 代码位置：`backend/litemall-next/litemall-goods`（写路径 + 库存服务 + controller）、`litemall-api`（DTO / GoodsClient）
- 数据库：`litemall`（本地 MySQL），改动仅涉及既有表，无 DDL

## 1. 验证命令与结果

| 命令 | 结果 |
| --- | --- |
| `mvn -o -B -pl litemall-goods -am -DskipTests compile` | BUILD SUCCESS |
| `mvn -o -B -pl litemall-goods test` | BUILD SUCCESS，Tests run: 23, Failures: 0, Errors: 0 |
| `python .dbwork/r101-smoke.py`（真实服务 18003） | `ALL SMOKE ASSERTIONS PASSED` |

单测构成：

- `GoodsSupportTest`（11 例）：元↔整数分、`spec_value_ids` 解析/归一（升序去重）、`sku_code` 生成（无规格 `goodsId-D`）、规格文本、`sellable=max(0, number-locked)`、locked 未知时必须为 `null`、低库存阈值 5、时间格式、状态文案。
- `QueryNormalizeTest`（9 例）：CI-31 分页缺省/截断、价格区间颠倒自动交换、负数 400、排序白名单非法值「忽略不报错」、关键字 50 字上限；CI-47 status 多值校验、onSale→status=1；CN-13 排序白名单、goodsStatus 多值、分页。
- `StockDeductBatchTest`（2 例，真 MySQL `@SpringBootTest`）：库存不足时 `deducted=false` + `shortage=4` + 全部 items `deducted=false` + 数据库零部分扣减；库存充足时整批生效。

## 2. 接口自测矩阵（真实 HTTP 调用，服务 `spring-boot:run` + dev profile）

| 用例 | 期望 | 实测 |
| --- | --- | --- |
| `POST /inner/goods/admin/create`（2 维规格 × 2 SKU，临时规格值文本） | 返回新建 id，服务端翻译真实规格值 id 并回填 | `"1181005"`，SKU 的 `specValueIds=[253,255]/[254,255]`、`skuCode=1181005-253_255` |
| `GET /inner/goods/admin/list?keyword=…` | `skuCount=2`、`displayPrice=1999`、`statusText=草稿`、`onSale=false`、`categoryName=文具`、id 为字符串 | 全部命中 |
| `GET /inner/goods/admin/stock/list?keyword=…` | 2 行 SKU；`specText` 按维度顺序（`白 / 350ml`）；`lowStockThreshold=5`；`locked=0`、`sellable=number` | 全部命中 |
| `GET /app/goods/detail?goodsId=<草稿>` | 404 `GOODS_OFF_SALE`，msg「商品已下架」 | 命中 |
| `PUT /inner/goods/admin/update`（updateTime 过期） | 409 `CONCURRENT_MODIFIED`，msg「该商品已被修改，请刷新」 | 命中 |
| `PUT /inner/goods/admin/update`（上架，updateTime 匹配） | 200，空响应体 | 命中 |
| `GET /app/goods/detail?goodsId=<上架>` | `price=1999`、`availableStock=13`、`canBuy=true`、`specs` 维度顺序 `[颜色, 容量]`、`specText=白 / 350ml`、`specValueIds` 升序、id 为字符串 | 全部命中 |
| `GET /app/goods/list?keyword=…` | `displayPrice=1999`（有货 SKU 最低价）、`totalStock=13`、`soldOut=false` | 命中 |
| `PUT /inner/goods/admin/stock/update`（number=-1） | 400 `PARAM_INVALID`，msg「库存数量必须大于等于 0」 | 命中 |
| 同上（expectNumber 不符） | 409 `CONCURRENT_MODIFIED` | 命中 |
| 同上（expectNumber=10，改 8） | `number=8`、`sellable=8`、`lowStock=false` | 命中 |
| `DELETE /inner/goods/admin/delete?id=<在售>` | 409 `GOODS_IN_USE`，msg「该商品已产生订单，不能删除，请改为下架」 | 命中 |
| 改回草稿后 `DELETE` | 200（逻辑删除） | 命中 |
| 删除后 `GET /app/goods/detail` | 404 `RESOURCE_NOT_FOUND`，msg「商品不存在」 | 命中 |
| `GET /app/goods/detail?goodsId=abc` | HTTP 200 + `code=400`、`PARAM_INVALID`、msg「商品ID 必须是数字」 | 命中（C 端恒 200） |

冒烟脚本幂等：运行前清理上一轮「R1自测-多规格商品」残留，运行结束删除自建数据。

### 2.1 异常响应 HTTP 状态（03 §4.1 分流；be-admin 联调发现后修复）

首版 `GoodsApiExceptionAdvice` 对 `/inner/**` 也恒返回 HTTP 200，导致 `PUT /inner/goods/admin/stock/update`（货品不存在）被 admin 侧当成成功（还会落成功操作日志）。已按 URI 前缀 `/inner/` 分流修复（与公共层 `CommonExceptionAdvice` 一致）：对外 `/app/**`、`/admin/**` 仍恒 200，对内 `/inner/**` 保留非 200 供 Feign 感知失败。修复后实测：

| 请求 | 修复前 | 修复后实测 |
| --- | --- | --- |
| `PUT /inner/goods/admin/stock/update` `{"productId":99999999,"number":1}` | HTTP 200 + `code=404` | **HTTP 404** + `{"code":404,"msg":"货品不存在","data":{"errorCode":"RESOURCE_NOT_FOUND"}}` |
| `DELETE /inner/goods/admin/delete?id=99999999` | HTTP 200 + `code=404` | **HTTP 404** + `{"code":404,"msg":"商品不存在","data":{"errorCode":"RESOURCE_NOT_FOUND"}}` |
| `PUT /inner/goods/admin/update`（updateTime 过期 / SKU_IN_USE / SKU_CODE_DUPLICATED） | HTTP 200 + `code=409` | 同一分流后 **HTTP 409** |
| `GET /inner/goods/admin/list`（正常） | HTTP 200 | HTTP 200（成功路径不变） |
| `GET /app/goods/detail?goodsId=abc` | HTTP 200 + `code=400` | HTTP 200 + `code=400`（对外恒 200，不变） |

非标准业务码（`HttpStatus.resolve` 返回 null）统一落 HTTP 500，避免非法状态行。

## 3. 契约偏离与需协同事项（**请评审确认**）

1. **CI-44 批量扣减的出参形态**：03 §4.4 表格写「400（任一行不足 → 整批回滚）」，但 §4.7 D-24 / P-31 要求订单侧拿 `DeductBatchResultDTO` 并把订单留在 101。本实现按 **D-24** 执行：整批回滚时 HTTP **200** + `{"deducted":false,"items":[{"productId","deducted":false,"shortage"}]}`，不抛 400；单件 `POST /product/deduct` 仍保留 400「库存不足」（CI-42 条件更新）。订单域需按此口径消费。
2. **08 §1.6 自造错误码降级**：`PRICE_ILLEGAL` / `SKU_COMBO_DUPLICATED` / `SKU_REQUIRED` / `CATEGORY_NOT_LEAF` 不在 03 §4.3 冻结的 27 码内，按 03「通用兜底、各域不得自造 XXX_INVALID」统一返回 **400 `PARAM_INVALID` + 中文 msg**（例如「请选择末级类目」「SKU 组合重复」「至少保留一个 SKU」）。08 文档措辞需回写，否则前端按 code 分支会落空。
3. **`GoodsAdminDTO.minPrice/displayPrice/totalStock/counterPrice` 为整数分（Integer）**：与 C 端一致。若 `litemall-admin` 既有代码仍按 `BigDecimal` 元处理，需要 R1-05 后台前端/接口层同步换算，避免出现「1999 元」。
4. **`OrderGoodsRefMapper` 为 goods 模块内的只读跨表 Mapper**：仅 `SELECT COUNT(1) FROM litemall_order_goods WHERE goods_id/product_id = ?`，用于 CI-12 / SKU_IN_USE 的引用判断；**不做 `deleted` 过滤**——逻辑删除的订单行仍是历史引用，删掉 SKU 会让历史订单无法回显。若架构上不允许跨域读表，需订单域提供 `countByGoodsId/countByProductId` 内部接口替换。
5. **locked 不可用时的降级口径**：订单域 `lockedStock` 不可达时，C 端列表/详情把 `sellable` 降级为 `number`（置 `lockedUnavailable=true`，避免整页不可售），CN-13 库存列表则严格返回 `locked=null`、`sellable=null` 并置 `lockedUnavailable=true`；`availableStock` 同样降级/置空。超卖保护最终由下单时 CI-42 条件扣减兜底。
6. **`specList` 为旧结构原样透传**：返回的是规格值实体（含 `addTime` 为 ISO 字符串 `2026-11-01T17:03:37`），保留它是为了不破坏既有前端；C 端新字段 `specs`/`skus` 的时间已统一 `yyyy-MM-dd HH:mm:ss`。如需全量统一时间格式，需前端确认可删 `specList` 后单独排期。
7. **评价**：本轮只改读路径（仅 `status=0` 可见、`addTime/replyTime` 统一格式、`hasPicture/adminContent` 直出）；`addComment` 的图片/订单校验属 R2 范围，未改。

## 4. 依赖与遗留

- 依赖订单域 `OrderClient.lockedStock(...)` 与 `LockedStockDTO`（已由 be-order 落盘，goods 侧编译依赖该 DTO 字段类型 `productId: Long`）。
- 依赖用户域 `UserClient.listUserByIds`（评价昵称/头像）；不可达时昵称回退「用户<id>」、头像为空，不阻断列表。
- 未覆盖：CI-36（订单域）、CI-49/50（后台前端联调，需 R1-05 完成页面后走端到端）、并发压测（批量扣减的死锁顺序已按 productId 升序固定，未做压测）。