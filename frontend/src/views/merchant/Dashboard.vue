<!--
  商家工作台首页 Dashboard.vue
  约束 23.2：PC 后台风格，蓝色/中性色，统计在前、待办在侧、最近订单在下
  数据源（后端全已实现，前端只调接口）：
    GET /analytics/merchant/summary  → GMV / 订单数 / 履约率 / 售后率
    GET /orders/merchant             → 最近 5 笔订单
    GET /fulfillments/mine?status=PENDING_PICKUP → 待自提数量
-->
<template>
  <div class="merchant-dashboard">
    <!-- ① 顶部欢迎条 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <el-icon :size="28" color="#1677FF"><OfficeBuilding /></el-icon>
        <div class="welcome-text">
          <div class="hello">你好，{{ store.nickname || store.username }} 👋</div>
          <div class="sub">今天 {{ todayStr }} · 邻里团购商家工作台</div>
        </div>
      </div>
      <el-button type="primary" plain @click="$router.push('/merchant/fulfillment')">
        📦 去发货备货
      </el-button>
    </div>

    <!-- ② 统计卡片组 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="s in statCards" :key="s.key">
        <el-card class="stat-card" :class="s.theme" shadow="hover">
          <div class="stat-card-inner">
            <div class="stat-icon">
              <el-icon :size="28"><component :is="s.icon" /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">{{ s.label }}</div>
              <div class="stat-value">{{ s.value }}</div>
              <div class="stat-sub" v-if="s.sub">{{ s.sub }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ②.5 迷你 GMV 近 7 天趋势（P2 增强）-->
    <el-card class="mini-gmv-card" shadow="never" style="margin-bottom:16px">
      <div class="mini-gmv-header">
        <span class="mini-gmv-title">📈 近 7 天营业额趋势</span>
        <span class="mini-gmv-note">· 含退款冲减 ·</span>
      </div>
      <div ref="miniTrendRef" class="mini-chart-box" />
    </el-card>

    <!-- ③ 双栏：最近订单 + 待办提醒 -->
    <el-row :gutter="16" class="main-row">
      <!-- 左：最近订单 -->
      <el-col :span="16">
        <el-card class="orders-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">📋 最近订单</span>
              <el-link type="primary" @click="$router.push('/merchant/orders')">查看全部 →</el-link>
            </div>
          </template>
          <el-table :data="recentOrders" stripe size="default" empty-text="暂无订单" v-loading="loading">
            <el-table-column prop="orderNo" label="订单号" width="180">
              <template #default="{ row }">
                <span class="order-no">{{ row.orderNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="orderStatus" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getOrderStatusType(row.orderStatus)" size="small">
                  {{ getOrderStatusLabel(row.orderStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="payAmount" label="金额" width="110">
              <template #default="{ row }">¥{{ row.payAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="下单时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右：待办提醒 -->
      <el-col :span="8">
        <el-card class="todo-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">🔔 待办提醒</span>
            </div>
          </template>

          <!-- 待自提 -->
          <div class="todo-item" v-if="pendingPickupCount > 0" @click="$router.push('/merchant/fulfillment')">
            <div class="todo-icon pickup">📦</div>
            <div class="todo-main">
              <div class="todo-title">{{ pendingPickupCount }} 笔订单已到取货点</div>
          <div class="todo-desc">等消费者来取货，点此确认取货</div>
            </div>
            <el-icon class="todo-arrow"><ArrowRight /></el-icon>
          </div>

          <!-- 已完成 -->
          <div class="todo-item muted" v-else>
            <div class="todo-icon done">✅</div>
            <div class="todo-main">
              <div class="todo-title">暂无待办</div>
              <div class="todo-desc">所有已支付订单均已配送或自提完成</div>
            </div>
          </div>

          <el-divider />

          <!-- 商家简介（信息卡片）-->
          <div class="merchant-info">
            <div class="mi-row">
              <span class="mi-label">商家</span>
              <span class="mi-value">{{ store.nickname || store.username }}</span>
            </div>
            <div class="mi-row">
              <span class="mi-label">今日营业额</span>
              <span class="mi-value money">¥{{ summary.todayGmv?.toFixed(2) || '0.00' }}</span>
            </div>
            <div class="mi-row">
              <span class="mi-label">订单完成率</span>
              <span class="mi-value">{{ summary.fulfillmentRate?.toFixed(1) || '0.0' }}%</span>
            </div>
            <div class="mi-row">
              <span class="mi-label">售后率</span>
              <span class="mi-value">{{ summary.aftersaleRate?.toFixed(1) || '0.0' }}%</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { OfficeBuilding, Goods, Money, Checked, ArrowRight } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getMerchantSummary, getMerchantOrders, listFulfillments, getMerchantGmvTrend } from '@/api/index'
import { getOrderStatusLabel, getOrderStatusType } from '@/config/business'

const store = useUserStore()
const loading = ref(false)
const summary = reactive({ gmv: 0, todayGmv: 0, orderCount: 0, todayOrderCount: 0, fulfillmentRate: 0, aftersaleRate: 0 })
const recentOrders = ref([])
const pendingPickupCount = ref(0)

// P2 迷你 GMV 趋势
const miniTrendRef = ref(null)
let miniTrendChart = null

const todayStr = computed(() => {
  const d = new Date()
  return `${d.getMonth() + 1}月${d.getDate()}日 ${['日','一','二','三','四','五','六'][d.getDay()]}`
})

// 统计卡片配置（约束 23.2：4 卡片，蓝/绿/橙/紫四色）
const statCards = computed(() => [
  { key: 'gmv',  label: '累计营业额',     value: `¥${Number(summary.gmv || 0).toFixed(2)}`, sub: `今日 ¥${Number(summary.todayGmv || 0).toFixed(2)}`, icon: Money,   theme: 'blue' },
  { key: 'cnt',  label: '累计订单',     value: summary.orderCount || 0,                  sub: `今日 ${summary.todayOrderCount || 0} 笔`,          icon: Goods,   theme: 'green' },
  { key: 'pp',   label: '等取货订单',   value: pendingPickupCount.value,                 sub: '消费者尚未取货',                                   icon: OfficeBuilding, theme: 'orange' },
  { key: 'rate', label: '订单完成率',   value: `${Number(summary.fulfillmentRate || 0).toFixed(1)}%`, sub: `售后率 ${Number(summary.aftersaleRate || 0).toFixed(1)}%`, icon: Checked, theme: 'purple' }
])

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

function viewDetail(row) {
  // 跳订单详情（复用 consumer 的 orderNo 路由结构）
  window.open(`/#/consumer/order/${row.orderNo}`, '_blank')
}

async function loadDashboard() {
  loading.value = true
  try {
    // 1) 汇总数据（单次请求，GMV/订单数/履约率全齐）
    const s = await getMerchantSummary()
    Object.assign(summary, s || {})

    // 2) 最近 5 笔订单 — 后端 listByMerchant 返回 List<Map<order, items, summary>>，
    //    仪表盘表格用平铺字段 (orderNo / orderStatus / payAmount / createTime)，
    //    这里把 row.order 的字段平铺到 row 上，再 slice
    const o = await getMerchantOrders({ pageNum: 1, pageSize: 5 })
    const raw = o?.records || o || []
    recentOrders.value = raw.slice(0, 5).map(row => ({
      orderNo: row.order?.orderNo ?? row.orderNo,
      orderStatus: row.order?.orderStatus ?? row.orderStatus,
      payAmount: row.order?.payAmount ?? row.payAmount,
      createTime: row.order?.createTime ?? row.createTime,
      id: row.order?.id ?? row.id,
      items: row.items,
    }))

    // 3) 待自提数量
    const ff = await listFulfillments({ status: 'PENDING_PICKUP' })
    pendingPickupCount.value = (ff || []).length
  } catch (e) {
    ElMessage.warning('工作台数据加载失败：' + (e.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

// P2 迷你 GMV 趋势
async function loadMiniGmvTrend() {
  try {
    const trend = await getMerchantGmvTrend({ days: 7 }) || []
    await nextTick()
    renderMiniTrend(trend)
  } catch { /* 非关键失败静默 */ }
}

function renderMiniTrend(trend) {
  const el = miniTrendRef.value
  if (!el || !trend.length) return
  if (miniTrendChart) miniTrendChart.dispose()
  miniTrendChart = echarts.init(el)
  miniTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 20, top: 10, bottom: 24 },
    xAxis: {
      type: 'category',
      data: trend.map(p => p.date.slice(5)),
      axisLabel: { fontSize: 11 },
      axisLine: { lineStyle: { color: '#d9d9d9' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 11, formatter: (v) => v >= 1000 ? (v/1000).toFixed(1) + 'k' : v },
      splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } }
    },
    series: [{
      type: 'line',
      data: trend.map(p => Number(p.gmv || 0)),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: { color: '#1677FF' },
      lineStyle: { width: 2, color: '#1677FF' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(22,119,255,0.25)' },
          { offset: 1, color: 'rgba(22,119,255,0.02)' }
        ])
      }
    }]
  })
}

onMounted(async () => {
  await loadDashboard()
  loadMiniGmvTrend()
  window.addEventListener('resize', () => miniTrendChart?.resize())
})
onBeforeUnmount(() => {
  miniTrendChart?.dispose()
  window.removeEventListener('resize', () => miniTrendChart?.resize())
})
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.merchant-dashboard { padding: 20px; }

// 欢迎条
.welcome-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 22px 28px;
  background: linear-gradient(135deg, #e6f4ff 0%, #bae0ff 100%);
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(22, 119, 255, 0.08);
  border: 1px solid rgba(22, 119, 255, 0.06);
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    right: -30px; top: -30px;
    width: 140px; height: 140px;
    background: radial-gradient(circle, rgba(22, 119, 255, 0.15), transparent 70%);
    border-radius: 50%;
  }
}
.welcome-left { display: flex; align-items: center; gap: 14px; position: relative; z-index: 1; }
.welcome-text .hello { font-size: 19px; font-weight: 600; color: #0958d9; letter-spacing: 0.3px; }
.welcome-text .sub   { font-size: 13px; color: #595959; margin-top: 4px; }

// 统计卡片（4 色主题）
.stat-row { margin-bottom: 16px; }
.mini-gmv-card { border-radius: 10px; border: 1px solid #E8ECF0; }
.mini-gmv-card :deep(.el-card__body) { padding: 14px 18px; }
.mini-gmv-header { display: flex; align-items: baseline; gap: 8px; margin-bottom: 6px; }
.mini-gmv-title { font-size: 13px; font-weight: 600; color: #262626; }
.mini-gmv-note { font-size: 11px; color: #bfbfbf; }
.mini-chart-box { width: 100%; height: 120px; }

.stat-card {
  border-radius: 10px;
  border: 1px solid #E8ECF0;
  transition: transform $duration-fast $ease-spring, box-shadow $duration-base;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  }
}
.stat-card :deep(.el-card__body) { padding: 18px 20px; }
.stat-card-inner { display: flex; align-items: center; gap: 16px; }
.stat-icon {
  width: 52px; height: 52px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  transition: transform $duration-base $ease-spring;
}
.stat-card:hover .stat-icon { transform: scale(1.06); }
.stat-card.blue   .stat-icon { background: linear-gradient(135deg, #1677FF, #0958D9); box-shadow: 0 4px 12px rgba(22, 119, 255, 0.3); }
.stat-card.green  .stat-icon { background: linear-gradient(135deg, #52C41A, #237804); box-shadow: 0 4px 12px rgba(82, 196, 26, 0.3); }
.stat-card.orange .stat-icon { background: linear-gradient(135deg, #FA8C16, #D4380D); box-shadow: 0 4px 12px rgba(250, 140, 22, 0.3); }
.stat-card.purple .stat-icon { background: linear-gradient(135deg, #722ED1, #531DAB); box-shadow: 0 4px 12px rgba(114, 46, 209, 0.3); }

.stat-content { flex: 1; }
.stat-label  { font-size: 13px; color: #8c8c8c; margin-bottom: 4px; }
.stat-value  { font-size: 26px; font-weight: 700; color: #262626; letter-spacing: -0.5px; font-family: 'Helvetica Neue', sans-serif; }
.stat-sub    { font-size: 12px; color: #bfbfbf; margin-top: 4px; }

// 主区域
.main-row { min-height: 480px; }
.orders-card, .todo-card {
  border-radius: 10px;
  border: 1px solid #E8ECF0;
  transition: box-shadow $duration-base;

  &:hover { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04); }
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title  { font-size: 15px; font-weight: 600; color: #262626; }
.order-no    { font-family: Consolas, monospace; color: #595959; }

// 待办
.todo-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px; margin-bottom: 8px;
  border-radius: 8px; cursor: pointer;
  transition: all $duration-base $ease-smooth;
  background: #fff7e6; border: 1px solid #ffd591;

  &:hover { background: #ffe7ba; transform: translateX(2px); }
  &:active { transform: translateX(1px); }
}
.todo-item.muted {
  cursor: default;
  background: #fafafa; border-color: #f0f0f0;
  &:hover { background: #fafafa; transform: none; }
}
.todo-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; flex-shrink: 0;
}
.todo-icon.pickup { background: #fff1b8; }
.todo-icon.done   { background: #f6ffed; }
.todo-main { flex: 1; }
.todo-title { font-size: 14px; font-weight: 600; color: #262626; }
.todo-desc  { font-size: 12px; color: #8c8c8c; margin-top: 4px; }
.todo-arrow { color: #bfbfbf; transition: transform $duration-fast; }
.todo-item:hover .todo-arrow { transform: translateX(3px); color: #fa8c16; }

// 商家信息
.merchant-info { padding: 4px 0; }
.mi-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed #f0f0f0; }
.mi-row:last-child { border-bottom: none; }
.mi-label { font-size: 13px; color: #8c8c8c; }
.mi-value { font-size: 13px; color: #262626; font-weight: 500; }
.mi-value.money { color: #cf1322; font-weight: 600; }
</style>
