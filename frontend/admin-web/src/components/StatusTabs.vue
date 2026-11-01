<template>
  <div class="seg" role="tablist">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      role="tab"
      :aria-selected="modelValue === opt.value"
      :class="{ on: modelValue === opt.value }"
      @click="select(opt.value)"
    >
      {{ opt.label }}<span v-if="opt.count != null" class="cnt">{{ opt.count }}</span>
    </button>
  </div>
</template>

<script setup>
defineProps({
  options: { type: Array, required: true },
  modelValue: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'change'])

const select = (value) => {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style scoped>
.cnt {
  margin-left: 4px;
  font-size: 11px;
  color: var(--text-3);
}
</style>