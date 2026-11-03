<!--
  管理端布局 v2 — Deep Console 控制台风格
  - 侧边栏：深岩灰 #0F1923 → #1A2938 渐变，高对比度
  - 顶栏：白色磨砂 + 管理员徽章 + 用户胶囊
  - 菜单激活态左侧亮蓝指示条
-->
<template>
  <el-container class="admin-layout">
    <el-aside width="240px" class="admin-aside">
      <div class="logo">
        <div class="logo-mark">
          <el-icon :size="20"><Setting /></el-icon>
        </div>
        <div class="logo-text">
          <span class="logo-title">管理控制台</span>
          <span class="logo-sub">Admin Console</span>
        </div>
      </div>

      <el-scrollbar class="aside-scroll">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="transparent"
          text-color="#8b95a1"
          active-text-color="#fff"
        >
          <el-menu-item index="/admin/dashboard">
            <el-icon><Monitor /></el-icon>
            <span>全局概览</span>
          </el-menu-item>

          <el-sub-menu index="user">
            <template #title>
              <el-icon><User /></el-icon><span>用户与权限</span>
            </template>
            <el-menu-item index="/admin/users">用户管理</el-menu-item>
            <el-menu-item index="/admin/roles">角色权限</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="/admin/products">
            <el-icon><Goods /></el-icon>
            <span>商品审核</span>
          </el-menu-item>
          <el-menu-item index="/admin/orders">
            <el-icon><Tickets /></el-icon>
            <span>全局订单</span>
          </el-menu-item>
          <el-menu-item index="/admin/aftersale">
            <el-icon><RefreshLeft /></el-icon>
            <span>售后监控</span>
          </el-menu-item>
          <el-menu-item index="/admin/logs">
            <el-icon><Document /></el-icon>
            <span>操作日志</span>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>

      <div class="aside-footer">
        <div class="system-status">
          <span class="status-dot"></span>
          系统运行正常
        </div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-breadcrumb separator="·">
            <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">管理端</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-tag type="danger" effect="dark" size="small" class="admin-badge" round>
            <el-icon :size="12"><Setting /></el-icon>
            管理员
          </el-tag>

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

      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
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
.admin-aside {
  background: linear-gradient(180deg, #0F1923 0%, #1A2938 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.12);

  .logo {
    height: 64px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    flex-shrink: 0;

    .logo-mark {
      width: 36px; height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, #304156, #409EFF);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
    }
    .logo-text { display: flex; flex-direction: column; line-height: 1.2; }
    .logo-title {
      color: #fff; font-size: 15px; font-weight: 600; letter-spacing: 0.5px;
    }
    .logo-sub {
      color: rgba(255, 255, 255, 0.3); font-size: 10px; letter-spacing: 1px;
    }
  }

  .aside-scroll {
    flex: 1;
    padding: 8px 0;

    :deep(.el-scrollbar__bar.is-vertical) { width: 4px; background: transparent; }
    :deep(.el-scrollbar__thumb) { background: rgba(255, 255, 255, 0.12); border-radius: 2px; }
  }

  .el-menu {
    border-right: none;
    padding: 4px 8px;

    .el-menu-item, .el-sub-menu__title {
      height: 44px;
      line-height: 44px;
      border-radius: 6px;
      margin: 2px 0;
      transition: all $duration-fast $ease-smooth;
    }

    .el-menu-item {
      &:hover {
        background: rgba(64, 158, 255, 0.1) !important;
        color: #c0d8f0 !important;
      }

      &.is-active {
        background: linear-gradient(90deg, rgba(64, 158, 255, 0.22), rgba(64, 158, 255, 0.05)) !important;
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
    }

    .el-sub-menu {
      .el-sub-menu__title {
        color: #8b95a1;

        &:hover { background: rgba(64, 158, 255, 0.1) !important; color: #c0d8f0 !important; }

        .el-icon { margin-right: 10px; font-size: 16px; }
      }

      .el-menu-item {
        padding-left: 50px !important;
        font-size: 13px;
      }
    }
  }

  .aside-footer {
    padding: 12px 20px;
    border-top: 1px solid rgba(255, 255, 255, 0.04);
    flex-shrink: 0;

    .system-status {
      display: flex; align-items: center; gap: 8px;
      color: rgba(255, 255, 255, 0.3);
      font-size: 11px; letter-spacing: 0.5px;
    }
    .status-dot {
      width: 7px; height: 7px; border-radius: 50%;
      background: #52C41A;
      box-shadow: 0 0 6px #52C41A;
      animation: pulse 2s ease-in-out infinite;
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.85); }
}

// ==========================================
// 顶栏
// ==========================================
.admin-header {
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
    display: flex; align-items: center;
    :deep(.el-breadcrumb__inner) { color: #606266; font-size: 13px; font-weight: 500; }
    :deep(.el-breadcrumb__separator) { color: #C0C4CC; margin: 0 8px; }
  }

  .header-right { display: flex; align-items: center; gap: 16px; }

  .admin-badge {
    display: flex; align-items: center; gap: 4px;
    font-weight: 600; letter-spacing: 0.5px;
  }

  .user-capsule {
    display: flex; align-items: center; gap: 8px;
    padding: 4px 12px 4px 4px;
    border-radius: 20px;
    cursor: pointer;
    transition: background $duration-fast;

    &:hover { background: #F5F7FA; }

    .avatar {
      background: linear-gradient(135deg, #304156, #409EFF);
      color: #fff; font-weight: 700; font-size: 13px;
    }
    .name { font-size: 14px; color: #303133; font-weight: 500; max-width: 100px; overflow: hidden; text-overflow: ellipsis; }
  }
}

// ==========================================
// 主内容
// ==========================================
.admin-main {
  background: #F0F2F5;
  padding: 20px;
  overflow-y: auto;
}
</style>
