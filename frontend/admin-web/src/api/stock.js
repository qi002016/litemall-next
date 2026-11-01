import request from '@/utils/request'

// 库存分页 GET /admin/stock/list（08 CN-13：number/locked/sellable，locked 不可用时为 null）
export const stockPage = (params) => request({ url: '/admin/stock/list', method: 'get', params })

// 调整库存 PUT /admin/stock/update（08 CN-14：number 为绝对值，expectNumber 做乐观锁）
export const stockUpdate = (data) => request({ url: '/admin/stock/update', method: 'put', data })