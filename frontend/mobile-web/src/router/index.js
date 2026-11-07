import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

/**
 * 路由与 prototype/ 的 C 端 21 屏一一对应（R1 只实现 R1 范围内的屏）。
 * 底部四 Tab：首页 / 消息 / 购物车 / 我的（对齐 prototype 的 .c-tab）。
 * R3 已交付 AI 助手：/assistant 指向真实会话页，入口只放「我的」与商品详情页，首页不放。
 */
const routes = [
  { path: '/', redirect: '/home' },
  {
    path: '/',
    component: () => import('@/layout/TabLayout.vue'),
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/home/Home.vue'), meta: { tab: 'home', title: '首页' } },
      { path: 'messages', name: 'Messages', component: () => import('@/views/message/Messages.vue'), meta: { tab: 'messages', title: '消息' } },
      { path: 'cart', name: 'Cart', component: () => import('@/views/cart/Cart.vue'), meta: { tab: 'cart', title: '购物车', requiresLogin: true } },
      { path: 'me', name: 'Me', component: () => import('@/views/user/Me.vue'), meta: { tab: 'me', title: '我的', requiresLogin: true } },
    ],
  },
  { path: '/login', name: 'Login', component: () => import('@/views/login/Login.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'Register', component: () => import('@/views/login/Register.vue'), meta: { title: '注册' } },
  { path: '/search', name: 'Search', component: () => import('@/views/search/Search.vue'), meta: { title: '搜索' } },
  { path: '/category', name: 'Category', component: () => import('@/views/category/Category.vue'), meta: { title: '分类' } },
  { path: '/list', name: 'GoodsList', component: () => import('@/views/goods/GoodsList.vue'), meta: { title: '商品列表' } },
  { path: '/goods/:id', name: 'GoodsDetail', component: () => import('@/views/goods/GoodsDetail.vue'), meta: { title: '商品详情' } },
  { path: '/checkout', name: 'Checkout', component: () => import('@/views/order/Checkout.vue'), meta: { title: '确认订单', requiresLogin: true } },
  { path: '/pay/:orderId', name: 'Pay', component: () => import('@/views/pay/Pay.vue'), meta: { title: '演示支付', requiresLogin: true } },
  { path: '/orders', name: 'Orders', component: () => import('@/views/order/Orders.vue'), meta: { title: '我的订单', requiresLogin: true } },
  { path: '/order/:id', name: 'OrderDetail', component: () => import('@/views/order/OrderDetail.vue'), meta: { title: '订单详情', requiresLogin: true } },
  { path: '/aftersale/apply', name: 'AftersaleApply', component: () => import('@/views/aftersale/AftersaleApply.vue'), meta: { title: '申请售后', requiresLogin: true } },
  { path: '/aftersale/:id', name: 'AftersaleDetail', component: () => import('@/views/aftersale/AftersaleDetail.vue'), meta: { title: '售后详情', requiresLogin: true } },
  { path: '/aftersales', name: 'Aftersales', component: () => import('@/views/aftersale/Aftersales.vue'), meta: { title: '我的售后', requiresLogin: true } },
  { path: '/review', name: 'Review', component: () => import('@/views/review/Review.vue'), meta: { title: '待评价', requiresLogin: true } },
  { path: '/address', name: 'Address', component: () => import('@/views/user/Address.vue'), meta: { title: '收货地址', requiresLogin: true } },
  { path: '/coupons', name: 'Coupons', component: () => import('@/views/user/Coupons.vue'), meta: { title: '我的优惠券', requiresLogin: true } },
  { path: '/collect', name: 'Collect', component: () => import('@/views/user/Collect.vue'), meta: { title: '我的收藏', requiresLogin: true } },
  { path: '/topic/:id', name: 'TopicDetail', component: () => import('@/views/topic/TopicDetail.vue'), meta: { title: '专题详情' } },
  // R3：真实 AI 会话页（需登录）。旧占位页 AssistantDisabled.vue 已删除（2026-11-07）
  { path: '/assistant', name: 'Assistant', component: () => import('@/views/assistant/Assistant.vue'), meta: { title: '智能助手', requiresLogin: true } },
  { path: '/:pathMatch(.*)*', redirect: '/home' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.requiresLogin && !userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  document.title = to.meta.title ? `${to.meta.title} · 小满商城` : '小满商城'
  return true
})

export default router