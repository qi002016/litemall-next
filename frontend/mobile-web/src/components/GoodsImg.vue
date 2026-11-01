<!-- 商品图：有 picUrl 用图片，无图时用「渐变底 + 品类插图」占位（对齐原型 .x-gpic 的视觉基调） -->
<template>
  <div class="x-gpic" :class="{ 'has-img': hasImg }" :style="{ background: bg }">
    <img v-if="hasImg" :src="src" :alt="alt" loading="lazy" @error="broken = true" />
    <span v-else class="ph">{{ emoji }}</span>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { mediaUrl } from '@/utils/media'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  seed: { type: [String, Number], default: '' },
})

const broken = ref(false)
watch(
  () => props.src,
  () => (broken.value = false)
)

const EMOJIS = ['👜', '👕', '👖', '👟', '🧥', '⌚', '🎧', '🧴', '🍵', '🧸', '📚', '🍪']

const key = computed(() => String(props.seed || props.alt || props.src || '0'))
const idx = computed(() => {
  let h = 0
  for (let i = 0; i < key.value.length; i++) h = (h * 31 + key.value.charCodeAt(i)) % 997
  return h
})
const emoji = computed(() => EMOJIS[idx.value % EMOJIS.length])
const hue = computed(() => (idx.value * 37) % 360)
const bg = computed(() => `linear-gradient(150deg, hsl(${hue.value} 82% 92%) 0%, #f7f8fa 74%)`)
const hasImg = computed(() => !!props.src && !broken.value)
const src = computed(() => mediaUrl(props.src))
</script>