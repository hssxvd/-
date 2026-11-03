<!--
  二次确认组件：约束文档 15
  - 关键操作（取消订单、支付、售后、上下架）必须有二次确认
  - 风险文档 #1/#2：防抖 + 幂等
-->
<template>
  <el-button
    :type="type"
    :disabled="disabled"
    :loading="loading"
    @click="onClick"
  >
    <el-icon v-if="icon"><component :is="icon" /></el-icon>
    {{ text }}
  </el-button>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'

const props = defineProps({
  text: { type: String, required: true },
  type: { type: String, default: 'primary' },
  icon: { type: [Object, String], default: null },
  confirmTitle: { type: String, default: '确认操作' },
  confirmMessage: { type: String, default: '确定执行此操作？' },
  confirmType: { type: String, default: 'warning' },
  confirmButtonText: { type: String, default: '确定' },
  cancelButtonText: { type: String, default: '取消' },
  disabled: { type: Boolean, default: false },
  // 防抖冷却时间（毫秒），防止重复点击
  debounce: { type: Number, default: 1000 }
})

const emit = defineEmits(['confirm', 'cancel'])
const loading = ref(false)
let lastClick = 0

const onClick = async () => {
  // 防抖
  const now = Date.now()
  if (now - lastClick < props.debounce) return
  lastClick = now

  try {
    await ElMessageBox.confirm(props.confirmMessage, props.confirmTitle, {
      type: props.confirmType,
      confirmButtonText: props.confirmButtonText,
      cancelButtonText: props.cancelButtonText
    })
    loading.value = true
    try {
      await emit('confirm')
    } finally {
      loading.value = false
    }
  } catch {
    emit('cancel')
  }
}
</script>
