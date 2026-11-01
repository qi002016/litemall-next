/* 数值与时间格式化：与 prototype/assets/ui.js 的 yuan / time / fromNow / countdown 逐字对齐 */

/** 分 → 元字符串（去掉无意义的 .00） */
export function yuan(cent) {
  const n = Math.round(Number(cent) || 0) / 100
  return n.toFixed(2).replace(/\.00$/, '')
}

/** 分 → 带符号金额，如 ¥12.5 */
export function money(cent) {
  return '¥' + yuan(cent)
}

/** 分 → 元字符串（强制两位小数，价格/金额展示统一口径：整数分 → ¥0.00） */
export function yuan2(cent) {
  return (Math.round(Number(cent) || 0) / 100).toFixed(2)
}

/** 分 → 带符号金额（强制两位小数），如 ¥12.50 */
export function money2(cent) {
  return '¥' + yuan2(cent)
}

const pad = (n) => String(n).padStart(2, '0')

function toDate(ts) {
  if (!ts && ts !== 0) return null
  if (ts instanceof Date) return ts
  if (typeof ts === 'number') return new Date(ts)
  // 后端时间：'2024-05-01 12:00:00' / ISO 串
  const s = String(ts).replace('T', ' ').replace(/\.\d+$/, '')
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ ](\d{2}):(\d{2})(?::(\d{2}))?)?/)
  if (m) {
    return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0))
  }
  const d = new Date(s)
  return isNaN(d.getTime()) ? null : d
}

/** 时间戳/时间串 → YYYY-MM-DD HH:mm */
export function time(ts, withTime = true) {
  const d = toDate(ts)
  if (!d) return '—'
  let s = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
  if (withTime !== false) s += ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  return s
}

/** HH:mm */
export function clock(ts) {
  const d = toDate(ts)
  if (!d) return '—'
  return pad(d.getHours()) + ':' + pad(d.getMinutes())
}

/** 相对时间 */
export function fromNow(ts) {
  const d = toDate(ts)
  if (!d) return ''
  let diff = Date.now() - d.getTime()
  if (diff < 0) diff = 0
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  return Math.floor(diff / 86400000) + ' 天前'
}

/** 毫秒 → mm:ss */
export function countdown(ms) {
  if (!(ms > 0)) return '00:00'
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return pad(m) + ':' + pad(s)
}

/** 毫秒 → mm:ss，超过 60 分钟按 hh:mm:ss */
export function countdownLong(ms) {
  if (!(ms > 0)) return '00:00'
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return h > 0 ? pad(h) + ':' + pad(m) + ':' + pad(s) : pad(m) + ':' + pad(s)
}

/** 客户端 UUID（提交订单幂等 token） */
export function uuid() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export default { yuan, money, yuan2, money2, time, clock, fromNow, countdown, countdownLong, uuid }