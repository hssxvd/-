<template>
  <div class="mygb-page">
    <div class="page-header">
      <h2>🧺 我的预售</h2>
      <p class="page-sub">预售下单 · 配货到自提点 · 凭码取货</p>
    </div>

    <div v-if="loading" class="loading-box">加载中...</div>
    <div v-else-if="!items.length" class="empty-box">
      <div class="empty-emoji">🤷‍♀️</div>
      <div>还没参与过任何团购</div>
      <el-button type="primary" size="small" @click="$router.push('/consumer/groupbuy')">去逛逛</el-button>
    </div>

    <div v-else class="mygb-list">
      <div v-for="(item, idx) in items" :key="idx" class="mygb-card">
        <div class="card-top">
          <div class="card-status" :class="'st-' + statusOf(item)">
            {{ statusLabel(item) }}
          </div>
          <span v-if="item.member.isLeader" class="leader-badge">🏅 团长</span>
        </div>

        <div class="card-body">
          <div class="img-wrap">
            <img v-if="item.activity?.productImage" :src="item.activity.productImage" class="img" />
            <div v-else class="img placeholder">📦</div>
          </div>
          <div class="info">
            <div class="title">{{ item.activity?.title || '活动#' + item.member.activityId }}</div>
            <div v-if="item.activity?.productName" class="pname">{{ item.activity.productName }}</div>
            <div class="meta">
              <span>参团 × {{ item.member.quantity }}</span>
              <span class="price">¥{{ item.member.groupPrice }}/件</span>
            </div>
            <div class="total">合计: <b>¥{{ (item.member.groupPrice * item.member.quantity).toFixed(2) }}</b></div>
          </div>
        </div>

        <div class="card-foot">
          <span class="join-time">下单于 {{ formatTime(item.member.joinTime) }}</span>
          <div class="actions">
            <!-- OPEN：预售进行中，等窗口截止 -->
            <template v-if="item.activity?.status === 'OPEN'">
              <el-tag size="small" type="primary" effect="plain">预售中</el-tag>
              <el-button size="small" type="primary" plain @click="$router.push('/consumer/groupbuy')">查看活动</el-button>
            </template>
            <!-- CLOSED：窗口已关，等商家配货 / 自提 -->
            <template v-else-if="item.activity?.status === 'CLOSED' && item.member.orderId">
              <el-tag size="small" :type="isPendingPickup(item) ? 'warning' : 'info'" effect="plain">
                {{ isPendingPickup(item) ? '待取货' : '配货中' }}
              </el-tag>
              <el-button v-if="isPendingPickup(item)" size="small" type="primary" @click="showPickupCode(item)">取货码</el-button>
              <el-button v-else size="small" plain @click="goOrderDetail(item.member.orderId)">查看订单</el-button>
            </template>
            <!-- COMPLETED / FULFILLED：已完成 -->
            <template v-else-if="['COMPLETED','FULFILLED'].includes(item.activity?.status)">
              <el-tag size="small" type="success">已完成</el-tag>
              <el-button size="small" plain @click="goOrderDetail(item.member.orderId)">订单详情</el-button>
            </template>
            <!-- CANCELLED：已取消 -->
            <template v-else-if="item.activity?.status === 'CANCELLED'">
              <el-tag size="small" type="info" effect="plain">已关闭</el-tag>
            </template>
            <!-- 其他状态 fallback -->
            <template v-else-if="item.member.orderId">
              <el-button size="small" plain @click="goOrderDetail(item.member.orderId)">查看订单</el-button>
            </template>
          </div>
        </div>

        <!-- 预售进度 / 状态展示 -->
        <div v-if="item.activity" class="card-progress">
          <template v-if="item.activity.status === 'OPEN'">
            <div class="progress-text">
              已预订 {{ item.activity.joinedQty || 0 }} / {{ item.activity.maxGroupQty || item.activity.minGroupQty || 0 }} 份
              · {{ item.activity.joinedMembers || 0 }} 人下单
              · ⏱ 截止 {{ formatTime(item.activity.cycleEnd) }}
            </div>
          </template>
          <template v-else-if="item.activity.status === 'CLOSED'">
            <div class="progress-text closed">📦 下单已截止，共 {{ item.activity.joinedQty || 0 }} 份 · {{ item.activity.joinedMembers || 0 }} 人，等待商家配货</div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyGroupBuys } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const items = ref([])

const load = async () => {
  loading.value = true
  try {
    const res = await getMyGroupBuys()
    items.value = res.data || res || []
  } catch (e) {
    ElMessage.error('加载失败: ' + e?.message)
  } finally {
    loading.value = false
  }
}

const statusOf = (item) => item.activity?.status || 'UNKNOWN'

const statusLabel = (item) => {
  const s = item.activity?.status
  if (s === 'OPEN') return '预售中'
  if (s === 'CLOSED') return isPendingPickup(item) ? '待取货' : '配货中'
  if (s === 'FULFILLED') return '已成团'
  if (s === 'CANCELLED') return '已关闭'
  if (s === 'SHIPPED') return '已发货'
  if (s === 'COMPLETED') return '已完成'
  return s || ''
}

// 判断订单是否已到 PENDING_PICKUP（可凭码取货）
const isPendingPickup = (item) => {
  const o = item.order || item.member.order
  if (o?.fulfillmentStatus === 'PENDING_PICKUP') return true
  return false
}

// 显示取货码弹窗
const showPickupCode = (item) => {
  const o = item.order || item.member.order
  if (!o?.pickupCode) { ElMessage.warning('取货码还未生成'); return }
  ElMessageBox.alert(
    `<div style="text-align:center;font-size:32px;font-weight:bold;letter-spacing:4px;color:#FF6B35;margin:16px 0 8px">${o.pickupCode}</div>
     <div style="color:#999;font-size:13px">请凭此码到自提点取货</div>
     <div style="margin-top:12px;color:#555;font-size:13px">📍 ${item.member.pickupPointName || '配送到自提点'}</div>`,
    '📦 取货码', { dangerouslyUseHTMLString: true, confirmButtonText: '我知道了' }
  )
}

const goPay = (orderId) => {
  router.push('/consumer/orders') // 跳订单列表，用户可以看到 PENDING_PAY 的订单然后支付
}

const goOrderDetail = (orderId) => {
  router.push('/consumer/orders')
}

const formatTime = (dt) => {
  if (!dt) return ''
  try {
    const d = new Date(dt)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch { return '' }
}

onMounted(load)
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.mygb-page {
  padding: 12px;
  background: $consumer-bg;
  min-height: 100%;
}

.page-header {
  background: linear-gradient(135deg, #FF8C42 0%, #FFB27A 100%);
  border-radius: $radius-md;
  padding: 16px 20px;
  color: #fff;
  margin-bottom: 16px;
  box-shadow: $shadow-consumer-card;
  h2 { margin: 0; font-size: 20px; font-weight: 700; }
  .page-sub { margin: 4px 0 0; font-size: 12px; opacity: 0.9; }
}

.loading-box, .empty-box {
  text-align: center; padding: 40px 20px; color: $consumer-text-soft; font-size: 13px;
}
.empty-emoji { font-size: 48px; margin-bottom: 8px; }

.mygb-list { display: flex; flex-direction: column; gap: 12px; }

.mygb-card {
  background: $consumer-cream;
  border-radius: $radius-md;
  padding: 14px;
  border: 1px solid rgba($consumer-primary, 0.08);
}

.card-top {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}
.card-status {
  font-size: 11px; font-weight: 600;
  padding: 2px 8px; border-radius: 4px;
  &.st-OPEN { background: rgba($consumer-primary, 0.15); color: $consumer-primary-dark; }
  &.st-FULFILLED { background: rgba(#52B788, 0.15); color: #3a8a5f; }
  &.st-CANCELLED { background: rgba(#F56C6C, 0.15); color: #c45656; }
  &.st-SHIPPED { background: rgba(#409EFF, 0.15); color: #2d7dd2; }
  &.st-COMPLETED { background: rgba(#909399, 0.15); color: #606266; }
}
.leader-badge { font-size: 11px; color: $consumer-primary; }

.card-body { display: flex; gap: 12px; margin-bottom: 10px; }
.img-wrap {
  width: 72px; height: 72px; flex-shrink: 0;
  border-radius: 8px; overflow: hidden;
  background: rgba($consumer-primary, 0.08);
}
.img { width: 100%; height: 100%; object-fit: cover; }
.img.placeholder { font-size: 28px; display: flex; align-items: center; justify-content: center; }
.info { flex: 1; min-width: 0; }
.title { font-size: 14px; font-weight: 700; color: $consumer-text; margin-bottom: 2px; }
.pname { font-size: 12px; color: $consumer-text-soft; margin-bottom: 4px; }
.meta {
  display: flex; gap: 8px; font-size: 11px; color: $consumer-text-soft; margin-bottom: 2px;
}
.price { color: $consumer-primary; font-weight: 600; }
.total { font-size: 13px; color: $consumer-text; }
.total b { color: $consumer-primary-dark; font-family: 'Menlo', monospace; font-size: 15px; }

.card-foot {
  display: flex; justify-content: space-between; align-items: center;
  padding-top: 8px; border-top: 1px solid rgba(0,0,0,0.05);
}
.join-time { font-size: 11px; color: $consumer-text-soft; }
.actions { display: flex; gap: 6px; align-items: center; }

.card-progress {
  margin-top: 8px; padding-top: 8px; border-top: 1px solid rgba(0,0,0,0.05);
  .progress-text { font-size: 12px; color: $consumer-text-soft; }
  .formed { color: #52B788; font-weight: 600; margin-left: 4px; }
  .tojoin { color: $consumer-primary; margin-left: 4px; }
}
</style>
