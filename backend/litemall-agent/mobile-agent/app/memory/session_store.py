"""
session_store.py — 会话记忆
============================
默认内存实现（进程内，重启丢失）；配置 REDIS_URL 后可切换到 Redis 实现
（沿用 ESDID/agent 的思路：记忆层可替换，接口不变）。

接口：create_session / get_history / append_turn / clear / mode
"""

import time
import uuid
from typing import Any

from app.config import settings

try:
    import redis as redis_lib
except ImportError:  # 未安装 redis 时退回内存
    redis_lib = None


class SessionStore:
    """会话存储抽象：内存 或 Redis。"""

    def __init__(self) -> None:
        self._memory: dict[str, list[dict]] = {}
        self._redis = None
        if settings.redis_url and redis_lib is not None:
            try:
                self._redis = redis_lib.from_url(settings.redis_url, decode_responses=True)
                self._redis.ping()
            except Exception as e:  # noqa: BLE001 —— Redis 不可达自动降级内存
                print(f"[memory] Redis 不可达，降级内存实现：{e}")
                self._redis = None

    def mode(self) -> str:
        return "redis" if self._redis else "memory"

    async def create_session(self) -> str:
        session_id = uuid.uuid4().hex
        if self._redis:
            self._redis.set(f"agent:session:{session_id}", "1", ex=settings.session_ttl)
        else:
            self._memory[session_id] = []
        return session_id

    async def ensure_session(self, session_id: str) -> None:
        """会话不存在自动创建（§10.5）：chat 不该因为未知 session_id 报 400/500。
        nx / setdefault 保证不覆盖已有会话。"""
        if self._redis:
            self._redis.set(f"agent:session:{session_id}", "1", ex=settings.session_ttl, nx=True)
        else:
            self._memory.setdefault(session_id, [])

    async def get_history(self, session_id: str) -> list[dict[str, Any]]:
        if self._redis:
            raw = self._redis.get(f"agent:history:{session_id}")
            import json

            return json.loads(raw) if raw else []
        return list(self._memory.get(session_id, []))

    async def append_turn(self, session_id: str, user_msg: str, reply: str) -> None:
        turn = {"user": user_msg, "assistant": reply, "ts": int(time.time())}
        if self._redis:
            import json

            key = f"agent:history:{session_id}"
            history = self.get_history_payload(session_id)
            history.append(turn)
            self._redis.set(key, json.dumps(history, ensure_ascii=False), ex=settings.session_ttl)
        else:
            self._memory.setdefault(session_id, []).append(turn)

    def get_history_payload(self, session_id: str) -> list[dict[str, Any]]:
        """同步取历史（Redis 分支内部用）。"""
        if self._redis:
            import json

            raw = self._redis.get(f"agent:history:{session_id}")
            return json.loads(raw) if raw else []
        return list(self._memory.get(session_id, []))

    async def clear(self, session_id: str) -> None:
        if self._redis:
            self._redis.delete(f"agent:history:{session_id}", f"agent:session:{session_id}")
        else:
            self._memory.pop(session_id, None)


_store: SessionStore | None = None


def get_store() -> SessionStore:
    global _store
    if _store is None:
        _store = SessionStore()
    return _store
