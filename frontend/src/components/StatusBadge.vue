<!--
  状态徽章：约束文档 15 + 23
  - 状态展示必须与后端枚举一致
  - 状态颜色统一：待支付灰、已支付蓝、备货中橙、完成绿、取消灰、售后红
  - 禁止前端硬编码文案，用状态映射
-->
<template>
  <el-tag :type="tagType" :effect="'light'" size="small" class="status-badge">
    {{ label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 后端枚举值，如 PENDING_PAY / PAID / FULFILLING / COMPLETED / CANCELLED
  status: { type: String, required: true }
})

// 后端枚举 → 中文文案映射（约束文档 15：禁止前端硬编码，统一维护）
const STATUS_LABELS = {
  // 订单状态
  PENDING_PAY: '待支付',
  PAID: '待处理',
  PROCESSING: '处理中',
  FULFILLING: '处理中',
  PENDING_PICKUP: '等你取货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  // 支付状态
  PENDING: '待支付',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  // 履约状态
  PENDING_ASSIGN: '待分配',
  PENDING_PICKUP: '等你取货',
  PENDING_DELIVERY: '配送中',
  PICKED_UP: '已取货',
  DELIVERED: '已送达',
  // 售后状态（REFUNDING / REFUNDED 与订单状态同值，统一在上方订单状态块定义）
  APPLYING: '申请中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  // 供应计划状态
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  ARRIVED: '已到货',
  // 商品状态
  DRAFT_GOODS: '草稿',
  ON_SHELF: '上架中',
  OFF_SHELF: '已下架',
  // 预测任务状态
  RUNNING: '运行中'
}

// 状态 → Element Plus tag type 映射（约束文档 23 颜色统一）
const STATUS_TYPES = {
  // 灰：待/取消/结束
  PENDING_PAY: 'info', PENDING: 'info', CANCELLED: 'info',
  PENDING_ASSIGN: 'info', DRAFT: 'info', DRAFT_GOODS: 'info',
  OFF_SHELF: 'info', CONFIRMED: 'info',
  // 蓝：已支付
  PAID: 'primary', SUCCESS: 'primary', ON_SHELF: 'primary',
  // 橙：处理中/进行中
  PROCESSING: 'warning', FULFILLING: 'warning', PENDING_PICKUP: 'warning',
  PENDING_DELIVERY: 'warning', APPLYING: 'warning', REFUNDING: 'warning',
  RUNNING: 'warning',
  // 绿：完成/通过
  COMPLETED: 'success', PICKED_UP: 'success', DELIVERED: 'success',
  ARRIVED: 'success', APPROVED: 'success',
  // 红：售后/失败
  FAILED: 'danger', REFUNDED: 'danger', REJECTED: 'danger'
}

const label = computed(() => STATUS_LABELS[props.status] || props.status)
const tagType = computed(() => STATUS_TYPES[props.status] || 'info')
</script>

<style scoped>
.status-badge { font-weight: 500; }
</style>
