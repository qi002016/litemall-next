import { reactive } from 'vue'

/**
 * 统一确认弹层（对齐 prototype/assets/ui.js 的 UI.confirm）：
 * 用 Promise<boolean> 返回值，避免原生 window.confirm 打断体验。
 */
export const confirmState = reactive({
  visible: false,
  title: '提示',
  body: '',
  okText: '确定',
  danger: false,
  _resolve: null,
})

export function confirm(options = {}) {
  const { title = '提示', body = '', okText = '确定', danger = false } = options
  confirmState.title = title
  confirmState.body = body
  confirmState.okText = okText
  confirmState.danger = danger
  confirmState.visible = true
  return new Promise((resolve) => {
    confirmState._resolve = resolve
  })
}

export function settleConfirm(result) {
  confirmState.visible = false
  const resolve = confirmState._resolve
  confirmState._resolve = null
  if (resolve) resolve(result)
}