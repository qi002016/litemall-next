<template>
  <div class="search">
    <van-search
      v-model="keyword"
      show-action
      placeholder="请输入商品关键词"
      @search="onSearch"
      @cancel="$router.back()"
    />
    <template v-if="history.length">
      <div class="history-head">
        <span>搜索历史</span>
        <van-icon name="delete-o" size="18" @click="onClear" />
      </div>
      <div class="tags">
        <van-tag
          v-for="h in history"
          :key="h"
          round
          class="tag"
          @click="useHistory(h)"
        >
          {{ h }}
        </van-tag>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { searchHistory, clearHistory, recordSearch } from '@/api/goods'

const router = useRouter()
const keyword = ref('')
const history = ref([])

const loadHistory = async () => {
  try {
    history.value = (await searchHistory()) || []
  } catch {
    // 未登录等情况静默隐藏
    history.value = []
  }
}

onMounted(loadHistory)

const onSearch = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  // 记录到后端（未登录会被后端忽略），不阻塞跳转
  recordSearch({ keyword: kw }).catch(() => {})
  router.push({ path: '/goods', query: { keyword: kw } })
}

const useHistory = (h) => {
  keyword.value = h
  onSearch()
}

const onClear = () => {
  showConfirmDialog({ title: '提示', message: '确定清空搜索历史？' })
    .then(async () => {
      try {
        await clearHistory()
      } catch {
        // 忽略
      }
      history.value = []
    })
    .catch(() => {})
}
</script>

<style scoped>
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 4px;
  font-size: 14px;
  color: #333;
}
.tags {
  padding: 8px 12px;
}
.tag {
  margin: 6px;
  cursor: pointer;
}
</style>
