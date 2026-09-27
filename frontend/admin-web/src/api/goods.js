import request from '@/utils/request'

// 商品分页 GET /admin/goods/list
export const goodsPage = (params) => request({ url: '/admin/goods/list', method: 'get', params })

// 新建商品 POST /admin/goods/create
export const goodsCreate = (data) => request({ url: '/admin/goods/create', method: 'post', data })

// 更新商品 PUT /admin/goods/update
export const goodsUpdate = (data) => request({ url: '/admin/goods/update', method: 'put', data })

// 删除商品 DELETE /admin/goods/delete
export const goodsDelete = (params) => request({ url: '/admin/goods/delete', method: 'delete', params })
