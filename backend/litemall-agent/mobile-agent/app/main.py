"""
main.py — FastAPI 入口：/agent/** 接口
======================================
接口（网关 Path=/agent/** 原样转发，不剥前缀）：
  POST /agent/session            创建会话 → data: { session_id }
  POST /agent/chat               发送消息 → data: { reply, cards }
  POST /agent/chat/stream        流式发送消息 → SSE（delta/cards/done/error）
  POST /agent/session/clear      清空会话 → data: { ok: true }
  GET  /agent/health             健康检查（含 llm_configured / gateway_reachable / checkpointer）

统一信封与前端契约一致：{ "code": 200, "msg": "成功", "data": {...} }。
身份来自网关注入的 user-info（AuthGlobalFilter 剥离外部头后用 JWT 的 userId 重注入），
Authorization 原样透传给 Java 完成归属校验（Python 不解析 token）。
"""

import json
import logging
import time

import httpx
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel

from app.config import settings
from app.llm import degraded_reason, effective_provider, get_chat_model
from app.memory.persistent_checkpoint import checkpoint_mode
from app.memory.session_store import get_store
from app.orchestrator import run_chat
from app.schemas import ChatRequest, SessionRequest

logger = logging.getLogger("litemall-agent")

app = FastAPI(title="litemall 购物助手 Agent", version="0.1.0")

# 本地开发跨域；生产由网关收敛入口
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


def ok(data: dict) -> dict:
    """统一成功信封（与 Java Result.ok() 对齐）。"""
    return {"code": 200, "msg": "成功", "data": data}


def fail(code: int, msg: str) -> dict:
    return {"code": code, "msg": msg}


# ---------- 限流（§10.5：RATE_LIMIT 30 次/分钟，Redis INCR+EXPIRE60，降级进程内） ----------
_rate_local: dict[str, int] = {}
_rate_redis = None
_rate_redis_checked = False


def _get_rate_redis():
    """惰性连接限流用的 Redis；连不上时返回 None 由调用方降级（只探测一次）。"""
    global _rate_redis, _rate_redis_checked
    if _rate_redis_checked:
        return _rate_redis
    _rate_redis_checked = True
    if not settings.redis_url:
        return None
    try:
        import redis as redis_lib

        r = redis_lib.from_url(settings.redis_url, decode_responses=True)
        r.ping()
        _rate_redis = r
    except Exception as e:  # noqa: BLE001 —— 连不上不算致命，降级并告警
        logger.warning("限流 Redis 不可用，降级进程内计数：%s", e)
        _rate_redis = None
    return _rate_redis


def check_rate_limit(key: str) -> bool:
    """60 秒窗口内按 key 计数，超限返回 False。
    为什么用 INCR+EXPIRE：首次自增时设 60s 过期，天然实现"每分钟"滑动窗口。
    """
    limit = settings.rate_limit_per_min
    r = _get_rate_redis()
    if r is not None:
        try:
            rkey = f"agent:ratelimit:{key}"
            n = r.incr(rkey)
            if n == 1:
                r.expire(rkey, 60)
            return n <= limit
        except Exception as e:  # noqa: BLE001 —— 运行期 Redis 抖动同样降级，不 500
            logger.warning("限流 Redis 操作失败，降级进程内计数：%s", e)
    # 进程内固定窗口：key 自带分钟桶，避免跨窗口累积
    bucket = int(time.time()) // 60
    k = f"{key}:{bucket}"
    _rate_local[k] = _rate_local.get(k, 0) + 1
    return _rate_local[k] <= limit


# ---------- 全局异常处理（§10.5：固定文案，不暴露堆栈） ----------
@app.exception_handler(Exception)
async def _unhandled_exception(request: Request, exc: Exception):
    # 只记录异常类型与信息，堆栈既不写入响应也不打进日志
    logger.error("未捕获异常：%s: %s", type(exc).__name__, exc)
    return JSONResponse(
        status_code=200,
        content={"code": 500, "msg": "助手暂时不可用，请稍后再试"},
    )


class _SessionOut(BaseModel):  # 文档用
    session_id: str


@app.post("/agent/session", response_model=None)
async def create_session():
    """创建会话，返回 session_id。"""
    store = get_store()
    session_id = await store.create_session()
    return ok({"session_id": session_id})


@app.post("/agent/chat")
async def chat(req: ChatRequest, request: Request):
    """发送消息，返回 { reply, cards }。"""
    message = req.message.strip()
    if not message:
        return fail(400, "message 不能为空")

    # 网关注入的是 user-info（AuthGlobalFilter 会剥离外部自带的 X-User-Id，再用 JWT
    # 的 userId 重新注入）；X-User-Id 仅作直连/旧客户端的兜底（§9.2、§10.1）
    user_id = request.headers.get("user-info", "") or request.headers.get("X-User-Id", "")
    auth_header = request.headers.get("Authorization", "")

    # 日志只打前 12 字符 + 长度，绝不打印完整 Authorization（§10.1）
    logger.info(
        "chat: session=%s user-info=%s auth_prefix=%s auth_len=%d",
        req.session_id,
        user_id,
        auth_header[:12],
        len(auth_header),
    )

    # 限流按 user-info（缺则按 session_id），Redis/进程内双实现（§10.5）
    rate_key = f"user:{user_id}" if user_id else f"session:{req.session_id}"
    if not check_rate_limit(rate_key):
        return fail(429, "请求太频繁，请稍后再试")

    # 会话不存在自动创建，不要 400（§10.5）
    store = get_store()
    await store.ensure_session(req.session_id)

    data = await run_chat(req.session_id, message, user_id, auth_header)
    return ok(data)


# ---------- SSE 流式（P1 ②） ----------
# 事件契约（与前端一致，不要改）：
#   event: delta / data: {"text":"..."}                     逐段回复文本
#   event: cards / data: {"cards":[...]}                    结构化卡片，文本结束后发一次
#   event: done  / data: {"reply":"完整文本","cards":[...]}  结束
#   event: error / data: {"code":500,"msg":"助手暂时不可用，请稍后再试"}
SSE_ERROR = {"code": 500, "msg": "助手暂时不可用，请稍后再试"}


def _sse(event: str, data: dict) -> str:
    """SSE 帧：事件行 + 单行 JSON 数据 + 空行分隔。"""
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def _slice_reply(reply: str, size: int = 10) -> list[str]:
    """mock 模式把整段回复切成若干片，保证离线也能逐段吐出；拼接后与原文一致。"""
    return [reply[i : i + size] for i in range(0, len(reply), size)] if reply else []


async def _stream_chat(session_id: str, message: str, user_id: str, auth_header: str):
    """一次流式问答。为什么先跑 run_chat：身份、限流、会话、工具链路、卡片、记忆
    与非流式共用同一份实现，SSE 只负责"把文本逐段吐出"，避免两套业务逻辑漂移。
    真实 provider 的文本 token 改由 LLM.astream 产生（thinking disabled 由 get_chat_model
    保证）；流式失败回退按段切片，任何异常只发 error 事件，HTTP 始终 200、不暴露堆栈。"""
    try:
        data = await run_chat(session_id, message, user_id, auth_header)
        reply = data.get("reply", "")
        cards = data.get("cards", [])

        streamed = ""
        try:
            llm = get_chat_model(streaming=True)
        except Exception:  # noqa: BLE001 —— 真实 provider 缺 Key/配置异常时回退切片
            llm = None
        if llm is not None:
            from langchain_core.messages import HumanMessage, SystemMessage

            prompt = [
                SystemMessage(
                    content=(
                        "你是 litemall 商城购物助手，用简洁友好的中文回答。"
                        "只根据用户问题和已知结构化数据作答，不要编造商品、订单号或金额。"
                    )
                ),
                HumanMessage(
                    content=(
                        f"用户问题：{message}\n"
                        f"已知数据（卡片）：{json.dumps(cards, ensure_ascii=False)}"
                    )
                ),
            ]
            try:
                async for chunk in llm.astream(prompt):
                    text = chunk.content if isinstance(chunk.content, str) else ""
                    if text:
                        streamed += text
                        yield _sse("delta", {"text": text})
            except Exception:  # noqa: BLE001 —— 流式失败就退回按段切片，不中断响应
                streamed = ""

        if not streamed:
            for piece in _slice_reply(reply):
                yield _sse("delta", {"text": piece})
            streamed = reply

        yield _sse("cards", {"cards": cards})
        yield _sse("done", {"reply": streamed, "cards": cards})
    except Exception as e:  # noqa: BLE001 —— 只记类型与信息，堆栈既不进响应也不进日志
        logger.error("SSE 流式异常：%s: %s", type(e).__name__, e)
        yield _sse("error", SSE_ERROR)


@app.post("/agent/chat/stream")
async def chat_stream(req: ChatRequest, request: Request):
    """SSE 流式接口；身份头、限流、会话自动创建与 /agent/chat 完全一致。"""
    message = req.message.strip()
    if not message:
        return fail(400, "message 不能为空")

    user_id = request.headers.get("user-info", "") or request.headers.get("X-User-Id", "")
    auth_header = request.headers.get("Authorization", "")

    rate_key = f"user:{user_id}" if user_id else f"session:{req.session_id}"
    if not check_rate_limit(rate_key):
        return fail(429, "请求太频繁，请稍后再试")

    store = get_store()
    await store.ensure_session(req.session_id)

    return StreamingResponse(
        _stream_chat(req.session_id, message, user_id, auth_header),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/agent/session/clear")
async def clear_session(req: SessionRequest):
    """清空会话历史。"""
    store = get_store()
    await store.clear(req.session_id)
    return ok({"ok": True})


def _llm_configured() -> bool:
    # ark 需要密钥+模型；deepseek 模型名有官方默认值，只需密钥；mock 无需外部配置
    if settings.llm_provider == "ark":
        return bool(settings.ark_api_key and settings.ark_model)
    if settings.llm_provider == "deepseek":
        return bool(settings.deepseek_api_key)
    return True


async def _gateway_reachable() -> bool:
    """探一次 /app/goods/list；任何异常都记为不可达，不阻断健康检查。"""
    try:
        async with httpx.AsyncClient(base_url=settings.mall_base_url, timeout=1.0) as client:
            resp = await client.get("/app/goods/list", params={"pageNum": 1, "pageSize": 1})
            return str(resp.json().get("code")) == "200"
    except Exception:  # noqa: BLE001 —— 探测失败只影响该布尔字段，不让 health 500
        return False


@app.get("/agent/health")
async def health():
    """健康检查：返回各层运行模式 + LLM 配置与网关可达性（§10.5）。
    P1 追加 checkpointer 字段（原有字段一字不改，保证前端 health 兼容逻辑不破）。"""
    # 先实例化一次 provider：降级状态是惰性产生的，不触发就会把「已降级」误报成「生效 provider=deepseek」
    get_chat_model()
    return {
        "status": "ok",
        "llm_provider": settings.llm_provider,
        "tool_fallback": settings.tool_fallback,
        "memory": get_store().mode(),
        "llm_configured": _llm_configured(),
        "gateway_reachable": await _gateway_reachable(),
        "checkpointer": checkpoint_mode(),
        # 新增（不改原有字段）：配置了真实模型但缺 Key 时会降级为 mock，这里如实说明，
        # 免得排障时把「回复很套路」误判成模型效果差。
        "llm_effective_provider": effective_provider(),
        "llm_degraded_reason": degraded_reason(),
    }


@app.get("/health")
async def health_alias():
    """直连（不经网关）时的健康检查别名。"""
    return await health()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=settings.host, port=settings.port, workers=1)
