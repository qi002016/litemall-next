import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/Dashboard.vue'), meta: { title: '工作台', icon: 'Odometer' } },
      { path: 'goods', name: 'Goods', component: () => import('@/views/goods/GoodsList.vue'), meta: { title: '商品管理', icon: 'Goods' } },
      { path: 'order', name: 'Order', component: () => import('@/views/order/OrderList.vue'), meta: { title: '订单管理', icon: 'Tickets' } },
      { path: 'user', name: 'User', component: () => import('@/views/user/UserList.vue'), meta: { title: '用户管理', icon: 'User' } },
      { path: 'coupon', name: 'Coupon', component: () => import('@/views/marketing/CouponList.vue'), meta: { title: '优惠券', icon: 'Ticket' } },
      { path: 'ad', name: 'Ad', component: () => import('@/views/marketing/AdList.vue'), meta: { title: '广告管理', icon: 'Picture' } },
      { path: 'topic', name: 'Topic', component: () => import('@/views/marketing/TopicList.vue'), meta: { title: '专题管理', icon: 'Notebook' } },
      { path: 'account', name: 'Account', component: () => import('@/views/system/AccountList.vue'), meta: { title: '管理员账号', icon: 'Avatar' } },
      { path: 'log', name: 'Log', component: () => import('@/views/system/LogList.vue'), meta: { title: '操作日志', icon: 'Document' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  document.title = to.meta.title ? `${to.meta.title} - litemall 运营后台` : 'litemall 运营后台'
})

export default router
