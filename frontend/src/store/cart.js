// 购物车全局状态（约束文档 28.9：购物车数量徽标必须实时同步）
// 唯一数据源：Layout 底部 tabbar、各加购/删购点都从这里读写
import { defineStore } from 'pinia'
import { getCart } from '@/api'

export const useCartStore = defineStore('cart', {
  state: () => ({
    cartCount: 0,       // 购物车商品件数（sum quantity）
    itemCount: 0,       // 购物车条目数（Cart 行数）
    lastRefresh: 0      // 上次刷新时间戳，防抖用
  }),

  actions: {
    /**
     * 从后端拉购物车并更新计数
     * @param {boolean} force 是否强制刷新（忽略防抖）
     */
    async refresh(force = false) {
      const now = Date.now()
      // 防抖：1 秒内不重复拉（快速点击加购时保护后端）
      if (!force && now - this.lastRefresh < 800) return
      this.lastRefresh = now

      try {
        const list = await getCart()
        const arr = list || []
        this.itemCount = arr.length
        this.cartCount = arr.reduce((s, i) => s + Number(i.quantity || 0), 0)
      } catch (e) {
        // 未登录或 401 时不要刷红
        this.cartCount = 0
        this.itemCount = 0
      }
    },

    /** 手动加 N（乐观更新，加购成功后立即 +1，不必等 refresh 回来） */
    optimisticAdd(n = 1) {
      this.cartCount += n
      this.itemCount += 1
    },

    /** 手动减 N（乐观更新） */
    optimisticRemove(n = 1) {
      this.cartCount = Math.max(0, this.cartCount - n)
      this.itemCount = Math.max(0, this.itemCount - 1)
    },

    /** 清空（下单成功后调） */
    clear() {
      this.cartCount = 0
      this.itemCount = 0
      this.lastRefresh = 0
    }
  }
})
