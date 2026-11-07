"""
pytest 根配置（沿用 ESDID/agent 约定）：
  1. 确保项目根目录在 sys.path，测试才能 import app；
  2. 强制测试走 mock 模式 —— 在 import app.config 之前设置环境变量：
     测试必须"确定性、离线、零成本"，绝不因为 .env 里配了 Key 就去调模型。
"""
import os
import sys

# 必须在 import app 之前设置（load_dotenv 不会覆盖已存在的环境变量）
os.environ["LLM_PROVIDER"] = "mock"
os.environ["ARK_API_KEY"] = ""
os.environ["TOOL_FALLBACK"] = "mock"
os.environ["REDIS_URL"] = ""

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
