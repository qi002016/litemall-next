"""
mall_tools.py — 商城工具层（LangGraph 图节点的工具）
====================================================
TOOL_FALLBACK=mock：返回演示数据，离线可跑、不查真实库（自测与既有契约测试用）。
TOOL_FALLBACK=remote：调用 Java 网关 /app/** 真实接口，Authorization 原样透传。

工具统一返回"负载"字典，供 graph 节点拼卡片 / 给 LLM 措辞：
  成功     : {"ok": True,  "cards": [...], "empty": bool, "text": str}
  工具失败 : {"ok": False, "reason": "...", "cards": [], "empty": True, "text": "暂时查不到..."}
  未登录   : {"ok": False, "login_required": True, "reason": "未登录", ...}

为什么这样分层：langgraph 节点不该因为后端抖动抛异常（会 500 打断会话），后端失败
应作为一次"暂时查不到"的对话结果交给 LLM；未登录则由图的 login_required 分支引导登录。
"""

import re
from typing import Callable

import httpx

from app.config import settings

# ---------- 金额 / ID 口径（§3.2：金额整数分、ID 字符串化） ----------


def _s(value) -> str:
    """把任意值规整成整数分字符串；空值给 "0"，绝不产出浮点。"""
    if value is None or value == "":
        return "0"
    try:
        return str(int(value))
    except (TypeError, ValueError):
        return "0"


def _sid(value) -> str:
    """ID 一律字符串化输出（Java 侧已有 Long→ToStringSerializer，这里兜底）。"""
    return "" if value is None else str(value)


def fen_to_yuan(fen) -> str:
    """整数分 → '元.角分'（/100 保留 2 位，用整数除法避免浮点累加误差）。"""
    n = int(fen or 0)
    return f"{n // 100}.{n % 100:02d}"


def _take5(items: list) -> list:
    """卡片 items 最多 5 条（§10.4 硬约束），集中在一处截断避免各工具遗漏。"""
    return items[:5]


# ---------- 演示数据（与前端 agent-mock.js 对齐） ----------
DEMO_PRODUCTS = [
    {"id": 1001, "name": "降噪无线耳机 Pro", "brief": "主动降噪 · 30 小时续航 · 蓝牙 5.3", "price": 299, "category": "耳机", "bg": "#0d9488"},
    {"id": 1002, "name": "便携蓝牙音箱", "brief": "IPX7 防水 · 环绕立体声", "price": 159, "category": "耳机", "bg": "#2563eb"},
    {"id": 1003, "name": "智能手环 6", "brief": "心率血氧监测 · 14 天续航", "price": 249, "category": "智能穿戴", "bg": "#d97706"},
    {"id": 1004, "name": "轻薄笔记本 Air 14", "brief": "16G+512G · 1.3kg 轻薄机身", "price": 4999, "category": "电脑", "bg": "#7c3aed"},
    {"id": 1005, "name": "快充移动电源 20000mAh", "brief": "22.5W 双向快充 · 三口输出", "price": 129, "category": "配件", "bg": "#059669"},
    {"id": 1006, "name": "机械键盘 87 键", "brief": "青轴 · 全键无冲 · RGB 背光", "price": 339, "category": "配件", "bg": "#e11d48"},
    {"id": 1007, "name": "有机棉基础 T 恤", "brief": "100% 有机棉 · 简约百搭", "price": 89, "category": "服饰", "bg": "#64748b"},
    {"id": 1008, "name": "空气炸锅 5L", "brief": "无油低脂 · 智能菜单", "price": 399, "category": "家电", "bg": "#b45309"},
    {"id": 1009, "name": "5G 智能手机 X20", "brief": "6.7 英寸 · 5000mAh · 一亿像素", "price": 1999, "category": "手机", "bg": "#334155"},
]

DEMO_ORDER = {
    "orderSn": "LM202609251432",
    "orderId": "8001",
    "status": "301",
    "statusText": "已发货",
    "goodsName": "降噪无线耳机 Pro × 1",
    "total": "29900",
    "time": "2026-09-25 14:32 下单",
}

DEMO_COUPONS = [
    {"id": 1, "name": "新人无门槛券", "value": 10, "desc": "满 0 元可用", "expire": "09-30"},
    {"id": 2, "name": "满减券", "value": 30, "desc": "满 199 减 30", "expire": "10-15"},
    {"id": 3, "name": "品类券", "value": 50, "desc": "满 499 减 50", "expire": "10-31"},
]

# 演示态无真实售后记录：明确告知"暂无"，并把申请流程一并给出（§14.2 第 6 步允许）
DEMO_AFTERSALE_TEXT = (
    "你暂无售后申请。\n"
    "如需申请售后：① 在「我的订单」找到对应订单 ② 点击「申请售后」选退货/退款/换货 "
    "③ 填写原因提交，1 个工作日内审核 ④ 审核通过后按指引寄回或退款。"
)

AFTERSALE_TEXT = DEMO_AFTERSALE_TEXT  # 兼容旧名（旧 graph 曾导入）


def _svg_img(text: str, bg: str) -> str:
    """内联 SVG 演示图（离线可用），接入真实后端后由 picUrl 替代。"""
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300">'
        f'<rect width="100%" height="100%" fill="{bg}"/>'
        f'<text x="50%" y="50%" font-size="30" fill="#ffffff" text-anchor="middle" '
        f'dominant-baseline="middle">{text}</text></svg>'
    )
    import urllib.parse

    return f"data:image/svg+xml;utf8,{urllib.parse.quote(svg)}"


def _match_price(text: str) -> float | None:
    m = re.search(r"(\d+(?:\.\d+)?)\s*元", text)
    return float(m.group(1)) if m else None


def _match_category(text: str) -> str | None:
    """从消息里抽品类。做去空格/大小写归一，否则「T 恤」（中间带空格）会漏匹配。"""
    norm = (text or "").replace(" ", "").replace("\u3000", "").lower()
    table = [
        ("耳机", ("耳机", "音箱")),
        ("手机", ("手机",)),
        ("电脑", ("电脑", "笔记本")),
        ("智能穿戴", ("手环", "手表")),
        ("配件", ("键盘", "充电", "电源")),
        ("服饰", ("衣服", "t恤")),
        ("家电", ("家电", "空气炸锅")),
    ]
    for cat, keys in table:
        if any(k in norm for k in keys):
            return cat
    return None


# 商品搜索的"口头语"词表：mock/降级路径没有模型帮忙抽词，只能按词表剥。
# 只剥整词，剥完为空就退回不带关键字的列表（宁可给热门，也不要整句去精确匹配 0 条）。
_SEARCH_FILLERS = (
    "帮我", "给我", "我想", "我要", "想买", "想找", "找一下", "搜一下", "请", "麻烦",
    "可以", "能不能", "推荐", "几款", "一些", "一下", "看看", "有没有", "有什么",
    "什么", "商品", "东西", "好物", "一款", "几件", "一件", "找", "搜", "要",
    "多少钱", "多少", "价格", "台",
    "的", "吗", "呢", "啊", "吧", "呗", "个", "件", "我",
)


def _extract_keyword(message: str) -> str:
    """把「推荐几款 T 恤」这类整句压成「T 恤」；压不出来就返回空串。"""
    kw = (message or "").strip()
    for w in _SEARCH_FILLERS:
        kw = kw.replace(w, "")
    kw = kw.strip(" ，。！？、,.!?~～")
    return kw


def _match_status(message: str) -> str | None:
    """从消息里抽状态词，作为后端 status/tab 的筛选值（话术质量不计分）。"""
    for w in ("待付款", "待发货", "已发货", "待收货", "已完成", "退款中", "已取消", "待评价"):
        if w in message:
            return w
    return None


def _extract_order_id(message: str) -> str | None:
    """长数字（≥6 位）才当作"查某一单"的订单号，避免把预算数字误判成订单号。"""
    m = re.search(r"\d{6,}", message)
    return m.group(0) if m else None


# ---------- 网关请求公共封装（§10.2：超时 3s、失败重试 1 次、信封校验） ----------
class _GatewayFailure(Exception):
    """网关调用失败（网络异常 / 非 2xx / 信封 code!=200）。"""


async def _gateway_get(path: str, params: dict, auth_header: str) -> dict:
    """成功返回 data；任何失败抛 _GatewayFailure(reason)。
    为什么收敛成异常：让上层 _call 统一转成 {"ok":false}，不把 500/超时泄漏到会话。
    """
    # Authorization 原样透传给 Java，由 Java 完成归属校验（Python 不解析 token）
    headers = {"Authorization": auth_header} if auth_header else {}
    last = ""
    for _ in range(2):  # 首次 + 失败重试 1 次
        try:
            async with httpx.AsyncClient(
                base_url=settings.mall_base_url, timeout=3.0, headers=headers
            ) as client:
                resp = await client.get(path, params=params)
                resp.raise_for_status()
                body = resp.json()
            # 信封 code!=200 一律当工具失败，绝不硬解析 data（§10.2、§10.6-2）
            if str(body.get("code")) != "200":
                raise _GatewayFailure(
                    f"网关返回 code={body.get('code')}: {body.get('msg', '')}"
                )
            return body.get("data") or {}
        except _GatewayFailure:
            raise
        except Exception as e:  # noqa: BLE001 —— 网络/解析异常统一收敛，重试后仍失败才上报
            last = f"{type(e).__name__}: {e}"
    raise _GatewayFailure(last or "未知错误")


def _fail(reason: str) -> dict:
    """工具失败负载：给 LLM 说"暂时查不到"，而不是抛 500。"""
    return {
        "ok": False,
        "reason": reason,
        "cards": [],
        "empty": True,
        "text": "暂时查不到相关数据，请稍后再试～",
    }


async def _call(path: str, params: dict, auth_header: str, build: Callable) -> dict:
    try:
        data = await _gateway_get(path, params, auth_header)
    except _GatewayFailure as e:
        return _fail(str(e))
    return build(data)


def _need_login(user_id: str) -> bool:
    """为什么按模式区分：mock 是离线演示数据、不碰真实库，既有契约测试也在 mock 下
    断言未登录仍能出卡片；只有 remote（真打 Java）才必须在缺 user-info 时拒绝，
    避免用空 userId 查库（§10.1）。"""
    return settings.tool_fallback == "remote" and not user_id


def _login_required() -> dict:
    return {
        "ok": False,
        "login_required": True,
        "reason": "未登录",
        "cards": [],
        "empty": True,
        "text": "请先登录，登录后我就能帮你查订单、我的优惠券和售后进度啦～",
    }


# ---------- 卡片构造（§10.4 字段名一字不改，金额整数分，items ≤5） ----------
def _order_from(r: dict) -> dict:
    goods = r.get("goodsList") or []
    goods_name = (goods[0].get("goodsName") if goods and isinstance(goods[0], dict) else "") or ""
    return {
        "orderSn": _sid(r.get("orderSn") or r.get("orderId") or ""),
        "orderId": _sid(r.get("orderId") or r.get("id") or ""),
        "status": _sid(r.get("status") or ""),
        "statusText": r.get("statusText") or _sid(r.get("status") or ""),
        "goodsName": goods_name or "订单商品",
        "total": _s(r.get("orderAmount") or r.get("total") or 0),
        "time": r.get("addTime") or "",
    }


def _build_products(data: dict) -> dict:
    records = (data or {}).get("list") or []
    items = [
        {
            "id": _sid(r.get("id")),
            "name": r.get("name") or "",
            "brief": r.get("brief") or "",
            "price": _s(r.get("minPrice") or r.get("retailPrice") or r.get("price")),
            "img": r.get("picUrl") or "",
        }
        for r in records
    ]
    items = _take5(items)
    return {
        "ok": True,
        "cards": [{"type": "product", "items": items}] if items else [],
        "empty": not items,
        "text": "为你找到这几款商品，看看合不合适：" if items else "暂时没找到匹配的商品。",
    }


def _build_order_list(data: dict) -> dict:
    records = (data or {}).get("list") or []
    if not records:
        return {"ok": True, "cards": [], "empty": True, "text": "暂时没有查到订单。"}
    order = _order_from(records[0])
    return {
        "ok": True,
        "cards": [{"type": "order", "order": order}],
        "empty": False,
        # 话术展示金额按 /100 保留 2 位；卡片本身仍是整数分字符串（§10.4）
        "text": f"查到你最近有一笔订单（金额 {fen_to_yuan(order['total'])} 元），当前状态如下：",
    }


def _build_order_detail(data: dict) -> dict:
    r = data or {}
    if not r:
        return {"ok": True, "cards": [], "empty": True, "text": "没有查到这笔订单。"}
    # detail 端点可能把订单包在 order 字段里，也可能直接是订单对象
    r = r.get("order") if isinstance(r.get("order"), dict) else r
    return {
        "ok": True,
        "cards": [{"type": "order", "order": _order_from(r)}],
        "empty": False,
        "text": "这笔订单的详情如下：",
    }


def _build_aftersale(data: dict) -> dict:
    records = (data or {}).get("list") or []
    items = [
        {
            "aftersaleId": _sid(r.get("aftersaleId") or r.get("id") or ""),
            "orderSn": _sid(r.get("orderSn") or ""),
            "goodsName": r.get("goodsName") or "",
            "statusText": r.get("statusText") or "",
            "amount": _s(r.get("amount") or 0),
        }
        for r in records
    ]
    items = _take5(items)
    return {
        "ok": True,
        "cards": [{"type": "aftersale", "items": items}] if items else [],
        "empty": not items,
        "text": "查到你的售后进度如下：" if items else "你暂无售后申请。",
    }


def _build_coupons(data: dict) -> dict:
    # usable 端点可能返回数组而非 PageVO，兼容两种形状
    records = (data or {}).get("list") if isinstance(data, dict) else None
    if records is None:
        records = data if isinstance(data, list) else []
    items = [
        {
            "id": _sid(c.get("id") or c.get("couponId") or ""),
            "name": c.get("name") or "",
            "value": _s(c.get("discount") or c.get("amount") or c.get("value") or 0),
            "desc": c.get("desc") or "",
            "expire": str(c.get("endTime") or c.get("expire") or ""),
        }
        for c in records
    ]
    items = _take5(items)
    return {
        "ok": True,
        "cards": [{"type": "coupon", "items": items}] if items else [],
        "empty": not items,
        "text": "这些优惠券可以使用：" if items else "暂时没有可用的优惠券。",
    }


def _mock_products(message: str) -> dict:
    cat = _match_category(message)
    price = _match_price(message)
    pool = [p for p in DEMO_PRODUCTS if (not cat or p["category"] == cat)]
    if price is not None:
        pool = [p for p in pool if p["price"] <= price]
    if not pool:  # 兜底：给热销低价款
        pool = sorted(DEMO_PRODUCTS, key=lambda p: p["price"])[:5]
    items = [
        {
            "id": _sid(p["id"]),
            "name": p["name"],
            "brief": p["brief"],
            # 演示数据的 price 是"元"，×100 转整数分（与真实接口口径一致）
            "price": _s(int(p["price"]) * 100),
            "img": _svg_img(p["name"][:4], p["bg"]),
        }
        for p in pool[:5]
    ]
    return {
        "ok": True,
        "cards": [{"type": "product", "items": items}] if items else [],
        "empty": not items,
        "text": "为你找到这几款商品，看看合不合适：",
    }


def _mock_order() -> dict:
    return {
        "ok": True,
        "cards": [{"type": "order", "order": dict(DEMO_ORDER)}],
        "empty": False,
        "text": "查到你最近有一笔订单，当前状态如下：",
    }


def _mock_coupons() -> dict:
    items = [
        {
            "id": _sid(c["id"]),
            "name": c["name"],
            "value": _s(int(c["value"]) * 100),
            "desc": c["desc"],
            "expire": c["expire"],
        }
        for c in DEMO_COUPONS[:5]
    ]
    return {
        "ok": True,
        "cards": [{"type": "coupon", "items": items}] if items else [],
        "empty": not items,
        "text": "这些优惠券可以使用：",
    }


# ---------- 工具实现（§10.2 六个动作 + 兼容旧名 get_coupons） ----------
async def search_goods(message: str, auth_header: str = "", user_id: str = "") -> dict:
    """找商品：GET /app/goods/list?keyword=&pageNum=1&pageSize=（商品用 pageNum/pageSize）。

    真实模型会用 bind_tools 给出干净关键词；mock / 降级路径只有整句，
    所以按「整句 → 抽词 → 抽词去空格 → 不带关键词取热门」依次退让。
    为什么要有「去空格」这一步：库里是「素色圆领T恤」（无空格），
    而用户常写「T 恤」，带空格去搜恒为 0 条。
    """
    if settings.tool_fallback == "mock":
        return _mock_products(message)
    candidates: list[str] = []

    def _add(kw: str) -> None:
        kw = (kw or "").strip()
        if kw and kw not in candidates:
            candidates.append(kw)

    _add(message)
    extracted = _extract_keyword(message)
    _add(extracted)
    _add(extracted.replace(" ", "").replace("\u3000", ""))

    payload: dict = {}
    for kw in candidates:
        payload = await _call(
            "/app/goods/list", {"keyword": kw, "pageNum": 1, "pageSize": 5}, auth_header, _build_products
        )
        if not payload.get("empty"):
            return payload
    # 关键词全落空：给不带关键字的热门列表，别让演示卡在「暂时没找到」
    if candidates:
        return await _call("/app/goods/list", {"pageNum": 1, "pageSize": 5}, auth_header, _build_products)
    return payload


async def get_latest_order(user_id: str = "", auth_header: str = "", status: str | None = None) -> dict:
    """最新订单 / 按状态筛选：GET /app/order/list?pageNo=1&pageSize=5（订单用 pageNo/pageSize）。"""
    if _need_login(user_id):
        return _login_required()
    if settings.tool_fallback == "mock":
        return _mock_order()
    params = {"pageNo": 1, "pageSize": 5}
    if status:
        params["status"] = status
    return await _call("/app/order/list", params, auth_header, _build_order_list)


async def get_order_detail(order_id: str, user_id: str = "", auth_header: str = "") -> dict:
    """查某一单：GET /app/order/detail?orderId=。"""
    if _need_login(user_id):
        return _login_required()
    if settings.tool_fallback == "mock":
        return _mock_order()
    return await _call("/app/order/detail", {"orderId": order_id}, auth_header, _build_order_detail)


async def list_aftersale(user_id: str = "", auth_header: str = "", status: str | None = None) -> dict:
    """售后进度：GET /app/aftersale/list?pageNo=1&pageSize=5（售后用 pageNo/pageSize）。"""
    if _need_login(user_id):
        return _login_required()
    if settings.tool_fallback == "mock":
        # 演示态不查库，明确告知"暂无"并给流程指引，避免出假售后卡片
        return {"ok": True, "cards": [], "empty": True, "text": DEMO_AFTERSALE_TEXT}
    params = {"pageNo": 1, "pageSize": 5}
    if status:
        params["status"] = status
    return await _call("/app/aftersale/list", params, auth_header, _build_aftersale)


async def get_my_coupons(user_id: str = "", auth_header: str = "", status: str | None = None) -> dict:
    """我的优惠券：GET /app/market/coupon/mylist?pageNum=1&pageSize=10（券用 pageNum/pageSize）。"""
    if _need_login(user_id):
        return _login_required()
    if settings.tool_fallback == "mock":
        return _mock_coupons()
    params = {"pageNum": 1, "pageSize": 10}
    if status:
        params["status"] = status
    return await _call("/app/market/coupon/mylist", params, auth_header, _build_coupons)


async def get_usable_coupons(goods_amount, user_id: str = "", auth_header: str = "") -> dict:
    """下单可用券：GET /app/market/coupon/usable?goodsAmount=（整数分）。"""
    if _need_login(user_id):
        return _login_required()
    if settings.tool_fallback == "mock":
        return _mock_coupons()
    return await _call(
        "/app/market/coupon/usable", {"goodsAmount": _s(goods_amount)}, auth_header, _build_coupons
    )


async def get_coupons(auth_header: str = "", user_id: str = "", status: str | None = None) -> dict:
    """兼容旧名：等价于 get_my_coupons（旧 graph 曾以位置参数 auth_header 调用）。"""
    return await get_my_coupons(user_id, auth_header, status)
