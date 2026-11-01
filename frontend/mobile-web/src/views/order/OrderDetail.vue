<!-- 订单详情：对齐 prototype/assets/app.js order/:id（2099-2223）；主状态与售后单状态分离展示 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">订单详情</div>
      <div style="width: 30px" />
    </div>

    <div v-if="loading" class="c-body"><div class="loading-more">加载中…</div></div>

    <template v-else-if="!o">
      <div class="c-body">
        <EmptyState icon="warn" title="订单不存在" hint="请回订单列表刷新" action="去订单列表" @action="router.replace('/orders')" />
      </div>
    </template>

    <template v-else>
      <div class="c-body">
        <div class="card pad state-card">
          <div class="row between">
            <div class="bold" style="font-size: 16px">{{ orderStatusText(o) }}</div>
            <div>
              <span class="tag" :class="orderTagClass(o)">{{ orderStatusText(o) }}</span>
              <span v-if="o.fullyRefunded" class="tag danger ml6">已全额退款</span>
            </div>
          </div>
          <div class="small muted mt6">{{ statusHint }}</div>
          <div v-if="isUnpaid" class="small mt8">
            支付剩余 <b style="color: var(--price); font-variant-numeric: tabular-nums">{{ leftText || '--:--' }}</b>
            <span class="tiny dim">（超时自动关单，BR-ORDER-06）</span>
          </div>
          <div v-if="o.message" class="tiny dim mt6">备注：{{ o.message }}</div>
        </div>

        <div class="card pad mt10 row top" style="gap: 10px">
          <div style="color: var(--brand)"><AppIcon name="location" :size="20" /></div>
          <div class="grow">
            <div class="small bold">{{ o.consignee || '—' }} <span class="muted">{{ o.mobile || '' }}</span></div>
            <div class="tiny dim mt6">{{ o.address || '无收货地址快照' }}</div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="pad row between" style="padding-bottom: 0">
            <span class="small bold">商品</span>
            <span class="tiny dim">共 {{ totalQty }} 件</span>
          </div>
          <div class="pad" style="padding-top: 0">
            <div v-for="(l, i) in lines" :key="l.orderGoodsId || i" style="border-bottom: 1px solid var(--line)">
              <div class="row top mt10">
                <div class="x-thumb sm" :class="{ 'has-img': !!pic(l) }">
                  <img v-if="pic(l)" :src="pic(l)" :alt="l.goodsName" />
                  <template v-else>📦</template>
                </div>
                <div class="grow">
                  <div class="small bold clamp2">{{ l.goodsName }}</div>
                  <div class="tiny dim mt6">{{ l.specText || '默认规格' }}</div>
                  <div class="tiny dim mt6" v-if="l.refundStatusText">
                    {{ l.refundStatusText }}<span v-if="Number(l.refundedNumber)"> · 已退 {{ l.refundedNumber }} 件</span>
                  </div>
                </div>
                <div style="text-align: right; flex: none">
                  <PriceText :cent="l.price" />
                  <div class="tiny dim">×{{ l.number }}</div>
                </div>
              </div>
              <div class="row" style="justify-content: flex-end; gap: 6px; padding: 8px 0; flex-wrap: wrap">
                <button v-if="l.aftersaleId" type="button" class="btn sm ghost" @click="router.push(`/aftersale/${l.aftersaleId}`)">
                  查看售后
                </button>
                <button v-if="canApply(l)" type="button" class="btn sm" @click="applyAftersale(l)">申请售后</button>
                <span v-else-if="applyReason(l)" class="tiny dim">{{ applyReason(l) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="cell"><div class="label">商品金额</div><div class="value"><PriceText :cent="o.goodsAmount" /></div></div>
          <div class="cell">
            <div class="label">优惠券抵扣</div>
            <div class="value">
              <span v-if="Number(o.couponAmount || 0) > 0" style="color: var(--price)">-<PriceText :cent="o.couponAmount" /></span>
              <span v-else>—</span>
            </div>
          </div>
          <div class="cell"><div class="label">运费</div><div class="value"><PriceText :cent="o.freightAmount" /></div></div>
          <div class="cell">
            <div class="label bold">实付金额</div>
            <div class="value"><PriceText :cent="o.actualPrice ?? o.orderAmount" cls="price-lg" /></div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="cell"><div class="label">订单号</div><div class="value">{{ o.orderSn }}</div></div>
          <div class="cell"><div class="label">下单时间</div><div class="value">{{ time(o.addTime) }}</div></div>
          <div class="cell"><div class="label">支付时间</div><div class="value">{{ o.payTime ? time(o.payTime) : '—' }}</div></div>
          <div class="cell"><div class="label">支付方式</div><div class="value">演示支付（不产生真实扣款）</div></div>
          <div v-if="o.shipSn || o.shipChannel" class="cell">
            <div class="label">物流</div>
            <div class="value">{{ o.shipChannel || '—' }} {{ o.shipSn || '' }}</div>
          </div>
          <div v-if="o.shipTime" class="cell"><div class="label">发货时间</div><div class="value">{{ time(o.shipTime) }}</div></div>
          <div v-if="o.closeTypeText" class="cell"><div class="label">关闭原因</div><div class="value">{{ o.closeTypeText }}</div></div>
        </div>

        <div v-if="timeline.length" class="card pad mt10">
          <div class="small bold" style="margin-bottom: 8px">状态时间线</div>
          <div class="timeline">
            <div v-for="(t, i) in timeline" :key="i" class="item" :class="{ on: true, now: i === 0 }">
              <div class="it">{{ t.action }}</div>
              <div class="is">{{ time(t.time) }} · {{ t.actorText || t.actor || '' }}</div>
            </div>
          </div>
        </div>

        <div class="tiny dim center" style="padding: 12px 0">主状态与售后单状态分离展示（07 §1.1/§1.2）</div>
      </div>

      <div class="c-actionbar">
        <div class="sum">
          实付 <PriceText :cent="o.actualPrice ?? o.orderAmount" />
        </div>
        <template v-if="isUnpaid">
          <button type="button" class="btn" @click="cancel">取消订单</button>
          <button type="button" class="btn primary" @click="router.push(`/pay/${o.orderId}`)">去支付</button>
        </template>
        <template v-else-if="stateKey === 'undelivered'">
          <button type="button" class="btn" @click="toast('待发货订单只能申请「仅退款」（BR-AS-03），请点商品行右侧「申请售后」')">仅退款说明</button>
        </template>
        <template v-else-if="stateKey === 'shipped'">
          <button type="button" class="btn" @click="showLogistics">查看物流</button>
          <button type="button" class="btn primary" @click="receive">确认收货</button>
        </template>
        <template v-else-if="stateKey === 'confirmed' || stateKey === 'completed'">
          <button v-if="o.hasUnreviewed" type="button" class="btn primary" @click="router.push({ path: '/review', query: { orderId: o.orderId } })">
            去评价
          </button>
          <button type="button" class="btn" @click="rebuy">再次购买</button>
        </template>
        <template v-else>
          <button type="button" class="btn primary" @click="rebuy">再次购买</button>
        </template>
      </div>
    </template>

    <BottomSheet v-if="logistics" title="物流信息" @close="logistics = null">
      <div class="cell"><div class="label">物流公司</div><div class="value">{{ o.shipChannel || '—' }}</div></div>
      <div class="cell"><div class="label">运单号</div><div class="value">{{ o.shipSn || '—' }}</div></div>
      <div v-if="o.shipTime" class="cell"><div class="label">发货时间</div><div class="value">{{ time(o.shipTime) }}</div></div>
      <div class="mt12 small bold">物流轨迹</div>
      <div class="timeline mt6">
        <div v-for="(t, i) in shippedTimeline" :key="i" class="item on" :class="{ now: i === 0 }">
          <div class="it">{{ t.action }}</div>
          <div class="is">{{ time(t.time) }}</div>
        </div>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import { orderCancel, orderConfirmReceive, orderDetail } from '@/api/order'
import { cartAdd } from '@/api/cart'
import { useAppStore } from '@/store/app'
import { orderStatusKey, orderStatusText, orderTagClass } from '@/utils/dict'
import { countdown, time } from '@/utils/format'
import { toast } from '@/utils/toast'
import { confirm } from '@/utils/confirm'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const o = ref(null)
const loading = ref(true)
const logistics = ref(false)
const leftText = ref('')
let timer = null

const stateKey = computed(() => orderStatusKey(o.value))
const isUnpaid = computed(() => stateKey.value === 'unpaid')
const lines = computed(() => o.value?.lines || o.value?.goodsList || [])
const totalQty = computed(() => lines.value.reduce((n, l) => n + Number(l.number || 0), 0))
const timeline = computed(() => {
  const arr = o.value?.timeline || []
  return arr.slice().reverse()
})
const shippedTimeline = computed(() => timeline.value.filter((t) => /发货|运输|派送|签收/.test(String(t.action || ''))))

const expireAt = computed(() => {
  const t = o.value?.expireTime
  if (!t) return 0
  const d = new Date(String(t).replace(/-/g, '/'))
  return Number.isFinite(d.getTime()) ? d.getTime() : 0
})

const statusHint = computed(() => {
  const v = o.value
  if (!v) return ''
  if (v.fullyRefunded) return '全部订单行已退款完成，订单打「已全额退款」标记（BR-AS-18）'
  switch (stateKey.value) {
    case 'unpaid':
      return '等待付款，30 分钟内未支付将自动关闭并释放库存。'
    case 'undelivered':
      return '商家正在备货，发货后可查看物流。'
    case 'shipped':
      return '商品运输中，收到货后请点击「确认收货」。'
    case 'confirmed':
      return v.hasUnreviewed ? '已收货，评价后会转入「已完成」。' : '已收货，售后期内仍可申请售后。'
    case 'completed':
      return '订单已完成，感谢你的支持。'
    case 'canceled':
      return '订单已关闭，库存与优惠券已释放。'
    case 'refund':
      return '退款处理中，进度见售后单。'
    case 'refunded':
      return '已退款（存量只读状态，R1 新流程不再写入）。'
    default:
      return ''
  }
})

function pic(l) {
  return l.picUrl || ''
}

function canApply(l) {
  return Array.isArray(l?.canApply?.types) && l.canApply.types.length > 0
}

function applyReason(l) {
  if (l?.refundStatusText) return ''
  return l?.canApply?.reason || ''
}

function applyAftersale(l) {
  router.push({
    path: '/aftersale/apply',
    query: { orderId: o.value.orderId, orderGoodsId: l.orderGoodsId },
  })
}

function tick() {
  if (!expireAt.value) return
  const left = expireAt.value - Date.now()
  if (left <= 0) {
    leftText.value = '已超时'
    if (timer) clearInterval(timer)
    timer = null
    load()
    return
  }
  leftText.value = countdown(left)
}

async function load() {
  try {
    const data = await orderDetail({ orderId: route.params.id })
    o.value = data
    if (timer) clearInterval(timer)
    if (orderStatusKey(data) === 'unpaid' && expireAt.value) {
      timer = setInterval(tick, 1000)
      tick()
    }
  } catch (e) {
    if (String(e?.code) === '404' || e?.code === 404) o.value = null
  } finally {
    loading.value = false
  }
}

async function cancel() {
  const ok = await confirm({ title: '取消订单', body: '取消后订单关闭，占用的库存立即释放、已使用的优惠券退回（BR-ORDER-07/08）。', okText: '取消订单', danger: true })
    if (!ok) return
  try {
    await orderCancel({ orderId: o.value.orderId })
    toast('订单已取消', 'ok')
    load()
  } catch {
    /* request 层已提示 */
  }
}

async function receive() {
  const ok = await confirm({ title: '确认收货', body: '确认后订单转为「已收货」，可申请售后或评价。', okText: '确认收货' })
    if (!ok) return
  try {
    await orderConfirmReceive({ orderId: o.value.orderId })
    toast('已确认收货', 'ok')
    load()
  } catch {
    /* request 层已提示 */
  }
}

function showLogistics() {
  if (!o.value?.shipSn && !o.value?.shipChannel) {
    toast('该订单还没有物流信息（发货后可见，BR-ORD-03）', 'err')
    return
  }
  logistics.value = true
}

async function rebuy() {
  let ok = 0
  for (const l of lines.value) {
    try {
      await cartAdd({ goodsId: Number(l.goodsId), specId: Number(l.productId), number: Number(l.number || 1) })
      ok += 1
    } catch {
      /* 单行失败继续 */
    }
  }
  if (!ok) {
    toast('商品已下架或售罄，无法再次购买（BR-GOODS-08）', 'err')
    return
  }
  appStore.refreshCartCount()
  toast(`已加入购物车 ${ok} 种`, 'ok')
  router.push('/cart')
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