<template>
  <div class="order-detail">
    <van-nav-bar title="订单详情" left-arrow @click-left="$router.back()" />
    <van-cell-group v-if="info">
      <van-cell title="订单编号" :value="info.orderSn" />
      <van-cell title="状态" :value="info.statusText || info.status" />
      <van-cell title="应付金额" :value="`¥${info.orderAmount}`" />
    </van-cell-group>
    <div class="actions">
      <van-button v-if="info?.status === 'unpaid'" type="danger" @click="onPay">立即支付</van-button>
      <van-button v-if="info?.status === 'unpaid'" @click="onCancel">取消订单</van-button>
      <van-button v-if="info?.status === 'shipped'" type="primary" @click="onConfirm">确认收货</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showConfirmDialog } from 'vant'
import { orderDetail, orderCancel, orderConfirmReceive } from '@/api/order'
import { payOrder } from '@/utils/pay'

const route = useRoute()
const router = useRouter()
const info = ref(null)

const load = async () => {
  info.value = (await orderDetail({ orderId: route.params.id })) || {}
}
onMounted(load)

const onPay = async () => {
  await payOrder(info.value)
  load()
}
const onCancel = async () => {
  await showConfirmDialog({ title: '提示', message: '确定取消该订单？' })
  await orderCancel({ orderId: info.value.orderId })
  showSuccessToast('已取消')
  load()
}
const onConfirm = async () => {
  await orderConfirmReceive({ orderId: info.value.orderId })
  showSuccessToast('已确认收货')
  load()
}
</script>
