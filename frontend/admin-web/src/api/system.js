import request from '@/utils/request'

// 管理员分页
export const accountPage = (params) => request({ url: '/admin/account/list', method: 'get', params })
export const accountCreate = (data) => request({ url: '/admin/account/create', method: 'post', data })
export const accountUpdate = (data) => request({ url: '/admin/account/update', method: 'put', data })
export const accountResetPwd = (data) => request({ url: '/admin/account/reset-password', method: 'put', data })
export const accountDelete = (params) => request({ url: '/admin/account/delete', method: 'delete', params })
// R2-06b：CN-37 账号启停（POST，成功会递增 auth:ver 让目标账号旧会话失效）
export const accountStatus = (data, config) =>
  request({ url: '/admin/account/status', method: 'post', data, ...config })

// 操作日志分页
export const logPage = (params) => request({ url: '/admin/log/list', method: 'get', params })
