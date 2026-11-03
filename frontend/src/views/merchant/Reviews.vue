<template>
  <div class="merchant-reviews">
    <!-- 头部统计 -->
    <div class="header">
      <h2>📊 评价管理</h2>
      <div class="stats">
        <div class="stat-item">
          <div class="stat-num">{{ reviews.length }}</div>
          <div class="stat-label">总评价数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num highlight">{{ pendingReply }}</div>
          <div class="stat-label">待回复</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ avgRating.toFixed(1) }}⭐</div>
          <div class="stat-label">平均评分</div>
        </div>
      </div>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-radio-group v-model="filterStatus" @change="loadReviews">
        <el-radio-button value="all">全部 ({{ reviews.length }})</el-radio-button>
        <el-radio-button value="pending">待回复 ({{ pendingReply }})</el-radio-button>
        <el-radio-button value="replied">已回复 ({{ repliedCount }})</el-radio-button>
      </el-radio-group>
      <el-select v-model="filterRating" placeholder="全部星级" clearable size="default" style="width: 140px" @change="loadReviews">
        <el-option :value="5" label="⭐⭐⭐⭐⭐ 5星" />
        <el-option :value="4" label="⭐⭐⭐⭐ 4星" />
        <el-option :value="3" label="⭐⭐⭐ 3星" />
        <el-option :value="2" label="⭐⭐ 2星" />
        <el-option :value="1" label="⭐ 1星" />
      </el-select>
    </div>

    <!-- 评价卡片列表 -->
    <div class="review-list" v-if="filteredReviews.length">
      <div v-for="r in filteredReviews" :key="r.id" class="review-card">
        <div class="review-header">
          <div class="review-user">
            <el-avatar :size="32" :style="{ background: '#f0a020', color: '#fff' }">
              {{ r.isAnonymous ? '匿' : 'U' }}
            </el-avatar>
            <span class="user-name">{{ r.isAnonymous ? '匿名用户' : ('用户' + r.userId) }}</span>
            <el-rate :model-value="r.rating" disabled size="small" text-color="#ff9900" />
          </div>
          <div class="review-meta">
            <span>{{ formatTime(r.createTime) }}</span>
            <el-tag v-if="r.replyContent" type="success" size="small">已回复</el-tag>
            <el-tag v-else type="warning" size="small">待回复</el-tag>
          </div>
        </div>

        <div class="review-body">
          <div class="product-info">商品：{{ r.skuName || r.productName || `SKU #${r.skuId}` }}</div>
          <div class="review-content">{{ r.content || '(无文字评价)' }}</div>
        </div>

        <!-- 商家回复区域 -->
        <div class="reply-area" v-if="r.replyContent">
          <div class="reply-label">🏪 商家回复：</div>
          <div class="reply-content">{{ r.replyContent }}</div>
          <div class="reply-meta" v-if="r.replyTime">{{ formatTime(r.replyTime) }}</div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button v-if="!r.replyContent" type="primary" size="small" @click="openReply(r)">
            回复评价
          </el-button>
          <el-button v-else type="default" size="small" @click="openEdit(r)">
            编辑回复
          </el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty v-else description="暂无评价" />

    <!-- 回复/编辑 Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑回复' : '回复评价'" width="500px">
      <div class="dialog-info">
        <div>用户：{{ currentReview?.isAnonymous ? '匿名用户' : ('用户' + currentReview?.userId) }}
          <el-rate :model-value="currentReview?.rating" disabled size="small" />
        </div>
        <div class="dialog-product">商品：{{ currentReview?.skuName || currentReview?.productName || `SKU #${currentReview?.skuId}` }}</div>
      </div>
      <el-input
        v-model="replyText"
        type="textarea"
        :rows="4"
        :maxlength="200"
        show-word-limit
        :placeholder="isEdit ? '修改您之前的回复...' : '请输入回复内容（最多200字）'"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!replyText.trim()" @click="submitReply">
          {{ isEdit ? '保存修改' : '提交回复' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMerchantReviews, replyReview, editReply } from '@/api'

const reviews = ref([])
const filterStatus = ref('all')
const filterRating = ref(null)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentReview = ref(null)
const replyText = ref('')
const submitting = ref(false)

// 筛选
const filteredReviews = computed(() => {
  let list = reviews.value
  if (filterStatus.value === 'pending') list = list.filter(r => !r.replyContent)
  if (filterStatus.value === 'replied') list = list.filter(r => r.replyContent)
  return list
})

const pendingReply = computed(() => reviews.value.filter(r => !r.replyContent).length)
const repliedCount = computed(() => reviews.value.filter(r => r.replyContent).length)
const avgRating = computed(() => {
  if (!reviews.value.length) return 0
  return reviews.value.reduce((s, r) => s + (r.rating || 0), 0) / reviews.value.length
})

const loadReviews = async () => {
  try {
    reviews.value = await getMerchantReviews(filterRating.value) || []
  } catch (e) {
    ElMessage.error('加载评价列表失败')
  }
}

const openReply = (r) => {
  isEdit.value = false
  currentReview.value = r
  replyText.value = ''
  dialogVisible.value = true
}
const openEdit = (r) => {
  isEdit.value = true
  currentReview.value = r
  replyText.value = r.replyContent || ''
  dialogVisible.value = true
}

const submitReply = async () => {
  if (!replyText.value.trim()) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await editReply(currentReview.value.id, replyText.value.trim())
      ElMessage.success('回复已更新')
    } else {
      await replyReview(currentReview.value.id, replyText.value.trim())
      ElMessage.success('回复成功')
    }
    dialogVisible.value = false
    await loadReviews()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const formatTime = (iso) => {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(loadReviews)
</script>

<style scoped lang="scss">
.merchant-reviews { padding: 16px; }

.header {
  margin-bottom: 16px;
  h2 { margin: 0 0 12px; font-size: 20px; color: #303133; }
  .stats {
    display: flex; gap: 16px;
    .stat-item {
      flex: 1;
      background: #fff; border-radius: 8px; padding: 14px;
      text-align: center;
      .stat-num { font-size: 22px; font-weight: 600; color: #303133; }
      .stat-num.highlight { color: #e6a23c; }
      .stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
    }
  }
}

.filter-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px;
}

.review-list { display: flex; flex-direction: column; gap: 12px; }
.review-card {
  background: #fff; border-radius: 8px; padding: 14px;
  border: 1px solid #ebeef5;

  .review-header {
    display: flex; justify-content: space-between; align-items: center;
    margin-bottom: 10px;
    .review-user {
      display: flex; align-items: center; gap: 8px;
      .user-name { font-size: 13px; font-weight: 500; color: #303133; }
    }
    .review-meta {
      display: flex; align-items: center; gap: 8px;
      font-size: 12px; color: #909399;
    }
  }

  .review-body {
    background: #fafafa; border-radius: 6px; padding: 10px;
    .product-info { font-size: 12px; color: #909399; margin-bottom: 6px; }
    .review-content { font-size: 14px; color: #303133; line-height: 1.6; }
  }

  .reply-area {
    margin-top: 10px; padding: 10px; background: #f0f9eb; border-radius: 6px;
    .reply-label { font-size: 12px; color: #67c23a; font-weight: 500; margin-bottom: 4px; }
    .reply-content { font-size: 13px; color: #303133; line-height: 1.5; }
    .reply-meta { font-size: 11px; color: #909399; margin-top: 4px; text-align: right; }
  }

  .action-bar { margin-top: 10px; text-align: right; }
}

.dialog-info {
  background: #f5f7fa; border-radius: 6px; padding: 10px; margin-bottom: 12px;
  font-size: 13px; color: #606266;
  .dialog-product { margin-top: 6px; color: #909399; }
}
</style>
