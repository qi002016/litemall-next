<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @closed="reset"
  >
    <div class="stack">
      <div v-if="mode === 'approve'" class="alert warn">
        <div v-if="isReturn">
          退货退款：同意后售后单进入「待寄回」，系统写入 <b>7 天</b> 退货截止时间；买家寄回后由运营确认收货。
        </div>
        <div v-else>仅退款：同意后售后单直接进入「退款中」，在此步骤执行退款。</div>
      </div>

      <div v-if="mode === 'reject'" class="alert warn">驳回后售后单进入终态「已驳回」，买家可重新发起（受售后时效与数量约束）。</div>

      <div v-if="mode === 'skip-receive'" class="alert warn">
        跳过收货将直接把售后单推进到「退款中」，操作原因会写入售后操作日志（BR-AS-08）。
      </div>

      <div v-if="mode === 'refund'" class="alert warn">
        退款与库存回补在同一事务内提交：退款成功后订单行标记已退款并回补库存；若回补失败，售后单停留在「退款中」并标记「库存待回补」，可重试。
      </div>

      <div v-if="mode === 'retry-restock'" class="alert err">
        该售后单资金已退但库存回补失败，请重试回补；重复点击不会二次退款（回补走幂等 CAS）。
      </div>

      <div v-if="mode === 'close'" class="alert warn">
        仅「申请中 / 待寄回 / 待收货」可关闭；关闭后售后单进入终态「已关闭」，退款中的售后单不能关闭。
      </div>

      <div class="kv">
        <div><span class="k">售后号</span><span class="v num">{{ row?.aftersaleSn || row?.aftersaleId || '—' }}</span></div>
        <div><span class="k">订单号</span><span class="v num">{{ row?.orderSn || '—' }}</span></div>
        <div><span class="k">类型</span><span class="v">{{ typeMeta.text }}</span></div>
        <div><span class="k">退款金额</span><span class="v price">{{ money(row?.amount) }}</span></div>
      </div>

      <div v-if="mode === 'approve' && isReturn" class="field">
        <label>退货地址（留空沿用全局配置）</label>
        <el-input v-model="form.returnAddress" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="仅覆盖本次响应，不落库" />
      </div>

      <div v-if="mode === 'reject' || mode === 'skip-receive' || mode === 'close'" class="field">
        <label>{{ reasonLabel }} <span class="danger">*</span></label>
        <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="255" show-word-limit :placeholder="reasonPlaceholder" />
        <div v-if="mode === 'reject'" class="row wrap mt8">
          <span
            v-for="r in AFTERSALE_REJECT_REASONS"
            :key="r"
            class="tag outline clickable"
            @click="form.reason = r"
          >
            {{ r }}
          </span>
        </div>
      </div>

      <div v-if="mode === 'approve' || mode === 'refund'" class="field">
        <label>备注（选填，写入操作日志）</label>
        <el-input v-model="form.remark" maxlength="255" placeholder="可填写处理说明" />
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :type="confirmType" :loading="submitting" @click="submit">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  aftersaleApprove,
  aftersaleClose,
  aftersaleReject,
  aftersaleRefund,
  aftersaleRetryRestock,
  aftersaleSkipReceive,
} from '@/api/aftersale'
import { AFTERSALE_REJECT_REASONS, aftersaleTypeMeta } from '@/constants/dict'
import { money } from '@/utils/format'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: { type: String, default: 'approve' },
  row: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'done'])

const submitting = ref(false)
const form = reactive({ returnAddress: '', reason: '', remark: '' })

const isReturn = computed(() => String(props.row?.type) === '2')
const typeMeta = computed(() => aftersaleTypeMeta(props.row || {}))

const title = computed(
  () =>
    ({
      approve: '同意售后',
      reject: '驳回售后',
      'skip-receive': '跳过收货',
      refund: '执行退款',
      'retry-restock': '重试回补并退款',
      close: '关闭售后单',
    })[props.mode] || '处理售后',
)

const reasonLabel = computed(
  () => ({ reject: '驳回理由', 'skip-receive': '跳过收货原因', close: '关闭原因' })[props.mode] || '原因',
)
const reasonPlaceholder = computed(
  () =>
    ({
      reject: '请说明驳回理由，买家可见',
      'skip-receive': '请说明为何跳过收货',
      close: '请说明关闭原因',
    })[props.mode] || '',
)

const confirmText = computed(
  () =>
    ({
      approve: '确认同意',
      reject: '确认驳回',
      'skip-receive': '跳过收货',
      refund: '确认退款',
      'retry-restock': '重试并退款',
      close: '确认关闭',
    })[props.mode] || '确认',
)
const confirmType = computed(() => (props.mode === 'reject' || props.mode === 'close' ? 'danger' : 'primary'))

const reset = () => {
  form.returnAddress = ''
  form.reason = ''
  form.remark = ''
}

const submit = async () => {
  if (!props.row) return
  if (['reject', 'skip-receive', 'close'].includes(props.mode) && !form.reason.trim()) {
    ElMessage.warning(`请填写${reasonLabel.value}`)
    return
  }

  const id = props.row.aftersaleId
  // CN-07/CN-08 出参才带 refundId / refundAmount（07:1069），详情接口没有这两个字段，
  // 因此把动作响应回传给列表/详情页，供「退款与库存回补」区块展示。
  let refundResult = null
  submitting.value = true
  try {
    if (props.mode === 'approve') {
      const data = { aftersaleId: id }
      if (form.returnAddress.trim()) data.returnAddress = form.returnAddress.trim()
      if (form.remark.trim()) data.remark = form.remark.trim()
      const res = await aftersaleApprove(data)
      ElMessage.success(`已同意，售后单进入「${res?.statusText || '下一环节'}」`)
    } else if (props.mode === 'reject') {
      await aftersaleReject({ aftersaleId: id, reason: form.reason.trim() })
      ElMessage.success('已驳回')
    } else if (props.mode === 'skip-receive') {
      await aftersaleSkipReceive({ aftersaleId: id, reason: form.reason.trim() })
      ElMessage.success('已跳过收货，进入退款中')
    } else if (props.mode === 'refund') {
      const data = { aftersaleId: id }
      if (form.remark.trim()) data.remark = form.remark.trim()
      const res = await aftersaleRefund(data)
      refundResult = res
      if (res?.restockPending) {
        ElMessage.warning('退款成功，但库存回补失败，售后单停留在「退款中」，请稍后重试回补')
      } else {
        ElMessage.success('退款完成')
      }
    } else if (props.mode === 'retry-restock') {
      const res = await aftersaleRetryRestock({ aftersaleId: id })
      refundResult = res
      if (res?.restockPending) {
        ElMessage.warning('回补仍未成功，请检查库存服务后重试')
      } else {
        ElMessage.success('回补成功，退款完成')
      }
    } else if (props.mode === 'close') {
      await aftersaleClose({ aftersaleId: id, reason: form.reason.trim() })
      ElMessage.success('售后单已关闭')
    }
    emit('update:modelValue', false)
    emit('done', refundResult ? { aftersaleId: id, refund: refundResult } : null)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.wrap {
  flex-wrap: wrap;
}
.clickable {
  cursor: pointer;
}
</style>