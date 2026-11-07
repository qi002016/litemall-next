"""
scripts/integration_check.py — P1 ④ 真实链路自检
=================================================
从环境变量读 MALL_BASE_URL / AUTH_TOKEN / AGENT_URL，依次打
/agent/health → /agent/session → /agent/chat → /agent/chat/stream，
打印真实输出与断言结果。

默认无 AUTH_TOKEN 时明确打印 SKIP 并以 0 退出：不启动服务也能跑、不误报通过。
真实链路前置条件：DEEPSEEK_API_KEY 有效、TOOL_FALLBACK=remote、9 个 Java 服务在跑。
本脚本不启动任何服务，只作为调用方。
"""

import json
import os
import sys

import httpx

_SKIP_HINT = (
    "真实链路前置：LLM_PROVIDER=deepseek 且 DEEPSEEK_API_KEY 有效、"
    "TOOL_FALLBACK=remote、9 个 Java 服务都在跑（工具真调网关 /app/**）。"
)


def _log(label: str, value) -> None:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    print(f"  {label}: {text[:500]}")


def _check(label: str, ok: bool, detail) -> bool:
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}")
    _log("响应", detail)
    return ok


def _parse_stream(lines):
    """把 iter_lines() 的输出还原成 [(event, data)]；每个 data 行收一帧。"""
    events = []
    ev, data = None, None
    for line in lines:
        if line.startswith("event: "):
            ev = line[len("event: ") :]
        elif line.startswith("data: "):
            raw = line[len("data: ") :]
            try:
                data = json.loads(raw)
            except ValueError:
                data = {"raw": raw}
            events.append((ev, data))
            ev, data = None, None
    return events


def main() -> int:
    mall = os.getenv("MALL_BASE_URL", "http://127.0.0.1:8000")
    agent = os.getenv("AGENT_URL", "http://127.0.0.1:8100").rstrip("/")
    token = os.getenv("AUTH_TOKEN", "").strip()

    print("=== P1 真实链路自检 ===")
    print(f"  MALL_BASE_URL = {mall}")
    print(f"  AGENT_URL     = {agent}")
    print(f"  AUTH_TOKEN    = {'已配置' if token else '未配置'}")

    if not token:
        print("[SKIP] 未提供 AUTH_TOKEN（Bearer），跳过真实链路自检，未发起任何请求。")
        print(f"       {_SKIP_HINT}")
        print("       配置后重跑（Windows cmd）：")
        print("         set AGENT_URL=http://127.0.0.1:8100")
        print("         set MALL_BASE_URL=http://127.0.0.1:8000")
        print("         set AUTH_TOKEN=Bearer <your-token>")
        print("         python scripts/integration_check.py")
        return 0

    headers = {"Authorization": token, "user-info": "1"}
    results = []
    try:
        with httpx.Client(base_url=agent, timeout=10.0) as client:
            # 1) health
            print("\n[1/4] GET /agent/health")
            r = client.get("/agent/health")
            body = r.json()
            results.append(
                _check(
                    "health",
                    r.status_code == 200
                    and body.get("status") == "ok"
                    and "checkpointer" in body
                    and "llm_provider" in body,
                    body,
                )
            )

            # 2) session
            print("\n[2/4] POST /agent/session")
            r = client.post("/agent/session")
            sbody = r.json()
            session_id = (sbody.get("data") or {}).get("session_id", "")
            results.append(
                _check("session", r.status_code == 200 and bool(session_id), sbody)
            )

            # 3) 非流式 chat
            print("\n[3/4] POST /agent/chat")
            r = client.post(
                "/agent/chat",
                json={"session_id": session_id, "message": "推荐 300 元以内的耳机"},
                headers=headers,
            )
            cbody = r.json()
            data = cbody.get("data") or {}
            results.append(
                _check(
                    "chat",
                    r.status_code == 200
                    and cbody.get("code") == 200
                    and bool(data.get("reply"))
                    and isinstance(data.get("cards"), list),
                    cbody,
                )
            )

            # 4) 流式 chat/stream
            print("\n[4/4] POST /agent/chat/stream")
            with client.stream(
                "POST",
                "/agent/chat/stream",
                json={"session_id": session_id, "message": "我的订单到哪了"},
                headers=headers,
            ) as resp:
                ct = resp.headers.get("content-type", "")
                events = _parse_stream(resp.iter_lines())
            names = [e for e, _ in events]
            done = next((d for e, d in events if e == "done"), {})
            results.append(
                _check(
                    "chat/stream",
                    resp.status_code == 200
                    and ct.startswith("text/event-stream")
                    and "delta" in names
                    and names[-2:] == ["cards", "done"]
                    and bool(done.get("reply")),
                    {"content_type": ct, "events": names, "done": done},
                )
            )
    except Exception as e:  # noqa: BLE001 —— 服务没起/网络异常都如实报失败，不假装通过
        print(f"[FAIL] 请求异常：{type(e).__name__}: {e}")
        print("       请确认 AGENT_URL 可达、服务已启动；本脚本不会替你启动服务。")
        return 1

    print(f"\n=== 结果：{sum(results)}/{len(results)} 通过 ===")
    if all(results):
        print("[PASS] 真实链路自检通过")
        return 0
    print("[FAIL] 真实链路自检存在失败项")
    return 1


if __name__ == "__main__":
    sys.exit(main())
