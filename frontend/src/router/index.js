// 路由守卫：登录验证 + 角色权限
// 约束文档 8：前端隐藏菜单只是体验，后端 Service 层二次校验
// 风险文档 #49：未登录白屏 → 必须在守卫里处理

import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  // 登录（三端共用）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  // 注册（三角色共用）
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false }
  },

  // ========== 消费者端（移动优先，约束文档 24.1） ==========
  {
    path: '/consumer',
    component: () => import('@/layouts/ConsumerLayout.vue'),
    meta: { requiresAuth: true, userType: 'CONSUMER' },
    children: [
      { path: '', redirect: '/consumer/home' },
      { path: 'home', name: 'ConsumerHome', component: () => import('@/views/consumer/Home.vue'), meta: { title: '首页' } },
      { path: 'groupbuy', name: 'ConsumerGroupBuy', component: () => import('@/views/consumer/GroupBuy.vue'), meta: { title: '邻里团购' } },
      { path: 'my-groupbuys', name: 'ConsumerMyGroupBuys', component: () => import('@/views/consumer/MyGroupBuys.vue'), meta: { title: '我的参团' } },
      { path: 'category', name: 'ConsumerCategory', component: () => import('@/views/consumer/Category.vue'), meta: { title: '分类检索' } },
      { path: 'product/:id', name: 'ConsumerProduct', component: () => import('@/views/consumer/Product.vue'), meta: { title: '商品详情' } },
      { path: 'cart', name: 'ConsumerCart', component: () => import('@/views/consumer/Cart.vue'), meta: { title: '购物车' } },
      { path: 'checkout', name: 'ConsumerCheckout', component: () => import('@/views/consumer/Checkout.vue'), meta: { title: '下单确认' } },
      { path: 'pay/:orderNo', name: 'ConsumerPay', component: () => import('@/views/consumer/Pay.vue'), meta: { title: '模拟支付' } },
      { path: 'orders', name: 'ConsumerOrders', component: () => import('@/views/consumer/Orders.vue'), meta: { title: '我的订单' } },
      { path: 'order/:orderNo', name: 'ConsumerOrderDetail', component: () => import('@/views/consumer/OrderDetail.vue'), meta: { title: '订单详情' } },
      { path: 'favorites', name: 'ConsumerFavorites', component: () => import('@/views/consumer/Favorites.vue'), meta: { title: '我的收藏' } },
      { path: 'profile', name: 'ConsumerProfile', component: () => import('@/views/consumer/Profile.vue'), meta: { title: '我的' } }
    ]
  },

  // ========== 商家端（PC 后台，约束文档 24.2） ==========
  {
    path: '/merchant',
    component: () => import('@/layouts/MerchantLayout.vue'),
    meta: { requiresAuth: true, userType: 'MERCHANT' },
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      { path: 'dashboard', name: 'MerchantDashboard', component: () => import('@/views/merchant/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'products', name: 'MerchantProducts', component: () => import('@/views/merchant/Products.vue'), meta: { title: '商品管理' } },
      { path: 'stock', name: 'MerchantStock', component: () => import('@/views/merchant/Stock.vue'), meta: { title: '库存管理' } },
      { path: 'supply', name: 'MerchantSupply', component: () => import('@/views/merchant/Supply.vue'), meta: { title: '供应计划 ★' } },
      { path: 'orders', name: 'MerchantOrders', component: () => import('@/views/merchant/Orders.vue'), meta: { title: '订单管理' } },
      { path: 'fulfillment', name: 'MerchantFulfillment', component: () => import('@/views/merchant/Fulfillment.vue'), meta: { title: '履约管理' } },
      { path: 'aftersale', name: 'MerchantAftersale', component: () => import('@/views/merchant/Aftersale.vue'), meta: { title: '售后管理' } },
      { path: 'reviews', name: 'MerchantReviews', component: () => import('@/views/merchant/Reviews.vue'), meta: { title: '评价管理' } },
      { path: 'analytics', name: 'MerchantAnalytics', component: () => import('@/views/merchant/Analytics.vue'), meta: { title: '经营分析 ★' } },
      { path: 'groupbuys', name: 'MerchantGroupBuys', component: () => import('@/views/merchant/MerchantGroupBuys.vue'), meta: { title: '团购管理 ★' } },
      { path: 'profile', name: 'MerchantProfile', component: () => import('@/views/merchant/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },

  // ========== 自提点核销员端（极简工作台，V5 扩展） ==========
  {
    path: '/pickup',
    component: () => import('@/layouts/PickupLayout.vue'),
    meta: { requiresAuth: true, userType: 'PICKUP_STAFF' },
    children: [
      { path: '', redirect: '/pickup/fulfillment' },
      { path: 'fulfillment', name: 'PickupFulfillment', component: () => import('@/views/pickup/PickupFulfillment.vue'), meta: { title: '核销工作台' } }
    ]
  },

  // ========== 管理端（PC 后台，约束文档 24.3） ==========
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, userType: 'ADMIN' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '全局概览' } },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/Users.vue'), meta: { title: '用户管理' } },
      { path: 'roles', name: 'AdminRoles', component: () => import('@/views/admin/Roles.vue'), meta: { title: '角色权限' } },
      { path: 'products', name: 'AdminProducts', component: () => import('@/views/admin/Products.vue'), meta: { title: '商品审核' } },
      { path: 'orders', name: 'AdminOrders', component: () => import('@/views/admin/Orders.vue'), meta: { title: '全局订单' } },
      { path: 'aftersale', name: 'AdminAftersale', component: () => import('@/views/admin/Aftersale.vue'), meta: { title: '售后监控' } },
      { path: 'logs', name: 'AdminLogs', component: () => import('@/views/admin/Logs.vue'), meta: { title: '操作日志' } },
      { path: 'communities', name: 'AdminCommunities', component: () => import('@/views/admin/Communities.vue'), meta: { title: '社区管理' } },
      { path: 'pickup-points', name: 'AdminPickupPoints', component: () => import('@/views/admin/PickupPoints.vue'), meta: { title: '自提点管理' } }
    ]
  },

  { path: '/', redirect: '/login' },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth === false) {
    // 已登录访问登录页 → 跳转到对应端
    if (to.path === '/login' && userStore.isLoggedIn) {
      next(userStore.redirectByRole())
      return
    }
    next()
    return
  }

  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 约束文档 8：前端路由守卫做角色检查（体验层面），后端 Service 层二次校验（安全层面）
  if (to.meta.userType && userStore.userType !== to.meta.userType) {
    // 消费者不能访问 /merchant /admin，商家不能访问 /consumer /admin
    next(userStore.redirectByRole())
    return
  }

  next()
})

router.afterEach((to) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} - 社区团购电商平台`
  }
})

export default router
