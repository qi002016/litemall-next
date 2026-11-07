<template>
  <div class="c-page">
    <div class="c-head x-hhead">
      <div class="x-chan">
        <div
          v-for="c in channels"
          :key="c.key"
          class="x-chan-i"
          :class="{ on: c.key === chan }"
          @click="selectChan(c.key)"
        >
          {{ c.label }}
        </div>
        <div class="x-chan-cat" @click="go('/category')">
          <AppIcon name="grid" :size="13" /><span>分类</span>
        </div>
      </div>
      <div class="x-hsearch">
        <div class="x-sbox" @click="go('/search')">
          <span class="x-ico" v-html="SVG_SCAN" />
          <span class="x-sph">搜商品 / 标签，如「卫衣」</span>
          <span class="x-ico" v-html="SVG_CAM" />
        </div>
        <!-- AI 助手入口：与列表页同款胶囊，保证首页也能一眼看到进得去（原 CR-01「首页不放」已按业主 2026-11-07 要求调整） -->
        <button type="button" class="x-aibtn" @click="go('/assistant')">
          <AppIcon name="comment" :size="13" /><span>AI</span>
        </button>
        <div class="x-cartbtn" @click="go('/cart')">
          <AppIcon name="cart" :size="22" />
          <span v-if="app.cartCount" class="badge-dot">{{ app.cartCount > 99 ? '99+' : app.cartCount }}</span>
        </div>
      </div>
    </div>

    <div class="c-body" style="padding-top: 10px">
    <div class="x-bwrap">
      <div class="x-banner" :style="{ background: banner.bg }" @click="goBanner(banner)">
        <img
          v-if="banner.url && !banner.imgBroken"
          class="x-bcover"
          :src="mediaUrl(banner.url)"
          :alt="banner.name"
          @error="banner.imgBroken = true"
        />
        <template v-else>
          <span class="x-btag">{{ banner.tag }}</span>
          <div class="x-btxt">
            <div class="x-bt">{{ banner.title }}</div>
            <div class="x-bs">{{ banner.sub }}</div>
          </div>
          <div class="x-bimg" v-html="BANNER_ART" />
        </template>
      </div>
      <div v-if="banners.length > 1" class="x-bdots">
        <i v-for="(b, i) in banners" :key="i" :class="{ on: i === bannerIdx }" @click="bannerIdx = i" />
      </div>
    </div>

      <div class="x-notice">
        <span class="x-ico" v-html="SVG_BULB" />
        <div class="grow ellipsis">{{ notice }}</div>
        <span class="t">{{ noticeTime }}</span>
      </div>

      <div class="row between" style="margin: 14px 2px 8px">
        <div class="bold" style="font-size: 15px">{{ chanCatId ? catName + ' · 精选' : '为你推荐' }}</div>
        <div class="tiny dim">{{ chanCatId ? '共 ' + total + ' 件' : '在售 ' + total + ' 件' }}</div>
      </div>

      <div v-if="loading" class="loading-more">加载中…</div>
      <template v-else>
        <div v-if="list.length" class="goods-grid">
          <GoodsCard v-for="g in list" :key="g.id" :g="g" @click="go('/goods/' + g.id)" />
        </div>
        <EmptyState v-else icon="grid" title="该分类暂无在售商品" hint="换一个频道看看" />
        <div v-if="list.length && list.length < total" class="loading-more" @click="loadMore">加载更多</div>
        <div v-else-if="list.length" class="tiny dim center" style="padding: 14px 0 4px">已经到底了</div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { goodsList, categoryTree } from '@/api/goods'
import { adList, noticeList } from '@/api/market'
import { useAppStore } from '@/store/app'
import { fromNow } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { SVG_SCAN, SVG_CAM, SVG_BULB, BANNER_ART } from '@/utils/svg'

const router = useRouter()
const app = useAppStore()

const channels = ref([{ key: 'rec', label: '推荐' }])
const chan = ref('rec')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20
const loading = ref(true)

const banners = ref([])
const bannerIdx = ref(0)
const notice = ref('演示环境：支付走模拟分支，不产生真实扣款；订单 30 分钟内未支付将自动关闭。')
const noticeTime = ref('')
let timer = null

const chanCatId = computed(() => (chan.value === 'rec' ? '' : chan.value))
const catName = computed(() => {
  const c = channels.value.find((x) => x.key === chan.value)
  return c ? c.label : ''
})
const banner = computed(
  () =>
    banners.value[bannerIdx.value] || {
      tag: '限时',
      title: '小满商城 · 春夏上新',
      sub: '精选好物 一站购齐',
      bg: 'linear-gradient(120deg,#FF7A18,#FF4D4D)',
      name: '小满商城 · 春夏上新',
      url: '',
      linkType: 0,
      link: '',
      imgBroken: false,
      fallback: true,
    }
)

function go(path) {
  router.push(path)
}

/** 轮播跳转：link 只是商品/专题 ID，绝不当 URL 用；linkType=0 不跳。静态兜底保留 R1 的 /list 行为。 */
function goBanner(b) {
  if (b.fallback) {
    router.push('/list')
    return
  }
  const t = Number(b.linkType)
  if (t === 1 && b.link) router.push('/goods/' + b.link)
  else if (t === 2 && b.link) router.push('/topic/' + b.link)
}

async function loadGoods(reset = false) {
  if (reset) {
    pageNum.value = 1
    loading.value = true
  }
  const params = { pageNum: pageNum.value, pageSize }
  if (chanCatId.value) params.categoryId = chanCatId.value
  try {
    const page = await goodsList(params)
    const rows = page?.list || []
    total.value = Number(page?.total ?? rows.length)
    list.value = pageNum.value === 1 ? rows : list.value.concat(rows)
  } catch {
    if (pageNum.value === 1) {
      list.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function loadMore() {
  pageNum.value += 1
  loadGoods()
}

function selectChan(key) {
  if (key === chan.value) return
  chan.value = key
  loadGoods(true)
}

async function loadCategories() {
  try {
    const cats = await categoryTree()
    const tops = (Array.isArray(cats) ? cats : []).slice(0, 6)
    if (tops.length) {
      channels.value = [{ key: 'rec', label: '推荐' }].concat(
        tops.map((c) => ({ key: String(c.id), label: c.name }))
      )
    }
  } catch {
    /* 分类拉取失败时仅保留「推荐」，不影响首页 */
  }
}

async function loadBanners() {
  try {
    const page = await adList({ position: 'home_banner', limit: 5 })
    const rows = (page?.list || []).filter((a) => a && (a.name || a.url)).slice(0, 5)
    if (rows.length) {
      const grads = ['#FF7A18,#FF4D4D', '#FF8A3D,#FF2741', '#FF6A00,#FF9A3D']
      banners.value = rows.map((a, i) => ({
        tag: '推荐',
        name: a.name || '小满商城',
        title: a.name || '小满商城',
        sub: '限时活动 · 精选好物',
        bg: `linear-gradient(120deg, ${grads[i % 3]})`,
        url: a.url || '',
        linkType: Number(a.linkType ?? 0),
        link: a.link == null ? '' : String(a.link),
        sortOrder: a.sortOrder,
        imgBroken: false,
      }))
      timer = setInterval(() => {
        bannerIdx.value = (bannerIdx.value + 1) % banners.value.length
      }, 4000)
    }
  } catch {
    /* 广告位为空时用静态 banner */
  }
}

async function loadNotices() {
  try {
    const page = await noticeList({ pageNum: 1, pageSize: 1 })
    const rows = page?.list || []
    if (rows.length) {
      notice.value = rows[0].title || rows[0].content || notice.value
      noticeTime.value = fromNow(rows[0].addTime)
    }
  } catch {
    /* 公告为空保持静态提示 */
  }
}

onMounted(() => {
  loadCategories()
  loadBanners()
  loadNotices()
  loadGoods(true)
  app.refreshCartCount()
  app.refreshUnread()
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  timer = null
})
</script>

<style scoped>
/* 广告图铺满 banner；加载失败时回退到模板里的本地渐变 + BANNER_ART 内联 SVG 兜底图 */
.x-bcover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>