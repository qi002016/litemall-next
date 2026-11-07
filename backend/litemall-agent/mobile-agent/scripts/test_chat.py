r"""
scripts/test_chat.py — 对运行中的 Agent 服务做冒烟测试（手测脚本）
=================================================================
先启动服务（scripts/start_dev.bat 或 python run_agent.py），再运行本脚本：
  .venv\Scripts\python scripts\test_chat.py
"""

import httpx

BASE = "http://127.0.0.1:8100"


def main() -> None:
    client = httpx.Client(timeout=15)
    r = client.get(f"{BASE}/agent/health")
    print("health:", r.json())

    sid = client.post(f"{BASE}/agent/session").json()["data"]["session_id"]
    print("session:", sid)

    cases = [
        "你好",
        "推荐 300 元以内的耳机",
        "查一下我的最新订单",
        "有什么优惠券可以领",
        "怎么申请售后",
        "随便说点什么",
    ]
    for msg in cases:
        body = client.post(
            f"{BASE}/agent/chat", json={"session_id": sid, "message": msg}
        ).json()
        cards = body["data"].get("cards") or []
        ctype = cards[0]["type"] if cards else "-"
        print(f"\n[user] {msg}\n[assistant] {body['data']['reply'][:60]}…\n[cards] {ctype}")

    r = client.post(f"{BASE}/agent/session/clear", json={"session_id": sid}).json()
    print("\nclear:", r)


if __name__ == "__main__":
    main()
