import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const service = axios.create({
  baseURL: '',
  timeout: 15000,
})

service.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

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
      ElMessage.error('登录已过期，请重新登录')
      router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      return Promise.reject(new Error(res.msg || '未登录'))
    }
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || 'Error'))
  },
  (error) => {
    const { response } = error
    if (response) {
      const res = response.data || {}
      if (response.status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        ElMessage.error('登录已过期，请重新登录')
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      } else if (response.status === 403) {
        // H1：admin 侧过滤链/网关角色校验拒绝，与"未登录"分流
        ElMessage.error(res.msg || '无权访问该功能')
      } else {
        ElMessage.error(res.msg || `请求失败(${response.status})`)
      }
      return Promise.reject(new Error(res.msg || `请求失败(${response.status})`))
    }
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

export default service
