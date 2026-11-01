/** 探查：支付成功分支是否最终一致（pay→order 回调/Seata 事务），poll 15s。 */
import { redisGet } from './redis-get.mjs'

const B = 'http://127.0.0.1:8000'
const call = async (method, path, { token, body } = {}) => {
  const r = await fetch(B + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await r.text()
  try {
    return JSON.parse(text)
  } catch {
    return { raw: text }
  }
}

const phone = '136' + String(Date.now()).slice(-8)
await call('POST', '/app/auth/captcha', { body: { phone, type: 'register' } })
const code = await redisGet('captcha:' + phone)
const reg = await call('POST', '/app/auth/register', { body: { username: 'p' + String(Date.now()).slice(-6), password: 'pp123456', mobile: phone, code } })
const token = reg.data.token
console.log('token ok', !!token)

const list = await call('GET', '/app/goods/list?pageNum=1&pageSize=5', { token })
const g = list.data.list.find((x) => !x.soldOut)
const det = await call('GET', `/app/goods/detail?goodsId=${g.id}`, { token })
const sku = det.data.skus.find((s) => s.sellable > 0)
await call('POST', '/app/cart/add', { token, body: { goodsId: String(det.data.id), specId: String(sku.id), number: 1 } })
const cart = await call('GET', '/app/cart/list', { token })
const row = (cart.data.list || cart.data)[0]
const addr = await call('GET', '/app/address', { token })
let addressId = (addr.data || [])[0]?.id
if (!addressId) {
  await call('POST', '/app/address/create', {
    token,
    body: { name: '探查', tel: '13800000000', province: '浙江省', city: '杭州市', county: '西湖区', addressDetail: '文三路 1 号', isDefault: true },
  })
  addressId = ((await call('GET', '/app/address', { token })).data || [])[0]?.id
}
const idem = await call('POST', '/app/order/idem-token', { token })
const sub = await call('POST', '/app/order/submit', {
  token,
  body: { cartIds: [String(row.id)], addressId: String(addressId), idemToken: idem.data.idemToken, message: 'pay probe' },
})
if (!sub.data) { console.log("submit failed:", JSON.stringify(sub).slice(0,300)); process.exit(1) }
const orderId = sub.data.orderId
console.log('order', orderId, sub.data.orderSn)

const pay = await call('POST', '/app/pay/wechat', { token, body: { orderId: String(orderId), mode: 'success' } })
console.log('pay resp', JSON.stringify(pay).slice(0, 300))

for (let i = 1; i <= 15; i += 1) {
  const res = await call('GET', `/app/pay/result?orderId=${orderId}`, { token })
  const det2 = await call('GET', `/app/order/detail?orderId=${orderId}`, { token })
  console.log(`t+${i}s pay=${res.data?.status} payId=${res.data?.payId} order=${det2.data?.status}/${det2.data?.statusText}`)
  if (res.data?.status === 'paid') break
  await new Promise((r) => setTimeout(r, 1000))
}