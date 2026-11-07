# litemall 购物助手 Agent（mobile-agent）

C 端买家购物助手后端服务。Python + FastAPI + **LangGraph**，独立部署，经 Spring Cloud 网关 `/agent/**` 接入商城前端（`mobile-web`）。网关把 JWT 里的 userId 注入 `user-info` 头，本服务只认这个头（`X-User-Id` 作兜底），`Authorization` 原样透传给 Java 完成归属校验。

## 快速开始

```bash
cd D:\litemall-next\backend\litemall-agent\mobile-agent
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python run_agent.py        # 或 scripts\start_dev.bat（--reload）
```

默认 `LLM_PROVIDER=mock` + `TOOL_FALLBACK=mock`：**无需任何 Key、无需 Java 后端**即可启动并完成演示对话。P1 的持久化默认用本地 JSON 文件 saver，**无需新增依赖**（见「checkpointer 持久化」）。

- `MALL_BASE_URL` 一律用 `http://127.0.0.1:8000`（Windows 上 `localhost` 先解析 `::1`，网关只听 IPv4 → 502）。
- 验收必须 `TOOL_FALLBACK=remote`（工具真调 Java 后端，BR-AI-04 不允许假数据）。

单元测试（离线、确定性）：

```bash
python -m pytest -q
```

`tests/conftest.py` 把 anyio 固定为 asyncio 后端，根 `conftest.py` 强制 mock 模式，因此不依赖 trio、不发网络、离线全绿。

## 三种 LLM Provider 切换（改 `.env` 后重启，密钥只放 `.env`）

| provider | `.env` 关键项 | 说明 |
| --- | --- | --- |
| `mock`（默认） | `LLM_PROVIDER=mock`、`TOOL_FALLBACK=mock` | 本地关键词规则，零成本、离线可测 |
| `ark` | `LLM_PROVIDER=ark`、`ARK_API_KEY=`、`ARK_MODEL=`、`ARK_BASE_URL=` | 火山方舟，OpenAI 兼容协议 |
| `deepseek` | `LLM_PROVIDER=deepseek`、`DEEPSEEK_API_KEY=`、`DEEPSEEK_MODEL=deepseek-flash`、`DEEPSEEK_BASE_URL=https://api.deepseek.com` | **DeepSeek V4.1 Flash** |

命令行临时切换（Windows cmd）：

```bat
set LLM_PROVIDER=mock     && python run_agent.py
set LLM_PROVIDER=ark      && set ARK_API_KEY=... && set ARK_MODEL=... && python run_agent.py
set LLM_PROVIDER=deepseek && set DEEPSEEK_API_KEY=... && python run_agent.py
```

**DeepSeek 模型名**：官方 API 调用名是 **`deepseek-flash`**（对应 DeepSeek-V4.1-Flash-1017，即 V4.1 Flash）。旧名 `deepseek-v4-flash` 虽兼容但会路由到新版，**不要用**。`DEEPSEEK_MODEL` 默认已是 `deepseek-flash`。

**thinking 坑（必须知道）**：DeepSeek 默认开启 thinking 模式；thinking 模式下 `tool_choice:"required"` 会返回 HTTP 400，且 `temperature` 无效。因此工具调用链路必须在 `ChatOpenAI` 上显式传 `extra_body={"thinking":{"type":"disabled"}}`。本项目在 `app/llm.py` 的 `OpenAICompatProvider.build_chat_model()` 统一注入，`bind_tools` / `with_structured_output` / 流式 `astream` 三条链路都不会漏；`model_kwargs` 写法会触发 langchain 告警，故用原生 `extra_body` 字段。

**真实链路前置条件**：`LLM_PROVIDER=deepseek` 的端到端真实对话需要 `DEEPSEEK_API_KEY` 有效、`TOOL_FALLBACK=remote`，且 **9 个 Java 服务都在跑**（工具要真调网关 `/app/**`）。缺任一条都只能走 mock/降级，不算真实链路验收。

## 接口（统一信封 `{code, msg, data}`）

| 方法 | 路径 | body | data |
| --- | --- | --- | --- |
| POST | `/agent/session` | - | `{ session_id }` |
| POST | `/agent/chat` | `{ session_id, message }` | `{ reply, cards }`（无卡片 `cards: []`） |
| POST | `/agent/chat/stream` | `{ session_id, message }` | **SSE 流**（见下） |
| POST | `/agent/session/clear` | `{ session_id }` | `{ ok: true }` |
| GET | `/agent/health` | - | `status / llm_provider / tool_fallback / memory / llm_configured / gateway_reachable / checkpointer` |

`/agent/chat/stream` 返回 `Content-Type: text/event-stream`，身份头、限流、会话自动创建与 `/agent/chat` 完全一致。事件契约（**与前端一致，不要改**）：

```
event: delta
data: {"text":"..."}                       ← 逐段回复文本（可多次）

event: cards
data: {"cards":[...]}                      ← 结构化卡片，文本结束后发一次

event: done
data: {"reply":"完整文本","cards":[...]}    ← 结束，reply 为完整文本

event: error
data: {"code":500,"msg":"助手暂时不可用，请稍后再试"}   ← 出错（HTTP 仍是 200，不暴露堆栈）
```

每帧以空行分隔。`delta` 文本拼接后等于 `done.reply`；`cards` 与 `done.cards`、非流式 `/agent/chat` 的 `cards` 三者一致。真实 provider 的文本 token 来自 LLM `astream`；mock 模式把 reply 切成若干片吐出，保证离线可测；流式失败自动回退按段切片，不中断响应。

错误码：`400` 空 message、`429` 触发限流、`500` 服务内部错误（固定文案，不暴露堆栈）。

## LangGraph 设计（P1 ① 双分支）

`app/graph.py` 按 `settings.use_agentic_graph` 选择，**仅 `LLM_PROVIDER in (ark, deepseek)` 且 `TOOL_FALLBACK=remote` 走模型自主分支，否则一律走原硬编码图**。两张图都通过 `graph.ainvoke` 暴露同样的 `{reply, cards}`。

**原硬编码图（mock 基线，28 例契约测试依赖，原样保留）**：

```
START → route_intent ──条件路由──┬─ greeting/fallback → answer → END
                                ├─ recommend → goods ──────┐
                                ├─ order     → order ──────┤
                                ├─ coupon    → coupon ─────┼─ 条件边 ─┬─ answer → END
                                └─ aftersale → aftersale ──┘          └─ login_required → END
```

**模型自主工具调用图（P1 ①）**：

```
START → prepare → agent(bind_tools) ──有 tool_calls──→ ToolNode → 回到 agent
                        └──无 tool_calls──→ finalize → END
```

- `app/tools/langchain_tools.py` 用 `@tool` + 显式 Pydantic `args_schema` 把 7 个商城工具包装成 LangChain 工具，内部**只调用** `app/tools/mall_tools.py` 已有函数。
- **身份不可由模型伪造**：`user_id` / `auth_header` 用 langgraph 的 `InjectedState` 从图状态注入，显式 `args_schema` 把它们从对模型可见的参数里排除。
- 工具负载以 JSON 串放在 `ToolMessage.content`，`finalize` 还原后按 §10.4 拼卡片（四类、金额整数分字符串、items≤5、无卡片 `[]`）。
- 工具报未登录 → `login_required` 固定文案；工具失败 → 「暂时查不到」；LLM 抖动也不抛 500。
- `node_route_intent` / `node_order` 等原节点一字未改；`ark` + `mock` 仍走原图并用 `with_structured_output` 意图分类 / `ChatPromptTemplate` 生成话术。

## 工具层（§10.2）

| 工具 | 真实端点 | 分页 |
| --- | --- | --- |
| `search_goods` | `/app/goods/list` | `pageNum/pageSize` |
| `get_latest_order` | `/app/order/list` | `pageNo/pageSize` |
| `get_order_detail` | `/app/order/detail` | - |
| `list_aftersale` | `/app/aftersale/list` | `pageNo/pageSize` |
| `get_my_coupons`（`get_coupons` 兼容旧名） | `/app/market/coupon/mylist` | `pageNum/pageSize` |
| `get_usable_coupons` | `/app/market/coupon/usable` | - |

信封 `code!=200` 一律当工具失败（不解析 `data`）；超时 3s、失败重试 1 次，仍失败返回 `{"ok":false,"reason":...}` 让 LLM 说「暂时查不到」，不抛 500。金额是整数分字符串，展示前 `fen_to_yuan()` 做 `/100` 保留 2 位。

## 卡片契约（§10.4）

`product` / `order` / `coupon` / `aftersale` 四类，字段名见 `app/schemas.py`；`price/value/total/amount` 全是整数分字符串，`items` 最多 5 条，未知 `type` 忽略。P1 的 `finalize` 节点会再做一次规范化兜底。

## checkpointer 持久化（P1 ③）—— 不需要 PostgreSQL / Milvus

**P1 明确不引入 PostgreSQL，也不引入 Milvus / pgvector，代码与配置里都没有。** 理由：P1 只需要「会话状态进程重启不丢、多实例可共享」，一个本地文件（或现有 Redis 承载 history）就够；向量库属于 P2 做 RAG 时才需要，现在上纯属过度设计且增加部署面。

`app/memory/persistent_checkpoint.py` 把 `InMemorySaver` 换成可持久化实现：

- `CHECKPOINT_BACKEND=auto`（默认）/ `sqlite` / `redis` / `memory`，`thread_id=session_id` 保持不变。
- 默认 `LocalFileSaver`：继承全局 `InMemorySaver`，在其 `put` / `put_writes` / `delete_thread` 后把状态原子落到本地 JSON 文件，构造时恢复 → **进程重启后可读、同机/共享盘多实例可共享状态**；零第三方依赖、异步安全。
- `sqlite`：显式配置时尝试官方 `SqliteSaver`（`langgraph-checkpoint-sqlite`）。本机按隔离要求装到 `.p1deps`（`python -m pip install --target .p1deps langgraph-checkpoint-sqlite`，已写进 `.gitignore`），但该包依赖 `langgraph-checkpoint 4.3.x`，与全局 `4.0.1` 不兼容（`DeltaChannelHistory` 缺失），**默认不启用**；加载失败会 WARN 并自动降级，**绝不启动失败**。
- `redis`：Redis 已承载 `SessionStore` 的 history 与限流，checkpointer 仍走本地文件；`memory` 显式关闭持久化（仅调试）。
- pytest 环境固定用 `InMemorySaver`，保证契约测试确定性、不跨运行累积落盘状态；持久化重启可读由 `tests/test_p1_*.py` 直接实例化 `LocalFileSaver` 覆盖。
- `/agent/health` 的 `checkpointer` 字段上报实际后端（`file` / `sqlite` / `memory`）。

## 限流与健壮性（§10.5）

`RATE_LIMIT`（默认 30 次/分钟）按 `user-info`（缺则 `session_id`）在 Redis `INCR + EXPIRE 60`，超限返回 `code=429`；Redis 不可用时降级进程内计数并打 WARN。会话不存在自动创建，空 message 400，任何未捕获异常返回 `code=500` 固定文案。

## 真实链路自检与评估集（P1 ④）

```bash
# 无 AUTH_TOKEN 时明确打印 SKIP 并以 0 退出，不误报通过、不需要启服务
python scripts/integration_check.py
# 配好环境变量后真实打 health / session / chat / chat/stream 并打印断言
set MALL_BASE_URL=http://127.0.0.1:8000
set AGENT_URL=http://127.0.0.1:8100
set AUTH_TOKEN=Bearer xxx
python scripts/integration_check.py
```

`tests/test_eval.py` 是 ≥20 条中文问答评估集（greeting/recommend/order/coupon/aftersale/fallback/login_required），用 mock provider 离线跑，断言意图路由准确率 ≥0.85、回复不编造商品/订单号、卡片字段与金额口径合规。

## 目录结构

```
mobile-agent/
├── run_agent.py            # uvicorn 启动入口
├── conftest.py             # pytest：强制 mock 模式（离线）
├── requirements.txt
├── .env.example / .env     # 配置（.env 不入库，密钥只放这里）
├── .p1deps/                # 可选：隔离安装的 checkpoint 依赖（.gitignore 忽略）
├── app/
│   ├── main.py             # FastAPI：/agent/** 接口 + SSE + 限流
│   ├── config.py           # 全局配置（从 .env 读取）
│   ├── schemas.py          # 请求模型 + 卡片/SSE 契约说明
│   ├── llm.py              # LLM provider：mock / ark / deepseek
│   ├── graph.py            # LangGraph 双分支：硬编码图 / 模型自主工具调用
│   ├── orchestrator.py     # 编排：历史→图→记忆→返回
│   ├── memory/
│   │   ├── session_store.py          # 会话记忆（内存 / Redis）
│   │   └── persistent_checkpoint.py  # P1 ③ 可持久化 checkpointer
│   └── tools/
│       ├── mall_tools.py             # 商城工具（mock / Java 网关 remote）
│       └── langchain_tools.py        # P1 ① 7 个工具的 LangChain 包装
├── tests/                  # 契约、图路由、T8、P1、评估集
├── scripts/
│   ├── start_dev.bat
│   └── integration_check.py          # P1 ④ 真实链路自检
└── docs/接口契约.md          # 与前端/网关的契约
```

## 不做

后台客服（`admin-agent/` 保持空）；P2 的 RAG / 向量库（因此也不需要 Milvus）；P1 不做 PostgreSQL（理由见上）。
