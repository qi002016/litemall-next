<template>
  <div class="coupons">
    <van-nav-bar title="我的优惠券" left-arrow @click-left="$router.back()" />
    <van-tabs v-model:active="tab">
      <van-tab title="未使用" />
      <van-tab title="已使用" />
      <van-tab title="已过期" />
    </van-tabs>
    <van-card v-for="c in list" :key="c.id" :title="c.name" :price="c.discount">
      <template #desc>
        <p class="coupon-desc">满{{ c.min }}元可用</p>
        <p class="coupon-time">有效期至：{{ c.endTime || '长期有效' }}</p>
      </template>
    </van-card>
    <van-empty v-if="!list.length" description="暂无优惠券">
      <van-button round type="primary" size="small" @click="$router.push('/user/coupon-center')">去领券中心</van-button>
    </van-empty>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { couponMyList } from '@/api/market'

const list = ref([])
const tab = ref(0)
const statusMap = ['unused', 'used', 'expired']

const load = async () => {
  const res = (await couponMyList({ status: statusMap[tab.value], pageNum: 1, pageSize: 20 })) || {}
  list.value = res.list || []
}
watch(tab, load)
onMounted(load)
</script>

<style scoped>
.coupon-desc {
  margin: 0;
  font-size: 12px;
  color: #666;
}
.coupon-time {
  margin: 4px 0 0;
  font-size: 12px;
  color: #999;
}
</style>
