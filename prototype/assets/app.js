/* ============================================================================
 * 小满商城 · 交互原型 SPA 外壳 + 手机端 17 屏
 * 纯静态、零依赖；所有业务规则来自 assets/store.js（window.Store），
 * 所有片段工厂与格式化来自 assets/ui.js（window.UI）。
 *
 * 路由：#/c/<path> 与 #/admin/<path>，支持 :id 段与 ?query
 * 屏函数：{ path, render(ctx) } → { html, action?, tab?, title?, mount?, actions? }
 * 契约细节见 assets/README.md
 * ==========================================================================*/
(function () {
  'use strict';

  var Store = window.Store;
  var UI = window.UI;

  var HIS_KEY = 'proto-search-history';
  var MAX_HIS = 8;
  var PAY_WINDOW = 30 * 60000;   // BR-ORDER-06：未付款 30 分钟关单

  /* ---------------- 运行期状态 ---------------- */
  var backStack = [];        // 简易历史栈，供 data-go="back"
  var buyNow = null;         // 「立即购买」草稿行（不经购物车）
  var timers = [];
  var cleanups = [];
  var currentActions = null;
  var payFailMsg = null;     // 支付失败提示（pay 屏展示一次）
  var selCache = {};         // 商品详情规格选择缓存 { goodsId: {颜色:'黑色'} }
  var qtyCache = {};         // 数量缓存 { skuId: n }
  var detailTab = 'detail';  // 详情页 Tab
  var asDraft = null;        // 售后申请表单草稿
  var reviewRate = {};       // 评价星级草稿 { itemId: 1..5 }
  var reviewText = {};       // 评价文字草稿 { itemId: string }

  /* ============================================================================
   * 一、通用小工具
   * ==========================================================================*/
  function esc(s) { return UI.esc(s); }
  function price(c, cls) { return UI.price(c || 0, cls); }
  function has(o, k) { return Object.prototype.hasOwnProperty.call(o, k); }
  function mix(a, b) {
    var o = {};
    var k;
    for (k in a) if (has(a, k)) o[k] = a[k];
    for (k in b) if (has(b, k)) o[k] = b[k];
    return o;
  }
  function each(list, fn) { (list || []).forEach(fn); }
  function sum(list, fn) {
    return (list || []).reduce(function (a, x) { return a + fn(x); }, 0);
  }
  function errMsg(e) { return (e && e.message) ? e.message : String(e); }

  function every(ms, fn) {
    var id = setInterval(fn, ms);
    timers.push(id);
    return id;
  }
  function onCleanup(fn) { if (typeof fn === 'function') cleanups.push(fn); }
  function clearTimers() {
    timers.forEach(function (id) { clearInterval(id); });
    timers = [];
    cleanups.forEach(function (fn) { try { fn(); } catch (e) { /* 清理失败不影响渲染 */ } });
    cleanups = [];
  }

  /* ---------------- 金额 / 数量 ---------------- */
  function totalQty(items) { return sum(items, function (it) { return it.qty || 0; }); }

  /* ---------------- 搜索历史（localStorage，BR-SEARCH-02） ---------------- */
  function hisGet() {
    try {
      var raw = localStorage.getItem(HIS_KEY);
      var arr = raw ? JSON.parse(raw) : [];
      return Object.prototype.toString.call(arr) === '[object Array]' ? arr : [];
    } catch (e) { return []; }
  }
  function hisAdd(kw) {
    kw = String(kw == null ? '' : kw).trim();
    if (!kw) return;
    var arr = hisGet().filter(function (x) { return x !== kw; });
    arr.unshift(kw);
    try { localStorage.setItem(HIS_KEY, JSON.stringify(arr.slice(0, MAX_HIS))); } catch (e) { /* 隐私模式下降级 */ }
  }
  function hisClear() {
    try { localStorage.removeItem(HIS_KEY); } catch (e) { /* ignore */ }
  }

  function toast(msg, type) { UI.toast(msg, type || ''); }
  function okToast(r) {
    if (r && r.ok) { toast(r.msg || '操作成功', 'ok'); return true; }
    toast((r && r.msg) || '操作失败', 'err');
    return false;
  }

  /* ============================================================================
   * 二、路由
   * ==========================================================================*/
  function parseHash(raw) {
    var h = String(raw || '').replace(/^#/, '');
    if (!h) h = '/c/home';
    var qi = h.indexOf('?');
    var pathPart = qi < 0 ? h : h.slice(0, qi);
    var query = qi < 0 ? {} : UI.qs(h);
    var segs = pathPart.split('/').filter(Boolean);
    var scope = segs[0] === 'admin' ? 'admin' : 'c';
    if (segs[0] !== 'c' && segs[0] !== 'admin') segs = ['c'].concat(segs);
    var rest = segs.slice(1);
    if (!rest.length) rest = [scope === 'admin' ? 'dashboard' : 'home'];
    return {
      scope: scope,
      segs: rest,
      path: rest.join('/'),
      query: query,
      raw: raw || ''
    };
  }

  // 段匹配：静态段必须相等，:id 段收集参数；同一路径多条命中时静态段多者优先
  function matchRoute(list, segs) {
    var best = null;
    each(list, function (r) {
      if (!r || !r.path) return;
      var ps = String(r.path).split('/').filter(Boolean);
      if (ps.length !== segs.length) return;
      var params = {};
      for (var i = 0; i < ps.length; i++) {
        if (ps[i].charAt(0) === ':') {
          params[ps[i].slice(1)] = decodeURIComponent(segs[i] || '');
        } else if (ps[i] !== segs[i]) {
          return;
        }
      }
      var statics = ps.filter(function (x) { return x.charAt(0) !== ':'; }).length;
      if (!best || statics > best.statics) best = { route: r, params: params, statics: statics };
    });
    return best;
  }

  function normalizeHash(hash) {
    var t = String(hash == null ? '' : hash);
    if (!t) return '';
    if (t.charAt(0) !== '#') {
      if (t.charAt(0) === '/') t = '#' + t;
      else t = '#/c/' + t;
    }
    return t;
  }

  function go(hash, noPush) {
    var target = normalizeHash(hash);
    if (!target) return;
    if (target === location.hash) { render(); return; }
    if (!noPush) backStack.push(location.hash || '#/c/home');
    location.hash = target;
  }

  function back() {
    var prev = backStack.pop();
    while (prev && prev === location.hash) prev = backStack.pop();
    if (!prev) { go('#/c/home', true); return; }
    if (location.hash === prev) { render(); return; }
    location.hash = prev;
  }

  /* ============================================================================
   * 三、演示顶栏 / 手机壳 / 后台外壳
   * ==========================================================================*/
  function demoBar(scope) {
    return '<div class="demo-bar">' +
      '<div class="brand"><span class="dot">满</span><span>小满商城 · 交互原型</span></div>' +
      '<span class="hint">单页 · 纯静态 · 数据存本机浏览器</span>' +
      '<div class="spacer"></div>' +
      '<div class="seg">' +
        '<button class="' + (scope === 'c' ? 'on' : '') + '" data-go="#/c/home">手机端</button>' +
        '<button class="' + (scope === 'admin' ? 'on' : '') + '" data-go="#/admin/dashboard">后台</button>' +
      '</div>' +
      '<button class="demo-btn" data-act="resetDemo">重置演示数据</button>' +
      '<button class="demo-btn" data-go="#/admin/demo">演示工具</button>' +
    '</div>';
  }

  var C_TABS = [
    { key: 'home', label: '首页', icon: 'home', href: '#/c/home' },
    { key: 'category', label: '分类', icon: 'grid', href: '#/c/category' },
    { key: 'cart', label: '购物车', icon: 'cart', href: '#/c/cart', badge: true },
    { key: 'me', label: '我的', icon: 'user', href: '#/c/me' }
  ];

  function cTabBar(active) {
    var n = Store.cartCount();
    return '<div class="c-tab">' + C_TABS.map(function (t) {
      var badge = (t.badge && n) ? '<span class="badge-dot">' + (n > 99 ? '99+' : n) + '</span>' : '';
      return '<a class="' + (t.key === active ? 'on' : '') + '" data-go="' + t.href + '">' +
        UI.icon(t.icon, 21) + '<span>' + esc(t.label) + '</span>' + badge + '</a>';
    }).join('') + '</div>';
  }

  var ADMIN_NAV = [
    { key: 'dashboard', label: '工作台', icon: 'chart' },
    { key: 'orders', label: '订单管理', icon: 'list' },
    { key: 'aftersales', label: '售后处理', icon: 'refresh', badge: true },
    { key: 'goods', label: '商品与规格', icon: 'box' },
    { key: 'catalog', label: '分类与品牌', icon: 'tag' },
    { key: 'stock', label: '库存视图', icon: 'grid' },
    { key: 'coupons', label: '优惠券', icon: 'money' },
    { key: 'comments', label: '评价管理', icon: 'comment' },
    { key: 'demo', label: '演示工具', icon: 'setting' }
  ];

  function adminLabel(key) {
    var hit = null;
    ADMIN_NAV.forEach(function (n) { if (n.key === key) hit = n; });
    return hit ? hit.label : '后台';
  }

  function adminShell(route, title, body) {
    var st = Store.stats();
    var cur = route.segs[0];
    var links = ADMIN_NAV.map(function (n) {
      var badge = (n.badge && st.pendingAs) ? '<span class="n">' + st.pendingAs + '</span>' : '';
      return '<a class="' + (cur === n.key ? 'on' : '') + '" data-go="#/admin/' + n.key + '">' +
        UI.icon(n.icon, 17) + '<span>' + esc(n.label) + '</span>' + badge + '</a>';
    }).join('');
    return '<div class="admin">' +
      '<aside class="aside">' +
        '<div class="logo"><span class="dot">满</span><span>小满商城后台</span></div>' +
        '<div class="grp">运营 / 客服（单商户 · 演示身份）</div>' +
        links +
      '</aside>' +
      '<div class="admin-main">' +
        '<div class="admin-head">' +
          '<h1>' + esc(title) + '</h1>' +
          '<span class="demo-flag">我是运营 / 客服（演示身份）</span>' +
          '<div style="flex:1"></div>' +
          '<button class="btn sm" data-go="#/c/home">返回手机端</button>' +
        '</div>' +
        '<div class="admin-body" id="admin-body">' + body + '</div>' +
      '</div>' +
    '</div>';
  }

  function adminLoginShell(body) {
    return '<div class="admin">' +
      '<div class="admin-main">' +
        '<div class="admin-body" id="admin-body" style="display:flex;align-items:center;justify-content:center">' + body + '</div>' +
      '</div>' +
    '</div>';
  }

  function adminMissing(route) {
    var list = window.ADMIN_ROUTES || [];
    var names = list.map(function (r) { return r && r.path; }).filter(Boolean);
    return '<div class="panel">' +
      '<div class="panel-hd"><h2>该后台屏尚未注册</h2></div>' +
      '<div class="alert warn">' + UI.icon('warn', 15) + '<div>路由 <b>#/admin/' + esc(route.path) + '</b> 没有在 <code>assets/admin-screens.js</code> 中注册。' +
        (names.length ? '' : ' 当前该文件未加载或未注册任何路由。') + '</div></div>' +
      '<div class="mt12 small muted">契约：<code>window.ADMIN_ROUTES = [{ path: \'dashboard\', render(ctx) { return { html, title, mount } } }]</code>（由后台端实现）。</div>' +
      '<div class="mt8 small muted">已注册路由：' + (names.length ? esc(names.join('、')) : '（无）') + '</div>' +
      '<div class="mt12"><button class="btn" data-go="#/c/home">返回手机端</button></div>' +
    '</div>';
  }

  function errPanel(e) {
    return '<div class="panel">' +
      '<div class="panel-hd"><h2>这一屏渲染出错</h2></div>' +
      '<div class="alert err">' + UI.icon('warn', 15) + '<div>' + esc(errMsg(e)) + '</div></div>' +
      '<div class="mt12 small muted">请对照 assets/README.md 的契约检查该屏的 render 返回值。</div>' +
    '</div>';
  }

  /* ============================================================================
   * 四、渲染
   * ==========================================================================*/
  function ctxOf(route, params, scope) {
    return {
      scope: scope,
      path: route.path,
      segs: route.segs,
      params: params || {},
      query: route.query || {},
      hash: location.hash,
      go: go,
      refresh: render,
      back: back,
      every: every,
      onCleanup: onCleanup
    };
  }

  function mountScreen(out, rootEl, hostEl, ctx) {
    currentActions = out.actions || null;
    if (typeof out.mount !== 'function') return;
    try {
      var ret = out.mount(rootEl, ctx);
      if (typeof ret === 'function') cleanups.push(ret);
    } catch (e) {
      toast('页面挂载出错：' + errMsg(e), 'err');
    }
  }

  function renderC(route, root) {
    var m = matchRoute(window.C_ROUTES || [], route.segs);
    if (!m) {
      root.innerHTML = demoBar('c') +
        '<div class="stage-c"><div class="phone"><div class="phone-scroll">' +
          '<div class="c-page">' + UI.cHeader({ title: '页面不存在' }) +
            UI.empty({
              icon: '🧭', title: '没有这个页面', hint: '路由 #/c/' + esc(route.path) + ' 未注册',
              action: '<button class="btn primary" data-go="#/c/home">回首页</button>'
            }) +
          '</div>' +
        '</div>' + cTabBar('home') + '</div></div>';
      currentActions = null;
      return;
    }

    var ctx = ctxOf(route, m.params, 'c');
    var out;
    try {
      out = m.route.render(ctx) || {};
    } catch (e) {
      out = {
        html: '<div class="c-page">' + UI.cHeader({ title: '页面出错' }) +
          '<div class="c-body">' + UI.alert('<b>这一屏渲染出错</b><br>' + esc(errMsg(e)), 'err') + '</div></div>',
        tab: undefined
      };
    }

    var tail = (out.action ? '<div class="c-actionbar">' + out.action + '</div>' : '') +
      (out.tab ? cTabBar(out.tab) : '');

    root.innerHTML = demoBar('c') +
      '<div class="stage-c"><div class="phone">' +
        '<div class="phone-scroll" id="c-scroll">' + (out.html || '') + '</div>' + tail +
      '</div></div>';

    var shell = root.querySelector('.phone');
    mountScreen(out, shell, shell && shell.querySelector('.phone-scroll'), ctx);
    if (out.title) document.title = out.title + ' · 小满商城原型';
  }

  function renderAdmin(route, root) {
    var list = window.ADMIN_ROUTES || [];
    var m = matchRoute(list, route.segs);
    var isLogin = route.segs[0] === 'login';
    var ctx = ctxOf(route, m ? m.params : {}, 'admin');
    var out = {};

    if (m) {
      try {
        out = m.route.render(ctx) || {};
      } catch (e) {
        out = { html: errPanel(e), title: '页面出错' };
      }
    } else {
      out = { html: adminMissing(route), title: adminLabel(route.segs[0]) };
    }

    var body = out.html || '';
    var title = out.title || adminLabel(route.segs[0]) || '后台';
    root.innerHTML = demoBar('admin') +
      (isLogin ? adminLoginShell(body) : adminShell(route, title, body));

    var host = root.querySelector('#admin-body');
    mountScreen(out, host, host, ctx);
    document.title = title + ' · 小满商城后台原型';
  }

  function render() {
    clearTimers();
    var root = document.getElementById('root');
    if (!root) return;
    var route = parseHash(location.hash);
    if (route.scope === 'admin') renderAdmin(route, root);
    else renderC(route, root);
    var sc = document.getElementById('c-scroll');
    if (sc) sc.scrollTop = 0;
  }

  /* ============================================================================
   * 五、事件委托（data-go / data-act / data-step）
   * ==========================================================================*/
  function hitTarget(node) {
    var el = node;
    while (el && el !== document.body) {
      if (el.hasAttribute && (el.hasAttribute('data-act') || el.hasAttribute('data-step') || el.hasAttribute('data-go'))) return el;
      el = el.parentElement;
    }
    return null;
  }

  function askReset() {
    UI.confirm({
      title: '重置演示数据',
      body: '将清空本机浏览器里保存的全部演示数据（购物车、订单、售后单、优惠券、地址、评价），并恢复初始种子数据。<br>此操作不可撤销。',
      okText: '确认重置',
      danger: true
    }).then(function (yes) {
      if (!yes) return;
      Store.resetAll();
      buyNow = null;
      payFailMsg = null;
      selCache = {};
      qtyCache = {};
      asDraft = null;
      reviewRate = {};
      reviewText = {};
      backStack = [];
      toast('演示数据已重置', 'ok');
      go('#/c/home', true);
    });
  }

  document.addEventListener('click', function (e) {
    var el = hitTarget(e.target);
    if (!el) return;

    if (el.hasAttribute('data-act')) {
      var name = el.getAttribute('data-act');
      if (name === 'resetDemo') { askReset(); return; }
      var fn = currentActions && currentActions[name];
      if (typeof fn === 'function') fn(el, e);
      return;
    }
    if (el.hasAttribute('data-step')) {
      var step = currentActions && currentActions.step;
      if (typeof step === 'function') step(el, e);
      return;
    }
    if (el.hasAttribute('data-go')) {
      var v = el.getAttribute('data-go');
      if (v === 'back') back();
      else if (v) go(v);
    }
  });

  window.addEventListener('hashchange', function () { render(); });

  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' && e.keyCode !== 13) return;
    var el = e.target;
    if (!el || !el.hasAttribute || !el.hasAttribute('data-enter')) return;
    var name = el.getAttribute('data-enter');
    var fn = currentActions && currentActions[name];
    if (typeof fn === 'function') { e.preventDefault(); fn(el, e); }
  });

  /* ============================================================================
   * 六、对外 API（proto-admin 与本文件的屏共用）
   * ==========================================================================*/
  window.App = {
    go: go,
    back: back,
    refresh: render,
    resetDemo: askReset,
    parseHash: parseHash,
    matchRoute: matchRoute,
    getBuyNow: function () { return buyNow; },
    setBuyNow: function (line) { buyNow = line; },
    clearBuyNow: function () { buyNow = null; },
    toast: toast
  };/* ============================================================================
   * 七、手机端共用片段
   * ==========================================================================*/
  function catName(id) {
    var c = Store.state.cats.filter(function (x) { return x.id === id; })[0];
    return c ? c.name : '';
  }
  function brandName(id) {
    var b = Store.state.brands.filter(function (x) { return x.id === id; })[0];
    return b ? b.name : '';
  }
  function inCartQty(goodsId, skuId) {
    var line = Store.state.cart.filter(function (l) { return l.goodsId === goodsId && l.skuId === skuId; })[0];
    return line ? line.qty : 0;
  }

  function goodsCard(g) {
    var soldOut = Store.totalStock(g) <= 0;
    var tag = '';
    if (soldOut) tag = '<span class="corner">暂时缺货</span>';
    else if (g.tags && g.tags.length) tag = '<span class="corner">' + esc(g.tags[0]) + '</span>';
    return '<div class="gcard' + (soldOut ? ' soldout' : '') + '" data-go="#/c/goods/' + esc(g.id) + '">' +
      '<div class="pic" style="background:linear-gradient(135deg,' + esc(g.pics[0]) + ',#ffffff66)">' +
        esc(g.pics[1]) + tag +
      '</div>' +
      '<div class="info">' +
        '<div class="nm">' + esc(g.name) + '</div>' +
        '<div class="meta">' + price(Store.displayPrice(g), 'price-md') +
          '<span class="sales">已售 ' + g.sales + '</span></div>' +
      '</div>' +
    '</div>';
  }

  /* ===== 界面改版（v3.1）：分类横滚条 / 消息入口 / 工具区 / 淘宝式筛选 ===== */
  var CAT_EMOJI = { c1: '👕', c2: '🏠', c3: '🎧', c4: '☕', c5: '🏃' };
  function catEmoji(id) { return CAT_EMOJI[id] || '🛍️'; }
  function topCats() {
    return Store.state.cats.filter(function (c) { return !c.parentId; })
      .slice().sort(function (a, b) { return (a.sort || 0) - (b.sort || 0); });
  }
  /* 首页分类横滚条：单行横滑、隐藏滚动条，末项固定「全部分类」；不参与页面横向溢出 */
  function catStrip() {
    var items = topCats().map(function (c) {
      return '<div class="x-catpill" data-go="#/c/list?catId=' + esc(c.id) + '">' +
        '<span class="em">' + catEmoji(c.id) + '</span>' + esc(c.name) + '</div>';
    }).join('');
    return '<div class="x-catstrip"><div class="x-catstrip-in">' + items +
      '<div class="x-catpill more" data-go="#/c/category">全部分类 ›</div></div></div>';
  }
  /* 消息入口卡（替换原 5 宫格位置） */
  function msgEntry() {
    var last = Store.messages()[0];
    var un = Store.unreadCount();
    return '<div class="x-msgcard" data-go="#/c/messages">' +
      '<div class="ic">' + UI.icon('bell', 20) +
        (un ? '<i class="dot">' + (un > 99 ? '99+' : un) + '</i>' : '') + '</div>' +
      '<div class="grow" style="min-width:0">' +
        '<div class="row between"><span class="small bold">消息</span>' +
          '<span class="tiny dim">' + (last ? UI.fromNow(last.at) : '') + '</span></div>' +
        '<div class="tiny dim ellipsis mt6">' +
          (last ? esc(last.title) + ' · ' + esc(last.text) : '暂无消息') + '</div>' +
      '</div>' +
      '<div class="tiny dim" style="flex:none">查看全部 ›</div>' +
    '</div>';
  }

  function itemRow(it, opt) {
    opt = opt || {};
    var refunded = (it.refundedQty || 0) > 0
      ? '<span class="tag gray" style="margin-left:4px">已退款 ' + it.refundedQty + ' 件</span>' : '';
    var reviewed = it.reviewed ? '<span class="tag ok" style="margin-left:4px">已评价</span>' : '';
    return '<div class="row top" style="gap:10px;padding:8px 0">' +
      UI.thumb(it.bg, it.emoji, opt.sm === false ? '' : 'sm') +
      '<div class="grow">' +
        '<div class="small bold clamp2">' + esc(it.name) + '</div>' +
        '<div class="tiny dim mt6">' + esc(it.specText || '') + refunded + reviewed + '</div>' +
      '</div>' +
      '<div style="text-align:right;flex:none">' +
        price(it.price) + '<div class="tiny dim">×' + it.qty + '</div>' +
      '</div>' +
    '</div>';
  }

  function listUrl(o) {
    var p = [];
    if (o.kw) p.push('kw=' + encodeURIComponent(o.kw));
    if (o.catId) p.push('catId=' + encodeURIComponent(o.catId));
    if (o.brandId) p.push('brandId=' + encodeURIComponent(o.brandId));
    if (o.onlyStock) p.push('onlyStock=1');
    if (o.sort && o.sort !== 'default') p.push('sort=' + encodeURIComponent(o.sort));
    if (o.minP !== '' && o.minP != null && isFinite(Number(o.minP))) p.push('minP=' + Number(o.minP));
    if (o.maxP !== '' && o.maxP != null && isFinite(Number(o.maxP))) p.push('maxP=' + Number(o.maxP));
    return '#/c/list' + (p.length ? '?' + p.join('&') : '');
  }

  function notFoundC(title, hint, back) {
    return '<div class="c-page">' + UI.cHeader({ title: title || '页面' }) +
      UI.empty({
        icon: '🧭', title: title || '没有找到', hint: hint || '',
        action: '<button class="btn primary" data-go="' + esc(back || '#/c/home') + '">回首页</button>'
      }) + '</div>';
  }

  function kvRow(k, v, strong) {
    return '<div class="x-kv"><span class="k">' + esc(k) + '</span>' +
      '<span class="v' + (strong ? ' bold' : '') + '">' + v + '</span></div>';
  }

  function addrText(a) {
    if (!a) return '';
    return esc(a.region || '') + ' ' + esc(a.detail || '');
  }

  function bannerHref(b) {
    var l = (b && b.link) || {};
    if (l.type === 'cat' && l.id) return '#/c/list?catId=' + encodeURIComponent(l.id);
    if (l.type === 'goods' && l.id) return '#/c/goods/' + encodeURIComponent(l.id);
    if (l.type === 'coupon') return '#/c/coupons';
    return '#/c/list';
  }

  /* ---------------- 商品详情：规格选择 ---------------- */
  function specDims(g) {
    var dims = [];
    each(g.skus, function (s) {
      Object.keys(s.specs).forEach(function (k) { if (dims.indexOf(k) < 0) dims.push(k); });
    });
    return dims;
  }
  function dimValues(g, dim) {
    var out = [];
    each(g.skus, function (s) {
      var v = s.specs[dim];
      if (v && out.indexOf(v) < 0) out.push(v);
    });
    return out;
  }
  function goodsSel(g) {
    var sel = selCache[g.id] || {};
    each(specDims(g), function (d) {
      if (sel[d] && dimValues(g, d).indexOf(sel[d]) < 0) delete sel[d];
    });
    selCache[g.id] = sel;
    return sel;
  }
  function findSkuBySel(g, sel) {
    var dims = specDims(g);
    for (var i = 0; i < dims.length; i++) if (!sel[dims[i]]) return null;
    return g.skus.filter(function (s) {
      return dims.every(function (d) { return s.specs[d] === sel[d]; });
    })[0] || null;
  }
  function optEnabled(g, sel, dim, val) {
    var dims = specDims(g);
    return g.skus.some(function (s) {
      if (s.specs[dim] !== val) return false;
      for (var i = 0; i < dims.length; i++) {
        var d = dims[i];
        if (d === dim) continue;
        if (sel[d] && s.specs[d] !== sel[d]) return false;
      }
      return Store.sellable(s.id) > 0;
    });
  }
  /* P-03：置灰有两种互斥原因，必须分开表达，不能一律说「缺货」——
     ① 该值在所有组合里都没有可售库存 → 真售罄（BR-GOODS-05）；
     ② 只与「当前已选维度」凑不出 SKU → 引导先取消冲突维度，点选时自动清空。 */
  function dimValueInStock(g, dim, val) {
    return g.skus.some(function (s) {
      return s.specs[dim] === val && Store.sellable(s.id) > 0;
    });
  }
  function specConflictDim(g, sel, dim, val) {
    var dims = specDims(g);
    for (var i = 0; i < dims.length; i++) {
      var d = dims[i];
      if (d === dim || !sel[d]) continue;
      var ok = g.skus.some(function (s) {
        return s.specs[dim] === val && s.specs[d] === sel[d] && Store.sellable(s.id) > 0;
      });
      if (!ok) return d;
    }
    return null;
  }

  /* ============================================================================
   * 八、手机端 17 屏
   * ==========================================================================*/
  var C_ROUTES = [];

  /* ---------------------------- 1. 首页 ---------------------------- */

  C_ROUTES.push({
    path: 'home',
    render: function () {
      var st = Store.state;
      var banners = (st.banners || []).slice(0, 5);
      var b0 = banners[0] || { title: '小满商城', sub: '', bg: '#ff5a1f', emoji: '🛍️' };
      var list = Store.goodsList({});

      var html = '<div class="c-page">' +
        catStrip() +
        '<div class="home-search">' +
          '<div class="logo">小满</div>' +
          '<div class="box" data-go="#/c/search">' + UI.icon('search', 15) +
            '<span>搜商品 / 标签，如「卫衣」</span></div>' +
          '<div data-go="#/c/cart" style="color:#fff">' + UI.icon('cart', 21) + '</div>' +
        '</div>' +
        '<div style="padding:10px 12px 0">' +
          '<div class="banner" id="home-banner" style="background:' + esc(b0.bg) + '" data-go="' + bannerHref(b0) + '">' +
            '<div><div class="t">' + esc(b0.title) + '</div><div class="s">' + esc(b0.sub) + '</div></div>' +
            '<div class="em">' + esc(b0.emoji) + '</div>' +
          '</div>' +
          '<div class="banner-dots" id="home-dots">' + banners.map(function (b, i) {
            return '<i class="' + (i === 0 ? 'on' : '') + '" data-i="' + i + '"></i>';
          }).join('') + '</div>' +
          '<div class="notice-bar mt10">' + UI.icon('bell', 14) +
            '<div class="grow">' + esc(st.notice.text) + '</div>' +
            '<span class="tiny dim">' + UI.fromNow(st.notice.at) + '</span></div>' +
          msgEntry() +
        '</div>' +
        '<div class="c-body" style="padding-top:0">' +
          '<div class="row between" style="margin-bottom:8px">' +
            '<div class="bold">为你推荐</div><div class="tiny dim">在售 ' + list.length + ' 件</div></div>' +
          '<div class="goods-grid">' + list.map(goodsCard).join('') + '</div>' +
          '<div class="tiny dim center" style="padding:14px 0 4px">已经到底了 · 演示数据</div>' +
        '</div>' +
      '</div>';

      return {
        html: html,
        tab: 'home',
        title: '首页',
        mount: function (root, ctx) {
          var el = root.querySelector('#home-banner');
          var dots = root.querySelector('#home-dots');
          if (!el || !banners.length) return;
          var idx = 0;
          function paint() {
            var b = banners[idx];
            el.style.background = b.bg;
            el.innerHTML = '<div><div class="t">' + esc(b.title) + '</div>' +
              '<div class="s">' + esc(b.sub) + '</div></div>' +
              '<div class="em">' + esc(b.emoji) + '</div>';
            el.setAttribute('data-go', bannerHref(b));
            each(dots.children, function (d, i) { d.className = i === idx ? 'on' : ''; });
          }
          dots.addEventListener('click', function (e) {
            var d = e.target.closest ? e.target.closest('[data-i]') : null;
            if (!d) return;
            idx = Number(d.getAttribute('data-i')) || 0;
            paint();
          });
          ctx.every(4000, function () {
            idx = (idx + 1) % banners.length;
            paint();
          });
        }
      };
    }
  });

  /* ---------------------------- 1b. 消息中心 ---------------------------- */
  C_ROUTES.push({
    path: 'messages',
    render: function (ctx) {
      var tab = ctx.query.type || 'all';
      if (!Store.MSG_TYPES.some(function (t) { return t.key === tab; })) tab = 'all';
      var list = Store.messages(tab);
      var unAll = Store.unreadCount();
      var tabs = Store.MSG_TYPES.map(function (t) {
        return { key: t.key, label: t.label, count: Store.unreadCount(t.key) };
      });
      var badge = { order: 'info', aftersale: 'warn', coupon: 'danger', system: 'gray' };
      var body = list.length
        ? list.map(function (m) {
            return '<div class="card pad x-msg' + (m.read ? '' : ' unread') + '" data-act="open" data-id="' + esc(m.id) + '">' +
              '<div class="row between"><span class="tag ' + (badge[m.type] || 'gray') + '">' +
                esc(Store.MSG_TYPE_NAME[m.type] || m.type) + '</span>' +
                '<span class="tiny dim">' + UI.fromNow(m.at) + ' · ' + UI.time(m.at) + '</span></div>' +
              '<div class="row between mt8"><div class="small bold">' + esc(m.title) + '</div>' +
                (m.read ? '' : '<i class="x-reddot"></i>') + '</div>' +
              '<div class="tiny dim mt6" style="line-height:1.7">' + esc(m.text) + '</div>' +
              (m.href ? '<div class="tiny mt8" style="color:var(--brand)">查看详情 ›</div>' : '') +
            '</div>';
          }).join('')
        : UI.empty({
            icon: '🔔', title: '暂无消息',
            hint: '订单、售后、优惠券的系统通知都会出现在这里'
          });

      return {
        html: '<div class="c-page">' +
          UI.cHeader({
            title: '消息',
            right: unAll
              ? '<span data-act="readAll">全部已读</span>'
              : '<span class="dim">已全部读过</span>'
          }) +
          tabsMarkup(tabs, tab) +
          '<div class="c-body" style="padding-top:10px">' + body + '</div>' +
        '</div>',
        title: '消息',
        actions: {
          otab: function (el) { go('#/c/messages?type=' + el.getAttribute('data-key')); },
          readAll: function () {
            Store.markAllRead();
            toast('已全部标记为已读', 'ok');
            render();
          },
          open: function (el) {
            var id = el.getAttribute('data-id');
            var m = Store.messages().filter(function (x) { return x.id === id; })[0];
            Store.markRead(id);
            if (m && m.href) go(m.href); else render();
          }
        }
      };
    }
  });

  /* ---------------------------- 2. 搜索 ---------------------------- */
  C_ROUTES.push({
    path: 'search',
    render: function (ctx) {
      var his = hisGet();
      var hot = ['T恤', '卫衣', '咖啡', '耳机', '保温杯', '牛仔裤', '键盘'];
      function chip(kind, kw) {
        return '<span class="spec-opt" data-act="' + kind + '" data-kw="' + esc(kw) + '">' + esc(kw) + '</span>';
      }
      var html = '<div class="c-page">' + UI.cHeader({ title: '搜索' }) +
        '<div class="c-body">' +
          '<div class="row" style="gap:8px">' +
            '<input class="input" id="kw" style="flex:1" placeholder="输入商品名 / 简介 / 标签" data-enter="submit" />' +
            '<button class="btn primary" data-act="submit">搜索</button>' +
          '</div>' +
          (ctx.query.kw ? '<div class="tiny dim mt6">上次搜索：' + esc(ctx.query.kw) + '</div>' : '') +
          '<div class="mt16"><div class="bold">热门搜索</div>' +
            '<div class="spec-opts mt10">' + hot.map(function (h) { return chip('hot', h); }).join('') + '</div></div>' +
          (his.length
            ? '<div class="mt16"><div class="row between"><div class="bold">搜索历史</div>' +
                '<div class="tiny dim" data-act="clearHis">清空</div></div>' +
                '<div class="spec-opts mt10">' + his.map(function (h) { return chip('his', h); }).join('') + '</div></div>'
            : '<div class="mt16 tiny dim">暂无搜索历史（提交过的关键词会记在这里）</div>') +
          '<div class="alert mt16">' + UI.icon('search', 15) +
            '<div>只搜在售商品（BR-SEARCH-01）；关键词为空时直接进列表，不写历史（BR-SEARCH-02）。</div></div>' +
        '</div>' +
      '</div>';

      function submit(kw) {
        kw = String(kw == null ? '' : kw).trim();
        if (!kw) { go('#/c/list'); return; }
        hisAdd(kw);
        go('#/c/list?kw=' + encodeURIComponent(kw));
      }

      return {
        html: html,
        title: '搜索',
        actions: {
          submit: function () {
            var el = document.getElementById('kw');
            submit(el ? el.value : '');
          },
          hot: function (el) { submit(el.getAttribute('data-kw')); },
          his: function (el) { submit(el.getAttribute('data-kw')); },
          clearHis: function () {
            hisClear();
            toast('搜索历史已清空', 'ok');
            render();
          }
        },
        mount: function (root) {
          var el = root.querySelector('#kw');
          if (el) setTimeout(function () { el.focus(); }, 30);
        }
      };
    }
  });

  /* ---------------------------- 3. 分类 ---------------------------- */
  C_ROUTES.push({
    path: 'category',
    render: function (ctx) {
      var cats = Store.state.cats;
      var tops = cats.filter(function (c) { return !c.parentId; })
        .sort(function (a, b) { return (a.sort || 0) - (b.sort || 0); });
      if (!tops.length) {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '分类', noBack: true }) +
            UI.empty({ icon: '🗂️', title: '还没有分类', hint: '后台新建分类后会出现在这里' }) + '</div>',
          tab: 'category', title: '分类'
        };
      }
      var curId = ctx.query.catId || tops[0].id;
      var curTop = tops.filter(function (c) { return c.id === curId; })[0] || tops[0];
      var kids = cats.filter(function (c) { return c.parentId === curTop.id; })
        .sort(function (a, b) { return (a.sort || 0) - (b.sort || 0); });

      var left = tops.map(function (c) {
        return '<div class="x-cat-item' + (c.id === curTop.id ? ' on' : '') + '" ' +
          'data-go="#/c/category?catId=' + esc(c.id) + '">' + esc(c.name) + '</div>';
      }).join('');

      var subs = '<div class="x-sub">' +
        '<div class="it" data-go="#/c/list?catId=' + esc(curTop.id) + '"><span class="e">🗂️</span>全部' + esc(curTop.name) + '</div>' +
        kids.map(function (c) {
          var n = Store.goodsList({ catId: c.id }).length;
          return '<div class="it" data-go="#/c/list?catId=' + esc(c.id) + '">' +
            '<span class="e">' + ['👗', '🏠', '🎧', '☕', '🏃'][(c.sort || 1) % 5] + '</span>' +
            esc(c.name) + '<div class="tiny dim">' + n + ' 件</div></div>';
        }).join('') +
      '</div>';

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '分类', noBack: true }) +
          '<div style="display:flex;min-height:600px">' +
            '<div class="x-cat-left">' + left + '</div>' +
            '<div class="x-cat-right">' +
              '<div class="bold" style="margin-bottom:10px">' + esc(curTop.name) + '</div>' + subs +
            '</div>' +
          '</div>' +
        '</div>',
        tab: 'category',
        title: '分类'
      };
    }
  });

  /* ---------------------------- 4. 列表（淘宝式筛选） ---------------------------- */
  var LIST_SORT = [
    { key: 'default', label: '综合' },
    { key: 'sales', label: '销量' },
    { key: 'price', label: '价格' },
    { key: 'new', label: '上新' }
  ];
  var PRICE_TIPS = [
    { min: 0, max: 50, label: '0-50' },
    { min: 50, max: 100, label: '50-100' },
    { min: 100, max: 200, label: '100-200' },
    { min: 200, max: '', label: '200 以上' }
  ];

  /* 商品层只支持 default/priceAsc/priceDesc/new；销量在页面侧后置排序（不改 store.js） */
  function storeSortOf(sort) {
    if (sort === 'priceAsc' || sort === 'priceDesc' || sort === 'new') return sort;
    return 'default';
  }
  function listQuery(o) {
    var opt = { kw: o.kw || '', catId: o.catId || '', sort: storeSortOf(o.sort) };
    if (o.minP !== '' && o.minP != null && isFinite(Number(o.minP))) opt.minP = Math.round(Number(o.minP) * 100);
    if (o.maxP !== '' && o.maxP != null && isFinite(Number(o.maxP))) opt.maxP = Math.round(Number(o.maxP) * 100);
    var list = Store.goodsList(opt);
    if (o.brandId) list = list.filter(function (g) { return g.brandId === o.brandId; });
    if (o.onlyStock) list = list.filter(function (g) { return Store.totalStock(g) > 0; });
    if (o.sort === 'sales') list = list.slice().sort(function (a, b) { return b.sales - a.sales; });
    return list;
  }
  function activeFilterCount(o) {
    var n = 0;
    if (o.catId) n++;
    if (o.brandId) n++;
    if (o.onlyStock) n++;
    if ((o.minP !== '' && o.minP != null) || (o.maxP !== '' && o.maxP != null)) n++;
    return n;
  }

  C_ROUTES.push({
    path: 'list',
    render: function (ctx) {
      var q = ctx.query;
      var kw = q.kw || '';
      var catId = q.catId || '';
      var brandId = q.brandId || '';
      var onlyStock = q.onlyStock === '1';
      var sort = q.sort || 'default';
      if (['default', 'sales', 'priceAsc', 'priceDesc', 'new'].indexOf(sort) < 0) sort = 'default';
      var minP = (q.minP === undefined || q.minP === '') ? '' : Number(q.minP);
      var maxP = (q.maxP === undefined || q.maxP === '') ? '' : Number(q.maxP);
      var swapped = false;
      if (minP !== '' && maxP !== '' && isFinite(minP) && isFinite(maxP) && minP > maxP) {
        var t = minP; minP = maxP; maxP = t; swapped = true;   // BR-SEARCH-05：上下限自动交换
      }

      var cond = { kw: kw, catId: catId, brandId: brandId, onlyStock: onlyStock, sort: sort, minP: minP, maxP: maxP };
      var list = listQuery(cond);
      var fCount = activeFilterCount(cond);
      var head = kw ? '「' + kw + '」的结果'
        : (catId ? catName(catId) : (brandId ? brandName(brandId) : '全部商品'));

      var chips = LIST_SORT.map(function (s) {
        var on = s.key === 'price'
          ? (sort === 'priceAsc' || sort === 'priceDesc')
          : (sort === s.key);
        var label = s.label + (s.key === 'price'
          ? (sort === 'priceDesc' ? ' ↓' : (sort === 'priceAsc' ? ' ↑' : ''))
          : '');
        return '<button class="x-chip' + (on ? ' on' : '') + '" data-act="sort" data-key="' + s.key + '">' +
          esc(label) + '</button>';
      }).join('') +
        '<button class="x-chip' + (onlyStock ? ' on' : '') + '" data-act="stock">仅看有货</button>';

      var filterBtn = '<button class="x-filter-btn' + (fCount ? ' on' : '') + '" data-act="filter">' +
        UI.icon('filter', 14) + '<span>筛选</span>' +
        (fCount ? '<span class="n">' + fCount + '</span>' : '') + '</button>';

      var body = list.length
        ? '<div class="goods-grid">' + list.map(goodsCard).join('') + '</div>'
        : UI.empty({
            icon: '🔍', title: '没有找到相关商品',
            hint: '当前筛选：' + esc(kw || '无关键词') + ' · ' + esc(catId ? catName(catId) : '全部分类') +
              (brandId ? ' · ' + esc(brandName(brandId)) : '') +
              '<br>试试换个关键词，或放宽价格区间 / 品牌',
            action: '<button class="btn primary" data-act="clearAll">清除筛选</button>'
          });

      var html = '<div class="c-page">' + UI.cHeader({ title: '商品列表' }) +
        '<div class="pad" style="padding-bottom:2px">' +
          '<div class="row between"><div class="bold ellipsis">' + esc(head) + '</div>' +
            '<span class="tiny dim" style="flex:none">共 ' + list.length + ' 件</span></div>' +
          (swapped ? '<div class="tiny mt6" style="color:var(--warn)">价格区间上下限已自动交换（BR-SEARCH-05）</div>' : '') +
        '</div>' +
        '<div class="x-listbar">' +
          '<div class="x-chips">' + chips + '</div>' + filterBtn +
        '</div>' +
        '<div class="c-body">' + body + '</div>' +
      '</div>';

      /* 筛选 sheet：草稿条件改动即刷新「查看 N 件商品」，点应用才写回 URL */
      function openFilter() {
        var d = { min: minP === '' ? '' : String(minP), max: maxP === '' ? '' : String(maxP), catId: catId, brandId: brandId, onlyStock: onlyStock };
        var catsHtml = topCats().map(function (c) {
          return '<div class="x-chip' + (d.catId === c.id ? ' on' : '') + '" data-fcat="' + esc(c.id) + '">' +
            esc(c.name) + '</div>';
        }).join('');
        var brandsHtml = (Store.state.brands || []).map(function (b) {
          return '<div class="x-chip' + (d.brandId === b.id ? ' on' : '') + '" data-fbrand="' + esc(b.id) + '">' +
            esc(b.name) + '</div>';
        }).join('');
        function quickOn(p) {
          var mn = d.min === '' ? 0 : Number(d.min);
          var mx = d.max === '' ? Infinity : Number(d.max);
          var want = p.max === '' ? Infinity : p.max;
          return (isFinite(mn) ? mn : 0) === p.min && mx === want;
        }
        var tipsHtml = PRICE_TIPS.map(function (p, i) {
          return '<div class="x-chip' + (quickOn(p) ? ' on' : '') + '" data-fq="' + i + '">' + esc(p.label) + '</div>';
        }).join('');
        var bodyHtml =
          '<div class="x-fsec"><div class="x-fhd">价格区间（元）</div>' +
            '<div class="row" style="gap:8px">' +
              '<input class="input" id="fx-min" inputmode="numeric" placeholder="最低价" value="' + esc(d.min) + '" />' +
              '<span class="dim">—</span>' +
              '<input class="input" id="fx-max" inputmode="numeric" placeholder="最高价" value="' + esc(d.max) + '" />' +
            '</div>' +
            '<div class="x-chips mt10">' + tipsHtml + '</div>' +
            '<div class="tiny dim mt8">上下限填反了会自动交换（BR-SEARCH-05）</div>' +
          '</div>' +
          '<div class="x-fsec"><div class="x-fhd">分类</div><div class="x-chips">' +
            '<div class="x-chip' + (d.catId ? '' : ' on') + '" data-fcat="">全部</div>' + catsHtml +
          '</div></div>' +
          '<div class="x-fsec"><div class="x-fhd">品牌</div><div class="x-chips">' +
            '<div class="x-chip' + (d.brandId ? '' : ' on') + '" data-fbrand="">不限</div>' + brandsHtml +
          '</div></div>' +
          '<div class="x-fsec"><div class="row between" data-fstock>' +
            '<div class="x-fhd" style="margin:0">仅看有货</div>' +
            '<div class="cbx' + (d.onlyStock ? ' on' : '') + '" id="fx-stock"></div></div></div>' +
          '<div class="x-fft">' +
            '<button class="btn ghost" id="fx-reset">重置</button>' +
            '<button class="btn primary" id="fx-apply">查看 ' + listQuery({ kw: kw, catId: d.catId, brandId: d.brandId, onlyStock: d.onlyStock, sort: sort, minP: d.min, maxP: d.max }).length + ' 件商品</button>' +
          '</div>';

        UI.sheet({
          title: '筛选',
          mid: false,                      // 底部弹出（.overlay 默认 flex-end）
          body: bodyHtml,
          onMount: function (ov, close) {
            var minEl = ov.querySelector('#fx-min');
            var maxEl = ov.querySelector('#fx-max');
            var cntEl = ov.querySelector('#fx-apply');
            var stockEl = ov.querySelector('#fx-stock');
            function paint() {
              each(ov.querySelectorAll('[data-fcat]'), function (el) {
                el.className = 'x-chip' + (d.catId === el.getAttribute('data-fcat') ? ' on' : '');
              });
              each(ov.querySelectorAll('[data-fbrand]'), function (el) {
                el.className = 'x-chip' + (d.brandId === el.getAttribute('data-fbrand') ? ' on' : '');
              });
              each(ov.querySelectorAll('[data-fq]'), function (el) {
                var p = PRICE_TIPS[Number(el.getAttribute('data-fq'))];
                el.className = 'x-chip' + (quickOn(p) ? ' on' : '');
              });
              if (stockEl) stockEl.className = 'cbx' + (d.onlyStock ? ' on' : '');
              var n = listQuery({
                kw: kw, catId: d.catId, brandId: d.brandId,
                onlyStock: d.onlyStock, sort: sort, minP: d.min, maxP: d.max
              }).length;
              if (cntEl) cntEl.textContent = '查看 ' + n + ' 件商品';
            }
            ov.addEventListener('click', function (e) {
              var t = e.target.closest ? e.target.closest('[data-fq],[data-fcat],[data-fbrand],[data-fstock],#fx-reset,#fx-apply') : null;
              if (!t) return;
              if (t.hasAttribute('data-fq')) {
                var p = PRICE_TIPS[Number(t.getAttribute('data-fq'))];
                if (quickOn(p)) { d.min = ''; d.max = ''; }
                else {
                  d.min = p.min === 0 ? '' : String(p.min);
                  d.max = p.max === '' ? '' : String(p.max);
                }
                if (minEl) minEl.value = d.min;
                if (maxEl) maxEl.value = d.max;
                paint();
                return;
              }
              if (t.hasAttribute('data-fcat')) { d.catId = t.getAttribute('data-fcat'); paint(); return; }
              if (t.hasAttribute('data-fbrand')) { d.brandId = t.getAttribute('data-fbrand'); paint(); return; }
              if (t.hasAttribute('data-fstock')) { d.onlyStock = !d.onlyStock; paint(); return; }
              if (t.id === 'fx-reset') {
                d.min = ''; d.max = ''; d.catId = ''; d.brandId = ''; d.onlyStock = false;
                if (minEl) minEl.value = '';
                if (maxEl) maxEl.value = '';
                paint();
                return;
              }
              if (t.id === 'fx-apply') {
                go(listUrl({
                  kw: kw, catId: d.catId, brandId: d.brandId, onlyStock: d.onlyStock,
                  sort: sort, minP: d.min, maxP: d.max
                }));
                close();
              }
            });
            if (minEl) minEl.addEventListener('input', function () { d.min = minEl.value.trim(); paint(); });
            if (maxEl) maxEl.addEventListener('input', function () { d.max = maxEl.value.trim(); paint(); });
          }
        });
      }

      return {
        html: html,
        title: '商品列表',
        actions: {
          sort: function (el) {
            var k = el.getAttribute('data-key');
            var next = k === 'price' ? (sort === 'priceAsc' ? 'priceDesc' : 'priceAsc') : k;
            go(listUrl(mix(cond, { sort: next })));
          },
          stock: function () { go(listUrl(mix(cond, { onlyStock: !onlyStock }))); },
          filter: openFilter,
          clearAll: function () { go('#/c/list'); }
        }
      };
    }
  });

  /* ---------------------------- 5. 商品详情 ---------------------------- */
  C_ROUTES.push({
    path: 'goods/:id',
    render: function (ctx) {
      var g = Store.goodsOf(ctx.params.id);
      if (!g) {
        return { html: notFoundC('商品不存在', '链接可能已失效', '#/c/list'), title: '商品详情' };
      }
      if (!g.onSale) {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '商品详情' }) +
            UI.empty({
              icon: '📦', title: '商品已下架', hint: '下架商品 C 端列表与详情不可访问（BR-GOODS-08）',
              action: '<button class="btn primary" data-go="#/c/list">看看别的</button>'
            }) + '</div>',
          title: '商品详情'
        };
      }

      /* 足迹：只有「不是最近一条」时才写入，避免每次局部重渲染都重复记账 */
      var fpTop = Store.footprintList()[0];
      if (!fpTop || fpTop.goodsId !== g.id) Store.addFootprint(g.id);

      var faved = Store.isFav(g.id);
      var dims = specDims(g);
      var sel = goodsSel(g);
      var sku = findSkuBySel(g, sel);
      var priceCent = sku ? sku.price : Store.displayPrice(g);
      var sellable = sku ? Store.sellable(sku.id) : 0;
      var qty = sku ? (qtyCache[sku.id] || 1) : 1;
      if (sku) qty = Math.min(Math.max(1, qty), Math.max(1, sellable));

      var specHtml = dims.map(function (d) {
        var opts = dimValues(g, d).map(function (v) {
          var on = sel[d] === v;
          var en = optEnabled(g, sel, d, v);
          var tip = en ? '' : (dimValueInStock(g, d, v)
            ? ' title="与已选规格没有这个组合，点选会自动切换"'
            : ' title="该规格已售罄"');
          return '<div class="spec-opt' + (on ? ' on' : '') + (en ? '' : ' disabled') + '"' + tip + ' ' +
            'data-act="spec" data-dim="' + esc(d) + '" data-val="' + esc(v) + '">' + esc(v) + '</div>';
        }).join('');
        return '<div class="spec-group"><div class="hd">' + esc(d) +
          (sel[d] ? '<span class="tiny dim" style="font-weight:400">已选 ' + esc(sel[d]) + '</span>' : '') +
          '</div><div class="spec-opts">' + opts + '</div></div>';
      }).join('');

      var reviews = Store.state.reviews.filter(function (r) { return r.goodsId === g.id; });
      var rateAvg = reviews.length
        ? (reviews.reduce(function (a, r) { return a + r.rate; }, 0) / reviews.length).toFixed(1) : '—';

      var detailPane = '<div class="small" style="line-height:1.8;color:var(--text-2)">' +
        esc(g.detail) + '</div>' +
        '<div class="mt12">' + UI.thumb(g.pics[0], g.pics[1], 'lg') + '</div>';
      var paramPane = '<div class="x-kv"><span class="k">商品名称</span><span class="v">' + esc(g.name) + '</span></div>' +
        '<div class="x-kv"><span class="k">品牌</span><span class="v">' + esc(brandName(g.brandId) || '—') + '</span></div>' +
        '<div class="x-kv"><span class="k">分类</span><span class="v">' + esc(catName(g.catId) || '—') + '</span></div>' +
        '<div class="x-kv"><span class="k">规格数</span><span class="v">' + g.skus.length + ' 个</span></div>' +
        '<div class="x-hr"></div>' +
        g.skus.map(function (s) {
          return '<div class="x-kv"><span class="k">' + esc(Store.specText(s)) + '</span>' +
            '<span class="v">' + price(s.price) + ' · 库存 ' + Store.sellable(s.id) + '</span></div>';
        }).join('');
      var reviewPane = reviews.length
        ? '<div class="row between" style="margin-bottom:8px"><div class="bold">' + rateAvg + ' 分</div>' +
            '<div class="tiny dim">' + reviews.length + ' 条评价</div></div>' +
          reviews.slice(0, 10).map(function (r) {
            return '<div style="padding:10px 0;border-bottom:1px solid var(--line)">' +
              '<div class="row between"><div class="small bold">' + esc(r.user) + '</div>' +
                '<div class="tiny dim">' + UI.fromNow(r.at) + '</div></div>' +
              '<div style="margin:4px 0">' + UI.starRate(r.rate, 13) + '</div>' +
              '<div class="small" style="color:var(--text-2)">' + esc(r.content) + '</div>' +
              (r.reply ? '<div class="tiny mt6" style="background:#f7f8fa;padding:6px 8px;border-radius:8px">商家回复：' + esc(r.reply) + '</div>' : '') +
            '</div>';
          }).join('')
        : UI.empty({ icon: '💬', title: '还没有评价', hint: '确认收货后可以来评价' });

      var pane = detailTab === 'params' ? paramPane : (detailTab === 'reviews' ? reviewPane : detailPane);

      var actionHtml = '<div class="row" style="flex:none;gap:2px;text-align:center">' +
          '<div data-act="service" style="font-size:10.5px;color:var(--text-2);padding:0 8px">' + UI.icon('comment', 19) + '<div>客服</div></div>' +
          '<div data-act="fav" style="font-size:10.5px;color:' + (faved ? 'var(--brand)' : 'var(--text-2)') + ';padding:0 8px">' +
            UI.icon('star', 19) + '<div>' + (faved ? '已收藏' : '收藏') + '</div></div>' +
        '</div>' +
        '<button class="btn" data-act="addCart" style="flex:1">加入购物车</button>' +
        '<button class="btn primary" data-act="buyNow" style="flex:1">立即购买</button>';

      function pick() {
        var s = findSkuBySel(g, goodsSel(g));
        if (!s) { toast('请先选全规格', 'err'); return null; }
        var av = Store.sellable(s.id);
        if (av <= 0) { toast('该规格暂时缺货', 'err'); return null; }
        return s;
      }

      return {
        html: '<div class="c-page">' +
          UI.cHeader({
            title: '商品详情',
            right: '<span data-act="share">分享</span>'
          }) +
          '<div class="thumb lg" style="background:linear-gradient(135deg,' + esc(g.pics[0]) + ',#ffffff66)">' + esc(g.pics[1]) + '</div>' +
          '<div class="detail-price">' +
            '<div class="p">' + price(priceCent) + '</div>' +
            '<div class="row between mt6" style="font-size:12px;opacity:.92">' +
              '<span>已售 ' + g.sales + ' 件</span>' +
              '<span>' + (sku ? ('库存 ' + sellable + ' 件') : '请选择规格') + '</span>' +
            '</div>' +
          '</div>' +
          '<div class="c-body">' +
            '<div class="card pad">' +
              '<div class="bold" style="font-size:15px;line-height:1.5">' + esc(g.name) + '</div>' +
              '<div class="tiny dim mt6">' + esc(g.brief) + '</div>' +
              '<div class="row mt8" style="gap:6px">' + (g.tags || []).map(function (t) {
                return '<span class="tag">' + esc(t) + '</span>';
              }).join('') + '</div>' +
            '</div>' +
            '<div class="card mt10">' + (specHtml || '<div class="pad tiny dim">该商品只有一个默认规格</div>') + '</div>' +
            '<div class="card pad mt10 row between">' +
              '<div class="bold">数量</div>' +
              '<div class="row" style="gap:10px"><span class="tiny dim">' + (sku ? '最多 ' + Math.max(1, sellable) + ' 件' : '先选规格') + '</span>' +
                (sku ? UI.stepper(qty, 1, Math.max(1, sellable)) : UI.stepper(1, 1, 1)) + '</div>' +
            '</div>' +
            '<div class="card mt10" style="overflow:hidden">' +
              UI.tabs([
                { key: 'detail', label: '商品详情' },
                { key: 'params', label: '参数' },
                { key: 'reviews', label: '评价(' + reviews.length + ')' }
              ], detailTab, 'gtab') +
              '<div class="pad">' + pane + '</div>' +
            '</div>' +
          '</div>' +
        '</div>',
        action: actionHtml,
        title: g.name,
        actions: {
          spec: function (el) {
            var d = el.getAttribute('data-dim');
            var v = el.getAttribute('data-val');
            var cur = goodsSel(g);
            if (el.className.indexOf('disabled') >= 0) {
              // P-03：置灰原因分开说，不再一律断言「缺货」
              if (!dimValueInStock(g, d, v)) {
                toast('「' + v + '」已售罄，暂无可售库存（BR-GOODS-05）', 'err');
                return;
              }
              var cleared = [];
              var conflict = specConflictDim(g, cur, d, v);
              while (conflict) {
                cleared.push(conflict + '：' + cur[conflict]);
                delete cur[conflict];
                conflict = specConflictDim(g, cur, d, v);
              }
              if (!cleared.length) { toast('该规格组合暂时缺货（BR-GOODS-05）', 'err'); return; }
              cur[d] = v;
              selCache[g.id] = cur;
              toast('已切换为「' + v + '」：原已选「' + cleared.join('、') + '」没有这个组合，已自动取消', 'ok');
              render();
              return;
            }
            if (cur[d] === v) delete cur[d]; else cur[d] = v;
            selCache[g.id] = cur;
            render();
          },
          step: function (el) {
            var s = findSkuBySel(g, goodsSel(g));
            if (!s) { toast('请先选全规格', 'err'); return; }
            var max = Math.max(1, Store.sellable(s.id));
            var q = qtyCache[s.id] || 1;
            q += el.getAttribute('data-step') === '+' ? 1 : -1;
            qtyCache[s.id] = Math.min(max, Math.max(1, q));
            render();
          },
          gtab: function (el) { detailTab = el.getAttribute('data-key'); render(); },
          addCart: function () {
            var s = pick();
            if (!s) return;
            var q = qtyCache[s.id] || 1;
            if (inCartQty(g.id, s.id) + q > Store.sellable(s.id)) {
              toast('该规格仅剩 ' + Store.sellable(s.id) + ' 件（购物车已有 ' + inCartQty(g.id, s.id) + '）', 'err');
              return;
            }
            var r = Store.cartAdd(g.id, s.id, q);
            if (okToast(r)) render();
          },
          buyNow: function () {
            var s = pick();
            if (!s) return;
            var q = qtyCache[s.id] || 1;
            if (q > Store.sellable(s.id)) { toast('该规格仅剩 ' + Store.sellable(s.id) + ' 件', 'err'); return; }
            App.setBuyNow({
              key: 'buynow|' + g.id + '|' + s.id, goodsId: g.id, skuId: s.id, qty: q,
              checked: true, name: g.name, specText: Store.specText(s),
              emoji: g.pics[1], bg: g.pics[0], price: s.price,
              invalid: null, sellable: Store.sellable(s.id)
            });
            go('#/c/checkout');
          },
          service: function () { toast('客服为占位入口（需求未定义会话能力）'); },
          fav: function () {
            okToast(Store.toggleFav(g.id));
            render();
          },
          share: function () { toast('分享为占位入口'); }
        }
      };
    }
  });

  /* ---------------------------- 6. 购物车 ---------------------------- */
  C_ROUTES.push({
    path: 'cart',
    render: function () {
      var lines = Store.cartLines();
      var good = lines.filter(function (l) { return !l.invalid; });
      var bad = lines.filter(function (l) { return l.invalid; });
      var checked = good.filter(function (l) { return l.checked; });
      var total = sum(checked, function (l) { return l.price * l.qty; });
      var allChecked = good.length > 0 && good.every(function (l) { return l.checked; });

      var goodRows = good.map(function (l) {
        return '<div class="card pad" style="margin-bottom:8px">' +
          '<div class="row top">' +
            '<div class="cbx' + (l.checked ? ' on' : '') + '" data-act="check" data-key="' + esc(l.key) + '"></div>' +
            UI.thumb(l.bg, l.emoji, 'sm') +
            '<div class="grow">' +
              '<div class="small bold clamp2">' + esc(l.name) + '</div>' +
              '<div class="tiny dim mt6">' + esc(l.specText) + '</div>' +
              '<div class="row between mt8">' + price(l.price) +
                '<div class="row" style="gap:8px">' +
                  '<div data-key="' + esc(l.key) + '">' + UI.stepper(l.qty, 1, Math.max(1, l.sellable)) + '</div>' +
                  '<span class="tiny dim" data-act="remove" data-key="' + esc(l.key) + '">删除</span>' +
                '</div>' +
              '</div>' +
              (l.qty > l.sellable ? '<div class="tiny" style="color:var(--danger);margin-top:4px">可售仅剩 ' + l.sellable + ' 件，结算会被拦截</div>' : '') +
            '</div>' +
          '</div>' +
        '</div>';
      }).join('');

      var badRows = bad.map(function (l) {
        return '<div class="card pad" style="margin-bottom:8px;opacity:.72">' +
          '<div class="row top">' +
            '<div class="cbx disabled"></div>' +
            UI.thumb(l.bg, l.emoji, 'sm') +
            '<div class="grow">' +
              '<div class="small bold clamp2">' + esc(l.name) + '</div>' +
              '<div class="tiny dim mt6">' + esc(l.specText) + '</div>' +
              '<div class="row between mt8"><span class="tag gray">' + esc(l.invalid) + '</span>' +
                '<span class="tiny dim" data-act="remove" data-key="' + esc(l.key) + '">删除</span></div>' +
            '</div>' +
          '</div>' +
        '</div>';
      }).join('');

      var body = lines.length
        ? '<div class="c-body">' +
            '<div class="card pad row between" style="margin-bottom:10px">' +
              '<div class="row" style="gap:8px">' +
                '<div class="cbx' + (allChecked ? ' on' : '') + '" data-act="checkAll"></div>' +
                '<span class="small bold">小满商城 · 自营</span>' +
              '</div>' +
              '<span class="tiny dim">' + good.length + ' 种在售</span>' +
            '</div>' +
            goodRows +
            (bad.length
              ? '<div class="row between mt12" style="margin-bottom:8px">' +
                  '<div class="small bold">失效商品（' + bad.length + '）</div>' +
                  '<div class="tiny dim" data-act="clearBad">清空失效</div></div>' + badRows
              : '') +
            '<div class="tiny dim center" style="padding:10px 0">失效行不可勾选、不参与结算（BR-CART-05）</div>' +
          '</div>'
        : UI.empty({
            icon: '🛒', title: '购物车还是空的', hint: '挑几件喜欢的商品加进来吧',
            action: '<button class="btn primary" data-go="#/c/home">去逛逛</button>'
          });

      return {
        html: '<div class="c-page">' +
          UI.cHeader({
            title: '购物车',
            noBack: true,
            right: (lines.length ? '<span data-act="clearAll">清空</span>' : '')
          }) + body + '</div>',
        action: lines.length
          ? '<div class="cbx' + (allChecked ? ' on' : '') + '" data-act="checkAll"></div>' +
            '<span class="small">全选</span>' +
            '<div class="sum">合计 <span>' + price(total) + '</span>' +
              '<span class="tiny dim">（已选 ' + checked.length + ' 种）</span></div>' +
            '<button class="btn primary" data-act="checkout"' + (checked.length ? '' : ' disabled') + '>去结算</button>'
          : '',
        tab: 'cart',
        title: '购物车',
        actions: {
          check: function (el) {
            Store.cartToggle(el.getAttribute('data-key'));
            render();
          },
          checkAll: function () {
            Store.cartToggleAll(!allChecked);
            render();
          },
          step: function (el) {
            var host = el.closest('[data-key]');
            if (!host) return;
            var key = host.getAttribute('data-key');
            var line = null;
            Store.cartLines().forEach(function (l) { if (l.key === key) line = l; });
            if (!line) return;
            var q = line.qty + (el.getAttribute('data-step') === '+' ? 1 : -1);
            if (q < 1) { toast('数量至少为 1', 'err'); return; }
            if (q > 999) { toast('单行最多 999 件（BR-CART-02）', 'err'); return; }
            if (q > line.sellable) { toast('该规格仅剩 ' + line.sellable + ' 件', 'err'); return; }
            Store.cartUpdateQty(key, q);
            render();
          },
          remove: function (el) {
            Store.cartRemove(el.getAttribute('data-key'));
            toast('已删除该行', 'ok');
            render();
          },
          clearBad: function () {
            Store.cartLines().filter(function (l) { return l.invalid; })
              .forEach(function (l) { Store.cartRemove(l.key); });
            toast('已清空失效商品', 'ok');
            render();
          },
          clearAll: function () {
            UI.confirm({ title: '清空购物车', body: '将删除购物车内全部商品行。', okText: '清空', danger: true })
              .then(function (yes) {
                if (!yes) return;
                Store.cartClear();
                toast('购物车已清空', 'ok');
                render();
              });
          },
          checkout: function () {
            if (!checked.length) { toast('请先勾选要结算的商品', 'err'); return; }
            App.clearBuyNow();
            go('#/c/checkout');
          }
        }
      };
    }
  });

  /* ---------------------------- 7. 确认订单 ---------------------------- */
  var coCouponId = null;
  var coRemark = '';

  C_ROUTES.push({
    path: 'checkout',
    render: function (ctx) {
      var lines = App.getBuyNow() ? [App.getBuyNow()] : Store.cartLines().filter(function (l) { return l.checked && !l.invalid; });
      if (!lines.length) {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '确认订单' }) +
            UI.empty({
              icon: '🧾', title: '没有待结算的商品', hint: '回购物车勾选商品，或直接「立即购买」',
              action: '<button class="btn primary" data-go="#/c/cart">回购物车</button>'
            }) + '</div>',
          title: '确认订单'
        };
      }

      var addr = null;
      if (ctx.query.addressId) {
        addr = Store.state.addresses.filter(function (x) { return x.id === ctx.query.addressId; })[0] || null;
      }
      if (!addr) addr = Store.defaultAddress();

      var goodsAmount = sum(lines, function (l) { return l.price * l.qty; });
      var mine = Store.myCoupons('unused').filter(function (x) { return x.c; });
      var couponId = (coCouponId && mine.some(function (x) { return x.c.id === coCouponId; })) ? coCouponId : null;
      var pv = Store.previewOrder({ lines: lines, couponId: couponId });
      var usable = mine.filter(function (x) { return x.c.amount <= goodsAmount && goodsAmount >= x.c.threshold; });
      var coupon = couponId ? Store.couponById(couponId) : null;

      function couponSheet() {
        var claimedIds = mine.map(function (x) { return x.c.id; });
        var body = '<div class="small bold" style="margin-bottom:6px">我的券</div>';
        if (!mine.length) body += '<div class="tiny dim" style="padding:8px 0">还没有券，去券中心领一张</div>';
        body += mine.map(function (x) {
          var ok = x.c.amount <= goodsAmount && goodsAmount >= x.c.threshold;
          var reason = ok ? '' : ('门槛不足：满 ' + UI.yuan(x.c.threshold) + ' 可用');
          return '<div class="cell clickable" data-pick="' + (ok ? esc(x.c.id) : '') + '" style="border-radius:10px"' + (ok ? '' : ' aria-disabled="true"') + '>' +
            '<div class="grow"><div class="small bold">' + esc(x.c.name) + '</div>' +
              '<div class="tiny dim">' + esc(reason || ('有效期至 ' + UI.time(x.c.expireAt, false))) + '</div></div>' +
            '<div class="value" style="color:' + (ok ? 'var(--price)' : 'var(--text-3)') + '">-' + price(x.c.amount) + '</div>' +
          '</div>';
        }).join('');

        body += '<div class="x-hr"></div><div class="row between" style="margin-bottom:6px">' +
          '<span class="small bold">领券中心</span>' +
          '<span class="tiny" data-close data-go="#/c/coupons" style="color:var(--brand)">去领券 ›</span></div>';
        body += Store.state.coupons.map(function (c) {
          var mineAlready = claimedIds.indexOf(c.id) >= 0;
          var used = Store.myCoupons('used').some(function (x) { return x.c.id === c.id; });
          var full = c.claimed >= c.total;
          var expired = Date.now() > c.expireAt;
          var note, col;
          if (mineAlready) { note = '已领过（门槛不足）'; col = 'var(--text-2)'; }
          else if (used) { note = '已使用'; col = 'var(--text-2)'; }
          else if (expired) { note = '已过期'; col = 'var(--text-3)'; }
          else if (full) { note = '已领完'; col = 'var(--danger)'; }
          else if (c.disabled) { note = '已停用'; col = 'var(--text-3)'; }
          else { note = '未领取，可去领取'; col = 'var(--brand)'; }
          return '<div class="cell" style="border-radius:10px">' +
            '<div class="grow"><div class="small">' + esc(c.name) + '</div>' +
              '<div class="tiny dim">满 ' + UI.yuan(c.threshold) + ' 可用 · 剩 ' + Math.max(0, c.total - c.claimed) + ' 张</div></div>' +
            '<div class="value tiny" style="color:' + col + '">' + esc(note) + '</div>' +
          '</div>';
        }).join('');

        var sh = UI.sheet({
          title: '选择优惠券',
          body: body,
          actions: [
            { text: '不使用优惠券', onClick: function () { coCouponId = null; render(); } },
            { text: '关闭', type: 'primary' }
          ],
          onMount: function (ov, close) {
            ov.addEventListener('click', function (e) {
              var pickEl = e.target.closest ? e.target.closest('[data-pick]') : null;
              if (!pickEl) return;
              var id = pickEl.getAttribute('data-pick');
              if (!id) { toast('这张券不满足使用条件', 'err'); return; }
              coCouponId = id;
              close();
              render();
            });
          }
        });
        return sh;
      }

      var addrBlock = addr
        ? '<div class="addr-card" data-go="#/c/address?pick=1">' +
            '<div class="ic">' + UI.icon('location', 20) + '</div>' +
            '<div class="grow">' +
              '<div class="line1">' + esc(addr.name) + ' <span class="small muted">' + esc(addr.phone) + '</span>' +
                (addr.isDefault ? ' <span class="tag">默认</span>' : '') + '</div>' +
              '<div class="line2">' + addrText(addr) + '</div>' +
            '</div>' +
            '<div class="arrow">' + UI.icon('right', 16) + '</div>' +
          '</div>'
        : '<div class="alert warn">' + UI.icon('warn', 15) +
            '<div class="grow">还没有收货地址，无法提交订单（BR-ORDER-01）。</div>' +
            '<button class="btn sm" data-go="#/c/address">去新增</button></div>';

      var itemBlock = '<div class="card pad" style="margin-top:10px">' +
        '<div class="row between small bold" style="margin-bottom:2px"><span>小满商城 · 自营</span>' +
          '<span class="tiny dim">' + lines.length + ' 种</span></div>' +
        lines.map(function (l) { return itemRow({ name: l.name, specText: l.specText, emoji: l.emoji, bg: l.bg, price: l.price, qty: l.qty }); }).join('') +
      '</div>';

      var couponRow = '<div class="card mt10" style="overflow:hidden">' +
        '<div class="cell clickable" data-act="coupon">' +
          '<div class="label">优惠券</div>' +
          '<div class="value">' +
            (pv.couponAmount > 0
              ? '<span style="color:var(--price);font-weight:700">-' + price(pv.couponAmount) + '</span> ' + esc(coupon ? coupon.name : '')
              : (usable.length ? '<span style="color:var(--brand)">' + usable.length + ' 张可用 ›</span>' : '<span class="dim">无可用券 ›</span>')) +
          '</div>' +
        '</div>' +
      '</div>';

      var amountBlock = '<div class="card mt10" style="overflow:hidden">' +
        '<div class="cell"><div class="label">商品金额</div><div class="value">' + price(pv.goodsAmount) + '</div></div>' +
        '<div class="cell"><div class="label">优惠</div><div class="value">' + (pv.couponAmount > 0 ? '<span style="color:var(--price)">-' + price(pv.couponAmount) + '</span>' : '—') + '</div></div>' +
        '<div class="cell"><div class="label">运费</div><div class="value">' + price(0) + ' <span class="tiny dim">（本期固定 0，BR-ORDER-11）</span></div></div>' +
        '<div class="cell"><div class="label bold">应付</div><div class="value">' + price(pv.payAmount, 'price-lg') + '</div></div>' +
      '</div>';

      var remarkBlock = '<div class="card pad mt10">' +
        '<div class="small bold" style="margin-bottom:6px">订单备注</div>' +
        '<textarea class="textarea" id="co-remark" maxlength="100" placeholder="给商家留言（选填，≤100 字）">' + esc(coRemark) + '</textarea>' +
      '</div>';

      function submit() {
        var rk = document.getElementById('co-remark');
        var remark = rk ? rk.value : coRemark;
        var r = Store.submitOrder({
          lines: lines,
          couponId: couponId || undefined,
          remark: remark,
          addressId: addr ? addr.id : undefined
        });
        if (!okToast(r)) return;
        App.clearBuyNow();
        coCouponId = null;
        coRemark = '';
        go('#/c/pay/' + r.orderId);
      }

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '确认订单' }) +
          '<div class="c-body">' + addrBlock + itemBlock + couponRow + amountBlock + remarkBlock +
            '<div class="tiny dim center" style="padding:12px 0">提交后库存立即锁定，30 分钟内未支付自动关单（BR-ORDER-02/06）</div>' +
          '</div>' +
        '</div>',
        action: '<div class="sum">应付 <span>' + price(pv.payAmount) + '</span>' +
            (pv.couponAmount > 0 ? '<div class="tiny dim">已优惠 ' + price(pv.couponAmount) + '</div>' : '') + '</div>' +
          '<button class="btn primary" data-act="submit"' + (addr ? '' : ' disabled') + '>提交订单</button>',
        title: '确认订单',
        mount: function (root) {
          var rk = root.querySelector('#co-remark');
          if (rk) rk.addEventListener('input', function () { coRemark = rk.value; });
        },
        actions: {
          coupon: couponSheet,
          submit: submit
        }
      };
    }
  });

  /* ---------------------------- 8. 演示支付 ---------------------------- */
  C_ROUTES.push({
    path: 'pay/:orderId',
    render: function (ctx) {
      var o = Store.orderById(ctx.params.orderId);
      if (!o) return { html: notFoundC('订单不存在', '请回订单列表刷新（BR-PAY-08）', '#/c/orders'), title: '支付' };

      if (o.status !== 'unpaid') {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '支付' }) +
            '<div class="c-body">' +
              UI.alert('<b>该订单无需支付</b><br>当前状态：' + esc(Store.ORDER_STATUS[o.status] || o.status) + '（BR-PAY-02）', 'warn') +
              '<div class="card pad mt10 center">' +
                '<div class="tiny dim">订单号 ' + esc(o.sn) + '</div>' +
                '<div class="mt8">' + price(o.payAmount, 'price-xl') + '</div>' +
              '</div>' +
            '</div>' +
            '<div style="padding:0 12px 16px"><button class="btn primary block" data-go="#/c/order/' + esc(o.id) + '">查看订单详情</button></div>' +
          '</div>',
          title: '支付'
        };
      }

      var deadline = o.createdAt + PAY_WINDOW;

      /* P-04：超时判定只认 Store.orderExpired（仓内唯一口径），页面不再自己算 30 分钟；
         超时后两个模拟支付按钮置灰，并真正按「超时关闭」落库（释放锁量、退券、写时间线）。 */
      if (Store.orderExpired(o.id)) {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '演示支付' }) +
            '<div class="c-body">' +
              '<div class="card pad center">' +
                '<div class="tiny dim mt12">应付金额</div>' +
                '<div style="margin-top:6px">' + price(o.payAmount, 'price-xl') + '</div>' +
                '<div class="small muted mt8">订单号 ' + esc(o.sn) + '</div>' +
              '</div>' +
              '<div class="mt10">' + UI.alert('<b>订单已超时关闭</b><br>' +
                '已超过 30 分钟未付款，按超时关闭处理：锁定库存已释放、优惠券退回未使用且不延长有效期（BR-ORDER-06）。<br>请重新下单。', 'err') + '</div>' +
            '</div>' +
            '<div style="padding:0 12px 16px"><button class="btn block" data-go="' +
              esc(o.items.length && o.items[0].goodsId ? '#/c/goods/' + o.items[0].goodsId : '#/c/home') + '">重新下单</button></div>' +
          '</div>',
          action: '<button class="btn" data-act="failPay" disabled style="flex:1">模拟支付失败</button>' +
            '<button class="btn primary" data-act="payOk" disabled style="flex:1">模拟支付成功</button>',
          title: '演示支付',
          mount: function () {
            Store.closeOrder(o.id, '超时关闭');
          }
        };
      }

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '演示支付' }) +
          '<div class="c-body">' +
            '<div class="card pad center">' +
              '<div class="demo-flag">演示模式：不产生真实扣款</div>' +
              '<div class="tiny dim mt12">应付金额</div>' +
              '<div style="margin-top:6px">' + price(o.payAmount, 'price-xl') + '</div>' +
              '<div class="small muted mt8">订单号 ' + esc(o.sn) + '</div>' +
              '<div class="mt10 small">支付剩余时间 <b id="pay-left" style="color:var(--price);font-variant-numeric:tabular-nums">--:--</b></div>' +
              '<div class="tiny dim mt6">倒计时仅作提示，超时以订单状态为准（BR-ORDER-06）</div>' +
            '</div>' +
            (payFailMsg
              ? '<div class="mt10">' + UI.alert('<b>支付失败（演示分支）</b><br>' + esc(payFailMsg) + '<br>订单仍为「未付款」，可在 30 分钟内重试（BR-PAY-04/05）。', 'err') + '</div>'
              : '') +
            '<div class="card mt10" style="overflow:hidden">' +
              '<div class="cell"><div class="label">商品金额</div><div class="value">' + price(o.goodsAmount) + '</div></div>' +
              '<div class="cell"><div class="label">券抵扣</div><div class="value">' + (o.couponAmount ? ('-' + price(o.couponAmount)) : '—') + '</div></div>' +
              '<div class="cell"><div class="label">运费</div><div class="value">' + price(o.freight) + '</div></div>' +
            '</div>' +
            '<div class="alert mt12">' + UI.icon('bell', 15) +
              '<div>点「模拟支付成功」后订单转「待发货」并完成锁库存转实扣；点「模拟支付失败」只改本次结果，订单与库存都不动（BR-PAY-03/04/05）。</div></div>' +
          '</div>' +
        '</div>',
        action: '<button class="btn" data-act="failPay" style="flex:1">模拟支付失败</button>' +
          '<button class="btn primary" data-act="payOk" style="flex:1">模拟支付成功</button>',
        title: '演示支付',
        mount: function (root, c) {
          var el = root.querySelector('#pay-left');
          if (!el) return;
          function tick() {
            if (!el.isConnected) return;
            // P-04：每秒问 Store 一次，超时即关单并刷新（不再只改文案）
            if (Store.orderExpired(o.id)) {
              el.textContent = '已超时';
              el.style.color = 'var(--text-3)';
              Store.closeOrder(o.id, '超时关闭');
              toast('订单已超时关闭（BR-ORDER-06）：库存与优惠券已释放，请重新下单', 'err');
              render();
              return;
            }
            el.textContent = UI.countdown(deadline - Date.now());
          }
          tick();
          c.every(1000, tick);
        },
        actions: {
          payOk: function () {
            if (Store.orderExpired(o.id)) {
              Store.closeOrder(o.id, '超时关闭');
              toast('订单已超时关闭（BR-ORDER-06），请重新下单', 'err');
              render();
              return;
            }
            var r = Store.payOrder(o.id, 'ok');
            if (!okToast(r)) return;
            payFailMsg = null;
            go('#/c/order/' + o.id);
          },
          failPay: function () {
            if (Store.orderExpired(o.id)) {
              Store.closeOrder(o.id, '超时关闭');
              toast('订单已超时关闭（BR-ORDER-06），请重新下单', 'err');
              render();
              return;
            }
            var r = Store.payOrder(o.id, 'fail');
            payFailMsg = r.msg || '支付失败（演示分支）';
            toast(payFailMsg, 'err');
            render();
          }
        }
      };
    }
  });/* ============================================================================
   * 九、订单相关：列表 / 详情 / 售后 / 评价
   * ==========================================================================*/
  function isFullRefunded(o) {
    return !!o && o.items.length > 0 && o.items.every(function (it) { return (it.refundedQty || 0) >= it.qty; });
  }
  function hasUnreviewed(o) {
    return !!o && o.items.some(function (it) { return (it.refundedQty || 0) < it.qty && !it.reviewed; });
  }
  function openAsOfOrder(o) {
    return Store.state.aftersales.filter(function (a) {
      return a.orderId === o.id && Store.AS_OPEN.indexOf(a.status) >= 0;
    });
  }
  function allAsOfOrder(o) {
    return Store.state.aftersales.filter(function (a) { return a.orderId === o.id; });
  }
  function orderTabOk(o, key) {
    var fr = isFullRefunded(o);
    if (key === 'all') return true;
    if (key === 'unpaid') return o.status === 'unpaid';
    if (key === 'wait_ship') return o.status === 'wait_ship';
    if (key === 'shipped') return o.status === 'shipped';
    if (key === 'review') return o.status === 'received' && !fr && hasUnreviewed(o);
    if (key === 'done') return o.status === 'done' || (o.status === 'received' && !fr && !hasUnreviewed(o));
    if (key === 'after') return openAsOfOrder(o).length > 0 || fr;
    return true;
  }

  var ORDER_TABS = [
    { key: 'all', label: '全部' },
    { key: 'unpaid', label: '待付款' },
    { key: 'wait_ship', label: '待发货' },
    { key: 'shipped', label: '待收货' },
    { key: 'review', label: '待评价' },
    { key: 'done', label: '已完成' },
    { key: 'after', label: '售后' }
  ];

  function smallBtn(text, act, id, cls) {
    return '<button class="btn sm ' + (cls || '') + '" data-act="' + act + '" data-id="' + esc(id) + '">' + esc(text) + '</button>';
  }

  function orderActs(o) {
    var fr = isFullRefunded(o);
    var b = [];
    if (o.status === 'unpaid') {
      b.push(smallBtn('取消订单', 'cancel', o.id, 'ghost'));
      b.push(smallBtn('去支付', 'pay', o.id, 'primary'));
    } else if (o.status === 'wait_ship') {
      b.push(smallBtn('查看详情', 'view', o.id, 'ghost'));
      b.push(smallBtn('仅退款说明', 'remind', o.id, ''));
    } else if (o.status === 'shipped') {
      b.push(smallBtn('查看物流', 'logistics', o.id, 'ghost'));
      b.push(smallBtn('确认收货', 'receive', o.id, 'primary'));
    } else if (o.status === 'received' || o.status === 'done') {
      if (hasUnreviewed(o)) b.push(smallBtn('去评价', 'review', o.id, 'primary'));
      b.push(smallBtn('再次购买', 'rebuy', o.id, fr ? 'primary' : 'ghost'));
      b.push(smallBtn('查看详情', 'view', o.id, 'ghost'));
    } else {
      b.push(smallBtn('再次购买', 'rebuy', o.id, 'primary'));
      b.push(smallBtn('查看详情', 'view', o.id, 'ghost'));
    }
    if (allAsOfOrder(o).length) b.push(smallBtn('查看售后', 'viewAs', o.id, 'ghost'));
    if (o.status === 'closed' || o.status === 'done' || (o.status === 'received' && !openAsOfOrder(o).length)) {
      b.push(smallBtn('删除订单', 'del', o.id, 'ghost'));
    }
    return b.join('');
  }

  function orderCard(o) {
    var fr = isFullRefunded(o);
    return '<div class="card" style="margin-bottom:10px;overflow:hidden">' +
      '<div class="pad row between" style="padding-bottom:4px;cursor:pointer" data-go="#/c/order/' + esc(o.id) + '">' +
        '<div class="tiny dim">' + esc(o.sn) + '</div>' +
        '<div>' + UI.statusTag({ status: o.status, fullyRefunded: fr }) + '</div>' +
      '</div>' +
      '<div class="pad" style="padding-top:0;padding-bottom:2px">' +
        o.items.map(function (it) { return itemRow(it); }).join('') +
      '</div>' +
      '<div class="pad row between" style="border-top:1px solid var(--line);padding-top:8px">' +
        '<div class="tiny dim">' + UI.time(o.createdAt) + ' · 共 ' + totalQty(o.items) + ' 件</div>' +
        '<div class="small">实付 ' + price(o.payAmount) + '</div>' +
      '</div>' +
      '<div class="pad row" style="justify-content:flex-end;gap:6px;padding-top:0;flex-wrap:wrap">' + orderActs(o) + '</div>' +
    '</div>';
  }

  function logisticsSheet(o) {
    if (!o) return;
    if (!o.tracking) { toast('该订单还没有物流信息（发货后可见，BR-ORD-03）', 'err'); return; }
    var rows = o.timeline.filter(function (t) { return /发货|物流|收货|收货地址/.test(t.text); });
    UI.sheet({
      title: '物流信息',
      body: '<div class="cell"><div class="label">物流公司</div><div class="value">' + esc(o.tracking.company) + '</div></div>' +
        '<div class="cell"><div class="label">运单号</div><div class="value">' + esc(o.tracking.no) + '</div></div>' +
        '<div class="mt12 small bold">物流轨迹（演示数据）</div>' +
        timelineHtml(o.timeline.slice(-4))
    });
  }

  function timelineHtml(list) {
    if (!list || !list.length) return '<div class="tiny dim">暂无进度</div>';
    var arr = list.slice().reverse();
    return '<div class="timeline">' + arr.map(function (t, i) {
      return '<div class="item on' + (i === 0 ? ' now' : '') + '">' +
        '<div class="it">' + esc(t.text) + '</div>' +
        '<div class="is">' + UI.time(t.at) + '</div>' +
      '</div>';
    }).join('') + '</div>';
  }

  function rebuyOrder(o) {
    if (!o) return;
    var n = 0, fail = 0;
    each(o.items, function (it) {
      var f = Store.skuOf(it.skuId);
      if (!f || !f.goods.onSale || Store.sellable(it.skuId) <= 0) { fail++; return; }
      var q = Math.min(it.qty, Math.max(1, Store.sellable(it.skuId)));
      var r = Store.cartAdd(it.goodsId, it.skuId, q);
      if (r && r.ok) n++; else fail++;
    });
    if (!n) { toast('商品已下架或售罄，无法再次购买（BR-GOODS-08）', 'err'); return; }
    toast('已加入购物车 ' + n + ' 种' + (fail ? ('，' + fail + ' 种不可购买') : ''), 'ok');
    go('#/c/cart');
  }

  var orderActions = {
    view: function (el) { go('#/c/order/' + el.getAttribute('data-id')); },
    pay: function (el) { go('#/c/pay/' + el.getAttribute('data-id')); },
    cancel: function (el) {
      var id = el.getAttribute('data-id');
      UI.confirm({
        title: '取消订单',
        body: '取消后订单关闭，占用的库存立即释放、已使用的优惠券退回（BR-ORDER-07/08）。',
        okText: '取消订单', cancelText: '再想想', danger: true
      }).then(function (yes) {
        if (!yes) return;
        var r = Store.cancelOrder(id);
        if (okToast(r)) render();
      });
    },
    receive: function (el) {
      var id = el.getAttribute('data-id');
      UI.confirm({ title: '确认收货', body: '确认后订单转为「已收货」，可申请售后或评价。', okText: '确认收货' })
        .then(function (yes) {
          if (!yes) return;
          var r = Store.confirmReceive(id);
          if (okToast(r)) render();
        });
    },
    logistics: function (el) { logisticsSheet(Store.orderById(el.getAttribute('data-id'))); },
    rebuy: function (el) { rebuyOrder(Store.orderById(el.getAttribute('data-id'))); },
    viewAs: function (el) { go('#/c/aftersales?orderId=' + el.getAttribute('data-id')); },
    del: function (el) {
      var id = el.getAttribute('data-id');
      UI.confirm({ title: '删除订单', body: '订单只对当前账号隐藏，后台与报表不受影响（BR-ORD-07）。', okText: '删除', danger: true })
        .then(function (yes) {
          if (!yes) return;
          var r = Store.deleteOrder(id);
          if (okToast(r)) render();
        });
    },
    review: function (el) { go('#/c/review?orderId=' + el.getAttribute('data-id')); }
  };

  /* ---------------------------- 9. 订单列表 ---------------------------- */
  C_ROUTES.push({
    path: 'orders',
    render: function (ctx) {
      var tab = ctx.query.tab || 'all';
      if (!ORDER_TABS.some(function (t) { return t.key === tab; })) tab = 'all';
      var all = Store.state.orders.filter(function (o) { return !o.hidden; });
      var counts = {};
      ORDER_TABS.forEach(function (t) {
        counts[t.key] = all.filter(function (o) { return orderTabOk(o, t.key); }).length;
      });
      var list = all.filter(function (o) { return orderTabOk(o, tab); });
      var tabLabel = (ORDER_TABS.filter(function (t) { return t.key === tab; })[0] || { label: '订单' }).label;

      var tabs = ORDER_TABS.map(function (t) {
        return { key: t.key, label: t.label, count: counts[t.key] || 0 };
      });

      var body = list.length
        ? list.map(orderCard).join('')
        : UI.empty({
            icon: '📋',
            title: tab === 'all' ? '还没有订单' : ('没有' + tabLabel + '的订单'),
            hint: '下单后可在这里查看进度、支付、申请售后',
            action: '<button class="btn primary" data-go="#/c/home">去逛逛</button>'
          });

      return {
        html: '<div class="c-page">' +
          UI.cHeader({ title: '我的订单', right: '<span data-go="#/c/me">我的</span>' }) +
          tabsMarkup(tabs, tab) +
          '<div class="c-body">' + body + '</div>' +
        '</div>',
        title: '我的订单',
        actions: {
          otab: function (el) { go('#/c/orders?tab=' + el.getAttribute('data-key')); },
          pay: orderActions.pay,
          cancel: orderActions.cancel,
          receive: orderActions.receive,
          logistics: orderActions.logistics,
          rebuy: orderActions.rebuy,
          viewAs: orderActions.viewAs,
          del: orderActions.del,
          review: orderActions.review,
          view: orderActions.view
        }
      };
    }
  });

  function tabsMarkup(items, active) {
    return UI.tabs(items, active, 'otab');
  }

  /* ---------------------------- 10. 订单详情 ---------------------------- */
  C_ROUTES.push({
    path: 'order/:id',
    render: function (ctx) {
      var o = Store.orderById(ctx.params.id);
      if (!o) return { html: notFoundC('订单不存在', '请回订单列表刷新', '#/c/orders'), title: '订单详情' };
      var fr = isFullRefunded(o);
      var deadline = o.createdAt + PAY_WINDOW;

      var statusBlock = '<div class="card pad" style="border-left:3px solid var(--brand)">' +
        '<div class="row between">' +
          '<div class="bold" style="font-size:16px">' + esc(Store.ORDER_STATUS[o.status] || o.status) + '</div>' +
          '<div>' + UI.statusTag({ status: o.status, fullyRefunded: fr }) + '</div>' +
        '</div>' +
        '<div class="small muted mt6">' + esc(statusHint(o, fr)) + '</div>' +
        (o.status === 'unpaid'
          ? '<div class="small mt8">支付剩余 <b id="od-left" style="color:var(--price);font-variant-numeric:tabular-nums">--:--</b>' +
            '<span class="tiny dim">（超时自动关单，BR-ORDER-06）</span></div>' : '') +
        (o.remark ? '<div class="tiny dim mt6">备注：' + esc(o.remark) + '</div>' : '') +
      '</div>';

      var addr = o.address
        ? '<div class="card pad mt10 row top" style="gap:10px">' +
            '<div style="color:var(--brand)">' + UI.icon('location', 20) + '</div>' +
            '<div class="grow">' +
              '<div class="small bold">' + esc(o.address.name) + ' <span class="muted">' + esc(o.address.phone) + '</span></div>' +
              '<div class="tiny dim mt6">' + addrText(o.address) + '</div>' +
            '</div>' +
          '</div>'
        : '<div class="card pad mt10 tiny dim">无收货地址快照</div>';

      var itemsBlock = '<div class="card mt10" style="overflow:hidden">' +
        '<div class="pad row between" style="padding-bottom:0">' +
          '<span class="small bold">商品</span><span class="tiny dim">共 ' + totalQty(o.items) + ' 件</span></div>' +
        '<div class="pad" style="padding-top:0">' + o.items.map(function (it) {
          var can = Store.canApply(o.id, it.id);
          var asList = Store.state.aftersales.filter(function (a) { return a.itemId === it.id; });
          return '<div style="border-bottom:1px solid var(--line)">' +
            itemRow(it) +
            '<div class="row" style="justify-content:flex-end;gap:6px;padding-bottom:8px;flex-wrap:wrap">' +
              (asList.length
                ? '<button class="btn sm ghost" data-go="#/c/aftersale/' + esc(asList[0].id) + '">查看售后</button>' : '') +
              (can.ok
                ? '<button class="btn sm" data-go="#/c/aftersale/apply?orderId=' + esc(o.id) + '&itemId=' + esc(it.id) + '">申请售后</button>'
                : '<span class="tiny dim">' + esc(can.msg) + '</span>') +
            '</div>' +
          '</div>';
        }).join('') + '</div>' +
      '</div>';

      var amountBlock = '<div class="card mt10" style="overflow:hidden">' +
        '<div class="cell"><div class="label">商品金额</div><div class="value">' + price(o.goodsAmount) + '</div></div>' +
        '<div class="cell"><div class="label">优惠券抵扣</div><div class="value">' +
          (o.couponAmount ? ('<span style="color:var(--price)">-' + price(o.couponAmount) + '</span>') : '—') + '</div></div>' +
        '<div class="cell"><div class="label">运费</div><div class="value">' + price(o.freight) + '</div></div>' +
        '<div class="cell"><div class="label bold">实付金额</div><div class="value">' + price(o.payAmount, 'price-lg') + '</div></div>' +
      '</div>';

      var infoBlock = '<div class="card mt10" style="overflow:hidden">' +
        '<div class="cell"><div class="label">订单号</div><div class="value">' + esc(o.sn) + '</div></div>' +
        '<div class="cell"><div class="label">下单时间</div><div class="value">' + UI.time(o.createdAt) + '</div></div>' +
        (o.paidAt ? '<div class="cell"><div class="label">支付时间</div><div class="value">' + UI.time(o.paidAt) + '</div></div>' : '') +
        '<div class="cell"><div class="label">支付方式</div><div class="value">' +
          (o.paidAt ? '演示支付（不产生真实扣款）' : '—') + '</div></div>' +
        (o.tracking ? '<div class="cell"><div class="label">物流</div><div class="value">' + esc(o.tracking.company) + ' ' + esc(o.tracking.no) + '</div></div>' : '') +
        (o.closedBy ? '<div class="cell"><div class="label">关闭原因</div><div class="value">' + esc(o.closedBy) + '</div></div>' : '') +
      '</div>';

      var tlBlock = '<div class="card pad mt10">' +
        '<div class="small bold" style="margin-bottom:10px">状态时间线</div>' +
        timelineHtml(o.timeline) + '</div>';

      var acts = [];
      if (o.status === 'unpaid') {
        acts.push('<button class="btn" data-act="cancel" data-id="' + esc(o.id) + '">取消订单</button>');
        acts.push('<button class="btn primary" data-act="pay" data-id="' + esc(o.id) + '">去支付</button>');
      } else if (o.status === 'wait_ship') {
        acts.push('<button class="btn ghost" data-act="contact">联系客服</button>');
        acts.push('<button class="btn" data-act="remind" data-id="' + esc(o.id) + '">仅退款说明</button>');
      } else if (o.status === 'shipped') {
        acts.push('<button class="btn ghost" data-act="logistics" data-id="' + esc(o.id) + '">查看物流</button>');
        acts.push('<button class="btn primary" data-act="receive" data-id="' + esc(o.id) + '">确认收货</button>');
      } else if (o.status === 'received' || o.status === 'done') {
        if (hasUnreviewed(o)) acts.push('<button class="btn primary" data-act="review" data-id="' + esc(o.id) + '">去评价</button>');
        acts.push('<button class="btn" data-act="rebuy" data-id="' + esc(o.id) + '">再次购买</button>');
      } else {
        acts.push('<button class="btn primary" data-act="rebuy" data-id="' + esc(o.id) + '">再次购买</button>');
      }

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '订单详情' }) +
          '<div class="c-body">' + statusBlock + addr + itemsBlock + amountBlock + infoBlock + tlBlock +
            '<div class="tiny dim center" style="padding:12px 0">主状态与售后单状态分离展示（BR-ORD-08）</div>' +
          '</div>' +
        '</div>',
        action: '<div class="sum">实付 <span>' + price(o.payAmount) + '</span></div>' + acts.join(''),
        title: '订单详情',
        mount: function (root, c) {
          /* P-04：未付款且已超时 → 立即按「超时关闭」落库再刷新；判定只认 Store.orderExpired */
          if (o.status === 'unpaid' && Store.orderExpired(o.id)) {
            if (Store.closeOrder(o.id, '超时关闭').ok) toast('订单超过 30 分钟未付款，已按超时关闭（BR-ORDER-06）', 'err');
            render();
            return;
          }
          var el = root.querySelector('#od-left');
          if (!el) return;
          function tick() {
            if (!el.isConnected) return;
            if (Store.orderExpired(o.id)) {
              if (Store.closeOrder(o.id, '超时关闭').ok) toast('订单超过 30 分钟未付款，已按超时关闭（BR-ORDER-06）', 'err');
              render();
              return;
            }
            el.textContent = UI.countdown(deadline - Date.now());
          }
          tick();
          c.every(1000, tick);
        },
        actions: mix(orderActions, {
          contact: function () { toast('客服为占位入口（需求未定义会话能力）'); },
          remind: function () {
            toast('待发货订单只能申请「仅退款」（BR-AS-03），请点商品行右侧「申请售后」', '');
          }
        })
      };
    }
  });

  function statusHint(o, fr) {
    if (fr) return '全部订单行已退款完成，订单打「已全额退款」标记（BR-AS-18）';
    switch (o.status) {
      case 'unpaid': return '等待付款，30 分钟内未支付将自动关闭并释放库存。';
      case 'wait_ship': return '商家正在备货，发货后可查看物流。';
      case 'shipped': return '商品运输中，收到货后请点击「确认收货」。';
      case 'received': return hasUnreviewed(o) ? '已收货，评价后会转入「已完成」。' : '已收货，售后期内仍可申请售后。';
      case 'done': return '订单已完成，感谢你的支持。';
      case 'closed': return '订单已关闭' + (o.closedBy ? '（' + o.closedBy + '）' : '') + '，库存与优惠券已释放。';
      default: return '';
    }
  }

  /* ---------------------------- 11. 申请售后 ---------------------------- */
  C_ROUTES.push({
    path: 'aftersale/apply',
    render: function (ctx) {
      var orderId = ctx.query.orderId || '';
      var itemId = ctx.query.itemId || '';
      var can = Store.canApply(orderId, itemId);
      if (!can.ok) {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '申请售后' }) +
            UI.empty({
              icon: '🚫', title: '当前无法申请售后', hint: esc(can.msg),
              action: '<button class="btn primary" data-go="' + esc(orderId ? '#/c/order/' + orderId : '#/c/orders') + '">返回订单</button>'
            }) + '</div>',
          title: '申请售后'
        };
      }

      if (!asDraft || asDraft.orderId !== orderId || asDraft.itemId !== itemId) {
        asDraft = { orderId: orderId, itemId: itemId, type: can.types[0], qty: 1, reason: '', note: '', photos: [] };
      }
      if (can.types.indexOf(asDraft.type) < 0) asDraft.type = can.types[0];
      asDraft.qty = Math.min(Math.max(1, asDraft.qty), can.maxQty);

      var it = can.item;
      var typeBlock = '<div class="card mt10" style="overflow:hidden">' +
        can.types.map(function (t) {
          var on = asDraft.type === t;
          return '<div class="cell clickable" data-act="type" data-v="' + t + '">' +
            '<div class="label' + (on ? ' bold' : '') + '">' + esc(Store.REFUND_TYPES[t]) + '</div>' +
            '<div class="value row" style="gap:8px">' +
              '<span class="tiny dim">' + (t === 'return' ? '需寄回商品，商家收货后退款' : '无需寄回，商家同意后直接退款') + '</span>' +
              '<span class="cbx' + (on ? ' on' : '') + '"></span>' +
            '</div>' +
          '</div>';
        }).join('') +
      '</div>';

      var qtyBlock = '<div class="card pad mt10 row between">' +
        '<div><div class="small bold">申请数量</div>' +
          '<div class="tiny dim mt6">最多可申请 ' + can.maxQty + ' 件 · 行实付 ' + price(can.itemPaid) + '</div></div>' +
        UI.stepper(asDraft.qty, 1, can.maxQty) +
      '</div>';

      var reasonBlock = '<div class="card pad mt10">' +
        '<div class="small bold" style="margin-bottom:8px">售后原因</div>' +
        '<div class="spec-opts">' + Store.AS_REASONS.map(function (r) {
          return '<div class="spec-opt' + (asDraft.reason === r ? ' on' : '') + '" data-act="reason" data-v="' + esc(r) + '">' + esc(r) + '</div>';
        }).join('') + '</div>' +
      '</div>';

      var noteBlock = '<div class="card pad mt10">' +
        '<div class="small bold" style="margin-bottom:6px">补充说明<span class="tiny dim" style="font-weight:400">（选填）</span></div>' +
        '<textarea class="textarea" id="as-note" maxlength="200" placeholder="描述一下问题，便于商家处理">' + esc(asDraft.note) + '</textarea>' +
      '</div>';

      var photoBlock = '<div class="card pad mt10">' +
        '<div class="row between" style="margin-bottom:8px">' +
          '<div class="small bold">上传凭证</div>' +
          '<div class="tiny dim">' + asDraft.photos.length + '/6' +
            (asDraft.type === 'return' ? ' · 退货退款至少 1 张（BR-AS-21）' : ' · 选填') + '</div>' +
        '</div>' +
        UI.uploads(asDraft.photos.length, 6) +
        (asDraft.photos.length ? '<div class="tiny dim mt6"><span data-act="clearPhotos" style="color:var(--brand)">清空凭证</span></div>' : '') +
      '</div>';

      var refundPreview = '<div class="card pad mt10 row between">' +
        '<div class="small bold">预计退款金额</div>' +
        '<div>' + price(Store.refundAmount(orderId, itemId, asDraft.qty)) + '</div>' +
      '</div>';

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '申请售后' }) +
          '<div class="c-body">' +
            '<div class="card pad">' + itemRow(it) + '</div>' +
            typeBlock + qtyBlock + reasonBlock + noteBlock + photoBlock + refundPreview +
            '<div class="alert mt12">' + UI.icon('warn', 15) +
              '<div>同一订单行同时只能有 1 个进行中的售后（BR-AS-04）；退款金额按行实付 × 申请数量计算（BR-AS-05）。</div></div>' +
          '</div>' +
        '</div>',
        action: '<div class="sum">退款 <span>' + price(Store.refundAmount(orderId, itemId, asDraft.qty)) + '</span></div>' +
          '<button class="btn primary" data-act="submit">提交申请</button>',
        title: '申请售后',
        actions: {
          type: function (el) {
            var n = document.getElementById('as-note');
            if (n) asDraft.note = n.value;
            asDraft.type = el.getAttribute('data-v');
            render();
          },
          reason: function (el) {
            var n = document.getElementById('as-note');
            if (n) asDraft.note = n.value;
            asDraft.reason = el.getAttribute('data-v');
            render();
          },
          upload: function () {
            if (asDraft.photos.length >= 6) { toast('最多上传 6 张凭证', 'err'); return; }
            asDraft.photos.push('凭证图' + (asDraft.photos.length + 1));
            toast('已添加凭证（演示占位图）', 'ok');
            render();
          },
          clearPhotos: function () { asDraft.photos = []; render(); },
          step: function (el) {
            var q = asDraft.qty + (el.getAttribute('data-step') === '+' ? 1 : -1);
            if (q < 1) { toast('申请数量至少 1 件', 'err'); return; }
            if (q > can.maxQty) { toast('最多可申请 ' + can.maxQty + ' 件', 'err'); return; }
            asDraft.qty = q;
            render();
          },
          submit: function () {
            var noteEl = document.getElementById('as-note');
            var r = Store.aftersaleApply({
              orderId: asDraft.orderId, itemId: asDraft.itemId, type: asDraft.type,
              qty: asDraft.qty, reason: asDraft.reason,
              note: noteEl ? noteEl.value : asDraft.note,
              photos: asDraft.photos.slice()
            });
            if (!okToast(r)) return;
            asDraft = null;
            go('#/c/aftersale/' + r.id);
          }
        }
      };
    }
  });

  /* ---------------------------- 12. 售后详情 ---------------------------- */
  C_ROUTES.push({
    path: 'aftersale/:id',
    render: function (ctx) {
      var a = Store.asById(ctx.params.id);
      if (!a) return { html: notFoundC('售后单不存在', '回我的售后列表看看', '#/c/aftersales'), title: '售后详情' };
      var order = Store.orderById(a.orderId);

      var MAIN_FLOW = ['apply', 'wait_send', 'wait_receive', 'refunding', 'done'];
      var TERM_FLOW = ['closed', 'rejected', 'canceled'];
      var isTerm = TERM_FLOW.indexOf(a.status) >= 0;

      function flowTag(k) {
        var on = k === a.status;
        var skipped = (a.type === 'refund' && (k === 'wait_send' || k === 'wait_receive'));
        var style = on ? '' : ('opacity:' + (skipped ? '.3' : '.5'));
        var tip = skipped ? ' title="仅退款流程跳过该步骤"' : '';
        return '<span class="tag ' + (on ? 'danger' : 'gray') + '" style="' + style + '"' + tip + '>' +
          esc(Store.AS_STATUS[k]) + (skipped ? '（跳过）' : '') + '</span>';
      }

      var strip = '<div class="card pad">' +
        '<div class="row between"><div class="bold">' + esc(Store.AS_STATUS[a.status] || a.status) + '</div>' +
          '<div>' + UI.asTag({ status: a.status, restockPending: a.restockPending }) + '</div></div>' +
        '<div class="row mt10" style="flex-wrap:wrap;gap:4px;align-items:center">' +
          MAIN_FLOW.map(function (k) { return flowTag(k); }).join('<span class="tiny dim">›</span>') +
        '</div>' +
        '<div class="row mt6" style="flex-wrap:wrap;gap:6px;align-items:center">' +
          '<span class="tiny dim">其他终态</span>' + TERM_FLOW.map(flowTag).join('') +
        '</div>' +
        (isTerm ? '<div class="tiny dim mt6">当前处于终态，售后单不再流转。</div>' : '') +
        '<div class="tiny dim mt8">售后单号 ' + esc(a.sn) + ' · 提交于 ' + UI.time(a.createdAt) + '</div>' +
      '</div>';

      var pending = (a.status === 'refunding' && a.restockPending)
        ? '<div class="mt10">' + UI.alert('<b>库存待回补</b><br>退款已处理但库存回补失败（规格可能被删除），需在后台「库存视图」或售后单上点「重试回补」（BR-AS-12/16）。', 'err') + '</div>'
        : '';

      var itemBlock = '<div class="card pad mt10">' +
        '<div class="row between" style="margin-bottom:2px"><div class="small bold">' + esc(Store.REFUND_TYPES[a.type] || a.type) + '</div>' +
          '<div class="tiny dim">' + esc(a.reason) + '</div></div>' +
        itemRow({ name: a.name, specText: a.specText, emoji: a.emoji, bg: a.bg, price: Math.round(a.amount / Math.max(1, a.qty)), qty: a.qty }) +
        '<div class="row between" style="border-top:1px solid var(--line);padding-top:8px">' +
          '<span class="small">退款金额</span>' + price(a.amount, 'price-lg') + '</div>' +
        (a.note ? '<div class="tiny dim mt6">说明：' + esc(a.note) + '</div>' : '') +
        (a.photos && a.photos.length ? '<div class="tiny dim mt6">凭证：' + a.photos.length + ' 张（' +
          a.photos.map(function (p) { return esc(p); }).join('、') + '）</div>' : '') +
      '</div>';

      var addrBlock = a.returnAddress
        ? '<div class="card pad mt10">' +
            '<div class="small bold" style="margin-bottom:6px">退货地址</div>' +
            '<div class="small">' + esc(a.returnAddress.name) + ' ' + esc(a.returnAddress.phone) + '</div>' +
            '<div class="tiny dim mt6">' + esc(a.returnAddress.region) + ' ' + esc(a.returnAddress.detail) + '</div>' +
          '</div>'
        : '';

      var trackBlock = a.trackingNo
        ? '<div class="card pad mt10 row between"><span class="small bold">退货物流单号</span>' +
            '<span class="small">' + esc(a.trackingNo) + '</span></div>'
        : '';

      var tlBlock = '<div class="card pad mt10">' +
        '<div class="small bold" style="margin-bottom:10px">处理进度</div>' +
        timelineHtml(a.timeline) + '</div>';

      var acts = [];
      if (a.status === 'apply') {
        acts.push('<button class="btn" data-act="cancelAs" data-id="' + esc(a.id) + '">撤销申请</button>');
      } else if (a.status === 'wait_send') {
        acts.push('<button class="btn primary" data-act="fillTrack" data-id="' + esc(a.id) + '">填写退货单号</button>');
      } else if (a.status === 'wait_receive') {
        acts.push('<button class="btn" data-act="showTrack">查看物流</button>');
      } else if (a.status === 'refunding') {
        acts.push('<button class="btn" data-act="retryHint">等待商家退款</button>');
      } else {
        acts.push('<button class="btn" data-act="toOrder" data-id="' + esc(a.orderId) + '">查看订单</button>');
      }
      acts.push('<button class="btn ghost" data-act="toOrder" data-id="' + esc(a.orderId) + '">订单</button>');

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '售后详情' }) +
          '<div class="c-body">' + strip + pending + itemBlock + addrBlock + trackBlock + tlBlock +
            '<div class="tiny dim center" style="padding:12px 0">售后单状态独立于订单主状态（BR-ORD-08）</div>' +
          '</div>' +
        '</div>',
        action: '<div class="sum">退款 ' + price(a.amount) + '</div>' + acts.join(''),
        title: '售后详情',
        actions: {
          cancelAs: function (el) {
            var id = el.getAttribute('data-id');
            UI.confirm({ title: '撤销申请', body: '撤销后该订单行可重新申请售后（BR-AS-11）。', okText: '撤销', danger: true })
              .then(function (yes) {
                if (!yes) return;
                var r = Store.asCancel(id);
                if (okToast(r)) render();
              });
          },
          fillTrack: function (el) {
            var id = el.getAttribute('data-id');
            UI.sheet({
              title: '填写退货物流单号',
              body: '<div class="field"><div class="label">物流单号</div>' +
                '<input class="input" id="as-track" placeholder="例如 SF1234567890" /></div>' +
                '<div class="tiny dim mt6">填写后状态转为「待收货」，商家收货后退款（BR-AS-08）。</div>',
              actions: [
                { text: '取消' },
                {
                  text: '提交', type: 'primary', onClick: function () {
                    var inp = document.getElementById('as-track');
                    var r = Store.asFillTracking(id, inp ? inp.value : '');
                    if (!okToast(r)) return false;
                    render();
                  }
                }
              ]
            });
          },
          showTrack: function () {
            UI.sheet({
              title: '退货物流',
              body: '<div class="cell"><div class="label">物流单号</div><div class="value">' + esc(a.trackingNo || '未填写') + '</div></div>' +
                '<div class="tiny dim mt8">等待商家确认收货，之后进入退款处理。</div>'
            });
          },
          retryHint: function () {
            toast(a.restockPending ? '库存回补失败，需运营在后台点「重试回补」' : '已提交退款，等待运营处理', '');
          },
          toOrder: function (el) { go('#/c/order/' + el.getAttribute('data-id')); }
        }
      };
    }
  });

  /* ---------------------------- 13. 我的售后 ---------------------------- */
  var AS_FILTERS = [
    { key: 'all', label: '全部' },
    { key: 'open', label: '处理中' },
    { key: 'done', label: '已完成' },
    { key: 'closed', label: '已关闭' }
  ];

  C_ROUTES.push({
    path: 'aftersales',
    render: function (ctx) {
      var f = ctx.query.status || 'all';
      if (!AS_FILTERS.some(function (x) { return x.key === f; })) f = 'all';
      var orderId = ctx.query.orderId || '';
      var all = Store.state.aftersales.slice();
      if (orderId) all = all.filter(function (a) { return a.orderId === orderId; });
      var list = all.filter(function (a) {
        if (f === 'all') return true;
        if (f === 'open') return Store.AS_OPEN.indexOf(a.status) >= 0;
        if (f === 'done') return a.status === 'done';
        return Store.AS_TERMINAL.indexOf(a.status) >= 0 && a.status !== 'done';
      });

      var tabs = AS_FILTERS.map(function (x) {
        return {
          key: x.key, label: x.label,
          count: all.filter(function (a) {
            if (x.key === 'all') return true;
            if (x.key === 'open') return Store.AS_OPEN.indexOf(a.status) >= 0;
            if (x.key === 'done') return a.status === 'done';
            return Store.AS_TERMINAL.indexOf(a.status) >= 0 && a.status !== 'done';
          }).length
        };
      });

      var body = list.length
        ? list.map(function (a) {
            return '<div class="card pad" style="margin-bottom:10px" data-go="#/c/aftersale/' + esc(a.id) + '">' +
              '<div class="row between" style="margin-bottom:4px">' +
                '<div class="tiny dim">' + esc(a.sn) + '</div>' +
                '<div>' + UI.asTag(a) + '</div></div>' +
              itemRow({ name: a.name, specText: a.specText, emoji: a.emoji, bg: a.bg, price: Math.round(a.amount / Math.max(1, a.qty)), qty: a.qty }) +
              '<div class="row between" style="border-top:1px solid var(--line);padding-top:8px">' +
                '<span class="tiny dim">' + esc(Store.REFUND_TYPES[a.type] || a.type) + ' · ' + UI.time(a.createdAt) + '</span>' +
                '<span class="small">退款 ' + price(a.amount) + '</span></div>' +
            '</div>';
          }).join('')
        : UI.empty({
            icon: '🧾', title: '没有售后记录',
            hint: '已收货/待发货的订单行可申请售后；同一订单行同时只能有 1 个进行中的售后',
            action: '<button class="btn primary" data-go="#/c/orders">去订单列表</button>'
          });

      return {
        html: '<div class="c-page">' +
          UI.cHeader({ title: '我的售后' }) +
          tabsMarkup(tabs, f) +
          '<div class="c-body">' + body + '</div>' +
        '</div>',
        title: '我的售后',
        actions: {
          otab: function (el) {
            var q = '#/c/aftersales?status=' + el.getAttribute('data-key');
            if (orderId) q += '&orderId=' + encodeURIComponent(orderId);
            go(q);
          }
        }
      };
    }
  });

  /* ---------------------------- 14. 评价 ---------------------------- */
  C_ROUTES.push({
    path: 'review',
    render: function (ctx) {
      var orderId = ctx.query.orderId || '';
      if (!orderId) {
        var pend = Store.state.orders.filter(function (o) {
          return !o.hidden && o.status === 'received' && !isFullRefunded(o) && hasUnreviewed(o);
        });
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '待评价' }) +
            '<div class="c-body">' + (pend.length
              ? pend.map(function (o) {
                  return '<div class="card pad" style="margin-bottom:10px" data-go="#/c/review?orderId=' + esc(o.id) + '">' +
                    '<div class="row between"><div class="tiny dim">' + esc(o.sn) + '</div>' +
                      '<div class="tiny dim">' + UI.time(o.receivedAt || o.createdAt) + '</div></div>' +
                    o.items.filter(function (it) { return (it.refundedQty || 0) < it.qty && !it.reviewed; })
                      .map(function (it) { return itemRow(it); }).join('') +
                    '<div class="row" style="justify-content:flex-end"><button class="btn sm primary">去评价</button></div>' +
                  '</div>';
                }).join('')
              : UI.empty({
                  icon: '⭐', title: '没有待评价的商品',
                  hint: '确认收货后可以评价；已退款的行不可评价（BR-AS-17）',
                  action: '<button class="btn primary" data-go="#/c/orders?tab=review">看待评价订单</button>'
                })) + '</div>' +
          '</div>',
          title: '待评价'
        };
      }

      var o = Store.orderById(orderId);
      if (!o) return { html: notFoundC('订单不存在', '', '#/c/orders'), title: '评价' };
      var rows = o.items.filter(function (it) { return (it.refundedQty || 0) < it.qty && !it.reviewed; });
      if (!rows.length) {
        return {
          html: '<div class="c-page">' + UI.cHeader({ title: '评价' }) +
            UI.empty({
              icon: '✅', title: '这个订单没有可评价的商品',
              hint: '已全部评价或商品已退款退货',
              action: '<button class="btn primary" data-go="#/c/order/' + esc(o.id) + '">返回订单</button>'
            }) + '</div>',
          title: '评价'
        };
      }

      each(rows, function (it) { if (!reviewRate[it.id]) reviewRate[it.id] = 5; });

      var body = rows.map(function (it) {
        var rate = reviewRate[it.id] || 5;
        return '<div class="card pad" style="margin-bottom:10px" data-rv="' + esc(it.id) + '">' +
          itemRow(it) +
          '<div class="row mt10" style="gap:4px;align-items:center">' +
            '<span class="tiny dim">评分</span>' +
            [1, 2, 3, 4, 5].map(function (n) {
              return '<span data-act="rate" data-item="' + esc(it.id) + '" data-v="' + n + '" ' +
                'style="font-size:22px;cursor:pointer;color:' + (n <= rate ? '#ffb400' : '#e3e6ec') + '">★</span>';
            }).join('') +
            '<span class="tiny dim">' + rate + ' 分</span>' +
          '</div>' +
          '<textarea class="textarea mt8" id="rv-' + esc(it.id) + '" maxlength="200" placeholder="说说使用感受（选填）">' + esc(reviewText[it.id] || '') + '</textarea>' +
          '<div class="mt8">' + UI.uploads(0, 3) + '</div>' +
        '</div>';
      }).join('');

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '评价', right: '<span data-go="#/c/order/' + esc(o.id) + '">订单</span>' }) +
          '<div class="c-body">' + body +
            '<div class="alert mt12">' + UI.icon('star', 15) +
              '<div>所有行评价完成后订单转「已完成」（BR-ORD-09）；已退款行不参与评价判定。</div></div>' +
          '</div>' +
        '</div>',
        action: '<div class="sum">共 ' + rows.length + ' 行待评价</div>' +
          '<button class="btn primary" data-act="submitReview">提交评价</button>',
        title: '评价',
        mount: function (root) {
          each(rows, function (it) {
            var el = root.querySelector('#rv-' + it.id);
            if (!el) return;
            el.addEventListener('input', function () { reviewText[it.id] = el.value; });
          });
        },
        actions: {
          rate: function (el) {
            reviewRate[el.getAttribute('data-item')] = Number(el.getAttribute('data-v')) || 5;
            render();
          },
          upload: function () { toast('评价图片为演示占位，不实际上传'); },
          submitReview: function () {
            var list = rows.map(function (it) {
              var el = document.getElementById('rv-' + it.id);
              return {
                itemId: it.id,
                rate: reviewRate[it.id] || 5,
                content: el ? el.value : ''
              };
            });
            var r = Store.reviewSubmit(o.id, list);
            if (!okToast(r)) return;
            reviewRate = {};
            reviewText = {};
            var o2 = Store.orderById(o.id);
            if (o2 && o2.status === 'received' && hasUnreviewed(o2)) go('#/c/review?orderId=' + o.id);
            else go('#/c/order/' + o.id);
          }
        }
      };
    }
  });

  /* ---------------------------- 15. 我的 ---------------------------- */
  C_ROUTES.push({
    path: 'me',
    render: function () {
      var st = Store.state;
      var orders = st.orders.filter(function (o) { return !o.hidden; });
      var cnt = {
        unpaid: orders.filter(function (o) { return o.status === 'unpaid'; }).length,
        wait_ship: orders.filter(function (o) { return o.status === 'wait_ship'; }).length,
        shipped: orders.filter(function (o) { return o.status === 'shipped'; }).length,
        review: orders.filter(function (o) { return o.status === 'received' && !isFullRefunded(o) && hasUnreviewed(o); }).length,
        after: st.aftersales.filter(function (a) { return Store.AS_OPEN.indexOf(a.status) >= 0; }).length
      };
      var shortcuts = [
        { key: 'unpaid', label: '待付款', icon: 'money', n: cnt.unpaid },
        { key: 'wait_ship', label: '待发货', icon: 'box', n: cnt.wait_ship },
        { key: 'shipped', label: '待收货', icon: 'truck', n: cnt.shipped },
        { key: 'review', label: '待评价', icon: 'star', n: cnt.review },
        { key: 'after', label: '售后', icon: 'refresh', n: cnt.after }
      ];
      var mine = Store.myCoupons();
      var unused = mine.filter(function (x) { return x.status === 'unused'; }).length;
      var tools = [
        { label: '收藏', icon: 'star', n: Store.favGoods().length, href: '#/c/favorites' },
        { label: '关注店铺', icon: 'shop', n: Store.followList().length, href: '#/c/follows' },
        { label: '足迹', icon: 'clock', n: Store.footprintList().length, href: '#/c/footprints' },
        { label: '我的优惠券', icon: 'tag', n: unused, href: '#/c/coupons' }
      ];
      var toolsHtml = '<div class="x-tools" style="margin-top:-12px">' + tools.map(function (t) {
        return '<div class="x-tool" data-go="' + t.href + '">' +
          '<div class="ic">' + UI.icon(t.icon, 21) +
            (t.n ? '<i class="n">' + (t.n > 99 ? '99+' : t.n) + '</i>' : '') + '</div>' +
          '<div class="tiny mt6">' + esc(t.label) + '</div></div>';
      }).join('') + '</div>';

      var html = '<div class="c-page">' +
        '<div class="c-head" style="background:linear-gradient(135deg,#ff5a1f,#ff2e4d);color:#fff;border:0">' +
          '<div style="width:30px"></div><div class="title" style="color:#fff">我的</div><div class="right"></div>' +
        '</div>' +
        '<div style="background:linear-gradient(135deg,#ff5a1f,#ff2e4d);color:#fff;padding:0 16px 22px">' +
          '<div class="row" style="gap:12px">' +
            '<div style="width:54px;height:54px;border-radius:50%;background:#ffffff33;display:grid;place-items:center;font-size:26px">🧑</div>' +
            '<div class="grow"><div class="bold" style="font-size:16px">' + esc(st.user.nick) + '</div>' +
              '<div class="tiny" style="opacity:.85;margin-top:4px">' + esc(st.user.phone) + ' · 演示账号</div></div>' +
            '<span class="tag" style="background:#ffffff2e;color:#fff">Lv.2 会员</span>' +
          '</div>' +
          '<div class="row mt12" style="gap:16px;font-size:12px;opacity:.92">' +
            '<span>订单 ' + orders.length + '</span><span>可用券 ' + unused + ' 张</span>' +
            '<span>积分 1280</span></div>' +
        '</div>' +
        '<div class="c-body" style="padding-top:0">' +
          toolsHtml +
          '<div class="card pad mt10">' +
            '<div class="row between"><span class="small bold">我的订单</span>' +
              '<span class="tiny dim" data-go="#/c/orders">全部订单 ›</span></div>' +
            '<div class="row mt12" style="justify-content:space-between">' +
              shortcuts.map(function (s) {
                var href = s.key === 'after' ? '#/c/aftersales' : '#/c/orders?tab=' + s.key;
                return '<div data-go="' + href + '" style="text-align:center;flex:1;position:relative">' +
                  '<div style="color:var(--text-2)">' + UI.icon(s.icon, 22) + '</div>' +
                  '<div class="tiny mt6">' + esc(s.label) + '</div>' +
                  (s.n ? '<span style="position:absolute;top:-4px;right:12px;min-width:15px;height:15px;line-height:15px;' +
                    'background:var(--price);color:#fff;border-radius:999px;font-size:10px;padding:0 3px">' + s.n + '</span>' : '') +
                '</div>';
              }).join('') +
            '</div>' +
          '</div>' +
          '<div class="card mt10" style="overflow:hidden">' +
            '<div class="cell clickable" data-go="#/c/coupons"><div class="label">我的优惠券</div>' +
              '<div class="value">' + unused + ' 张可用 ' + UI.icon('right', 14) + '</div></div>' +
            '<div class="cell clickable" data-go="#/c/address"><div class="label">收货地址</div>' +
              '<div class="value">' + st.addresses.length + ' 个 ' + UI.icon('right', 14) + '</div></div>' +
            '<div class="cell clickable" data-go="#/c/review"><div class="label">我的评价</div>' +
              '<div class="value">' + st.reviews.length + ' 条 ' + UI.icon('right', 14) + '</div></div>' +
            '<div class="cell clickable" data-go="#/c/aftersales"><div class="label">我的售后</div>' +
              '<div class="value">' + st.aftersales.length + ' 单 ' + UI.icon('right', 14) + '</div></div>' +
          '</div>' +
          '<div class="card pad mt10">' +
            '<div class="row between"><span class="small bold">演示模式说明</span>' +
              '<span class="tag warn">不接后端</span></div>' +
            '<div class="tiny dim mt8" style="line-height:1.8">' +
              '· 所有数据保存在本机浏览器（localStorage），刷新不丢；<br>' +
              '· 支付只有「模拟成功 / 模拟失败」两个手段，不产生真实扣款；<br>' +
              '· 金额单位一律「分」，展示时统一格式化；<br>' +
              '· 需要从零演示时，点顶栏「重置演示数据」即可恢复种子数据。' +
            '</div>' +
            '<div class="row mt10" style="gap:8px">' +
              '<button class="btn sm ghost" data-go="#/admin/demo">演示工具（后台）</button>' +
              '<button class="btn sm ghost" data-act="resetDemo">重置演示数据</button>' +
            '</div>' +
          '</div>' +
          '<div class="tiny dim center" style="padding:14px 0">小满商城 · 交互原型 v3 · 仅用于交互评审</div>' +
        '</div>' +
      '</div>';

      return { html: html, tab: 'me', title: '我的' };
    }
  });

  /* ---------------------------- 15b. 我的收藏 ---------------------------- */
  C_ROUTES.push({
    path: 'favorites',
    render: function () {
      var list = Store.favGoods();
      var body = list.length
        ? '<div class="goods-grid">' + list.map(function (g) {
            return '<div class="x-fav">' + goodsCard(g) +
              '<button class="x-fav-del" data-act="unfav" data-id="' + esc(g.id) + '">取消收藏</button>' +
            '</div>';
          }).join('') + '</div>'
        : UI.empty({
            icon: '⭐', title: '还没有收藏商品',
            hint: '在商品详情页点「收藏」，就能在这里快速找回',
            action: '<button class="btn primary" data-go="#/c/home">去逛逛</button>'
          });
      return {
        html: '<div class="c-page">' +
          UI.cHeader({ title: '我的收藏', right: '共 ' + list.length + ' 件' }) +
          '<div class="c-body">' + body + '</div></div>',
        title: '我的收藏',
        actions: {
          unfav: function (el) {
            okToast(Store.toggleFav(el.getAttribute('data-id')));
            render();
          }
        }
      };
    }
  });

  /* ---------------------------- 15c. 关注店铺 / 品牌 ---------------------------- */
  C_ROUTES.push({
    path: 'follows',
    render: function () {
      var shopOn = Store.isFollow('shop', 'shop1');
      var brandRows = (Store.state.brands || []).map(function (b) {
        var n = Store.state.goods.filter(function (g) { return g.brandId === b.id && g.onSale; }).length;
        return { b: b, n: n };
      });
      var folBrands = Store.followList().filter(function (f) { return f.type === 'brand'; }).length;
      function followBtn(type, id, on) {
        return '<button class="btn sm ' + (on ? 'ghost' : 'primary') + '" data-act="follow" ' +
          'data-type="' + type + '" data-id="' + esc(id) + '">' + (on ? '已关注' : '+ 关注') + '</button>';
      }
      var body = '<div class="x-shopcard">' +
          '<div class="avatar">🏪</div>' +
          '<div class="grow" style="min-width:0"><div class="bold">小满商城官方店</div>' +
            '<div class="tiny dim mt6">自营 · 全品类 · 在售 ' + Store.goodsList({}).length + ' 件</div></div>' +
          followBtn('shop', 'shop1', shopOn) +
        '</div>' +
        '<div class="row between mt16"><div class="small bold">品牌（' + brandRows.length + ' 个）</div>' +
          '<div class="tiny dim">已关注品牌 ' + folBrands + ' 个</div></div>' +
        brandRows.map(function (x) {
          return '<div class="x-follow-row">' +
            '<div class="grow" style="min-width:0"><div class="small bold">' + esc(x.b.name) + '</div>' +
              '<div class="tiny dim mt6">在售 ' + x.n + ' 件</div></div>' +
            followBtn('brand', x.b.id, Store.isFollow('brand', x.b.id)) +
          '</div>';
        }).join('');
      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '关注店铺 / 品牌' }) +
          '<div class="c-body">' + body + '</div></div>',
        title: '关注店铺',
        actions: {
          follow: function (el) {
            okToast(Store.toggleFollow(el.getAttribute('data-type'), el.getAttribute('data-id')));
            render();
          }
        }
      };
    }
  });

  /* ---------------------------- 15d. 浏览足迹 ---------------------------- */
  function fpGroupName(at) {
    var now = new Date();
    var today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    if (at >= today) return '今天';
    if (at >= today - 86400000) return '昨天';
    return '更早';
  }
  C_ROUTES.push({
    path: 'footprints',
    render: function () {
      var list = Store.footprintList();
      var groups = ['今天', '昨天', '更早'].map(function (name) {
        var arr = list.filter(function (x) { return fpGroupName(x.at) === name; });
        if (!arr.length) return '';
        return '<div class="x-fp-group"><div class="tiny dim" style="padding:0 2px 8px">' +
          name + '（' + arr.length + '）</div>' +
          arr.map(function (x) {
            var g = x.goods;
            return '<div class="card pad x-fp" data-go="#/c/goods/' + esc(g.id) + '">' +
              UI.thumb(g.pics[0], g.pics[1], 'sm') +
              '<div class="grow" style="min-width:0">' +
                '<div class="small bold clamp2">' + esc(g.name) + '</div>' +
                '<div class="row between mt8">' + price(Store.displayPrice(g), 'price-md') +
                  '<span class="tiny dim">' + UI.time(x.at) + '</span></div>' +
              '</div></div>';
          }).join('') + '</div>';
      }).join('');
      var body = list.length ? groups
        : UI.empty({
            icon: '👣', title: '还没有浏览记录',
            hint: '看过的商品会按「今天 / 昨天 / 更早」自动分组，最多保留 30 件',
            action: '<button class="btn primary" data-go="#/c/home">去逛逛</button>'
          });
      return {
        html: '<div class="c-page">' +
          UI.cHeader({
            title: '浏览足迹',
            right: list.length ? '<span data-act="clear">清空</span>' : '<span class="dim">共 0 件</span>'
          }) +
          '<div class="c-body">' + body + '</div></div>',
        title: '浏览足迹',
        actions: {
          clear: function () {
            UI.confirm({ title: '清空足迹', body: '将清空本机的全部浏览记录（最多 30 件），此操作不可撤销。', danger: true, okText: '清空' })
              .then(function (ok) {
                if (!ok) return;
                okToast(Store.clearFootprints());
                render();
              });
          }
        }
      };
    }
  });

  /* ---------------------------- 16. 优惠券 ---------------------------- */
  var COUPON_TAB = 'center';
  var MYCP_TAB = 'unused';

  C_ROUTES.push({
    path: 'coupons',
    render: function (ctx) {
      if (ctx.query.tab === 'mine') COUPON_TAB = 'mine';
      if (ctx.query.tab === 'center') COUPON_TAB = 'center';
      if (ctx.query.sub) MYCP_TAB = ctx.query.sub;

      var mine = Store.myCoupons();
      var mineIds = mine.map(function (x) { return x.c ? x.c.id : ''; });

      var centerRows = Store.state.coupons.map(function (c) {
        var got = mineIds.indexOf(c.id) >= 0;
        var used = mine.some(function (x) { return x.c && x.c.id === c.id && x.status === 'used'; });
        var full = c.claimed >= c.total;
        var expired = Date.now() > c.expireAt;
        var left = Math.max(0, c.total - c.claimed);
        var btn, cls;
        if (c.disabled) { btn = '已停用'; cls = 'ghost'; }
        else if (expired) { btn = '已过期'; cls = 'ghost'; }
        else if (got) { btn = used ? '已使用' : '已领取'; cls = 'ghost'; }
        else if (full) { btn = '已领完'; cls = 'ghost'; }
        else { btn = '立即领取'; cls = 'primary'; }
        return '<div class="card pad" style="margin-bottom:10px">' +
          '<div class="row between">' +
            '<div class="grow">' +
              '<div class="bold" style="font-size:14px">' + esc(c.name) + '</div>' +
              '<div class="tiny dim mt6">满 ' + UI.yuan(c.threshold) + ' 可用 · 有效期至 ' + UI.time(c.expireAt, false) + '</div>' +
              '<div class="tiny dim mt6">剩余 ' + left + ' / ' + c.total + ' 张' +
                (left > 0 && left <= 5 ? ' <span class="tag warn">仅剩 ' + left + ' 张</span>' : '') +
                (got ? ' <span class="tag ok">' + (used ? '已使用' : '已领取') + '</span>' : '') +
                (full && !got ? ' <span class="tag danger">已领完</span>' : '') +
              '</div>' +
            '</div>' +
            '<div style="text-align:right">' +
              '<div style="color:var(--price);font-weight:800;font-size:22px"><span style="font-size:13px">¥</span>' + UI.yuan(c.amount) + '</div>' +
              '<button class="btn sm ' + cls + ' mt6" data-act="claim" data-id="' + esc(c.id) + '">' + btn + '</button>' +
            '</div>' +
          '</div>' +
        '</div>';
      }).join('');

      var subTabs = [
        { key: 'unused', label: '未使用', count: mine.filter(function (x) { return x.status === 'unused'; }).length },
        { key: 'used', label: '已使用', count: mine.filter(function (x) { return x.status === 'used'; }).length },
        { key: 'expired', label: '已过期', count: mine.filter(function (x) { return x.status === 'expired'; }).length }
      ];
      var myList = mine.filter(function (x) { return x.status === MYCP_TAB; });

      var myRows = myList.length
        ? myList.map(function (x) {
            var c = x.c;
            if (!c) return '';
            var akt = x.status === 'used' ? '<span class="tag gray">已使用</span>' :
              (x.status === 'expired' ? '<span class="tag gray">已过期</span>' : '<span class="tag ok">可用</span>');
            return '<div class="card pad" style="margin-bottom:10px;' + (x.status === 'unused' ? '' : 'opacity:.72') + '">' +
              '<div class="row between">' +
                '<div class="grow"><div class="bold" style="font-size:14px">' + esc(c.name) + '</div>' +
                  '<div class="tiny dim mt6">满 ' + UI.yuan(c.threshold) + ' 可用 · ' + UI.time(c.expireAt, false) + ' 到期</div>' +
                  '<div class="mt8">' + akt +
                    (x.status === 'unused' ? ' <span class="tiny dim">下单时自动可选</span>' : '') + '</div></div>' +
                '<div style="color:var(--price);font-weight:800;font-size:22px"><span style="font-size:13px">¥</span>' + UI.yuan(c.amount) + '</div>' +
              '</div>' +
            '</div>';
          }).join('')
        : UI.empty({
            icon: '🎫', title: MYCP_TAB === 'unused' ? '还没有可用的券' : '这里还是空的',
            hint: MYCP_TAB === 'unused' ? '去「领券中心」领一张，下单时自动可选' : '',
            action: MYCP_TAB === 'unused' ? '<button class="btn primary" data-go="#/c/coupons?tab=center">去领券</button>' : ''
          });

      var seg = '<div class="seg-tabs" style="margin:10px 12px 0">' +
        '<button class="' + (COUPON_TAB === 'center' ? 'on' : '') + '" data-act="ctab" data-key="center">领券中心</button>' +
        '<button class="' + (COUPON_TAB === 'mine' ? 'on' : '') + '" data-act="ctab" data-key="mine">我的券（' + mine.length + '）</button>' +
      '</div>';

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: '优惠券' }) +
          seg +
          '<div class="c-body">' +
            (COUPON_TAB === 'center'
              ? '<div class="tiny dim" style="margin-bottom:8px">一人一券：同一张券只能领 1 次（BR-CP-01）；领过与领完会给出不同提示（BR-CP-02/03）。</div>' + centerRows
              : tabsMarkup(subTabs, MYCP_TAB) + myRows) +
          '</div>' +
        '</div>',
        title: '优惠券',
        actions: {
          ctab: function (el) { go('#/c/coupons?tab=' + el.getAttribute('data-key')); },
          otab: function (el) { go('#/c/coupons?tab=mine&sub=' + el.getAttribute('data-key')); },
          claim: function (el) {
            var r = Store.claimCoupon(el.getAttribute('data-id'));
            /* 硬要求：区分「你已领过这张券」与「已领完」——直接透传 Store 的 msg */
            okToast(r);
            render();
          }
        }
      };
    }
  });

  /* ---------------------------- 17. 地址 ---------------------------- */
  C_ROUTES.push({
    path: 'address',
    render: function (ctx) {
      var pick = ctx.query.pick === '1';
      var list = Store.state.addresses;

      var rows = list.length
        ? list.map(function (a) {
            return '<div class="card pad" style="margin-bottom:10px">' +
              '<div class="row top" style="gap:10px">' +
                '<div style="color:var(--brand)">' + UI.icon('location', 19) + '</div>' +
                '<div class="grow" data-act="' + (pick ? 'pick' : 'noop') + '" data-id="' + esc(a.id) + '" style="cursor:pointer">' +
                  '<div class="small bold">' + esc(a.name) + ' <span class="muted">' + esc(a.phone) + '</span>' +
                    (a.isDefault ? ' <span class="tag">默认</span>' : '') + '</div>' +
                  '<div class="tiny dim mt6">' + addrText(a) + '</div>' +
                '</div>' +
              '</div>' +
              '<div class="row mt8" style="justify-content:flex-end;gap:8px;border-top:1px solid var(--line);padding-top:8px">' +
                (a.isDefault ? '' : '<button class="btn sm ghost" data-act="setDefault" data-id="' + esc(a.id) + '">设为默认</button>') +
                '<button class="btn sm ghost" data-act="edit" data-id="' + esc(a.id) + '">编辑</button>' +
                '<button class="btn sm ghost" data-act="del" data-id="' + esc(a.id) + '">删除</button>' +
              '</div>' +
            '</div>';
          }).join('')
        : UI.empty({ icon: '📍', title: '还没有收货地址', hint: '添加地址后才能在结算页提交订单' });

      function formSheet(id) {
        var a = id ? list.filter(function (x) { return x.id === id; })[0] : null;
        a = a || { name: '', phone: '', region: '', detail: '', isDefault: !list.length };
        UI.sheet({
          title: id ? '编辑地址' : '新增地址',
          body: '<div class="field"><div class="label">收货人</div>' +
              '<input class="input" id="ad-name" maxlength="20" value="' + esc(a.name) + '" placeholder="姓名" /></div>' +
            '<div class="field"><div class="label">手机号</div>' +
              '<input class="input" id="ad-phone" maxlength="11" inputmode="numeric" value="' + esc(a.phone) + '" placeholder="11 位手机号" /></div>' +
            '<div class="field"><div class="label">所在地区</div>' +
              '<input class="input" id="ad-region" value="' + esc(a.region) + '" placeholder="如：浙江省 杭州市 西湖区" /></div>' +
            '<div class="field"><div class="label">详细地址</div>' +
              '<input class="input" id="ad-detail" value="' + esc(detail(a.detail)) + '" placeholder="街道、门牌号、楼层" /></div>' +
            '<div class="row between" style="padding:4px 0"><span class="small">设为默认地址</span>' +
              '<span class="cbx' + (a.isDefault ? ' on' : '') + '" id="ad-default" data-act="toggleDefault"></span></div>',
          actions: [
            { text: '取消' },
            {
              text: '保存', type: 'primary', onClick: function () {
                var name = val('ad-name'), phone = val('ad-phone'), region = val('ad-region'), det = val('ad-detail');
                var def = document.getElementById('ad-default');
                if (!name) { toast('请填写收货人', 'err'); return false; }
                if (!/^\d{6,11}$/.test(phone.replace(/\s/g, ''))) { toast('请填写正确的手机号', 'err'); return false; }
                if (!det) { toast('请填写详细地址', 'err'); return false; }
                var r = Store.addressSave({
                  id: id || undefined, name: name, phone: phone,
                  region: region || '演示地区', detail: det,
                  isDefault: !!(def && def.className.indexOf('on') >= 0)
                });
                if (!okToast(r)) return false;
                render();
              }
            }
          ]
        });
      }

      return {
        html: '<div class="c-page">' + UI.cHeader({ title: pick ? '选择收货地址' : '收货地址', right: '<span data-act="add">新增</span>' }) +
          '<div class="c-body">' +
            (pick ? '<div class="tiny dim" style="margin-bottom:8px">点击地址可带回结算页</div>' : '') + rows +
            '<button class="btn primary block mt12" data-act="add">+ 新增收货地址</button>' +
            '<div class="tiny dim center" style="padding:12px 0">地址仅保存在本机浏览器</div>' +
          '</div>' +
        '</div>',
        title: pick ? '选择收货地址' : '收货地址',
        actions: {
          add: function () { formSheet(null); },
          edit: function (el) { formSheet(el.getAttribute('data-id')); },
          noop: function () { },
          pick: function (el) { go('#/c/checkout?addressId=' + el.getAttribute('data-id')); },
          setDefault: function (el) {
            var id = el.getAttribute('data-id');
            list.forEach(function (x) { x.isDefault = (x.id === id); });
            Store.save();
            toast('已设为默认地址', 'ok');
            render();
          },
          del: function (el) {
            var id = el.getAttribute('data-id');
            UI.confirm({ title: '删除地址', body: '删除后不可恢复。', okText: '删除', danger: true }).then(function (yes) {
              if (!yes) return;
              Store.addressRemove(id);
              toast('地址已删除', 'ok');
              render();
            });
          },
          toggleDefault: function (el) {
            el.className = el.className.indexOf('on') >= 0 ? 'cbx' : 'cbx on';
          }
        }
      };
    }
  });

  function val(id) {
    var el = document.getElementById(id);
    return el ? String(el.value || '').trim() : '';
  }
  function detail(s) { return s == null ? '' : s; }

  /* ============================================================================
   * 十、注册与启动
   * ==========================================================================*/
  window.C_ROUTES = C_ROUTES;

  function start() {
    if (!location.hash) {
      location.hash = '#/c/home';
      return;
    }
    render();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();