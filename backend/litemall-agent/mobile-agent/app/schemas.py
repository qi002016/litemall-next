"""
schemas.py — 请求模型 + 卡片数据结构约定
=========================================
与前端 `mobile-web/src/api/agent.js` 的契约对齐：
  ChatResponse.data = { "reply": str, "cards": Card[] }（无卡片时 cards 为 []，不为 null/省略）

卡片四类（type 字段区分，字段名一处都不能改；§10.4）：
  product:   { "type": "product",   "items": [{ "id","name","brief","price","img" }] }
  order:     { "type": "order",     "order": { "orderSn","orderId","status","statusText","goodsName","total","time" } }
  coupon:    { "type": "coupon",    "items": [{ "id","name","value","desc","expire" }] }
  aftersale: { "type": "aftersale", "items": [{ "aftersaleId","orderSn","goodsName","statusText","amount" }] }

规则：price/value/total/amount 全是整数分的字符串；items 最多 5 条；前端按 type 渲染，
未知 type 忽略（不报错）。无卡片时 cards=[]，不为 null / 不省略。

P1 ② SSE 事件契约（POST /agent/chat/stream，text/event-stream，与前端一致不要改）：
  event: delta / data: {"text":"..."}                      逐段回复文本
  event: cards / data: {"cards":[...]}                     结构化卡片，文本结束后发一次
  event: done  / data: {"reply":"完整文本","cards":[...]}   结束
  event: error / data: {"code":500,"msg":"助手暂时不可用，请稍后再试"}
error 事件 HTTP 状态仍是 200，且不暴露堆栈。卡片结构与非流式 /agent/chat 完全相同。
"""

from pydantic import BaseModel


class ChatRequest(BaseModel):
    """POST /agent/chat 请求体。"""

    session_id: str
    message: str


class SessionRequest(BaseModel):
    """POST /agent/session/clear 请求体。"""

    session_id: str
