<!-- 专题详情（T4 / R3-02 D7）：头图 + 标题/副标题 + 商品瀑布流，卡片沿用 GoodsList.vue -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">专题详情</div>
      <div style="width: 30px" />
    </div>

    <div v-if="loading" class="c-body"><div class="loading-more">加载中…</div></div>

    <template v-else-if="topic">
      <div class="topic-cover">
        <img
          v-if="topic.picUrl && !coverBroken"
          :src="mediaUrl(topic.picUrl)"
          :alt="topic.title"
          @error="coverBroken = true"
        />
        <div v-else class="topic-cover-fallback"><AppIcon name="image" :size="36" /></div>
      </div>

      <div class="c-body">
        <div class="card pad">
          <div class="bold" style="font-size: 17px">{{ topic.title }}</div>
          <div v-if="topic.subtitle" class="small dim mt6">{{ topic.subtitle }}</div>
        </div>

        <div class="row between" style="margin: 14px 2px 8px">
          <div class="bold" style="font-size: 15px">专题商品</div>
          <div class="tiny dim">共 {{ goods.length }} 件</div>
        </div>

        <div v-if="goods.length" class="goods-grid">
          <GoodsCard v-for="g in goods" :key="g.id" :g="g" @click="router.push('/goods/' + g.id)" />
        </div>
        <EmptyState v-else icon="grid" title="该专题暂无在售商品" hint="去逛逛别的商品吧" />

        <div class="page-foot">
          <button type="button" class="btn sm ghost" @click="router.push('/list')">看全部商品</button>
        </div>
      </div>
    </template>

    <div v-else class="c-body">
      <EmptyState icon="warn" title="专题不存在或已下线" hint="链接可能已失效" action="回首页" @action="router.replace('/home')" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import { topicDetail } from '@/api/market'
import { mediaUrl } from '@/utils/media'

const route = useRoute()
const router = useRouter()

const topic = ref(null)
const loading = ref(true)
const coverBroken = ref(false)

// 失效商品（onSale === false / 无 id）跳过，不渲染也不产生死链；content 为运营富文本，本页不渲染避免 XSS
const goods = computed(() =>
  (topic.value?.goodsList || []).filter((g) => g && g.id != null && g.onSale !== false)
)

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

async function load() {
  loading.value = true
  try {
    // silent：专题不存在（404）不弹全局错误 toast，由本页友好空态承接
    topic.value = await topicDetail({ topicId: route.params.id }, { silent: true })
  } catch {
    topic.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.topic-cover {
  position: relative;
  height: 180px;
  margin: 10px 12px 0;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(120deg, #ff7a18, #ff4d4d);
}
.topic-cover > img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.topic-cover-fallback {
  height: 100%;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.85);
}
</style>
