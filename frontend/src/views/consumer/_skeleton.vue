<!-- 下单确认 → 支付 → 订单详情 骨架页 -->
<template>
  <div class="skeleton-page">
    <el-result :icon="icon" :title="title" :sub-title="subTitle">
      <template #extra>
        <el-button v-for="(btn, idx) in buttons" :key="idx" :type="btn.type" @click="btn.action">
          {{ btn.text }}
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  icon: { type: String, default: 'info' },
  title: { type: String, default: '页面占位' },
  subTitle: { type: String, default: '核心功能骨架，待开发' }
})

const router = useRouter()

const buttons = computed(() => [
  { text: '返回首页', type: 'primary', action: () => router.push('/consumer/home') },
  { text: '查看订单', type: 'default', action: () => router.push('/consumer/orders') }
])
</script>

<style scoped>
.skeleton-page { padding: 40px 20px; }
</style>
