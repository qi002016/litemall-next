/** 调试：详情页规格点击后 DOM 状态 */
import { spawn } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9334
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, '--user-data-dir=C:/Users/17896/AppData/Local/Temp/hm-chrome-dbg', '--no-first-run', '--disable-gpu', '--window-size=390,844', 'about:blank'], { stdio: 'ignore' })

let page
for (let i = 0; i < 50; i += 1) {
  try {
    const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
    page = list.find((t) => t.type === 'page')
    if (page) break
  } catch {}
  await sleep(300)
}
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((r) => (ws.onopen = r))
let seq = 0
const pending = new Map()
ws.onmessage = (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id) }
}
const send = (method, params = {}) => new Promise((res) => { const id = ++seq; pending.set(id, res); ws.send(JSON.stringify({ id, method, params })) })
const ev = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })).result?.value

await send('Page.enable')
await send('Runtime.enable')
await send('Page.navigate', { url: 'http://127.0.0.1:5173/goods/1143006' })
await sleep(2500)
console.log('opts html:', await ev(`document.querySelector('.spec-opts')?.outerHTML`))
console.log('btn bar:', await ev(`document.querySelector('.c-actionbar')?.innerText`))
console.log('click result:', await ev(`(() => { const o = document.querySelector('.spec-opt'); o.click(); return o.className; })()`))
await sleep(600)
console.log('after click on:', await ev(`[...document.querySelectorAll('.spec-opt')].map(o=>o.className).join(' | ')`))
console.log('sku line:', await ev(`document.querySelector('.spec-group .hd')?.innerText`))
ws.close()
chrome.kill()