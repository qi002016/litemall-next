import request from '@/utils/request'

// 用户分页 GET /admin/user/list
export const userPage = (params) => request({ url: '/admin/user/list', method: 'get', params })

// 冻结/解冻用户 POST /admin/user/status
export const userStatus = (data) => request({ url: '/admin/user/status', method: 'post', data })
