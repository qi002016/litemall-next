import request from '@/utils/request'

// 购物车列表 GET /app/cart/list
export const cartList = () => request({ url: '/app/cart/list', method: 'get' })

// 加入购物车 POST /app/cart/add
export const cartAdd = (data) => request({ url: '/app/cart/add', method: 'post', data })

// 修改购物车 PUT /app/cart/update
export const cartUpdate = (data) => request({ url: '/app/cart/update', method: 'put', data })

// 删除购物车项 DELETE /app/cart/delete
export const cartDelete = (params) => request({ url: '/app/cart/delete', method: 'delete', params })
