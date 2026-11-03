<!--
  商家端-发货备货管理
-->
<template>
  <div class="merchant-fulfillment">
    <div class="page-header">
      <h3>📦 发货备货管理</h3>
      <div class="sub">取货订单到货标记 & 确认取货</div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="activeStatus" size="default" @change="reload">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="PENDING_PICKUP">已到货</el-radio-button>
        <el-radio-button label="PICKED_UP">已取货</el-radio-button>
        <el-radio-button label="DELIVERING">配送中</el-radio-button>
        <el-radio-button label="EXCEPTION">异常</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 列表 -->
    <div v-loading="loading">
      <div v-if="list.length" class="ff-list">
        <div v-for="f in list" :key="f.id" class="ff-card">
          <div class="ff-head">
            <span class="order-no">订单号：{{ f.orderNo }}</span>
            <span class="ff-type" :class="f.fulfillmentType === 'PICKUP' ? 'pickup' : 'delivery'">
              {{ f.fulfillmentType === 'PICKUP' ? '到店取货' : '配送到家' }}
            </span>
            <span class="ff-status" :class="getStatusClass(f.fulfillmentStatus)">
              {{ getStatusLabel(f.fulfillmentStatus) }}
            </span>
          </div>

          <div class="ff-body">
            <div class="ff-info" v-if="f.fulfillmentType === 'PICKUP'">
              <span class="info-label">取货点</span>
              <span>#{{ f.pickupPointId || '-' }}</span>
            </div>
            <div class="ff-info">
              <span class="info-label">收货人</span>
              <span>{{ f.receiverName || '-' }} {{ f.receiverPhone || '' }}</span>
            </div>
            <div class="ff-info">
              <span class="info-label">创建时间</span>
              <span>{{ f.createTime || '-' }}</span>
            </div>
          </div>

          <div class="ff-actions">
            <el-button
              v-if="f.fulfillmentStatus === 'PENDING_ASSIGN' || f.fulfillmentStatus === 'PENDING_PICKUP' && isSelfPickup(f)"
              type="warning" size="small"
              @click="markArrived(f)"
            >
              标记已到货
            </el-button>

            <el-button
              v-if="f.fulfillmentStatus === 'PENDING_PICKUP'"
              type="success" size="small"
              @click="doConfirmPickup(f)"
            >
              ✅ 确认取货
            </el-button>

            <!-- 配送中：已送达 -->
            <el-button
              v-if="f.fulfillmentStatus === 'DELIVERING'"
              type="success" size="small"
              @click="markDelivered(f)"
            >
              已送达
            </el-button>

            <!-- 标记异常（任何非终态都可以）-->
            <el-button
              v-if="!['COMPLETED','PICKED_UP','DELIVERED','EXCEPTION'].includes(f.fulfillmentStatus)"
              size="small" plain
              @click="markException(f)"
            >
              标记异常
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无记录" />
    </div>

    <!-- 确认取货弹窗（输取货码） -->
    <el-dialog v-model="verifyVisible" title="确认取货" width="380px">
      <div v-if="verifyTarget" class="verify-content">
        <el-input
          v-model="verifyCode"
          placeholder="请输入取货码"
          size="large"
          clearable
          @keyup.enter="confirmVerify"
        >
          <template #prepend>QH</template>
        </el-input>
        <div class="verify-hint">提示：可对照消费者出示的取货码核对后确认</div>
      </div>
      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button type="success" @click="confirmVerify">确认取货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFulfillments, transitionFulfillment, confirmPickup } from '@/api'

const loading = ref(false)
const list = ref([])
const activeStatus = ref('')

const verifyVisible = ref(false)
const verifyTarget = ref(null)
const verifyCode = ref('')

onMounted(reload)

async function reload() {
  loading.value = true
  try {
    list.value = await listFulfillments({
      status: activeStatus.value || null
    }) || []
  } catch (e) { /* request.js 已提示 */ }
  loading.value = false
}

function isSelfPickup(f) { return f.fulfillmentType === 'PICKUP' }

function getStatusLabel(s) {
  const m = {
    PENDING_ASSIGN: '待备货',
    PENDING_PICKUP: '已到货',
    DELIVERING: '配送中',
    DELIVERED: '已送达',
    PICKED_UP: '已取货',
    COMPLETED: '已完成',
    EXCEPTION: '异常'
  }
  return m[s] || s
}

function getStatusClass(s) {
  if (['PICKED_UP', 'DELIVERED', 'COMPLETED'].includes(s)) return 'success'
  if (s === 'EXCEPTION') return 'danger'
  if (s === 'PENDING_PICKUP') return 'warning'
  return 'primary'
}

/**
 * 标记已到货 —— fulfillment → PENDING_PICKUP
 */
async function markArrived(f) {
  try {
    await ElMessageBox.confirm(
      `确认订单 ${f.orderNo} 的商品已送到取货点？`,
      '标记到货', { type: 'warning' }
    )
    await transitionFulfillment(f.id, 'PENDING_PICKUP', '商家标记已到货')
    ElMessage.success('已标记到货，消费者端将显示"等你取货"')
    await reload()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '操作失败') }
}

/**
 * 确认取货 —— fulfillment → PICKED_UP
 */
function doConfirmPickup(f) {
  verifyTarget.value = f
  verifyCode.value = ''
  verifyVisible.value = true
}

async function confirmVerify() {
  const code = verifyCode.value?.trim()
  if (!code) { ElMessage.warning('请输入取货码'); return }
  try {
    await confirmPickup(verifyTarget.value.id)
    verifyVisible.value = false
    ElMessage.success('✅ 确认成功！订单已完成')
    await reload()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

/**
 * 配送已送达
 */
async function markDelivered(f) {
  try {
    await ElMessageBox.confirm(`确认订单 ${f.orderNo} 已送达？`, '已送达确认', { type: 'info' })
    await transitionFulfillment(f.id, 'DELIVERED', '商家标记已送达')
    ElMessage.success('已标记送达')
    await reload()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '操作失败') }
}

async function markException(f) {
  try {
    const { value } = await ElMessageBox.prompt('请输入异常原因', '标记异常', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '异常原因不能为空'
    })
    await transitionFulfillment(f.id, 'EXCEPTION', value)
    ElMessage.warning('已标记异常')
    await reload()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '操作失败') }
}
</script>

<style scoped lang="scss">
.merchant-fulfillment { padding: 16px; }

.page-header { margin-bottom: 16px; }
.page-header h3 { margin: 0; font-size: 18px; color: #303133; }
.page-header .sub { font-size: 12px; color: #909399; margin-top: 4px; }

.filter-bar { margin-bottom: 12px; }

.ff-list { display: flex; flex-direction: column; gap: 10px; }

.ff-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.ff-head {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; background: #fafafa;
  border-bottom: 1px solid #ebeef5;

  .order-no { font-size: 13px; color: #303133; font-weight: 500; }
  .ff-type {
    font-size: 11px; padding: 2px 8px; border-radius: 10px;
    &.pickup { background: #fdf6ec; color: #E6A23C; }
    &.delivery { background: #ecf5ff; color: #409EFF; }
  }
  .ff-status {
    margin-left: auto; font-size: 12px; font-weight: 500;
    &.primary { color: #409EFF; }
    &.success { color: #67C23A; }
    &.warning { color: #E6A23C; font-weight: 600; }
    &.danger { color: #F56C6C; }
  }
}

.ff-body { padding: 10px 14px; display: flex; flex-direction: column; gap: 4px; }
.ff-info {
  font-size: 12px; color: #606266;
  .info-label { color: #909399; margin-right: 8px; }
}

.ff-actions {
  padding: 10px 14px; background: #fafafa;
  border-top: 1px solid #ebeef5; display: flex; gap: 8px; flex-wrap: wrap;
}

.verify-content { padding: 8px 0; }
.verify-hint { font-size: 12px; color: #909399; margin-top: 8px; }
</style>
