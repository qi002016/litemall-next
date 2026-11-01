<!-- 消息：对齐 prototype/assets/app.js messages（813-874）；R1 消息类型以后端 order/coupon/system 为准 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div style="width: 30px" />
      <div class="title">消息</div>
      <div class="right">
        <span v-if="unreadAll" style="color: var(--brand)" @click="readAll">全部已读</span>
        <span v-else class="dim">已全部读过</span>
      </div>
    </div>

    <TabsStrip v-model="tab" :items="tabs" />

    <div class="c-body" style="padding-top: 10px">
      <div v-if="loading" class="loading-more">加载中…</div>
      <template v-else-if="list.length">
        <div v-for="m in list" :key="m.id" class="card pad x-msg" :class="{ unread: m.readStatus === false }" @click="open(m)">
          <div class="row top" style="gap: 10px">
            <div class="x-mav" :class="m.type"><AppIcon :name="typeIcon(m.type)" :size="19" /></div>
            <div class="grow" style="min-width: 0">
              <div class="row between">
                <span class="tag" :class="typeTag(m.type)">{{ typeName(m.type) }}</span>
                <span class="tiny dim">{{ fromNow(m.addTime) }}</span>
              </div>
              <div class="row between mt6">
                <span class="small bold ellipsis">{{ m.title }}</span>
                <i v-if="m.readStatus === false" class="x-reddot" />
              </div>
              <div class="tiny dim mt6 clamp2">{{ m.brief }}</div>
              <div class="tiny mt6" style="color: var(--brand)">查看详情 ›</div>
            </div>
          </div>
        </div>
        <div v-if="hasMore" class="loading-more" @click="loadMore">加载更多</div>
        <div v-else class="page-foot">已经到底了 · 订单与优惠券通知</div>
      </template>
      <EmptyState v-else icon="bell" title="暂无消息" hint="订单、售后、优惠券的系统通知都会出现在这里" />
    </div>

    <BottomSheet v-if="detailVisible" :title="detail?.title || '消息详情'" :mid="false" @close="detailVisible = false">
      <div class="tiny dim" style="margin-bottom: 10px">{{ detail ? time(detail.addTime) : '' }}</div>
      <div class="small" style="line-height: 1.9; white-space: pre-wrap">{{ detail?.content || '（无内容）' }}</div>
      <button type="button" class="btn block primary mt12" @click="detailVisible = false">关闭</button>
    </BottomSheet>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import TabsStrip from '@/components/TabsStrip.vue'
import { msgDetail, msgList, msgRead } from '@/api/msg'
import { useAppStore } from '@/store/app'
import { fromNow, time } from '@/utils/format'
import { toast } from '@/utils/toast'

const appStore = useAppStore()

const TYPE_NAME = { order: '订单', coupon: '优惠券', system: '系统', aftersale: '售后' }
const TYPE_ICON = { order: 'truck', aftersale: 'refresh', coupon: 'tag', system: 'bell' }
const TYPE_TAG = { order: 'info', aftersale: 'warn', coupon: 'danger', system: 'gray' }

const tab = ref('all')
const tabs = ref([
  { key: 'all', label: '全部', count: 0 },
  { key: 'order', label: '订单', count: 0 },
  { key: 'coupon', label: '优惠券', count: 0 },
  { key: 'system', label: '系统', count: 0 },
])

const loading = ref(true)
const list = ref([])
const pageNum = ref(1)
const hasMore = ref(false)

const detailVisible = ref(false)
const detail = ref(null)

const unreadAll = computed(() => list.value.some((m) => m.readStatus === false))

function typeName(t) {
  return TYPE_NAME[t] || t || '消息'
}
function typeIcon(t) {
  return TYPE_ICON[t] || 'bell'
}
function typeTag(t) {
  return TYPE_TAG[t] || 'gray'
}

async function load(reset) {
  if (reset) {
    pageNum.value = 1
    loading.value = true
  }
  try {
    const page = await msgList({ type: tab.value === 'all' ? undefined : tab.value, pageNum: pageNum.value, pageSize: 20 })
    const rows = page?.list || (Array.isArray(page) ? page : [])
    list.value = reset ? rows : list.value.concat(rows)
    hasMore.value = rows.length >= 20
    await loadCounts()
  } catch {
    if (reset) list.value = []
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

async function loadCounts() {
  const keys = ['all', 'order', 'coupon', 'system']
  const results = await Promise.all(
    keys.map(async (k) => {
      try {
        const page = await msgList({ type: k === 'all' ? undefined : k, pageNum: 1, pageSize: 50 })
        const rows = page?.list || (Array.isArray(page) ? page : [])
        return [k, rows.filter((m) => m.readStatus === false).length]
      } catch {
        return [k, 0]
      }
    })
  )
  const map = Object.fromEntries(results)
  tabs.value = tabs.value.map((t) => ({ ...t, count: map[t.key] || 0 }))
}

function reload() {
  load(true)
}

function loadMore() {
  pageNum.value += 1
  load(false)
}

async function open(m) {
  detail.value = { ...m, content: m.brief }
  detailVisible.value = true
  try {
    const data = await msgDetail({ id: m.id })
    if (data) detail.value = data
  } catch {
    /* 详情失败时保留摘要 */
  }
  if (m.readStatus === false) {
    try {
      await msgRead({ id: m.id })
      m.readStatus = true
      appStore.refreshUnread()
      loadCounts()
    } catch {
      /* 忽略已读失败 */
    }
  }
}

watch(tab, () => reload())

async function readAll() {
  const targets = list.value.filter((m) => m.readStatus === false)
  for (const m of targets) {
    try {
      await msgRead({ id: m.id })
      m.readStatus = true
    } catch {
      /* 继续 */
    }
  }
  toast('已全部标记为已读', 'ok')
  appStore.refreshUnread()
  loadCounts()
}

onMounted(() => load(true))
</script>