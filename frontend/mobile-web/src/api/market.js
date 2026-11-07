import request from '@/utils/request'

// 广告位 GET /app/market/ad/list
export const adList = (params) => request({ url: '/app/market/ad/list', method: 'get', params })

// 可领优惠券 GET /app/market/coupon/list
export const couponList = (params) => request({ url: '/app/market/coupon/list', method: 'get', params })

// 领取优惠券 POST /app/market/coupon/receive（R2 开放；CI-54 四态：成功 200 / 其余 code≠200 且 data 仍带 result）
export const couponReceive = (data, config = {}) =>
  request({ url: '/app/market/coupon/receive', method: 'post', data, ...config })

// 我的优惠券 GET /app/market/coupon/mylist
export const couponMyList = (params) => request({ url: '/app/market/coupon/mylist', method: 'get', params })

// CN-19 结算页可用券 GET /app/market/coupon/usable（R1 新增）
export const couponUsable = (params) => request({ url: '/app/market/coupon/usable', method: 'get', params })

// 团购列表 GET /app/market/groupon/list
export const grouponList = (params) => request({ url: '/app/market/groupon/list', method: 'get', params })

// 专题列表 GET /app/market/topic/list
export const topicList = (params) => request({ url: '/app/market/topic/list', method: 'get', params })

// 专题详情 GET /app/market/topic/detail?topicId=（R3，免登录；不存在时后端返回 404）
// config 透传是为了让专题页用 silent 抑制 404 的全局错误 toast，改由页面友好空态承接
export const topicDetail = (params, config = {}) =>
  request({ url: '/app/market/topic/detail', method: 'get', params, ...config })

// 公告列表 GET /app/market/notice/list
export const noticeList = (params) => request({ url: '/app/market/notice/list', method: 'get', params })