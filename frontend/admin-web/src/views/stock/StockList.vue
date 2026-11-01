<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>库存视图</h2>
          <span class="dim small">低库存阈值 5（BR-GOODS-10）；库存不能为负，也不能低于已锁数量</span>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div v-if="lockedUnavailable" class="alert warn">
        <el-icon><WarningFilled /></el-icon>
        <div>锁定库存暂时不可用（订单服务未返回锁定量）：本页「锁定 / 可售」显示为「—」，<b>不代表 0</b>，请稍后刷新。</div>
      </div>

      <div v-if="soldOutCount" class="alert warn">
        <el-icon><WarningFilled /></el-icon>
        <div>当前页有 {{ soldOutCount }} 个 SKU 可售为 0，建议尽快补货或在商品管理中下架。</div>
      </div>

      <StatusTabs v-model="tab" :options="STOCK_TABS" @change="reload" />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="商品名 / SKU 编码"
          clearable
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="没有符合条件的 SKU">
        <el-table-column label="商品" min-width="230">
          <template #default="{ row }">
            <div class="row">
              <el-image v-if="row.picUrl" :src="row.picUrl" fit="cover" class="thumb" />
              <div class="grow">
                <div class="bold ellipsis">{{ row.goodsName }}</div>
                <div class="tiny dim mt6">{{ row.skuCode || '无 SKU 编码' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="specText" label="SKU 规格" width="140" />
        <el-table-column label="实物库存" width="160">
          <template #default="{ row }">
            <el-input-number
              v-model="row.number"
              :min="0"
              :precision="0"
              size="small"
              controls-position="right"
              style="width: 120px"
              @change="(v) => onNumberChange(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="锁定" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ dim: row.locked == null }">{{ row.locked == null ? '—' : row.locked }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可售" width="80" align="center">
          <template #default="{ row }">
            <span class="num bold" :class="sellableClass(row)">{{ row.sellable == null ? '—' : row.sellable }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存水位" width="150">
          <template #default="{ row }">
            <div class="bar" :class="barClass(row)">
              <i :style="{ width: barWidth(row) }" />
            </div>
            <div class="tiny dim mt6">{{ row.sellable == null ? '可售未知' : `可售 ${row.sellable}` }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :text="stockStatus(row).text" :tone="stockStatus(row).tone" />
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="165" />
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

      <div class="tiny dim mt10">
        提示：实物库存可直接在表格内修改，失焦/回车后保存；保存时携带「修改前的数量」做乐观锁，冲突会提示刷新。
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import { stockPage, stockUpdate } from '@/api/stock'
import StatusTabs from '@/components/StatusTabs.vue'
import StatusTag from '@/components/StatusTag.vue'

const STOCK_TABS = [
  { value: '', label: '全部 SKU' },
  { value: 'low', label: '低库存（≤5）' },
  { value: 'out', label: '缺货（=0）' },
]

const list = ref([])
const total = ref(0)
const loading = ref(false)
const lockedUnavailable = ref(false)
const tab = ref('')
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const soldOutCount = computed(() => list.value.filter((r) => r.sellable != null && Number(r.sellable) === 0).length)

const params = computed(() => {
  const p = { pageNum: query.pageNum, pageSize: query.pageSize, sortBy: 'sellable', isAsc: true }
  if (query.keyword) p.keyword = query.keyword.trim()
  if (tab.value === 'low') p.lowStockOnly = true
  if (tab.value === 'out') p.soldOutOnly = true
  return p
})

const load = async () => {
  loading.value = true
  try {
    const data = (await stockPage(params.value)) || {}
    list.value = (data.list || []).map((row) => ({ ...row, _expected: row.number }))
    total.value = Number(data.total || 0)
    lockedUnavailable.value = Boolean(data.lockedUnavailable)
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
  tab.value = ''
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

const stockStatus = (row) => {
  if (row.sellable == null) return { text: '未知', tone: 'gray' }
  const n = Number(row.sellable)
  if (n <= 0) return { text: '缺货', tone: 'danger' }
  if (n <= (row.lowStockThreshold ?? 5)) return { text: '低库存', tone: 'warn' }
  return { text: '正常', tone: 'ok' }
}

const sellableClass = (row) => {
  if (row.sellable == null) return 'dim'
  const n = Number(row.sellable)
  if (n <= 0) return 'danger'
  if (n <= (row.lowStockThreshold ?? 5)) return 'warn'
  return ''
}

const barClass = (row) => {
  if (row.sellable == null) return ''
  const n = Number(row.sellable)
  if (n <= 0) return 'zero'
  if (n <= (row.lowStockThreshold ?? 5)) return 'low'
  return ''
}

const barWidth = (row) => {
  if (row.sellable == null) return '0%'
  const n = Number(row.sellable)
  const pct = Math.max(0, Math.min(100, (n / 20) * 100))
  return `${pct}%`
}

const onNumberChange = async (row, value) => {
  const number = Number(value)
  if (!Number.isInteger(number) || number < 0) {
    ElMessage.warning('库存必须为非负整数')
    load()
    return
  }
  if (number === row._expected) return

  try {
    const res = await stockUpdate({
      productId: row.productId,
      number,
      expectNumber: row._expected,
    })
    row.number = res?.number ?? number
    row.locked = res?.locked ?? row.locked
    row.sellable = res?.sellable ?? row.sellable
    row.lowStock = res?.lowStock ?? row.lowStock
    row.updateTime = res?.updateTime ?? row.updateTime
    row._expected = row.number
    ElMessage.success('库存已更新')
  } catch {
    // 并发冲突/低于锁定量等错误由拦截器提示，这里回读服务端权威值
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.thumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  border: 1px solid var(--line);
  flex: none;
}
.grow {
  flex: 1;
  min-width: 0;
}
.warn {
  color: var(--warn);
}
.danger {
  color: var(--danger);
}
</style>