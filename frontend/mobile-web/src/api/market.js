import request from '@/utils/request'

// 广告位 GET /app/market/ad/list
export const adList = (params) => request({ url: '/app/market/ad/list', method: 'get', params })

// 可领优惠券 GET /app/market/coupon/list
export const couponList = (params) => request({ url: '/app/market/coupon/list', method: 'get', params })

// 领取优惠券 POST /app/market/coupon/receive
export const couponReceive = (data) => request({ url: '/app/market/coupon/receive', method: 'post', data })

// 我的优惠券 GET /app/market/coupon/mylist
export const couponMyList = (params) => request({ url: '/app/market/coupon/mylist', method: 'get', params })

// 团购列表 GET /app/market/groupon/list
export const grouponList = (params) => request({ url: '/app/market/groupon/list', method: 'get', params })

// 专题列表 GET /app/market/topic/list
export const topicList = (params) => request({ url: '/app/market/topic/list', method: 'get', params })

// 公告列表 GET /app/market/notice/list
export const noticeList = (params) => request({ url: '/app/market/notice/list', method: 'get', params })
