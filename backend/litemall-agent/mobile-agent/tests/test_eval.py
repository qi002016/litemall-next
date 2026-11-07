"""
tests/test_eval.py — P1 ④ 小评估集（≥20 条中文问答，离线可跑）
================================================================
用 mock provider + 原硬编码图跑端到端，断言：
  1. 意图路由准确率 ≥ 0.85（覆盖 6 类意图，另单独覆盖 login_required）；
  2. 回复不出现编造的商品名 / 订单号（模型不编造，卡片才带真实数据）；
  3. 卡片字段与金额口径合规（§10.4：四类、金额整数分字符串、items≤5）。
"""

import re

import pytest

from app import graph as graph_mod
from app.config import settings
from app.tools.mall_tools import DEMO_PRODUCTS

pytestmark = pytest.mark.anyio

# (用户消息, 期望意图, 期望卡片 type；None 表示不应出卡片)
EVAL_CASES = [
    ("你好", "greeting", None),
    ("您好，在吗", "greeting", None),
    ("hi", "greeting", None),
    ("hello", "greeting", None),
    ("推荐 300 元以内的耳机", "recommend", "product"),
    ("帮我找一款性价比手机", "recommend", "product"),
    ("有什么商品分类", "recommend", "product"),
    ("你们卖什么", "recommend", "product"),
    ("查一下我的最新订单", "order", "order"),
    ("我的包裹到哪了", "order", "order"),
    ("订单 202609251432 详情", "order", "order"),
    ("查一下物流发货没", "order", "order"),
    ("有什么优惠券可以领", "coupon", "coupon"),
    ("我的优惠券", "coupon", "coupon"),
    ("满减券还有吗", "coupon", "coupon"),
    ("怎么申请售后", "aftersale", None),
    ("我的退款进度", "aftersale", None),
    ("退货换货怎么弄", "aftersale", None),
    ("维修要多久", "aftersale", None),
    ("今天天气怎么样", "fallback", None),
    ("随便说点什么", "fallback", None),
    ("讲个笑话吧", "fallback", None),
]

# 缺 user-info 且 TOOL_FALLBACK=remote 时，工具报未登录 → login_required 固定文案
LOGIN_CASES = ["我的订单到哪了", "查一下我的优惠券", "我的售后进度"]

_ITEM_FIELDS = {
    "product": {"id", "name", "brief", "price", "img"},
    "coupon": {"id", "name", "value", "desc", "expire"},
    "aftersale": {"aftersaleId", "orderSn", "goodsName", "statusText", "amount"},
}
_MONEY_FIELDS = {
    "product": ("price",),
    "coupon": ("value",),
    "aftersale": ("amount",),
}
_ORDER_FIELDS = {"orderSn", "orderId", "status", "statusText", "goodsName", "total", "time"}


def _assert_cards_valid(cards):
    for card in cards:
        t = card["type"]
        assert t in ("product", "order", "coupon", "aftersale")
        if t == "order":
            assert set(card["order"]) == _ORDER_FIELDS
            assert isinstance(card["order"]["total"], str) and card["order"]["total"].isdigit()
        else:
            assert len(card["items"]) <= 5
            for item in card["items"]:
                assert set(item) == _ITEM_FIELDS[t]
                for money in _MONEY_FIELDS[t]:
                    assert isinstance(item[money], str) and item[money].isdigit()


def _assert_no_hallucination(reply):
    # 回复文本里不该凭空出现演示商品名，也不该出现像订单号的长数字串
    for p in DEMO_PRODUCTS:
        assert p["name"] not in reply
    assert not re.search(r"\d{6,}", reply)


async def test_eval_intent_accuracy_and_contract():
    assert len(EVAL_CASES) >= 20
    correct = 0
    for i, (message, expected_intent, expected_card) in enumerate(EVAL_CASES):
        out = await graph_mod.graph.ainvoke(
            {"message": message, "user_id": "", "auth_header": "", "history": []},
            config={"configurable": {"thread_id": f"eval-{i}"}},
        )
        if out["intent"] == expected_intent:
            correct += 1
        assert out["reply"], f"空回复：{message}"
        _assert_no_hallucination(out["reply"])

        cards = out["cards"]
        _assert_cards_valid(cards)
        if expected_card is None:
            assert cards == [], f"不该出卡片：{message}"
        else:
            assert cards and cards[0]["type"] == expected_card, f"卡片类型不符：{message}"

    accuracy = correct / len(EVAL_CASES)
    print(f"\n[eval] 意图路由准确率 = {correct}/{len(EVAL_CASES)} = {accuracy:.2f}")
    assert accuracy >= 0.85


async def test_eval_login_required_uses_fixed_reply(monkeypatch):
    """login_required 样本：remote + 缺 user-info，工具不查库、走固定文案。"""
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    for i, message in enumerate(LOGIN_CASES):
        out = await graph_mod.graph.ainvoke(
            {"message": message, "user_id": "", "auth_header": "", "history": []},
            config={"configurable": {"thread_id": f"eval-login-{i}"}},
        )
        assert out["intent"] == "login_required"
        assert "请先登录" in out["reply"]
        assert out["cards"] == []
