<!-- 待评价 / 评价：对齐 prototype/assets/app.js review（2571-2677）；提交评价属 R2（CI-36），R1 只做展示与占位 -->
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
            <div v-for="(g, i) in (o.goodsList || []).filter((x) => Number(x.commentStatus) === 0)" :key="i" class="row top mt10">
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
          hint="确认收货后可以评价；已退款的行不可评价（BR-AS-17）"
          action="看待评价订单"
          @action="router.push({ path: '/orders', query: { tab: 'unreviewed' } })"
        />
      </template>

      <!-- 单订单评价 -->
      <template v-else>
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
            <div class="row mt10" style="gap: 4px; align-items: center">
              <span class="tiny dim">评分</span>
              <span
                v-for="n in 5"
                :key="n"
                style="cursor: pointer; line-height: 0"
                @click="rates[l.orderGoodsId] = n"
              >
                <svg width="22" height="22" viewBox="0 0 24 24" :fill="n <= (rates[l.orderGoodsId] || 5) ? '#ff8f1f' : 'none'" stroke="#ff8f1f" stroke-width="1.6">
                  <path d="M12 3l2.7 5.7 6.3.9-4.6 4.4 1.1 6.2L12 17.3 6.5 20.2l1.1-6.2L3 9.6l6.3-.9z" />
                </svg>
              </span>
              <span class="tiny dim">{{ rates[l.orderGoodsId] || 5 }} 分</span>
            </div>
            <textarea
              v-model="texts[l.orderGoodsId]"
              class="textarea mt8"
              maxlength="200"
              placeholder="说说使用感受（选填）"
            />
            <div class="mt8">
              <div class="uploads">
                <div class="upload-slot" @click="toast('评价图片上传在 R2 开放')">+<br />图片</div>
              </div>
            </div>
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

    <div v-if="orderId && rows.length" class="c-actionbar">
      <div class="sum">共 {{ rows.length }} 行待评价</div>
      <button type="button" class="btn primary" @click="submit">提交评价</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { orderDetail, orderList } from '@/api/order'
import { time } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const pendingOrders = ref([])
const rows = ref([])
const rates = reactive({})
const texts = reactive({})

const orderId = computed(() => String(route.query.orderId || ''))

function pic(g) {
  return mediaUrl(g.picUrl || '')
}

function specText(g) {
  if (Array.isArray(g.specifications)) return g.specifications.join(' / ')
  return g.specText || '默认规格'
}

function submit() {
  toast('评价提交在 R2 开放（CI-36），R1 只做展示与评分交互')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/orders')
}

onMounted(async () => {
  try {
    if (!orderId.value) {
      const data = await orderList({ tab: 'unreviewed', pageNo: 1, pageSize: 20 })
      pendingOrders.value = (data?.list || []).filter((o) => o.hasUnreviewed)
      return
    }
    const data = await orderDetail({ orderId: orderId.value })
    const all = data?.lines || data?.goodsList || []
    rows.value = all.filter((l) => Number(l.commentStatus) === 0 && !Number(l.refundedNumber))
    for (const l of rows.value) {
      rates[l.orderGoodsId] = 5
      texts[l.orderGoodsId] = ''
    }
  } catch {
    pendingOrders.value = []
    rows.value = []
  } finally {
    loading.value = false
  }
})
</script>