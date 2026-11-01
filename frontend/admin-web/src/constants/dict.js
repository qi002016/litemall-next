/**
 * 后台字典 —— 与 07/08 契约 `*Text` 字段一一对应的本地兜底文案。
 *
 * 契约口径（07 §1.1 / §1.2、03 §1.3）：所有状态文案**优先取后端 `statusText`**，
 * 本地字典只用于字段缺失时兜底，且不得在页面里用 `status` 反推业务可执行动作
 * （03 §6.2 R1-07「状态判定以后端出参为准」）。
 */

/** 订单主状态（P-06 字符串标识；401/402 为存量只读） */
export const ORDER_STATUS = {
  unpaid: { text: '未付款', tone: 'warn' },
  undelivered: { text: '待发货', tone: 'warn' },
  shipped: { text: '已发货', tone: 'info' },
  confirmed: { text: '已收货', tone: 'info' },
  completed: { text: '已完成', tone: 'ok' },
  canceled: { text: '已关闭', tone: 'gray' },
  refund: { text: '退款', tone: 'danger' },
  refunded: { text: '已退款', tone: 'danger' },
}

/** 订单列表 6 态筛选 + 全部（03 §2.4；「已关闭」为 103，必修） */
export const ORDER_TABS = [
  { value: '', label: '全部' },
  { value: 'unpaid', label: '未付款' },
  { value: 'undelivered', label: '待发货' },
  { value: 'shipped', label: '已发货' },
  { value: 'confirmed', label: '已收货' },
  { value: 'completed', label: '已完成' },
  { value: 'canceled', label: '已关闭' },
]

/** 售后 8 态（07 §1.2，已冻结） */
export const AFTERSALE_STATUS = {
  applied: { text: '申请中', tone: 'warn' },
  waitReturn: { text: '待寄回', tone: 'warn' },
  waitReceive: { text: '待收货', tone: 'info' },
  refunding: { text: '退款中', tone: 'danger' },
  completed: { text: '退款完成', tone: 'ok' },
  closed: { text: '已关闭', tone: 'gray' },
  rejected: { text: '已驳回', tone: 'gray' },
  cancelled: { text: '已撤销', tone: 'gray' },
}

/** 售后处理台页签（全部 + 8 态） */
export const AFTERSALE_TABS = [
  { value: '', label: '全部' },
  { value: 'applied', label: '申请中' },
  { value: 'waitReturn', label: '待寄回' },
  { value: 'waitReceive', label: '待收货' },
  { value: 'refunding', label: '退款中' },
  { value: 'completed', label: '退款完成' },
  { value: 'closed', label: '已关闭' },
  { value: 'rejected', label: '已驳回' },
  { value: 'cancelled', label: '已撤销' },
]

/** 售后终态（不可再操作） */
export const AFTERSALE_TERMINAL = ['completed', 'closed', 'rejected', 'cancelled']

/**
 * 售后状态数值码 → 标识。
 * 07 契约要求出参为标识字符串，实测 CN-01 列表返回的是数字码（"0"/"3"…），
 * 用 statusText 兜底展示、用本表兜底配色，避免两个口径打架时页面掉色。
 */
export const AFTERSALE_STATUS_CODE = {
  0: 'applied',
  1: 'waitReturn',
  2: 'waitReceive',
  3: 'refunding',
  4: 'completed',
  5: 'closed',
  6: 'rejected',
  7: 'cancelled',
}

/** 售后类型：0 历史只读 / 1 仅退款 / 2 退货退款 */
export const AFTERSALE_TYPE = {
  0: { text: '历史售后', tone: 'gray' },
  1: { text: '仅退款', tone: 'danger' },
  2: { text: '退货退款', tone: 'warn' },
}

/** 订单行退款状态 */
export const REFUND_STATUS = {
  0: '未退款',
  1: '部分退款',
  2: '全额退款',
}

/** 售后库存回补状态 */
export const RESTOCK_STATUS = {
  0: '未回补',
  1: '已回补',
  2: '待回补',
}

/** 商品状态 */
export const GOODS_STATUS = {
  0: { text: '草稿', tone: 'gray' },
  1: { text: '在售', tone: 'ok' },
  2: { text: '已下架', tone: 'gray' },
}

/** 商品列表页签 */
export const GOODS_TABS = [
  { value: '', label: '全部' },
  { value: '1', label: '在售' },
  { value: '2', label: '已下架' },
  { value: '0', label: '草稿' },
]

/** 评价回复状态（R2-09：去掉「低分」页签，低分改由星级多选筛选实现） */
export const COMMENT_REPLY_TABS = [
  { value: '', label: '全部' },
  { value: 'unreplied', label: '待回复' },
  { value: 'replied', label: '已回复' },
]

/** 评价可见状态（CN-30/31：0 显示 / 1 隐藏） */
export const COMMENT_STATUS = {
  0: { text: '显示', tone: 'ok' },
  1: { text: '已隐藏', tone: 'danger' },
}

/** 评价可见状态筛选页签 */
export const COMMENT_VISIBLE_TABS = [
  { value: '', label: '全部' },
  { value: '0', label: '显示中' },
  { value: '1', label: '已隐藏' },
]

/** 星级筛选项（多选，后端 star=1,2,3 逗号多值） */
export const COMMENT_STAR_OPTIONS = [
  { value: '5', label: '5 星' },
  { value: '4', label: '4 星' },
  { value: '3', label: '3 星' },
  { value: '2', label: '2 星' },
  { value: '1', label: '1 星' },
]

/** 管理员账号状态（CN-37：1 启用 / 0 禁用） */
export const ACCOUNT_STATUS = {
  1: { text: '启用', tone: 'ok' },
  0: { text: '禁用', tone: 'gray' },
}

/** 售后常用驳回理由（原型 AS_REASONS 的去重精简版） */
export const AFTERSALE_REJECT_REASONS = [
  '不满足退款条件',
  '商品已使用影响二次销售',
  '凭证不完整',
  '已超过售后时效',
  '其他',
]

/** 默认物流公司（原型发货弹层默认值） */
export const DEFAULT_SHIP_CHANNEL = '顺丰速运'

/** 订单状态归一：数字码（101/102/201/301/103/401/402）与标识串都转成契约标识 */
export function orderStatusKey(value) {
  const raw = value == null ? '' : String(value)
  return ORDER_STATUS_CODE[raw] || raw
}

export function orderStatusMeta(row) {
  const key = orderStatusKey(row && row.status)
  const hit = key && ORDER_STATUS[key]
  return {
    text: (row && row.statusText) || (hit && hit.text) || (key || '—'),
    tone: (hit && hit.tone) || 'gray',
  }
}

/** 订单状态数字码（07）：101 未付款 / 102 待发货 / 201 已发货 / 301 已收货 / 501 已完成 / 103 已关闭 / 401 存量退款 / 402 已退款 */
export const ORDER_STATUS_CODE = {
  101: 'unpaid',
  102: 'undelivered',
  201: 'shipped',
  301: 'confirmed',
  501: 'completed',
  103: 'canceled',
  401: 'refund',
  402: 'refunded',
}

/** 售后状态归一：数字码（"0"…"7"）与标识串都转成契约标识 */
export function aftersaleStatusKey(value) {
  const raw = value == null ? '' : String(value)
  return AFTERSALE_STATUS_CODE[raw] || raw
}

export function aftersaleStatusMeta(row) {
  const raw = row && row.status != null ? String(row.status) : ''
  const hit = AFTERSALE_STATUS[aftersaleStatusKey(raw)]
  return {
    text: (row && row.statusText) || (hit && hit.text) || raw || '—',
    tone: (hit && hit.tone) || 'gray',
  }
}

export function aftersaleTypeMeta(row) {
  const hit = row && row.type != null && AFTERSALE_TYPE[row.type]
  return {
    text: (row && row.typeText) || (hit && hit.text) || '—',
    tone: (hit && hit.tone) || 'gray',
  }
}

/** 评价可见状态：优先用后端 statusText，缺失时按字典兜底 */
export function commentStatusMeta(row) {
  const raw = row && row.status != null ? String(row.status) : ''
  const hit = COMMENT_STATUS[raw]
  return {
    text: (row && row.statusText) || (hit && hit.text) || raw || '—',
    tone: (hit && hit.tone) || 'gray',
  }
}

/** 管理员账号状态：优先用后端 statusText */
export function accountStatusMeta(row) {
  const raw = row && row.status != null ? String(row.status) : ''
  const hit = ACCOUNT_STATUS[raw]
  return {
    text: (row && row.statusText) || (hit && hit.text) || (raw === '0' ? '禁用' : raw ? '启用' : '—'),
    tone: (hit && hit.tone) || 'gray',
  }
}