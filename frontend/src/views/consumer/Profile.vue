<!--
  消费者端-个人中心（约束文档 24.1 移动端优先）
  升级：头像可换 + 资料卡 + 修改密码 + 订单快捷入口
-->
<template>
  <div class="consumer-profile">
    <!-- 用户信息头 -->
    <div class="profile-header">
      <div class="header-bg"></div>
      <div class="user-card">
        <AvatarUploader :url="userStore.avatar" :fallback="userStore.avatarFallback" :size="72"
                        @changed="onAvatarChanged" />
        <div class="user-info">
          <div class="user-name">
            {{ userStore.displayName }}
            <el-tag size="small" type="warning" effect="light" class="role-tag">消费者</el-tag>
          </div>
          <div class="user-id">ID: {{ userStore.userId }}</div>
          <div class="user-community">🏘️ {{ userStore.communityName || '未绑定社区' }}</div>
        </div>
      </div>
    </div>

    <!-- 📝 个人资料卡（新增） -->
    <div class="profile-card">
      <div class="card-title">📝 个人资料</div>
      <el-form :model="basicForm" label-width="80px" size="default" @keyup.enter="saveBasic">
        <el-form-item label="昵称">
          <el-input v-model="basicForm.nickname" placeholder="留空则用用户名" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="basicForm.phone" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="userStore.username" disabled />
        </el-form-item>
        <el-form-item label="社区">
          <el-input :model-value="userStore.communityName" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" :loading="saving" @click="saveBasic">保存资料</el-button>
          <el-button size="small" @click="loadForm">还原</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 🔐 账号安全 -->
    <div class="profile-card">
      <div class="card-title">🔐 账号安全</div>
      <div class="security-row">
        <div class="security-left">
          <div class="sec-title">登录密码</div>
          <div class="sec-desc">定期更换密码可以保障账号安全</div>
        </div>
        <el-button type="primary" plain size="small" @click="pwdDialog = true">修改密码</el-button>
      </div>
    </div>

    <!-- 订单快捷入口 -->
    <div class="quick-section">
      <div class="section-header">
        <span class="section-title">我的订单</span>
        <span class="section-more" @click="router.push('/consumer/orders')">全部订单 ›</span>
      </div>
      <div class="order-shortcuts">
        <div class="shortcut-item" @click="router.push({ path: '/consumer/orders', query: { tab: 'PENDING_PAY' } })">
          <el-icon :size="28" color="#E6A23C"><Money /></el-icon>
          <span>待支付</span>
        </div>
        <div class="shortcut-item" @click="router.push({ path: '/consumer/orders', query: { tab: 'FULFILLING' } })">
          <el-icon :size="28" color="#409EFF"><Van /></el-icon>
          <span>备货中</span>
        </div>
        <div class="shortcut-item" @click="router.push({ path: '/consumer/orders', query: { tab: 'PENDING_PICKUP' } })">
          <el-icon :size="28" color="#67C23A"><Shop /></el-icon>
          <span>等你取货</span>
        </div>
        <div class="shortcut-item" @click="router.push({ path: '/consumer/orders', query: { tab: 'AFTERSALE' } })">
          <el-icon :size="28" color="#F56C6C"><RefreshLeft /></el-icon>
          <span>售后</span>
        </div>
      </div>
    </div>

    <!-- 功能菜单 -->
    <div class="menu-section">
      <div class="menu-item" @click="router.push('/consumer/groupbuy')">
        <el-icon><UserFilled /></el-icon>
        <span>邻里团购</span>
        <el-icon class="arrow"><ArrowRight /></el-icon>
      </div>
      <div class="menu-item" @click="router.push('/consumer/my-groupbuys')">
        <el-icon><User /></el-icon>
        <span>我的参团</span>
        <el-icon class="arrow"><ArrowRight /></el-icon>
      </div>
      <div class="menu-item" @click="router.push('/consumer/cart')">
        <el-icon><ShoppingCart /></el-icon>
        <span>购物车</span>
        <el-icon class="arrow"><ArrowRight /></el-icon>
      </div>
      <div class="menu-item">
        <el-icon><Star /></el-icon>
        <span>我的收藏</span>
        <el-icon class="arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 退出登录 -->
    <div class="logout-section">
      <el-button type="danger" plain size="large" class="logout-btn" @click="onLogout">退出登录</el-button>
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="pwdDialog" title="修改密码" width="420px">
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
import { Money, Van, Shop, RefreshLeft, ShoppingCart, Star, ArrowRight, User, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import AvatarUploader from '@/components/AvatarUploader.vue'
import { updateMyProfile, changeMyPassword } from '@/api'

const router = useRouter()
const userStore = useUserStore()

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

async function onAvatarChanged(newUrl) {
  await userStore.updateAvatar(newUrl)
}

async function saveBasic() {
  saving.value = true
  try {
    await userStore.updateBasic({
      nickname: basicForm.nickname || null,
      phone: basicForm.phone || null
    })
    ElMessage.success('资料已更新')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
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
  } finally {
    pwdLoading.value = false
  }
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
.consumer-profile { padding-bottom: 40px; }

.profile-header {
  position: relative;
  .header-bg { height: 140px; background: linear-gradient(135deg, #E6A23C, #F5C861); }
  .user-card {
    display: flex; align-items: center; gap: 14px;
    padding: 0 20px 20px; margin-top: -40px; position: relative;
    .user-info { min-width: 0; }
    .user-name { font-size: 18px; font-weight: 600; display: flex; align-items: center; gap: 8px; color: #303133; }
    .role-tag { border-radius: 10px; }
    .user-id { font-size: 12px; color: #909399; margin-top: 4px; }
    .user-community { font-size: 12px; color: #E6A23C; margin-top: 2px; }
  }
}

.profile-card {
  margin: 10px; background: #fff; border-radius: 10px; padding: 14px;
  .card-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 10px; }
  .security-row {
    display: flex; align-items: center; justify-content: space-between;
    padding: 6px 0;
    .sec-title { font-size: 14px; color: #303133; font-weight: 500; }
    .sec-desc { font-size: 12px; color: #909399; margin-top: 2px; }
  }
}

.quick-section {
  margin: 10px; background: #fff; border-radius: 10px; padding: 14px;
  .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
  .section-title { font-size: 14px; font-weight: 600; color: #303133; }
  .section-more { font-size: 12px; color: #909399; cursor: pointer; }
  .order-shortcuts {
    display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px;
    .shortcut-item { display: flex; flex-direction: column; align-items: center; gap: 6px; font-size: 12px; color: #606266; cursor: pointer; &:active { opacity: 0.6; } }
  }
}

.menu-section {
  margin: 10px; background: #fff; border-radius: 10px; overflow: hidden;
  .menu-item {
    display: flex; align-items: center; gap: 12px;
    padding: 14px 16px; font-size: 14px; color: #303133;
    border-bottom: 1px solid #f5f5f5; cursor: pointer;
    &:last-child { border-bottom: none; }
    .arrow { margin-left: auto; color: #c0c4cc; }
    &:active { background: #fafafa; }
  }
}

.logout-section { padding: 20px 16px; .logout-btn { width: 100%; } }
</style>
