// API 模块汇总：按后端 Controller 对应
// 约束文档 1.2：统一封装 axios，统一处理 token、错误码

import request from '@/utils/request'

// ============ 认证 ============
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)
export const checkUsername = (u) => request.get('/auth/check-username', { params: { username: u } })
export const checkPhone = (p) => request.get('/auth/check-phone', { params: { phone: p } })
export const getCommunities = () => request.get('/auth/communities')
export const getRegisterPickupPoints = (communityId) => request.get('/auth/pickup-points', { params: { communityId } })

// ============ 用户个人中心 ============
export const getMyProfile = () => request.get('/users/me/profile')
export const updateMyProfile = (data) => request.put('/users/me', data)
export const setMyAvatar = (avatarUrl) => request.post('/users/me/avatar', { avatarUrl })
export const changeMyPassword = (oldPassword, newPassword) => request.post('/users/me/password', { oldPassword, newPassword })
// 头像上传 — 用 axios 实例（自动识别 FormData 不会 JSON 化，拦截器自动带 token）
export const uploadAvatar = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/upload/avatar', fd).then(data => {
    if (data?.url) return data.url
    throw new Error('上传失败')
  })
}

// ============ 自提点 ============
export const getPickupPoints = (communityId, all) => request.get('/pickup-points', { params: { communityId, all } })
export const createPickupPoint = (data) => request.post('/pickup-points', data)
export const updatePickupPoint = (id, data) => request.put(`/pickup-points/${id}`, data)
export const deletePickupPoint = (id) => request.delete(`/pickup-points/${id}`)

// ============ 商品 ============
export const getProducts = (params) => request.get('/products', { params })
export const getProduct = (id, communityId) => request.get(`/products/${id}`, { params: { communityId } })

// ============ 社区（公开 · 商品详情页强制先选）============
export const getActiveCommunities = () => request.get('/communities/active')
export const searchProducts = (params) => request.get('/products', { params })
export const getRecentViewed = () => request.get('/products/recent-viewed')
export const getFilterableAttrs = () => request.get('/products/filterable-attr-keys')
// 约束 28.22: 后端 filterable-attr-keys 只返回 key[]，前端需调此接口组装完整筛选器
export const getAttrValues = (key) => request.get('/products/attr-values', { params: { key } })
// ============ 分类（后端嵌在 /products 下：/products/categories/tree）============
export const getCategories = () => request.get('/products/categories/tree')

// ============ 购物车 ============
export const getCart = () => request.get('/cart')
export const addToCart = (data) => request.post('/cart', null, { params: data })
export const updateCartItemQty = (id, quantity) => request.put(`/cart/${id}/quantity`, null, { params: { quantity } })
export const updateCartItemSelected = (id, selected) => request.put(`/cart/${id}/selected`, null, { params: { selected } })
export const removeCartItem = (id) => request.delete(`/cart/${id}`)
export const clearCart = () => request.delete('/cart')

// ============ 商品收藏（消费者端）============
export const favoriteToggle = (productId) => request.post('/favorites/toggle', null, { params: { productId } })
export const favoriteStatus = (productId) => request.get('/favorites/status', { params: { productId } })
export const favoriteList = (params) => request.get('/favorites', { params })
export const favoriteRemove = (favoriteId) => request.delete(`/favorites/item/${favoriteId}`)
export const favoriteClear = () => request.delete('/favorites')

// ============ 订单 ============
export const createOrder = (data) => request.post('/orders', data)
export const getMyOrders = () => request.get('/orders/mine')
export const getOrder = (orderNo) => request.get(`/orders/no/${orderNo}`)

// ============ 支付 ============
export const createPayment = (orderId) => request.post('/payments', null, { params: { orderId } })
export const mockPaymentCallback = (paymentNo) => request.post(`/payments/callback/${paymentNo}`)

// ============ 履约 ============
// 约束 28.21: 后端 /fulfillments 统一前缀，参数用 requestParam 不是 pathVariable
export const confirmPickup = (fulfillmentId) => request.post(`/fulfillments/${fulfillmentId}/confirm-pickup`)
export const listFulfillments = (params) => request.get('/fulfillments/mine', { params })

// ============ 售后（v1.14 · 约束 5.5 售后状态机）============
// 约束 7.2：查询用 GET，创建/变更用 POST + @RequestBody；路径变量后端已加 \d+ 正则约束
export const applyAftersale     = (data)       => request.post('/aftersales', data)
export const getMyAftersales    = (params)     => request.get('/aftersales/mine', { params })
export const getMerchantAftersales = (params)  => request.get('/aftersales/merchant', { params })
export const getMerchantAftersaleStats = ()    => request.get('/aftersales/merchant/stats')
export const getAftersaleDetail = (id)         => request.get(`/aftersales/${id}`)
export const auditAftersale     = (id, data)   => request.post(`/aftersales/${id}/audit`, data)

// ============ Admin 售后管理（只读监控 + 可强制介入）============
export const getAdminAftersales = (params)     => request.get('/admin/aftersales', { params })
export const getAdminAftersaleStats = (params) => request.get('/admin/aftersales/stats', { params })
export const adminAuditAftersale = (id, data)  => request.post(`/admin/aftersales/${id}/audit`, data)
export const adminForceRefund    = (id, data)  => request.post(`/admin/aftersales/${id}/force-refund`, data)

// ============ 评价 ============
// 写评价（需登录）
export const createReview = (orderItemId, rating, content, isAnonymous) =>
  request.post('/reviews', null, { params: { orderItemId, rating, content, isAnonymous } })
// 公开看评价（匿名可访问，后端 WebMvcConfig 已放行）
export const getReviewsBySku    = (skuId)     => request.get(`/reviews/sku/${skuId}`)
export const getReviewAvg       = (skuId)     => request.get(`/reviews/avg/${skuId}`)
export const getReviewDist      = (skuId)     => request.get(`/reviews/distribution/${skuId}`)
// Product 粒度聚合（前端"全部规格评价"tab）
export const getReviewsByProduct = (productId)  => request.get(`/reviews/product/${productId}`)
export const getReviewAvgProduct = (productId)  => request.get(`/reviews/avg-product/${productId}`)
export const getReviewDistProduct = (productId) => request.get(`/reviews/distribution-product/${productId}`)
// 商家评价管理（需登录 + 商家角色）
export const getMerchantReviews = (rating) => request.get('/reviews/merchant', { params: rating ? { rating } : {} })
export const replyReview    = (id, replyContent) => request.post(`/reviews/${id}/reply`, null, { params: { replyContent } })
export const editReply      = (id, replyContent) => request.put(`/reviews/${id}/reply`, null, { params: { replyContent } })

// ============ 经营分析 ============
export const getMerchantAnalytics = (params) => request.get('/analytics/merchant', { params })
export const getMerchantSummary = () => request.get('/analytics/merchant/summary')
export const getMerchantGmvTrend = (params) => request.get('/analytics/merchant/gmv-trend', { params })
export const getMerchantTopProducts = (n) => request.get('/analytics/merchant/top-products', { params: n ? { n } : {} })
export const getAdminAnalytics = (params) => request.get('/analytics/admin', { params })
export const getMerchantForecastVsActual = () => request.get('/analytics/merchant/forecast-vs-actual')

// ============ 个性化推荐 ============
// 约束 L753-L763：仅 consumer 模块调用，商家/管理端不可见
export const getRecommendHome = (communityId, personalized = true) =>
  request.get('/recommend/home', { params: { communityId, personalized } })
export const recordBrowse = (data) => request.post('/recommend/browse', data)
export const recordCart = (productId, categoryId, communityId) =>
  request.post('/recommend/cart', null, { params: { productId, categoryId, communityId } })
export const recordBuy = (productId, categoryId, communityId) =>
  request.post('/recommend/buy', null, { params: { productId, categoryId, communityId } })

// ============ 社区团购 ============
export const getGroupBuys = (communityId) =>
  request.get('/group-buys', { params: communityId ? { communityId } : {} })
export const getGroupBuyDetail = (id) => request.get(`/group-buys/${id}`)
export const joinGroupBuy = (id, body) =>
  request.post(`/group-buys/${id}/join`, body)
export const getMyGroupBuys = () => request.get('/group-buys/mine')

// ============ 需求预测 ============
export const triggerForecast = (data) => request.post('/forecast/trigger', data)
export const getForecastTasks = (params) => request.get('/forecast/tasks', { params })
export const getForecastResults = (taskId) => request.get(`/forecast/results/${taskId}`)
export const getForecastMetrics = (taskId) => request.get(`/forecast/metrics/${taskId}`)

// ============ 商家库存管理 ============
// ============ 社区列表 ============
export const getCommunitiesActive = () => request.get('/communities/active')

// 约束文档 11：列表接口必须分页，参数统一 pageNum / pageSize（默认 1 / 10，最大 100）
export const getMyStocks = (params) => request.get('/supply/stocks/mine', { params })
// 按 Product×社区 归组视图（方案 B）：同一商品多 SKU 汇总成一行，状态取最严重
export const getMyStocksGrouped = (params) => request.get('/supply/stocks/mine/grouped', { params })
// 库存总览：统计卡 + 近 N 天出入库趋势（后端统一统计口径，前端不二次计算）
export const getStockOverview = (params) => request.get('/supply/stocks/overview', { params })
// 库存流水分页查询：changeType / orderNo / stockId / skuId / communityId / 日期区间
export const getStockRecords = (params) => request.get('/supply/stocks/records', { params })
export const adjustStock = (id, data) => request.post(`/supply/stocks/${id}/adjust`, data)

// ============ Admin 库存管理（全商家视角，只读） ============
export const getAdminStocks = (params) => request.get('/admin/stocks', { params })
export const getAdminStockOverview = (params) => request.get('/admin/stocks/overview', { params })
export const getAdminStockRecords = (params) => request.get('/admin/stocks/records', { params })

// ============ 商家订单管理 ============
export const getMerchantOrders = (status) => request.get('/orders/merchant', { params: status ? { status } : {} })
export const getOrderDetail = (id) => request.get(`/orders/${id}`)
export const cancelOrder = (id, reason) => request.post(`/orders/${id}/cancel`, null, { params: { reason } })
export const assignFulfillment = (orderId, fulfillmentType, pickupPointId) =>
  request.post('/fulfillments/assign', null, { params: { orderId, fulfillmentType, ...(pickupPointId ? { pickupPointId } : {}) } })
export const transitionFulfillment = (id, toStatus, reason) =>
  request.post(`/fulfillments/${id}/transition`, null, { params: { toStatus, ...(reason ? { reason } : {}) } })
// v1.13：商家专用端点（PROCESSING → PENDING_PICKUP / COMPLETED）
export const markPickedUpFulfillment = (id, reason) =>
  request.post(`/fulfillments/${id}/mark-picked-up`, null, { params: { ...(reason ? { reason } : {}) } })
export const markDeliveredFulfillment = (id, reason) =>
  request.post(`/fulfillments/${id}/mark-delivered`, null, { params: { ...(reason ? { reason } : {}) } })
export const getMyFulfillments = (status, fulfillmentType) =>
  request.get('/fulfillments/mine', { params: { ...(status ? { status } : {}), ...(fulfillmentType ? { fulfillmentType } : {}) } })
// ============ 供应计划（v1.13 + P1 增强 · 约束 6.1）============
export const createSupplyPlan   = (data)       => request.post('/supply-plans', data)
export const adjustSupplyPlan   = (data)       => request.post('/supply-plans/adjust', data)
export const confirmSupplyPlan  = (planId)     => request.post('/supply-plans/confirm', { planId })
export const arriveSupplyPlan   = (planId, actualQty) => request.post('/supply-plans/arrive', { planId, ...(actualQty !== undefined && actualQty !== null ? { actualQty } : {}) })
export const cancelSupplyPlan   = (planId, reason)    => request.post('/supply-plans/cancel', { planId, reason })
export const getSupplyPlans     = (params = {}) => request.post('/supply-plans/mine', params)
export const batchCreateSupplyPlans = (data)   => request.post('/supply-plans/batch-create', data)
export const getSupplySuggestions   = (params = {}) => request.post('/supply-plans/suggest', params)

// ============ 管理员端全局概览 ============
// 约束文档 §23 管理员职责含"全局统计"，§11 口径统一后端聚合
export const getAdminDashboardOverview = (params) => request.get('/admin/dashboard/overview', { params })

// ============ 管理员端用户管理 ============
// 约束文档 L602 RBAC / L736 最小必要权限
export const adminListUsers = (params) => request.get('/admin/users', { params })
export const adminGetUser = (id) => request.get('/admin/users/' + id)
export const adminCreateUser = (data) => request.post('/admin/users', data)
export const adminUpdateUser = (id, data) => request.put('/admin/users/' + id, data)
export const adminUpdateUserStatus = (id, status) => request.put('/admin/users/' + id + '/status', null, { params: { status } })
export const adminUpdateUserRoles = (id, roleIds) => request.put('/admin/users/' + id + '/roles', { roleIds })
export const adminResetUserPassword = (id) => request.post('/admin/users/' + id + '/reset-password')
export const adminRoleOptions = () => request.get('/admin/users/roles/options')

// ============ 管理员端角色权限 ============
export const adminListRoles = () => request.get('/admin/roles')
export const adminGetRole = (id) => request.get('/admin/roles/' + id)
export const adminUpdateRolePermissions = (id, permissionIds) => request.put('/admin/roles/' + id + '/permissions', { permissionIds })
// ============ 管理员端操作日志 ============
export const adminLogList = (params) => request.get('/admin/logs', { params })
export const adminLogDetail = (id) => request.get('/admin/logs/' + id)
export const adminLogModules = () => request.get('/admin/logs/modules')
export const adminLogStatsToday = () => request.get('/admin/logs/stats-today')
// ============ 管理员端全局订单 ============
export const adminOrderList = (params) => request.get('/admin/orders', { params })
export const adminOrderDetail = (id) => request.get('/admin/orders/' + id)
export const adminOrderCancel = (id, reason) => request.post('/admin/orders/' + id + '/cancel', null, { params: { reason } })
export const adminOrderComplete = (id, reason) => request.post('/admin/orders/' + id + '/complete', null, { params: { reason } })
export const adminOrderStatusOptions = () => request.get('/admin/orders/status-options')
// ============ 管理员端拆单 ============
export const adminOrderSplitPreview = (id) => request.get('/admin/orders/' + id + '/split-preview')
export const adminOrderSplit = (id, reason) => request.post('/admin/orders/' + id + '/split', null, { params: { reason } })
// ============ 管理员端全局商品 ============
export const adminProductList = (params) => request.get('/admin/products', { params })
export const adminProductUpdate = (id, data) => request.put('/admin/products/' + id, data)