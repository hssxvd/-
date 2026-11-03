<template>
  <div class="consumer-category">
    <div class="filter-panel">
      <!-- 分类 -->
      <div class="filter-row" v-if="categories.length">
        <span class="filter-label">分类:</span>
        <div class="filter-options">
          <span class="filter-chip" :class="{ active: !query.categoryId }" @click="query.categoryId = null">全部</span>
          <span v-for="c in categories" :key="c.id" class="filter-chip"
            :class="{ active: query.categoryId === c.id }"
            @click="query.categoryId = c.id">{{ c.name }}</span>
        </div>
      </div>

    </div>

    <div class="toolbar">
      <div class="sort-group">
        <span class="sort-chip" :class="{ active: query.sort === 'default' }" @click="setSort('default')">综合</span>
        <span class="sort-chip" :class="{ active: query.sort === 'sales' }" @click="setSort('sales')">销量↓</span>
        <span class="sort-chip" :class="{ active: query.sort === 'price_asc' }" @click="setSort('price_asc')">价格↑</span>
        <span class="sort-chip" :class="{ active: query.sort === 'price_desc' }" @click="setSort('price_desc')">价格↓</span>
      </div>
      <div class="result-count" v-if="!loading">共 {{ total }} 件商品</div>
    </div>

    <div v-loading="loading" class="product-grid">
      <div v-for="p in products" :key="p.id" class="product-card" @click="goDetail(p.id)">
        <div class="product-img">
          <el-image v-if="p.mainImage" :src="p.mainImage" fit="cover" style="width:100%;height:100%" />
          <el-icon v-else :size="40" color="#E8D5C4"><Picture /></el-icon>
          <div v-if="p.warnStatus === 'EMPTY'" class="img-badge sold-out">售罄</div>
          <div v-else-if="p.warnStatus === 'WARN'" class="img-badge stock-warn">紧张</div>
        </div>
        <div class="product-info">
          <div class="product-name">{{ p.productName || p.name }}</div>
          <div class="product-merchant" v-if="p.merchantName">🏪 {{ p.merchantName }}</div>
          <div class="product-price-row">
            <span class="price-text">¥{{ (p.price || 0).toFixed(2) }}</span>
            <el-button v-if="p.warnStatus !== 'EMPTY'" type="primary" size="small" round
              @click.stop="handleAddCart(p)">加入购物车</el-button>
          </div>
        </div>
      </div>
      <div v-if="!loading && !products.length" class="empty">
        <el-icon :size="48" color="#ccc"><Box /></el-icon>
        <p>没有找到符合条件的商品</p>
      </div>
    </div>

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      background
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, Box } from '@element-plus/icons-vue'
import { getCategories, getProducts, addToCart } from '@/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const categories = ref([])
const products = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)

const query = reactive({
  categoryId: null,
  sort: 'default',
  keyword: ''
})

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (query.categoryId) params.categoryId = query.categoryId
    if (query.sort) params.sort = query.sort
    if (query.keyword) params.keyword = query.keyword
    const res = await getProducts(params)
    const d = res.data || res
    products.value = d?.list || d?.records || (Array.isArray(d) ? d : [])
    total.value = d?.total || products.value.length
  } finally { loading.value = false }
}

function flattenTree(tree, out) {
  if (!tree) return out
  for (const c of tree) {
    out.push({ id: c.id, name: c.categoryName || c.name })
    if (c.children?.length) flattenTree(c.children, out)
  }
  return out
}

async function loadCategories() {
  const tree = await getCategories()
  categories.value = flattenTree(tree, [])
}

function setSort(s) { query.sort = s; pageNum.value = 1; load() }
function goDetail(id) { router.push('/consumer/product/' + id) }

async function handleAddCart(p) {
  try { await addToCart({ productId: p.id, quantity: 1 }); ElMessage.success('已加入购物车') }
  catch (e) { /* handled by api layer */ }
}

onMounted(async () => {
  if (route.query.keyword) query.keyword = route.query.keyword
  if (route.query.categoryId) query.categoryId = Number(route.query.categoryId)
  await Promise.all([loadCategories(), load()])
})

watch(
  () => query.categoryId,
  () => { pageNum.value = 1; load() }
)
</script>

<style scoped lang="scss">
.consumer-category { padding: 12px 16px 32px; background: #fafafa; min-height: 100%; }
.filter-panel { background: #fff; border-radius: 10px; padding: 16px; margin-bottom: 10px; box-shadow: 0 1px 4px rgba(0,0,0,.05); }
.filter-row { display: flex; align-items: flex-start; gap: 10px; padding: 6px 0; border-bottom: 1px solid #f0f0f0; }
.filter-row:last-child { border-bottom: none; }
.filter-label { flex-shrink: 0; font-size: 13px; color: #888; padding-top: 4px; width: 70px; }
.filter-options { display: flex; flex-wrap: wrap; gap: 6px; }
.filter-chip { font-size: 12px; padding: 3px 10px; border: 1px solid #e8e8e8; border-radius: 12px; cursor: pointer; color: #666; background: #fafafa; transition: all .15s; }
.filter-chip.active { background: #ff6a3a; color: #fff; border-color: #ff6a3a; }
.filter-chip:hover:not(.active) { border-color: #ff6a3a; color: #ff6a3a; }
.toolbar { display: flex; justify-content: space-between; align-items: center; padding: 10px 4px; }
.sort-group { display: flex; gap: 14px; }
.sort-chip { font-size: 13px; color: #666; cursor: pointer; border-bottom: 2px solid transparent; padding-bottom: 2px; }
.sort-chip.active { color: #ff6a3a; border-color: #ff6a3a; font-weight: 600; }
.result-count { font-size: 12px; color: #999; }
.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 10px; }
.product-card { background: #fff; border-radius: 10px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,.05); cursor: pointer; }
.product-img { width: 100%; padding-bottom: 100%; position: relative; background: #f5f5f5; }
.product-img .el-image, .product-img .el-icon { position: absolute; inset: 0; }
.product-info { padding: 8px 10px; }
.product-name { font-size: 13px; font-weight: 500; line-height: 1.3; height: 34px; overflow: hidden; color: #333; }
.product-merchant { font-size: 11px; color: #999; margin-top: 2px; }
.product-price-row { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
.price-text { font-size: 15px; font-weight: 700; color: #ff6a3a; }
.img-badge { position: absolute; top: 6px; left: 6px; font-size: 11px; padding: 1px 6px; border-radius: 3px; color: #fff; }
.img-badge.sold-out { background: rgba(0,0,0,.55); }
.img-badge.stock-warn { background: #ff9a00; }
.empty { text-align: center; padding: 60px 0; color: #999; }
.pager { margin: 16px auto; display: flex; justify-content: center; }
</style>
