import request from '@/utils/request'

// 获取用户信息 GET /app/user/info
export const userInfo = () => request({ url: '/app/user/info', method: 'get' })

// 修改用户信息 PUT /app/user/info
export const updateUserInfo = (data) => request({ url: '/app/user/info', method: 'put', data })

// 修改密码 PUT /app/user/password
export const updatePassword = (data) => request({ url: '/app/user/password', method: 'put', data })

// 地址列表 GET /app/address
export const addressList = () => request({ url: '/app/address', method: 'get' })

// 新增地址 POST /app/address/create
export const addressCreate = (data) => request({ url: '/app/address/create', method: 'post', data })

// 修改地址 POST /app/address/update?addressId=
export const addressUpdate = (addressId, data) =>
  request({ url: '/app/address/update', method: 'post', params: { addressId }, data })

// 删除地址 POST /app/address/delete?addressId=（后端 @Validated AddressDTO 需同时带完整地址体）
export const addressDelete = (addressId, data) =>
  request({ url: '/app/address/delete', method: 'post', params: { addressId }, data })

// 设置默认地址 GET /app/address/default?addressId=
export const addressSetDefault = (addressId) =>
  request({ url: '/app/address/default', method: 'get', params: { addressId } })

// 我的收藏 GET /app/collect/list
export const collectList = () => request({ url: '/app/collect/list', method: 'get' })

// 收藏/取消 POST /app/collect/add
export const collectAdd = (data) => request({ url: '/app/collect/add', method: 'post', data })

// 我的足迹 GET /app/footprint/list
export const footprintList = (params) => request({ url: '/app/footprint/list', method: 'get', params })

// 记录浏览足迹 POST /app/footprint/add
export const footprintAdd = (data) => request({ url: '/app/footprint/add', method: 'post', data })

// 意见反馈 POST /app/feedback/submit
export const feedbackSubmit = (data) => request({ url: '/app/feedback/submit', method: 'post', data })
