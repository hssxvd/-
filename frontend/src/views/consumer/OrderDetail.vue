<!--
  消费者端-订单详情（约束文档 5.2 订单状态机 + 5.5 售后状态机 + 状态时间线）
  v1.14：新增申请售后入口、订单售后进度展示、售后记录列表
-->
<template>
  <div class="consumer-detail" v-if="order">
    <!-- 状态时间线 -->
    <div class="timeline-card">
      <div class="timeline-title">订单进度</div>
      <el-timeline>
        <el-timeline-item
          v-for="(step, idx) in timelineSteps"
          :key="idx"
          :timestamp="step.time"
          :type="step.done ? 'success' : (step.active ? 'warning' : 'info')"
          :hollow="!step.done"
        >
          {{ step.label }}
          <span v-if="step.active" class="step-active-tag">进行中</span>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- ✨ 自提码大卡片（约束 5.2 PENDING_PICKUP） -->
    <div v-if="order.orderStatus === ORDER_STATUS.PENDING_PICKUP && order.pickupCode"
         class="pickup-big-card" @click="showPickupCode(order)">
      <div class="pickup-badge">📦 货已到自提点</div>
      <div class="pickup-label">自提核销码</div>
      <div class="pickup-big-code">{{ order.pickupCode }}</div>
      <div class="pickup-hint">点击放大 · 请出示给自提点工作人员</div>
    </div>

    <!-- 订单基本信息 -->
    <div class="info-card">
      <div class="info-row"><span class="label">订单号</span><span class="value">{{ order.orderNo }}</span></div>
      <div class="info-row"><span class="label">订单状态</span>
        <span class="value">
          <el-tag :type="getOrderStatusType(order.orderStatus)" size="small" effect="dark">
            {{ getOrderStatusLabel(order.orderStatus) }}
          </el-tag>
        </span>
      </div>
      <div class="info-row" v-if="hasAftersale">
        <span class="label">售后进度</span>
        <span class="value">
          <el-tag :type="getOrderAftersaleType(order.aftersaleStatus)" size="small" effect="plain">
            {{ getOrderAftersaleLabel(order.aftersaleStatus) }}
          </el-tag>
        </span>
      </div>
      <div class="info-row" v-if="Number(order.refundedAmount) > 0">
        <span class="label">已退金额</span>
        <span class="value refund-amount">¥{{ order.refundedAmount }}</span>
      </div>
      <div class="info-row" v-if="order.pickupPointId"><span class="label">自提点</span><span class="value">自提点 #{{ order.pickupPointId }}</span></div>
      <div class="info-row"><span class="label">下单时间</span><span class="value">{{ order.createTime }}</span></div>
      <div class="info-row" v-if="order.payTime"><span class="label">支付时间</span><span class="value">{{ order.payTime }}</span></div>
      <div class="info-row" v-if="order.remark"><span class="label">备注</span><span class="value">{{ order.remark }}</span></div>
    </div>

    <!-- 商品快照 -->
    <div class="items-card">
      <div class="card-title">商品快照</div>
      <div v-for="item in order.items" :key="item.id" class="detail-item">
        <el-image class="item-img" :src="item.image" fit="cover">
          <template #error><div class="img-ph">无图</div></template>
        </el-image>
        <div class="item-info">
          <div class="item-name">{{ item.productName }}</div>
          <div class="item-sku" v-if="item.skuName">{{ item.skuName }}</div>
          <div class="item-attrs" v-if="item.attrSnapshot">
            <span v-for="(val, key) in safeAttrs(item.attrSnapshot)" :key="key" class="attr-tag">{{ key }}:{{ val }}</span>
          </div>
          <div class="item-bottom">
            <AmountText :amount="item.price" />
            <span>x{{ item.quantity }}</span>
          </div>
          <div class="item-refund" v-if="Number(item.refundedAmount) > 0">
            已退 {{ item.refundedQty }} 件 / ¥{{ item.refundedAmount }}
          </div>
          <el-button
            v-if="canApplyAftersale && itemRefundable(item) > 0"
            class="apply-btn"
            size="small"
            type="primary"
            plain
            @click="openApply(item)"
          >
            申请售后
          </el-button>
        </div>
      </div>
    </div>

    <!-- 金额明细 -->
    <div class="amount-card">
      <div class="amount-row">
        <span>商品总额</span>
        <AmountText :amount="order.totalAmount" />
      </div>
      <div class="amount-row" v-if="order.discountAmount">
        <span>优惠</span>
        <span class="discount">-{{ order.discountAmount }}</span>
      </div>
      <div class="amount-row total">
        <span>实付金额</span>
        <AmountText :amount="order.payAmount || order.totalAmount" strong size="large" />
      </div>
    </div>

    <!-- 售后记录（约束 5.5） -->
    <div class="aftersale-card" v-if="aftersales.length">
      <div class="card-title">售后记录</div>
      <div v-for="a in aftersales" :key="a.id" class="aftersale-item">
        <div class="as-head">
          <span class="as-no">{{ a.aftersaleNo }}</span>
          <el-tag :type="getAftersaleStatusType(a.aftersaleStatus)" size="small" effect="dark">
            {{ a.aftersaleStatusLabel || getAftersaleStatusLabel(a.aftersaleStatus) }}
          </el-tag>
        </div>
        <div class="as-line">
          <span>{{ a.aftersaleTypeLabel || getAftersaleTypeLabel(a.aftersaleType) }}</span>
          <span class="as-amount">¥{{ a.approvedAmount ?? a.applyAmount }}</span>
        </div>
        <div class="as-line muted">{{ a.productName }}<span v-if="a.skuName"> · {{ a.skuName }}</span></div>
        <div class="as-line muted">原因：{{ a.reason }}</div>
        <div class="as-line muted" v-if="a.auditRemark">处理说明：{{ a.auditRemark }}</div>
        <div class="as-line muted">申请时间：{{ formatTime(a.createTime) }}</div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="action-bar">
      <el-button v-if="order.orderStatus === ORDER_STATUS.PENDING_PAY" type="warning" size="large" @click="goPay">
        去支付
      </el-button>
      <el-button v-if="order.orderStatus === ORDER_STATUS.PENDING_PAY" size="large" @click="onCancel">
        取消订单
      </el-button>
      <el-button v-if="order.orderStatus === ORDER_STATUS.PENDING_PICKUP" type="warning" size="large" @click="showPickupCode(order)">
        📦 出示自提码
      </el-button>
    </div>

    <!-- 申请售后 Dialog（约束 5.5） -->
    <el-dialog v-model="applyVisible" title="申请售后" width="480px">
      <div v-if="applyItem" class="apply-goods">
        <div><b>商品：</b>{{ applyItem.productName }}</div>
        <div v-if="applyItem.skuName"><b>规格：</b>{{ applyItem.skuName }}</div>
        <div>
          <b>可退金额：</b>
          <span class="refundable">¥{{ itemRefundable(applyItem) }}</span>
          <span class="muted">（小计 ¥{{ applyItem.subtotal }} − 已退 ¥{{ applyItem.refundedAmount || 0 }}）</span>
        </div>
      </div>
      <el-form :model="applyForm" label-width="90px" style="margin-top: 16px">
        <el-form-item label="售后类型" required>
          <el-radio-group v-model="applyForm.type">
            <el-radio :label="AFTERSALE_TYPE.REFUND_ONLY">
              {{ AFTERSALE_TYPE_LABEL.REFUND_ONLY.text }}
            </el-radio>
            <el-radio :label="AFTERSALE_TYPE.RETURN">
              {{ AFTERSALE_TYPE_LABEL.RETURN.text }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="退款金额" required>
          <el-input-number
            v-model="applyForm.applyAmount"
            :min="0.01"
            :max="applyItem ? itemRefundable(applyItem) : 0"
            :precision="2"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="售后原因" required>
          <el-select
            v-model="applyForm.reason"
            placeholder="请选择或输入售后原因"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="r in REASON_OPTIONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input
            v-model="applyForm.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="补充说明问题情况（选填）"
          />
        </el-form-item>
        <el-form-item label="凭证图片">
          <el-input v-model="applyForm.images" placeholder="图片链接，多张用英文逗号分隔（选填）" />
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="提交后需商家审核；退货退款类型审核通过后请按商家指引退回商品。"
      />
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="loading">
    <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    <div>加载中...</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import AmountText from '@/components/AmountText.vue'
import { getOrder, cancelOrder, applyAftersale, getMyAftersales } from '@/api'
import {
  ORDER_STATUS,
  ORDER_AFTERSALE_STATUS,
  AFTERSALE_TYPE,
  AFTERSALE_TYPE_LABEL,
  getOrderStatusLabel,
  getOrderStatusType,
  getOrderAftersaleLabel,
  getOrderAftersaleType,
  getAftersaleStatusLabel,
  getAftersaleStatusType,
  getAftersaleTypeLabel
} from '@/config/business'

const route = useRoute()
const router = useRouter()

/** 售后原因候选项（仅 UI 文案，支持自定义输入）*/
const REASON_OPTIONS = ['商品质量问题', '商品破损/变质', '少发/漏发', '与商品描述不符', '不想要了', '其他原因']

const order = ref(null)
const aftersales = ref([])

// ---------- 申请售后 ----------
const applyVisible = ref(false)
const applyItem = ref(null)
const applying = ref(false)
const applyForm = reactive({
  type: AFTERSALE_TYPE.REFUND_ONLY,
  reason: '',
  description: '',
  images: '',
  applyAmount: 0
})

onMounted(async () => {
  await loadOrder()
})

async function loadOrder() {
  try {
    const detail = await getOrder(route.params.orderNo)
    // 后端 detail() 返回 { order: {...}, items: [...], logs: [...] } — 约束 28.20
    // 模板同时访问 order.totalAmount 和 order.items，需要合并到同一个对象
    order.value = { ...(detail.order || detail), items: detail.items || [] }
    loadAftersales()
  } catch (e) {
    ElMessage.error(e.message || '订单不存在')
    router.push('/consumer/orders')
  }
}

async function loadAftersales() {
  try {
    const res = await getMyAftersales({ orderId: order.value.id, pageNum: 1, pageSize: 100 })
    aftersales.value = res?.records || []
  } catch (e) {
    aftersales.value = []
  }
}

const hasAftersale = computed(() =>
  !!order.value?.aftersaleStatus && order.value.aftersaleStatus !== ORDER_AFTERSALE_STATUS.NONE
)

/** 订单是否仍可申请售后（与后端 AftersaleService.apply 准入一致）*/
const canApplyAftersale = computed(() => {
  if (!order.value) return false
  const st = order.value.orderStatus
  const allowed = [
    ORDER_STATUS.PAID,
    ORDER_STATUS.PROCESSING,
    ORDER_STATUS.FULFILLING, // 兼容旧 DB 值
    ORDER_STATUS.PENDING_PICKUP,
    ORDER_STATUS.COMPLETED
  ]
  if (!allowed.includes(st)) return false
  // 退款在途禁止重复申请
  return order.value.aftersaleStatus !== ORDER_AFTERSALE_STATUS.REFUNDING
})

const timelineSteps = computed(() => {
  if (!order.value) return []
  const s = order.value
  const st = s.orderStatus
  const paid = [
    ORDER_STATUS.PAID,
    ORDER_STATUS.PROCESSING,
    ORDER_STATUS.FULFILLING,
    ORDER_STATUS.PENDING_PICKUP,
    ORDER_STATUS.COMPLETED,
    ORDER_STATUS.REFUNDING,
    ORDER_STATUS.REFUNDED
  ]
  const steps = [
    { label: '创建订单', done: true, time: s.createTime || '—' },
    { label: '支付成功', done: paid.includes(st), time: s.payTime || '—' },
    {
      label: '商家备货',
      done: [ORDER_STATUS.PROCESSING, ORDER_STATUS.PENDING_PICKUP, ORDER_STATUS.COMPLETED].includes(st),
      active: st === ORDER_STATUS.PROCESSING,
      time: '—'
    },
    {
      label: '货到自提点',
      done: [ORDER_STATUS.PENDING_PICKUP, ORDER_STATUS.COMPLETED].includes(st),
      active: st === ORDER_STATUS.PENDING_PICKUP,
      time: '—'
    },
    { label: '自提完成', done: st === ORDER_STATUS.COMPLETED, time: s.finishTime || '—' }
  ]
  // 售后进度节点（约束 5.5）
  const as = s.aftersaleStatus
  if (as === ORDER_AFTERSALE_STATUS.APPLYING) {
    steps.push({ label: '售后申请中', active: true, time: '—' })
  } else if (as === ORDER_AFTERSALE_STATUS.REFUNDING) {
    steps.push({ label: '退款处理中', active: true, time: '—' })
  } else if (as === ORDER_AFTERSALE_STATUS.REFUNDED) {
    steps.push({ label: '已退款', done: true, time: '—' })
  }
  return steps
})

function safeAttrs(raw) {
  try { return JSON.parse(raw) || {} } catch { return {} }
}

/** 明细可退金额 = 小计 − 已退（约束 5.5）*/
function itemRefundable(item) {
  const max = Number(item.subtotal || 0) - Number(item.refundedAmount || 0)
  return Math.max(0, Math.round(max * 100) / 100)
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 16)
}

function goPay() {
  // 约束 7.2.3：路由 :orderNo 用业务编号 String
  router.push(`/consumer/pay/${order.value.orderNo}`)
}

/**
 * 自提码放大弹窗
 */
function showPickupCode(o) {
  ElMessageBox.alert(
    `<div style="text-align:center;">
      <div style="font-size:13px;color:#E6A23C;margin-bottom:6px;">📦 货已到自提点</div>
      <div style="font-size:14px;color:#606266;margin-bottom:10px;">自提核销码</div>
      <div style="font-size:56px;font-weight:700;color:#F56C6C;letter-spacing:6px;font-family:monospace;">${o.pickupCode || '-'}</div>
      <div style="font-size:12px;color:#909399;margin-top:16px;">订单号：${o.orderNo}</div>
      <div style="font-size:12px;color:#909399;margin-top:4px;">请出示给自提点工作人员核销</div>
    </div>`,
    '出示自提码',
    { dangerouslyUseHTMLString: true, showCancelButton: false, confirmButtonText: '我知道了' }
  )
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认取消订单？', '提示', { type: 'warning' })
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    order.value.orderStatus = ORDER_STATUS.CANCELLED
  } catch {}
}

// ---------- 申请售后 ----------
function openApply(item) {
  applyItem.value = item
  applyForm.type = AFTERSALE_TYPE.REFUND_ONLY
  applyForm.reason = ''
  applyForm.description = ''
  applyForm.images = ''
  applyForm.applyAmount = itemRefundable(item)
  applyVisible.value = true
}

async function submitApply() {
  if (!applyForm.reason?.trim()) {
    ElMessage.warning('请填写售后原因')
    return
  }
  if (!applyForm.applyAmount || applyForm.applyAmount <= 0) {
    ElMessage.warning('退款金额必须大于 0')
    return
  }
  applying.value = true
  try {
    await applyAftersale({
      orderId: order.value.id,
      orderItemId: applyItem.value.id,
      type: applyForm.type,
      reason: applyForm.reason,
      description: applyForm.description,
      images: applyForm.images,
      applyAmount: applyForm.applyAmount
    })
    ElMessage.success('售后申请已提交，等待商家审核')
    applyVisible.value = false
    await loadOrder()
  } catch (e) {
    // 失败原因由拦截器 toast
  } finally {
    applying.value = false
  }
}
</script>

<style scoped lang="scss">
.consumer-detail { padding: 12px; padding-bottom: 80px; }

.loading {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 300px; gap: 12px; color: #909399;
}

.timeline-card, .info-card, .items-card, .amount-card, .aftersale-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 10px;
}

.timeline-title, .card-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 12px; }

.info-row {
  display: flex; justify-content: space-between; align-items: center; padding: 6px 0; font-size: 13px;
  border-bottom: 1px solid #f5f5f5;

  &:last-child { border-bottom: none; }

  .label { color: #909399; }
  .value { color: #303133; }
  .refund-amount { color: #F56C6C; font-weight: 600; }
}

.detail-item {
  display: flex; gap: 10px; padding: 10px 0; border-bottom: 1px solid #f5f5f5;
  &:last-child { border-bottom: none; }

  .item-img { width: 80px; height: 80px; background: #f5f5f5; border-radius: 6px; flex-shrink: 0; overflow: hidden; }
  .img-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #ccc; font-size: 12px; }
  .item-info { flex: 1; }
  .item-name { font-size: 13px; color: #303133; }
  .item-sku { font-size: 12px; color: #909399; margin-top: 2px; }
  .item-attrs { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 4px; }
  .attr-tag {
    font-size: 11px; padding: 1px 6px;
    background: #fdf6ec; color: #E6A23C; border-radius: 3px;
  }
  .item-bottom {
    display: flex; justify-content: space-between; align-items: center; margin-top: 6px;
    font-size: 12px; color: #606266;
  }
  .item-refund { font-size: 12px; color: #F56C6C; margin-top: 4px; }
  .apply-btn { margin-top: 8px; }
}

.amount-row {
  display: flex; justify-content: space-between; padding: 8px 0; font-size: 13px; color: #606266;
  border-bottom: 1px solid #f5f5f5;

  &.total {
    border-bottom: none; font-size: 14px; font-weight: 600; color: #303133;
    :deep(.amount-text) { color: #FF6B35; font-size: 18px; }
  }

  .discount { color: #F56C6C; }
}

.aftersale-item {
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
  &:last-child { border-bottom: none; }

  .as-head { display: flex; justify-content: space-between; align-items: center; }
  .as-no { font-size: 12px; font-family: monospace; color: #606266; }
  .as-line { font-size: 12px; color: #606266; margin-top: 4px; display: flex; justify-content: space-between; }
  .as-amount { color: #F56C6C; font-weight: 600; }
  .muted { color: #909399; }
}

.action-bar {
  position: fixed;
  bottom: 64px; left: 0; right: 0;
  max-width: 750px; margin: 0 auto;
  background: #fff; padding: 10px 16px;
  display: flex; gap: 10px; justify-content: flex-end;
  border-top: 1px solid #eee;
}

// ✨ 自提码大卡片（约束 5.2）
.pickup-big-card {
  background: linear-gradient(135deg, #FDF6EC 0%, #FEF0F0 100%);
  border: 2px dashed #E6A23C;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 12px;
  text-align: center;
  cursor: pointer;

  .pickup-badge {
    display: inline-block;
    background: #E6A23C; color: #fff;
    padding: 3px 12px; border-radius: 12px;
    font-size: 12px; font-weight: 600;
    margin-bottom: 10px;
  }
  .pickup-label { font-size: 13px; color: #606266; margin-bottom: 8px; }
  .pickup-big-code {
    font-size: 44px; font-weight: 700;
    color: #F56C6C; font-family: monospace;
    letter-spacing: 5px;
  }
  .pickup-hint { font-size: 12px; color: #909399; margin-top: 8px; }
}

.step-active-tag {
  display: inline-block; font-size: 11px;
  background: #E6A23C; color: #fff;
  padding: 1px 6px; border-radius: 3px;
  margin-left: 6px;
}

.apply-goods { background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.9; }
.refundable { color: #F56C6C; font-weight: 600; }
.muted { color: #909399; }
</style>
