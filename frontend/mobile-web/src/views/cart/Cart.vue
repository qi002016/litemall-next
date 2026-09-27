<template>
  <div class="cart">
    <van-nav-bar title="购物车" left-arrow @click-left="$router.back()" />
    <van-empty v-if="!list.length" description="购物车为空" />
    <template v-else>
      <van-checkbox-group v-model="checked">
        <div v-for="(item, idx) in list" :key="idx" class="cart-row">
          <van-checkbox :name="String(idx)" />
          <van-image width="80" height="80" fit="cover" :src="item.goodsPic" class="thumb" />
          <div class="body">
            <p class="name">{{ item.goodsName }}</p>
            <p class="spec">{{ item.spec }}</p>
            <div class="bottom">
              <span class="price">¥{{ item.price }}</span>
              <van-stepper v-model="item.number" @change="(v) => onUpdate(item, v)" />
            </div>
          </div>
        </div>
      </van-checkbox-group>
      <van-submit-bar :price="checkedPrice" button-text="去结算" @submit="goConfirm" />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { cartList, cartUpdate } from '@/api/cart'

const router = useRouter()
const list = ref([])
const checked = ref([])

const checkedPrice = computed(() =>
  Math.round(
    list.value.reduce(
      (sum, it, i) => (checked.value.includes(String(i)) ? sum + (it.price || 0) * it.number : sum),
      0,
    ) * 100,
  ),
)

onMounted(async () => {
  list.value = (await cartList()) || []
})

const onUpdate = async (item, number) => {
  await cartUpdate({ goodsId: item.goodsId, specId: item.specId || 0, number })
  showSuccessToast('已更新')
}

const goConfirm = () => {
  const ids = list.value
    .filter((_, i) => checked.value.includes(String(i)))
    .map((it) => it.id)
  if (!ids.length) return showFailToast('请先勾选商品')
  router.push({ path: '/order/confirm', query: { cartIds: ids.join(',') } })
}
</script>

<style scoped>
.cart-row {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 12px;
  margin-bottom: 8px;
}
.thumb {
  margin: 0 12px;
  border-radius: 6px;
}
.body {
  flex: 1;
}
.name {
  margin: 0;
  font-size: 14px;
}
.spec {
  margin: 4px 0;
  font-size: 12px;
  color: #999;
}
.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.price {
  color: #ee0a24;
  font-weight: bold;
}
</style>
