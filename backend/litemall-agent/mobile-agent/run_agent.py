"""
run_agent.py — Agent 服务启动入口
==================================
等价于命令行：uvicorn app.main:app --host 127.0.0.1 --port 8100 --workers 1
直接 `from app.main import app` 而非字符串 "app.main:app"，
保证 PyInstaller 打包时能纳入整个 app 包（沿用 ESDID/agent 约定）。
"""
import uvicorn

from app.config import settings
from app.main import app  # noqa: F401  # 强制打包纳入 app 包

if __name__ == "__main__":
    uvicorn.run(
        app,
        host=settings.host,
        port=settings.port,
        workers=1,  # 内存记忆/InMemorySaver 是进程内状态，多 worker 会不一致
        log_level="info",
    )
