<!--
  消费者端-模拟支付（约束文档 5.3 模拟支付 + 幂等回调）
  - 展示订单信息、支付金额
  - 模拟支付方式选择（微信/支付宝）
  - 模拟支付按钮 → 调用后端回调接口
-->
<template>
  <div class="consumer-pay" v-if="order">
    <!-- 订单状态 -->
    <div class="status-card" :class="{ paid: isPaid }">
      <template v-if="isPaid">
        <el-icon :size="36"><CircleCheckFilled /></el-icon>
        <div class="status-text">
          <div class="status-title">支付成功</div>
          <div class="status-sub">订单号：{{ order.orderNo }}</div>
        </div>
      </template>
      <template v-else>
        <el-icon :size="36" color="#E6A23C"><WarningFilled /></el-icon>
        <div class="status-text">
          <div class="status-title">待支付</div>
          <div class="status-sub">
            订单号：{{ order.orderNo }}
            <span class="expire" v-if="order.expireTime">
              （请在 {{ expireCountdown }} 后支付）
            </span>
          </div>
        </div>
      </template>
    </div>

    <!-- 待支付金额 -->
    <div class="amount-card">
      <div class="amount-label">待支付金额</div>
      <div class="amount-value">
        <AmountText :amount="order.payAmount || order.totalAmount" size="xl" strong />
      </div>
    </div>

    <!-- 商品清单 -->
    <div class="section items-section" v-if="items.length">
      <div class="section-title">订单商品</div>
      <div v-for="item in items" :key="item.id" class="pay-item">
        <el-image class="item-img" :src="item.image" fit="cover">
          <template #error><div class="img-ph">无图</div></template>
        </el-image>
        <div class="item-info">
          <div class="item-name">{{ item.productName }}</div>
          <div class="item-spec" v-if="item.skuName">{{ item.skuName }}</div>
          <div class="item-bottom">
            <AmountText :amount="item.price" />
            <span class="qty">x{{ item.quantity }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 支付方式 -->
    <div class="section pay-methods">
      <div class="section-title">选择支付方式</div>
      <el-radio-group v-model="payMethod" size="large">
        <el-radio value="WECHAT">
          <el-icon><ChatDotRound /></el-icon>微信支付
        </el-radio>
        <el-radio value="ALIPAY">
          <el-icon><Wallet /></el-icon>支付宝
        </el-radio>
      </el-radio-group>
    </div>

    <!-- 底部操作 -->
    <div class="action-bar">
      <el-button v-if="!isPaid" type="warning" size="large" :loading="paying" block @click="onMockPay">
        模拟支付成功
      </el-button>
      <template v-else>
        <el-button type="primary" size="large" block @click="goOrders">查看订单</el-button>
      </template>
      <el-button v-if="!isPaid" size="large" text @click="onCancelOrder">
        取消订单
      </el-button>
    </div>
  </div>

  <div v-else class="loading">
    <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    <div>加载中...</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheckFilled, WarningFilled, ChatDotRound, Wallet, Loading } from '@element-plus/icons-vue'
import AmountText from '@/components/AmountText.vue'
import { createPayment, mockPaymentCallback, getOrder, cancelOrder } from '@/api'
import { ORDER_STATUS } from '@/config/business'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const items = ref([])
const payment = ref(null)
const payMethod = ref('WECHAT')
const paying = ref(false)
const expireCountdown = ref('')

const isPaid = computed(() => {
  if (order.value?.payStatus === 'SUCCESS') return true
  // v1.13/v1.14：PROCESSING 替代 FULFILLING，并补充待自提 / 退款中 / 已退款
  return [
    ORDER_STATUS.PAID,
    ORDER_STATUS.PROCESSING,
    ORDER_STATUS.FULFILLING, // 兼容旧 DB 值
    ORDER_STATUS.PENDING_PICKUP,
    ORDER_STATUS.COMPLETED,
    ORDER_STATUS.REFUNDING,
    ORDER_STATUS.REFUNDED
  ].includes(order.value?.orderStatus)
})

onMounted(async () => {
  try {
    // 路由参数是 orderNo，后端 detail 用的是数字 id
    // 这里简化处理：先尝试用 id 查，如果失败再用 orderNo 查
    const param = route.params.orderNo
    const detail = await getOrder(param)
    // 后端 detail() 返回 { order: {...}, items: [...], logs: [...] } — 约束 28.20
    order.value = detail.order || detail
    items.value = detail.items || []
    // 启动倒计时
    startCountdown()
    // 如果还没生成支付单，创建一个
    const orderId = detail.order?.id || detail.id
    if (!isPaid.value && orderId) {
      try {
        payment.value = await createPayment(orderId)
      } catch {}
    }
  } catch (e) {
    ElMessage.error(e.message || '订单不存在')
    router.push('/consumer/orders')
  }
})

function startCountdown() {
  if (!order.value?.expireTime) return
  const expire = new Date(order.value.expireTime).getTime()
  const timer = setInterval(() => {
    const diff = Math.max(0, expire - Date.now())
    const m = Math.floor(diff / 60000)
    const s = Math.floor((diff % 60000) / 1000)
    expireCountdown.value = `${m}分${s}秒`
    if (diff <= 0) {
      clearInterval(timer)
      expireCountdown.value = '已过期'
    }
  }, 1000)
}

async function onMockPay() {
  if (!payment.value?.paymentNo) {
    ElMessage.error('支付单创建失败')
    return
  }
  paying.value = true
  try {
    await mockPaymentCallback(payment.value.paymentNo)
    // 刷新订单状态
    const updated = await getOrder(route.params.orderNo)
    order.value = updated.order || updated
    items.value = updated.items || []
    ElMessage.success('支付成功！订单进入履约流程')
  } catch (e) {
    // request.js 已提示
  } finally {
    paying.value = false
  }
}

async function onCancelOrder() {
  try {
    await ElMessageBox.confirm('确认取消订单？取消后无法恢复', '提示', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想'
    })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    router.push('/consumer/orders')
  } catch {}
}

function goOrders() {
  router.push('/consumer/orders')
}
</script>

<style scoped lang="scss">
.consumer-pay {
  padding: 12px;
  padding-bottom: 80px;
  min-height: 100vh;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  gap: 12px;
  color: #909399;
}

.status-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  border-left: 4px solid #E6A23C;

  &.paid { border-left-color: #67C23A; }

  .status-text { .status-title { font-size: 16px; font-weight: 600; color: #303133; } }
  .status-sub { font-size: 12px; color: #909399; margin-top: 4px; }
  .expire { color: #E6A23C; }
}

.amount-card {
  background: linear-gradient(135deg, #FF6B35, #FF9A6B);
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  color: #fff;
  margin-bottom: 12px;

  .amount-label { font-size: 13px; opacity: 0.9; }
  .amount-value { margin-top: 8px; :deep(.amount-text) { font-size: 36px; color: #fff; } }
}

.section {
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 12px;

  .section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 10px; }
}

.pay-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-child { border-bottom: none; }

  .item-img { width: 70px; height: 70px; background: #f5f5f5; border-radius: 6px; overflow: hidden; flex-shrink: 0; }
  .img-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #ccc; font-size: 12px; }
  .item-info { flex: 1; }
  .item-name { font-size: 13px; color: #303133; }
  .item-spec { font-size: 11px; color: #909399; margin-top: 2px; }

  .item-bottom {
    display: flex; justify-content: space-between; align-items: center; margin-top: 6px;
    .qty { font-size: 12px; color: #606266; }
  }
}

.action-bar {
  position: fixed;
  bottom: 64px;
  left: 0; right: 0;
  max-width: 750px; margin: 0 auto;
  background: #fff; padding: 12px 16px;
  display: flex; flex-direction: column; gap: 8px;
  border-top: 1px solid #eee;
}
</style>
