/* ============================================================================
 * 小满商城 · 原型后台 10 屏（无依赖）
 * 契约：window.ADMIN_ROUTES = [{ path, render(ctx) }]
 *       render 返回 { html, title }；html 挂到 .admin-body，title 进 .admin-head
 * 数据：全部读写 window.Store（金额单位=分，写操作返回 {ok,msg}）
 * 规则出处见各屏注释里的 BR-* 编号（D:\litemall-next\需求文档.md）
 * ==========================================================================*/
(function () {
  'use strict';

  var U = window.UI || {};
  var esc = U.esc || function (s) { return String(s == null ? '' : s); };
  var price = U.price || function (c) { return '<span class="price">¥' + ((c || 0) / 100).toFixed(2) + '</span>'; };
  var yuan = U.yuan || function (c) { return ((c || 0) / 100).toFixed(2); };
  var time = U.time || function (t) { return t ? new Date(t).toLocaleString() : '—'; };
  var fromNow = U.fromNow || function () { return ''; };
  var toast = U.toast || function () {};
  var icon = U.icon || function () { return ''; };
  var starRate = U.starRate || function (n) { return new Array((n || 0) + 1).join('★'); };
  var empty = U.empty || function (o) {
    return '<div class="empty"><div class="t">' + esc((o && o.title) || '暂无数据') + '</div></div>';
  };

  function S() { return window.Store; }
  function st() { return window.Store.state; }
  function sum(arr, fn) {
    return arr.reduce(function (a, x) { return a + (fn ? fn(x) : x); }, 0);
  }
  function byDesc(a, b) { return b.createdAt - a.createdAt; }
  function byAsc(a, b) { return a.createdAt - b.createdAt; }
  function cent(v) {
    var n = parseFloat(v);
    if (isNaN(n)) return null;
    return Math.round(n * 100);
  }
  function intOf(v, dflt) {
    var n = parseInt(v, 10);
    return isNaN(n) ? dflt : n;
  }
  function val(id) { var n = document.getElementById(id); return n ? n.value : ''; }
  function dayText(ts) { var d = new Date(ts); return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2); }

  /* ------------------------------ 筛选状态（模块内，跨 render 保留） ------------- */
  var F = {
    orders: { tab: 'all', kw: '' },
    as: { tab: 'all', kw: '' },
    goods: { tab: 'all' },
    stock: { tab: 'all', kw: '' },
    comments: { tab: 'all' }
  };

  var ROUTES = [];
  var LAST_HASH = null;
  var LAST_CTX = null;

  function go(hash) {
    if (window.App && typeof window.App.go === 'function') { window.App.go(hash); return; }
    location.hash = hash;
  }

  /* 每次导航变化时，允许把 hash 上的 ?tab= 带入筛选；App.refresh() 时不覆盖用户已点的 tab */
  function queryFor(ctx) {
    var h = String(location.hash || '');
    if (h === LAST_HASH) return {};
    LAST_HASH = h;
    if (ctx && ctx.query && Object.keys(ctx.query).length) return ctx.query;
    return U.qs ? U.qs(h) : {};
  }

  function currentPath() {
    var h = String(location.hash || '').replace(/^#\/?/, '').split('?')[0];
    var parts = h.split('/').filter(Boolean);
    if (parts[0] === 'admin') parts.shift();
    return parts.join('/');
  }

  function refresh() {
    if (LAST_CTX && typeof LAST_CTX.refresh === 'function') { LAST_CTX.refresh(); return; }
    if (window.App && typeof window.App.refresh === 'function') { window.App.refresh(); return; }
    // 外壳未就绪时的兜底：直接重渲染当前后台路由（不影响真实外壳）
    var p = currentPath();
    var route = null;
    for (var i = 0; i < ROUTES.length; i++) if (ROUTES[i].path === p) route = ROUTES[i];
    if (!route) return;
    var out = route.render({ params: {}, query: U.qs ? U.qs(location.hash) : {} });
    var body = document.querySelector('.admin-body');
    var head = document.querySelector('.admin-head h1');
    if (body) body.innerHTML = out.html;
    if (head) head.textContent = out.title;
  }

  /* ------------------------------ 片段工厂 ------------------------------ */
  function btn(text, act, id, cls) {
    return '<button type="button" class="btn sm ' + (cls || '') + '" data-aact="' + act + '" data-id="' + esc(id || '') + '">' + esc(text) + '</button>';
  }
  function goLink(text, hash, cls) {
    return '<a class="btn sm ' + (cls || '') + '" href="' + esc(hash) + '" data-go="' + esc(hash) + '">' + esc(text) + '</a>';
  }
  function seg(items, act) {
    return '<div class="seg-tabs" style="flex-wrap:wrap">' + items.map(function (t) {
      var n = (t.n === 0 || t.n) ? '<span style="color:var(--text-3);font-size:11px;margin-left:4px">' + t.n + '</span>' : '';
      return '<button type="button" class="' + (t.on ? 'on' : '') + '" data-aact="' + act + '" data-key="' + esc(t.key) + '">' + esc(t.label) + n + '</button>';
    }).join('') + '</div>';
  }
  function searchBar(act, value, placeholder) {
    return '<input class="input" id="f-kw" value="' + esc(value || '') + '" placeholder="' + esc(placeholder || '搜索') +
      '" style="width:230px;height:34px" data-aenter="' + act + '">' +
      '<button type="button" class="btn sm" data-aact="' + act + '">筛选</button>';
  }
  function toolbar(left, right) {
    return '<div class="row between" style="gap:12px;margin-bottom:14px;flex-wrap:wrap;align-items:flex-start">' +
      '<div>' + left + '</div><div class="row" style="gap:8px;flex-wrap:wrap">' + (right || '') + '</div></div>';
  }
  function panel(title, body, right) {
    return '<div class="panel"><div class="panel-hd"><h2>' + esc(title) + '</h2><div class="spacer"></div>' + (right || '') + '</div>' + body + '</div>';
  }
  function tblWrap(html) { return '<div class="tbl-wrap">' + html + '</div>'; }
  function field(label, id, value, opt) {
    opt = opt || {};
    var input = opt.textarea
      ? '<textarea class="textarea" id="' + id + '" placeholder="' + esc(opt.ph || '') + '"' + (opt.rows ? ' rows="' + opt.rows + '"' : '') + '>' + esc(value == null ? '' : value) + '</textarea>'
      : '<input class="input" id="' + id + '" type="' + (opt.type || 'text') + '" value="' + esc(value == null ? '' : value) + '"' +
        (opt.min != null ? ' min="' + opt.min + '"' : '') + (opt.max != null ? ' max="' + opt.max + '"' : '') +
        (opt.step != null ? ' step="' + opt.step + '"' : '') + (opt.ph ? ' placeholder="' + esc(opt.ph) + '"' : '') + '>';
    return '<div class="field"><label>' + esc(label) + (opt.req ? '<span class="req">*</span>' : '') + '</label>' + input +
      (opt.hint ? '<div class="help">' + opt.hint + '</div>' : '') + '</div>';
  }
  function selField(label, id, options, value, opt) {
    opt = opt || {};
    return '<div class="field"><label>' + esc(label) + (opt.req ? '<span class="req">*</span>' : '') + '</label>' +
      '<select class="select" id="' + id + '">' + options.map(function (o) {
        return '<option value="' + esc(o.v) + '"' + (String(o.v) === String(value) ? ' selected' : '') + '>' + esc(o.t) + '</option>';
      }).join('') + '</select>' + (opt.hint ? '<div class="help">' + opt.hint + '</div>' : '') + '</div>';
  }
  function sheetErr(ov, msg) {
    var bd = ov.querySelector('.sheet-bd');
    if (!bd) return;
    var box = bd.querySelector('.sheet-err');
    if (!box) {
      box = document.createElement('div');
      box.className = 'sheet-err alert err';
      box.style.marginBottom = '12px';
      bd.insertBefore(box, bd.firstChild);
    }
    box.textContent = msg;
  }
  function kpi(t, v, d) {
    return '<div class="kpi"><div class="t">' + esc(t) + '</div><div class="v">' + v + '</div><div class="d">' + esc(d || '') + '</div></div>';
  }
  function catPath(id) {
    var cats = st().cats;
    var c = cats.filter(function (x) { return x.id === id; })[0];
    if (!c) return '未分类';
    var p = c.parentId ? cats.filter(function (x) { return x.id === c.parentId; })[0] : null;
    return (p ? p.name + ' / ' : '') + c.name;
  }
  function brandName(id) {
    var b = st().brands.filter(function (x) { return x.id === id; })[0];
    return b ? b.name : '无品牌';
  }
  function goodsName(id) {
    var g = st().goods.filter(function (x) { return x.id === id; })[0];
    return g ? g.name : '商品已删除';
  }
  function thumbOf(item, cls) {
    return U.thumb ? U.thumb(item.bg, item.emoji, cls) : '<div class="thumb ' + (cls || '') + '">' + esc(item.emoji || '🛍️') + '</div>';
  }
  function itemSummary(o) {
    if (!o.items || !o.items.length) return '—';
    var n = sum(o.items, function (it) { return it.qty; });
    var first = o.items[0];
    return '<div class="ellipsis" style="max-width:260px" title="' + esc(first.name + ' ' + first.specText) + '">' + esc(first.name) + '</div>' +
      '<div class="tiny dim">' + esc(first.specText || '') + (o.items.length > 1 ? ' · 等 ' + o.items.length + ' 种' : '') + ' · 共 ' + n + ' 件</div>';
  }

  /* ------------------------------ 1. 登录 ------------------------------ */
  function loginScreen() {
    var stats = S().stats();
    var html =
      '<div style="max-width:420px;margin:36px auto 0">' +
        '<div class="panel">' +
          '<div class="row" style="gap:10px;margin-bottom:4px">' +
            '<div style="width:40px;height:40px;border-radius:12px;display:grid;place-items:center;background:linear-gradient(135deg,#ff5a1f,#ff2e4d);color:#fff;font-size:19px">🛍️</div>' +
            '<div><div class="bold" style="font-size:16px">小满商城 · 管理后台</div><div class="tiny dim">演示环境 · 单商户 · 单管理员身份（BR-ADM-01）</div></div>' +
          '</div>' +
          '<div class="mt12">' + U.alert('演示模式：任意密码均可登录，不校验真实凭据；登录后拥有全部后台操作权限。', 'warn') + '</div>' +
          '<div class="mt16">' +
            field('账号', 'lg-user', 'admin', { req: true }) +
            field('密码', 'lg-pwd', 'admin', { type: 'password', req: true, hint: '演示账号已预填；密码不落库、不明文展示（BR-ADM-05）。' }) +
            '<button type="button" class="btn primary block lg" data-aact="login">登 录</button>' +
          '</div>' +
          '<div class="divider"></div>' +
          '<div class="row between tiny dim"><span>当前演示数据</span><span>商品 ' + st().goods.length + ' · 订单 ' + st().orders.length + ' · 售后 ' + st().aftersales.length + '</span></div>' +
          '<div class="row between tiny dim mt6"><span>近 30 天 GMV</span><span>' + price(stats.gmv) + ' · 待发货 ' + stats.waitShip + ' 单</span></div>' +
        '</div>' +
        '<div class="tiny dim center mt12">未登录访问后台应跳转本页，登录后回到原页面（BR-ADM-04）——原型以演示登录页表达该入口。</div>' +
      '</div>';
    return { title: '登录', html: html };
  }

  /* ------------------------------ 2. 工作台 ------------------------------ */
  function trendData() {
    var out = [];
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var os = st().orders;
    for (var i = 6; i >= 0; i--) {
      var s = new Date(today.getTime() - i * 86400000);
      var e = new Date(s.getTime() + 86400000);
      var paid = os.filter(function (o) { return o.paidAt && o.paidAt >= s.getTime() && o.paidAt < e.getTime(); });
      out.push({
        label: (s.getMonth() + 1) + '-' + s.getDate(),
        weekday: '日一二三四五六'.charAt(s.getDay()),
        count: paid.length,
        gmv: sum(paid, function (o) { return o.payAmount; })
      });
    }
    return out;
  }
  function dashboardScreen() {
    var stats = S().stats();
    var orders = st().orders;
    var waitShip = orders.filter(function (o) { return o.status === 'wait_ship'; }).sort(byAsc).slice(0, 5);
    var pendAs = st().aftersales.filter(function (a) { return S().AS_OPEN.indexOf(a.status) >= 0; }).sort(byAsc).slice(0, 5);
    var trend = trendData();
    var maxCount = Math.max.apply(null, trend.map(function (d) { return d.count; }).concat([1]));
    var trendGmv = sum(trend, function (d) { return d.gmv; });
    var trendCnt = sum(trend, function (d) { return d.count; });

    var bars = trend.map(function (d) {
      var h = d.count ? Math.max(8, Math.round(d.count / maxCount * 104)) : 3;
      return '<div style="flex:1;min-width:0;text-align:center" title="' + esc(d.label + ' 支付 ' + d.count + ' 单 / ¥' + yuan(d.gmv)) + '">' +
        '<div class="tiny" style="color:var(--text-2);font-weight:700">' + d.count + '</div>' +
        '<div style="height:110px;display:flex;align-items:flex-end;justify-content:center">' +
          '<div style="width:60%;max-width:38px;height:' + h + 'px;border-radius:6px 6px 2px 2px;background:' +
            (d.count ? 'linear-gradient(180deg,#ff9166,#ff5a1f)' : '#eef0f3') + '"></div>' +
        '</div>' +
        '<div class="tiny dim" style="margin-top:6px">' + d.label + '</div>' +
        '<div class="tiny dim">周' + d.weekday + '</div>' +
      '</div>';
    }).join('');

    var shipList = waitShip.length ? waitShip.map(function (o) {
      return '<div class="row between" style="padding:9px 0;border-bottom:1px solid var(--line);gap:10px">' +
        '<div style="min-width:0"><div class="small bold ellipsis">' + esc(o.sn) + '</div>' +
          '<div class="tiny dim ellipsis">' + esc(o.address ? o.address.name : '') + ' · ' + esc(itemText(o)) + '</div></div>' +
        '<div class="row" style="gap:8px;flex:none">' + price(o.payAmount) +
          '<button type="button" class="btn sm primary" data-aact="ord-ship" data-id="' + esc(o.id) + '">发货</button></div>' +
      '</div>';
    }).join('') : empty({ icon: '✅', title: '没有待发货订单', hint: '所有已付款订单都已发出' });

    var asList = pendAs.length ? pendAs.map(function (a) {
      return '<div class="row between" style="padding:9px 0;border-bottom:1px solid var(--line);gap:10px">' +
        '<div style="min-width:0"><div class="small bold ellipsis">' + esc(a.sn) + '</div>' +
          '<div class="tiny dim ellipsis">' + esc(S().REFUND_TYPES[a.type] || a.type) + ' · ' + esc(a.name) + ' ×' + a.qty + '</div></div>' +
        '<div class="row" style="gap:8px;flex:none">' + U.asTag(a) +
          '<a class="btn sm" href="#/admin/aftersales?tab=' + esc(a.status) + '" data-go="#/admin/aftersales?tab=' + esc(a.status) + '">处理</a></div>' +
      '</div>';
    }).join('') : empty({ icon: '✅', title: '没有待处理售后', hint: '售后申请都已处理完' });

    var html =
      '<div class="kpis">' +
        kpi('近 30 天 GMV', price(stats.gmv, 'price-lg'), '按支付成功时间统计（BR-RPT-01），不扣退款') +
        kpi('支付订单数', stats.orderCount, '不含未付款、不含已关闭（BR-RPT-03）') +
        kpi('退款金额', price(stats.refunded, 'price-lg'), '退款完成的售后单金额合计（BR-RPT-02）') +
        kpi('待处理售后', stats.pendingAs, '申请中 / 待寄回 / 待收货 / 退款中') +
        kpi('待发货', stats.waitShip, '运营现在要处理的活（BR-ORD-02）') +
        kpi('缺货 / 低库存 SKU', stats.lowStock, '可售 ≤ 5 的 SKU 数（BR-GOODS-10）') +
        kpi('客单价', stats.orderCount ? price(stats.avgPrice, 'price-md') : '—', 'GMV ÷ 订单数；分母为 0 显示 —（BR-RPT-04）') +
      '</div>' +
      '<div style="display:grid;grid-template-columns:minmax(0,1.25fr) minmax(0,1fr);gap:14px;margin-top:14px">' +
        panel('近 7 天订单趋势',
          '<div class="tiny dim" style="margin-top:-6px;margin-bottom:8px">支付 ' + trendCnt + ' 单 · GMV ' + price(trendGmv) + ' · 按支付成功时间归日（BR-RPT-07）</div>' +
          '<div style="display:flex;align-items:flex-end;gap:12px;padding:4px 2px 2px">' + bars + '</div>') +
        panel('待我处理 · 待发货（前 5）', '<div style="margin-top:-4px">' + shipList + '</div>',
          '<a class="tiny" href="#/admin/orders?tab=wait_ship" data-go="#/admin/orders?tab=wait_ship">全部订单 →</a>') +
      '</div>' +
      '<div style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px;margin-top:14px">' +
        panel('待我处理 · 待审核售后（前 5）', '<div style="margin-top:-4px">' + asList + '</div>',
          '<a class="tiny" href="#/admin/aftersales?tab=apply" data-go="#/admin/aftersales?tab=apply">售后处理台 →</a>') +
        panel('报表口径说明',
          '<div class="small" style="line-height:1.9;color:var(--text-2)">' +
            '<div>· <b>GMV</b>：统计期内支付成功订单的实付金额合计，<b>按支付成功时间</b>归属，不按创建时间（BR-RPT-01），不扣退款（BR-RPT-02）。</div>' +
            '<div>· <b>订单数</b>：统计期内支付成功的订单数，未付款与已关闭不计（BR-RPT-03）。</div>' +
            '<div>· <b>客单价</b>：GMV ÷ 订单数；订单数为 0 时显示「—」（BR-RPT-04）。</div>' +
            '<div>· <b>退款金额</b>：售后单「退款完成」的金额合计；退款率单列，与 GMV 不相减（BR-RPT-05）。</div>' +
            '<div>· <b>低库存</b>：可售 = 实际库存 − 已锁未释放；可售 ≤ 5 视为低库存（7.3 / BR-GOODS-10）。</div>' +
          '</div>' +
          '<div class="mt8 tiny dim">当前统计期固定「近 30 天」（Store.stats 口径）。切今日/近 7 天需数据层提供统计期参数。</div>') +
      '</div>';
    return { title: '工作台', html: html };
  }
  function itemText(o) {
    if (!o.items || !o.items.length) return '';
    return o.items[0].name + (o.items.length > 1 ? ' 等 ' + o.items.length + ' 种' : '');
  }

  /* ------------------------------ 3. 订单管理 ------------------------------ */
  function ordersScreen(ctx) {
    var q = queryFor(ctx);
    if (q.tab && (q.tab === 'all' || S().ORDER_STATUS[q.tab])) F.orders.tab = q.tab;

    var all = st().orders.slice().sort(byDesc);
    var counts = { all: all.length };
    Object.keys(S().ORDER_STATUS).forEach(function (k) {
      counts[k] = all.filter(function (o) { return o.status === k; }).length;
    });
    var list = F.orders.tab === 'all' ? all : all.filter(function (o) { return o.status === F.orders.tab; });
    if (F.orders.kw) {
      var kw = F.orders.kw.trim();
      list = list.filter(function (o) {
        var hay = [o.sn, o.address ? o.address.name : '', o.address ? o.address.phone : '']
          .concat((o.items || []).map(function (it) { return it.name + ' ' + (it.specText || ''); }))
          .join(' ');
        return hay.indexOf(kw) >= 0;
      });
    }

    var tabs = [{ key: 'all', label: '全部', n: counts.all, on: F.orders.tab === 'all' }];
    Object.keys(S().ORDER_STATUS).forEach(function (k) {
      tabs.push({ key: k, label: S().ORDER_STATUS[k], n: counts[k], on: F.orders.tab === k });
    });

    var rows = list.map(function (o) {
      var ops = '<button type="button" class="btn sm" data-aact="ord-detail" data-id="' + esc(o.id) + '">详情</button>';
      if (o.status === 'wait_ship') ops += '<button type="button" class="btn sm primary" data-aact="ord-ship" data-id="' + esc(o.id) + '">发货</button>';
      // P-02：单笔「立即关单」——只关这一笔，不碰其他未付款订单（与 demo 页批量入口并存）
      if (o.status === 'unpaid') ops += '<button type="button" class="btn sm" data-aact="ord-close" data-id="' + esc(o.id) + '">立即关单</button>';
      if (o.tracking) {
        if (o.trackingEdited) ops += '<button type="button" class="btn sm" disabled title="发货后仅允许修改 1 次（BR-ORD-10）">改单号</button>';
        else ops += '<button type="button" class="btn sm" data-aact="ord-track" data-id="' + esc(o.id) + '">改单号</button>';
      }
      var track = o.tracking
        ? '<div class="tiny dim ellipsis" style="max-width:150px">' + esc(o.tracking.company + ' ' + o.tracking.no) + '</div>'
        : '';
      return '<tr>' +
        '<td><div class="num small bold">' + esc(o.sn) + '</div>' + (o.hidden ? '<span class="tag gray">用户已删除</span>' : '') + '</td>' +
        '<td class="num small">' + time(o.createdAt) + '</td>' +
        '<td><div class="small">' + esc(o.address ? o.address.name : '—') + '</div><div class="tiny dim">' + esc(o.address ? o.address.phone : '') + '</div></td>' +
        '<td>' + itemSummary(o) + '</td>' +
        '<td class="num">' + price(o.payAmount) + '<div class="tiny dim">' + (o.couponAmount ? '已抵扣 ' + yuan(o.couponAmount) : '无券') + '</div></td>' +
        '<td>' + U.statusTag(o) + track + '</td>' +
        '<td><div class="ops">' + ops + '</div></td>' +
      '</tr>';
    }).join('');

    var body = rows
      ? '<table class="tbl"><thead><tr>' +
          '<th style="width:150px">单号</th><th style="width:116px">下单时间</th><th style="width:110px">买家</th>' +
          '<th>商品行</th><th style="width:96px">实付</th><th style="width:130px">状态 / 物流</th><th style="width:160px">操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table>'
      : empty({ icon: '📦', title: '没有符合条件的订单', hint: '调整状态或关键词后重试' });

    var html =
      toolbar(
        seg(tabs, 'ord-tab') +
        '<div class="tiny dim mt6">共 ' + list.length + ' 单 · 点击「发货」填物流公司与单号，C 端即可见（BR-ORD-03）</div>',
        searchBar('ord-search', F.orders.kw, '单号 / 买家 / 收货人 / 商品名') +
        (F.orders.kw ? btn('清除', 'ord-reset', '', '') : '')
      ) + tblWrap(body);
    return { title: '订单管理', html: html };
  }

  function orderDetailSheet(id) {
    var o = S().orderById(id);
    if (!o) { toast('订单不存在', 'err'); return; }
    var items = (o.items || []).map(function (it) {
      var refunded = (it.refundedQty || 0) >= it.qty;
      return '<div class="row" style="gap:10px;padding:9px 0;border-bottom:1px solid var(--line)">' +
        thumbOf(it, 'sm') +
        '<div class="grow"><div class="small bold">' + esc(it.name) + '</div>' +
          '<div class="tiny dim">' + esc(it.specText || '') + ' × ' + it.qty + (refunded ? ' · <span style="color:var(--danger)">已退款</span>' : '') + '</div></div>' +
        '<div class="right">' + price(it.price * it.qty) + '</div>' +
      '</div>';
    }).join('');

    var asList = st().aftersales.filter(function (a) { return a.orderId === o.id; });
    var asHtml = asList.length ? asList.map(function (a) {
      return '<div class="row between" style="padding:7px 0;border-bottom:1px dashed var(--line)">' +
        '<div class="small"><b>' + esc(a.sn) + '</b> · ' + esc(S().REFUND_TYPES[a.type]) + ' · ' + esc(a.name) + ' ×' + a.qty + '</div>' +
        '<div class="row" style="gap:6px">' + U.asTag(a) + '<span class="tiny dim">' + price(a.amount) + '</span></div>' +
      '</div>';
    }).join('') : '<div class="tiny dim">该订单暂无售后单</div>';

    var timeline = (o.timeline || []).map(function (t, i, arr) {
      return '<div class="item ' + (i === arr.length - 1 ? 'now' : 'on') + '">' +
        '<div class="it">' + esc(t.text) + '</div><div class="is">' + time(t.at) + (t.by ? ' · ' + esc(t.by) : '') + '</div></div>';
    }).join('');

    var body =
      '<div class="row between" style="margin-bottom:10px"><div><div class="bold" style="font-size:15px">' + esc(o.sn) + '</div>' +
        '<div class="tiny dim">下单 ' + time(o.createdAt) + (o.paidAt ? ' · 支付 ' + time(o.paidAt) : '') + '</div></div>' +
        '<div>' + U.statusTag(o) + '</div></div>' +
      '<div class="divider"></div>' +
      '<div class="small bold" style="margin-bottom:2px">商品行</div>' + items +
      '<div class="small" style="margin-top:10px;line-height:1.9">' +
        '<div class="row between"><span class="muted">商品金额</span><span class="num">' + price(o.goodsAmount) + '</span></div>' +
        '<div class="row between"><span class="muted">优惠券</span><span class="num">' + (o.couponAmount ? '-' + price(o.couponAmount) : '—') + '</span></div>' +
        '<div class="row between"><span class="muted">运费</span><span class="num">' + price(o.freight) + '</span></div>' +
        '<div class="row between"><span class="bold">实付</span><span class="num bold">' + price(o.payAmount) + '</span></div>' +
      '</div>' +
      '<div class="divider"></div>' +
      '<div class="small"><div class="bold">收货信息</div>' +
        '<div class="tiny dim mt6">' + esc(o.address ? o.address.name + ' ' + o.address.phone : '—') + '</div>' +
        '<div class="tiny dim">' + esc(o.address ? o.address.region + ' ' + o.address.detail : '') + '</div>' +
        (o.remark ? '<div class="tiny dim">买家备注：' + esc(o.remark) + '</div>' : '') +
      '</div>' +
      '<div class="mt10 small"><div class="bold">物流</div>' +
        '<div class="tiny dim mt6">' + (o.tracking ? esc(o.tracking.company + ' ' + o.tracking.no) + (o.trackingEdited ? '（已修改 1 次）' : '') : '尚未发货') + '</div>' +
        (o.closedBy ? '<div class="tiny dim">关闭原因：' + esc(o.closedBy) + '</div>' : '') +
      '</div>' +
      '<div class="divider"></div>' +
      '<div class="small bold">售后记录（' + asList.length + '）</div><div class="mt6">' + asHtml + '</div>' +
      '<div class="divider"></div>' +
      '<div class="small bold" style="margin-bottom:8px">时间线</div>' +
      '<div class="timeline">' + (timeline || '<div class="tiny dim">暂无</div>') + '</div>' +
      ((o.logs || []).length ? '<div class="mt10 tiny dim">操作日志：' + o.logs.map(function (l) { return esc(time(l.at) + ' ' + l.text); }).join('；') + '</div>' : '');

    U.sheet({
      title: '订单详情',
      wide: true,
      body: body,
      actions: [
        { text: '关闭' },
        // P-02：未付款订单在详情弹层也能单笔关单
        o.status === 'unpaid' ? { text: '立即关单', onClick: function () { setTimeout(function () { closeOneOrder(o.id); }, 30); } } : null,
        o.status === 'wait_ship' ? { text: '发货', type: 'primary', onClick: function () { setTimeout(function () { shipSheet(o.id); }, 30); } } : null
      ].filter(Boolean)
    });
  }

  /* P-02：单笔「立即关单」（需求 6.1 第 11 步）。走 Store.demoCloseUnpaid，与批量入口同一套
     「超时关闭」逻辑：释放锁量、券退回未使用、写演示日志；只作用于当前这一笔订单。 */
  function closeOneOrder(id) {
    var o = S().orderById(id);
    if (!o) { toast('订单不存在', 'err'); return; }
    if (o.status !== 'unpaid') { toast('只有未付款订单可以关单（BR-ORD-13）', 'err'); return; }
    var qty = sum(o.items || [], function (it) { return it.qty || 0; });
    U.confirm({
      title: '关闭订单 ' + o.sn,
      body: '将把这一笔未付款订单按「超时关闭」处理：释放锁定库存 ' + qty + ' 件、优惠券退回未使用且不延长有效期、写演示日志（BR-ORD-13）。<br>' +
        '只影响当前订单，其他未付款订单保持不变。',
      okText: '立即关单'
    }).then(function (ok) {
      if (!ok) return;
      var r = S().demoCloseUnpaid(id);
      if (!r.ok) { toast(r.msg, 'err'); return; }
      toast('已关单 1 笔，释放库存 ' + qty + ' 件', 'ok');
      refresh();
    });
  }

  function shipSheet(id) {
    var o = S().orderById(id);
    if (!o) return;
    U.sheet({
      title: '发货 · ' + o.sn,
      body: U.alert('只有「待发货」订单可以发货，物流公司与单号均不能为空（BR-ORD-02）。', 'warn') +
        '<div class="mt12">' + field('物流公司', 'sp-co', '顺丰速运', { req: true }) +
        field('物流单号', 'sp-no', '', { req: true, ph: '如 SF1234567890', hint: '演示环境可用任意单号；发货后 C 端订单详情立即可见（BR-ORD-03）。' }) + '</div>',
      actions: [
        { text: '取消' },
        { text: '确认发货', type: 'primary', onClick: function (ov) {
          var co = val('sp-co').trim(), no = val('sp-no').trim();
          if (!co || !no) { sheetErr(ov, '物流公司与单号均不能为空'); return false; }
          var r = S().shipOrder(id, { company: co, no: no });
          if (!r.ok) { sheetErr(ov, r.msg); return false; }
          toast(r.msg, 'ok');
          refresh();
        } }
      ]
    });
  }

  function trackSheet(id) {
    var o = S().orderById(id);
    if (!o) return;
    if (o.trackingEdited) { toast('物流单号发货后仅允许修改 1 次（BR-ORD-10）', 'err'); return; }
    U.sheet({
      title: '修改物流单号 · ' + o.sn,
      body: U.alert('发货后物流单号仅允许修改 1 次，修改会留操作日志（BR-ORD-10 / BR-ADM-03）。', 'warn') +
        '<div class="mt12">' + field('物流公司', 'sp-co', o.tracking ? o.tracking.company : '', { req: true }) +
        field('物流单号', 'sp-no', o.tracking ? o.tracking.no : '', { req: true }) + '</div>',
      actions: [
        { text: '取消' },
        { text: '保存修改', type: 'primary', onClick: function (ov) {
          var co = val('sp-co').trim(), no = val('sp-no').trim();
          if (!co || !no) { sheetErr(ov, '物流公司与单号均不能为空'); return false; }
          var r = S().editTracking(id, { company: co, no: no });
          if (!r.ok) { sheetErr(ov, r.msg); return false; }
          toast(r.msg, 'ok');
          refresh();
        } }
      ]
    });
  }

  /* ------------------------------ 4. 售后处理台 ------------------------------ */
  function asScreen(ctx) {
    var q = queryFor(ctx);
    if (q.tab && (q.tab === 'all' || S().AS_STATUS[q.tab])) F.as.tab = q.tab;

    var all = st().aftersales.slice().sort(byDesc);
    var counts = { all: all.length };
    Object.keys(S().AS_STATUS).forEach(function (k) {
      counts[k] = all.filter(function (a) { return a.status === k; }).length;
    });
    var list = F.as.tab === 'all' ? all : all.filter(function (a) { return a.status === F.as.tab; });
    if (F.as.kw) {
      var kw = F.as.kw.trim();
      list = list.filter(function (a) {
        return (a.sn + ' ' + a.orderSn + ' ' + a.name + ' ' + (a.specText || '')).indexOf(kw) >= 0;
      });
    }
    var pendingRestock = all.filter(function (a) { return a.restockPending; });

    var tabs = [{ key: 'all', label: '全部', n: counts.all, on: F.as.tab === 'all' }];
    Object.keys(S().AS_STATUS).forEach(function (k) {
      tabs.push({ key: k, label: S().AS_STATUS[k], n: counts[k], on: F.as.tab === k });
    });

    var rows = list.map(function (a) {
      var ops = '<button type="button" class="btn sm" data-aact="as-detail" data-id="' + esc(a.id) + '">详情</button>';
      if (a.status === 'apply') {
        ops += '<button type="button" class="btn sm primary" data-aact="as-approve" data-id="' + esc(a.id) + '">同意</button>';
        ops += '<button type="button" class="btn sm danger" data-aact="as-reject" data-id="' + esc(a.id) + '">驳回</button>';
      } else if (a.status === 'wait_send') {
        ops += '<button type="button" class="btn sm" data-aact="as-skip" data-id="' + esc(a.id) + '">跳过收货</button>';
        ops += '<button type="button" class="btn sm" data-aact="as-autoclose" data-id="' + esc(a.id) + '">关闭</button>';
      } else if (a.status === 'wait_receive') {
        ops += '<button type="button" class="btn sm primary" data-aact="as-receive" data-id="' + esc(a.id) + '">确认收到退货</button>';
        ops += '<button type="button" class="btn sm" data-aact="as-skip" data-id="' + esc(a.id) + '">跳过收货</button>';
      } else if (a.status === 'refunding') {
        ops += '<button type="button" class="btn sm primary" data-aact="as-refund" data-id="' + esc(a.id) + '">' +
          (a.restockPending ? '重试回补并退款' : '执行退款') + '</button>';
      } else if (a.status === 'rejected' || a.status === 'closed') {
        ops += '<span class="tiny dim">终态</span>';
      }

      return '<tr' + (a.restockPending ? ' style="background:#fff7f7"' : '') + '>' +
        '<td class="num small bold">' + esc(a.sn) + (a.photos ? '<div class="tiny dim">凭证 ' + a.photos + ' 张</div>' : '') + '</td>' +
        '<td class="num tiny">' + esc(a.orderSn) + '</td>' +
        '<td><div class="small ellipsis" style="max-width:200px" title="' + esc(a.name + ' ' + (a.specText || '')) + '">' + esc(a.name) + '</div>' +
          '<div class="tiny dim">' + esc(a.specText || '') + ' × ' + a.qty + '</div></td>' +
        '<td><span class="tag outline">' + esc(S().REFUND_TYPES[a.type] || a.type) + '</span><div class="tiny dim">' + esc(a.reason) + '</div></td>' +
        '<td class="num">' + price(a.amount) + '</td>' +
        '<td>' + U.asTag(a) + '</td>' +
        '<td class="num tiny">' + time(a.createdAt) + '</td>' +
        '<td><div class="ops">' + ops + '</div></td>' +
      '</tr>';
    }).join('');

    var body = rows
      ? '<table class="tbl"><thead><tr>' +
          '<th style="width:124px">售后号</th><th style="width:126px">订单号</th><th>商品行</th>' +
          '<th style="width:104px">类型 / 理由</th><th style="width:82px">金额</th><th style="width:112px">状态</th>' +
          '<th style="width:104px">申请时间</th><th style="width:200px">操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table>'
      : empty({ icon: '🧾', title: '没有符合条件的售后单', hint: '售后单由 C 端在订单行发起，审核状态在此处理' });

    var html =
      (pendingRestock.length ? U.alert('<b>资金已退但库存回补失败：' + pendingRestock.length + ' 笔售后单停留在「退款中」。</b>' +
        '退款与库存回补必须同事务完成（BR-AS-16），请点「重试回补并退款」；回补仍失败时售后单保持「退款中」，不允许标记退款完成（BR-AS-12）。', 'err') + '<div style="height:12px"></div>' : '') +
      toolbar(
        seg(tabs, 'as-tab') +
        '<div class="tiny dim mt6">退款执行顺序：同意 →（退货退款：待寄回 → 填单号 → 待收货）→ 确认收货 / 跳过收货 → 退款中 → 退款完成；退款与回补同事务（BR-AS-16）</div>',
        searchBar('as-search', F.as.kw, '售后号 / 订单号 / 商品名') +
        (F.as.kw ? btn('清除', 'as-reset', '', '') : '')
      ) + tblWrap(body);
    return { title: '售后处理台', html: html };
  }

  function asBody(a) {
    var timeline = (a.timeline || []).map(function (t, i, arr) {
      return '<div class="item ' + (i === arr.length - 1 ? 'now' : 'on') + '">' +
        '<div class="it">' + esc(t.text) + '</div><div class="is">' + time(t.at) + (t.by ? ' · ' + esc(t.by) : '') + '</div></div>';
    }).join('');
    return '<div class="row between" style="margin-bottom:8px"><div>' +
        '<div class="bold" style="font-size:15px">' + esc(a.sn) + '</div>' +
        '<div class="tiny dim">订单 ' + esc(a.orderSn) + ' · 申请 ' + time(a.createdAt) + '</div></div>' +
        '<div>' + U.asTag(a) + '</div></div>' +
      (a.restockPending ? U.alert('资金已退但库存回补失败，售后单停留「退款中」，必须重试回补成功后才能退款完成（BR-AS-12 / BR-AS-16）。', 'err') + '<div style="height:10px"></div>' : '') +
      '<div class="row" style="gap:10px;padding:8px 0">' + thumbOf(a, 'sm') +
        '<div class="grow"><div class="small bold">' + esc(a.name) + '</div><div class="tiny dim">' + esc(a.specText || '') + ' × ' + a.qty + '</div></div>' +
        '<div class="right">' + price(a.amount) + '</div></div>' +
      '<div class="small" style="line-height:1.9">' +
        '<div class="row between"><span class="muted">类型</span><span>' + esc(S().REFUND_TYPES[a.type] || a.type) + '</span></div>' +
        '<div class="row between"><span class="muted">理由</span><span>' + esc(a.reason) + '</span></div>' +
        '<div class="row between"><span class="muted">退款金额</span><span class="num bold">' + price(a.amount) + '</span></div>' +
        '<div class="row between"><span class="muted">退货单号</span><span>' + (a.trackingNo ? esc(a.trackingNo) : '未填写') + '</span></div>' +
        (a.returnAddress ? '<div class="row between"><span class="muted">退货地址</span><span class="right tiny">' + esc(a.returnAddress.region + ' ' + a.returnAddress.detail) + '</span></div>' : '') +
        (a.rejectReason ? '<div class="row between"><span class="muted">驳回理由</span><span style="color:var(--danger)">' + esc(a.rejectReason) + '</span></div>' : '') +
        '<div class="row between"><span class="muted">库存回补</span><span>' + (a.restocked ? '<span class="tag ok">已回补</span>' : a.restockPending ? '<span class="tag danger">待回补</span>' : '—') + '</span></div>' +
      '</div>' +
      '<div class="divider"></div><div class="small bold" style="margin-bottom:8px">处理进度</div>' +
      '<div class="timeline">' + (timeline || '<div class="tiny dim">暂无</div>') + '</div>';
  }

  function asDetailSheet(id) {
    var a = S().asById(id);
    if (!a) { toast('售后单不存在', 'err'); return; }
    U.sheet({
      title: '售后详情',
      wide: true,
      body: asBody(a),
      actions: [
        { text: '关闭' },
        a.status === 'apply' ? { text: '同意', type: 'primary', onClick: function () { setTimeout(function () { approveAs(id); }, 30); } } : null,
        a.status === 'refunding' ? { text: a.restockPending ? '重试回补并退款' : '执行退款', type: 'primary', onClick: function () { setTimeout(function () { doRefund(id); }, 30); } } : null
      ].filter(Boolean)
    });
  }

  function approveAs(id) {
    var a = S().asById(id);
    if (!a) return;
    U.confirm({
      title: '同意售后 · ' + a.sn,
      body: '<div style="line-height:1.8">类型：<b>' + esc(S().REFUND_TYPES[a.type]) + '</b>；退款金额 <b>¥' + yuan(a.amount) + '</b>。<br>' +
        (a.type === 'return'
          ? '同意后进入「待寄回」，系统会向买家展示退货地址；买家填写退货单号后进入「待收货」（BR-AS-08）。'
          : '同意后进入「退款中」，执行退款时按库存回补矩阵处理（BR-AS-07 / BR-AS-14）。') + '</div>',
      okText: '确认同意'
    }).then(function (ok) {
      if (!ok) return;
      var r = S().asApprove(id);
      if (!r.ok) { toast(r.msg, 'err'); return; }
      toast(r.msg, 'ok');
      refresh();
    });
  }

  function rejectAs(id) {
    var a = S().asById(id);
    if (!a) return;
    var reasons = S().AS_REASONS.map(function (r) {
      return '<button type="button" class="tag" style="cursor:pointer" data-aact="as-reason" data-id="' + esc(r) + '">' + esc(r) + '</button>';
    }).join(' ');
    U.sheet({
      title: '驳回售后 · ' + a.sn,
      body: U.alert('驳回必须填写理由，理由对买家可见且不得为空（BR-AS-22）。', 'warn') +
        '<div class="mt12">' + field('驳回理由', 'rj-reason', '', { textarea: true, req: true, ph: '例如：商品已使用且无质量问题，不符合退货条件' }) +
        '<div class="tiny dim" style="margin:-4px 0 4px">常用理由：' + reasons + '</div></div>',
      actions: [
        { text: '取消' },
        { text: '确认驳回', type: 'danger', onClick: function (ov) {
          var rsn = val('rj-reason').trim();
          if (!rsn) { sheetErr(ov, '驳回必须填写理由（BR-AS-22）'); return false; }
          var r = S().asReject(id, rsn);
          if (!r.ok) { sheetErr(ov, r.msg); return false; }
          toast(r.msg, 'ok');
          refresh();
        } }
      ]
    });
  }

  function skipAs(id) {
    var a = S().asById(id);
    if (!a) return;
    U.sheet({
      title: '一键跳过收货 · ' + a.sn,
      body: U.alert('跳过收货会让售后单直接进入「退款中」，必须留下操作日志（谁、何时、原因，BR-AS-09 / BR-ADM-03）。' +
        '<br>跳过收货后按「商品已寄回」处理，退款完成时回补库存（BR-AS-14）。建议仅在买家无法寄回、或已线下确认收到货时使用。', 'warn') +
        '<div class="mt12">' + field('跳过收货原因', 'sk-reason', '', { textarea: true, req: true, ph: '例如：买家反馈快递丢失，已电话确认，直接退款' }) + '</div>',
      actions: [
        { text: '取消' },
        { text: '确认跳过收货', type: 'danger', onClick: function (ov) {
          var rsn = val('sk-reason').trim();
          if (!rsn) { sheetErr(ov, '请填写跳过收货的原因'); return false; }
          var r = S().asSkipReceive(id, rsn);
          if (!r.ok) { sheetErr(ov, r.msg); return false; }
          toast(r.msg, 'ok');
          refresh();
        } }
      ]
    });
  }

  function doRefund(id) {
    var a = S().asById(id);
    if (!a) return;
    var pending = !!a.restockPending;
    U.confirm({
      title: '执行退款 · ' + a.sn,
      body: '<div style="line-height:1.8">退款金额 <b>¥' + yuan(a.amount) + '</b>。' +
        '退款与库存回补在同一次操作内完成：要么都成功（转「退款完成」），要么停在「退款中」，不允许「退款完成但库存没回补」（BR-AS-16 / BR-AS-12）。</div>' +
        (pending ? '<div class="alert err" style="margin-top:10px">上次库存回补失败。本次将先重试回补，仍失败则售后单继续停留「退款中」，并标红「库存待回补」。</div>' : ''),
      okText: pending ? '重试并退款' : '确认退款'
    }).then(function (ok) {
      if (!ok) return;
      var r = pending ? S().asRetryRestock(id) : S().asRefund(id);
      if (!r.ok) {
        toast(pending ? ('库存回补失败：' + r.msg + '，售后单仍停留「退款中」') : r.msg, 'err');
        refresh();
        return;
      }
      toast(r.msg, 'ok');
      refresh();
    });
  }

  function autoCloseAs(id) {
    var a = S().asById(id);
    if (!a) return;
    U.confirm({
      title: '关闭售后单 · ' + a.sn,
      body: '「待寄回」超 7 天未填退货单号会自动关闭（BR-AS-10）；此处手动触发同一结果。关闭后为终态，该订单行可重新申请。',
      okText: '确认关闭'
    }).then(function (ok) {
      if (!ok) return;
      var r = S().asAutoClose(id);
      if (!r.ok) { toast(r.msg, 'err'); return; }
      toast(r.msg, 'ok');
      refresh();
    });
  }

  /* ------------------------------ 5. 商品管理 ------------------------------ */
  var EMOJIS = ['👕', '👖', '👟', '🧥', '👜', '☕', '🍵', '🎧', '⌨️', '🕯️', '💡', '🧺', '🧦', '🧴', '🛍️'];

  function specKeysOf(g) {
    var keys = [];
    (g.skus || []).forEach(function (s) {
      Object.keys(s.specs || {}).forEach(function (k) { if (keys.indexOf(k) < 0) keys.push(k); });
    });
    return keys;
  }
  function leafCats() {
    return st().cats.filter(function (c) { return !!c.parentId; });
  }
  function catOptions(sel) {
    var cats = st().cats;
    var out = '<option value="">未分类</option>';
    cats.filter(function (c) { return !c.parentId; }).forEach(function (p) {
      var kids = cats.filter(function (c) { return c.parentId === p.id; });
      if (kids.length) {
        out += '<optgroup label="' + esc(p.name) + '">' + kids.map(function (k) {
          return '<option value="' + esc(k.id) + '"' + (k.id === sel ? ' selected' : '') + '>' + esc(p.name + ' / ' + k.name) + '</option>';
        }).join('') + '</optgroup>';
      } else {
        out += '<option value="' + esc(p.id) + '"' + (p.id === sel ? ' selected' : '') + '>' + esc(p.name) + '</option>';
      }
    });
    return out;
  }
  function brandOptions(sel) {
    return st().brands.map(function (b) {
      return '<option value="' + esc(b.id) + '"' + (b.id === sel ? ' selected' : '') + '>' + esc(b.name) + '</option>';
    }).join('');
  }

  function goodsScreen() {
    var all = st().goods;
    var counts = {
      all: all.length,
      on: all.filter(function (g) { return g.onSale; }).length,
      off: all.filter(function (g) { return !g.onSale; }).length
    };
    var list = F.goods.tab === 'on' ? all.filter(function (g) { return g.onSale; })
      : F.goods.tab === 'off' ? all.filter(function (g) { return !g.onSale; }) : all;
    var tabs = [
      { key: 'all', label: '全部', n: counts.all, on: F.goods.tab === 'all' },
      { key: 'on', label: '在售', n: counts.on, on: F.goods.tab === 'on' },
      { key: 'off', label: '已下架', n: counts.off, on: F.goods.tab === 'off' }
    ];

    var rows = list.map(function (g) {
      var physical = sum(g.skus, function (s) { return s.number; });
      var sellable = S().totalStock(g);
      return '<tr>' +
        '<td>' + thumbOf({ bg: g.pics[0], emoji: g.pics[1] }, 'sm') + '</td>' +
        '<td><div class="small bold ellipsis" style="max-width:250px" title="' + esc(g.name) + '">' + esc(g.name) + '</div>' +
          '<div class="tiny dim ellipsis" style="max-width:250px">' + esc(g.brief) + '</div>' +
          '<div class="tiny dim">' + g.skus.length + ' 个规格 · 销量 ' + (g.sales || 0) + '</div></td>' +
        '<td class="small">' + esc(catPath(g.catId)) + '</td>' +
        '<td class="small">' + esc(brandName(g.brandId)) + '</td>' +
        '<td class="num">' + price(S().displayPrice(g)) + '</td>' +
        '<td class="num small">' + sellable + '<div class="tiny dim">实物 ' + physical + '</div></td>' +
        '<td>' + (g.onSale ? '<span class="tag ok">在售</span>' : '<span class="tag gray">已下架</span>') + '</td>' +
        '<td><div class="ops">' +
          '<button type="button" class="btn sm" data-aact="goods-edit" data-id="' + esc(g.id) + '">编辑</button>' +
          '<button type="button" class="btn sm ' + (g.onSale ? 'danger' : 'ok') + '" data-aact="goods-toggle" data-id="' + esc(g.id) + '">' + (g.onSale ? '下架' : '上架') + '</button>' +
        '</div></td>' +
      '</tr>';
    }).join('');

    var body = rows
      ? '<table class="tbl"><thead><tr>' +
          '<th style="width:58px">图片</th><th>商品</th><th style="width:120px">分类</th><th style="width:108px">品牌</th>' +
          '<th style="width:88px">展示价</th><th style="width:96px">可售 / 总库存</th><th style="width:72px">状态</th><th style="width:128px">操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table>'
      : empty({ icon: '🧺', title: '没有符合条件的商品', hint: '新建商品或切换在售筛选' });

    var html = toolbar(
      seg(tabs, 'goods-tab') +
        '<div class="tiny dim mt6">展示价 = 可售规格最低价（BR-GOODS-05）；每个规格是独立售卖与库存单位（7.3）</div>',
      '<button type="button" class="btn sm primary" data-aact="goods-new">＋ 新建商品</button>'
    ) + tblWrap(body);
    return { title: '商品管理', html: html };
  }

  function sheetGoods(id) {
    var src = id ? st().goods.filter(function (g) { return g.id === id; })[0] : null;
    if (id && !src) { toast('商品不存在', 'err'); return; }
    var isNew = !src;
    var firstCat = leafCats()[0];
    var draft = src ? JSON.parse(JSON.stringify(src)) : {
      id: '', name: '', brief: '', catId: firstCat ? firstCat.id : '',
      brandId: st().brands[0] ? st().brands[0].id : '', onSale: true, sales: 0, rate: 100,
      tags: [], pics: ['#FF7A45', '🛍️'], detail: '',
      skus: [{ id: '', specs: { '颜色': '', '尺码': '' }, price: 0, number: 0 }]
    };
    var keys = specKeysOf(draft);
    if (!keys.length) keys = ['颜色', '尺码'];

    var body =
      '<div class="row" style="gap:12px;align-items:flex-start">' +
        '<div class="thumb" id="gd-pic" style="background:linear-gradient(135deg,' + esc(draft.pics[0]) + ',#ffffff88);width:62px;height:62px;font-size:28px">' + esc(draft.pics[1] || '🛍️') + '</div>' +
        '<div class="grow">' + field('商品名称', 'gd-name', draft.name, { req: true, ph: '如：重磅纯棉oversize T恤' }) + '</div>' +
      '</div>' +
      field('商品简介', 'gd-brief', draft.brief, { textarea: true, rows: 2, ph: '一句话卖点，会显示在商品卡片上' }) +
      '<div class="row" style="gap:10px;align-items:flex-start">' +
        '<div class="grow">' + selField('分类', 'gd-cat', [], draft.catId, {}) + '</div>' +
        '<div class="grow">' + selField('品牌', 'gd-brand', [], draft.brandId, {}) + '</div>' +
        '<div class="grow">' + selField('在售状态', 'gd-onsale', [{ v: '1', t: '在售' }, { v: '0', t: '下架' }], draft.onSale ? '1' : '0', {}) + '</div>' +
      '</div>' +
      '<div class="field"><label>商品图（演示用 emoji）</label><div class="row" style="gap:6px;flex-wrap:wrap">' +
        EMOJIS.map(function (e) {
          return '<button type="button" class="btn sm ' + (draft.pics[1] === e ? 'primary' : '') + '" data-emoji="' + e + '" style="padding:0 7px;font-size:15px">' + e + '</button>';
        }).join('') + '</div></div>' +
      '<div class="field"><label>规格项（用 / 分隔）' + (isNew ? '' : '<span class="tiny dim" style="font-weight:400">已有商品不可改规格项</span>') + '</label>' +
        '<input class="input" id="gd-keys" value="' + esc(keys.join('/')) + '"' + (isNew ? '' : ' readonly') + '>' +
        '<div class="help">每个规格 = 库存矩阵一行（BR-GOODS-01）；价格单位元，库存为实物库存，可售 = 实物 − 已锁。</div></div>' +
      '<div id="gd-sku-wrap"></div>';

    U.sheet({
      title: isNew ? '新建商品' : '编辑商品 · ' + (src.name || ''),
      wide: true,
      body: body,
      onMount: function (ov) {
        // 分类 / 品牌下拉用 JS 注入，避免把 optgroup 拼进通用组件
        var catSel = ov.querySelector('#gd-cat');
        catSel.innerHTML = catOptions(draft.catId);
        var brandSel = ov.querySelector('#gd-brand');
        brandSel.innerHTML = brandOptions(draft.brandId);

        function readSkuInputs() {
          ov.querySelectorAll('tr[data-row]').forEach(function (tr) {
            var s = draft.skus[Number(tr.getAttribute('data-row'))];
            if (!s) return;
            tr.querySelectorAll('input[data-k]').forEach(function (inp) { s.specs[inp.getAttribute('data-k')] = inp.value; });
            var p = tr.querySelector('.sku-price');
            if (p) { var c = cent(p.value); s.price = c == null ? 0 : c; }
            var n = tr.querySelector('.sku-num');
            if (n) s.number = intOf(n.value, 0);
          });
        }

        function renderSku() {
          var ks = (val('gd-keys') || '').split('/').map(function (x) { return x.trim(); }).filter(Boolean);
          if (!ks.length) ks = ['规格'];
          draft.skus.forEach(function (s) {
            var ns = {};
            ks.forEach(function (k) { ns[k] = s.specs[k] !== undefined ? s.specs[k] : ''; });
            s.specs = ns;
          });
          var head = ks.map(function (k) { return '<th>' + esc(k) + '</th>'; }).join('');
          var trs = draft.skus.map(function (s, i) {
            var cells = ks.map(function (k) {
              return '<td><input class="input" data-k="' + esc(k) + '" value="' + esc(s.specs[k] || '') + '" style="height:30px;padding:0 8px;font-size:12.5px" placeholder="' + esc(k) + '"></td>';
            }).join('');
            return '<tr data-row="' + i + '">' + cells +
              '<td><input class="input sku-price" type="number" step="0.01" min="0" value="' + esc(yuan(s.price)) + '" style="height:30px;padding:0 8px;font-size:12.5px;width:92px"></td>' +
              '<td><input class="input sku-num" type="number" step="1" min="0" value="' + s.number + '" style="height:30px;padding:0 8px;font-size:12.5px;width:74px"></td>' +
              '<td><button type="button" class="btn sm danger" data-del="' + i + '" style="padding:0 8px">删</button></td>' +
            '</tr>';
          }).join('');
          var wrap = ov.querySelector('#gd-sku-wrap');
          wrap.innerHTML =
            '<div class="tbl-wrap" style="box-shadow:none;border:1px solid var(--line-2)">' +
              '<table class="tbl"><thead><tr>' + head +
                '<th style="width:100px">价格（元）</th><th style="width:82px">库存</th><th style="width:52px"></th>' +
              '</tr></thead><tbody>' + trs + '</tbody></table>' +
            '</div>' +
            '<button type="button" class="btn sm mt8" id="gd-add-sku">＋ 新增规格</button>' +
            '<span class="tiny dim" style="margin-left:8px">至少保留 1 个规格（BR-GOODS-01）</span>';
          wrap.querySelector('#gd-add-sku').addEventListener('click', function () {
            readSkuInputs();
            var blank = {};
            ks.forEach(function (k) { blank[k] = ''; });
            draft.skus.push({ id: '', specs: blank, price: draft.skus[0] ? draft.skus[0].price : 0, number: 0 });
            renderSku();
          });
          wrap.querySelectorAll('[data-del]').forEach(function (b) {
            b.addEventListener('click', function () {
              if (draft.skus.length <= 1) { toast('至少保留 1 个规格（BR-GOODS-01）', 'err'); return; }
              readSkuInputs();
              draft.skus.splice(Number(b.getAttribute('data-del')), 1);
              renderSku();
            });
          });
        }
        renderSku();
        var keyInput = ov.querySelector('#gd-keys');
        keyInput.addEventListener('change', function () { readSkuInputs(); renderSku(); });
        ov.querySelectorAll('[data-emoji]').forEach(function (b) {
          b.addEventListener('click', function () {
            draft.pics[1] = b.getAttribute('data-emoji');
            ov.querySelector('#gd-pic').textContent = draft.pics[1];
            ov.querySelectorAll('[data-emoji]').forEach(function (x) { x.classList.toggle('primary', x === b); });
          });
        });
        // 保存时把编辑器里的最新值回填到 draft
        ov.__readDraft = function () {
          readSkuInputs();
          draft.name = val('gd-name').trim();
          draft.brief = val('gd-brief').trim();
          draft.catId = val('gd-cat');
          draft.brandId = val('gd-brand');
          draft.onSale = val('gd-onsale') === '1';
          draft.skus.forEach(function (s) { if (!s.id) s.id = 'sk' + Date.now().toString(36) + Math.floor(Math.random() * 1000); });
        };
      },
      actions: [
        { text: '取消' },
        { text: isNew ? '创建商品' : '保存修改', type: 'primary', onClick: function (ov) {
          if (ov.__readDraft) ov.__readDraft();
          if (!draft.name) { sheetErr(ov, '请填写商品名称'); return false; }
          var ks = specKeysOf(draft);
          if (!ks.length) { sheetErr(ov, '请至少配置一个规格'); return false; }
          for (var i = 0; i < draft.skus.length; i++) {
            var s = draft.skus[i];
            for (var j = 0; j < ks.length; j++) {
              if (!String(s.specs[ks[j]] || '').trim()) { sheetErr(ov, '第 ' + (i + 1) + ' 个规格的「' + ks[j] + '」未填写'); return false; }
            }
            if (!(s.price >= 0)) { sheetErr(ov, '第 ' + (i + 1) + ' 个规格的价格不能为空或负数'); return false; }
            if (!(s.number >= 0)) { sheetErr(ov, '第 ' + (i + 1) + ' 个规格的库存不能为空或负数'); return false; }
          }
          var r = S().goodsSave(draft);
          if (!r.ok) { sheetErr(ov, r.msg || '保存失败'); return false; }
          toast(isNew ? '商品已创建' : '商品已保存', 'ok');
          refresh();
        } }
      ]
    });
  }

  /* ------------------------------ 6. 分类与品牌 ------------------------------ */
  function catalogScreen() {
    var cats = st().cats;
    var tops = cats.filter(function (c) { return !c.parentId; }).sort(function (a, b) { return (a.sort || 0) - (b.sort || 0); });

    function catRow(c, level) {
      var kids = cats.filter(function (x) { return x.parentId === c.id; });
      var used = st().goods.filter(function (g) { return g.catId === c.id; }).length;
      return '<tr>' +
        '<td><span style="padding-left:' + (level === 2 ? 22 : 0) + 'px" class="small ' + (level === 1 ? 'bold' : '') + '">' +
          (level === 2 ? '<span class="dim">└ </span>' : '') + esc(c.name) + '</span></td>' +
        '<td><span class="tag ' + (level === 1 ? 'outline' : 'gray') + '">' + (level === 1 ? '一级' : '二级') + '</span></td>' +
        '<td class="num tiny">' + (c.sort || 0) + '</td>' +
        '<td class="num small">' + used + (level === 1 && kids.length ? ' <span class="tiny dim">（含子类 ' + kids.reduce(function (a, k) {
          return a + st().goods.filter(function (g) { return g.catId === k.id; }).length;
        }, 0) + '）</span>' : '') + '</td>' +
        '<td><div class="ops">' +
          (level === 1 ? '<button type="button" class="btn sm" data-aact="cat-add" data-id="' + esc(c.id) + '">加子类</button>' : '') +
          '<button type="button" class="btn sm" data-aact="cat-edit" data-id="' + esc(c.id) + '">改名</button>' +
          '<button type="button" class="btn sm danger" data-aact="cat-del" data-id="' + esc(c.id) + '">删除</button>' +
        '</div></td>' +
      '</tr>';
    }
    var catRows = '';
    tops.forEach(function (p) {
      catRows += catRow(p, 1);
      cats.filter(function (c) { return c.parentId === p.id; }).sort(function (a, b) { return (a.sort || 0) - (b.sort || 0); })
        .forEach(function (k) { catRows += catRow(k, 2); });
    });

    var brandRows = st().brands.map(function (b) {
      var n = st().goods.filter(function (g) { return g.brandId === b.id; }).length;
      return '<tr>' +
        '<td class="small bold">' + esc(b.name) + '</td>' +
        '<td class="num small">' + n + '</td>' +
        '<td><div class="ops">' +
          '<button type="button" class="btn sm" data-aact="brand-edit" data-id="' + esc(b.id) + '">改名</button>' +
          '<button type="button" class="btn sm danger" data-aact="brand-del" data-id="' + esc(b.id) + '">删除</button>' +
        '</div></td>' +
      '</tr>';
    }).join('');

    var html =
      toolbar(
        '<div class="small bold">分类与品牌</div><div class="tiny dim mt6">两级分类：顶级 + 子类（BR-CAT-01）；删除有商品的分类会被拒绝（R2 第 8 步）。</div>',
        '<button type="button" class="btn sm primary" data-aact="cat-add" data-id="">＋ 新增一级分类</button>' +
        '<button type="button" class="btn sm" data-aact="brand-add">＋ 新增品牌</button>'
      ) +
      panel('分类树',
        '<div class="tbl-wrap" style="box-shadow:none">' +
          '<table class="tbl"><thead><tr><th>分类名称</th><th style="width:80px">层级</th><th style="width:64px">排序</th><th style="width:130px">商品数</th><th style="width:210px">操作</th></tr></thead>' +
          '<tbody>' + (catRows || '<tr><td colspan="5">' + empty({ icon: '🗂️', title: '还没有分类' }) + '</td></tr>') + '</tbody></table>' +
        '</div>',
        '<span class="tiny dim">共 ' + cats.length + ' 个分类</span>') +
      panel('品牌列表',
        '<div class="tbl-wrap" style="box-shadow:none">' +
          '<table class="tbl"><thead><tr><th>品牌名称</th><th style="width:120px">商品数</th><th style="width:160px">操作</th></tr></thead>' +
          '<tbody>' + (brandRows || '<tr><td colspan="3">' + empty({ icon: '🏷️', title: '还没有品牌' }) + '</td></tr>') + '</tbody></table>' +
        '</div>',
        '<span class="tiny dim">共 ' + st().brands.length + ' 个品牌</span>');
    return { title: '分类与品牌', html: html };
  }

  function catSheet(id, parentId) {
    var c = id ? st().cats.filter(function (x) { return x.id === id; })[0] : null;
    if (id && !c) return;
    var parentVal = c ? (c.parentId || '') : (parentId || '');
    var tops = st().cats.filter(function (x) { return !x.parentId && x.id !== id; });
    var opts = [{ v: '', t: '（顶级分类）' }].concat(tops.map(function (p) { return { v: p.id, t: p.name }; }));
    U.sheet({
      title: c ? '编辑分类' : (parentId ? '新增子类' : '新增一级分类'),
      body: field('分类名称', 'ct-name', c ? c.name : '', { req: true }) +
        selField('上级分类', 'ct-parent', opts, parentVal, { hint: '两级分类：选择顶级分类即为新增二级分类（BR-CAT-01）' }) +
        field('排序', 'ct-sort', c ? (c.sort || 0) : (st().cats.length + 1), { type: 'number', min: 0 }),
      actions: [
        { text: '取消' },
        { text: '保存', type: 'primary', onClick: function (ov) {
          var name = val('ct-name').trim();
          if (!name) { sheetErr(ov, '请填写分类名称'); return false; }
          var parent = val('ct-parent') || null;
          if (parent === id) { sheetErr(ov, '上级分类不能是自己'); return false; }
          var sort = intOf(val('ct-sort'), 0);
          if (c) {
            c.name = name; c.parentId = parent; c.sort = sort;
            toast('分类已更新', 'ok');
          } else {
            st().cats.push({ id: 'c' + Date.now().toString(36), name: name, parentId: parent, sort: sort });
            toast('分类已新增', 'ok');
          }
          S().save();
          refresh();
        } }
      ]
    });
  }

  function brandSheet(id) {
    var b = id ? st().brands.filter(function (x) { return x.id === id; })[0] : null;
    if (id && !b) return;
    U.sheet({
      title: b ? '编辑品牌' : '新增品牌',
      body: field('品牌名称', 'bd-name', b ? b.name : '', { req: true }),
      actions: [
        { text: '取消' },
        { text: '保存', type: 'primary', onClick: function (ov) {
          var name = val('bd-name').trim();
          if (!name) { sheetErr(ov, '请填写品牌名称'); return false; }
          if (b) { b.name = name; toast('品牌已更新', 'ok'); }
          else { st().brands.push({ id: 'b' + Date.now().toString(36), name: name }); toast('品牌已新增', 'ok'); }
          S().save();
          refresh();
        } }
      ]
    });
  }

  /* ------------------------------ 7. 库存视图 ------------------------------ */
  function stockScreen() {
    var allRows = S().stockRows({ kw: F.stock.kw });
    var lowCount = allRows.filter(function (r) { return r.low; }).length;
    var zeroCount = allRows.filter(function (r) { return r.sellable <= 0; }).length;
    var rows = F.stock.tab === 'low' ? allRows.filter(function (r) { return r.low; })
      : F.stock.tab === 'zero' ? allRows.filter(function (r) { return r.sellable <= 0; })
        : allRows;
    rows = rows.slice().sort(function (a, b) { return a.sellable - b.sellable; });

    var tabs = [
      { key: 'all', label: '全部 SKU', n: allRows.length, on: F.stock.tab === 'all' },
      { key: 'low', label: '低库存（≤5）', n: lowCount, on: F.stock.tab === 'low' },
      { key: 'zero', label: '缺货（=0）', n: zeroCount, on: F.stock.tab === 'zero' }
    ];

    var trs = rows.map(function (r) {
      var cls = r.sellable <= 0 ? 'zero' : (r.low ? 'low' : '');
      var pct = r.sellable <= 0 ? 0 : Math.min(100, Math.round(r.sellable / 20 * 100));
      var status = r.sellable <= 0 ? '<span class="tag danger">缺货</span>'
        : (r.low ? '<span class="tag warn">低库存</span>' : '<span class="tag ok">正常</span>');
      return '<tr>' +
        '<td><div class="small ellipsis" style="max-width:230px" title="' + esc(r.goodsName) + '">' + esc(r.goodsName) + '</div>' +
          (r.onSale ? '' : '<span class="tag gray">已下架</span>') + '</td>' +
        '<td class="small">' + esc(r.specText) + '</td>' +
        '<td class="num">' + price(r.price) + '</td>' +
        '<td><input class="input" type="number" min="0" step="1" value="' + r.number + '" data-achange="stock-num" data-id="' + esc(r.skuId) + '" style="width:78px;height:30px;padding:0 8px;font-size:12.5px"></td>' +
        '<td class="num tiny">' + r.locked + '</td>' +
        '<td class="num ' + (r.sellable <= 0 ? 'bold' : '') + '">' + r.sellable + '</td>' +
        '<td><div class="bar ' + cls + '" style="width:88px"><i style="width:' + pct + '%"></i></div>' +
          '<div class="tiny dim" style="margin-top:4px">' + (r.sellable <= 0 ? '可售为 0' : r.low ? '低于阈值 5' : '水位充足') + '</div></td>' +
        '<td>' + status + '</td>' +
      '</tr>';
    }).join('');

    var body = trs
      ? '<table class="tbl"><thead><tr>' +
          '<th>商品名</th><th style="width:130px">SKU 规格</th><th style="width:86px">价格</th>' +
          '<th style="width:96px">实物库存</th><th style="width:58px">锁定</th><th style="width:58px">可售</th>' +
          '<th style="width:110px">库存水位</th><th style="width:82px">状态</th>' +
        '</tr></thead><tbody>' + trs + '</tbody></table>'
      : empty({ icon: '📦', title: '没有符合条件的 SKU', hint: '调整筛选或关键词后重试' });

    var html =
      (zeroCount ? U.alert('<b>' + zeroCount + ' 个 SKU 可售为 0。</b>下单会锁定库存，支付成功后转为实扣；可售 = 实物 − 已锁（7.3）。缺货规格在 C 端不可加购。', 'warn') + '<div style="height:12px"></div>' : '') +
      toolbar(
        seg(tabs, 'stock-tab') +
        '<div class="tiny dim mt6">直接修改「实物库存」后失焦即保存：库存不能为负，也不能低于已锁数量（BR-ORDER-12）；低库存阈值 5（BR-GOODS-10）。</div>',
        searchBar('stock-search', F.stock.kw, '商品名 / 规格') + (F.stock.kw ? btn('清除', 'stock-reset', '', '') : '')
      ) + tblWrap(body);
    return { title: '库存视图', html: html };
  }

  /* ------------------------------ 8. 优惠券管理 ------------------------------ */
  function couponStatus(c) {
    if (c.disabled) return '<span class="tag gray">已停用</span>';
    if (c.claimed >= c.total) return '<span class="tag warn">已领完</span>';
    if (c.expireAt && c.expireAt < Date.now()) return '<span class="tag gray">已过期</span>';
    return '<span class="tag ok">进行中</span>';
  }
  function couponsScreen() {
    var list = st().coupons.slice().sort(function (a, b) { return a.expireAt - b.expireAt; });
    var rows = list.map(function (c) {
      return '<tr>' +
        '<td><div class="small bold">' + esc(c.name) + '</div><div class="tiny dim">券 ID：' + esc(c.id) + '</div></td>' +
        '<td class="num bold">' + price(c.amount) + '</td>' +
        '<td class="small">' + (c.threshold ? '满 ' + yuan(c.threshold) + ' 元' : '无门槛') + '</td>' +
        '<td class="small">至 ' + time(c.expireAt, false) + '</td>' +
        '<td class="num small">' + c.claimed + '</td>' +
        '<td class="num small">' + c.total + '</td>' +
        '<td>' + couponStatus(c) + '</td>' +
        '<td><div class="ops">' +
          '<button type="button" class="btn sm" data-aact="cp-edit" data-id="' + esc(c.id) + '">编辑</button>' +
          '<button type="button" class="btn sm" data-aact="cp-toggle" data-id="' + esc(c.id) + '">' + (c.disabled ? '启用' : '停用') + '</button>' +
          (c.claimed > 0
            ? '<button type="button" class="btn sm" disabled title="已有领取记录，只能停用不能删除（BR-CP-10）">删除</button>'
            : '<button type="button" class="btn sm danger" data-aact="cp-del" data-id="' + esc(c.id) + '">删除</button>') +
        '</div></td>' +
      '</tr>';
    }).join('');

    var body = rows
      ? '<table class="tbl"><thead><tr>' +
          '<th>券名称</th><th style="width:84px">面额</th><th style="width:112px">使用门槛</th><th style="width:118px">有效期</th>' +
          '<th style="width:62px">已领</th><th style="width:62px">总量</th><th style="width:80px">状态</th><th style="width:196px">操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table>'
      : empty({ icon: '🎟️', title: '还没有优惠券', hint: '新建一张券供 C 端领取' });

    var html = toolbar(
      '<div class="small bold">优惠券</div><div class="tiny dim mt6">一人一券、一单一张、整单全退才退券（BR-CP-01/04/07）；已发放的券面额不可修改（BR-CP-10）。</div>',
      '<button type="button" class="btn sm primary" data-aact="cp-new">＋ 新建优惠券</button>'
    ) + tblWrap(body);
    return { title: '优惠券管理', html: html };
  }

  function couponSheet(id) {
    var c = id ? st().coupons.filter(function (x) { return x.id === id; })[0] : null;
    if (id && !c) return;
    var locked = !!c && c.claimed > 0;
    U.sheet({
      title: c ? '编辑优惠券 · ' + c.name : '新建优惠券',
      wide: true,
      body:
        (locked ? U.alert('该券已发放 ' + c.claimed + ' 张，面额不可修改（BR-CP-10：保护历史订单展示）。只能改名称、门槛、总量与有效期。', 'warn') + '<div style="height:12px"></div>' : '') +
        field('券名称', 'cp-name', c ? c.name : '', { req: true, ph: '如：满 99 减 10' }) +
        '<div class="row" style="gap:10px">' +
          '<div class="grow">' + field('面额（元）', 'cp-amount', c ? yuan(c.amount) : '10', { type: 'number', step: '0.01', min: 0, req: true }) + '</div>' +
          '<div class="grow">' + field('使用门槛（元，0=无门槛）', 'cp-thr', c ? yuan(c.threshold) : '99', { type: 'number', step: '0.01', min: 0, req: true }) + '</div>' +
        '</div>' +
        '<div class="row" style="gap:10px">' +
          '<div class="grow">' + field('发放总量', 'cp-total', c ? c.total : '100', { type: 'number', min: 1, req: true, hint: c ? '不能小于已领取数量 ' + c.claimed : '领完即提示「已领完」' }) + '</div>' +
          '<div class="grow">' + field('有效期至', 'cp-exp', c ? dayText(c.expireAt) : dayText(Date.now() + 30 * 86400000), { type: 'date', req: true }) + '</div>' +
        '</div>' +
        '<div class="tiny dim">有效期为当天 23:59:59；下单占用、关单/取消退回时有效期不延长（BR-CP-06）。</div>',
      onMount: function (ov) {
        if (locked) {
          var a = ov.querySelector('#cp-amount');
          if (a) { a.disabled = true; a.title = '已发放券面额不可修改'; }
        }
      },
      actions: [
        { text: '取消' },
        { text: '保存', type: 'primary', onClick: function (ov) {
          var name = val('cp-name').trim();
          if (!name) { sheetErr(ov, '请填写券名称'); return false; }
          var amount = locked ? c.amount : cent(val('cp-amount'));
          if (amount == null || amount <= 0) { sheetErr(ov, '面额必须大于 0'); return false; }
          var thr = cent(val('cp-thr'));
          if (thr == null || thr < 0) { sheetErr(ov, '门槛金额不能为负'); return false; }
          var total = intOf(val('cp-total'), -1);
          if (total < 1) { sheetErr(ov, '发放总量至少为 1'); return false; }
          if (c && total < c.claimed) { sheetErr(ov, '总量不能小于已领取数量（' + c.claimed + ' 张）'); return false; }
          var expStr = val('cp-exp');
          var exp = expStr ? new Date(expStr + 'T23:59:59').getTime() : NaN;
          if (!exp || isNaN(exp)) { sheetErr(ov, '请选择有效期'); return false; }
          if (c) {
            c.name = name; c.amount = amount; c.threshold = thr; c.total = total; c.expireAt = exp;
            S().log('修改优惠券「' + name + '」：面额 ' + yuan(amount) + ' 元、门槛 ' + yuan(thr) + ' 元、总量 ' + total);
          } else {
            st().coupons.push({ id: 'cp' + Date.now().toString(36), name: name, amount: amount, threshold: thr, total: total, claimed: 0, expireAt: exp, disabled: false });
            S().log('新建优惠券「' + name + '」：面额 ' + yuan(amount) + ' 元、总量 ' + total);
          }
          S().save();
          toast(c ? '优惠券已更新' : '优惠券已创建', 'ok');
          refresh();
        } }
      ]
    });
  }

  /* ------------------------------ 9. 评价管理 ------------------------------ */
  function commentsScreen() {
    var all = st().reviews.slice().sort(function (a, b) { return b.at - a.at; });
    var low = all.filter(function (r) { return r.rate <= 3; });
    var un = all.filter(function (r) { return !r.reply; });
    var re = all.filter(function (r) { return !!r.reply; });
    var counts = { all: all.length, low: low.length, un: un.length, re: re.length };
    var list = F.comments.tab === 'low' ? low : F.comments.tab === 'un' ? un : F.comments.tab === 're' ? re : all;
    var tabs = [
      { key: 'all', label: '全部评价', n: counts.all, on: F.comments.tab === 'all' },
      { key: 'low', label: '低分（≤3 星）', n: counts.low, on: F.comments.tab === 'low' },
      { key: 'un', label: '待回复', n: counts.un, on: F.comments.tab === 'un' },
      { key: 're', label: '已回复', n: counts.re, on: F.comments.tab === 're' }
    ];

    var rows = list.map(function (r) {
      return '<tr>' +
        '<td>' + starRate(r.rate, 14) + '<div class="tiny dim">' + r.rate + ' 星</div></td>' +
        '<td class="small">' + esc(r.user || '匿名') + (r.orderId ? '<div class="tiny dim">订单评价</div>' : '<div class="tiny dim">种子评价</div>') + '</td>' +
        '<td><div class="small clamp2" style="max-width:330px" title="' + esc(r.content) + '">' + esc(r.content) + '</div>' +
          (r.reply ? '<div class="tiny" style="color:var(--ok);margin-top:4px">商家回复：' + esc(r.reply) + '</div>' : '') + '</td>' +
        '<td class="num tiny">' + time(r.at) + '</td>' +
        '<td class="small">' + esc(goodsName(r.goodsId)) + '</td>' +
        '<td>' + (r.reply ? '<span class="tag ok">已回复</span>' : '<span class="tag warn">待回复</span>') + '</td>' +
        '<td><div class="ops"><button type="button" class="btn sm" data-aact="cm-reply" data-id="' + esc(r.id) + '">' + (r.reply ? '改回复' : '回复') + '</button></div></td>' +
      '</tr>';
    }).join('');

    var body = rows
      ? '<table class="tbl"><thead><tr>' +
          '<th style="width:104px">评分</th><th style="width:100px">用户</th><th>评价内容</th>' +
          '<th style="width:116px">时间</th><th style="width:150px">商品</th><th style="width:82px">回复状态</th><th style="width:82px">操作</th>' +
        '</tr></thead><tbody>' + rows + '</tbody></table>'
      : empty({ icon: '💬', title: '没有符合条件的评价', hint: '评价由 C 端「已收货」订单行提交（BR-CM-01）' });

    var html = toolbar(
      seg(tabs, 'cm-tab') +
        '<div class="tiny dim mt6">每条评价只能有 1 条回复、可修改（BR-CM-03）；已退款订单行不可评价（BR-CM-05）。</div>',
      '<a class="btn sm" href="#/admin/comments?tab=un" data-go="#/admin/comments?tab=un" data-aact="cm-tab" data-key="un">只看待回复</a>'
    ) + tblWrap(body);
    return { title: '评价管理', html: html };
  }

  function replySheet(id) {
    var r = st().reviews.filter(function (x) { return x.id === id; })[0];
    if (!r) { toast('评价不存在', 'err'); return; }
    U.sheet({
      title: '回复评价',
      body:
        '<div class="row" style="gap:10px;padding:8px 10px;background:#f7f8fa;border-radius:10px">' +
          '<div class="grow"><div class="small">' + starRate(r.rate, 13) + ' · ' + esc(r.user || '匿名') + '</div>' +
          '<div class="tiny dim mt6">' + esc(r.content) + '</div></div>' +
        '</div>' +
        '<div class="mt12">' + field('商家回复', 'cm-reply-text', r.reply || '', { textarea: true, req: true, ph: '例如：感谢支持，已为您补发一份小样～' }) +
        '<div class="tiny dim">回复会展示在 C 端商品详情该条评价下；再次保存即覆盖旧回复（BR-CM-03）。</div></div>',
      actions: [
        { text: '取消' },
        { text: '保存回复', type: 'primary', onClick: function (ov) {
          var txt = val('cm-reply-text').trim();
          if (!txt) { sheetErr(ov, '回复内容不能为空'); return false; }
          var res = S().reviewReply(id, txt);
          if (!res.ok) { sheetErr(ov, res.msg); return false; }
          toast(res.msg, 'ok');
          refresh();
        } }
      ]
    });
  }

  /* ------------------------------ 10. 演示工具 ------------------------------ */
  function r1Steps() {
    var orders = st().orders;
    var unpaid = orders.filter(function (o) { return o.status === 'unpaid'; })[0];
    var shipped = orders.filter(function (o) { return o.status === 'shipped'; })[0];
    var recv = orders.filter(function (o) { return o.status === 'received' || o.status === 'done'; })[0];
    return [
      { n: 1, t: '后台新建「T 恤」，配「黑色/L」「白色/M」，各设价格与库存', d: '保存后列表出现该商品，展示价 99；两规格各占库存矩阵一行', go: '#/admin/goods' },
      { n: 2, t: 'C 端搜索 T 恤 → 商品详情 → 选「黑色/L」', d: '价格与库存随规格变化；未选规格不可加购', go: '#/c/list?kw=T恤' },
      { n: 3, t: '加购两行 → 打开购物车', d: '购物车出现两行，规格文字可辨', go: '#/c/cart' },
      { n: 4, t: '结算 → 用一张券 → 提交 → 演示支付成功', d: '订单显示「待发货」，券后金额正确，演示标注可见', go: '#/c/checkout' },
      { n: 5, t: '后台订单列表（待发货栏）', d: '该单在待发货，金额与 C 端一致', go: '#/admin/orders?tab=wait_ship' },
      { n: 6, t: '发货，填物流公司与单号', d: 'C 端订单详情显示「已发货」与单号', go: '#/admin/orders?tab=wait_ship' },
      { n: 7, t: 'C 端确认收货', d: '转「已收货」，出现「申请售后」入口', go: shipped ? '#/c/order/' + shipped.id : '#/c/orders?tab=shipped' },
      { n: 8, t: '对「黑色/L」这一行申请「退货退款」，上传 1 张凭证', d: '生成售后单，C 端进度「申请中」', go: recv ? '#/c/aftersale/apply?orderId=' + recv.id + '&itemId=' + recv.items[0].id : '#/c/orders' },
      { n: 9, t: '后台同意并给地址 → 用户填单号 → 运营确认收货 → 退款完成', d: '售后单「退款完成」；订单行「已退款」；订单主状态仍是「已收货」', go: '#/admin/aftersales?tab=apply' },
      { n: 10, t: '后台库存视图核对两行规格', d: '退货行实际与可售 +1；未退行不变', go: '#/admin/stock' },
      { n: 11, t: '另开一单只下单不付款 → 后台演示工具「立即关单」', d: '订单转「已关闭」；锁量释放、券回未使用；日志记录', go: '#/admin/demo' },
      { n: 12, t: '同形态未付款单在 C 端手动取消', d: '结果与第 11 步一致（库存与券都释放）', go: '#/c/orders?tab=unpaid' },
      { n: 13, t: '演示支付失败分支', d: '点「模拟支付失败」→ 订单仍「未付款」，可重试', go: unpaid ? '#/c/pay/' + unpaid.id : '#/c/orders?tab=unpaid' }
    ];
  }

  function demoScreen() {
    var goods = st().goods;
    var counts = {
      goods: goods.length,
      skus: sum(goods, function (g) { return g.skus.length; }),
      orders: st().orders.length,
      as: st().aftersales.length,
      reviews: st().reviews.length
    };
    var stats = S().stats();
    var logs = S().demoLog().slice(0, 30);
    var unpaid = st().orders.filter(function (o) { return o.status === 'unpaid'; });
    var steps = r1Steps();

    var logRows = logs.length ? logs.map(function (l) {
      return '<div class="row top" style="gap:10px;padding:8px 0;border-bottom:1px solid var(--line)">' +
        '<div class="num tiny dim" style="width:112px;flex:none">' + time(l.at) + '</div>' +
        '<div class="grow small">' + esc(l.text) + '<span class="tiny dim"> · ' + fromNow(l.at) + '</span></div>' +
        '<div class="tiny dim" style="flex:none">' + esc(l.by || '—') + '</div>' +
      '</div>';
    }).join('') : empty({ icon: '📝', title: '还没有操作日志', hint: '发货、改单号、售后处理、库存调整、立即关单都会留痕（BR-ADM-03）' });

    var stepRows = steps.map(function (s) {
      return '<a class="cell clickable" href="' + esc(s.go) + '" data-go="' + esc(s.go) + '" style="text-decoration:none;color:inherit;align-items:flex-start">' +
        '<span class="tag info" style="flex:none;min-width:26px;justify-content:center">' + s.n + '</span>' +
        '<span class="grow"><span class="small bold">' + esc(s.t) + '</span>' +
          '<span class="tiny dim" style="display:block;margin-top:3px">判定：' + esc(s.d) + '</span>' +
          '<span class="tiny" style="display:block;color:var(--text-3);margin-top:2px">' + esc(s.go) + '</span></span>' +
        '<span class="tiny dim" style="flex:none">打开 →</span>' +
      '</a>';
    }).join('');

    var html =
      '<div class="kpis">' +
        kpi('商品', counts.goods, '含已下架') +
        kpi('SKU', counts.skus, '库存与售卖的最小单位') +
        kpi('订单', counts.orders, '未付款 ' + stats.unpaid + ' · 待发货 ' + stats.waitShip) +
        kpi('售后单', counts.as, '处理中 ' + stats.pendingAs) +
        kpi('评价', counts.reviews, '来自已收货订单行') +
      '</div>' +
      '<div style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.1fr);gap:14px;margin-top:14px">' +
        panel('演示工具',
          U.alert('演示模式只允许两种手段：① 模拟支付成功/失败；② 演示工具「立即关单」按超时逻辑关闭未付款订单，必须留日志（7.1）。除此之外不为演示改数据、跳状态或直接改库存。', 'warn') +
          '<div class="mt12" style="display:flex;flex-direction:column;gap:10px">' +
            '<button type="button" class="btn primary" data-aact="demo-close-unpaid">一键关闭全部超时未付款订单（' + unpaid.length + ' 笔）</button>' +
            '<button type="button" class="btn danger" data-aact="demo-reset">重置演示数据（恢复种子数据）</button>' +
          '</div>' +
          '<div class="divider"></div>' +
          '<div class="small" style="line-height:1.9;color:var(--text-2)">' +
            '<div>· 关单与 C 端手动取消共用同一套释放逻辑：锁量释放、券退回未使用（BR-ORDER-07/13）。</div>' +
            '<div>· 重置会清空本地演示数据并重建种子商品/订单/售后/评价，用于反复演示。</div>' +
            '<div>· 数据保存在浏览器 localStorage（file:// 下不可用时降级为内存）。</div>' +
          '</div>') +
        panel('最近操作日志（最多 30 条）', logRows, '<span class="tiny dim">共 ' + S().demoLog().length + ' 条</span>') +
      '</div>' +
      panel('R1 验收 13 步（可点击跳转）',
        '<div style="margin:-4px 0">' + stepRows + '</div>',
        '<span class="tiny dim">C 端路由由 SPA 外壳提供；示例单据 id 自动取当前数据</span>');
    return { title: '演示工具', html: html };
  }

  /* ------------------------------ 事件处理 ------------------------------ */
  var ACT = {
    login: function () {
      var u = String(val('lg-user') || '').trim();
      if (!u) { toast('请输入账号（演示环境可用 admin）', 'err'); return; }
      toast('演示模式：任意密码均可登录', 'ok');
      go('#/admin/dashboard');
    },
    'ord-tab': function (id, el) { F.orders.tab = el.getAttribute('data-key'); refresh(); },
    'ord-search': function () { F.orders.kw = val('f-kw').trim(); refresh(); },
    'ord-reset': function () { F.orders.kw = ''; refresh(); },
    'ord-detail': function (id) { orderDetailSheet(id); },
    'ord-ship': function (id) { shipSheet(id); },
    'ord-track': function (id) { trackSheet(id); },
    'ord-close': function (id) { closeOneOrder(id); },

    'as-tab': function (id, el) { F.as.tab = el.getAttribute('data-key'); refresh(); },
    'as-search': function () { F.as.kw = val('f-kw').trim(); refresh(); },
    'as-reset': function () { F.as.kw = ''; refresh(); },
    'as-detail': function (id) { asDetailSheet(id); },
    'as-approve': function (id) { approveAs(id); },
    'as-reject': function (id) { rejectAs(id); },
    'as-reason': function (id) { var ta = document.getElementById('rj-reason'); if (ta) { ta.value = id; ta.focus(); } },
    'as-skip': function (id) { skipAs(id); },
    'as-receive': function (id) {
      // P-05：与同意/驳回/跳过/退款保持一致，收到退货这种不可逆动作先二次确认
      var a = S().asById(id);
      if (!a) { toast('售后单不存在', 'err'); return; }
      U.confirm({
        title: '确认收到退货',
        body: '确认已收到售后单 ' + esc(a.sn) + ' 的退货（' + esc(a.name) + ' × ' + a.qty + '）？<br>' +
          '确认后售后单进入退款处理，库存按「是否回补」矩阵回补（BR-AS-13/14）。',
        okText: '确认收到'
      }).then(function (ok) {
        if (!ok) return;
        var r = S().asConfirmReceive(id);
        if (!r.ok) { toast(r.msg, 'err'); return; }
        toast(r.msg, 'ok');
        refresh();
      });
    },
    'as-refund': function (id) { doRefund(id); },
    'as-autoclose': function (id) { autoCloseAs(id); },

    /* ---- 商品 ---- */
    'goods-tab': function (id, el) { F.goods.tab = el.getAttribute('data-key'); refresh(); },
    'goods-new': function () { sheetGoods(); },
    'goods-edit': function (id) { sheetGoods(id); },
    'goods-toggle': function (id) {
      var g = S().goodsOf(id);
      if (!g) { toast('商品不存在', 'err'); return; }
      var r = S().goodsToggle(id);
      if (!r.ok) { toast(r.msg, 'err'); return; }
      S().log((g.onSale ? '上架' : '下架') + '商品「' + g.name + '」');
      S().save();
      toast(r.msg, 'ok');
      refresh();
    },

    /* ---- 分类 / 品牌 ---- */
    'cat-add': function (id) { catSheet('', id || ''); },
    'cat-edit': function (id) { catSheet(id); },
    'cat-del': function (id) {
      var c = st().cats.filter(function (x) { return x.id === id; })[0];
      if (!c) return;
      if (st().cats.some(function (x) { return x.parentId === id; })) { toast('该分类下还有子分类，请先删除子分类', 'err'); return; }
      var used = st().goods.filter(function (g) { return g.catId === id; }).length;
      if (used) { toast('该分类下还有 ' + used + ' 个商品，请先移走商品', 'err'); return; }
      U.confirm({
        title: '删除分类',
        body: '确认删除分类「' + esc(c.name) + '」？删除后不可恢复。',
        danger: true,
        okText: '删除'
      }).then(function (ok) {
        if (!ok) return;
        var i = st().cats.findIndex(function (x) { return x.id === id; });
        if (i >= 0) st().cats.splice(i, 1);
        S().save();
        toast('分类已删除', 'ok');
        refresh();
      });
    },
    'brand-add': function () { brandSheet(); },
    'brand-edit': function (id) { brandSheet(id); },
    'brand-del': function (id) {
      var b = st().brands.filter(function (x) { return x.id === id; })[0];
      if (!b) return;
      var used = st().goods.filter(function (g) { return g.brandId === id; }).length;
      if (used) { toast('该品牌下还有 ' + used + ' 个商品，请先调整商品品牌', 'err'); return; }
      U.confirm({
        title: '删除品牌',
        body: '确认删除品牌「' + esc(b.name) + '」？删除后不可恢复。',
        danger: true,
        okText: '删除'
      }).then(function (ok) {
        if (!ok) return;
        var i = st().brands.findIndex(function (x) { return x.id === id; });
        if (i >= 0) st().brands.splice(i, 1);
        S().save();
        toast('品牌已删除', 'ok');
        refresh();
      });
    },

    /* ---- 库存 ---- */
    'stock-tab': function (id, el) { F.stock.tab = el.getAttribute('data-key'); refresh(); },
    'stock-search': function () { F.stock.kw = val('f-kw').trim(); refresh(); },
    'stock-reset': function () { F.stock.kw = ''; refresh(); },

    /* ---- 优惠券 ---- */
    'cp-new': function () { couponSheet(); },
    'cp-edit': function (id) { couponSheet(id); },
    'cp-toggle': function (id) {
      var c = st().coupons.filter(function (x) { return x.id === id; })[0];
      if (!c) return;
      c.disabled = !c.disabled;
      S().log((c.disabled ? '停用' : '启用') + '优惠券「' + c.name + '」');
      S().save();
      toast(c.disabled ? '优惠券已停用，C 端不可再领' : '优惠券已启用', 'ok');
      refresh();
    },
    'cp-del': function (id) {
      var c = st().coupons.filter(function (x) { return x.id === id; })[0];
      if (!c) return;
      if (c.claimed > 0) { toast('已有 ' + c.claimed + ' 条领取记录，只能停用不能删除（BR-CP-10）', 'err'); return; }
      U.confirm({
        title: '删除优惠券',
        body: '确认删除「' + esc(c.name) + '」？该券尚无领取记录，可以删除。',
        danger: true,
        okText: '删除'
      }).then(function (ok) {
        if (!ok) return;
        var i = st().coupons.findIndex(function (x) { return x.id === id; });
        if (i >= 0) st().coupons.splice(i, 1);
        S().log('删除优惠券「' + c.name + '」');
        S().save();
        toast('优惠券已删除', 'ok');
        refresh();
      });
    },

    /* ---- 评价 ---- */
    'cm-tab': function (id, el) { F.comments.tab = el.getAttribute('data-key'); refresh(); },
    'cm-reply': function (id) { replySheet(id); },

    /* ---- 演示工具 ---- */
    'demo-close-unpaid': function () {
      var unpaid = st().orders.filter(function (o) { return o.status === 'unpaid'; });
      if (!unpaid.length) { toast('当前没有未付款订单', 'err'); return; }
      U.confirm({
        title: '关闭全部超时未付款订单',
        body: '将把 ' + unpaid.length + ' 笔未付款订单按「超时关闭」处理：释放锁量、优惠券退回未使用、写演示日志（7.1 / BR-ORD-13）。',
        okText: '立即关单'
      }).then(function (ok) {
        if (!ok) return;
        var n = 0, qty = 0;
        unpaid.forEach(function (o) {
          var r = S().demoCloseUnpaid(o.id);
          if (r.ok) { n++; qty += sum(o.items, function (it) { return it.qty; }); }
        });
        toast('已关单 ' + n + ' 笔，释放库存 ' + qty + ' 件', 'ok');
        refresh();
      });
    },
    'demo-reset': function () {
      U.confirm({
        title: '重置演示数据',
        body: '将清空当前演示数据并恢复种子商品 / 订单 / 售后 / 评价，用于重新走一遍演示脚本。此操作不可撤销。',
        danger: true,
        okText: '重置'
      }).then(function (ok) {
        if (!ok) return;
        S().resetAll();
        F.orders = { tab: 'all', kw: '' };
        F.as = { tab: 'all', kw: '' };
        F.goods = { tab: 'all' };
        F.stock = { tab: 'all', kw: '' };
        F.comments = { tab: 'all' };
        LAST_HASH = null;
        toast('演示数据已重置', 'ok');
        refresh();
      });
    }
  };

  /* 输入框直接改库存：失焦/回车即保存（BR-ADM-03 库存调整需留日志） */
  var CHG = {
    'stock-num': function (skuId, el) {
      var n = intOf(el.value, null);
      if (n === null || n < 0) {
        toast('实物库存必须是大于等于 0 的整数', 'err');
        el.value = el.__old == null ? el.value : el.__old;
        return;
      }
      var f = S().skuOf(skuId);
      var before = f ? f.sku.number : 0;
      var r = S().stockUpdate(skuId, n);
      if (!r.ok) {
        toast(r.msg, 'err');
        el.value = before;
        return;
      }
      if (f) S().log('库存调整：' + f.goods.name + ' / ' + (S().specText ? S().specText(f.sku) : '') + ' 实物 ' + before + ' → ' + n);
      S().save();
      toast('库存已更新为 ' + n + ' 件', 'ok');
      refresh();
    }
  };

  /* ------------------------------ 路由注册 ------------------------------ */
  function wrap(screen) {
    return function (ctx) {
      LAST_CTX = ctx || null;
      return screen(ctx || { params: {}, query: {} });
    };
  }
  ROUTES = [
    { path: 'login', render: wrap(loginScreen) },
    { path: 'dashboard', render: wrap(dashboardScreen) },
    { path: 'orders', render: wrap(ordersScreen) },
    { path: 'aftersales', render: wrap(asScreen) },
    { path: 'goods', render: wrap(goodsScreen) },
    { path: 'catalog', render: wrap(catalogScreen) },
    { path: 'stock', render: wrap(stockScreen) },
    { path: 'coupons', render: wrap(couponsScreen) },
    { path: 'comments', render: wrap(commentsScreen) },
    { path: 'demo', render: wrap(demoScreen) }
  ];
  window.ADMIN_ROUTES = ROUTES;

  document.addEventListener('focusin', function (e) {
    var el = e.target;
    if (el && el.getAttribute && el.getAttribute('data-achange')) el.__old = el.value;
  });

  document.addEventListener('change', function (e) {
    if (!document.querySelector('.admin-body')) return;
    var el = e.target;
    if (!el || !el.getAttribute) return;
    var act = el.getAttribute('data-achange');
    if (!act || !CHG[act]) return;
    CHG[act](el.getAttribute('data-id') || '', el);
  });

  document.addEventListener('click', function (e) {
    if (!document.querySelector('.admin-body')) return;
    var el = e.target && e.target.closest ? e.target.closest('[data-aact]') : null;
    if (!el) return;
    var act = el.getAttribute('data-aact');
    if (!ACT[act]) return;
    e.preventDefault();
    ACT[act](el.getAttribute('data-id') || '', el);
  });

  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter') return;
    var el = e.target && e.target.closest ? e.target.closest('[data-aenter]') : null;
    if (!el) return;
    var act = el.getAttribute('data-aenter');
    if (ACT[act]) { e.preventDefault(); ACT[act](); }
  });
})();