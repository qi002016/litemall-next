import { spawn } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'
const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'; const PORT=9341
const chrome=spawn(CHROME,['--headless=new',`--remote-debugging-port=${PORT}`,'--user-data-dir=C:/Users/17896/AppData/Local/Temp/hm-chrome-dbg8','--no-first-run','--disable-gpu','--window-size=390,844','about:blank'],{stdio:'ignore'})
let page; for(let i=0;i<50;i++){try{const l=await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); page=l.find(t=>t.type==='page'); if(page)break}catch{} await sleep(300)}
const ws=new WebSocket(page.webSocketDebuggerUrl); await new Promise(r=>ws.onopen=r)
let seq=0; const pend=new Map(); const net=[]
ws.onmessage=e=>{const m=JSON.parse(e.data)
  if(m.method==='Network.responseReceived'&&m.params.response.url.includes('/app/')) net.push(m.params.response.status+' '+m.params.response.url.split('?')[0])
  if(m.id&&pend.has(m.id)){pend.get(m.id)(m.result); pend.delete(m.id)}}
const send=(m,p={})=>new Promise(res=>{const id=++seq; pend.set(id,res); ws.send(JSON.stringify({id,method:m,params:p}))})
const ev=async x=>{const r=await send('Runtime.evaluate',{expression:x,awaitPromise:true,returnByValue:true}); if(r.exceptionDetails) return 'EX:'+r.exceptionDetails.text; return r.result?.value}
await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
await send('Page.navigate',{url:'http://127.0.0.1:5173/pay/7'}); await sleep(2500)
console.log('before:', await ev(`document.body.innerText.slice(0,120)`))
await ev(`(() => { const b=[...document.querySelectorAll('.c-actionbar .btn')].find(x=>x.innerText.includes('模拟支付失败')); b && b.click(); return !!b; })()`)
await sleep(1800)
console.log('after fail:', await ev(`document.body.innerText.slice(0,200)`))
await ev(`document.querySelectorAll('#toast-wrap .toast').forEach(t=>t.remove())`)
await ev(`(() => { const b=[...document.querySelectorAll('.c-actionbar .btn')].find(x=>x.innerText.includes('模拟支付成功')); b && b.click(); return !!b; })()`)
await sleep(2500)
console.log('after success url:', await ev('location.pathname'))
console.log('toast:', await ev(`document.querySelector('#toast-wrap')?.innerText`))
console.log('net:', net.slice(-8).join(' | '))
ws.close(); chrome.kill()
