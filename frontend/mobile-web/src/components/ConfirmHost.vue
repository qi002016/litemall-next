<!-- 全局确认弹层：对齐 prototype/assets/ui.js 的 UI.confirm -->
<template>
  <teleport to=".app-shell">
    <div v-if="confirmState.visible" class="overlay mid" @click.self="settleConfirm(false)">
      <div class="sheet" role="dialog">
        <div class="sheet-hd">
          <div class="t">{{ confirmState.title }}</div>
          <div class="x" @click="settleConfirm(false)">✕</div>
        </div>
        <div class="sheet-bd">
          <div class="small" style="line-height: 1.8">{{ confirmState.body }}</div>
        </div>
        <div class="sheet-ft">
          <button type="button" class="btn" @click="settleConfirm(false)">取消</button>
          <button type="button" class="btn" :class="confirmState.danger ? 'danger' : 'primary'" @click="settleConfirm(true)">
            {{ confirmState.okText }}
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { confirmState, settleConfirm } from '@/utils/confirm'
</script>