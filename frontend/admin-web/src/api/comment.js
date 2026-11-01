import request from '@/utils/request'

// 评价分页 GET /admin/comment/list（08 CN-29）
// star / status 支持逗号多值；dateFrom / dateTo 必须 'yyyy-MM-dd HH:mm:ss'
export const commentPage = (params, config) =>
  request({ url: '/admin/comment/list', method: 'get', params, ...config })

// 回复评价 POST /admin/comment/reply（08 CN-30，覆盖语义）
export const commentReply = (data, config) =>
  request({ url: '/admin/comment/reply', method: 'post', data, ...config })

// 显示/隐藏评价 POST /admin/comment/status（08 CN-31）
export const commentStatus = (data, config) =>
  request({ url: '/admin/comment/status', method: 'post', data, ...config })