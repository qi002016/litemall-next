import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '工作台', icon: 'Odometer' },
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '订单管理', icon: 'Tickets' },
      },
      {
        path: 'aftersale',
        name: 'Aftersale',
        component: () => import('@/views/aftersale/AftersaleList.vue'),
        meta: { title: '售后处理', icon: 'Refresh' },
      },
      {
        path: 'goods',
        name: 'Goods',
        component: () => import('@/views/goods/GoodsList.vue'),
        meta: { title: '商品与规格', icon: 'Goods' },
      },
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/goods/CategoryList.vue'),
        meta: { title: '分类管理', icon: 'PriceTag' },
      },
      {
        path: 'brand',
        name: 'Brand',
        component: () => import('@/views/goods/BrandList.vue'),
        meta: { title: '品牌管理', icon: 'Collection' },
      },
      {
        path: 'stock',
        name: 'Stock',
        component: () => import('@/views/stock/StockList.vue'),
        meta: { title: '库存视图', icon: 'Grid' },
      },
      {
        path: 'comment',
        name: 'Comment',
        component: () => import('@/views/comment/CommentList.vue'),
        meta: { title: '评价管理', icon: 'ChatDotRound' },
      },
      {
        path: 'notice',
        name: 'Notice',
        component: () => import('@/views/marketing/NoticeList.vue'),
        meta: { title: '公告管理', icon: 'Bell' },
      },
      {
        path: 'account',
        name: 'Account',
        component: () => import('@/views/system/AccountList.vue'),
        meta: { title: '账号管理', icon: 'User' },
      },
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
  document.title = to.meta.title ? `${to.meta.title} - 小满商城后台` : '小满商城后台'
})

export default router