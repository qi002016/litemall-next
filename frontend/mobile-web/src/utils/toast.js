/* 轻提示：与 prototype/assets/ui.js 的 toast 一致（无第三方依赖） */
import { reactive } from 'vue'

export const toastState = reactive({ list: [] })

let seed = 0

export function toast(msg, type) {
  const id = ++seed
  toastState.list.push({ id, msg: String(msg || ''), type: type || '' })
  setTimeout(
    () => {
      const i = toastState.list.findIndex((t) => t.id === id)
      if (i >= 0) toastState.list.splice(i, 1)
    },
    type === 'err' ? 2600 : 1900
  )
}

export default toast