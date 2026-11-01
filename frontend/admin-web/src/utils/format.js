/**
 * 格式化工具。
 *
 * 约定（06 §4）：金额一律整数分传输，仅在展示层转元；ID 是 BIGINT JSON 字符串，
 * 任何地方都不得 `parseInt` 截断，URL 拼接直接使用字符串原值。
 */

/** 分 → 元（保留 2 位小数），非法/空值返回占位符 */
export function fen2yuan(cents, dash = '—') {
  if (cents === null || cents === undefined || cents === '') return dash
  const n = Number(cents)
  if (!Number.isFinite(n)) return dash
  return (n / 100).toFixed(2)
}

/** 分 → 元（带 ¥ 前缀） */
export function money(cents, dash = '—') {
  const v = fen2yuan(cents, dash)
  return v === dash ? dash : `¥${v}`
}

/** 元（字符串/数字，最多两位小数）→ 分；非法返回 NaN */
export function yuan2fen(yuan) {
  if (yuan === null || yuan === undefined || yuan === '') return NaN
  const n = Number(yuan)
  if (!Number.isFinite(n)) return NaN
  return Math.round(n * 100)
}

/** 空值占位 */
export function dash(value, placeholder = '—') {
  if (value === null || value === undefined || value === '') return placeholder
  return value
}

/**
 * 时间兜底格式化：06 §4 要求 yyyy-MM-dd HH:mm:ss，但接口个别字段仍返回 ISO（如订单列表 addTime）。
 * 统一把 'T' 换成空格并去掉毫秒/时区尾缀，空值返回「—」。
 */
export function fmtTime(value, dash = '—') {
  if (value == null || value === '') return dash
  const s = String(value).trim()
  if (!s) return dash
  if (/^\d{4}-\d{2}-\d{2}T/.test(s)) {
    const m = s.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/)
    if (m) return `${m[1]} ${m[2]}`
    return s.replace('T', ' ')
  }
  return s
}
