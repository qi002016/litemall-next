<!-- AI 购物助手会话页（T9）：消息列表 / 流式打字 / 发送 / 错误重试 / 清空 / 降级黄条 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">AI 购物助手</div>
      <div class="right" @click="clearChat"><span>清空对话</span></div>
    </div>

    <!-- BR-AI-02：后端不可用时降级提示 + 重试，不白屏、不 500 页面 -->
    <div v-if="degraded" class="alert warn as-alert" @click="init">
      <AppIcon name="warn" :size="15" />
      <div>AI 助手暂时不可用，点击重试</div>
    </div>

    <div ref="scrollBox" class="as-body c-body">
      <div v-if="initializing" class="loading-more">正在连接助手…</div>
      <div v-else-if="!messages.length" class="as-welcome">
        <div class="as-welcome-ico"><AppIcon name="comment" :size="28" /></div>
        <div class="bold">你好，我是小满购物助手</div>
        <div class="tiny dim mt6" style="line-height: 1.8">可以问我：找商品 / 查订单 / 我的优惠券 / 售后进度</div>
      </div>

      <div v-for="m in messages" :key="m.id" class="as-msg" :class="m.role">
        <div class="as-bubble" :class="{ err: m.error }">
          <div v-if="m.text" class="as-text">{{ m.text }}</div>
          <!-- 流式未结束时给一个轻量「正在输入」提示，纯 CSS 不引入依赖 -->
          <div v-if="m.streaming" class="as-typing tiny dim">
            <span class="as-typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>正在输入…
          </div>
          <AssistantCard v-if="!m.error && m.cards && m.cards.length" :cards="m.cards" />
          <template v-if="m.error">
            <div class="tiny as-err-tip">{{ m.errTip || '消息发送失败' }}</div>
            <button type="button" class="btn sm ghost mt6" @click="retry(m)">重试</button>
          </template>
        </div>
      </div>
    </div>

    <div class="as-inputbar">
      <input
        v-model="input"
        class="input"
        type="text"
        placeholder="问问小满…"
        :disabled="!canInput"
        @keyup.enter="send()"
      />
      <button type="button" class="btn primary" :disabled="!canSend" @click="send()">
        {{ sending ? '发送中…' : '发送' }}
      </button>
    </div>
  </div>
</template>

<script>
// 显式多词组件名：eslint vue/multi-word-component-names 未豁免 Assistant
export default { name: 'AssistantView' }
</script>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import AssistantCard from '@/components/AssistantCard.vue'
import { clearSession, createSession, health, sendChat, streamChat } from '@/api/agent'
import { confirm } from '@/utils/confirm'
import { toast } from '@/utils/toast'

const SESSION_KEY = 'litemall_agent_session'

const route = useRoute()
const router = useRouter()

const messages = ref([])
const input = ref('')
const sessionId = ref('')
const sending = ref(false)
const initializing = ref(true)
const degraded = ref(false)
const scrollBox = ref(null)
let seq = 0
// 持有当前流式请求的控制器，清空对话 / 组件卸载时中止，避免回调继续写入与连接泄漏
let currentAbort = null

const canInput = computed(() => !degraded.value && !initializing.value && !!sessionId.value && !sending.value)
const canSend = computed(() => canInput.value && !!input.value.trim())

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/me')
}

async function scrollToBottom() {
  await nextTick()
  const box = scrollBox.value
  if (box) box.scrollTop = box.scrollHeight
  window.scrollTo(0, document.documentElement.scrollHeight)
}

/** 健康检查 + 会话初始化；任一环失败都进降级态，刷新复用 sessionStorage 里的 session_id。 */
async function init() {
  degraded.value = false
  initializing.value = true
  try {
    const h = await health()
    if (h && h.gateway_reachable === false) throw new Error('gateway_unreachable')
    let sid = sessionStorage.getItem(SESSION_KEY)
    if (!sid) {
      const s = await createSession()
      sid = s?.session_id
      if (!sid) throw new Error('no_session')
      sessionStorage.setItem(SESSION_KEY, sid)
    }
    sessionId.value = sid
  } catch {
    degraded.value = true
  } finally {
    initializing.value = false
    scrollToBottom()
  }
}

function abortCurrentStream() {
  if (!currentAbort) return
  currentAbort.abort()
  currentAbort = null
}

/** 降级路径：复用同一个气泡，用非流式完整结果覆盖流式半成品，避免重复气泡与白屏。 */
async function fallbackToChat(bubble, message) {
  try {
    const data = await sendChat({ session_id: sessionId.value, message })
    bubble.text = data?.reply || ''
    bubble.cards = Array.isArray(data?.cards) ? data.cards : []
    bubble.error = false
  } catch {
    bubble.text = ''
    bubble.cards = []
    bubble.error = true
    bubble.errTip = '消息发送失败'
  } finally {
    bubble.streaming = false
    scrollToBottom()
  }
}

// 优先走流式以获得逐段打字效果；任何流故障都统一回退非流式，保证始终有回复、不白屏
async function send(text, skipUser = false) {
  const message = String(text ?? input.value).trim()
  if (!message || sending.value || degraded.value || initializing.value || !sessionId.value) return
  sending.value = true
  if (!skipUser) {
    messages.value.push({ id: ++seq, role: 'user', text: message, cards: [] })
    input.value = ''
  }
  // 用 reactive 包裹后 push，模板持有的是同一个 proxy，delta 追加才会触发视图刷新
  const bubble = reactive({
    id: ++seq, role: 'assistant', text: '', cards: [],
    streaming: true, error: false, retryText: message,
  })
  messages.value.push(bubble)
  const ctrl = new AbortController()
  currentAbort = ctrl
  scrollToBottom()
  try {
    await streamChat(
      { session_id: sessionId.value, message },
      {
        signal: ctrl.signal,
        onDelta: (t) => { bubble.text += t; scrollToBottom() },
        onCards: (cards) => { bubble.cards = Array.isArray(cards) ? cards : []; scrollToBottom() },
        onDone: (payload) => {
          // done 带完整 reply，用它校正分片可能丢字的文本；cards 若提供也以它为准
          if (typeof payload?.reply === 'string') bubble.text = payload.reply
          if (Array.isArray(payload?.cards)) bubble.cards = payload.cards
          bubble.error = false
          bubble.streaming = false
          scrollToBottom()
        },
        onError: (err) => {
          // 后端 error 事件 HTTP 仍为 200：清掉流式半成品，转成可重试错误气泡
          bubble.text = ''
          bubble.cards = []
          bubble.streaming = false
          bubble.error = true
          bubble.errTip = err?.message || '助手暂时不可用，请稍后再试'
          scrollToBottom()
        },
      }
    )
  } catch {
    // 主动 abort（清空/卸载）不该再降级或写消息；其余流故障一律回退非流式
    if (ctrl.signal.aborted) bubble.streaming = false
    else await fallbackToChat(bubble, message)
  } finally {
    if (currentAbort === ctrl) currentAbort = null
    sending.value = false
    scrollToBottom()
  }
}

function retry(m) {
  const i = messages.value.indexOf(m)
  if (i >= 0) messages.value.splice(i, 1)
  send(m.retryText, true)
}

async function clearChat() {
  if (!sessionId.value) return
  const yes = await confirm({ title: '清空对话', body: '确定要清空当前对话记录吗？', okText: '清空', danger: true })
  if (!yes) return
  // 确认清空后先中止当前流，避免 abort 之后的回调继续写入或误触发降级
  abortCurrentStream()
  try {
    await clearSession({ session_id: sessionId.value })
  } catch {
    toast('清空失败，请稍后再试', 'err')
    return
  }
  messages.value = []
  toast('对话已清空', 'ok')
}

onMounted(() => {
  // BR-AI-01：商品详情「问 AI」可带 ?ask= 预填输入框，不自动发送
  const ask = String(route.query.ask || '')
  if (ask) input.value = ask
  init()
})

// 组件卸载时兜底中止，防止切路由后流仍占用连接、回调写入已销毁的响应式对象
onUnmounted(abortCurrentStream)
</script>

<style scoped>
.as-alert {
  margin: 10px 12px 0;
  cursor: pointer;
}
.as-body {
  flex: 1;
  min-height: 0;
  padding-bottom: 12px;
}
.as-welcome {
  padding: 36px 16px;
  text-align: center;
}
.as-welcome-ico {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  margin: 0 auto 12px;
  display: grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
}
.as-msg {
  display: flex;
  margin-bottom: 12px;
}
.as-msg.user {
  justify-content: flex-end;
}
.as-msg.assistant {
  justify-content: flex-start;
}
.as-bubble {
  max-width: 86%;
  padding: 9px 12px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 6px rgba(17, 24, 39, 0.07);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.as-msg.user .as-bubble {
  background: var(--brand);
  color: #fff;
  border-top-right-radius: 4px;
}
.as-msg.assistant .as-bubble {
  border-top-left-radius: 4px;
}
.as-bubble.err {
  background: #fff1f0;
  color: #a3121c;
}
.as-err-tip {
  color: #a3121c;
}
.as-typing {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}
.as-typing-dots {
  display: inline-flex;
  gap: 3px;
}
.as-typing-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.3;
  animation: as-blink 1.1s infinite ease-in-out;
}
.as-typing-dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.as-typing-dots i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes as-blink {
  0%, 80%, 100% { opacity: 0.25; }
  40% { opacity: 1; }
}
.as-inputbar {
  position: sticky;
  bottom: 0;
  z-index: 40;
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  background: #fff;
  border-top: 1px solid var(--line);
}
.as-inputbar .input {
  flex: 1;
  min-width: 0;
}
.as-inputbar .btn {
  flex: none;
  height: 38px;
}
</style>
