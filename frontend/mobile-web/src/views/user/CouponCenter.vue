<template>
  <div class="coupon-center">
    <van-nav-bar title="领券中心" left-arrow @click-left="$router.back()" />
    <van-card v-for="c in list" :key="c.id" class="coupon-card">
      <template #title>
        <div class="coupon-title">
          <span class="name">{{ c.name }}</span>
          <van-tag v-if="c.tag" plain type="danger">{{ c.tag }}</van-tag>
        </div>
      </template>
      <template #desc>
        <div class="coupon-desc">
          <p class="amount">
            <span class="symbol">¥</span>{{ c.discount }}<span class="limit">（满{{ c.min }}元可用）</span>
          </p>
          <p class="time">有效期至：{{ c.endTime || '长期有效' }}</p>
          <p class="left">剩余 {{ c.leftCount === -1 ? '不限量' : c.leftCount + ' 张' }}</p>
        </div>
      </template>
      <template #tags>
        <van-button
          size="small"
          round
          :type="c.received ? 'default' : 'danger'"
          :disabled="c.received"
          :loading="receiving === c.id"
          @click="onReceive(c)"
        >
          {{ c.received ? '已领取' : '领取' }}
        </van-button>
      </template>
    </van-card>
    <van-empty v-if="!list.length" description="暂无可领优惠券" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showSuccessToast, showFailToast } from 'vant'
import { couponList, couponReceive, couponMyList } from '@/api/market'

const list = ref([])
const receiving = ref(null)

onMounted(async () => {
  const res = (await couponList({ pageNum: 1, pageSize: 50 })) || {}
  list.value = res.list || []
  // 拉取已领取的优惠券，标记“已领取”
  try {
    const myRes = (await couponMyList({ pageNum: 1, pageSize: 200 })) || {}
    const receivedIds = new Set((myRes.list || []).map((i) => String(i.couponId)))
    list.value.forEach((c) => {
      c.received = receivedIds.has(String(c.id))
    })
  } catch {
    // 未登录或查询失败时不影响列表展示
  }
})

const onReceive = async (c) => {
  if (c.received) return
  receiving.value = c.id
  try {
    await couponReceive({ couponId: c.id })
    c.received = true
    showSuccessToast('领取成功')
  } catch (e) {
    showFailToast(e.message || '领取失败')
  } finally {
    receiving.value = null
  }
}
</script>

<style scoped>
.coupon-card {
  margin-bottom: 8px;
}
.coupon-title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.coupon-title .name {
  font-weight: bold;
  font-size: 15px;
}
.coupon-desc .amount {
  color: #ee0a24;
  font-size: 18px;
  font-weight: bold;
  margin: 4px 0;
}
.coupon-desc .amount .symbol {
  font-size: 12px;
}
.coupon-desc .amount .limit {
  font-size: 12px;
  font-weight: normal;
  color: #666;
}
.coupon-desc .time,
.coupon-desc .left {
  font-size: 12px;
  color: #999;
  margin: 2px 0;
}
</style>
