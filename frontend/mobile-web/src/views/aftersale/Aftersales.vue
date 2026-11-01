<!-- 我的售后：对齐 prototype/assets/app.js aftersales（2508-2566） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">我的售后</div>
      <div style="width: 30px" />
    </div>

    <TabsStrip v-model="tab" :items="tabs" />

    <div class="c-body">
      <div v-if="loading" class="loading-more">加载中…</div>

      <template v-else-if="list.length">
        <div
          v-for="a in list"
          :key="a.aftersaleId"
          class="card pad"
          style="margin-bottom: 10px; cursor: pointer"
          @click="router.push(`/aftersale/${a.aftersaleId}`)"
        >
          <div class="row between" style="margin-bottom: 4px">
            <div class="tiny dim">{{ a.aftersaleSn }}</div>
            <div>
              <span class="tag" :class="asTagClass(a)">{{ asStatusText(a) }}</span>
              <span v-if="a.restockPending" class="tag danger ml6">库存待回补</span>
            </div>
          </div>
          <div class="row top mt10">
            <div class="x-thumb sm" :class="{ 'has-img': !!pic(a) }">
              <img v-if="pic(a)" :src="pic(a)" :alt="a.goodsName" />
              <template v-else>📦</template>
            </div>
            <div class="grow">
              <div class="small bold clamp2">{{ a.goodsName }}</div>
              <div class="tiny dim mt6">{{ a.specText || '默认规格' }}</div>
            </div>
            <div style="text-align: right; flex: none">
              <PriceText :cent="unitPrice(a)" />
              <div class="tiny dim">×{{ a.number }}</div>
            </div>
          </div>
          <div class="row between" style="border-top: 1px solid var(--line); padding-top: 8px; margin-top: 8px">
            <span class="tiny dim">{{ asTypeText(a) }} · {{ time(a.applyTime) }}</span>
            <span class="small">退款 <PriceText :cent="a.amount" /></span>
          </div>
        </div>
      </template>

      <EmptyState
        v-else
        icon="list"
        title="没有售后记录"
        hint="已收货/待发货的订单行可申请售后；同一订单行同时只能有 1 个进行中的售后"
        action="去订单列表"
        @action="router.push('/orders')"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import TabsStrip from '@/components/TabsStrip.vue'
import { aftersaleList } from '@/api/order'
import { AS_PROCESSING, asStatusKey, asStatusText, asTagClass, asTypeText } from '@/utils/dict'
import { time } from '@/utils/format'
import { mediaUrl } from '@/utils/media'

const route = useRoute()
const router = useRouter()

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'open', label: '处理中' },
  { key: 'done', label: '已完成' },
  { key: 'closed', label: '已关闭' },
]
const TERMINAL = ['closed', 'rejected', 'cancelled']

const tab = ref(FILTERS.some((f) => f.key === route.query.status) ? String(route.query.status) : 'all')
const rows = ref([])
const loading = ref(true)
const tabs = ref(FILTERS.map((f) => ({ ...f, count: 0 })))

const orderId = computed(() => String(route.query.orderId || ''))

const list = computed(() => {
  const base = orderId.value ? rows.value.filter((a) => String(a.orderId) === orderId.value) : rows.value
  if (tab.value === 'all') return base
  return base.filter((a) => {
    const k = asStatusKey(a)
    if (tab.value === 'open') return AS_PROCESSING.includes(k)
    if (tab.value === 'done') return k === 'completed'
    return TERMINAL.includes(k)
  })
})

function pic(a) {
  return mediaUrl(a.picUrl || '')
}

function unitPrice(a) {
  const n = Number(a.number || 1)
  return n ? Math.round(Number(a.amount || 0) / n) : 0
}

function syncTabs() {
  const base = orderId.value ? rows.value.filter((a) => String(a.orderId) === orderId.value) : rows.value
  tabs.value = FILTERS.map((f) => ({
    ...f,
    count:
      f.key === 'all'
        ? base.length
        : f.key === 'open'
          ? base.filter((a) => AS_PROCESSING.includes(asStatusKey(a))).length
          : f.key === 'done'
            ? base.filter((a) => asStatusKey(a) === 'completed').length
            : base.filter((a) => TERMINAL.includes(asStatusKey(a))).length,
  }))
}

async function load() {
  loading.value = true
  const acc = []
  try {
    for (let pageNo = 1; pageNo <= 5; pageNo += 1) {
      const data = await aftersaleList({ pageNo, pageSize: 50 })
      const page = data?.list || []
      acc.push(...page)
      const total = Number(data?.total || 0)
      if (acc.length >= total || !page.length) break
    }
    rows.value = acc
  } catch {
    rows.value = acc
  } finally {
    syncTabs()
    loading.value = false
  }
}

watch(tab, (v) => {
  const q = { ...route.query }
  if (v === 'all') delete q.status
  else q.status = v
  router.replace({ path: '/aftersales', query: q })
})

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/me')
}

onMounted(load)
</script>