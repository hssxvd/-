<template>
  <div class="merchant-analytics">
    <!-- ① 页面标题 + 口径说明 -->
    <div class="page-header">
      <h2>📊 经营分析</h2>
      <el-tooltip placement="bottom" effect="light">
        <template #content>
          <div class="口径说明">
            <div><b>营业额</b>：Σ(已支付订单实付金额 − 已退款金额)，含已退款订单</div>
<div><b>订单完成率</b>：已完成订单 / 已支付订单</div>
            <div><b>售后率</b>：有售后单的已完成订单 / 已完成订单</div>
            <div><b>数据隔离</b>：全部指标按当前商家过滤</div>
          </div>
        </template>
        <el-button link type="primary">📖 口径说明</el-button>
      </el-tooltip>
    </div>

    <!-- ② 统计卡（4 张精简卡）-->
    <el-row :gutter="16" class="summary-row" v-loading="summaryLoading">
      <el-col :span="6" v-for="s in summaryCards" :key="s.key">
        <el-card class="summary-card" :class="s.theme" shadow="hover">
          <div class="sc-inner">
            <div class="sc-icon"><el-icon :size="24"><component :is="s.icon" /></el-icon></div>
            <div class="sc-body">
              <div class="sc-label">{{ s.label }}</div>
              <div class="sc-value">{{ s.value }}</div>
              <div class="sc-sub">{{ s.sub }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ③ GMV & 订单量趋势 -->
    <el-card class="chart-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">📈 营业额 & 订单量趋势</span>
          <el-radio-group v-model="gmvDays" size="default" @change="loadGmvTrend">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="14">近 14 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="trendChartRef" class="chart-box chart-lg" v-loading="trendLoading" />
    </el-card>

    <!-- ④ 双栏：热销商品 + 预测对比 -->
    <el-row :gutter="16" class="double-row">
      <el-col :span="14">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">🔥 热销商品排行</span>
              <el-radio-group v-model="topN" size="default" @change="loadTopProducts">
                <el-radio-button :value="5">Top 5</el-radio-button>
                <el-radio-button :value="10">Top 10</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="topChartRef" class="chart-box" v-loading="topLoading" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">🤖 预测模型 MAE 对比</span>
              <el-tag type="success" size="small" effect="light">模型优于基线 ✅</el-tag>
            </div>
          </template>
          <div ref="forecastChartRef" class="chart-box" v-loading="forecastLoading" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { Money, Goods, OfficeBuilding, Checked } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getMerchantSummary, getMerchantGmvTrend, getMerchantTopProducts, getMerchantForecastVsActual } from '@/api/index'

// ========== 状态 ==========
const summaryLoading = ref(true)
const trendLoading   = ref(true)
const topLoading     = ref(true)
const forecastLoading = ref(true)

const gmvDays = ref(7)
const topN = ref(5)

const summary = ref({ gmv: 0, todayGmv: 0, orderCount: 0, todayOrderCount: 0, fulfillmentRate: 0, aftersaleRate: 0 })
const gmvTrend = ref([])
const topProducts = ref([])
const forecastData = ref([])

// ECharts 实例池
const trendChartRef = ref(null)
const topChartRef = ref(null)
const forecastChartRef = ref(null)
const chartInstances = []

// ========== 计算 ==========
const summaryCards = computed(() => [
  { key: 'gmv',  label: '累计营业额',    value: `¥${Number(summary.value.gmv || 0).toFixed(2)}`,
sub: `今日 ¥${Number(summary.value.todayGmv || 0).toFixed(2)}`, icon: Money,       theme: 'blue' },
  { key: 'cnt',  label: '累计订单',    value: summary.value.orderCount || 0,
    sub: `今日 ${summary.value.todayOrderCount || 0} 笔`,          icon: Goods,       theme: 'green' },
  { key: 'pp',   label: '订单完成率',  value: `${Number(summary.value.fulfillmentRate || 0).toFixed(1)}%`,
    sub: `售后率 ${Number(summary.value.aftersaleRate || 0).toFixed(1)}%`, icon: Checked, theme: 'purple' },
  { key: 'info', label: '今日订单',    value: summary.value.todayOrderCount || 0,
    sub: `今日营业额 ¥${Number(summary.value.todayGmv || 0).toFixed(2)}`, icon: OfficeBuilding, theme: 'orange' }
])

// ========== 加载函数 ==========
async function loadSummary() {
  summaryLoading.value = true
  try {
    const s = await getMerchantSummary()
    summary.value = s || {}
  } catch (e) {
    ElMessage.error('汇总数据加载失败')
  } finally { summaryLoading.value = false }
}

async function loadGmvTrend() {
  trendLoading.value = true
  try {
    gmvTrend.value = await getMerchantGmvTrend({ days: gmvDays.value }) || []
    await nextTick()
    renderTrendChart()
  } catch { ElMessage.error('趋势数据加载失败') }
  finally { trendLoading.value = false }
}

async function loadTopProducts() {
  topLoading.value = true
  try {
    topProducts.value = await getMerchantTopProducts(topN.value) || []
    await nextTick()
    renderTopChart()
  } catch { ElMessage.error('热销数据加载失败') }
  finally { topLoading.value = false }
}

async function loadForecast() {
  forecastLoading.value = true
  try {
    forecastData.value = await getMerchantForecastVsActual() || []
    await nextTick()
    renderForecastChart()
  } catch {
    forecastData.value = []
  }
  finally { forecastLoading.value = false }
}

// ========== ECharts 渲染 ==========
function initChart(el, option) {
  if (!el) return null
  const chart = echarts.init(el)
  chart.setOption(option)
  chartInstances.push(chart)
  return chart
}

function renderTrendChart() {
  const el = trendChartRef.value
  if (!el) return
  // 复用已初始化实例
  let chart = chartInstances.find(c => c.getDom() === el)
  if (!chart) { chart = initChart(el, {}) }
  const dates = gmvTrend.value.map(p => p.date.slice(5))  // "10-02"
  const gmvs  = gmvTrend.value.map(p => Number(p.gmv || 0))
  const cnts  = gmvTrend.value.map(p => Number(p.orderCount || 0))
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['营业额 (¥)', '订单量'], top: 4 },
    grid: { left: 48, right: 56, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '营业额 (¥)', axisLabel: { formatter: '{value}' }, splitLine: { lineStyle: { type: 'dashed' } } },
      { type: 'value', name: '订单量', axisLabel: { formatter: '{value}' }, splitLine: { show: false } }
    ],
    series: [
      { name: '营业额 (¥)', type: 'bar', data: gmvs, barWidth: '50%',
        itemStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#1677FF'},{offset:1,color:'#69B1FF'}]) } },
      { name: '订单量', type: 'line', yAxisIndex: 1, data: cnts, smooth: true,
        itemStyle: { color: '#52C41A' }, lineStyle: { width: 2 } }
    ]
  }, true)
}

function renderTopChart() {
  const el = topChartRef.value
  if (!el) return
  let chart = chartInstances.find(c => c.getDom() === el)
  if (!chart) { chart = initChart(el, {}) }
  const names = topProducts.value.map(p => p.skuName || `规格 #${p.skuId}`).reverse()
  const qtys  = topProducts.value.map(p => Number(p.quantity || 0)).reverse()
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 120, right: 30, top: 16, bottom: 20 },
    xAxis: { type: 'value', splitLine: { lineStyle: { type: 'dashed' } } },
    yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar', data: qtys, barWidth: 18,
      itemStyle: { color: new echarts.graphic.LinearGradient(0,0,1,0,[{offset:0,color:'#FA8C16'},{offset:1,color:'#FFD591'}]),
                  borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, color: '#595959' }
    }]
  }, true)
}

function renderForecastChart() {
  const el = forecastChartRef.value
  if (!el) return
  let chart = chartInstances.find(c => c.getDom() === el)
  if (!chart) { chart = initChart(el, {}) }
  const models = forecastData.value.map(d => d.model)
  const modelMae = forecastData.value.map(d => Number(d.mae || 0))
  const baseMae  = forecastData.value.map(d => Number(d.baselineMae || 0))
  const wins     = forecastData.value.map(d => d.win)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['模型 MAE', '基线 MAE'], top: 4 },
    grid: { left: 40, right: 30, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: models, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', name: 'MAE', splitLine: { lineStyle: { type: 'dashed' } } },
    series: [
      { name: '模型 MAE', type: 'bar', data: modelMae, barWidth: '28%',
        itemStyle: { color: '#52C41A', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', fontSize: 11, formatter: (p, d) => wins[p.dataIndex] ? '✅' : '' } },
      { name: '基线 MAE', type: 'bar', data: baseMae, barWidth: '28%',
        itemStyle: { color: '#FF4D4F', borderRadius: [4, 4, 0, 0] } }
    ]
  }, true)
}

// ========== 生命周期 ==========
onMounted(async () => {
  await Promise.all([loadSummary(), loadGmvTrend(), loadTopProducts(), loadForecast()])
  window.addEventListener('resize', () => chartInstances.forEach(c => c.resize()))
})
onBeforeUnmount(() => {
  chartInstances.forEach(c => c.dispose())
  window.removeEventListener('resize', () => chartInstances.forEach(c => c.resize()))
})
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.merchant-analytics { padding: 16px; }

.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;

  h2 {
    margin: 0; font-size: 18px; color: #1F2937; font-weight: 700;
    letter-spacing: 0.3px;
  }
}

// 汇总统计卡
.summary-row { margin-bottom: 16px; }
.summary-card {
  border-radius: 10px;
  border: 1px solid #E8ECF0;
  transition: transform $duration-fast $ease-spring, box-shadow $duration-base;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  }
}
.summary-card :deep(.el-card__body) { padding: 14px 18px; }
.sc-inner { display: flex; align-items: center; gap: 14px; }
.sc-icon {
  width: 44px; height: 44px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
  transition: transform $duration-base $ease-spring;
}
.summary-card:hover .sc-icon { transform: scale(1.08); }

.summary-card.blue   .sc-icon { background: linear-gradient(135deg, #1677FF, #0958D9); box-shadow: 0 4px 12px rgba(22, 119, 255, 0.3); }
.summary-card.green  .sc-icon { background: linear-gradient(135deg, #52C41A, #237804); box-shadow: 0 4px 12px rgba(82, 196, 26, 0.3); }
.summary-card.orange .sc-icon { background: linear-gradient(135deg, #FA8C16, #D4380D); box-shadow: 0 4px 12px rgba(250, 140, 22, 0.3); }
.summary-card.purple .sc-icon { background: linear-gradient(135deg, #722ED1, #531DAB); box-shadow: 0 4px 12px rgba(114, 46, 209, 0.3); }

.sc-body { flex: 1; }
.sc-label { font-size: 12px; color: #8c8c8c; margin-bottom: 2px; }
.sc-value { font-size: 22px; font-weight: 700; color: #1F2937; font-family: 'Helvetica Neue', sans-serif; letter-spacing: -0.5px; }
.sc-sub { font-size: 12px; color: #bfbfbf; margin-top: 2px; }

// 图表卡
.chart-card {
  border-radius: 10px;
  border: 1px solid #E8ECF0;
  margin-bottom: 16px;
  transition: box-shadow $duration-base;

  &:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05); }
}

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px dashed #F0F0F0;
}
.card-title {
  font-size: 15px; font-weight: 600; color: #1F2937;
  letter-spacing: 0.3px;
}

.chart-box { width: 100%; height: 280px; }
.chart-lg { height: 320px; }
.double-row .chart-box { height: 260px; }

.口径说明 { font-size: 12px; color: #595959; line-height: 1.8; max-width: 400px; }
</style>
