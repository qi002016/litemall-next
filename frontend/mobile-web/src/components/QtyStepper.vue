<!-- 数量步进器：与 prototype/assets/ui.js 的 stepper 一致，qty<=min / >=max 时按钮禁用 -->
<template>
  <div class="stepper">
    <button type="button" :disabled="modelValue <= min" @click="step(-1)">
      <AppIcon name="minus" :size="14" />
    </button>
    <span class="n">{{ modelValue }}</span>
    <button type="button" :disabled="modelValue >= max" @click="step(1)">
      <AppIcon name="plus" :size="14" />
    </button>
  </div>
</template>

<script setup>
import AppIcon from './AppIcon.vue'

const props = defineProps({
  modelValue: { type: Number, default: 1 },
  min: { type: Number, default: 1 },
  max: { type: Number, default: 99 },
})
const emit = defineEmits(['update:modelValue', 'change'])

function step(d) {
  const next = Math.min(props.max, Math.max(props.min, (props.modelValue || 0) + d))
  if (next === props.modelValue) return
  emit('update:modelValue', next)
  emit('change', next)
}
</script>