<!-- 居中弹层：与 prototype/assets/ui.js 的 sheet 一致，外层用 .overlay.mid -->
<template>
  <teleport to=".app-shell">
    <div class="overlay" :class="{ mid }" @click.self="close">
      <div class="sheet" role="dialog">
        <div class="sheet-hd">
          <div class="t">{{ title }}</div>
          <div class="x" @click="close">✕</div>
        </div>
        <div class="sheet-bd">
          <slot />
        </div>
        <div v-if="actions.length" class="sheet-ft">
          <button
            v-for="(a, i) in actions"
            :key="i"
            type="button"
            class="btn"
            :class="a.type === 'primary' ? 'primary' : a.type === 'danger' ? 'danger' : ''"
            :disabled="a.disabled"
            @click="onAction(a)"
          >
            {{ a.text }}
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
const props = defineProps({
  title: { type: String, default: '' },
  actions: { type: Array, default: () => [] },
  mid: { type: Boolean, default: true },
})
const emit = defineEmits(['close'])

function close() {
  emit('close')
}

function onAction(a) {
  if (a.onClick) a.onClick()
  if (a.keepOpen !== true) close()
}
</script>