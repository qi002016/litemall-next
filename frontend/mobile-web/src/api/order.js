import request from '@/utils/request'

/* ============================ 订单（06/07 契约） ============================ */

// CI-80 确认订单（试算）POST /app/order/confirm
// 入参 { cartIds[] | buyNow{productId,number}, addressId, couponId? }
export const orderConfirm = (data) => request({ url: '/app/order/confirm', method: 'post', data })

// CI-82 提交订单 POST /app/order/submit
// 入参 { cartIds[] | buyNow, addressId, couponId?, idemToken, message? }
// 07 契约：idemToken 由客户端生成 UUID；当前后端仍保留 CI-81 服务端签发（POST /app/order/idem-token），
// 故 submitOrder 先尝试取服务端 token，取不到再退回客户端 UUID —— 两种后端实现都能下单。
export const orderSubmit = (data) => request({ url: '/app/order/submit', method: 'post', data })

// CI-81 申请下单幂等 token（后端兼容路径，契约外兜底）
export const orderIdemToken = () => request({ url: '/app/order/idem-token', method: 'post' })

// CI-83 订单列表 GET /app/order/list?tab=&status=&pageNo=&pageSize=&sortBy=&isAsc=
export const orderList = (params) => request({ url: '/app/order/list', method: 'get', params })

// CI-84 订单详情 GET /app/order/detail?orderId=
export const orderDetail = (params) => request({ url: '/app/order/detail', method: 'get', params })

// CI-85 取消订单 POST /app/order/cancel?orderId=
export const orderCancel = (params) => request({ url: '/app/order/cancel', method: 'post', params })

// CI-86 确认收货 POST /app/order/confirm-receive?orderId=
export const orderConfirmReceive = (params) =>
  request({ url: '/app/order/confirm-receive', method: 'post', params })

// CN-20 删除订单 POST /app/order/hide?orderId=
export const orderHide = (params) => request({ url: '/app/order/hide', method: 'post', params })

/* ============================== 售后（07 契约） ============================== */

// CI-91 申请售后 POST /app/aftersale/apply
export const aftersaleApply = (data) => request({ url: '/app/aftersale/apply', method: 'post', data })

// CN-15 我的售后列表 GET /app/aftersale/list?status=&pageNo=&pageSize=
export const aftersaleList = (params) => request({ url: '/app/aftersale/list', method: 'get', params })

// CN-16 售后详情 GET /app/aftersale/detail?aftersaleId=
export const aftersaleDetail = (params) => request({ url: '/app/aftersale/detail', method: 'get', params })

// CN-17 撤销售后 POST /app/aftersale/cancel?aftersaleId=
export const aftersaleCancel = (params) => request({ url: '/app/aftersale/cancel', method: 'post', params })

// CN-18 填写退货物流 POST /app/aftersale/return-ship
export const aftersaleReturnShip = (data) =>
  request({ url: '/app/aftersale/return-ship', method: 'post', data })

/* ============================== 演示支付（07 契约） ============================== */

// CI-103 演示支付 POST /app/pay/wechat  { orderId, mode: 'success' | 'fail' }
export const payWechat = (data) => request({ url: '/app/pay/wechat', method: 'post', data })

// CI-104 支付回调：演示环境由前端模拟微信回调（dev 验签器直接解析明文 { orderId, payId }）
// 真实环境由微信服务器调用，前端不会走这条
export const payNotifyWechat = (data) => request({ url: '/app/pay/notify/wechat', method: 'post', data })

// CI-106 支付结果 GET /app/pay/result?orderId=
export const payResult = (params) => request({ url: '/app/pay/result', method: 'get', params })