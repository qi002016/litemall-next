import request from '@/utils/request'

// 订单分页 GET /admin/order/list（07 CI-26）
export const orderPage = (params) => request({ url: '/admin/order/list', method: 'get', params })

// 订单详情 GET /admin/order/{id}（07 CN-33）
export const orderDetail = (orderId, config = {}) =>
  request({ url: `/admin/order/${orderId}`, method: 'get', ...config })

// 发货 POST /admin/order/ship（07 CI-27）
export const orderShip = (data) => request({ url: '/admin/order/ship', method: 'post', data })

// 立即关单 POST /admin/order/close（07 CN-10，仅未付款可关）
export const orderClose = (data) => request({ url: '/admin/order/close', method: 'post', data })

// 批量关闭超时订单 POST /admin/order/close-expired（07 CN-11）
export const orderCloseExpired = (data) => request({ url: '/admin/order/close-expired', method: 'post', data })

// 修改物流单号 POST /admin/order/update-ship（07 CN-12）
export const orderUpdateShip = (data) => request({ url: '/admin/order/update-ship', method: 'post', data })