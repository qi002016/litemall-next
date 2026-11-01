<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="dot">满</span>
        <div>
          <div class="name">小满商城 · 管理后台</div>
          <div class="sub">演示环境 · 单商户 · 单管理员身份（BR-ADM-01）</div>
        </div>
      </div>

      <div class="alert warn">
        <el-icon><InfoFilled /></el-icon>
        <div>演示账号由种子数据初始化；登录后的审核、发货、关单等写操作会真实落到本机数据库。</div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" size="large" placeholder="请输入管理员账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button class="submit" type="primary" size="large" :loading="loading" @click="onSubmit">
          登录后台
        </el-button>
      </el-form>

      <div class="divider" />
      <div class="foot">
        <div>登录后可处理：订单发货 / 立即关单 / 售后审核 / 库存调整</div>
        <div class="dim tiny mt6">遇到登录问题请联系运维；账号锁定与权限管理属 R2 范围。</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, InfoFilled } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

// 演示环境（dev）预填种子账号，生产构建不写入任何默认凭据
const form = reactive({
  username: import.meta.env.DEV ? 'admin123' : '',
  password: import.meta.env.DEV ? 'admin123' : '',
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const onSubmit = async () => {
  if (loading.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await login(form)
    userStore.setLogin(data)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: radial-gradient(circle at 20% 10%, #fff1e8 0, #f5f5f7 46%, #f0f2f5 100%);
}

.login-card {
  width: 420px;
  max-width: 100%;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  padding: 26px 26px 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.brand .dot {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 800;
  font-size: 18px;
  background: linear-gradient(135deg, var(--brand), #ff2e4d);
}

.brand .name {
  font-size: 17px;
  font-weight: 700;
}

.brand .sub {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.foot {
  font-size: 12.5px;
  color: var(--text-2);
  line-height: 1.6;
}
</style>