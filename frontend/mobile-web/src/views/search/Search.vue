<!-- 搜索：对齐 prototype/assets/app.js search（880-934） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">搜索</div>
      <div style="width: 30px" />
    </div>

    <div class="c-body">
      <div class="row" style="gap: 8px">
        <input
          ref="inputEl"
          v-model="kw"
          class="input"
          style="flex: 1"
          placeholder="输入商品名 / 简介 / 标签"
          @keyup.enter="submit(kw)"
        />
        <button type="button" class="btn primary" @click="submit(kw)">搜索</button>
      </div>
      <div v-if="lastKw" class="tiny dim mt6">上次搜索：{{ lastKw }}</div>

      <div class="mt16">
        <div class="bold">热门搜索</div>
        <div class="spec-opts mt10">
          <span v-for="h in HOT" :key="h" class="spec-opt" @click="submit(h)">{{ h }}</span>
        </div>
      </div>

      <div v-if="history.length" class="mt16">
        <div class="row between">
          <div class="bold">搜索历史</div>
          <div class="tiny dim" @click="clear">清空</div>
        </div>
        <div class="spec-opts mt10">
          <span v-for="h in history" :key="h" class="spec-opt" @click="submit(h)">{{ h }}</span>
        </div>
      </div>
      <div v-else class="mt16 tiny dim">暂无搜索历史（提交过的关键词会记在这里）</div>

      <div class="alert mt16">
        <AppIcon name="search" :size="15" />
        <div>只搜在售商品（BR-SEARCH-01）；关键词为空时直接进列表，不写历史（BR-SEARCH-02）。</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { clearHistory, recordSearch, searchHistory } from '@/api/goods'
import { toast } from '@/utils/toast'

const HOT = ['T恤', '卫衣', '咖啡', '耳机', '保温杯', '牛仔裤', '键盘']

const route = useRoute()
const router = useRouter()

const kw = ref('')
const lastKw = ref(String(route.query.kw || ''))
const history = ref([])
const inputEl = ref(null)

async function loadHistory() {
  try {
    const data = await searchHistory()
    history.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    history.value = []
  }
}

async function submit(keyword) {
  const k = String(keyword == null ? '' : keyword).trim()
  if (!k) {
    router.push('/list')
    return
  }
  try {
    await recordSearch({ keyword: k })
  } catch {
    /* 记录失败不阻断搜索 */
  }
  router.push({ path: '/list', query: { kw: k } })
}

async function clear() {
  try {
    await clearHistory()
  } catch {
    /* 忽略 */
  }
  history.value = []
  toast('搜索历史已清空', 'ok')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/home')
}

onMounted(async () => {
  await loadHistory()
  await nextTick()
  inputEl.value?.focus()
})
</script>