/* 逐路由 DOM 扫描：空屏 / undefined·NaN·[object Object]·TODO / 横向溢出。
   只读，不写业务数据；每个路由先 resetAll 保证种子态。 */
'use strict';
const { open, sleep } = require('./cdp');
const fs = require('fs');
const path = require('path');
const BASE = 'http://127.0.0.1:8765/index.html';

const C = [
  ['c01-home', '#/c/home'], ['c02-search', '#/c/search'], ['c03-category', '#/c/category'],
  ['c04-list', '#/c/list?kw=T%E6%81%A4'], ['c05-goods', '#/c/goods/g1'], ['c06-cart', '#/c/cart'],
  ['c07-checkout', '#/c/checkout'], ['c08-pay', '#/c/pay/o103'], ['c09-orders', '#/c/orders'],
  ['c10-order', '#/c/order/o114'], ['c11-as-apply', '#/c/aftersale/apply?orderId=o114&itemId=it0'],
  ['c12-as-detail', '#/c/aftersale/as100'], ['c13-aftersales', '#/c/aftersales'],
  ['c14-review', '#/c/review?orderId=o114'], ['c15-me', '#/c/me'], ['c16-coupons', '#/c/coupons'],
  ['c17-address', '#/c/address'],
  // 界面改版新增 4 屏（消息 / 收藏 / 关注 / 足迹）
  ['c18-messages', '#/c/messages'], ['c19-favorites', '#/c/favorites'],
  ['c20-follows', '#/c/follows'], ['c21-footprints', '#/c/footprints']
];
const A = [
  ['a01-login', '#/admin/login'], ['a02-dashboard', '#/admin/dashboard'], ['a03-orders', '#/admin/orders'],
  ['a04-aftersales', '#/admin/aftersales'], ['a05-goods', '#/admin/goods'], ['a06-catalog', '#/admin/catalog'],
  ['a07-stock', '#/admin/stock'], ['a08-coupons', '#/admin/coupons'], ['a09-comments', '#/admin/comments'],
  ['a10-demo', '#/admin/demo']
];

(async () => {
  const page = await open();
  await page.goto(BASE + '#/c/home');
  await sleep(500);
  const out = [];
  for (const [id, hash] of C.concat(A)) {
    await page.eval('location.hash=' + JSON.stringify(hash));
    await sleep(420);
    const r = await page.eval(`(function(){
      var app=document.getElementById('root');
      var txt=app?app.innerText:'';
      var bad=[];
      ['undefined','NaN','[object Object]','TODO','null'].forEach(function(t){
        var n=txt.split(t).length-1; if(n) bad.push(t+'×'+n);
      });
      var phone=document.querySelector('.phone'); var body=document.querySelector('.admin-body');
      var el=phone||body||document.body;
      var overflow=(el.scrollWidth-el.clientWidth);
      return {len:txt.replace(/\\s+/g,'').length, bad:bad, overflow:overflow,
              hash:location.hash, adminBody:!!body, phone:!!phone,
              title:(document.querySelector('.admin-head .ttl,.c-head .ttl')||{}).innerText||''};
    })()`);
    out.push(Object.assign({ id, hash }, r));
    console.log(id.padEnd(15), 'chars=' + String(r.len).padStart(5), 'overflow=' + r.overflow, 'bad=' + (r.bad.join(',') || '-'));
  }
  fs.writeFileSync(path.join(__dirname, 'dom-scan.json'), JSON.stringify(out, null, 2));
  await page.eval('window.Store.resetAll()');
  await page.close();
  const empties = out.filter(o => o.len < 20);
  console.log('\n空屏(<20字):', empties.map(o => o.id).join(',') || '无');
  console.log('含可疑文案:', out.filter(o => o.bad.length).map(o => o.id + ':' + o.bad.join(',')).join(' | ') || '无');
  console.log('横向溢出:', out.filter(o => o.overflow > 0).map(o => o.id + ':' + o.overflow).join(',') || '无');
})().catch(e => { console.error('FATAL', e); process.exit(1); });