<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>订单管理</h2>
          <span class="dim small">退款进度不占主状态，按「已退款金额 / 全额退款」单独标注</span>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <StatusTabs v-model="query.status" :options="ORDER_TABS" @change="reload" />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="单号 / 收货人 / 手机号"
          clearable
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          start-placeholder="下单开始"
          end-placeholder="下单结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="没有符合条件的订单">
        <el-table-column label="单号" width="180">
          <template #default="{ row }">
            <span class="num link" @click="openDetail(row)">{{ row.orderSn }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="165">
          <template #default="{ row }">{{ fmtTime(row.addTime) }}</template>
        </el-table-column>
        <el-table-column label="买家" width="110">
          <template #default="{ row }">{{ dash(row.userId) }}</template>
        </el-table-column>
        <el-table-column label="商品" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.goodsSummary || '—' }}</template>
        </el-table-column>
        <el-table-column label="实付" width="120" align="right">
          <template #default="{ row }">
            <div class="price">{{ money(row.orderAmount) }}</div>
            <div v-if="Number(row.refundedAmount) > 0" class="tiny danger">
              已退 {{ money(row.refundedAmount) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态 / 售后" width="140">
          <template #default="{ row }">
            <StatusTag :text="orderStatusMeta(row).text" :tone="orderStatusMeta(row).tone" />
            <div v-if="row.aftersaleStatusText" class="tiny mt6">
              <span class="tag outline">{{ row.aftersaleStatusText }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'undelivered'"
              link
              type="primary"
              size="small"
              @click="openShip(row)"
            >
              发货
            </el-button>
            <el-button v-if="row.status === 'unpaid'" link type="danger" size="small" @click="onCloseOrder(row)">
              立即关单
            </el-button>
            <el-button v-if="row.status === 'shipped'" link size="small" @click="onUpdateShip(row)">
              改单号
            </el-button>
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

    <OrderDetailDrawer v-model="detailVisible" :order-id="activeOrder?.id" @updated="load" />
    <OrderShipDialog v-model="shipVisible" :order="shipTarget" :mode="shipMode" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { orderPage, orderDetail } from '@/api/order'
import { ORDER_TABS, orderStatusMeta } from '@/constants/dict'
import { dash, fmtTime, money } from '@/utils/format'
import StatusTabs from '@/components/StatusTabs.vue'
import StatusTag from '@/components/StatusTag.vue'
import OrderDetailDrawer from '@/views/order/OrderDetailDrawer.vue'
import OrderShipDialog from '@/views/order/OrderShipDialog.vue'
import { closeOrderFlow } from '@/views/order/closeOrder'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dateRange = ref([])
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: '',
  keyword: '',
})

const detailVisible = ref(false)
const activeOrder = ref(null)
const shipVisible = ref(false)
const shipMode = ref('ship')
const shipTarget = ref(null)

const params = computed(() => {
  const p = { pageNum: query.pageNum, pageSize: query.pageSize }
  if (query.status) p.status = query.status
  if (query.keyword) p.keyword = query.keyword.trim()
  if (dateRange.value?.length === 2) {
    p.addTimeMin = dateRange.value[0]
    p.addTimeMax = dateRange.value[1]
  }
  return p
})

const load = async () => {
  loading.value = true
  try {
    const data = (await orderPage(params.value)) || {}
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
  dateRange.value = []
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

const openDetail = (row) => {
  activeOrder.value = row
  detailVisible.value = true
}

const openShip = (row) => {
  shipMode.value = 'ship'
  shipTarget.value = row
  shipVisible.value = true
}

// 列表出参不含 shipUpdateCount，改单号前用详情（CN-33 canUpdateShip）判定是否还能改
const onUpdateShip = async (row) => {
  let detail = null
  try {
    detail = await orderDetail(row.id, { silent: true })
  } catch {
    /* 详情不可读时退回列表行数据，最终由后端校验 */
  }
  if (detail && detail.canUpdateShip === false) {
    ElMessage.warning('发货后物流单号仅允许修改 1 次，该订单已修改过')
    return
  }
  shipMode.value = 'update-ship'
  shipTarget.value = detail ? { ...detail } : row
  shipVisible.value = true
}

// 硬要求：立即关单必须二次确认（closeOrderFlow 内部弹确认框后才调 /admin/order/close）
const onCloseOrder = async (row) => {
  try {
    const done = await closeOrderFlow(row)
    if (done) load()
  } catch {
    /* 用户取消或接口拒绝，提示已由确认框/拦截器给出 */
  }
}

onMounted(load)
</script>