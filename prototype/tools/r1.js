/* 只读验收脚本 v2：在真实外壳上点击走通 6.1 R1 十三步。
   仅用于验收取证，不属于交付物。依赖 cdp.js（无第三方包）。
   用法：node r1.js   （需 http://127.0.0.1:8765 以 prototype 为根在线） */
'use strict';
const { open, sleep } = require('./cdp');
const fs = require('fs');
const path = require('path');

const BASE = 'http://127.0.0.1:8765/index.html';
const OUT = path.join(__dirname, 'r1-results.json');
const results = [];
let page;

function rec(id, name, pass, ev) {
  results.push({ id, name, pass: !!pass, evidence: String(ev) });
  console.log((pass ? '[通过]   ' : '[不通过] ') + id + '  ' + name + '\n         ' + String(ev) + '\n');
}

async function nav(h, waitExpr) {
  await page.eval('location.hash=' + JSON.stringify(h));
  await sleep(220);
  if (waitExpr) await page.waitFor(waitExpr, 6000).catch(() => {});
}
async function bodyHas(s) {
  return page.eval('document.body.innerText.indexOf(' + JSON.stringify(s) + ')>=0');
}
async function toastText() {
  return page.eval("(function(){var w=document.getElementById('toast-wrap');return w?w.innerText.trim():'';})()");
}
async function clearToast() {
  await page.eval("(function(){var w=document.getElementById('toast-wrap');if(w)w.innerHTML='';})()");
}
async function sheetErrText() {
  return page.eval("(function(){var e=document.querySelector('.overlay .sheet-err');return e?e.innerText.trim():'';})()");
}
async function sheetBtn(text) {
  await page.clickText(text, '.overlay .sheet-ft button');
  await sleep(320);
}
async function st(expr) {  // expr 里可直接用 S 指 Store.state
  return page.eval('(function(){var S=window.Store.state;return (' + expr + ');})()');
}
function specSel(dim, val) {
  return '.spec-opt[data-act="spec"][data-dim="' + dim + '"][data-val="' + val + '"]';
}
async function specOn(dim, val) {
  return page.eval('(function(){var e=document.querySelector(' + JSON.stringify(specSel(dim, val)) + ');' +
    'return !!e && /(^|\\s)on(\\s|$)/.test(e.className);})()');
}
async function specDisabled(dim, val) {
  return page.eval('(function(){var e=document.querySelector(' + JSON.stringify(specSel(dim, val)) + ');return !!e && e.className.indexOf("disabled")>=0;})()');
}
async function onValOf(dim) {
  return page.eval('(function(){var e=document.querySelector(".spec-opt.on[data-act=\\"spec\\"][data-dim=\\"" + ' + JSON.stringify(dim) + ' + "\\"]");return e?e.getAttribute("data-val"):null;})()');
}
async function clearOtherDims(dim) {
  const dims = await page.eval('(function(){var out=[];document.querySelectorAll(\'.spec-opt[data-act="spec"]\').forEach(function(e){var d=e.getAttribute("data-dim");if(out.indexOf(d)<0)out.push(d);});return out;})()');
  for (let i = 0; i < dims.length; i++) {
    if (dims[i] === dim) continue;
    const ov = await onValOf(dims[i]);
    if (ov) { await page.click(specSel(dims[i], ov)); await sleep(120); }
  }
}
/* 选规格：先关掉同维其它值；若目标被置灰（组合不存在）先清其它维度再点 */
async function selectSpec(dim, val) {
  if (await specOn(dim, val)) return;
  const ov = await onValOf(dim);
  if (ov && ov !== val) { await page.click(specSel(dim, ov)); await sleep(120); }
  if (await specDisabled(dim, val)) await clearOtherDims(dim);
  if (!(await specOn(dim, val))) await page.click(specSel(dim, val));
  await sleep(140);
}
async function goodsBy(id) {
  return page.eval('(function(){var g=window.Store.state.goods.filter(function(x){return x.id===' + JSON.stringify(id) + ';})[0];return g?{id:g.id,name:g.name,skus:g.skus.map(function(s){return {id:s.id,spec:s.specs,price:s.price,number:s.number};}),onSale:g.onSale}:null;})()');
}
async function toastHas(sub) {
  const t = await toastText();
  return t.indexOf(sub) >= 0 ? t : null;
}

async function main() {
  page = await open();
  await page.goto(BASE + '#/c/home');
  await page.eval('window.Store.resetAll(); window.App.refresh();');
  await sleep(300);

  let gid = null, orderId = null, asId = null, itemId = null;

  /* ---------------- R1-1 后台新建 T恤（两个规格） ---------------- */
  try {
    await nav('#/admin/goods', '!!document.querySelector("[data-aact=\\"goods-new\\"]")');
    await page.click('[data-aact="goods-new"]');
    await page.waitFor('!!document.querySelector("#gd-name")');
    await page.type('#gd-name', '验收T恤');
    await page.type('#gd-brief', '验收脚本专用商品');
    async function fillSku(i, color, size, yuanPrice, num) {
      await page.type('tr[data-row="' + i + '"] input[data-k="颜色"]', color);
      await page.type('tr[data-row="' + i + '"] input[data-k="尺码"]', size);
      await page.type('tr[data-row="' + i + '"] .sku-price', String(yuanPrice));
      await page.type('tr[data-row="' + i + '"] .sku-num', String(num));
    }
    await fillSku(0, '黑色', 'L', 99, 10);
    await page.click('#gd-add-sku');
    await sleep(200);
    await fillSku(1, '白色', 'M', 99, 8);
    await sheetBtn('创建商品');
    const g = await page.eval('(function(){var a=window.Store.state.goods.filter(function(x){return x.name==="验收T恤";});return a.length?a[a.length-1]:null;})()');
    const toast = await toastText();
    if (g && g.skus.length === 2) {
      gid = g.id;
      rec('R1-1', '后台新建 T恤（两个规格）', true,
        'toast「' + toast + '」；新商品 id=' + g.id + '，skus=' + g.skus.map(function (s) { return s.specs['颜色'] + '/' + s.specs['尺码'] + ' ¥' + (s.price / 100) + ' 库存' + s.number; }).join('、') + '；#/admin/goods');
    } else {
      rec('R1-1', '后台新建 T恤（两个规格）', false, 'toast=' + toast + '；读出商品=' + JSON.stringify(g));
    }
  } catch (e) { rec('R1-1', '后台新建 T恤（两个规格）', false, '异常：' + e.message); }

  /* ---------------- R1-2 搜索 → 选规格 → 未选全不可加购 ---------------- */
  try {
    await nav('#/c/search', '!!document.querySelector("#kw")');
    await page.type('#kw', 'T恤');
    await page.eval('document.querySelector("#kw").dispatchEvent(new KeyboardEvent("keydown",{key:"Enter",keyCode:13,which:13,bubbles:true}))');
    await sleep(500);
    const h1 = await page.hash();
    const hit = await page.eval('!!document.querySelector(' + JSON.stringify('[data-go="#/c/goods/' + gid + '"]') + ')');
    await page.click('[data-go="#/c/goods/' + gid + '"]');
    await sleep(450);
    const specCount = await page.eval('document.querySelectorAll(\'.spec-opt[data-act="spec"]\').length');
    const priceBefore = await page.eval('(function(){var e=document.querySelector(".detail-price .p");return e?e.innerText.trim():"";})()');
    // 未选全规格 → 加购被拒
    await clearToast();
    await page.click('[data-act="addCart"]');
    await sleep(250);
    const t1 = await toastText();
    const cartN = await st('S.cart.length');
    // 选黑色 / L
    await selectSpec('颜色', '黑色');
    await selectSpec('尺码', 'L');
    const stockBL = await bodyHas('库存 10 件');
    const priceBL = await page.eval('(function(){var e=document.querySelector(".detail-price .p");return e?e.innerText.trim():"";})()');
    // 切到 白色 / M，库存应变 8
    await selectSpec('颜色', '白色');
    await selectSpec('尺码', 'M');
    const stockWM = await bodyHas('库存 8 件');
    const okPass = h1.indexOf('/c/list?kw=') >= 0 && hit && specCount === 4 &&
      t1.indexOf('请先选全规格') >= 0 && cartN === 0 && stockBL && stockWM && priceBL.indexOf('99') >= 0;
    rec('R1-2', '搜「T恤」→ 详情选规格（未选全不可加购）', okPass,
      '搜索跳转 ' + h1 + '；列表命中新商品=' + hit + '；规格项 ' + specCount + ' 个；未选全加购 toast「' + t1 + '」且 cart=' + cartN +
      '；黑色/L 显示库存10件、价 ' + priceBL + '；白色/M 显示库存8件=' + stockWM);
    // R1-2b 规格切换价格随动（用种子 g1 的 XL 规格验证 109 元）
    await nav('#/c/goods/g1', '!!document.querySelector(\'.spec-opt[data-act="spec"]\')');
    await selectSpec('颜色', '黑色');
    await selectSpec('尺码', 'XL');
    const p109 = await bodyHas('¥109');
    await selectSpec('尺码', 'L');
    const p99 = await page.eval('(function(){var e=document.querySelector(".detail-price .p");return e?e.innerText.trim():"";})()');
    rec('R1-2b', '规格切换价格随动（g1 黑色 XL ¥109 → L ¥99）', p109 && p99.indexOf('99') >= 0,
      '选中 黑色/XL 页面出现 ¥109=' + p109 + '；切回 黑色/L 价格=' + p99 + '（#/c/goods/g1）');
  } catch (e) { rec('R1-2', '搜「T恤」→ 详情选规格', false, '异常：' + e.message); }

  /* ---------------- R1-3 两规格加购 → 购物车两行 ---------------- */
  try {
    await nav('#/c/goods/' + gid, '!!document.querySelector(\'.spec-opt[data-act="spec"]\')');
    await selectSpec('颜色', '黑色');
    await selectSpec('尺码', 'L');
    await page.click('[data-act="addCart"]');
    await sleep(300);
    const t1 = await toastText();
    await selectSpec('颜色', '白色');
    await selectSpec('尺码', 'M');
    await page.click('[data-act="addCart"]');
    await sleep(300);
    const lines = await st('S.cart.map(function(l){return l.goodsId+"|"+l.skuId+"|"+l.qty+"|"+l.checked;})');
    await nav('#/c/cart', '!!document.querySelector("[data-act=\\"checkout\\"]")');
    const totalOk = await bodyHas('合计 ¥198');
    const rows = await st('window.Store.cartLines().map(function(l){return l.specText+"×"+l.qty;})');
    const pass = lines.length === 2 && totalOk;
    rec('R1-3', '加购两规格 → 购物车两行', pass,
      '加购 toast「' + t1 + '」；cart=' + JSON.stringify(lines) + '；购物车行=' + JSON.stringify(rows) + '；合计 ¥198=' + totalOk + '（#/c/cart）');
  } catch (e) { rec('R1-3', '加购两规格 → 购物车两行', false, '异常：' + e.message); }

  /* ---------------- R1-4 领券 → 结算用券 → 演示支付成功 ---------------- */
  try {
    await nav('#/c/coupons?tab=center', '!!document.querySelector("[data-act=\\"claim\\"][data-id=\\"cp1\\"]")');
    await clearToast();
    await page.click('[data-act="claim"][data-id="cp1"]');
    await sleep(400);
    const tClaim = await toastText();
    await nav('#/c/cart', '!!document.querySelector("[data-act=\\"checkout\\"]")');
    await page.click('[data-act="checkout"]');
    await sleep(400);
    const hCheckout = await page.hash();
    await page.click('[data-act="coupon"]');
    await page.waitFor('!!document.querySelector("[data-pick=\\"cp1\\"]")');
    await page.click('[data-pick="cp1"]');
    await sleep(400);
    const payTxt = await page.eval('(function(){var e=document.querySelector(".c-actionbar .sum");return e?e.innerText.replace(/\\s+/g," ").trim():"";})()');
    const pay193 = await bodyHas('¥193');
    await page.click('[data-act="submit"]');
    await sleep(500);
    const hPay = await page.hash();
    const demoFlag = await page.eval('(function(){var e=document.querySelector(".demo-flag");return e?e.innerText.trim():"";})()');
    orderId = hPay.split('/c/pay/')[1] || null;
    await page.click('[data-act="payOk"]');
    await sleep(600);
    const o = await page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(orderId) + ');return o?{id:o.id,status:o.status,goods:o.goodsAmount,cp:o.couponAmount,pay:o.payAmount,couponId:o.couponId}:null;})()');
    const nums = (await goodsBy(gid)).skus.map(function (s) { return s.number; });
    const cartEmpty = await st('S.cart.length');
    const uc = await st('S.userCoupons.filter(function(u){return u.couponId==="cp1";}).map(function(u){return u.status;})');
    const pass = tClaim.indexOf('领取成功') >= 0 && hCheckout.indexOf('/c/checkout') >= 0 &&
      pay193 && hPay.indexOf('/c/pay/') >= 0 && demoFlag.indexOf('演示模式') >= 0 &&
      o && o.status === 'wait_ship' && o.pay === 19300 && cartEmpty === 0 && uc[0] === 'used';
    rec('R1-4', '领券 → 结算用券 → 演示支付成功', pass,
      '领券 toast「' + tClaim + '」；结算 ' + hCheckout + '；券选择后应付区「' + payTxt + '」，页面 ¥193=' + pay193 +
      '；支付页 ' + hPay + '，常驻标注「' + demoFlag + '」；支付后订单=' + JSON.stringify(o) + '，库存=' + JSON.stringify(nums) + '，cart=' + cartEmpty + '，userCoupon=' + JSON.stringify(uc));
  } catch (e) { rec('R1-4', '领券 → 结算用券 → 演示支付成功', false, '异常：' + e.message); }

  /* ---------------- R1-5 后台待发货金额一致 ---------------- */
  try {
    await nav('#/admin/orders?tab=wait_ship');
    await page.waitFor('!!document.querySelector(\'[data-aact="ord-detail"][data-id="' + orderId + '"]\')', 6000);
    const rowTxt = await page.eval('(function(){var b=document.querySelector(\'[data-aact="ord-detail"][data-id="' + orderId + '"]\');if(!b)return "";var tr=b.closest("tr");return tr?tr.innerText.replace(/\\s+/g," ").trim():"no-tr";})()');
    const sn = await st('window.Store.orderById(' + JSON.stringify(orderId) + ').sn');
    const pass = rowTxt.indexOf('193') >= 0;
    rec('R1-5', '后台待发货列表金额与 C 端一致（¥193）', pass,
      '订单 ' + sn + ' 在 #/admin/orders?tab=wait_ship 的行文本「' + rowTxt + '」（含 193）');
  } catch (e) { rec('R1-5', '后台待发货列表金额与 C 端一致', false, '异常：' + e.message); }

  /* ---------------- R1-6 发货（空值拦截 + 填单号）→ C 端可见 ---------------- */
  try {
    await nav('#/admin/orders?tab=wait_ship');
    await page.click('[data-aact="ord-ship"][data-id="' + orderId + '"]');
    await page.waitFor('!!document.querySelector("#sp-no")');
    await page.type('#sp-no', '');
    await sheetBtn('确认发货');
    const err = await sheetErrText();
    const stillWait = await st('window.Store.orderById(' + JSON.stringify(orderId) + ').status');
    await page.type('#sp-no', 'SF99887766');
    await page.type('#sp-co', '顺丰速运');
    await sheetBtn('确认发货');
    const track = await st('(function(){var o=window.Store.orderById(' + JSON.stringify(orderId) + ');return o.status+"|"+(o.tracking?o.tracking.company+" "+o.tracking.no:"-");})()');
    await nav('#/c/order/' + orderId, '!!document.querySelector(".c-page")');
    const visible = (await bodyHas('顺丰速运')) && (await bodyHas('SF99887766'));
    const pass = err.indexOf('物流公司与单号均不能为空') >= 0 && stillWait === 'wait_ship' && track.indexOf('shipped') >= 0 && visible;
    rec('R1-6', '发货：空值被拦 → 填公司单号 → C 端可见', pass,
      '空提交弹层红字「' + err + '」且订单仍 ' + stillWait + '；成功发货后 Store=' + track + '；C 端 #/c/order/' + orderId + ' 可见公司单号=' + visible);
  } catch (e) { rec('R1-6', '发货：空值被拦 → 填公司单号 → C 端可见', false, '异常：' + e.message); }

  /* ---------------- R1-7 确认收货 → 售后入口出现 ---------------- */
  try {
    await nav('#/c/order/' + orderId, '!!document.querySelector("[data-act=\\"receive\\"]")');
    await page.click('[data-act="receive"]');
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    await sheetBtn('确认收货');
    await sleep(300);
    const status = await st('window.Store.orderById(' + JSON.stringify(orderId) + ').status');
    const applyBtns = await page.eval('document.querySelectorAll(\'button[data-go^="#/c/aftersale/apply"]\').length');
    rec('R1-7', '确认收货 → 出售后入口', status === 'received' && applyBtns === 2,
      '确认收货弹层 okText「确认收货」；订单状态=' + status + '；商品行「申请售后」按钮数=' + applyBtns + '（#/c/order/' + orderId + '）');
  } catch (e) { rec('R1-7', '确认收货 → 出售后入口', false, '异常：' + e.message); }

  /* ---------------- R1-8 黑色/L 退货退款 + 1 张凭证 ---------------- */
  try {
    const it = await st('(function(){var o=window.Store.orderById(' + JSON.stringify(orderId) + ');var it=o.items.filter(function(x){return x.specText.indexOf("黑色")>=0;})[0];return it?{id:it.id,spec:it.specText,price:it.price}:null;})()');
    itemId = it.id;
    await page.click('button[data-go*="itemId=' + itemId + '"]');
    await sleep(400);
    await page.click('[data-act="type"][data-v="return"]');
    await sleep(250);
    const typeOn = await page.eval('!!document.querySelector(\'[data-act="type"][data-v="return"] .cbx.on\')');
    await page.click('.spec-opt[data-act="reason"]');
    await sleep(220);
    const preview = await page.eval('(function(){var e=document.querySelector(".c-actionbar .sum");return e?e.innerText.replace(/\\s+/g," ").trim():"";})()');
    // 凭证 0 张时先试提交，应被拒
    await clearToast();
    await page.click('[data-act="submit"]');
    await sleep(300);
    const t0 = await toastText();
    const asCount0 = await st('S.aftersales.length');
    await page.click('[data-act="upload"]');
    await sleep(300);
    const photoTxt = await bodyHas('1/6');
    await page.click('[data-act="submit"]');
    await sleep(500);
    asId = (await page.hash()).split('/c/aftersale/')[1] || null;
    const asObj = await page.eval('(function(){var a=window.Store.asById(' + JSON.stringify(asId) + ');return a?{id:a.id,status:a.status,type:a.type,qty:a.qty,amount:a.amount,photos:a.photos.length,reason:a.reason}:null;})()');
    const pass = typeOn && t0.indexOf('凭证') >= 0 && asCount0 === 8 && photoTxt && asObj && asObj.status === 'apply' && asObj.type === 'return' && asObj.photos === 1;
    rec('R1-8', '黑色/L 退货退款 + 1 张凭证', pass,
      '类型选中=' + typeOn + '；0 凭证提交被拒 toast「' + t0 + '」（售后数仍 ' + asCount0 + '）；上传后 ' + photoTxt + '；提交跳 ' + (await page.hash()) +
      '；售后单=' + JSON.stringify(asObj) + '；申请页预计退款「' + preview + '」');
  } catch (e) { rec('R1-8', '黑色/L 退货退款 + 1 张凭证', false, '异常：' + e.message); }

  /* ---------------- R1-9 后台同意 → 填单号 → 确认收货 → 退款完成 ---------------- */
  try {
    await nav('#/admin/aftersales?tab=apply', '!!document.querySelector(\'[data-aact="as-approve"][data-id="' + asId + '"]\')');
    await page.click('[data-aact="as-approve"][data-id="' + asId + '"]');
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    await sheetBtn('确认同意');
    const s1 = await st('window.Store.asById(' + JSON.stringify(asId) + ').status');
    await nav('#/c/aftersale/' + asId, '!!document.querySelector("[data-act=\\"fillTrack\\"]")');
    const addrShown = await bodyHas('退货地址');
    await page.click('[data-act="fillTrack"]');
    await page.waitFor('!!document.querySelector("#as-track")');
    await page.type('#as-track', 'SF55667788');
    await sheetBtn('提交');
    const s2 = await st('window.Store.asById(' + JSON.stringify(asId) + ').status');
    await nav('#/admin/aftersales?tab=wait_receive', '!!document.querySelector(\'[data-aact="as-receive"][data-id="' + asId + '"]\')');
    await page.click('[data-aact="as-receive"][data-id="' + asId + '"]');
    // t4/P-05：确认收到退货已补二次确认（与同意/驳回/跳过/退款一致），先点弹层「确认收到」
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    await sheetBtn('确认收到');
    await sleep(400);
    const s3 = await st('window.Store.asById(' + JSON.stringify(asId) + ').status');
    await nav('#/admin/aftersales?tab=refunding', '!!document.querySelector(\'[data-aact="as-refund"][data-id="' + asId + '"]\')');
    await page.click('[data-aact="as-refund"][data-id="' + asId + '"]');
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    await sheetBtn('确认退款');
    await sleep(500);
    const s4 = await page.eval('(function(){var a=window.Store.asById(' + JSON.stringify(asId) + ');var o=window.Store.orderById(' + JSON.stringify(orderId) + ');var it=o.items.filter(function(x){return x.specText.indexOf("黑色")>=0;})[0];return {as:a.status,restocked:a.restocked,pending:a.restockPending,order:o.status,fully:!!o.fullyRefunded,refundedQty:it.refundedQty,qty:it.qty};})()');
    await nav('#/c/order/' + orderId, '!!document.querySelector(".c-page")');
    const lineTag = await bodyHas('已退款 1 件');
    const orderTag = await bodyHas('已收货');
    const num = (await goodsBy(gid)).skus[0].number;
    const pass = s1 === 'wait_send' && addrShown && s2 === 'wait_receive' && s3 === 'refunding' &&
      s4.as === 'done' && s4.order === 'received' && s4.refundedQty === 1 && lineTag && orderTag && num === 10;
    rec('R1-9', '后台同意 → 填单号 → 确认收货 → 退款完成', pass,
      '同意后 ' + s1 + '，C 端显示退货地址=' + addrShown + '；填单号后 ' + s2 + '；确认收货后 ' + s3 + '；退款完成后 ' + JSON.stringify(s4) +
      '；C 端订单行「已退款 1 件」=' + lineTag + '，主状态仍「已收货」=' + orderTag + '；黑色/L 库存=' + num);
  } catch (e) { rec('R1-9', '后台同意 → 填单号 → 确认收货 → 退款完成', false, '异常：' + e.message); }

  /* ---------------- R1-10 后台库存视图（黑/L 10、白/M 7） ---------------- */
  try {
    await nav('#/admin/stock', '!!document.querySelector("#admin-body")');
    const g = await goodsBy(gid);
    const bodyTxt = await page.eval('(function(){var b=document.querySelector("#admin-body");return b?b.innerText.replace(/\\s+/g," ").slice(0,4000):"";})()');
    const rowTxt = await page.eval('(function(){var b=document.querySelector("#admin-body").innerText;var i=b.indexOf("验收T恤");return i>=0?b.slice(i,i+220).replace(/\\s+/g," "):"";})()');
    const nums = g.skus.map(function (s) { return s.number; });
    rec('R1-10', '库存视图：黑/L 10、白/M 7', nums[0] === 10 && nums[1] === 7 && rowTxt.indexOf('验收T恤') >= 0,
      '#/admin/stock 中商品行「' + rowTxt + '」；Store 实物库存=' + JSON.stringify(nums) + '（回补只加退货行）');
  } catch (e) { rec('R1-10', '库存视图：黑/L 10、白/M 7', false, '异常：' + e.message); }

  /* ---------------- R1-11 另开未付款单 → 后台「立即关单」 ---------------- */
  let unpaidId = null;
  try {
    // 领 cp2（满99减10），下一单用券
    await nav('#/c/coupons?tab=center', '!!document.querySelector("[data-act=\\"claim\\"][data-id=\\"cp2\\"]")');
    await clearToast();
    await page.click('[data-act="claim"][data-id="cp2"]');
    await sleep(350);
    const tClaim2 = await toastText();
    await nav('#/c/goods/g1', '!!document.querySelector(\'[data-act="addCart"]\')');
    await selectSpec('颜色', '黑色');
    await selectSpec('尺码', 'L');
    const lockBefore = await st('window.Store.lockedQty("g1-1")');
    await page.click('[data-act="addCart"]');
    await sleep(300);
    await nav('#/c/cart', '!!document.querySelector("[data-act=\\"checkout\\"]")');
    await page.click('[data-act="checkout"]');
    await page.waitFor('!!document.querySelector("[data-act=\\"coupon\\"]")');
    await page.click('[data-act="coupon"]');
    await page.waitFor('!!document.querySelector("[data-pick=\\"cp2\\"]")');
    await page.click('[data-pick="cp2"]');
    await sleep(350);
    const pay89 = await page.eval('(function(){var e=document.querySelector(".c-actionbar .sum");return e?e.innerText.replace(/\\s+/g," ").trim():"";})()');
    await page.click('[data-act="submit"]');
    await sleep(500);
    unpaidId = (await page.hash()).split('/c/pay/')[1];
    const lockDuring = await st('window.Store.lockedQty("g1-1")');
    await nav('#/admin/demo', '!!document.querySelector(\'[data-aact="demo-close-unpaid"]\')');
    const btnTxt = await page.eval('(function(){var b=document.querySelector(\'[data-aact="demo-close-unpaid"]\');return b?b.innerText.trim():"";})()');
    await page.click('[data-aact="demo-close-unpaid"]');
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    const confirmTxt = await page.eval('(function(){var e=document.querySelector(".overlay .sheet-bd");return e?e.innerText.replace(/\\s+/g," ").trim():"";})()');
    await sheetBtn('立即关单');
    await sleep(500);
    const closed = await page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(unpaidId) + ');return {status:o.status,closedBy:o.closedBy,paidAt:o.paidAt};})()');
    const lockAfter = await st('window.Store.lockedQty("g1-1")');
    const uc = await st('S.userCoupons.filter(function(u){return u.couponId==="cp2";}).map(function(u){return u.status;})');
    const logs = await st('window.Store.demoLog().map(function(l){return l.text;})');
    const logged = logs.some(function (t) { return t.indexOf(unpaidId) >= 0; });
    const toastTxt = await toastText();
    rec('R1-11', '另开未付款单 → 后台「立即关单」→ 释放锁量/退券/日志', closed.status === 'closed' &&
      closed.closedBy === '超时关闭' && lockDuring === lockBefore + 1 && lockAfter === 0 && uc[0] === 'unused' && logged,
      '领 cp2 toast「' + tClaim2 + '」；下单页应付「' + pay89 + '」；演示工具按钮「' + btnTxt + '」；确认框「' + confirmTxt + '」；关单 toast「' + toastTxt +
      '」；订单=' + JSON.stringify(closed) + '；g1-1 锁量 ' + lockBefore + '→提交后' + lockDuring + '→关单后' + lockAfter + '（注意：本条为「一键关闭全部未付款」，非单笔关单）' + '；cp2=' + JSON.stringify(uc) + '；demoLog 含该单=' + logged);
  } catch (e) { rec('R1-11', '另开未付款单 → 后台「立即关单」', false, '异常：' + e.message); }

  /* ---------------- R1-12 C 端手动取消（同 11 的结果） ---------------- */
  let cancelId = null;
  try {
    await nav('#/c/goods/g1', '!!document.querySelector(\'[data-act="addCart"]\')');
    await selectSpec('颜色', '黑色');
    await selectSpec('尺码', 'L');
    await page.click('[data-act="addCart"]');
    await sleep(300);
    await nav('#/c/cart', '!!document.querySelector("[data-act=\\"checkout\\"]")');
    await page.click('[data-act="checkout"]');
    await page.waitFor('!!document.querySelector("[data-act=\\"submit\\"]")');
    const lockBefore = await st('window.Store.lockedQty("g1-1")');
    await page.click('[data-act="submit"]');
    await sleep(500);
    cancelId = (await page.hash()).split('/c/pay/')[1];
    await nav('#/c/order/' + cancelId, '!!document.querySelector(\'[data-act="cancel"]\')');
    await page.click('[data-act="cancel"]');
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    const confirmBody = await page.eval('(function(){var e=document.querySelector(".overlay .sheet-bd");return e?e.innerText.replace(/\\s+/g," ").trim():"";})()');
    await sheetBtn('取消订单');
    await sleep(400);
    const closed = await page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(cancelId) + ');return {status:o.status,closedBy:o.closedBy};})()');
    const lockAfter = await st('window.Store.lockedQty("g1-1")');
    rec('R1-12', 'C 端手动取消 → 与关单同一套释放逻辑', closed.status === 'closed' && closed.closedBy === '用户取消' && lockAfter === lockBefore,
      '取消确认框「' + confirmBody + '」；确认后订单=' + JSON.stringify(closed) + '；锁量 ' + lockBefore + '→' + lockAfter + '（#/c/order/' + cancelId + '）');
  } catch (e) { rec('R1-12', 'C 端手动取消', false, '异常：' + e.message); }

  /* ---------------- R1-13 模拟支付失败 → 仍未付款 → 可重试 ---------------- */
  try {
    await nav('#/c/goods/g1', '!!document.querySelector(\'[data-act="addCart"]\')');
    await selectSpec('颜色', '黑色');
    await selectSpec('尺码', 'L');
    await page.click('[data-act="addCart"]');
    await sleep(300);
    await nav('#/c/cart', '!!document.querySelector("[data-act=\\"checkout\\"]")');
    await page.click('[data-act="checkout"]');
    await page.waitFor('!!document.querySelector("[data-act=\\"submit\\"]")');
    await page.click('[data-act="submit"]');
    await sleep(500);
    const fid = (await page.hash()).split('/c/pay/')[1];
    await clearToast();
    await page.click('[data-act="failPay"]');
    await sleep(500);
    const t = await toastText();
    const alertTxt = await bodyHas('支付失败（演示分支）');
    const st1 = await st('window.Store.orderById(' + JSON.stringify(fid) + ').status');
    const lockFail = await st('window.Store.lockedQty("g1-1")');
    await page.click('[data-act="payOk"]');
    await sleep(600);
    const st2 = await st('window.Store.orderById(' + JSON.stringify(fid) + ').status');
    rec('R1-13', '模拟支付失败 → 仍 unpaid → 可重试成功', t.indexOf('支付失败') >= 0 && alertTxt && st1 === 'unpaid' && st2 === 'wait_ship',
      '失败 toast「' + t + '」，页面提示支付失败=' + alertTxt + '；失败后订单=' + st1 + '，锁量仍 ' + lockFail + '；再点支付成功 → ' + st2 + '（#/c/pay/' + fid + '）');
  } catch (e) { rec('R1-13', '模拟支付失败 → 仍 unpaid → 可重试成功', false, '异常：' + e.message); }

  /* ---------------- 运行期错误汇总 ---------------- */
  const errs = { console: page.consoleErrors.slice(), runtime: page.errors.slice() };
  results.push({ id: 'RUNTIME', name: '控制台/运行期异常', pass: errs.console.length === 0 && errs.runtime.length === 0, evidence: JSON.stringify(errs) });
  console.log('运行期 console.error=' + errs.console.length + '，未捕获异常=' + errs.runtime.length);
  if (errs.console.length) console.log(errs.console.join('\n'));

  fs.writeFileSync(OUT, JSON.stringify({ at: new Date().toISOString(), results: results }, null, 2), 'utf8');
  const passed = results.filter(d => d.pass).length;
  console.log('\n==== ' + passed + '/' + results.length + ' 通过 ====');
  await page.close();
}

main().catch(async e => {
  console.error('FATAL', e);
  try { fs.writeFileSync(OUT, JSON.stringify({ fatal: String(e && e.stack || e), results: results }, null, 2), 'utf8'); } catch (x) {}
  if (page) await page.close();
  process.exit(1);
});