<!--
  取货点工作人员工作台 v2 — Utility Cockpit 风格
  - 待确认取货订单有 pulse 微动画 + 荧光橙左边条
  - 取货码视觉焦点更突出
  - 统计卡采用数据看板式布局
-->
<template>
  <div class="pickup-fulfillment">
    <!-- 顶部统计看板 -->
    <div class="stat-board">
      <div class="stat-card stat-pending" :class="{ pulse: pendingCount > 0 }">
        <div class="stat-icon pending-icon">⏳</div>
        <div class="stat-body">
          <div class="stat-num">{{ pendingCount }}</div>
          <div class="stat-label">待确认取货</div>
        </div>
      </div>
      <div class="stat-card stat-done">
        <div class="stat-icon done-icon">✅</div>
        <div class="stat-body">
          <div class="stat-num">{{ doneCount }}</div>
          <div class="stat-label">已确认取货</div>
        </div>
      </div>
      <div class="stat-card stat-today">
        <div class="stat-icon today-icon">📅</div>
        <div class="stat-body">
          <div class="stat-num">{{ todayDone }}</div>
          <div class="stat-label">今日确认取货</div>
        </div>
      </div>
    </div>

    <!-- 列表 -->
    <div class="ff-list" v-loading="loading">
      <div
        class="ff-card"
        :class="{
          'ff-pending': f.fulfillmentStatus === 'PENDING_PICKUP',
          'ff-done': f.fulfillmentStatus === 'COMPLETED',
        }"
        v-for="f in list"
        :key="f.id"
      >
        <!-- 状态条（左边色带） -->
        <div class="ff-side-bar"></div>

        <div class="ff-body">
          <!-- 头部 -->
          <div class="ff-head">
            <div class="order-info">
              <span class="order-no">{{ f.orderNo }}</span>
              <span class="order-sub">下单 {{ formatTime(f.createTime) }}</span>
            </div>
            <span class="status-chip" :class="f.fulfillmentStatus === 'COMPLETED' ? 'chip-done' : 'chip-pending'">
              {{ f.fulfillmentStatus === 'COMPLETED' ? '已确认取货' : '待确认取货' }}
            </span>
          </div>

          <!-- 取货码 —— 视觉焦点 -->
          <div class="pickup-code" :class="{ 'code-done': f.fulfillmentStatus === 'COMPLETED' }">
            <div class="code-left">
              <div class="code-label">取货码</div>
              <div class="code-value">{{ f.pickupCode || '-' }}</div>
            </div>
            <div class="code-right">
              <template v-if="f.fulfillmentStatus !== 'COMPLETED'">
                <div class="code-tip">念此码找货</div>
                <div class="code-emphasis">⚠ 未确认取货</div>
              </template>
              <template v-else>
                <div class="code-tip">确认取货完成</div>
                <div class="code-time">{{ formatTime(f.pickupTime || f.completeTime) }}</div>
              </template>
            </div>
          </div>

          <!-- 商品清单 -->
          <div class="ff-items">
            <div class="item-row" v-for="(it, idx) in (f.items || []).slice(0, 3)" :key="idx">
              <span class="it-qty">×{{ it.quantity }}</span>
              <span class="it-name">{{ it.productName }}<span class="it-spec" v-if="it.spec"> · {{ it.spec }}</span></span>
            </div>
            <div class="item-more" v-if="(f.items || []).length > 3">
              等 {{ f.items.length }} 件 / 共 {{ f.totalQty }} 件
            </div>
            <div class="item-empty" v-if="!f.items || !f.items.length">商品信息暂缺</div>
          </div>

          <!-- 收货人 -->
          <div class="ff-receiver" v-if="(f.receiverName && f.receiverName !== 'null') || (f.receiverPhone && f.receiverPhone !== 'null')">
            <span class="receiver-icon">👤</span>
            <span class="receiver-val">
              {{ (f.receiverName && f.receiverName !== 'null') ? f.receiverName : '消费者自提' }}
              <span v-if="f.receiverPhone && f.receiverPhone !== 'null'" class="receiver-phone">· {{ f.receiverPhone }}</span>
            </span>
          </div>

          <!-- 确认取货操作 -->
          <div class="ff-actions">
            <template v-if="f.fulfillmentStatus === 'PENDING_PICKUP'">
              <el-button type="primary" size="large" class="btn-verify" @click="doVerify(f)">
                <el-icon :size="16"><CircleCheckFilled /></el-icon>
                确认已取货
              </el-button>
              <span class="ff-total">共 {{ f.totalQty || 0 }} 件</span>
            </template>
            <template v-else-if="f.fulfillmentStatus === 'COMPLETED'">
              <div class="ff-done-badge">
                <el-icon :size="14"><Select /></el-icon>
                已完成
                <span v-if="f.lastOperatorName && f.lastOperatorName !== 'null'" class="ff-op-by">
                  · {{ f.lastOperatorName }}（{{ getUserTypeLabel(f.lastOperatorRole) }}）
                </span>
              </div>
            </template>
            <template v-else>
              <el-tag type="info" effect="plain">{{ f.fulfillmentStatus }}</el-tag>
            </template>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无确认取货订单">
        <template #default>
          <div class="empty-hint">消费者下单后，商家备货完成即可看到待确认取货订单</div>
        </template>
      </el-empty>
    </div>

    <!-- 确认取货确认弹窗 -->
    <el-dialog v-model="verifyVisible" title="确认确认取货" width="420px" :close-on-click-modal="false" class="verify-dialog">
      <div v-if="verifyTarget" class="verify-body">
        <div class="verify-section">
          <div class="verify-label">订单号</div>
          <div class="verify-value mono">{{ verifyTarget.orderNo }}</div>
        </div>
        <div class="verify-section highlight">
          <div class="verify-label">取货码</div>
          <div class="verify-value mono code">{{ verifyTarget.pickupCode }}</div>
        </div>
        <el-divider style="margin: 16px 0" />
        <div class="verify-items-title">商品清单</div>
        <div class="verify-items">
          <div v-for="(it, idx) in (verifyTarget.items || [])" :key="idx" class="verify-item-row">
            <span class="vi-name">{{ it.productName }}</span>
            <span class="vi-qty">×{{ it.quantity }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="verifyVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmVerify" :loading="verifying">确认确认取货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Select } from '@element-plus/icons-vue'
import { getUserTypeLabel } from '@/config/business'
import { listFulfillments, confirmPickup } from '@/api'

const loading = ref(false)
const list = ref([])
const verifyVisible = ref(false)
const verifyTarget = ref(null)
const verifying = ref(false)

onMounted(reload)

const pendingCount = computed(() => list.value.filter(f => f.fulfillmentStatus === 'PENDING_PICKUP').length)
const doneCount    = computed(() => list.value.filter(f => f.fulfillmentStatus === 'COMPLETED').length)
const todayDone    = computed(() => list.value.filter(f => {
  if (f.fulfillmentStatus !== 'COMPLETED') return false
  const t = (f.pickupTime || f.completeTime || '').slice(0, 10)
  return t === new Date().toISOString().slice(0, 10)
}).length)

async function reload() {
  loading.value = true
  try {
    list.value = await listFulfillments({}) || []
  } catch (e) { /* request.js 已提示 */ }
  loading.value = false
}

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

function doVerify(f) {
  verifyTarget.value = f
  verifyVisible.value = true
}

async function confirmVerify() {
  verifying.value = true
  try {
    await confirmPickup(verifyTarget.value.id)
    verifyVisible.value = false
    ElMessage.success('确认成功')
    await reload()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
  verifying.value = false
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.pickup-fulfillment { max-width: 720px; margin: 0 auto; }

// ---------- 统计看板 ----------
.stat-board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: $shadow-xs;
  border: 1px solid #E8ECF0;
  transition: transform $duration-fast $ease-spring, box-shadow $duration-base;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-sm;
  }

  .stat-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    flex-shrink: 0;
  }

  .stat-body { flex: 1; min-width: 0; }
  .stat-num { font-size: 24px; font-weight: 700; line-height: 1.1; font-family: 'Menlo', monospace; }
  .stat-label { font-size: 12px; color: #6B7280; margin-top: 2px; }
}

.stat-pending {
  border-color: rgba($pickup-warning, 0.25);
  background: linear-gradient(135deg, rgba($pickup-warning, 0.05), #fff);
  .stat-icon { background: rgba($pickup-warning, 0.12); color: $pickup-warning; }
  .stat-num { color: $pickup-warning; }
}
.stat-pending.pulse .stat-icon { animation: iconPulse 1.6s ease-in-out infinite; }

.stat-done {
  border-color: rgba($pickup-success, 0.25);
  background: linear-gradient(135deg, rgba($pickup-success, 0.05), #fff);
  .stat-icon { background: rgba($pickup-success, 0.12); color: $pickup-success; }
  .stat-num { color: $pickup-success; }
}

.stat-today {
  border-color: rgba($pickup-primary, 0.2);
  background: linear-gradient(135deg, rgba($pickup-primary, 0.04), #fff);
  .stat-icon { background: rgba($pickup-primary, 0.1); color: $pickup-primary; }
  .stat-num { color: $pickup-primary; }
}

@keyframes iconPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.15); }
}

// ---------- 订单卡片 ----------
.ff-list { display: flex; flex-direction: column; gap: 12px; }

.ff-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: $shadow-xs;
  border: 1px solid #E8ECF0;
  display: flex;
  transition: box-shadow $duration-base, transform $duration-fast $ease-smooth;

  &:hover { box-shadow: $shadow-md; }

  .ff-side-bar {
    width: 4px;
    flex-shrink: 0;
    background: #D1D5DB;
  }

  &.ff-pending .ff-side-bar {
    background: $pickup-warning;
    box-shadow: 0 0 8px rgba($pickup-warning, 0.6);
  }
  &.ff-done {
    opacity: 0.75;
    .ff-side-bar { background: $pickup-success; }
  }
}

.ff-body { flex: 1; min-width: 0; padding: 12px 16px; }

// 头部
.ff-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;

  .order-info { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
  .order-no {
    font-size: 13px; font-weight: 600; color: #1F2937;
    font-family: 'Menlo', monospace; letter-spacing: 0.3px;
  }
  .order-sub { font-size: 11px; color: #9CA3AF; flex-shrink: 0; }

  .status-chip {
    font-size: 11px; padding: 2px 10px; border-radius: 20px; font-weight: 600; letter-spacing: 0.5px;
  }
  .chip-pending {
    background: rgba($pickup-warning, 0.1);
    color: $pickup-warning;
    border: 1px solid rgba($pickup-warning, 0.3);
  }
  .chip-done {
    background: rgba($pickup-success, 0.1);
    color: $pickup-success;
    border: 1px solid rgba($pickup-success, 0.3);
  }
}

// 取货码
.pickup-code {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: linear-gradient(135deg, $pickup-primary, $pickup-primary-light);
  border-radius: 8px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;

  &.code-done {
    background: linear-gradient(135deg, #9CA3AF, #D1D5DB);
  }

  .code-left { display: flex; align-items: baseline; gap: 10px; }
  .code-label { font-size: 13px; opacity: 0.8; letter-spacing: 0.5px; }
  .code-value {
    font-size: 22px; font-weight: 700; letter-spacing: 4px;
    font-family: 'Menlo', 'Consolas', monospace;
  }

  .code-right { text-align: right; display: flex; flex-direction: column; gap: 2px; }
  .code-tip { font-size: 11px; opacity: 0.85; }
  .code-emphasis {
    font-size: 11px; font-weight: 700;
    background: rgba(255, 107, 53, 0.9);
    padding: 1px 8px;
    border-radius: 4px;
    margin-left: auto;
    width: fit-content;
  }
  .code-time { font-size: 11px; opacity: 0.75; }
}

// 商品清单
.ff-items {
  padding: 8px 12px;
  background: #F9FAFB;
  border-radius: 6px;
  margin-bottom: 8px;
}
.item-row {
  display: flex; align-items: baseline; gap: 8px;
  font-size: 13px; color: #374151;
  padding: 2px 0;
  .it-qty {
    color: $pickup-primary; font-weight: 700; font-size: 14px;
    font-family: 'Menlo', monospace;
  }
  .it-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .it-spec { color: #9CA3AF; font-size: 12px; font-weight: 400; }
}
.item-more { font-size: 11px; color: #9CA3AF; padding-top: 2px; text-align: right; }
.item-empty { font-size: 12px; color: #D1D5DB; padding: 4px 0; }

// 收货人
.ff-receiver {
  font-size: 12px; color: #6B7280;
  display: flex; align-items: center; gap: 6px;
  margin-bottom: 8px;
  .receiver-phone { color: #9CA3AF; margin-left: 4px; }
}

// 底部操作
.ff-actions {
  display: flex; justify-content: space-between; align-items: center;
}
.btn-verify {
  background: $pickup-warning;
  border: none;
  border-radius: 6px;
  font-weight: 600;
  padding: 10px 20px;
  box-shadow: 0 2px 8px rgba($pickup-warning, 0.35);
  transition: all $duration-fast $ease-spring;

  &:hover {
    background: lighten($pickup-warning, 4%);
    box-shadow: 0 4px 14px rgba($pickup-warning, 0.45);
    transform: translateY(-1px);
  }
  &:active { transform: translateY(0) scale(0.98); }
}
.ff-total { font-size: 12px; color: #9CA3AF; font-weight: 500; }
.ff-done-badge {
  color: $pickup-success;
  font-size: 13px;
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  font-weight: 600;
}
.ff-op-by { color: #9CA3AF; font-size: 11px; font-weight: 400; }

// 空状态
.empty-hint { font-size: 12px; color: #9CA3AF; }

// ---------- 弹窗 ----------
.verify-body {
  .verify-section {
    text-align: center;
    padding: 8px 0;
  }
  .verify-section.highlight {
    background: linear-gradient(135deg, rgba($pickup-primary, 0.08), transparent);
    border-radius: 8px;
    padding: 12px;
  }
  .verify-label { font-size: 12px; color: #9CA3AF; margin-bottom: 4px; }
  .verify-value { font-size: 15px; color: #1F2937; font-weight: 600; }
  .verify-value.mono { font-family: 'Menlo', monospace; letter-spacing: 1px; }
  .verify-value.code {
    font-size: 22px; color: $pickup-primary;
    letter-spacing: 4px;
  }
  .verify-items-title {
    font-size: 12px; color: #9CA3AF; margin-bottom: 6px; text-align: center;
  }
  .verify-items {
    text-align: left;
    padding: 0 12px;
    .verify-item-row {
      display: flex; justify-content: space-between;
      font-size: 13px; color: #374151;
      padding: 4px 0;
      border-bottom: 1px dashed #F3F4F6;
      &:last-child { border-bottom: none; }
    }
    .vi-qty { color: $pickup-primary; font-weight: 600; font-family: 'Menlo', monospace; }
  }
}
</style>
