"""
persistent_checkpoint.py — 可持久化 checkpointer（P1 ③）
=========================================================
把 InMemorySaver 换成"重启后会话状态不丢、多实例可共享"的实现，且不引入
PostgreSQL / Milvus（P1 明确不做）。

为什么默认不是官方 SqliteSaver：本机按隔离要求把 langgraph-checkpoint-sqlite
装进了 .p1deps，但它依赖 langgraph-checkpoint 4.3.1 的 DeltaChannelHistory，
而全局装的是 4.0.1；.p1deps 还带进了不同版本的 httpx / anyio。若把 .p1deps
插到 sys.path 首位，会让整个 langgraph 栈版本错配，风险远大于收益（会直接破坏
现有 28 例契约测试）。因此默认走自实现的 LocalFileSaver：继承全局 InMemorySaver，
只在其写操作后把内部状态原子落盘，零第三方依赖、零版本冲突、异步安全。
显式 CHECKPOINT_BACKEND=sqlite 时才尝试官方 saver，失败自动降级并打 WARN。

LocalFileSaver 存的是 JSON 文件：serde.dumps_typed 产出的值是 (str, bytes)，
且 blobs/writes 用 tuple 作键；用 base64 + tuple 标记把这些无损编码进 JSON，
加载时还原成普通容器，再按 InMemorySaver 的约定重建 defaultdict 层级。
文件只由本进程写、本进程读，不接受外部输入。
"""

from __future__ import annotations

import base64
import json
import logging
import os
import sys
import threading
import time
from collections import defaultdict
from typing import Any

from langgraph.checkpoint.memory import InMemorySaver

from app.config import BASE_DIR, settings

logger = logging.getLogger("litemall-agent")

_FILE_NAME = ".p1checkpoints.json"
_SQLITE_FILE_NAME = ".p1checkpoints.sqlite"


# ---------- JSON 无损编解码（bytes / tuple 键 / defaultdict） ----------
def _to_json(o: Any) -> Any:
    if isinstance(o, bytes):
        return {"__p1_bytes__": base64.b64encode(o).decode("ascii")}
    if isinstance(o, tuple):
        return {"__p1_tuple__": [_to_json(x) for x in o]}
    if isinstance(o, dict):  # defaultdict 也是 dict 子类
        # 用 [k, v] 对列表而非对象键，才能保留 tuple 这类非字符串键
        return {"__p1_dict__": [[_to_json(k), _to_json(v)] for k, v in o.items()]}
    if isinstance(o, list):
        return [_to_json(x) for x in o]
    return o  # str / int / float / bool / None


def _from_json(o: Any) -> Any:
    if isinstance(o, dict):
        if "__p1_bytes__" in o:
            return base64.b64decode(o["__p1_bytes__"])
        if "__p1_tuple__" in o:
            return tuple(_from_json(x) for x in o["__p1_tuple__"])
        if "__p1_dict__" in o:
            return {_from_json(k): _from_json(v) for k, v in o["__p1_dict__"]}
        return {k: _from_json(v) for k, v in o.items()}
    if isinstance(o, list):
        return [_from_json(x) for x in o]
    return o


def _rebuild_containers(storage: dict, writes: dict, blobs: dict):
    """按 InMemorySaver 的私有约定重建三层容器；load 后必须调用，否则 put 会因
    缺 defaultdict 自动建键而 KeyError。"""
    s = defaultdict(lambda: defaultdict(dict))
    for thread_id, ns_map in (storage or {}).items():
        inner = defaultdict(dict)
        inner.update(ns_map or {})
        s[thread_id] = inner
    w = defaultdict(dict)
    w.update(writes or {})
    return s, w, dict(blobs or {})


class LocalFileSaver(InMemorySaver):
    """本地 JSON 文件持久化 saver：复用 InMemorySaver 全部 checkpoint 语义，
    仅在 put / put_writes / delete_thread 之后原子落盘，构造时从文件恢复。
    RLock 保护同一进程内并发写；os.replace 保证文件不会写一半损坏。"""

    def __init__(self, path: str) -> None:
        self._path = path
        self._lock = threading.RLock()
        super().__init__()
        self._load()

    def _load(self) -> None:
        if not os.path.exists(self._path):
            return
        try:
            with open(self._path, "r", encoding="utf-8") as f:
                snap = json.load(f)
            self.storage, self.writes, self.blobs = _rebuild_containers(*_from_json(snap))
            logger.info("checkpointer 已从 %s 恢复会话状态", self._path)
        except Exception as e:  # noqa: BLE001 —— 文件损坏不该让服务起不来
            logger.warning("checkpointer 恢复失败（%s: %s），以空状态启动", type(e).__name__, e)

    def _dump(self) -> None:
        # tmp 带 pid/线程号：多进程同时写时不会互相覆盖同一个临时文件
        tmp = f"{self._path}.{os.getpid()}.{threading.get_ident()}.tmp"
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(
                    [_to_json(self.storage), _to_json(self.writes), _to_json(self.blobs)],
                    f,
                    ensure_ascii=False,
                )
            # Windows 上目标文件被另一进程短暂占用时 os.replace 会 WinError 32；
            # 重试几次仍失败就只告警、本次写入留内存，绝不把异常抛进会话（不 500）。
            for attempt in range(3):
                try:
                    os.replace(tmp, self._path)
                    return
                except OSError:
                    if attempt == 2:
                        raise
                    time.sleep(0.01 * (attempt + 1))
        except Exception as e:  # noqa: BLE001
            logger.warning("checkpointer 落盘失败（%s: %s），本次状态仅存内存", type(e).__name__, e)
            try:
                os.remove(tmp)
            except OSError:
                pass

    def put(self, config, checkpoint, metadata, new_versions):
        with self._lock:
            result = super().put(config, checkpoint, metadata, new_versions)
            self._dump()
            return result

    def put_writes(self, config, writes, task_id, task_path=""):
        with self._lock:
            super().put_writes(config, writes, task_id, task_path)
            self._dump()

    def delete_thread(self, thread_id):
        with self._lock:
            super().delete_thread(thread_id)
            self._dump()


# ---------- 隔离依赖目录（仅供显式 sqlite 后端尝试） ----------
def _ensure_p1deps() -> None:
    """把 .p1deps 追加到 sys.path 末尾：全局已装包优先，避免 httpx/anyio 被覆盖。"""
    p1 = os.path.join(BASE_DIR, ".p1deps")
    if os.path.isdir(p1) and p1 not in sys.path:
        sys.path.append(p1)


def _resolve_dir() -> str:
    d = settings.checkpoint_dir or BASE_DIR
    os.makedirs(d, exist_ok=True)
    return d


def _try_sqlite(path: str):
    """显式 CHECKPOINT_BACKEND=sqlite 时尝试官方 SqliteSaver；任何异常都如实降级。"""
    try:
        _ensure_p1deps()
        from langgraph.checkpoint.sqlite import SqliteSaver

        # from_conn_string 是 @contextmanager，返回 Iterator[SqliteSaver]；
        # 进程生命周期内保持连接，不调用 __exit__。
        saver = SqliteSaver.from_conn_string(path).__enter__()
        return saver
    except Exception as e:  # noqa: BLE001 —— 版本冲突/缺依赖都不该阻断启动
        logger.warning(
            "CHECKPOINT_BACKEND=sqlite 不可用（%s: %s），自动降级本地文件 saver",
            type(e).__name__,
            e,
        )
        return None


_checkpointer = None
_checkpointer_mode = "memory"


def get_checkpointer():
    """返回进程内单例 saver。绝不抛异常：任何后端失败都降级到可用的实现。"""
    global _checkpointer, _checkpointer_mode
    if _checkpointer is not None:
        return _checkpointer

    backend = (settings.checkpoint_backend or "auto").strip().lower()

    # pytest 环境用内存 saver：契约测试必须确定性、离线，且不能跨多次 pytest 运行
    # 累积落盘状态（Windows 文件锁 + thread_id 复用会让 28 例基线不稳定）。
    # 持久化本身的重启可读由 test_p1 直接实例化 LocalFileSaver 覆盖。
    if backend == "auto" and "pytest" in sys.modules:
        _checkpointer, _checkpointer_mode = InMemorySaver(), "memory"
        return _checkpointer

    d = _resolve_dir()

    if backend == "memory":
        _checkpointer, _checkpointer_mode = InMemorySaver(), "memory"
    elif backend == "sqlite":
        saver = _try_sqlite(os.path.join(d, _SQLITE_FILE_NAME))
        if saver is not None:
            _checkpointer, _checkpointer_mode = saver, "sqlite"
        else:
            _checkpointer, _checkpointer_mode = (
                LocalFileSaver(os.path.join(d, _FILE_NAME)),
                "file",
            )
    else:
        # auto / redis：redis 仅承载 SessionStore history 与限流，checkpointer 用本地文件
        if backend == "redis":
            if settings.redis_url:
                logger.info(
                    "CHECKPOINT_BACKEND=redis：Redis 已持久化 history/限流，"
                    "checkpointer 走本地文件 saver（多机共享写需 sqlite/共享盘）"
                )
            else:
                logger.warning("CHECKPOINT_BACKEND=redis 但未配 REDIS_URL，checkpointer 降级本地文件 saver")
        _checkpointer, _checkpointer_mode = (
            LocalFileSaver(os.path.join(d, _FILE_NAME)),
            "file",
        )

    logger.info("checkpointer 后端=%s", _checkpointer_mode)
    return _checkpointer


def checkpoint_mode() -> str:
    """供 /agent/health 的 checkpointer 字段上报实际后端。"""
    get_checkpointer()
    return _checkpointer_mode
