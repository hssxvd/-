<!-- 我的收藏 -->
<template>
  <div class="consumer-favorites">
    <div class="page-header">
      <h2>❤️ 我的收藏</h2>
      <span v-if="total" class="count">共 {{ total }} 件</span>
      <el-button v-if="total" link type="danger" size="small" @click="onClear">清空</el-button>
    </div>

    <el-empty v-if="!items.length && !loading" description="还没有收藏任何商品">
      <el-button type="warning" @click="$router.push('/consumer/category')">去逛逛</el-button>
    </el-empty>

    <div v-else class="fav-wrap" v-loading="loading">
      <div
        v-for="item in items"
        :key="item.favoriteId"
        class="fav-card"
        @click="$router.push(`/consumer/product/${item.productId}`)"
      >
        <el-image class="fav-img" :src="item.mainImage" fit="cover">
          <template #error><div class="img-placeholder">无图</div></template>
        </el-image>
        <div class="fav-info">
          <div class="fav-name">{{ item.productName }}</div>
          <div v-if="item.unit" class="fav-unit">{{ item.unit }}</div>
          <div v-if="item.merchantName" class="fav-merchant">来自：{{ item.merchantName }}</div>
          <div class="fav-actions" @click.stop>
            <el-button size="small" type="primary" @click="onAddCart(item)">加入购物车</el-button>
            <el-button size="small" type="danger" text @click="onRemove(item)">移除</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="pagination-wrap" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[12, 24, 48]"
        background
        layout="prev, pager, next, total"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { favoriteList, favoriteRemove, favoriteClear, addToCart } from '@/api'
import { useCartStore } from '@/store/cart'

const router = useRouter()
const cartStore = useCartStore()
const loading = ref(false)
const items = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)

async function load() {
  loading.value = true
  try {
    const r = await favoriteList({ pageNum: pageNum.value, pageSize: pageSize.value })
    items.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}

async function onRemove(item) {
  try {
    await favoriteRemove(item.favoriteId)
    ElMessage.success('已移除收藏')
    load()
  } catch {}
}

async function onClear() {
  try {
    await ElMessageBox.confirm('确定清空所有收藏吗？', '提示', { type: 'warning' })
    await favoriteClear()
    ElMessage.success('已清空')
    pageNum.value = 1
    load()
  } catch {}
}

async function onAddCart(item) {
  try {
    await addToCart({
      skuId: item.defaultSkuId || item.productId,
      quantity: 1,
      communityId: null
    })
    cartStore.refresh()
    ElMessage.success('已加入购物车')
  } catch {
    ElMessage.warning('加入购物车失败，请先选社区')
  }
}

onMounted(load)
</script>

<style scoped>
.consumer-favorites {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px;
}
.page-header h2 { font-size: 18px; margin: 0; }
.page-header .count { color: #909399; font-size: 13px; }

.fav-wrap {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.fav-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  cursor: pointer;
  transition: transform 0.15s ease;
  display: flex;
  flex-direction: column;
}
.fav-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }

.fav-img {
  width: 100%;
  height: 140px;
  background: #f5f7fa;
}
.img-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  color: #c0c4cc; font-size: 12px;
}

.fav-info {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}
.fav-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 36px;
}
.fav-unit { font-size: 12px; color: #909399; }
.fav-merchant {
  font-size: 12px;
  color: #e6a23c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fav-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
  padding-top: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 16px;
}
</style>
