<!--
  商家端布局 v2 — Professional Commerce 深蓝风格
  - 侧边栏：渐变深蓝 #001529 → #002140，带 logo 品牌区 + 图标激活态发光
  - 顶栏：白色磨砂玻璃 + 面包屑 + 右上角用户胶囊
  - 主内容：浅灰底 + 统一卡片圆角/阴影
-->
<template>
  <el-container class="merchant-layout">
    <!-- 侧边栏 -->
    <el-aside width="228px" class="merchant-aside">
      <div class="logo">
        <div class="logo-mark">
          <el-icon :size="22"><OfficeBuilding /></el-icon>
        </div>
        <div class="logo-text">
          <span class="logo-title">商家工作台</span>
          <span class="logo-sub">Merchant Console</span>
        </div>
      </div>

      <el-scrollbar class="aside-scroll">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="transparent"
          text-color="#a6adb4"
          active-text-color="#fff"
        >
          <el-menu-item index="/merchant/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/merchant/products">
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/merchant/stock">
            <el-icon><Box /></el-icon>
            <span>库存管理</span>
          </el-menu-item>
          <el-menu-item index="/merchant/supply">
            <el-icon><Van /></el-icon>
            <span>供应计划</span>
            <span class="menu-badge">★</span>
          </el-menu-item>
          <el-menu-item index="/merchant/orders">
            <el-icon><Tickets /></el-icon>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/merchant/fulfillment">
            <el-icon><Promotion /></el-icon>
            <span>发货备货</span>
          </el-menu-item>
          <el-menu-item index="/merchant/aftersale">
            <el-icon><RefreshLeft /></el-icon>
            <span>售后管理</span>
          </el-menu-item>
          <el-menu-item index="/merchant/reviews">
            <el-icon><ChatDotRound /></el-icon>
            <span>评价管理</span>
          </el-menu-item>
          <el-menu-item index="/merchant/analytics">
            <el-icon><TrendCharts /></el-icon>
            <span>经营分析</span>
            <span class="menu-badge">★</span>
          </el-menu-item>
          <el-menu-item index="/merchant/groupbuys">
            <el-icon><UserFilled /></el-icon>
            <span>团购管理</span>
            <span class="menu-badge">★</span>
          </el-menu-item>
          <el-menu-item index="/merchant/profile">
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>

      <!-- 底部版本 -->
      <div class="aside-footer">v2.0 · © GroupBuy</div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="merchant-header">
        <div class="header-left">
          <el-breadcrumb separator="·">
            <el-breadcrumb-item :to="{ path: '/merchant/dashboard' }">商家端</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-dropdown @command="onCommand" trigger="click">
            <div class="user-capsule">
              <el-avatar :size="30" class="avatar">{{ userStore.username?.charAt(0)?.toUpperCase() }}</el-avatar>
              <span class="name">{{ userStore.username }}</span>
              <el-icon :size="14"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="merchant-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { OfficeBuilding, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')

const onCommand = async (cmd) => {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录？', '提示', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning',
      })
      userStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch { /* 取消 */ }
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;

// ==========================================
// 侧边栏
// ==========================================
.merchant-aside {
  background: linear-gradient(180deg, #001529 0%, #002140 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);

  .logo {
    height: 64px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    flex-shrink: 0;

    .logo-mark {
      width: 36px; height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, #409EFF, #1677FF);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      box-shadow: 0 2px 8px rgba(22, 119, 255, 0.4);
    }
    .logo-text {
      display: flex;
      flex-direction: column;
      line-height: 1.2;
    }
    .logo-title {
      color: #fff;
      font-size: 15px;
      font-weight: 600;
      letter-spacing: 0.5px;
    }
    .logo-sub {
      color: rgba(255, 255, 255, 0.4);
      font-size: 10px;
      letter-spacing: 1px;
    }
  }

  .aside-scroll {
    flex: 1;
    padding: 8px 0;

    :deep(.el-scrollbar__bar.is-vertical) {
      width: 4px;
      background: transparent;
    }
    :deep(.el-scrollbar__thumb) {
      background: rgba(255, 255, 255, 0.15);
      border-radius: 2px;
    }
  }

  .el-menu {
    border-right: none;
    padding: 4px 8px;

    .el-menu-item {
      height: 44px;
      line-height: 44px;
      border-radius: 6px;
      margin: 2px 0;
      transition: all $duration-fast $ease-smooth;

      &:hover {
        background: rgba(64, 158, 255, 0.12) !important;
        color: #c6e2ff !important;
      }

      &.is-active {
        background: linear-gradient(90deg, rgba(64, 158, 255, 0.25), rgba(64, 158, 255, 0.08)) !important;
        color: #fff !important;
        font-weight: 600;
        position: relative;

        &::before {
          content: '';
          position: absolute;
          left: -8px;
          top: 8px;
          bottom: 8px;
          width: 3px;
          background: #409EFF;
          border-radius: 0 3px 3px 0;
          box-shadow: 0 0 6px rgba(64, 158, 255, 0.6);
        }
      }

      .el-icon { margin-right: 10px; font-size: 16px; }

      .menu-badge {
        margin-left: auto;
        font-size: 10px;
        color: #FFB020;
        font-weight: 700;
      }
    }
  }

  .aside-footer {
    padding: 12px 20px;
    color: rgba(255, 255, 255, 0.25);
    font-size: 11px;
    letter-spacing: 0.5px;
    border-top: 1px solid rgba(255, 255, 255, 0.04);
    flex-shrink: 0;
  }
}

// ==========================================
// 顶栏
// ==========================================
.merchant-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #E8ECF0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px !important;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);

  .header-left {
    display: flex;
    align-items: center;

    :deep(.el-breadcrumb__inner) { color: #606266; font-size: 13px; font-weight: 500; }
    :deep(.el-breadcrumb__separator) { color: #C0C4CC; margin: 0 8px; }
  }

  .user-capsule {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 12px 4px 4px;
    border-radius: 20px;
    cursor: pointer;
    transition: background $duration-fast;

    &:hover { background: #F5F7FA; }

    .avatar {
      background: linear-gradient(135deg, #409EFF, #1677FF);
      color: #fff;
      font-weight: 700;
      font-size: 13px;
    }
    .name { font-size: 14px; color: #303133; font-weight: 500; max-width: 100px; overflow: hidden; text-overflow: ellipsis; }
  }
}

// ==========================================
// 主内容
// ==========================================
.merchant-main {
  background: #F0F2F5;
  padding: 20px;
  overflow-y: auto;
}
</style>
