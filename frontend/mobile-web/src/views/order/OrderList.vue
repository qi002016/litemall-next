<template>
  <div class="order-list">
    <van-nav-bar title="我的订单" left-arrow @click-left="$router.back()" />
    <van-tabs v-model:active="tab">
      <van-tab title="全部" />
      <van-tab title="待付款" />
      <van-tab title="待发货" />
      <van-tab title="待收货" />
      <van-tab title="已完成" />
    </van-tabs>
    <van-list v-model:loading="loading" :finished="finished" @load="onLoad">
      <van-card
        v-for="o in list"
        :key="o.id || o.orderId"
        :title="o.orderSn || o.id"
        :desc="o.statusText || o.status"
        :price="o.orderAmount"
        @click="$router.push(`/order/${o.orderId || o.id}`)"
      />
    </van-list>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { orderList } from '@/api/order'

const list = ref([])
const loading = ref(false)
const finished = ref(false)
const pageNum = ref(1)
const tab = ref(0)
const statusMap = ['', 'unpaid', 'undelivered', 'shipped', 'confirmed']

const onLoad = async () => {
  try {
    const data = await orderList({ pageNum: pageNum.value, pageSize: 10, status: statusMap[tab.value] })
    const records = data?.list || []
    list.value.push(...records)
    if (!data || data.list.length < 10) finished.value = true
    pageNum.value++
  } finally {
    loading.value = false
  }
}

const reload = () => {
  list.value = []
  pageNum.value = 1
  finished.value = false
  onLoad()
}

watch(tab, reload)
onMounted(reload)
</script>
