"""
orchestrator.py — 编排层
=========================
把一次问答走完：取会话历史 → LangGraph 图执行 → 写回记忆 → 返回统一 data。
上层 main.py 负责 HTTP 与统一信封（{code, msg, data}），本层只关心业务。
"""

from app.graph import graph
from app.memory.session_store import get_store


async def run_chat(session_id: str, message: str, user_id: str = "", auth_header: str = "") -> dict:
    """一次完整问答，返回 {reply, cards}。"""
    store = get_store()
    history = await store.get_history(session_id)

    result = await graph.ainvoke(
        {
            "message": message,
            "user_id": user_id,
            "auth_header": auth_header,
            "history": history,
        },
        config={"configurable": {"thread_id": session_id}},
    )

    reply = result.get("reply", "")
    cards = result.get("cards", [])
    await store.append_turn(session_id, message, reply)
    return {"reply": reply, "cards": cards}
