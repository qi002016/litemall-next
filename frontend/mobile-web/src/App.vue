<template>
  <div class="app-shell">
    <div ref="scroller" class="app-scroll">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </div>
    <TabBar v-if="showTab" :cart-count="cartCount" :msg-count="msgCount" />
    <ToastHost />
    <ConfirmHost />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from '@/components/TabBar.vue'
import ConfirmHost from '@/components/ConfirmHost.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useAppStore } from '@/store/app'

const route = useRoute()
const app = useAppStore()
const scroller = ref(null)

const showTab = computed(() => !!route.meta.tab)
const cartCount = computed(() => app.cartCount)
const msgCount = computed(() => app.unreadCount)

// 路由切换回到顶部（新页面从顶部开始）
watch(
  () => route.fullPath,
  () => {
    if (scroller.value) scroller.value.scrollTop = 0
  }
)
</script>