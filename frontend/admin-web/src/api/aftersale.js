import request from '@/utils/request'

// 售后分页 GET /admin/aftersale/list（07 CN-01）
export const aftersalePage = (params) => request({ url: '/admin/aftersale/list', method: 'get', params })

// 售后详情 GET /admin/aftersale/detail（07 CN-02）
export const aftersaleDetail = (params) => request({ url: '/admin/aftersale/detail', method: 'get', params })

// 同意售后 POST /admin/aftersale/approve（07 CN-03；退货退款可覆盖退货地址）
export const aftersaleApprove = (data) => request({ url: '/admin/aftersale/approve', method: 'post', data })

// 驳回售后 POST /admin/aftersale/reject（07 CN-04）
export const aftersaleReject = (data) => request({ url: '/admin/aftersale/reject', method: 'post', data })

// 确认收到退货 POST /admin/aftersale/confirm-receive（07 CN-05）
export const aftersaleConfirmReceive = (data) =>
  request({ url: '/admin/aftersale/confirm-receive', method: 'post', data })

// 跳过收货 POST /admin/aftersale/skip-receive（07 CN-06）
export const aftersaleSkipReceive = (data) => request({ url: '/admin/aftersale/skip-receive', method: 'post', data })

// 执行退款 POST /admin/aftersale/refund（07 CN-07；退款与回补同事务，回补失败返回 restockPending）
export const aftersaleRefund = (data) => request({ url: '/admin/aftersale/refund', method: 'post', data })

// 重试回补并退款 POST /admin/aftersale/retry-restock（07 CN-08）
export const aftersaleRetryRestock = (data) =>
  request({ url: '/admin/aftersale/retry-restock', method: 'post', data })

// 关闭售后 POST /admin/aftersale/close（07 CN-09）
export const aftersaleClose = (data) => request({ url: '/admin/aftersale/close', method: 'post', data })