<template>
  <div class="stock-page">
    <el-tabs v-model="activeTab" class="stock-tabs">
      <!-- ==================== 库存总览 ==================== -->
      <el-tab-pane label="库存总览" name="overview">
        <!-- 全局社区选择器：el-select 动态渲染 ACTIVE 社区列表，新社区自动出现 -->
        <div class="community-switch">
          <div class="community-switch__label">📍 统计范围</div>
          <el-select
            v-model="globalCommunityId"
            clearable
            placeholder="全部社区"
            style="width: 260px"
            filterable
            @clear="globalCommunityId = null"
            @change="onGlobalCommunityChange"
          >
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="c.communityName || c.name"
              :value="c.id"
            />
          </el-select>
          <el-tag type="info" effect="plain" round size="small" class="community-current">
            {{ globalCommunityId ? currentCommunityLabel : '全部社区汇总' }}
          </el-tag>
          <el-button
            v-if="globalCommunityId !== null"
            size="small"
            text
            type="primary"
            @click="globalCommunityId = null"
          >重置</el-button>
        </div>

        <!-- 统计卡：数据全部来自后端统计接口（约束文档 11，前端不二次计算） -->
        <div class="stats-row">
          <div class="stat-card total">
            <div class="stat-num">{{ stats.skuCount }}</div>
            <div class="stat-label">全部商品库存</div>
          </div>
          <div class="stat-card in">
            <div class="stat-num">{{ stats.totalQty }}</div>
            <div class="stat-label">实际在仓</div>
          </div>
          <div class="stat-card locked">
            <div class="stat-num">{{ stats.lockedQty }}</div>
            <div class="stat-label">已被下单锁定</div>
          </div>
          <div class="stat-card avail">
            <div class="stat-num">{{ stats.availableQty }}</div>
            <div class="stat-label">可出售</div>
          </div>
          <div class="stat-card warn">
            <div class="stat-num">{{ stats.warnCount }}</div>
            <div class="stat-label">库存不足</div>
          </div>
          <div class="stat-card empty">
            <div class="stat-num">{{ stats.emptyCount }}</div>
            <div class="stat-label">已断货</div>
          </div>
        </div>

        <!-- 近 N 天出入库趋势 -->
        <el-card shadow="never" class="trend-card">
          <template #header>
            <div class="card-head">
              <span>近 {{ overview.days || 30 }} 天出入库趋势</span>
              <span class="caliber">统计口径：{{ overview.caliber || '后端统计接口' }}</span>
            </div>
          </template>
          <div ref="chartRef" class="trend-chart"></div>
          <el-empty v-if="!trend.length" description="暂无流水数据" :image-size="60" />
        </el-card>

        <!-- 库存明细 -->
        <div class="filter-bar">
          <el-radio-group v-model="stockQuery.tab" size="default" @change="onStockFilterChange">
            <el-radio-button :label="STOCK_FILTER_TAB.ALL">全部 ({{ stats.skuCount }})</el-radio-button>
            <el-radio-button :label="STOCK_FILTER_TAB.WARN">库存不足 ({{ stats.warnCount }})</el-radio-button>
            <el-radio-button :label="STOCK_FILTER_TAB.EMPTY">已断货 ({{ stats.emptyCount }})</el-radio-button>
            <el-radio-button :label="STOCK_FILTER_TAB.LOCKED">锁定中 ({{ stats.lockedCount }})</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="stockQuery.keyword"
            placeholder="搜索商品名 / 规格 / 编码"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="onStockFilterChange"
            @clear="onStockFilterChange"
          />
          <el-radio-group v-model="viewMode" size="default" style="margin-left: auto" @change="onStockFilterChange">
            <el-radio-button label="grouped">按商品归组</el-radio-button>
            <el-radio-button label="sku">按规格显示</el-radio-button>
          </el-radio-group>
        </div>

        <!-- ============ 归组模式：Product×社区 ============ -->
        <el-table v-if="viewMode === 'grouped'" :data="stocks" stripe style="margin-top: 16px" v-loading="stockLoading" empty-text="暂无库存">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-if="row.sku_list && row.sku_list.length" style="padding: 0 40px 16px 40px">
                <div class="expand-title">商品规格详情（{{ row.sku_list.length }} 个）</div>
                <el-table :data="row.sku_list" size="small" border stripe>
                  <el-table-column label="社区" width="120" align="center">
                    <template #default="{ row: sku }">
                      <el-tag size="small" type="info" effect="plain">{{ sku.community_name }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="规格名称" min-width="180">
                    <template #default="{ row: sku }">
                      <div>{{ sku.sku_name }}</div>
                      <div class="item-sub muted">{{ sku.sku_code }} · ¥{{ sku.price }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column label="实际在仓" prop="total_qty" width="70" align="center" />
                  <el-table-column label="已锁定" prop="locked_qty" width="70" align="center" />
                  <el-table-column label="可出售" width="110" align="center">
                    <template #default="{ row: sku }">
                      <el-progress
                        :percentage="sku.available_qty > 0 ? Math.min(100, sku.available_qty) : 0"
                        :stroke-width="6"
                        :format="() => sku.available_qty"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="安全库存" width="60" align="center">
                    <template #default="{ row: sku }">{{ sku.warn_qty == null ? '—' : sku.warn_qty }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="80" align="center">
                    <template #default="{ row: sku }">
                      <el-tag :type="getStockWarnType(sku.warn_status)" effect="plain" size="small">
                        {{ getStockWarnLabel(sku.warn_status) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="140" align="center">
                    <template #default="{ row: sku }">
                      <el-button size="small" type="primary" link @click="openAdjustFromSku(sku)">调整</el-button>
                      <el-button size="small" link @click="drillBySku(sku)">流水</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="商品" min-width="240">
            <template #default="{ row }">
              <div class="item-cell">
                <el-image :src="row.main_image" fit="cover" class="item-img" />
                <div class="item-info">
                  <div class="item-name">
                    {{ row.product_name }}
                    <el-tag size="small" type="info" effect="plain" round style="margin-left: 6px">共 {{ row.sku_count }} 种规格</el-tag>
                  </div>
                  <div class="item-sub muted">跨 {{ row.community_count }} 社区 · {{ row.category_name || '未分类' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="实际在仓" prop="total_qty" width="80" align="center" />
          <el-table-column label="已锁定" prop="locked_qty" width="80" align="center" />
          <el-table-column label="可出售" width="130" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="availablePercent(row)"
                :color="progressColor(row)"
                :stroke-width="8"
                :format="() => row.available_qty"
              />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStockWarnType(row.warn_status)" effect="dark" round>
                {{ getStockWarnLabel(row.warn_status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="drillByProduct(row)">流水</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- ============ SKU 模式：SKU×社区（原有） ============ -->
        <el-table v-else :data="stocks" stripe style="margin-top: 16px" v-loading="stockLoading" empty-text="暂无库存">
          <el-table-column label="商品" min-width="220">
            <template #default="{ row }">
              <div class="item-cell">
                <el-image :src="row.sku_image || row.main_image" fit="cover" class="item-img" />
                <div class="item-info">
                  <div class="item-name">{{ row.product_name }}</div>
                  <div class="item-sub">{{ row.sku_name }} · ¥{{ row.price }}</div>
                  <div class="item-sub muted">{{ row.community_name }} · {{ row.sku_code }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="实际在仓" prop="total_qty" width="80" align="center" />
          <el-table-column label="已锁定" prop="locked_qty" width="80" align="center" />
          <el-table-column label="可出售" width="130" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="availablePercent(row)"
                :color="progressColor(row)"
                :stroke-width="8"
                :format="() => row.available_qty"
              />
            </template>
          </el-table-column>
          <el-table-column label="安全库存" width="90" align="center">
            <template #default="{ row }">{{ row.warn_qty == null ? '—' : row.warn_qty }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStockWarnType(row.warn_status)" effect="dark" round>
                {{ getStockWarnLabel(row.warn_status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openAdjust(row)">调整</el-button>
              <el-button size="small" @click="drillByStock(row)">流水</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pager"
          background
          layout="total, sizes, prev, pager, next"
          :total="stockTotal"
          :current-page="stockQuery.pageNum"
          :page-size="stockQuery.pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="onStockPageChange"
          @size-change="onStockSizeChange"
        />
      </el-tab-pane>

      <!-- ==================== 库存流水 ==================== -->
      <el-tab-pane label="库存流水" name="records">
        <div class="community-switch community-switch--inline">
          <div class="community-switch__label">📍 社区</div>
          <el-select
            v-model="globalCommunityId"
            clearable
            placeholder="全部社区"
            style="width: 220px"
            filterable
          >
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="c.communityName || c.name"
              :value="c.id"
            />
          </el-select>
        </div>
        <div class="record-filter">
          <el-select v-model="recordQuery.changeType" placeholder="全部变更类型" clearable style="width: 160px">
            <el-option
              v-for="(item, key) in STOCK_CHANGE_TYPE_LABEL"
              :key="key"
              :label="item.text"
              :value="key"
            />
          </el-select>
          <el-date-picker
            v-model="recordDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
          <el-input
            v-model="recordQuery.orderNo"
            placeholder="关联单号"
            clearable
            style="width: 200px"
            @keyup.enter="loadRecords(1)"
          />
          <el-button type="primary" :icon="Search" @click="loadRecords(1)">查询</el-button>
          <el-button @click="resetRecordQuery">重置</el-button>
        </div>

        <el-alert
          v-if="drillText"
          class="drill-tip"
          type="info"
          :closable="false"
          show-icon
        >
          <span>已下钻：{{ drillText }}</span>
          <el-button link type="primary" @click="clearDrill">清除下钻</el-button>
        </el-alert>

        <el-table :data="records" stripe v-loading="recordLoading" empty-text="暂无流水">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatTime(row.create_time) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStockChangeType(row.change_type)" round>{{ getStockChangeLabel(row.change_type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更数量" width="100" align="center">
            <template #default="{ row }">
              <span :class="row.change_qty > 0 ? 'qty-in' : 'qty-out'">
                {{ row.change_qty > 0 ? '+' : '' }}{{ row.change_qty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="在仓前→后" width="120" align="center">
            <template #default="{ row }">{{ qtyRange(row.before_total, row.after_total) }}</template>
          </el-table-column>
          <el-table-column label="锁定前→后" width="120" align="center">
            <template #default="{ row }">{{ qtyRange(row.before_locked, row.after_locked) }}</template>
          </el-table-column>
          <el-table-column label="商品 / 规格" min-width="200">
            <template #default="{ row }">
              <div class="item-name">{{ row.product_name }}</div>
              <div class="item-sub muted">{{ row.sku_name }} · {{ row.sku_code }}</div>
            </template>
          </el-table-column>
          <el-table-column label="社区" prop="community_name" width="110" />
          <el-table-column label="关联单号" width="170">
            <template #default="{ row }">
              <el-button v-if="row.order_no" link type="primary" @click="drillByOrder(row.order_no)">
                {{ row.order_no }}
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
        </el-table>

        <el-pagination
          class="pager"
          background
          layout="total, sizes, prev, pager, next"
          :total="recordTotal"
          :current-page="recordQuery.pageNum"
          :page-size="recordQuery.pageSize"
          :page-sizes="[10, 20, 50]"
          @current-change="onRecordPageChange"
          @size-change="onRecordSizeChange"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 调整 Dialog -->
    <el-dialog v-model="adjustVisible" title="调整库存" width="420px">
      <div v-if="adjustRow" class="adjust-info">
        <div><b>商品：</b>{{ adjustRow.product_name }}</div>
        <div><b>规格：</b>{{ adjustRow.sku_name }}</div>
        <div>
          <b>调整前：</b>实际在仓 {{ adjustRow.total_qty }} / 可出售 {{ adjustRow.available_qty }} / 已锁定 {{ adjustRow.locked_qty }}
        </div>
      </div>
      <el-form :model="adjustForm" label-width="80px" style="margin-top: 16px">
        <el-form-item label="调整数量" required>
          <el-input-number v-model="adjustForm.qty" :min="-9999" :max="9999" style="width: 100%" />
          <div class="form-tip">正数=增加（盘点补入/供应到货），负数=减少（报损/过期）</div>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input
            v-model="adjustForm.remark"
            type="textarea"
            :rows="2"
            placeholder="必填，如「盘点补入 50 件」「临期报损 10 件」"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { getMyStocks, getMyStocksGrouped, getStockOverview, getStockRecords, adjustStock, getCommunitiesActive } from '@/api';
import {
  STOCK_WARN_STATUS,
  STOCK_FILTER_TAB,
  STOCK_CHANGE_TYPE_LABEL,
  getStockWarnLabel,
  getStockWarnType,
  getStockChangeLabel,
  getStockChangeType
} from '@/config/business';

const activeTab = ref('overview');

// ---------- 全局社区过滤器（驱动 overview / stocks / records 全部数据源）----------
// 默认 null = 全部社区；切换时三处同步刷新
const globalCommunityId = ref(null);
const currentCommunityLabel = computed(() => {
  if (!globalCommunityId.value) return '全部社区';
  const c = communities.value.find(x => x.id === globalCommunityId.value);
  return c ? (c.communityName || c.name || `社区 #${c.id}`) : '全部社区';
});

// ---------- 总览 ----------
const overview = reactive({ stats: {}, trend: [], days: 30, caliber: '' });
const stats = computed(() => overview.stats || {});
const trend = computed(() => overview.trend || []);
const chartRef = ref(null);
let chart = null;

// ---------- 库存明细 ----------
const stocks = ref([]);
const stockLoading = ref(false);
const stockTotal = ref(0);
const stockQuery = reactive({ tab: STOCK_FILTER_TAB.ALL, keyword: '', communityId: null, pageNum: 1, pageSize: 10 });
const communities = ref([]);
// 视图模式：grouped（按 Product×社区 归组显示，默认）/ sku（SKU×社区 原始粒度）
// 方案 A：展示层切换，存储层始终是 SKU 粒度（约束文档 §4.4）
const viewMode = ref('grouped');

// ---------- 流水 ----------
const records = ref([]);
const recordLoading = ref(false);
const recordTotal = ref(0);
const recordDateRange = ref(null);
const recordQuery = reactive({ changeType: '', orderNo: '', stockId: null, communityId: null, pageNum: 1, pageSize: 10 });

const drillText = computed(() => {
  const parts = [];
  if (recordQuery.stockId) parts.push(`库存记录 #${recordQuery.stockId}`);
  if (recordQuery.orderNo) parts.push(`单号 ${recordQuery.orderNo}`);
  return parts.join(' · ');
});

// ---------- 调整 ----------
const adjustVisible = ref(false);
const adjustRow = ref(null);
const adjustForm = reactive({ qty: 0, remark: '' });
const submitting = ref(false);

// ==================== 数据加载 ====================
async function loadOverview() {
  try {
    const res = await getStockOverview({ days: 30, communityId: globalCommunityId.value });
    overview.stats = res?.stats || {};
    overview.trend = res?.trend || [];
    overview.days = res?.days || 30;
    overview.caliber = res?.caliber || '';
    await nextTick();
    renderChart();
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理（约束文档 9）
    overview.stats = {};
    overview.trend = [];
  }
}

async function loadCommunities() {
  try {
    const res = await getCommunitiesActive();
    communities.value = res || [];
  } catch (e) {
    communities.value = [];
  }
}

async function loadStocks() {
  stockLoading.value = true;
  try {
    const params = buildStockParams();
    // 方案 A+B：根据视图模式选择 API
    // grouped → /mine/grouped（Product×社区 聚合展示，后端 GROUP BY）
    // sku    → /mine（SKU×社区 原始粒度）
    const api = viewMode.value === 'grouped' ? getMyStocksGrouped : getMyStocks;
    const res = await api(params);
    stocks.value = (res?.records || []).map(r => {
      // grouped 模式：后端返回 JSON 字符串，前端 parse
      if (viewMode.value === 'grouped' && typeof r.sku_list === 'string') {
        try { r.sku_list = JSON.parse(r.sku_list); } catch { r.sku_list = []; }
      }
      return r;
    });
    stockTotal.value = res?.total || 0;
  } catch (e) {
    stocks.value = [];
    stockTotal.value = 0;
  } finally {
    stockLoading.value = false;
  }
}

/**
 * 组装库存列表查询参数
 * 筛选维度与总览统计卡口径一一对应（约束文档 11）：
 *   预警 → warnStatus=WARN；空仓 → warnStatus=EMPTY；锁定中 → lockedOnly=true
 */
function buildStockParams() {
  const params = buildParams({
    keyword: stockQuery.keyword,
    communityId: stockQuery.communityId,
    pageNum: stockQuery.pageNum,
    pageSize: stockQuery.pageSize
  });
  if (stockQuery.tab === STOCK_FILTER_TAB.WARN) {
    params.warnStatus = STOCK_WARN_STATUS.WARN;
  } else if (stockQuery.tab === STOCK_FILTER_TAB.EMPTY) {
    params.warnStatus = STOCK_WARN_STATUS.EMPTY;
  } else if (stockQuery.tab === STOCK_FILTER_TAB.LOCKED) {
    params.lockedOnly = true;
  }
  return params;
}

async function loadRecords(page) {
  if (page) recordQuery.pageNum = page;
  recordLoading.value = true;
  try {
    const params = buildParams(recordQuery);
    if (recordDateRange.value?.length === 2) {
      params.startDate = recordDateRange.value[0];
      params.endDate = recordDateRange.value[1];
    }
    const res = await getStockRecords(params);
    records.value = res?.records || [];
    recordTotal.value = res?.total || 0;
  } catch (e) {
    records.value = [];
    recordTotal.value = 0;
  } finally {
    recordLoading.value = false;
  }
}

/** 过滤空值参数，避免后端把空串当筛选条件 */
function buildParams(source) {
  const params = {};
  Object.keys(source).forEach((k) => {
    const v = source[k];
    if (v !== null && v !== undefined && v !== '') params[k] = v;
  });
  return params;
}

// ==================== 趋势图 ====================
function renderChart() {
  if (!chartRef.value || !trend.value.length) return;
  if (!chart) chart = echarts.init(chartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入库', '出库'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trend.value.map((p) => String(p.date).slice(5)),
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '入库',
        type: 'line',
        smooth: true,
        data: trend.value.map((p) => p.inQty),
        itemStyle: { color: '#67C23A' },
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '出库',
        type: 'line',
        smooth: true,
        data: trend.value.map((p) => p.outQty),
        itemStyle: { color: '#F56C6C' },
        areaStyle: { opacity: 0.12 }
      }
    ]
  });
  chart.resize();
}

function resizeChart() {
  if (chart) chart.resize();
}

// ==================== 交互 ====================
function onStockFilterChange() {
  stockQuery.pageNum = 1;
  loadStocks();
}

function onStockPageChange(page) {
  stockQuery.pageNum = page;
  loadStocks();
}

function onStockSizeChange(size) {
  stockQuery.pageSize = size;
  stockQuery.pageNum = 1;
  loadStocks();
}

function onRecordPageChange(page) {
  recordQuery.pageNum = page;
  loadRecords();
}

function onRecordSizeChange(size) {
  recordQuery.pageSize = size;
  loadRecords(1);
}

function resetRecordQuery() {
  recordQuery.changeType = '';
  recordQuery.orderNo = '';
  recordQuery.stockId = null;
  recordDateRange.value = null;
  loadRecords(1);
}

/** 单号下钻：只看该订单号关联的库存流水 */
function drillByOrder(orderNo) {
  recordQuery.orderNo = orderNo;
  recordQuery.stockId = null;
  activeTab.value = 'records';
  loadRecords(1);
}

/** 从库存明细下钻到该 SKU 的流水 */
function drillByStock(row) {
  recordQuery.stockId = row.id;
  recordQuery.orderNo = '';
  activeTab.value = 'records';
  loadRecords(1);
}

function clearDrill() {
  recordQuery.stockId = null;
  recordQuery.orderNo = '';
  loadRecords(1);
}

// ==================== 展示辅助 ====================
function availablePercent(row) {
  const total = Math.max(1, row.total_qty || 0);
  return Math.min(100, Math.round(((row.available_qty || 0) / total) * 100));
}

function progressColor(row) {
  if (row.warn_status === STOCK_WARN_STATUS.WARN) return '#F56C6C';
  if (row.warn_status === STOCK_WARN_STATUS.EMPTY) return '#909399';
  return '#67C23A';
}

function qtyRange(before, after) {
  if (before == null && after == null) return '—';
  return `${before ?? '—'} → ${after ?? '—'}`;
}

function formatTime(t) {
  if (!t) return '—';
  return String(t).replace('T', ' ').slice(0, 16);
}

// ==================== 库存调整 ====================
function openAdjust(row) {
  adjustRow.value = row;
  adjustForm.qty = 0;
  adjustForm.remark = '';
  adjustVisible.value = true;
}

async function submitAdjust() {
  if (!adjustForm.qty || adjustForm.qty === 0) {
    ElMessage.warning('调整数量不能为 0');
    return;
  }
  if (!adjustForm.remark?.trim()) {
    ElMessage.warning('调整原因必填');
    return;
  }
  submitting.value = true;
  try {
    await adjustStock(adjustRow.value.id, { qty: adjustForm.qty, remark: adjustForm.remark });
    ElMessage.success('调整成功');
    adjustVisible.value = false;
    // 库存变更后：明细 / 统计 / 趋势 / 流水全部刷新（约束文档 6 双写一致）
    await Promise.all([loadStocks(), loadOverview()]);
    if (activeTab.value === 'records') await loadRecords(1);
  } catch (e) {
    // 失败原因由拦截器 toast
  } finally {
    submitting.value = false;
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  window.addEventListener('resize', resizeChart);
  loadCommunities();
  loadOverview();
  loadStocks();
  loadRecords(1);
});

// 全局社区切换 → 三处同步刷新（overview + stocks + records）
watch(globalCommunityId, (val) => {
  stockQuery.communityId = val;
  stockQuery.pageNum = 1;
  recordQuery.communityId = val;
  recordQuery.pageNum = 1;
  loadOverview();
  loadStocks();
  loadRecords(1);
});

watch(activeTab, async (val) => {
  if (val === 'overview') {
    await nextTick();
    renderChart();
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart);
  if (chart) {
    chart.dispose();
    chart = null;
  }
});
</script>

<style scoped>
.stock-page { padding: 0; }
.stock-tabs { background: #fff; padding: 8px 16px 16px; border-radius: 8px; }

/* ============ 全局社区选择器 ============ */
.community-switch {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: linear-gradient(90deg, #f0f7ff 0%, #f7f9fc 100%);
  border-radius: 10px;
  margin-bottom: 16px;
  border: 1px solid #e4e7ed;
}
.community-switch--inline {
  background: transparent;
  border: none;
  padding: 8px 0;
  margin-bottom: 8px;
}
.community-switch__label {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  flex-shrink: 0;
}
.community-current {
  font-size: 12px;
  margin-left: auto;
}

/* ============ 统计卡 ============ */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 16px 10px;
  border-radius: 10px;
  color: #fff;
  text-align: center;
  transition: transform .15s, box-shadow .15s;
  position: relative;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute;
  top: -20px;
  right: -20px;
  width: 60px;
  height: 60px;
  background: rgba(255,255,255,.12);
  border-radius: 50%;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, .12);
}
.stat-card.total { background: linear-gradient(135deg, #409eff, #66b1ff); }
.stat-card.in    { background: linear-gradient(135deg, #67c23a, #85ce61); }
.stat-card.locked { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.stat-card.avail { background: linear-gradient(135deg, #909399, #a6a9ad); }
.stat-card.warn  { background: linear-gradient(135deg, #f56c6c, #f78989); }
.stat-card.empty { background: linear-gradient(135deg, #606266, #73767a); }
.stat-num { font-size: 26px; font-weight: 700; letter-spacing: .5px; position: relative; z-index: 1; }
.stat-label { font-size: 12px; opacity: 0.95; margin-top: 4px; position: relative; z-index: 1; }

.trend-card { margin-bottom: 16px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.caliber { font-size: 12px; color: #909399; }
.trend-chart { width: 100%; height: 280px; }

.filter-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.record-filter { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.drill-tip { margin-bottom: 12px; }

.pager { margin-top: 16px; display: flex; justify-content: flex-end; }

.item-cell { display: flex; gap: 10px; align-items: center; }
.item-img { width: 48px; height: 48px; border-radius: 6px; background: #f5f7fa; flex-shrink: 0; }
.item-info { display: flex; flex-direction: column; }
.item-name { font-weight: 600; font-size: 14px; }
.item-sub { font-size: 12px; color: #606266; }
.muted { color: #909399; }

.qty-in { color: #67c23a; font-weight: bold; }
.qty-out { color: #f56c6c; font-weight: bold; }

.adjust-info { background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.8; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }

.expand-title { font-size: 13px; color: #606266; font-weight: 600; margin: 4px 0 10px 0; padding-left: 4px; border-left: 3px solid #409eff; }
</style>
