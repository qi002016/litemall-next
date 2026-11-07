# R3 与 AI 购物助手 · 独立交付需求书

> 版本：v1.0 ｜ 出具日期：2026-11-07 ｜ 出具方：R1/R2 主 Agent
> 接收方：独立执行 Agent（不需要、也不应该再回头询问出具方，本文件即全部输入）
> 涉及范围：**R3 收尾（我的收藏、广告轮播与专题、公告验收）** + **AI 购物助手（C 端智能客服）**
> 交付形态：可运行代码 + 通过本文件 §14 验收 + 文档回写

---

## 0. 怎么用这份文档（执行者协议）

1. **先读 §2（基线）与 §3（红线）**，再读 §4（已完成能力）——§4 决定你「不要重做什么」。
2. 按 §5 任务表的依赖顺序执行，每个任务完成后**立刻**跑该任务自己的验收步骤（§6/§7/§9–§11 内嵌），不要攒到最后。
3. §14 是**最终验收脚本**，必须逐条真实执行并把输出贴进交付报告；"应该能过"不算过。
4. §3 与 §16 的每条红线都是硬约束。**红线与任何"更优雅的实现"冲突时，按红线做**。若确实无法满足，停下来写清冲突点，不要自行改需求。
5. §13 的 SQL **只写不执行**，必须在交付物里标【需人工确认】。你可以写 SQL 文件，但不得在生产/演示库上跑 DDL。
6. 本文件所有"现状"描述均于 2026-11-07 在 `D:\litemall-next` 实测得到；若你执行时发现代码与描述不符，**以代码为准**，并在交付报告里记一条"文档与代码偏差"。

---

## 1. 一页速览

### 1.1 项目是什么

`D:\litemall-next` 是一个 **Spring Cloud 微服务 + Vue3 双端** 的单商户电商演示系统（淘宝风格 C 端 + 管理后台），目标见 `需求文档.md:16` §1.2 —— **"陌生人 10 分钟走完一单"**，`需求文档.md:31` §1.4 —— **"断链数 = 0"**。

已完成 R1（13 步脚本）、R2（8 步脚本）两期，验收均通过。本期做 **R3**（`需求文档.md:505-516` §6.3）与 **AI 购物助手**（原本被 `需求文档.md:447-459` §5.15 明确排除，现由业主改为本期交付）。

### 1.2 本期要做什么（判据见 §1.4）

| 编号 | 内容 | 性质 | 预估 |
|---|---|---|---|
| T1–T2 | 我的收藏（后端补字段与幂等 + 前端两屏） | 新功能（有半成品） | 1 天 |
| T3–T5 | 首页轮播修复（9 个真缺陷）+ 专题详情页与后台配置 | **修缺陷 + 补缺** | 1.5 天 |
| T6 | 公告条（Q6 已定稿不做弹窗） | 只验收、不开发 | 0.1 天 |
| T7–T9 | AI 购物助手（网关接入 + Python agent 改造 + C 端会话页） | 新功能 | 2.5 天 |
| T10 | 验收、文档回写、提交 | 收尾 | 1 天 |

合计约 **6 个工作日**。

### 1.3 明确不做（做了算超范围，需要先问人）

- 关注店铺、浏览足迹的**前端页面**（足迹后端已存在，接不接都行，见 §6.9）。
- 公告**弹窗**（Q6 已定稿：只做顶部公告条，`需求文档.md:562`）。
- AI 助手**流式输出（SSE）**、多轮意图记忆增强、后台客服端（`backend/litemall-agent/admin-agent/` 是空目录，本期不填）。
- 真实 LLM 效果的调优（话术质量不计分，见 §1.4）。
- 任何 R1/R2 已验收功能的架构性重构。
- 支付、物流、退款真实对接（演示态即可）。

### 1.4 完成判据（Definition of Done）

同时满足以下 5 条才算完成：

1. **R3 五步脚本全通过**（§14.1），且"断链"为 0：收藏可持久、轮播三种跳转都能落地到真实页面、停用后首页立即变化、公告条可见。
2. **AI 六步脚本全通过**（§14.2），且 AI 的回答基于**真实库数据**（`TOOL_FALLBACK=remote`），不是模板假数据。
3. `mvn -o verify` 全绿（18 模块，0 失败）；`pnpm build`（两个前端）全绿。
4. 无新增 lint error（前端既有 2 条历史 error 见 §16，不得增加）。
5. 交付报告写清：改了什么、怎么验的（真实输出）、哪些没验（未验证边界）、哪些要人工做（DDL/密钥）。

---

## 2. 项目基线（照抄即可跑起来）

### 2.1 技术栈

| 层 | 技术 |
|---|---|
| 后端 | JDK 25.0.2 / Spring Boot 4.0.8 / Spring Cloud 2025.1.2 / Spring Cloud Alibaba 2025.1.0.0 / MyBatis-Plus 3.5.17 / Seata 2.5.0 / Maven 3.9.16 |
| 前端 | Vue 3 + Vite + Pinia + Vue Router，两个独立工程 |
| AI 服务 | Python 3.14 + FastAPI + LangGraph（**独立进程，不在 Maven 工程内**） |
| DB / 中间件 | MySQL 8.0.46、Redis、Nacos（服务发现）、Seata（分布式事务） |
| 系统 | Windows + PowerShell（也可用 git-bash，注意 §16 的差异） |

### 2.2 模块与端口

后端根目录 `backend/litemall-next/`（Maven 多模块，`-T 1C` 可并行构建）：

| 模块 | 端口 | 职责 |
|---|---|---|
| `litemall-gateway` | 8000 | 唯一入口：JWT 校验、角色/受众校验、注入 `user-info` 头、路由转发 |
| `litemall-user` | 8001 | 用户、地址、账号 |
| `litemall-goods` | 8002 | 商品、SKU、库存三量、分类、品牌、评价 |
| `litemall-marketing` | 8003 | 优惠券、**广告**、**专题**、公告 |
| `litemall-order` | 8004 | 购物车、订单、售后、**收藏**、足迹 |
| `litemall-pay` | 8005 | 支付单、回调、退款 |
| `litemall-message` | 8006 | 站内信/通知 |
| `litemall-storage` | 8007 | 对象存储 |
| `litemall-admin` | 8008 | 管理后台聚合层（Feign 聚合各服务 `/inner/**`） |

公共模块：`litemall-api`（Feign 客户端与跨服务 DTO）、`litemall-common-core`、`litemall-common-web`、`litemall-common-cache`、`litemall-common-jwt`、`litemall-common-security`（Maven 已按 pom 打包，消费方 pom 需 `<type>pom</type>`）。

前端：`frontend/mobile-web`（C 端，dev 5173）、`frontend/admin-web`（后台，dev 5174）。
AI：`backend/litemall-agent/mobile-agent/`（Python，默认 8100）。

### 2.3 中间件与连接

| 组件 | 地址 | 备注 |
|---|---|---|
| MySQL | `127.0.0.1:3306`，库 `litemall` | `max_connections=151`；CLI 必须加 `--default-character-set=utf8mb4`，否则中文乱码/报错 |
| Redis | `127.0.0.1:6379` | 缓存、幂等令牌、`auth:ver`、发布订阅失效 |
| Nacos | `192.168.96.129:8848` | 远端，本机 `netstat` 看不到 8848 属正常 |
| Seata | `192.168.96.129:8848`（注册在 Nacos，服务端 8091） | 9 个服务都必须有 `seata:` 配置块，否则启动失败 |
| 网关 | `http://127.0.0.1:8000` | **前端与 agent 一律用 127.0.0.1，不要用 localhost**（Windows 上 localhost 先解析 `::1`，网关只监听 IPv4 → 502） |

### 2.4 启动后端（PowerShell）

必须先设环境变量（H4 fail-fast，缺了直接启动失败）：

```powershell
$env:HM_ACTIVE_PROFILE   = 'dev'
$env:HM_JKS_LOCATION     = 'classpath:litemall.jks'
$env:HM_JKS_ALIAS        = 'hmall'
$env:HM_JKS_PASSWORD     = 'hmall123'
$env:HM_DB_PW            = '123456'
$env:HM_INTERNAL_TOKEN   = 'dev-internal-token'
```

构建与启动（**改了代码必须先 `package`，jar 不会自动更新**；启动前务必先 kill 旧 java 进程，否则 `repackage` 会因文件锁报 `Unable to rename 'xxx.jar' to 'xxx.jar.original'`）：

```powershell
cd D:\litemall-next\backend\litemall-next
mvn -T 1C -DskipTests -q clean package
# 用现成脚本一键起 9 个服务（PowerShell Start-Process 真脱离）
bash D:\litemall-next\.dbwork\r2-accept\start-all.sh
# 日志：D:\litemall-next\.dbwork\r2-accept\logs\<svc>.log
```

启动自检：`curl http://127.0.0.1:8000/app/goods/list?pageNum=1&pageSize=2` → `code=200` 且 `data.list` 非空。

### 2.5 启动前端

```powershell
cd D:\litemall-next\frontend\mobile-web ; pnpm install ; pnpm dev   # 5173
cd D:\litemall-next\frontend\admin-web  ; pnpm install ; pnpm dev   # 5174
```

### 2.6 演示账号与数据

| 用途 | 账号 | 密码 |
|---|---|---|
| C 端用户 | `user123` | `123456`（user id=1） |
| 管理后台 | `admin123` | `admin123` |
| 演示运营号（R2 种子） | `r2demo_admin` / `r2demo_disabled` | `admin123` |

- 后台认证头：`Authorization: Bearer <token>`（登录 `POST /admin/auth/login`）。
- 库内既有：商品 262（在售数十）、广告 6 行、公告 3 行、专题 22 行、收藏 0 行、订单 60+、优惠券若干。
- 种子脚本：`db/seed/r2-demo-seed.sql`（R2 演示数据，可重复执行，脚本内 `DELETE` 用精确 id 列表，**不要改成区间删除**，会删掉别人的验收数据）。

---

## 3. 全局契约与红线（不可协商）

### 3.1 响应与错误码

- 统一信封 `Result{code,msg,data}`，**成功恒 `code=200`**（不是 0，历史代码里的 0 已统一）。
- **对外 `/app/**`、`/admin/**` 恒 HTTP 200**，业务失败通过 `code` + `data.errorCode` 表达；分页统一 `PageVO{total,pages,list}`（注意是 `list`，不是 `records`）。
- **`/inner/**` 与网关鉴权拒绝保留真实非 2xx**（否则 Feign 会把失败当成功）。
- 鉴权层例外：401/403 是**真实 HTTP 状态码**（网关与 admin 的 Servlet Filter 早于 Controller）。
- 新增码位要登记，**码位总数 27、码面 22** 已冻结；细分类别走 `data.errorCode`，不得新增 HTTP 状态码（409/429 是已批准的两个例外）。

### 3.2 数据口径

- **金额一律整数分**（`Integer`/`Long`），不出浮点；元→分用 `movePointRight(2)` 或 `×100` 取整。
- **ID 一律字符串化输出**（全局 `Long→ToStringSerializer` 已配置，别手工 `String.valueOf`）。
- 时间格式 `yyyy-MM-dd HH:mm:ss`。
- 逻辑删除字段 `deleted tinyint(1)`，查询必须带 `deleted=0`。
- 字段缺失返回 `null`，不返回空串/0 兜底；派生值分母为 0 返回 `null`。

### 3.3 分层与跨服务

- 分层固定：`Controller`（只做参数校验与装配）→ `IService`/`ServiceImpl`（业务）→ `Mapper`（SQL）。
- **服务之间只能通过 Feign（`litemall-api` 里的 `*Client`）调用 `/inner/**`**，禁止跨库直连别的服务的表（例外见 §6.9 的历史技术债说明，本期不新增）。
- 改 `litemall-api` 的 DTO：**只能加字段**，不得改/删既有字段（否则 9 个服务要同批发布）。

### 3.4 代码与提交

- 中文注释，注释写"为什么"不写"是什么"；不改公共组件的对外行为。
- 提交信息：`feat(R3-01): ...` / `fix(R3-02): ...`，**只做本地 commit，不 push**。
- 提交时用显式路径（`git commit -- <files>`），**不要 `git add -A`**：工作区有他人未提交的改动（见 §16.19）。
- **不得删除他人文件**（用户删除过的整改期文档保持删除状态，不要恢复也不要重新创建）。

### 3.5 数据变更

- 任何 DDL/索引/字段类型变更：**写成 SQL 文件 + 标【需人工确认】，不执行**。
- 需要立刻生效的演示数据可以用 DML 写进 `db/seed/`，但必须幂等（可重复执行）。

---

## 4. 已完成能力（不要重做）

R1 已完成 13 步（首页→分类→搜索→详情→规格→购物车→下单→支付→订单→售后→评价→后台商品→后台订单），R2 已完成 8 步（分类品牌、搜索筛选排序、评价管理、经营报表、账号启停、日志筛选）。

**本期直接复用、不要改的东西**：JWT/网关鉴权、库存三量、幂等下单令牌、按行售后 8 态与退款两段式、评价九步链、报表统计、缓存框架 `CacheService`（`get/getList/del/delByPrefix/evictLocal`）、统一异常 `BadRequestException/NotFoundException/ConflictException`、`PageVO`。

**关于"半成品"的真相（很重要，别重复造）**：

| 你以为没做 | 实际状态 |
|---|---|
| 我的收藏后端 | **已实现**：`litemall-order` 里 `CollectController`（`/app/collect/list`、`/app/collect/add`）、`CollectServiceImpl`、`Collect` PO、`CollectMapper`、`CollectVO`、`CollectAddDTO` 全都存在且已提交；前端 `frontend/mobile-web/src/api/user.js:30-34` 也已有 `collectList`/`collectAdd` 封装 |
| 我的足迹后端 | **已实现**：`FootprintController`（`/app/footprint/list`、`/app/footprint/add`）+ `FootprintServiceImpl` |
| 广告后端 | **已实现但有 9 个缺陷**（§7.2），导致首页轮播实际一直为空、走静态兜底图 |
| 广告后台页 | **存在**：`frontend/admin-web/src/views/marketing/AdList.vue`（但字段严重不足，§7.4） |
| 公告 | **前后端都已完成**（顶部公告条已渲染），R3-03 只需验收（§8） |
| AI 助手 | 后端有可运行的骨架（`backend/litemall-agent/mobile-agent`，9 个单测，mock 模式可跑），**但没接网关、身份头读错、工具不全**；前端只有占位页 `AssistantDisabled.vue` |

所以本期的真实工作量：**收藏 = 补 1 张表唯一键 + 2 个返回字段 + 前端 2 屏**；**轮播 = 修 9 个缺陷 + 补专题详情**；**AI = 打通网关 + 改造 agent + 做会话页**。

---

## 5. 任务分解

| 编号 | 任务 | 依赖 | 主要负责文件（预估） |
|---|---|---|---|
| T1 | 收藏后端：返回字段补 `price`/`onSale`、幂等与唯一键、取消物理删除 | — | `litemall-order`、`litemall-goods`（`GoodsInnerController`）、`litemall-api`（`GoodsBriefDTO`） |
| T2 | 收藏前端：详情页收藏按钮、我的收藏列表页、我的页入口、未登录回跳 | T1 | `frontend/mobile-web` |
| T3 | 轮播后端修复 9 缺陷（含 `sort_order` 列、缓存失效） | — | `litemall-marketing` |
| T4 | 专题详情端点 + C 端专题页 | T3 | `litemall-marketing`、`frontend/mobile-web` |
| T5 | 后台广告/专题配置页补字段（linkType/排序/时间窗/图片） | T3,T4 | `frontend/admin-web`、`litemall-admin` |
| T6 | 公告条验收（不开发） | T3 | — |
| T7 | 网关接入 `/agent/**` + 前端 proxy | — | `litemall-gateway`、`frontend/mobile-web/vite.config.js` |
| T8 | agent 服务改造（身份头、工具补全、限流、降级、错误码、测试） | T7 | `backend/litemall-agent/mobile-agent` |
| T9 | C 端 AI 会话页（会话 UI + 三类卡片 + 入口 + 降级） | T7,T8 | `frontend/mobile-web` |
| T10 | 全量验收 + 文档回写 + 提交 | 全部 | `docs/`、根目录 |

建议顺序：**T1→T2 → T3→T4→T5→T6 → T7→T8→T9 → T10**。T3 与 T7 可并行（不同服务、不同人）。

---

## 6. T1/T2 · 我的收藏

### 6.1 需求原文（`需求文档.md:385-395` §5.11）

- **BR-FAV-01** 收藏需登录；未登录点击跳登录，登录后回到原商品。
- **BR-FAV-02** 同一商品重复收藏只留 1 条；再点即取消。
- **BR-FAV-03** 下架商品仍在收藏列表可见，标「已下架」且不可点击购买。
- **BR-FAV-04** 收藏列表按收藏时间倒序。

### 6.2 现状（实测）

| 项 | 现状 |
|---|---|
| 表 `litemall_collect` | 列：`id bigint`、`user_id bigint`、`value_id bigint`、`type tinyint`（0 商品 / 1 专题）、`add_time`、`update_time`、`deleted tinyint(1)`；索引 `KEY user_id`、`KEY goods_id(value_id)`；**无唯一键，0 行数据** |
| 后端 | `CollectController`：`GET /app/collect/list`（`pageNum`/`pageSize`，默认 10）、`POST /app/collect/add`（body `{goodsId, type}`，`type=0` 收藏 / `1` 取消，返回 `{collected:boolean}`） |
| 服务 | `CollectServiceImpl.listCollect` 按 `user_id`+`type=0`+`deleted=0` 倒序分页，再通过 `GoodsClient.listGoodsBriefByIds` 批量补商品名与主图；`addCollect` 用"存在则复活、不存在则插入"实现，取消是**逻辑删除** |
| 商品信息 | `GoodsBriefDTO{id,name,picUrl}`（`litemall-api`）← `GoodsInnerController#listGoodsBriefByIds`（`GET /inner/goods/brief/list?ids=`，注意实际路径以 `GoodsInnerController` 类上 `@RequestMapping` 为准），**不含价格、不含上下架** |
| 前端 | `api/user.js` 有封装；`Me.vue:128-130` 三项（收藏/关注店铺/足迹）仍是 `toast('...不在 R1 范围（R3 开放）')`；`GoodsDetail.vue:181` 收藏按钮也是 toast；**无收藏列表页、无路由** |
| 登录回跳 | 已具备：`utils/request.js:26` 401 时 `router.replace({path:'/login',query:{redirect:current.fullPath}})`，`Login.vue:71-72` 登录后 `router.replace(redirect)` |

### 6.3 设计决策（照做，不要另设计）

1. **服务归属不变**：`/app/collect/**` 已在网关路由到 `litemall-order`（`litemall-gateway/src/main/resources/application.yaml` 的 `order` 路由 Path 里包含 `/app/collect/**`），不要搬到 goods 服务，也不要改网关路由。
2. **取消收藏改为物理删除**（`deleteById`），并加唯一键 `(user_id, value_id, type)`。理由：逻辑删除 + 唯一键会互相打架——取消后重新收藏会撞键。物理删除对"收藏"这种用户偏好无留痕价值。
3. **幂等兜底**：`add` 时捕获 `DuplicateKeyException` 视为"已收藏"并返回 `true`（并发双击不会 500，也不会出现 2 条）。这是 BR-FAV-02 的 DB 级保证；唯一键没上时也要靠这一层兜住（先上线代码、后执行 DDL 也安全）。
4. **商品信息扩展**：只往 `GoodsBriefDTO` **增加** `price`（`Integer`，整数分，取该商品可售 SKU 最低价；无 SKU 时取 `retail_price×100`）与 `onSale`（`Boolean`：`is_on_sale=1 AND deleted=0`）。加字段向后兼容，只需重新发布 **goods** 与 **order** 两个服务。
5. **下架商品不隐藏**（BR-FAV-03）：列表照常返回，`onSale=false`，前端标「已下架」且整行置灰不可跳转。
6. **分页上限 100、默认 10**（沿用现有实现，不改成 20）。

### 6.4 接口契约（改后）

**`GET /app/collect/list?pageNum=1&pageSize=10`**（需登录）

```json
{ "code": 200, "msg": "ok", "data": { "total": 2, "pages": 1, "list": [
  { "id": "1001", "goodsId": "993001", "goodsName": "纯棉短袖T恤", "picUrl": "https://...", "price": "3900", "onSale": true,  "addTime": "2026-11-07 10:12:33" },
  { "id": "1002", "goodsId": "993002", "goodsName": "复古牛仔外套", "picUrl": "https://...", "price": "12900", "onSale": false, "addTime": "2026-11-06 09:00:00" }
] } }
```

字段说明：`price` 整数分（`Long`/`Integer` 会被全局序列化成字符串，前端 `Number()` 即可）；`onSale=false` 表示已下架；`goodsName`/`picUrl` 在商品被物理删时返回 `null`（不要造空串）。

**`POST /app/collect/add`**（需登录）

```json
// 请求
{ "goodsId": 993001, "type": 0 }   // 0=收藏, 1=取消
// 响应
{ "code": 200, "msg": "ok", "data": { "collected": true } }
```

契约**保持不变**（前端已按此写好），只改实现语义（物理删除 + 幂等）。`goodsId` 不存在或已删除 → 400 `PARAM_INVALID`（新增行为，要落地）。

### 6.5 实现要点

- `CollectServiceImpl.listCollect`：用扩展后的 `GoodsBriefDTO` 填 `price`/`onSale`；`brief == null` 时 `goodsName`/`picUrl`/`price` 留 `null`，`onSale` 给 `false`（前端按"已下架/不可用"渲染）。
- `CollectServiceImpl.addCollect`：`type=0` → 先查存在则直接返回 `true`；否则 `save`，`save` 包 `try/catch DuplicateKeyException` → `true`。`type=1` → `lambdaUpdate().eq(userId).eq(valueId).eq(type,0).remove()`（物理删除）。**不要**用 `deleted=true`。
- `GoodsInnerController#listGoodsBriefByIds`：现有实现用 `goodsService.listByIds` 后过滤 `deleted`；补 `price` 时**批量**取 SKU（一次 `in` 查询 + 分组），不要 N+1。
- `GoodsBriefDTO` 加字段后，检查其它调用方（`CollectServiceImpl`、`FootprintServiceImpl`）是否受影响（只读不写，安全）。

### 6.6 数据库变更（只写不执行）

新建文件 `db/migrations/2026-11-07-r3-01-collect.sql`，内容：

```sql
-- 【需人工确认】上线前执行；执行前必须确认 litemall_collect 无重复 (user_id,value_id,type)
-- 执行前清理逻辑删除的历史行（物理删除方案不再需要 deleted=true 的行）
DELETE FROM litemall_collect WHERE deleted = 1;

-- 若已存在重复行，先合并：保留 add_time 最早的一条，删除其余（下面这句会报错，报错即说明有重复，需人工处理）
ALTER TABLE litemall_collect ADD UNIQUE INDEX uk_collect_user_value_type (user_id, value_id, type);
```

> 说明：`type` 参与唯一键，保证"同一用户同一商品只 1 条"。不建唯一键也能靠应用层幂等（第 6.3 条），但并发下不绝对；**建议执行**。

### 6.7 前端改动（T2）

1. `frontend/mobile-web/src/api/collect.js`（新建）：`collectList({pageNum,pageSize})`、`collectToggle({goodsId,type})`，或直接复用 `api/user.js` 里已有的 `collectList`/`collectAdd`（**优先复用，别新建重复文件**）。
2. `views/goods/GoodsDetail.vue:181`：把 toast 换成真实收藏；未登录（`userStore.isLogin === false`）→ `router.push({path:'/login',query:{redirect:route.fullPath}})`；已登录 → 调 `collectAdd({goodsId, type: 已收藏?1:0})`，成功后按钮态切换（实心/空心 + 文案"已收藏"）。**收藏态初始化**：进入详情页时调一次 `collectList` 判断？不做——太重；改用 `GET /app/collect/list` 无法按商品查。**决定**：进入页面不预查，按钮初始为"收藏"，点击后再按返回值校正；若用户已收藏，点击一次显示"已收藏"（首次点击是"取消后再收藏"的幂等场景，不会产生重复行）。此偏差要写进交付报告。
3. `views/user/Me.vue:128-130`：「收藏」改为 `router.push('/collect')`（未登录先跳登录，`redirect=/collect`）；"关注店铺/足迹"保持 toast 或删掉（**不许新增假页面**；足迹后端已有，接不接自选，接了要写进报告）。
4. 新建 `views/user/Collect.vue` + 路由 `/collect`（`router/index.js`，`meta.requiresAuth` 若已有该机制则复用）：列表（主图、名称、价格、收藏时间）、左上返回、空态文案"还没有收藏的商品"、加载失败重试、下拉/点击加载更多（复用 `GoodsList.vue` 里已有的分页写法）。已下架行：整行 `opacity:.5`、右侧徽标「已下架」、点击不跳转（BR-FAV-03）。
5. 复用现有工具类：`utils/format.js` 的 `money2`/`yuan2`、`components/PriceText.vue`。

### 6.8 T1/T2 自验（做完立刻跑）

1. 登录拿 token，`POST /app/collect/add {goodsId,type:0}` 两次 → 两次都 `collected:true`，`SELECT COUNT(*)` 仍为 1（BR-FAV-02）。
2. `POST {type:1}` 后再 `{type:0}` → 仍 1 条（逻辑删除改物理删除后不撞键）。
3. `GET /app/collect/list` → 倒序、含 `price`/`onSale`、`total` 正确（BR-FAV-04）。
4. 后台把该商品下架（`PUT /admin/goods/...` 或 SQL 改 `is_on_sale=0`）→ 列表里 `onSale:false`（BR-FAV-03）。
5. 未登录打开商品详情点收藏 → 跳到 `/login?redirect=/goods/<id>`，登录后回到该商品（BR-FAV-01）。
6. 单元测试：`CollectServiceImplTest` 覆盖"重复收藏只 1 条""取消后重新收藏""下架商品 onSale=false""分页倒序"。

### 6.9 边界

- 足迹（`/app/footprint/**`）不在需求内；接前端属加分，做与不做都要在报告里写清。
- 收藏专题（`type=1`）不做（BR 未要求），但不要删表里的 `type` 语义。

---

## 7. T3/T4/T5 · 首页轮播与专题

### 7.1 需求原文（`需求文档.md:399-411` §5.12 + `:505-516` §6.3 第 2–4 步）

- **BR-CMS-01** 广告只投首页轮播位。
- **BR-CMS-02** `link_type`：`0` 无跳转、`1` 跳商品、`2` 跳专题。
- **BR-CMS-03** 首页最多 5 张，按排序取前 5。
- **R3-02** 后台配 3 个轮播（跳商品 / 跳专题 / 无跳转）。
- **R3-03** 点轮播分别落到商品页、专题页，**无白屏无死链**。
- **R3-04** 后台停用 1 个 → 首页**立即**只剩 2 张。
- 不通过判定：轮播死链、停用后仍展示。

### 7.2 现状与 9 个真缺陷（逐条已定位）

| # | 缺陷 | 证据（文件:行） | 后果 |
|---|---|---|---|
| D1 | `getAdList(String position)` 把 String 传给 `eq(Ad::getPosition, ...)`，而 `Ad.position` 是 `Integer`、DB 列是 `tinyint` | `litemall-marketing/.../service/impl/AdServiceImpl.java:31-42`；`domain/PO/Ad.java:45`；`litemall_ad.position tinyint` | 类型不匹配 → **恒 0 行**，首页轮播永远空、走静态兜底图 |
| D2 | 前端调 `adList({pageNum:1,pageSize:3})` 不传 `position` | `frontend/mobile-web/src/views/home/Home.vue:168` | 即使 D1 修好也传 null |
| D2b | **返回形状不一致**：后端 `Result<List<AdVO>>`（`data` 是数组），前端却读 `page.list` | `MarketingController.java:41`（`Result<List<AdVO>>`）vs `Home.vue:169`（`page.list 兜底为空数组`） | 即使 D1/D2 修好，`page.list` 仍是 `undefined` → 继续走静态兜底。**这是"轮播恒空"的第三个独立原因** |
| D3 | 时间窗用 `.lt(start).gt(end)` 强过滤 | `AdServiceImpl.java:37-38` | `start_time/end_time` 为 NULL 的行被整行排除（库里 id=3 就是这种） |
| D4 | 管理端 create/update **丢弃 `linkType`** | `MarketingInnerController.java:181-220`（只拷 name/link/url/position/content/startTime/endTime/enabled） | DB `link_type` 永远是默认 0，跳商品/跳专题永远不生效 |
| D5 | 表**没有排序字段** | `litemall_ad` 列清单（无 `sort_order`） | BR-CMS-03「按排序取前 5」无处落地 |
| D6 | 广告改/删后**不失效缓存**（`CacheService` 有 `delByPrefix` 但没人调） | `AdServiceImpl.getAdList` 用 `cacheService.getList("ad:list:"+position, ...)`，写入口无失效 | **R3-04 必挂**：停用后首页仍展示（缓存 300s） |
| D7 | C 端**没有专题页**，且**无专题详情端点** | `frontend/mobile-web/src/views` 无 topic 目录；`MarketingController.java:92` 只有 `/topic/list` | 跳专题 = 死链（R3-03 必挂） |
| D8 | 种子广告 id=995003 `link=/pages/coupon/list`（小程序路径） | `litemall_ad` 数据 | H5 死链 |

### 7.3 设计决策（照做）

1. **不改 `litemall_ad.position` 的类型**（`tinyint`），在服务层做白名单映射：`home_banner → 1`，未知 `position` 直接返回空列表（不要 `eq` 一个字符串）。C 端固定传 `position=home_banner`，服务端只认这一个值；后台表单让运营选"首页轮播"（值 1）。
2. **新增 `sort_order smallint NOT NULL DEFAULT 0`**（DDL 标【需人工确认】）。排序规则：`sort_order ASC, id DESC`。后台列表也按这个排。
3. **时间窗语义改为"空即不限"**：`(start_time IS NULL OR start_time <= now) AND (end_time IS NULL OR end_time >= now)`。
4. **上限 5**：接口支持 `limit` 参数（默认 5，最大 5），在服务层截断（BR-CMS-03）。
5. **缓存失效**：create/update/delete 三个写入口在**事务提交后**调 `cacheService.delByPrefix("ad:list:")`（参考 R2 已确立的 `afterCommit` 规则：`TransactionSynchronizationManager.isSynchronizationActive()` → `registerSynchronization(afterCommit → del)`；无事务时立即 del）。**必须在 `afterCommit`**，否则并发读会用旧快照回填。
6. **专题详情**：新增 `GET /app/market/topic/detail?topicId=`，返回专题基本信息 + 商品列表（`litemall_topic.goods` 是逗号分隔的商品 id 字符串）。商品信息复用 §6.3 扩展后的 `GoodsBriefDTO`（含 price/onSale）。
7. **C 端专题页**：新建 `/topic/:id` 路由页（头图 + 标题/副标题 + 商品瀑布流，商品可点进详情）。样式沿用 `views/goods/GoodsList.vue` 的卡片。
8. **跳转统一在前端解析**：轮播项返回 `{linkType, link}`，前端 `linkType=1 → /goods/<link>`、`2 → /topic/<link>`、`0 → 不跳`。**`link` 里不要存 URL**（历史数据存了小程序的路径，属于脏数据）；后台表单改成"跳转类型 + 目标 ID 选择器"。

### 7.4 接口契约（改后）

**`GET /app/market/ad/list?position=home_banner&limit=5`**（**免登录**，已在网关注释白名单）

```json
{ "code": 200, "msg": "ok", "data": { "total": 3, "pages": 1, "list": [
  { "id": "995001", "name": "秋季新品", "url": "https://cdn/.../a.jpg", "linkType": 1, "link": "993001", "content": "...", "position": "home_banner", "sortOrder": 10 }
] } }
```

> **必须改成 `PageVO<AdVO>`**（`data.total`/`data.pages`/`data.list`）。理由：前端 `Home.vue:169` 已经按 `page?.list` 消费（写这段代码的人以为后端返 `PageVO`），现状是"前端读 `.list`、后端返数组"，两者必须对齐；对齐到 `PageVO` 的改动最小且与全站列表接口一致。改形状前确认没有第二个调用方：`grep -rn "ad/list" frontend/ backend/` 里只有 mobile 首页在用。
> `AdVO` 增加 `linkType`（Integer）与 `sortOrder`（Integer）；`link` 保持 String（内部就是商品/专题 ID 字符串）。

**`GET /app/market/topic/detail?topicId=996001`**（免登录）

```json
{ "code": 200, "msg": "ok", "data": {
  "id": "996001", "title": "秋季上新", "subtitle": "换季好物", "picUrl": "https://...", "content": "<p>...</p>",
  "goodsList": [ { "id": "993001", "name": "纯棉短袖T恤", "picUrl": "https://...", "price": "3900", "onSale": true } ]
} }
```

不存在/已删除 → 404 `NOT_FOUND`（`NotFoundException`）。`goods` 字段里的 id 有失效项时跳过（不要 500）。
**免登录**：新增端点后必须同时把它加进网关 `hm.auth.excludePaths`（§9.2 的注），否则从首页点"跳专题"会 401。

**管理端**（`litemall-admin` → `litemall-marketing` 的 `/inner/admin/ad/*`）：
- `AdDTO` 增加 `linkType`、`sortOrder`（`Integer`）；
- create/update 必须落库这两个字段；
- 列表排序改 `sortOrder ASC, id DESC`；
- 参数校验：`linkType=1/2` 时 `link` 必须是非空数字 → 否则 400 `PARAM_INVALID`；`linkType=0` 时 `link` 置空（不要留脏值）。

### 7.5 前端改动

- **C 端** `Home.vue`：`const page = await adList({ position: 'home_banner', limit: 5 })`，从 `page.list` 取行（对齐后的形状）；点击按 `linkType` 跳转（`linkType=0` 不跳转）；图片加载失败用本地兜底图；最多 5 张。
- **C 端** 新增 `views/topic/TopicDetail.vue` + 路由 `/topic/:id`（`router/index.js`）。
- **后台** `views/marketing/AdList.vue`：表单补「跳转类型」下拉（无跳转/商品/专题）、「目标 ID」输入（或商品/专题下拉选择，成本自行控制）、「排序值」数字、时间窗（可留空）、图片地址（必填，`http(s)://` 校验）；列表补展示这三列。表单调 `api/catalog.js` 里已有的 admin 商品/专题接口取下拉数据。

### 7.6 数据库变更（只写不执行）

`db/migrations/2026-11-07-r3-02-ad-sort-order.sql`：

```sql
-- 【需人工确认】
ALTER TABLE litemall_ad ADD COLUMN sort_order smallint NOT NULL DEFAULT 0 COMMENT '排序值，越小越前' AFTER enabled;
-- 清理历史脏数据（小程序路径），按需执行
UPDATE litemall_ad SET link_type = 0, link = NULL WHERE link LIKE '/pages/%';
```

### 7.7 T3/T4/T5 自验

1. 修 D1/D2/D2b 后：`curl 'http://127.0.0.1:8000/app/market/ad/list?position=home_banner&limit=5'` → `code=200`、`data.list` 非空且 ≤5 条、每项含 `linkType`/`sortOrder`。
2. 造 3 条广告（`linkType` 0/1/2）→ 首页显示 3 张；点第 2 张进商品详情、第 3 张进专题页（无白屏）。
3. 后台把其中 1 条 `enabled=0` → **刷新首页立即**只剩 2 张（验证缓存真的失效了，必要时 `<5s`）。
4. 时间窗留空的广告也能显示（D3）。
5. 后台建广告选"跳商品"、填商品 ID → 保存后查库 `link_type=1`（D4）。

---

## 8. T6 · 公告条（只验收）

- 需求已定稿：**只做顶部公告条，不做弹窗**（Q6，`需求文档.md:562`）；BR-CMS-06 相应为"不做"。
- 现状：`frontend/mobile-web/src/views/home/Home.vue:46-49` 渲染公告条，`:190-194` 调 `noticeList({pageNum:1,pageSize:1})`；后端 `GET /app/market/notice/list`（免登录）已实现。
- 你要做的：**验收** —— 首页顶部能看到一条启用中的公告（库内 997001/997002 为启用态）；后台把两条都停用后刷新首页公告条消失（可作为"停用生效"的旁证）。把结论写进报告，不写代码。

---

## 9. T7 · AI 助手 · 网关与前端接入

### 9.1 需求变更（必须先登记，见 §12）

原需求 `需求文档.md:447-459` §5.15 明确"本期不交付 AI 能力、演示链路上不得出现入口"。现在业主改为**本期交付**，因此：

- **BR-AI-01 废止**，改为：AI 助手入口**只放两处**——「我的」页工具区第一项「AI 购物助手」、商品详情页底部「问 AI」小图标；**首页首屏不放**，保证"首页→分类→详情→购物车→下单→支付"这条演示主链路不被插入式入口打断。
- **BR-AI-02 改写**：`/assistant` 直达进入**真实会话页**；后端不可用/未配置密钥时显示可重试的降级提示，**不得白屏、不得 500 页面**。
- **BR-AI-03 废止**（后端本期交付）。
- **BR-AI-04 保留**：卡片文案与数据都来自真实接口，不得复用假数据；**mock LLM 允许，mock 工具数据不允许**（见 §9.4）。

### 9.2 网关接入（`litemall-gateway`）

1. `backend/litemall-next/litemall-gateway/src/main/resources/application.yaml` 增加路由（放在 `admin` 路由之后、`storage-file` 之前皆可）：

```yaml
      - id: agent
        uri: http://127.0.0.1:8100
        predicates:
          - Path=/agent/**
```

2. `hm.auth.roleRules` 增加一条：`/agent/**` → role `user`、aud `litemall-app`（与 `/app/**` 一致）。**不要**把 `/agent/**` 加进 `excludePaths`（会话必须知道"你是谁"）。

> 网关这两个键是**驼峰**（`application.yaml:79-95`）：`hm.auth.roleRules`、`hm.auth.excludePaths`。写成 kebab-case 不会报错但**不生效**。
> 另外：新增 `/app/market/topic/detail` 后**必须把它加进 `excludePaths`**（现有白名单只有 `/app/market/topic/list`），否则未登录用户从首页点"跳专题"的轮播会 401。
3. 注意网关的 `AuthGlobalFilter` 会**剥离外部自带的 `user-info`**，然后用 JWT 里的 userId 重新注入 `user-info: <userId>`（`AuthGlobalFilter.java:28,41-48,73`）。因此 agent 侧**只能读 `user-info`**（见 §10.1）。
4. 不要给 `/agent/**` 加 `StripPrefix`（agent 的路由前缀就是 `/agent`）。

### 9.3 前端 proxy（`frontend/mobile-web/vite.config.js`）

> ⚠️ 该文件当前**有他人未提交的改动**（`git diff frontend/mobile-web/vite.config.js` 看得到）。修改前先看 diff，**只增补 `/agent` 代理条目，不要覆盖或回退别人的改动**。

```js
'/agent': { target: 'http://127.0.0.1:8100', changeOrigin: true },
```

同样必须用 `127.0.0.1`（原因见 §16.6）。生产环境走网关（Nginx 反代 `/agent` 到网关 8000），不需要额外配置。

### 9.4 运行模式矩阵（验收口径）

| 变量 | 允许值 | 演示/验收要求 |
|---|---|---|
| `LLM_PROVIDER` | `mock` / `ark` | **都允许**。`mock` 用模板话术，不需密钥，`ark` 需 `ARK_API_KEY`。话术质量不计分 |
| `TOOL_FALLBACK` | `mock` / `remote` | **验收必须是 `remote`**（工具真调 Java 后端）。`mock` 只能用来自测 UI，**不得用于验收**——否则"AI 客服"演示的是假数据，等于违反 BR-AI-04 |
| `MALL_BASE_URL` | URL | `http://127.0.0.1:8000`（走网关，复用鉴权） |
| `SESSION_TTL` | 秒 | 默认 86400 |
| `RATE_LIMIT` | 次/分钟 | 默认 30；**必须真的生效**（见 §10.5） |

### 9.5 T7 自验

1. 带 token `curl -X POST http://127.0.0.1:8000/agent/session` → `code=200` 且返回 `session_id`（证明网关路由通）。
2. 不带 token 同请求 → **401**（真实状态码）。
3. `curl http://127.0.0.1:8000/agent/health`（带 token）→ `status=ok`。

---

## 10. T8 · agent 服务改造清单

目录：`backend/litemall-agent/mobile-agent/`（Python 3.14 + FastAPI + LangGraph，`app/` 下 `main.py`/`config.py`/`graph.py`/`llm.py`/`orchestrator.py`/`schemas.py`/`memory/session_store.py`/`tools/mall_tools.py`；测试 `tests/`，脚本 `scripts/`）。

### 10.1 身份头（**必修，否则永远查不到"我的订单"**）

`app/main.py` 的 `chat()` 现在读 `request.headers.get("X-User-Id")`，而网关注入的是 **`user-info`** → `user_id` 恒为空。

- 改为读 `user-info`（首行 `request.headers.get("user-info", "")`），保留对 `X-User-Id` 的兼容读取作为兜底，但**优先 `user-info`**。
- `Authorization` 头原样透传给 Java（`_gateway_get` 已有 `auth_header` 参数，确认真的用上了）。
- `user_id` 为空时：仍可回答通用问题（问候、推荐），但涉及"我的订单/我的券/我的售后"必须回"请先登录"，**不要**用空 userId 查库。
- 日志里**不要打印** `Authorization` 全量（只打印前 12 字符 + 长度）。

### 10.2 工具补全（`app/tools/mall_tools.py`）

现有 3 个：`search_goods`、`get_latest_order`、`get_coupons`。**必须补齐动作型工具**，否则"客服"只能闲聊：

| 工具 | 调用的真实端点 | 返回要点 |
|---|---|---|
| `search_goods(keyword, pageSize)` | `GET /app/goods/list?keyword=...&pageNum=1&pageSize=` | `{id,name,picUrl,displayPrice}` |
| `get_latest_order(status?)` | `GET /app/order/list?pageNo=1&pageSize=5&tab?` | `{orderId,orderSn,status,statusText,orderAmount,goodsList[0].goodsName,addTime}` |
| `get_order_detail(orderId)` | `GET /app/order/detail?orderId=` | 状态、金额、行、可执行动作 |
| `list_aftersale(status?)` | `GET /app/aftersale/list?pageNo=1&pageSize=5` | `{aftersaleId,orderSn,goodsName,typeText,statusText,amount}` |
| `get_my_coupons(status?)` | `GET /app/market/coupon/mylist?pageNum=1&pageSize=10&status=` | 券名、面额、门槛、到期 |
| `get_usable_coupons(goodsAmount)` | `GET /app/market/coupon/usable?goodsAmount=` | 可用券列表（整数分） |

要点：
- **响应信封解析**：`{code,msg,data}`，`code!=200` 一律当作工具失败（不要拿 `data` 硬解析）。
- 分页参数两套：商品/券是 `pageNum/pageSize`，订单/售后是 `pageNo/pageSize`（**别统一，服务端就是这样**）。
- 超时 3s，失败重试 1 次；仍失败返回 `{"ok":false,"reason":"..."}` 给 LLM，让它说"暂时查不到"，**不要抛 500**。
- 金额字段是**字符串化的整数分**，展示前 `/100` 保留 2 位；不要用浮点累加。

### 10.3 意图与节点（`app/graph.py`）

现有意图：`greeting/recommend/order/coupon/aftersale/fallback`，节点：`route_intent → goods|order|coupon|aftersale → answer`。

- `node_aftersale` 目前只出话术，改为**先调 `list_aftersale`** 再答。
- `node_order` 补「按状态筛选」与「查某一单」两条分支（用 `get_latest_order` / `get_order_detail`）。
- 新增意图 `login_required`：工具报"未登录"时走此分支，回答固定文案 + 引导登录。
- `fallback` 兜底话术必须包含"可以问我：找商品 / 查订单 / 我的优惠券 / 售后进度"。

### 10.4 卡片契约（前后端唯一约定，别改字段名）

```jsonc
{ "type": "product", "items": [ { "id": "993001", "name": "纯棉短袖T恤", "brief": "...", "price": "3900", "img": "https://..." } ] }
{ "type": "order",   "order":  { "orderSn": "20261107001", "orderId":"8001", "status": "101", "statusText": "待付款", "goodsName": "纯棉短袖T恤", "total": "3900", "time": "2026-11-07 10:00:00" } }
{ "type": "coupon",  "items": [ { "id": "9001", "name": "满100减10", "value": "1000", "desc": "满100元可用", "expire": "2026-12-31 23:59:59" } ] }
{ "type": "aftersale", "items": [ { "aftersaleId": "7001", "orderSn": "20261107001", "goodsName": "纯棉短袖T恤", "statusText": "待商家处理", "amount": "3900" } ] }
```

规则：`price/value/total/amount` 全是**整数分**（字符串）；`items` 最多 5 条；无卡片时 `cards` 返回 `[]`（不要 `null`/不要省略）。前端按 `type` 渲染，未知 `type` 忽略（不报错）。

### 10.5 限流与健壮性

- `RATE_LIMIT`（默认 30 次/分钟）**必须真实实现**：按 `user-info`（缺则按 session_id）在 Redis 里 `INCR + EXPIRE 60`，超限返回 `code=429`。Redis 不可用时降级为进程内计数并打 WARN（不要因此 500）。
- 会话不存在 → 自动创建（不要 400）。
- 空 `message` → 400（现有行为保留）。
- 全局异常处理：任何未捕获异常 → `code=500, msg="助手暂时不可用，请稍后再试"`，**不暴露堆栈**。
- 健康检查 `GET /agent/health` 增加 `"llm_configured": bool`、`"gateway_reachable": bool`（探一次 `/app/goods/list`），便于排障。

### 10.6 测试（必须新增）

`pytest` 用例（现有 9 个要全绿，新增 ≥8 个）：
1. 身份头：带 `user-info: 1` 的请求，工具上下文 `user_id == "1"`；不带头 → 引导登录。
2. 网关信封：`code=500` 的响应被识别为工具失败（不解析 `data`）。
3. 分页参数：订单用 `pageNo`、商品用 `pageNum`（断言实际请求 URL）。
4. 金额转换：`"3900"` → 展示 `39.00`。
5. 限流：第 31 次返回 `429`。
6. 空消息 400、未知 intent 走 fallback。
7. 卡片：`search_goods` 命中 → `cards[0].type == "product"`，≤5 条。
8. 会话不存在自动创建。

运行：`cd backend/litemall-agent/mobile-agent && python -m pytest -q`。

### 10.7 不做什么

- 不写 SSE 流式（`docs/接口契约.md` §7 的 `/agent/chat/stream` 保持"预留"，不做实现）。
- 不做后台客服（`admin-agent/` 空目录保持空，或删掉空目录并在报告说明）。
- 不改 LangGraph 的图结构范式（只加节点/边）。

---

## 11. T9 · C 端 AI 会话页

现状：只有占位页 `frontend/mobile-web/src/views/assistant/AssistantDisabled.vue`（42 行），路由 `router/index.js:38` 指向它，**无 api 封装、无卡片组件、无入口按钮**。

要做：
1. `api/agent.js`：`createSession()`、`sendChat({session_id, message})`、`clearSession({session_id})`、`health()`。
2. `views/assistant/Assistant.vue`（新，替换路由指向）：消息列表（用户右、助手左）、底部输入框 + 发送、发送中禁用、错误气泡可重试、右上"清空对话"、首次进入自动 `createSession` 并把 `session_id` 存 `sessionStorage`（刷新复用）。
3. 卡片组件 `components/AssistantCard.vue`：按 `type` 渲染 product（横向卡片，点击进详情）/order（点进订单详情）/coupon/aftersale；金额用 `money2`/`PriceText`。
4. **入口**（BR-AI-01 新规则）：
   - `views/user/Me.vue` 工具区第一项「AI 购物助手」→ `/assistant`（这是主入口）；
   - `views/goods/GoodsDetail.vue` 底部小图标「问 AI」→ `/assistant`（可带 `?ask=这件商品` 预填输入框）；
   - **首页不放**。
5. 未登录访问 `/assistant` → 跳登录并 `redirect=/assistant`（与 §6.7 一致）。
6. 降级：`/agent/health` 不通或 `code!=200` → 顶部黄条「AI 助手暂时不可用，点击重试」，输入框禁用；**不白屏**。
7. 旧占位页 `AssistantDisabled.vue`：**删除**（并确认无其它引用），或保留但不再被路由引用——二选一，在报告里写清。

---

## 12. 需求变更登记（必须写进 `需求文档.md` 附录 A）

| 编号 | 原文 | 变更为 | 理由 |
|---|---|---|---|
| CR-01 | BR-AI-01「演示链路上不得出现 AI 入口」（`需求文档.md:449`） | 入口只放「我的」页 + 商品详情页「问 AI」，首页不放 | 本期真交付 AI 能力，入口可放开；但首页首屏属于演示主链路，保持干净 |
| CR-02 | BR-AI-02「直达必须提示未启用」 | `/assistant` 直达真实会话页；后端不可用时降级提示 + 重试，不白屏 | 能力已交付，占位提示失去意义 |
| CR-03 | BR-AI-03「后端不在 R1–R3 范围」 | **废止**（本期交付 `backend/litemall-agent/mobile-agent`） | 业主明确要求交付 |
| CR-04 | BR-AI-04「单独立项时以现有页面与三类卡片为起点、不复用假数据」 | **保留**；补充"卡片增至 4 类（product/order/coupon/aftersale）"；工具数据必须真实（`TOOL_FALLBACK=remote`） | 防止演示假数据 |
| CR-05 | §6.3 R3 第 5 步「公告弹窗（若做）」 | 不做弹窗，只保留顶部公告条（与 Q6 `需求文档.md:562` 一致） | Q6 已定稿 |

写入位置：`需求文档.md` 附录 A「变更记录」末尾追加一行 v3.x；并在 §5.15 段落顶部加一行「> 本期已交付，见 R3-AI 需求书」的指引（**只加注，不删原文**）。

---

## 13. 数据库变更汇总（只写不执行，全部标【需人工确认】）

| 文件 | 内容 | 影响 |
|---|---|---|
| `db/migrations/2026-11-07-r3-01-collect.sql` | 清理 `deleted=1` 行 + `uk_collect_user_value_type(user_id,value_id,type)` | 收藏幂等的 DB 级保证 |
| `db/migrations/2026-11-07-r3-02-ad-sort-order.sql` | `litemall_ad` 加 `sort_order smallint NOT NULL DEFAULT 0`；可选清理 `/pages/%` 脏链接 | 轮播排序 |
| `db/seed/r3-demo-seed.sql`（新建，DML 可执行） | 3 条首页轮播（跳商品/跳专题/无跳转，`position=1`、`link_type` 分别是 1/2/0、`sort_order` 10/20/30）+ 2 条收藏演示数据（user_id=1） | 演示与验收 |

DML 要求：幂等（`DELETE ... WHERE id IN (精确列表)` 或 `INSERT ... ON DUPLICATE KEY UPDATE`），**不要用区间删除**（会删掉别人数据，见 §16.13）。

---

## 14. 最终验收脚本（逐条执行并记录真实输出）

### 14.1 R3 五步

| 步 | 操作 | 通过判据 |
|---|---|---|
| 1 | C 端登录 `user123/user123` → 商品详情点收藏 → 我的 → 收藏列表 → 取消收藏 | 收藏后列表出现该商品；取消后消失；再收藏恢复（不出现 2 条） |
| 2 | 后台新建 3 条轮播：跳商品（`linkType=1` + 商品 ID）、跳专题（`2` + 专题 ID）、无跳转（`0`） | 3 条都保存成功，回显 `linkType` 正确 |
| 3 | 首页点这 3 张 | 第 1 张进商品详情、第 2 张进专题页且商品列表非空、第 3 张无跳转；**均无白屏/404** |
| 4 | 后台停用 1 条 → 刷新首页 | 只剩 2 张（立即，不是 5 分钟后） |
| 5 | 公告条 | 首页顶部有 1 条启用中的公告文案（不做弹窗，检查未出现弹窗） |

### 14.2 AI 六步

| 步 | 操作 | 通过判据 |
|---|---|---|
| 1 | 未登录访问 `/assistant` | 跳登录，登录后回到 `/assistant` |
| 2 | 发送「你好」 | 有回复，无报错，回复包含能力引导 |
| 3 | 发送「推荐几款 T 恤」 | 回复带 **product 卡片**（≥1 条，≤5 条），点卡片进真实商品详情 |
| 4 | 发送「我的订单到哪了」 | 回复带 **order 卡片**，`orderSn/statusText` 与后台订单列表一致（拿真实订单核对） |
| 5 | 发送「我的优惠券」 | 回复带 **coupon 卡片**，面额/门槛与 `GET /app/market/coupon/mylist` 一致 |
| 6 | 发送「我的售后进度」 | 回复带 **aftersale 卡片**（有售后时）或明确说明"你暂无售后申请" |

**AI 验收的硬前提**：`TOOL_FALLBACK=remote`、9 个服务在跑、`/agent/health` 的 `gateway_reachable=true`。截图或贴 curl 输出留证。

### 14.3 回归

```powershell
cd D:\litemall-next\backend\litemall-next ; mvn -o verify          # 期望 18/18 模块，0 失败
cd D:\litemall-next\frontend\mobile-web  ; pnpm lint ; pnpm build   # 期望 0 error
cd D:\litemall-next\frontend\admin-web   ; pnpm lint ; pnpm build
cd D:\litemall-next\backend\litemall-agent\mobile-agent ; python -m pytest -q   # 期望全绿
```

---

## 15. 交付物清单与提交规范

| 交付物 | 位置 |
|---|---|
| 代码 | 后端 + 两个前端 + `backend/litemall-agent/mobile-agent` |
| SQL（不执行） | `db/migrations/2026-11-07-r3-01-collect.sql`、`.../r3-02-ad-sort-order.sql` |
| 演示数据 | `db/seed/r3-demo-seed.sql` |
| 交付报告 | `docs/impl/R3-交付报告.md`（含：改动清单、验收真实输出、未验证边界、待人工确认、需求变更 CR-01~05） |
| 接口契约回写 | `docs/design/07-接口设计-*.md` / `08-*.md`（新增 `CN-*` 编号要登记；收藏契约归 order 域、轮播/专题归 marketing 域） |
| 需求回写 | `需求文档.md` 附录 A（CR-01~05）+ §5.15 加注 |

提交：按任务分 commit，信息形如 `feat(R3-01): 我的收藏…`、`fix(R3-02): 修复首页轮播 position 类型不匹配导致恒空`、`feat(AI-01): 网关接入 /agent/**`、`docs(R3): 交付报告`。**只本地 commit，不 push。** 提交时显式给路径，**不要 `git add -A`**。

---

## 16. 已知坑与红线（每条都踩过）

1. **改完必须 `mvn package` 再启动**，`java -jar` 不会自动重载；启动前先 kill 旧进程，否则 `repackage` 报 `Unable to rename 'x.jar' to 'x.jar.original'`（文件锁）。
2. **`mvn -pl <module>` 必须加 `-am`**，否则兄弟模块解析失败。
3. **Maven 不要并行跑多个命令**，会互相锁 `~/.m2` 与 target；一次只跑一个 build。
4. **干净检出跑 `verify` 需要两个前置**：环境变量（§2.4）+ `litemall-common-core/src/main/resources/litemall.jks`（被 `.gitignore` 的 `*.jks` 排除，需手工放回）。
5. **MySQL 连接数**：9 个服务常驻约占 89 连接，`max_connections=151`；并发跑测试可能撞 `Too many connections`，可临时 `SET GLOBAL max_connections=600`，**用完改回 151**。
6. **Windows 上 `localhost` 可能解析到 `::1`**，网关只听 IPv4 → 一律用 `127.0.0.1`（前端 proxy、agent 的 `MALL_BASE_URL`、curl）。
7. **中文写文件**：不要用 `Set-Content -Encoding utf8`（会乱码）；用编辑工具或 `[System.IO.File]::WriteAllText(path, text, [Text.UTF8Encoding]::new($false))`。
8. **git-bash 无 `setsid`**，后台起服务用 PowerShell `Start-Process -RedirectStandardOutput`；`Start-Process` 不认 PowerShell 的 `cd`，用绝对路径或 `-WorkingDirectory`。
9. **`git commit -- <pathspec>` 对"存在于磁盘但无 tracked 文件"的目录会报 `pathspec did not match`**，路径列表要用 `git status --porcelain` 的真实输出。
10. **MySQL CLI 必须 `--default-character-set=utf8mb4`**，否则中文乱码。
11. **对外接口恒 HTTP 200**：别给 `/app/**` 加 `@ResponseStatus` 或 `ResponseEntity`；业务失败用 `errorCode`。
12. **`/inner/**` 必须保留真实非 2xx**，否则 Feign 把失败当成功（R1 曾因此出现"资损级"问题）。
13. **`db/seed` 脚本禁止区间删除**（`DELETE ... WHERE id BETWEEN a AND b`）——R2 曾删掉别人 71 行验收数据；用精确 `id IN (...)`。
14. **缓存失效必须在 `afterCommit`**，不能在 `@Transactional` 方法体内 `del`（会读到未提交旧快照回填，R2 踩过：分类树 60s 不更新）。
15. **`OrderListItemVO` 分页参数是 `pageNo`**，不是 `pageNum`（商品/券才是 `pageNum`）。
16. **金额是整数分且在 JSON 里是字符串**（`Long→ToStringSerializer`）；前端 `Number()` 后再 `/100`。
17. **前端有 2 条历史 eslint error**（`GoodsDetail.vue` 的 `specSaveKey` 未使用、组件名单词规则全仓通病）——**不许增加新的**，也不要求你修旧的（修了要单独说明）。
18. **`.gitignore` 有 `*.jks`**，keystore 不入库；不要试图提交它。
19. **工作区有他人未提交的改动**（`README.md`、`docker/docker-compose.yml`、`frontend/mobile-web/.env.*`、`vite.config.js`、`前端开发文档.md`、16 份 `application*.yaml`）。**不要提交、不要回退、不要覆盖**；改 `vite.config.js` 前先看 diff。
20. **不要删除用户文件**；不要 push；不要在生产库执行 DDL。
21. **AI 服务是独立进程**，不受 Maven 管；`pip install -r requirements.txt` 后 `python -m uvicorn app.main:app --port 8100`（或 `scripts/start_dev.bat`）。
22. **演示环境必须 9 个后端服务 + 2 个前端 + agent 同时在跑**；AI 验收缺一不可（`gateway_reachable` 会 false）。

---

## 17. 附录

### 17.1 关键文件索引

| 用途 | 路径 |
|---|---|
| 需求正文 | `需求文档.md`（§1.2 判据、§4.1 做/不做、§5.11 收藏、§5.12 CMS、§5.15 AI、§6.3 R3、附录 A 变更记录） |
| 接口总则 | `docs/design/06-接口设计-总则与错误码.md` |
| C 端接口 | `docs/design/07-接口设计-C端.md` |
| 后台/对内接口 | `docs/design/08-接口设计-后台与对内.md` |
| 实施口径 | `docs/impl/05-R2实施口径.md`（含 §5.20 缓存 afterCommit 规则，可复用） |
| R2 验收证据 | `docs/evidence/r2-08/R2-八步验收结论.md`（验收写法照抄这个格式） |
| 启动脚本 | `.dbwork/r2-accept/start-all.sh` |
| 网关路由与鉴权 | `backend/litemall-next/litemall-gateway/src/main/resources/application.yaml`、`filter/AuthGlobalFilter.java` |
| 收藏实现 | `backend/litemall-next/litemall-order/src/main/java/com/litemall/{controller/CollectController,service/impl/CollectServiceImpl,domain/PO/Collect,domain/VO/CollectVO,domain/DTO/CollectAddDTO,mapper/CollectMapper}.java` |
| 轮播实现 | `litemall-marketing/.../{controller/MarketingController,controller/MarketingInnerController,service/impl/AdServiceImpl,domain/PO/Ad,domain/VO/AdVO}.java` |
| 专题实现 | 同上目录的 `Topic*`、`litemall_topic` 表 |
| AI 后端 | `backend/litemall-agent/mobile-agent/`（`app/`、`tests/`、`docs/接口契约.md`） |
| C 端 AI 占位页 | `frontend/mobile-web/src/views/assistant/AssistantDisabled.vue` |

### 17.2 本期新增/变更端点一览

| 端点 | 状态 | 服务 |
|---|---|---|
| `GET /app/collect/list` | **改出参**（+`price`、`onSale`） | order |
| `POST /app/collect/add` | 改实现（物理删除、幂等），契约不变 | order |
| `GET /inner/goods/brief/list` | **改出参**（+`price`、`onSale`） | goods |
| `GET /app/market/ad/list` | 改实现 + **改形状**（`data` 数组 → `PageVO`；+`linkType`/`sortOrder`） | marketing |
| `GET /app/market/topic/detail` | **新增** | marketing |
| `GET /inner/admin/ad/list|create|update|delete` | 改出参/入参（+`linkType`、`sortOrder`） | marketing |
| `/agent/session`、`/agent/chat`、`/agent/session/clear`、`/agent/health` | 网关接入（新增路由与 role-rule） | gateway → agent(8100) |

### 17.3 术语

- **断链**：演示路径上任何一步点进去是 404/白屏/空页。
- **卡片**：AI 回复里附带的商品/订单/优惠券/售后结构化数据块。
- **`TOOL_FALLBACK=remote`**：AI 的工具调用真的打到 Java 后端（验收必须）。
- **afterCommit 失效**：事务提交后再删缓存，避免用未提交快照回填。
- **恒 200**：对外接口 HTTP 状态永远 200，业务错误只在 `code`/`errorCode` 里表达。

---

## 18. 开工前 5 分钟自检清单（执行者执行）

- [ ] 9 个后端服务能起、`curl /app/goods/list` 返回 `code=200`
- [ ] 两个前端能起、C 端能登录 `user123/user123`、后台能登录 `admin123/admin123`
- [ ] `mvn -o verify` 在我改代码前是绿的（基线）
- [ ] `git status` 里他人改动我知道哪些不能碰（§16.19）
- [ ] `python -m pytest -q` 在 agent 目录是绿的（基线 9 例）
- [ ] 我读过 §3 红线、§12 需求变更、§14 验收

> 任何一项不满足，先修环境，不要开始改代码。