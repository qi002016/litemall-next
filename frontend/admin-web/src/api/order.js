import request from '@/utils/request'

// 订单分页 GET /admin/order/list
export const orderPage = (params) => request({ url: '/admin/order/list', method: 'get', params })

// 订单发货 POST /admin/order/ship
export const orderShip = (data) => request({ url: '/admin/order/ship', method: 'post', data })

// 退款审核 POST /admin/order/refund
export const orderRefund = (data) => request({ url: '/admin/order/refund', method: 'post', data })
