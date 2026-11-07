<!-- 我的收藏（T2）：列表 / 空态 / 失败重试 / 加载更多，分页写法沿用 GoodsList.vue -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">我的收藏</div>
      <div style="width: 30px" />
    </div>

    <div class="c-body">
      <div v-if="list.length" class="collect-list">
        <div
          v-for="row in list"
          :key="row.id"
          class="collect-row"
          :class="{ off: row.onSale === false }"
          @click="openGoods(row)"
        >
          <GoodsImg :src="row.picUrl" :alt="row.goodsName || '商品'" :seed="row.goodsId" />
          <div class="collect-info">
            <div class="clamp2 collect-name">{{ row.goodsName || '商品已失效' }}</div>
            <div class="row between mt6" style="gap: 6px">
              <PriceText v-if="row.price != null" :cent="Number(row.price)" />
              <span v-else class="tiny dim">价格不可用</span>
              <span v-if="row.onSale === false" class="off-badge">已下架</span>
            </div>
            <div class="tiny dim mt6">{{ row.addTime || '—' }}</div>
          </div>
          <button type="button" class="btn sm ghost collect-cancel" @click.stop="cancelCollect(row)">取消</button>
        </div>
      </div>

      <EmptyState
        v-else-if="!loading && loadError"
        icon="warn"
        title="加载失败，请重试"
        hint="网络或服务异常，不展示空列表"
        action="重试"
        @action="load(true)"
      />
      <EmptyState v-else-if="!loading" icon="star" title="还没有收藏的商品" hint="在商品详情页点「收藏」即可添加" />
      <div v-else class="loading-more">加载中…</div>

      <div v-if="list.length" class="page-foot">
        <button v-if="hasMore" type="button" class="btn sm ghost" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>
        <span v-else class="tiny dim">已经到底了 · 共 {{ total }} 件</span>
      </div>
    </div>
  </div>
</template>

<script>
// 显式多词组件名：eslint vue/multi-word-component-names 未豁免 Collect
export default { name: 'CollectView' }
</script>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import GoodsImg from '@/components/GoodsImg.vue'
import PriceText from '@/components/PriceText.vue'
import { collectAdd, collectList } from '@/api/user'
import { toast } from '@/utils/toast'

const PAGE_SIZE = 10
const router = useRouter()

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const loadError = ref(false)

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/me')
}

// BR-FAV-03：已下架行整行置灰且点击不跳详情
function openGoods(row) {
  if (row.onSale === false) return
  router.push('/goods/' + row.goodsId)
}

async function load(reset = true) {
  if (reset) {
    loading.value = true
    loadError.value = false
    pageNum.value = 1
  } else {
    loadingMore.value = true
  }
  try {
    const data = await collectList({ pageNum: reset ? 1 : pageNum.value + 1, pageSize: PAGE_SIZE })
    const rows = data?.list || []
    if (reset) list.value = rows
    else list.value = list.value.concat(rows)
    total.value = Number(data?.total || 0)
    pageNum.value = reset ? 1 : pageNum.value + 1
    hasMore.value = list.value.length < total.value && rows.length > 0
  } catch {
    // 失败不等于「没有收藏」：单独标记，避免把异常渲染成空列表
    if (reset) {
      list.value = []
      total.value = 0
      loadError.value = true
    }
    hasMore.value = false
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  load(false)
}

async function cancelCollect(row) {
  try {
    await collectAdd({ goodsId: Number(row.goodsId), type: 1 })
    list.value = list.value.filter((x) => x.id !== row.id)
    total.value = Math.max(0, total.value - 1)
    toast('已取消收藏', 'ok')
  } catch {
    /* request 层已提示 */
  }
}

onMounted(() => load(true))
</script>

<style scoped>
.collect-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.collect-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 12px;
  padding: 10px;
  box-shadow: 0 1px 6px rgba(17, 24, 39, 0.07);
  cursor: pointer;
}
.collect-row.off {
  opacity: 0.5;
  cursor: default;
}
.collect-row :deep(.x-gpic) {
  width: 72px;
  height: 72px;
  padding: 0;
  border-radius: 10px;
  flex: none;
  overflow: hidden;
}
.collect-info {
  flex: 1;
  min-width: 0;
}
.collect-name {
  font-size: 13px;
  line-height: 1.42;
  color: #111;
}
.off-badge {
  flex: none;
  padding: 1px 6px;
  border-radius: 5px;
  background: rgba(17, 24, 39, 0.55);
  color: #fff;
  font-size: 10px;
}
.collect-cancel {
  flex: none;
  height: 30px;
  padding: 0 10px;
}
</style>
