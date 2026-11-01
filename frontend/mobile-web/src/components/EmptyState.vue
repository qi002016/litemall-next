<!-- 空态：与 prototype/assets/app.js 的 emp() 一致（图标用 AppIcon，不用 emoji） -->
<template>
  <div class="empty">
    <div class="ico" style="font-size: 0; line-height: 0; color: #c4cbd6">
      <AppIcon :name="iconName" :size="30" />
    </div>
    <div class="t">{{ title }}</div>
    <div v-if="hint" class="h">{{ hint }}</div>
    <div v-if="$slots.default || action" style="margin-top: 14px">
      <slot>
        <button v-if="action" type="button" class="btn primary" @click="$emit('action')">{{ action }}</button>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const EMP_ICONS = {
  cart: 'cart',
  bell: 'bell',
  search: 'search',
  box: 'box',
  list: 'list',
  star: 'star',
  comment: 'comment',
  money: 'money',
  grid: 'grid',
  user: 'user',
  truck: 'truck',
  location: 'location',
  tag: 'tag',
  clock: 'clock',
  warn: 'warn',
  check: 'check',
  refresh: 'refresh',
}

const props = defineProps({
  icon: { type: String, default: 'box' },
  title: { type: String, default: '暂无数据' },
  hint: { type: String, default: '' },
  action: { type: String, default: '' },
})
defineEmits(['action'])
const iconName = computed(() => EMP_ICONS[props.icon] || 'box')
</script>