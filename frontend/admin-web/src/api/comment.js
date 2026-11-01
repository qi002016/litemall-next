import request from '@/utils/request'

// 评价分页 GET /admin/comment/list（08 CN-29）
export const commentPage = (params) => request({ url: '/admin/comment/list', method: 'get', params })

// 回复评价 POST /admin/comment/reply（08 CN-30，覆盖语义）
export const commentReply = (data) => request({ url: '/admin/comment/reply', method: 'post', data })

// 显示/隐藏评价 POST /admin/comment/status（08 CN-31）
export const commentStatus = (data) => request({ url: '/admin/comment/status', method: 'post', data })