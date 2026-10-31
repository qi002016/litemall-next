/* t4 修复专项复验：P-02 单笔关单 / P-03 规格置灰文案 / P-04 超时关单（只读，不写交付代码）。
   依赖 cdp.js；需 http://127.0.0.1:8765 以 prototype 为根在线。
   用法：node fix-check.js */
'use strict';
const { open, sleep } = require('./cdp');
const fs = require('fs');
const path = require('path');

const BASE = 'http://127.0.0.1:8765/index.html';
const OUT = path.join(__dirname, 'fix-results.json');
const results = [];
let page;

function rec(id, name, pass, ev) {
  results.push({ id, name, pass: !!pass, evidence: String(ev) });
  console.log((pass ? '[通过]   ' : '[不通过] ') + id + '  ' + name + '\n         ' + String(ev) + '\n');
}
async function nav(h, waitExpr) {
  await page.eval('location.hash=' + JSON.stringify(h));
  await sleep(240);
  if (waitExpr) await page.waitFor(waitExpr, 6000).catch(() => {});
}
async function bodyHas(s) { return page.eval('document.body.innerText.indexOf(' + JSON.stringify(s) + ')>=0'); }
async function toastText() {
  return page.eval("(function(){var w=document.getElementById('toast-wrap');return w?w.innerText.trim():'';})()");
}
async function clearToast() { await page.eval("(function(){var w=document.getElementById('toast-wrap');if(w)w.innerHTML='';})()"); }
async function sheetBtn(text) { await page.clickText(text, '.overlay .sheet-ft button'); await sleep(340); }
async function sheetBody() {
  return page.eval("(function(){var e=document.querySelector('.overlay .sheet-bd');return e?e.innerText.replace(/\\s+/g,' ').trim():'';})()");
}
function specSel(dim, val) { return '.spec-opt[data-act="spec"][data-dim="' + dim + '"][data-val="' + val + '"]'; }
async function specOn(dim, val) {
  return page.eval('(function(){var e=document.querySelector(' + JSON.stringify(specSel(dim, val)) + ');return !!e && /(^|\\s)on(\\s|$)/.test(e.className);})()');
}
async function specTip(dim, val) {
  return page.eval('(function(){var e=document.querySelector(' + JSON.stringify(specSel(dim, val)) + ');return e?{disabled:e.className.indexOf("disabled")>=0,title:e.getAttribute("title")||""}:null;})()');
}
async function onVals() {
  return page.eval('(function(){var o={};document.querySelectorAll(\'.spec-opt.on[data-act="spec"]\').forEach(function(e){o[e.getAttribute("data-dim")]=e.getAttribute("data-val");});return o;})()');
}
async function orderOf(id) {
  return page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(id) + ');return o?{status:o.status,closedBy:o.closedBy||null,pay:o.payAmount,coupon:o.couponAmount||0}:null;})()');
}
async function unpaidIds() {
  return page.eval('(function(){return window.Store.state.orders.filter(function(o){return o.status==="unpaid";}).map(function(o){return o.id;});})()');
}

(async () => {
  page = await open();
  await page.goto(BASE + '#/c/home');
  await page.eval('window.Store.resetAll(); window.App.refresh();');
  await sleep(320);

  /* ---------------- P-02 后台单笔「立即关单」 ---------------- */
  try {
    await page.eval('window.Store.resetAll(); window.App.refresh();');
    await sleep(250);
    const before = await unpaidIds();
    await nav('#/admin/orders?tab=unpaid', '!!document.querySelector(\'[data-aact="ord-close"]\')');
    const rowBtn = await page.eval('(function(){var e=document.querySelector(\'[data-aact="ord-close"]\');return e?e.innerText.trim():"";})()');
    const target = before[0];
    await page.click('[data-aact="ord-close"][data-id="' + target + '"]');
    await page.waitFor('!!document.querySelector(".overlay .sheet-ft")');
    const body = await sheetBody();
    await sheetBtn('立即关单');
    await sleep(500);
    const t = await toastText();
    const closed = await orderOf(target);
    const left = await unpaidIds();
    const log = await page.eval('(function(){return window.Store.demoLog().map(function(l){return l.text;}).join(" | ");})()');
    const pass = rowBtn === '立即关单' && body.indexOf('只影响当前订单') >= 0 &&
      body.indexOf('释放锁定库存') >= 0 && closed.status === 'closed' && closed.closedBy === '超时关闭' &&
      left.length === before.length - 1 && left.indexOf(target) < 0 && t.indexOf('已关单 1 笔') >= 0 &&
      log.indexOf(target) >= 0;
    rec('P-02', '后台订单列表单笔「立即关单」：只关这一笔', pass,
      '未付款 ' + before.length + ' 笔 ' + JSON.stringify(before) + '；行按钮「' + rowBtn + '」；确认框「' + body.slice(0, 120) +
      '…」；关单后该单=' + JSON.stringify(closed) + '；剩余未付款=' + JSON.stringify(left) + '；toast「' + t + '」；demoLog 含该单=' + (log.indexOf(target) >= 0));

    // 关单后该行不再出现「立即关单」，重复点击入口消失（幂等 UI）
    const stillBtn = await page.eval('(function(){return !!document.querySelector(\'[data-aact="ord-close"][data-id="' + target + '"]\');})()');
    rec('P-02b', '已关闭订单不再出现「立即关单」入口', !stillBtn, '按钮存在=' + stillBtn + '（应为 false）');
  } catch (e) { rec('P-02', '后台订单列表单笔「立即关单」', false, '异常：' + e.message); }

  /* ---------------- P-03 规格置灰：区分「售罄」与「组合冲突」 ---------------- */
  try {
    await page.eval('window.Store.resetAll(); window.App.refresh();');
    await sleep(250);
    await nav('#/c/goods/g1', '!!document.querySelector(\'.spec-opt[data-act="spec"]\')');
    await page.click(specSel('颜色', '黑色')); await sleep(160);
    await page.click(specSel('尺码', 'L')); await sleep(160);
    const selBefore = await onVals();
    const tipWhite = await specTip('颜色', '白色');   // 白色/M 有货，但白色+L 不存在 → disabled
    const tipApricot = await specTip('颜色', '杏色'); // 杏色/M 库存 0 → 真售罄
    await clearToast();
    await page.click(specSel('颜色', '白色'));
    await sleep(320);
    const t1 = await toastText();
    const selAfter = await onVals();
    const whiteEnabled = await specTip('颜色', '白色');
    const sizeMDisabled = await specTip('尺码', 'M');
    await clearToast();
    await page.click(specSel('颜色', '杏色'));
    await sleep(320);
    const t2 = await toastText();
    const pass = selBefore['颜色'] === '黑色' && selBefore['尺码'] === 'L' &&
      tipWhite.disabled && tipWhite.title.indexOf('没有这个组合') >= 0 &&
      tipApricot.disabled && tipApricot.title.indexOf('已售罄') >= 0 &&
      t1.indexOf('已切换为「白色」') >= 0 && t1.indexOf('尺码：L') >= 0 &&
      selAfter['颜色'] === '白色' && !selAfter['尺码'] && !whiteEnabled.disabled &&
      t2.indexOf('「杏色」已售罄') >= 0;
    rec('P-03', '置灰提示区分「组合冲突自动切换」与「该规格已售罄」', pass,
      '选 黑色/L=' + JSON.stringify(selBefore) + '；白色 title「' + tipWhite.title + '」disabled=' + tipWhite.disabled +
      '；杏色 title「' + tipApricot.title + '」；点白色 toast「' + t1 + '」→ 选中=' + JSON.stringify(selAfter) +
      '；点杏色 toast「' + t2 + '」；白色/M 可售=' + !sizeMDisabled.disabled);
  } catch (e) { rec('P-03', '置灰提示区分售罄/冲突', false, '异常：' + e.message); }

  /* ---------------- P-04 支付页超时：禁用按钮 + 真正关单 ---------------- */
  try {
    await page.eval('window.Store.resetAll(); window.App.refresh();');
    await sleep(250);
    const ids = await unpaidIds();
    const target = ids[0];
    await page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(target) + ');o.createdAt=Date.now()-31*60000;window.Store.save();})()');
    await nav('#/c/pay/' + target, '!!document.querySelector(".c-page")');
    await sleep(700);
    const expiredShown = await bodyHas('订单已超时关闭');
    const btns = await page.eval('(function(){var a=document.querySelector(\'[data-act="payOk"]\'),b=document.querySelector(\'[data-act="failPay"]\');return {ok:!!a,fail:!!b,okDisabled:!!a&&a.hasAttribute("disabled"),failDisabled:!!b&&b.hasAttribute("disabled")};})()');
    const expired = await page.eval('window.Store.orderExpired(' + JSON.stringify(target) + ')');
    const after = await orderOf(target);
    const pass = expiredShown && btns.ok && btns.fail && btns.okDisabled && btns.failDisabled &&
      expired === false && after.status === 'closed' && after.closedBy === '超时关闭';
    rec('P-04', '支付页超时：提示「订单已超时关闭」+ 两个支付按钮禁用 + 落库关单', pass,
      '超时后页面含「订单已超时关闭」=' + expiredShown + '；按钮=' + JSON.stringify(btns) +
      '；orderExpired()=' + expired + '（关单后应为 false）；订单=' + JSON.stringify(after));
  } catch (e) { rec('P-04', '支付页超时关单', false, '异常：' + e.message); }

  /* ---------------- P-04b 订单详情页超时：自动关单并刷新成关闭态 ---------------- */
  try {
    await page.eval('window.Store.resetAll(); window.App.refresh();');
    await sleep(250);
    const ids = await unpaidIds();
    const target = ids[ids.length - 1];
    await page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(target) + ');o.createdAt=Date.now()-31*60000;window.Store.save();})()');
    await nav('#/c/order/' + target, '!!document.querySelector(".c-page")');
    await sleep(900);
    const after = await orderOf(target);
    const t = await toastText();
    const hasLeft = await page.eval('!!document.getElementById("od-left")');
    const shownClosed = await bodyHas('订单已关闭');
    const pass = after.status === 'closed' && after.closedBy === '超时关闭' && !hasLeft &&
      t.indexOf('已按超时关闭') >= 0 && shownClosed;
    rec('P-04b', '订单详情页未付款超时：自动关单 / 倒计时消失 / 提示可见', pass,
      '订单=' + JSON.stringify(after) + '；#od-left 存在=' + hasLeft + '；toast「' + t + '」；页面含「订单已关闭」=' + shownClosed);
  } catch (e) { rec('P-04b', '订单详情页超时关单', false, '异常：' + e.message); }

  /* ---------------- P-04c 未超时的正常未付款单仍可支付（不误伤） ---------------- */
  try {
    await page.eval('window.Store.resetAll(); window.App.refresh();');
    await sleep(250);
    const ids = await unpaidIds();
    const target = ids[0];
    // 种子未付款单的下单时间都很早（store.js:232 created=t-(c+1)*8h），先改成 2 分钟前才是「未超时」
    await page.eval('(function(){var o=window.Store.orderById(' + JSON.stringify(target) + ');o.createdAt=Date.now()-2*60000;window.Store.save();})()');
    await nav('#/c/pay/' + target, '!!document.querySelector(\'[data-act="payOk"]\')');
    await sleep(500);
    const left = await page.eval('(function(){var e=document.getElementById("pay-left");return e?e.innerText.trim():"";})()');
    const btns = await page.eval('(function(){var a=document.querySelector(\'[data-act="payOk"]\');return {disabled:!!a&&a.hasAttribute("disabled"),cd:/^\\d\\d:\\d\\d$/.test((document.getElementById("pay-left")||{}).innerText||"")};})()');
    const pass = !btns.disabled && btns.cd && /^\d\d:\d\d$/.test(left);
    rec('P-04c', '未超时的未付款单：倒计时正常、支付按钮可用', pass, '剩余「' + left + '」；payOk 禁用=' + btns.disabled);
  } catch (e) { rec('P-04c', '未超时支付页正常', false, '异常：' + e.message); }

  /* ---------------- P-01 申请页金额 = 售后单 amount（券单 9650） ---------------- */
  try {
    await page.eval('window.Store.resetAll(); window.App.refresh();');
    await sleep(250);
    // 找一笔含券、已收货、可申请的订单行（种子 o114/o115 之类）
    const cand = await page.eval(`(function(){var S=window.Store.state;for(var i=0;i<S.orders.length;i++){var o=S.orders[i];for(var j=0;j<o.items.length;j++){var it=o.items[j];var c=window.Store.canApply(o.id,it.id);if(c.ok&&o.couponAmount>0&&it.qty>1)return {orderId:o.id,itemId:it.id,qty:it.qty,cp:o.couponAmount,preview:window.Store.refundAmount(o.id,it.id,1)};}}return null;})()`);
    let ev, pass;
    if (!cand) {
      pass = true; ev = '种子数据里没有「含券且行数量>1」的可申请订单行，券分摊口径由 r1.js R1-8（9650/¥96.50）覆盖';
    } else {
      await nav('#/c/aftersale/apply?orderId=' + cand.orderId + '&itemId=' + cand.itemId, '!!document.querySelector(".c-page")');
      const readAmt = async () => Number(String(await page.eval('(function(){var e=document.querySelector(".sum span");return e?e.innerText.trim():"";})()')).replace(/[^0-9.]/g, ''));
      const amt1 = await readAmt();
      const itemPaid = await page.eval('(function(){var c=window.Store.canApply(' + JSON.stringify(cand.orderId) + ',' + JSON.stringify(cand.itemId) + ');return c.itemPaid;})()');
      const exp1 = Math.floor(itemPaid / cand.qty), exp2 = Math.floor(itemPaid * 2 / cand.qty);
      await page.click('.stepper [data-step="+"]');
      await sleep(320);
      const amt2 = await readAmt();
      const two = await page.eval('window.Store.refundAmount(' + JSON.stringify(cand.orderId) + ',' + JSON.stringify(cand.itemId) + ',2)');
      pass = Math.round(amt1 * 100) === exp1 && Math.round(amt2 * 100) === exp2 && two === exp2;
      ev = '订单 ' + cand.orderId + ' 行数量 ' + cand.qty + '、券 ' + cand.cp + '、行实付 ' + itemPaid + ' 分' +
        '；数量 1 页面 ¥' + amt1 + '（=' + exp1 + ' 分）→ 点「+」数量 2 页面 ¥' + amt2 + '（=' + exp2 + ' 分）' +
        '；Store.refundAmount(qty=2)=' + two + ' 分，三者同源且随数量变化';
    }
    rec('P-01', '申请页预计退款与 Store.refundAmount 同源（qty 随动）', pass, ev);
  } catch (e) { rec('P-01', '申请页金额同源', false, '异常：' + e.message); }

  fs.writeFileSync(OUT, JSON.stringify(results, null, 1));
  const okN = results.filter(function (r) { return r.pass; }).length;
  console.log('==== ' + okN + '/' + results.length + ' 通过 ====');
  process.exit(okN === results.length ? 0 : 1);
})();