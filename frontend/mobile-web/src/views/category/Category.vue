<template>
  <div class="category">
    <van-nav-bar title="商品分类" />
    <div class="body">
      <div class="sidebar">
        <div
          v-for="cat in tree"
          :key="cat.id"
          class="sidebar-item"
          :class="{ active: activeId === cat.id }"
          @click="activeId = cat.id"
        >
          {{ cat.name }}
        </div>
      </div>
      <div class="content">
        <template v-if="current">
          <h3>{{ current.name }}</h3>
          <div class="sub">
            <div
              v-for="sub in current.children || []"
              :key="sub.id"
              class="sub-item"
              @click="goList(sub.id)"
            >
              <van-image width="60" height="60" round :src="sub.picUrl" />
              <p>{{ sub.name }}</p>
            </div>
            <van-empty v-if="!(current.children && current.children.length)" description="暂无子分类" />
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { categoryTree } from '@/api/goods'

const router = useRouter()
const tree = ref([])
const activeId = ref(null)

const current = computed(() => tree.value.find((c) => c.id === activeId.value))

onMounted(async () => {
  try {
    tree.value = (await categoryTree()) || []
    if (tree.value.length) activeId.value = tree.value[0].id
  } catch {
    tree.value = []
  }
})

const goList = (id) => router.push({ path: '/goods', query: { categoryId: id } })
</script>

<style scoped>
.body {
  display: flex;
}
.sidebar {
  width: 90px;
  background: #f7f8fa;
}
.sidebar-item {
  padding: 14px 8px;
  text-align: center;
  font-size: 13px;
  color: #333;
  cursor: pointer;
}
.sidebar-item.active {
  background: #fff;
  color: #ee0a24;
  font-weight: bold;
  position: relative;
}
.sidebar-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #ee0a24;
}
.content {
  flex: 1;
  padding: 12px;
  background: #fff;
}
.sub {
  display: flex;
  flex-wrap: wrap;
}
.sub-item {
  width: 33%;
  text-align: center;
  font-size: 12px;
  margin-bottom: 12px;
}
.sub-item p {
  margin-top: 4px;
}
</style>
