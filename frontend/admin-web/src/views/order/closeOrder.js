import { ElMessage, ElMessageBox } from 'element-plus'
import { orderClose, orderDetail } from '@/api/order'

/**
 * 「立即关单」的强制二次确认（R1-07 硬要求 / CI-26 + CN-10）。
 *
 * 第二次确认弹窗展示释放的锁定库存数量与优惠券处理口径；用户在弹窗中再次点击
 * 「立即关单」才会真正调用 `POST /admin/order/close`，取消则不做任何写入。
 * 订单详情取不到时降级为不含件数的文案，不阻断关单流程（detail 调用为静默失败）。
 */
export async function closeOrderFlow(order) {
  if (!order?.id) {
    ElMessage.error('订单标识缺失，无法关单')
    return false
  }

  let detail = null
  try {
    detail = await orderDetail(order.id, { silent: true })
  } catch {
    /* 详情不可读时降级为不含件数的文案 */
  }

  const lines = Array.isArray(detail?.lines) ? detail.lines : []
  const lockedQty = lines.length ? lines.reduce((sum, line) => sum + Number(line.number || 0), 0) : null
  const stockText = lockedQty == null ? '释放该订单锁定的库存' : `释放锁定库存 ${lockedQty} 件`

  const message =
    `按超时关闭处理：${stockText}、优惠券退回未使用且不延长有效期（BR-ORD-13）。` +
    '只影响当前订单，其他未付款订单保持不变。'

  await ElMessageBox.confirm(message, `关闭订单 ${order.orderSn || order.id}`, {
    confirmButtonText: '立即关单',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger',
    distinguishCancelAndClose: true,
  })

  await orderClose({ orderId: order.id, reason: '后台立即关单' })
  ElMessage.success(lockedQty == null ? '订单已关闭' : `已关单 1 笔，释放库存 ${lockedQty} 件`)
  return true
}