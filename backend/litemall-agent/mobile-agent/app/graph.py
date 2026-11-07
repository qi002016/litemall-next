"""
graph.py — 购物助手 LangGraph 状态图
====================================
两张图按配置切换，都通过 `graph.ainvoke` 暴露同样的 {reply, cards}：

1) 原硬编码图（mock 基线，必须原样保留，现有 28 例测试依赖它）：
   route_intent →(条件路由)→ 工具节点 →(条件路由)→ answer / login_required → END

2) 模型自主工具调用图（P1 ①，仅 LLM_PROVIDER in (ark, deepseek) 且
   TOOL_FALLBACK=remote 时启用）：
   prepare → agent(bind_tools) →(有 tool_calls)→ ToolNode → 回 agent → finalize → END
   - 身份（user_id / auth_header）由 InjectedState 从图状态注入，模型看不到也改不了；
   - 工具负载以 JSON 串放在 ToolMessage.content，finalize 还原后按 §10.4 拼卡片；
   - 未登录 / 工具失败仍走固定文案，绝不把异常抛进图里。

会话持久化：checkpointer 来自 app.memory.persistent_checkpoint（P1 ③），
thread_id=session_id 保持不变。
"""

import json
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AIMessage, AnyMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode, tools_condition

from app.config import settings
from app.llm import get_chat_model, get_llm
from app.memory.persistent_checkpoint import get_checkpointer
from app.tools.langchain_tools import TOOLS
from app.tools.mall_tools import (
    _extract_order_id,
    _match_status,
    get_latest_order,
    get_my_coupons,
    get_order_detail,
    list_aftersale,
    search_goods,
)

# 固定文案（与 MockProvider / node_login_required 保持一字不差）
LOGIN_REPLY = "请先登录，登录后我就能帮你查订单、我的优惠券和售后进度啦～"
FAIL_REPLY = "暂时查不到相关数据，请稍后再试～"
FALLBACK_REPLY = "我还没完全理解你的意思，可以问我：找商品 / 查订单 / 我的优惠券 / 售后进度。"


class AgentState(TypedDict, total=False):
    """图状态：节点间传递的数据。"""

    message: str            # 用户消息
    user_id: str            # 网关透传 user-info
    auth_header: str        # 网关透传 Authorization（转发给 Java 鉴权）
    history: list[dict]     # 会话历史
    intent: str             # 意图
    tool_payload: dict | None  # 工具结果负载
    reply: str              # 回复文本
    cards: list[dict]       # 卡片数据（与前端契约一致，无卡片为 []）


# ============================================================
# 原硬编码图节点（保持原样，不动一行：28 例契约测试的基线）
# ============================================================
async def node_route_intent(state: AgentState) -> dict[str, Any]:
    llm = get_llm()
    # 每轮先把上一轮的工具负载清掉：checkpointer 会跨轮累积 state，
    # 不清的话「先问商品、再说你好」会让问候也带上上一轮的商品卡片。
    return {"intent": await llm.route_intent(state["message"]), "tool_payload": None}


async def node_goods(state: AgentState) -> dict[str, Any]:
    payload = await search_goods(
        state["message"], state.get("auth_header", ""), state.get("user_id", "")
    )
    return {"tool_payload": payload}


async def node_order(state: AgentState) -> dict[str, Any]:
    """两条分支（§10.3）：带长订单号 → 查某一单；否则按状态筛选 / 查最新一单。"""
    message = state["message"]
    uid = state.get("user_id", "")
    auth = state.get("auth_header", "")
    order_id = _extract_order_id(message)
    if order_id:
        payload = await get_order_detail(order_id, uid, auth)
    else:
        payload = await get_latest_order(uid, auth, _match_status(message))
    return {"tool_payload": payload}


async def node_coupon(state: AgentState) -> dict[str, Any]:
    payload = await get_my_coupons(state.get("user_id", ""), state.get("auth_header", ""))
    return {"tool_payload": payload}


async def node_aftersale(state: AgentState) -> dict[str, Any]:
    # 先调 list_aftersale 再答（§10.3），不再只出话术；工具负载自带话术与卡片
    payload = await list_aftersale(
        state.get("user_id", ""), state.get("auth_header", ""), _match_status(state["message"])
    )
    return {"tool_payload": payload}


async def node_answer(state: AgentState) -> dict[str, Any]:
    llm = get_llm()
    payload = state.get("tool_payload") or {}
    reply = await llm.compose_answer(state["message"], state["intent"], payload)
    cards = payload.get("cards", []) if payload else []
    return {"reply": reply, "cards": cards if cards else []}


async def node_login_required(state: AgentState) -> dict[str, Any]:
    """工具报未登录时走：固定文案 + 引导登录（§10.3）。"""
    return {
        "reply": LOGIN_REPLY,
        "cards": [],
        "intent": "login_required",
    }


def route_after_intent(state: AgentState) -> str:
    """条件路由：按意图决定下一个节点。"""
    return state.get("intent", "fallback")


def route_after_tool(state: AgentState) -> str:
    """工具节点之后：工具报未登录 → login_required，否则正常 answer。"""
    payload = state.get("tool_payload") or {}
    return "login_required" if payload.get("login_required") else "answer"


def build_legacy_graph(checkpointer):
    """组装原硬编码图（mock / ark+mock 等场景）。节点与边一字未改。"""
    g = StateGraph(AgentState)
    g.add_node("route_intent", node_route_intent)
    g.add_node("goods", node_goods)
    g.add_node("order", node_order)
    g.add_node("coupon", node_coupon)
    g.add_node("aftersale", node_aftersale)
    g.add_node("answer", node_answer)
    g.add_node("login_required", node_login_required)

    g.add_edge(START, "route_intent")
    g.add_conditional_edges(
        "route_intent",
        route_after_intent,
        {
            "recommend": "goods",
            "order": "order",
            "coupon": "coupon",
            "aftersale": "aftersale",
            "greeting": "answer",
            "fallback": "answer",
            "login_required": "login_required",  # 防御：规则/模型直接给出该意图时也能落地
        },
    )
    # 工具节点后统一过一条条件边，判断是否要引导登录（只加节点/边，不改图范式）
    for tool_node in ("goods", "order", "coupon", "aftersale"):
        g.add_conditional_edges(
            tool_node,
            route_after_tool,
            {"login_required": "login_required", "answer": "answer"},
        )
    g.add_edge("login_required", END)
    g.add_edge("answer", END)

    return g.compile(checkpointer=checkpointer)


# ============================================================
# 模型自主工具调用图（P1 ①）
# ============================================================
_AGENTIC_SYSTEM = (
    "你是 litemall 商城的购物助手，用简洁友好的中文回答。"
    "需要查商品、订单、优惠券、售后时，必须从提供的工具里选一个调用，不要凭空编造"
    "商品名、订单号、优惠券或金额；卡片数据由系统从工具结果拼接，你只负责文字。"
    "用户身份由系统注入，你不需要也不允许猜测或指定用户身份。"
    "工具返回 ok=false 时，如实说明暂时查不到，不要编造数据。"
)

# 工具名 → 意图（仅用于让返回契约里的 intent 与原图口径对齐）
_TOOL_INTENT = {
    "search_goods": "recommend",
    "get_latest_order": "order",
    "get_order_detail": "order",
    "list_aftersale": "aftersale",
    "get_my_coupons": "coupon",
    "get_usable_coupons": "coupon",
    "get_coupons": "coupon",
}

# §10.4 四类卡片的字段集合（字段名一字不改）
_CARD_ITEM_FIELDS = {
    "product": ("id", "name", "brief", "price", "img"),
    "coupon": ("id", "name", "value", "desc", "expire"),
    "aftersale": ("aftersaleId", "orderSn", "goodsName", "statusText", "amount"),
}
_ORDER_FIELDS = ("orderSn", "orderId", "status", "statusText", "goodsName", "total", "time")
_MONEY_FIELDS = {"price", "value", "total", "amount"}


class AgenticState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    message: str
    user_id: str
    auth_header: str
    history: list[dict]
    reply: str
    cards: list[dict]
    intent: str


def _fen_str(value) -> str:
    """金额一律整数分字符串；空值/非法值给 "0"，绝不产出浮点（§10.4）。"""
    try:
        return str(int(value))
    except (TypeError, ValueError):
        return "0"


def _normalize_item(item: dict, fields: tuple[str, ...]) -> dict:
    out = {}
    for f in fields:
        v = item.get(f, "")
        if f in _MONEY_FIELDS:
            v = _fen_str(v)
        elif not isinstance(v, str):
            v = "" if v is None else str(v)
        out[f] = v
    return out


def _normalize_cards(cards: list[dict]) -> list[dict]:
    """按 §10.4 契约拼卡片：只保留四类、金额整数分字符串、items≤5、无卡片返回 []。
    为什么还要规范化：工具层已产出合规数据，但模型自主链路可能串起多次工具调用，
    集中兜底一次能保证前端拿到的结构永远可信。"""
    out: list[dict] = []
    for card in cards or []:
        if not isinstance(card, dict):
            continue
        t = card.get("type")
        if t in _CARD_ITEM_FIELDS:
            items = [
                _normalize_item(i, _CARD_ITEM_FIELDS[t])
                for i in (card.get("items") or [])
                if isinstance(i, dict)
            ][:5]
            if items:
                out.append({"type": t, "items": items})
        elif t == "order":
            o = card.get("order") or {}
            if isinstance(o, dict):
                order = _normalize_item(o, _ORDER_FIELDS)
                out.append({"type": "order", "order": order})
    return out


async def node_prepare(state: AgenticState) -> dict[str, Any]:
    """入口：把 message + history 转成消息链；只在 START 后跑一次。"""
    msgs: list[AnyMessage] = [SystemMessage(content=_AGENTIC_SYSTEM)]
    for turn in state.get("history") or []:
        if not isinstance(turn, dict):
            continue
        if turn.get("user"):
            msgs.append(HumanMessage(content=str(turn["user"])))
        if turn.get("assistant"):
            msgs.append(AIMessage(content=str(turn["assistant"])))
    msgs.append(HumanMessage(content=state["message"]))
    return {"messages": msgs}


async def node_agent(state: AgenticState) -> dict[str, Any]:
    """让模型决定是否调工具。身份不进消息链（由 InjectedState 在 ToolNode 注入）。"""
    messages = state.get("messages") or []
    # 轮次保护：超过 4 轮工具调用后不再绑定工具，强制收口，避免模型无限循环
    tool_rounds = sum(1 for m in messages if isinstance(m, ToolMessage))
    try:
        llm = get_chat_model() if tool_rounds >= 8 else get_chat_model(tools=TOOLS)
        if llm is None:  # 防御：mock 模式误入 agentic 分支时不碰网络
            return {"messages": [AIMessage(content=FALLBACK_REPLY)]}
        ai = await llm.ainvoke(messages)
        return {"messages": [ai]}
    except Exception:  # noqa: BLE001 —— LLM 抖动仍当"暂时查不到"，不 500、不泄漏堆栈
        return {"messages": [AIMessage(content=FAIL_REPLY)]}


def _payloads(messages: list[AnyMessage]) -> list[dict]:
    """还原 ToolMessage 里的结构化负载；解析失败的内容忽略（不影响其它工具结果）。"""
    out = []
    for m in messages:
        if isinstance(m, ToolMessage):
            try:
                data = json.loads(m.content)
                if isinstance(data, dict):
                    out.append(data)
            except (TypeError, ValueError):
                continue
    return out


def _current_turn_messages(messages: list[AnyMessage]) -> list[AnyMessage]:
    """只取本轮消息（最后一个 HumanMessage 之后）。
    checkpointer 以 add_messages 跨轮累积 messages：不切分的话，历史轮的工具卡片
    会残留进本轮回复，历史成功负载也会让"工具全失败"的固定文案分支永不成立。"""
    last_human = -1
    for i, m in enumerate(messages):
        if isinstance(m, HumanMessage):
            last_human = i
    return messages[last_human + 1:] if last_human >= 0 else messages


def node_finalize(state: AgenticState) -> dict[str, Any]:
    """最终节点：还原负载 → 固定文案分支 → 按 §10.4 拼卡片 → 出 reply。"""
    messages = state.get("messages") or []
    turn = _current_turn_messages(messages)
    payloads = _payloads(turn)

    # 未登录优先：固定文案，不展示任何卡片
    if any(p.get("login_required") for p in payloads):
        return {"reply": LOGIN_REPLY, "cards": [], "intent": "login_required"}

    cards = _normalize_cards(
        [c for p in payloads for c in (p.get("cards") or []) if isinstance(c, dict)]
    )

    tool_names = [
        tc["name"]
        for m in turn
        if isinstance(m, AIMessage)
        for tc in (getattr(m, "tool_calls", None) or [])
        if isinstance(tc, dict) and tc.get("name")
    ]
    intent = "fallback"
    for n in tool_names:
        if n in _TOOL_INTENT:
            intent = _TOOL_INTENT[n]
            break

    # 工具全失败：固定"暂时查不到"，不出卡片
    if payloads and all(p.get("ok") is False for p in payloads):
        return {"reply": FAIL_REPLY, "cards": [], "intent": intent}

    # 正常：取最后一条没有 tool_calls 的 AIMessage 文本（即模型最终答复）
    reply = ""
    for m in reversed(turn):
        if isinstance(m, AIMessage) and not getattr(m, "tool_calls", None) and m.content:
            reply = m.content if isinstance(m.content, str) else str(m.content)
            break
    if not reply:
        for p in reversed(payloads):
            if p.get("text"):
                reply = p["text"]
                break
    if not reply:
        reply = FALLBACK_REPLY

    return {"reply": reply, "cards": cards, "intent": intent}


def build_agentic_graph(checkpointer):
    """组装模型自主工具调用图。"""
    g = StateGraph(AgenticState)
    g.add_node("prepare", node_prepare)
    g.add_node("agent", node_agent)
    g.add_node("tools", ToolNode(TOOLS))
    g.add_node("finalize", node_finalize)

    g.add_edge(START, "prepare")
    g.add_edge("prepare", "agent")
    g.add_conditional_edges(
        "agent",
        tools_condition,
        {"tools": "tools", "__end__": "finalize"},
    )
    g.add_edge("tools", "agent")
    g.add_edge("finalize", END)
    return g.compile(checkpointer=checkpointer)


def build_graph(checkpointer=None):
    """配置开关：仅"真实模型 + remote 工具"走模型自主分支，否则走原硬编码图。"""
    ckpt = checkpointer or get_checkpointer()
    if settings.use_agentic_graph:
        return build_agentic_graph(ckpt)
    return build_legacy_graph(ckpt)


graph = build_graph()
