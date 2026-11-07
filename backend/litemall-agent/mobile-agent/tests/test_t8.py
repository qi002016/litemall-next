"""
tests/test_t8.py — T8 改造验收用例（§10.6 的 8 项 + 健壮性补充）
================================================================
离线、确定性：conftest.py 固定 mock/asyncio；remote 行为用 monkeypatch 的假网关，
不发真实网络请求，也不改动 Java 服务。
"""

import httpx
import pytest
from httpx import ASGITransport, AsyncClient

import app.main as main_mod
from app.config import settings
from app.main import app
from app.memory.session_store import get_store
from app.tools import mall_tools

pytestmark = pytest.mark.anyio


def _client(raise_app_exceptions: bool = True) -> AsyncClient:
    transport = ASGITransport(app=app, raise_app_exceptions=raise_app_exceptions)
    return AsyncClient(transport=transport, base_url="http://test")


# ---------- 假网关：捕获真实请求 URL / headers，返回可控信封 ----------
class _FakeResponse:
    def __init__(self, body, status_code=200):
        self._body = body
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise httpx.HTTPStatusError("err", request=None, response=None)

    def json(self):
        return self._body


class _FakeAsyncClient:
    calls: list[dict] = []
    body: dict = {"code": 200, "data": {"list": []}}

    def __init__(self, *args, **kwargs):
        self.headers = kwargs.get("headers", {})

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return None

    async def get(self, path, params=None):
        params = params or {}
        # 用 httpx 真实构造带 query 的请求，断言的就是这个 URL
        req = httpx.Request("GET", f"http://127.0.0.1:8000{path}", params=params)
        _FakeAsyncClient.calls.append(
            {"path": path, "params": params, "headers": self.headers, "url": str(req.url)}
        )
        return _FakeResponse(_FakeAsyncClient.body)


@pytest.fixture
def fake_gateway(monkeypatch):
    _FakeAsyncClient.calls = []
    _FakeAsyncClient.body = {"code": 200, "data": {"list": []}}
    monkeypatch.setattr(mall_tools.httpx, "AsyncClient", _FakeAsyncClient)
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    return _FakeAsyncClient


# ---------- §10.6-1 身份头 ----------
async def test_identity_user_info_wins_and_auth_forwarded(monkeypatch):
    captured = {}

    async def fake_run_chat(session_id, message, user_id, auth_header):
        captured["user_id"] = user_id
        captured["auth"] = auth_header
        return {"reply": "ok", "cards": []}

    monkeypatch.setattr(main_mod, "run_chat", fake_run_chat)
    async with _client() as c:
        resp = await c.post(
            "/agent/chat",
            json={"session_id": "s1", "message": "我的订单"},
            headers={
                "user-info": "1",
                "X-User-Id": "2",
                "Authorization": "Bearer abcdefghijklmnop",
            },
        )
    assert resp.json()["code"] == 200
    assert captured["user_id"] == "1"  # user-info 优先于 X-User-Id
    assert captured["auth"] == "Bearer abcdefghijklmnop"  # Authorization 原样透传


async def test_identity_x_user_id_fallback(monkeypatch):
    captured = {}

    async def fake_run_chat(session_id, message, user_id, auth_header):
        captured["user_id"] = user_id
        return {"reply": "ok", "cards": []}

    monkeypatch.setattr(main_mod, "run_chat", fake_run_chat)
    async with _client() as c:
        await c.post(
            "/agent/chat",
            json={"session_id": "s1b", "message": "你好"},
            headers={"X-User-Id": "9"},
        )
    assert captured["user_id"] == "9"  # 缺 user-info 时用 X-User-Id 兜底


async def test_not_login_guides_login(monkeypatch):
    # remote 且缺 user-info：工具报未登录 → login_required 分支，"我的"类不查库
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    async with _client() as c:
        resp = await c.post(
            "/agent/chat", json={"session_id": "s2", "message": "我的订单到哪了"}
        )
    body = resp.json()
    assert body["code"] == 200
    assert "请先登录" in body["data"]["reply"]
    assert body["data"]["cards"] == []


# ---------- §10.6-2 信封 code!=200 ----------
async def test_envelope_code_500_is_tool_failure(fake_gateway):
    fake_gateway.body = {
        "code": 500,
        "msg": "内部错误",
        "data": {"list": [{"id": "HACK-SHOULD-NOT-BE-PARSED"}]},
    }
    result = await mall_tools.search_goods("耳机", "Bearer x", "1")
    assert result["ok"] is False
    assert result["cards"] == []
    assert "HACK-SHOULD-NOT-BE-PARSED" not in str(result)  # 不硬解析 data


# ---------- §10.6-3 分页参数两套 ----------
async def test_pagination_pageNo_vs_pageNum(fake_gateway):
    fake_gateway.body = {"code": 200, "data": {"list": []}}

    await mall_tools.search_goods("T恤", "Bearer x", "1")
    goods_url = fake_gateway.calls[-1]["url"]
    assert "pageNum=1" in goods_url and "pageNo" not in goods_url

    await mall_tools.get_latest_order("1", "Bearer x")
    order_url = fake_gateway.calls[-1]["url"]
    assert "pageNo=1" in order_url and "pageNum" not in order_url

    await mall_tools.list_aftersale("1", "Bearer x")
    aftersale_url = fake_gateway.calls[-1]["url"]
    assert "pageNo=1" in aftersale_url and "pageNum" not in aftersale_url

    await mall_tools.get_my_coupons("1", "Bearer x")
    coupon_url = fake_gateway.calls[-1]["url"]
    assert "pageNum=1" in coupon_url and "pageNo" not in coupon_url


# ---------- §10.6-4 金额转换 ----------
async def test_fen_to_yuan_3900():
    assert mall_tools.fen_to_yuan("3900") == "39.00"
    assert mall_tools.fen_to_yuan("5") == "0.05"
    assert mall_tools.fen_to_yuan("0") == "0.00"


# ---------- §10.6-5 限流第 31 次 429 ----------
async def test_rate_limit_31st_429(monkeypatch):
    main_mod._rate_local.clear()

    async def fake_run_chat(session_id, message, user_id, auth_header):
        return {"reply": "ok", "cards": []}

    monkeypatch.setattr(main_mod, "run_chat", fake_run_chat)
    codes = []
    async with _client() as c:
        for _ in range(31):
            resp = await c.post(
                "/agent/chat",
                json={"session_id": "rl", "message": "你好"},
                headers={"user-info": "t8-ratelimit-user"},
            )
            codes.append(resp.json()["code"])
    assert codes[:30] == [200] * 30
    assert codes[30] == 429


# ---------- §10.6-6 空消息 400 + 未知 intent fallback ----------
async def test_empty_message_400_and_fallback_guide():
    async with _client() as c:
        r1 = await c.post("/agent/chat", json={"session_id": "x", "message": "   "})
        assert r1.json()["code"] == 400
        r2 = await c.post(
            "/agent/chat", json={"session_id": "x2", "message": "今天天气怎么样"}
        )
        body = r2.json()
        assert body["code"] == 200
        assert "可以问我：找商品 / 查订单 / 我的优惠券 / 售后进度" in body["data"]["reply"]
        assert body["data"]["cards"] == []


# ---------- §10.6-7 search_goods 命中 product 卡片 ≤5 ----------
async def test_search_goods_product_card_max5(fake_gateway):
    records = [
        {"id": str(i), "name": f"商品{i}", "brief": "简介", "minPrice": "3900", "picUrl": "u"}
        for i in range(8)
    ]
    fake_gateway.body = {"code": 200, "data": {"list": records}}
    result = await mall_tools.search_goods("T恤", "Bearer x", "1")
    assert result["ok"] is True
    card = result["cards"][0]
    assert card["type"] == "product"
    assert len(card["items"]) <= 5
    assert set(card["items"][0]) == {"id", "name", "brief", "price", "img"}
    assert card["items"][0]["price"] == "3900"  # 整数分字符串


# ---------- §10.6-8 会话不存在自动创建 ----------
async def test_session_auto_create():
    session_id = "t8-never-created-session"
    get_store()._memory.pop(session_id, None)
    async with _client() as c:
        resp = await c.post(
            "/agent/chat", json={"session_id": session_id, "message": "你好"}
        )
    body = resp.json()
    assert body["code"] == 200  # 不 400、不 500
    assert body["data"]["reply"]
    assert session_id in get_store()._memory  # 确实自动创建了会话


# ---------- 补充：health 增强、全局异常、Authorization 头、aftersale 卡片 ----------
async def test_health_llm_configured_and_gateway_reachable(monkeypatch):
    async def fake_reach():
        return False

    monkeypatch.setattr(main_mod, "_gateway_reachable", fake_reach)
    async with _client() as c:
        resp = await c.get("/agent/health")
    body = resp.json()
    assert body["status"] == "ok"
    assert body["llm_configured"] is True  # mock 模式无需 Key
    assert isinstance(body["gateway_reachable"], bool)
    assert body["gateway_reachable"] is False


async def test_global_exception_returns_500(monkeypatch):
    async def boom(session_id, message, user_id, auth_header):
        raise RuntimeError("STACK_TRACE_SECRET_SHOULD_NOT_LEAK")

    monkeypatch.setattr(main_mod, "run_chat", boom)
    # 关掉 ASGITransport 的重抛，才能拿到 ServerErrorMiddleware 已发出的 500 响应
    async with _client(raise_app_exceptions=False) as c:
        resp = await c.post(
            "/agent/chat", json={"session_id": "e", "message": "你好"}
        )
    body = resp.json()
    assert body["code"] == 500
    assert body["msg"] == "助手暂时不可用，请稍后再试"
    assert "STACK_TRACE_SECRET_SHOULD_NOT_LEAK" not in str(body)


async def test_authorization_forwarded_to_java(fake_gateway):
    await mall_tools.search_goods("耳机", "Bearer SECRET_TOKEN_VALUE", "1")
    assert fake_gateway.calls[-1]["headers"]["Authorization"] == "Bearer SECRET_TOKEN_VALUE"


async def test_aftersale_card_contract(fake_gateway):
    records = [
        {
            "aftersaleId": "7001",
            "orderSn": "20261107001",
            "goodsName": "纯棉短袖T恤",
            "statusText": "待商家处理",
            "amount": "3900",
        }
    ]
    fake_gateway.body = {"code": 200, "data": {"list": records}}
    result = await mall_tools.list_aftersale("1", "Bearer x")
    card = result["cards"][0]
    assert card["type"] == "aftersale"
    assert set(card["items"][0]) == {
        "aftersaleId",
        "orderSn",
        "goodsName",
        "statusText",
        "amount",
    }
    assert card["items"][0]["amount"] == "3900"
    assert len(card["items"]) <= 5


async def test_user_id_reaches_tool_context(monkeypatch):
    # §10.6-1：user-info 解析出的 user_id 必须真的传到工具上下文，而不是停在 main 层
    import app.graph as graph_mod

    seen = {}

    async def fake_search(message, auth_header="", user_id=""):
        seen["user_id"] = user_id
        return {"ok": True, "cards": [], "empty": True, "text": "ok"}

    monkeypatch.setattr(graph_mod, "search_goods", fake_search)
    await graph_mod.graph.ainvoke(
        {"message": "推荐耳机", "user_id": "1", "auth_header": "Bearer x", "history": []},
        config={"configurable": {"thread_id": "t8-tool-context"}},
    )
    assert seen["user_id"] == "1"
