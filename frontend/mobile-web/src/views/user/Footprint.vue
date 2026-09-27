<template>
  <div class="footprint">
    <van-nav-bar title="浏览记录" left-arrow @click-left="$router.back()" />
    <div v-for="f in list" :key="f.id" class="foot-item" @click="$router.push(`/goods/${f.goodsId}`)">
      <van-image width="80" height="80" fit="cover" :src="f.picUrl" class="thumb" />
      <div class="body">
        <p class="name">{{ f.goodsName }}</p>
        <p class="time">{{ f.addTime }}</p>
      </div>
    </div>
    <van-empty v-if="!list.length" description="暂无浏览记录" />
    <div v-if="total > list.length" class="more">
      <van-button size="small" plain type="primary" :loading="loading" @click="loadMore">加载更多</van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { footprintList } from '@/api/user'

const list = ref([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = (await footprintList({ pageNum: pageNum.value, pageSize: 10 })) || {}
    list.value = pageNum.value === 1 ? res.list || [] : [...list.value, ...(res.list || [])]
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(load)

const loadMore = () => {
  pageNum.value += 1
  load()
}
</script>

<style scoped>
.foot-item {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 12px;
  margin-bottom: 8px;
}
.thumb {
  margin-right: 12px;
  border-radius: 6px;
}
.body {
  flex: 1;
}
.name {
  margin: 0 0 6px;
  font-size: 14px;
}
.time {
  margin: 0;
  font-size: 12px;
  color: #999;
}
.more {
  text-align: center;
  padding: 12px;
}
</style>
