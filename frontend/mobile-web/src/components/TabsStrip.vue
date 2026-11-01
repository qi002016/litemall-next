<!-- 横向滚动 Tab：与 prototype/assets/ui.js 的 tabs 一致（items: {key,label,count?}） -->
<template>
  <div class="tabs-scroll">
    <button
      v-for="it in items"
      :key="it.key"
      type="button"
      :class="{ on: it.key === modelValue }"
      @click="$emit('update:modelValue', it.key)"
    >
      {{ it.label }}<span v-if="it.count !== undefined && it.count !== null" class="count"> {{ it.count }}</span>
    </button>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
  modelValue: { type: [String, Number], default: '' },
})
defineEmits(['update:modelValue'])
</script>