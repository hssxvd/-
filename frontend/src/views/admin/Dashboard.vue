<template>
  <div class="admin-dashboard">
    <!-- 顶部标题栏 -->
    <div class="dash-header">
      <div class="dash-title">
        <h2>全局概览</h2>
        <span class="dash-sub">数据截至 {{ system.dbDate }} · {{ system.version }}</span>
      </div>
      <div class="dash-toolbar">
        <el-radio-group v-model="days" size="default" @change="load">
          <el-radio-button :label="7">近 7 天</el-radio-button>
          <el-radio-button :label="30">近 30 天</el-radio-button>
          <el-radio-button :label="90">近 90 天</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- ① 核心指标卡（9 格） -->
    <el-row :gutter="16" class="dash-metrics">
      <el-col :span="6">
        <div class="metric-card metric-orders">
          <div class="metric-label">累计订单</div>
          <div class="metric-value">{{ formatNum(metrics.orders_total) }}</div>
          <div class="metric-tip" v-if="metrics.orders_today">今日 +{{ metrics.orders_today }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric-card metric-gmv">
          <div class="metric-label">累计营业额</div>
          <div class="metric-value">¥{{ formatMoney(metrics.gmv_total) }}</div>
          <div class="metric-tip" v-if="metrics.gmv_today">今日 +¥{{ formatMoney(metrics.gmv_today) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric-card metric-users">
          <div class="metric-label">注册用户</div>
          <div class="metric-value">{{ formatNum(metrics.users) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric-card metric-merchants">
          <div class="metric-label">入驻商家</div>
          <div class="metric-value">{{ formatNum(metrics.merchants) }}</div>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="dash-metrics">
      <el-col :span="6">
        <div class="metric-card metric-products">
          <div class="metric-label">在售商品</div>
          <div class="metric-value">{{ formatNum(metrics.products) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric-card metric-communities">
          <div class="metric-label">覆盖社区</div>
          <div class="metric-value">{{ formatNum(metrics.communities) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric-card metric-aftersale" :class="{ urgent: metrics.aftersales_pending > 0 }">
          <div class="metric-label">待处理售后</div>
          <div class="metric-value">{{ formatNum(metrics.aftersales_pending) }}</div>
          <div class="metric-tip" v-if="metrics.aftersales_pending > 0">需及时介入</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="metric-card metric-stock" :class="{ urgent: (stockAlerts.warn_count + stockAlerts.empty_count) > 0 }">
          <div class="metric-label">库存预警</div>
          <div class="metric-value">{{ stockAlerts.warn_count + stockAlerts.empty_count }}</div>
          <div class="metric-tip">空仓 {{ stockAlerts.empty_count }} · 预警 {{ stockAlerts.warn_count }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- ②③ 上排：GMV 趋势 + 订单状态饼图 -->
    <el-row :gutter="16" class="dash-charts">
      <el-col :span="14">
        <div class="chart-card">
          <div class="chart-title">营业额趋势（近 {{ days }} 天）</div>
          <div ref="gmvChart" class="chart-body"></div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="chart-card">
          <div class="chart-title">订单状态分布</div>
          <div ref="statusChart" class="chart-body"></div>
        </div>
      </el-col>
    </el-row>

    <!-- ④⑤ 中排：社区聚合 + Top 商家 -->
    <el-row :gutter="16" class="dash-charts">
      <el-col :span="14">
        <div class="chart-card">
          <div class="chart-title">社区维度聚合（订单数 / 营业额）</div>
          <div ref="communityChart" class="chart-body"></div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="chart-card">
          <div class="chart-title">Top 商家营业额</div>
          <div ref="merchantChart" class="chart-body"></div>
        </div>
      </el-col>
    </el-row>

    <!-- ⑥⑦⑧ 下排：Top 商品 + 库存预警 + 近期售后 -->
    <el-row :gutter="16" class="dash-charts">
      <el-col :span="10">
        <div class="chart-card">
          <div class="chart-title">Top 热销商品（按销量）</div>
          <div ref="productChart" class="chart-body"></div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="chart-title">库存预警概览</div>
          <div class="alert-body">
            <div class="alert-ring">
              <div class="ring-center">
                <div class="ring-num">{{ stockAlerts.total_stock_rows }}</div>
                <div class="ring-lbl">总库存行</div>
              </div>
            </div>
            <div class="alert-list">
              <div class="alert-item alert-warn">
                <span class="alert-dot"></span>
                <span>低于阈值（预警）</span>
                <span class="alert-cnt">{{ stockAlerts.warn_count }}</span>
              </div>
              <div class="alert-item alert-empty">
                <span class="alert-dot"></span>
                <span>零库存（空仓）</span>
                <span class="alert-cnt">{{ stockAlerts.empty_count }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="chart-card">
          <div class="chart-title">近期售后</div>
          <div class="aftersale-list">
            <template v-if="recentAftersales.length">
              <div v-for="a in recentAftersales" :key="a.aftersale_id" class="aftersale-item">
                <div class="as-head">
                  <el-tag size="small" :type="aftersaleTagColor(a.type)" effect="plain">{{ aftersaleLabel(a.type) }}</el-tag>
                  <el-tag size="small" :type="aftersaleStatusColor(a.status)" effect="dark">{{ aftersaleStatusLabel(a.status) }}</el-tag>
                </div>
                <div class="as-order">{{ a.order_no }}</div>
                <div class="as-reason">{{ a.reason }}</div>
              </div>
            </template>
            <el-empty v-else description="暂无售后" :image-size="60" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getAdminDashboardOverview } from '@/api'

// ====== 状态 ======
const loading = ref(false)
const days = ref(30)
const metrics = reactive({})
const orderStatus = ref([])
const gmvTrend = ref([])
const communityStats = ref([])
const topMerchants = ref([])
const topProducts = ref([])
const stockAlerts = reactive({})
const recentAftersales = ref([])
const system = reactive({ version: '', dbDate: '' })

// ====== Chart refs ======
const gmvChart = ref(null)
const statusChart = ref(null)
const communityChart = ref(null)
const merchantChart = ref(null)
const productChart = ref(null)
let instances = {}

// ====== 加载数据 ======
async function load() {
  loading.value = true
  try {
    const d = await getAdminDashboardOverview({ days: days.value, limit: 5 })
    Object.assign(metrics, d.metrics || {})
    orderStatus.value = d.orderStatus || []
    gmvTrend.value = d.gmvTrend || []
    communityStats.value = d.communityStats || []
    topMerchants.value = d.topMerchants || []
    topProducts.value = d.topProducts || []
    Object.assign(stockAlerts, d.stockAlerts || {})
    recentAftersales.value = d.recentAftersales || []
    Object.assign(system, d.system || {})
    await nextTick()
    renderCharts()
  } catch (e) {
    console.error('[AdminDashboard] load fail:', e)
  } finally {
    loading.value = false
  }
}

// ====== Chart 渲染 ======
function renderCharts() {
  renderGmv()
  renderStatus()
  renderCommunity()
  renderMerchant()
  renderProduct()
}

function ensureChart(refEl, key) {
  if (!refEl?.value) return null
  if (!instances[key] || instances[key].isDisposed()) {
    instances[key] = echarts.init(refEl.value)
  }
  return instances[key]
}

function renderGmv() {
  const chart = ensureChart(gmvChart, 'gmv')
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['GMV', '订单数'], top: 0 },
    grid: { left: 50, right: 50, top: 36, bottom: 32 },
    xAxis: {
      type: 'category',
      data: gmvTrend.value.map(r => r.d),
      axisLabel: { rotate: 30, fontSize: 11 }
    },
    yAxis: [
      { type: 'value', name: 'GMV (¥)', axisLabel: { formatter: v => (v / 1000).toFixed(0) + 'k' } },
      { type: 'value', name: '订单数', position: 'right' }
    ],
    series: [
      {
        name: 'GMV', type: 'bar',
        data: gmvTrend.value.map(r => r.gmv),
        itemStyle: { color: '#409eff', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '订单数', type: 'line', yAxisIndex: 1, smooth: true,
        data: gmvTrend.value.map(r => r.orders),
        itemStyle: { color: '#e6a23c' },
        areaStyle: { color: 'rgba(230,162,60,0.12)' }
      }
    ]
  })
}

function renderStatus() {
  const chart = ensureChart(statusChart, 'status')
  if (!chart) return
  const palette = { PAID: '#409eff', PROCESSING: '#e6a23c', PENDING_PICKUP: '#909399', COMPLETED: '#67c23a', CANCELLED: '#f56c6c', REFUNDED: '#9b59b6', PENDING: '#c0c4cc' }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll', itemWidth: 12 },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '42%'],
      avoidLabelOverlap: true,
      label: { show: true, formatter: '{b}\n{c}', fontSize: 11 },
      data: orderStatus.value
        .filter(r => r.count > 0)
        .map(r => ({ name: r.label, value: r.count, itemStyle: { color: palette[r.status] || '#909399' } }))
    }]
  })
}

function renderCommunity() {
  const chart = ensureChart(communityChart, 'community')
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['GMV', '订单数'], top: 0 },
    grid: { left: 60, right: 60, top: 36, bottom: 32 },
    xAxis: { type: 'category', data: communityStats.value.map(r => r.community_name) },
    yAxis: [
      { type: 'value', name: 'GMV (¥)', axisLabel: { formatter: v => (v / 1000).toFixed(0) + 'k' } },
      { type: 'value', name: '订单数', position: 'right' }
    ],
    series: [
      {
        name: 'GMV', type: 'bar',
        data: communityStats.value.map(r => r.gmv),
        itemStyle: { color: '#67c23a', borderRadius: [3, 3, 0, 0] }
      },
      {
        name: '订单数', type: 'line', yAxisIndex: 1, smooth: true,
        data: communityStats.value.map(r => r.orders),
        itemStyle: { color: '#e6a23c' }
      }
    ]
  })
}

function renderMerchant() {
  const chart = ensureChart(merchantChart, 'merchant')
  if (!chart) return
  const sorted = [...topMerchants.value].sort((a, b) => a.gmv - b.gmv)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 100, right: 40, top: 10, bottom: 20 },
    xAxis: { type: 'value', axisLabel: { formatter: v => '¥' + (v / 1000).toFixed(0) + 'k' } },
    yAxis: { type: 'category', data: sorted.map(r => r.merchant_name) },
    series: [{
      type: 'bar', data: sorted.map(r => r.gmv),
      itemStyle: { color: '#409eff', borderRadius: [0, 3, 3, 0] },
      label: { show: true, position: 'right', formatter: p => '¥' + p.value.toLocaleString() }
    }]
  })
}

function renderProduct() {
  const chart = ensureChart(productChart, 'product')
  if (!chart) return
  const sorted = [...topProducts.value].sort((a, b) => a.qty - b.qty)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 50, top: 10, bottom: 20 },
    xAxis: { type: 'value', name: '销量' },
    yAxis: { type: 'category', data: sorted.map(r => r.product_name) },
    series: [{
      type: 'bar', data: sorted.map(r => r.qty),
      itemStyle: { color: '#9b59b6', borderRadius: [0, 3, 3, 0] },
      label: { show: true, position: 'right', formatter: p => p.value + ' 件' }
    }]
  })
}

// ====== 工具 ======
function formatNum(v) { return (v ?? 0).toLocaleString() }
function formatMoney(v) { return Number(v ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) }

function aftersaleLabel(t) { return ({ REFUND: '仅退款', RETURN: '退货退款', EXCHANGE: '换货', COMPENSATION: '赔付' })[t] || t }
function aftersaleTagColor(t) { return ({ REFUND: 'info', RETURN: 'warning', EXCHANGE: 'primary', COMPENSATION: 'danger' })[t] || '' }
function aftersaleStatusLabel(s) { return ({ APPLYING: '申请中', APPROVING: '审核中', APPROVED: '已通过', REJECTED: '已驳回', COMPLETED: '已完成', CANCELLED: '已取消', REFUNDED: '已退款' })[s] || s }
function aftersaleStatusColor(s) { return ({ APPLYING: 'warning', APPROVING: 'primary', APPROVED: 'success', REJECTED: 'danger', COMPLETED: 'success', REFUNDED: 'success' })[s] || 'info' }

// ====== 生命周期 ======
function handleResize() {
  Object.values(instances).forEach(c => c && !c.isDisposed() && c.resize())
}

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(instances).forEach(c => c && !c.isDisposed() && c.dispose())
  instances = {}
})

watch(days, () => load())
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.admin-dashboard { padding: 16px 20px 40px; }

// 标题栏
.dash-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
  padding: 14px 20px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #E8ECF0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}
.dash-title h2 {
  margin: 0; font-size: 18px; color: #1F2937; font-weight: 700;
  letter-spacing: 0.3px;
}
.dash-sub {
  color: #9CA3AF; font-size: 12px; margin-left: 12px; font-weight: normal;
}
.dash-toolbar { display: flex; gap: 10px; align-items: center; }

// 指标卡
.dash-metrics { margin-bottom: 16px; }
.metric-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  border: 1px solid #E8ECF0;
  border-top: 3px solid #409EFF;
  display: flex; flex-direction: column; gap: 4px;
  transition: transform $duration-fast $ease-spring, box-shadow $duration-base;
  position: relative;
  overflow: hidden;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  }

  // 装饰性背景光
  &::before {
    content: '';
    position: absolute;
    right: -20px; bottom: -20px;
    width: 80px; height: 80px;
    border-radius: 50%;
    opacity: 0.08;
    transition: transform $duration-base $ease-smooth;
  }
  &:hover::before { transform: scale(1.4); }
}

.metric-orders     { border-top-color: #409EFF; &::before { background: #409EFF; } }
.metric-gmv        { border-top-color: #52C41A; &::before { background: #52C41A; } }
.metric-users      { border-top-color: #FA8C16; &::before { background: #FA8C16; } }
.metric-merchants  { border-top-color: #722ED1; &::before { background: #722ED1; } }
.metric-products   { border-top-color: #13C2C2; &::before { background: #13C2C2; } }
.metric-communities{ border-top-color: #FA541C; &::before { background: #FA541C; } }
.metric-aftersale  { border-top-color: #8C8C8C; &::before { background: #8C8C8C; } }
.metric-stock      { border-top-color: #F5222D; &::before { background: #F5222D; } }

.metric-card.urgent {
  background: linear-gradient(135deg, #fff1f0, #fff);
  border-color: #FFCCC7;

  &::before { background: #F5222D; opacity: 0.12; }
}

.metric-label { font-size: 12px; color: #9CA3AF; position: relative; z-index: 1; }
.metric-value {
  font-size: 26px; font-weight: 700; color: #1F2937;
  letter-spacing: -0.5px; position: relative; z-index: 1;
  font-family: 'Helvetica Neue', sans-serif;
}
.metric-tip { font-size: 12px; color: #52C41A; position: relative; z-index: 1; }
.metric-card.urgent .metric-tip { color: #F5222D; font-weight: 600; }

// 图表卡
.dash-charts { margin-bottom: 16px; }
.chart-card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #E8ECF0;
  padding: 14px 18px 18px;
  height: 100%;
  transition: box-shadow $duration-base;

  &:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05); }
}
.chart-title {
  font-size: 14px; font-weight: 600; color: #1F2937;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #F0F0F0;
  letter-spacing: 0.3px;
}
.chart-body { width: 100%; height: 280px; }

// 库存预警
.alert-body {
  display: flex; gap: 14px; align-items: center; height: 280px;
  padding: 8px 0;
}
.alert-ring {
  position: relative; width: 110px; height: 110px;
  border-radius: 50%;
  background: conic-gradient(#409eff 100%, #ebeef5 0);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}
.ring-center {
  position: absolute; inset: 8px;
  border-radius: 50%; background: #fff;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  box-shadow: inset 0 0 0 1px #F0F0F0;
}
.ring-num {
  font-size: 22px; font-weight: 700; color: #409eff;
  font-family: 'Helvetica Neue', sans-serif;
}
.ring-lbl { font-size: 11px; color: #9CA3AF; }
.alert-list { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.alert-item {
  display: flex; align-items: center; gap: 10px;
  font-size: 13px; color: #4B5563;
  padding: 8px 12px;
  background: #FAFAFA;
  border-radius: 8px;
  transition: background $duration-fast;

  &:hover { background: #F3F4F6; }
}
.alert-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.alert-warn .alert-dot { background: #FAAD14; box-shadow: 0 0 6px rgba(250, 173, 20, 0.5); }
.alert-empty .alert-dot { background: #F5222D; box-shadow: 0 0 6px rgba(245, 34, 45, 0.5); }
.alert-cnt { margin-left: auto; font-weight: 700; color: #1F2937; font-size: 15px; font-family: 'Menlo', monospace; }

// 售后列表
.aftersale-list { height: 280px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding-right: 2px; }
.aftersale-list::-webkit-scrollbar { width: 4px; }
.aftersale-list::-webkit-scrollbar-thumb { background: #E5E7EB; border-radius: 2px; }

.aftersale-item {
  border-left: 3px solid #409EFF;
  padding: 8px 12px;
  background: #FAFAFA;
  border-radius: 0 6px 6px 0;
  transition: background $duration-fast;

  &:hover { background: #F0F5FF; }
}
.as-head { display: flex; gap: 4px; margin-bottom: 4px; flex-wrap: wrap; }
.as-order { font-size: 12px; color: #9CA3AF; font-family: 'Menlo', monospace; }
.as-reason {
  font-size: 12px; color: #4B5563; margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%;
}
</style>
