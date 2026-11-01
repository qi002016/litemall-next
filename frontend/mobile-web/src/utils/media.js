/** 图片地址归一：后端可能返回相对路径（/wx/storage/...）或对象存储绝对地址 */
export function mediaUrl(src) {
  if (!src) return ''
  const s = String(src).trim()
  if (!s) return ''
  if (/^(https?:)?\/\//i.test(s) || s.startsWith('data:') || s.startsWith('blob:')) return s
  if (s.startsWith('/')) return s
  return '/' + s
}

export default { mediaUrl }