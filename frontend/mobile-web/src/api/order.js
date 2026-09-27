import request from '@/utils/request'

// 订单确认（试算） POST /app/order/confirm
export const orderConfirm = (data) => request({ url: '/app/order/confirm', method: 'post', data })

// 申请下单幂等token POST /app/order/idem-token
export const orderIdemToken = () => request({ url: '/app/order/idem-token', method: 'post' })

// 提交订单 POST /app/order/submit
export const orderSubmit = (data) => request({ url: '/app/order/submit', method: 'post', data })

// 我的订单列表 GET /app/order/list
export const orderList = (params) => request({ url: '/app/order/list', method: 'get', params })

// 订单详情 GET /app/order/detail
export const orderDetail = (params) => request({ url: '/app/order/detail', method: 'get', params })

// 取消订单 POST /app/order/cancel
export const orderCancel = (params) => request({ url: '/app/order/cancel', method: 'post', params })

// 确认收货 POST /app/order/confirm-receive
export const orderConfirmReceive = (params) => request({ url: '/app/order/confirm-receive', method: 'post', params })

// 申请售后 POST /app/aftersale/apply
export const aftersaleApply = (data) => request({ url: '/app/aftersale/apply', method: 'post', data })

// 微信支付下单 POST /app/pay/wechat
export const payWechat = (data) => request({ url: '/app/pay/wechat', method: 'post', data })

// 查询支付结果 GET /app/pay/result
export const payResult = (params) => request({ url: '/app/pay/result', method: 'get', params })

// 微信支付回调（仅 dev 模拟用；生产由微信服务器直接回调网关白名单路径）
export const payNotifyWechat = (data) => request({ url: '/app/pay/notify/wechat', method: 'post', data })
