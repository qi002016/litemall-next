import request from '@/utils/request'

/**
 * 分类与品牌管理（R2-01：CN-21 ~ CN-28，05-R2 实施口径 §3.1~§3.8）。
 *
 * 约定：
 * - 分类树/品牌分页都是 **admin 端点**（不是 C 端 `/app/goods/*`）：C 端分类树只返回
 *   「自身或子分类下存在在售商品」的分类，新建的空分类不会出现，后台管理不能用它；
 * - 金额 `floorPrice` 一律整数分；ID 是 BIGINT 字符串，原样透传不截断；
 * - 写接口默认带 `silent`：由页面把后端文案（如 409「请先移走商品」）落在页面内的
 *   错误条上，避免只闪一个 toast。
 */

// 分类（CN-21~24）
export const adminCategoryTree = (params, config) =>
  request({ url: '/admin/category/list', method: 'get', params, ...config })

export const adminCategoryCreate = (data, config) =>
  request({ url: '/admin/category/create', method: 'post', data, ...config })

export const adminCategoryUpdate = (data, config) =>
  request({ url: '/admin/category/update', method: 'put', data, ...config })

export const adminCategoryDelete = (params, config) =>
  request({ url: '/admin/category/delete', method: 'delete', params, ...config })

// 品牌（CN-25~28）
export const adminBrandPage = (params, config) =>
  request({ url: '/admin/brand/list', method: 'get', params, ...config })

export const adminBrandCreate = (data, config) =>
  request({ url: '/admin/brand/create', method: 'post', data, ...config })

export const adminBrandUpdate = (data, config) =>
  request({ url: '/admin/brand/update', method: 'put', data, ...config })

export const adminBrandDelete = (params, config) =>
  request({ url: '/admin/brand/delete', method: 'delete', params, ...config })