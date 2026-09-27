<template>
  <div class="register">
    <van-nav-bar title="注册账号" left-arrow @click-left="$router.back()" />
    <van-form @submit="onSubmit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          label="用户名"
          placeholder="请输入用户名"
          :rules="[{ required: true, message: '请填写用户名' }]"
        />
        <van-field
          v-model="form.mobile"
          name="mobile"
          label="手机号"
          type="tel"
          maxlength="11"
          placeholder="请输入 11 位手机号"
          :rules="[
            { required: true, message: '请填写手机号' },
            { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
          ]"
        />
        <van-field
          v-model="form.code"
          name="code"
          label="验证码"
          type="digit"
          maxlength="6"
          placeholder="6 位短信验证码"
          :rules="[{ required: true, message: '请填写验证码' }]"
        >
          <template #button>
            <van-button
              size="small"
              type="primary"
              :disabled="counting || !form.mobile || !/^1[3-9]\d{9}$/.test(form.mobile)"
              @click="onSendCode"
            >
              {{ counting ? `${seconds}s 后重发` : '获取验证码' }}
            </van-button>
          </template>
        </van-field>
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请设置密码"
          :rules="[{ required: true, message: '请设置密码' }]"
        />
      </van-cell-group>
      <div style="margin: 16px">
        <van-button round block type="primary" native-type="submit" :loading="loading">注 册</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { register, sendCaptcha } from '@/api/auth'

const router = useRouter()
const loading = ref(false)
const counting = ref(false)
const seconds = ref(60)
let timer = null

const form = reactive({
  username: '',
  mobile: '',
  code: '',
  password: '',
})

const startCountdown = () => {
  counting.value = true
  seconds.value = 60
  timer = setInterval(() => {
    seconds.value--
    if (seconds.value <= 0) {
      clearInterval(timer)
      counting.value = false
    }
  }, 1000)
}

const onSendCode = async () => {
  try {
    await sendCaptcha({ phone: form.mobile, type: 'register' })
    showSuccessToast('验证码已发送')
    startCountdown()
  } catch {
    showFailToast('发送失败')
  }
}

const onSubmit = async () => {
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      mobile: form.mobile,
      code: form.code,
    })
    showSuccessToast('注册成功，请登录')
    router.replace('/login')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => clearInterval(timer))
</script>
