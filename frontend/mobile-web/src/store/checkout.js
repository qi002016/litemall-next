/**
 * 结算态：立即购买（buyNow）与会话内支付失败重试上下文
 * - buyNow 用 sessionStorage 暂存，刷新结算页不丢；下单成功即清除
 * - 与购物车结算（cartIds）二选一，见 07 CI-80/CI-82
 */
import { ref } from 'vue'

const KEY = 'hm_buy_now'

function read() {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const buyNowState = ref(read())

export function setBuyNow(payload) {
  buyNowState.value = payload || null
  try {
    if (payload) sessionStorage.setItem(KEY, JSON.stringify(payload))
    else sessionStorage.removeItem(KEY)
  } catch {
    /* 忽略存储异常 */
  }
}

export function clearBuyNow() {
  setBuyNow(null)
}