<!-- 购物车：对齐 prototype/assets/app.js cart（1446-1580）；失效行不可勾选、不参与结算（BR-CART-05） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div style="width: 30px" />
      <div class="title">购物车</div>
      <div class="right">
        <span v-if="lines.length" @click="clearAll">清空</span>
      </div>
    </div>

    <div v-if="lines.length" class="c-body">
      <div class="card pad row between" style="margin-bottom: 10px">
        <div class="row" style="gap: 8px">
          <div class="cbx" :class="{ on: allChecked }" @click="toggleAll" />
          <span class="small bold">小满商城 · 自营</span>
        </div>
        <span class="tiny dim">{{ goodLines.length }} 种在售</span>
      </div>

      <div v-for="l in goodLines" :key="l.key" class="card pad" style="margin-bottom: 8px">
        <div class="row top">
          <div class="cbx" :class="{ on: l.checked }" @click="toggle(l)" />
          <div class="x-thumb sm" :class="{ 'has-img': !!l.pic }">
            <img v-if="l.pic" :src="l.pic" :alt="l.name" />
            <template v-else>{{ emojiOf(l) }}</template>
          </div>
          <div class="grow">
            <div class="small bold clamp2">{{ l.name }}</div>
            <div class="tiny dim mt6">{{ l.specText }}</div>
            <div class="row between mt8">
              <PriceText :cent="l.price" />
              <div class="row" style="gap: 8px">
                <QtyStepper :model-value="l.number" :min="1" :max="maxOf(l)" @change="(v) => changeQty(l, v)" />
                <span class="tiny dim" @click="removeLine(l)">删除</span>
              </div>
            </div>
            <div v-if="overSold(l)" class="tiny" style="color: var(--danger); margin-top: 4px">
              可售仅剩 {{ stockOf(l) }} 件，结算会被拦截
            </div>
            <div v-else-if="l.priceChanged" class="tiny" style="color: var(--warn); margin-top: 4px">价格有变动，以当前价为准</div>
          </div>
        </div>
      </div>

      <template v-if="badLines.length">
        <div class="row between mt12" style="margin-bottom: 8px">
          <div class="small bold">失效商品（{{ badLines.length }}）</div>
          <div class="tiny dim" @click="clearBad">清空失效</div>
        </div>
        <div v-for="l in badLines" :key="l.key" class="card pad" style="margin-bottom: 8px; opacity: 0.72">
          <div class="row top">
            <div class="cbx disabled" />
            <div class="x-thumb sm" :class="{ 'has-img': !!l.pic }">
              <img v-if="l.pic" :src="l.pic" :alt="l.name" />
              <template v-else>{{ emojiOf(l) }}</template>
            </div>
            <div class="grow">
              <div class="small bold clamp2">{{ l.name }}</div>
              <div class="tiny dim mt6">{{ l.specText }}</div>
              <div class="row between mt8">
                <span class="tag gray">{{ l.invalidReason || '已失效' }}</span>
                <span class="tiny dim" @click="removeLine(l)">删除</span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <div class="tiny dim center" style="padding: 10px 0">失效行不可勾选、不参与结算（BR-CART-05）</div>
    </div>

    <div v-else-if="loading" class="c-body"><div class="loading-more">加载中…</div></div>

    <div v-else class="c-body">
      <EmptyState icon="cart" title="购物车还是空的" hint="挑几件喜欢的商品加进来吧" action="去逛逛" @action="router.push('/home')" />
    </div>

    <div v-if="lines.length" class="c-actionbar">
      <div class="cbx" :class="{ on: allChecked }" @click="toggleAll" />
      <span class="small">全选</span>
      <div class="sum">
        合计 <PriceText :cent="totalCent" />
        <span class="tiny dim">（已选 {{ checkedLines.length }} 种）</span>
      </div>
      <button type="button" class="btn primary" :disabled="!checkedLines.length" @click="checkout">去结算</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import QtyStepper from '@/components/QtyStepper.vue'
import { cartDelete, cartList, cartUpdate } from '@/api/cart'
import { useAppStore } from '@/store/app'
import { clearBuyNow } from '@/store/checkout'
import { toast } from '@/utils/toast'
import { mediaUrl } from '@/utils/media'
import { confirm } from '@/utils/confirm'

const router = useRouter()
const appStore = useAppStore()

const lines = ref([])
const loading = ref(true)

function normalize(raw) {
  return (raw || []).map((it, i) => {
    const goodsId = it.goodsId ?? it.goods_id ?? ''
    const specId = it.productId ?? it.specId ?? it.product_id ?? ''
    const cartId = it.id ?? it.cartId ?? `${goodsId}:${specId || i}`
    const invalid = it.invalid === true || (!!it.invalidReason && String(it.invalidReason).trim() !== '')
    return {
      key: `${cartId}:${i}`,
      cartId: String(cartId),
      goodsId,
      specId,
      name: it.goodsName || it.name || '',
      specText: it.specText || it.spec || it.specifications || '默认规格',
      price: Number(it.price ?? it.minPrice ?? 0),
      number: Number(it.number ?? it.qty ?? 1),
      checked: it.checked !== false,
      invalid,
      invalidReason: it.invalidReason || '',
      priceChanged: it.priceChanged === true,
      pic: mediaUrl(it.picUrl || it.goodsPic || it.pic || ''),
      sellable: it.sellable != null ? Number(it.sellable) : Number(it.stock ?? it.availableStock ?? 9999),
    }
  })
}

const goodLines = computed(() => lines.value.filter((l) => !l.invalid))
const badLines = computed(() => lines.value.filter((l) => l.invalid))
const checkedLines = computed(() => goodLines.value.filter((l) => l.checked))
const allChecked = computed(() => goodLines.value.length > 0 && goodLines.value.every((l) => l.checked))
const totalCent = computed(() =>
  checkedLines.value.reduce((sum, l) => sum + Number(l.price || 0) * Math.min(l.number, stockOf(l)), 0)
)

function stockOf(l) {
  return Math.max(1, Number(l.sellable ?? 1))
}
function maxOf(l) {
  return Math.min(999, stockOf(l))
}
function overSold(l) {
  return l.number > stockOf(l)
}
function emojiOf(l) {
  return ['🛍️', '📦', '🎁', '🧺'][Number(l.goodsId || 0) % 4]
}

async function load() {
  loading.value = true
  try {
    const data = await cartList()
    const rows = Array.isArray(data) ? data : data?.list || []
    lines.value = normalize(rows)
    appStore.refreshCartCount()
  } catch {
    lines.value = []
  } finally {
    loading.value = false
  }
}

async function persistChecked(l, checked) {
  try {
    await cartUpdate({ goodsId: Number(l.goodsId), specId: Number(l.specId), number: l.number, checked })
  } catch {
    /* 勾选是本地优先，后端失败不阻断 */
  }
}

function toggle(l) {
  l.checked = !l.checked
  persistChecked(l, l.checked)
}

function toggleAll() {
  const next = !allChecked.value
  for (const l of goodLines.value) {
    l.checked = next
    persistChecked(l, next)
  }
}

async function changeQty(l, value) {
  const n = Math.max(1, Math.min(999, Number(value) || 1))
  if (n > stockOf(l)) {
    toast(`该规格仅剩 ${stockOf(l)} 件`, 'err')
    return
  }
  const prev = l.number
  l.number = n
  try {
    await cartUpdate({ goodsId: Number(l.goodsId), specId: Number(l.specId), number: n, checked: l.checked })
  } catch {
    l.number = prev
  }
}

async function removeLine(l) {
  try {
    await cartDelete({ goodsId: Number(l.goodsId), specId: Number(l.specId) })
    lines.value = lines.value.filter((x) => x.key !== l.key)
    toast('已删除该行', 'ok')
    appStore.refreshCartCount()
  } catch {
    /* request 层已提示 */
  }
}

async function clearBad() {
  const rows = badLines.value.slice()
  for (const l of rows) {
    try {
      await cartDelete({ goodsId: Number(l.goodsId), specId: Number(l.specId) })
      lines.value = lines.value.filter((x) => x.key !== l.key)
    } catch {
      /* 单行失败继续 */
    }
  }
  toast('已清空失效商品', 'ok')
  appStore.refreshCartCount()
}

async function clearAll() {
  const ok = await confirm({ title: '清空购物车', body: '将删除购物车内全部商品行。', okText: '清空', danger: true })
  if (!ok) return
  const rows = lines.value.slice()
  for (const l of rows) {
    try {
      await cartDelete({ goodsId: Number(l.goodsId), specId: Number(l.specId) })
    } catch {
      /* 单行失败继续 */
    }
  }
  lines.value = []
  toast('购物车已清空', 'ok')
  appStore.refreshCartCount()
}

function checkout() {
  if (!checkedLines.value.length) {
    toast('请先勾选要结算的商品', 'err')
    return
  }
  clearBuyNow()
  const cartIds = checkedLines.value.map((l) => l.cartId)
  sessionStorage.setItem('hm_cart_ids', JSON.stringify(cartIds))
  router.push({ path: '/checkout', query: { mode: 'cart' } })
}

onMounted(load)
</script>