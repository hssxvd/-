<!-- 购物车（P0+P1 优化版） -->
<template>
  <div class="consumer-cart">
    <!-- 空购物车引导 -->
    <el-empty v-if="!items.length" description="购物车还是空的，去挑点好东西吧">
      <el-button type="warning" @click="router.push('/consumer/category')">去逛逛</el-button>
    </el-empty>

    <!-- 购物车列表 -->
    <div v-else class="cart-wrap">
      <!-- 顶部全选 -->
      <div class="cart-header">
        <el-checkbox v-model="allChecked" @change="onCheckAll">全选</el-checkbox>
        <span class="count">共 {{ items.length }} 件商品</span>
        <el-button link type="danger" size="small" @click="clearAll">清空</el-button>
      </div>

      <!-- 商品卡片 -->
      <div
        v-for="item in items"
        :key="item.id"
        class="cart-item"
        :class="{ 'item-disabled': item.warnStatus === 'EMPTY' }"
      >
        <el-checkbox
          v-model="selectedMap[item.id]"
          :disabled="item.warnStatus === 'EMPTY'"
          @change="onSelectChange(item)"
        />
        <el-image class="item-img" :src="item.image" fit="cover">
          <template #error><div class="img-placeholder">无图</div></template>
        </el-image>
        <div class="item-info">
          <div class="item-top">
            <span class="item-name">{{ item.productName }}</span>
            <el-tag v-if="item.warnStatus === 'EMPTY'" type="danger" size="small" effect="plain">已售罄</el-tag>
            <el-tag v-else-if="item.warnStatus === 'WARN'" type="warning" size="small" effect="plain">库存紧张</el-tag>
          </div>
          <div class="item-spec">{{ item.skuName }}<template v-if="item.unit"> · {{ item.unit }}</template></div>
          <div v-if="item.merchantName" class="item-merchant">来自：{{ item.merchantName }}</div>
          <div class="item-bottom">
            <div class="price-block">
              <span class="price-now">¥{{ item.currentPrice ?? item.priceSnapshot }}</span>
              <span v-if="item.priceChanged" class="price-snap">¥{{ item.priceSnapshot }}</span>
            </div>
            <el-input-number
              v-model="item.quantity"
              :min="1"
              :max="Math.max(1, item.availableQty || 1)"
              size="small"
              :disabled="item.warnStatus === 'EMPTY'"
              @change="() => updateItemQty(item)"
            />
            <el-button link type="danger" @click="removeItem(item.id)">删除</el-button>
          </div>
          <!-- 库存提示 -->
          <div v-if="item.warnStatus === 'WARN'" class="stock-tip">仅余 {{ item.availableQty }} 件</div>
          <div v-else-if="item.quantity > item.availableQty && item.warnStatus !== 'EMPTY'" class="stock-tip warn">
            当前库存不足（可用 {{ item.availableQty }}）
          </div>
        </div>
      </div>

      <!-- 底部结算栏 -->
      <div class="checkout-bar">
        <div class="checkout-left">
          <el-checkbox v-model="allChecked" @change="onCheckAll">全选</el-checkbox>
          <div class="total-box">
            <span>合计</span>
            <span class="amount">¥{{ totalAmount }}</span>
          </div>
        </div>
        <el-button
          type="warning"
          size="large"
          :disabled="!selectedIds.length || hasEmptySelected"
          @click="goCheckout"
        >
          去结算 ({{ selectedIds.length }})
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/store/cart'
import { getCart, updateCartItemQty, updateCartItemSelected, removeCartItem, clearCart } from '@/api'

const cartStore = useCartStore()
const router = useRouter()
const items = ref([])
const selectedMap = reactive({})
const loading = ref(false)

onMounted(loadCart)

async function loadCart() {
  try {
    loading.value = true
    items.value = await getCart()
    items.value.forEach(i => {
      // 售罄商品强制取消选中
      selectedMap[i.id] = i.warnStatus === 'EMPTY' ? false : ((i.selected ?? 1) === 1)
    })
  } catch (err) {
    ElMessage.error(err?.message || '加载购物车失败')
  } finally {
    loading.value = false
  }
}

const selectedIds = computed(() =>
  items.value.filter(i => selectedMap[i.id]).map(i => i.id)
)

// 选中项里是否包含售罄商品（防止结算空）
const hasEmptySelected = computed(() =>
  items.value.some(i => selectedMap[i.id] && i.warnStatus === 'EMPTY')
)

// 合计：优先用 currentPrice（实时），priceSnapshot 划线仅参考
const totalAmount = computed(() => {
  return items.value.filter(i => selectedMap[i.id] && i.warnStatus !== 'EMPTY')
    .reduce((s, i) => {
      const p = Number(i.currentPrice ?? i.priceSnapshot)
      return s + p * i.quantity
    }, 0).toFixed(2)
})

const allChecked = computed({
  get: () => {
    const available = items.value.filter(i => i.warnStatus !== 'EMPTY')
    return available.length > 0 && available.every(i => selectedMap[i.id])
  },
  set: (v) => items.value.forEach(i => {
    // 售罄项保持取消
    if (i.warnStatus !== 'EMPTY') selectedMap[i.id] = v
  })
})

async function onCheckAll(v) {
  try {
    await updateCartItemSelected(0, v ? 1 : 0)
  } catch (err) {
    ElMessage.error(err?.message || '全选操作失败')
  }
}

function onSelectChange(item) {
  // 单项选中变化实时保存
  updateCartItemSelected(item.id, selectedMap[item.id] ? 1 : 0)
    .catch(err => {
      ElMessage.error(err?.message || '操作失败')
      selectedMap[item.id] = !selectedMap[item.id] // rollback
    })
}

async function updateItemQty(item) {
  try {
    await updateCartItemQty(item.id, item.quantity)
    cartStore.refresh()
  } catch (err) {
    ElMessage.error(err?.message || '数量修改失败')
    await loadCart() // 后端可能拒绝，重新拉取最新状态
  }
}

async function removeItem(id) {
  try {
    await ElMessageBox.confirm('确认删除该商品？', '提示', { type: 'warning' })
    await removeCartItem(id)
    cartStore.refresh()
    delete selectedMap[id]
    await loadCart()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '删除失败')
  }
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确认清空购物车？', '提示', { type: 'warning' })
    await clearCart()
    Object.keys(selectedMap).forEach(k => delete selectedMap[k])
    items.value = []
    cartStore.clear()
    ElMessage.success('购物车已清空')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '清空失败')
  }
}

async function goCheckout() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请选择要结算的商品')
    return
  }
  if (hasEmptySelected.value) {
    ElMessage.warning('请先移除售罄商品')
    return
  }

  // P1-6：结算前批量校验选中项库存（前端最后一道守卫）
  const selected = items.value.filter(i => selectedMap[i.id])
  const shortage = selected.filter(i => i.quantity > i.availableQty)
  if (shortage.length) {
    const msg = shortage.map(i => `${i.productName}（可用 ${i.availableQty}，需要 ${i.quantity}）`).join('；')
    try {
      await ElMessageBox.confirm(
        `以下商品库存不足：${msg}。是否移除缺货商品后继续？`,
        '库存不足',
        { type: 'warning', confirmButtonText: '移除缺货并继续', cancelButtonText: '取消' }
      )
      // 移除缺货项的选中 → 刷新
      shortage.forEach(i => { selectedMap[i.id] = false })
      await loadCart()
      ElMessage.info('缺货商品已排除，请重新确认')
      return
    } catch {
      return
    }
  }

  // 把选中的 cart item 信息存到 sessionStorage 供 Checkout 页读取
  const selectedItems = selected.map(i => ({
    cartId: i.id,
    skuId: i.skuId,
    quantity: i.quantity,
    price: Number(i.currentPrice ?? i.priceSnapshot),
    image: i.image,
    productName: i.productName,
    skuName: i.skuName,
    merchantName: i.merchantName,
  }))
  sessionStorage.setItem('checkoutItems', JSON.stringify(selectedItems))
  sessionStorage.setItem('checkoutTotal', totalAmount.value)
  router.push('/consumer/checkout')
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.consumer-cart {
  padding: 12px;
  padding-bottom: 120px;
  min-height: 100vh;
  background: $consumer-bg;
}

.cart-wrap { max-width: 750px; margin: 0 auto; }

.cart-header {
  background: $consumer-cream;
  border-radius: $radius-md $radius-md 0 0;
  padding: 12px 16px;
  display: flex; align-items: center; gap: 12px;
  border-bottom: 1px solid rgba($consumer-primary, 0.08);
}
.cart-header .count { flex: 1; font-size: 13px; color: $consumer-text-soft; }

.cart-item {
  background: $consumer-cream;
  padding: 12px 16px;
  display: flex; gap: 12px; align-items: flex-start;
  border-bottom: 1px solid rgba($consumer-primary, 0.06);
  transition: background $duration-fast;

  &:active { background: rgba($consumer-primary, 0.03); }
}
.cart-item:last-child { border-bottom: none; border-radius: 0 0 $radius-md $radius-md; }
.cart-item.item-disabled { opacity: 0.5; }

.cart-item .item-img {
  width: 84px; height: 84px; border-radius: $radius-sm;
  flex-shrink: 0;
  overflow: hidden;
  background: linear-gradient(135deg, #FDF6EC, #F5EDE4);
  display: block;
}
.cart-item .item-img .img-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: #FDF6EC; color: #D4C4B8; font-size: 12px;
}
.cart-item .item-info { flex: 1; min-width: 0; }

.item-top {
  display: flex; align-items: center; gap: 8px; margin-bottom: 2px;
}
.item-top .item-name {
  font-weight: 500; font-size: 14px; color: $consumer-text;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.item-spec { font-size: 12px; color: $consumer-text-soft; margin-bottom: 2px; }
.item-merchant { font-size: 11px; color: #B8ADA4; margin-bottom: 6px; }

.item-bottom {
  display: flex; align-items: center; gap: 10px; margin-top: 6px;
}
.item-bottom .price-block { flex: 1; display: flex; align-items: baseline; gap: 6px; }
.item-bottom .price-now {
  color: $consumer-primary-dark; font-weight: 700; font-size: 16px;
  font-family: 'Menlo', monospace;
}
.item-bottom .price-snap { color: #D4C4B8; font-size: 11px; text-decoration: line-through; }

.stock-tip {
  margin-top: 4px; font-size: 11px; color: $consumer-primary;
}
.stock-tip.warn { color: #F56C6C; }

.checkout-bar {
  position: fixed;
  bottom: 64px;
  left: 0;
  right: 0;
  max-width: 750px;
  margin: 0 auto;
  background: rgba(255, 253, 247, 0.98);
  backdrop-filter: blur(16px);
  padding: 12px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0));
  display: flex; align-items: center; gap: 12px;
  border-top: 1px solid rgba($consumer-primary, 0.08);
  box-shadow: 0 -4px 20px rgba($consumer-primary, 0.1);
  z-index: 10;
}
.checkout-bar .checkout-left { flex: 1; display: flex; align-items: center; gap: 16px; }
.checkout-bar .total-box { font-size: 13px; color: $consumer-text-soft; }
.checkout-bar .amount {
  color: $consumer-primary-dark; font-weight: 700; font-size: 20px; margin-left: 4px;
  font-family: 'Menlo', monospace;
}
</style>
