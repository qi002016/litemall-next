<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>商品管理</h2>
          <span class="dim small">展示价 = 可售规格最低价（BR-GOODS-05）</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建商品</el-button>
      </div>

      <StatusTabs v-model="query.status" :options="GOODS_TABS" @change="reload" />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="商品名称"
          clearable
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="没有符合条件的商品">
        <el-table-column label="图片" width="70">
          <template #default="{ row }">
            <el-image v-if="row.picUrl" :src="row.picUrl" fit="cover" class="thumb" :preview-src-list="[row.picUrl]" preview-teleported />
            <div v-else class="thumb placeholder">无图</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="bold">{{ row.name }}</div>
            <div class="tiny dim mt6">{{ row.brief || '—' }}</div>
            <div class="tiny dim mt6">{{ row.skuCount ?? '—' }} 个规格 · 总可售 {{ row.totalStock ?? '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="130">
          <template #default="{ row }">{{ row.categoryName || '—' }}</template>
        </el-table-column>
        <el-table-column label="品牌" width="110">
          <template #default="{ row }">{{ row.brandName || '—' }}</template>
        </el-table-column>
        <el-table-column label="展示价" width="110" align="right">
          <template #default="{ row }">
            <span class="price">{{ money(row.displayPrice ?? row.minPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可售 / 总库存" width="120" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.totalStock ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :text="goodsStatusMeta(row).text" :tone="goodsStatusMeta(row).tone" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" link size="small" @click="toggleSale(row)">下架</el-button>
            <el-button v-else link type="primary" size="small" @click="toggleSale(row)">上架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          :page-sizes="[10, 20, 50]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <GoodsEditDialog v-model="editVisible" :goods="editing" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { goodsPage, goodsUpdate } from '@/api/goods'
import { GOODS_STATUS, GOODS_TABS } from '@/constants/dict'
import { money } from '@/utils/format'
import StatusTabs from '@/components/StatusTabs.vue'
import StatusTag from '@/components/StatusTag.vue'
import GoodsEditDialog from '@/views/goods/GoodsEditDialog.vue'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, status: '', keyword: '' })

const editVisible = ref(false)
const editing = ref(null)

const params = computed(() => {
  const p = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.status !== '') p.status = query.status
  if (query.keyword) p.keyword = query.keyword.trim()
  return p
})

function goodsStatusMeta(row) {
  const hit = GOODS_STATUS[row?.status]
  return {
    text: row?.statusText || hit?.text || '—',
    tone: hit?.tone || 'gray',
  }
}

const load = async () => {
  loading.value = true
  try {
    const data = (await goodsPage(params.value)) || {}
    list.value = data.list || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const reload = () => {
  query.pageNum = 1
  load()
}

const resetQuery = () => {
  query.keyword = ''
  query.status = ''
  reload()
}

const onPageChange = (p) => {
  query.pageNum = p
  load()
}
const onSizeChange = (size) => {
  query.pageSize = size
  query.pageNum = 1
  load()
}

const openCreate = () => {
  editing.value = null
  editVisible.value = true
}

const openEdit = (row) => {
  editing.value = row
  editVisible.value = true
}

const toggleSale = async (row) => {
  const target = row.status === 1 ? 2 : 1
  await goodsUpdate({ id: row.id, status: target })
  ElMessage.success(target === 1 ? '商品已上架' : '商品已下架')
  load()
}

onMounted(load)
</script>

<style scoped>
.thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  border: 1px solid var(--line);
}
.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-3);
  background: #fafbfc;
}
</style>