<template>
  <div>
    <el-row :gutter="16">
      <el-col v-for="c in cards" :key="c.label" :span="6">
        <el-card shadow="hover">
          <div class="num">{{ c.value }}</div>
          <div class="label">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 16px">
      <template #header>欢迎使用 litemall 微服务商城运营后台</template>
      <p>已对接微服务网关 :8000。左侧菜单可进入商品、订单、用户、营销管理。</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { dashboardStats } from '@/api/marketing'

const cards = ref([
  { label: '商品总数', value: '--' },
  { label: '今日订单', value: '--' },
  { label: '用户总数', value: '--' },
  { label: '待发货', value: '--' },
])

onMounted(async () => {
  try {
    const s = (await dashboardStats()) || {}
    cards.value = [
      { label: '商品总数', value: s.goodsTotal ?? '--' },
      { label: '今日订单', value: s.todayOrders ?? '--' },
      { label: '用户总数', value: s.userTotal ?? '--' },
      { label: '待发货', value: s.undelivered ?? '--' },
    ]
  } catch {
    /* 未登录或服务未启动时保持占位 */
  }
})
</script>

<style scoped>
.num {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}
.label {
  color: #999;
  margin-top: 8px;
}
</style>
