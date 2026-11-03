<template>
  <div class="merchant-groupbuys">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <h2>🧺 预售活动管理</h2>
        <span class="subtitle">创建预售活动 · 窗口结束自动生成订单 · 配送到自提点</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">开预售</el-button>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-num">{{ stats.total }}</div>
        <div class="stat-label">总活动数</div>
      </div>
      <div class="stat-card stat-open">
        <div class="stat-num">{{ stats.open }}</div>
        <div class="stat-label">预售中</div>
      </div>
      <div class="stat-card stat-warning">
        <div class="stat-num">{{ stats.closed }}</div>
        <div class="stat-label">待配货</div>
      </div>
      <div class="stat-card stat-success">
        <div class="stat-num">{{ stats.completed }}</div>
        <div class="stat-label">已完成</div>
      </div>
      <div class="stat-card stat-muted">
        <div class="stat-num">{{ stats.cancelled }}</div>
        <div class="stat-label">已关闭</div>
      </div>
      <div class="stat-card stat-amount">
        <div class="stat-num">¥{{ stats.totalAmount.toFixed(2) }}</div>
        <div class="stat-label">预售总成交额</div>
      </div>
    </div>

    <!-- 状态 Tab -->
    <div class="tabs-row">
      <el-radio-group v-model="activeStatus" size="default">
        <el-radio-button label="ALL">全部</el-radio-button>
        <el-radio-button label="OPEN">预售中</el-radio-button>
        <el-radio-button label="CLOSED">待配货</el-radio-button>
        <el-radio-button label="COMPLETED">已完成</el-radio-button>
        <el-radio-button label="CANCELLED">已关闭</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 列表 -->
    <div class="table-wrap">
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column label="活动信息" min-width="220">
          <template #default="{ row }">
            <div class="activity-cell">
              <img v-if="row.productImage" :src="row.productImage" class="mini-img" />
              <div class="mini-img placeholder">📦</div>
              <div class="info">
                <div class="title">{{ row.title }}</div>
                <div class="activity-no">{{ row.activityNo }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="140">
          <template #default="{ row }">
            {{ row.productName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="团价" width="90" align="right">
          <template #default="{ row }">
            <span class="money">¥{{ row.groupPrice }}</span>
            <span v-if="row.originalPrice" class="origin">¥{{ row.originalPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预售进度" min-width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="progressPct(row)"
              :color="progressColor(row)"
              :stroke-width="8"
              :status="row.status === 'CANCELLED' ? 'exception' : ''"
            />
            <div class="progress-text">
              已预订 {{ row.joinedQty || 0 }} / {{ row.maxGroupQty || row.minGroupQty }} 份
              <span class="max">· {{ row.joinedMembers || 0 }} 人下单</span>
              <template v-if="row.status === 'OPEN'">
                <span class="countdown">⏱ 截止 {{ fmt(row.cycleEnd) }}</span>
              </template>
              <template v-else-if="row.status === 'CLOSED'">
                <span class="closed">📦 已截止 · 待配货</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="200">
          <template #default="{ row }">
            <div>下单: {{ fmt(row.cycleStart) }} ~ {{ fmt(row.cycleEnd) }}</div>
            <div v-if="row.expectedArrive" class="time-sub">到货: {{ fmt(row.expectedArrive) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="viewMembers(row)">成员 ({{ row.joinedMembers || 0 }})</el-button>
            <el-button
              v-if="row.status === 'CLOSED'"
              size="small" type="success" link
              @click="markReady(row)"
            >一键配货</el-button>
            <el-button
              v-if="row.status === 'OPEN'"
              size="small" link type="danger"
              @click="cancelActivity(row)"
            >关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 开团弹窗 -->
    <!-- 开预售活动弹窗 -->
    <el-dialog v-model="createVisible" title="开预售活动" width="640px" destroy-on-close>
      <el-alert
        title="预售模式说明" type="info" :closable="false" show-icon
        style="margin-bottom: 14px"
      >
        下单窗口到期后自动生成订单，无论多少人下单都有效。订单配货到自提点后，用户凭取货码到店自提。
      </el-alert>
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="选择商品" required>
          <el-select
            v-model="createForm.productId"
            filterable
            placeholder="选择本店商品"
            style="width: 100%"
            @change="onProductChange"
          >
            <el-option
              v-for="p in myProducts"
              :key="p.id"
              :label="`${p.productName} (SKU: ${p.id})`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选 SKU" required>
          <el-select v-model="createForm.skuId" placeholder="选择规格" style="width: 100%">
            <el-option
              v-for="s in selectedProductSkus"
              :key="s.id"
              :label="`${s.skuName} ¥${s.price}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="活动标题">
          <el-input v-model="createForm.title" placeholder="默认用商品名 + '预售'" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="预售价" required>
              <el-input-number v-model="createForm.groupPrice" :min="0.01" :step="0.5" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="目标销量">
              <el-input-number v-model="createForm.minGroupQty" :min="1" :max="999" style="width: 100%" />
              <span class="form-hint">仅展示用</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大可售份" required>
              <el-input-number v-model="createForm.maxGroupQty" :min="1" :max="9999" style="width: 100%" />
              <span class="form-hint">不能超库存</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="每人限购">
              <el-input-number v-model="createForm.maxQtyPerPerson" :min="1" :max="createForm.maxGroupQty" style="width: 100%" />
              <span class="form-hint">防止一人刷完</span>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预售窗口">
              <el-input-number v-model="createForm.cycleDays" :min="1" :max="7" style="width: 100%" />
              <span class="form-hint">默认 3 天截单</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确认开预售</el-button>
      </template>
    </el-dialog>

    <!-- 成员弹窗 -->
    <el-dialog v-model="membersVisible" title="参团成员" width="520px" destroy-on-close>
      <el-table :data="membersList" v-loading="membersLoading" stripe size="small">
        <el-table-column label="成员" min-width="120">
          <template #default="{ row }">
            <span>用户 #{{ row.userId }}</span>
            <el-tag v-if="row.isLeader" size="small" type="warning" style="margin-left:6px">🏅 团长</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="center">{{ row.quantity }}</el-table-column>
        <el-table-column label="团价" width="100" align="right">¥{{ row.groupPrice }}</el-table-column>
        <el-table-column label="支付" width="90" align="center">
          <el-tag :type="row.payStatus === 'PENDING' ? 'warning' : 'success'" size="small">
            {{ row.payStatus === 'PENDING' ? '待支付' : row.payStatus === 'SUCCESS' ? '已支付' : row.payStatus }}
          </el-tag>
        </el-table-column>
        <el-table-column label="订单" width="120">
          <template #default="{ row }">
            <span v-if="row.orderId" class="order-link">订单#{{ row.orderId }}</span>
            <span v-else class="muted">未生成</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!membersLoading && !membersList.length" description="暂无参团成员" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const activities = ref([])
const myProducts = ref([]) // 本商家的商品（用于开团选品）
const allSkus = ref([])
const activeStatus = ref('ALL')

const filteredList = computed(() => {
  if (activeStatus.value === 'ALL') return activities.value
  return activities.value.filter(a => a.status === activeStatus.value)
})

const stats = computed(() => {
  const total = activities.value.length
  const open = activities.value.filter(a => a.status === 'OPEN').length
  const closed = activities.value.filter(a => a.status === 'CLOSED').length
  const completed = activities.value.filter(a => ['COMPLETED','FULFILLED'].includes(a.status)).length
  const cancelled = activities.value.filter(a => a.status === 'CANCELLED').length
  const totalAmount = activities.value
    .filter(a => ['CLOSED','COMPLETED','FULFILLED'].includes(a.status))
    .reduce((s, a) => s + (a.joinedQty || 0) * Number(a.groupPrice || 0), 0)
  return { total, open, closed, completed, cancelled, totalAmount }
})

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({
  productId: null,
  skuId: null,
  title: '',
  groupPrice: 1,
  minGroupQty: 10,
  maxGroupQty: 30,
  minGroupMembers: 2,
  maxQtyPerPerson: 5,
  cycleDays: 3,
})
const selectedProductSkus = computed(() => {
  if (!createForm.value.productId) return []
  return allSkus.value.filter(s => s.productId === createForm.value.productId)
})

const membersVisible = ref(false)
const membersLoading = ref(false)
const membersList = ref([])
const currentActivityId = ref(null)

// ===== 加载 =====
onMounted(async () => {
  await Promise.all([loadList(), loadMyProducts()])
})

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/merchant/group-buys')
    activities.value = res.data || res || []
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMyProducts() {
  try {
    // 商家可以拉到自己的商品（用 /products?merchantId=currentMerchant 方式或直接从 ProductsService）
    const res = await request.get('/products', { params: { page: 1, size: 1000 } })
    const all = res.data?.records || res.data || []
    // 前端没法直接知道 merchantId，用用户 store 里的
    const mid = getCurrentMerchantId()
    myProducts.value = all.filter(p => String(p.merchantId) === String(mid))
    if (myProducts.value.length === 0) {
      // 兜底：如果 filter 掉了，至少显示全部让商家选（后续后端可以强制 merchantId 过滤）
      myProducts.value = all
    }
  } catch (e) {
    // 降级：尝试拉全量 SKU
  }
  try {
    const skuRes = await request.get('/product-skus', { params: { page: 1, size: 2000 } })
    allSkus.value = skuRes.data?.records || skuRes.data || []
    if (!allSkus.value.length) {
      // 降级：没有 SKU 接口就从商品里拉
      for (const p of myProducts.value) {
        allSkus.value.push({ id: p.id * 100 + 1, productId: p.id, skuName: p.productName, price: p.price || 0 })
      }
    }
  } catch {
    for (const p of myProducts.value) {
      allSkus.value.push({ id: p.id * 100 + 1, productId: p.id, skuName: p.productName, price: p.price || 0 })
    }
  }
}

function getCurrentMerchantId() {
  try {
    const store = JSON.parse(localStorage.getItem('user') || '{}')
    return store.userId || store.id || store.merchantId || 3 // 兜底 merchantId=3
  } catch { return 3 }
}

// ===== 开团 =====
function openCreateDialog() {
  createForm.value = {
    productId: myProducts.value[0]?.id || null,
    skuId: null,
    title: '',
    groupPrice: 1,
    minGroupQty: 10,
    maxGroupQty: 30,
    minGroupMembers: 2,
    maxQtyPerPerson: 5,
    cycleDays: 3,
  }
  createVisible.value = true
}

function onProductChange(pid) {
  const skus = selectedProductSkus.value
  createForm.value.skuId = skus[0]?.id || null
  // 自动填团价为 SKU 价的 9 折
  const sku = skus[0]
  if (sku?.price) {
    createForm.value.groupPrice = Math.round(Number(sku.price) * 0.9 * 100) / 100
  }
  if (!createForm.value.title) {
    const p = myProducts.value.find(x => x.id === pid)
    if (p) createForm.value.title = p.productName + ' 团购'
  }
}

async function submitCreate() {
  if (!createForm.value.productId || !createForm.value.skuId) {
    ElMessage.warning('请选择商品和 SKU'); return
  }
  if (createForm.value.groupPrice <= 0) { ElMessage.warning('预售价必须大于 0'); return }

  creating.value = true
  try {
    await request.post('/merchant/group-buys', createForm.value)
    ElMessage.success('开预售成功！')
    createVisible.value = false
    await loadList()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '开预售失败')
  } finally {
    creating.value = false
  }
}

async function cancelActivity(row) {
  try {
    await ElMessageBox.confirm(`确定关闭预售活动「${row.title}」？关闭后将无法恢复。`, '确认关闭', { type: 'warning' })
    await request.post(`/merchant/group-buys/${row.id}/cancel`)
    ElMessage.success('已关闭')
    await loadList()
  } catch {}
}

async function viewMembers(row) {
  currentActivityId.value = row.id
  membersVisible.value = true
  membersLoading.value = true
  try {
    const res = await request.get(`/merchant/group-buys/${row.id}/members`)
    membersList.value = res.data || res || []
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    membersLoading.value = false
  }
}

// ===== 辅助 =====
const progressPct = (a) => {
  const target = a.maxGroupQty || a.minGroupQty || 1
  return Math.min(100, Math.round(((a.joinedQty || 0) / target) * 100))
}
const progressColor = (row) => {
  if (row.status === 'CANCELLED') return '#c0c4cc'
  if (row.status === 'CLOSED') return '#FF8C42'
  if (progressPct(row) >= 100) return '#52B788'
  return '#409EFF'
}
const statusLabel = (s) => ({
  OPEN: '预售中', CLOSED: '待配货', FULFILLED: '已成团',
  CANCELLED: '已关闭', COMPLETED: '已完成', SHIPPED: '已发货'
}[s] || s || '')
const statusTagType = (s) => ({
  OPEN: '', CLOSED: 'warning', FULFILLED: 'success',
  CANCELLED: 'info', COMPLETED: 'success', SHIPPED: 'primary'
}[s] || '')

async function markReady(row) {
  try {
    await ElMessageBox.confirm(
      `确认活动「${row.title}」已配送到自提点？所有 ${row.joinedQty || 0} 份已下单用户将收到取货通知。`,
      '一键配货', { confirmButtonText: '确认配货', type: 'warning' }
    )
    await request.post(`/merchant/group-buys/${row.id}/ready`)
    ElMessage.success('配货完成，已通知用户取货')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}
const fmt = (dt) => dt ? dt.replace('T', ' ').substring(0, 16) : ''
</script>

<style scoped lang="scss">
.merchant-groupbuys {
  padding: 16px 20px;
}

.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
  h2 { margin: 0 0 4px; font-size: 22px; }
  .subtitle { font-size: 13px; color: #909399; }
}

.stats-row {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px;
  margin-bottom: 16px;
  .stat-card {
    background: #fff; border-radius: 8px; padding: 14px 16px;
    border: 1px solid #ebeef5;
    .stat-num { font-size: 22px; font-weight: 700; color: #303133; font-family: Menlo, monospace; }
    .stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
    &.stat-open .stat-num { color: #409EFF; }
    &.stat-success .stat-num { color: #67C23A; }
    &.stat-muted .stat-num { color: #c0c4cc; }
    &.stat-amount .stat-num { color: #E6A23C; }
  }
}

.tabs-row { margin-bottom: 12px; }

.table-wrap {
  background: #fff; border-radius: 8px; border: 1px solid #ebeef5;
  overflow: hidden;
}

.activity-cell {
  display: flex; gap: 10px; align-items: center;
  .mini-img {
    width: 48px; height: 48px; border-radius: 6px; object-fit: cover;
    background: #f5f7fa; flex-shrink: 0;
    &.placeholder { display: flex; align-items: center; justify-content: center; font-size: 22px; }
  }
  .info { min-width: 0; }
  .title { font-weight: 600; font-size: 14px; }
  .activity-no { font-size: 11px; color: #909399; font-family: Menlo, monospace; }
}

.money { color: #E6A23C; font-weight: 600; font-family: Menlo, monospace; }
.origin { color: #c0c4cc; text-decoration: line-through; font-size: 12px; margin-left: 4px; }

.progress-text { font-size: 12px; color: #606266; margin-top: 4px; }
.max { color: #909399; }
.formed { color: #67C23A; font-weight: 600; margin-left: 4px; }

.form-hint { margin-left: 8px; font-size: 12px; color: #909399; }

.order-link { color: #409EFF; font-family: Menlo, monospace; font-size: 12px; cursor: pointer; }
.muted { color: #c0c4cc; font-size: 12px; }
</style>
