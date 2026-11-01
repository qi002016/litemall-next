<!-- 确认订单：对齐 prototype/assets/app.js checkout（1587-1750）；金额一律整数分 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">确认订单</div>
      <div style="width: 30px" />
    </div>

    <div v-if="!hasPayload" class="c-body">
      <EmptyState
        icon="list"
        title="没有待结算的商品"
        hint="回购物车勾选商品，或直接「立即购买」"
        action="回购物车"
        @action="router.replace('/cart')"
      />
    </div>

    <template v-else>
      <div class="c-body">
        <div v-if="address" class="addr-card" @click="pickAddress">
          <div class="ic"><AppIcon name="location" :size="20" /></div>
          <div class="grow">
            <div class="line1">
              {{ address.name }} <span class="small muted">{{ address.tel }}</span>
              <span v-if="address.isDefault" class="tag">默认</span>
            </div>
            <div class="line2">{{ addressText(address) }}</div>
          </div>
          <div class="arrow"><AppIcon name="right" :size="16" /></div>
        </div>
        <div v-else class="alert warn">
          <AppIcon name="warn" :size="15" />
          <div class="grow">还没有收货地址，无法提交订单（BR-ORDER-01）。</div>
          <button type="button" class="btn sm" @click="pickAddress">去新增</button>
        </div>

        <div class="card pad" style="margin-top: 10px">
          <div class="row between small bold" style="margin-bottom: 2px">
            <span>小满商城 · 自营</span>
            <span class="tiny dim">{{ lines.length }} 种</span>
          </div>
          <div v-for="(l, i) in lines" :key="i" class="row top mt10">
            <div class="x-thumb sm" :class="{ 'has-img': !!l.pic }">
              <img v-if="l.pic" :src="l.pic" :alt="l.goodsName" />
              <template v-else>📦</template>
            </div>
            <div class="grow">
              <div class="small bold clamp2">{{ l.goodsName }}</div>
              <div class="tiny dim mt6">{{ l.specText }}</div>
              <div class="row between mt6">
                <PriceText :cent="l.price" />
                <span class="tiny dim">×{{ l.number }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="cell clickable" @click="openCouponSheet">
            <div class="label">优惠券</div>
            <div class="value">
              <template v-if="couponAmount > 0">
                <span style="color: var(--price); font-weight: 700">-{{ money(couponAmount) }}</span>
                {{ couponName }}
              </template>
              <span v-else-if="usableCoupons.length" style="color: var(--brand)">{{ usableCoupons.length }} 张可用 ›</span>
              <span v-else class="dim">无可用券 ›</span>
            </div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div class="cell">
            <div class="label">商品金额</div>
            <div class="value"><PriceText :cent="preview.goodsAmount" /></div>
          </div>
          <div class="cell">
            <div class="label">优惠</div>
            <div class="value">
              <span v-if="couponAmount > 0" style="color: var(--price)">-{{ money(couponAmount) }}</span>
              <span v-else>—</span>
            </div>
          </div>
          <div class="cell">
            <div class="label">运费</div>
            <div class="value"><PriceText :cent="preview.freightAmount" /> <span class="tiny dim">（本期固定 0，BR-ORDER-11）</span></div>
          </div>
          <div class="cell">
            <div class="label bold">应付</div>
            <div class="value"><PriceText :cent="payAmount" cls="price-lg" /></div>
          </div>
        </div>

        <div class="card pad mt10">
          <div class="small bold" style="margin-bottom: 6px">订单备注</div>
          <textarea
            v-model="message"
            class="textarea"
            maxlength="100"
            placeholder="给商家留言（选填，≤100 字）"
          />
        </div>

        <div class="tiny dim center" style="padding: 12px 0">
          提交后库存立即锁定，30 分钟内未支付自动关单（BR-ORDER-02/06）
        </div>
      </div>

      <div class="c-actionbar">
        <div class="sum">
          应付 <PriceText :cent="payAmount" />
          <div v-if="couponAmount > 0" class="tiny dim">已优惠 {{ money(couponAmount) }}</div>
        </div>
        <button type="button" class="btn primary" :disabled="!address || submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </template>

    <BottomSheet v-if="couponOpen" title="选择优惠券" @close="couponOpen = false">
      <div class="small bold" style="margin-bottom: 6px">可用券</div>
      <div v-if="!usableCoupons.length" class="tiny dim" style="padding: 8px 0">当前订单没有可用券（R1 领券在 R2 开放）</div>
      <div
        v-for="c in usableCoupons"
        :key="c.id"
        class="cell clickable"
        style="border-radius: 10px"
        @click="pickCoupon(c)"
      >
        <div class="grow">
          <div class="small bold">{{ c.name }}</div>
          <div class="tiny dim">满 {{ yuan(c.threshold || 0) }} 元可用 · {{ c.timeText || '有效期内' }}</div>
        </div>
        <div class="value" style="color: var(--price)">-{{ money(c.amount) }}</div>
      </div>
      <div class="x-hr"></div>
      <button type="button" class="btn block ghost" @click="clearCoupon">不使用优惠券</button>
    </BottomSheet>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import { orderConfirm, orderIdemToken, orderSubmit } from '@/api/order'
import { couponUsable } from '@/api/market'
import { addressList } from '@/api/user'
import { buyNowState, clearBuyNow } from '@/store/checkout'
import { toast } from '@/utils/toast'
import { money, uuid, yuan } from '@/utils/format'
import { mediaUrl } from '@/utils/media'

const route = useRoute()
const router = useRouter()

const address = ref(null)
const lines = ref([])
const preview = ref({ goodsAmount: 0, freightAmount: 0, couponAmount: 0, orderAmount: 0 })
const coupons = ref([])
const couponId = ref('')
const couponOpen = ref(false)
const message = ref('')
const submitting = ref(false)

const mode = computed(() => (route.query.mode === 'buynow' || buyNowState.value ? 'buynow' : 'cart'))
const cartIds = computed(() => {
  try {
    const raw = sessionStorage.getItem('hm_cart_ids')
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
})
const hasPayload = computed(() => (mode.value === 'buynow' ? !!buyNowState.value : cartIds.value.length > 0))

const couponAmount = computed(() => Number(preview.value.couponAmount || 0))
const payAmount = computed(() => {
  if (preview.value.orderAmount != null) return Number(preview.value.orderAmount)
  return Math.max(0, Number(preview.value.goodsAmount || 0) + Number(preview.value.freightAmount || 0) - couponAmount.value)
})
const couponName = computed(() => coupons.value.find((c) => String(c.id) === String(couponId.value))?.name || '')
const usableCoupons = computed(() => {
  const goods = Number(preview.value.goodsAmount || 0)
  return coupons.value.filter((c) => {
    const threshold = Number(c.threshold || c.minPoint || 0)
    return goods >= threshold
  })
})

function addressText(a) {
  return [a.province, a.city, a.county, a.addressDetail || a.address].filter(Boolean).join(' ')
}

function payload() {
  const p = { addressId: address.value?.id }
  if (mode.value === 'buynow') {
    p.buyNow = { productId: String(buyNowState.value.productId), number: Number(buyNowState.value.number || 1) }
  } else {
    p.cartIds = cartIds.value
  }
  if (couponId.value) p.couponId = couponId.value
  return p
}

async function loadPreview() {
  if (!hasPayload.value || !address.value) return
  try {
    const data = await orderConfirm(payload())
    preview.value = data || {}
    lines.value = (data?.lines || []).map((l) => ({
      ...l,
      price: Number(l.price ?? 0),
      number: Number(l.number ?? 1),
      pic: mediaUrl(l.picUrl || ''),
    }))
    if (!lines.value.length && mode.value === 'buynow') {
      lines.value = [
        {
          goodsName: buyNowState.value.name,
          specText: buyNowState.value.specText,
          price: buyNowState.value.price,
          number: buyNowState.value.number,
          pic: mediaUrl(buyNowState.value.picUrl || ''),
        },
      ]
    }
  } catch {
    /* request 层已提示 */
  }
}

async function loadCoupons() {
  try {
    const data = await couponUsable()
    coupons.value = data?.list || (Array.isArray(data) ? data : [])
  } catch {
    coupons.value = []
  }
}

function openCouponSheet() {
  couponOpen.value = true
}

async function pickCoupon(c) {
  couponId.value = String(c.id)
  couponOpen.value = false
  await loadPreview()
}

async function clearCoupon() {
  couponId.value = ''
  couponOpen.value = false
  await loadPreview()
}

function pickAddress() {
  router.push({ path: '/address', query: { pick: '1' } })
}

/**
 * 幂等 token：07 契约为客户端 UUID；当前后端仍要求 CI-81 服务端签发，
 * 因此先申请服务端 token，接口不可用（404/异常）时退回客户端 UUID。
 */
async function idemToken() {
  try {
    const data = await orderIdemToken()
    const token = data?.idemToken || data?.token
    if (token) return token
  } catch {
    /* 忽略：契约路径下无此接口 */
  }
  return uuid()
}

async function submit() {
  if (!address.value) {
    toast('请先选择收货地址', 'err')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const data = await orderSubmit({ ...payload(), idemToken: await idemToken(), message: message.value || undefined })
    clearBuyNow()
    sessionStorage.removeItem('hm_cart_ids')
    toast('下单成功，请在 30 分钟内完成支付', 'ok')
    router.replace(`/pay/${data.orderId}`)
  } catch {
    /* request 层已提示 */
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/cart')
}

onMounted(async () => {
  // 地址：优先 URL 指定，其次默认地址
  try {
    const data = await addressList()
    const rows = Array.isArray(data) ? data : data?.list || []
    const wantId = route.query.addressId
    address.value =
      (wantId && rows.find((a) => String(a.id) === String(wantId))) || rows.find((a) => a.isDefault) || rows[0] || null
  } catch {
    address.value = null
  }
  await loadPreview()
  await loadCoupons()
})
</script>