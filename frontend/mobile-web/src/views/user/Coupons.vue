<!-- 优惠券：对齐 prototype/assets/app.js coupons（2923-3018）——领券中心 / 我的券 -->
<template>
  <div class="c-page">
    <div class="c-head">
      <div class="back" @click="goBack"><AppIcon name="back" :size="18" /></div>
      <div class="title">优惠券</div>
      <div style="width: 30px" />
    </div>

    <div class="seg-tabs" style="margin: 10px 12px 0">
      <button type="button" :class="{ on: seg === 'center' }" @click="switchSeg('center')">领券中心</button>
      <button type="button" :class="{ on: seg === 'mine' }" @click="switchSeg('mine')">我的券（{{ mineTotal }}）</button>
    </div>

    <div class="c-body">
      <!-- 领券中心 -->
      <template v-if="seg === 'center'">
        <div class="tiny dim" style="margin-bottom: 8px">
          一人一券：同一张券只能领 1 次（BR-CP-01）；领过与领完会给出不同提示（BR-CP-02/03）。
        </div>
        <div v-if="centerLoading" class="loading-more">加载中…</div>
        <template v-else-if="centerList.length">
          <div v-for="c in centerList" :key="c.id" class="card pad" style="margin-bottom: 10px">
            <div class="row between">
              <div class="grow">
                <div class="bold" style="font-size: 14px">{{ c.name }}</div>
                <div class="tiny dim mt6">满 {{ moneyOf(c.min) }} 可用 · 有效期至 {{ dateOnly(c.endTime) }}</div>
                <div class="tiny dim mt6">
                  剩余 {{ leftOf(c) }} 张
                  <span v-if="leftOf(c) > 0 && leftOf(c) <= 5 && claimState(c) === 'claim'" class="tag warn">仅剩 {{ leftOf(c) }} 张</span>
                  <!-- 四态标签互斥：已领取 / 已领完 / 已停发 不混用（BR-CP-02） -->
                  <span v-if="claimState(c) === 'got'" class="tag ok">已领取</span>
                  <span v-else-if="claimState(c) === 'full'" class="tag danger">已领完</span>
                  <span v-else-if="claimState(c) === 'off'" class="tag gray">已停发</span>
                </div>
                <div v-if="c.desc" class="tiny dim mt6">{{ c.desc }}</div>
              </div>
              <div style="text-align: right">
                <div style="color: var(--price); font-weight: 800; font-size: 22px">
                  <span style="font-size: 13px">¥</span>{{ amountOf(c.discount) }}
                </div>
                <button
                  type="button"
                  class="btn sm mt6"
                  :class="claimState(c) === 'claim' ? 'primary' : 'ghost'"
                  :disabled="claimState(c) !== 'claim' || claiming === String(c.id)"
                  @click="claim(c)"
                >
                  {{ claimText(c) }}
                </button>
              </div>
            </div>
          </div>
        </template>
        <EmptyState v-else icon="tag" title="暂无可领的券" hint="商家配置优惠券后会出现在这里（CI-53）" />
      </template>

      <!-- 我的券 -->
      <template v-else>
        <TabsStrip v-model="subTab" :items="subTabs" />
        <div v-if="mineLoading" class="loading-more">加载中…</div>
        <template v-else-if="mineList.length">
          <div
            v-for="m in mineList"
            :key="m.id"
            class="card pad"
            style="margin-bottom: 10px"
            :style="subTab !== 'unused' ? 'opacity:.72' : ''"
          >
            <div class="row between">
              <div class="grow">
                <div class="bold" style="font-size: 14px">{{ m.name }}</div>
                <div class="tiny dim mt6">满 {{ moneyOf(m.min) }} 可用 · {{ dateOnly(m.endTime) }} 到期</div>
                <div class="mt8">
                  <span class="tag" :class="subTab === 'unused' ? 'ok' : 'gray'">{{ subTabText(subTab) }}</span>
                  <span v-if="subTab === 'unused'" class="tiny dim">下单时自动可选</span>
                </div>
              </div>
              <div style="color: var(--price); font-weight: 800; font-size: 22px">
                <span style="font-size: 13px">¥</span>{{ amountOf(m.discount) }}
              </div>
            </div>
          </div>
        </template>
        <EmptyState
          v-else
          icon="tag"
          :title="subTab === 'unused' ? '还没有可用的券' : '这里还是空的'"
          :hint="subTab === 'unused' ? '去「领券中心」领一张，下单时自动可选' : ''"
        >
          <button v-if="subTab === 'unused'" type="button" class="btn primary" @click="switchSeg('center')">去领券</button>
        </EmptyState>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import TabsStrip from '@/components/TabsStrip.vue'
import { couponList, couponMyList, couponReceive } from '@/api/market'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const seg = ref(route.query.tab === 'mine' ? 'mine' : 'center')
const subTab = ref(['unused', 'used', 'expired'].includes(String(route.query.sub)) ? String(route.query.sub) : 'unused')

const centerList = ref([])
const centerLoading = ref(false)
const mineList = ref([])
const mineLoading = ref(false)
const mineTotal = ref(0)
const claiming = ref('')
const mineIds = ref([])
// CI-54 失败态：按券记录服务端 result（already_received/sold_out/disabled），保证四种文案不混用
const failState = reactive({})

const subTabs = ref([
  { key: 'unused', label: '未使用', count: 0 },
  { key: 'used', label: '已使用', count: 0 },
  { key: 'expired', label: '已过期', count: 0 },
])

const gotIds = computed(() => mineIds.value)

function subTabText(k) {
  return { unused: '可用', used: '已使用', expired: '已过期' }[k] || '可用'
}

/** 06 §4：金额对外一律整数分。若后端仍返回元（小数），按元×100 兜底并告警。 */
function toCent(v) {
  if (v == null || v === '') return 0
  if (typeof v === 'string' && v.includes('.')) {
    console.warn('[coupons] 金额疑似元单位（应遵循 06 §4 整数分）：', v)
    return Math.round(Number(v) * 100)
  }
  if (typeof v === 'number' && !Number.isInteger(v)) {
    console.warn('[coupons] 金额疑似元单位（应遵循 06 §4 整数分）：', v)
    return Math.round(v * 100)
  }
  return Number(v) || 0
}

function amountOf(v) {
  return (toCent(v) / 100).toFixed(2)
}

function moneyOf(v) {
  return '¥' + amountOf(v)
}

function dateOnly(t) {
  if (!t) return '永久有效'
  return String(t).slice(0, 10)
}

function leftOf(c) {
  if (c.leftCount == null) return 0
  if (Number(c.leftCount) < 0) return 999
  return Number(c.leftCount)
}

/**
 * CI-54 四态：success / already_received（已领过）/ sold_out（已领完）/ disabled（已停发）。
 * 领券中心只下发可领的券（CouponVO 无 status 字段），故「已停发」只可能来自领取失败的 result，
 * 不能靠列表字段臆断。
 */
function claimState(c) {
  const id = String(c.id)
  const f = failState[id]
  if (f === 'already_received' || gotIds.value.includes(id)) return 'got'
  if (f === 'sold_out') return 'full'
  if (f === 'disabled') return 'off'
  if (c.leftCount != null && Number(c.leftCount) === 0) return 'full'
  return 'claim'
}

function claimText(c) {
  if (claiming.value === String(c.id)) return '领取中…'
  return { off: '已停发', got: '已领取', full: '已领完', claim: '立即领取' }[claimState(c)]
}

async function loadCenter() {
  centerLoading.value = true
  try {
    const page = await couponList({ pageNum: 1, pageSize: 50 })
    centerList.value = page?.list || (Array.isArray(page) ? page : [])
  } catch {
    centerList.value = []
  } finally {
    centerLoading.value = false
  }
}

async function loadMineIds() {
  try {
    const page = await couponMyList({ pageNum: 1, pageSize: 100 })
    const rows = page?.list || []
    mineIds.value = rows.map((r) => String(r.couponId ?? r.id))
    mineTotal.value = Number(page?.total ?? rows.length)
  } catch {
    mineIds.value = []
    mineTotal.value = 0
  }
}

async function loadMine() {
  mineLoading.value = true
  try {
    const page = await couponMyList({ pageNum: 1, pageSize: 50, status: subTab.value })
    mineList.value = page?.list || []
    subTabs.value = subTabs.value.map((t) =>
      t.key === subTab.value ? { ...t, count: Number(page?.total ?? mineList.value.length) } : t
    )
  } catch {
    mineList.value = []
  } finally {
    mineLoading.value = false
  }
}

async function claim(c) {
  if (claimState(c) !== 'claim') return
  const id = String(c.id)
  claiming.value = id
  try {
    // CI-54：成功 HTTP 200 + result=success；失败 code≠200 但 data 仍带 {result,message}。
    // 用 silent 抑制默认 toast，按四态给出各自文案（BR-CP-02 已领过 ≠ 已领完）。
    const res = await couponReceive({ couponId: id }, { silent: true })
    if (res?.result === 'success') {
      toast('领取成功，去「我的券」查看', 'ok')
      await Promise.all([loadCenter(), loadMineIds()])
    } else {
      applyFail(c, res?.result, res?.message)
    }
  } catch (e) {
    applyFail(c, e?.data?.result, e?.message)
  } finally {
    claiming.value = ''
  }
}

const FAIL_TEXT = {
  already_received: '你已领过这张券，去「我的券」查看',
  sold_out: '这张券已领完，下次早点来',
  disabled: '该券已停发，暂不可领取',
}

function applyFail(c, result, message) {
  const id = String(c.id)
  const known = Object.prototype.hasOwnProperty.call(FAIL_TEXT, result)
  if (known) failState[id] = result
  toast(known ? message || FAIL_TEXT[result] : message || '领取失败，请稍后再试', 'err')
  // 只刷新对应数据，避免把「已领过」误刷新成「已领完」的展示
  if (result === 'already_received') loadMineIds()
  if (result === 'sold_out' || result === 'disabled') loadCenter()
}

function switchSeg(v) {
  seg.value = v
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/me')
}

watch(subTab, () => loadMine())

watch(seg, (v) => {
  if (v === 'center') loadCenter()
  else {
    loadMineIds()
    loadMine()
  }
})

onMounted(() => {
  loadCenter()
  loadMineIds()
  if (seg.value === 'mine') loadMine()
})
</script>