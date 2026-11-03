<!--
  金额组件：约束文档 15
  - 所有金额展示统一格式，保留两位小数
  - 右对齐
-->
<template>
  <span class="amount-text">
    <small class="symbol">¥</small>
    <span class="value">{{ formatted }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  amount: { type: [Number, String], required: true },
  // 大金额用粗体
  strong: { type: Boolean, default: false }
})

const formatted = computed(() => {
  const num = Number(props.amount) || 0
  return num.toFixed(2)
})
</script>

<style scoped>
.amount-text {
  font-family: 'Consolas', 'Monaco', monospace;
  text-align: right;
  display: inline-block;

  .symbol {
    font-size: 12px;
    color: #999;
    margin-right: 2px;
  }

  .value {
    font-weight: v-bind(strong ? 700 : 500);
  }
}
</style>
