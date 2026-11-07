<!-- AI 会话卡片（T9）：按 type 渲染 product/order/coupon/aftersale；未知 type 忽略，cards 为空不渲染 -->
<template>
  <div v-if="known.length" class="as-cards">
    <template v-for="(c, ci) in known" :key="ci">
      <!-- 商品：横向卡片，点击进商品详情 -->
      <div v-if="c.type === 'product'" class="as-card">
        <div class="as-card-hd">为你找到的商品</div>
        <div v-for="(it, i) in c.items || []" :key="i" class="as-line clickable" @click="goGoods(it.id)">
          <GoodsImg :src="it.img" :alt="it.name" :seed="it.id" />
          <div class="as-line-info">
            <div class="clamp2 as-line-name">{{ it.name }}</div>
            <div v-if="it.brief" class="tiny dim ellipsis">{{ it.brief }}</div>
            <PriceText :cent="it.price" cls="as-price" />
          </div>
          <AppIcon name="right" :size="14" />
        </div>
      </div>

      <!-- 订单：点击进订单详情 -->
      <div v-else-if="c.type === 'order' && c.order" class="as-card">
        <div class="as-card-hd">订单信息</div>
        <div class="as-line clickable" @click="goOrder(c.order)">
          <div class="as-order-ico"><AppIcon name="box" :size="20" /></div>
          <div class="as-line-info">
            <div class="small bold">{{ c.order.statusText || '订单' }}</div>
            <div class="tiny dim ellipsis">{{ c.order.orderSn }}</div>
            <div class="tiny dim ellipsis">{{ c.order.goodsName }}</div>
          </div>
          <PriceText :cent="c.order.total" cls="as-price" />
        </div>
        <div v-if="c.order.time" class="tiny dim as-card-ft">{{ c.order.time }}</div>
      </div>

      <!-- 优惠券 -->
      <div v-else-if="c.type === 'coupon'" class="as-card">
        <div class="as-card-hd">优惠券</div>
        <div v-for="(it, i) in c.items || []" :key="i" class="as-line">
          <div class="as-coupon-ico"><AppIcon name="tag" :size="20" /></div>
          <div class="as-line-info">
            <div class="small bold">{{ it.name }}</div>
            <div class="tiny dim">{{ it.desc }}</div>
            <div class="tiny dim">有效期至 {{ it.expire || '—' }}</div>
          </div>
          <span class="as-amount">{{ money2(it.value) }}</span>
        </div>
      </div>

      <!-- 售后 -->
      <div v-else-if="c.type === 'aftersale'" class="as-card">
        <div class="as-card-hd">售后进度</div>
        <div v-for="(it, i) in c.items || []" :key="i" class="as-line">
          <div class="as-order-ico"><AppIcon name="refresh" :size="20" /></div>
          <div class="as-line-info">
            <div class="small bold">{{ it.statusText || '售后申请' }}</div>
            <div class="tiny dim ellipsis">{{ it.goodsName }}</div>
            <div class="tiny dim">订单号 {{ it.orderSn || '—' }}</div>
          </div>
          <span class="as-amount">{{ money2(it.amount) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import GoodsImg from '@/components/GoodsImg.vue'
import PriceText from '@/components/PriceText.vue'
import { money2 } from '@/utils/format'

const props = defineProps({
  cards: { type: Array, default: () => [] },
})

const router = useRouter()

const KNOWN = ['product', 'order', 'coupon', 'aftersale']
const known = computed(() => (props.cards || []).filter((c) => c && KNOWN.includes(c.type)))

function goGoods(id) {
  if (id == null || id === '') return
  router.push('/goods/' + id)
}

function goOrder(o) {
  // §10.4 契约带 orderId；当前 agent 实现（mall_tools.py）尚未带出该字段，缺 id 时退到订单列表避免死链
  const id = o?.orderId ?? o?.id
  if (id != null && id !== '') router.push('/order/' + id)
  else router.push('/orders')
}
</script>

<style scoped>
.as-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.as-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
}
.as-card-hd {
  padding: 7px 10px;
  font-size: 11px;
  color: var(--text-3);
  background: #f7f8fa;
}
.as-line {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
}
.as-line + .as-line {
  border-top: 1px solid var(--line);
}
.as-line.clickable {
  cursor: pointer;
}
.as-line-info {
  flex: 1;
  min-width: 0;
}
.as-line-name {
  font-size: 12.5px;
  line-height: 1.4;
  color: #111;
}
.as-order-ico,
.as-coupon-ico {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
}
.as-coupon-ico {
  background: #fff1f0;
  color: #ff4d4f;
}
.as-cards :deep(.x-gpic) {
  width: 44px;
  height: 44px;
  padding: 0;
  border-radius: 8px;
  flex: none;
  overflow: hidden;
}
.as-cards :deep(.as-price) {
  color: var(--price);
  font-weight: 800;
  font-size: 13px;
}
.as-amount {
  flex: none;
  color: var(--price);
  font-weight: 800;
  font-size: 13px;
}
.as-card-ft {
  padding: 0 10px 8px;
}
</style>
