<template>
  <div class="merchant-supply">

    <!-- ===== 顶部概览：运营视角数字 ===== -->
    <el-row :gutter="12" class="overview-row">
      <el-col :xs="12" :sm="6">
        <div class="ov-card ov-warn">
          <div class="ov-num">{{ suggestions.length }}</div>
          <div class="ov-label">⚠️ 待补货 SKU</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="ov-card ov-draft">
          <div class="ov-num">{{ statCounts.DRAFT }}</div>
          <div class="ov-label">📝 草稿计划</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="ov-card ov-confirmed">
          <div class="ov-num">{{ statCounts.CONFIRMED }}</div>
          <div class="ov-label">⏳ 已确认待到货</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="ov-card ov-arrived">
          <div class="ov-num">{{ statCounts.ARRIVED }}</div>
          <div class="ov-label">✅ 本月已到货</div>
        </div>
      </el-col>
    </el-row>

    <!-- ===== Tab 切换：预警建议 vs 计划列表 ===== -->
    <el-tabs v-model="activeTab" class="supply-tabs">

      <!-- ==================== Tab 1：💡 补货建议 ==================== -->
      <el-tab-pane label="💡 补货建议" name="suggest">
        <div class="tab-actions">
          <span class="tab-hint">根据当前库存警戒线自动生成，仅建议不会自动创建计划</span>
          <el-button type="success" :disabled="suggestions.length === 0" @click="batchCreateFromSuggestions">
            ⚡ 全部生成计划（{{ suggestions.length }} 项）
          </el-button>
          <el-button text @click="loadSuggestions">🔄 刷新</el-button>
        </div>

        <!-- 无建议 -->
        <el-empty v-if="!loadingSuggest && suggestions.length === 0"
          description="当前所有 SKU 库存充足，暂无补货建议 🎉" />

        <!-- 建议卡片列表 -->
        <div v-else class="suggest-grid">
          <div
            v-for="s in sortedSuggestions"
            :key="'s-' + s.skuId + '-' + s.communityId"
            class="suggest-card"
            :class="{ 'suggest-empty': s.availableQty === 0 }"
          >
            <!-- 商品图 -->
            <el-image class="suggest-img" :src="s.mainImage || s.skuImage" fit="cover">
              <template #error><div class="img-ph">无图</div></template>
            </el-image>

            <!-- 信息区 -->
            <div class="suggest-info">
              <div class="suggest-title">
                {{ s.productName || `SKU #${s.skuId}` }}
              </div>
              <div class="suggest-sub">
                <span v-if="s.skuName" class="sku-tag">{{ s.skuName }}</span>
                <span v-if="s.communityName" class="comm-tag">📍 {{ s.communityName }}</span>
              </div>

              <!-- 库存可视化 -->
              <div class="stock-bar-wrap">
                <div class="stock-bar">
                  <div
                    class="stock-bar-fill"
                    :style="{ width: stockBarPct(s) + '%' }"
                    :class="{ 'bar-empty': s.availableQty === 0, 'bar-warn': s.availableQty > 0 && s.availableQty <= s.warnQty }"
                  />
                  <div class="stock-bar-warn" :style="{ left: warnBarPct(s) + '%' }" />
                </div>
                <div class="stock-bar-labels">
                  <span :class="{ 'danger': s.availableQty === 0, 'warn': s.availableQty > 0 && s.availableQty <= s.warnQty }">
                    当前 {{ s.availableQty }}
                  </span>
                  <span class="warn-line-label">警戒线 {{ s.warnQty }}</span>
                  <span>满量 {{ totalQty(s) }}</span>
                </div>
              </div>

              <!-- 建议区 -->
              <div class="suggest-advice">
                <span class="advice-qty">
                  建议补 <strong>{{ s.suggestedQty }}</strong> 件
                </span>
                <el-tag
                  size="small"
                  :type="s.reason === '预测驱动' ? 'primary' : 'warning'"
                  effect="plain"
                >
                  {{ s.reason }}
                  <span v-if="s.forecastQty">（预测 {{ s.forecastQty }}）</span>
                </el-tag>
              </div>
            </div>

            <!-- 操作 -->
            <el-button type="primary" size="small" @click="quickCreatePlan(s)">
              生成计划
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== Tab 2：📋 供应计划 ==================== -->
      <el-tab-pane label="📋 供应计划" name="plans">
        <!-- 筛选栏 -->
        <div class="plans-filter">
          <el-radio-group v-model="planStatusFilter" size="small">
            <el-radio-button :value="''">全部 ({{ totalPlans }})</el-radio-button>
            <el-radio-button :value="SUPPLY_PLAN_STATUS.DRAFT">草稿 ({{ statCounts.DRAFT }})</el-radio-button>
            <el-radio-button :value="SUPPLY_PLAN_STATUS.CONFIRMED">已确认 ({{ statCounts.CONFIRMED }})</el-radio-button>
            <el-radio-button :value="SUPPLY_PLAN_STATUS.ARRIVED">已到货 ({{ statCounts.ARRIVED }})</el-radio-button>
            <el-radio-button :value="SUPPLY_PLAN_STATUS.CANCELLED">已取消 ({{ statCounts.CANCELLED }})</el-radio-button>
          </el-radio-group>
          <div class="filter-actions">
            <el-button size="small" @click="openCreateDialog">+ 手动创建</el-button>
            <el-button size="small" type="success" plain @click="openBatchDialog">⚡ 批量生成</el-button>
          </div>
        </div>

        <el-table :data="filteredPlans" v-loading="loading" stripe>
          <el-table-column label="商品" min-width="220">
            <template #default="{ row }">
              <div class="item-cell">
                <el-image class="item-img" :src="row.mainImage || row.skuImage" fit="cover">
                  <template #error><div class="img-ph-sm">无</div></template>
                </el-image>
                <div class="item-info">
                  <div class="item-name">{{ row.productName || `商品 #(sku=${row.skuId})` }}</div>
                  <div class="item-sub">
                    <span v-if="row.skuName" class="sku-mini">{{ row.skuName }}</span>
                    <span v-if="row.communityName">📍 {{ row.communityName }}</span>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="计划日期" prop="planDate" width="110" />

          <el-table-column label="数量" width="180">
            <template #default="{ row }">
              <div class="qty-col">
                <div v-if="row.forecastQty" class="qty-line qty-forecast">
                  预测 <span class="num">{{ row.forecastQty }}</span>
                </div>
                <div class="qty-line qty-plan">
                  计划 <span class="num font-600">{{ row.adjustedQty ?? row.planQty }}</span>
                  <el-tag v-if="row.adjustedQty && row.adjustedQty !== row.planQty" size="small" type="warning" effect="plain">
                    已调
                  </el-tag>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getSupplyPlanType(row.status)" effect="dark" size="small">
                {{ getSupplyPlanLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === SUPPLY_PLAN_STATUS.DRAFT">
                <el-button link type="success" size="small" @click="confirmPlan(row)">确认</el-button>
                <el-button link type="primary" size="small" @click="openAdjustDialog(row)">调整</el-button>
                <ConfirmButton text="取消" type="danger" :confirm-message="'取消后无法恢复'" @confirm="cancelPlan(row)" />
              </template>
              <template v-else-if="row.status === SUPPLY_PLAN_STATUS.CONFIRMED">
                <el-button link type="success" size="small" @click="openArriveDialog(row)">✓ 标记到货</el-button>
                <el-button link type="primary" size="small" @click="openAdjustDialog(row)">调整</el-button>
                <ConfirmButton text="取消" type="danger" :confirm-message="'取消后库存需要回滚'" @confirm="cancelPlan(row)" />
              </template>
              <template v-else-if="row.status === SUPPLY_PLAN_STATUS.ARRIVED">
                <ConfirmButton text="取消（回滚库存）" type="danger"
                               :confirm-message="'⚠️ 已到货的计划取消会触发库存 ROLLBACK 回滚'"
                               @confirm="cancelPlan(row)" />
              </template>
              <span v-else class="na">已关闭</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 手动创建对话框 ===== -->
    <el-dialog v-model="showCreateDialog" title="新建供应计划" width="480px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="SKU ID">
          <el-input-number v-model="createForm.skuId" :min="1" style="width: 100%" />
          <small class="hint">在库存管理页面查看 SKU ID</small>
        </el-form-item>
        <el-form-item label="社区">
          <el-select v-model="createForm.communityId" placeholder="选择社区" style="width: 100%">
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="c.communityName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="createForm.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input-number v-model="createForm.planQty" :min="1" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注原因">
          <el-input v-model="createForm.adjustReason" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="doCreatePlan">创建</el-button>
      </template>
    </el-dialog>

    <!-- ===== 批量创建对话框 ===== -->
    <el-dialog v-model="showBatchDialog" title="⚡ 批量从预测生成" width="520px">
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="社区">
          <el-select v-model="batchForm.communityId" placeholder="选择社区" style="width: 100%">
            <el-option
              v-for="c in communities"
              :key="c.id"
              :label="c.communityName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="batchForm.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="补货倍数">
          <el-input-number v-model="batchForm.multiplier" :min="0.5" :max="5" :step="0.1" />
          <small class="hint">默认 1.0 = 预测值原样；>1 商家加码</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchDialog = false">取消</el-button>
        <el-button type="success" @click="doBatchCreate">批量生成</el-button>
      </template>
    </el-dialog>

    <!-- ===== 调整对话框 ===== -->
    <el-dialog v-model="showAdjustDialog" title="调整供应计划" width="480px">
      <div class="adjust-head">
        <el-image class="item-img-sm" :src="adjustForm.mainImage || adjustForm.skuImage" fit="cover">
          <template #error><div class="img-ph-sm">无</div></template>
        </el-image>
        <div>
          <div class="item-name">{{ adjustForm.productName || `商品 #(sku=${adjustForm.skuId})` }}</div>
          <div class="item-sub">{{ adjustForm.skuName }} · {{ adjustForm.communityName }} · {{ adjustForm.planDate }}</div>
        </div>
      </div>
      <el-form :model="adjustForm" label-width="110px" style="margin-top: 12px">
        <el-form-item label="预测值" v-if="adjustForm.forecastQty">
          <span>{{ adjustForm.forecastQty }}</span>
        </el-form-item>
        <el-form-item label="原计划数量">
          <span class="font-600">{{ adjustForm.oldQty }}</span>
        </el-form-item>
        <el-form-item label="新数量">
          <el-input-number v-model="adjustForm.newQty" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" placeholder="约束 6.1 必须记录" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="doAdjustPlan">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- ===== 到货对话框 ===== -->
    <el-dialog v-model="showArriveDialog" title="标记到货" width="480px">
      <div class="adjust-head">
        <el-image class="item-img-sm" :src="arriveForm.mainImage || arriveForm.skuImage" fit="cover">
          <template #error><div class="img-ph-sm">无</div></template>
        </el-image>
        <div>
          <div class="item-name">{{ arriveForm.productName }}</div>
          <div class="item-sub">{{ arriveForm.skuName }} · {{ arriveForm.communityName }} · {{ arriveForm.planDate }}</div>
        </div>
      </div>
      <el-form :model="arriveForm" label-width="110px" style="margin-top: 12px">
        <el-form-item label="计划数量">
          <span class="font-600">{{ arriveForm.plannedQty }}</span>
          <el-tag v-if="arriveForm.forecastQty" size="small" effect="plain" style="margin-left:8px">
            预测 {{ arriveForm.forecastQty }}
          </el-tag>
        </el-form-item>
        <el-form-item label="实际到货">
          <el-input-number v-model="arriveForm.actualQty" :min="0" :max="9999" />
          <small class="hint">留空或 = 计划 → 无差异；填不同值 → 差异写入 SUPPLY_IN 流水</small>
        </el-form-item>
        <el-form-item v-if="arriveForm.diff !== 0" label="差异提示">
          <el-alert
            :type="arriveForm.diff < 0 ? 'warning' : 'info'"
            :title="arriveForm.diff < 0
              ? '比计划少到 ' + Math.abs(arriveForm.diff) + ' 件（库存将少加）'
              : '比计划多到 ' + arriveForm.diff + ' 件'"
            show-icon :closable="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showArriveDialog = false">取消</el-button>
        <el-button type="success" @click="doArrivePlan">✓ 确认到货入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ConfirmButton from '@/components/ConfirmButton.vue'

import {
  SUPPLY_PLAN_STATUS,
  SUPPLY_PLAN_STATUS_LABEL,
  getSupplyPlanLabel,
  getSupplyPlanType
} from '@/config/business.js'

import {
  createSupplyPlan,
  adjustSupplyPlan,
  confirmSupplyPlan,
  arriveSupplyPlan,
  cancelSupplyPlan,
  getSupplyPlans,
  batchCreateSupplyPlans,
  getSupplySuggestions,
  getCommunitiesActive
} from '@/api'

// ==================== 状态 ====================
const activeTab = ref('suggest')
const plans = ref([])
const loading = ref(false)
const suggestions = ref([])
const loadingSuggest = ref(false)
const communities = ref([])

const planStatusFilter = ref('')

const showCreateDialog = ref(false)
const showBatchDialog  = ref(false)
const showAdjustDialog = ref(false)
const showArriveDialog = ref(false)

const createForm = reactive({ skuId: 1, communityId: null, planDate: '', planQty: 50, adjustReason: '' })
const batchForm  = reactive({ communityId: null, planDate: '', multiplier: 1.0 })
const adjustForm = reactive({ planId: null, skuId: null, productName: '', skuName: '', communityName: '', planDate: '', mainImage: '', skuImage: '', oldQty: 0, forecastQty: null, newQty: 0, reason: '' })
const arriveForm = reactive({ planId: null, productName: '', skuName: '', communityName: '', planDate: '', mainImage: '', skuImage: '', plannedQty: 0, forecastQty: null, actualQty: null, diff: 0 })

// ==================== computed ====================
const statCounts = computed(() => {
  const counts = { DRAFT: 0, CONFIRMED: 0, ARRIVED: 0, CANCELLED: 0 }
  plans.value.forEach(p => { if (counts[p.status] !== undefined) counts[p.status]++ })
  return counts
})

const totalPlans = computed(() => plans.value.length)

const filteredPlans = computed(() => {
  if (!planStatusFilter.value) return plans.value
  return plans.value.filter(p => p.status === planStatusFilter.value)
})

// 建议按缺口从大到小排序（最紧急的排最前）
const sortedSuggestions = computed(() => {
  return [...suggestions.value].sort((a, b) => b.gapQty - a.gapQty)
})

// 库存条百分比（max 用总库存或 200）
const stockMax = computed(() => {
  if (suggestions.value.length === 0) return 200
  const all = suggestions.value.flatMap(s => [s.availableQty, s.warnQty])
  return Math.max(200, ...all)
})

function stockBarPct(s) {
  return Math.max(0, Math.min(100, (s.availableQty / stockMax.value) * 100))
}
function warnBarPct(s) {
  return Math.max(0, Math.min(100, (s.warnQty / stockMax.value) * 100))
}
function totalQty(s) {
  return s.availableQty + s.gapQty
}

// ==================== 生命周期 ====================
onMounted(async () => {
  loadCommunities()
  loadPlans()
  loadSuggestions()
})

async function loadCommunities() {
  try {
    communities.value = await getCommunitiesActive() || []
    if (communities.value.length > 0) {
      createForm.communityId = communities.value[0].id
      batchForm.communityId = communities.value[0].id
    }
  } catch {}
}

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await getSupplyPlans({}) ?? []
  } catch (e) { plans.value = [] }
  finally { loading.value = false }
}

async function loadSuggestions() {
  loadingSuggest.value = true
  try {
    suggestions.value = await getSupplySuggestions() ?? []
  } catch (e) { suggestions.value = [] }
  finally { loadingSuggest.value = false }
}

// ==================== quickCreate：从建议一键生成 ====================
async function quickCreatePlan(suggestion) {
  const planDate = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
  try {
    await createSupplyPlan({
      skuId: suggestion.skuId,
      communityId: suggestion.communityId,
      planDate,
      planQty: suggestion.suggestedQty,
      adjustReason: `从补货建议自动生成（缺口 ${suggestion.gapQty}, 原因: ${suggestion.reason}）`
    })
    ElMessage.success(`已为「${suggestion.productName}」创建供应计划（${suggestion.suggestedQty} 件）`)
    await loadPlans()
    await loadSuggestions()
  } catch (e) { /* axios 拦截器已提示 */ }
}

async function batchCreateFromSuggestions() {
  if (suggestions.value.length === 0) return
  const communityId = suggestions.value[0].communityId
  const planDate = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
  const skuIds = suggestions.value.map(s => s.skuId)
  try {
    const r = await batchCreateSupplyPlans({ communityId, planDate, skuIds, multiplier: 1.0 })
    ElMessage.success(`批量完成：创建 ${r.createdCount} 条，跳过 ${r.skippedCount} 条`)
    await loadPlans()
    await loadSuggestions()
  } catch {}
}

// ==================== 手动创建 ====================
function openCreateDialog() {
  createForm.planDate = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
  showCreateDialog.value = true
}
async function doCreatePlan() {
  await createSupplyPlan({ ...createForm })
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  await loadPlans()
}

// ==================== 批量创建（从预测） ====================
function openBatchDialog() {
  batchForm.planDate = new Date(Date.now() + 86400000).toISOString().slice(0, 10)
  showBatchDialog.value = true
}
async function doBatchCreate() {
  const r = await batchCreateSupplyPlans({ ...batchForm })
  ElMessage.success(`批量完成：创建 ${r.createdCount} 条，跳过 ${r.skippedCount} 条`)
  showBatchDialog.value = false
  await loadPlans()
  await loadSuggestions()
}

// ==================== 调整 ====================
function openAdjustDialog(row) {
  const oldQty = row.adjustedQty ?? row.planQty
  adjustForm.planId = row.id
  adjustForm.skuId = row.skuId
  adjustForm.productName = row.productName
  adjustForm.skuName = row.skuName
  adjustForm.communityName = row.communityName
  adjustForm.planDate = row.planDate
  adjustForm.mainImage = row.mainImage
  adjustForm.skuImage = row.skuImage
  adjustForm.forecastQty = row.forecastQty
  adjustForm.oldQty = oldQty
  adjustForm.newQty = oldQty
  adjustForm.reason = ''
  showAdjustDialog.value = true
}
async function doAdjustPlan() {
  await adjustSupplyPlan({ planId: adjustForm.planId, newQty: adjustForm.newQty, reason: adjustForm.reason })
  ElMessage.success('调整成功')
  showAdjustDialog.value = false
  await loadPlans()
}

// ==================== 确认 ====================
async function confirmPlan(row) {
  await confirmSupplyPlan(row.id)
  ElMessage.success('已确认，等待到货')
  await loadPlans()
}

// ==================== 到货 ====================
function openArriveDialog(row) {
  const planned = row.adjustedQty ?? row.planQty
  arriveForm.planId = row.id
  arriveForm.productName = row.productName
  arriveForm.skuName = row.skuName
  arriveForm.communityName = row.communityName
  arriveForm.planDate = row.planDate
  arriveForm.mainImage = row.mainImage
  arriveForm.skuImage = row.skuImage
  arriveForm.plannedQty = planned
  arriveForm.forecastQty = row.forecastQty
  arriveForm.actualQty = planned
  arriveForm.diff = 0
  showArriveDialog.value = true
}
watch(() => arriveForm.actualQty, v => {
  arriveForm.diff = (v ?? arriveForm.plannedQty) - arriveForm.plannedQty
})
async function doArrivePlan() {
  const actualQty = arriveForm.actualQty === arriveForm.plannedQty ? null : arriveForm.actualQty
  await arriveSupplyPlan(arriveForm.planId, actualQty)
  ElMessage.success(actualQty !== null
    ? `到货入库完成（实际 ${actualQty}，差异 ${arriveForm.diff}）`
    : '到货入库完成（库存已增加）')
  showArriveDialog.value = false
  await loadPlans()
  await loadSuggestions()
}

// ==================== 取消 ====================
async function cancelPlan(row) {
  const reason = row.status === SUPPLY_PLAN_STATUS.ARRIVED
    ? '手动取消（触发库存 ROLLBACK）'
    : '手动取消'
  await cancelSupplyPlan(row.id, reason)
  ElMessage.success(row.status === SUPPLY_PLAN_STATUS.ARRIVED
    ? '已取消 + 库存回滚完成' : '已取消')
  await loadPlans()
  await loadSuggestions()
}
</script>

<style scoped lang="scss">
.merchant-supply { display: flex; flex-direction: column; gap: 14px; }

/* ===== 概览卡 ===== */
.overview-row { margin-bottom: 4px; }
.ov-card {
  border-radius: 10px;
  padding: 14px 10px;
  color: #fff;
  text-align: center;
  transition: transform .15s;
}
.ov-card:hover { transform: translateY(-2px); }
.ov-warn     { background: linear-gradient(135deg, #f56c6c, #f78989); }
.ov-draft    { background: linear-gradient(135deg, #909399, #a6a9ad); }
.ov-confirmed{ background: linear-gradient(135deg, #e6a23c, #ebb563); }
.ov-arrived  { background: linear-gradient(135deg, #67c23a, #85ce61); }
.ov-num  { font-size: 28px; font-weight: 700; line-height: 1.1; }
.ov-label { font-size: 12px; margin-top: 4px; opacity: .95; }

/* ===== 概览 tabs ===== */
.supply-tabs { background: #fff; padding: 8px 16px 16px; border-radius: 10px; }
.tab-actions { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.tab-hint { font-size: 12px; color: #909399; flex: 1; }

/* ===== 建议网格 ===== */
.suggest-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
}
.suggest-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 10px;
  border: 1px solid #ebeef5;
  background: #fff;
  transition: box-shadow .15s, border-color .15s;
}
.suggest-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,.08); border-color: #c6e2ff; }
.suggest-card.suggest-empty { border-color: #fbc4c4; background: #fff8f8; }

.suggest-img {
  width: 80px; height: 80px; flex-shrink: 0;
  border-radius: 8px; background: #f5f7fa;
  display: flex; align-items: center; justify-content: center;
}
.img-ph { color: #ccc; font-size: 12px; }
.img-ph-sm { color: #ccc; font-size: 10px; }

.suggest-info { flex: 1; min-width: 0; }
.suggest-title { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-sub { display: flex; gap: 6px; margin-top: 4px; flex-wrap: wrap; }
.sku-tag, .comm-tag { font-size: 11px; padding: 2px 6px; border-radius: 4px; background: #f0f2f5; color: #606266; }
.comm-tag { background: #ecf5ff; color: #409eff; }

/* 库存可视化条 */
.stock-bar-wrap { margin-top: 10px; }
.stock-bar {
  height: 8px; background: #f5f7fa; border-radius: 4px; position: relative;
  margin-top: 4px;
}
.stock-bar-fill {
  position: absolute; top: 0; left: 0; height: 100%;
  background: #67c23a; border-radius: 4px; transition: width .3s;
}
.stock-bar-fill.bar-warn { background: #e6a23c; }
.stock-bar-fill.bar-empty { background: #f56c6c; }
.stock-bar-warn {
  position: absolute; top: -2px; width: 2px; height: 12px;
  background: #e6a23c; border-radius: 1px;
}
.stock-bar-labels {
  display: flex; justify-content: space-between;
  font-size: 11px; color: #909399; margin-top: 4px;
}
.stock-bar-labels .danger { color: #f56c6c; font-weight: 600; }
.stock-bar-labels .warn { color: #e6a23c; font-weight: 600; }
.warn-line-label { color: #e6a23c; }

.suggest-advice {
  display: flex; align-items: center; gap: 8px;
  margin-top: 10px;
  padding: 6px 10px; border-radius: 6px;
  background: linear-gradient(90deg, #fdf6ec, #fff8f0);
}
.advice-qty { font-size: 13px; color: #e6a23c; }
.advice-qty strong { font-size: 16px; color: #e6a23c; }

/* ===== 计划列表 ===== */
.plans-filter {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; flex-wrap: wrap; gap: 10px;
}
.filter-actions { display: flex; gap: 8px; }

.item-cell { display: flex; gap: 10px; align-items: center; }
.item-img { width: 44px; height: 44px; border-radius: 6px; background: #f5f7fa; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.item-img-sm { width: 48px; height: 48px; border-radius: 6px; background: #f5f7fa; flex-shrink: 0; }
.item-info { display: flex; flex-direction: column; min-width: 0; }
.item-name { font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-sub { font-size: 12px; color: #606266; display: flex; gap: 6px; flex-wrap: wrap; }
.sku-mini { color: #909399; }

.qty-col { line-height: 1.6; }
.qty-forecast { font-size: 12px; color: #909399; }
.qty-plan { font-size: 13px; }
.num { color: #409eff; margin-left: 2px; }
.num.font-600 { font-weight: 600; color: #303133; }

.font-600 { font-weight: 600; }
.na { color: #c0c4cc; font-size: 12px; }
.hint { display: block; font-size: 12px; color: #909399; margin-top: 4px; }

.adjust-head { display: flex; gap: 12px; align-items: center; padding: 10px; background: #f5f7fa; border-radius: 6px; }
</style>
