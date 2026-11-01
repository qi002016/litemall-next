<!-- 待评价 / 评价：对齐 prototype/assets/app.js review（2571-2677）；R2 接入 CI-36 真实提交 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">{{ orderId ? '评价' : '待评价' }}</div>
      <div class="right" v-if="orderId" @click="router.push(`/order/${orderId}`)">订单</div>
      <div v-else style="width: 30px" />
    </div>

    <div class="c-body">
      <div v-if="loading" class="loading-more">加载中…</div>

      <!-- 待评价订单列表 -->
      <template v-else-if="!orderId">
        <template v-if="pendingOrders.length">
          <div
            v-for="o in pendingOrders"
            :key="o.orderId"
            class="card pad"
            style="margin-bottom: 10px; cursor: pointer"
            @click="router.push({ path: '/review', query: { orderId: o.orderId } })"
          >
            <div class="row between">
              <div class="tiny dim">{{ o.orderSn }}</div>
              <div class="tiny dim">{{ time(o.addTime) }}</div>
            </div>
            <div v-for="g in (o.goodsList || []).filter((x) => Number(x.commentStatus) === 0)" :key="g.orderGoodsId" class="row top mt10">
              <div class="x-thumb sm" :class="{ 'has-img': !!pic(g) }">
                <img v-if="pic(g)" :src="pic(g)" :alt="g.goodsName" />
                <template v-else>📦</template>
              </div>
              <div class="grow">
                <div class="small bold clamp2">{{ g.goodsName }}</div>
                <div class="tiny dim mt6">{{ specText(g) }}</div>
              </div>
              <div class="tiny dim">×{{ g.number }}</div>
            </div>
            <div class="row" style="justify-content: flex-end">
              <button type="button" class="btn sm primary">去评价</button>
            </div>
          </div>
        </template>
        <EmptyState
          v-else
          icon="star"
          title="没有待评价的商品"
          hint="确认收货后可以评价；已退款的行不可评价（BR-CM-05）"
          action="看待评价订单"
          @action="router.push({ path: '/orders', query: { tab: 'unreviewed' } })"
        />
      </template>

      <!-- 单订单评价 -->
      <template v-else>
        <div v-if="!evaluable" class="alert mt10">
          <AppIcon name="warn" :size="15" />
          <div>只有「已收货 / 已完成」的订单可以评价；当前订单状态不满足（BR-CM-01）。</div>
        </div>

        <template v-if="rows.length">
          <div v-for="l in rows" :key="l.orderGoodsId" class="card pad" style="margin-bottom: 10px">
            <div class="row top">
              <div class="x-thumb sm" :class="{ 'has-img': !!pic(l) }">
                <img v-if="pic(l)" :src="pic(l)" :alt="l.goodsName" />
                <template v-else>📦</template>
              </div>
              <div class="grow">
                <div class="small bold clamp2">{{ l.goodsName }}</div>
                <div class="tiny dim mt6">{{ l.specText || '默认规格' }}</div>
              </div>
              <div class="tiny dim">×{{ l.number }}</div>
            </div>

            <!-- 已评价：按钮/状态变「已评价」，不可重复提交 -->
            <template v-if="isDone(l)">
              <div class="row between mt10">
                <span class="tag ok">已评价</span>
                <span class="tiny dim">感谢你的评价</span>
              </div>
              <div class="row mt8" style="gap: 4px; align-items: center">
                <div class="stars">
                  <span v-for="n in 5" :key="n" :class="{ off: n > Number(rates[idOf(l)] || 5) }">★</span>
                </div>
                <span class="tiny dim">{{ Number(rates[idOf(l)] || 5) }} 分</span>
              </div>
              <div v-if="texts[idOf(l)]" class="small mt6" style="color: var(--text-2)">{{ texts[idOf(l)] }}</div>
            </template>

            <!-- 已退款 / 状态不满足：给出原因，不出现评分与提交 -->
            <div v-else-if="!canEvaluate(l)" class="row mt10">
              <span class="tag gray">{{ unavailableText(l) }}</span>
            </div>

            <!-- 可评价：星级 + 文字 + 提交 -->
            <template v-else>
              <div class="row mt10" style="gap: 4px; align-items: center">
                <span class="tiny dim">评分</span>
                <span v-for="n in 5" :key="n" style="cursor: pointer; line-height: 0" @click="rates[idOf(l)] = n">
                  <svg width="22" height="22" viewBox="0 0 24 24" :fill="n <= (rates[idOf(l)] || 5) ? '#ff8f1f' : 'none'" stroke="#ff8f1f" stroke-width="1.6">
                    <path d="M12 3l2.7 5.7 6.3.9-4.6 4.4 1.1 6.2L12 17.3 6.5 20.2l1.1-6.2L3 9.6l6.3-.9z" />
                  </svg>
                </span>
                <span class="tiny dim">{{ rates[idOf(l)] || 5 }} 分</span>
              </div>
              <textarea
                v-model="texts[idOf(l)]"
                class="textarea mt8"
                maxlength="500"
                placeholder="说说使用感受（选填，最多 500 字）"
              />
              <div class="row between mt8">
                <span class="tiny dim">图片评价暂不支持上传</span>
                <button
                  type="button"
                  class="btn sm primary"
                  :disabled="stateOf(l).submitting"
                  @click="submitOne(l)"
                >
                  {{ stateOf(l).submitting ? '提交中…' : '提交评价' }}
                </button>
              </div>
            </template>
          </div>

          <div class="alert mt12">
            <AppIcon name="star" :size="15" />
            <div>所有行评价完成后订单转「已完成」（BR-ORD-09）；已退款行不参与评价判定。</div>
          </div>
        </template>
        <EmptyState
          v-else
          icon="check"
          title="这个订单没有可评价的商品"
          hint="已全部评价或商品已退款退货"
          action="返回订单"
          @action="router.replace(`/order/${orderId}`)"
        />
      </template>
    </div>

    <div v-if="orderId && pendingRows.length" class="c-actionbar">
      <div class="sum">还有 {{ pendingRows.length }} 行待评价</div>
      <button type="button" class="btn primary" :disabled="submittingAll" @click="submitAll">
        {{ submittingAll ? '提交中…' : '全部提交' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { orderDetail, orderList } from '@/api/order'
import { commentPost } from '@/api/goods'
import { orderStatusKey } from '@/utils/dict'
import { time } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const pendingOrders = ref([])
const detail = ref(null)
const rates = reactive({})
const texts = reactive({})
const rowState = reactive({})
const submittingAll = ref(false)

const orderId = computed(() => String(route.query.orderId || ''))
// 订单行入口（/review?orderId=&orderGoodsId=）：仅聚焦该行，不改变可评判定
const focusGoodsId = computed(() => String(route.query.orderGoodsId || ''))

/** 只有已收货(301)/已完成(501)才允许评价（BR-CM-01） */
const evaluable = computed(() => ['confirmed', 'completed'].includes(orderStatusKey(detail.value)))

/** 评价锚订单行：commentStatus 只出现在 goodsList(OrderGoodsVO)，lines 没有，故优先 goodsList */
const rows = computed(() => {
  const d = detail.value
  if (!d) return []
  const goods = Array.isArray(d.goodsList) ? d.goodsList : []
  let out
  if (goods.length) out = goods.map((g) => ({ ...g, commentStatus: Number(g.commentStatus ?? -1) }))
  else out = (d.lines || []).map((l) => ({ ...l, commentStatus: -1 }))
  // 行级入口：把焦点行排到最前（不改可评判定）
  if (focusGoodsId.value) {
    const idx = out.findIndex((l) => idOf(l) === focusGoodsId.value)
    if (idx > 0) out = [out[idx], ...out.slice(0, idx), ...out.slice(idx + 1)]
  }
  return out
})

const pendingRows = computed(() => rows.value.filter((l) => canEvaluate(l) && !isDone(l)))

function idOf(l) {
  return String(l?.orderGoodsId ?? '')
}

function stateOf(l) {
  const id = idOf(l)
  if (!rowState[id]) rowState[id] = { submitting: false, done: false, commentId: '' }
  return rowState[id]
}

function isDone(l) {
  return Number(l.commentStatus) > 0 || !!stateOf(l).done
}

function refunded(l) {
  if (Number(l.refundStatus) === 2) return true
  const num = Number(l.number || 0)
  return num > 0 && Number(l.refundedNumber || 0) >= num
}

function canEvaluate(l) {
  if (!evaluable.value) return false
  if (refunded(l)) return false
  return Number(l.commentStatus) === 0
}

function unavailableText(l) {
  if (refunded(l)) return '已退款，不可评价（BR-CM-05）'
  if (!evaluable.value) return '订单未收货，暂不可评价'
  if (Number(l.commentStatus) > 0) return '已评价'
  return '暂不可评价'
}

function pic(g) {
  return mediaUrl(g.picUrl || '')
}

function specText(g) {
  if (Array.isArray(g.specifications)) return g.specifications.join(' / ')
  return g.specText || '默认规格'
}

/** CI-36：逐行提交，成功即把该行置「已评价」；重复评价(409) 也按已评价处理 */
async function submitOne(l) {
  const id = idOf(l)
  const st = stateOf(l)
  if (!canEvaluate(l) || st.submitting || st.done) return
  st.submitting = true
  try {
    const res = await commentPost({
      orderId: Number(orderId.value),
      orderGoodsId: Number(l.orderGoodsId),
      star: Number(rates[id] || 5),
      content: (texts[id] || '').trim() || undefined,
    })
    st.done = true
    st.commentId = res?.commentId ? String(res.commentId) : ''
    l.commentStatus = 1
    toast(res?.orderStatusUpdated ? '评价成功，订单已完成' : '评价成功', 'ok')
    if (res?.orderStatusUpdated) await reloadDetail()
  } catch (e) {
    const code = Number(e?.code)
    if (code === 409) {
      // COMMENT_ALREADY_EXISTS：服务端已存在评价，按已评价展示，避免重复提交
      st.done = true
      l.commentStatus = 1
    }
  } finally {
    st.submitting = false
  }
}

async function submitAll() {
  const list = pendingRows.value.slice()
  if (!list.length) return
  submittingAll.value = true
  try {
    for (const l of list) {
      await submitOne(l)
    }
  } finally {
    submittingAll.value = false
  }
}

async function reloadDetail() {
  try {
    detail.value = await orderDetail({ orderId: orderId.value })
  } catch {
    /* request 层已提示 */
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/orders')
}

function initRows() {
  for (const l of rows.value) {
    const id = idOf(l)
    if (rates[id] == null) rates[id] = 5
    if (texts[id] == null) texts[id] = ''
    stateOf(l)
  }
}

onMounted(async () => {
  try {
    if (!orderId.value) {
      const data = await orderList({ tab: 'unreviewed', pageNo: 1, pageSize: 20 })
      pendingOrders.value = (data?.list || []).filter((o) => o.hasUnreviewed)
      return
    }
    detail.value = await orderDetail({ orderId: orderId.value })
    initRows()
  } catch {
    pendingOrders.value = []
    detail.value = null
  } finally {
    loading.value = false
  }
})
</script>