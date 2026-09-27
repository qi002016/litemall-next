<template>
  <div class="list">
    <van-nav-bar left-arrow @click-left="$router.back()">
      <template #title>
        <van-search
          v-model="keyword"
          shape="round"
          background="#f7f8fa"
          show-action
          placeholder="请输入商品关键词"
          @search="onKeywordSearch"
          @clear="onKeywordSearch"
        >
          <template #action>
            <div @click="onKeywordSearch">搜索</div>
          </template>
        </van-search>
      </template>
    </van-nav-bar>
    <van-dropdown-menu>
      <van-dropdown-item v-model="sort" :options="sortOptions" @change="reload" />
      <van-dropdown-item v-model="order" :options="orderOptions" @change="reload" />
    </van-dropdown-menu>

    <van-list v-model:loading="loading" :finished="finished" finished-text="— 没有更多了 —" @load="onLoad">
      <div class="goods-grid">
        <div
          v-for="item in list"
          :key="item.id"
          class="goods-item"
          @click="$router.push(`/goods/${item.id}`)"
        >
          <van-image width="100%" height="160" fit="cover" :src="item.picUrl" />
          <p class="name">{{ item.name }}</p>
          <p class="brief">{{ item.brief }}</p>
          <p class="price">¥{{ item.minPrice }}</p>
        </div>
      </div>
      <van-empty v-if="finished && !list.length" description="没有相关商品" />
    </van-list>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { goodsList, recordSearch } from '@/api/goods'

const route = useRoute()
const router = useRouter()
const list = ref([])
const loading = ref(false)
const finished = ref(false)
const pageNum = ref(1)
const sort = ref('addTime')
const order = ref('desc')
const keyword = ref(route.query.keyword || '')
const sortOptions = [
  { text: '综合', value: 'addTime' },
  { text: '价格', value: 'price' },
]
const orderOptions = [
  { text: '降序', value: 'desc' },
  { text: '升序', value: 'asc' },
]

const onLoad = async () => {
  try {
    const data = await goodsList({
      pageNum: pageNum.value,
      pageSize: 10,
      sort: sort.value,
      order: order.value,
      categoryId: route.query.categoryId,
      keyword: keyword.value || undefined,
    })
    const records = data?.list || []
    list.value.push(...records)
    if (!data || data.list.length < 10) finished.value = true
    pageNum.value++
  } finally {
    loading.value = false
  }
}

const onKeywordSearch = () => {
  const kw = keyword.value.trim()
  router.replace({ query: { ...route.query, keyword: kw || undefined } })
  if (kw) recordSearch({ keyword: kw }).catch(() => {})
  reload()
}

const reload = () => {
  list.value = []
  pageNum.value = 1
  finished.value = false
  onLoad()
}

onMounted(reload)
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
  cursor: pointer;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.goods-item .price {
  color: #ee0a24;
  font-weight: bold;
}
</style>
