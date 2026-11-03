<!--
  消费者端布局 v2 — Warm Market "街角鲜集" 风格
  - 顶部：暖杏橙渐变头部 + 圆角搜索框 + 用户标识
  - 底部 Tab：iOS 风格胶囊 + iPhone 安全区适配 + 悬浮动画
  - 背景：暖米色 #FAF6F1 替代冷灰，营造社区集市感
-->
<template>
  <div class="consumer-layout">
    <!-- 顶部 -->
    <header class="consumer-header">
      <div class="header-bar">
        <div class="header-user">
          <div class="avatar-wrap">
            <el-avatar :size="34" class="user-avatar">{{ userStore.username?.charAt(0)?.toUpperCase() || 'G' }}</el-avatar>
          </div>
          <span class="user-name">{{ userStore.username || '逛集人' }}</span>
        </div>
        <div class="search-wrap">
          <el-input
            v-model="keyword"
            placeholder="搜商品、搜店铺..."
            :prefix-icon="Search"
            clearable
            size="large"
            @keyup.enter="onSearch"
          />
        </div>
        <el-dropdown @command="onCommand" trigger="click">
          <div class="more-btn" role="button" tabindex="0">
            <el-icon :size="22"><MoreFilled /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" icon="SwitchButton">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="consumer-main">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>

    <!-- 底部 Tab -->
    <nav class="consumer-tabbar safe-bottom">
      <router-link to="/consumer/home" class="tab-item" active-class="active">
        <div class="tab-icon-wrap"><el-icon><HomeFilled /></el-icon></div>
        <span>首页</span>
      </router-link>
      <router-link to="/consumer/groupbuy" class="tab-item groupbuy-tab" active-class="active">
        <div class="tab-icon-wrap">
          <!-- 自定义双头像 SVG：两个人头交叠 -->
          <svg class="tab-double-avatar" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <!-- 后面的人头（稍小，半透明偏右后） -->
            <circle cx="9" cy="8" r="3.5" fill="currentColor" opacity="0.55" />
            <path d="M4.5 19.5c0-2.8 2.2-5 4.5-5s4.5 2.2 4.5 5" fill="currentColor" opacity="0.55" />
            <!-- 前面的人头（大，实色） -->
            <circle cx="14" cy="9" r="4" fill="currentColor" />
            <path d="M7.5 21c0-3.3 2.9-6 6.5-6s6.5 2.7 6.5 6" fill="currentColor" />
          </svg>
        </div>
        <span>团购</span>
      </router-link>
      <router-link to="/consumer/category" class="tab-item" active-class="active">
        <div class="tab-icon-wrap"><el-icon><Grid /></el-icon></div>
        <span>分类</span>
      </router-link>
      <router-link to="/consumer/cart" class="tab-item" active-class="active">
        <el-badge :value="cartCount" :max="99" :hidden="!cartCount" class="cart-badge">
          <div class="tab-icon-wrap"><el-icon><ShoppingCart /></el-icon></div>
        </el-badge>
        <span>购物车</span>
      </router-link>
      <router-link to="/consumer/orders" class="tab-item" active-class="active">
        <div class="tab-icon-wrap"><el-icon><Tickets /></el-icon></div>
        <span>订单</span>
      </router-link>
      <router-link to="/consumer/favorites" class="tab-item" active-class="active">
        <div class="tab-icon-wrap"><el-icon><Star /></el-icon></div>
        <span>收藏</span>
      </router-link>
      <router-link to="/consumer/profile" class="tab-item" active-class="active">
        <div class="tab-icon-wrap"><el-icon><User /></el-icon></div>
        <span>我的</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Search, HomeFilled, Grid, ShoppingCart, Tickets, User, MoreFilled, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useCartStore } from '@/store/cart'

const keyword = ref('')
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()
const cartCount = computed(() => cartStore.cartCount)

onMounted(() => { cartStore.refresh() })
watch(() => userStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) cartStore.refresh(true)
  else cartStore.clear()
})
watch(() => router.currentRoute.value.path, () => { cartStore.refresh() })

const onSearch = () => {
  if (keyword.value.trim()) {
    router.push({ path: '/consumer/category', query: { keyword: keyword.value } })
  }
}

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

.consumer-layout {
  min-height: 100vh;
  background: $consumer-bg;
  max-width: 750px;
  margin: 0 auto;
  position: relative;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: $consumer-text;
}

// ---------- 头部 ----------
.consumer-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: linear-gradient(160deg, $consumer-primary 0%, $consumer-primary-light 100%);
  padding: 12px 12px calc(12px + env(safe-area-inset-top, 0));
  box-shadow: 0 4px 16px rgba($consumer-primary, 0.28);
  border-radius: 0 0 20px 20px; // 底部圆角，温柔过渡

  .header-bar {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .header-user {
    display: flex;
    flex-direction: column;
    align-items: center;
    color: #fff;
    flex-shrink: 0;

    .avatar-wrap {
      width: 40px; height: 40px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      backdrop-filter: blur(6px);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }
    .user-avatar { background: rgba(255, 255, 255, 0.35) !important; color: $consumer-primary-dark !important; font-weight: 700; }
    .user-name {
      font-size: 10px; margin-top: 3px;
      max-width: 44px; overflow: hidden; text-overflow: ellipsis;
      opacity: 0.9;
    }
  }

  .search-wrap {
    flex: 1;
    .el-input {
      :deep(.el-input__wrapper) {
        background: rgba(255, 255, 255, 0.95);
        border-radius: 22px;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
        transition: box-shadow $duration-base $ease-spring;
      }
      :deep(.el-input__wrapper.is-focus) {
        box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
      }
      :deep(.el-input__inner) { font-size: 14px; }
      :deep(.el-input__prefix .el-icon) { color: $consumer-primary; }
    }
  }

  .more-btn {
    padding: 8px;
    color: #fff;
    border-radius: 10px;
    cursor: pointer;
    display: flex;
    align-items: center;
    transition: background $duration-fast;

    &:hover { background: rgba(255, 255, 255, 0.15); }
    &:active { transform: scale(0.92); }
  }
}

// ---------- 主内容 ----------
.consumer-main {
  padding-bottom: 84px; // tabbar 高度 + 安全区
}

// ---------- 底部 Tab ----------
.consumer-tabbar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 750px;
  height: 64px;
  background: rgba(255, 253, 247, 0.95);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border-top: 1px solid rgba($consumer-primary, 0.08);
  box-shadow: 0 -4px 20px rgba($consumer-primary, 0.08);
  display: flex;
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom, 0);

  .tab-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-decoration: none;
    color: #B8ADA4;
    font-size: 11px;
    gap: 2px;
    transition: color $duration-base $ease-smooth;
    position: relative;

    .tab-icon-wrap {
      width: 32px; height: 32px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all $duration-base $ease-spring;

      .el-icon { font-size: 20px; transition: font-size $duration-base $ease-spring; }
    }

    .cart-badge {
      :deep(.el-badge__content) {
        background: $consumer-primary-dark;
        border: none;
        top: 4px;
      }
    }

    &.active {
      color: $consumer-primary;
      font-weight: 600;

      .tab-icon-wrap {
        background: linear-gradient(135deg, rgba($consumer-primary, 0.15), rgba($consumer-primary-light, 0.08));
        transform: translateY(-2px);

        .el-icon { font-size: 22px; }
      }
    }

    &:active .tab-icon-wrap { transform: scale(0.9); }
  }

  // 团购 tab：active 时用橙红渐变突出特色入口感
  .groupbuy-tab {
    &.active {
      color: #E85D26;
      font-weight: 600;

      .tab-icon-wrap {
        background: linear-gradient(135deg, rgba(232, 93, 38, 0.18), rgba($consumer-primary, 0.1));
        transform: translateY(-2px);
        .tab-double-avatar { width: 22px; height: 22px; }
      }
    }
    // 默认态也要控制 SVG 尺寸
    .tab-double-avatar {
      width: 20px; height: 20px;
      display: block;
      transition: width $duration-base $ease-spring, height $duration-base $ease-spring;
    }
  }
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s $ease-smooth; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
