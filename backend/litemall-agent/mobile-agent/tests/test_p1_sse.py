"""
tests/test_p1_sse.py — P1 ② SSE 事件契约 + 身份/限流/会话一致性
================================================================
离线：mock 模式按段吐出，不启服务、不发网络（用 httpx.ASGITransport 直连 app）。
"""

import json

import pytest
from httpx import ASGITransport, AsyncClient

import app.main as main_mod
from app.config import settings
from app.main import app
from app.memory.session_store import get_store

pytestmark = pytest.mark.anyio


def _client() -> AsyncClient:
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


def _parse_sse(text: str):
    """把 SSE 响应体解析成 [(event, data)]；data 必须是单行 JSON。"""
    events = []
    for block in text.strip().split("\n\n"):
        if not block.strip():
            continue
        ev, data = None, None
        for line in block.split("\n"):
            if line.startswith("event: "):
                ev = line[len("event: ") :]
            elif line.startswith("data: "):
                data = json.loads(line[len("data: ") :])
        events.append((ev, data))
    return events


async def test_sse_four_events_and_done_reply():
    async with _client() as c:
        sid = (await c.post("/agent/session")).json()["data"]["session_id"]
        resp = await c.post(
            "/agent/chat/stream",
            json={"session_id": sid, "message": "推荐 300 元以内的耳机"},
        )
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/event-stream")

    events = _parse_sse(resp.text)
    names = [e for e, _ in events]
    assert "delta" in names and "cards" in names and "done" in names
    assert names[-2:] == ["cards", "done"]  # 文本结束后发一次 cards，再 done

    deltas = [d["text"] for e, d in events if e == "delta"]
    cards_events = [d["cards"] for e, d in events if e == "cards"]
    done = [d for e, d in events if e == "done"][0]
    assert len(deltas) >= 2  # 切成若干片
    assert "".join(deltas) == done["reply"]  # 逐段拼接 == 完整文本
    assert len(cards_events) == 1  # cards 只发一次
    assert done["cards"] == cards_events[0]
    assert cards_events[0][0]["type"] == "product"


async def test_sse_greeting_has_empty_cards():
    async with _client() as c:
        sid = (await c.post("/agent/session")).json()["data"]["session_id"]
        resp = await c.post(
            "/agent/chat/stream", json={"session_id": sid, "message": "你好"}
        )
    done = [d for e, d in _parse_sse(resp.text) if e == "done"][0]
    assert done["reply"] and done["cards"] == []


async def test_sse_error_event_http200_no_stack(monkeypatch):
    async def boom(*args, **kwargs):
        raise RuntimeError("SECRET_STACK_SHOULD_NOT_LEAK")

    monkeypatch.setattr(main_mod, "run_chat", boom)
    async with _client() as c:
        resp = await c.post(
            "/agent/chat/stream", json={"session_id": "e", "message": "你好"}
        )
    events = _parse_sse(resp.text)
    assert resp.status_code == 200
    assert events == [("error", {"code": 500, "msg": "助手暂时不可用，请稍后再试"})]
    assert "SECRET_STACK_SHOULD_NOT_LEAK" not in resp.text


async def test_sse_empty_message_and_identity(monkeypatch):
    captured = {}

    async def fake_run_chat(session_id, message, user_id, auth_header):
        captured.update(uid=user_id, auth=auth_header)
        return {"reply": "ok", "cards": []}

    monkeypatch.setattr(main_mod, "run_chat", fake_run_chat)
    async with _client() as c:
        empty = await c.post(
            "/agent/chat/stream", json={"session_id": "x", "message": "   "}
        )
        assert empty.json()["code"] == 400
        await c.post(
            "/agent/chat/stream",
            json={"session_id": "x", "message": "你好"},
            headers={"user-info": "1", "X-User-Id": "2", "Authorization": "Bearer abcdef"},
        )
    assert captured["uid"] == "1"  # user-info 优先
    assert captured["auth"] == "Bearer abcdef"  # Authorization 原样透传


async def test_sse_rate_limit_matches_chat(monkeypatch):
    main_mod._rate_local.clear()

    async def fake_run_chat(session_id, message, user_id, auth_header):
        return {"reply": "ok", "cards": []}

    monkeypatch.setattr(main_mod, "run_chat", fake_run_chat)
    monkeypatch.setattr(settings, "rate_limit_per_min", 2)
    codes = []
    async with _client() as c:
        for _ in range(3):
            r = await c.post(
                "/agent/chat/stream",
                json={"session_id": "p1-stream-rl", "message": "你好"},
                headers={"user-info": "p1-stream-rl-user"},
            )
            codes.append(r.json()["code"] if r.headers["content-type"].startswith("application/json") else 200)
    assert codes == [200, 200, 429]


async def test_sse_session_auto_create():
    session_id = "p1-stream-never-created"
    get_store()._memory.pop(session_id, None)
    async with _client() as c:
        resp = await c.post(
            "/agent/chat/stream", json={"session_id": session_id, "message": "你好"}
        )
    assert resp.status_code == 200
    assert session_id in get_store()._memory  # 与非流式一致，不 400/500


async def test_health_has_checkpointer_and_keeps_old_fields():
    async with _client() as c:
        body = (await c.get("/agent/health")).json()
    for key in (
        "status",
        "llm_provider",
        "tool_fallback",
        "memory",
        "llm_configured",
        "gateway_reachable",
    ):
        assert key in body  # 原字段一个都不能少
    assert isinstance(body["checkpointer"], str) and body["checkpointer"]
