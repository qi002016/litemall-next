<!-- 商品详情：对齐 prototype/assets/app.js goods/:id（1238-1443） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">商品详情</div>
      <div class="right" @click="toast('分享为占位入口')">分享</div>
    </div>

    <template v-if="offSale">
      <div class="c-body">
        <EmptyState
          icon="box"
          title="商品已下架"
          hint="下架商品 C 端列表与详情不可访问（BR-GOODS-08）"
          action="看看别的"
          @action="router.replace('/list')"
        />
      </div>
    </template>

    <template v-else-if="g">
      <div class="x-thumb lg" :class="{ 'has-img': !!cover }">
        <img v-if="cover" :src="cover" :alt="g.name" />
        <template v-else>{{ emoji }}</template>
      </div>

      <div class="detail-price">
        <div class="p"><PriceText :cent="priceCent" /></div>
        <div class="row between mt6" style="font-size: 12px; opacity: 0.92">
          <span>已售 {{ g.sales ?? g.soldCount ?? 0 }} 件</span>
          <span>{{ sku ? `库存 ${sku.sellable ?? sku.number ?? 0} 件` : '请选择规格' }}</span>
        </div>
      </div>

      <div class="c-body">
        <div class="card pad">
          <div class="bold" style="font-size: 15px; line-height: 1.5">{{ g.name }}</div>
          <div class="tiny dim mt6">{{ g.brief }}</div>
          <div class="row mt8" style="gap: 6px">
            <span v-for="t in tags" :key="t" class="tag">{{ t }}</span>
          </div>
        </div>

        <div class="card mt10">
          <template v-if="specs.length">
            <div v-for="sp in specs" :key="sp.name" class="spec-group">
              <div class="hd">
                {{ sp.name }}
                <span v-if="sel[sp.name]" class="tiny dim" style="font-weight: 400">已选 {{ sel[sp.name].value ?? sel[sp.name] }}</span>
              </div>
              <div class="spec-opts">
                <div
                  v-for="v in sp.values"
                  :key="v.valueId ?? v.value"
                  class="spec-opt"
                  :class="{ on: isPicked(sp.name, v), disabled: !optEnabled(sp.name, v) }"
                  @click="pickSpec(sp.name, v)"
                >
                  {{ v.value }}
                </div>
              </div>
            </div>
          </template>
          <div v-else class="pad tiny dim">该商品只有一个默认规格</div>
        </div>

        <div class="card pad mt10 row between">
          <div class="bold">数量</div>
          <div class="row" style="gap: 10px">
            <span class="tiny dim">{{ sku ? `最多 ${maxQty} 件` : '先选规格' }}</span>
            <QtyStepper v-model="qty" :min="1" :max="sku ? maxQty : 1" />
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="tabs-scroll">
            <button
              v-for="t in detailTabs"
              :key="t.key"
              type="button"
              :class="{ on: detailTab === t.key }"
              @click="detailTab = t.key"
            >
              {{ t.label }}
            </button>
          </div>
          <div class="pad">
            <template v-if="detailTab === 'detail'">
              <div class="small" style="line-height: 1.8; color: var(--text-2)">{{ g.detail || '暂无详情' }}</div>
              <div class="mt12">
                <div class="x-thumb" :class="{ 'has-img': !!cover }">
                  <img v-if="cover" :src="cover" :alt="g.name" />
                  <template v-else>{{ emoji }}</template>
                </div>
              </div>
            </template>

            <template v-else-if="detailTab === 'params'">
              <div class="x-kv"><span class="k">商品名称</span><span class="v">{{ g.name }}</span></div>
              <div class="x-kv"><span class="k">品牌</span><span class="v">{{ g.brandName || brandName || '—' }}</span></div>
              <div class="x-kv"><span class="k">分类</span><span class="v">{{ g.categoryName || '—' }}</span></div>
              <div class="x-kv"><span class="k">规格数</span><span class="v">{{ skus.length }} 个</span></div>
              <template v-if="g.attributes && g.attributes.length">
                <div class="x-hr"></div>
                <div v-for="a in g.attributes" :key="a.name" class="x-kv">
                  <span class="k">{{ a.name }}</span><span class="v">{{ a.value }}</span>
                </div>
              </template>
              <div class="x-hr"></div>
              <div v-for="s in skus" :key="s.id" class="x-kv">
                <span class="k">{{ s.specText || '默认' }}</span>
                <span class="v">{{ yuan(s.price) }} 元 · 库存 {{ s.sellable ?? s.number ?? 0 }}</span>
              </div>
            </template>

            <template v-else>
              <div class="row between" style="margin-bottom: 8px">
                <div class="bold">{{ rateAvg }} 分</div>
                <div class="tiny dim">{{ commentTotal }} 条评价</div>
              </div>
              <template v-if="reviews.length">
                <div v-for="r in reviews" :key="r.id" style="padding: 10px 0; border-bottom: 1px solid var(--line)">
                  <div class="row between">
                    <div class="row" style="gap: 6px; min-width: 0">
                      <img
                        v-if="r.avatar"
                        :src="mediaUrl(r.avatar)"
                        alt=""
                        style="width: 22px; height: 22px; border-radius: 50%; object-fit: cover; flex: none"
                      />
                      <div class="small bold ellipsis">{{ r.nickname || '匿名用户' }}</div>
                    </div>
                    <div class="tiny dim" style="flex: none">{{ r.addTime || '—' }}</div>
                  </div>
                  <div class="stars" style="margin: 4px 0">
                    <span v-for="n in 5" :key="n" :class="{ off: n > Number(r.star || 5) }">★</span>
                    <span class="tiny dim" style="margin-left: 6px">{{ Number(r.star || 5) }} 分</span>
                  </div>
                  <div class="small" style="color: var(--text-2); white-space: pre-wrap">
                    {{ r.content || '该用户没有填写评价内容' }}
                  </div>
                  <div v-if="r.picUrls && r.picUrls.length" class="row mt6" style="gap: 6px; flex-wrap: wrap">
                    <img
                      v-for="(p, pi) in r.picUrls"
                      :key="pi"
                      :src="mediaUrl(p)"
                      alt=""
                      style="width: 64px; height: 64px; border-radius: 8px; object-fit: cover"
                    />
                  </div>
                  <div v-if="r.adminContent" class="tiny mt6" style="background: #f7f8fa; padding: 6px 8px; border-radius: 8px">
                    <div style="color: var(--text-2)">商家回复：{{ r.adminContent }}</div>
                    <div v-if="r.replyTime" class="dim mt6">{{ r.replyTime }}</div>
                  </div>
                </div>
                <button
                  v-if="reviewHasMore"
                  type="button"
                  class="btn sm ghost mt8"
                  :disabled="reviewLoading"
                  @click="loadReviews(route.params.id, false)"
                >
                  {{ reviewLoading ? '加载中…' : '加载更多评价' }}
                </button>
                <div v-else class="tiny dim center mt8">已经到底了</div>
              </template>
              <div v-else-if="reviewLoading" class="loading-more">加载中…</div>
              <EmptyState v-else icon="comment" title="还没有评价" hint="确认收货后可以来评价" />
            </template>
          </div>
        </div>
      </div>

      <div class="c-actionbar">
        <div class="row" style="flex: none; gap: 2px; text-align: center">
          <div style="font-size: 10.5px; color: var(--text-2); padding: 0 8px" @click="askAi">
            <AppIcon name="comment" :size="19" />
            <div>问 AI</div>
          </div>
          <div
            style="font-size: 10.5px; padding: 0 8px"
            :style="{ color: collected ? 'var(--brand)' : 'var(--text-2)' }"
            @click="toggleCollect"
          >
            <AppIcon name="star" :size="19" :class="{ 'fav-on': collected }" />
            <div>{{ collected ? '已收藏' : '收藏' }}</div>
          </div>
        </div>
        <button type="button" class="btn" style="flex: 1" :disabled="adding" @click="addCart">加入购物车</button>
        <button type="button" class="btn primary" style="flex: 1" @click="buyNow">立即购买</button>
      </div>
    </template>

    <div v-else class="c-body">
      <div v-if="loading" class="loading-more">加载中…</div>
      <EmptyState v-else icon="warn" title="商品不存在" hint="链接可能已失效" action="看看别的" @action="router.replace('/list')" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import QtyStepper from '@/components/QtyStepper.vue'
import { goodsDetail, commentList } from '@/api/goods'
import { cartAdd } from '@/api/cart'
import { collectAdd } from '@/api/user'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { setBuyNow } from '@/store/checkout'
import { toast } from '@/utils/toast'
import { yuan } from '@/utils/format'
import { mediaUrl } from '@/utils/media'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const g = ref(null)
const loading = ref(true)
const offSale = ref(false)
const adding = ref(false)
const collected = ref(false)
const collectBusy = ref(false)
const detailTab = ref('detail')
const qty = ref(1)
const sel = reactive({})
const reviews = ref([])
const commentTotal = ref(0)
const reviewPage = ref(0)
const reviewLoading = ref(false)
const reviewHasMore = ref(false)
const REVIEW_PAGE_SIZE = 10

const specSaveKey = computed(() => `hm:sel:${route.params.id}`)

const specs = computed(() => g.value?.specs || [])
const skus = computed(() => g.value?.skus || [])
const cover = computed(() => mediaUrl(g.value?.gallery?.[0] || g.value?.picUrl || ''))
const tags = computed(() => (g.value?.tags || []).slice(0, 6))
const emoji = computed(() => ['🛍️', '📦', '🎁', '🧺'][Number(g.value?.id || 0) % 4])

const detailTabs = computed(() => [
  { key: 'detail', label: '商品详情' },
  { key: 'params', label: '参数' },
  { key: 'reviews', label: `评价(${commentTotal.value})` },
])

/** sel 里可能存 value 对象或（历史数据/外部写入的）字符串，统一解析成 { valueId, value } 形态再算 SKU */
function normSel(v, dimName) {
  if (!v) return null
  if (typeof v !== 'string') return v
  const group = (specs.value || []).find((sp) => sp.name === dimName)
  return (group?.values || []).find((x) => x.value === v) || { valueId: v, value: v }
}

const selectedValues = computed(() =>
  Object.entries(sel)
    .filter(([, v]) => v)
    .map(([k, v]) => normSel(v, k))
    .filter(Boolean)
)

const sku = computed(() => {
  if (!skus.value.length) return null
  if (selectedValues.value.length !== specs.value.length) return null
  const hit = skus.value.find((s) => {
    const ids = (s.specValueIds || []).map(String)
    if (ids.length !== selectedValues.value.length) return false
    return selectedValues.value.every((v) => ids.includes(String(v.valueId)))
  })
  return hit || null
})

const priceCent = computed(() => {
  if (sku.value) return Number(sku.value.price || 0)
  if (g.value?.price != null) return Number(g.value.price)
  return Number(g.value?.minPrice || 0)
})

const maxQty = computed(() => Math.max(1, Number(sku.value?.sellable ?? sku.value?.number ?? 1)))

const rateAvg = computed(() => {
  if (!reviews.value.length) return '—'
  const sum = reviews.value.reduce((a, r) => a + Number(r.star || 5), 0)
  return (sum / reviews.value.length).toFixed(1)
})

function optEnabled(dimName, opt) {
  const candidates = skus.value.filter((s) => (s.specValueIds || []).map(String).includes(String(opt.valueId)))
  if (!candidates.length) return false
  // 与已选的其他维度兼容且仍有可售库存才可选
  const others = Object.entries(sel)
    .filter(([k, v]) => k !== dimName && v)
    .map(([k, v]) => normSel(v, k))
  return candidates.some((s) => {
    const ids = (s.specValueIds || []).map(String)
    const compatible = others.every((v) => ids.includes(String(v.valueId)) || ids.includes(String(v.value)))
    return compatible && Number(s.sellable ?? s.number ?? 0) > 0
  })
}

function isPicked(dimName, v) {
  const cur = sel[dimName]
  if (!cur) return false
  if (typeof cur === 'string') return cur === v.value
  return String(cur.valueId) === String(v.valueId) || cur.value === v.value
}

function pickSpec(dimName, opt) {
  const sameValue = isPicked(dimName, opt)
  if (sameValue) {
    delete sel[dimName]
    qty.value = 1
    return
  }
  if (!optEnabled(dimName, opt)) {
    const inStock = skus.value.some(
      (s) => (s.specValueIds || []).map(String).includes(String(opt.valueId)) && Number(s.sellable ?? 0) > 0
    )
    toast(inStock ? `已切换为「${opt.value}」：原已选规格没有这个组合，已自动取消` : `「${opt.value}」已售罄，暂无可售库存（BR-GOODS-05）`, 'err')
    // 自动清理冲突维度
    if (inStock) {
      for (const k of Object.keys(sel)) {
        if (k !== dimName) delete sel[k]
      }
      sel[dimName] = opt.value
    }
    return
  }
  sel[dimName] = opt.value
  qty.value = 1
}

function pickedSku() {
  if (!sku.value) {
    toast('请先选全规格', 'err')
    return null
  }
  if (Number(sku.value.sellable ?? sku.value.number ?? 0) <= 0) {
    toast('该规格暂时缺货', 'err')
    return null
  }
  return sku.value
}

async function addCart() {
  const s = pickedSku()
  if (!s) return
  const n = Math.min(qty.value, maxQty.value)
  adding.value = true
  try {
    await cartAdd({ goodsId: Number(g.value.id), specId: Number(s.id), number: n })
    toast('已加入购物车', 'ok')
    appStore.refreshCartCount()
  } catch {
    /* request 层已提示 */
  } finally {
    adding.value = false
  }
}

function buyNow() {
  const s = pickedSku()
  if (!s) return
  const n = Math.min(qty.value, maxQty.value)
  setBuyNow({
    productId: String(s.id),
    goodsId: String(g.value.id),
    name: g.value.name,
    specText: s.specText || '',
    price: Number(s.price || priceCent.value),
    number: n,
    picUrl: g.value.picUrl || g.value.gallery?.[0] || '',
    availableStock: Number(s.sellable ?? s.number ?? 0),
  })
  router.push({ path: '/checkout', query: { mode: 'buynow' } })
}

/** BR-FAV-01/02：未登录跳登录并回跳当前商品；已登录调 collectAdd。
 *  §6.7 决定进入页面不预查收藏态（后端无法按商品查），按钮初始「收藏」，点击后按返回的 collected 校正。 */
async function toggleCollect() {
  if (collectBusy.value || !g.value) return
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  collectBusy.value = true
  try {
    const data = await collectAdd({ goodsId: Number(g.value.id), type: collected.value ? 1 : 0 })
    collected.value = data?.collected ?? !collected.value
    toast(collected.value ? '已收藏' : '已取消收藏', 'ok')
  } catch {
    /* request 层已提示 */
  } finally {
    collectBusy.value = false
  }
}

function askAi() {
  router.push({ path: '/assistant', query: { ask: '这件商品' } })
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

/**
 * CI-35：只返回未隐藏评价（status=0），total 同口径；分页 1/20/上限 100。
 * 评价数随「加载更多」保持为后端 total，隐藏后自然回落（BR-CM-04）。
 */
async function loadReviews(goodsId, reset = true) {
  if (reviewLoading.value) return
  reviewLoading.value = true
  const page = reset ? 1 : reviewPage.value + 1
  try {
    const data = await commentList({ goodsId, pageNum: page, pageSize: REVIEW_PAGE_SIZE })
    const rows = data?.list || (Array.isArray(data) ? data : [])
    reviews.value = reset ? rows : reviews.value.concat(rows)
    reviewPage.value = page
    commentTotal.value = Number(data?.total ?? reviews.value.length)
    reviewHasMore.value = reviews.value.length < commentTotal.value && rows.length > 0
  } catch {
    if (reset) {
      reviews.value = []
      reviewHasMore.value = false
    }
  } finally {
    reviewLoading.value = false
  }
}

onMounted(async () => {
  const goodsId = route.params.id
  try {
    const data = await goodsDetail({ goodsId })
    g.value = data
    commentTotal.value = Number(data?.commentCount || 0)
    // 默认单规格自动选中（存整个 value 对象：sku 匹配用的是 valueId，存字符串会导致选不中 SKU）
    if ((data?.specs || []).length === 1 && data.specs[0].values?.length === 1) {
      sel[data.specs[0].name] = data.specs[0].values[0]
    }
    loadReviews(goodsId)
  } catch (e) {
    if (String(e?.code || '') === 'GOODS_OFF_SALE' || e?.code === 404) offSale.value = true
    g.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* 收藏态：AppIcon 的 svg 默认 fill="none"，CSS 可覆盖该 presentation attribute 变实心星 */
:deep(.fav-on) {
  fill: currentColor;
}
</style>