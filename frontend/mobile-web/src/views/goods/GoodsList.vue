<!-- 商品列表 / 搜索 / 分类下钻：对齐 prototype/assets/app.js list（1036-1234）；R3 起按原型 t14 恢复顶部「AI」入口 -->
<template>
  <div class="c-page">
    <div class="c-head x-lhead">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="x-lsearch" @click="router.push('/search')">
        <AppIcon name="search" :size="15" />
        <span :class="kw ? 'x-lkw' : 'x-sph'">{{ kw || '搜商品 / 标签' }}</span>
      </div>
      <!-- 原型 t14 的「返回 + 搜索框 + AI 助手」；带上当前关键词，会话页会预填输入框（不自动发送） -->
      <button type="button" class="x-aibtn" @click="goAi">
        <AppIcon name="comment" :size="13" /><span>AI</span>
      </button>
    </div>

    <div class="pad" style="padding-bottom: 2px">
      <div class="row between">
        <div class="bold ellipsis">{{ head }}</div>
        <span class="tiny dim" style="flex: none">共 {{ total }} 件</span>
      </div>
      <div v-if="swapped" class="tiny mt6" style="color: var(--warn)">
        价格区间上下限已自动交换（BR-SEARCH-05）
      </div>
    </div>

    <div class="x-listbar">
      <div class="x-chips">
        <button
          v-for="s in SORTS"
          :key="s.key"
          type="button"
          class="x-chip"
          :class="{ on: isSortOn(s.key) }"
          @click="pickSort(s.key)"
        >
          {{ s.label }}{{ s.key === 'price' ? (sort === 'price_desc' ? ' ↓' : sort === 'price_asc' ? ' ↑' : '') : '' }}
        </button>
        <button type="button" class="x-chip" :class="{ on: onlyStock }" @click="toggleStock">仅看有货</button>
      </div>
      <button type="button" class="x-filter-btn" :class="{ on: filterCount > 0 }" @click="openFilter">
        <AppIcon name="filter" :size="14" /><span>筛选</span>
        <span v-if="filterCount" class="n">{{ filterCount }}</span>
      </button>
    </div>

    <div class="c-body">
      <div v-if="list.length" class="goods-grid">
        <GoodsCard v-for="g in list" :key="g.id" :g="g" @click="openGoods(g.id)" />
      </div>
      <EmptyState
        v-else-if="!loading && loadError"
        icon="warn"
        title="搜索失败，请重试"
        hint="网络或服务异常，不展示空列表（BR-SEARCH-06）"
        action="重试"
        @action="load(true)"
      />
      <EmptyState
        v-else-if="!loading"
        icon="search"
        title="没有找到相关商品"
        :hint="emptyHint"
        action="清除筛选"
        @action="clearAll"
      />
      <div v-else class="loading-more">加载中…</div>

      <div v-if="list.length" class="page-foot">
        <button v-if="hasMore" type="button" class="btn sm ghost" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>
        <span v-else class="tiny dim">已经到底了 · 共 {{ total }} 件</span>
      </div>
    </div>

    <BottomSheet v-if="filterOpen" title="筛选" :mid="false" @close="filterOpen = false">
      <div class="x-fsec">
        <div class="x-fhd">价格区间（元）</div>
        <div class="row" style="gap: 8px">
          <input v-model="draft.min" class="input" type="number" inputmode="decimal" placeholder="最低价" />
          <span class="dim">—</span>
          <input v-model="draft.max" class="input" type="number" inputmode="decimal" placeholder="最高价" />
        </div>
        <div class="row mt10" style="gap: 7px; flex-wrap: wrap">
          <span
            v-for="t in PRICE_TIPS"
            :key="t.label"
            class="x-chip"
            :class="{ on: draft.min === t.min && draft.max === t.max }"
            @click="applyPriceTip(t)"
          >
            {{ t.label }}
          </span>
        </div>
        <div class="tiny dim mt6">上下限填反了会自动交换（BR-SEARCH-05）</div>
      </div>

      <div class="x-fsec">
        <div class="x-fhd">分类</div>
        <div class="row" style="gap: 7px; flex-wrap: wrap">
          <span class="x-chip" :class="{ on: !draft.categoryId }" @click="draft.categoryId = ''">全部</span>
          <span
            v-for="c in cats"
            :key="c.id"
            class="x-chip"
            :class="{ on: String(draft.categoryId) === String(c.id) }"
            @click="draft.categoryId = c.id"
          >
            {{ c.name }}
          </span>
        </div>
      </div>

      <div class="x-fsec">
        <div class="x-fhd">品牌</div>
        <div class="row" style="gap: 7px; flex-wrap: wrap">
          <span class="x-chip" :class="{ on: !draft.brandId }" @click="draft.brandId = ''">不限</span>
          <span
            v-for="b in brands"
            :key="b.id"
            class="x-chip"
            :class="{ on: String(draft.brandId) === String(b.id) }"
            @click="draft.brandId = b.id"
          >
            {{ b.name }}
          </span>
        </div>
      </div>

      <div class="x-fsec">
        <div class="row between">
          <div class="x-fhd" style="margin: 0">仅看有货</div>
          <div class="cbx" :class="{ on: draft.onlyStock }" @click="draft.onlyStock = !draft.onlyStock">
            <AppIcon v-if="draft.onlyStock" name="check" :size="13" />
          </div>
        </div>
      </div>

      <div class="x-fft">
        <button type="button" class="btn ghost" @click="resetDraft">重置</button>
        <button type="button" class="btn primary" :disabled="previewLoading" @click="applyFilter">
          查看 {{ previewCount }} 件商品
        </button>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import { brandList, categoryTree, goodsList } from '@/api/goods'

const SORTS = [
  { key: 'default', label: '综合' },
  { key: 'price', label: '价格' },
  { key: 'new', label: '上新' },
]

const PRICE_TIPS = [
  { label: '0-50', min: '0', max: '50' },
  { label: '50-100', min: '50', max: '100' },
  { label: '100-300', min: '100', max: '300' },
  { label: '300-1000', min: '300', max: '1000' },
  { label: '1000 以上', min: '1000', max: '' },
]

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const loadError = ref(false)

const cats = ref([])
const brands = ref([])

const filterOpen = ref(false)
const previewCount = ref(0)
const previewLoading = ref(false)
const draft = reactive({ min: '', max: '', categoryId: '', brandId: '', onlyStock: false })

const kw = computed(() => String(route.query.kw || ''))
const onlyStock = computed(() => String(route.query.onlyStock || '') === '1')
/**
 * 排序入参按 08/05 冻结口径：sort=price_asc|price_desc|new，缺省=综合。
 * 兼容历史链接的 priceAsc/priceDesc/newest 别名（CI-31 后端本身也兼容），URL 里统一写契约值。
 */
const SORT_ALIAS = { priceasc: 'price_asc', pricedesc: 'price_desc', newest: 'new' }
const SORT_KEYS = ['price_asc', 'price_desc', 'new']
const sort = computed(() => {
  const raw = String(route.query.sort || '').trim()
  const s = SORT_ALIAS[raw.toLowerCase()] || raw
  return SORT_KEYS.includes(s) ? s : 'default'
})
const categoryId = computed(() => String(route.query.categoryId || route.query.catId || ''))
const brandId = computed(() => String(route.query.brandId || ''))

const swapped = computed(() => String(route.query.swapped || '') === '1')

const catName = computed(() => {
  if (!categoryId.value) return ''
  const hit = flatCats.value.find((c) => String(c.id) === categoryId.value)
  return hit ? hit.name : ''
})
const brandName = computed(() => {
  if (!brandId.value) return ''
  const hit = brands.value.find((b) => String(b.id) === brandId.value)
  return hit ? hit.name : ''
})

const flatCats = computed(() => {
  const out = []
  for (const c of cats.value) {
    out.push({ id: c.id, name: c.name })
    for (const s of c.children || []) out.push({ id: s.id, name: s.name })
  }
  return out
})

const head = computed(() => {
  if (kw.value) return `「${kw.value}」的结果`
  if (categoryId.value) return catName.value || '分类商品'
  if (brandId.value) return brandName.value || '品牌商品'
  return '全部商品'
})

const emptyHint = computed(() => {
  const parts = [`当前筛选：${kw.value || '无关键词'}`, categoryId.value ? catName.value || '分类' : '全部分类']
  if (brandId.value) parts.push(brandName.value || '品牌')
  return `${parts.join(' · ')}；试试换个关键词，或放宽价格区间 / 品牌`
})

const filterCount = computed(() => {
  let n = 0
  if (route.query.minP || route.query.maxP) n += 1
  if (categoryId.value) n += 1
  if (brandId.value) n += 1
  if (onlyStock.value) n += 1
  return n
})

function isSortOn(key) {
  if (key === 'price') return sort.value === 'price_asc' || sort.value === 'price_desc'
  if (key === 'default') return sort.value === 'default'
  return sort.value === key
}

function pickSort(key) {
  // 价格：升 → 降 → 升 循环；综合：清掉 sort 回默认序
  let next
  if (key === 'price') next = sort.value === 'price_asc' ? 'price_desc' : 'price_asc'
  else if (key === 'default') next = ''
  else next = key
  pushQuery({ sort: next || undefined })
}

function toggleStock() {
  pushQuery({ onlyStock: onlyStock.value ? undefined : '1' })
}

function openGoods(id) {
  router.push(`/goods/${id}`)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

/** AI 入口：带当前关键词进会话页（未登录由路由守卫接管，登录后回跳仍回到列表页） */
function goAi() {
  const ask = kw.value || ''
  router.push({ path: '/assistant', query: ask ? { ask } : {} })
}

function pushQuery(patch) {
  const q = { ...route.query }
  for (const [k, v] of Object.entries(patch)) {
    if (v === undefined || v === '' || v === null) delete q[k]
    else q[k] = String(v)
  }
  router.replace({ path: '/list', query: q })
}

function clearAll() {
  router.replace({ path: '/list' })
}

function buildParams(page) {
  const p = { pageNum: page, pageSize: PAGE_SIZE }
  if (kw.value) p.keyword = kw.value
  const cid = categoryId.value
  if (cid) p.categoryId = cid
  if (brandId.value) p.brandId = brandId.value
  if (onlyStock.value) p.onlyStock = true
  const minP = route.query.minP
  const maxP = route.query.maxP
  if (minP !== undefined && minP !== '') p.minP = Number(minP)
  if (maxP !== undefined && maxP !== '') p.maxP = Number(maxP)
  const s = sort.value
  if (s !== 'default') p.sort = s
  return p
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
    const data = await goodsList(buildParams(reset ? 1 : pageNum.value + 1))
    const rows = data?.list || []
    if (reset) list.value = rows
    else list.value = list.value.concat(rows)
    total.value = Number(data?.total || 0)
    pageNum.value = reset ? 1 : pageNum.value + 1
    hasMore.value = list.value.length < total.value && rows.length > 0
  } catch {
    // 失败不等于「没有结果」：单独标记，避免把异常渲染成空列表（BR-SEARCH-06）
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

function openFilter() {
  draft.min = route.query.minP !== undefined && route.query.minP !== '' ? String(Number(route.query.minP) / 100) : ''
  draft.max = route.query.maxP !== undefined && route.query.maxP !== '' ? String(Number(route.query.maxP) / 100) : ''
  draft.categoryId = categoryId.value
  draft.brandId = brandId.value
  draft.onlyStock = onlyStock.value
  filterOpen.value = true
  refreshPreview()
}

function applyPriceTip(t) {
  draft.min = t.min
  draft.max = t.max
  refreshPreview()
}

function resetDraft() {
  draft.min = ''
  draft.max = ''
  draft.categoryId = ''
  draft.brandId = ''
  draft.onlyStock = false
  refreshPreview()
}

let previewTimer = null
function refreshPreview() {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(async () => {
    previewLoading.value = true
    try {
      const data = await goodsList(draftParams(1))
      previewCount.value = Number(data?.total || 0)
    } catch {
      previewCount.value = 0
    } finally {
      previewLoading.value = false
    }
  }, 220)
}

function draftParams(page) {
  const p = { pageNum: page, pageSize: 1 }
  if (kw.value) p.keyword = kw.value
  if (draft.categoryId) p.categoryId = draft.categoryId
  if (draft.brandId) p.brandId = draft.brandId
  if (draft.onlyStock) p.onlyStock = true
  const minY = Number(draft.min)
  const maxY = Number(draft.max)
  if (draft.min !== '' && isFinite(minY)) p.minP = Math.round(minY * 100)
  if (draft.max !== '' && isFinite(maxY)) p.maxP = Math.round(maxY * 100)
  const s = sort.value
  if (s !== 'default') p.sort = s
  return p
}

function applyFilter() {
  let minC = draft.min !== '' && isFinite(Number(draft.min)) ? Math.round(Number(draft.min) * 100) : undefined
  let maxC = draft.max !== '' && isFinite(Number(draft.max)) ? Math.round(Number(draft.max) * 100) : undefined
  let swap = false
  if (minC !== undefined && maxC !== undefined && minC > maxC) {
    const t = minC
    minC = maxC
    maxC = t
    swap = true
  }
  const q = { ...route.query }
  delete q.catId
  if (minC === undefined) delete q.minP
  else q.minP = String(minC)
  if (maxC === undefined) delete q.maxP
  else q.maxP = String(maxC)
  if (draft.categoryId) q.categoryId = String(draft.categoryId)
  else delete q.categoryId
  if (draft.brandId) q.brandId = String(draft.brandId)
  else delete q.brandId
  if (draft.onlyStock) q.onlyStock = '1'
  else delete q.onlyStock
  if (swap) q.swapped = '1'
  else delete q.swapped
  filterOpen.value = false
  router.replace({ path: '/list', query: q })
}

watch(
  () => route.fullPath,
  () => {
    if (route.path !== '/list') return
    load(true)
  }
)

onMounted(async () => {
  load(true)
  try {
    const data = await categoryTree()
    cats.value = Array.isArray(data) ? data : []
  } catch {
    cats.value = []
  }
  try {
    const data = await brandList({ pageNum: 1, pageSize: 50 })
    brands.value = data?.list || (Array.isArray(data) ? data : [])
  } catch {
    brands.value = []
  }
})
</script>