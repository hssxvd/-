<template>
  <div class="groupbuy-page">
    <div class="page-header">
      <h2>👥 邻里团购</h2>
      <p class="page-sub">团越多人 · 省越多钱</p>
      <el-button class="mygb-entry" size="small" circle @click="$router.push('/consumer/my-groupbuys')" title="我的参团">
        🧺
      </el-button>
    </div>

    <div v-if="loading" class="loading-box">加载中...</div>
    <div v-else-if="!activities.length" class="empty-box">
      <div class="empty-emoji">🤷</div>
      <div>暂无进行中的团购活动</div>
    </div>

    <div v-else class="gb-list">
      <div
        v-for="a in activities"
        :key="a.id"
        class="gb-card"
        @click="openDetail(a)"
      >
        <div class="gb-card-head">
          <div class="gb-status" :class="'status-' + a.status">
            {{ statusLabel(a.status) }}
          </div>
          <div class="gb-time">
            📅 {{ formatTime(a.cycleStart) }} ~ {{ formatTime(a.cycleEnd) }}
          </div>
        </div>

        <div class="gb-card-body">
          <div class="gb-img-wrap">
            <img
              v-if="a.productImage"
              :src="a.productImage"
              :alt="a.productName || a.title"
              class="gb-img"
            />
            <div v-else class="gb-img placeholder">📦</div>
          </div>
          <div class="gb-info">
            <div class="gb-title">{{ a.title }}</div>
            <div v-if="a.productName && a.productName !== a.title" class="gb-product">
              {{ a.productName }}
            </div>
            <div class="gb-price-row">
              <span class="gb-price">¥{{ a.groupPrice }}</span>
              <span v-if="a.originalPrice" class="gb-origin">¥{{ a.originalPrice }}</span>
              <span class="gb-discount">省{{ Math.round((1 - a.groupPrice / a.originalPrice) * 100) }}%</span>
            </div>
          </div>
        </div>

        <div class="gb-progress-wrap">
          <div class="gb-progress-bar">
            <div class="gb-progress-fill" :style="{ width: progressOf(a) + '%' }" />
          </div>
          <div class="gb-progress-text">
            <template v-if="a.status === 'OPEN'">
              已预订 <b>{{ joinedQty(a) }}</b> / {{ a.maxGroupQty || a.minGroupQty }} 份
              <span class="gb-divider">·</span>
              <b>{{ joinedMembers(a) }}</b> 人参与
              <span class="gb-divider">·</span>
              <span class="gb-countdown">⏱ 距下单截止 {{ countdown(a.cycleEnd) }}</span>
            </template>
            <template v-else-if="a.status === 'CLOSED'">
              📦 配货中 · 共 {{ joinedQty(a) }} 份待配
            </template>
            <template v-else-if="a.status === 'CANCELLED'">
              🛑 活动已结束
            </template>
            <template v-else>
              ✅ 已完成
            </template>
          </div>
        </div>

        <div class="gb-card-foot">
          <span class="gb-limit">上限 {{ a.maxGroupQty }} 份</span>
          <el-button
            v-if="a.status === 'OPEN'"
            type="primary" size="small" @click.stop="openJoinDialog(a)">
            {{ joinedQty(a) > 0 ? '追加订购' : '立即订购' }}
          </el-button>
          <el-tag v-else-if="a.status === 'CLOSED'" type="warning" size="small" effect="plain">配货中</el-tag>
          <el-tag v-else type="info" size="small" effect="plain">已截止</el-tag>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="currentActivity?.title" width="420px" destroy-on-close>
      <div v-if="detailLoading" class="loading-box">加载中...</div>
      <div v-else-if="currentActivity" class="detail-content">
        <div class="detail-price">
          <span class="gb-price big">¥{{ currentActivity.groupPrice }}</span>
          <span v-if="currentActivity.originalPrice" class="gb-origin">¥{{ currentActivity.originalPrice }}</span>
        </div>
        <div class="detail-info">
          <div>社区 ID: {{ currentActivity.communityId }}</div>
          <div>截单时间: {{ formatTime(currentActivity.cycleEnd) }}</div>
          <div>预计到货: {{ formatTime(currentActivity.expectedArrive) }}</div>
        </div>
        <div v-if="detailProgress" class="detail-progress">
          <el-progress
            :percentage="detailProgress.progressPct || 0"
            :stroke-width="10"
            :color="detailProgress.isFormed ? '#52B788' : '#FF8C42'"
          />
          <div class="detail-members" v-if="detailProgress.members?.length">
            <div class="members-title">参团成员 ({{ detailProgress.members.length }})</div>
            <div class="members-list">
              <div v-for="m in detailProgress.members" :key="m.id" class="member-item">
                <span>👤 用户#{{ m.userId }}</span>
                <span>{{ m.isLeader ? '🏅 团长' : '' }} × {{ m.quantity }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="openJoinDialog(currentActivity)">参与此团</el-button>
      </template>
    </el-dialog>

    <!-- 预售下单弹窗 -->
    <el-dialog v-model="joinVisible" title="预售下单" width="400px">
      <div v-if="currentActivity" class="join-form">
        <div class="join-title">{{ currentActivity.title }}</div>
        <div class="join-price">¥{{ currentActivity.groupPrice }} / 份
          <span v-if="currentActivity.originalPrice" class="join-origin">原价 ¥{{ currentActivity.originalPrice }}</span>
        </div>
        <div class="join-qty">
          <div class="join-label">购买数量</div>
          <el-input-number v-model="joinQty" :min="1" :max="joinMax" size="large" />
          <div class="join-limit-hint">每人限购 1-{{ joinMax }} 份 · 活动共 {{ currentActivity.maxGroupQty }} 份可售</div>
        </div>
        <div v-if="!currentActivity.pickupPointId" class="join-pickup">
          <div class="join-label">自提点（配货到这里）</div>
          <el-select v-model="selectedPickupPoint" placeholder="请选择自提点" size="large" style="width: 100%">
            <el-option
              v-for="p in pickupPoints" :key="p.id"
              :label="(p.name || p.pointName || '') + ' · ' + (p.address || '')"
              :value="p.id" />
          </el-select>
        </div>
        <div v-else class="join-pickup">
          <div class="join-label">自提点</div>
          <div class="join-pickup-fixed">商家指定：{{ activityPickupPointName }}</div>
        </div>
        <div class="join-total">合计: <b>¥{{ (currentActivity.groupPrice * joinQty).toFixed(2) }}</b></div>
        <div class="join-tip">📌 本次为预售订单，将于 {{ formatTime(currentActivity.cycleEnd) }} 截止下单后统一配货到自提点，预计 {{ formatTime(currentActivity.expectedArrive) }} 送达</div>
      </div>
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" :loading="joinLoading" @click="confirmJoin">确认下单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getGroupBuys, getGroupBuyDetail, joinGroupBuy, getPickupPoints } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const loading = ref(false)
const activities = ref([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentActivity = ref(null)
const detailProgress = ref(null)

const joinVisible = ref(false)
const joinLoading = ref(false)
const joinQty = ref(1)
const selectedPickupPoint = ref(null)
const pickupPoints = ref([])

// 商家指定自提点时显示名称（从 pickupPoints 里找）
const activityPickupPointName = computed(() => {
  const a = currentActivity.value
  if (!a?.pickupPointId) return ''
  const p = pickupPoints.value.find(x => x.id === a.pickupPointId)
  return p ? `${p.name} · ${p.address}` : `#${a.pickupPointId}`
})

// 加载自提点列表（按当前用户社区）
const loadPickupPoints = async () => {
  try {
    const res = await getPickupPoints(userStore.communityId || null)
    pickupPoints.value = res?.data || res || []
  } catch { pickupPoints.value = [] }
}

const joinMax = computed(() => {
  const a = currentActivity.value
  if (!a) return 10
  // 优先活动配置的 maxQtyPerPerson，其次 min(后端单次上限 10, maxGroupQty)
  return a.maxQtyPerPerson ? a.maxQtyPerPerson : Math.min(10, a.maxGroupQty || 10)
})

const load = async () => {
  loading.value = true
  try {
    const res = await getGroupBuys(userStore.communityId || 1)
    activities.value = res.data || res || []
  } catch (e) {
    console.warn('[GroupBuy] 加载失败:', e?.message)
  } finally {
    loading.value = false
  }
}

const openDetail = async (a) => {
  currentActivity.value = a
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getGroupBuyDetail(a.id)
    const d = res.data || res
    if (d.activity) currentActivity.value = d.activity
    detailProgress.value = d.progress || null
  } catch (e) {
    ElMessage.error('详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

const openJoinDialog = async (a) => {
  currentActivity.value = a
  joinQty.value = 1
  selectedPickupPoint.value = null
  joinVisible.value = true
  // 商家没指定自提点 → 加载自提点列表让用户选
  if (!a?.pickupPointId) {
    await loadPickupPoints()
  } else {
    // 商家指定 → 只加载那一个自提点用于显示名称
    await loadPickupPoints()
  }
}

const confirmJoin = async () => {
  if (!currentActivity.value) return
  if (currentActivity.value.status !== 'OPEN') {
    ElMessage.warning('下单窗口已关闭')
    return
  }
  joinLoading.value = true
  try {
    await joinGroupBuy(currentActivity.value.id, {
      quantity: joinQty.value,
      pickupPointId: selectedPickupPoint.value || null
    })
    ElMessage.success('预售下单成功！')
    joinVisible.value = false
    detailVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.message || '下单失败')
  } finally {
    joinLoading.value = false
  }
}

const joinedQty = (a) => a.joinedQty ?? a.joined_qty ?? 0
const joinedMembers = (a) => a.joinedMembers ?? a.joined_members ?? 0
// 预售模式：进度 = 已预订 / 最大可售
const progressOf = (a) => {
  const target = a.maxGroupQty || a.minGroupQty || 1
  return Math.min(100, Math.round((joinedQty(a) / target) * 100))
}

// 预售倒计时：cycleEnd → "HH:MM:SS" 或 "已截止"
const countdown = (cycleEnd) => {
  if (!cycleEnd) return '--:--:--'
  const diff = new Date(cycleEnd).getTime() - Date.now()
  if (diff <= 0) return '已截止'
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const s = Math.floor((diff % 60000) / 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

const statusLabel = (s) => {
  const map = { OPEN: '进行中', CLOSED: '配货中', FULFILLED: '已成团', CANCELLED: '已结束', COMPLETED: '已完成' }
  return map[s] || s || ''
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

.groupbuy-page {
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
  position: relative;

  h2 { margin: 0; font-size: 20px; font-weight: 700; }
  .page-sub { margin: 4px 0 0; font-size: 12px; opacity: 0.9; }

  .mygb-entry {
    position: absolute; right: 14px; top: 50%;
    transform: translateY(-50%);
    background: rgba(255,255,255,0.25) !important;
    border: none; color: #fff; font-size: 16px;
    backdrop-filter: blur(4px);
  }
}

.loading-box, .empty-box {
  text-align: center;
  padding: 40px 20px;
  color: $consumer-text-soft;
  font-size: 13px;
}
.empty-emoji { font-size: 48px; margin-bottom: 8px; }

.gb-list { display: flex; flex-direction: column; gap: 12px; }

.gb-card {
  background: $consumer-cream;
  border-radius: $radius-md;
  padding: 14px;
  cursor: pointer;
  border: 1px solid rgba($consumer-primary, 0.08);
  transition: transform $duration-fast $ease-spring, box-shadow $duration-base;

  &:hover { transform: translateY(-2px); box-shadow: $shadow-consumer-card; }
}

.gb-card-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px;
}
.gb-status {
  font-size: 11px; font-weight: 600;
  padding: 2px 8px; border-radius: 4px;
  &.status-OPEN { background: rgba($consumer-primary, 0.15); color: $consumer-primary-dark; }
  &.status-FULFILLED { background: rgba(#52B788, 0.15); color: #3a8a5f; }
  &.status-CANCELLED { background: rgba(#F56C6C, 0.15); color: #c45656; }
}
.gb-time { font-size: 11px; color: $consumer-text-soft; }

.gb-title {
  font-size: 15px; font-weight: 700; color: $consumer-text;
  margin-bottom: 4px;
}
.gb-product {
  font-size: 12px; color: $consumer-text-soft; margin-bottom: 6px;
}

.gb-card-body {
  display: flex; gap: 12px;
  margin-bottom: 10px;
}
.gb-img-wrap {
  width: 72px; height: 72px; flex-shrink: 0;
  border-radius: 8px; overflow: hidden;
  background: rgba($consumer-primary, 0.08);
  display: flex; align-items: center; justify-content: center;
}
.gb-img {
  width: 100%; height: 100%; object-fit: cover;
}
.gb-img.placeholder {
  font-size: 28px;
}
.gb-info { flex: 1; min-width: 0; }
.gb-members { color: $consumer-text-soft; font-size: 11px; margin-left: 2px; }

.gb-price-row {
  display: flex; align-items: baseline; gap: 8px;
  margin-bottom: 10px;
}
.gb-price { font-size: 20px; font-weight: 800; color: $consumer-primary-dark; font-family: 'Menlo', monospace; }
.gb-price.big { font-size: 28px; }
.gb-origin { font-size: 12px; color: $consumer-text-soft; text-decoration: line-through; }
.gb-discount {
  font-size: 11px; color: #fff; background: $consumer-primary;
  padding: 1px 6px; border-radius: 3px; font-weight: 600;
}

.gb-progress-wrap { margin-bottom: 10px; }
.gb-progress-bar {
  height: 8px; background: rgba($consumer-primary, 0.12);
  border-radius: 4px; overflow: hidden; margin-bottom: 4px;
}
.gb-progress-fill {
  height: 100%; background: linear-gradient(90deg, #FF8C42, #FFB27A);
  border-radius: 4px; transition: width 0.4s ease;
}
.gb-progress-text { font-size: 12px; color: $consumer-text-soft; }
.gb-tojoin { color: $consumer-primary; margin-left: 4px; }
.gb-formed { color: #52B788; margin-left: 4px; font-weight: 600; }

.gb-card-foot {
  display: flex; justify-content: space-between; align-items: center;
}
.gb-limit { font-size: 11px; color: $consumer-text-soft; }

.detail-content {
  .detail-price { margin-bottom: 12px; }
  .detail-info {
    background: rgba($consumer-primary, 0.06);
    border-radius: 8px; padding: 10px 12px;
    font-size: 12px; color: $consumer-text-soft; line-height: 1.8;
    margin-bottom: 12px;
  }
  .detail-progress { margin-bottom: 8px; }
  .members-title { font-size: 12px; color: $consumer-text-soft; margin: 10px 0 6px; }
  .members-list {
    max-height: 180px; overflow-y: auto;
    border-top: 1px solid rgba(0,0,0,0.05); padding-top: 6px;
  }
  .member-item {
    display: flex; justify-content: space-between;
    padding: 4px 0; font-size: 12px; color: $consumer-text;
  }
}

.join-form {
  text-align: center; padding: 10px 0;
  .join-title { font-size: 14px; font-weight: 600; color: $consumer-text; margin-bottom: 4px; }
  .join-price { font-size: 12px; color: $consumer-text-soft; margin-bottom: 14px; }
  .join-qty { margin-bottom: 14px; }
  .join-total { font-size: 14px; color: $consumer-text; }
  .join-total b { color: $consumer-primary-dark; font-size: 18px; font-family: 'Menlo', monospace; }
}
</style>
