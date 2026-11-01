import request from '@/utils/request'

// 商品分页 GET /admin/goods/list（08 CI-09）
export const goodsPage = (params) => request({ url: '/admin/goods/list', method: 'get', params })

// 新建商品 POST /admin/goods/create（08 CI-10：规格维度 + SKU 矩阵）
export const goodsCreate = (data) => request({ url: '/admin/goods/create', method: 'post', data })

// 更新商品 PUT /admin/goods/update（08 CI-11：skus 全量覆盖，updateTime 做并发校验）
export const goodsUpdate = (data) => request({ url: '/admin/goods/update', method: 'put', data })

/**
 * 商品详情（08 CI-32，C 端免登录）。
 *
 * R1 约束：02 基线没有 `/admin/goods/*` 详情端点，`CN-21~28`（分类/品牌管理）属 R2，
 * 因此编辑弹层只能复用免登录的 C 端读接口取 `specifications`/`skus`/分类/品牌；
 * 这些调用**只读**，写操作仍全部走 `/admin/**`。已下架/草稿商品在 CI-32 返回
 * 404 GOODS_OFF_SALE，弹层会降级为「仅编辑基础字段、不覆盖 SKU」。
 */
export const goodsDetail = (goodsId) =>
  request({ url: '/app/goods/detail', method: 'get', params: { goodsId } })

// 分类树（08 CI-33，只读）
export const categoryTree = () => request({ url: '/app/goods/category', method: 'get' })

// 品牌列表（08 CI-34，只读）
export const brandList = (params) => request({ url: '/app/goods/brand/list', method: 'get', params })