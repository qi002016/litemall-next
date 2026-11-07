"""
tests/test_graph.py — LangGraph 状态图意图路由测试（mock 模式）
===============================================================
不经过 HTTP，直接 ainvoke 编译好的图，验证：意图 → 工具节点 → answer → 卡片。
"""

import pytest

from app.graph import graph

# 异步测试：anyio 已随依赖安装，无需额外插件
pytestmark = pytest.mark.anyio


@pytest.mark.parametrize(
    ("message", "expect_cards"),
    [
        ("推荐 300 元以内的耳机", "product"),
        ("查一下我的最新订单", "order"),
        ("有什么优惠券可以领", "coupon"),
        ("怎么申请售后", None),
        ("你好", None),
    ],
)
async def test_graph_intent_routing(message, expect_cards):
    result = await graph.ainvoke(
        {"message": message, "user_id": "", "auth_header": "", "history": []},
        config={"configurable": {"thread_id": "test-session"}},
    )
    assert result["reply"]
    assert result["intent"] in ("greeting", "recommend", "order", "coupon", "aftersale", "fallback")
    if expect_cards is None:
        assert not result["cards"]
    else:
        assert result["cards"] and result["cards"][0]["type"] == expect_cards


async def test_graph_fallback_intent():
    result = await graph.ainvoke(
        {"message": "今天天气怎么样", "user_id": "", "auth_header": "", "history": []},
        config={"configurable": {"thread_id": "test-session-2"}},
    )
    assert result["intent"] == "fallback"
    assert not result["cards"]
