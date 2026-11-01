<!-- 我的订单：对齐 prototype/assets/app.js orders（2045-2098）；七栏含「已完成」（R1 501 不可达，只保留空栏） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="router.push('/me')"><AppIcon name="back" :size="18" /></div>
      <div class="title">我的订单</div>
      <div class="right" @click="router.push('/me')">我的</div>
    </div>

    <TabsStrip v-model="tab" :items="tabs" />

    <div class="c-body">
      <div v-if="loading && !list.length" class="loading-more">加载中…</div>

      <template v-else-if="list.length">
        <div v-for="o in list" :key="o.orderId" class="card" style="margin-bottom: 10px; overflow: hidden">
          <div class="pad row between" style="padding-bottom: 4px; cursor: pointer" @click="view(o)">
            <div class="tiny dim">{{ o.orderSn }}</div>
            <div>
              <span class="tag" :class="orderTagClass(o)">{{ orderStatusText(o) }}</span>
              <span v-if="o.fullyRefunded" class="tag danger ml6">已全额退款</span>
            </div>
          </div>
          <div class="pad" style="padding-top: 0; padding-bottom: 2px" @click="view(o)">
            <div v-for="(g, i) in o.goodsList || []" :key="i" class="row top mt10">
              <div class="x-thumb sm" :class="{ 'has-img': !!pic(g) }">
                <img v-if="pic(g)" :src="pic(g)" :alt="g.goodsName" />
                <template v-else>📦</template>
              </div>
              <div class="grow">
                <div class="small bold clamp2">{{ g.goodsName }}</div>
                <div class="tiny dim mt6">{{ specText(g) }}</div>
                <div class="tiny dim mt6" v-if="g.refundStatusText">{{ g.refundStatusText }}<span v-if="Number(g.refundNumber)"> · 已退 {{ g.refundNumber }} 件</span></div>
              </div>
              <div style="text-align: right; flex: none">
                <PriceText :cent="g.price" />
                <div class="tiny dim">×{{ g.number }}</div>
              </div>
            </div>
          </div>
          <div class="pad row between" style="border-top: 1px solid var(--line); padding-top: 8px">
            <div class="tiny dim">{{ time(o.addTime) }} · 共 {{ totalQty(o) }} 件</div>
            <div class="small">实付 <PriceText :cent="o.orderAmount" /></div>
          </div>
          <div class="pad row" style="justify-content: flex-end; gap: 6px; padding-top: 0; flex-wrap: wrap">
            <button
              v-for="a in acts(o)"
              :key="a.key"
              type="button"
              class="btn sm"
              :class="a.cls"
              @click="run(a, o)"
            >
              {{ a.text }}
            </button>
          </div>
        </div>

        <div class="page-foot">
          <button v-if="hasMore" type="button" class="btn sm ghost" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
          <span v-else class="tiny dim">已经到底了</span>
        </div>
      </template>

      <EmptyState
        v-else
        icon="list"
        :title="tab === 'all' ? '还没有订单' : '该栏暂无订单'"
        hint="挑几件喜欢的商品下单吧"
        action="去逛逛"
        @action="router.push('/home')"
      />
    </div>

    <BottomSheet v-if="logistics" title="物流信息" @close="logistics = null">
      <div class="cell"><div class="label">物流公司</div><div class="value">{{ logistics.shipChannel || '—' }}</div></div>
      <div class="cell"><div class="label">运单号</div><div class="value">{{ logistics.shipSn || '—' }}</div></div>
      <div class="mt12 small bold">物流轨迹</div>
      <div class="timeline mt6">
        <div
          v-for="(t, i) in (logistics.timeline || []).slice().reverse()"
          :key="i"
          class="item on"
          :class="{ now: i === 0 }"
        >
          <div class="it">{{ t.action || t.text }}</div>
          <div class="is">{{ time(t.time || t.at) }}</div>
        </div>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import TabsStrip from '@/components/TabsStrip.vue'
import { orderCancel, orderConfirmReceive, orderDetail, orderHide, orderList } from '@/api/order'
import { cartAdd } from '@/api/cart'
import { useAppStore } from '@/store/app'
import { ORDER_TABS, orderStatusKey, orderStatusText, orderTagClass } from '@/utils/dict'
import { time } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { toast } from '@/utils/toast'
import { confirm } from '@/utils/confirm'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const PAGE_SIZE = 10

const tab = ref(ORDER_TABS.some((t) => t.key === route.query.tab) ? String(route.query.tab) : 'all')
const list = ref([])
const totals = ref({})
const pageNo = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const logistics = ref(null)

const tabs = ref(ORDER_TABS.map((t) => ({ ...t })))

function pic(g) {
  return mediaUrl(g.picUrl || '')
}

function specText(g) {
  const arr = g.specifications
  if (Array.isArray(arr)) return arr.join(' / ')
  return g.specText || '默认规格'
}

function totalQty(o) {
  return (o.goodsList || []).reduce((n, g) => n + Number(g.number || 0), 0)
}

function view(o) {
  router.push(`/order/${o.orderId}`)
}

function acts(o) {
  const out = []
  const s = orderStatusKey(o)
  const push = (key, text, cls) => out.push({ key, text, cls })
  if (s === 'unpaid') {
    push('cancel', '取消订单', 'ghost')
    push('pay', '去支付', 'primary')
  } else if (s === 'undelivered') {
    push('view', '查看详情', 'ghost')
    push('remind', '仅退款说明', '')
  } else if (s === 'shipped') {
    push('logistics', '查看物流', 'ghost')
    push('receive', '确认收货', 'primary')
  } else if (s === 'confirmed' || s === 'completed') {
    if (o.hasUnreviewed) push('review', '去评价', 'primary')
    push('rebuy', '再次购买', o.fullyRefunded ? 'primary' : 'ghost')
    push('view', '查看详情', 'ghost')
  } else {
    push('rebuy', '再次购买', 'primary')
    push('view', '查看详情', 'ghost')
  }
  if ((o.goodsList || []).some((g) => Number(g.refundStatus) > 0 || g.refundStatusText)) push('viewAs', '查看售后', 'ghost')
  if (s === 'canceled' || s === 'completed' || s === 'confirmed') push('del', '删除订单', 'ghost')
  return out
}

async function run(a, o) {
  if (a.key === 'view') return view(o)
  if (a.key === 'pay') return router.push(`/pay/${o.orderId}`)
  if (a.key === 'review') return router.push({ path: '/review', query: { orderId: o.orderId } })
  if (a.key === 'viewAs') return router.push({ path: '/aftersales', query: { orderId: o.orderId } })
  if (a.key === 'remind') return toast('待发货订单可申请仅退款：请在订单详情对应商品行点「申请售后」')
  if (a.key === 'logistics') {
    try {
      const data = await orderDetail({ orderId: o.orderId })
      if (!data?.shipSn && !data?.shipChannel) {
        toast('该订单还没有物流信息（发货后可见，BR-ORD-03）', 'err')
        return
      }
      logistics.value = data
    } catch {
      /* request 层已提示 */
    }
    return
  }
  if (a.key === 'cancel') {
    const ok = await confirm({ title: '取消订单', body: '取消后订单关闭，占用的库存立即释放、已使用的优惠券退回（BR-ORDER-07/08）。', okText: '取消订单', danger: true })
    if (!ok) return
    try {
      await orderCancel({ orderId: o.orderId })
      toast('订单已取消', 'ok')
      await load(true)
    } catch {
      /* request 层已提示 */
    }
    return
  }
  if (a.key === 'receive') {
    const ok = await confirm({ title: '确认收货', body: '确认后订单转为「已收货」，可申请售后或评价。', okText: '确认收货' })
    if (!ok) return
    try {
      await orderConfirmReceive({ orderId: o.orderId })
      toast('已确认收货', 'ok')
      await load(true)
    } catch {
      /* request 层已提示 */
    }
    return
  }
  if (a.key === 'del') {
    const ok = await confirm({ title: '删除订单', body: '订单只对当前账号隐藏，后台与报表不受影响（BR-ORD-07）。', okText: '删除', danger: true })
    if (!ok) return
    try {
      await orderHide({ orderId: o.orderId })
      toast('订单已删除', 'ok')
      await load(true)
    } catch (e) {
      if (String(e?.code) === 'AFTERSALE_EXISTS') toast('有处理中的售后，暂不能删除', 'err')
    }
    return
  }
  if (a.key === 'rebuy') {
    let ok = 0
    for (const g of o.goodsList || []) {
      try {
        await cartAdd({ goodsId: Number(g.goodsId), specId: Number(g.productId), number: Number(g.number || 1) })
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
}

async function load(reset) {
  if (reset) {
    loading.value = true
    pageNo.value = 1
  } else {
    loadingMore.value = true
  }
  try {
    const params = { tab: tab.value, pageNo: reset ? 1 : pageNo.value + 1, pageSize: PAGE_SIZE }
    const data = await orderList(params)
    const rows = data?.list || []
    if (reset) list.value = rows
    else list.value = list.value.concat(rows)
    const total = Number(data?.total || 0)
    totals.value = { ...totals.value, [tab.value]: total }
    pageNo.value = reset ? 1 : pageNo.value + 1
    hasMore.value = list.value.length < total && rows.length > 0
    syncCounts()
  } catch {
    if (reset) list.value = []
    hasMore.value = false
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function syncCounts() {
  tabs.value = ORDER_TABS.map((t) => ({ ...t, count: totals.value[t.key] || 0 }))
}

async function loadCounts() {
  const results = await Promise.all(
    ORDER_TABS.map(async (t) => {
      try {
        const data = await orderList({ tab: t.key, pageNo: 1, pageSize: 1 })
        return [t.key, Number(data?.total || 0)]
      } catch {
        return [t.key, 0]
      }
    })
  )
  const map = {}
  for (const [k, v] of results) map[k] = v
  totals.value = { ...totals.value, ...map }
  syncCounts()
}

function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  load(false)
}

watch(tab, (v) => {
  router.replace({ path: '/orders', query: v === 'all' ? {} : { tab: v } })
  load(true)
})

onMounted(async () => {
  await load(true)
  loadCounts()
})
</script>