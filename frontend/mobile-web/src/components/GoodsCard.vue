<!-- 商品卡：首页 / 列表 / 搜索共用（对齐 prototype goodsCard 的结构与类名） -->
<template>
  <div class="x-gcard" :class="{ soldout }">
    <GoodsImg :src="g.picUrl || g.pic" :alt="g.name" :seed="g.id" />
    <div class="x-ginfo">
      <div class="x-gnm clamp2">{{ g.name }}</div>
      <div class="x-gmeta">
        <PriceText :cent="displayPrice" />
        <span class="sales">已售 {{ g.sales ?? g.sold ?? 0 }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import GoodsImg from './GoodsImg.vue'
import PriceText from './PriceText.vue'

const props = defineProps({
  g: { type: Object, required: true },
})

const soldout = computed(() => props.g.soldOut === true || Number(props.g.totalStock) <= 0)
const displayPrice = computed(() => {
  const g = props.g
  if (g.displayPrice != null) return Number(g.displayPrice)
  if (g.minPrice != null) return Number(g.minPrice)
  return Number(g.price) || 0
})
</script>