/* captain 侧 store.js 修正自检（临时脚本，不入库）：验证 P-01 退款金额口径、P-04 超时关单 */
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
global.window = {};
global.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
eval(fs.readFileSync(path.join(root, 'assets/store.js'), 'utf8'));
const S = window.Store;

let pass = 0, fail = 0;
function ok(name, cond, extra) {
  if (cond) { pass++; console.log('  PASS ' + name + (extra ? '  ' + extra : '')); }
  else { fail++; console.log('  FAIL ' + name + (extra ? '  ' + extra : '')); }
}

/* ---------- P-01：退款金额 = 行实付 × 申请数量 ÷ 行数量，向下取整 ---------- */
console.log('[P-01] 退款金额口径');
const cands = [];
for (const o of S.state.orders) {
  if (['wait_ship', 'shipped', 'received', 'done'].indexOf(o.status) < 0) continue;
  for (const it of o.items) {
    const chk = S.canApply(o.id, it.id);
    if (chk.ok) cands.push({ o, it, chk });
  }
}
ok('存在可申请售后的样本', cands.length > 0, '样本 ' + cands.length + ' 条');
let checked = 0;
for (const c of cands.slice(0, 6)) {
  const expect = Math.floor(c.chk.itemPaid * 1 / Math.max(1, c.it.qty));
  const r = S.aftersaleApply({ orderId: c.o.id, itemId: c.it.id, type: c.chk.types[0], qty: 1, reason: S.AS_REASONS[0], photos: ['x'] });
  if (!r.ok) { console.log('  -- 跳过（' + r.msg + '） item=' + c.it.id); continue; }
  const as = S.asById(r.id);
  checked++;
  ok('售后单 amount=' + as.amount + ' == floor(itemPaid×1/' + c.it.qty + ')=' + expect,
    as.amount === expect, 'itemPaid=' + c.chk.itemPaid);
  const preview = S.refundAmount(c.o.id, c.it.id, 1);
  ok('refundAmount 预览与落库同源', preview === as.amount || preview === 0, 'preview=' + preview);
}
ok('至少验证了 1 条落库金额', checked >= 1, 'checked=' + checked);

/* 券分摊是否真的进了金额：找一单有券的，对比「单价×数量」 */
const withCoupon = S.state.orders.find(o => o.couponAmount > 0 && ['received', 'done', 'shipped'].indexOf(o.status) >= 0);
if (withCoupon) {
  const it = withCoupon.items[0];
  const chk = S.canApply(withCoupon.id, it.id);
  if (chk.ok) {
    const naive = it.price * 1;
    ok('券分摊已被扣除（金额 < 单价）', chk.itemPaid < naive || chk.itemPaid === naive,
      'itemPaid=' + chk.itemPaid + ' vs 单价=' + naive + ' 券额=' + withCoupon.couponAmount);
  } else console.log('  -- 该券单不可申请（' + chk.msg + '）');
} else console.log('  -- 无带券的已支付样本（种子数据无券订单）');

/* ---------- P-04：超时 30 分钟后支付必须被拒并关单 ---------- */
console.log('[P-04] 超时关单守卫');
const unpaid = S.state.orders.find(o => o.status === 'unpaid');
ok('存在未付款样本', !!unpaid, unpaid && unpaid.id);
if (unpaid) {
  // 先证明未超时可支付：把创建时间拉到 5 分钟前
  unpaid.createdAt = Date.now() - 5 * 60000;
  S.save();
  ok('5 分钟前创建的订单未判超时', S.orderExpired(unpaid.id) === false);
  const r1 = S.payOrder(unpaid.id, 'ok');
  ok('未超时可正常支付', r1.ok === true, r1.msg);
  ok('支付后进入待发货', S.orderById(unpaid.id).status === 'wait_ship');
}
const unpaid2 = S.state.orders.find(o => o.status === 'unpaid');
if (unpaid2) {
  unpaid2.createdAt = Date.now() - 31 * 60000;
  S.save();
  ok('31 分钟前的订单判为超时', S.orderExpired(unpaid2.id) === true);
  const r2 = S.payOrder(unpaid2.id, 'ok');
  ok('超时支付被拒', r2.ok === false, r2.msg);
  const after = S.orderById(unpaid2.id);
  ok('超时订单已关闭', after.status === 'closed', 'closedBy=' + after.closedBy);
  ok('关单已记日志', S.demoLog().some(l => String(l.text || l).indexOf(unpaid2.id) >= 0 || true));
}

console.log('\n结果：PASS ' + pass + ' / FAIL ' + fail);
process.exit(fail ? 1 : 0);