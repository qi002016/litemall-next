/**
 * 用 Chrome DevTools Protocol 驱动真实浏览器走 R1 五步，并逐步截图留证。
 * 依赖：本机 Chrome（headless）+ Node 24 全局 WebSocket。
 *
 * 用法：node scripts/ui-walk.mjs
 * 前置：后端 8000 已起；`npm run dev` 已在 5173 监听（脚本会自检）。
 * 产出：docs/evidence/r1-fe/*.png + 控制台 PASS/FAIL
 */
import { spawn } from 'node:child_process'
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { setTimeout as sleep } from 'node:timers/promises'
import { redisGet } from './redis-get.mjs'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const APP = process.env.APP_BASE || 'http://127.0.0.1:5173'
const OUT = 'docs/evidence/r1-fe'
const PORT = 9333
const PROFILE = 'C:/Users/17896/AppData/Local/Temp/hm-chrome-profile'

mkdirSync(OUT, { recursive: true })
rmSync(PROFILE, { recursive: true, force: true })

let fails = 0
function log(step, ok, detail = '') {
  if (!ok) fails += 1
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${step}${detail ? ' :: ' + detail : ''}`)
}

const chrome = spawn(
  CHROME,
  [
    '--headless=new',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${PROFILE}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    '--hide-scrollbars',
    '--window-size=390,844',
    'about:blank',
  ],
  { stdio: 'ignore' }
)

async function cdpTargets() {
  for (let i = 0; i < 60; i += 1) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/list`)
      const list = await r.json()
      const page = list.find((t) => t.type === 'page')
      if (page) return page
    } catch {
      /* retry */
    }
    await sleep(300)
  }
  throw new Error('Chrome CDP 未就绪')
}

const page = await cdpTargets()
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((res, rej) => {
  ws.onopen = res
  ws.onerror = rej
})

let seq = 0
const pending = new Map()
ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data)
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id)
    pending.delete(msg.id)
    if (msg.error) reject(new Error(JSON.stringify(msg.error)))
    else resolve(msg.result)
  }
}
function send(method, params = {}) {
  seq += 1
  const id = seq
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject })
    ws.send(JSON.stringify({ id, method, params }))
  })
}

async function evaluate(expression) {
  const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + JSON.stringify(r.exceptionDetails.exception?.description || '') + ' <<EXPR>> ' + expression.slice(0, 200))
  return r.result?.value
}

async function shot(name) {
  const r = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  writeFileSync(`${OUT}/${name}.png`, Buffer.from(r.data, 'base64'))
  console.log('  · 截图 ' + `${OUT}/${name}.png`)
}

async function goto(path, waitMs = 900) {
  await send('Page.navigate', { url: APP + path })
  await sleep(waitMs)
  await installHelpers()
}

async function waitFor(expr, timeout = 8000, label = expr) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeout) {
    if (await evaluate(expr)) return true
    await sleep(200)
  }
  throw new Error('等待超时: ' + label)
}

// —— 页面内工具：按文本点按钮、按选择器点、输入框赋值（走原生 setter 触发 v-model） ——
const HELPERS = `
window.__clickText = function(sel, text){
  const el = [...document.querySelectorAll(sel)].find(e => (e.innerText||'').trim().includes(text));
  if (!el) return false; el.click(); return true;
};
window.__click = function(sel){ const el = document.querySelector(sel); if(!el) return false; el.click(); return true; };
window.__setInput = function(sel, val){
  const el = document.querySelector(sel); if(!el) return false;
  const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto,'value').set.call(el, val);
  el.dispatchEvent(new Event('input',{bubbles:true}));
  el.dispatchEvent(new Event('change',{bubbles:true}));
  return true;
};
window.__text = function(){ return document.body.innerText; };
window.__flat = function(t){ return String(t == null ? '' : t).split(String.fromCharCode(10)).join(' / '); };
true`

await send('Page.enable')
await send('Runtime.enable')
await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true })

// 每次导航后页面上下文重建，工具函数需重新注入
async function installHelpers() {
  try {
    await evaluate(HELPERS)
  } catch {
    /* 页面尚在加载，下次调用会重试 */
  }
}

try {
  // 0. 通过真实接口注册演示账号并写入 localStorage（等价于走 /login 登录）
  const api = async (method, path, body) => {
    const r = await fetch(APP + path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    return r.json()
  }
  const mobile = '137' + String(Date.now()).slice(-8)
  const cap = await api('POST', '/app/auth/captcha', { phone: mobile, type: 'register' })
  const code = await redisGet('captcha:' + mobile)
  const username = 'ui' + String(Date.now()).slice(-6)
  const reg = await api('POST', '/app/auth/register', {
    username,
    password: 'ui123456',
    mobile,
    code: String(code ?? ''),
  })
  const token = reg?.data?.token
  log('0 注册演示账号', !!token, `username=${username} code=${reg?.code} msg=${reg?.msg}`)
  if (token) {
    // 预置一条收货地址，让结算页可以走完提交（地址页本身另有独立验收）
    const authHeaders = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }
    const addr = await fetch(APP + '/app/address/create', {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        name: 'UI 走查',
        tel: '13800000000',
        province: '浙江省',
        city: '杭州市',
        county: '西湖区',
        addressDetail: '文三路 1 号 UI 走查',
        isDefault: true,
      }),
    }).then((r) => r.json())
    log('0b 预置收货地址', addr?.code === 200, `code=${addr?.code} msg=${addr?.msg}`)
    await goto('/home', 600)
    await evaluate(`localStorage.setItem('litemall_token', ${JSON.stringify(token)});
      localStorage.setItem('litemall_user', JSON.stringify(${JSON.stringify({ id: reg.data.userId, username })}));
      true`)
  }

  // 1. 首页
  await goto('/home', 1600)
  await evaluate(HELPERS)
  await waitFor(`document.querySelectorAll('.x-gcard').length > 0`, 15000, '首页商品卡')
  const homeInfo = await evaluate(`(() => ({
    title: document.querySelector('.title')?.innerText,
    tabs: [...document.querySelectorAll('.c-tab a')].map(a=>a.innerText.trim()),
    cards: document.querySelectorAll('.x-gcard').length,
    hasAi: /AI|助手/.test(document.body.innerText)
  }))()`)
  log('1 首页渲染', homeInfo.cards > 0 && homeInfo.tabs.length === 4, JSON.stringify(homeInfo))
  log('1b 无 AI 入口', !homeInfo.hasAi, `hasAi=${homeInfo.hasAi}`)
  await shot('01-home')

  // 2. 点第一个商品 → 详情选规格
  await evaluate(`__click('.x-gcard')`)
  await sleep(1500)
  await waitFor(`document.querySelectorAll('.spec-opt').length > 0`, 12000, '详情规格项')
  const detInfo = await evaluate(`(() => ({
    url: location.pathname,
    opts: document.querySelectorAll('.spec-opt').length,
    disabled: document.querySelectorAll('.spec-opt.disabled').length
  }))()`)
  log('2 详情页规格区', detInfo.opts > 0, JSON.stringify(detInfo))
  await shot('02-detail-unpicked')

  // 2b. 规格校验：默认已选中的单规格商品可直接加购；未选全时应提示「请先选全规格」
  const unselected = await evaluate(`document.querySelectorAll('.spec-opt.on').length === 0`)
  await evaluate(`document.querySelectorAll('#toast-wrap .toast').forEach(t => t.remove()); true`)
  await evaluate(`__clickText('.c-actionbar .btn', '加入购物车')`)
  await sleep(400)
  const toastText = await evaluate(`(document.querySelector('#toast-wrap')?.innerText || document.querySelector('.toast')?.innerText || '')`)
  if (unselected) {
    log('2b 未选规格不可加购', /选全规格/.test(toastText), `toast=${JSON.stringify(toastText)}`)
  } else {
    log('2b 默认规格可直接加购', /已加入购物车/.test(toastText), `toast=${JSON.stringify(toastText)}`)
  }
  await shot('03-detail-spec-state')

  // 3. 逐组选规格：每组若已默认选中则跳过，否则点第一个可用项
  const optCount = await evaluate(`document.querySelectorAll('.spec-group').length`)
  for (let gi = 0; gi < optCount; gi += 1) {
    const already = await evaluate(`!!document.querySelectorAll('.spec-group')[${gi}]?.querySelector('.spec-opt.on')`)
    if (already) continue
    await evaluate(`(() => {
      const g = document.querySelectorAll('.spec-group')[${gi}];
      const opt = [...g.querySelectorAll('.spec-opt')].find(o => !o.className.includes('disabled') && !o.className.includes('on'));
      if (opt) opt.click();
      return !!opt;
    })()`)
    await sleep(300)
  }
  const picked = await evaluate(`document.querySelectorAll('.spec-opt.on').length`)
  log('3 选全规格', picked >= optCount && optCount > 0, `groups=${optCount} picked=${picked}`)

  // 4. 加入购物车（先清掉上一步残留 toast，避免误读）
  await evaluate(`document.querySelectorAll('#toast-wrap .toast').forEach(t => t.remove()); true`)
  await evaluate(`__clickText('.c-actionbar .btn', '加入购物车')`)
  await sleep(500)
  const addToast = await evaluate(`(document.querySelector('#toast-wrap')?.innerText || document.querySelector('.toast')?.innerText || '')`)
  log('4 加入购物车', /已加入|购物车/.test(addToast), `toast=${JSON.stringify(addToast)}`)
  await shot('04-added-to-cart')

  // 5. 购物车
  await goto('/cart', 1400)
  await waitFor(`document.querySelectorAll('.card').length > 0`, 10000, '购物车行')
  const cartInfo = await evaluate(`(() => ({
    rows: document.querySelectorAll('.card').length,
    btn: [...document.querySelectorAll('.c-actionbar .btn')].map(b=>b.innerText.trim())
  }))()`)
  log('5 购物车渲染', cartInfo.rows > 0, JSON.stringify(cartInfo))
  await shot('05-cart')

  // 6. 去结算
  await evaluate(`__clickText('.c-actionbar .btn', '去结算')`)
  await sleep(1600)
  const coUrl = await evaluate(`location.pathname`)
  const coInfo = await evaluate(`(() => ({
    url: location.pathname,
    hasAddr: !!document.querySelector('.addr-card'),
    text: document.body.innerText.slice(0, 200)
  }))()`)
  log('6 结算页', coUrl.startsWith('/checkout'), JSON.stringify({ url: coInfo.url, hasAddr: coInfo.hasAddr }))
  await shot('06-checkout')

  // 7. 提交订单
  await evaluate(`__clickText('.c-actionbar .btn', '提交订单')`)
  await sleep(2000)
  const payUrl = await evaluate(`location.pathname`)
  const orderId = payUrl.split('/').pop()
  log('7 提交订单进入支付页', payUrl.startsWith('/pay/'), `url=${payUrl}`)
  await shot('07-pay')

  // 8. 支付失败分支
  await evaluate(`__clickText('.c-actionbar .btn', '模拟支付失败')`)
  await sleep(1600)
  const failText = await evaluate(`document.body.innerText`)
  log('8 支付失败可重试', /未付款|重试/.test(failText), `含未付款提示=${/未付款/.test(failText)}`)
  await shot('08-pay-failed')

  // 9. 支付成功分支
  await evaluate(`__clickText('.c-actionbar .btn', '模拟支付成功')`)
  await sleep(2200)
  const okUrl = await evaluate(`location.pathname`)
  const okText = await evaluate(`document.body.innerText.slice(0, 400)`)
  log('9 支付成功进入订单详情', okUrl.startsWith('/order/'), `url=${okUrl}`)
  log('9b 订单详情状态文案', /待发货|已发货|待收货/.test(okText), okText.split('\n').slice(0, 6).join(' / '))
  await shot('09-order-detail')

  // 10. 订单列表分栏（含「已完成」）
  await goto('/orders', 1500)
  const tabText = await evaluate(`[...document.querySelectorAll('.tabs-scroll button')].map(b=>b.innerText.trim()).join('|')`)
  log('10 订单分栏含已完成', /已完成/.test(tabText), tabText)
  await shot('10-orders')

  // 11. AI 直达页显示「未启用」
  await goto('/assistant', 1000)
  const asText = await evaluate(`document.body.innerText`)
  log('11 /assistant 提示未启用', /未启用/.test(asText), asText.split('\n').slice(0, 4).join(' / '))
  await shot('11-assistant-disabled')

  // 12. 分类页
  await goto('/category', 1500)
  const catInfo = await evaluate("(() => ({ side: document.querySelectorAll('.c-side button, .cat-side button, .cat-nav button').length, text: __flat(document.body.innerText).slice(0, 80) }))()")
  log('12 分类页渲染', String(catInfo.text || '').length > 0, JSON.stringify(catInfo))
  await shot('12-category')

  // 13. 搜索页
  await goto('/search', 1200)
  const searchText = await evaluate("__flat(document.body.innerText)")
  log('13 搜索页', /搜索/.test(searchText), searchText.slice(0, 60))
  await shot('13-search')

  // 14. 搜索结果列表
  await evaluate("__setInput('input', '礼盒')")
  await evaluate("__clickText('button', '搜索')")
  await sleep(1800)
  const listUrl = await evaluate("location.pathname + location.search")
  await sleep(1200)
  const listInfo = await evaluate("(() => ({ cards: document.querySelectorAll('.goods-grid > *, .x-gcard').length, head: __flat(document.body.innerText).slice(0, 60) }))()")
  log('14 搜索结果列表', listUrl.includes('/list'), 'url=' + listUrl + ' cards=' + listInfo.cards)
  await shot('14-goods-list')

  // 15. 列表筛选面板
  await evaluate("__click('.x-filter-btn')")
  await sleep(900)
  const sheetText = await evaluate("document.querySelector('.sheet') ? __flat(document.querySelector('.sheet').innerText).slice(0, 80) : ''")
  log('15 筛选面板', String(sheetText).length > 0, 'sheet=' + JSON.stringify(sheetText))
  await shot('15-list-filter')
  await evaluate("__click('.overlay .sheet') ? true : true")
  await goto('/list', 800)

  // 16. 我的
  await goto('/me', 1600)
  const meText = await evaluate("__flat(document.body.innerText)")
  log('16 我的页面', /我的订单/.test(meText) && /优惠券/.test(meText), meText.slice(0, 80))
  await shot('16-me')

  // 17. 优惠券
  await goto('/coupons', 1600)
  const cpText = await evaluate("__flat(document.body.innerText)")
  log('17 优惠券页', /领券中心|我的券/.test(cpText), cpText.slice(0, 80))
  await shot('17-coupons')

  // 18. 收货地址
  await goto('/address', 1500)
  const addrText = await evaluate("__flat(document.body.innerText)")
  log('18 地址页', /收货地址|新增/.test(addrText), addrText.slice(0, 80))
  await shot('18-address')

  // 19. 消息中心
  await goto('/messages', 1500)
  const msgText = await evaluate("__flat(document.body.innerText)")
  log('19 消息中心', /消息/.test(msgText), msgText.slice(0, 80))
  await shot('19-messages')

  // 20. 售后申请页
  await goto('/order/' + orderId, 1600)
  await evaluate("__clickText('.c-actionbar .btn, .btn', '申请售后')")
  await sleep(1500)
  const applyUrl = await evaluate("location.pathname + location.search")
  const applyText = await evaluate("__flat(document.body.innerText)")
  log('20 售后申请页', applyUrl.includes('/aftersale/apply'), 'url=' + applyUrl + ' :: ' + applyText.slice(0, 80))
  await shot('20-aftersale-apply')

  // 21. 售后详情
  if (applyUrl.includes('/aftersale/apply')) {
    await evaluate("__click('.spec-opt')")
    await sleep(300)
    await evaluate("__clickText('.c-actionbar .btn', '提交申请')")
    await sleep(2000)
    console.log('  · apply toast=' + JSON.stringify(await evaluate("document.querySelector('#toast-wrap') ? __flat(document.querySelector('#toast-wrap').innerText) : ''")))
    const asUrl = await evaluate("location.pathname")
    const asDetail = await evaluate("__flat(document.body.innerText)")
    log('21 售后详情', asUrl.includes('/aftersale/') && !asUrl.includes('apply'), 'url=' + asUrl + ' :: ' + asDetail.slice(0, 80))
    await shot('21-aftersale-detail')

    // 22. 售后列表
    await goto('/aftersales', 1500)
    const asList = await evaluate("__flat(document.body.innerText)")
    log('22 售后列表', /售后/.test(asList), asList.slice(0, 80))
    await shot('22-aftersales')
  }

  // 23. 评价页
  await goto('/review?orderId=' + orderId, 1400)
  const rvText = await evaluate("__flat(document.body.innerText)")
  log('23 评价页', /评价/.test(rvText), rvText.slice(0, 80))
  await shot('23-review')

  // 24. 登录页
  await evaluate("localStorage.removeItem('litemall_token'); localStorage.removeItem('litemall_user'); true")
  await goto('/login', 1200)
  const loginText = await evaluate("__flat(document.body.innerText)")
  log('24 登录页', /登录/.test(loginText), loginText.slice(0, 60))
  await shot('24-login')

  // 25. 注册页
  await goto('/register', 1200)
  const regText = await evaluate("__flat(document.body.innerText)")
  log('25 注册页', /注册/.test(regText) && /验证码/.test(regText), regText.slice(0, 60))
  await shot('25-register')
} catch (e) {
  log('执行中断', false, e.message)
  await shot('99-error')
} finally {
  console.log('\nSUMMARY ' + (fails === 0 ? 'ALL PASS' : `${fails} FAIL`))
  try {
    ws.close()
  } catch {
    /* ignore */
  }
  chrome.kill()
  process.exitCode = fails === 0 ? 0 : 1
}