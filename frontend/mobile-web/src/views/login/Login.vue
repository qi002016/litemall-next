<template>
  <div class="login">
    <van-nav-bar title="账号登录" />
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
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          :rules="[{ required: true, message: '请填写密码' }]"
        />
      </van-cell-group>
      <div style="margin: 16px">
        <van-button round block type="primary" native-type="submit" :loading="loading">登录</van-button>
        <div class="login-tip">
          还没有账号？<span @click="goRegister">立即注册</span>
        </div>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast } from 'vant'
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const goRegister = () => router.push('/register')

const onSubmit = async () => {
  loading.value = true
  try {
    const data = await login(form)
    userStore.setLogin(data)
    showSuccessToast('登录成功')
    router.replace(route.query.redirect || '/home')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-tip {
  margin-top: 12px;
  text-align: center;
  color: #969799;
  font-size: 13px;
}
.login-tip span {
  color: #1989fa;
}
</style>
