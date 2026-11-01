import { spawn } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'
const CHROME='C:/Program Files/Google/Chrome/Application/chrome.exe'; const PORT=9343
const chrome=spawn(CHROME,['--headless=new',`--remote-debugging-port=${PORT}`,'--user-data-dir=C:/Users/17896/AppData/Local/Temp/hm-chrome-dbg8','--no-first-run','--disable-gpu','--window-size=390,844','about:blank'],{stdio:'ignore'})
let page; for(let i=0;i<50;i++){try{const l=await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); page=l.find(t=>t.type==='page'); if(page)break}catch{} await sleep(300)}
const ws=new WebSocket(page.webSocketDebuggerUrl); await new Promise(r=>ws.onopen=r)
let seq=0; const pend=new Map()
ws.onmessage=e=>{const m=JSON.parse(e.data); if(m.id&&pend.has(m.id)){pend.get(m.id)(m.result); pend.delete(m.id)}}
const send=(m,p={})=>new Promise(res=>{const id=++seq; pend.set(id,res); ws.send(JSON.stringify({id,method:m,params:p}))})
const ev=async x=>{const r=await send('Runtime.evaluate',{expression:x,awaitPromise:true,returnByValue:true}); if(r.exceptionDetails) return 'EX:'+r.exceptionDetails.text; return r.result?.value}
await send('Page.enable'); await send('Runtime.enable')
await send('Page.navigate',{url:'http://127.0.0.1:5173/pay/9'}); await sleep(2500)
console.log(await ev(`(() => {
  const walk = (node, out) => { const i = node.__vueParentComponent; if (i) out.push(i); [...node.children].forEach(c => walk(c, out)); };
  const list = []; walk(document.body, list);
  const hit = list.find(i => i.setupState && 'state' in i.setupState);
  if (!hit) return 'no pay component';
  const s = hit.setupState;
  return JSON.stringify({state:s.state, info:s.info, url:location.pathname}).slice(0,800);
})()`))
ws.close(); chrome.kill()
