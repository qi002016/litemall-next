/* 只读验收工具：无依赖 CDP 驱动（Node 24 内置 WebSocket）。
   仅用于本地验收取证，不属于交付物。 */
'use strict';
const { spawn } = require('child_process');
const os = require('os');
const path = require('path');
const fs = require('fs');

const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

/* 每次运行现取一个空闲端口。
   历史缺陷（L-01）：端口写死 9333 时，若上一轮 headless Chrome 没被回收，
   新一轮会连到那个遗留实例上（孤儿进程 / 旧页面），表现为 r1 只有 7/15 之类的假失败。
   现改为向系统申请临时端口，从根上避免"静默复用遗留实例"。 */
async function freePort() {
  const net = require('net');
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const p = srv.address().port;
      srv.close(() => resolve(p));
    });
  });
}

async function launch() {
  const profile = path.join(os.tmpdir(), 'verify-cdp-' + Date.now());
  const PORT = await freePort();
  const child = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--no-sandbox', '--no-first-run',
    '--remote-debugging-port=' + PORT, '--user-data-dir=' + profile,
    '--window-size=420,900', 'about:blank'
  ], { stdio: 'ignore' });
  const cleanup = () => { try { child.kill(); } catch (e) {} };
  for (let i = 0; i < 100; i++) {
    try {
      const r = await fetch('http://127.0.0.1:' + PORT + '/json/list');
      if (r.ok) {
        const list = await r.json();
        const page = list.find(t => t.type === 'page');
        if (page) return { child, profile, wsUrl: page.webSocketDebuggerUrl, port: PORT };
      }
    } catch (e) { /* not up yet */ }
    await sleep(150);
  }
  cleanup();
  throw new Error('chrome devtools not reachable on port ' + PORT);
}

class Page {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map();
    this.errors = []; this.consoleErrors = [];
    ws.addEventListener('message', ev => {
      const msg = JSON.parse(ev.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
        return;
      }
      if (msg.method === 'Runtime.exceptionThrown') {
        const d = msg.params.exceptionDetails;
        this.errors.push((d.exception && d.exception.description) || d.text);
      }
      if (msg.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(msg.params.type)) {
        this.consoleErrors.push(msg.params.args.map(a => a.value || a.description || a.type).join(' '));
      }
    });
  }
  send(method, params) {
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params: params || {} }));
    });
  }
  async init() {
    await this.send('Runtime.enable');
    await this.send('Page.enable');
    await this.send('Log.enable').catch(() => {});
    await this.send('Emulation.setDeviceMetricsOverride', { width: 420, height: 900, deviceScaleFactor: 1, mobile: false }).catch(() => {});
  }
  async eval(expr, awaitPromise) {
    const r = await this.send('Runtime.evaluate', {
      expression: expr, returnByValue: true, awaitPromise: !!awaitPromise, userGesture: true
    });
    if (r.exceptionDetails) throw new Error('EVAL: ' + ((r.exceptionDetails.exception && r.exceptionDetails.exception.description) || r.exceptionDetails.text) + ' :: ' + expr.slice(0, 120));
    return r.result.value;
  }
  async goto(url) {
    await this.send('Page.navigate', { url });
    await this.waitFor('!!window.App && document.readyState === "complete"', 8000);
    await sleep(250);
  }
  async waitFor(expr, timeout) {
    const t0 = Date.now();
    while (Date.now() - t0 < (timeout || 6000)) {
      try { if (await this.eval(expr)) return true; } catch (e) { /* navigating */ }
      await sleep(100);
    }
    throw new Error('waitFor timeout: ' + expr);
  }
  async rect(sel) {
    return this.eval(`(function(){var e=document.querySelector(${JSON.stringify(sel)});if(!e)return null;var r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:r.width,h:r.height,text:(e.innerText||'').trim().slice(0,40),vis:!!(r.width&&r.height)};})()`);
  }
  /* 真点击：scrollIntoView + 命中测试（toast 遮挡时清 toast 后重试） */
  async _clearToasts() {
    try {
      await this.eval("(function(){var w=document.getElementById('toast-wrap');if(w)[].slice.call(w.querySelectorAll('.toast,.ui-toast')).forEach(function(t){t.remove()});return true;})()");
    } catch (e) {}
  }
  async click(sel, opt) {
    try {
      await this.eval(`(function(){var e=document.querySelector(${JSON.stringify(sel)});if(e&&e.scrollIntoView)e.scrollIntoView({block:'center'});return true;})()`);
    } catch (e) {}
    let last = '';
    for (let i = 0; i < 4; i++) {
      const r = await this.rect(sel);
      if (!r) throw new Error('no element: ' + sel);
      if (!r.vis) throw new Error('element not visible: ' + sel);
      const info = await this.eval(`(function(){var e=document.querySelector(${JSON.stringify(sel)});var t=document.elementFromPoint(${r.x},${r.y});if(!t)return {ok:false,over:'null'};var ok=!!(t===e||e.contains(t));return {ok:ok,over:(t.id?'#'+t.id:'.'+(t.className||'').toString().split(' ')[0])};})()`);
      if (info.ok) {
        await this.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: r.x, y: r.y });
        await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: r.x, y: r.y, button: 'left', clickCount: 1 });
        await this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: r.x, y: r.y, button: 'left', clickCount: 1 });
        await sleep((opt && opt.wait) || 260);
        return r.text;
      }
      last = info.over;
      await this._clearToasts();
      await sleep(80);
    }
    throw new Error('element covered at center: ' + sel + ' (by ' + last + ')');
  }
  /* 文本定位点击：标记命中元素后复用 click（含命中校验） */
  async clickText(text, sel, opt) {
    const mark = 'data-vt-click';
    const found = await this.eval(`(function(){var els=[].slice.call(document.querySelectorAll(${JSON.stringify(sel || 'button,a,.cell,.tag,.x-sub .it')}));[].slice.call(document.querySelectorAll('[${mark}]')).forEach(function(x){x.removeAttribute('${mark}')});for(var i=0;i<els.length;i++){if((els[i].innerText||'').replace(/\\s+/g,' ').indexOf(${JSON.stringify(text)})>=0 && els[i].offsetParent!==null){els[i].setAttribute('${mark}','1');return i;}}return -1;})()`);
    if (found < 0) throw new Error('text not found: ' + text);
    try {
      return await this.click('[' + mark + ']', opt);
    } finally {
      try { await this.eval("(function(){[].slice.call(document.querySelectorAll('[" + mark + "]')).forEach(function(x){x.removeAttribute('" + mark + "')});return true;})()"); } catch (e) {}
    }
  }
  async type(sel, value) {
    await this.eval(`(function(){var e=document.querySelector(${JSON.stringify(sel)});e.focus();e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return e.value;})()`);
    await sleep(80);
  }
  text() { return this.eval('document.body.innerText'); }
  hash() { return this.eval('location.hash'); }
}

async function open() {
  const { child, profile, wsUrl } = await launch();
  const ws = new WebSocket(wsUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const page = new Page(ws);
  await page.init();
  page.close = async () => { try { ws.close(); } catch (e) {} try { child.kill(); } catch (e) {} };
  return page;
}

module.exports = { open, sleep };