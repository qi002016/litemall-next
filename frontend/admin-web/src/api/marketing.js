import request from '@/utils/request'

// 优惠券
export const couponPage = (params) => request({ url: '/admin/market/coupon/list', method: 'get', params })
export const couponCreate = (data) => request({ url: '/admin/market/coupon/create', method: 'post', data })
export const couponUpdate = (data) => request({ url: '/admin/market/coupon/update', method: 'put', data })
export const couponDelete = (params) => request({ url: '/admin/market/coupon/delete', method: 'delete', params })

// 广告
export const adPage = (params) => request({ url: '/admin/market/ad/list', method: 'get', params })
export const adCreate = (data) => request({ url: '/admin/market/ad/create', method: 'post', data })
export const adUpdate = (data) => request({ url: '/admin/market/ad/update', method: 'put', data })
export const adDelete = (params) => request({ url: '/admin/market/ad/delete', method: 'delete', params })

// 专题
export const topicPage = (params) => request({ url: '/admin/market/topic/list', method: 'get', params })
export const topicCreate = (data) => request({ url: '/admin/market/topic/create', method: 'post', data })
export const topicUpdate = (data) => request({ url: '/admin/market/topic/update', method: 'put', data })
export const topicDelete = (params) => request({ url: '/admin/market/topic/delete', method: 'delete', params })

// 公告（R2-06：CN-34~36；无删除端点，停用 = enabled:false）
export const noticePage = (params, config) => request({ url: '/admin/notice/list', method: 'get', params, ...config })
export const noticeCreate = (data, config) => request({ url: '/admin/notice/create', method: 'post', data, ...config })
export const noticeUpdate = (data, config) => request({ url: '/admin/notice/update', method: 'post', data, ...config })

// 仪表盘统计（R2-05：CI-08，period=today|7d|30d；此处保留向后兼容，页面优先用 api/dashboard.js）
export const dashboardStats = (params) => request({ url: '/admin/dashboard/stats', method: 'get', params })
