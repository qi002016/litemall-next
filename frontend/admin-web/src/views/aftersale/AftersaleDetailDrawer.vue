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
            <span class="bold num">{{ detail?.aftersaleSn || row?.aftersaleSn || row?.aftersaleId }}</span>
            <StatusTag v-if="detail || row" :text="statusMeta.text" :tone="statusMeta.tone" />
            <StatusTag v-if="detail?.restockPending" text="库存待回补" tone="danger" />
          </div>
          <div class="dim small mt6">
            订单 {{ detail?.orderSn || row?.orderSn || '—' }}｜申请 {{ detail?.applyTime || row?.applyTime || '—' }}
          </div>
        </div>
        <el-button text @click="emit('update:modelValue', false)">关闭</el-button>
      </div>

      <div v-if="error" class="alert err">
        <el-icon><WarningFilled /></el-icon>
        <div>售后详情加载失败：{{ error }}</div>
      </div>

      <template v-if="detail">
        <div class="drawer-section">
          <h3>售后诉求</h3>
          <div class="kv">
            <div><span class="k">类型</span><span class="v">{{ typeMeta.text }}</span></div>
            <div><span class="k">退款金额</span><span class="v price">{{ money(detail.amount) }}</span></div>
            <div><span class="k">理由</span><span class="v">{{ detail.reason || '—' }}</span></div>
            <div><span class="k">售后数量</span><span class="v">{{ detail.number ?? line?.number ?? '—' }}</span></div>
            <div><span class="k">退货单号</span><span class="v num">{{ detail.returnShipSn || '—' }}</span></div>
            <div><span class="k">退货截止</span><span class="v">{{ detail.returnDeadline || '—' }}</span></div>
            <div style="grid-column: 1 / -1">
              <span class="k">退货地址</span><span class="v">{{ detail.returnAddress || '—' }}</span>
            </div>
            <div v-if="detail.closeReason" style="grid-column: 1 / -1">
              <span class="k">关闭原因</span><span class="v">{{ detail.closeReason }}</span>
            </div>
            <div v-if="detail.rejectReason" style="grid-column: 1 / -1">
              <span class="k">驳回理由</span><span class="v">{{ detail.rejectReason }}</span>
            </div>
          </div>
          <div v-if="evidence.length" class="row wrap mt10">
            <el-image
              v-for="(pic, i) in evidence"
              :key="i"
              :src="pic"
              :preview-src-list="evidence"
              :initial-index="i"
              fit="cover"
              class="evidence"
            />
          </div>
        </div>

        <div class="drawer-section">
          <h3>关联订单与商品</h3>
          <div class="line-card">
            <div class="grow">
              <div class="bold">{{ line?.goodsName || detail.goodsName || '—' }}</div>
              <div class="dim small mt6">
                {{ line?.specText || detail.specText || '单一规格' }} × {{ line?.number ?? detail.number ?? '—' }}
              </div>
              <div class="tiny mt6">
                行退款状态：{{ line?.refundStatusText || detail.refundStatusText || '—' }}
                <span v-if="line?.refundedNumber != null">（已退 {{ line.refundedNumber }} 件）</span>
              </div>
            </div>
            <div class="right">
              <div class="price">{{ money(line?.actualAmount ?? detail.amount) }}</div>
              <div class="dim tiny">单价 {{ money(line?.price) }}</div>
            </div>
          </div>
          <div class="kv mt10">
            <div><span class="k">订单状态</span><span class="v">{{ orderStatusMeta(order).text }}</span></div>
            <div><span class="k">订单实付</span><span class="v">{{ money(order?.orderAmount) }}</span></div>
            <div><span class="k">已退款</span><span class="v">{{ money(order?.refundedAmount) }}</span></div>
            <div><span class="k">是否全退</span><span class="v">{{ order?.fullyRefunded ? '已全额退款' : '未全额退款' }}</span></div>
            <div><span class="k">收货人</span><span class="v">{{ order?.consignee || '—' }}</span></div>
            <div><span class="k">电话</span><span class="v">{{ order?.mobile || '—' }}</span></div>
            <div style="grid-column: 1 / -1"><span class="k">地址</span><span class="v">{{ order?.address || '—' }}</span></div>
          </div>
        </div>

        <div class="drawer-section">
          <h3>退款与库存回补</h3>
          <div class="kv">
            <div><span class="k">退款金额</span><span class="v num">{{ money(refundInfo?.refundAmount ?? detail.amount) }}</span></div>
            <div><span class="k">退款单号</span><span class="v num">{{ refundInfo?.refundId ?? '—' }}</span></div>
            <div><span class="k">退款时间</span><span class="v">{{ fmtTime(refundInfo?.refundTime || detail.refundTime) }}</span></div>
            <div>
              <span class="k">回补状态</span>
              <span class="v">
                <span class="tag" :class="detail.restockPending ? 'danger' : 'ok'">
                  {{ detail.restockStatusText || RESTOCK_STATUS[detail.restockStatus] || '—' }}
                </span>
              </span>
            </div>
            <div><span class="k">审核人</span><span class="v">{{ detail.auditAdmin || '—' }}</span></div>
          </div>
          <div v-if="detail.restockPending" class="alert err mt10">资金已退但库存回补失败：点「重试回补并退款」完成回补。</div>
          <div v-else-if="!refundInfo?.refundId && detail.status && aftersaleStatusKey(detail.status) === 'completed'" class="tiny dim mt8">
            退款单号仅在本次退款动作的响应中返回（CN-07 出参），刷新页面后不再展示，属契约口径。
          </div>
        </div>

        <div class="drawer-section">
          <h3>处理进度</h3>
          <div v-if="timeline.length" class="timeline">
            <div v-for="(e, i) in timeline" :key="i" class="item on">
              {{ e.text }}<span class="t">{{ e.time }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无处理记录</div>
        </div>
      </template>

      <div v-if="detail" class="drawer-foot">
        <template v-if="status === 'applied'">
          <el-button type="primary" @click="act('approve')">同意</el-button>
          <el-button type="danger" @click="act('reject')">驳回</el-button>
          <el-button @click="act('close')">关闭</el-button>
        </template>
        <template v-else-if="status === 'waitReturn'">
          <el-button type="primary" @click="act('skip-receive')">跳过收货</el-button>
          <el-button @click="act('close')">关闭</el-button>
        </template>
        <template v-else-if="status === 'waitReceive'">
          <el-button type="primary" @click="onConfirmReceive">确认收到退货</el-button>
          <el-button @click="act('skip-receive')">跳过收货</el-button>
          <el-button @click="act('close')">关闭</el-button>
        </template>
        <template v-else-if="status === 'refunding'">
          <el-button v-if="detail.restockPending" type="danger" @click="act('retry-restock')">重试回补并退款</el-button>
          <el-button v-else type="primary" @click="act('refund')">执行退款</el-button>
        </template>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { aftersaleConfirmReceive, aftersaleDetail } from '@/api/aftersale'
import { RESTOCK_STATUS, aftersaleStatusKey, aftersaleStatusMeta, aftersaleTypeMeta } from '@/constants/dict'
import { fmtTime, money } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  aftersaleId: { type: [String, Number], default: null },
  row: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'action', 'updated'])

const detail = ref(null)
const loading = ref(false)
const error = ref('')
// 退款动作（CN-07/CN-08）的响应，详情接口本身不含 refundId/refundAmount（07:909-931）
const refundInfo = ref(null)

// 后端实测 status 可能是数字码（"0"…"7"）而不是标识串，统一归一后再做按钮分支
const status = computed(() => aftersaleStatusKey(detail.value?.status ?? props.row?.status))
const statusMeta = computed(() => aftersaleStatusMeta(detail.value || props.row || {}))
const typeMeta = computed(() => aftersaleTypeMeta(detail.value || props.row || {}))
const order = computed(() => detail.value?.order || null)
const line = computed(() => detail.value?.line || null)
const evidence = computed(() => {
  const d = detail.value
  if (!d) return []
  const raw = d.picUrls || d.evidence || []
  return Array.isArray(raw) ? raw : []
})
const timeline = computed(() => {
  const d = detail.value
  if (!d) return []
  if (Array.isArray(d.timeline) && d.timeline.length) {
    return d.timeline.map((e) => ({
      text: e.title || e.text || e.action || e.statusText || '处理记录',
      time: e.time || e.createTime || '',
    }))
  }
  const list = []
  if (d.applyTime) list.push({ text: '买家提交售后申请', time: d.applyTime })
  if (d.auditTime) list.push({ text: `运营审核（${d.auditAdmin || '—'}）`, time: d.auditTime })
  if (d.receiveTime) list.push({ text: '确认收到退货', time: d.receiveTime })
  if (d.refundTime) list.push({ text: '退款完成', time: d.refundTime })
  if (d.closeTime) list.push({ text: '售后关闭', time: d.closeTime })
  return list
})

const applyRefundResult = (res) => {
  if (!res) return
  refundInfo.value = { ...res }
  if (String(res.aftersaleId ?? '') === String(props.aftersaleId ?? '')) load()
}

const load = async () => {
  if (!props.aftersaleId) return
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await aftersaleDetail({ aftersaleId: props.aftersaleId })
  } catch (e) {
    error.value = e?.message || '未知错误'
  } finally {
    loading.value = false
  }
}

const act = (mode) => {
  emit('action', { mode, row: { ...(props.row || {}), ...(detail.value || {}) } })
}

const onConfirmReceive = async () => {
  try {
    await ElMessageBox.confirm(
      '确认已收到买家寄回的商品？确认后售后单进入「退款中」，并在退货收货记录中留痕。',
      `确认收到退货 ${detail.value?.aftersaleSn || ''}`,
      { confirmButtonText: '确认收到', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await aftersaleConfirmReceive({ aftersaleId: detail.value.aftersaleId })
  ElMessage.success('已确认收到退货')
  load()
  emit('updated')
}

watch(
  () => [props.modelValue, props.aftersaleId],
  ([visible], prev = []) => {
    // 换单或重新打开时清掉上一单的退款动作结果
    if (!visible || String(prev[1] ?? '') !== String(props.aftersaleId ?? '')) refundInfo.value = null
    if (visible) load()
  },
)

defineExpose({ reload: load, applyRefundResult })
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

.grow {
  flex: 1;
  min-width: 0;
}
.wrap {
  flex-wrap: wrap;
}
.evidence {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  border: 1px solid var(--line);
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