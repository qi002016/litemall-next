<!-- 注册：用户名 + 密码 + 手机号 + 验证码（POST /app/auth/register，验证码 POST /app/auth/captcha） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">注册</div>
      <div style="width: 30px" />
    </div>

    <div class="c-body login-wrap">
      <div class="login-brand">
        <div class="logo">满</div>
        <div class="nm">创建账号</div>
        <div class="sub">R1 演示版 · 注册后自动登录</div>
      </div>

      <div class="field">
        <div class="label">用户名</div>
        <input v-model="username" class="input" placeholder="请输入用户名" autocomplete="username" />
      </div>
      <div class="field">
        <div class="label">密码</div>
        <input v-model="password" class="input" type="password" placeholder="请输入密码" autocomplete="new-password" />
      </div>
      <div class="field">
        <div class="label">手机号</div>
        <input v-model="mobile" class="input" maxlength="11" inputmode="numeric" placeholder="11 位手机号" />
      </div>
      <div class="field">
        <div class="label">验证码</div>
        <div class="row" style="gap: 8px">
          <input v-model="code" class="input" style="flex: 1" placeholder="短信验证码" />
          <button type="button" class="btn ghost" :disabled="sending" @click="sendCode">
            {{ sending ? '发送中…' : '获取验证码' }}
          </button>
        </div>
      </div>

      <button type="button" class="btn primary block lg mt16" :disabled="submitting" @click="submit">
        {{ submitting ? '注册中…' : '注册并登录' }}
      </button>

      <div class="row between mt12">
        <span class="tiny dim" @click="router.replace('/login')">已有账号？去登录</span>
        <span class="tiny dim" @click="router.replace('/home')">先逛逛</span>
      </div>

      <div class="alert mt16">
        <AppIcon name="warn" :size="15" />
        <div>演示环境验证码由后端直接返回，不会真的发短信（CI-115）。</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { register, sendCaptcha } from '@/api/auth'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { toast } from '@/utils/toast'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const username = ref('')
const password = ref('')
const mobile = ref('')
const code = ref('')
const sending = ref(false)
const submitting = ref(false)

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) return toast('请填写正确的手机号', 'err')
  sending.value = true
  try {
    const data = await sendCaptcha({ phone: mobile.value, type: 'register' })
    if (data) {
      code.value = String(data)
      toast(`验证码已生成（演示）：${data}`, 'ok')
    } else {
      toast('验证码已发送', 'ok')
    }
  } catch {
    /* request 层已 toast */
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (!username.value.trim()) return toast('请输入用户名', 'err')
  if (!password.value) return toast('请输入密码', 'err')
  if (!/^1[3-9]\d{9}$/.test(mobile.value)) return toast('请填写正确的手机号', 'err')
  if (!code.value.trim()) return toast('请填写验证码', 'err')
  submitting.value = true
  try {
    const data = await register({
      username: username.value.trim(),
      password: password.value,
      mobile: mobile.value,
      code: code.value.trim(),
    })
    userStore.setLogin({ token: data?.token, userId: data?.userId, username: data?.username || username.value.trim() })
    toast('注册成功', 'ok')
    appStore.refreshCartCount()
    appStore.refreshUnread()
    router.replace('/me')
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