<!--
  消费者端-我的订单（约束文档 5.2 订单状态机）
  - 状态 Tab：全部/待支付/待履约/已完成/售后
  - 订单卡片：订单号、状态、商品缩略、金额、操作按钮
-->
<template>
  <div class="consumer-orders">
    <!-- 状态 Tab -->
    <div class="order-tabs">
      <div
        v-for="tab in tabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: activeTab === tab.value }"
        @click="activeTab = tab.value"
      >
        {{ tab.label }}
      </div>
    </div>

    <!-- 订单列表 -->
    <div v-if="filteredOrders.length" class="order-list">
      <div
        v-for="o in filteredOrders"
        :key="o.id"
        class="order-card"
        @click="goDetail(o)"
      >
        <!-- 订单头部 -->
        <div class="order-header">
          <div class="header-left">
            <span class="order-no">订单号：{{ o.orderNo }}</span>
            <el-tag v-if="o.source === 'GROUP_BUY'" type="warning" size="small" effect="light" class="source-tag">
              👥 团购订单
            </el-tag>
          </div>
          <span class="header-right">
            <el-tag
              v-if="o.aftersaleStatus && o.aftersaleStatus !== ORDER_AFTERSALE_STATUS.NONE"
              :type="getOrderAftersaleType(o.aftersaleStatus)"
              size="small"
              effect="plain"
            >
              {{ getOrderAftersaleLabel(o.aftersaleStatus) }}
            </el-tag>
            <span class="order-status" :class="getStatusClass(o.orderStatus)">
              {{ getStatusLabel(o.orderStatus) }}
            </span>
          </span>
        </div>

        <!-- ✨ 取货码提示（约束 5.2 PENDING_PICKUP） -->
        <div v-if="o.orderStatus === ORDER_STATUS.PENDING_PICKUP && o.pickupCode" class="pickup-banner" @click.stop="showPickupCode(o)">
          <el-icon :size="16" color="#E6A23C"><LocationFilled /></el-icon>
          <span>📦 货已到取货点，取货码：<strong class="pickup-code">{{ o.pickupCode }}</strong></span>
          <span class="pickup-hint">点击查看 →</span>
        </div>

        <!-- 商品列表 -->
        <div class="order-items" v-if="o.items?.length">
          <div v-for="item in o.items.slice(0, 2)" :key="item.id" class="mini-item">
            <el-image class="mini-img" :src="item.image" fit="cover">
              <template #error><div class="img-placeholder">无图</div></template>
            </el-image>
            <div class="mini-info">
              <div class="mini-name">{{ item.productName }}</div>
              <div class="mini-bottom">
                <AmountText :amount="item.price" />
                <span>x{{ item.quantity }}</span>
              </div>
            </div>
          </div>
          <div v-if="o.items.length > 2" class="more-items">
            等 {{ o.items.length }} 件商品
          </div>
        </div>

        <!-- 金额 & 操作 -->
        <div class="order-footer">
          <span class="order-amount">
            共 {{ totalQty(o) }} 件 合计
            <AmountText :amount="o.totalAmount" strong />
          </span>
          <div class="order-actions">
            <template v-if="o.orderStatus === ORDER_STATUS.PENDING_PAY">
              <el-button size="small" @click.stop="onCancel(o)">取消订单</el-button>
              <el-button type="warning" size="small" @click.stop="onPay(o)">去支付</el-button>
            </template>
            <template v-else-if="o.orderStatus === ORDER_STATUS.PENDING_PICKUP">
              <el-button type="warning" size="small" @click.stop="showPickupCode(o)">出示取货码</el-button>
              <el-button size="small" plain @click.stop="goDetail(o)">查看详情</el-button>
            </template>
            <template v-else-if="o.orderStatus === ORDER_STATUS.COMPLETED">
              <!-- 已全部评价：灰显示 -->
              <el-button
                v-if="hasAllReviewed(o)"
                size="small" plain disabled
              >
                已评价
              </el-button>
              <!-- 有未评价 item：显示评价按钮 -->
              <el-button
                v-else
                v-if="o.aftersaleStatus !== ORDER_AFTERSALE_STATUS.REFUNDED"
                type="primary"
                size="small"
                @click.stop="openReview(o)"
              >
                评价
              </el-button>
              <el-button size="small" plain @click.stop="goDetail(o)">查看详情</el-button>
            </template>
            <template v-else>
              <el-button size="small" plain @click.stop="goDetail(o)">查看详情</el-button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-else description="暂无订单" />

    <!-- 评价弹窗：一笔订单可分别评多个商品 -->
    <el-dialog
      v-model="reviewDialogVisible"
      title="发表评价"
      width="480px"
      :close-on-click-modal="false"
    >
      <div v-if="reviewOrder" class="review-list">
        <div
          v-for="it in reviewOrder.items.filter(i => !i.reviewed)"
          :key="it.id"
          class="review-item"
        >
          <div class="review-header">
            <span class="review-name">{{ it.productName }}</span>
            <span class="review-spec">{{ it.skuName }}</span>
          </div>
          <el-rate v-model="reviewForm[it.id].rating" :max="5" show-score text-color="#ff9900" />
          <el-input
            v-model="reviewForm[it.id].content"
            type="textarea"
            :rows="2"
            placeholder="说说使用感受（选填）"
            maxlength="512"
            show-word-limit
            size="small"
            style="margin-top:6px"
          />
          <el-checkbox
            v-model="reviewForm[it.id].isAnonymous"
            :value="1"
            :false-value="0"
            border
            size="small"
            style="margin-top:4px"
          >匿名评价</el-checkbox>
        </div>
        <el-empty v-if="!reviewOrder.items.some(i => !i.reviewed)" description="所有商品已评价" />
      </div>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { LocationFilled } from '@element-plus/icons-vue'
import AmountText from '@/components/AmountText.vue'
import { getMyOrders, cancelOrder, createReview } from '@/api'
import {
  ORDER_STATUS,
  ORDER_AFTERSALE_STATUS,
  getOrderStatusLabel,
  getOrderAftersaleLabel,
  getOrderAftersaleType
} from '@/config/business'

const router = useRouter()

const tabs = [
  { label: '全部', value: 'ALL' },
  { label: '待支付', value: ORDER_STATUS.PENDING_PAY },
  { label: '处理中', value: ORDER_STATUS.PROCESSING },
  { label: '等你取货', value: ORDER_STATUS.PENDING_PICKUP },
  { label: '已完成', value: ORDER_STATUS.COMPLETED },
  { label: '售后', value: 'AFTERSALE' }
]

const activeTab = ref('ALL')
const orders = ref([])

const filteredOrders = computed(() => {
  if (activeTab.value === 'ALL') return orders.value
  if (activeTab.value === 'AFTERSALE') {
    // 售后 Tab：订单维度售后进度非 NONE（含退款中 / 已退款）
    return orders.value.filter(o => o.aftersaleStatus && o.aftersaleStatus !== ORDER_AFTERSALE_STATUS.NONE)
  }
  // 处理中 Tab 需同时兼容旧 DB 值 FULFILLING
  if (activeTab.value === ORDER_STATUS.PROCESSING) {
    return orders.value.filter(o =>
      o.orderStatus === ORDER_STATUS.PROCESSING || o.orderStatus === ORDER_STATUS.FULFILLING)
  }
  return orders.value.filter(o => o.orderStatus === activeTab.value)
})

onMounted(loadOrders)

async function loadOrders() {
  try {
    orders.value = await getMyOrders()
  } catch (e) { /* request.js 已提示 */ }
}

/** 状态文案统一取自 config/business（约束 29 禁止硬编码）*/
function getStatusLabel(status) {
  return getOrderStatusLabel(status)
}

function getStatusClass(status) {
  if (status === ORDER_STATUS.CANCELLED) return 'status-muted'
  if (status === ORDER_STATUS.PENDING_PAY) return 'status-warning'
  if (status === ORDER_STATUS.PENDING_PICKUP) return 'status-pickup'
  if (status === ORDER_STATUS.COMPLETED) return 'status-success'
  if (status === ORDER_STATUS.PROCESSING || status === ORDER_STATUS.FULFILLING || status === ORDER_STATUS.PAID) return 'status-primary'
  if (status === ORDER_STATUS.REFUNDING) return 'status-warning'
  if (status === ORDER_STATUS.REFUNDED) return 'status-muted'
  return 'status-info'
}

function totalQty(o) {
  return (o.items || []).reduce((s, i) => s + Number(i.quantity), 0)
}

function hasAllReviewed(o) {
  return (o.items || []).length > 0 &&
         (o.items || []).every(it => it.reviewed)
}
function goDetail(o) {
  // 约束 7.2.3：路由 :orderNo 需要业务编号 String，不是 Long 主键
  router.push(`/consumer/order/${o.orderNo}`)
}

function onPay(o) {
  router.push(`/consumer/pay/${o.orderNo}`)
}

async function onCancel(o) {
  try {
    await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
    await cancelOrder(o.id, '用户主动取消')
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch {}
}

const reviewDialogVisible = ref(false)
const reviewOrder = ref(null)
const reviewForm = ref({})   // orderItemId -> { rating, content, isAnonymous }

function openReview(o) {
  reviewOrder.value = o
  // 每个未评价的 item 初始化表单
  const form = {}
  ;(o.items || []).forEach(it => {
    if (!it.reviewed) {
      form[it.id] = { rating: 5, content: '', isAnonymous: 0 }
    }
  })
  reviewForm.value = form
  reviewDialogVisible.value = true
}

async function submitReview() {
  let okCount = 0, failCount = 0
  try {
    const orderId = reviewOrder.value.id
    const pendingItems = reviewOrder.value.items.filter(it => !it.reviewed && reviewForm.value[it.id])
    if (!pendingItems.length) {
      ElMessage.warning('没有可提交的评价')
      return
    }
    for (const it of pendingItems) {
      const f = reviewForm.value[it.id]
      if (!f.rating) { failCount++; continue }
      try {
        await createReview(it.id, f.rating, f.content || '', f.isAnonymous)
        okCount++
      } catch (e) {
        console.error('createReview FAIL', it.id, e?.response?.data || e?.message || e)
        failCount++
      }
    }
  } catch (e) {
    console.error('submitReview outer error', e)
  }
  reviewDialogVisible.value = false
  if (okCount > 0) ElMessage.success(`成功提交 ${okCount} 条评价${failCount ? '，' + failCount + ' 条失败' : ''}`)
  else ElMessage.error('评价提交失败，请稍后重试')
  await loadOrders()
}

/**
 * 弹出取货码大弹窗（约束 5.2 PENDING_PICKUP）
 */
function showPickupCode(o) {
  ElMessageBox.alert(
    `<div style="text-align:center;">
      <div style="font-size:14px;color:#606266;margin-bottom:8px;">取货码</div>
      <div style="font-size:48px;font-weight:700;color:#E6A23C;letter-spacing:4px;font-family:monospace;">${o.pickupCode || '-'}</div>
      <div style="font-size:12px;color:#909399;margin-top:12px;">订单号：${o.orderNo}</div>
      <div style="font-size:12px;color:#909399;margin-top:4px;">请出示给取货点工作人员确认取货</div>
    </div>`,
    '📦 货已到取货点',
    { dangerouslyUseHTMLString: true, showCancelButton: false, confirmButtonText: '我知道了' }
  )
}
</script>

<style scoped lang="scss">
.consumer-orders { padding-bottom: 20px; }

.order-tabs {
  position: sticky;
  top: 0;
  background: #fff;
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  z-index: 10;

  .tab-item {
    flex: 1;
    padding: 12px 0;
    font-size: 14px;
    color: #606266;
    text-align: center;
    cursor: pointer;
    position: relative;

    &.active {
      color: #E6A23C;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 24px;
        height: 2px;
        background: #E6A23C;
        border-radius: 1px;
      }
    }
  }
}

.order-list { padding: 10px; }

.order-card {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 10px;
  overflow: hidden;
  cursor: pointer;

  .order-header {
    padding: 10px 12px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #f5f5f5;

    .header-left {
      display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0;
    }
    .order-no { font-size: 12px; color: #909399; }
    .source-tag { flex-shrink: 0; }
    .header-right { display: flex; align-items: center; gap: 6px; }
    .order-status {
      font-size: 13px; font-weight: 500;

      &.status-warning { color: #E6A23C; }
      &.status-pickup { color: #F56C6C; font-weight: 600; }
      &.status-success { color: #67C23A; }
      &.status-primary { color: #409EFF; }
      &.status-muted { color: #c0c4cc; }
      &.status-info { color: #909399; }
    }
  }

  .order-items {
    padding: 8px 12px;

    .mini-item {
      display: flex; gap: 8px; padding: 4px 0;

      .mini-img {
      width: 60px; height: 60px; border-radius: 4px; flex-shrink: 0;
      overflow: hidden; background: #f5f7fa; display: block;
      .img-placeholder {
        width: 100%; height: 100%;
        display: flex; align-items: center; justify-content: center;
        font-size: 11px; color: #c0c4cc;
      }
    }
      .mini-info { flex: 1; }
      .mini-name { font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .mini-bottom {
        display: flex; justify-content: space-between; align-items: center; margin-top: 16px;
        font-size: 12px; color: #606266;
      }
    }

    .more-items { font-size: 12px; color: #909399; text-align: right; padding-top: 4px; }
  }

  .order-footer {
    padding: 10px 12px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-top: 1px solid #f5f5f5;

    .order-amount { font-size: 12px; color: #606266; }
  }

  // ✨ 取货码 banner 样式
  .pickup-banner {
    display: flex; align-items: center; gap: 6px;
    padding: 8px 12px;
    background: linear-gradient(90deg, #FDF6EC 0%, #FEF0F0 100%);
    border-top: 1px solid #F5DAB1;
    border-bottom: 1px solid #F5DAB1;
    font-size: 12px; color: #E6A23C;
    cursor: pointer;

    .pickup-code {
      font-family: monospace; font-size: 14px; color: #F56C6C;
      letter-spacing: 2px;
    }
    .pickup-hint { margin-left: auto; color: #909399; font-size: 11px; }
  }
}

.review-list .review-item {
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
  &:last-child { border-bottom: none; }
}
.review-list .review-header {
  font-size: 13px; color: #303133; margin-bottom: 6px;
  .review-spec { color: #909399; font-size: 12px; margin-left: 6px; }
}</style>
