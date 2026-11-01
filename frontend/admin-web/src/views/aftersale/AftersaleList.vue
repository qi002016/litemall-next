<template>
  <div>
    <div class="panel">
      <div class="panel-hd between">
        <div class="row">
          <h2>售后处理台</h2>
          <span class="dim small">未完结 = 申请中 / 待寄回 / 待收货 / 退款中</span>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div v-if="pendingRestock.length" class="alert err">
        <el-icon><WarningFilled /></el-icon>
        <div>
          资金已退但库存回补失败：当前页 {{ pendingRestock.length }} 笔售后单停留在「退款中」。
          请点行内「重试回补并退款」完成回补；重复操作不会二次退款。
        </div>
      </div>

      <StatusTabs v-model="query.status" :options="AFTERSALE_TABS" @change="reload" />

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="售后号 / 订单号 / 收货人 / 商品名"
          clearable
          style="width: 260px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-input
          v-model="query.aftersaleSn"
          placeholder="精确售后号"
          clearable
          style="width: 180px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          start-placeholder="申请开始"
          end-placeholder="申请结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px"
          @change="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" class="tbl-wrap" empty-text="没有符合条件的售后单">
        <el-table-column label="售后号" width="150">
          <template #default="{ row }">
            <span class="num link" @click="openDetail(row)">{{ row.aftersaleSn }}</span>
            <div v-if="row.applyTime" class="tiny dim mt6">{{ fmtTime(row.applyTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单号" width="150">
          <template #default="{ row }">
            <span class="num">{{ row.orderSn || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.goodsName || '—' }}</div>
            <div class="tiny dim mt6">{{ row.specText || '单一规格' }} × {{ row.number ?? '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型 / 理由" width="120">
          <template #default="{ row }">
            <span class="tag" :class="aftersaleTypeMeta(row).tone">{{ aftersaleTypeMeta(row).text }}</span>
            <div class="tiny dim mt6 ellipsis">{{ row.reason || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span class="price">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <StatusTag :text="aftersaleStatusMeta(row).text" :tone="aftersaleStatusMeta(row).tone" />
            <div v-if="row.restockStatusText" class="tiny mt6" :class="{ danger: isPendingRestock(row) }">
              {{ row.restockStatusText }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="165" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>

            <template v-if="row.statusKey === 'applied'">
              <el-button link type="primary" size="small" @click="act(row, 'approve')">同意</el-button>
              <el-button link type="danger" size="small" @click="act(row, 'reject')">驳回</el-button>
            </template>

            <template v-else-if="row.statusKey === 'waitReturn'">
              <el-button link type="primary" size="small" @click="act(row, 'skip-receive')">跳过收货</el-button>
              <el-button link size="small" @click="act(row, 'close')">关闭</el-button>
            </template>

            <template v-else-if="row.statusKey === 'waitReceive'">
              <el-button link type="primary" size="small" @click="act(row, 'confirm-receive')">确认收货</el-button>
              <el-button link size="small" @click="act(row, 'skip-receive')">跳过收货</el-button>
              <el-button link size="small" @click="act(row, 'close')">关闭</el-button>
            </template>

            <template v-else-if="row.statusKey === 'refunding'">
              <el-button
                link
                :type="isPendingRestock(row) ? 'danger' : 'primary'"
                size="small"
                @click="act(row, isPendingRestock(row) ? 'retry-restock' : 'refund')"
              >
                {{ isPendingRestock(row) ? '重试回补并退款' : '执行退款' }}
              </el-button>
            </template>

            <span v-else class="tiny dim">终态</span>
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

    <AftersaleDetailDrawer
      ref="detailDrawerRef"
      v-model="detailVisible"
      :aftersale-id="activeRow?.aftersaleId"
      :row="activeRow"
      @action="onDrawerAction"
      @updated="load"
    />

    <AftersaleActionDialog v-model="actionVisible" :mode="actionMode" :row="actionRow" @done="onActionDone" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import { aftersaleConfirmReceive, aftersalePage } from '@/api/aftersale'
import { AFTERSALE_TABS, aftersaleStatusKey, aftersaleStatusMeta, aftersaleTypeMeta } from '@/constants/dict'
import { fmtTime, money } from '@/utils/format'
import StatusTabs from '@/components/StatusTabs.vue'
import StatusTag from '@/components/StatusTag.vue'
import AftersaleDetailDrawer from '@/views/aftersale/AftersaleDetailDrawer.vue'
import AftersaleActionDialog from '@/views/aftersale/AftersaleActionDialog.vue'

const route = useRoute()

const list = ref([])
const total = ref(0)
const loading = ref(false)
const dateRange = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, status: '', keyword: '', aftersaleSn: '' })

const detailVisible = ref(false)
const detailDrawerRef = ref(null)
const activeRow = ref(null)
const actionVisible = ref(false)
const actionMode = ref('approve')
const actionRow = ref(null)

const pendingRestock = computed(() => list.value.filter(isPendingRestock))

function isPendingRestock(row) {
  return Boolean(row?.restockPending) || String(row?.restockStatus) === '2'
}

const params = computed(() => {
  const p = {
    // 06 §3 分页统一 pageNum（07 CN-01 文档段写 pageNo，后端已兼容该别名）
    pageNum: query.pageNum,
    pageSize: query.pageSize,
  }
  if (query.status) p.status = query.status
  if (query.keyword) p.keyword = query.keyword.trim()
  if (query.aftersaleSn) p.aftersaleSn = query.aftersaleSn.trim()
  if (dateRange.value?.length === 2) {
    p.applyTimeMin = dateRange.value[0]
    p.applyTimeMax = dateRange.value[1]
  }
  return p
})

const load = async () => {
  loading.value = true
  try {
    const data = (await aftersalePage(params.value)) || {}
    // status 可能是数字码（"0"…"7"）也可能是标识串：统一补 statusKey 供操作按钮/页签分支使用
    list.value = (data.list || []).map((row) => ({ ...row, statusKey: aftersaleStatusKey(row.status) }))
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
  query.aftersaleSn = ''
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
  activeRow.value = row
  detailVisible.value = true
}

const act = (row, mode) => {
  // 待收货的「确认收到退货」是独立端点，直接走二次确认，不打开通用处理弹层
  if (mode === 'confirm-receive') {
    confirmReceive(row)
    return
  }
  actionRow.value = row
  actionMode.value = mode
  actionVisible.value = true
}

const confirmReceive = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认已收到买家寄回的商品？确认后售后单 ${row.aftersaleSn} 进入「退款中」。`,
      '确认收到退货',
      { confirmButtonText: '确认收到', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await aftersaleConfirmReceive({ aftersaleId: row.aftersaleId })
  ElMessage.success('已确认收到退货')
  load()
}

const onDrawerAction = ({ mode, row }) => {
  act(row, mode)
}

// 退款动作的响应里才有 refundId/refundAmount（CN-07/CN-08），回填给详情页展示
const onActionDone = (payload) => {
  if (payload?.refund) detailDrawerRef.value?.applyRefundResult(payload.refund)
  load()
}

onMounted(async () => {
  await load()
  const focus = route.query.focus
  if (focus) {
    const hit = list.value.find((r) => String(r.aftersaleId) === String(focus))
    if (hit) {
      openDetail(hit)
    } else {
      try {
        activeRow.value = { aftersaleId: focus }
        detailVisible.value = true
      } catch {
        /* 忽略 */
      }
    }
  }
})
</script>