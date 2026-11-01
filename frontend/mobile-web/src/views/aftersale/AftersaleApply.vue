<!-- 申请售后：对齐 prototype/assets/app.js aftersale/apply（2240-2362）；按行申请，只列可申请行（07 CI-91） -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">申请售后</div>
      <div style="width: 30px" />
    </div>

    <div v-if="loading" class="c-body"><div class="loading-more">加载中…</div></div>

    <template v-else-if="!line">
      <div class="c-body">
        <EmptyState
          icon="warn"
          title="当前无法申请售后"
          :hint="notApplyReason"
          action="返回订单"
          @action="router.replace(orderId ? `/order/${orderId}` : '/orders')"
        />
      </div>
    </template>

    <template v-else>
      <div class="c-body">
        <div class="card pad">
          <div class="row top">
            <div class="x-thumb sm" :class="{ 'has-img': !!pic }">
              <img v-if="pic" :src="pic" :alt="line.goodsName" />
              <template v-else>📦</template>
            </div>
            <div class="grow">
              <div class="small bold clamp2">{{ line.goodsName }}</div>
              <div class="tiny dim mt6">{{ line.specText || '默认规格' }}</div>
              <div class="row between mt8">
                <PriceText :cent="line.price" />
                <span class="tiny dim">×{{ line.number }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card mt10" style="overflow: hidden">
          <div v-for="t in types" :key="t.value" class="cell clickable" @click="type = t.value">
            <div class="label" :class="{ bold: type === t.value }">{{ t.text }}</div>
            <div class="value row" style="gap: 8px">
              <span class="tiny dim">{{ t.hint }}</span>
              <span class="cbx" :class="{ on: type === t.value }" />
            </div>
          </div>
        </div>

        <div class="card pad mt10 row between">
          <div>
            <div class="small bold">申请数量</div>
            <div class="tiny dim mt6">最多可申请 {{ maxQty }} 件 · 行实付 {{ money(lineActual) }}</div>
          </div>
          <QtyStepper v-model="number" :min="1" :max="maxQty" />
        </div>

        <div class="card pad mt10">
          <div class="small bold" style="margin-bottom: 8px">售后原因</div>
          <div class="spec-opts">
            <div v-for="r in REASONS" :key="r" class="spec-opt" :class="{ on: reason === r }" @click="reason = r">
              {{ r }}
            </div>
          </div>
        </div>

        <div class="card pad mt10">
          <div class="small bold" style="margin-bottom: 6px">
            补充说明<span class="tiny dim" style="font-weight: 400">（选填）</span>
          </div>
          <textarea v-model="comment" class="textarea" maxlength="200" placeholder="描述一下问题，便于商家处理" />
        </div>

        <div class="card pad mt10">
          <div class="row between" style="margin-bottom: 8px">
            <div class="small bold">上传凭证</div>
            <div class="tiny dim">
              {{ images.length }}/6{{ type === 2 ? ' · 退货退款至少 1 张（BR-AS-21）' : ' · 选填' }}
            </div>
          </div>
          <div class="uploads">
            <div v-for="(im, i) in images" :key="i" class="upload-slot filled" title="点击移除" @click="removeImage(i)">
              <img :src="im" alt="凭证" style="width: 100%; height: 100%; object-fit: cover; border-radius: 10px" />
            </div>
            <div v-if="images.length < 6" class="upload-slot" @click="pickFile">+<br />凭证</div>
          </div>
          <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFile" />
          <div v-if="images.length" class="tiny dim mt6">
            <span style="color: var(--brand)" @click="images = []">清空凭证</span>
          </div>
        </div>

        <div class="card pad mt10 row between">
          <div class="small bold">预计退款金额</div>
          <div><PriceText :cent="refundAmount" /></div>
        </div>

        <div class="alert mt12">
          <AppIcon name="warn" :size="15" />
          <div>同一订单行同时只能有 1 个进行中的售后（BR-AS-04）；退款金额按行实付 × 申请数量计算（BR-AS-05）。</div>
        </div>
      </div>

      <div class="c-actionbar">
        <div class="sum">退款 <PriceText :cent="refundAmount" /></div>
        <button type="button" class="btn primary" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交申请' }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import QtyStepper from '@/components/QtyStepper.vue'
import { aftersaleApply, orderDetail } from '@/api/order'
import { money } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { toast } from '@/utils/toast'

const REASONS = ['不想要了', '商品与描述不符', '尺码/颜色选错', '质量问题', '少件/漏发', '其他']
const TYPE_HINT = {
  1: '无需寄回，商家同意后直接退款',
  2: '需寄回商品，商家收货后退款',
}

const route = useRoute()
const router = useRouter()

const orderId = computed(() => String(route.query.orderId || ''))
const orderGoodsId = computed(() => String(route.query.orderGoodsId || ''))

const loading = ref(true)
const line = ref(null)
const notApplyReason = ref('该订单行当前不支持申请售后')
const types = ref([{ value: 1, text: '仅退款', hint: TYPE_HINT[1] }])
const type = ref(1)
const number = ref(1)
const reason = ref('')
const comment = ref('')
const images = ref([])
const submitting = ref(false)
const fileInput = ref(null)

const maxQty = computed(() => Math.max(1, Number(line.value?.canApply?.maxQty || line.value?.number || 1)))
const pic = computed(() => mediaUrl(line.value?.picUrl || ''))
const lineActual = computed(() => Number(line.value?.actualAmount ?? (Number(line.value?.price || 0) * Number(line.value?.number || 1))))
const refundAmount = computed(() => {
  const n = Number(line.value?.number || 1)
  if (!n) return 0
  return Math.floor((lineActual.value * Number(number.value || 1)) / n)
})

function pickFile() {
  if (images.value.length >= 6) {
    toast('最多上传 6 张凭证', 'err')
    return
  }
  fileInput.value?.click()
}

function onFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast('请选择图片文件', 'err')
    return
  }
  if (file.size > 900 * 1024) {
    toast('单张凭证请小于 900KB', 'err')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    images.value = images.value.concat(String(reader.result || '')).slice(0, 6)
  }
  reader.readAsDataURL(file)
}

function removeImage(i) {
  images.value = images.value.filter((_, idx) => idx !== i)
}

async function submit() {
  if (!type.value) {
    toast('请选择售后类型', 'err')
    return
  }
  if (!reason.value) {
    toast('请选择售后原因', 'err')
    return
  }
  if (type.value === 2 && !images.value.length) {
    toast('退货退款必须上传至少 1 张凭证（BR-AS-21）', 'err')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const data = await aftersaleApply({
      orderGoodsId: orderGoodsId.value,
      orderId: orderId.value,
      type: Number(type.value),
      number: Number(number.value),
      reason: reason.value,
      comment: comment.value || undefined,
      images: images.value,
      amount: refundAmount.value,
    })
    toast('售后申请已提交', 'ok')
    router.replace(`/aftersale/${data.aftersaleId}`)
  } catch {
    /* request 层已提示 */
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace(orderId.value ? `/order/${orderId.value}` : '/orders')
}

onMounted(async () => {
  if (!orderId.value || !orderGoodsId.value) {
    notApplyReason.value = '缺少订单行参数，请从订单详情对应商品行发起申请'
    loading.value = false
    return
  }
  try {
    const data = await orderDetail({ orderId: orderId.value })
    const all = data?.lines || data?.goodsList || []
    const hit = all.find((l) => String(l.orderGoodsId ?? l.id) === orderGoodsId.value)
    if (!hit) {
      notApplyReason.value = '该订单行不存在或已不在此订单中'
      loading.value = false
      return
    }
    const can = hit.canApply
    if (!can || !Array.isArray(can.types) || !can.types.length) {
      notApplyReason.value = can?.reason || '该订单行当前不支持申请售后'
      loading.value = false
      return
    }
    line.value = hit
    types.value = can.types.map((t) => ({ value: Number(t), text: Number(t) === 2 ? '退货退款' : '仅退款', hint: TYPE_HINT[Number(t)] }))
    type.value = Number(can.types[0])
    number.value = Math.min(1, Math.max(1, Number(can.maxQty || 1)))
  } catch {
    notApplyReason.value = '订单信息加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
})
</script>