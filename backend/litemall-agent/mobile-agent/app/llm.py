"""
llm.py — LLM 提供者抽象
========================
  provider=mock     : 本地确定性规则模拟（无 Key 调试，意图与前端 agent-mock.js 对齐）
  provider=ark      : 火山方舟（OpenAI 兼容协议）真实模型
  provider=deepseek : DeepSeek V4.1 Flash（OpenAI 兼容协议，官方调用名 deepseek-flash）

graph.py 只依赖本模块的 `get_llm()` / `get_chat_model()` 与统一接口，换模型不换编排。
工具结果（成功/失败/未登录）由负载里的 ok / login_required / text 决定措辞，
模型只负责文字，不编造商品、订单、优惠券（BR-AI-04）。

关于 DeepSeek 的 thinking 坑（务必保留）：DeepSeek 默认开启 thinking；thinking 模式下
`tool_choice:"required"` 会返回 HTTP 400、且 temperature 无效。因此所有走工具调用 /
结构化输出 / 流式的链路，都必须在 ChatOpenAI 上显式传
`extra_body={"thinking":{"type":"disabled"}}`。统一收敛在 OpenAICompatProvider 构造里，
避免某条链路漏传。
"""

from abc import ABC, abstractmethod
import logging
from typing import Literal

from app.config import settings

logger = logging.getLogger(__name__)

# 降级状态：配置了真实 provider 但密钥缺失时不 500，而是回落 mock（只记一次 WARN）。
_degraded_reason: str | None = None

# 意图枚举：新增 login_required（工具报未登录时的分支，§10.3）
Intent = Literal[
    "greeting", "recommend", "order", "coupon", "aftersale", "fallback", "login_required"
]


class LLMProvider(ABC):
    """Agent 大脑的抽象接口。"""

    @abstractmethod
    async def route_intent(self, message: str) -> Intent:
        """判断用户消息属于哪个意图。"""

    @abstractmethod
    async def compose_answer(self, message: str, intent: Intent, tool_payload: dict | None) -> str:
        """根据意图与工具结果生成回复文本；卡片由 graph 从 tool_payload 确定性拼接。"""


# ============================================================
# MockProvider：确定性规则（离线、零成本、可测试）
# ============================================================
class MockProvider(LLMProvider):
    GREET_WORDS = ("你好", "您好", "hi", "hello", "在吗")
    RECOMMEND_WORDS = ("推荐", "找一款", "帮我找", "预算", "以内的", "性价比", "买")
    ORDER_WORDS = ("订单", "包裹", "物流", "发货", "快递", "到哪了")
    COUPON_WORDS = ("优惠", "券", "折扣", "满减", "领")
    AFTERSALE_WORDS = ("售后", "退货", "退款", "换货", "维修")
    CATEGORY_WORDS = ("卖什么", "有什么", "分类", "品类", "能买")

    async def route_intent(self, message: str) -> Intent:
        text = message.strip().lower()
        if any(w in text for w in self.GREET_WORDS):
            return "greeting"
        if any(w in text for w in self.RECOMMEND_WORDS):
            return "recommend"
        if any(w in text for w in self.ORDER_WORDS):
            return "order"
        if any(w in text for w in self.COUPON_WORDS):
            return "coupon"
        if any(w in text for w in self.AFTERSALE_WORDS):
            return "aftersale"
        if any(w in text for w in self.CATEGORY_WORDS):
            return "recommend"
        return "fallback"

    async def compose_answer(self, message: str, intent: Intent, tool_payload: dict | None) -> str:
        payload = tool_payload or {}
        # 工具报未登录 / 工具失败：按负载固定措辞，避免拿空数据编造答案
        if payload.get("login_required"):
            return "请先登录，登录后我就能帮你查订单、我的优惠券和售后进度啦～"
        if payload.get("ok") is False:
            return "暂时查不到相关数据，请稍后再试～"
        if intent == "greeting":
            return (
                "你好呀，我是你的购物助手，可以帮你：\n"
                "① 按预算推荐商品\n② 查询订单 / 物流\n③ 找优惠券\n④ 解答售后问题\n"
                "直接输入，或点下方的问题试试～"
            )
        if intent in ("recommend", "order", "coupon", "aftersale"):
            # 工具负载自带措辞（含空结果、售后流程/暂无售后说明），优先采用
            if payload.get("text"):
                return payload["text"]
            return {
                "recommend": "为你找到这几款商品，看看合不合适：",
                "order": "查到你最近有一笔订单，当前状态如下：",
                "coupon": "这些优惠券可以使用：",
                "aftersale": "售后问题请描述具体订单，我可以帮你指引流程。",
            }[intent]
        # fallback：兜底话术必须含能力引导（§10.3）
        return (
            "我还没完全理解你的意思，可以问我：找商品 / 查订单 / 我的优惠券 / 售后进度。\n"
            "例如「推荐 300 元以内的耳机」「我的订单到哪了」「我的优惠券」「我的售后进度」。"
        )


# ============================================================
# OpenAICompatProvider：火山方舟 / DeepSeek 共用（OpenAI 兼容协议）
# ============================================================
class OpenAICompatProvider(LLMProvider):
    """把两家 OpenAI 兼容端点的差异收敛到构造参数：端点、模型名、额外请求体。

    为什么统一在这里构造 ChatOpenAI：DeepSeek 的 thinking disabled 是"每条链路都不能漏"
    的硬约束，集中在一处比在 bind_tools / with_structured_output / astream 各处补更安全。
    """

    def __init__(self, *, model: str, base_url: str, api_key: str, extra_body: dict | None = None) -> None:
        if not api_key or not model:
            raise RuntimeError("真实模型缺少 api_key / model，请检查 .env 配置")
        self._model = model
        self._base_url = base_url
        self._api_key = api_key
        self._extra_body = extra_body
        # route_intent / compose_answer 用的基础实例；构造不发网络请求
        self._llm = self.build_chat_model()

    def build_chat_model(self, *, tools=None, streaming: bool = False):
        """按需要重建模型实例：streaming / bind_tools 是构造期参数，不能复用同一实例。
        extra_body 一律带上（DeepSeek 关闭 thinking，ark 为 None 无副作用）。
        为什么用原生 extra_body 而非 model_kwargs：后者会触发 langchain 的告警且层级更深。
        """
        from langchain_openai import ChatOpenAI

        llm = ChatOpenAI(
            model=self._model,
            base_url=self._base_url,
            api_key=self._api_key,
            temperature=settings.llm_temperature,
            timeout=60,
            streaming=streaming,
            extra_body=self._extra_body,
        )
        return llm.bind_tools(tools) if tools else llm

    async def route_intent(self, message: str) -> Intent:
        from pydantic import BaseModel, Field

        class _IntentModel(BaseModel):
            intent: Literal[
                "greeting", "recommend", "order", "coupon", "aftersale", "fallback", "login_required"
            ] = Field(description="用户意图分类")

        # with_structured_output 底层是 tool calling；DeepSeek 已关 thinking，不会再 400
        structured = self._llm.with_structured_output(_IntentModel)
        result = await structured.ainvoke(
            f"把用户消息分类为意图（greeting/recommend/order/coupon/aftersale/login_required/fallback）：{message}"
        )
        return result.intent

    async def compose_answer(self, message: str, intent: Intent, tool_payload: dict | None) -> str:
        from langchain_core.prompts import ChatPromptTemplate

        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "你是 litemall 商城的购物助手。根据意图和工具结果，用简洁友好的中文回答用户。"
                    "不要编造工具结果里不存在的商品、订单或优惠券；卡片数据由系统拼接，你只负责文字。",
                ),
                ("human", "意图：{intent}\n工具结果：{payload}\n用户问题：{message}"),
            ]
        )
        chain = prompt | self._llm
        resp = await chain.ainvoke(
            {"intent": intent, "payload": tool_payload or {}, "message": message}
        )
        return resp.content


# ============================================================
# ArkProvider：火山方舟真实模型（保留原有行为不变）
# ============================================================
class ArkProvider(OpenAICompatProvider):
    def __init__(self) -> None:
        if not settings.ark_api_key or not settings.ark_model:
            raise RuntimeError("LLM_PROVIDER=ark 但 ARK_API_KEY / ARK_MODEL 未配置，请检查 .env")
        super().__init__(
            model=settings.ark_model,
            base_url=settings.ark_base_url,
            api_key=settings.ark_api_key,
        )


# ============================================================
# DeepSeekProvider：DeepSeek V4.1 Flash（P1）
# ============================================================
class DeepSeekProvider(OpenAICompatProvider):
    # DeepSeek 默认开 thinking：thinking 模式下 tool_choice:"required" 返回 400、
    # temperature 无效。禁用后工具调用 / 流式 / JSON output 才都可用。
    THINKING_DISABLED = {"thinking": {"type": "disabled"}}

    def __init__(self) -> None:
        if not settings.deepseek_api_key:
            raise RuntimeError("LLM_PROVIDER=deepseek 但 DEEPSEEK_API_KEY 未配置，请检查 .env")
        super().__init__(
            model=settings.deepseek_model,
            base_url=settings.deepseek_base_url,
            api_key=settings.deepseek_api_key,
            extra_body=self.THINKING_DISABLED,
        )


def get_llm() -> LLMProvider:
    """按配置返回 provider 实例。

    配置了 ark / deepseek 但密钥缺失时**不抛 RuntimeError**（否则 /agent/chat 直接 500，
    演示环境忘配 Key 就整页不可用），改为回落 mock 规则并只记一次 WARN；
    health 的 llm_configured 仍是 false、llm_effective_provider 会如实报 mock。
    """
    global _degraded_reason
    if _degraded_reason is not None:
        return MockProvider()
    try:
        if settings.llm_provider == "ark":
            return ArkProvider()
        if settings.llm_provider == "deepseek":
            return DeepSeekProvider()
        return MockProvider()
    except RuntimeError as exc:  # noqa: PERF203 —— 只有构造期配置缺失会走到这里
        _degraded_reason = str(exc)
        logger.warning("LLM 不可用，已降级为 mock 规则：%s", exc)
        return MockProvider()


def effective_provider() -> str:
    """实际生效的 provider（降级后为 mock），供 health 如实上报。"""
    return "mock" if _degraded_reason is not None else settings.llm_provider


def degraded_reason() -> str | None:
    """降级原因；未降级为 None。"""
    return _degraded_reason


def get_chat_model(*, tools=None, streaming: bool = False):
    """返回真实模型的 ChatOpenAI（已带 DeepSeek thinking disabled），供 graph 的 agent
    节点 bind_tools 与 SSE 流式 astream 使用；mock 模式返回 None，由调用方走确定性规则，
    绝不碰网络。工具列表由 app.tools.langchain_tools 提供。"""
    provider = get_llm()
    if not isinstance(provider, OpenAICompatProvider):
        return None
    return provider.build_chat_model(tools=tools, streaming=streaming)
