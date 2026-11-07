import request from '@/utils/request'
import { useUserStore } from '@/store/user'

// 创建会话 POST /agent/session → data: { session_id }
export const createSession = () => request({ url: '/agent/session', method: 'post' })

// 发送消息 POST /agent/chat → data: { reply, cards }
export const sendChat = (data) => request({ url: '/agent/chat', method: 'post', data })

// 清空会话 POST /agent/session/clear → data: { ok }
export const clearSession = (data) => request({ url: '/agent/session/clear', method: 'post', data })

/**
 * 健康检查 GET /agent/health。
 * 以 app/main.py 为准：当前该端点返回裸对象 { status, llm_provider, tool_fallback, memory }，
 * 不带 { code, msg, data } 信封；而 utils/request.js 只放行 code===200，直接用会被判为失败并永远降级。
 * 这里用 axios transformResponse 把「无 code 的对象」补成标准信封，标准信封则原样透传，
 * 这样 T8 改造前后（裸对象 / 标准信封 / 增加 gateway_reachable）都能被会话页正确消费。
 * silent 抑制降级时的全局错误 toast，由会话页顶部黄条承接。
 */
export const health = () =>
  request({
    url: '/agent/health',
    method: 'get',
    silent: true,
    transformResponse: [
      (raw) => {
        try {
          const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
          if (parsed && typeof parsed === 'object' && !Array.isArray(parsed) && parsed.code === undefined) {
            return { code: 200, msg: 'ok', data: parsed }
          }
          return parsed
        } catch {
          return raw
        }
      },
    ],
  })

// ---------------- SSE 流式对话 ----------------

// 后端或代理可能用 LF、CRLF 或两者混用分帧，统一按「连续两个换行」切出完整帧
function takeFrame(buffer) {
  const m = /\r?\n\r?\n/.exec(buffer)
  if (!m) return null
  return { frame: buffer.slice(0, m.index), rest: buffer.slice(m.index + m[0].length) }
}

// 单帧 JSON 非法必须抛错而非吞掉：静默跳过会让调用方误判流仍正常，错过降级时机
function parseFrame(frame, handlers, state) {
  let event = 'message'
  const dataLines = []
  for (const raw of frame.split('\n')) {
    const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw
    // SSE 允许以 : 开头的注释行做心跳保活，不能把它喂给 JSON.parse
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''))
  }
  if (!dataLines.length) return
  let payload
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch {
    throw new Error('sse_bad_json')
  }
  switch (event) {
    case 'delta':
      if (typeof handlers.onDelta === 'function' && typeof payload.text === 'string') handlers.onDelta(payload.text)
      break
    case 'cards':
      if (typeof handlers.onCards === 'function') handlers.onCards(Array.isArray(payload.cards) ? payload.cards : [])
      break
    case 'done':
      state.finished = true
      if (typeof handlers.onDone === 'function') handlers.onDone(payload)
      break
    case 'error': {
      state.finished = true
      const err = Object.assign(new Error(payload.msg || '助手暂时不可用，请稍后再试'), { code: payload.code })
      // 有错误气泡处理器就交给页面呈现可重试态；否则退回异常路径触发降级，避免把错误吞掉
      if (typeof handlers.onError === 'function') handlers.onError(err)
      else throw err
      break
    }
    default:
      break
  }
}

/**
 * SSE 流式对话 POST /agent/chat/stream。
 * axios 在浏览器拿不到 ReadableStream，改用原生 fetch + getReader + TextDecoder 逐帧解析。
 * 认证头必须与 utils/request.js 拦截器一致（Authorization: Bearer <token>），否则会被网关按未登录拒绝而白白降级。
 * 任何「流建立失败 / 非流响应 / 解析异常 / 流异常结束」都抛给调用方，由页面统一回退到 sendChat。
 */
export async function streamChat({ session_id, message }, handlers = {}) {
  const { signal } = handlers
  const userStore = useUserStore()
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  if (userStore.token) headers.Authorization = `Bearer ${userStore.token}`

  const resp = await fetch('/agent/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify({ session_id, message }),
    signal,
  })

  const ct = resp.headers.get('Content-Type') || ''
  // 端点缺失(404/501)、代理返回 JSON/HTML、或响应体不可读都说明后端不支持流式，主动关掉响应体再抛给降级路径
  if (!resp.ok || !resp.body || !ct.includes('text/event-stream')) {
    try { await resp.body?.cancel() } catch { /* 响应体已关闭，无需再处理 */ }
    throw new Error(`stream_unavailable(${resp.status}, ${ct || 'no-content-type'})`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  const state = { finished: false }
  let buffer = ''
  try {
    while (!state.finished) {
      const { done, value } = await reader.read()
      if (done) break
      // stream:true 保留跨 chunk 被截断的多字节字符，避免中文被拆成乱码
      buffer += decoder.decode(value, { stream: true })
      let part = takeFrame(buffer)
      while (part && !state.finished) {
        buffer = part.rest
        parseFrame(part.frame, handlers, state)
        part = takeFrame(buffer)
      }
    }
    // 后端可能不发尾部空行，把解码器余量和残留缓冲当作最后一帧兜底
    if (!state.finished) {
      buffer += decoder.decode()
      if (buffer.trim()) parseFrame(buffer, handlers, state)
    }
    if (state.finished) {
      // 收到 done/error 后主动关闭流，防止后端不关连接时 read() 一直挂起
      try { await reader.cancel() } catch { /* 流已结束，忽略 */ }
    } else {
      // 对端关闭了流却没给 done/error，视为异常结束，触发降级以免留下半截文本或白屏
      throw new Error('stream_incomplete')
    }
    return { finished: true }
  } catch (e) {
    try { await reader.cancel() } catch { /* 流已关闭或已被 abort，忽略 */ }
    throw e
  }
}
