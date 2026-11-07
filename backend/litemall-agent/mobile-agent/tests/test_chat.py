"""
tests/test_chat.py — HTTP 接口契约测试（mock 模式，离线）
========================================================
用 httpx.ASGITransport 直连 FastAPI 应用，不启真实端口。
断言统一信封 { code, msg, data } 与卡片结构（前端契约）。
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app

# 异步测试：anyio 已随 fastapi/httpx 安装，无需额外插件
pytestmark = pytest.mark.anyio


@pytest.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c


async def _create_session(client) -> str:
    resp = await client.post("/agent/session")
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 200
    assert body["data"]["session_id"]
    return body["data"]["session_id"]


async def test_health(client):
    resp = await client.get("/agent/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["llm_provider"] == "mock"
    assert body["tool_fallback"] == "mock"


async def test_create_and_clear_session(client):
    session_id = await _create_session(client)
    resp = await client.post("/agent/session/clear", json={"session_id": session_id})
    body = resp.json()
    assert body["code"] == 200
    assert body["data"] == {"ok": True}


async def test_chat_recommend_returns_product_card(client):
    session_id = await _create_session(client)
    resp = await client.post(
        "/agent/chat",
        json={"session_id": session_id, "message": "推荐 300 元以内的耳机"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 200
    data = body["data"]
    assert data["reply"]
    assert data["cards"][0]["type"] == "product"
    item = data["cards"][0]["items"][0]
    for field in ("id", "name", "brief", "price", "img"):
        assert field in item


async def test_chat_order_returns_order_card(client):
    session_id = await _create_session(client)
    resp = await client.post(
        "/agent/chat", json={"session_id": session_id, "message": "查一下我的最新订单"}
    )
    body = resp.json()
    assert body["code"] == 200
    card = body["data"]["cards"][0]
    assert card["type"] == "order"
    for field in ("orderSn", "statusText", "goodsName", "total"):
        assert field in card["order"]


async def test_chat_coupon_returns_coupon_card(client):
    session_id = await _create_session(client)
    resp = await client.post(
        "/agent/chat", json={"session_id": session_id, "message": "有什么优惠券可以领"}
    )
    body = resp.json()
    assert body["code"] == 200
    card = body["data"]["cards"][0]
    assert card["type"] == "coupon"
    assert card["items"][0]["name"]


async def test_chat_greeting_and_fallback_no_cards(client):
    session_id = await _create_session(client)
    for message in ("你好", "随便说点什么"):
        resp = await client.post("/agent/chat", json={"session_id": session_id, "message": message})
        body = resp.json()
        assert body["code"] == 200
        assert body["data"]["reply"]
        assert not body["data"]["cards"]


async def test_chat_empty_message_rejected(client):
    resp = await client.post("/agent/chat", json={"session_id": "x", "message": "   "})
    body = resp.json()
    assert body["code"] == 400
