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
    if (!response.config?.silent) {
      ElMessage.error(res.msg || '请求失败')
    }
    return Promise.reject(makeError(res))
  },
  (error) => {
    const { response } = error
    const silent = error.config?.silent
    if (response) {
      // 网关 401/403 可能回空 body（非 JSON），统一降级成对象再取 msg
      const res = response.data && typeof response.data === 'object' ? response.data : {}
      if (response.status === 401) {
        const userStore = useUserStore()
        userStore.logout()
        ElMessage.error('登录已过期，请重新登录')
        router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      } else if (response.status === 403) {
        // H1：admin 侧过滤链/网关角色校验拒绝，与"未登录"分流
        if (!silent) ElMessage.error(res.msg || '无权访问该功能')
      } else if (!silent) {
        ElMessage.error(res.msg || `请求失败(${response.status})`)
      }
      // 409/404 走 HTTP 状态码时也把 code/errorCode 带给调用方（如并发冲突提示刷新）
      const err = makeError(res, `请求失败(${response.status})`)
      err.status = response.status
      err.code = res.code ?? response.status
      return Promise.reject(err)
    }
    if (!silent) ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

/**
 * 统一构造业务错误：保留 code / errorCode，供页面按业务码分支。
 * 06：HTTP 恒 200 + code；03/be-admin：/inner 与非 200 语义时 code 可能同时出现在 body 与 HTTP 状态里。
 */
function makeError(res = {}, fallback = '请求失败') {
  const body = res && typeof res === 'object' ? res : {}
  const err = new Error(body.msg || fallback)
  err.code = body.code
  err.errorCode = body.data?.errorCode ?? body.errorCode
  err.payload = body.data ?? null
  return err
}

export default service
