<template>
  <div class="logs-page">

    <!-- 顶部统计卡（最近 N 天） -->
    <div class="stat-cards">
      <div class="stat-card stat-card--primary">
        <div class="stat-card__icon">📋</div>
        <div class="stat-card__body">
          <div class="stat-card__label">近 {{ stats.periodDays || 7 }} 天操作</div>
          <div class="stat-card__value">{{ stats.total }}</div>
        </div>
      </div>
      <div class="stat-card stat-card--success">
        <div class="stat-card__icon">🔄</div>
        <div class="stat-card__body">
          <div class="stat-card__label">状态变更</div>
          <div class="stat-card__value">{{ stats.transitionCount }}</div>
        </div>
      </div>
      <div class="stat-card stat-card--warning">
        <div class="stat-card__icon">👥</div>
        <div class="stat-card__body">
          <div class="stat-card__label">活跃用户</div>
          <div class="stat-card__value">{{ stats.uniqueUsers }}</div>
        </div>
      </div>
      <div class="stat-card stat-card--danger">
        <div class="stat-card__icon">⚠️</div>
        <div class="stat-card__body">
          <div class="stat-card__label">操作失败</div>
          <div class="stat-card__value">{{ stats.failedCount }}</div>
        </div>
      </div>
    </div>

    <!-- 筛选条 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="onSearch">
        <el-form-item label="模块">
          <el-select v-model="filters.module" clearable placeholder="全部模块" style="width: 140px">
            <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作">
          <el-select v-model="filters.operationType" clearable placeholder="全部操作" style="width: 150px">
            <el-option v-for="o in operationTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filters.result" clearable placeholder="全部结果" style="width: 110px">
            <el-option label="✅ 成功" value="SUCCESS" />
            <el-option label="❌ 失败" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="参数 / 错误信息 / traceId" style="width: 220px" clearable />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 时间线卡片列表 -->
    <el-card class="timeline-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="timeline-header">
          <span class="timeline-header__title">📝 操作日志</span>
          <span class="timeline-header__total">共 {{ total }} 条</span>
        </div>
      </template>

      <div class="timeline">
        <div
          v-for="(item, idx) in records"
          :key="item.id"
          class="timeline-item"
          :class="{ 'timeline-item--fail': item.resultClass === 'danger' }"
        >
          <div class="timeline-item__dot" :class="'dot--' + (item.resultClass || 'info')" />
          <div class="timeline-item__content">
            <div class="timeline-item__head">
              <el-tag
                :type="opTagType(item.actionLabel)"
                size="small"
                effect="light"
                class="op-tag"
              >{{ item.actionLabel }}</el-tag>

              <span class="timeline-item__summary">{{ item.summary }}</span>

              <span class="timeline-item__time" :title="formatTime(item.createTime)">
                {{ toRelative(item.createTime) }}
              </span>
            </div>

            <div class="timeline-item__meta">
              <span class="meta-item">
                <el-icon><User /></el-icon>
                {{ item.operatorName }}
                <span v-if="item.operatorRole && item.operatorRole !== '—'" class="meta-role">（{{ item.operatorRole }}）</span>
              </span>

              <span v-if="item.module" class="meta-item">
                <el-icon><Box /></el-icon>{{ item.module }}
              </span>

              <span v-if="item.beforeLabel || item.afterLabel" class="meta-item meta-item--transition">
                <el-icon><SwitchButton /></el-icon>
                {{ item.beforeLabel || '—' }} → {{ item.afterLabel || '—' }}
              </span>

              <span class="meta-item" :class="'result--' + (item.resultClass || 'info')">
                {{ item.resultLabel }}
              </span>

              <el-button
                link
                type="primary"
                size="small"
                @click="toggleExpand(item.id)"
              >{{ expanded.has(item.id) ? '收起' : '详情' }}</el-button>
            </div>

            <!-- 可展开详情区 -->
            <div v-if="expanded.has(item.id)" class="timeline-item__detail">
              <div class="detail-grid">
                <div class="detail-grid__item" v-if="item.detailHint">
                  <div class="detail-grid__label">业务提示</div>
                  <div class="detail-grid__value">{{ item.detailHint }}</div>
                </div>
                <div class="detail-grid__item" v-if="item.ip">
                  <div class="detail-grid__label">IP 地址</div>
                  <div class="detail-grid__value">{{ item.ip }}</div>
                </div>
                <div class="detail-grid__item" v-if="item.traceId">
                  <div class="detail-grid__label">Trace ID</div>
                  <div class="detail-grid__value mono">{{ item.traceId }}</div>
                </div>
                <div class="detail-grid__item">
                  <div class="detail-grid__label">精确时间</div>
                  <div class="detail-grid__value">{{ formatTime(item.createTime) }}</div>
                </div>
              </div>

              <div v-if="item.rawParams" class="detail-section">
                <div class="detail-section__title">请求参数</div>
                <pre class="json-block">{{ tryFormatJson(item.rawParams) }}</pre>
              </div>

              <div v-if="item.errorMsg" class="detail-section">
                <div class="detail-section__title detail-section__title--err">错误信息</div>
                <el-alert type="error" :closable="false" show-icon>{{ item.errorMsg }}</el-alert>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && records.length === 0" description="暂无操作日志" />
      </div>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, User, Box, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import { adminLogList, adminLogDetail, adminLogModules, adminLogStatsToday } from '@/api'

// ---- dayjs ----
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

// ---- 状态 ----
const loading = ref(false)
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const moduleOptions = ref([])
const expanded = reactive(new Set())

const stats = reactive({
  periodDays: 7,
  total: 0,
  failedCount: 0,
  uniqueUsers: 0,
  auditCount: 0,
  transitionCount: 0
})

const filters = reactive({
  module: '',
  operationType: '',
  result: '',
  keyword: '',
  timeRange: []
})

// 操作类型下拉（中文 + emoji）
const operationTypeOptions = [
  { value: 'CREATE',     label: '➕ 创建' },
  { value: 'UPDATE',     label: '✏️ 更新' },
  { value: 'DELETE',     label: '🗑️ 删除' },
  { value: 'TRANSITION', label: '🔄 状态变更' },
  { value: 'AUDIT',      label: '📝 审核' },
  { value: 'CALLBACK',   label: '📡 回调' },
  { value: 'REFUND',     label: '💰 退款' },
  { value: 'ASSIGN',     label: '📋 分配' },
  { value: 'LOGIN',      label: '🔐 登录' },
  { value: 'LOGOUT',     label: '🚪 登出' },
  { value: 'RESET',      label: '🔁 重置' }
]

// ---- 工具 ----
function toRelative(t) {
  if (!t) return ''
  return dayjs(t).fromNow()
}
function formatTime(t) {
  if (!t) return ''
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}
function tryFormatJson(p) {
  if (!p) return ''
  try { return JSON.stringify(JSON.parse(p), null, 2) } catch { return p }
}

// 根据 actionLabel 推断 tag type（通过 emoji 前缀匹配）
function opTagType(actionLabel) {
  if (!actionLabel) return 'info'
  if (actionLabel.includes('删除') || actionLabel.includes('重置') || actionLabel.includes('退款')) return 'danger'
  if (actionLabel.includes('审核') || actionLabel.includes('登录')) return 'success'
  if (actionLabel.includes('更新') || actionLabel.includes('分配')) return 'warning'
  if (actionLabel.includes('回调')) return 'info'
  return 'primary'
}

function toggleExpand(id) {
  if (expanded.has(id)) {
    expanded.delete(id)
  } else {
    expanded.add(id)
    // 懒加载详情（如果 rawParams 为空的话）
    const item = records.value.find(r => r.id === id)
    if (item && !item.rawParams) {
      adminLogDetail(id).then(r => {
        Object.assign(item, r)
      }).catch(() => {})
    }
  }
}

// ---- 数据加载 ----
async function load() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      module: filters.module || undefined,
      operationType: filters.operationType || undefined,
      result: filters.result || undefined,
      keyword: filters.keyword || undefined,
      startTime: filters.timeRange?.[0] || undefined,
      endTime: filters.timeRange?.[1] || undefined
    }
    const r = await adminLogList(params)
    records.value = r.records || []
    total.value = r.total || 0
  } catch (e) {
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const r = await adminLogStatsToday()
    if (r) {
      Object.assign(stats, r)
    }
  } catch {}
}

async function loadModules() {
  try {
    const r = await adminLogModules()
    moduleOptions.value = r || []
  } catch {}
}

function onSearch() { pageNum.value = 1; load() }
function onReset() {
  filters.module = ''
  filters.operationType = ''
  filters.result = ''
  filters.keyword = ''
  filters.timeRange = []
  pageNum.value = 1
  load()
}

onMounted(() => {
  load()
  loadStats()
  loadModules()
  // 每分钟刷新一次统计卡（不强制刷新列表）
  setInterval(loadStats, 60_000)
})
</script>

<style scoped>
.logs-page { padding: 16px; display: flex; flex-direction: column; gap: 12px; }

/* 统计卡 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 10px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.15s ease;
}
.stat-card:hover { transform: translateY(-2px); }
.stat-card__icon {
  font-size: 36px;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 12px;
}
.stat-card__body { flex: 1; }
.stat-card__label { font-size: 13px; opacity: 0.85; margin-bottom: 4px; }
.stat-card__value { font-size: 28px; font-weight: 700; line-height: 1.1; }
.stat-card--primary  { background: linear-gradient(135deg, #409eff, #66b1ff); }
.stat-card--success  { background: linear-gradient(135deg, #67c23a, #85ce61); }
.stat-card--warning  { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.stat-card--danger   { background: linear-gradient(135deg, #f56c6c, #f78989); }

/* 筛选条 */
.filter-card :deep(.el-card__body) { padding: 12px 16px 4px; }

/* 时间线 */
.timeline-card :deep(.el-card__body) { padding: 16px 20px; }
.timeline-header {
  display: flex; justify-content: space-between; align-items: center;
}
.timeline-header__title { font-size: 16px; font-weight: 600; }
.timeline-header__total { color: #909399; font-size: 13px; }

.timeline { display: flex; flex-direction: column; gap: 4px; }
.timeline-item {
  position: relative;
  padding: 14px 0 14px 24px;
  border-bottom: 1px dashed #ebeef5;
}
.timeline-item:last-child { border-bottom: none; }
.timeline-item__dot {
  position: absolute; left: 0; top: 20px;
  width: 10px; height: 10px; border-radius: 50%;
  border: 2px solid #fff; box-shadow: 0 0 0 2px currentColor;
}
.dot--success { color: #67c23a; background: #67c23a; }
.dot--danger  { color: #f56c6c; background: #f56c6c; }
.dot--warning { color: #e6a23c; background: #e6a23c; }
.dot--info,
.dot--default { color: #909399; background: #909399; }
.timeline-item--fail .timeline-item__dot { animation: pulse 2s ease-in-out infinite; }
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 2px currentColor, 0 0 0 0 currentColor; }
  50%      { box-shadow: 0 0 0 2px currentColor, 0 0 0 6px transparent; }
}

.timeline-item__head {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
}
.op-tag { flex-shrink: 0; }
.timeline-item__summary {
  flex: 1; min-width: 200px;
  font-size: 14.5px; color: #303133; font-weight: 500;
}
.timeline-item__time {
  font-size: 12px; color: #909399; flex-shrink: 0;
}

.timeline-item__meta {
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
  margin-top: 6px; font-size: 12.5px; color: #606266;
}
.meta-item {
  display: inline-flex; align-items: center; gap: 4px;
}
.meta-item .el-icon { font-size: 13px; }
.meta-role { color: #909399; }
.meta-item--transition { color: #8a2be2; font-weight: 500; }
.result--success { color: #67c23a; font-weight: 600; }
.result--danger  { color: #f56c6c; font-weight: 600; }
.result--info    { color: #909399; }

.timeline-item__detail {
  margin-top: 12px; padding: 14px 16px;
  background: #fafbfc; border-radius: 8px;
  border: 1px solid #ebeef5;
}
.detail-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px 24px; margin-bottom: 12px;
}
.detail-grid__label { font-size: 12px; color: #909399; margin-bottom: 2px; }
.detail-grid__value { font-size: 13px; color: #303133; word-break: break-all; }
.detail-grid__value.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }

.detail-section { margin-top: 12px; }
.detail-section__title {
  font-size: 12.5px; color: #606266; margin-bottom: 6px; font-weight: 600;
}
.detail-section__title--err { color: #f56c6c; }

.json-block {
  background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 6px;
  font-family: Consolas, Monaco, monospace; font-size: 12px;
  max-height: 280px; overflow: auto; white-space: pre-wrap; word-break: break-all;
  margin: 0;
}

/* 分页 */
.pagination-wrap {
  display: flex; justify-content: flex-end; padding-top: 16px;
}
</style>
