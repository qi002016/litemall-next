<template>
  <el-dialog
    :model-value="modelValue"
    :title="isUpdate ? '修改物流单号' : '订单发货'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div class="alert warn">
      <el-icon><WarningFilled /></el-icon>
      <div v-if="isUpdate">
        发货后物流单号仅允许修改 1 次，修改会留操作日志（BR-ORD-10 / BR-ADM-03）。
      </div>
      <div v-else>只有待发货订单可以发货，物流公司与单号均不能为空（BR-ORD-02）。</div>
    </div>

    <div class="order-line">
      <span class="dim small">订单号</span>
      <span class="bold num">{{ order?.orderSn || '—' }}</span>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px" class="mt12" @submit.prevent>
      <el-form-item label="物流公司" prop="shipChannel">
        <el-input v-model="form.shipChannel" maxlength="64" placeholder="如：顺丰速运" />
      </el-form-item>
      <el-form-item label="物流单号" prop="shipSn">
        <el-input v-model="form.shipSn" maxlength="64" placeholder="请输入物流单号" />
      </el-form-item>
      <el-form-item v-if="isUpdate" label="修改原因">
        <el-input v-model="form.reason" maxlength="255" placeholder="选填，写入操作日志" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">{{ isUpdate ? '保存修改' : '确认发货' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { orderShip, orderUpdateShip } from '@/api/order'
import { DEFAULT_SHIP_CHANNEL } from '@/constants/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null },
  mode: { type: String, default: 'ship' }, // ship | update-ship
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref()
const saving = ref(false)
const isUpdate = computed(() => props.mode === 'update-ship')
const form = reactive({ shipChannel: DEFAULT_SHIP_CHANNEL, shipSn: '', reason: '' })

const rules = {
  shipChannel: [{ required: true, whitespace: true, message: '请输入物流公司', trigger: 'blur' }],
  shipSn: [{ required: true, whitespace: true, message: '请输入物流单号', trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.shipChannel = props.order?.shipChannel || DEFAULT_SHIP_CHANNEL
    form.shipSn = props.order?.shipSn || ''
    form.reason = ''
    formRef.value?.clearValidate()
  },
)

const onSubmit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      orderId: props.order?.id,
      shipChannel: form.shipChannel.trim(),
      shipSn: form.shipSn.trim(),
    }
    if (isUpdate.value) {
      if (form.reason.trim()) payload.reason = form.reason.trim()
      await orderUpdateShip(payload)
      ElMessage.success('物流单号已修改')
    } else {
      await orderShip(payload)
      ElMessage.success('已发货')
    }
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.order-line {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>