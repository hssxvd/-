<!--
  通用头像上传组件 — 三端 Profile 复用
  用法：
    <AvatarUploader :url="userStore.avatar" :fallback="userStore.avatarFallback" :size="80"
                    @changed="onAvatarChanged" />
  props: url (当前头像 URL), fallback (无图时显示的首字母), size (像素，默认 80)
  emits: changed(newUrl)  上传并绑定成功后触发
-->
<template>
  <div class="avatar-uploader" :style="{width: size+'px', height: size+'px'}">
    <el-avatar :size="size" :src="displayUrl" class="avatar">
      <span>{{ fallback }}</span>
    </el-avatar>
    <div class="upload-mask" @click="triggerFile">
      <el-icon><Camera /></el-icon>
      <span>{{ uploading ? '上传中' : '更换' }}</span>
    </div>
    <input ref="fileRef" type="file" class="hidden-file"
           accept="image/jpeg,image/png,image/gif,image/webp"
           @change="onFileChange" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { uploadAvatar } from '@/api'

const props = defineProps({
  url: { type: String, default: '' },
  fallback: { type: String, default: 'U' },
  size: { type: Number, default: 80 }
})
const emit = defineEmits(['changed'])

const fileRef = ref(null)
const uploading = ref(false)
const previewUrl = ref('')

const displayUrl = computed(() => previewUrl.value || props.url || '')

function triggerFile() {
  fileRef.value?.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片超过 2MB 限制')
    return
  }
  // 本地预览
  previewUrl.value = URL.createObjectURL(file)
  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    emit('changed', url)
    ElMessage.success('头像更新成功')
  } catch (err) {
    ElMessage.error(err?.message || '上传失败')
    previewUrl.value = '' // 还原
  } finally {
    uploading.value = false
    // 清空 input 以便重复选同一文件
    e.target.value = ''
  }
}
</script>

<style scoped lang="scss">
.avatar-uploader {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;

  .avatar {
    border: 3px solid #fff;
    box-shadow: 0 2px 8px rgba(0,0,0,0.12);
    background: #f0f0f0;
  }

  .upload-mask {
    position: absolute; inset: 0;
    background: rgba(0,0,0,0.45);
    border-radius: 50%;
    display: none;
    flex-direction: column;
    align-items: center; justify-content: center;
    gap: 2px;
    color: #fff; font-size: 11px;
  }

  &:hover .upload-mask { display: flex; }

  .hidden-file { display: none; }
}
</style>
