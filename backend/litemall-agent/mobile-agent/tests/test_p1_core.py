"""
tests/test_p1_core.py — P1 后端核心用例（① ② ③ ⑤）
====================================================
全部离线：根 conftest.py 固定 mock 模式，DeepSeek / agentic 分支用 monkeypatch 的
假配置与假 LLM，不发任何网络请求，也不改动 mall_tools.py。
"""

import pytest
from langchain_core.messages import AIMessage
from langgraph.prebuilt import ToolNode

from app import graph as graph_mod
from app import llm as llm_mod
from app.config import settings
from app.llm import ArkProvider, DeepSeekProvider, MockProvider, get_chat_model, get_llm
from app.memory.persistent_checkpoint import LocalFileSaver, checkpoint_mode
from app.tools import langchain_tools as lt
from app.tools.langchain_tools import TOOLS

pytestmark = pytest.mark.anyio

_ALL_TOOLS = {
    "search_goods",
    "get_latest_order",
    "get_order_detail",
    "list_aftersale",
    "get_my_coupons",
    "get_usable_coupons",
    "get_coupons",
}


# ---------- ⑤ DeepSeek 配置 + thinking 坑 ----------
def test_deepseek_defaults(monkeypatch):
    from pathlib import Path

    from app import config as config_mod

    # 验证的是 config.py 里写死的官方默认值，不能直接断言单例 settings：真实链路本就该
    # 从 .env / 环境变量读到密钥，本机一旦配了有效 DEEPSEEK_API_KEY 就会误报失败。
    # 清掉外部覆盖后重新构造，才能确定性地测到 dataclass 的 default_factory。
    for key in ("DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"):
        monkeypatch.delenv(key, raising=False)
    fresh = config_mod.Settings()
    assert fresh.deepseek_model == "deepseek-flash"
    assert fresh.deepseek_base_url == "https://api.deepseek.com"
    assert fresh.deepseek_api_key == ""

    # 「不写任何明文密钥」还要约束交付的 .env.example 本身，而不是运行时。
    env_example = Path(__file__).resolve().parents[1] / ".env.example"
    key_lines = [
        ln
        for ln in env_example.read_text(encoding="utf-8").splitlines()
        if ln.strip().startswith("DEEPSEEK_API_KEY")
    ]
    assert len(key_lines) == 1
    assert key_lines[0].split("=", 1)[1].strip() == ""


def test_deepseek_provider_model_and_thinking_disabled(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-fake-not-a-real-key")
    p = get_llm()
    assert isinstance(p, DeepSeekProvider)
    assert p._model == "deepseek-flash"
    assert p._extra_body == {"thinking": {"type": "disabled"}}


def test_deepseek_thinking_disabled_on_every_path(monkeypatch):
    """bind_tools / 流式 / 结构化输出三条链路都必须带 thinking disabled。"""
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-fake-not-a-real-key")
    disabled = {"thinking": {"type": "disabled"}}

    base = get_chat_model()                       # route_intent / compose_answer
    stream = get_chat_model(streaming=True)       # SSE 流式
    bound = get_chat_model(tools=TOOLS)           # agent 节点 bind_tools
    assert base.extra_body == disabled and base.model_name == "deepseek-flash"
    assert stream.extra_body == disabled and stream.streaming is True
    assert bound.bound.extra_body == disabled


def test_ark_and_mock_behaviour_unchanged(monkeypatch):
    assert isinstance(get_llm(), MockProvider)
    monkeypatch.setattr(settings, "llm_provider", "ark")
    monkeypatch.setattr(settings, "ark_api_key", "fake")
    monkeypatch.setattr(settings, "ark_model", "doubao-test")
    p = get_llm()
    assert isinstance(p, ArkProvider)
    assert p._extra_body is None  # ark 不需要 thinking disabled


def test_use_agentic_graph_switch(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-test")
    assert settings.use_agentic_graph is True
    # 配了 deepseek 但没给 Key：get_llm() 会降级 mock，图也必须跟着退回规则图，
    # 否则 agentic 图拿到 None 模型，每句话都答兜底文案（2026-11-07 实测过）。
    monkeypatch.setattr(settings, "deepseek_api_key", "")
    assert settings.use_agentic_graph is False
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-test")
    monkeypatch.setattr(settings, "llm_provider", "mock")
    assert settings.use_agentic_graph is False
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "tool_fallback", "mock")
    assert settings.use_agentic_graph is False


def test_mock_legacy_graph_preserved():
    """28 例基线依赖的原硬编码图必须原样保留。"""
    nodes = graph_mod.graph.get_graph().nodes
    assert "route_intent" in nodes and "answer" in nodes and "login_required" in nodes
    assert "agent" not in nodes  # mock 模式不编译模型自主分支


# ---------- ① bind_tools 工具选择 + 身份不泄漏 ----------
def test_bind_tools_seven_tools(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-fake-not-a-real-key")
    bound = get_chat_model(tools=TOOLS)
    names = [t["function"]["name"] for t in bound.kwargs["tools"]]
    assert set(names) == _ALL_TOOLS
    assert bound.kwargs["tools"][0]["function"]["name"] == "search_goods"


def test_tools_hide_identity_args():
    """user_id / auth_header / state 都不能出现在对模型可见的参数 schema 里。"""
    for t in TOOLS:
        props = t.args_schema.model_json_schema()["properties"]
        assert "user_id" not in props and "auth_header" not in props and "state" not in props
    node = ToolNode(TOOLS)
    assert set(node._injected_args) == _ALL_TOOLS  # 身份由 InjectedState 注入


class _FakeLLM:
    """按轮次返回：第一轮带 tool_calls，之后给最终答复。"""

    def __init__(self, tool_name="search_goods", final="为你找到几款合适的商品"):
        self.n = 0
        self.tool_name = tool_name
        self.final = final

    async def ainvoke(self, messages, **kwargs):
        self.n += 1
        if self.n == 1:
            args = {"keyword": "耳机"} if self.tool_name == "search_goods" else {"status": None}
            return AIMessage(
                content="",
                tool_calls=[{"name": self.tool_name, "args": args, "id": "c1", "type": "tool_call"}],
            )
        return AIMessage(content=self.final)


@pytest.fixture
def agentic_env(monkeypatch):
    """把模型与工具执行都替换成离线假实现，只验证编排、卡片与固定文案。"""
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-fake-not-a-real-key")
    holder = {"mode": "ok", "llm": None}

    async def fake_dispatch(name, args, uid, auth):
        if holder["mode"] == "login":
            return {"ok": False, "login_required": True, "cards": [], "empty": True, "text": "请先登录"}
        if holder["mode"] == "fail":
            return {"ok": False, "cards": [], "empty": True, "text": "暂时查不到相关数据，请稍后再试～"}
        items = [
            {"id": str(i), "name": f"商品{i}", "brief": "b", "price": 29900 + i, "img": "u"}
            for i in range(8)
        ]
        return {"ok": True, "cards": [{"type": "product", "items": items}], "empty": False, "text": "为你找到商品"}

    monkeypatch.setattr(lt, "_dispatch", fake_dispatch)
    monkeypatch.setattr(graph_mod, "get_chat_model", lambda **kw: holder["llm"])
    return holder


def _input(thread_id):
    return (
        {"message": "推荐耳机", "user_id": "U1", "auth_header": "Bearer x", "history": []},
        {"configurable": {"thread_id": thread_id}},
    )


async def test_agentic_graph_tool_selection_and_cards(agentic_env):
    agentic_env["llm"] = _FakeLLM()
    g = graph_mod.build_graph()
    assert {"prepare", "agent", "tools", "finalize"} <= set(g.get_graph().nodes)

    state, cfg = _input("p1-core-select")
    out = await g.ainvoke(state, cfg)
    assert out["reply"] == "为你找到几款合适的商品"
    assert out["intent"] == "recommend"
    card = out["cards"][0]
    assert card["type"] == "product"
    assert len(card["items"]) == 5  # §10.4 items≤5 兜底
    assert set(card["items"][0]) == {"id", "name", "brief", "price", "img"}
    assert card["items"][0]["price"] == "29900"  # 金额整数分字符串


async def test_agentic_login_required_fixed_reply(agentic_env):
    agentic_env["mode"] = "login"
    agentic_env["llm"] = _FakeLLM(tool_name="get_latest_order")
    g = graph_mod.build_graph()
    out = await g.ainvoke(*_input("p1-core-login"))
    assert "请先登录" in out["reply"]
    assert out["cards"] == []
    assert out["intent"] == "login_required"


async def test_agentic_tool_failure_fixed_reply(agentic_env):
    agentic_env["mode"] = "fail"
    agentic_env["llm"] = _FakeLLM(tool_name="get_latest_order")
    g = graph_mod.build_graph()
    out = await g.ainvoke(*_input("p1-core-fail"))
    assert "暂时查不到" in out["reply"]
    assert out["cards"] == []


def test_normalize_cards_drops_unknown_and_empty():
    cards = graph_mod._normalize_cards(
        [
            {"type": "unknown", "items": [{"x": 1}]},
            {"type": "product", "items": []},
            {"type": "order", "order": {"total": "39.5", "orderSn": "SN1"}},
        ]
    )
    assert len(cards) == 1 and cards[0]["type"] == "order"
    assert cards[0]["order"]["total"] == "0"  # 非法金额归零，绝不产出浮点
    assert graph_mod._normalize_cards([]) == []


# ---------- ③ checkpointer 重启后可读 ----------
async def test_checkpointer_survives_restart(tmp_path):
    from typing import TypedDict

    from langgraph.graph import END, START, StateGraph

    path = str(tmp_path / "ckpt.json")

    class S(TypedDict):
        value: int

    async def inc(s):
        return {"value": s["value"] + 1}

    b = StateGraph(S)
    b.add_node("inc", inc)
    b.add_edge(START, "inc")
    b.add_edge("inc", END)
    cfg = {"configurable": {"thread_id": "t1"}}

    saver1 = LocalFileSaver(path)
    await b.compile(checkpointer=saver1).ainvoke({"value": 0}, cfg)

    # 新实例 == 进程重启，状态必须从文件恢复
    saver2 = LocalFileSaver(path)
    t = saver2.get_tuple(cfg)
    assert t is not None and t.checkpoint["channel_values"]["value"] == 1
    await b.compile(checkpointer=saver2).ainvoke({"value": 10}, cfg)

    saver3 = LocalFileSaver(path)
    assert saver3.get_tuple(cfg).checkpoint["channel_values"]["value"] == 11


def test_checkpoint_mode_is_reported():
    assert checkpoint_mode() in ("file", "sqlite", "memory")
