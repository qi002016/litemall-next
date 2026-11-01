<template>
  <el-drawer
    :model-value="modelValue"
    size="720px"
    direction="rtl"
    :with-header="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="drawer">
      <div class="drawer-head">
        <div>
          <div class="row">
            <span class="bold num sn">{{ detail?.orderSn || orderId }}</span>
            <StatusTag v-if="detail" :text="statusMeta.text" :tone="statusMeta.tone" />
            <StatusTag v-if="detail?.fullyRefunded" text="已全额退款" tone="danger" />
          </div>
          <div class="dim small mt6">
            下单 {{ detail?.addTime || '—' }}<template v-if="detail?.payTime">｜支付 {{ detail.payTime }}</template>
          </div>
        </div>
        <el-button text @click="emit('update:modelValue', false)">关闭</el-button>
      </div>

      <div v-if="error" class="alert err">
        <el-icon><WarningFilled /></el-icon>
        <div>订单详情加载失败：{{ error }}</div>
      </div>

      <template v-if="detail">
        <div class="drawer-section">
          <h3>商品明细</h3>
          <div v-for="line in lines" :key="line.orderGoodsId" class="line-card">
            <div class="grow">
              <div class="bold">{{ line.goodsName }}</div>
              <div class="dim small mt6">
                {{ line.specText || '单一规格' }} × {{ line.number }}
                <span v-if="line.refundStatus" class="tag outline ml6">{{ line.refundStatusText || refundLabel(line.refundStatus) }}</span>
              </div>
              <div v-if="line.aftersaleId" class="tiny mt6 link" @click="goAftersale(line.aftersaleId)">
                售后 {{ line.aftersaleStatusText || line.aftersaleStatus || '' }} · 查看售后单 →
              </div>
            </div>
            <div class="right">
              <div class="price">{{ money(line.actualAmount ?? line.price * line.number) }}</div>
              <div class="dim tiny">单价 {{ money(line.price) }}</div>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <h3>金额</h3>
          <div class="kv">
            <div><span class="k">商品金额</span><span class="v num">{{ money(detail.goodsAmount) }}</span></div>
            <div><span class="k">运费</span><span class="v num">{{ money(detail.freightAmount) }}</span></div>
            <div><span class="k">优惠券</span><span class="v num">-{{ money(detail.couponAmount) }}</span></div>
            <div><span class="k">实付</span><span class="v price">{{ money(detail.orderAmount) }}</span></div>
            <div><span class="k">已退款</span><span class="v num">{{ money(detail.refundedAmount) }}</span></div>
          </div>
        </div>

        <div class="drawer-section">
          <h3>收货信息</h3>
          <div class="kv">
            <div><span class="k">收货人</span><span class="v">{{ detail.consignee || '—' }}</span></div>
            <div><span class="k">电话</span><span class="v">{{ detail.mobile || '—' }}</span></div>
            <div style="grid-column: 1 / -1"><span class="k">地址</span><span class="v">{{ detail.address || '—' }}</span></div>
            <div v-if="detail.message" style="grid-column: 1 / -1">
              <span class="k">买家备注</span><span class="v">{{ detail.message }}</span>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <h3>物流与关闭</h3>
          <div class="kv">
            <div><span class="k">物流公司</span><span class="v">{{ detail.shipChannel || '—' }}</span></div>
            <div><span class="k">物流单号</span><span class="v num">{{ detail.shipSn || '—' }}</span></div>
            <div><span class="k">发货时间</span><span class="v">{{ detail.shipTime || '—' }}</span></div>
            <div>
              <span class="k">单号修改</span>
              <span class="v">
                {{ detail.shipUpdateCount ? `已修改 ${detail.shipUpdateCount} 次` : '未修改' }}
                <span v-if="detail.canUpdateShip" class="tag ok ml6">可改 1 次</span>
              </span>
            </div>
            <div><span class="k">支付截止</span><span class="v">{{ detail.expireTime || '—' }}</span></div>
            <div v-if="detail.closeTypeText"><span class="k">关闭原因</span><span class="v">{{ detail.closeTypeText }}</span></div>
          </div>
        </div>

        <div class="drawer-section">
          <h3>订单时间线</h3>
          <div v-if="timeline.length" class="timeline">
            <div v-for="(e, i) in timeline" :key="i" class="item" :class="{ on: e.on }">
              {{ e.text }}<span class="t">{{ e.time }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无时间线数据</div>
        </div>
      </template>

      <div v-if="detail" class="drawer-foot">
        <el-button v-if="detail.status === 'unpaid'" type="danger" @click="onClose">立即关单</el-button>
        <el-button v-if="detail.status === 'undelivered'" type="primary" @click="openShip">发货</el-button>
        <el-button v-if="detail.canUpdateShip" @click="openUpdateShip">修改物流单号</el-button>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </div>
    </div>

    <OrderShipDialog v-model="shipVisible" :order="shipTarget" :mode="shipMode" @saved="onShipSaved" />
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { WarningFilled } from '@element-plus/icons-vue'
import { orderDetail } from '@/api/order'
import { orderStatusMeta, REFUND_STATUS } from '@/constants/dict'
import { money } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import OrderShipDialog from '@/views/order/OrderShipDialog.vue'
import { closeOrderFlow } from '@/views/order/closeOrder'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderId: { type: [String, Number], default: null },
})
const emit = defineEmits(['update:modelValue', 'updated'])

const router = useRouter()
const detail = ref(null)
const loading = ref(false)
const error = ref('')
const shipVisible = ref(false)
const shipMode = ref('ship')

const lines = computed(() => (Array.isArray(detail.value?.lines) ? detail.value.lines : []))
const statusMeta = computed(() => orderStatusMeta(detail.value))
const shipTarget = computed(() => (detail.value ? { ...detail.value, id: detail.value.id } : null))
const refundLabel = (code) => REFUND_STATUS[code] || '—'

const timeline = computed(() => {
  const d = detail.value
  if (!d) return []
  const list = []
  if (d.addTime) list.push({ text: '提交订单', time: d.addTime, on: true })
  if (d.payTime) list.push({ text: '支付成功', time: d.payTime, on: true })
  if (d.shipTime) list.push({ text: '商家发货', time: d.shipTime, on: true })
  if (d.closeTypeText) list.push({ text: `订单关闭（${d.closeTypeText}）`, time: '', on: true })
  return list
})

const load = async () => {
  if (!props.orderId) return
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await orderDetail(props.orderId)
  } catch (e) {
    error.value = e?.message || '未知错误'
  } finally {
    loading.value = false
  }
}

const openShip = () => {
  shipMode.value = 'ship'
  shipVisible.value = true
}

const openUpdateShip = () => {
  shipMode.value = 'update-ship'
  shipVisible.value = true
}

const onShipSaved = () => {
  load()
  emit('updated')
}

const onClose = async () => {
  try {
    const done = await closeOrderFlow({ id: detail.value.id, orderSn: detail.value.orderSn })
    if (done) {
      load()
      emit('updated')
    }
  } catch {
    /* 用户取消或接口拒绝：保留抽屉，错误提示已由拦截器/确认框给出 */
  }
}

const goAftersale = (aftersaleId) => {
  emit('update:modelValue', false)
  router.push({ path: '/aftersale', query: { focus: String(aftersaleId) } })
}

watch(
  () => [props.modelValue, props.orderId],
  ([visible]) => {
    if (visible) load()
  },
)
</script>

<style scoped>
.drawer {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  padding: 2px 6px 12px;
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--line);
}

.sn {
  font-size: 15px;
}

.grow {
  flex: 1;
  min-width: 0;
}

.ml6 {
  margin-left: 6px;
}

.drawer-foot {
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>