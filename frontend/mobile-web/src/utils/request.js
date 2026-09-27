import axios from 'axios'
import { showFailToast } from 'vant'
import { useUserStore } from '@/store/user'
import router from '@/router'

// 创建 axios 实例
const service = axios.create({
  baseURL: '', // 同源，由 Vite 代理 /app、/admin 到网关
  timeout: 15000,
})

// 请求拦截器：注入 token
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一 { code, msg, data }
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 契约统一：成功码只有 200（Result.ok()），后端不存在返回 0 的代码路径
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      showFailToast('请先登录')
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      return Promise.reject(new Error(res.msg || '未登录'))
    }
    showFailToast(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || 'Error'))
  },
  (error) => {
    const { response } = error
    if (response) {
      const res = response.data || {}
      if (response.status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        showFailToast('请先登录')
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      } else if (response.status === 403) {
        // H1：网关按角色拒绝（如 C 端令牌访问 /admin/**），与"未登录"分流
        showFailToast(res.msg || '无权访问该功能')
      } else {
        showFailToast(res.msg || `请求失败(${response.status})`)
      }
      return Promise.reject(new Error(res.msg || `请求失败(${response.status})`))
    }
    showFailToast(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default service
