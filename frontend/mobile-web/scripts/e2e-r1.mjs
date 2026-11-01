/**
 * R1 C 端五步 E2E（对齐 frontend/mobile-web/src/api 的真实调用与 06/07 契约）：
 *   首页/列表 → 详情选规格 → 加购 → 结算下单 → 演示支付（成功/失败分支）
 *
 * 用法：node scripts/e2e-r1.mjs [gatewayBase]
 * 输出：逐步 PASS/FAIL + 关键响应字段，末行 SUMMARY。
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8000'

import { redisGet } from './redis-get.mjs'

let failures = 0
const results = []

function log(step, ok, detail) {
  results.push({ step, ok, detail })
  const tag = ok ? 'PASS' : 'FAIL'
  if (!ok) failures += 1
  console.log(`[${tag}] ${step}${detail ? ' :: ' + detail : ''}`)
}

async function call(method, path, { token, body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  let json
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = { raw: text }
  }
  return { status: res.status, json }
}

const isOk = (r) => r.json && r.json.code === 200

async function main() {
  // 0. 健康检查
  const health = await call('GET', '/app/goods/list?pageNum=1&pageSize=1')
  log('0 网关可用 /app/goods/list', isOk(health), `code=${health.json?.code} status=${health.status}`)
  if (!isOk(health)) return

  // 1. 注册/登录（演示环境验证码由后端返回）
  const mobile = '139' + String(Date.now()).slice(-8)
  const cap = await call('POST', '/app/auth/captcha', { body: { phone: mobile, type: 'register' } })
  const code = await redisGet(`captcha:${mobile}`)
  log('1a 获取注册验证码', isOk(cap) && !!code, `code=${cap.json?.code} redisCode=${code ? '已取到' : '未取到'}`)
  const username = 'e2e' + String(Date.now()).slice(-6)
  const reg = await call('POST', '/app/auth/register', {
    body: { username, password: 'e2e12345', mobile, code: String(code ?? '') },
  })
  const token = reg.json?.data?.token
  log('1b 注册并拿到 token', isOk(reg) && !!token, `code=${reg.json?.code} msg=${reg.json?.msg}`)
  if (!token) return

  // 2. 首页商品列表（首页 / 列表页同端点）
  const list = await call('GET', '/app/goods/list?pageNum=1&pageSize=20', { token })
  const goodsRows = list.json?.data?.list || []
  log('2 首页/列表商品列表', isOk(list) && goodsRows.length > 0, `total=${list.json?.data?.total} rows=${goodsRows.length}`)
  if (!goodsRows.length) return
  console.log('    样例商品:', JSON.stringify(goodsRows[0]))

  // 3. 详情 + 选规格（选第一个可售 SKU）
  let chosen = null
  for (const g of goodsRows.slice(0, 8)) {
    const det = await call('GET', `/app/goods/detail?goodsId=${g.id}`, { token })
    if (!isOk(det)) continue
    const d = det.json.data
    const sku = (d.skus || []).find((s) => Number(s.sellable ?? s.stock ?? 0) > 0)
    if (sku) {
      chosen = { goods: d, sku }
      log(
        '3 商品详情 + 规格/SKU',
        true,
        `goodsId=${d.id} name=${d.name} price=${sku.price} specText=${sku.specText} skuId=${sku.id} sellable=${sku.sellable}` +
          ` specs=${(d.specs || []).length} availableStock=${d.availableStock}`
      )
      break
    }
  }
  if (!chosen) {
    log('3 商品详情 + 规格/SKU', false, '所有候选商品都没有可售 SKU（检查 CI-32 skus/sellable）')
    return
  }

  // 4. 加购
  const add = await call('POST', '/app/cart/add', {
    token,
    body: { goodsId: String(chosen.goods.id), specId: String(chosen.sku.id), number: 1 },
  })
  log('4 加入购物车', isOk(add), `code=${add.json?.code} msg=${add.json?.msg}`)

  const cart = await call('GET', '/app/cart/list', { token })
  const cartRows = cart.json?.data?.list || cart.json?.data || []
  const row = Array.isArray(cartRows) ? cartRows[0] : null
  log(
    '4b 购物车列表',
    isOk(cart) && !!row,
    `rows=${Array.isArray(cartRows) ? cartRows.length : 'n/a'} id=${row?.id} goodsId=${row?.goodsId} price=${row?.price} invalidReason=${row?.invalidReason ?? 'null'}`
  )
  if (!row) return

  // 5. 地址（没有就建一个）
  const addrList = await call('GET', '/app/address', { token })
  let addressId = (addrList.json?.data || [])[0]?.id
  if (!addressId) {
    const created = await call('POST', '/app/address/create', {
      token,
      body: {
        name: 'E2E 测试',
        tel: '13800000000',
        province: '浙江省',
        city: '杭州市',
        county: '西湖区',
        addressDetail: '文三路 1 号 E2E',
        isDefault: true,
      },
    })
    log('5a 新建收货地址', isOk(created), `code=${created.json?.code}`)
    const again = await call('GET', '/app/address', { token })
    addressId = (again.json?.data || [])[0]?.id
  }
  log('5 收货地址可用', !!addressId, `addressId=${addressId}`)
  if (!addressId) return

  // 6. 结算页试算（CI-80）
  const confirm = await call('POST', '/app/order/confirm', {
    token,
    body: { cartIds: [String(row.id)], addressId: String(addressId) },
  })
  const cdata = confirm.json?.data
  log(
    '6 结算试算 CI-80',
    isOk(confirm),
    `goodsAmount=${cdata?.goodsAmount} freightAmount=${cdata?.freightAmount} couponAmount=${cdata?.couponAmount} orderAmount=${cdata?.orderAmount} lines=${(cdata?.lines || []).length}`
  )

  // 7. 提交订单（CI-82）
  const idemReq = await call('POST', '/app/order/idem-token', { token })
  const idemToken = idemReq.json?.data?.idemToken || 'e2e-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
  log('7a 申请幂等 token', isOk(idemReq) || !!idemToken, `token=${String(idemToken).slice(0, 12)}…`)
  const submit = await call('POST', '/app/order/submit', {
    token,
    body: { cartIds: [String(row.id)], addressId: String(addressId), idemToken, message: 'E2E 自动化下单' },
  })
  const orderId = submit.json?.data?.orderId
  log(
    '7 提交订单 CI-82',
    isOk(submit) && !!orderId,
    `code=${submit.json?.code} msg=${submit.json?.msg} orderId=${orderId} orderSn=${submit.json?.data?.orderSn} orderAmount=${submit.json?.data?.orderAmount} expireTime=${submit.json?.data?.expireTime}`
  )
  if (!orderId) return

  // 8. 支付失败分支（订单仍应未付款、可重试）
  const payFail = await call('POST', '/app/pay/wechat', { token, body: { orderId: String(orderId), mode: 'fail' } })
  const failResult = await call('GET', `/app/pay/result?orderId=${orderId}`, { token })
  log(
    '8 支付失败分支不动订单',
    isOk(payFail) && failResult.json?.data?.status === 'unpaid' && payFail.json?.data?.payResult === 'fail',
    `payResult=${payFail.json?.data?.payResult} payStatus=${failResult.json?.data?.status} orderStatus=${failResult.json?.data?.orderStatus}`
  )

  // 9. 支付成功分支：CI-103 只返回支付参数，入账由 CI-104 回调完成（dev 明文 {orderId, payId}）
  const payOk = await call('POST', '/app/pay/wechat', { token, body: { orderId: String(orderId), mode: 'success' } })
  const notify = await call('POST', '/app/pay/notify/wechat', {
    token,
    body: { orderId: Number(orderId), payId: 'wxmock' + orderId },
  })
  let okResult = await call('GET', `/app/pay/result?orderId=${orderId}`, { token })
  for (let i = 0; i < 10 && okResult.json?.data?.status !== 'paid'; i += 1) {
    await new Promise((r) => setTimeout(r, 500))
    okResult = await call('GET', `/app/pay/result?orderId=${orderId}`, { token })
  }
  log(
    '9 支付成功（CI-103 + CI-104 回调）',
    isOk(payOk) && isOk(notify) && okResult.json?.data?.status === 'paid',
    `payResult=${payOk.json?.data?.payResult} notifyCode=${notify.json?.code} payStatus=${okResult.json?.data?.status} payId=${okResult.json?.data?.payId}`
  )

  // 10. 订单详情（新契约字段）
  const det = await call('GET', `/app/order/detail?orderId=${orderId}`, { token })
  const od = det.json?.data
  log(
    '10 订单详情 CI-84',
    isOk(det) && !!od?.statusText && Array.isArray(od?.lines),
    `status=${od?.status} statusText=${od?.statusText} orderAmount=${od?.orderAmount} lines=${(od?.lines || []).length} canApply=${JSON.stringify((od?.lines || [])[0]?.canApply)}`
  )

  // 11. 订单列表分栏（含「已完成」栏）
  const listUnpaid = await call('GET', '/app/order/list?tab=unpaid&pageNo=1&pageSize=5', { token })
  const listFinished = await call('GET', '/app/order/list?tab=finished&pageNo=1&pageSize=5', { token })
  log(
    '11 订单列表分栏 CI-83',
    isOk(listUnpaid) && isOk(listFinished),
    `unpaid.total=${listUnpaid.json?.data?.total} finished.total=${listFinished.json?.data?.total}`
  )

  // 12. 售后可申请行（订单行 canApply 应按行给出）
  const od2 = await call('GET', `/app/order/detail?orderId=${orderId}`, { token })
  const line = (od2.json?.data?.lines || [])[0]
  log(
    '12 售后按行可申请',
    !!line?.canApply,
    `orderGoodsId=${line?.orderGoodsId} types=${JSON.stringify(line?.canApply?.types)} maxQty=${line?.canApply?.maxQty}`
  )

  console.log('\nSUMMARY ' + (failures === 0 ? 'ALL PASS' : `${failures} FAIL`) + ` (${results.length} steps)`)
  process.exitCode = failures === 0 ? 0 : 1
}

main().catch((e) => {
  console.log('[FAIL] 未捕获异常 :: ' + (e && e.stack ? e.stack : e))
  console.log('\nSUMMARY 1 FAIL')
  process.exitCode = 1
})