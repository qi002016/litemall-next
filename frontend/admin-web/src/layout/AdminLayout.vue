<template>
  <el-container class="admin">
    <el-aside width="210px">
      <div class="logo">litemall 运营后台</div>
      <el-menu :default-active="$route.path" router background-color="#001529" text-color="#fff" active-text-color="#409EFF">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ $route.meta.title }}</span>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-icon><UserFilled /></el-icon> {{ userStore.userInfo?.username || '管理员' }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/dashboard', title: '工作台', icon: 'Odometer' },
  { path: '/goods', title: '商品管理', icon: 'Goods' },
  { path: '/order', title: '订单管理', icon: 'Tickets' },
  { path: '/user', title: '用户管理', icon: 'User' },
  { path: '/coupon', title: '优惠券', icon: 'Ticket' },
  { path: '/ad', title: '广告管理', icon: 'Picture' },
  { path: '/topic', title: '专题管理', icon: 'Notebook' },
  { path: '/account', title: '管理员账号', icon: 'Avatar' },
  { path: '/log', title: '操作日志', icon: 'Document' },
]

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    userStore.logout()
    router.replace('/login')
  }
}
</script>

<style scoped>
.admin {
  height: 100%;
}
.el-aside {
  background: #001529;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-weight: bold;
  background: #002140;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #eee;
}
.user {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
