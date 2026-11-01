<template>
  <div class="c-page x-catpage">
    <div class="x-catpanel">
      <div class="x-catpanel-hd">
        <div class="t">分类</div>
        <div class="x-catclose" @click="close"><AppIcon name="close" :size="17" /></div>
      </div>
      <div class="x-catpanel-bd">
        <div class="x-cat-left">
          <div
            v-for="c in cats"
            :key="c.id"
            class="x-cat-item"
            :class="{ on: String(c.id) === String(activeId) }"
            @click="activeId = c.id"
          >
            {{ c.name }}
          </div>
        </div>
        <div class="x-cat-right">
          <template v-if="active">
            <div class="bold" style="font-size: 13.5px; margin-bottom: 10px">{{ active.name }}</div>
            <div class="x-sub">
              <div class="it" @click="goList(active.id)">
                <span class="e">{{ emojiOf(active) }}</span>
                全部{{ active.name }}
              </div>
              <div v-for="s in active.children || []" :key="s.id" class="it" @click="goList(s.id)">
                <span class="e">{{ emojiOf(s) }}</span>
                {{ s.name }}
              </div>
            </div>
          </template>
          <EmptyState v-else icon="grid" title="暂无在售分类" hint="稍后再来看看" />
        </div>
      </div>
    </div>
    <div class="x-catmask" @click="close" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { categoryTree } from '@/api/goods'

const router = useRouter()
const cats = ref([])
const activeId = ref('')

const EMOJIS = ['👕', '🏠', '📱', '🍵', '🏀', '🧴', '🧸', '📚', '👟', '👜']

const active = computed(() => cats.value.find((c) => String(c.id) === String(activeId.value)) || null)

function emojiOf(c, i) {
  const n = Number(c?.id) || 0
  return EMOJIS[n % EMOJIS.length]
}

function goList(id) {
  router.push({ path: '/list', query: { categoryId: id } })
}

function close() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

onMounted(async () => {
  try {
    const data = await categoryTree()
    cats.value = Array.isArray(data) ? data : []
    if (cats.value.length) activeId.value = cats.value[0].id
  } catch {
    cats.value = []
  }
})
</script>