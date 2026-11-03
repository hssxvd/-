// 约束文档 1.2：Pinia 状态管理
// 约束文档 8：前端隐藏菜单只是体验，不是安全（后端 Service 层二次校验）

import { defineStore } from 'pinia'
import { storage } from '@/utils/storage'
import request from '@/utils/request'
import { USER_TYPE } from '@/config/business'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: storage.get('token') || '',
    userId: storage.get('userId') || null,
    username: storage.get('username') || '',
    nickname: storage.get('nickname') || '',
    avatar: storage.get('avatar') || '',
    phone: storage.get('phone') || '',
    userType: storage.get('userType') || '', // CONSUMER / MERCHANT / ADMIN / SUPER_ADMIN / PICKUP_STAFF
    permissions: storage.get('permissions') || [],
    communityId: storage.get('communityId') || null,
    communityName: storage.get('communityName') || '',
    merchantId: storage.get('merchantId') || null,
    merchantName: storage.get('merchantName') || '',
    pickupPointId: storage.get('pickupPointId') || null,  // V5: 核销员绑定的自提点 ID
  pickupPointName: storage.get('pickupPointName') || '',
  pickupPointAddress: storage.get('pickupPointAddress') || '',
  pickupCommunityName: storage.get('pickupCommunityName') || '',
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isConsumer: (state) => state.userType === USER_TYPE.CONSUMER,
    isMerchant: (state) => state.userType === USER_TYPE.MERCHANT,
    isPickupStaff: (state) => state.userType === USER_TYPE.PICKUP_STAFF,
    isAdmin: (state) => state.userType === USER_TYPE.ADMIN || state.userType === USER_TYPE.SUPER_ADMIN,
    // 约束文档 23：各端 Token 颜色区分
    tokenColor: (state) => {
      const map = { CONSUMER: '#E6A23C', MERCHANT: '#409EFF', PICKUP_STAFF: '#67C23A', ADMIN: '#304156', SUPER_ADMIN: '#304156' }
      return map[state.userType] || '#666'
    },
    // 无头像时的 fallback 首字母
    avatarFallback: (state) => (state.nickname || state.username || 'U').charAt(0).toUpperCase(),
    // 用于展示的昵称（nickname 优先）
    displayName: (state) => state.nickname || state.username
  },

  actions: {
    async login(username, password) {
      const res = await request.post('/auth/login', { username, password })
      this.token = res.token
      this.userId = res.userId
      this.username = res.username
      this.userType = res.userType
      this.permissions = res.permissions || []
      this.communityId = res.communityId || null
      this.merchantId = res.merchantId || null
      this.pickupPointId = res.pickupPointId || null
    this.pickupPointName = res.pickupPointName || ''
    this.pickupPointAddress = res.pickupPointAddress || ''
    this.pickupCommunityName = res.pickupCommunityName || ''
    storage.set('token', res.token)
      storage.set('userId', res.userId)
      storage.set('username', res.username)
      storage.set('userType', res.userType)
      storage.set('permissions', res.permissions)
      storage.set('communityId', res.communityId || null)
      storage.set('merchantId', res.merchantId || null)
      storage.set('pickupPointId', res.pickupPointId || null)
    storage.set('pickupPointName', res.pickupPointName || '')
    storage.set('pickupPointAddress', res.pickupPointAddress || '')
    storage.set('pickupCommunityName', res.pickupCommunityName || '')
      // 登录后顺手拉完整资料（头像/昵称/手机号/社区名等）
      try { await this.fetchProfile() } catch { /* 不阻断登录流程 */ }
      return res
    },

    /** 拉取当前用户完整资料并回填 store（头像/昵称/手机号/社区名/商家名） */
    async fetchProfile() {
      const p = await request.get('/users/me/profile')
      this.nickname = p.nickname || ''
      this.avatar = p.avatar || ''
      this.phone = p.phone || ''
      this.communityName = p.communityName || ''
      this.merchantName = p.merchantName || ''
      if (p.communityId) this.communityId = p.communityId
      if (p.merchantId) this.merchantId = p.merchantId
      storage.set('nickname', this.nickname)
      storage.set('avatar', this.avatar)
      storage.set('phone', this.phone)
      storage.set('communityName', this.communityName)
      storage.set('merchantName', this.merchantName)
      return p
    },

    async updateAvatar(url) {
      await request.post('/users/me/avatar', { avatarUrl: url })
      this.avatar = url
      storage.set('avatar', url)
    },

    async updateBasic(data) {
      // data: { nickname?, phone? }
      await request.put('/users/me', data)
      if (data.nickname !== undefined) { this.nickname = data.nickname; storage.set('nickname', data.nickname) }
      if (data.phone !== undefined) { this.phone = data.phone; storage.set('phone', data.phone) }
    },

    // 根据 userType 跳转到对应端（约束 29.4：禁止硬编码，必须用 config/business 常量）
    redirectByRole() {
      const routeMap = {
        [USER_TYPE.CONSUMER]:    '/consumer',
        [USER_TYPE.MERCHANT]:    '/merchant',
        [USER_TYPE.PICKUP_STAFF]: '/pickup',
        [USER_TYPE.ADMIN]:       '/admin',
        [USER_TYPE.SUPER_ADMIN]: '/admin'
      }
      return routeMap[this.userType] || '/login'
    },

    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.nickname = ''
      this.avatar = ''
      this.phone = ''
      this.userType = ''
      this.permissions = []
      this.communityId = null
      this.communityName = ''
      this.merchantId = null
      this.merchantName = ''
      this.pickupPointId = null
    this.pickupPointName = ''
    this.pickupPointAddress = ''
    this.pickupCommunityName = ''
      storage.clear()
    },

    // 约束文档 8：前端根据 permissions 隐藏菜单，但后端必须二次校验
    hasPermission(code) {
      return this.permissions.includes(code) || this.permissions.includes('*')
    }
  }
})
