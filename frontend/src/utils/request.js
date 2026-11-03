// 约束文档 1.2：请求统一封装 axios，统一处理 token、错误码、loading、异常提示
// 风险文档 #46：打包后接口 404 → baseURL 由 Vite proxy 处理
// 风险文档 #47：跨域 → Vite proxy 解决

import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：自动带 token
request.interceptors.request.use(
  config => {
    const token = useUserStore().token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器：统一处理错误码
request.interceptors.response.use(
  response => {
    const res = response.data

    // 后端统一响应体 R<T>{ code, data, msg }
    // 约束文档 7.1：后端成功码为 0，失败码为 4xx/5xx
    if (res.code !== 0) {
      // 约束 8.1：code=401（业务层鉴权失败）或 code=1002（历史遗留）都触发 logout
      if (res.code === 401 || res.code === 1002) {
        const userStore = useUserStore()
        userStore.logout()
        ElMessage.warning('登录已过期，请重新登录')
        router.push('/login')
        return Promise.reject(new Error(res.msg || '未授权'))
      }
      // 403: 越权（约束文档 8：越权返回统一无权限错误）
      if (res.code === 403) {
        ElMessage.error(res.msg || '无权限访问')
        return Promise.reject(new Error(res.msg || '无权限'))
      }
      // 其他业务错误
      const msg = res.message || res.msg || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }

    return res.data // 直接返回 data 部分
  },
  error => {
    // 网络层错误
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        ElMessage.warning('登录已过期，请重新登录')
        router.push('/login')
      } else if (status === 403) {
        ElMessage.error('无权限访问')
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在')
      } else {
        ElMessage.error(error.response.data?.msg || `服务器错误(${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error('网络连接失败')
    }
    return Promise.reject(error)
  }
)

export default request
