"""
config.py — 全局配置中心（沿用 ESDID/agent 约定）
====================================================
所有外部依赖（LLM、商城网关、Redis、checkpointer）的连接信息集中在这里，从 .env 读取。
换环境只改 .env，不改代码；代码里不出现硬编码的 Key / 密码。
"""

import os
import sys
from dataclasses import dataclass, field

from dotenv import load_dotenv

# 配置文件"家"：开发模式=项目根目录；打包后=可执行文件所在目录
if getattr(sys, "frozen", False):
    BASE_DIR = os.path.dirname(sys.executable)
else:
    BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

load_dotenv(os.path.join(BASE_DIR, ".env"))


def _env(key: str, default: str = "") -> str:
    """读取环境变量，统一入口。"""
    return os.getenv(key, default)


@dataclass
class Settings:
    # ================= LLM（Agent 的大脑） =================
    # provider: mock=本地确定性规则 | ark=火山方舟(OpenAI 兼容) | deepseek=DeepSeek(OpenAI 兼容)
    llm_provider: str = field(default_factory=lambda: _env("LLM_PROVIDER", "mock"))
    ark_api_key: str = field(default_factory=lambda: _env("ARK_API_KEY", ""))
    ark_base_url: str = field(
        default_factory=lambda: _env("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3")
    )
    ark_model: str = field(default_factory=lambda: _env("ARK_MODEL", ""))
    llm_temperature: float = field(default_factory=lambda: float(_env("LLM_TEMPERATURE", "0.2")))

    # ---- DeepSeek V4.1 Flash（P1）----
    # 官方 API 调用名是 deepseek-flash（对应 DeepSeek-V4.1-Flash-1017），不是旧名
    # deepseek-v4-flash（旧名虽兼容但会路由到新版，一律不用）。
    deepseek_api_key: str = field(default_factory=lambda: _env("DEEPSEEK_API_KEY", ""))
    deepseek_base_url: str = field(
        default_factory=lambda: _env("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    )
    deepseek_model: str = field(default_factory=lambda: _env("DEEPSEEK_MODEL", "deepseek-flash"))

    # ================= 商城 Java 网关（工具执行层） =================
    # Windows 上 localhost 会先解析到 ::1，而网关只监听 IPv4 → 502；一律用 127.0.0.1
    mall_base_url: str = field(default_factory=lambda: _env("MALL_BASE_URL", "http://127.0.0.1:8000"))
    # tool_fallback: mock=工具返回演示数据（独立开发） | remote=调用 Java 网关真实接口
    tool_fallback: str = field(default_factory=lambda: _env("TOOL_FALLBACK", "mock"))

    # ================= 会话记忆（可选 Redis，缺省内存实现） =================
    redis_url: str = field(default_factory=lambda: _env("REDIS_URL", ""))

    # ================= checkpointer 持久化（P1 ③） =================
    # 可选 sqlite|redis|memory；默认 auto=按可用实现自动选（sqlite > 本地文件 > memory）。
    # 为什么不写死 sqlite：本机可能装不上 langgraph-checkpoint-sqlite，装不上必须自动
    # 降级且绝不启动失败；P1 明确不引入 PostgreSQL / Milvus。
    checkpoint_backend: str = field(default_factory=lambda: _env("CHECKPOINT_BACKEND", "auto"))
    # 持久化文件目录（sqlite / 本地文件 saver 共用），缺省放项目根目录
    checkpoint_dir: str = field(default_factory=lambda: _env("CHECKPOINT_DIR", ""))

    # ================= 服务参数 =================
    host: str = field(default_factory=lambda: _env("AGENT_HOST", "127.0.0.1"))
    port: int = field(default_factory=lambda: int(_env("AGENT_PORT", "8100")))
    session_ttl: int = field(default_factory=lambda: int(_env("SESSION_TTL", "86400")))
    rate_limit_per_min: int = field(default_factory=lambda: int(_env("RATE_LIMIT", "30")))

    @property
    def llm_key_present(self) -> bool:
        """声明的 provider 是否真有可用密钥。缺 Key 时 llm.get_llm() 会降级成 mock，
        编排层必须跟着降级，否则会进 agentic 图、拿不到模型、每条消息都回兜底话术。"""
        if self.llm_provider == "deepseek":
            return bool(self.deepseek_api_key)
        if self.llm_provider == "ark":
            return bool(self.ark_api_key and self.ark_model)
        return False

    @property
    def use_agentic_graph(self) -> bool:
        """仅"真实模型 + 真实工具链路"才让模型自主选工具（P1 ①）。
        为什么这样判定：mock 硬编码图是离线契约基线（现有测试依赖它），必须原样保留；
        工具自主调用也只有在远程 Java 真接口可用时才有意义。
        注意这里必须看 llm_key_present 而不是 provider 名字：配了 deepseek 却没配 Key 时
        get_llm() 已降级 mock，此处若仍返回 True，agentic 图会拿到 None 模型，
        所有问题都答"我还没完全理解你的意思"。"""
        return self.llm_key_present and self.tool_fallback == "remote"


settings = Settings()
