<!--
  消费者端-下单确认（约束文档 5.2 订单状态机：PENDING_CONFIRM → PENDING_PAY）
  - 商品清单、自提点、备注、提交订单
-->
<template>
  <div class="consumer-checkout">
    <!-- 自提点选择（约束 28.13：按用户所属社区过滤；28.14：字段名严格对齐后端 DTO） -->
    <div class="section pickup-section">
      <div class="section-title">
        <el-icon><LocationFilled /></el-icon>
        自提点
        <span class="section-sub" v-if="pickupPoints.length">（{{ pickupPoints.length }} 个可选）</span>
      </div>
      <el-radio-group v-model="pickupPointId">
        <el-radio v-for="pp in pickupPoints" :key="pp.id" :value="pp.id" border class="pickup-radio">
          <div class="pickup-option">
            <div class="pickup-name">{{ pp.pointName }}</div>
          </div>
        </el-radio>
      </el-radio-group>
      <!-- 选中后展示详情面板 -->
      <div v-if="selectedPickup" class="pickup-detail">
        <div class="detail-row">
          <el-icon><LocationFilled /></el-icon>
          <span class="detail-label">地址：</span>
          <span>{{ selectedPickup.address }}</span>
        </div>
        <div class="detail-row">
          <el-icon><User /></el-icon>
          <span class="detail-label">联系人：</span>
          <span>{{ selectedPickup.contactName }}</span>
          <span v-if="selectedPickup.contactPhone" class="detail-phone">{{ selectedPickup.contactPhone }}</span>
        </div>
        <div class="detail-row">
          <el-icon><Clock /></el-icon>
          <span class="detail-label">营业时间：</span>
          <span>{{ selectedPickup.openTime }}</span>
        </div>
      </div>
      <div v-if="pickupPoints.length === 0" class="empty-tip">
        当前社区暂无自提点，将由社区统一代发
      </div>
    </div>

    <!-- 商品清单 -->
    <div class="section items-section">
      <div class="section-title">
        <el-icon><Goods /></el-icon>
        商品清单 ({{ checkoutItems.length }})
      </div>
      <div v-for="item in checkoutItems" :key="item.id" class="checkout-item">
        <el-image class="item-img" :src="item.image" fit="cover">
          <template #error><div class="img-ph">无图</div></template>
        </el-image>
        <div class="item-info">
          <div class="item-name">{{ item.productName || item.name }}</div>
          <div class="item-spec" v-if="item.skuName">{{ item.skuName }}</div>
          <div class="item-bottom">
            <AmountText :amount="item.priceSnapshot" />
            <span class="qty">x{{ item.quantity }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 备注 -->
    <div class="section remark-section">
      <div class="section-title">
        <el-icon><EditPen /></el-icon>
        备注
      </div>
      <el-input
        v-model="remark"
        type="textarea"
        :rows="2"
        maxlength="200"
        placeholder="选填：截单日/自提时间等"
      />
    </div>

    <!-- 底部提交栏 -->
    <div class="submit-bar">
      <div class="submit-info">
        <div class="amount-row">
          共 {{ totalCount }} 件商品，合计
          <AmountText :amount="totalAmount" strong size="large" />
        </div>
      </div>
      <!-- 约束 28.11: 后端允许 pickupPointId=null 下单，前端只校验商品非空 -->
      <el-button type="warning" size="large" :loading="submitting" :disabled="!checkoutItems.length" @click="onSubmit">
        提交订单
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LocationFilled, User, Clock, Goods, EditPen } from '@element-plus/icons-vue'
import AmountText from '@/components/AmountText.vue'
import { createOrder, getPickupPoints, getCart } from '@/api'
import { useUserStore } from '@/store/user'
import { useCartStore } from '@/store/cart'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const submitting = ref(false)
const pickupPointId = ref(null)
const remark = ref('')
const checkoutItems = ref([])
const pickupPoints = ref([])

// 约束 28.13: 选中的自提点详情（供 template 面板渲染）
const selectedPickup = computed(() =>
  pickupPoints.value.find(p => p.id === pickupPointId.value) || null
)

const totalAmount = computed(() =>
  checkoutItems.value.reduce((s, i) => s + Number(i.priceSnapshot) * i.quantity, 0)
)
const totalCount = computed(() =>
  checkoutItems.value.reduce((s, i) => s + Number(i.quantity), 0)
)

onMounted(async () => {
  // 约束 28.9: 每次进页面都从后端拉最新购物车，禁止信 sessionStorage 缓存
  try {
    const allCartItems = await getCart()
    // 只拿选中的商品（后端 selected=1 表示选中）
    checkoutItems.value = (allCartItems || []).filter(i => Number(i.selected) === 1 || i.selected === true)
    if (!checkoutItems.value.length) {
      ElMessage.warning('请先在购物车选择商品')
      router.push('/consumer/cart')
      return
    }
  } catch (e) {
    ElMessage.error('加载购物车失败')
    router.push('/consumer/cart')
    return
  }

  // 约束 28.13: 按用户所属社区查询自提点（不传 communityId 会返回全社区数据）
  try {
    const res = await getPickupPoints(userStore.communityId)
    pickupPoints.value = res || []
    if (pickupPoints.value.length) pickupPointId.value = pickupPoints.value[0].id
    else pickupPointId.value = null
  } catch {
    pickupPointId.value = null
  }
})

async function onSubmit() {
  if (!checkoutItems.value.length) {
    ElMessage.warning('没有商品')
    return
  }
  submitting.value = true
  try {
    // 【约束 §8 拆单】后端按 merchant_id 自动拆单，返回 List<Orders>
    const orders = await createOrder({
      communityId: 1,
      pickupPointId: pickupPointId.value,
      remark: remark.value,
      items: checkoutItems.value.map(i => ({ skuId: i.skuId, quantity: i.quantity }))
    })
    // 清理购物车勾选数据
    sessionStorage.removeItem('checkoutItems')
    sessionStorage.removeItem('checkoutTotal')
    cartStore.clear()
    const n = orders?.length ?? 1
    if (n > 1) {
      ElMessage.success(`下单成功！系统已拆成 ${n} 笔独立订单，请在 15 分钟内完成支付`)
    } else {
      ElMessage.success('下单成功，请在 15 分钟内完成支付')
    }
    // 跳转到支付页 —— 取第一笔订单；支付后会自动关联同一笔支付下的所有拆单
    const firstOrder = Array.isArray(orders) ? orders[0] : orders
    router.push(`/consumer/pay/${firstOrder?.orderNo || firstOrder?.id}`)
  } catch (e) {
    // request.js 已弹 ElMessage
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.consumer-checkout {
  padding: 12px;
  padding-bottom: 100px;
  min-height: 100vh;
}

.section {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 10px;
  }

  .empty-tip { font-size: 12px; color: #909399; }
}

.pickup-section .el-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pickup-section .section-sub {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}

.pickup-option {
  padding: 2px 4px;
}

.pickup-detail {
  margin-top: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  border-left: 3px solid #409eff;

  .detail-row {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    color: #606266;
    padding: 3px 0;

    .detail-label {
      color: #909399;
      min-width: 56px;
    }

    .detail-phone {
      margin-left: auto;
      color: #409eff;
      font-weight: 500;
    }
  }
}

.checkout-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;

  &:last-child { border-bottom: none; }

  .item-img {
    width: 80px; height: 80px;
    background: #f5f5f5;
    border-radius: 6px;
    flex-shrink: 0;
    overflow: hidden;
  }
  .img-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #ccc; font-size: 12px; }

  .item-info { flex: 1; }
  .item-name { font-size: 14px; color: #303133; line-height: 1.3; }
  .item-spec { font-size: 12px; color: #909399; margin-top: 4px; }

  .item-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 8px;

    .qty { font-size: 13px; color: #606266; }
  }
}

.submit-bar {
  position: fixed;
  bottom: 64px;
  left: 0;
  right: 0;
  max-width: 750px;
  margin: 0 auto;
  background: #fff;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #eee;

  .submit-info { .amount-row { font-size: 13px; color: #606266; } }
}
</style>
