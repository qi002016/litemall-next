import request from '@/utils/request'

// 管理员登录 POST /admin/auth/login
export const login = (data) => request({ url: '/admin/auth/login', method: 'post', data })

// 管理员信息 GET /admin/auth/info
export const adminInfo = () => request({ url: '/admin/auth/info', method: 'get' })
