import request from '@/utils/request'

// 工作台统计 GET /admin/dashboard/stats（08 CI-08；period=today|7d|30d）
export const dashboardStats = (params) => request({ url: '/admin/dashboard/stats', method: 'get', params })