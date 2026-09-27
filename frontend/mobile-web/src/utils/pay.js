import { showSuccessToast, showFailToast, showLoadingToast, closeToast } from 'vant'
import { payWechat, payResult, payNotifyWechat } from '@/api/order'

/**
 * 支付入口
 * 当前为【模拟支付】：向后端发起回调，由后端置为已支付，再以服务端结果为准。
 * 不再在前端 setTimeout 假装成功（M15/H2：避免前端支付态与后端不一致）。
 * 真实微信支付流程见下方注释，接入微信商户号后切换即可。
 */
export async function payOrder(order) {
  // ============ 真实微信支付流程（先注释保留，后续启用） ============
  // 1. 后端统一下单，返回前端调起支付所需参数（timeStamp/nonceStr/package/paySign/signType）
  // const payParams = await payWechat({ orderId: order.orderId })
  //
  // 2. 调起微信支付（JSSDK / 小程序 wx.requestPayment）
  // WeixinJSBridge.invoke('getBrandWCPayRequest', {
  //   timeStamp: payParams.timeStamp,
  //   nonceStr: payParams.nonceStr,
  //   package: payParams.package,        // prepay_id=xxx
  //   signType: payParams.signType,     // RSA
  //   paySign: payParams.paySign,
  // }, async (res) => {
  //   if (res.err_msg === 'get_brand_wcpay_request:ok') {
  //     // 3. 轮询后端确认支付结果（以服务端为准，不要只信前端回调）
  //     await pollPayResult(order.orderId)
  //   } else {
  //     showFailToast('支付已取消')
  //   }
  // })
  // return

  // ============ 当前：模拟支付——走后端回调链路，以服务端状态为准 ============
  showLoadingToast({ message: '支付中...', forbidClick: true, duration: 0 })
  try {
    // 下单取得支付参数（同时校验订单归属与状态）
    await payWechat({ orderId: order.orderId })
    // dev 明文回调：后端 MockPayNotifyVerifier 解析后入账；生产环境该端点要求微信验签
    await payNotifyWechat({ orderId: order.orderId })
    const paid = await pollPayResult(order.orderId, 3)
    closeToast()
    if (!paid) {
      showFailToast('支付结果确认中，请稍后刷新')
    }
    return paid
  } catch (e) {
    closeToast()
    showFailToast(e.message || '支付失败')
    return false
  }
}

/**
 * 轮询支付结果：每 1.5s 查一次，以服务端 status 为准
 */
async function pollPayResult(orderId, times = 10) {
  for (let i = 0; i < times; i++) {
    await new Promise((r) => setTimeout(r, 1500))
    const res = (await payResult({ orderId })) || {}
    if (res.status === 'paid' || res.paid) {
      showSuccessToast('支付成功')
      return true
    }
  }
  return false
}
