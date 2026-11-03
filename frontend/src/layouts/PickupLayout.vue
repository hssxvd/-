<!--
  自提点核销员端 Layout v2 — Utility Cockpit 风格
  - 钢蓝主色 + 荧光警示，仓库场景下高对比度
  - 顶部：品牌区 + 当前自提点标识 + 登出
-->
<template>
  <div class="pickup-app">
    <header class="pickup-header">
      <div class="header-left">
        <div class="logo-mark">
          <span class="logo-icon">📦</span>
        </div>
        <div class="brand">
          <div class="title">{{ userStore.pickupPointName || userStore.username || '核销工作台' }}</div>
          <div class="subtitle" v-if="userStore.pickupPointId">
            <span class="point-badge">{{ userStore.pickupCommunityName || '社区' }}</span>
            <span class="point-addr">📍 {{ userStore.pickupPointAddress || '—' }}</span>
          </div>
          <div class="subtitle" v-else>自提点核销工作台</div>
        </div>
      </div>
      <button class="logout-btn" @click="handleLogout">
        <el-icon :size="16"><SwitchButton /></el-icon>
        <span>退出</span>
      </button>
    </header>

    <main class="pickup-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出核销工作台？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch { /* 取消 */ }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

.pickup-app {
  min-height: 100vh;
  background: $pickup-bg;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.pickup-header {
  background: linear-gradient(135deg, $pickup-primary 0%, $pickup-primary-light 100%);
  color: #fff;
  padding: 14px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 12px rgba(30, 58, 95, 0.25);
  position: sticky;
  top: 0;
  z-index: 100;

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .logo-mark {
    width: 42px;
    height: 42px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.15);
    display: flex;
    align-items: center;
    justify-content: center;
    backdrop-filter: blur(4px);
  }

  .logo-icon { font-size: 22px; }

  .brand {
    display: flex;
    flex-direction: column;
    gap: 2px;

    .title {
      font-size: 15px;
      font-weight: 600;
      letter-spacing: 0.5px;
    }

    .subtitle {
      font-size: 12px;
      opacity: 0.78;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .point-badge {
      background: rgba(255, 107, 53, 0.9);
      color: #fff;
      padding: 1px 7px;
      border-radius: 4px;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.5px;
    }

    .point-addr {
      opacity: 0.85;
      max-width: 260px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .logout-btn {
    display: flex;
    align-items: center;
    gap: 4px;
    background: rgba(255, 255, 255, 0.12);
    border: 1px solid rgba(255, 255, 255, 0.25);
    color: #fff;
    padding: 6px 12px;
    border-radius: 6px;
    font-size: 13px;
    cursor: pointer;
    transition: all $duration-fast $ease-smooth;

    &:hover {
      background: rgba(255, 255, 255, 0.22);
      border-color: rgba(255, 255, 255, 0.4);
    }

    &:active { transform: scale(0.97); }
  }
}

.pickup-main {
  padding: 20px;
  max-width: 820px;
  margin: 0 auto;
}
</style>
