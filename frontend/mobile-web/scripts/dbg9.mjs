import { spawn } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'
import { redisGet } from './redis-get.mjs'
const APP='http://127.0.0.1:5173'
const api=async(m,p,b)=>{const r=await fetch(APP+p,{method:m,headers:{'Content-Type':'application/json'},body:b===undefined?undefined:JSON.stringify(b)});return r.json()}
const phone='139'+String(Date.now()).slice(-8)
await api('POST','/app/auth/captcha',{phone,type:'register'})
const code=await redisGet('captcha:'+phone)
const username='d'+String(Date.now()).slice(-6)
const reg=await api('POST','/app/auth/register',{username,password:'dd123456',mobile:phone,code})
const token=reg.data.token
const auth={'Content-Type':'application/json',Authorization:'Bearer '+token}
// 建地址 + 下单（走 API），再把 token 写进浏览器后直接打开 /pay/{id}
await fetch(APP+'/app/address/create',{method:'POST',headers:auth,body:JSON.stringify({name:'d',tel:'13800000000',province:'浙江省',city:'杭州市',county:'西湖区',addressDetail:'x',isDefault:true})})
const list=await (await fetch(APP+'/app/goods/list?pageNum=1&pageSize=5',{headers:auth})).json()
const g=list.data.list.find(x=>!x.soldOut)
const det=await (await fetch(APP+`/app/goods/detail?goodsId=${g.id}`,{headers:auth})).json()
const sku=det.data.skus.find(s=>s.sellable>0)
await fetch(APP+'/app/cart/add',{method:'POST',headers:auth,body:JSON.stringify({goodsId:String(det.data.id),specId:String(sku.id),number:1})})
const cart=await (await fetch(APP+'/app/cart/list',{headers:auth})).json()
const row=(cart.data.list||cart.data)[0]
const addr=await (await fetch(APP+'/app/address',{headers:auth})).json()
const idem=await (await fetch(APP+'/app/order/idem-token',{method:'POST',headers:auth})).json()
const sub=await (await fetch(APP+'/app/order/submit',{method:'POST',headers:auth,body:JSON.stringify({cartIds:[String(row.id)],addressId:String(addr.data[0].id),idemToken:idem.data.idemToken})})).json()
console.log('order', sub.data?.orderId, sub.msg)
console.log('payResult json:', JSON.stringify(await (await fetch(APP+'/app/pay/result?orderId='+sub.data.orderId,{headers:auth})).json()))
console.log('orderDetail json:', JSON.stringify(await (await fetch(APP+'/app/order/detail?orderId='+sub.data.orderId,{headers:auth})).json()).slice(0,400))
const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'; const PORT=9342
const chrome=spawn(CHROME,['--headless=new',`--remote-debugging-port=${PORT}`,'--user-data-dir=C:/Users/17896/AppData/Local/Temp/hm-chrome-dbg9','--no-first-run','--disable-gpu','--window-size=390,844','about:blank'],{stdio:'ignore'})
let page; for(let i=0;i<50;i++){try{const l=await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); page=l.find(t=>t.type==='page'); if(page)break}catch{} await sleep(300)}
const ws=new WebSocket(page.webSocketDebuggerUrl); await new Promise(r=>ws.onopen=r)
let seq=0; const pend=new Map(); const net=[]
ws.onmessage=e=>{const m=JSON.parse(e.data)
  if(m.method==='Network.responseReceived'&&m.params.response.url.includes('/app/')) net.push(m.params.response.status+' '+m.params.response.url.split('?')[0].replace('http://127.0.0.1:5173',''))
  if(m.id&&pend.has(m.id)){pend.get(m.id)(m.result); pend.delete(m.id)}}
const send=(m,p={})=>new Promise(res=>{const id=++seq; pend.set(id,res); ws.send(JSON.stringify({id,method:m,params:p}))})
const ev=async x=>{const r=await send('Runtime.evaluate',{expression:x,awaitPromise:true,returnByValue:true}); if(r.exceptionDetails) return 'EX:'+r.exceptionDetails.text; return r.result?.value}
await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable')
await send('Page.navigate',{url:APP+'/home'}); await sleep(1500)
await ev(`localStorage.setItem('litemall_token', ${JSON.stringify(token)}); localStorage.setItem('litemall_user', JSON.stringify(${JSON.stringify({id:reg.data.userId,username})})); true`)
await send('Page.navigate',{url:APP+'/pay/'+sub.data.orderId}); await sleep(2500)
console.log('before:', (await ev(`document.body.innerText`)).slice(0,100).replace(/\n/g,' / '))
await ev(`(() => { const b=[...document.querySelectorAll('.c-actionbar .btn')].find(x=>x.innerText.includes('模拟支付失败')); b && b.click(); return !!b; })()`)
await sleep(1800)
console.log('after fail:', (await ev(`document.body.innerText`)).slice(0,160).replace(/\n/g,' / '))
await ev(`document.querySelectorAll('#toast-wrap .toast').forEach(t=>t.remove())`)
await ev(`(() => { const b=[...document.querySelectorAll('.c-actionbar .btn')].find(x=>x.innerText.includes('模拟支付成功')); b && b.click(); return !!b; })()`)
await sleep(3000)
console.log('after success url:', await ev('location.pathname'))
console.log('toast:', await ev(`document.querySelector('#toast-wrap')?.innerText`))
console.log('net:', net.slice(-10).join(' | '))
ws.close(); chrome.kill()
