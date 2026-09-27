<template>
  <div class="confirm">
    <van-nav-bar title="确认订单" left-arrow @click-left="$router.back()" />
    <van-cell title="收货地址" :value="address ? `${address.name} ${address.tel} ${address.addressDetail}` : '请选择'" is-link @click="pickAddress" />
    <van-cell-group title="费用明细">
      <van-cell title="商品金额" :value="`¥${detail.goodsAmount ?? '--'}`" />
      <van-cell title="运费" :value="`¥${detail.freightAmount ?? 0}`" />
      <van-cell title="优惠券抵扣" :value="`-¥${detail.couponAmount ?? 0}`" />
    </van-cell-group>
    <van-submit-bar :price="payable" button-text="提交订单" @submit="onSubmit" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { orderConfirm, orderSubmit, orderIdemToken } from '@/api/order'
import { addressList } from '@/api/user'

const route = useRoute()
const router = useRouter()
const detail = ref({})
const address = ref(null)

// 购物车页传入的选中购物车项ID列表（逗号分隔）
const cartIds = computed(() =>
  (route.query.cartIds || '')
    .split(',')
    .map(Number)
    .filter((n) => n > 0),
)

const payable = computed(() => Math.round((detail.value.orderAmount || 0) * 100))

onMounted(async () => {
  const addrs = (await addressList()) || []
  address.value = addrs.find((a) => a.isDefault) || addrs[0] || null
  await calc()
})

const calc = async () => {
  if (!cartIds.value.length) return showFailToast('请先勾选商品')
  if (!address.value) return
  detail.value = (await orderConfirm({ cartIds: cartIds.value, addressId: address.value.id })) || {}
}

const pickAddress = () => router.push('/user/address')

const onSubmit = async () => {
  if (!cartIds.value.length) return showFailToast('请先勾选商品')
  if (!address.value) return showFailToast('请选择收货地址')
  const { idemToken } = (await orderIdemToken()) || {}
  const res = await orderSubmit({ cartIds: cartIds.value, addressId: address.value.id, idemToken })
  showSuccessToast('下单成功')
  router.replace(`/order/${res.orderId}`)
}
</script>
