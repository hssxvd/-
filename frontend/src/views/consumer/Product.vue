<!--
  消费者端-商品详情：强制先选社区 + 规格真实显示 + 按社区隔离的库存
  约束文档 4.5 / 11.2 / 6.1
-->
<template>
  <div class="product-detail" v-if="!communityLocked">
    <!-- 强制选社区弹层 -->
    <div class="community-gate">
      <div class="gate-card">
        <div class="gate-icon">🏘️</div>
        <h3>请先选择社区</h3>
        <p class="gate-tip">商品库存按社区隔离，选好后才能查看价格与库存</p>
        <el-select
          v-model="selectedCommunityId"
          placeholder="请选择自提社区"
          size="large"
          style="width: 100%"
        >
          <el-option
            v-for="c in communities"
            :key="c.id"
            :label="c.communityName + '（' + c.address + '）'"
            :value="c.id"
          />
        </el-select>
        <el-button
          type="primary"
          size="large"
          style="width: 100%; margin-top: 16px"
          :disabled="!selectedCommunityId"
          :loading="loading"
          @click="onConfirmCommunity"
        >进入商品详情</el-button>
      </div>
    </div>
  </div>

  <div class="product-detail" v-else-if="product">
    <!-- 商品图 -->
    <div class="product-img">
      <el-image
        v-if="product?.mainImage"
        :src="product.mainImage"
        fit="contain"
        style="width:100%;height:100%"
        :preview-src-list="[product.mainImage]"
        preview-teleported
      />
      <el-icon v-else :size="80" color="#ccc"><Picture /></el-icon>
    </div>

    <!-- 基本信息 -->
    <div class="info-section">
      <div class="community-bar">
        <el-icon><Location /></el-icon>
        <span class="community-label">当前社区：</span>
        <span class="community-name">{{ currentCommunityName }}</span>
        <el-button size="small" link type="primary" @click="switchCommunity">切换</el-button>
      </div>
      <h1 class="product-name">{{ product?.productName || product?.name }}
        <el-button
          class="fav-btn"
          :class="{ 'fav-btn--active': isFavorited }"
          circle
          size="small"
          :title="isFavorited ? '取消收藏' : '收藏'"
          @click="toggleFav"
        >
          <el-icon :size="16"><Star /></el-icon>
        </el-button>
      </h1>
      <div class="product-merchant" v-if="product?.merchantName">
        🏪 {{ product.merchantName }}
        <span class="product-category" v-if="product.categoryName">· {{ product.categoryName }}</span>
      </div>
      <div class="product-price">
        <AmountText :amount="currentSku?.price || product?.price" />
      </div>
      <div class="product-stock-row">
        <span class="stock-label">库存：</span>
        <el-tag v-if="stockStatus === 'out'" type="info" effect="light">已售罄</el-tag>
        <el-tag v-else-if="stockStatus === 'low'" type="warning" effect="light">紧张 · 仅剩 {{ currentSku?.availableQty }} 件</el-tag>
        <el-tag v-else type="success" effect="light">充足 · {{ currentSku?.availableQty }} 件</el-tag>
      </div>
    </div>

    <!-- SKU 规格选择 -->
    <div class="sku-section" v-if="skus.length > 0">
      <h4>规格选择</h4>
      <div class="sku-list">
        <div
          v-for="sku in skus"
          :key="sku.id"
          class="sku-item"
          :class="{ active: currentSku?.id === sku.id, disabled: !sku.availableQty }"
          @click="sku.availableQty && (currentSku = sku)"
        >
          <div class="sku-spec">{{ sku.spec }}</div>
          <small v-if="sku.availableQty" class="sku-qty">剩 {{ sku.availableQty }}</small>
          <small v-else class="sku-qty sold">已售罄</small>
        </div>
      </div>
    </div>

    <!-- 特色属性高亮（约束文档 4.5） -->
    <div class="attr-section" v-if="highlights.length">
      <h4>特色属性</h4>
      <div class="attr-list">
        <span v-for="h in highlights" :key="h.attrKey" class="highlight-tag">
          <el-icon><Star /></el-icon>
          {{ h.attrValue }}
        </span>
      </div>
    </div>

    <!-- 用户评价 -->
    <div class="review-section" v-if="currentSku">
      <h4>
        <el-icon><Star /></el-icon>
        用户评价
        <span class="review-summary">
          <span class="review-avg">{{ reviewAvg.toFixed(1) }}</span> 星
          <span class="review-count">({{ reviews.length }} 条)</span>
        </span>
      </h4>

      <!-- 双 tab：当前规格 / 全部规格 -->
      <el-tabs v-model="reviewScope" class="review-tabs" @tab-change="onReviewScopeChange">
        <el-tab-pane label="当前规格" name="sku" />
        <el-tab-pane label="全部规格" name="product" />
      </el-tabs>

      <!-- 星级占比条 -->
      <div class="review-dist" v-if="reviews.length">
        <div v-for="n in [5,4,3,2,1]" :key="n" class="dist-row">
          <span class="dist-label">{{ n }}星</span>
          <div class="dist-bar-bg">
            <div class="dist-bar-fill" :style="{ width: distPercent(n) + '%' }" />
          </div>
          <span class="dist-pct">{{ distPercent(n) }}%</span>
        </div>
      </div>

      <!-- 空评价状态 -->
      <div class="review-empty" v-else>
        📝 当前范围暂无评价，可切换 tab 查看全部规格评价~
      </div>

      <!-- 评价列表（显示 SKU 名区分不同规格的评价） -->
      <div class="review-list" v-if="reviews.length">
        <div v-for="r in reviews" :key="r.id" class="review-item">
          <div class="review-user">
            <el-icon><User /></el-icon>
            <span class="review-name">{{ r.isAnonymous ? '匿名用户' : ('用户' + r.userId) }}</span>
            <span class="review-sku" v-if="reviewScope === 'product' && r.skuId !== currentSku?.id">
              — 来自其他规格
            </span>
            <el-rate :model-value="r.rating" :max="5" disabled size="small" text-color="#ff9900" />
          </div>
          <div class="review-time">{{ formatTime(r.createTime) }}</div>
          <div class="review-content" v-if="r.content">{{ r.content }}</div>
          <div class="review-reply" v-if="r.replyContent">
            <span class="reply-tag">商家回复：</span>{{ r.replyContent }}
          </div>
        </div>
      </div>
    </div>

    <!-- 数量 + 加购 -->
    <div class="action-bar">
      <el-input-number
        v-model="quantity"
        :min="1"
        :max="Math.max(1, currentSku?.availableQty || 1)"
        :disabled="!currentSku?.availableQty"
        size="large"
      />
      <el-button
        type="warning"
        size="large"
        :disabled="!currentSku?.availableQty"
        @click="onAddCart"
      >加入购物车</el-button>
      <el-button
        type="primary"
        size="large"
        :disabled="!currentSku?.availableQty"
        @click="onBuyNow"
      >立即购买</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, Star, Location, User } from '@element-plus/icons-vue'
import AmountText from '@/components/AmountText.vue'
import { getProduct, addToCart, getActiveCommunities, getReviewsBySku, getReviewAvg, getReviewDist, getReviewsByProduct, getReviewAvgProduct, getReviewDistProduct, favoriteToggle, favoriteStatus } from '@/api'
import { useCartStore } from '@/store/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

// 社区强制选择状态
const communities = ref([])
const selectedCommunityId = ref(null)
const communityLocked = ref(false)
const loading = ref(false)

// 商品数据
const product = ref(null)
const skus = ref([])
const currentSku = ref(null)
const highlights = ref([])
const quantity = ref(1)

// 收藏状态
const isFavorited = ref(false)
async function checkFavStatus() {
  if (!product.value?.id) return
  try { isFavorited.value = await favoriteStatus(product.value.id) } catch {}
}
async function toggleFav() {
  if (!product.value?.id) return
  try {
    isFavorited.value = await favoriteToggle(product.value.id)
    ElMessage.success(isFavorited.value ? '已收藏 ❤️' : '已取消收藏')
  } catch (e) { ElMessage.warning('收藏操作失败') }
}

const currentCommunityName = computed(() => {
  const c = communities.value.find(x => x.id === selectedCommunityId.value)
  return c ? c.communityName : ''
})

const stockStatus = computed(() => {
  const qty = currentSku.value?.availableQty
  if (qty == null) return ''
  if (qty === 0) return 'out'
  // warnQty 来自 stock.warn_qty（后端 ProductService 已填），没填就硬编码 10
  const warn = currentSku.value?.warnQty ?? 10
  return qty <= warn ? 'low' : 'ok'
})

onMounted(async () => {
  try {
    communities.value = (await getActiveCommunities()) || []
  } catch (e) {
    ElMessage.error('社区列表加载失败')
  }
})

const onConfirmCommunity = async () => {
  if (!selectedCommunityId.value) return
  loading.value = true
  try {
    const res = await getProduct(route.params.id, selectedCommunityId.value)
    product.value = res.product || res
    skus.value = res.skus || []
    highlights.value = res.highlights || []
    if (skus.value.length) currentSku.value = skus.value[0]
    communityLocked.value = true
    checkFavStatus()
  } catch (e) {
    ElMessage.error(e.message || '商品加载失败')
  } finally {
    loading.value = false
  }
}

const switchCommunity = () => {
  communityLocked.value = false
  product.value = null
  skus.value = []
  currentSku.value = null
}

const onAddCart = async () => {
  if (!currentSku.value || !currentSku.value.availableQty) {
    ElMessage.warning('该规格已售罄')
    return
  }
  await addToCart({
    skuId: currentSku.value.id,
    quantity: quantity.value,
    communityId: selectedCommunityId.value,
  })
  cartStore.refresh()
  ElMessage.success('已加入购物车')
}

const onBuyNow = async () => {
  await onAddCart()
  router.push('/consumer/checkout')
}

// ===== 用户评价（公开接口，匿名可访问）=====
const reviews = ref([])
const reviewAvg = ref(0)
const reviewDist = ref({})
const reviewScope = ref('sku')   // 'sku' 当前规格 / 'product' 全部规格

const loadReviews = async () => {
  try {
    if (reviewScope.value === 'sku') {
      const skuId = currentSku.value?.id
      if (!skuId) return
      const [list, avg, dist] = await Promise.all([
        getReviewsBySku(skuId),
        getReviewAvg(skuId),
        getReviewDist(skuId),
      ])
      reviews.value = list || []
      reviewAvg.value = Number(avg) || 0
      reviewDist.value = dist || {}
    } else {
      const pid = product.value?.id
      if (!pid) return
      const [list, avg, dist] = await Promise.all([
        getReviewsByProduct(pid),
        getReviewAvgProduct(pid),
        getReviewDistProduct(pid),
      ])
      reviews.value = list || []
      reviewAvg.value = Number(avg) || 0
      reviewDist.value = dist || {}
    }
  } catch (e) {
    console.error('[Product.loadReviews] scope=', reviewScope.value, 'error=', e?.response?.status || e?.message || e)
  }
}

// 切换 tab 时刷新
const onReviewScopeChange = () => loadReviews()

// 切换规格时按当前 scope 刷新
watch(() => currentSku.value?.id, () => { loadReviews() })

// 切换商品详情（productId 变化）也刷新
watch(() => product.value?.id, () => { loadReviews() })

const distPercent = (n) => {
  const total = reviews.value.length || 1
  const cnt = Number(reviewDist.value?.[n]) || 0
  return Math.round((cnt / total) * 100)
}

const formatTime = (iso) => {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('zh-CN')
}
</script>

<style scoped lang="scss">
.product-detail { padding-bottom: 80px; }

// ===== 强制选社区弹层 =====
.community-gate {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(135deg, #fdf6ec 0%, #fff 100%);
}
.gate-card {
  width: 100%;
  max-width: 420px;
  padding: 32px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(230, 162, 60, 0.12);
  text-align: center;
  .gate-icon { font-size: 48px; margin-bottom: 8px; }
  h3 { margin: 0 0 8px; font-size: 18px; color: #303133; }
  .gate-tip { margin: 0 0 20px; font-size: 13px; color: #909399; }
}

// ===== 商品图 =====
.product-img {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff !important;
}

// ===== 基本信息 =====
.info-section {
  padding: 12px;
  background: #fff;
  margin-top: 8px;
}
.community-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
  .community-label { color: #909399; }
  .community-name { color: #303133; font-weight: 500; }
}
.product-name { margin: 0; font-size: 18px; color: #303133; display: flex; align-items: center; gap: 10px; }
.fav-btn {
  width: 28px; height: 28px; border: 1px solid #dcdfe6;
  background: #fff; color: #909399;
}
.fav-btn:hover { color: #f56c6c; border-color: #f56c6c; }
.fav-btn--active { color: #f56c6c; border-color: #f56c6c; background: #fff0f0; }
.product-merchant {
  margin-top: 6px;
  font-size: 12px;
  color: #E6A23C;
  .product-category { color: #909399; }
}
.product-price {
  margin-top: 8px;
  .amount-text { font-size: 24px; color: #F56C6C; }
}
.product-stock-row {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
  .stock-label { color: #909399; margin-right: 4px; }
}

// ===== SKU 规格 =====
.sku-section, .attr-section {
  background: #fff;
  margin-top: 10px;
  padding: 12px;
  h4 { margin: 0 0 10px; font-size: 14px; color: #303133; }
}
.sku-list { display: flex; flex-wrap: wrap; gap: 8px; }
.sku-item {
  padding: 8px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;

  &.active {
    border-color: #E6A23C;
    color: #E6A23C;
    background: #fdf6ec;
  }
  &.disabled {
    opacity: 0.4;
    cursor: not-allowed;
    text-decoration: line-through;
  }
  .sku-spec { font-weight: 500; }
  .sku-qty {
    display: block;
    font-size: 11px;
    color: #909399;
    margin-top: 2px;
    &.sold { color: #c0c4cc; text-decoration: line-through; }
  }
}

// ===== 特色属性 =====
.attr-list { display: flex; flex-wrap: wrap; gap: 6px; }
.highlight-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  background: #fdf6ec;
  color: #E6A23C;
  border-radius: 12px;
  font-size: 12px;
}

// ===== 用户评价 =====
.review-section {
  background: #fff;
  margin-top: 10px;
  padding: 12px;

  h4 {
    margin: 0 0 12px;
    font-size: 14px;
    color: #303133;
    display: flex;
    align-items: center;
    gap: 4px;

    .review-summary {
      margin-left: auto;
      font-size: 13px;
      color: #606266;
      display: flex;
      align-items: baseline;
      gap: 4px;

      .review-avg {
        font-size: 18px;
        font-weight: 600;
        color: #ff9900;
      }
      .review-count { font-size: 12px; color: #909399; }
    }
  }

  .review-tabs {
    margin-bottom: 10px;
    :deep(.el-tabs__item) { font-size: 13px; }
  }
}
.review-dist { margin-bottom: 12px; }
.dist-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
  font-size: 12px;
  color: #606266;

  .dist-label { width: 28px; color: #909399; }
  .dist-bar-bg {
    flex: 1;
    height: 6px;
    background: #f0f2f5;
    border-radius: 3px;
    overflow: hidden;
  }
  .dist-bar-fill {
    height: 100%;
    background: linear-gradient(90deg, #ff9900, #ffb84d);
    border-radius: 3px;
    transition: width 0.3s;
  }
  .dist-pct { width: 32px; text-align: right; color: #909399; }
}
.review-empty {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: #909399;
  background: #fafafa;
  border-radius: 8px;
}
.review-list { margin-top: 8px; }
.review-item {
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;

  &:last-child { border-bottom: none; }
  .review-user {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #303133;

    .review-name { font-weight: 500; }
    .review-sku {
      font-size: 11px;
      color: #909399;
      background: #f5f7fa;
      padding: 1px 6px;
      border-radius: 8px;
    }
    :deep(.el-rate) { margin-left: auto; }
  }
  .review-time {
    font-size: 11px;
    color: #c0c4cc;
    margin-top: 2px;
  }
  .review-content {
    font-size: 13px;
    color: #606266;
    margin-top: 6px;
    line-height: 1.6;
  }
  .review-reply {
    margin-top: 6px;
    padding: 6px 10px;
    background: #f0f9eb;
    border-radius: 6px;
    font-size: 12px;
    color: #67c23a;

    .reply-tag { color: #909399; }
  }
}

// ===== 底部操作栏 =====
.action-bar {
  position: fixed;
  bottom: 64px;
  left: 0;
  right: 0;
  max-width: 750px;
  margin: 0 auto;
  background: #fff;
  padding: 10px 16px;
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid #eee;
}
</style>