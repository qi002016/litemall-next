<template>
  <div class="user">
    <van-nav-bar title="我的" />
    <div class="header" @click="$router.push('/user/profile')">
      <van-image round width="50" height="50" :src="user.avatar || defaultAvatar" />
      <div class="head-right">
        <p class="name">{{ user.nickname || user.username || '未登录' }}</p>
        <p class="sub">查看并编辑个人资料 ›</p>
      </div>
    </div>
    <van-cell-group>
      <van-cell title="待付款" is-link center @click="$router.push({ path: '/order', query: { status: 'unpaid' } })" />
      <van-cell title="待收货" is-link center @click="$router.push({ path: '/order', query: { status: 'shipped' } })" />
      <van-cell title="全部订单" is-link center @click="$router.push('/order')" />
    </van-cell-group>
    <van-cell-group>
      <van-cell title="收货地址" is-link center @click="$router.push('/user/address')" />
      <van-cell title="我的优惠券" is-link center @click="$router.push('/user/coupons')" />
      <van-cell title="领券中心" is-link center @click="$router.push('/user/coupon-center')" />
      <van-cell title="浏览记录" is-link center @click="$router.push('/user/footprint')" />
      <van-cell title="设置" is-link center @click="$router.push('/user/setting')" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { userInfo } from '@/api/user'
import { useUserStore } from '@/store/user'

const defaultAvatar = 'https://img.yzcdn.cn/vant/cat.jpeg'
const user = ref({})
const userStore = useUserStore()

onMounted(async () => {
  try {
    user.value = (await userInfo()) || {}
    userStore.setUserInfo(user.value)
  } catch (e) {
    user.value = userStore.userInfo || {}
  }
})
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  padding: 20px;
  background: #fff;
  margin-bottom: 10px;
  cursor: pointer;
}
.head-right {
  margin-left: 12px;
}
.name {
  font-size: 16px;
  font-weight: bold;
}
.sub {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}
</style>
