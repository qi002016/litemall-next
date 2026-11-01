<template>
  <div>
    <div class="toolbar">
      <div class="seg">
        <button v-for="p in periods" :key="p.value" :class="{ on: period === p.value }" @click="switchPeriod(p.value)">
          {{ p.label }}
        </button>
      </div>
      <span class="hint">统计口径：{{ periodLabel }}，按支付时间归日，退款金额单列、GMV 不扣退款</span>
      <div class="spacer" />
      <el-button text :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="kpis">
      <div v-for="k in kpis" :key="k.label" class="kpi">
        <div class="t">{{ k.label }}</div>
        <div class="v" :class="{ dim: k.empty }">{{ k.value }}</div>
        <div class="d">{{ k.desc }}</div>
      </div>
    </div>

    <div class="panel mt16">
      <div class="panel-hd">
        <h2>近 7 天订单趋势</h2>
        <span class="hint">柱高 = GMV，悬浮查看支付单数；缺失日期补 0</span>
        <div class="spacer" />
        <span class="dim small">按支付时间归日</span>
      </div>
      <div v-if="trend.length" class="trend">
        <div v-for="d in trend" :key="d.date" class="col" :title="`${d.date}｜支付 ${d.orderCount} 单｜GMV ${money(d.gmv)}`">
          <div class="bar-area">
            <div class="bar-fill" :style="{ height: barHeight(d) }" />
          </div>
          <div class="bar-label">{{ shortDate(d.date) }}</div>
          <div class="bar-num">{{ d.orderCount }}</div>
        </div>
      </div>
      <div v-else class="empty">暂无趋势数据（CI-08 `trend` 未返回，接口就绪后自动展示）</div>
    </div>

    <div class="two-col mt16">
      <div class="panel">
        <div class="panel-hd">
          <h2>待发货前 5 单</h2>
          <div class="spacer" />
          <router-link class="link" to="/order">全部订单 →</router-link>
        </div>
        <div v-if="waitShip.length" class="mini-list">
          <div v-for="o in waitShip" :key="o.id" class="mini-row">
            <div class="grow">
              <div class="sn">{{ o.orderSn }}</div>
              <div class="dim tiny ellipsis">{{ o.goodsSummary || '商品明细见订单详情' }}</div>
            </div>
            <span class="price">{{ money(o.orderAmount) }}</span>
            <el-button size="small" type="primary" @click="openShip(o)">发货</el-button>
          </div>
        </div>
        <div v-else class="empty">暂无待发货订单</div>
      </div>

      <div class="panel">
        <div class="panel-hd">
          <h2>待审核售后前 5 单</h2>
          <div class="spacer" />
          <router-link class="link" to="/aftersale">售后处理台 →</router-link>
        </div>
        <div v-if="pendingAftersales.length" class="mini-list">
          <div v-for="a in pendingAftersales" :key="a.aftersaleId" class="mini-row">
            <div class="grow">
              <div class="sn">{{ a.aftersaleSn }}</div>
              <div class="dim tiny ellipsis">
                {{ aftersaleTypeMeta(a).text }} · {{ a.goodsName || '—' }} × {{ a.number || 1 }}
              </div>
            </div>
            <StatusTag :text="aftersaleStatusMeta(a).text" :tone="aftersaleStatusMeta(a).tone" />
            <el-button size="small" @click="goAftersale(a)">处理</el-button>
          </div>
        </div>
        <div v-else class="empty">暂无待审核售后</div>
      </div>
    </div>

    <div class="panel mt16">
      <div class="panel-hd"><h2>报表口径说明</h2></div>
      <ul class="notes">
        <li>GMV：统计区间内支付成功订单金额合计，不扣退款；退款金额与退款单数单列。</li>
        <li>客单价 = GMV ÷ 支付订单数，向下取整到分；支付订单数为 0 时展示「—」。</li>
        <li>待处理售后 = 状态为「申请中 / 待寄回 / 待收货 / 退款中」的售后单数。</li>
        <li>缺货 / 低库存 SKU：可售库存 ≤ 5 的 SKU 数（阈值恒 5，来自库存视图同口径统计）。</li>
      </ul>
    </div>

    <OrderShipDialog v-model="shipVisible" :order="shipOrder" mode="ship" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { dashboardStats } from '@/api/dashboard'
import { orderPage } from '@/api/order'
import { aftersalePage } from '@/api/aftersale'
import { stockPage } from '@/api/stock'
import { money } from '@/utils/format'
import { aftersaleStatusMeta, aftersaleTypeMeta } from '@/constants/dict'
import StatusTag from '@/components/StatusTag.vue'
import OrderShipDialog from '@/views/order/OrderShipDialog.vue'

const router = useRouter()
const loading = ref(false)
const period = ref('30d')
const periods = [
  { value: 'today', label: '今日' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
]
const stats = ref({})
const trend = ref([])
const waitShip = ref([])
const pendingAftersales = ref([])
const lowStockTotal = ref(null)

const shipVisible = ref(false)
const shipOrder = ref(null)

const periodLabel = computed(() => periods.find((p) => p.value === period.value)?.label || '近 30 天')

const kpis = computed(() => {
  const s = stats.value || {}
  return [
    {
      label: `${periodLabel.value} GMV`,
      value: s.gmv == null ? '—' : money(s.gmv),
      empty: s.gmv == null,
      desc: '不扣退款',
    },
    {
      label: '支付订单数',
      value: s.orderCount == null ? '—' : s.orderCount,
      empty: s.orderCount == null,
      desc: '按支付时间归入区间',
    },
    {
      label: '退款金额',
      value: s.refundAmount == null ? '—' : money(s.refundAmount),
      empty: s.refundAmount == null,
      desc: s.refundCount == null ? '退款单数未返回' : `退款 ${s.refundCount} 单`,
    },
    {
      label: '待处理售后',
      value: s.pendingAftersaleCount == null ? '—' : s.pendingAftersaleCount,
      empty: s.pendingAftersaleCount == null,
      desc: '申请中 / 待寄回 / 待收货 / 退款中',
    },
    {
      label: '待发货',
      value: s.waitShipCount ?? s.undelivered ?? '—',
      empty: (s.waitShipCount ?? s.undelivered) == null,
      desc: '102 待发货',
    },
    {
      label: '缺货 / 低库存 SKU',
      value: lowStockTotal.value == null ? '—' : lowStockTotal.value,
      empty: lowStockTotal.value == null,
      desc: '可售 ≤ 5（阈值恒 5）',
    },
    {
      label: '客单价',
      value: s.avgOrderValue == null ? '—' : money(s.avgOrderValue),
      empty: s.avgOrderValue == null,
      desc: 'GMV ÷ 支付订单数',
    },
  ]
})

const barHeight = (d) => {
  const list = trend.value.map((x) => Number(x.gmv) || 0)
  const max = Math.max(...list, 1)
  const h = Math.round(((Number(d.gmv) || 0) / max) * 100)
  return `${Math.max(h, 2)}%`
}

const shortDate = (date) => (date ? String(date).slice(5) : '—')

const switchPeriod = (value) => {
  period.value = value
  load()
}

const load = async () => {
  loading.value = true
  try {
    const s = (await dashboardStats({ period: period.value })) || {}
    stats.value = s
    trend.value = Array.isArray(s.trend) ? s.trend : []
  } catch {
    stats.value = {}
    trend.value = []
  } finally {
    loading.value = false
  }

  // 三块辅助数据互不阻塞：任一口径未就绪只影响自身卡片
  const [shipRes, aftersaleRes, stockRes] = await Promise.allSettled([
    orderPage({ pageNum: 1, pageSize: 5, status: 'undelivered' }),
    aftersalePage({ pageNum: 1, pageSize: 5, status: 'applied' }),
    stockPage({ pageNum: 1, pageSize: 1, lowStockOnly: true }),
  ])
  waitShip.value = shipRes.status === 'fulfilled' ? shipRes.value?.list || [] : []
  pendingAftersales.value = aftersaleRes.status === 'fulfilled' ? aftersaleRes.value?.list || [] : []
  lowStockTotal.value =
    stockRes.status === 'fulfilled' && stockRes.value?.total != null ? Number(stockRes.value.total) : null
}

const openShip = (order) => {
  shipOrder.value = order
  shipVisible.value = true
}

const goAftersale = (row) => {
  if (!row?.aftersaleId) {
    ElMessage.info('售后单号缺失，已跳转售后处理台')
    router.push('/aftersale')
    return
  }
  router.push({ path: '/aftersale', query: { focus: String(row.aftersaleId) } })
}

onMounted(load)
</script>

<style scoped>
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  height: 190px;
  padding: 6px 4px 0;
}

.trend .col {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.bar-area {
  width: 100%;
  max-width: 46px;
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.bar-fill {
  width: 100%;
  border-radius: 6px 6px 2px 2px;
  background: linear-gradient(180deg, #ff964d, var(--brand));
  min-height: 3px;
}

.bar-label {
  font-size: 11.5px;
  color: var(--text-3);
}

.bar-num {
  font-size: 11px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}

.mini-list .mini-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 2px;
  border-bottom: 1px solid var(--line);
}

.mini-list .mini-row:last-child {
  border-bottom: 0;
}

.mini-row .grow {
  flex: 1;
  min-width: 0;
}

.mini-row .sn {
  font-size: 12.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.notes {
  margin: 0;
  padding-left: 18px;
  color: var(--text-2);
  font-size: 12.5px;
  line-height: 1.9;
}
</style>