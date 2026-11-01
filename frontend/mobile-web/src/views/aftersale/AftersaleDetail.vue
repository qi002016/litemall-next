<!-- 售后详情：对齐 prototype/assets/app.js aftersale/:id（2367-2496）；售后单状态独立于订单主状态 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">售后详情</div>
      <div style="width: 30px" />
    </div>

    <div v-if="loading" class="c-body"><div class="loading-more">加载中…</div></div>

    <template v-else-if="!a">
      <div class="c-body">
        <EmptyState icon="warn" title="售后单不存在" hint="回我的售后列表看看" action="我的售后" @action="router.replace('/aftersales')" />
      </div>
    </template>

    <template v-else>
      <div class="c-body">
        <div class="card pad">
          <div class="row between">
            <div class="bold">{{ asStatusText(a) }}</div>
            <div>
              <span class="tag" :class="asTagClass(a)">{{ asStatusText(a) }}</span>
              <span v-if="a.restockPending" class="tag danger ml6">库存待回补</span>
            </div>
          </div>
          <div class="row mt10" style="flex-wrap: wrap; gap: 4px; align-items: center">
            <template v-for="(k, i) in MAIN_FLOW" :key="k">
              <span v-if="i" class="tiny dim">›</span>
              <span
                class="tag"
                :class="k === key ? 'danger' : 'gray'"
                :style="{ opacity: k === key ? 1 : (skipped(k) ? 0.3 : 0.5) }"
              >
                {{ AS_TEXT[k] }}{{ skipped(k) ? '（跳过）' : '' }}
              </span>
            </template>
          </div>
          <div class="row mt6" style="flex-wrap: wrap; gap: 6px; align-items: center">
            <span class="tiny dim">其他终态</span>
            <span
              v-for="k in TERM_FLOW"
              :key="k"
              class="tag"
              :class="k === key ? 'danger' : 'gray'"
              :style="{ opacity: k === key ? 1 : 0.5 }"
            >
              {{ AS_TEXT[k] }}
            </span>
          </div>
          <div v-if="isTerm" class="tiny dim mt6">当前处于终态，售后单不再流转。</div>
          <div class="tiny dim mt8">售后单号 {{ a.aftersaleSn }} · 提交于 {{ time(a.applyTime) }}</div>
        </div>

        <div v-if="a.restockPending" class="mt10">
          <div class="alert err">
            <div>
              <b>库存待回补</b><br />退款已处理但库存回补失败（规格可能被删除），需在后台「库存视图」或售后单上点「重试回补」（BR-AS-12/16）。
            </div>
          </div>
        </div>

        <div class="card pad mt10">
          <div class="row between" style="margin-bottom: 2px">
            <div class="small bold">{{ asTypeText(a) }}</div>
            <div class="tiny dim">{{ a.reason }}</div>
          </div>
          <div class="row top mt10">
            <div class="x-thumb sm" :class="{ 'has-img': !!pic }">
              <img v-if="pic" :src="pic" :alt="a.goodsName" />
              <template v-else>📦</template>
            </div>
            <div class="grow">
              <div class="small bold clamp2">{{ a.goodsName }}</div>
              <div class="tiny dim mt6">{{ a.specText || '默认规格' }}</div>
            </div>
            <div style="text-align: right; flex: none">
              <PriceText :cent="unitPrice" />
              <div class="tiny dim">×{{ a.number }}</div>
            </div>
          </div>
          <div class="row between" style="border-top: 1px solid var(--line); padding-top: 8px; margin-top: 8px">
            <span class="small">退款金额</span>
            <PriceText :cent="a.amount" cls="price-lg" />
          </div>
          <div v-if="a.comment" class="tiny dim mt6">说明：{{ a.comment }}</div>
          <div v-if="a.images && a.images.length" class="tiny dim mt6">凭证：{{ a.images.length }} 张</div>
        </div>

        <div v-if="a.images && a.images.length" class="card pad mt10">
          <div class="small bold" style="margin-bottom: 8px">凭证图片</div>
          <div class="uploads">
            <div v-for="(im, i) in a.images" :key="i" class="upload-slot filled">
              <img :src="im" alt="凭证" style="width: 100%; height: 100%; object-fit: cover; border-radius: 10px" />
            </div>
          </div>
        </div>

        <div v-if="a.returnAddress" class="card pad mt10">
          <div class="small bold" style="margin-bottom: 6px">退货地址</div>
          <div class="small">{{ returnAddr.name }} {{ returnAddr.phone }}</div>
          <div class="tiny dim mt6">{{ returnAddr.region }} {{ returnAddr.detail }}</div>
          <div v-if="a.returnDeadline" class="tiny mt6" style="color: var(--warn)">
            请在 {{ time(a.returnDeadline) }} 前寄回（超时未寄回将自动关闭，BR-AS-09）
          </div>
        </div>

        <div v-if="a.returnShipSn || a.returnShipCompany" class="card pad mt10">
          <div class="row between">
            <span class="small bold">退货物流</span>
            <span class="small">{{ a.returnShipCompany || '—' }} {{ a.returnShipSn || '' }}</span>
          </div>
          <div v-if="a.returnShipTime" class="tiny dim mt6">寄回时间 {{ time(a.returnShipTime) }}</div>
        </div>

        <div v-if="timeline.length" class="card pad mt10">
          <div class="small bold" style="margin-bottom: 10px">处理进度</div>
          <div class="timeline">
            <div v-for="(t, i) in timeline" :key="i" class="item on" :class="{ now: i === 0 }">
              <div class="it">{{ t.action }}</div>
              <div class="is">{{ time(t.time) }} · {{ t.actorText || t.actor || '' }}</div>
            </div>
          </div>
        </div>

        <div class="tiny dim center" style="padding: 12px 0">售后单状态独立于订单主状态（BR-ORD-08）</div>
      </div>

      <div class="c-actionbar">
        <div class="sum">退款 <PriceText :cent="a.amount" /></div>
        <button v-if="key === 'applied'" type="button" class="btn" @click="cancelAs">撤销申请</button>
        <button v-else-if="key === 'waitReturn'" type="button" class="btn primary" @click="openShip">填写退货单号</button>
        <button v-else-if="key === 'waitReceive'" type="button" class="btn" @click="showShip">查看物流</button>
        <button v-else-if="key === 'refunding'" type="button" class="btn" @click="waitHint">等待商家退款</button>
        <button v-else type="button" class="btn" @click="toOrder">查看订单</button>
        <button type="button" class="btn ghost" @click="toOrder">订单</button>
      </div>
    </template>

    <BottomSheet v-if="shipSheet" title="填写退货物流单号" @close="shipSheet = false">
      <div class="field">
        <div class="label">物流公司</div>
        <input v-model="shipCompany" class="input" placeholder="例如 顺丰速运" />
      </div>
      <div class="field mt10">
        <div class="label">物流单号</div>
        <input v-model="shipSn" class="input" placeholder="例如 SF1234567890" />
      </div>
      <div class="tiny dim mt6">填写后状态转为「待收货」，商家收货后退款（BR-AS-08）。</div>
      <div class="mt12">
        <button type="button" class="btn primary block" :disabled="submitting" @click="submitShip">
          {{ submitting ? '提交中…' : '提交' }}
        </button>
      </div>
    </BottomSheet>

    <BottomSheet v-if="showTrackSheet" title="退货物流" @close="showTrackSheet = false">
      <div class="cell"><div class="label">物流公司</div><div class="value">{{ a.returnShipCompany || '—' }}</div></div>
      <div class="cell"><div class="label">物流单号</div><div class="value">{{ a.returnShipSn || '未填写' }}</div></div>
      <div class="tiny dim mt8">等待商家确认收货，之后进入退款处理。</div>
    </BottomSheet>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomSheet from '@/components/BottomSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import PriceText from '@/components/PriceText.vue'
import { aftersaleCancel, aftersaleDetail, aftersaleReturnShip } from '@/api/order'
import { asStatusKey, asStatusText, asTagClass, asTypeText } from '@/utils/dict'
import { time } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { toast } from '@/utils/toast'
import { confirm } from '@/utils/confirm'

const MAIN_FLOW = ['applied', 'waitReturn', 'waitReceive', 'refunding', 'completed']
const TERM_FLOW = ['closed', 'rejected', 'cancelled']
const AS_TEXT = {
  applied: '申请中',
  waitReturn: '待寄回',
  waitReceive: '待收货',
  refunding: '退款中',
  completed: '退款完成',
  closed: '已关闭',
  rejected: '已驳回',
  cancelled: '已撤销',
}

const route = useRoute()
const router = useRouter()

const a = ref(null)
const loading = ref(true)
const shipSheet = ref(false)
const showTrackSheet = ref(false)
const shipCompany = ref('')
const shipSn = ref('')
const submitting = ref(false)

const key = computed(() => asStatusKey(a.value))
const isTerm = computed(() => TERM_FLOW.includes(key.value))
const pic = computed(() => mediaUrl(a.value?.picUrl || ''))
const unitPrice = computed(() => {
  const n = Number(a.value?.number || 1)
  return n ? Math.round(Number(a.value?.amount || 0) / n) : 0
})
const returnAddr = computed(() => {
  const r = a.value?.returnAddress
  if (!r) return {}
  if (typeof r === 'string') return { name: r, phone: '', region: '', detail: '' }
  return r
})
const timeline = computed(() => (a.value?.timeline || []).slice().reverse())

function skipped(k) {
  return Number(a.value?.type) === 1 && (k === 'waitReturn' || k === 'waitReceive')
}

function toOrder() {
  if (!a.value?.orderId) return router.replace('/orders')
  router.push(`/order/${a.value.orderId}`)
}

async function load() {
  try {
    const data = await aftersaleDetail({ aftersaleId: route.params.id })
    a.value = data
    shipCompany.value = data?.returnShipCompany || ''
    shipSn.value = data?.returnShipSn || ''
  } catch (e) {
    if (String(e?.code) === '404' || e?.code === 404) a.value = null
  } finally {
    loading.value = false
  }
}

async function cancelAs() {
  const ok = await confirm({ title: '撤销申请', body: '撤销后该订单行可重新申请售后（BR-AS-11）。', okText: '撤销申请', danger: true })
    if (!ok) return
  try {
    await aftersaleCancel({ aftersaleId: a.value.aftersaleId })
    toast('售后已撤销', 'ok')
    load()
  } catch {
    /* request 层已提示 */
  }
}

function openShip() {
  shipSheet.value = true
}

function showShip() {
  showTrackSheet.value = true
}

function waitHint() {
  toast(a.value?.restockPending ? '库存回补失败，需运营在后台点「重试回补」' : '已提交退款，等待运营处理')
}

async function submitShip() {
  if (!shipCompany.value.trim() || !shipSn.value.trim()) {
    toast('请填写物流公司与物流单号', 'err')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await aftersaleReturnShip({
      aftersaleId: a.value.aftersaleId,
      returnShipCompany: shipCompany.value.trim(),
      returnShipSn: shipSn.value.trim(),
    })
    toast('退货物流已提交', 'ok')
    shipSheet.value = false
    load()
  } catch {
    /* request 层已提示 */
  } finally {
    submitting.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/aftersales')
}

onMounted(load)
</script>