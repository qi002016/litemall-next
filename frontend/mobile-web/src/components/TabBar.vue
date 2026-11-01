<!-- 底部四 Tab：首页 / 消息 / 购物车 / 我的（对齐 prototype 的 .c-tab） -->
<template>
  <nav class="c-tab">
    <a :class="{ on: active === 'home' }" @click="go('/home')">
      <AppIcon name="home" :size="21" />
      <span>首页</span>
    </a>
    <a :class="{ on: active === 'messages' }" @click="go('/messages')">
      <AppIcon name="comment" :size="21" />
      <span>消息</span>
      <i v-if="msgCount > 0" class="badge-dot">{{ msgCount > 99 ? '99+' : msgCount }}</i>
    </a>
    <a :class="{ on: active === 'cart' }" @click="go('/cart')">
      <AppIcon name="cart" :size="21" />
      <span>购物车</span>
      <i v-if="cartCount > 0" class="badge-dot">{{ cartCount > 99 ? '99+' : cartCount }}</i>
    </a>
    <a :class="{ on: active === 'me' }" @click="go('/me')">
      <AppIcon name="user" :size="21" />
      <span>我的</span>
    </a>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

defineProps({
  cartCount: { type: Number, default: 0 },
  msgCount: { type: Number, default: 0 },
})

const route = useRoute()
const router = useRouter()
const active = computed(() => route.meta.tab || '')

function go(path) {
  if (route.path !== path) router.push(path)
}
</script>