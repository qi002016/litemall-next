"""
tests/conftest.py — 固定 anyio 只跑 asyncio 后端
=================================================
anyio 的 pytest 插件默认把每个 pytest.mark.anyio 用例参数化成 asyncio + trio 两套，
但本项目的 requirements.txt 不含 trio、离线环境也没装，导致一半用例在 setup 阶段
就 ModuleNotFoundError，基线无法全绿。这里覆盖 anyio_backend fixture，只跑项目真正
依赖的 asyncio（anyio 官方推荐的定制方式），保证测试离线、确定性、零额外依赖。
"""
import pytest


@pytest.fixture
def anyio_backend():
    return "asyncio"
