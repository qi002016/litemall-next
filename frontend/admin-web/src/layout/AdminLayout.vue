<template>
  <div class="admin-shell">
    <aside class="aside">
      <div class="logo"><span class="dot">满</span><span>小满商城后台</span></div>
      <div class="grp">运营 / 客服（单商户 · 演示身份）</div>

      <template v-for="m in menus" :key="m.key">
        <router-link v-if="!m.disabled" :to="m.path" class="nav-item" :class="{ on: isActive(m) }">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
          <span v-if="m.badge && workbench.pendingAftersale" class="n">{{ workbench.pendingAftersale }}</span>
        </router-link>
        <span v-else class="nav-item disabled" :title="m.tip">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
          <span class="soon">R2/R3</span>
        </span>
      </template>
    </aside>

    <div class="admin-main">
      <header class="admin-head">
        <h1>{{ $route.meta.title }}</h1>
        <span class="demo-flag">我是运营 / 客服（演示身份）</span>
        <div class="spacer" />
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon>
            {{ userStore.userInfo?.username || '管理员' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="admin-body">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useWorkbenchStore } from '@/store/workbench'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const workbench = useWorkbenchStore()

/**
 * 导航与 prototype/assets/app.js:ADMIN_NAV 对齐。
 * R1 接入 6 屏；R2-09 接入分类管理 / 品牌管理 / 公告管理 / 账号管理；
 * 优惠券 / 演示工具属 R2 之后，保留灰置入口。
 */
const menus = [
  { key: 'dashboard', label: '工作台', icon: 'Odometer', path: '/dashboard' },
  { key: 'order', label: '订单管理', icon: 'Tickets', path: '/order' },
  { key: 'aftersale', label: '售后处理', icon: 'Refresh', path: '/aftersale', badge: true },
  { key: 'goods', label: '商品与规格', icon: 'Goods', path: '/goods' },
  { key: 'category', label: '分类管理', icon: 'PriceTag', path: '/category' },
  { key: 'brand', label: '品牌管理', icon: 'Collection', path: '/brand' },
  { key: 'stock', label: '库存视图', icon: 'Grid', path: '/stock' },
  { key: 'coupon', label: '优惠券', icon: 'Ticket', disabled: true, tip: '优惠券管理属 R2 后续，暂未接入' },
  { key: 'comment', label: '评价管理', icon: 'ChatDotRound', path: '/comment' },
  { key: 'notice', label: '公告管理', icon: 'Bell', path: '/notice' },
  { key: 'account', label: '账号管理', icon: 'User', path: '/account' },
  { key: 'demo', label: '演示工具', icon: 'Setting', disabled: true, tip: '原型演示工具不进入生产后台（R3）' },
]

const isActive = (m) => route.path === m.path || route.path.startsWith(`${m.path}/`)

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    userStore.logout()
    router.replace('/login')
  }
}

onMounted(() => {
  workbench.refreshPending()
})
</script>