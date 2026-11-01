<template>
  <div id="toast-wrap">
    <transition-group name="t">
      <div v-for="t in toastState.list" :key="t.id" class="toast" :class="t.type">
        {{ t.msg }}
      </div>
    </transition-group>
  </div>
</template>

<script setup>
import { toastState } from '@/utils/toast'
</script>

<style scoped>
.t-enter-active,
.t-leave-active {
  transition: opacity 0.25s, transform 0.25s;
}
.t-enter-from,
.t-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>