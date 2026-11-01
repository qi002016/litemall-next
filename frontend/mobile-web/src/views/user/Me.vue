<!-- 我的：对齐 prototype/assets/app.js me（2682-2785）；收藏/关注/足迹属 R3，R1 只保留入口占位 -->
<template>
  <div class="c-page">
    <div class="c-head" style="background: linear-gradient(135deg, #ff5a1f, #ff2e4d); color: #fff; border: 0">
      <div style="width: 30px" />
      <div class="title" style="color: #fff">我的</div>
      <div class="right" style="color: #fff" @click="toast('客服为占位入口（需求未定义会话能力）')">
        <span style="display: flex; align-items: center; gap: 4px; color: #fff">
          <AppIcon name="comment" :size="17" />客服
        </span>
      </div>
    </div>

    <div style="background: linear-gradient(135deg, #ff5a1f, #ff2e4d); color: #fff; padding: 0 16px 22px">
      <div class="row" style="gap: 12px">
        <div class="x-mavatar"><AppIcon name="user" :size="28" /></div>
        <div class="grow">
          <div class="bold" style="font-size: 16px">{{ nickname }}</div>
          <div class="tiny" style="opacity: 0.85; margin-top: 4px">
            {{ isLogin ? `${phone || '未绑定手机号'} · 演示账号` : '未登录 · 点此登录' }}
          </div>
        </div>
        <span v-if="isLogin" class="tag" style="background: #ffffff2e; color: #fff">Lv.2 会员</span>
        <button v-else type="button" class="tag" style="background: #ffffff2e; color: #fff; border: 0" @click="router.push('/login')">
          登录
        </button>
      </div>
      <div class="row mt12" style="gap: 16px; font-size: 12px; opacity: 0.92">
        <span>订单 {{ counts.all || 0 }}</span>
        <span>可用券 {{ couponCount }} 张</span>
      </div>
    </div>

    <div class="c-body" style="padding-top: 0">
      <div class="x-tools" style="margin-top: -12px">
        <div v-for="t in tools" :key="t.label" class="x-tool" @click="t.run">
          <div class="ic">
            <AppIcon :name="t.icon" :size="21" />
            <i v-if="t.n" class="n">{{ t.n > 99 ? '99+' : t.n }}</i>
          </div>
          <div class="tiny mt6">{{ t.label }}</div>
        </div>
      </div>

      <div class="card pad mt10">
        <div class="row between">
          <span class="small bold">我的订单</span>
          <span class="tiny dim" @click="router.push('/orders')">全部订单 ›</span>
        </div>
        <div class="row mt12" style="justify-content: space-between">
          <div v-for="s in shortcuts" :key="s.key" style="text-align: center; flex: 1; position: relative" @click="s.run">
            <div style="color: var(--text-2)"><AppIcon :name="s.icon" :size="22" /></div>
            <div class="tiny mt6">{{ s.label }}</div>
            <span v-if="s.n" class="me-badge">{{ s.n > 99 ? '99+' : s.n }}</span>
          </div>
        </div>
      </div>

      <div class="card mt10" style="overflow: hidden">
        <div class="cell clickable" @click="router.push('/coupons')">
          <div class="label">我的优惠券</div>
          <div class="value">{{ couponCount }} 张可用 <AppIcon name="right" :size="14" /></div>
        </div>
        <div class="cell clickable" @click="router.push('/address')">
          <div class="label">收货地址</div>
          <div class="value">{{ addressCount }} 个 <AppIcon name="right" :size="14" /></div>
        </div>
        <div class="cell clickable" @click="router.push('/review')">
          <div class="label">我的评价</div>
          <div class="value">待评价 {{ counts.unreviewed || 0 }} 单 <AppIcon name="right" :size="14" /></div>
        </div>
        <div class="cell clickable" @click="router.push('/aftersales')">
          <div class="label">我的售后</div>
          <div class="value">{{ aftersaleCount }} 单 <AppIcon name="right" :size="14" /></div>
        </div>
        <div class="cell clickable" @click="toast('客服为占位入口（需求未定义会话能力）')">
          <div class="label">客服</div>
          <div class="value">在线客服 9:00–21:00 <AppIcon name="right" :size="14" /></div>
        </div>
      </div>

      <div class="card pad mt10">
        <div class="row between">
          <span class="small bold">演示模式说明</span>
          <span class="tag warn">R1 演示</span>
        </div>
        <div class="tiny dim mt8" style="line-height: 1.8">
          · 商品、订单、售后数据都来自本机后端服务，刷新不丢；<br />
          · 支付只有「模拟成功 / 模拟失败」两个手段，不产生真实扣款；<br />
          · 金额单位一律「分」，展示时统一格式化；<br />
          · 未付款订单 30 分钟内未支付会自动关单，库存与优惠券同时释放。
        </div>
        <div class="row mt10" style="gap: 8px">
          <button type="button" class="btn sm ghost" @click="logout">退出登录</button>
        </div>
      </div>

      <div class="tiny dim center" style="padding: 14px 0">小满商城 · R1 演示版</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { aftersaleList, orderList } from '@/api/order'
import { couponMyList } from '@/api/market'
import { addressList } from '@/api/user'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { toast } from '@/utils/toast'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const counts = ref({})
const couponCount = ref(0)
const addressCount = ref(0)
const aftersaleCount = ref(0)

const isLogin = computed(() => userStore.isLogin)
const nickname = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || (isLogin.value ? '小满用户' : '未登录'))
const phone = computed(() => userStore.userInfo?.phone || userStore.userInfo?.mobile || '')

const tools = computed(() => [
  { label: '收藏', icon: 'star', n: 0, run: () => toast('收藏功能不在 R1 范围（R3 开放）') },
  { label: '关注店铺', icon: 'shop', n: 0, run: () => toast('关注店铺不在 R1 范围（R3 开放）') },
  { label: '足迹', icon: 'clock', n: 0, run: () => toast('浏览足迹不在 R1 范围（R3 开放）') },
  { label: '我的优惠券', icon: 'tag', n: couponCount.value, run: () => router.push('/coupons') },
])

const shortcuts = computed(() => [
  { key: 'unpaid', label: '待付款', icon: 'money', n: counts.value.unpaid || 0, run: () => router.push({ path: '/orders', query: { tab: 'unpaid' } }) },
  { key: 'undelivered', label: '待发货', icon: 'box', n: counts.value.undelivered || 0, run: () => router.push({ path: '/orders', query: { tab: 'undelivered' } }) },
  { key: 'shipped', label: '待收货', icon: 'truck', n: counts.value.shipped || 0, run: () => router.push({ path: '/orders', query: { tab: 'shipped' } }) },
  { key: 'unreviewed', label: '待评价', icon: 'star', n: counts.value.unreviewed || 0, run: () => router.push('/review') },
  { key: 'aftersale', label: '售后', icon: 'refresh', n: counts.value.aftersale || 0, run: () => router.push('/aftersales') },
])

async function loadCounts() {
  if (!isLogin.value) return
  const tabs = ['all', 'unpaid', 'undelivered', 'shipped', 'unreviewed', 'finished', 'aftersale']
  const results = await Promise.all(
    tabs.map(async (t) => {
      try {
        const data = await orderList({ tab: t, pageNo: 1, pageSize: 1 })
        return [t, Number(data?.total || 0)]
      } catch {
        return [t, 0]
      }
    })
  )
  const map = {}
  for (const [k, v] of results) map[k] = v
  counts.value = map
  try {
    const data = await aftersaleList({ pageNo: 1, pageSize: 50 })
    aftersaleCount.value = Number(data?.total || 0)
    counts.value.aftersale = Number(data?.total || 0)
  } catch {
    aftersaleCount.value = 0
  }
}

async function loadMisc() {
  if (!isLogin.value) return
  try {
    const data = await couponMyList()
    const rows = data?.list || (Array.isArray(data) ? data : [])
    couponCount.value = rows.filter((c) => c.status === 'unused' || c.status == null).length
  } catch {
    couponCount.value = 0
  }
  try {
    const data = await addressList()
    addressCount.value = (Array.isArray(data) ? data : data?.list || []).length
  } catch {
    addressCount.value = 0
  }
}

function logout() {
  userStore.logout()
  appStore.reset()
  toast('已退出登录', 'ok')
  router.replace('/home')
}

onMounted(() => {
  loadCounts()
  loadMisc()
})
</script>