<template>
  <div class="aftersale-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="admin-tip"
      title="Admin 端默认只读监控；强制审核 / 强制退款为显式二次介入操作，会跳过商家审核并记录操作日志（operator_role=ADMIN）。"
    />

    <!-- 商家下钻：后端无商家下拉接口，按 merchantId 精确下钻（与 Admin 供应计划页一致） -->
    <div class="merchant-filter">
      <span class="filter-label">商家 ID</span>
      <el-input-number
        v-model="merchantId"
        :min="1"
        :controls="false"
        placeholder="全部商家"
        style="width: 160px"
        @change="onMerchantChange"
      />
      <el-button link type="primary" @click="onMerchantChange">应用</el-button>
      <span class="caliber">统计范围：{{ stats.scope || '全平台' }}</span>
    </div>

    <!-- 统计卡：数据全部来自后端统计接口（约束文档 11，前端不二次计算） -->
    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-num">{{ stats.total }}</div>
        <div class="stat-label">售后单总数</div>
      </div>
      <div class="stat-card applying">
        <div class="stat-num">{{ stats.applying }}</div>
        <div class="stat-label">待审核</div>
      </div>
      <div class="stat-card approved">
        <div class="stat-num">{{ stats.approved }}</div>
        <div class="stat-label">待退款</div>
      </div>
      <div class="stat-card rejected">
        <div class="stat-num">{{ stats.rejected }}</div>
        <div class="stat-label">已驳回</div>
      </div>
      <div class="stat-card refunding">
        <div class="stat-num">{{ stats.refunding }}</div>
        <div class="stat-label">退款中</div>
      </div>
      <div class="stat-card refunded">
        <div class="stat-num">{{ stats.refunded }}</div>
        <div class="stat-label">已退款</div>
      </div>
    </div>

    <div class="caliber-bar">
      <span>待处理 <b class="pending">{{ stats.pendingCount }}</b> 单</span>
      <span>已退款金额合计 <b class="amount">¥{{ stats.refundedAmount }}</b></span>
      <span class="caliber">统计口径：{{ stats.caliber || '后端统计接口' }}</span>
    </div>

    <!-- 筛选区 -->
    <div class="filter-bar">
      <el-radio-group v-model="query.tab" size="default" @change="onTabChange">
        <el-radio-button :label="AFTERSALE_FILTER_TAB.ALL">全部 ({{ stats.total }})</el-radio-button>
        <el-radio-button :label="AFTERSALE_FILTER_TAB.APPLYING">待审核 ({{ stats.applying }})</el-radio-button>
        <el-radio-button :label="AFTERSALE_FILTER_TAB.APPROVED">待退款 ({{ stats.approved }})</el-radio-button>
        <el-radio-button :label="AFTERSALE_FILTER_TAB.REJECTED">已驳回 ({{ stats.rejected }})</el-radio-button>
        <el-radio-button :label="AFTERSALE_FILTER_TAB.REFUNDING">退款中 ({{ stats.refunding }})</el-radio-button>
        <el-radio-button :label="AFTERSALE_FILTER_TAB.REFUNDED">已退款 ({{ stats.refunded }})</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="query.keyword"
        placeholder="搜索售后单号 / 订单号"
        clearable
        style="width: 240px"
        :prefix-icon="Search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
    </div>

    <div class="filter-bar second">
      <el-select v-model="query.aftersaleType" placeholder="全部售后类型" clearable style="width: 160px" @change="onSearch">
        <el-option
          v-for="(item, key) in AFTERSALE_TYPE_LABEL"
          :key="key"
          :label="item.text"
          :value="key"
        />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="申请开始日期"
        end-placeholder="申请结束日期"
        style="width: 280px"
        @change="onSearch"
      />
      <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <el-table :data="list" stripe v-loading="loading" empty-text="暂无售后单">
      <el-table-column label="售后单号 / 申请时间" width="200">
        <template #default="{ row }">
          <div class="mono">{{ row.aftersaleNo }}</div>
          <div class="muted">{{ formatTime(row.createTime) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="商家" prop="merchantName" width="120" show-overflow-tooltip />
      <el-table-column label="商品明细" min-width="220">
        <template #default="{ row }">
          <div class="item-cell">
            <el-image :src="row.image" fit="cover" class="item-img" />
            <div class="item-info">
              <div class="item-name">{{ row.productName }}</div>
              <div class="item-sub">{{ row.skuName }}<span v-if="row.spec"> · {{ row.spec }}</span></div>
              <div class="item-sub muted">×{{ row.quantity }} · 小计 ¥{{ row.subtotal }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="订单号" width="170">
        <template #default="{ row }">
          <div class="mono">{{ row.orderNo }}</div>
          <el-tag :type="getOrderStatusType(row.orderStatus)" size="small" effect="plain">
            {{ row.orderStatusLabel || getOrderStatusLabel(row.orderStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="消费者" prop="userNickname" width="110" show-overflow-tooltip />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getAftersaleTypeType(row.aftersaleType)" round>
            {{ row.aftersaleTypeLabel || getAftersaleTypeLabel(row.aftersaleType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请金额" width="100" align="right">
        <template #default="{ row }">¥{{ row.applyAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getAftersaleStatusType(row.aftersaleStatus)" effect="dark" round>
            {{ row.aftersaleStatusLabel || getAftersaleStatusLabel(row.aftersaleStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canForceRefund(row)"
            size="small"
            type="danger"
            @click="openForceRefund(row)"
          >
            强制退款
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <!-- 详情 Drawer（只读监控） -->
    <el-drawer v-model="detailVisible" title="售后详情（只读）" size="640px">
      <div v-if="detail" v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="售后单号">{{ detail.aftersaleNo }}</el-descriptions-item>
          <el-descriptions-item label="售后状态">
            <el-tag :type="getAftersaleStatusType(detail.aftersaleStatus)" effect="dark" round>
              {{ detail.aftersaleStatusLabel || getAftersaleStatusLabel(detail.aftersaleStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="商家">{{ detail.merchantName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="消费者">{{ detail.userNickname }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="getOrderStatusType(detail.orderStatus)" effect="plain">
              {{ detail.orderStatusLabel || getOrderStatusLabel(detail.orderStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单售后进度">
            <el-tag :type="getOrderAftersaleType(detail.orderAftersaleStatus)" effect="plain">
              {{ getOrderAftersaleLabel(detail.orderAftersaleStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="售后类型">
            {{ detail.aftersaleTypeLabel || getAftersaleTypeLabel(detail.aftersaleType) }}
          </el-descriptions-item>
          <el-descriptions-item label="申请金额">¥{{ detail.applyAmount }}</el-descriptions-item>
          <el-descriptions-item label="审核通过金额">
            {{ detail.approvedAmount == null ? '—' : '¥' + detail.approvedAmount }}
          </el-descriptions-item>
          <el-descriptions-item label="订单实付">¥{{ detail.payAmount }}</el-descriptions-item>
          <el-descriptions-item label="订单已退">¥{{ detail.orderRefundedAmount }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatTime(detail.auditTime) }}</el-descriptions-item>
          <el-descriptions-item label="退款单号">{{ detail.refundNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="退款时间">{{ formatTime(detail.refundTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核备注" :span="2">{{ detail.auditRemark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="block-title">商品明细</div>
        <div class="item-cell item-block">
          <el-image :src="detail.image" fit="cover" class="item-img lg" />
          <div class="item-info">
            <div class="item-name">{{ detail.productName }}</div>
            <div class="item-sub">{{ detail.skuName }}<span v-if="detail.spec"> · {{ detail.spec }}</span></div>
            <div class="item-sub muted">
              数量 ×{{ detail.quantity }} · 小计 ¥{{ detail.subtotal }}
              · 已退 {{ detail.refundedQty }} 件 / ¥{{ detail.itemRefundedAmount }}
            </div>
          </div>
        </div>

        <div class="block-title">申请内容</div>
        <div class="apply-block">
          <div><b>原因：</b>{{ detail.reason }}</div>
          <div><b>描述：</b>{{ detail.description || '—' }}</div>
          <div v-if="imageList(detail.images).length" class="proof">
            <b>凭证：</b>
            <el-image
              v-for="(url, idx) in imageList(detail.images)"
              :key="idx"
              :src="url"
              :preview-src-list="imageList(detail.images)"
              :initial-index="idx"
              fit="cover"
              class="proof-img"
            />
          </div>
        </div>

        <div class="block-title">处理轨迹</div>
        <el-timeline v-if="detail.timeline?.length">
          <el-timeline-item
            v-for="(node, idx) in detail.timeline"
            :key="node.id || idx"
            :timestamp="formatTime(node.createTime)"
            placement="top"
            :type="idx === detail.timeline.length - 1 ? 'primary' : ''"
          >
            <div class="tl-title">
              {{ node.toStatusLabel || getAftersaleStatusLabel(node.toStatus) }}
              <el-tag size="small" effect="plain" class="tl-role">
                {{ node.operatorRoleLabel || node.operatorRole }}
              </el-tag>
            </div>
            <div v-if="node.fromStatus" class="tl-sub">
              {{ node.fromStatusLabel || getAftersaleStatusLabel(node.fromStatus) }}
              →
              {{ node.toStatusLabel || getAftersaleStatusLabel(node.toStatus) }}
            </div>
            <div v-if="node.remark" class="tl-remark">{{ node.remark }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无处理轨迹" :image-size="60" />
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail && detail.aftersaleStatus === AFTERSALE_STATUS.APPLYING"
          @click="openForceAudit(detail)"
        >
          强制审核
        </el-button>
        <el-button v-if="detail && canForceRefund(detail)" type="danger" @click="openForceRefund(detail)">
          强制退款
        </el-button>
      </template>
    </el-drawer>

    <!-- 强制审核 Dialog -->
    <el-dialog v-model="auditVisible" title="强制审核（Admin 介入）" width="480px">
      <div v-if="auditRow" class="audit-info">
        <div><b>售后单号：</b>{{ auditRow.aftersaleNo }}</div>
        <div><b>商家：</b>{{ auditRow.merchantName || '—' }}</div>
        <div><b>商品：</b>{{ auditRow.productName }} · {{ auditRow.skuName }}</div>
        <div><b>申请金额：</b>¥{{ auditRow.applyAmount }}</div>
        <div><b>申请原因：</b>{{ auditRow.reason }}</div>
      </div>
      <el-form :model="auditForm" label-width="80px" style="margin-top: 16px">
        <el-form-item label="审核结论" required>
          <el-radio-group v-model="auditForm.approve">
            <el-radio :label="true">通过（立即退款）</el-radio>
            <el-radio :label="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注" :required="!auditForm.approve">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="auditForm.approve ? '可填写审核说明' : '请填写驳回理由（消费者可见）'"
          />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="该操作将跳过商家审核直接改变售后状态并触发退款，且会记录操作日志（operator_role=ADMIN）。"
      />
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForceAudit">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 强制退款 Dialog（二次确认） -->
    <el-dialog v-model="refundVisible" title="强制退款（Admin 介入）" width="480px">
      <div v-if="refundRow" class="audit-info">
        <div><b>售后单号：</b>{{ refundRow.aftersaleNo }}</div>
        <div><b>当前状态：</b>{{ refundRow.aftersaleStatusLabel || getAftersaleStatusLabel(refundRow.aftersaleStatus) }}</div>
        <div><b>商家：</b>{{ refundRow.merchantName || '—' }}</div>
        <div><b>商品：</b>{{ refundRow.productName }} · {{ refundRow.skuName }}</div>
        <div><b>申请金额：</b>¥{{ refundRow.applyAmount }}</div>
      </div>
      <el-form :model="refundForm" label-width="80px" style="margin-top: 16px">
        <el-form-item label="退款金额">
          <span class="amount-text">¥{{ refundRow?.approvedAmount ?? refundRow?.applyAmount }}</span>
          <div class="form-tip">金额由后端按「审核通过金额，未审核则取申请金额」确定，并二次校验可退余额</div>
        </el-form-item>
        <el-form-item label="介入原因" required>
          <el-input
            v-model="refundForm.reason"
            type="textarea"
            :rows="3"
            placeholder="必填，如「商家超时未处理，投诉成立」"
          />
        </el-form-item>
        <el-form-item label="二次确认" required>
          <el-checkbox v-model="refundForm.confirmed">
            我已知晓：该操作将跳过商家审核立即退款，且不可撤销
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="submitting"
          :disabled="!refundForm.confirmed"
          @click="submitForceRefund"
        >
          确认强制退款
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import {
  getAdminAftersales,
  getAdminAftersaleStats,
  getAftersaleDetail,
  adminAuditAftersale,
  adminForceRefund
} from '@/api';
import {
  AFTERSALE_STATUS,
  AFTERSALE_TYPE_LABEL,
  AFTERSALE_FILTER_TAB,
  getAftersaleStatusLabel,
  getAftersaleStatusType,
  getAftersaleTypeLabel,
  getAftersaleTypeType,
  getOrderStatusLabel,
  getOrderStatusType,
  getOrderAftersaleLabel,
  getOrderAftersaleType
} from '@/config/business';

// ---------- 商家下钻 ----------
const merchantId = ref(null);

// ---------- 统计 ----------
const rawStats = ref({});
const stats = computed(() => ({
  total: rawStats.value.total ?? 0,
  applying: rawStats.value.applying ?? 0,
  approved: rawStats.value.approved ?? 0,
  rejected: rawStats.value.rejected ?? 0,
  refunding: rawStats.value.refunding ?? 0,
  refunded: rawStats.value.refunded ?? 0,
  pendingCount: rawStats.value.pendingCount ?? 0,
  refundedAmount: rawStats.value.refundedAmount ?? '0.00',
  scope: rawStats.value.scope || '全平台',
  caliber: rawStats.value.caliber || ''
}));

// ---------- 列表 ----------
const list = ref([]);
const loading = ref(false);
const total = ref(0);
const dateRange = ref(null);
const query = reactive({
  tab: AFTERSALE_FILTER_TAB.ALL,
  keyword: '',
  aftersaleType: '',
  pageNum: 1,
  pageSize: 10
});

// ---------- 详情 ----------
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref(null);

// ---------- 强制介入 ----------
const auditVisible = ref(false);
const auditRow = ref(null);
const auditForm = reactive({ approve: true, remark: '' });

const refundVisible = ref(false);
const refundRow = ref(null);
const refundForm = reactive({ reason: '', confirmed: false });

const submitting = ref(false);

/** 仅 APPLYING / REJECTED 可强制退款（与后端准入一致） */
function canForceRefund(row) {
  return row && (row.aftersaleStatus === AFTERSALE_STATUS.APPLYING
    || row.aftersaleStatus === AFTERSALE_STATUS.REJECTED);
}

// ==================== 数据加载 ====================
async function loadStats() {
  try {
    const params = {};
    if (merchantId.value) params.merchantId = merchantId.value;
    rawStats.value = (await getAdminAftersaleStats(params)) || {};
  } catch (e) {
    // 错误提示由 axios 拦截器统一处理（约束文档 9）
    rawStats.value = {};
  }
}

async function loadList(page) {
  if (page) query.pageNum = page;
  loading.value = true;
  try {
    const params = buildParams({
      keyword: query.keyword,
      aftersaleType: query.aftersaleType,
      merchantId: merchantId.value,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    });
    // ALL 为前端聚合维度，不作为后端筛选条件
    if (query.tab !== AFTERSALE_FILTER_TAB.ALL) params.aftersaleStatus = query.tab;
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0];
      params.endDate = dateRange.value[1];
    }
    const res = await getAdminAftersales(params);
    list.value = res?.records || [];
    total.value = res?.total || 0;
  } catch (e) {
    list.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
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

// ==================== 交互 ====================
function onMerchantChange() {
  loadStats();
  loadList(1);
}

function onTabChange() {
  loadList(1);
}

function onSearch() {
  loadList(1);
}

function resetQuery() {
  merchantId.value = null;
  query.tab = AFTERSALE_FILTER_TAB.ALL;
  query.keyword = '';
  query.aftersaleType = '';
  dateRange.value = null;
  loadStats();
  loadList(1);
}

function onPageChange(page) {
  loadList(page);
}

function onSizeChange(size) {
  query.pageSize = size;
  loadList(1);
}

async function openDetail(row) {
  detailVisible.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    detail.value = await getAftersaleDetail(row.id);
  } catch (e) {
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

function openForceAudit(row) {
  auditRow.value = row;
  auditForm.approve = true;
  auditForm.remark = '';
  auditVisible.value = true;
}

async function submitForceAudit() {
  if (!auditForm.approve && !auditForm.remark?.trim()) {
    ElMessage.warning('驳回必须填写理由');
    return;
  }
  submitting.value = true;
  try {
    await adminAuditAftersale(auditRow.value.id, {
      approve: auditForm.approve,
      remark: auditForm.remark
    });
    ElMessage.success(auditForm.approve ? '强制审核通过，退款已完成' : '已强制驳回');
    auditVisible.value = false;
    detailVisible.value = false;
    await Promise.all([loadList(1), loadStats()]);
  } catch (e) {
    // 失败原因由拦截器 toast
  } finally {
    submitting.value = false;
  }
}

function openForceRefund(row) {
  refundRow.value = row;
  refundForm.reason = '';
  refundForm.confirmed = false;
  refundVisible.value = true;
}

async function submitForceRefund() {
  if (!refundForm.reason?.trim()) {
    ElMessage.warning('强制退款原因必填');
    return;
  }
  if (!refundForm.confirmed) {
    ElMessage.warning('请先完成二次确认');
    return;
  }
  submitting.value = true;
  try {
    await adminForceRefund(refundRow.value.id, { reason: refundForm.reason });
    ElMessage.success('强制退款已完成');
    refundVisible.value = false;
    detailVisible.value = false;
    await Promise.all([loadList(1), loadStats()]);
  } catch (e) {
    // 失败原因由拦截器 toast
  } finally {
    submitting.value = false;
  }
}

// ==================== 展示辅助 ====================
function imageList(images) {
  if (!images) return [];
  return String(images)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

function formatTime(t) {
  if (!t) return '—';
  return String(t).replace('T', ' ').slice(0, 16);
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadStats();
  loadList(1);
});
</script>

<style scoped>
.aftersale-page { padding: 0; background: #fff; border-radius: 8px; padding: 16px; }

.admin-tip { margin-bottom: 12px; }
.merchant-filter { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.filter-label { font-size: 13px; color: #606266; }

.stats-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.stat-card { padding: 14px 8px; border-radius: 8px; color: #fff; text-align: center; }
.stat-card.total     { background: linear-gradient(135deg, #409eff, #66b1ff); }
.stat-card.applying  { background: linear-gradient(135deg, #e6a23c, #f0c78a); }
.stat-card.approved  { background: linear-gradient(135deg, #7c5cff, #a48bff); }
.stat-card.rejected  { background: linear-gradient(135deg, #909399, #b1b3b8); }
.stat-card.refunding { background: linear-gradient(135deg, #409eff, #79bbff); }
.stat-card.refunded  { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-num { font-size: 24px; font-weight: bold; }
.stat-label { font-size: 12px; opacity: 0.92; margin-top: 4px; }

.caliber-bar {
  display: flex;
  gap: 20px;
  align-items: center;
  font-size: 13px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
}
.caliber-bar .pending { color: #e6a23c; }
.caliber-bar .amount { color: #67c23a; }
.caliber { font-size: 12px; color: #909399; }
.caliber-bar .caliber { margin-left: auto; }

.filter-bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.filter-bar.second { justify-content: flex-start; margin-top: 12px; }

.pager { margin-top: 16px; display: flex; justify-content: flex-end; }

.mono { font-family: Consolas, Monaco, monospace; font-size: 13px; }
.muted { color: #909399; font-size: 12px; }

.item-cell { display: flex; gap: 10px; align-items: center; }
.item-img { width: 48px; height: 48px; border-radius: 6px; background: #f5f7fa; flex-shrink: 0; }
.item-img.lg { width: 64px; height: 64px; }
.item-info { display: flex; flex-direction: column; }
.item-name { font-weight: 600; font-size: 14px; }
.item-sub { font-size: 12px; color: #606266; }

.detail-body { font-size: 13px; }
.block-title {
  font-weight: 600;
  font-size: 14px;
  margin: 18px 0 10px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.item-block { background: #f5f7fa; padding: 12px; border-radius: 6px; }
.apply-block { background: #f5f7fa; padding: 12px; border-radius: 6px; line-height: 1.9; }
.proof { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }
.proof-img { width: 64px; height: 64px; border-radius: 6px; }

.tl-title { font-weight: 600; }
.tl-role { margin-left: 8px; }
.tl-sub { font-size: 12px; color: #909399; }
.tl-remark { font-size: 12px; color: #606266; margin-top: 2px; }

.audit-info { background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 13px; line-height: 1.9; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
.amount-text { font-size: 16px; font-weight: bold; color: #f56c6c; }
</style>
