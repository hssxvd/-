<!--
  商家端-个人中心（PC 风格，左侧头像+右侧资料）
-->
<template>
  <div class="merchant-profile">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>🧑 个人中心</span>
          <el-button type="danger" plain size="small" @click="onLogout">退出登录</el-button>
        </div>
      </template>

      <div class="profile-body">
        <!-- 左侧 -->
        <div class="side">
          <AvatarUploader :url="userStore.avatar" :fallback="userStore.avatarFallback" :size="100"
                          @changed="onAvatarChanged" />
          <div class="side-info">
            <div class="username">{{ userStore.displayName }}</div>
            <el-tag size="small" type="primary" effect="light">商家</el-tag>
            <div class="merchant-name">🏪 {{ userStore.merchantName || '--' }}</div>
            <div class="community">🏘️ {{ userStore.communityName || '--' }}</div>
            <div class="uid">用户 ID: {{ userStore.userId }}</div>
          </div>
          <el-divider />
          <el-button :icon="Lock" plain size="default" style="width:100%" @click="pwdDialog = true">
            修改密码
          </el-button>
        </div>

        <!-- 右侧 Tab -->
        <div class="main">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="📝 基本信息" name="basic">
              <el-form :model="basicForm" label-width="100px" size="default" class="basic-form">
                <el-form-item label="用户名">
                  <el-input :model-value="userStore.username" disabled />
                </el-form-item>
                <el-form-item label="昵称">
                  <el-input v-model="basicForm.nickname" placeholder="显示名称" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input v-model="basicForm.phone" placeholder="11 位手机号" />
                </el-form-item>
                <el-form-item label="所在社区">
                  <el-input :model-value="userStore.communityName" disabled />
                </el-form-item>
                <el-form-item label="商家名称">
                  <el-input :model-value="userStore.merchantName" disabled />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="saving" @click="saveBasic">保存修改</el-button>
                  <el-button @click="loadForm">还原</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="🏪 商家信息" name="merchant-info">
              <el-alert type="info" :closable="false" show-icon
                title="商家名称、商家编码、所在社区在注册时确定，不可修改。" />
              <el-descriptions :column="2" border class="merchant-desc" style="margin-top:16px">
                <el-descriptions-item label="商家名称">{{ userStore.merchantName }}</el-descriptions-item>
                <el-descriptions-item label="用户 ID">{{ userStore.userId }}</el-descriptions-item>
                <el-descriptions-item label="角色">商家 ROLE_MERCHANT</el-descriptions-item>
                <el-descriptions-item label="所在社区">{{ userStore.communityName }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-card>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="pwdDialog" title="修改登录密码" width="420px">
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="旧密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="pwdForm.confirm" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import AvatarUploader from '@/components/AvatarUploader.vue'
import { changeMyPassword } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('basic')
const basicForm = reactive({ nickname: '', phone: '' })
const saving = ref(false)
const pwdDialog = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

onMounted(() => { loadForm() })
function loadForm() {
  basicForm.nickname = userStore.nickname || ''
  basicForm.phone = userStore.phone || ''
}

async function onAvatarChanged(newUrl) { await userStore.updateAvatar(newUrl) }

async function saveBasic() {
  saving.value = true
  try {
    await userStore.updateBasic({ nickname: basicForm.nickname || null, phone: basicForm.phone || null })
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally { saving.value = false }
}

async function changePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) return ElMessage.warning('请填写完整')
  if (pwdForm.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwdForm.newPassword !== pwdForm.confirm) return ElMessage.warning('两次密码不一致')
  pwdLoading.value = true
  try {
    await changeMyPassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    pwdDialog.value = false
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
    await userStore.logout()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '修改失败')
  } finally { pwdLoading.value = false }
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    await userStore.logout()
    router.push('/login')
  } catch {}
}
</script>

<style scoped lang="scss">
.merchant-profile { padding: 16px; }

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 16px; font-weight: 600;
}

.profile-body {
  display: grid; grid-template-columns: 220px 1fr; gap: 28px;
}

.side {
  text-align: center; padding: 20px 14px;
  border: 1px dashed #e4e7ed; border-radius: 8px; height: fit-content;

  .side-info { margin-top: 14px; }
  .username { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 6px; }
  .merchant-name { font-size: 13px; color: #303133; margin-top: 10px; }
  .community { font-size: 12px; color: #909399; margin-top: 4px; }
  .uid { font-size: 11px; color: #c0c4cc; margin-top: 6px; font-family: Menlo; }
}

.main { min-width: 0; }
.basic-form { max-width: 500px; }
.merchant-desc :deep(.el-descriptions__label) { width: 120px; }
</style>
