<template>
  <div class="home">
    <van-nav-bar title="litemall 商城" />
    <div class="search-entry" @click="goSearch">
      <van-search v-model="keyword" placeholder="搜索商品" readonly @click-input="goSearch" />
    </div>
    <van-notice-bar left-icon="volume-o" :scrollable="true">欢迎来到 litemall 精选商城</van-notice-bar>

    <van-list v-model:loading="loading" :finished="finished" finished-text="— 没有更多了 —" @load="onLoad">
      <div class="goods-grid">
        <div v-for="item in list" :key="item.id" class="goods-item" @click="goDetail(item.id)">
          <van-image width="100%" height="160" fit="cover" :src="item.picUrl" />
          <p class="name">{{ item.name }}</p>
          <p class="brief">{{ item.brief }}</p>
          <p class="price">¥{{ item.minPrice }}</p>
        </div>
      </div>
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { goodsList } from '@/api/goods'

const router = useRouter()
const keyword = ref('')
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const pageNum = ref(1)

const onLoad = async () => {
  try {
    const data = await goodsList({ pageNum: pageNum.value, pageSize: 10 })
    const records = data?.list || data || []
    list.value.push(...records)
    if (!data || !data.list || data.list.length < 10) finished.value = true
    pageNum.value++
  } finally {
    loading.value = false
  }
}

const goSearch = () => router.push('/search')
const goDetail = (id) => router.push(`/goods/${id}`)
</script>

<style scoped>
.goods-grid {
  display: flex;
  flex-wrap: wrap;
  padding: 6px;
}
.goods-item {
  width: 50%;
  box-sizing: border-box;
  padding: 6px;
  background: #fff;
  margin-bottom: 8px;
}
.goods-item .name {
  font-size: 14px;
  margin: 6px 0 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goods-item .brief {
  font-size: 12px;
  color: #999;
  margin: 0 0 4px;
}
.goods-item .price {
  color: #ee0a24;
  font-weight: bold;
}
</style>
