/**
 * 前端业务常量统一入口（约束文档 29：禁止硬编码铁律 · v1.14）
 *
 * v1.13 订单状态机重构：FULFILLING（备货中）→ PROCESSING（处理中）
 *   - ORDER_STATUS 加 PROCESSING，FULFILLING 保留作兼容旧 DB 值
 *   - ORDER_STATUS_LABEL.PAID 改为"待处理"（暗示商家还没干活）
 *   - 所有组件统一 import 本文件，禁止散写状态字符串
 *
 * v1.14 售后管理（约束文档 5.5 + 5.2）：
 *   - ORDER_STATUS 加 REFUNDING / REFUNDED（全额退款支线，终态）
 *   - 新增 AFTERSALE_STATUS（售后单据状态机）/ AFTERSALE_TYPE（仅退款/退货退款）
 *   - 新增 ORDER_AFTERSALE_STATUS（orders.aftersale_status 聚合状态，表达订单维度售后进度）
 */

// ========== 订单状态 ==========
export const ORDER_STATUS = {
  PENDING_PAY:    'PENDING_PAY',
  PAID:           'PAID',
  PROCESSING:     'PROCESSING',   // v1.13: 替代 FULFILLING
  FULFILLING:     'FULFILLING',   // 兼容旧 DB 值（已废弃）
  PENDING_PICKUP: 'PENDING_PICKUP',
  COMPLETED:      'COMPLETED',
  CANCELLED:      'CANCELLED',
  REFUNDING:      'REFUNDING',    // v1.14: 全额退款中（终态）
  REFUNDED:       'REFUNDED'      // v1.14: 已退款（终态）
}

export const ORDER_STATUS_LABEL = {
  PENDING_PAY:    { text: '待支付',  type: 'warning' },
  PAID:           { text: '待处理',  type: 'info' },     // v1.13 改：暗示商家还没干活
  PROCESSING:     { text: '处理中',  type: 'primary' },   // v1.13: 替代 FULFILLING
  FULFILLING:     { text: '处理中',  type: 'primary' },   // 兼容旧 DB 值
  PENDING_PICKUP: { text: '等你取货',  type: 'danger' },
  COMPLETED:      { text: '已完成',  type: 'success' },
  CANCELLED:      { text: '已取消',  type: 'info' },
  REFUNDING:      { text: '退款中',  type: 'warning' },   // v1.14
  REFUNDED:       { text: '已退款',  type: 'danger' }     // v1.14
}

// ========== 售后单据状态（aftersale.aftersale_status · v1.14 · 约束 5.5）==========
// 状态机：APPLYING → APPROVED | REJECTED；APPROVED → REFUNDING → REFUNDED
export const AFTERSALE_STATUS = {
  APPLYING:  'APPLYING',
  APPROVED:  'APPROVED',
  REJECTED:  'REJECTED',
  REFUNDING: 'REFUNDING',
  REFUNDED:  'REFUNDED'
}

export const AFTERSALE_STATUS_LABEL = {
  APPLYING:  { text: '待审核', type: 'warning' },
  APPROVED:  { text: '待退款', type: 'primary' },
  REJECTED:  { text: '已驳回', type: 'info' },
  REFUNDING: { text: '退款中', type: 'primary' },
  REFUNDED:  { text: '已退款', type: 'success' }
}

// ========== 售后类型（aftersale.aftersale_type）==========
export const AFTERSALE_TYPE = {
  REFUND_ONLY: 'REFUND_ONLY',
  RETURN:      'RETURN'
}

export const AFTERSALE_TYPE_LABEL = {
  REFUND_ONLY: { text: '仅退款',   type: 'warning' },
  RETURN:      { text: '退货退款', type: 'danger' }
}

// ========== 订单售后聚合状态（orders.aftersale_status · 表达订单维度售后进度）==========
// 注：聚合状态只表达进度，不改变 order_status；COMPLETED 订单售后后主状态仍为 COMPLETED
export const ORDER_AFTERSALE_STATUS = {
  NONE:      'NONE',
  APPLYING:  'APPLYING',
  REFUNDING: 'REFUNDING',
  REFUNDED:  'REFUNDED'
}

export const ORDER_AFTERSALE_STATUS_LABEL = {
  NONE:      { text: '无售后', type: 'info' },
  APPLYING:  { text: '售后申请中', type: 'warning' },
  REFUNDING: { text: '退款中', type: 'primary' },
  REFUNDED:  { text: '已退款', type: 'success' }
}

// ========== 售后列表筛选 Tab（v1.14）==========
export const AFTERSALE_FILTER_TAB = {
  ALL:       'ALL',
  APPLYING:  'APPLYING',
  APPROVED:  'APPROVED',
  REJECTED:  'REJECTED',
  REFUNDING: 'REFUNDING',
  REFUNDED:  'REFUNDED'
}

// ========== 履约状态 ==========
export const FF_STATUS = {
  PENDING_ASSIGN: 'PENDING_ASSIGN',
  PENDING_PICKUP: 'PENDING_PICKUP',
  DELIVERING: 'DELIVERING',
  DELIVERED: 'DELIVERED',
  PICKED_UP: 'PICKED_UP',
  COMPLETED: 'COMPLETED',
  EXCEPTION: 'EXCEPTION'
}

export const FF_STATUS_LABEL = {
  PENDING_ASSIGN: { text: '待分配', type: 'info' },
  PENDING_PICKUP: { text: '等你取货', type: 'danger' },
  DELIVERING:     { text: '配送中', type: 'primary' },
  DELIVERED:      { text: '已送达', type: 'success' },
  PICKED_UP:      { text: '已取货', type: 'success' },
  COMPLETED:      { text: '已完成', type: 'success' },
  EXCEPTION:      { text: '异常', type: 'danger' }
}

// ========== 用户角色 ==========
export const USER_TYPE = {
  CONSUMER: 'CONSUMER',
  MERCHANT: 'MERCHANT',
  PICKUP_STAFF: 'PICKUP_STAFF',
  ADMIN: 'ADMIN',
  SUPER_ADMIN: 'SUPER_ADMIN'
}

export const USER_TYPE_LABEL = {
  CONSUMER:    '消费者',
  MERCHANT:    '商家',
  PICKUP_STAFF: '取货点工作人员',
  ADMIN:       '管理员',
  SUPER_ADMIN: '超级管理员'
}

// ========== 便捷 helper ==========
export const getOrderStatusLabel = (s) => ORDER_STATUS_LABEL[s]?.text || s || '-'
export const getOrderStatusType  = (s) => ORDER_STATUS_LABEL[s]?.type || 'info'
export const getFfStatusLabel    = (s) => FF_STATUS_LABEL[s]?.text || s || '-'
export const getFfStatusType     = (s) => FF_STATUS_LABEL[s]?.type || 'info'
export const getUserTypeLabel    = (t) => USER_TYPE_LABEL[t] || t || '-'
// ========== 供应计划状态（v1.13 新增 · 约束文档 6.1）==========
export const SUPPLY_PLAN_STATUS = {
  DRAFT:     'DRAFT',
  CONFIRMED: 'CONFIRMED',
  ARRIVED:   'ARRIVED',
  CANCELLED:  'CANCELLED'
}
export const SUPPLY_PLAN_STATUS_LABEL = {
  DRAFT:     { text: '草稿',   type: 'info' },
  CONFIRMED: { text: '已确认', type: 'warning' },
  ARRIVED:   { text: '已到货', type: 'success' },
  CANCELLED:  { text: '已取消', type: 'info' }
}

// ========== 库存流水变更类型（v1.13 新增 · 约束文档 6）==========
export const STOCK_CHANGE_TYPE = {
  LOCK:      'LOCK',
  UNLOCK:    'UNLOCK',
  DEDUCT:    'DEDUCT',
  ROLLBACK:  'ROLLBACK',
  INBOUND:   'INBOUND',
  SUPPLY_IN: 'SUPPLY_IN'
}
export const STOCK_CHANGE_TYPE_LABEL = {
  LOCK:      { text: '预占',     type: 'warning' },
  UNLOCK:    { text: '释放预占', type: 'info' },
  DEDUCT:    { text: '实扣',     type: 'danger' },
  ROLLBACK:  { text: '回滚',     type: 'danger' },
  INBOUND:   { text: '人工入库', type: 'primary' },
  SUPPLY_IN: { text: '供应到货', type: 'success' }
}

// ========== 库存预警状态（v1.13 新增 · 约束文档 11 口径统一）==========
// 取值与后端 StockWarnStatusConstant 一一对应，后端 SQL 统一判定，前端只做展示
// 判定口径：WARN=warn_qty非空且available_qty<=warn_qty；EMPTY=warn_qty为空且available_qty=0；其余 NORMAL
export const STOCK_WARN_STATUS = {
  WARN:   'WARN',
  EMPTY:  'EMPTY',
  NORMAL: 'NORMAL'
}
export const STOCK_WARN_STATUS_LABEL = {
  WARN:   { text: '预警', type: 'danger' },
  EMPTY:  { text: '空仓', type: 'info' },
  NORMAL: { text: '正常', type: 'success' }
}

// ========== 库存列表筛选维度 ==========
// 说明：LOCKED 是"存在预占库存(locked_qty>0)"的独立筛选维度，不属于预警状态枚举；
// 与后端 /supply/stocks/mine 的 lockedOnly 参数对应，计数取自总览接口 stats.lockedCount
export const STOCK_FILTER_TAB = {
  ALL:    'ALL',
  WARN:   'WARN',
  EMPTY:  'EMPTY',
  LOCKED: 'LOCKED'
}

// ========== 库存便捷 helper ==========
export const getSupplyPlanLabel = (s) => SUPPLY_PLAN_STATUS_LABEL[s]?.text || s || '-'
export const getSupplyPlanType  = (s) => SUPPLY_PLAN_STATUS_LABEL[s]?.type || 'info'
export const getStockChangeLabel = (t) => STOCK_CHANGE_TYPE_LABEL[t]?.text || t || '-'
export const getStockChangeType  = (t) => STOCK_CHANGE_TYPE_LABEL[t]?.type || 'info'
export const getStockWarnLabel = (s) => STOCK_WARN_STATUS_LABEL[s]?.text || s || '-'
export const getStockWarnType  = (s) => STOCK_WARN_STATUS_LABEL[s]?.type || 'info'

// ========== 售后便捷 helper（v1.14）==========
export const getAftersaleStatusLabel = (s) => AFTERSALE_STATUS_LABEL[s]?.text || s || '-'
export const getAftersaleStatusType  = (s) => AFTERSALE_STATUS_LABEL[s]?.type || 'info'
export const getAftersaleTypeLabel   = (t) => AFTERSALE_TYPE_LABEL[t]?.text || t || '-'
export const getAftersaleTypeType    = (t) => AFTERSALE_TYPE_LABEL[t]?.type || 'info'
export const getOrderAftersaleLabel  = (s) => ORDER_AFTERSALE_STATUS_LABEL[s]?.text || s || '-'
export const getOrderAftersaleType   = (s) => ORDER_AFTERSALE_STATUS_LABEL[s]?.type || 'info'
