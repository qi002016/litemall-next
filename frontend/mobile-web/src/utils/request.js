import axios from 'axios'
import { useUserStore } from '@/store/user'
import { toast } from '@/utils/toast'
import router from '@/router'

// 统一契约：{ code, msg, data }，成功码只有 200（Result.ok()）
const service = axios.create({
  baseURL: '', // 同源，由 Vite 代理 /app、/admin 到网关
  timeout: 15000,
})

service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

function toLogin() {
  const current = router.currentRoute.value
  if (current.path === '/login') return
  router.replace({ path: '/login', query: { redirect: current.fullPath } })
}

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code === 200) {
      return res.data
    }
    const code = res && res.code
    const msg = (res && res.msg) || '请求失败'
    if (code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      toast('请先登录', 'err')
      toLogin()
      return Promise.reject(Object.assign(new Error(msg), { code, data: res && res.data }))
    }
    toast(msg, 'err')
    return Promise.reject(Object.assign(new Error(msg), { code, data: res && res.data }))
  },
  (error) => {
    const { response } = error
    if (response) {
      const res = response.data || {}
      if (response.status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        toast('请先登录', 'err')
        toLogin()
      } else if (response.status === 403) {
        toast(res.msg || '无权访问该功能', 'err')
      } else {
        toast(res.msg || `请求失败(${response.status})`, 'err')
      }
      return Promise.reject(
        Object.assign(new Error(res.msg || `请求失败(${response.status})`), {
          code: res.code || response.status,
          data: res.data,
        })
      )
    }
    toast('网络异常，请稍后重试', 'err')
    return Promise.reject(error)
  }
)

export default service