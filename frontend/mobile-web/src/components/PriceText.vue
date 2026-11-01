<!-- 价格：统一 .price 结构（.sym 为 ¥） -->
<template>
  <span class="price" :class="cls"><span class="sym">¥</span>{{ yuan(cent) }}</span>
</template>

<script setup>
import { yuan } from '@/utils/format'

defineProps({
  cent: { type: [Number, String], default: 0 },
  cls: { type: String, default: '' },
})
</script>