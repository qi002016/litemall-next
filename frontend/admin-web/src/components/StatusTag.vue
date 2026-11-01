<template>
  <span class="tag" :class="tone">{{ text }}</span>
</template>

<script setup>
defineProps({
  text: { type: String, default: '—' },
  tone: { type: String, default: 'gray' },
})
</script>