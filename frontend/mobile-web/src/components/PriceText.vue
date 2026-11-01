<!-- 价格：统一 .price 结构（.sym 为 ¥）；金额按整数分入参、固定两位小数展示（R2-08） -->
<template>
  <span class="price" :class="cls"><span class="sym">¥</span>{{ yuan2(cent) }}</span>
</template>

<script setup>
import { yuan2 } from '@/utils/format'

defineProps({
  cent: { type: [Number, String], default: 0 },
  cls: { type: String, default: '' },
})
</script>