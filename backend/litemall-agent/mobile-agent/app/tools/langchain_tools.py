"""
langchain_tools.py — 把商城工具包装成 LangChain 工具（P1 ①）
============================================================
供 agent 节点 `ChatOpenAI.bind_tools(TOOLS)` 让模型自主决定调哪个工具，以及
`ToolNode(TOOLS)` 执行。内部只调用 app.tools.mall_tools 已有函数（只读依赖，
本文件绝不修改 mall_tools.py），保持 {ok, cards, empty, text, login_required} 负载口径。

两个关键约束（都是"为什么"，不是"是什么"）：
1. 身份不能由模型提供。user_id / auth_header 决定"查谁的数据"，若放进工具参数，
   模型可能凭空造一个 userId 造成越权。因此用 langgraph 的 InjectedState 从图状态
   注入，再用显式 args_schema 把该参数从对模型可见的 schema 里彻底排除。
2. 工具返回 JSON 字符串而不是 dict。ToolNode 会把返回值原样塞进 ToolMessage.content，
   字符串可被引擎稳定保留；最终节点再 json.loads 还原结构化负载来拼卡片。
   失败一律转成"暂时查不到"负载，绝不把异常抛进图里打断会话。
"""

import json
import re
from typing import Annotated, Any

from langchain_core.tools import tool
from langgraph.prebuilt import InjectedState
from pydantic import BaseModel, Field

from app.tools import mall_tools as mt


# ---------- 对模型可见的参数（Pydantic 描述清楚，身份字段一律不出现） ----------
class SearchGoodsArgs(BaseModel):
    keyword: str = Field(description="商品关键词或预算描述，例如「300 元以内的耳机」「降噪耳机」")


class GetLatestOrderArgs(BaseModel):
    status: str | None = Field(
        default=None,
        description="订单状态筛选，可选：待付款/待发货/已发货/待收货/已完成/退款中/已取消/待评价；不填查最新一单",
    )


class GetOrderDetailArgs(BaseModel):
    order_id: str = Field(description="要查询的订单号（长数字串）")


class ListAftersaleArgs(BaseModel):
    # 后端 AftersaleStatus.parseMulti 只接受状态码数字（非法直接 400），模型传"全部"
    # 这类自然语言会让工具假性失败，所以描述里明确"只传数字码、不确定别传"，
    # dispatch 处再做一次正则兜底。
    status: str | None = Field(
        default=None, description="可选的售后状态码数字（如 101），不确定时不要传"
    )


class GetMyCouponsArgs(BaseModel):
    status: str | None = Field(default=None, description="优惠券状态筛选，不填则查我的全部优惠券")


class GetUsableCouponsArgs(BaseModel):
    goods_amount: int = Field(description="下单商品金额，单位：整数分（例如 29900 表示 299.00 元）")


class GetCouponsArgs(BaseModel):
    status: str | None = Field(default=None, description="优惠券状态筛选，不填则查我的全部优惠券")


# ---------- 负载工具函数 ----------
def _dump(payload: dict) -> str:
    """结构化负载 → JSON 字符串；default=str 兜底任何非标准类型，绝不让工具抛异常。"""
    return json.dumps(payload, ensure_ascii=False, default=str)


def _fail(reason: str) -> dict:
    """工具失败负载：让上层说"暂时查不到"，而不是把 500 泄漏到会话。"""
    return {
        "ok": False,
        "reason": reason,
        "cards": [],
        "empty": True,
        "text": "暂时查不到相关数据，请稍后再试～",
    }


async def _dispatch(name: str, args: dict, user_id: str, auth_header: str) -> dict:
    """唯一一处真正调用 mall_tools 的分发器。
    为什么收敛到这里：ToolNode 路径（带 InjectedState）与 SSE 流式路径（直接传身份）
    共用同一份工具实现，避免两处逻辑漂移；异常统一收敛为失败负载。"""
    try:
        if name == "search_goods":
            return await mt.search_goods(args.get("keyword") or "", auth_header, user_id)
        if name == "get_latest_order":
            return await mt.get_latest_order(user_id, auth_header, args.get("status"))
        if name == "get_order_detail":
            return await mt.get_order_detail(args.get("order_id") or "", user_id, auth_header)
        if name == "list_aftersale":
            # 兜底：status 不是"数字或逗号分隔数字"就丢弃，避免模型传自然语言被后端 400
            raw = args.get("status")
            status = raw.strip() if isinstance(raw, str) else None
            if not (status and re.fullmatch(r"\d+(,\d+)*", status)):
                status = None
            return await mt.list_aftersale(user_id, auth_header, status)
        if name == "get_my_coupons":
            return await mt.get_my_coupons(user_id, auth_header, args.get("status"))
        if name == "get_usable_coupons":
            return await mt.get_usable_coupons(args.get("goods_amount") or 0, user_id, auth_header)
        if name == "get_coupons":
            return await mt.get_coupons(auth_header, user_id, args.get("status"))
        return _fail(f"未知工具：{name}")
    except Exception as e:  # noqa: BLE001 —— 工具层不抛异常，后端抖动只当"暂时查不到"
        return _fail(f"{type(e).__name__}: {e}")


async def execute_tool(name: str, args: dict, user_id: str, auth_header: str) -> dict:
    """给 SSE 流式循环直接调用：返回 dict 负载（不 JSON 化），身份由服务端显式传入。"""
    return await _dispatch(name, args, user_id, auth_header)


# ---------- 7 个 LangChain 工具（名称与 mall_tools 对齐） ----------
@tool(args_schema=SearchGoodsArgs)
async def search_goods(keyword: str, state: Annotated[dict, InjectedState]) -> str:
    """按关键词/预算搜索商城商品，返回商品卡片。用户想找商品、要推荐时使用。"""
    payload = await _dispatch("search_goods", {"keyword": keyword}, _uid(state), _auth(state))
    return _dump(payload)


@tool(args_schema=GetLatestOrderArgs)
async def get_latest_order(status: str | None, state: Annotated[dict, InjectedState]) -> str:
    """查询我的最新一笔订单，或按状态筛选订单，返回订单卡片。"""
    payload = await _dispatch("get_latest_order", {"status": status}, _uid(state), _auth(state))
    return _dump(payload)


@tool(args_schema=GetOrderDetailArgs)
async def get_order_detail(order_id: str, state: Annotated[dict, InjectedState]) -> str:
    """按订单号查询某一笔订单详情，返回订单卡片。用户给了具体订单号时使用。"""
    payload = await _dispatch("get_order_detail", {"order_id": order_id}, _uid(state), _auth(state))
    return _dump(payload)


@tool(args_schema=ListAftersaleArgs)
async def list_aftersale(status: str | None, state: Annotated[dict, InjectedState]) -> str:
    """查询我的售后申请进度，返回售后卡片。"""
    payload = await _dispatch("list_aftersale", {"status": status}, _uid(state), _auth(state))
    return _dump(payload)


@tool(args_schema=GetMyCouponsArgs)
async def get_my_coupons(status: str | None, state: Annotated[dict, InjectedState]) -> str:
    """查询我名下已有的优惠券，返回优惠券卡片。"""
    payload = await _dispatch("get_my_coupons", {"status": status}, _uid(state), _auth(state))
    return _dump(payload)


@tool(args_schema=GetUsableCouponsArgs)
async def get_usable_coupons(goods_amount: int, state: Annotated[dict, InjectedState]) -> str:
    """查询下单时指定商品金额可用的优惠券，返回优惠券卡片。"""
    payload = await _dispatch(
        "get_usable_coupons", {"goods_amount": goods_amount}, _uid(state), _auth(state)
    )
    return _dump(payload)


@tool(args_schema=GetCouponsArgs)
async def get_coupons(status: str | None, state: Annotated[dict, InjectedState]) -> str:
    """查询我的优惠券（兼容旧名），等价于 get_my_coupons。"""
    payload = await _dispatch("get_coupons", {"status": status}, _uid(state), _auth(state))
    return _dump(payload)


def _uid(state: dict) -> str:
    return (state or {}).get("user_id", "") or ""


def _auth(state: dict) -> str:
    return (state or {}).get("auth_header", "") or ""


# 供 graph.agent 节点 bind_tools / ToolNode 使用；顺序即呈现给模型的工具顺序
TOOLS: list[Any] = [
    search_goods,
    get_latest_order,
    get_order_detail,
    list_aftersale,
    get_my_coupons,
    get_usable_coupons,
    get_coupons,
]
