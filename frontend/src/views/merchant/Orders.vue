<template>
  <div class="orders-page">
    <!-- 顶部统计卡 -->
    <div class="stats-row">
      <div class="stat-card pending">
        <div class="stat-num">{{ stats.PENDING_PAY + stats.PAID }}</div>
        <div class="stat-label">待处理（已支付未发货）</div>
      </div>
      <div class="stat-card ff">
        <div class="stat-num">{{ (stats.PROCESSING || stats.FULFILLING) }}</div>
        <div class="stat-label">备货中</div>
      </div>
      <div class="stat-card pickup">
        <div class="stat-num">{{ stats.PENDING_PICKUP }}</div>
        <div class="stat-label">等你取货</div>
      </div>
      <div class="stat-card done">
        <div class="stat-num">{{ stats.COMPLETED }}</div>
        <div class="stat-label">已完成</div>
      </div>
      <div class="stat-card refund">
        <div class="stat-num">{{ refundOrders.length }}</div>
        <div class="stat-label">退款 / 售后</div>
      </div>
    </div>

    <!-- 筛选 Tab -->
    <div class="filter-bar">
      <el-radio-group v-model="statusFilter" size="default">
        <el-radio-button label="">全部 ({{ orders.length }})</el-radio-button>
        <el-radio-button label="PAID">待处理 ({{ stats.PAID }})</el-radio-button>
        <el-radio-button label="PROCESSING">处理中 ({{ (stats.PROCESSING || stats.FULFILLING) }})</el-radio-button>
        <el-radio-button label="PENDING_PICKUP">等你取货 ({{ stats.PENDING_PICKUP }})</el-radio-button>
        <el-radio-button label="COMPLETED">已完成 ({{ stats.COMPLETED }})</el-radio-button>
        <el-radio-button label="CANCELLED">已取消 ({{ stats.CANCELLED }})</el-radio-button>
        <el-radio-button :label="REFUND_TAB">退款/售后 ({{ refundOrders.length }})</el-radio-button>
      </el-radio-group>
      <el-input v-model="search" placeholder="订单号搜索" clearable style="width:220px" />
    </div>

    <!-- 订单列表 -->
    <div v-loading="loading" style="margin-top:16px">
      <el-empty v-if="filteredOrders.length === 0" description="暂无订单" />
      <div v-for="row in filteredOrders" :key="row.order.id" class="order-card">
        <div class="card-header">
          <div class="left">
            <span class="order-no">{{ row.order.orderNo }}</span>
            <span class="order-time">{{ formatTime(row.order.createTime) }}</span>
            <el-tag :type="getOrderStatusType(row.order.orderStatus)" round effect="dark">
              {{ getOrderStatusLabel(row.order.orderStatus) }}
            </el-tag>
            <el-tag
              v-if="row.order.aftersaleStatus && row.order.aftersaleStatus !== ORDER_AFTERSALE_STATUS.NONE"
              :type="getOrderAftersaleType(row.order.aftersaleStatus)"
              size="small"
              effect="plain"
            >
              {{ getOrderAftersaleLabel(row.order.aftersaleStatus) }}
            </el-tag>
          </div>
          <div class="right">
            <span class="pay-amount">¥{{ row.order.payAmount }}</span>
          </div>
        </div>
        <div class="card-body">
          <div class="items-wrap">
            <div v-for="it in row.items.slice(0, 4)" :key="it.id" class="item-mini">
              <el-image :src="it.image" fit="cover" class="mini-img" />
              <div class="mini-info">
                <div class="mini-name">{{ it.productName }}</div>
                <div class="mini-sub">¥{{ it.price }} × {{ it.quantity }}</div>
              </div>
            </div>
            <div v-if="row.items.length > 4" class="more">+{{ row.items.length - 4 }}</div>
          </div>
          <div class="summary">
            <div>📦 {{ row.itemCount }} 件商品 · {{ row.order.pickupPointId ? '自提' : '配送' }}</div>
            <div v-if="row.order.pickupCode">🔑 取货码：<b class="code">{{ row.order.pickupCode }}</b></div>
            <div v-if="row.order.cancelReason" class="cancel-reason">已取消：{{ row.order.cancelReason }}</div>
          </div>
        </div>
        <div class="card-footer">
          <el-button size="small" @click="openDetail(row)">查看详情</el-button>
          <el-button size="small" v-if="row.order.orderStatus === 'PAID'" type="primary" @click="openAssign(row)">分配履约</el-button>
          <el-button size="small" v-if="['PROCESSING','FULFILLING'].includes(row.order.orderStatus)" type="warning" @click="markPickup(row)">已到取货点</el-button>
          <el-button size="small" v-if="['PROCESSING','FULFILLING'].includes(row.order.orderStatus)" type="success" @click="markDelivered(row)">已送达</el-button>
          <el-button size="small" v-if="['PAID','PROCESSING','FULFILLING'].includes(row.order.orderStatus)" type="danger" plain @click="openCancel(row)">取消订单</el-button>
        </div>
      </div>
    </div>

    <!-- 详情 Dialog -->
    <el-dialog v-model="detailVisible" title="订单详情" width="680px" top="5vh">
      <div v-if="detail">
        <div class="detail-section">
          <h4>订单信息</h4>
          <div class="kv">
            <span>订单号</span><b>{{ detail.order.orderNo }}</b>
            <span>状态</span>
            <el-tag :type="getOrderStatusType(detail.order.orderStatus)">{{ getOrderStatusLabel(detail.order.orderStatus) }}</el-tag>
            <span>客户 ID</span><b>{{ detail.order.userId }}</b>
            <span>社区/取货点</span><b>{{ detail.order.communityId }} / {{ detail.order.pickupPointId || '-' }}</b>
            <span>下单时间</span><b>{{ formatTime(detail.order.createTime) }}</b>
            <span>支付时间</span><b>{{ formatTime(detail.order.payTime) }}</b>
            <span>实付金额</span><b class="amount">¥{{ detail.order.payAmount }}</b>
            <span>取货码</span><b class="code-big" v-if="detail.order.pickupCode">{{ detail.order.pickupCode }}</b><span v-else>-</span>
          </div>
        </div>
        <div class="detail-section">
          <h4>商品明细（{{ detail.items?.length || 0 }} 件）</h4>
          <el-table :data="detail.items" size="small" border>
            <el-table-column label="商品" min-width="160">
              <template #default="{ row }">
                <div style="display:flex;gap:8px;align-items:center">
                  <el-image :src="row.image" class="mini-img" />
                  <div>
                    <div>{{ row.productName }}</div>
                    <div class="muted2">{{ row.skuName }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单价" prop="price" width="80" />
            <el-table-column label="数量" prop="quantity" width="70" align="center" />
            <el-table-column label="小计" width="90" align="center">
              <template #default="{ row }">¥{{ row.subtotal }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div class="detail-section">
          <h4>状态变更日志</h4>
          <el-timeline>
            <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="formatTime(log.createTime)" placement="top">
              {{ log.fromStatus || '—' }} → <b>{{ log.toStatus }}</b>
              <span v-if="log.reason" class="muted2">（{{ log.reason }}）</span>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-dialog>

    <!-- 分配履约 Dialog -->
    <el-dialog v-model="assignVisible" title="分配履约" width="400px">
      <div>订单 {{ assignRow?.order?.orderNo }}</div>
      <el-form :model="assignForm" label-width="80px" style="margin-top:16px">
        <el-form-item label="履约类型">
          <el-radio-group v-model="assignForm.type">
            <el-radio label="PICKUP">自提</el-radio>
            <el-radio label="DELIVERY">配送</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 取消 Dialog -->
    <el-dialog v-model="cancelVisible" title="取消订单" width="400px">
      <el-input v-model="cancelReason" type="textarea" :rows="2" placeholder="请填写取消原因" />
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getOrderStatusLabel, getOrderStatusType, getOrderAftersaleLabel, getOrderAftersaleType, ORDER_STATUS, ORDER_AFTERSALE_STATUS } from '@/config/business';
import { getMerchantOrders, getOrderDetail, cancelOrder, assignFulfillment, transitionFulfillment, getMyFulfillments, markPickedUpFulfillment, markDeliveredFulfillment } from '@/api';

/** 退款/售后为前端聚合 Tab（非后端单一状态），不参与后端筛选 */
const REFUND_TAB = 'REFUND';

const orders = ref([]);
const loading = ref(false);
const statusFilter = ref('');
const search = ref('');

const detailVisible = ref(false);
const detail = ref(null);

const assignVisible = ref(false);
const assignRow = ref(null);
const assignForm = ref({ type: 'PICKUP' });

const cancelVisible = ref(false);
const cancelOrderId = ref(null);
const cancelReason = ref('');

const submitting = ref(false);

const stats = computed(() => {
  const s = { PENDING_PAY: 0, PAID: 0, PROCESSING: 0, FULFILLING: 0, PENDING_PICKUP: 0, COMPLETED: 0, CANCELLED: 0, REFUNDING: 0, REFUNDED: 0 };
  orders.value.forEach(r => { s[r.order.orderStatus] = (s[r.order.orderStatus] || 0) + 1; });
  return s;
});

/** 退款/售后订单：主状态为退款中/已退款，或订单维度售后进度非 NONE（含 COMPLETED 售后）*/
const refundOrders = computed(() =>
  orders.value.filter(r =>
    [ORDER_STATUS.REFUNDING, ORDER_STATUS.REFUNDED].includes(r.order.orderStatus)
    || (r.order.aftersaleStatus && r.order.aftersaleStatus !== ORDER_AFTERSALE_STATUS.NONE))
);

const filteredOrders = computed(() => {
  let list = orders.value;
  if (statusFilter.value === REFUND_TAB) {
    list = refundOrders.value;
  } else if (statusFilter.value === ORDER_STATUS.PROCESSING) {
    // 处理中需兼容旧 DB 值 FULFILLING
    list = list.filter(r => [ORDER_STATUS.PROCESSING, ORDER_STATUS.FULFILLING].includes(r.order.orderStatus));
  } else if (statusFilter.value) {
    list = list.filter(r => r.order.orderStatus === statusFilter.value);
  }
  if (search.value.trim()) {
    const kw = search.value.trim().toLowerCase();
    list = list.filter(r => r.order.orderNo?.toLowerCase().includes(kw));
  }
  return list;
});

function formatTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 16);
}

async function loadOrders() {
  loading.value = true;
  try {
    // 一次性拉全量后在前端分 Tab 过滤：后端按状态过滤会让其余 Tab 计数归零，
    // 且「退款/售后」是聚合维度，后端无对应单一状态值
    orders.value = await getMerchantOrders(null);
  } catch (e) {
    ElMessage.error('加载订单失败');
  } finally {
    loading.value = false;
  }
}

async function openDetail(row) {
  try {
    detail.value = await getOrderDetail(row.order.id);
    detailVisible.value = true;
  } catch (e) { ElMessage.error('加载订单详情失败：' + (e?.response?.data?.message || e?.message || '未知错误')); }
}

function openAssign(row) { assignRow.value = row; assignForm.value = { type: 'PICKUP' }; assignVisible.value = true; }
async function submitAssign() {
  submitting.value = true;
  try {
    await assignFulfillment(assignRow.value.order.id, assignForm.value.type, null);
    ElMessage.success('分配成功');
    assignVisible.value = false;
    await loadOrders();
  } catch (e) { ElMessage.error('分配履约失败：' + (e?.response?.data?.message || e?.message || '未知错误')); } finally { submitting.value = false; }
}

async function markPickup(row) {
  try {
    await ElMessageBox.confirm('确认标记该订单已送到取货点？（订单将变为「等你取货」状态，消费者届时才能看到取货码）', '确认');
  } catch { return; }
  try {
    // 拿 fulfillment id —— 用 getMyFulfillments 按 orderNo 查
    const ffList = await getMyFulfillments(null, 'PICKUP');
    const ff = ffList?.find(f => f.orderNo === row.order.orderNo);
    if (!ff) { ElMessage.error('未找到履约记录'); return; }
    await markPickedUpFulfillment(ff.id, '商家标记已送到取货点');
    ElMessage.success('已标记为等你取货');
    await loadOrders();
  } catch (e) { ElMessage.error('标记自提失败：' + (e?.response?.data?.message || e?.message || '未知错误')); }
}

async function markDelivered(row) {
  try {
    await ElMessageBox.confirm('确认标记该订单已送达？（订单将变为「已完成」状态）', '确认');
  } catch { return; }
  try {
    const ffList = await getMyFulfillments(null, 'DELIVERY');
    const ff = ffList?.find(f => f.orderNo === row.order.orderNo);
    if (!ff) { ElMessage.error('未找到履约记录'); return; }
    await markDeliveredFulfillment(ff.id, '商家标记已送达');
    ElMessage.success('已标记送达');
    await loadOrders();
  } catch (e) { ElMessage.error('标记送达失败：' + (e?.response?.data?.message || e?.message || '未知错误')); }
}

function openCancel(row) { cancelOrderId.value = row.order.id; cancelReason.value = ''; cancelVisible.value = true; }
async function submitCancel() {
  if (!cancelReason.value.trim()) { ElMessage.warning('请填写取消原因'); return; }
  submitting.value = true;
  try {
    await cancelOrder(cancelOrderId.value, cancelReason.value);
    ElMessage.success('已取消');
    cancelVisible.value = false;
    await loadOrders();
  } catch (e) { ElMessage.error('取消订单失败：' + (e?.response?.data?.message || e?.message || '未知错误')); } finally { submitting.value = false; }
}

onMounted(loadOrders);
</script>

<style scoped>
.orders-page { padding: 0; }
.stats-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card { padding: 16px; border-radius: 8px; color: #fff; text-align: center; }
.stat-card.pending  { background: linear-gradient(135deg, #e6a23c, #f0c78a); }
.stat-card.ff       { background: linear-gradient(135deg, #409eff, #66b1ff); }
.stat-card.pickup   { background: linear-gradient(135deg, #f56c6c, #f89898); }
.stat-card.done     { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-card.refund   { background: linear-gradient(135deg, #909399, #b1b3b8); }
.stat-num { font-size: 28px; font-weight: bold; }
.stat-label { font-size: 13px; opacity: 0.9; margin-top: 4px; }

.filter-bar { display: flex; justify-content: space-between; align-items: center; }

.order-card { background: #fff; border-radius: 8px; padding: 12px 16px; margin-bottom: 12px; border: 1px solid #ebeef5; }
.card-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #f0f2f5; padding-bottom: 8px; }
.left { display: flex; gap: 12px; align-items: center; }
.order-no { font-weight: bold; font-family: monospace; font-size: 13px; color: #606266; }
.order-time { color: #909399; font-size: 12px; }
.pay-amount { font-size: 20px; font-weight: bold; color: #f56c6c; }

.card-body { display: flex; gap: 16px; padding: 12px 0; }
.items-wrap { display: flex; gap: 8px; flex-wrap: wrap; flex: 1; }
.item-mini { display: flex; gap: 8px; width: 180px; }
.mini-img { width: 44px; height: 44px; border-radius: 4px; background: #f5f7fa; flex-shrink: 0; }
.mini-info { display: flex; flex-direction: column; font-size: 12px; }
.mini-name { color: #303133; font-weight: 500; }
.mini-sub { color: #909399; }
.more { font-size: 12px; color: #909399; align-self: center; }

.summary { width: 180px; font-size: 12px; color: #606266; line-height: 1.8; }
.code { background: #fdf6ec; padding: 2px 6px; border-radius: 4px; color: #e6a23c; letter-spacing: 2px; }
.cancel-reason { color: #f56c6c; }

.card-footer { display: flex; justify-content: flex-end; gap: 8px; }

.detail-section { margin-bottom: 20px; }
.detail-section h4 { margin: 0 0 12px 0; padding-bottom: 6px; border-bottom: 2px solid #409eff; display: inline-block; color: #303133; }
.kv { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 24px; font-size: 13px; }
.kv span { color: #909399; }
.kv b { color: #303133; font-weight: 500; }
.amount { color: #f56c6c; font-size: 16px; }
.code-big { background: #fdf6ec; padding: 4px 10px; border-radius: 4px; color: #e6a23c; letter-spacing: 3px; font-size: 16px; }
.muted2 { color: #909399; font-size: 12px; }
</style>