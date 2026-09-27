import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  { path: '/', redirect: '/home' },
  {
    path: '/',
    component: () => import('@/layout/DefaultLayout.vue'),
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/home/Home.vue'), meta: { tab: 'home', title: '首页' } },
      { path: 'category', name: 'Category', component: () => import('@/views/category/Category.vue'), meta: { tab: 'category', title: '分类' } },
      { path: 'cart', name: 'Cart', component: () => import('@/views/cart/Cart.vue'), meta: { tab: 'cart', title: '购物车', requiresLogin: true } },
      { path: 'user', name: 'User', component: () => import('@/views/user/User.vue'), meta: { tab: 'user', title: '我的', requiresLogin: true } },
    ],
  },
  { path: '/login', name: 'Login', component: () => import('@/views/login/Login.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'Register', component: () => import('@/views/login/Register.vue'), meta: { title: '注册' } },
  { path: '/search', name: 'Search', component: () => import('@/views/search/Search.vue'), meta: { title: '搜索' } },
  { path: '/goods', name: 'GoodsList', component: () => import('@/views/goods/GoodsList.vue'), meta: { title: '商品列表' } },
  { path: '/goods/:id', name: 'GoodsDetail', component: () => import('@/views/goods/GoodsDetail.vue'), meta: { title: '商品详情' } },
  { path: '/order/confirm', name: 'OrderConfirm', component: () => import('@/views/order/OrderConfirm.vue'), meta: { title: '确认订单', requiresLogin: true } },
  { path: '/order', name: 'OrderList', component: () => import('@/views/order/OrderList.vue'), meta: { title: '我的订单', requiresLogin: true } },
  { path: '/order/:id', name: 'OrderDetail', component: () => import('@/views/order/OrderDetail.vue'), meta: { title: '订单详情', requiresLogin: true } },
  { path: '/user/address', name: 'AddressList', component: () => import('@/views/user/AddressList.vue'), meta: { title: '收货地址', requiresLogin: true } },
  { path: '/user/coupons', name: 'Coupons', component: () => import('@/views/user/Coupons.vue'), meta: { title: '我的优惠券', requiresLogin: true } },
  { path: '/user/coupon-center', name: 'CouponCenter', component: () => import('@/views/user/CouponCenter.vue'), meta: { title: '领券中心', requiresLogin: true } },
  { path: '/user/footprint', name: 'Footprint', component: () => import('@/views/user/Footprint.vue'), meta: { title: '浏览记录', requiresLogin: true } },
  { path: '/user/setting', name: 'Setting', component: () => import('@/views/user/Setting.vue'), meta: { title: '设置', requiresLogin: true } },
  { path: '/user/profile', name: 'Profile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人信息', requiresLogin: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 登录守卫
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresLogin && !userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  document.title = to.meta.title ? `${to.meta.title} - litemall` : 'litemall 商城'
})

export default router
