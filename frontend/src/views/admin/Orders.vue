<template>
  <div class="orders-page">
    <!-- 状态 Tab -->
    <el-card class="filter-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待确认" name="PENDING_CONFIRM" />
        <el-tab-pane label="待支付" name="PENDING_PAY" />
        <el-tab-pane label="已支付" name="PAID" />
        <el-tab-pane label="备货中" name="FULFILLING" />
        <el-tab-pane label="等你取货" name="PENDING_PICKUP" />
        <el-tab-pane label="已完成" name="COMPLETED" />
        <el-tab-pane label="已取消" name="CANCELLED" />
        <el-tab-pane label="退款中/已退款" name="REFUNDING__OR__REFUNDED" />
      </el-tabs>

      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="订单号">
          <el-input v-model="filters.orderNo" placeholder="orderNo" style="width: 200px" clearable />
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select v-model="filters.payStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="s in statusOptions.payStatus" :key="s" :label="payStatusLabel(s)" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="履约状态">
          <el-select v-model="filters.fulfillmentStatus" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="s in statusOptions.fulfillmentStatus" :key="s" :label="fulfillmentLabel(s)" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额≥">
          <el-input-number v-model="filters.minAmount" :min="0" :precision="2" controls-position="right" style="width: 130px" />
        </el-form-item>
        <el-form-item label="金额≤">
          <el-input-number v-model="filters.maxAmount" :min="0" :precision="2" controls-position="right" style="width: 130px" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="filters.timeRange" type="datetimerange"
            start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DDTHH:mm:ss" style="width: 360px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 订单表格 -->
    <el-card class="table-card">
      <el-table :data="records" v-loading="loading" stripe height="calc(100vh - 340px)">
        <el-table-column prop="order.orderNo" label="订单号" width="200">
          <template #default="{ row }">
            <code class="order-no">{{ row.order.orderNo }}</code>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="100">
          <template #default="{ row }">
            <span>{{ row.userNickname || 'u#' + row.order.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商家" width="110">
          <template #default="{ row }">{{ row.merchantName || '—' }}</template>
        </el-table-column>
        <el-table-column label="社区" width="120">
          <template #default="{ row }">{{ row.communityName || '—' }}</template>
        </el-table-column>
        <el-table-column label="商品" width="68">
          <template #default="{ row }">
            <el-image v-if="row.firstItemImage" :src="row.firstItemImage" fit="cover" style="width:48px;height:48px;border-radius:4px;border:1px solid #ebeef5;">
              <template #error><div style="width:48px;height:48px;background:#f5f7fa;border-radius:4px;display:flex;align-items:center;justify-content:center;color:#c0c4cc;font-size:10px;">无图</div></template>
            </el-image>
            <div v-else style="width:48px;height:48px;background:#f5f7fa;border-radius:4px;display:flex;align-items:center;justify-content:center;color:#c0c4cc;font-size:10px;">无图</div>
          </template>
        </el-table-column>
        <el-table-column prop="order.payAmount" label="实付" width="100" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ row.order.payAmount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="120">
          <template #default="{ row }">
            <el-tag :type="orderStatusTag(row.order.orderStatus)" effect="dark" size="small">
              {{ orderStatusLabel(row.order.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.order.payStatus" size="small" type="info">{{ payStatusLabel(row.order.payStatus) }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="履约" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.order.fulfillmentStatus" size="small" type="warning">{{ fulfillmentLabel(row.order.fulfillmentStatus) }}</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="order.createTime" label="下单时间" width="170" sortable />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" size="small" v-if="canCancel(row)" @click="confirmCancel(row)">强制取消</el-button>
            <el-button link type="success" size="small" v-if="canComplete(row)" @click="confirmComplete(row)">手动完结</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
          :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="load" @current-change="load" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="订单详情" size="720px">
      <template v-if="currentDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号" :span="2">
            <code class="order-no-lg">{{ currentDetail.order.orderNo }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentDetail.userNickname || 'u#' + currentDetail.order.userId }}</el-descriptions-item>
          <el-descriptions-item label="商家">{{ currentDetail.merchantName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="社区">{{ currentDetail.communityName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="取货点">{{ currentDetail.order.pickupPointId ? '#' + currentDetail.order.pickupPointId : '—' }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="orderStatusTag(currentDetail.order.orderStatus)" effect="dark">{{ orderStatusLabel(currentDetail.order.orderStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="支付状态">
            <el-tag size="small" type="info">{{ payStatusLabel(currentDetail.order.payStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="实付"><span class="amount">¥{{ currentDetail.order.payAmount?.toFixed(2) }}</span></el-descriptions-item>
          <el-descriptions-item label="退款">
            <span v-if="currentDetail.order.refundedAmount > 0" class="refund">¥{{ currentDetail.order.refundedAmount.toFixed(2) }}</span>
            <span v-else class="muted">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ currentDetail.order.createTime }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ currentDetail.order.payTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ currentDetail.order.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="currentDetail.order.cancelReason" label="取消原因" :span="2">
            <el-alert :title="currentDetail.order.cancelReason" type="warning" :closable="false" />
          </el-descriptions-item>
        </el-descriptions>

        <el-divider>商品明细（{{ currentDetail.items?.length || 0 }} 项）</el-divider>
        <el-table :data="currentDetail.items || []" border size="small">
          <el-table-column label="商品" min-width="200">
            <template #default="{ row }">
              <div class="product-cell">
                <img v-if="row.image" :src="row.image" class="thumb" />
                <div>
                  <div class="pname">{{ row.product_name }}</div>
                  <div v-if="row.sku_name" class="muted">{{ row.sku_name }} <span v-if="row.spec">· {{ row.spec }}</span></div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="price" label="单价" width="90" align="right">
            <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="70" align="center" />
          <el-table-column prop="subtotal" label="小计" width="90" align="right">
            <template #default="{ row }">¥{{ row.subtotal?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column v-if="currentDetail?.items?.some(i => i.refundedQty > 0)" label="退款" width="120" align="center">
            <template #default="{ row }">
              <span v-if="row.refundedQty > 0" class="refund">{{ row.refundedQty }}件 ¥{{ row.refundedAmount?.toFixed(2) }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>

        <el-divider>状态流转（{{ currentDetail.logs?.length || 0 }} 条）</el-divider>
        <el-timeline>
          <el-timeline-item v-for="log in currentDetail.logs" :key="log.id" :timestamp="log.createTime" placement="top">
            <div>
              <el-tag size="small" :type="orderStatusTag(log.fromStatus)">{{ orderStatusLabel(log.fromStatus) }}</el-tag>
              <span class="arrow">→</span>
              <el-tag size="small" :type="orderStatusTag(log.toStatus)">{{ orderStatusLabel(log.toStatus) }}</el-tag>
              <span v-if="log.reason" class="muted reason">｜ {{ log.reason }}</span>
              <span v-if="log.operatorId" class="muted">｜ 操作人: {{ log.operatorRole }}#{{ log.operatorId }}</span>
            </div>
          </el-timeline-item>
          <el-timeline-item v-if="!currentDetail.logs?.length" :timestamp="currentDetail.order.createTime" placement="top">
            <el-tag size="small" type="info">ORDER_CREATED</el-tag>
            <span class="muted">｜ 订单创建</span>
          </el-timeline-item>
        </el-timeline>

        <!-- 拆单操作区 -->
        <template v-if="canSplitDetail(currentDetail)">
          <el-divider />
          <div class="split-actions">
            <el-button type="warning" :icon="DataLine" @click="onPrepareSplit(currentDetail)">强制拆单（按商家分组）</el-button>
            <span class="muted hint">脏订单/异常订单按 merchant 分组拆成多笔独立订单</span>
          </div>
        </template>
      </template>
    </el-drawer>

    <!-- 拆单预览弹窗 -->
    <el-dialog v-model="splitVisible" title="拆单预览（按商家分组）" width="640px">
      <div v-if="splitPreview">
        <el-alert type="warning" :closable="false" class="mb12">
          原订单 <b>{{ splitPreview.sourceOrder.orderNo }}</b> 将被置为 <b>SPLIT_SOURCE</b>（已拆单），
          按以下分组拆成 {{ splitPreview.groups.length }} 笔独立子订单：
        </el-alert>
        <el-table :data="splitPreview.groups" border size="small">
          <el-table-column label="子订单" width="170">
            <template #default="{ row }">
              <code>{{ splitPreview.sourceOrder.orderNo }}_{{ splitPreview.groups.indexOf(row) + 1 }}</code>
            </template>
          </el-table-column>
          <el-table-column label="商家" width="120">
            <template #default="{ row }">{{ row.merchantId ? '商家#' + row.merchantId : '未绑定' }}</template>
          </el-table-column>
          <el-table-column label="明细数" width="80" align="center">
            <template #default="{ row }">{{ row.items.length }}</template>
          </el-table-column>
          <el-table-column label="商品快照" min-width="200">
            <template #default="{ row }">
              <el-tag v-for="it in row.items" :key="it.id" size="small" effect="plain" class="mr4 mb4">{{ it.productName }} ×{{ it.quantity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="小计" width="100" align="right">
            <template #default="{ row }"><span class="amount">¥{{ row.subtotal.toFixed(2) }}</span></template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="splitVisible = false">取消</el-button>
        <el-button type="warning" :loading="splitting" @click="doSplit">确认拆单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, DataLine } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminOrderList, adminOrderDetail, adminOrderCancel, adminOrderComplete, adminOrderStatusOptions, adminOrderSplitPreview, adminOrderSplit } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const activeTab = ref('')
const statusOptions = reactive({ orderStatus: [], payStatus: [], fulfillmentStatus: [], aftersaleStatus: [] })

const filters = reactive({
  orderStatus: '',
  payStatus: '',
  fulfillmentStatus: '',
  orderNo: '',
  minAmount: null,
  maxAmount: null,
  timeRange: []
})

const detailVisible = ref(false)
const currentDetail = ref(null)

// ---------- label 映射 ----------
const OS_MAP = {
  PENDING_CONFIRM: '待确认', PENDING_PAY: '待支付', PAID: '已支付',
  FULFILLING: '备货中', PENDING_PICKUP: '等你取货', DELIVERING: '配送中',
  DELIVERED: '已送达', PICKED_UP: '已确认取货', COMPLETED: '已完成',
  CANCELLED: '已取消', REFUNDING: '退款中', REFUNDED: '已退款', EXCEPTION: '异常'
}
function orderStatusLabel(s) { return OS_MAP[s] || s || '—' }
function orderStatusTag(s) {
  const map = { PENDING_PAY: 'warning', PAID: 'primary', FULFILLING: 'warning',
    COMPLETED: 'success', CANCELLED: 'info', REFUNDING: 'danger', REFUNDED: 'danger', EXCEPTION: 'danger' }
  return map[s] || 'info'
}
const PAY_MAP = { PENDING: '待支付', SUCCESS: '已支付', FAILED: '失败', TIMEOUT: '超时', REFUNDING: '退款中', REFUNDED: '已退款' }
const FUL_MAP = { PENDING_ASSIGN: '待指派', PENDING_PICKUP: '等你取货', DELIVERING: '配送中', DELIVERED: '已送达', PICKED_UP: '已确认取货', EXCEPTION: '异常' }
const AFS_MAP = { NONE: '无', APPLYING: '申请中', APPROVED: '已通过', REJECTED: '已拒绝', REFUNDING: '退款中', REFUNDED: '已退款' }
const payStatusLabel = s => PAY_MAP[s] || s
const fulfillmentLabel = s => FUL_MAP[s] || s
const aftersaleLabel = s => AFS_MAP[s] || s

// ---------- 状态操作 ----------
function canCancel(row) {
  return ['PENDING_CONFIRM', 'PENDING_PAY', 'PAID'].includes(row.order.orderStatus)
}
function canComplete(row) {
  return ['FULFILLING', 'PICKED_UP', 'PENDING_PICKUP'].includes(row.order.orderStatus)
}

async function confirmCancel(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '强制取消订单', {
      confirmButtonText: '确认取消', cancelButtonText: '再想想',
      inputPattern: /.+/, inputErrorMessage: '取消原因不能为空',
      inputValue: '管理员强制取消'
    })
    await adminOrderCancel(row.order.id, value)
    ElMessage.success('已取消')
    load()
  } catch {}
}

async function confirmComplete(row) {
  try {
    await ElMessageBox.confirm('确定手动完结此订单？', '手动完结', { type: 'warning', confirmButtonText: '确认完结' })
    await adminOrderComplete(row.order.id, '管理员手动完结')
    ElMessage.success('已完结')
    load()
  } catch {}
}

// ---------- 列表加载 ----------
async function load() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value, pageSize: pageSize.value,
      orderStatus: activeTab.value || undefined,
      payStatus: filters.payStatus || undefined,
      fulfillmentStatus: filters.fulfillmentStatus || undefined,
      orderNo: filters.orderNo || undefined,
      minAmount: filters.minAmount ?? undefined,
      maxAmount: filters.maxAmount ?? undefined,
      startTime: filters.timeRange?.[0] || undefined,
      endTime: filters.timeRange?.[1] || undefined
    }
    const r = await adminOrderList(params)
    records.value = r.records || []
    total.value = r.total || 0
  } catch (e) { ElMessage.error('加载订单失败') }
  finally { loading.value = false }
}

async function loadStatusOptions() {
  try {
    const r = await adminOrderStatusOptions()
    Object.assign(statusOptions, r)
  } catch {}
}

function onTabChange() { pageNum.value = 1; load() }
function onSearch() { pageNum.value = 1; load() }
function onReset() {
  activeTab.value = ''; filters.payStatus = ''; filters.fulfillmentStatus = ''
  filters.orderNo = ''; filters.minAmount = null; filters.maxAmount = null; filters.timeRange = []
  pageNum.value = 1; load()
}

async function openDetail(row) {
  try {
    const r = await adminOrderDetail(row.order.id)
    currentDetail.value = r
    detailVisible.value = true
  } catch (e) { ElMessage.error('加载详情失败') }
}


// ---------- 拆单 ----------
const splitVisible = ref(false)
const splitting = ref(false)
const splitPreview = ref(null)
const splitSourceOrderId = ref(null)

function canSplitDetail(detail) {
  if (!detail?.order) return false
  // 脏订单：merchantId=null 但有多个商家的 item
  // 或非终态（允许管理员对未完结订单拆单）
  const locked = ['COMPLETED','CANCELLED','REFUNDING','REFUNDED','SPLIT_SOURCE']
  return !locked.includes(detail.order.orderStatus) || detail.order.merchantId == null
}

async function onPrepareSplit(detail) {
  try {
    splitting.value = false
    splitSourceOrderId.value = detail.order.id
    const r = await adminOrderSplitPreview(detail.order.id)
    splitPreview.value = r
    if (!r.canSplit) { ElMessage.warning('无需拆单：所有明细属同一商家或已完结'); return }
    splitVisible.value = true
  } catch(e) { ElMessage.error(e?.response?.data?.message || '加载拆单预览失败') }
}

async function doSplit() {
  splitting.value = true
  try {
    const childIds = await adminOrderSplit(splitSourceOrderId.value, '管理员手动拆单')
    ElMessage.success('拆单成功！生成 ' + childIds.length + ' 条子订单')
    splitVisible.value = false
    detailVisible.value = false
    load() // 刷新列表
  } catch(e) {
    ElMessage.error(e?.response?.data?.message || '拆单失败')
  } finally {
    splitting.value = false
  }
}

onMounted(() => { load(); loadStatusOptions() })
</script>

<style scoped>
.orders-page { padding: 16px; }
.filter-card { margin-bottom: 12px; }
.filter-card :deep(.el-card__body) { padding: 12px 16px 0; }
.filter-form { margin-top: 8px; }
.table-card :deep(.el-card__body) { padding: 8px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding: 12px 4px; }

.order-no { font-family: Consolas, monospace; color: var(--el-color-primary); }
.order-no-lg { font-family: Consolas, monospace; font-size: 14px; color: var(--el-color-primary); }
.amount { color: var(--el-color-danger); font-weight: 600; }
.refund { color: var(--el-color-danger); }
.muted { color: #909399; }

.product-cell { display: flex; gap: 10px; align-items: center; }
.product-cell .thumb { width: 48px; height: 48px; object-fit: cover; border-radius: 4px; border: 1px solid #ebeef5; }
.product-cell .pname { font-weight: 500; }

.reason { margin-left: 6px; }
.arrow { margin: 0 4px; color: #c0c4cc; }
</style>