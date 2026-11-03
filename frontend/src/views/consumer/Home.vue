<!--
  消费者端-首页 — Warm Market 风格
  轮播图 + 分类标签 + 商品瀑布流
-->
<template>
  <div class="consumer-home">
    <!-- 轮播图 -->
    <el-carousel height="170px" class="banner" indicator-position="outside">
      <el-carousel-item v-for="(banner, i) in banners" :key="i">
        <div class="banner-item" :style="{ background: banner.gradient }">
          <div class="banner-emoji">{{ banner.emoji }}</div>
          <div class="banner-text">
            <div class="banner-title">{{ banner.title }}</div>
            <div class="banner-sub">{{ banner.sub }}</div>
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 特色分类标签 -->
    <div class="featured-tags">
      <div
        v-for="tag in featuredTags"
        :key="tag.key"
        class="tag-card"
        :style="{ '--tag-bg': tag.bg }"
        @click="goCategory(tag.key)"
      >
        <div class="tag-emoji">{{ tag.emoji }}</div>
        <span class="tag-label">{{ tag.label }}</span>
      </div>
    </div>

    <!-- 热销商品 -->
    <div class="section-block">
      <div class="section-title">
        <h3><span class="title-emoji">🔥</span> 今日热销</h3>
        <router-link to="/consumer/category" class="more-link">
          查看更多
          <el-icon :size="12"><ArrowRight /></el-icon>
        </router-link>
      </div>
      <div v-loading="loading" class="product-grid">
        <div
          v-for="p in products"
          :key="p.id"
          class="product-card"
          @click="goDetail(p.id)"
        >
          <div class="product-img">
            <el-image
              v-if="p.mainImage"
              :src="p.mainImage"
              fit="cover"
              style="width:100%;height:100%"
            />
            <el-icon v-else :size="40" color="#E8D5C4"><Picture /></el-icon>
            <div v-if="p.warnStatus === 'EMPTY'" class="img-badge sold-out">售罄</div>
            <div v-else-if="p.warnStatus === 'WARN'" class="img-badge stock-warn">紧张</div>
          </div>
          <div class="product-info">
            <div class="product-name">{{ p.productName || p.name }}</div>
            <div class="product-merchant" v-if="p.merchantName">🏪 {{ p.merchantName }}</div>
            <div class="product-price-row">
              <AmountText :amount="p.price" />
              <el-button
                v-if="p.warnStatus !== 'EMPTY'"
                type="primary"
                size="small"
                class="mini-add-btn"
                @click.stop="quickAdd(p)"
              >+</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Picture, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AmountText from '@/components/AmountText.vue'
import { getProducts, addToCart as apiAddToCart } from '@/api'
import { useUserStore } from '@/store/user'
import { useCartStore } from '@/store/cart'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const loading = ref(false)
const products = ref([])

const banners = [
  { gradient: 'linear-gradient(135deg, #FF8C42 0%, #FFB27A 100%)', emoji: '🥬', title: '每日新鲜直达', sub: '产地直采 · 当日送达' },
  { gradient: 'linear-gradient(135deg, #52B788 0%, #95D5B2 100%)', emoji: '🍎', title: '社区优选水果', sub: '精选好果 · 皮薄汁多' },
  { gradient: 'linear-gradient(135deg, #E85D26 0%, #FF8C42 100%)', emoji: '🛒', title: '邻里团购价', sub: '团越多人 · 省越多钱' },
]

const featuredTags = [
  { key: 'fresh', label: '生鲜蔬果', emoji: '🥬', bg: 'linear-gradient(135deg, #FFF0E5, #FFE0CC)' },
  { key: 'daily', label: '日用百货', emoji: '🧴', bg: 'linear-gradient(135deg, #E8F4FF, #D0E8FF)' },
  { key: 'snack', label: '零食饮料', emoji: '🍪', bg: 'linear-gradient(135deg, #FFF5E5, #FFE5B0)' },
  { key: 'household', label: '家居清洁', emoji: '🧹', bg: 'linear-gradient(135deg, #E5F5EC, #C8E8D4)' },
]

onMounted(async () => {
  loading.value = true
  try {
    const communityId = userStore.communityId || 1
    const res = await getProducts({ pageNum: 1, pageSize: 20, communityId })
    const d = res.data || res
    // 兼容 {list, total} 或直接数组
    products.value = d.list || d.records || (Array.isArray(d) ? d : [])
  } catch (e) {
    console.warn('[Home] 商品加载失败:', e?.message)
  } finally {
    loading.value = false
  }
})

const goDetail = (id) => router.push(`/consumer/product/${id}`)
const goCategory = (tag) => router.push({ path: '/consumer/category', query: { tag } })

async function quickAdd(p) {
  try {
    const skuId = p.defaultSkuId || p.skus?.[0]?.id || p.id
    await apiAddToCart({ skuId, quantity: 1, communityId: userStore.communityId || 1 })
    cartStore.refresh()
    ElMessage.success('已加入购物车')
  } catch (e) {
    ElMessage.error(e.message || '加购失败')
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.consumer-home {
  padding: 12px;
  background: $consumer-bg;
}

.banner {
  border-radius: $radius-md;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: $shadow-consumer-card;

  .banner-item {
    height: 100%;
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 0 24px;
    color: #fff;
  }
  .banner-emoji { font-size: 52px; filter: drop-shadow(0 4px 8px rgba(0,0,0,0.15)); }
  .banner-text { flex: 1; }
  .banner-title { font-size: 20px; font-weight: 700; letter-spacing: 1px; }
  .banner-sub { font-size: 13px; opacity: 0.9; margin-top: 4px; letter-spacing: 0.5px; }

  :deep(.el-carousel__indicators--outside) { margin-top: 4px; }
  :deep(.el-carousel__indicator .el-carousel__button) {
    background: rgba($consumer-primary, 0.4);
    &.is-active { background: $consumer-primary; }
  }
}

.featured-tags {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 20px;

  .tag-card {
    background: var(--tag-bg);
    border-radius: $radius-md;
    padding: 14px 4px 10px;
    text-align: center;
    font-size: 12px;
    color: $consumer-text;
    display: flex;
    flex-direction: column;
    gap: 6px;
    cursor: pointer;
    transition: transform $duration-fast $ease-spring, box-shadow $duration-base;

    &:hover {
      transform: translateY(-3px);
      box-shadow: $shadow-consumer-card;
    }
    &:active { transform: translateY(-1px) scale(0.97); }
  }
  .tag-emoji { font-size: 26px; line-height: 1; }
  .tag-label { font-weight: 500; }
}

.section-block { margin-bottom: 20px; }

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;

  h3 {
    margin: 0; font-size: 17px; font-weight: 700;
    color: $consumer-text;
    display: flex; align-items: center; gap: 6px;
  }
  .title-emoji { font-size: 18px; }
  .more-link {
    color: $consumer-primary;
    font-size: 12px;
    text-decoration: none;
    display: flex; align-items: center; gap: 2px;
    font-weight: 500;
    transition: opacity $duration-fast;
    &:hover { opacity: 0.75; }
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.product-card {
  background: $consumer-cream;
  border-radius: $radius-md;
  overflow: hidden;
  cursor: pointer;
  transition: transform $duration-fast $ease-spring, box-shadow $duration-base;
  border: 1px solid rgba($consumer-primary, 0.06);

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-consumer-card;
  }
  &:active { transform: translateY(-2px) scale(0.98); }

  .product-img {
    height: 130px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #FDF6EC 0%, #F5EDE4 100%);
    position: relative;
    overflow: hidden;
  }

  .img-badge {
    position: absolute;
    top: 8px; left: 8px;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 600;
    color: #fff;
  }
  .img-badge.sold-out { background: #F56C6C; }
  .img-badge.stock-warn { background: $consumer-primary; }

  .product-info { padding: 10px 12px; }
  .product-name {
    font-size: 13px;
    color: $consumer-text;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }
  .product-merchant {
    font-size: 11px; color: $consumer-text-soft; margin-top: 2px;
  }
  .product-price-row {
    display: flex; justify-content: space-between; align-items: center;
    margin-top: 8px;
  }
  .mini-add-btn {
    width: 28px; height: 28px; padding: 0;
    border-radius: 50%;
    background: $consumer-primary;
    border-color: $consumer-primary;
    font-size: 16px; font-weight: 700;
    box-shadow: 0 2px 6px rgba($consumer-primary, 0.35);
    transition: all $duration-fast $ease-spring;

    &:hover {
      background: $consumer-primary-dark;
      border-color: $consumer-primary-dark;
      transform: scale(1.1);
      box-shadow: 0 4px 10px rgba($consumer-primary, 0.45);
    }
    &:active { transform: scale(0.9); }
  }
}
</style>
