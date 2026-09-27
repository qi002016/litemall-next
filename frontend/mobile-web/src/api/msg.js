import request from '@/utils/request'

// 我的消息列表 GET /app/msg/list?type=&pageNum=&pageSize=
// type 可选: order / system / coupon
export const msgList = (params) => request({ url: '/app/msg/list', method: 'get', params })

// 消息详情 GET /app/msg/detail?id=
export const msgDetail = (params) => request({ url: '/app/msg/detail', method: 'get', params })

// 标记已读 POST /app/msg/read  body: { id }
export const msgRead = (data) => request({ url: '/app/msg/read', method: 'post', data })
