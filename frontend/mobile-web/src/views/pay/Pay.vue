<!-- 演示支付：对齐 prototype/assets/app.js pay/:orderId（1755-1876）；失败可重试、订单仍是「未付款」 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">{{ state === 'unpaid' ? '演示支付' : '支付' }}</div>
      <div style="width: 30px" />
    </div>

    <div v-if="loading" class="c-body"><div class="loading-more">加载中…</div></div>

    <template v-else-if="notFound">
      <div class="c-body">
        <EmptyState icon="warn" title="订单不存在" hint="请回订单列表刷新（BR-PAY-08）" action="去订单列表" @action="router.replace('/orders')" />
      </div>
    </template>

    <template v-else-if="state === 'other'">
      <div class="c-body">
        <div class="alert warn">
          <div><b>该订单无需支付</b><br />当前状态：{{ info.orderStatusText || '—' }}（BR-PAY-02）</div>
        </div>
        <div class="card pad mt10 center">
          <div class="tiny dim">订单号 {{ info.orderSn || orderId }}</div>
          <div class="mt8"><PriceText :cent="payAmount" cls="price-xl" /></div>
        </div>
      </div>
      <div style="padding: 0 12px 16px">
        <button type="button" class="btn primary block" @click="router.replace(`/order/${orderId}`)">查看订单详情</button>
      </div>
    </template>

    <template v-else-if="state === 'expired'">
      <div class="c-body">
        <div class="card pad center">
          <div class="tiny dim mt12">应付金额</div>
          <div style="margin-top: 6px"><PriceText :cent="payAmount" cls="price-xl" /></div>
          <div class="small muted mt8">订单号 {{ info.orderSn || orderId }}</div>
        </div>
        <div class="mt10">
          <div class="alert err">
            <div>
              <b>订单已超时关闭</b><br />
              已超过 30 分钟未付款，按超时关闭处理：锁定库存已释放、优惠券退回未使用且不延长有效期（BR-ORDER-06）。<br />请重新下单。
            </div>
          </div>
        </div>
      </div>
      <div style="padding: 0 12px 16px">
        <button type="button" class="btn block" @click="router.replace('/home')">重新下单</button>
      </div>
    </template>

    <template v-else>
      <div class="c-body">
        <div class="card pad center">
          <div class="demo-flag">演示模式：不产生真实扣款</div>
          <div class="tiny dim mt12">应付金额</div>
          <div style="margin-top: 6px"><PriceText :cent="payAmount" cls="price-xl" /></div>
          <div class="small muted mt8">订单号 {{ info.orderSn || orderId }}</div>
          <div class="mt10 small">
            支付剩余时间
            <b style="color: var(--price); font-variant-numeric: tabular-nums">{{ leftText || '--:--' }}</b>
          </div>
          <div class="tiny dim mt6">倒计时仅作提示，超时以订单状态为准（BR-ORDER-06）</div>
        </div>

        <div v-if="failMsg" class="mt10">
          <div class="alert err">
            <div>
              <b>支付失败（演示分支）</b><br />{{ failMsg }}<br />订单仍为「未付款」，可在 30 分钟内重试（BR-PAY-04/05）。
            </div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="cell">
            <div class="label">商品金额</div>
            <div class="value"><PriceText :cent="info.goodsAmount" /></div>
          </div>
          <div class="cell">
            <div class="label">券抵扣</div>
            <div class="value">
              <span v-if="Number(info.couponAmount || 0) > 0" style="color: var(--price)">-<PriceText :cent="info.couponAmount" /></span>
              <span v-else>—</span>
            </div>
          </div>
          <div class="cell">
            <div class="label">运费</div>
            <div class="value"><PriceText :cent="info.freightAmount" /></div>
          </div>
        </div>

        <div class="alert mt12">
          <AppIcon name="bell" :size="15" />
          <div>
            点「模拟支付成功」后订单转「待发货」并完成锁库存转实扣；点「模拟支付失败」只改本次结果，订单与库存都不动（BR-PAY-03/04/05）。
          </div>
        </div>
      </div>

      <div class="c-actionbar">
        <button type="button" class="btn" style="flex: 1" :disabled="paying" @click="pay('fail')">模拟支付失败</button>
        <button type="button" class="btn primary" style="flex: 1" :disabled="paying" @click="pay('success')">
          {{ paying ? '处理中…' : '模拟支付成功' }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import { orderDetail, payNotifyWechat, payResult, payWechat } from '@/api/order'
import { countdown } from '@/utils/format'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => String(route.params.orderId))
const loading = ref(true)
const notFound = ref(false)
const info = ref({})
const failMsg = ref('')
const paying = ref(false)
const leftText = ref('')
let timer = null

const state = computed(() => {
  const s = String(info.value.orderStatus || info.value.status || '')
  if (s === 'unpaid') return Date.now() > expireAt.value ? 'expired' : 'unpaid'
  if (!s) return 'other'
  return 'other'
})

/** 订单状态数字码 → 语义串（07 §1.1 状态机：101 未付款 / 103 已取消 / 201 待发货 / 301 待收货 / 401 已完成(待评价) / 501 已完成） */
const CODE_TEXT = {
  101: 'unpaid',
  102: 'unpaid',
  103: 'canceled',
  201: 'undelivered',
  202: 'undelivered',
  301: 'shipped',
  401: 'confirmed',
  501: 'completed',
}
function codeToText(code) {
  if (code == null || code === '') return ''
  return CODE_TEXT[Number(code)] || String(code)
}

const expireAt = computed(() => {
  const t = info.value.expireTime
  if (!t) return 0
  const d = typeof t === 'number' ? new Date(t) : new Date(String(t).replace(/-/g, '/'))
  const ms = d.getTime()
  return Number.isFinite(ms) ? ms : 0
})

const payAmount = computed(() => {
  const v = info.value.orderAmount ?? info.value.actualPrice ?? info.value.payAmount
  return Number(v || 0)
})

function tick() {
  if (!expireAt.value) {
    leftText.value = ''
    return
  }
  const left = expireAt.value - Date.now()
  if (left <= 0) {
    leftText.value = '已超时'
    if (timer) clearInterval(timer)
    timer = null
    toast('订单已超时关闭（BR-ORDER-06）：库存与优惠券已释放，请重新下单', 'err')
    load()
    return
  }
  leftText.value = countdown(left)
}

async function load() {
  try {
    const [result, detail] = await Promise.all([
      payResult({ orderId: orderId.value }).catch(() => null),
      orderDetail({ orderId: orderId.value }).catch(() => null),
    ])
    if (!result && !detail) {
      notFound.value = true
      return
    }
    info.value = {
      ...(detail || {}),
      ...(result || {}),
      // CI-106 同时给 status（unpaid/paid 字符串，优先）与 orderStatus（101 等数字码，仅兜底）
      orderStatus: result?.status || detail?.status || codeToText(result?.orderStatus),
      orderSn: detail?.orderSn,
      goodsAmount: detail?.goodsAmount,
      couponAmount: detail?.couponAmount,
      freightAmount: detail?.freightAmount,
      orderAmount: detail?.orderAmount,
      expireTime: result?.expireTime || detail?.expireTime,
    }
    if (timer) clearInterval(timer)
    timer = setInterval(tick, 1000)
    tick()
  } catch (e) {
    if (String(e?.code) === '404' || e?.code === 404) notFound.value = true
  } finally {
    loading.value = false
  }
}

/** 轮询 CI-106，最多 ~6s，确认订单已入账（BR-PAY-08：不自动重试扣款，只查结果） */
async function waitPaid() {
  for (let i = 0; i < 10; i += 1) {
    try {
      const r = await payResult({ orderId: orderId.value })
      if (r?.status === 'paid') return true
    } catch {
      /* 继续轮询 */
    }
    await new Promise((res) => setTimeout(res, 600))
  }
  return false
}

async function pay(mode) {
  if (paying.value) return
  paying.value = true
  try {
    const data = await payWechat({ orderId: orderId.value, mode })
    if (mode === 'success' || data?.payResult === 'success') {
      // 演示支付：CI-103 只返回支付参数，真正入账由 CI-104 回调完成（dev 明文 {orderId, payId}）。
      // 生产由微信服务器回调；这里前端模拟一次回调，等订单入账后再跳订单详情。
      const payId = 'wxmock' + orderId.value
      await payNotifyWechat({ orderId: Number(orderId.value), payId })
      const settled = await waitPaid()
      if (!settled) {
        toast('支付结果确认中，可在订单列表查看', 'err')
        router.replace('/orders')
        return
      }
      toast('支付成功', 'ok')
      router.replace(`/order/${orderId.value}?paid=1`)
      return
    }
    failMsg.value = data?.payResult || '支付未完成（演示分支）'
    toast('支付失败：订单仍为「未付款」，可在 30 分钟内重试', 'err')
    await load()
  } catch (e) {
    const code = String(e?.code || '')
    if (code === 'ORDER_EXPIRED') {
      toast('订单已超时关闭（BR-ORDER-06），请重新下单', 'err')
      await load()
    } else if (code === 'ORDER_ALREADY_PAID') {
      toast('该订单已支付，无需重复支付', 'err')
      router.replace(`/order/${orderId.value}`)
    } else if (code === 'ORDER_NOT_PAYABLE') {
      toast('当前订单状态不可支付（BR-PAY-02）', 'err')
    }
    // 其余错误 request 层已提示；保持订单未付款，允许重试
  } finally {
    paying.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/orders')
}

onMounted(load)
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>