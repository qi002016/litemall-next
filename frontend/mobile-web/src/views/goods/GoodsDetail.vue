<template>
  <div class="detail">
    <van-nav-bar title="商品详情" left-arrow @click-left="$router.back()" />
    <van-swipe :autoplay="3000" indicator-color="white">
      <van-swipe-item v-for="(img, i) in gallery" :key="i">
        <van-image fit="cover" width="100%" height="220" :src="img" />
      </van-swipe-item>
    </van-swipe>

    <div class="info">
      <p class="price">¥{{ detail.price }}</p>
      <p class="name">{{ detail.name }}</p>
      <p class="brief">{{ detail.brief }}</p>
      <p class="stock">库存：{{ detail.stock }} ｜ 评论：{{ detail.commentCount }}</p>
    </div>

    <van-cell-group title="商品详情">
      <div class="rich" v-html="detail.detail"></div>
    </van-cell-group>

    <van-submit-bar :price="totalPrice" button-text="加入购物车" @submit="onAddCart" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { showSuccessToast } from 'vant'
import { goodsDetail } from '@/api/goods'
import { cartAdd } from '@/api/cart'
import { footprintAdd } from '@/api/user'

const route = useRoute()
const detail = ref({})
const count = ref(1)

const gallery = computed(() => detail.value.gallery || (detail.value.picUrl ? [detail.value.picUrl] : []))
const totalPrice = computed(() => Math.round((detail.value.price || 0) * count.value * 100))

onMounted(async () => {
  detail.value = (await goodsDetail({ goodsId: route.params.id })) || {}
  // 记录浏览足迹（未登录时静默忽略）
  footprintAdd({ goodsId: route.params.id }).catch(() => {})
})

const onAddCart = async () => {
  await cartAdd({ goodsId: detail.value.id, specId: 0, number: count.value })
  showSuccessToast('已加入购物车')
}
</script>

<style scoped>
.price {
  color: #ee0a24;
  font-size: 20px;
  font-weight: bold;
}
.info {
  background: #fff;
  padding: 12px;
}
.rich {
  padding: 12px;
  font-size: 14px;
  line-height: 1.6;
}
</style>
