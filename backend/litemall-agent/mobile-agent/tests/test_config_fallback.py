"""
配置降级与关键词兜底（2026-11-07 配置修复的回归）
=================================================
① 配了 deepseek 却没给 DEEPSEEK_API_KEY 时，/agent/chat 不能 500，
   而是降级 mock；health 通过 effective_provider()/degraded_reason() 如实上报。
② 没有真实模型抽词时（mock / 降级），「推荐几款 T 恤」不能被整句当关键词去精确匹配。
"""
import pytest

from app import llm
from app.config import settings
from app.tools import mall_tools


@pytest.fixture(autouse=True)
def _reset_degrade_state(monkeypatch):
    """每个用例前清掉模块级降级状态，避免用例间串味。"""
    monkeypatch.setattr(llm, "_degraded_reason", None, raising=False)
    yield
    monkeypatch.setattr(llm, "_degraded_reason", None, raising=False)


def test_deepseek_without_key_degrades_to_mock(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "deepseek_api_key", "")

    provider = llm.get_llm()

    assert isinstance(provider, llm.MockProvider)
    assert llm.effective_provider() == "mock"
    assert llm.degraded_reason()


def test_ark_without_key_degrades_to_mock(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "ark")
    monkeypatch.setattr(settings, "ark_api_key", "")
    monkeypatch.setattr(settings, "ark_model", "")

    assert isinstance(llm.get_llm(), llm.MockProvider)
    assert llm.effective_provider() == "mock"


def test_mock_provider_is_not_degraded(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "mock")

    assert isinstance(llm.get_llm(), llm.MockProvider)
    assert llm.effective_provider() == "mock"
    assert llm.degraded_reason() is None


def test_deepseek_with_key_keeps_real_provider(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "deepseek")
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-test")

    provider = llm.get_llm()

    assert isinstance(provider, llm.DeepSeekProvider)
    assert llm.effective_provider() == "deepseek"
    assert llm.degraded_reason() is None


@pytest.mark.parametrize(
    ("message", "expected"),
    [
        ("推荐几款 T 恤", "T 恤"),
        ("帮我找一下耳机", "耳机"),
        ("T恤", "T恤"),
        ("有没有手机", "手机"),
        ("推荐一些好物", ""),
        ("耳机多少钱", "耳机"),
    ],
)
def test_extract_keyword(message, expected):
    assert mall_tools._extract_keyword(message) == expected


def test_match_category_tolerates_space():
    """「T 恤」中间带空格也要落到服饰，否则演示话术选错品类。"""
    assert mall_tools._match_category("推荐几款 T 恤") == "服饰"
    assert mall_tools._match_category("t恤") == "服饰"


@pytest.mark.anyio
async def test_search_goods_retries_with_extracted_keyword(monkeypatch):
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    calls = []

    async def fake_call(path, params, auth_header, builder):
        calls.append(dict(params))
        if params.get("keyword") == "T 恤":
            return {"ok": True, "cards": [{"type": "product", "items": [{"id": "1"}]}], "empty": False, "text": "ok"}
        return {"ok": True, "cards": [], "empty": True, "text": "暂时没找到匹配的商品。"}

    monkeypatch.setattr(mall_tools, "_call", fake_call)

    result = await mall_tools.search_goods("推荐几款 T 恤")

    assert result["empty"] is False
    assert calls[0]["keyword"] == "推荐几款 T 恤"
    assert calls[1]["keyword"] == "T 恤"


@pytest.mark.anyio
async def test_search_goods_falls_back_to_hot_list(monkeypatch):
    """关键词也查不到时，最后一次不带 keyword 取热门，别让演示停在"没找到"。"""
    monkeypatch.setattr(settings, "tool_fallback", "remote")
    calls = []

    async def fake_call(path, params, auth_header, builder):
        calls.append(dict(params))
        if "keyword" in params:
            return {"ok": True, "cards": [], "empty": True, "text": "暂时没找到匹配的商品。"}
        return {"ok": True, "cards": [{"type": "product", "items": [{"id": "1"}]}], "empty": False, "text": "ok"}

    monkeypatch.setattr(mall_tools, "_call", fake_call)

    result = await mall_tools.search_goods("推荐几款 T 恤")

    assert result["empty"] is False
    assert "keyword" not in calls[-1]