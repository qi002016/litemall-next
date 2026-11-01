/**
 * 状态字典：以 07 契约与 docs/impl/03-R1实施口径.md 为准
 * - 订单主状态 6 态：unpaid / undelivered / shipped / confirmed / completed / canceled
 * - 存量只读 2 态：refund(401 退款中) / refunded(402 已退款)
 * - 售后 8 态：applied / waitReturn / waitReceive / refunding / completed / closed / rejected / cancelled
 * 出参已由后端给出 statusText，这里仅做容错兜底与样式映射。
 */

const ORDER_TEXT = {
  unpaid: '待付款',
  undelivered: '待发货',
  shipped: '待收货',
  confirmed: '待评价',
  completed: '已完成',
  canceled: '已取消',
  refund: '退款中',
  refunded: '已退款',
}

const ORDER_TAG = {
  unpaid: 'warn',
  undelivered: '',
  shipped: 'info',
  confirmed: 'ok',
  completed: 'gray',
  canceled: 'gray',
  refund: 'warn',
  refunded: 'gray',
}

// 兼容历史数值出参（101/102/201/301/501/103/401/402）
const ORDER_CODE = {
  101: 'unpaid',
  102: 'undelivered',
  201: 'shipped',
  301: 'confirmed',
  501: 'completed',
  103: 'canceled',
  401: 'refund',
  402: 'refunded',
}

export function orderStatusKey(order) {
  if (!order) return ''
  const raw = order.status
  if (typeof raw === 'number') return ORDER_CODE[raw] || ''
  const s = String(raw || '')
  if (ORDER_TEXT[s]) return s
  if (ORDER_CODE[s]) return ORDER_CODE[s]
  if (s === 'cancelled') return 'canceled'
  return ''
}

export function orderStatusText(order) {
  if (!order) return ''
  if (order.statusText) return order.statusText
  return ORDER_TEXT[orderStatusKey(order)] || '—'
}

export function orderTagClass(order) {
  return ORDER_TAG[orderStatusKey(order)] || 'gray'
}

const AS_TEXT = {
  applied: '申请中',
  waitReturn: '待寄回',
  waitReceive: '待收货',
  refunding: '退款中',
  completed: '退款完成',
  closed: '已关闭',
  rejected: '已驳回',
  cancelled: '已撤销',
  canceled: '已撤销',
}

const AS_TAG = {
  applied: 'warn',
  waitReturn: 'info',
  waitReceive: 'info',
  refunding: 'warn',
  completed: 'ok',
  closed: 'gray',
  rejected: 'gray',
  cancelled: 'gray',
}

const AS_CODE = { 0: 'applied', 1: 'waitReturn', 2: 'waitReceive', 3: 'refunding', 4: 'completed', 5: 'closed', 6: 'rejected', 7: 'cancelled' }

export function asStatusKey(as) {
  if (!as) return ''
  const raw = as.status
  if (typeof raw === 'number') return AS_CODE[raw] || ''
  const s = String(raw || '')
  if (AS_TEXT[s]) return s
  if (AS_CODE[s]) return AS_CODE[s]
  return ''
}

export function asStatusText(as) {
  if (!as) return ''
  if (as.statusText) return as.statusText
  return AS_TEXT[asStatusKey(as)] || '—'
}

export function asTagClass(as) {
  return AS_TAG[asStatusKey(as)] || 'gray'
}

/** 售后类型：1 仅退款 / 2 退货退款 / 0 历史只读 */
export function asTypeText(as) {
  if (!as) return ''
  if (as.typeText) return as.typeText
  const t = Number(as.type)
  if (t === 0) return '历史售后'
  return t === 2 ? '退货退款' : '仅退款'
}

/** 订单行可申请售后（按 canApply 出参；R1 只列可申请行） */
export function canApplyLine(line) {
  return !!(line && line.canApply && Array.isArray(line.canApply.types) && line.canApply.types.length)
}

export const ORDER_TABS = [
  { key: 'all', label: '全部' },
  { key: 'unpaid', label: '待付款' },
  { key: 'undelivered', label: '待发货' },
  { key: 'shipped', label: '待收货' },
  { key: 'unreviewed', label: '待评价' },
  { key: 'finished', label: '已完成' },
  { key: 'aftersale', label: '售后' },
]

export const AS_TABS = [
  { key: 'all', label: '全部' },
  { key: 'processing', label: '处理中' },
  { key: 'finished', label: '已完成' },
  { key: 'closed', label: '已关闭' },
]

/** 售后处理中状态集合（与后端 aftersale 列表筛选口径一致） */
export const AS_PROCESSING = ['applied', 'waitReturn', 'waitReceive', 'refunding']

export default {
  orderStatusKey,
  orderStatusText,
  orderTagClass,
  asStatusKey,
  asStatusText,
  asTagClass,
  asTypeText,
  canApplyLine,
  ORDER_TABS,
  AS_TABS,
  AS_PROCESSING,
}