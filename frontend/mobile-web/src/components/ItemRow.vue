<!-- 订单行 / 售后行：对齐 prototype itemRow（缩略图 + 名称 + 规格 + 单价 × 数量） -->
<template>
  <div class="row top" style="gap: 10px; padding: 8px 0">
    <div class="x-thumb sm" :class="{ 'has-img': !!pic }">
      <img v-if="pic" :src="pic" :alt="name" />
      <span v-else style="font-size: 20px; opacity: 0.6">📦</span>
    </div>
    <div class="grow">
      <div class="small bold clamp2">{{ name }}</div>
      <div class="tiny dim mt6">
        {{ line.specText || line.specifications?.join(' / ') || '' }}
        <span v-if="refundedQty > 0" class="tag gray" style="margin-left: 4px">已退款 {{ refundedQty }} 件</span>
        <span v-if="line.refundStatusText" class="tag warn" style="margin-left: 4px">{{ line.refundStatusText }}</span>
        <span v-if="line.commentStatus > 0" class="tag ok" style="margin-left: 4px">已评价</span>
      </div>
    </div>
    <div style="text-align: right; flex: none">
      <PriceText :cent="line.actualAmount ?? line.price" />
      <div class="tiny dim">×{{ line.number ?? line.qty }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PriceText from './PriceText.vue'
import { mediaUrl } from '@/utils/media'

const props = defineProps({
  line: { type: Object, required: true },
})

const name = computed(() => props.line.goodsName || props.line.name || '')
const pic = computed(() => mediaUrl(props.line.picUrl || props.line.pic || ''))
const refundedQty = computed(() => Number(props.line.refundedNumber || props.line.refundedQty || 0))
</script>