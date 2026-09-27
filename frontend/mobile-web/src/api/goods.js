import request from '@/utils/request'

// 商品列表 GET /app/goods/list
export const goodsList = (params) => request({ url: '/app/goods/list', method: 'get', params })

// 商品详情 GET /app/goods/detail
export const goodsDetail = (params) => request({ url: '/app/goods/detail', method: 'get', params })

// 分类树 GET /app/goods/category
export const categoryTree = () => request({ url: '/app/goods/category', method: 'get' })

// 品牌列表 GET /app/goods/brand/list
export const brandList = (params) => request({ url: '/app/goods/brand/list', method: 'get', params })

// 评论列表 GET /app/goods/comment/list
export const commentList = (params) => request({ url: '/app/goods/comment/list', method: 'get', params })

// 发表评论 POST /app/goods/comment/post
export const commentPost = (data) => request({ url: '/app/goods/comment/post', method: 'post', data })

// 搜索历史 GET /app/search/history
export const searchHistory = () => request({ url: '/app/search/history', method: 'get' })

// 清空搜索历史 POST /app/search/clear
export const clearHistory = () => request({ url: '/app/search/clear', method: 'post' })

// 记录搜索 POST /app/search/record  body: { keyword }
export const recordSearch = (data) => request({ url: '/app/search/record', method: 'post', data })
