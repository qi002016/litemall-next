<!-- 登录：演示账号登录（POST /app/auth/login） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">登录</div>
      <div style="width: 30px" />
    </div>

    <div class="c-body login-wrap">
      <div class="login-brand">
        <div class="logo">满</div>
        <div class="nm">小满商城</div>
        <div class="sub">R1 演示版 · 登录后可使用购物车与订单</div>
      </div>

      <div class="field">
        <div class="label">用户名</div>
        <input v-model="username" class="input" placeholder="请输入用户名" autocomplete="username" />
      </div>
      <div class="field">
        <div class="label">密码</div>
        <input v-model="password" class="input" type="password" placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit" />
      </div>

      <button type="button" class="btn primary block lg mt16" :disabled="submitting" @click="submit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <div class="row between mt12">
        <span class="tiny dim" @click="router.push('/register')">还没有账号？去注册</span>
        <span class="tiny dim" @click="router.replace('/home')">先逛逛</span>
      </div>

      <div class="alert mt16">
        <AppIcon name="warn" :size="15" />
        <div>演示环境不校验真实身份；下单与支付均为模拟，不产生真实扣款（BR-PAY-01）。</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { login } from '@/api/auth'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)

async function submit() {
  if (!username.value.trim()) return toast('请输入用户名', 'err')
  if (!password.value) return toast('请输入密码', 'err')
  submitting.value = true
  try {
    const data = await login({ username: username.value.trim(), password: password.value })
    userStore.setLogin({ token: data?.token, userId: data?.userId, username: data?.username || username.value.trim() })
    toast('登录成功', 'ok')
    appStore.refreshCartCount()
    appStore.refreshUnread()
    const redirect = String(route.query.redirect || '/me')
    router.replace(redirect)
  } catch {
    /* request 层已 toast */
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/home')
}
</script>